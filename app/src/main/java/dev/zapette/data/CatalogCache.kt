package dev.zapette.data

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class CatalogCache(
    private val dir: File,
    private val ttlMillis: Long = TimeUnit.HOURS.toMillis(24),
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val marker = File(dir, ".created")

    @Synchronized
    fun isExpired(): Boolean {
        val created = createdAt() ?: return false
        return now() - created !in 0 until ttlMillis
    }

    @Synchronized
    fun read(key: String): String? {
        if (createdAt() == null || isExpired()) return null
        return fileFor(key).takeIf { it.isFile }?.readText()
    }

    @Synchronized
    fun write(key: String, body: String) {
        if (isExpired()) clear()
        dir.mkdirs()
        if (createdAt() == null) marker.writeText(now().toString())
        val tmp = File(dir, "${fileFor(key).name}.tmp")
        tmp.writeText(body)
        if (!tmp.renameTo(fileFor(key))) {
            fileFor(key).delete()
            tmp.renameTo(fileFor(key))
        }
    }

    @Synchronized
    fun clear() {
        dir.deleteRecursively()
    }

    private fun createdAt(): Long? = marker.takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull()

    private fun fileFor(key: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        return File(dir, hash.joinToString("") { "%02x".format(it) })
    }
}
