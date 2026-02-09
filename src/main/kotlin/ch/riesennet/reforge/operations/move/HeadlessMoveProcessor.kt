package ch.riesennet.reforge.operations.move

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination

/**
 * Subclass that exposes protected findUsages/execute for headless use,
 * bypassing the UI dialog path in BaseRefactoringProcessor.run().
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
    fun findAndExecute() {
        val usages = findUsages()
        execute(usages)
    }
}
