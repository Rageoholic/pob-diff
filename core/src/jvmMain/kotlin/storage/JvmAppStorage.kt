package storage

import dev.dirs.ProjectDirectories
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.CRC32

private val projectDirs = ProjectDirectories.from("com.parengus", "", "epoch-path-builder")
private val intentJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class RenameIntent(val fromKey: String, val toKey: String)

private const val RENAME_INTENT_KEY = "__intent__/rename"

private fun encode(data: ByteArray): ByteArray {
    val crc = CRC32().also { it.update(data) }.value
    return ByteBuffer.allocate(4 + data.size)
        .putInt(crc.toInt())
        .put(data)
        .array()
}

private fun decode(bytes: ByteArray): ByteArray? {
    if (bytes.size < 4) return null
    val buf = ByteBuffer.wrap(bytes)
    val storedCrc = buf.int.toLong() and 0xFFFFFFFFL
    val payload = bytes.copyOfRange(4, bytes.size)
    val actualCrc = CRC32().also { it.update(payload) }.value
    return if (storedCrc == actualCrc) payload else null
}

private class FileStorageBucket(val root: File) : StorageBucket {
    override suspend fun read(key: String): ByteArray? =
        tryDecode(File(root, "$key.bin")) ?: tryDecode(File(root, "$key.bak"))

    override suspend fun list(prefix: String): List<String> {
        val dir = File(root, prefix)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".bin") }
            ?.map { "$prefix/${it.nameWithoutExtension}" }
            ?: emptyList()
    }

    override suspend fun write(key: String, data: ByteArray) {
        val file = File(root, "$key.bin")
        val bak = File(root, "$key.bak")
        val tmp = File(root, "$key.tmp")
        file.parentFile?.mkdirs()
        tmp.writeBytes(encode(data))
        if (file.exists()) file.renameTo(bak)
        tmp.renameTo(file)
    }

    override suspend fun delete(key: String) {
        File(root, "$key.bin").delete()
        File(root, "$key.bak").delete()
    }

    override suspend fun rename(fromKey: String, toKey: String) {
        // Write intent before touching any data files.
        val intent = intentJson.encodeToString(RenameIntent.serializer(), RenameIntent(fromKey, toKey))
        write(RENAME_INTENT_KEY, intent.encodeToByteArray())
        executeRename(fromKey, toKey)
        delete(RENAME_INTENT_KEY)
    }

    // Replays the rename atomically. Safe to call multiple times — renameTo is a no-op
    // if the source file no longer exists.
    fun executeRename(fromKey: String, toKey: String) {
        for (ext in listOf("bin", "bak")) {
            val from = File(root, "$fromKey.$ext")
            val to = File(root, "$toKey.$ext")
            if (from.exists()) {
                to.parentFile?.mkdirs()
                from.renameTo(to)
            }
        }
    }

    suspend fun replayPendingRename() {
        val bytes = read(RENAME_INTENT_KEY) ?: return
        val intent = runCatching {
            intentJson.decodeFromString(RenameIntent.serializer(), bytes.decodeToString())
        }.getOrNull() ?: return
        executeRename(intent.fromKey, intent.toKey)
        delete(RENAME_INTENT_KEY)
    }

    private fun tryDecode(file: File): ByteArray? =
        if (file.exists()) decode(file.readBytes()) else null
}

private class JvmAppStorage : AppStorage {
    override val cache = FileStorageBucket(File(projectDirs.cacheDir))
    override val persistent = FileStorageBucket(File(projectDirs.dataDir))

    override suspend fun recover() {
        persistent.replayPendingRename()
    }
}

private val instance by lazy { JvmAppStorage() }
actual fun appStorage(): AppStorage = instance
