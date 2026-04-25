package storage

interface AppStorage {
    val cache: StorageBucket
    val persistent: StorageBucket
    suspend fun recover()
}

interface StorageBucket {
    suspend fun read(key: String): ByteArray?
    suspend fun write(key: String, data: ByteArray)
    suspend fun list(prefix: String): List<String>
    suspend fun delete(key: String)
    suspend fun rename(fromKey: String, toKey: String)
}

suspend fun <T : Storable> StorageBucket.write(key: String, value: T) =
    write(key, value.toBytes())

suspend fun <T> StorageBucket.read(key: String, decoder: Decoder<T>): T? =
    read(key)?.let { decoder.fromBytes(it) }

expect fun appStorage(): AppStorage
