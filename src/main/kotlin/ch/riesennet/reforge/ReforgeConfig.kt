package ch.riesennet.reforge

import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * A raw operation entry from the YAML config, before being parsed
 * into a typed OperationSpec by the corresponding Operation.
 */
data class RawOperation(
    val type: String,
    val fields: Map<String, Any>
)

/**
 * Parses YAML configuration for Reforge operations.
 *
 * Expected format:
 * ```yaml
 * operations:
 *   - type: move
 *     target: com.example.app.task.model
 *     sources:
 *       - com.example.app.model.Task*
 *
 *   - type: extract-interface
 *     class: com.example.app.task.service.TaskService
 *     interface: com.example.app.task.port.TaskPort
 *     methods: [findAll, findById, createTask]
 * ```
 */
object ReforgeConfig {

    fun parse(configFile: File): List<RawOperation> {
        val yaml = Yaml()
        val config = configFile.inputStream().use { stream ->
            @Suppress("UNCHECKED_CAST")
            yaml.load(stream) as Map<String, Any>
        }

        val operations = config["operations"] as? List<*>
            ?: throw IllegalArgumentException("Config must contain 'operations' list")

        return operations.map { entry ->
            @Suppress("UNCHECKED_CAST")
            val map = entry as? Map<String, Any>
                ?: throw IllegalArgumentException("Each operation must be a map")

            val type = map["type"] as? String
                ?: throw IllegalArgumentException("Each operation must have a 'type' field")

            RawOperation(type = type, fields = map)
        }
    }
}
