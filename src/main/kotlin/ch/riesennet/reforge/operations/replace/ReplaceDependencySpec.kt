package ch.riesennet.reforge.operations.replace

import ch.riesennet.reforge.operation.OperationSpec

/**
 * Specification for a replace-dependency operation.
 *
 * @param inClass Fully qualified name of the class to modify
 * @param replace Fully qualified name of the type to replace
 * @param with Fully qualified name of the replacement type
 */
data class ReplaceDependencySpec(
    val inClass: String,
    val replace: String,
    val with: String
) : OperationSpec
