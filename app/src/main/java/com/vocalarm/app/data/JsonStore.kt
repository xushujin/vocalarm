package com.vocalarm.app.data

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Small JSON file store with atomic replacement. A partially written file can never be observed,
 * which matters because alarms are restored after a reboot without any user interaction.
 */
class JsonStore(private val file: File) {

    val exists: Boolean get() = file.isFile

    fun read(): String? {
        if (!file.isFile) return null
        return runCatching { file.readText(Charsets.UTF_8) }.getOrElse { null }
    }

    fun write(content: String) {
        val parent = file.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()
        val temp = File(parent, "${file.name}.tmp")
        FileOutputStream(temp).use { output ->
            output.write(content.toByteArray(Charsets.UTF_8))
            output.flush()
            output.fd.sync()
        }
        try {
            Files.move(
                temp.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /**
     * Moves unreadable content aside instead of deleting it, so a user can always recover the file
     * even if a future schema or a corrupted write is encountered.
     */
    fun moveToBackup(reason: String): File? {
        if (!file.isFile) return null
        val stamp = System.currentTimeMillis()
        val backup = File(file.parentFile, "${file.name}.$reason-$stamp.bak")
        return runCatching { Files.move(file.toPath(), backup.toPath()) }.getOrNull()?.let { backup }
    }
}
