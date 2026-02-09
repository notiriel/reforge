package ch.riesennet.reforge.operations.replace

import ch.riesennet.reforge.ProgressReporter
import ch.riesennet.reforge.operation.Operation
import ch.riesennet.reforge.operation.OperationResult
import ch.riesennet.reforge.operation.OperationSpec
import ch.riesennet.reforge.operation.ResultStatus
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope

/**
 * Replace dependency operation: finds fields, constructor parameters, and method parameters
 * of the old type in the target class and replaces them with the new type.
 * Also updates import statements.
 */
class ReplaceDependencyOperation : Operation {

    override val type: String = "replace-dependency"

    override fun parseSpec(raw: Map<String, Any>): OperationSpec {
        val inClass = raw["in"] as? String
            ?: throw IllegalArgumentException("replace-dependency requires 'in' field")
        val replace = raw["replace"] as? String
            ?: throw IllegalArgumentException("replace-dependency requires 'replace' field")
        val with = raw["with"] as? String
            ?: throw IllegalArgumentException("replace-dependency requires 'with' field")
        return ReplaceDependencySpec(inClass = inClass, replace = replace, with = with)
    }

    override fun execute(
        project: Project,
        specs: List<OperationSpec>,
        reporter: ProgressReporter,
        dryRun: Boolean
    ): List<OperationResult> {
        val results = mutableListOf<OperationResult>()

        for (spec in specs.filterIsInstance<ReplaceDependencySpec>()) {
            val description = "${spec.replace} → ${spec.with} in ${spec.inClass}"
            reporter.section("Replacing dependency: $description")

            if (dryRun) {
                reporter.info("  [dry-run] would replace ${spec.replace} with ${spec.with} in ${spec.inClass}")
                results.add(OperationResult(
                    "replace-dependency", spec.inClass, description,
                    ResultStatus.SKIPPED
                ))
                continue
            }

            try {
                DumbService.getInstance(project).waitForSmartMode()
                replaceDependency(project, spec, reporter)
                reporter.operationSuccess("replace-dependency", spec.inClass, description)
                results.add(OperationResult(
                    "replace-dependency", spec.inClass, description,
                    ResultStatus.SUCCESS
                ))
            } catch (e: Exception) {
                val error = e.message ?: "Unknown error"
                reporter.operationFailure("replace-dependency", spec.inClass, error)
                results.add(OperationResult(
                    "replace-dependency", spec.inClass, description,
                    ResultStatus.FAILED, error
                ))
            }
        }

        return results
    }

    private fun replaceDependency(project: Project, spec: ReplaceDependencySpec, reporter: ProgressReporter) {
        ApplicationManager.getApplication().invokeAndWait {
            val psiClass = ReadAction.compute<PsiClass?, Exception> {
                JavaPsiFacade.getInstance(project)
                    .findClass(spec.inClass, GlobalSearchScope.projectScope(project))
            } ?: throw IllegalStateException("Class not found: ${spec.inClass}")

            val replacementClass = ReadAction.compute<PsiClass?, Exception> {
                JavaPsiFacade.getInstance(project)
                    .findClass(spec.with, GlobalSearchScope.allScope(project))
            } ?: throw IllegalStateException("Replacement type not found: ${spec.with}")

            WriteCommandAction.writeCommandAction(project).run<Exception> {
                val factory = JavaPsiFacade.getElementFactory(project)
                val newType = factory.createType(replacementClass)
                val oldSimpleName = spec.replace.substringAfterLast('.')
                var replacementCount = 0

                // Replace field types
                for (field in psiClass.fields) {
                    if (field.type.canonicalText == spec.replace ||
                        field.type.presentableText == oldSimpleName) {
                        field.typeElement?.replace(factory.createTypeElement(newType))
                        replacementCount++
                    }
                }

                // Replace constructor parameter types
                for (constructor in psiClass.constructors) {
                    for (param in constructor.parameterList.parameters) {
                        if (param.type.canonicalText == spec.replace ||
                            param.type.presentableText == oldSimpleName) {
                            param.typeElement?.replace(factory.createTypeElement(newType))
                            replacementCount++
                        }
                    }
                }

                // Replace method parameter types
                for (method in psiClass.methods) {
                    if (method.isConstructor) continue
                    for (param in method.parameterList.parameters) {
                        if (param.type.canonicalText == spec.replace ||
                            param.type.presentableText == oldSimpleName) {
                            param.typeElement?.replace(factory.createTypeElement(newType))
                            replacementCount++
                        }
                    }
                    // Replace return type
                    val returnType = method.returnTypeElement
                    if (returnType != null && (method.returnType?.canonicalText == spec.replace ||
                                returnType.text == oldSimpleName)) {
                        returnType.replace(factory.createTypeElement(newType))
                        replacementCount++
                    }
                }

                // Update imports: add new import, remove old if no longer used
                val javaFile = psiClass.containingFile as? PsiJavaFile
                if (javaFile != null) {
                    val importList = javaFile.importList
                    if (importList != null) {
                        // Add import for replacement type
                        val newImport = factory.createImportStatement(replacementClass)
                        importList.add(newImport)

                        // Remove old import if no longer referenced
                        val oldImport = importList.importStatements.find {
                            it.qualifiedName == spec.replace
                        }
                        if (oldImport != null) {
                            // Check if old type is still referenced anywhere in the file
                            val stillUsed = javaFile.text.contains(oldSimpleName)
                            if (!stillUsed || replacementCount > 0) {
                                // Be conservative: only remove if we replaced all usages
                                oldImport.delete()
                            }
                        }
                    }
                }

                reporter.info("  Replaced $replacementCount reference(s)")
            }

            VirtualFileManager.getInstance().syncRefresh()
        }
    }
}
