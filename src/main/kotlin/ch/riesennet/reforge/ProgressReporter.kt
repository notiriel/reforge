package ch.riesennet.reforge

/**
 * Reports progress of refactoring operations to the console.
 */
class ProgressReporter {

    private var movedCount = 0
    private var failedCount = 0
    private var skippedCount = 0
    private var deletedPackageCount = 0
    private val failures = mutableListOf<Pair<String, String>>()

    private fun output(message: String) {
        System.err.println(message)
    }

    fun info(message: String) {
        output(message)
    }

    fun section(title: String) {
        output("")
        output(title)
    }

    fun patternResolved(pattern: String, count: Int) {
        output("  $pattern → $count class${if (count != 1) "es" else ""}")
    }

    fun moveSuccess(source: String, target: String) {
        movedCount++
        output("  ✓ $source → $target")
    }

    fun moveFailure(source: String, error: String) {
        failedCount++
        failures.add(source to error)
        output("  ✗ $source (error: $error)")
    }

    fun moveSkipped(source: String, reason: String) {
        skippedCount++
        output("  - $source (skipped: $reason)")
    }

    fun packageDeleted(packageName: String) {
        deletedPackageCount++
        output("  ✓ Removed $packageName")
    }

    fun dryRunMove(source: String, target: String) {
        output("  [dry-run] $source → $target")
    }

    fun operationSuccess(action: String, source: String, target: String) {
        movedCount++
        output("  ✓ [$action] $source → $target")
    }

    fun operationFailure(action: String, source: String, error: String) {
        failedCount++
        failures.add("[$action] $source" to error)
        output("  ✗ [$action] $source (error: $error)")
    }

    fun printSummary() {
        section("Summary:")
        output("  Moved: $movedCount")
        output("  Failed: $failedCount")
        output("  Skipped: $skippedCount")
        output("  Removed packages: $deletedPackageCount")

        if (failures.isNotEmpty()) {
            section("Failures:")
            failures.forEach { (source, error) ->
                output("  - $source: $error")
            }
        }
    }

    fun hasFailures(): Boolean = failedCount > 0

    fun getStats(): Triple<Int, Int, Int> = Triple(movedCount, failedCount, skippedCount)
}
