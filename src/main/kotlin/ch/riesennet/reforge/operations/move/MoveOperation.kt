package ch.riesennet.reforge.operations.move

import ch.riesennet.reforge.ClassResolver
import ch.riesennet.reforge.ProgressReporter
import ch.riesennet.reforge.infrastructure.IndexingHelper
import ch.riesennet.reforge.operation.Operation
import ch.riesennet.reforge.operation.OperationResult
import ch.riesennet.reforge.operation.OperationSpec
import ch.riesennet.reforge.operation.ResultStatus
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AllClassesSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiUtil
import com.intellij.refactoring.PackageWrapper
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination

/**
 * Move operation: resolves class patterns across multiple passes, then executes
 * all moves in a single phase. Cleans up empty source packages afterward.
 */
class MoveOperation : Operation {

    override val type: String = "move"

    override fun parseSpec(raw: Map<String, Any>): OperationSpec {
        val target = raw["target"] as? String
            ?: throw IllegalArgumentException("Move operation requires 'target' field")
        val sources = when (val s = raw["sources"]) {
            is List<*> -> s.filterIsInstance<String>()
            else -> throw IllegalArgumentException("Move operation requires 'sources' list")
        }
        return MoveSpec(target = target, sources = sources)
    }

    override fun execute(
        project: Project,
        specs: List<OperationSpec>,
        reporter: ProgressReporter,
        dryRun: Boolean
    ): List<OperationResult> {
        val moveSpecs = specs.filterIsInstance<MoveSpec>()
        val results = mutableListOf<OperationResult>()
        val sourcePackages = mutableSetOf<String>()
        // Track successful moves for stale import fixing: oldFqn -> newFqn
        val movedClasses = mutableMapOf<String, String>()

        // Phase 1: Resolve all patterns (multi-pass)
        data class ResolvedEntry(val target: String, val pattern: String, val classes: List<PsiClass>)

        var resolved = emptyList<ResolvedEntry>()

        for (pass in 1..5) {
            if (pass > 1) {
                reporter.info("")
                reporter.info("Pass $pass: retrying resolution...")
                IndexingHelper.waitForSmartMode(project)
            }

            reporter.section("Resolving patterns...")
            resolved = moveSpecs.flatMap { spec ->
                spec.sources.map { pattern ->
                    val classes = ClassResolver.findMatchingClasses(project, pattern)
                    reporter.patternResolved(pattern, classes.size)
                    ResolvedEntry(spec.target, pattern, classes)
                }
            }

            val unresolvedCount = resolved.count { it.classes.isEmpty() }
            if (unresolvedCount == 0) break
        }

        // Phase 1.5: Deduplicate — first match wins
        val claimed = mutableMapOf<String, String>() // FQN → target that claimed it
        resolved = resolved.map { entry ->
            val deduplicated = entry.classes.filter { psiClass ->
                val fqn = ReadAction.compute<String?, Exception> { psiClass.qualifiedName } ?: return@filter false
                val existingTarget = claimed[fqn]
                if (existingTarget == null) {
                    claimed[fqn] = entry.target
                    true
                } else if (existingTarget == entry.target) {
                    // Same target — skip silently (duplicate within same target is harmless but wasteful)
                    false
                } else {
                    reporter.moveSkipped(fqn, "already claimed for $existingTarget")
                    false
                }
            }
            entry.copy(classes = deduplicated)
        }

        // Phase 2: Execute all moves
        val byTarget = resolved.groupBy { it.target }
        val allAffectedFiles = mutableSetOf<PsiJavaFile>()

        for ((targetPackage, entries) in byTarget) {
            reporter.section("Moving to $targetPackage:")

            val allClasses = entries.flatMap { it.classes }
            if (allClasses.isEmpty()) {
                reporter.info("  (no classes to move)")
                continue
            }

            if (dryRun) {
                for (psiClass in allClasses) {
                    val sourceName = ReadAction.compute<String?, Exception> { psiClass.qualifiedName }
                        ?: continue
                    val targetName = "$targetPackage.${ReadAction.compute<String?, Exception> { psiClass.name }}"
                    reporter.dryRunMove(sourceName, targetName)
                    results.add(OperationResult("move", sourceName, targetName, ResultStatus.SKIPPED))
                }
                continue
            }

            for (psiClass in allClasses) {
                val sourceName = ReadAction.compute<String?, Exception> { psiClass.qualifiedName }
                    ?: continue
                val sourcePackageName = sourceName.substringBeforeLast('.', "")
                val targetName = "$targetPackage.${ReadAction.compute<String?, Exception> { psiClass.name }}"

                var moved = false
                for (attempt in 1..3) {
                    try {
                        DumbService.getInstance(project).waitForSmartMode()
                        moveClass(project, psiClass, targetPackage, allAffectedFiles)
                        reporter.moveSuccess(sourceName, targetName)
                        results.add(OperationResult("move", sourceName, targetName, ResultStatus.SUCCESS))
                        moved = true
                        movedClasses[sourceName] = targetName
                        break
                    } catch (e: Exception) {
                        if (IndexingHelper.isIndexNotReadyException(e) && attempt < 3) {
                            System.err.println("  Index not ready for $sourceName, retrying (attempt ${attempt + 1}/3)...")
                            Thread.sleep(2000)
                        } else {
                            val error = e.message ?: "Unknown error"
                            reporter.moveFailure(sourceName, error)
                            results.add(OperationResult("move", sourceName, targetName, ResultStatus.FAILED, error))
                            if (!IndexingHelper.isIndexNotReadyException(e)) break
                        }
                    }
                }

                if (moved && sourcePackageName.isNotEmpty()) {
                    sourcePackages.add(sourcePackageName)
                }
            }
        }

        // Phase 2.5: Optimize imports in all affected files (deferred from individual moves)
        if (!dryRun && allAffectedFiles.isNotEmpty()) {
            reporter.section("Optimizing imports in affected files...")
            IndexingHelper.waitForSmartMode(project)
            ApplicationManager.getApplication().invokeAndWait {
                val codeStyleManager = com.intellij.psi.codeStyle.JavaCodeStyleManager.getInstance(project)
                WriteCommandAction.writeCommandAction(project).run<Exception> {
                    for (file in allAffectedFiles) {
                        if (!file.isValid) continue
                        try {
                            codeStyleManager.optimizeImports(file)
                        } catch (e: Exception) {
                            System.err.println("  Warning: Could not optimize imports in ${file.name}: ${e.message}")
                        }
                    }
                }
                VirtualFileManager.getInstance().syncRefresh()
            }
            reporter.info("  Optimized imports in ${allAffectedFiles.count { it.isValid }} file(s)")
        }

        // Phase 2.75: Infer MapStruct *Impl moves
        if (!dryRun && movedClasses.isNotEmpty()) {
            inferGeneratedClassMoves(project, movedClasses, reporter)
        }

        // Phase 3: Fix stale imports that weren't updated due to indexing issues
        if (!dryRun && movedClasses.isNotEmpty()) {
            fixStaleImports(project, movedClasses, reporter)
        }

        // Phase 4: Fix visibility issues for package-private members accessed from other packages
        if (!dryRun && movedClasses.isNotEmpty()) {
            fixVisibilityIssues(project, movedClasses, reporter)
        }

        // Phase 5: Cleanup empty packages
        if (!dryRun) {
            cleanupEmptyPackages(project, sourcePackages, reporter)
        }

        return results
    }

