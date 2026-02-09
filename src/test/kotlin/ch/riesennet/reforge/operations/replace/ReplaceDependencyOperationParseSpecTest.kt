package ch.riesennet.reforge.operations.replace

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ReplaceDependencyOperationParseSpecTest {

    private val operation = ReplaceDependencyOperation()

    @Test
    fun `parseSpec with valid fields`() {
        val raw = mapOf<String, Any>(
            "type" to "replace-dependency",
            "in" to "com.example.TaskController",
            "replace" to "com.example.TaskService",
            "with" to "com.example.TaskPort"
        )

        val spec = operation.parseSpec(raw) as ReplaceDependencySpec

        assertEquals("com.example.TaskController", spec.inClass)
        assertEquals("com.example.TaskService", spec.replace)
        assertEquals("com.example.TaskPort", spec.with)
    }

    @Test
    fun `parseSpec throws when in is missing`() {
        val raw = mapOf<String, Any>(
            "type" to "replace-dependency",
            "replace" to "com.example.TaskService",
            "with" to "com.example.TaskPort"
        )

        val ex = assertThrows(IllegalArgumentException::class.java) {
            operation.parseSpec(raw)
        }
        assertEquals("replace-dependency requires 'in' field", ex.message)
    }

    @Test
    fun `parseSpec throws when replace is missing`() {
        val raw = mapOf<String, Any>(
            "type" to "replace-dependency",
            "in" to "com.example.TaskController",
            "with" to "com.example.TaskPort"
        )

        val ex = assertThrows(IllegalArgumentException::class.java) {
            operation.parseSpec(raw)
        }
        assertEquals("replace-dependency requires 'replace' field", ex.message)
    }

    @Test
    fun `parseSpec throws when with is missing`() {
        val raw = mapOf<String, Any>(
            "type" to "replace-dependency",
            "in" to "com.example.TaskController",
            "replace" to "com.example.TaskService"
        )

        val ex = assertThrows(IllegalArgumentException::class.java) {
            operation.parseSpec(raw)
        }
        assertEquals("replace-dependency requires 'with' field", ex.message)
    }
}
