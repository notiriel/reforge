package ch.riesennet.reforge.operations.move

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MoveOperationParseSpecTest {

    private val operation = MoveOperation()

    @Test
    fun `parseSpec with valid target and sources`() {
        val raw = mapOf<String, Any>(
            "type" to "move",
            "target" to "com.example.target",
            "sources" to listOf("com.example.Foo", "com.example.Bar")
        )

        val spec = operation.parseSpec(raw) as MoveSpec

        assertEquals("com.example.target", spec.target)
        assertEquals(listOf("com.example.Foo", "com.example.Bar"), spec.sources)
    }

    @Test
    fun `parseSpec with single source`() {
        val raw = mapOf<String, Any>(
            "type" to "move",
            "target" to "com.example.target",
            "sources" to listOf("com.example.Foo")
        )

        val spec = operation.parseSpec(raw) as MoveSpec

        assertEquals(1, spec.sources.size)
    }

    @Test
    fun `parseSpec throws when target is missing`() {
        val raw = mapOf<String, Any>(
            "type" to "move",
            "sources" to listOf("com.example.Foo")
        )

        val ex = assertThrows(IllegalArgumentException::class.java) {
            operation.parseSpec(raw)
        }
        assertEquals("Move operation requires 'target' field", ex.message)
    }

    @Test
    fun `parseSpec throws when sources is missing`() {
        val raw = mapOf<String, Any>(
            "type" to "move",
            "target" to "com.example.target"
        )

        val ex = assertThrows(IllegalArgumentException::class.java) {
            operation.parseSpec(raw)
        }
        assertEquals("Move operation requires 'sources' list", ex.message)
    }

    @Test
    fun `parseSpec throws when sources is not a list`() {
        val raw = mapOf<String, Any>(
            "type" to "move",
            "target" to "com.example.target",
            "sources" to "com.example.Foo"
        )

        val ex = assertThrows(IllegalArgumentException::class.java) {
            operation.parseSpec(raw)
        }
        assertEquals("Move operation requires 'sources' list", ex.message)
    }

    @Test
    fun `parseSpec with wildcard sources`() {
        val raw = mapOf<String, Any>(
            "type" to "move",
            "target" to "com.example.target",
            "sources" to listOf("com.example.model.Task*", "com.example.**.*Repository")
        )

        val spec = operation.parseSpec(raw) as MoveSpec

        assertEquals(listOf("com.example.model.Task*", "com.example.**.*Repository"), spec.sources)
    }
}
