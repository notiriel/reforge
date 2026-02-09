package ch.riesennet.reforge.infrastructure

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager

/**
 * Utilities for working with IntelliJ's indexing system in headless mode.
 */
object IndexingHelper {

    /**
     * Waits for indexing to complete with VFS refresh and stabilization delay.
     * Must NOT be called from the EDT.
     */
    fun waitForSmartMode(project: Project) {
        // Refresh VFS to pick up any file changes before indexing
        ApplicationManager.getApplication().invokeAndWait {
            VirtualFileManager.getInstance().syncRefresh()
        }
        DumbService.getInstance(project).waitForSmartMode()
        // Small delay to let the index stabilize after smart mode is entered
        Thread.sleep(500)
        // Re-check smart mode in case indexing restarted
        DumbService.getInstance(project).waitForSmartMode()
    }

    /**
     * Checks if an exception (or any cause in its chain) is an IndexNotReadyException.
     */
    fun isIndexNotReadyException(e: Throwable): Boolean {
        var current: Throwable? = e
        while (current != null) {
            if (current is com.intellij.openapi.project.IndexNotReadyException) return true
            current = current.cause
        }
        return false
    }
}
