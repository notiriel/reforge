package ch.riesennet.reforge.infrastructure

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFileManager

/**
 * Utilities for VFS synchronization and document saving.
 */
object VfsHelper {

    /**
     * Synchronously refreshes the VFS to flush changes to/from disk.
     * Must be called from a non-EDT thread (wraps in invokeAndWait).
     */
    fun syncRefresh() {
        ApplicationManager.getApplication().invokeAndWait {
            VirtualFileManager.getInstance().syncRefresh()
        }
    }

    /**
     * Saves all in-memory documents and syncs VFS.
     * Must be called from a non-EDT thread (wraps in invokeAndWait).
     */
    fun saveAllAndSync() {
        ApplicationManager.getApplication().invokeAndWait {
            FileDocumentManager.getInstance().saveAllDocuments()
            VirtualFileManager.getInstance().syncRefresh()
        }
    }
}
