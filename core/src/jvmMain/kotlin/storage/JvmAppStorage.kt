package storage

import dev.dirs.ProjectDirectories
import java.io.File

private val projectDirs = ProjectDirectories.from("com.parengus", "", "epoch-path-builder")

private class FileStorageBucket(val root: File) : StorageBucket {
    override suspend fun read(key: String): ByteArray? {
        val file = File(root, "$key.bin")
        return if (file.exists()) file.readBytes() else null
    }

    override suspend fun write(key: String, data: ByteArray) {
        val file = File(root, "$key.bin")
        file.parentFile.mkdirs()
        file.writeBytes(data)
    }
}

private class JvmAppStorage : AppStorage {
    override val cache = FileStorageBucket(File(projectDirs.cacheDir))
    override val persistent = FileStorageBucket(File(projectDirs.dataDir))
}

actual fun appStorage(): AppStorage = JvmAppStorage()