    private fun moveClass(
        project: Project,
        psiClass: PsiClass,
        targetPackage: String,
        affectedFilesAccumulator: MutableSet<PsiJavaFile>? = null
    ) {
        ApplicationManager.getApplication().invokeAndWait {
            val targetDirectory = WriteCommandAction.writeCommandAction(project)
                .compute<PsiDirectory, Exception> {
                    val sourceFile = psiClass.containingFile?.virtualFile
                    val fileIndex = ProjectRootManager.getInstance(project).fileIndex
                    val sourceRoot = sourceFile?.let { fileIndex.getSourceRootForFile(it) }

                    if (sourceRoot != null) {
                        createPackageInSourceRoot(project, targetPackage, sourceRoot)
                    } else {
                        createPackageDirectories(project, targetPackage)
                    }
                }

            VirtualFileManager.getInstance().syncRefresh()

            val packageWrapper = PackageWrapper(PsiManager.getInstance(project), targetPackage)
            val destination = SingleSourceRootMoveDestination(packageWrapper, targetDirectory)

            val processor = HeadlessMoveProcessor(
                project,
                arrayOf(psiClass),
                destination,
                true,
                true,
                null
            )

            processor.setPreviewUsages(false)
            processor.findAndExecute(affectedFilesAccumulator)

            VirtualFileManager.getInstance().syncRefresh()
        }
    }

