package ch.riesennet.reforge.operation

import ch.riesennet.reforge.ProgressReporter
import com.intellij.openapi.project.Project

/**
 * Interface for refactoring operations.
 *
 * Each operation type (move, extract-interface, replace-dependency) implements this
 * interface with its own resolve/execute logic. The orchestrator groups consecutive
 * same-type operations into batches and calls [execute] once per batch.
 */
interface Operation {
    val type: String

    /**
     * Parse a raw YAML map into a typed spec for this operation.
     */
    fun parseSpec(raw: Map<String, Any>): OperationSpec

    /**
     * Execute a batch of specs of this type.
     * Called with all consecutive specs of the same type grouped together.
     * Returns results for each individual action taken.
     */
    fun execute(
        project: Project,
        specs: List<OperationSpec>,
        reporter: ProgressReporter,
        dryRun: Boolean
    ): List<OperationResult>
}
