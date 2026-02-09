package ch.riesennet.reforge.operations.extract

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
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope

/**
 * Extract interface operation: creates an interface from specified methods of a class,
 * makes the class implement it.
 */
class ExtractInterfaceOperation : Operation {

    override val type: String = "extract-interface"

    override fun parseSpec(raw: Map<String, Any>): OperationSpec {
        val sourceClass = raw["class"] as? String
            ?: throw IllegalArgumentException("extract-interface requires 'class' field")
        val interfaceName = raw["interface"] as? String
            ?: throw IllegalArgumentException("extract-interface requires 'interface' field")
        val methods = when (val m = raw["methods"]) {
            is List<*> -> m.filterIsInstance<String>()
            else -> throw IllegalArgumentException("extract-interface requires 'methods' list")
        }
        return ExtractInterfaceSpec(
            sourceClass = sourceClass,
            interfaceName = interfaceName,
            methods = methods
        )
    }

    override fun execute(
        project: Project,
        specs: List<OperationSpec>,
        reporter: ProgressReporter,
        dryRun: Boolean
    ): List<OperationResult> {
        val results = mutableListOf<OperationResult>()

        for (spec in specs.filterIsInstance<ExtractInterfaceSpec>()) {
            reporter.section("Extracting interface ${spec.interfaceName} from ${spec.sourceClass}:")

            if (dryRun) {
                for (method in spec.methods) {
                    reporter.info("  [dry-run] would extract method: $method")
                }
                results.add(OperationResult(
                    "extract-interface", spec.sourceClass, spec.interfaceName,
                    ResultStatus.SKIPPED
                ))
                continue
            }

            try {
                DumbService.getInstance(project).waitForSmartMode()
                extractInterface(project, spec, reporter)
                reporter.operationSuccess("extract-interface", spec.sourceClass, spec.interfaceName)
                results.add(OperationResult(
                    "extract-interface", spec.sourceClass, spec.interfaceName,
                    ResultStatus.SUCCESS
                ))
            } catch (e: Exception) {
                val error = e.message ?: "Unknown error"
                reporter.operationFailure("extract-interface", spec.sourceClass, error)
                results.add(OperationResult(
                    "extract-interface", spec.sourceClass, spec.interfaceName,
                    ResultStatus.FAILED, error
                ))
            }
        }

        return results
    }

    private fun extractInterface(project: Project, spec: ExtractInterfaceSpec, reporter: ProgressReporter) {
        ApplicationManager.getApplication().invokeAndWait {
            // Find the source class
            val psiClass = ReadAction.compute<PsiClass?, Exception> {
                JavaPsiFacade.getInstance(project)
                    .findClass(spec.sourceClass, GlobalSearchScope.projectScope(project))
            } ?: throw IllegalStateException("Class not found: ${spec.sourceClass}")

            // Find the methods to extract
            val methodsToExtract = ReadAction.compute<List<PsiMethod>, Exception> {
                spec.methods.mapNotNull { methodName ->
                    psiClass.findMethodsByName(methodName, false).firstOrNull()
                }
            }

            if (methodsToExtract.size != spec.methods.size) {
                val found = ReadAction.compute<List<String>, Exception> {
                    methodsToExtract.mapNotNull { it.name }
                }
                val missing = spec.methods - found.toSet()
                System.err.println("  Warning: methods not found: $missing")
            }

            // Determine where to create the interface
            val interfacePackage = spec.interfaceName.substringBeforeLast('.')
            val interfaceSimpleName = spec.interfaceName.substringAfterLast('.')

            // Create the interface file
            WriteCommandAction.writeCommandAction(project).run<Exception> {
                // Find or create target directory
                val sourceFile = psiClass.containingFile?.virtualFile
                val fileIndex = ProjectRootManager.getInstance(project).fileIndex
                val sourceRoot = sourceFile?.let { fileIndex.getSourceRootForFile(it) }

                val targetDir = if (sourceRoot != null) {
                    createPackageDir(project, interfacePackage, sourceRoot)
                } else {
                    val sourceRoots = ProjectRootManager.getInstance(project).contentSourceRoots
                    createPackageDir(project, interfacePackage, sourceRoots.first())
                }

                // Build interface source
                val methodDeclarations = methodsToExtract.joinToString("\n\n") { method ->
                    val returnType = method.returnType?.presentableText ?: "void"
                    val params = method.parameterList.parameters.joinToString(", ") { param ->
                        "${param.type.presentableText} ${param.name}"
                    }
                    "    $returnType ${method.name}($params);"
                }

                val interfaceSource = buildString {
                    appendLine("package $interfacePackage;")
                    appendLine()
                    appendLine("public interface $interfaceSimpleName {")
                    appendLine()
                    append(methodDeclarations)
                    appendLine()
                    appendLine("}")
                }

                // Create the interface file
                val factory = PsiFileFactory.getInstance(project)
                val interfaceFile = factory.createFileFromText(
                    "$interfaceSimpleName.java",
                    com.intellij.lang.java.JavaLanguage.INSTANCE,
                    interfaceSource
                )
                targetDir.add(interfaceFile)

                // Make the source class implement the interface
                val elementFactory = JavaPsiFacade.getElementFactory(project)
                val interfaceRef = elementFactory.createReferenceFromText(spec.interfaceName, psiClass)
                val implementsList = psiClass.implementsList
                if (implementsList != null) {
                    implementsList.add(interfaceRef)
                } else {
                    // Class has no implements clause — create one
                    val refList = elementFactory.createReferenceList(arrayOf(interfaceRef))
                    psiClass.addAfter(refList, psiClass.extendsList ?: psiClass.nameIdentifier)
                }
            }

            VirtualFileManager.getInstance().syncRefresh()
        }
    }

    private fun createPackageDir(project: Project, packageName: String, sourceRoot: com.intellij.openapi.vfs.VirtualFile): PsiDirectory {
        val baseDir = PsiManager.getInstance(project).findDirectory(sourceRoot)
            ?: throw IllegalStateException("Cannot find PsiDirectory for source root: ${sourceRoot.path}")

        var currentDir = baseDir
        for (segment in packageName.split(".")) {
            val subDir = currentDir.findSubdirectory(segment)
            currentDir = subDir ?: currentDir.createSubdirectory(segment)
        }

        return currentDir
    }
}