    /**
     * Infers virtual move entries for generated classes (e.g. MapStruct *Impl).
     * These classes aren't in the source tree but their imports still need fixing.
     */
    private fun inferGeneratedClassMoves(
        project: Project,
        movedClasses: MutableMap<String, String>,
        reporter: ProgressReporter
    ) {
        reporter.section("Inferring generated class moves...")

        val mapperFqns = mutableSetOf<String>()

        ApplicationManager.getApplication().invokeAndWait {
            ReadAction.run<Exception> {
                val scope = GlobalSearchScope.projectScope(project)
                for ((_, newFqn) in movedClasses) {
                    val psiClass = JavaPsiFacade.getInstance(project).findClass(newFqn, scope)
                        ?: continue
                    if (psiClass.annotations.any { it.qualifiedName == "org.mapstruct.Mapper" }) {
                        mapperFqns.add(newFqn)
                    }
                }
            }
        }

        val inferred = ImportFixHelper.inferMapperImplMoves(movedClasses, mapperFqns)
        if (inferred.isNotEmpty()) {
            movedClasses.putAll(inferred)
            reporter.info("  Inferred ${inferred.size} MapStruct Impl move(s)")
        } else {
            reporter.info("  No generated class moves inferred")
        }
    }

    /**
     * Fixes stale imports that weren't updated during the move due to indexing issues.
     * This can happen when the project becomes non-compilable mid-execution.
     *
     * Uses three strategies:
     * 1. Exact-match: import FQN found in movedClasses map (with text-based fallback)
     * 2. Package-rename fallback: infer new FQN from package rename patterns
     * 3. Static imports: match class portion against movedClasses
     */
    private fun fixStaleImports(
        project: Project,
        movedClasses: Map<String, String>,
        reporter: ProgressReporter
    ) {
        reporter.section("Fixing stale imports...")

        var fixedCount = 0
        val filesToProcess = mutableSetOf<PsiJavaFile>()

        // Collect all Java files in the project
        ApplicationManager.getApplication().invokeAndWait {
            ReadAction.run<Exception> {
                val scope = GlobalSearchScope.projectScope(project)
                AllClassesSearch.search(scope, project).forEach { psiClass ->
                    val file = psiClass.containingFile as? PsiJavaFile
                    if (file != null) {
                        filesToProcess.add(file)
                    }
                }
            }
        }

        val packageRenameMap = ImportFixHelper.buildPackageRenameMap(movedClasses)

        // Process each file looking for stale imports
        ApplicationManager.getApplication().invokeAndWait {
            WriteCommandAction.writeCommandAction(project).run<Exception> {
                val scope = GlobalSearchScope.projectScope(project)

                for (file in filesToProcess) {
                    if (!file.isValid) continue

                    val importList = file.importList ?: continue
                    val fixedImports = mutableSetOf<String>()

                    // Pass 1: Exact-match with text-based fallback
                    for (importStatement in importList.importStatements) {
                        val importedFqn = importStatement.qualifiedName ?: continue

                        val newFqn = movedClasses[importedFqn]
                        if (newFqn != null) {
                            try {
                                val targetClass = JavaPsiFacade.getInstance(project).findClass(newFqn, scope)
                                if (targetClass != null) {
                                    val newImport = JavaPsiFacade.getElementFactory(project)
                                        .createImportStatement(targetClass)
                                    importStatement.replace(newImport)
                                } else {
                                    // Text-based fallback when class not in index (e.g. generated code)
                                    replaceImportTextually(project, importStatement, newFqn)
                                    System.err.println("  Warning: Class $newFqn not found in index, fixed import textually")
                                }
                                fixedImports.add(importedFqn)
                                fixedCount++
                            } catch (e: Exception) {
                                System.err.println("  Warning: Could not fix import $importedFqn in ${file.name}: ${e.message}")
                            }
                        }
                    }

                    // Pass 2: Package-rename fallback for unresolved imports
                    for (importStatement in importList.importStatements) {
                        val importedFqn = importStatement.qualifiedName ?: continue
                        if (importedFqn in fixedImports) continue

                        // Skip if the import is still valid
                        if (JavaPsiFacade.getInstance(project).findClass(importedFqn, scope) != null) continue

                        val candidates = ImportFixHelper.resolveViaPackageRename(importedFqn, packageRenameMap)
                        if (candidates.isEmpty()) continue

                        try {
                            if (candidates.size == 1) {
                                val candidateFqn = candidates.single()
                                replaceImportTextually(project, importStatement, candidateFqn)
                                fixedCount++
                                System.err.println("  Fixed by package rename: $importedFqn → $candidateFqn")
                            } else {
                                // Multiple candidates — try to resolve each
                                val resolved = candidates.firstOrNull { candidateFqn ->
                                    JavaPsiFacade.getInstance(project).findClass(candidateFqn, scope) != null
                                }
                                if (resolved != null) {
                                    val targetClass = JavaPsiFacade.getInstance(project).findClass(resolved, scope)!!
                                    val newImport = JavaPsiFacade.getElementFactory(project)
                                        .createImportStatement(targetClass)
                                    importStatement.replace(newImport)
                                    fixedCount++
                                    System.err.println("  Fixed by package rename: $importedFqn → $resolved")
                                } else {
                                    System.err.println("  Warning: Multiple candidates for $importedFqn but none resolved: $candidates")
                                }
                            }
                        } catch (e: Exception) {
                            System.err.println("  Warning: Could not fix import $importedFqn via package rename in ${file.name}: ${e.message}")
                        }
                    }

                    // Pass 3: Static imports
                    for (staticImport in importList.importStaticStatements) {
                        val importedFqn = staticImport.importReference?.qualifiedName ?: continue

                        for ((oldFqn, newFqn) in movedClasses) {
                            if (importedFqn.startsWith("$oldFqn.")) {
                                val memberName = importedFqn.removePrefix("$oldFqn.")
                                try {
                                    val targetClass = JavaPsiFacade.getInstance(project)
                                        .findClass(newFqn, scope)
                                    if (targetClass != null) {
                                        val newStaticImport = JavaPsiFacade.getElementFactory(project)
                                            .createImportStaticStatement(targetClass, memberName)
                                        staticImport.replace(newStaticImport)
                                        fixedCount++
                                    } else {
                                        System.err.println("  Warning: Class $newFqn not found for static import fix")
                                    }
                                } catch (e: Exception) {
                                    System.err.println("  Warning: Could not fix static import $importedFqn in ${file.name}: ${e.message}")
                                }
                                break
                            }
                        }
                    }
                }
            }
            VirtualFileManager.getInstance().syncRefresh()
        }

        if (fixedCount > 0) {
            reporter.info("  Fixed $fixedCount stale import(s)")
        } else {
            reporter.info("  No stale imports found")
        }
    }

