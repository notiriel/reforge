package ch.riesennet.reforge.operations.move

import ch.riesennet.reforge.operation.OperationSpec

/**
 * Specification for a move operation: move classes matching source patterns
 * to a target package.
 */
data class MoveSpec(
    val target: String,
    val sources: List<String>
) : OperationSpec
