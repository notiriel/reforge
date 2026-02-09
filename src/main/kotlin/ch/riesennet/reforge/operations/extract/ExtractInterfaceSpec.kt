package ch.riesennet.reforge.operations.extract

import ch.riesennet.reforge.operation.OperationSpec

/**
 * Specification for an extract-interface operation.
 *
 * @param sourceClass Fully qualified name of the class to extract from
 * @param interfaceName Fully qualified name of the interface to create
 * @param methods List of method names to extract into the interface
 */
data class ExtractInterfaceSpec(
    val sourceClass: String,
    val interfaceName: String,
    val methods: List<String>
) : OperationSpec