    /**
     * Replaces an import statement textually, without requiring the target class to exist in the index.
     * Used for generated classes (MapStruct Impl, etc.) that aren't in source.
     */
    private fun replaceImportTextually(project: Project, importStatement: PsiImportStatement, newFqn: String) {
        val importText = "import $newFqn;"
        val dummyFile = PsiFileFactory.getInstance(project)
            .createFileFromText("Dummy.java", JavaFileType.INSTANCE, "package dummy;\n$importText\nclass Dummy {}")
        val newImport = (dummyFile as PsiJavaFile).importList?.importStatements?.firstOrNull()
            ?: throw IllegalStateException("Failed to create import statement for $newFqn")
        importStatement.replace(newImport)
    }

    /**
     * Fixes visibility issues where package-private members become inaccessible after moves.
     *
     * When a class is moved to a different package, callers that were in the same package
     * can no longer access package-private members. This method finds such cases and
     * changes the visibility to public.
     */
    private fun fixVisibilityIssues(
        project: Project,
        movedClasses: Map<String, String>,
        reporter: ProgressReporter
    ) {
        reporter.section("Fixing visibility issues...")

        var fixedCount = 0
        val membersToFix = mutableSetOf<PsiModifierListOwner>()

        // For each moved class, find package-private members that are used from other packages
        ApplicationManager.getApplication().invokeAndWait {
            ReadAction.run<Exception> {
                val scope = GlobalSearchScope.projectScope(project)

                for ((_, newFqn) in movedClasses) {
                    val movedClass = JavaPsiFacade.getInstance(project).findClass(newFqn, scope)
                        ?: continue

                    val movedPackage = newFqn.substringBeforeLast('.', "")

                    // Check the class itself if it's package-private
                    if (isPackagePrivate(movedClass)) {
                        if (hasUsagesFromOtherPackages(project, movedClass, movedPackage)) {
                            membersToFix.add(movedClass)
                        }
                    }

                    // Check all members (methods, fields, inner classes)
                    for (method in movedClass.methods) {
                        if (isPackagePrivate(method) && hasUsagesFromOtherPackages(project, method, movedPackage)) {
                            membersToFix.add(method)
                        }
                    }

                    for (field in movedClass.fields) {
                        if (isPackagePrivate(field) && hasUsagesFromOtherPackages(project, field, movedPackage)) {
                            membersToFix.add(field)
                        }
                    }

                    for (innerClass in movedClass.innerClasses) {
                        if (isPackagePrivate(innerClass) && hasUsagesFromOtherPackages(project, innerClass, movedPackage)) {
                            membersToFix.add(innerClass)
                        }
                    }
                }
            }
        }

        // Fix visibility for all identified members
        if (membersToFix.isNotEmpty()) {
            ApplicationManager.getApplication().invokeAndWait {
                WriteCommandAction.writeCommandAction(project).run<Exception> {
                    for (member in membersToFix) {
                        if (!member.isValid) continue
                        try {
                            PsiUtil.setModifierProperty(member, PsiModifier.PUBLIC, true)
                            val memberName = when (member) {
                                is PsiClass -> member.qualifiedName ?: member.name
                                is PsiMethod -> "${member.containingClass?.name}.${member.name}()"
                                is PsiField -> "${member.containingClass?.name}.${member.name}"
                                else -> member.toString()
                            }
                            reporter.info("  Changed to public: $memberName")
                            fixedCount++
                        } catch (e: Exception) {
                            System.err.println("  Warning: Could not fix visibility for $member: ${e.message}")
                        }
                    }
                }
                VirtualFileManager.getInstance().syncRefresh()
            }
        }

        if (fixedCount > 0) {
            reporter.info("  Fixed $fixedCount visibility issue(s)")
        } else {
            reporter.info("  No visibility issues found")
        }
    }

