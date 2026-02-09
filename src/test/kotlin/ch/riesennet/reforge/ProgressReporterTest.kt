package ch.riesennet.reforge

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ProgressReporterTest {

    @Test
    fun `initial state has no failures`() {
        val reporter = ProgressReporter()
        assertFalse(reporter.hasFailures())
    }

    @Test
    fun `initial stats are all zero`() {
        val reporter = ProgressReporter()
        val (moved, failed, skipped) = reporter.getStats()
        assertEquals(0, moved)
        assertEquals(0, failed)
        assertEquals(0, skipped)
    }

    @Test
    fun `moveSuccess increments moved count`() {
        val reporter = ProgressReporter()
        reporter.moveSuccess("com.A", "com.B")
        reporter.moveSuccess("com.C", "com.D")

        val (moved, _, _) = reporter.getStats()
        assertEquals(2, moved)
    }

    @Test
    fun `moveFailure increments failed count and records failure`() {
        val reporter = ProgressReporter()
        reporter.moveFailure("com.A", "some error")

        val (_, failed, _) = reporter.getStats()
        assertEquals(1, failed)
        assertTrue(reporter.hasFailures())
    }

    @Test
    fun `moveSkipped increments skipped count`() {
        val reporter = ProgressReporter()
        reporter.moveSkipped("com.A", "already exists")

        val (_, _, skipped) = reporter.getStats()
        assertEquals(1, skipped)
    }

    @Test
    fun `operationSuccess increments moved count`() {
        val reporter = ProgressReporter()
        reporter.operationSuccess("extract-interface", "com.A", "com.B")

        val (moved, _, _) = reporter.getStats()
        assertEquals(1, moved)
    }

    @Test
    fun `operationFailure increments failed count`() {
        val reporter = ProgressReporter()
        reporter.operationFailure("replace-dependency", "com.A", "error")

        val (_, failed, _) = reporter.getStats()
        assertEquals(1, failed)
        assertTrue(reporter.hasFailures())
    }

    @Test
    fun `hasFailures is false when only successes and skips`() {
        val reporter = ProgressReporter()
        reporter.moveSuccess("com.A", "com.B")
        reporter.moveSkipped("com.C", "reason")

        assertFalse(reporter.hasFailures())
    }

    @Test
    fun `mixed operations track correctly`() {
        val reporter = ProgressReporter()
        reporter.moveSuccess("a", "b")
        reporter.moveSuccess("c", "d")
        reporter.moveFailure("e", "err")
        reporter.moveSkipped("f", "skip")

        val (moved, failed, skipped) = reporter.getStats()
        assertEquals(2, moved)
        assertEquals(1, failed)
        assertEquals(1, skipped)
        assertTrue(reporter.hasFailures())
    }
}
