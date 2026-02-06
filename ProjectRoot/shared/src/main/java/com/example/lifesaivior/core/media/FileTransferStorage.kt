package com.example.lifesaivior.core.media

import android.content.Context
import android.webkit.MimeTypeMap
import com.example.lifesaivior.protocol.model.FileTransferPayload
import java.io.File

object FileTransferStorage {
    enum class Kind { VOICE, IMAGE, FILE }

    data class StoredFile(
        val file: File,
        val kind: Kind
    )

    fun storeIncoming(
        context: Context,
        payload: FileTransferPayload,
        timestamp: Long
    ): StoredFile? {
        val kind = classify(payload.mimeType)
        val dir = when (kind) {
            Kind.VOICE -> File(context.filesDir, "voicenotes/incoming")
            Kind.IMAGE -> File(context.filesDir, "images/incoming")
            Kind.FILE -> File(context.filesDir, "files/incoming")
        }
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val baseName = payload.fileName?.takeIf { it.isNotBlank() }
            ?.let { sanitizeFileName(it) }
        val resolvedName = baseName ?: defaultName(kind, payload.mimeType, timestamp)
        val target = uniqueFile(dir, resolvedName)

        return runCatching {
            target.writeBytes(payload.content)
            StoredFile(target, kind)
        }.getOrNull()
    }

    fun buildMarker(storedFile: StoredFile): String {
        return when (storedFile.kind) {
            Kind.VOICE -> "[voice] ${storedFile.file.absolutePath}"
            Kind.IMAGE -> "[image] ${storedFile.file.absolutePath}"
            Kind.FILE -> "[file] ${storedFile.file.absolutePath}"
        }
    }

    private fun classify(mimeType: String?): Kind {
        val lower = mimeType?.lowercase() ?: return Kind.FILE
        return when {
            lower.startsWith("audio/") -> Kind.VOICE
            lower.startsWith("image/") -> Kind.IMAGE
            else -> Kind.FILE
        }
    }

    private fun sanitizeFileName(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return if (cleaned.isBlank()) "file" else cleaned
    }

    private fun defaultName(kind: Kind, mimeType: String?, timestamp: Long): String {
        val prefix = when (kind) {
            Kind.VOICE -> "voice"
            Kind.IMAGE -> "image"
            Kind.FILE -> "file"
        }
        val extension = extensionForMime(mimeType, kind)
        return if (extension.isNotBlank()) {
            "${prefix}_$timestamp.$extension"
        } else {
            "${prefix}_$timestamp"
        }
    }

    private fun extensionForMime(mimeType: String?, kind: Kind): String {
        val sanitized = mimeType?.lowercase()?.trim()
        val ext = sanitized?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        if (!ext.isNullOrBlank()) return ext
        return when (kind) {
            Kind.VOICE -> "m4a"
            Kind.IMAGE -> "jpg"
            Kind.FILE -> "bin"
        }
    }

    private fun uniqueFile(dir: File, filename: String): File {
        val base = filename.substringBeforeLast('.', filename)
        val extension = filename.substringAfterLast('.', "")
        var candidate = if (extension.isNotBlank()) {
            File(dir, "$base.$extension")
        } else {
            File(dir, base)
        }
        var index = 1
        while (candidate.exists()) {
            val suffix = " ($index)"
            candidate = if (extension.isNotBlank()) {
                File(dir, "$base$suffix.$extension")
            } else {
                File(dir, "$base$suffix")
            }
            index += 1
        }
        return candidate
    }
}
