package ch.riesennet.reforge.operation

import ch.riesennet.reforge.operations.extract.ExtractInterfaceOperation
import ch.riesennet.reforge.operations.move.MoveOperation
import ch.riesennet.reforge.operations.replace.ReplaceDependencyOperation

/**
 * Registry mapping operation type strings to Operation implementations.
 */
object OperationRegistry {

    private val operations: Map<String, Operation> = listOf(
        MoveOperation(),
        ExtractInterfaceOperation(),
        ReplaceDependencyOperation()
    ).associateBy { it.type }

    fun get(type: String): Operation =
        operations[type] ?: throw IllegalArgumentException("Unknown operation type: '$type'. Known types: ${operations.keys}")

    fun knownTypes(): Set<String> = operations.keys
}