    /**
     * Checks if a member has package-private (default) visibility.
     */
    private fun isPackagePrivate(member: PsiModifierListOwner): Boolean {
        val modifierList = member.modifierList ?: return true // No modifier list = package-private
        return !modifierList.hasModifierProperty(PsiModifier.PUBLIC) &&
                !modifierList.hasModifierProperty(PsiModifier.PROTECTED) &&
                !modifierList.hasModifierProperty(PsiModifier.PRIVATE)
    }

    /**
     * Checks if a member has usages from packages other than the specified one.
     */
    private fun hasUsagesFromOtherPackages(
        project: Project,
        member: PsiModifierListOwner,
        memberPackage: String
    ): Boolean {
        val scope = GlobalSearchScope.projectScope(project)
        val references = ReferencesSearch.search(member as PsiElement, scope).findAll()

        for (reference in references) {
            val referenceFile = reference.element.containingFile as? PsiJavaFile ?: continue
            val referencePackage = referenceFile.packageName
            if (referencePackage != memberPackage) {
                return true
            }
        }
        return false
    }

    private fun cleanupEmptyPackages(project: Project, sourcePackages: Set<String>, reporter: ProgressReporter) {
        if (sourcePackages.isEmpty()) return

        reporter.section("Cleaning up empty packages...")

        val sortedPackages = sourcePackages.sortedByDescending { it.count { c -> c == '.' } }
        val deletedPackages = mutableSetOf<String>()

        for (packageName in sortedPackages) {
            var currentPackage = packageName
            while (currentPackage.isNotEmpty()) {
                if (currentPackage in deletedPackages) {
                    currentPackage = currentPackage.substringBeforeLast('.', "")
                    continue
                }

                val deletedAny = deleteEmptyDirectories(project, currentPackage)

                if (deletedAny) {
                    deletedPackages.add(currentPackage)
                    reporter.packageDeleted(currentPackage)
                    currentPackage = currentPackage.substringBeforeLast('.', "")
                } else {
                    break
                }
            }
        }

        if (deletedPackages.isEmpty()) {
            reporter.info("  No empty packages to remove")
        }

        ApplicationManager.getApplication().invokeAndWait {
            VirtualFileManager.getInstance().syncRefresh()
        }
    }

