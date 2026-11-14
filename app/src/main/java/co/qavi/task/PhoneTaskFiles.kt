package co.qavi.task

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import co.qavi.cap.sandbox.AndroidSandbox
import co.qavi.cap.sandbox.Attachments
import co.qavi.cap.sandbox.SandboxDirs
import co.qavi.cap.sandbox.SandboxState
import co.qavi.chat.SharedFile
import co.qavi.core.agent.AttachmentLines
import co.qavi.core.remote.PendingUploads
import co.qavi.core.remote.RemoteTask
import co.qavi.core.remote.RemoteTaskError
import co.qavi.core.remote.RemoteTaskFiles
import co.qavi.core.remote.TaskFile
import co.qavi.core.remote.Upload
import co.qavi.core.workspace.SandboxError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.util.UUID

internal fun SharedFile.remoteFile() = TaskFile(UUID.nameUUIDFromBytes(uri.toByteArray(Charsets.UTF_8)).toString(), name, size, sha256)

/** Incoming files use the chat inbox gate. Outgoing reads resolve the task's exported MediaStore items only. */
internal class PhoneTaskFiles(private val context: Context, private val exported: (String) -> List<SharedFile>) : RemoteTaskFiles {
    override val uploads by lazy { PendingUploads(File(context.cacheDir, "remote-uploads"), Attachments.MIN_FREE) }

    override fun start(conversation: String, goal: String, files: List<Upload>, launch: (String) -> RemoteTask): RemoteTask {
        val dirs = SandboxDirs(context)
        val sandbox = AndroidSandbox(context, SandboxState.get(context))
        val bytes = files.sumOf { it.size }
        if (sandbox.freeBytes() - bytes < Attachments.MIN_FREE || sandbox.workspaceBytes(conversation) + bytes > Attachments.WORKSPACE_MAX)
            throw IOException("Not enough space for attachments")
        return runBlocking(Dispatchers.IO) {
            try {
                Attachments.withTaskFiles(files.map { it.name to it.path }, dirs.workspace(conversation), dirs.gate, dirs.tree,
                    { sandbox.freeBytes() >= Attachments.MIN_FREE && sandbox.workspaceBytes(conversation) <= Attachments.WORKSPACE_MAX }) { stored ->
                    launch(AttachmentLines.composeBytes(goal, stored))
                }
            } catch (e: SandboxError) { throw IOException("Cannot import into this workspace", e) }
        }
    }

    override fun read(taskId: String, file: TaskFile, offset: Long): ByteArray {
        val item = exported(taskId).firstOrNull { it.remoteFile().id == file.id }
            ?: throw RemoteTaskError.General(RemoteTaskError.Code.NOT_FOUND, "No such result file")
        val descriptor = try { context.contentResolver.openFileDescriptor(Uri.parse(item.uri), "r") }
            catch (e: SecurityException) { throw IOException("Export is no longer readable", e) }
        descriptor ?: throw IOException("Export is unavailable")
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
            if (input.channel.size() != file.size) throw IOException("Export size changed")
            input.channel.position(offset)
            return ByteArray(minOf(file.size - offset, PendingUploads.CHUNK_BYTES.toLong()).toInt()).also {
                DataInputStream(input).readFully(it)
            }
        }
    }
}
