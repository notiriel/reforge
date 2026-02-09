package ch.riesennet.reforge.operation

/**
 * Result of a single refactoring action within an operation.
 */
data class OperationResult(
    val action: String,
    val source: String,
    val target: String,
    val status: ResultStatus,
    val error: String? = null
)

enum class ResultStatus { SUCCESS, FAILED, SKIPPED }
