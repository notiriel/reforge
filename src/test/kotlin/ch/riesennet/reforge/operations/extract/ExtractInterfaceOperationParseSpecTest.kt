package ch.riesennet.reforge.operations.extract

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ExtractInterfaceOperationParseSpecTest {

    private val operation = ExtractInterfaceOperation()

    @Test
    fun `parseSpec with valid fields`() {
        val raw = mapOf<String, Any>(
            "type" to "extract-interface",
            "class" to "com.example.TaskService",
            "interface" to "com.example.TaskPort",
            "methods" to listOf("findAll", "findById", "createTask")
        )

        val spec = operation.parseSpec(raw) as ExtractInterfaceSpec

        assertEquals("com.example.TaskService", spec.sourceClass)
        assertEquals("com.example.TaskPort", spec.interfaceName)
        assertEquals(listOf("findAll", "findById", "createTask"), spec.methods)
    }

    @Test
    fun `parseSpec throws when class is missing`() {
        val raw = mapOf<String, Any>(
            "type" to "extract-interface",
            "interface" to "com.example.TaskPort",
            "methods" to listOf("findAll")
        )

        val ex = assertThrows(IllegalArgumentException::class.java) {
            operation.parseSpec(raw)
        }
        assertEquals("extract-interface requires 'class' field", ex.message)
    }

    @Test
    fun `parseSpec throws when interface is missing`() {
        val raw = mapOf<String, Any>(
            "type" to "extract-interface",
            "class" to "com.example.TaskService",
            "methods" to listOf("findAll")
        )

        val ex = assertThrows(IllegalArgumentException::class.java) {
            operation.parseSpec(raw)
        }
        assertEquals("extract-interface requires 'interface' field", ex.message)
    }

    @Test
    fun `parseSpec throws when methods is missing`() {
        val raw = mapOf<String, Any>(
            "type" to "extract-interface",
            "class" to "com.example.TaskService",
            "interface" to "com.example.TaskPort"
        )

        val ex = assertThrows(IllegalArgumentException::class.java) {
            operation.parseSpec(raw)
        }
        assertEquals("extract-interface requires 'methods' list", ex.message)
    }

    @Test
    fun `parseSpec throws when methods is not a list`() {
        val raw = mapOf<String, Any>(
            "type" to "extract-interface",
            "class" to "com.example.TaskService",
            "interface" to "com.example.TaskPort",
            "methods" to "findAll"
        )

        val ex = assertThrows(IllegalArgumentException::class.java) {
            operation.parseSpec(raw)
        }
        assertEquals("extract-interface requires 'methods' list", ex.message)
    }

    @Test
    fun `parseSpec with single method`() {
        val raw = mapOf<String, Any>(
            "type" to "extract-interface",
            "class" to "com.example.TaskService",
            "interface" to "com.example.TaskPort",
            "methods" to listOf("findAll")
        )

        val spec = operation.parseSpec(raw) as ExtractInterfaceSpec

        assertEquals(1, spec.methods.size)
        assertEquals("findAll", spec.methods[0])
    }
}
