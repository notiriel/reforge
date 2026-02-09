package ch.riesennet.reforge

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ReforgeConfigTest {

    @TempDir
    lateinit var tempDir: File

    private fun writeConfig(yaml: String): File {
        val file = File(tempDir, "config.yaml")
        file.writeText(yaml)
        return file
    }

    @Test
    fun `parse valid YAML with single move operation`() {
        val file = writeConfig("""
            operations:
              - type: move
                target: com.example.target
                sources:
                  - com.example.source.Foo
        """.trimIndent())

        val ops = ReforgeConfig.parse(file)

        assertEquals(1, ops.size)
        assertEquals("move", ops[0].type)
        assertEquals("com.example.target", ops[0].fields["target"])
        @Suppress("UNCHECKED_CAST")
        val sources = ops[0].fields["sources"] as List<String>
        assertEquals(listOf("com.example.source.Foo"), sources)
    }

    @Test
    fun `parse valid YAML with multiple operations`() {
        val file = writeConfig("""
            operations:
              - type: move
                target: com.example.target
                sources:
                  - com.example.source.Foo
              - type: extract-interface
                class: com.example.MyClass
                interface: com.example.MyInterface
                methods: [doA, doB]
              - type: replace-dependency
                in: com.example.Consumer
                replace: com.example.MyClass
                with: com.example.MyInterface
        """.trimIndent())

        val ops = ReforgeConfig.parse(file)

        assertEquals(3, ops.size)
        assertEquals("move", ops[0].type)
        assertEquals("extract-interface", ops[1].type)
        assertEquals("replace-dependency", ops[2].type)
    }

    @Test
    fun `parse throws when operations key is missing`() {
        val file = writeConfig("""
            something_else:
              - type: move
        """.trimIndent())

        val ex = assertThrows(IllegalArgumentException::class.java) {
            ReforgeConfig.parse(file)
        }
        assertEquals("Config must contain 'operations' list", ex.message)
    }

    @Test
    fun `parse throws when operations is not a list`() {
        val file = writeConfig("""
            operations:
              key: value
        """.trimIndent())

        val ex = assertThrows(IllegalArgumentException::class.java) {
            ReforgeConfig.parse(file)
        }
        assertEquals("Config must contain 'operations' list", ex.message)
    }

    @Test
    fun `parse throws when operation entry is not a map`() {
        val file = writeConfig("""
            operations:
              - just a string
        """.trimIndent())

        val ex = assertThrows(IllegalArgumentException::class.java) {
            ReforgeConfig.parse(file)
        }
        assertEquals("Each operation must be a map", ex.message)
    }

    @Test
    fun `parse throws when type field is missing`() {
        val file = writeConfig("""
            operations:
              - target: com.example.target
                sources:
                  - com.example.source.Foo
        """.trimIndent())

        val ex = assertThrows(IllegalArgumentException::class.java) {
            ReforgeConfig.parse(file)
        }
        assertEquals("Each operation must have a 'type' field", ex.message)
    }

    @Test
    fun `parse preserves all fields in the raw map`() {
        val file = writeConfig("""
            operations:
              - type: move
                target: com.example.target
                sources:
                  - com.example.A
                  - com.example.B
        """.trimIndent())

        val ops = ReforgeConfig.parse(file)
        val fields = ops[0].fields

        assertEquals("move", fields["type"])
        assertEquals("com.example.target", fields["target"])
        @Suppress("UNCHECKED_CAST")
        assertEquals(listOf("com.example.A", "com.example.B"), fields["sources"] as List<String>)
    }
}
