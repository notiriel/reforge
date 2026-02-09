package ch.riesennet.reforge.infrastructure

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import ch.riesennet.reforge.ProgressReporter

/**
 * Auto-configures project JDK and source roots for headless mode.
 *
 * In a fresh IntelliJ sandbox, the JDK table may be empty and Maven/Gradle
 * auto-import may not run, leaving the project without source roots. This
 * ensures both are configured before indexing begins.
 */
object ProjectSetup {

    /**
     * Ensures a project JDK is configured. In headless mode, the project may reference
     * a JDK not available in the sandbox. Falls back to the running JVM's JDK.
     */
    fun ensureProjectJdk(project: Project, reporter: ProgressReporter) {
        val rootManager = ProjectRootManager.getInstance(project)
        if (rootManager.projectSdk != null) {
            reporter.info("Project JDK: ${rootManager.projectSdk!!.name}")
            return
        }

        val javaHome = System.getProperty("java.home")
            ?: throw IllegalStateException("Cannot determine JAVA_HOME")

        reporter.info("Project JDK not found, configuring from running JVM: $javaHome")

        val javaSdkType = com.intellij.openapi.projectRoots.JavaSdk.getInstance()
        val sdk = javaSdkType.createJdk("auto-jdk", javaHome, false)

        ApplicationManager.getApplication().invokeAndWait {
            WriteCommandAction.writeCommandAction(project).run<Exception> {
                val jdkTable = com.intellij.openapi.projectRoots.ProjectJdkTable.getInstance()
                jdkTable.addJdk(sdk)
                rootManager.projectSdk = sdk
            }
        }
    }

    /**
     * Ensures source roots are configured. In headless mode, Maven/Gradle auto-import
     * may not run, leaving the project without source roots.
     */
    fun ensureSourceRoots(project: Project, reporter: ProgressReporter) {
        // Give auto-import a chance to run first
        Thread.sleep(2000)
        ApplicationManager.getApplication().invokeAndWait {
            VirtualFileManager.getInstance().syncRefresh()
        }

        val rootManager = ProjectRootManager.getInstance(project)
        if (rootManager.contentSourceRoots.isNotEmpty()) {
            reporter.info("Source roots: ${rootManager.contentSourceRoots.size} configured")
            for (root in rootManager.contentSourceRoots) {
                reporter.info("  ${root.path}")
            }
            return
        }

        reporter.info("No source roots detected, configuring from project layout...")

        val projectDir = LocalFileSystem.getInstance()
            .findFileByPath(project.basePath!!) ?: return

        ApplicationManager.getApplication().invokeAndWait {
            WriteCommandAction.writeCommandAction(project).run<Exception> {
                val moduleManager = ModuleManager.getInstance(project)
                val module = moduleManager.modules.firstOrNull()
                    ?: moduleManager.newModule(
                        project.basePath + "/project.iml",
                        "JAVA_MODULE"
                    )

                val modifiableModel = ModuleRootManager.getInstance(module).modifiableModel

                val contentEntry = modifiableModel.contentEntries.firstOrNull()
                    ?: modifiableModel.addContentEntry(projectDir)

                // Add standard Maven/Gradle source directories if they exist
                val sourceDirs = listOf(
                    "src/main/java" to false,
                    "src/test/java" to true
                )

                for ((path, isTest) in sourceDirs) {
                    val dir = projectDir.findFileByRelativePath(path)
                    if (dir != null) {
                        contentEntry.addSourceFolder(dir, isTest)
                        reporter.info("  Added source root: $path (test=$isTest)")
                    }
                }

                modifiableModel.commit()
            }
        }
    }
}
