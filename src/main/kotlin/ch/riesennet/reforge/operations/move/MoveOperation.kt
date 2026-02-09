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
import com.intellij.psi.*
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

        // Phase 2: Execute all moves
        val byTarget = resolved.groupBy { it.target }

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
                        moveClass(project, psiClass, targetPackage)
                        reporter.moveSuccess(sourceName, targetName)
                        results.add(OperationResult("move", sourceName, targetName, ResultStatus.SUCCESS))
                        moved = true
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

        // Phase 3: Cleanup empty packages
        if (!dryRun) {
            cleanupEmptyPackages(project, sourcePackages, reporter)
        }

        return results
    }

    private fun moveClass(project: Project, psiClass: PsiClass, targetPackage: String) {
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
            processor.findAndExecute()

            VirtualFileManager.getInstance().syncRefresh()
        }
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
