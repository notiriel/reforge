package ch.riesennet.reforge.operations.move

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination
import com.intellij.usageView.UsageInfo

/**
 * Subclass that exposes protected findUsages/execute for headless use,
 * bypassing the UI dialog path in BaseRefactoringProcessor.run().
 *
 * Also optimizes imports after the move to fix duplicate import conflicts
 * that can occur when moved classes have the same simple name as existing imports.
 */
class HeadlessMoveProcessor(
    project: Project,
    classes: Array<PsiClass>,
    destination: SingleSourceRootMoveDestination,
    searchInComments: Boolean,
    searchTextOccurrences: Boolean,
    moveCallback: MoveCallback?
) : MoveClassesOrPackagesProcessor(
    project, classes, destination, searchInComments, searchTextOccurrences, moveCallback
) {
    fun findAndExecute(affectedFilesAccumulator: MutableSet<PsiJavaFile>? = null) {
        val usages = findUsages()

        // Collect affected Java files before the move
        val affectedFiles = collectAffectedFiles(usages)

        execute(usages)

        if (affectedFilesAccumulator != null) {
            // Defer import optimization — caller will run it after all moves
            affectedFilesAccumulator.addAll(affectedFiles)
        } else {
            optimizeImportsInAffectedFiles(affectedFiles)
        }
    }

    /**
     * Collects unique Java files that will be affected by the move.
     * These files may end up with duplicate imports after the refactoring.
     */
    private fun collectAffectedFiles(usages: Array<UsageInfo>): Set<PsiJavaFile> {
        val files = mutableSetOf<PsiJavaFile>()
        for (usage in usages) {
            val element = usage.element ?: continue
            val file = element.containingFile as? PsiJavaFile ?: continue
            files.add(file)
        }
        return files
    }

    /**
     * Runs optimizeImports on all affected files to resolve duplicate import conflicts.
     * This handles the case where a moved class has the same simple name as an existing import.
     */
    internal fun optimizeImportsInAffectedFiles(files: Set<PsiJavaFile>) {
        if (files.isEmpty()) return

        val codeStyleManager = JavaCodeStyleManager.getInstance(myProject)

        WriteCommandAction.writeCommandAction(myProject).run<Exception> {
            for (file in files) {
                if (!file.isValid) continue
                try {
                    codeStyleManager.optimizeImports(file)
                } catch (e: Exception) {
                    // Log but don't fail - import optimization is best-effort
                    System.err.println("  Warning: Could not optimize imports in ${file.name}: ${e.message}")
                }
            }
        }
    }
}
