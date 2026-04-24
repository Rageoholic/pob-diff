package storage

interface Storable {
    fun toBytes(): ByteArray
}

interface Decoder<T> {
    fun fromBytes(bytes: ByteArray): T
}
