package ch.riesennet.reforge

import ch.riesennet.reforge.infrastructure.IndexingHelper
import ch.riesennet.reforge.infrastructure.ProjectSetup
import ch.riesennet.reforge.infrastructure.VfsHelper
import ch.riesennet.reforge.operation.OperationRegistry
import ch.riesennet.reforge.operation.OperationResult
import ch.riesennet.reforge.operation.ResultStatus
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import java.io.File
import kotlin.system.exitProcess

/**
 * ApplicationStarter entry point for Reforge refactoring engine.
 *
 * Usage: idea reforge <project-path> <config.yaml> [--dry-run]
 */
class ReforgeStarter : ApplicationStarter {

    override val commandName: String = "reforge"

    override val requiredModality: Int = ApplicationStarter.NOT_IN_EDT

    override fun main(args: List<String>) {
        System.err.println("[Reforge] main() called with args: $args")
        val reporter = ProgressReporter()

        try {
            val parsedArgs = parseArgs(args.drop(1)) // Drop command name
            run(parsedArgs, reporter)
            exitProcess(if (reporter.hasFailures()) 1 else 0)
        } catch (e: IllegalArgumentException) {
            System.err.println("Error: ${e.message}")
            printUsage()
            exitProcess(2)
        } catch (e: Exception) {
            System.err.println("Error: ${e.message}")
            e.printStackTrace()
            exitProcess(1)
        }
    }

    private data class Args(
        val projectPath: String,
        val configPath: String,
        val dryRun: Boolean
    )

    private fun parseArgs(args: List<String>): Args {
        if (args.size < 2) {
            throw IllegalArgumentException("Missing required arguments")
        }

        val projectPath = args[0]
        val configPath = args[1]
        val dryRun = args.contains("--dry-run")

        return Args(projectPath, configPath, dryRun)
    }

    private fun printUsage() {
        System.err.println()
        System.err.println("Usage: idea reforge <project-path> <config.yaml> [--dry-run]")
        System.err.println()
        System.err.println("Arguments:")
        System.err.println("  project-path  Path to the IntelliJ project to refactor")
        System.err.println("  config.yaml   Path to the YAML configuration file")
        System.err.println("  --dry-run     Show what would be moved without making changes")
    }

    private fun run(args: Args, reporter: ProgressReporter) {
        val projectFile = File(args.projectPath)
        val configFile = File(args.configPath)

        if (!projectFile.exists()) {
            throw IllegalArgumentException("Project path does not exist: ${args.projectPath}")
        }

        if (!configFile.exists()) {
            throw IllegalArgumentException("Config file does not exist: ${args.configPath}")
        }

        reporter.info("Loading project: ${projectFile.absolutePath}")
        val project = openProject(projectFile)

        try {
            ProjectSetup.ensureProjectJdk(project, reporter)
            ProjectSetup.ensureSourceRoots(project, reporter)
            reporter.info("Waiting for indexing...")
            IndexingHelper.waitForSmartMode(project)

            reporter.info("Parsing config: ${configFile.name}")
            val rawOps = ReforgeConfig.parse(configFile)

            if (rawOps.isEmpty()) {
                reporter.info("No operations found in config")
                return
            }

            if (args.dryRun) {
                reporter.info("DRY RUN MODE - no changes will be made")
            }

            // Group consecutive same-type operations into batches
            val batches = groupIntoBatches(rawOps)
            val allResults = mutableListOf<OperationResult>()

            for (batch in batches) {
                val operation = OperationRegistry.get(batch.type)

                // Parse raw entries into typed specs
                val specs = batch.entries.map { operation.parseSpec(it.fields) }

                // Wait for indexing before each batch
                IndexingHelper.waitForSmartMode(project)

                // Execute the batch
                val results = operation.execute(project, specs, reporter, args.dryRun)
                allResults.addAll(results)

                // Save and sync after each batch
                if (!args.dryRun) {
                    VfsHelper.saveAllAndSync()
                }
            }

            // Print summary
            reporter.section("Summary:")
            val successCount = allResults.count { it.status == ResultStatus.SUCCESS }
            val failedCount = allResults.count { it.status == ResultStatus.FAILED }
            val skippedCount = allResults.count { it.status == ResultStatus.SKIPPED }
            reporter.info("  Succeeded: $successCount")
            reporter.info("  Failed: $failedCount")
            reporter.info("  Skipped: $skippedCount")

            val failures = allResults.filter { it.status == ResultStatus.FAILED }
            if (failures.isNotEmpty()) {
                reporter.section("Failures:")
                for (f in failures) {
                    reporter.info("  - [${f.action}] ${f.source}: ${f.error}")
                }
            }

        } finally {
            reporter.info("")
            reporter.info("Closing project...")
            closeProject(project)
        }
    }

    private data class Batch(val type: String, val entries: List<RawOperation>)

    /**
     * Groups consecutive same-type operations into batches.
     * This preserves ordering while allowing same-type optimizations
     * (e.g., multi-pass resolve for moves).
     */
    private fun groupIntoBatches(ops: List<RawOperation>): List<Batch> {
        if (ops.isEmpty()) return emptyList()

        val batches = mutableListOf<Batch>()
        var currentType = ops.first().type
        var currentEntries = mutableListOf(ops.first())

        for (op in ops.drop(1)) {
            if (op.type == currentType) {
                currentEntries.add(op)
            } else {
                batches.add(Batch(currentType, currentEntries))
                currentType = op.type
                currentEntries = mutableListOf(op)
            }
        }
        batches.add(Batch(currentType, currentEntries))

        return batches
    }

    private fun openProject(projectFile: File): Project {
        val projectManager = ProjectManager.getInstance()
        return projectManager.loadAndOpenProject(projectFile.absolutePath)
            ?: throw IllegalStateException("Failed to open project: ${projectFile.absolutePath}")
    }

    private fun closeProject(project: Project) {
        ApplicationManager.getApplication().invokeAndWait {
            ProjectManager.getInstance().closeAndDispose(project)
        }
    }
}