    private fun deleteEmptyDirectories(project: Project, packageName: String): Boolean {
        var deleted = false
        ApplicationManager.getApplication().invokeAndWait {
            WriteCommandAction.writeCommandAction(project).run<Exception> {
                val psiPackage = JavaPsiFacade.getInstance(project).findPackage(packageName) ?: return@run
                for (dir in psiPackage.directories) {
                    if (dir.files.isEmpty() && dir.subdirectories.isEmpty()) {
                        dir.delete()
                        deleted = true
                    }
                }
            }
            VirtualFileManager.getInstance().syncRefresh()
        }
        return deleted
    }

    private fun createPackageInSourceRoot(project: Project, packageName: String, sourceRoot: VirtualFile): PsiDirectory {
        val baseDir = PsiManager.getInstance(project).findDirectory(sourceRoot)
            ?: throw IllegalStateException("Cannot find PsiDirectory for source root: ${sourceRoot.path}")

        var currentDir = baseDir
        for (segment in packageName.split(".")) {
            val subDir = currentDir.findSubdirectory(segment)
            currentDir = subDir ?: currentDir.createSubdirectory(segment)
        }

        return currentDir
    }

    private fun createPackageDirectories(project: Project, packageName: String): PsiDirectory {
        val sourceRoots = ProjectRootManager.getInstance(project).contentSourceRoots

        if (sourceRoots.isEmpty()) {
            throw IllegalStateException("No source roots found in project")
        }

        val baseDir = PsiManager.getInstance(project).findDirectory(sourceRoots.first())
            ?: throw IllegalStateException("Cannot find base source directory")

        var currentDir = baseDir
        for (segment in packageName.split(".")) {
            val subDir = currentDir.findSubdirectory(segment)
            currentDir = subDir ?: currentDir.createSubdirectory(segment)
        }

        return currentDir
    }
}
