package com.possatstack.app.wallet.signer.tapsigner

/**
 * Minimal CBOR (RFC 8949) encoder/decoder covering the subset used by the
 * Coinkite TAPSIGNER tap-protocol: unsigned ints, negative ints, byte
 * strings, text strings, arrays, and maps with text keys.
 *
 * Kept intentionally small so there is no new runtime dependency. If later
 * phases require tags, floats, or indefinite-length items, replace this
 * with a proper library — the protocol layer is the only caller.
 */
internal object Cbor {
    private const val MAJOR_UINT: Int = 0x00
    private const val MAJOR_NEG_INT: Int = 0x20
    private const val MAJOR_BYTE_STRING: Int = 0x40
    private const val MAJOR_TEXT_STRING: Int = 0x60
    private const val MAJOR_ARRAY: Int = 0x80
    private const val MAJOR_MAP: Int = 0xA0

    fun encode(value: Any?): ByteArray {
        val buffer = ArrayList<Byte>(64)
        write(buffer, value)
        return buffer.toByteArray()
    }

    fun decode(bytes: ByteArray): Any? {
        val reader = Reader(bytes)
        val result = reader.read()
        require(reader.exhausted()) { "Trailing bytes after CBOR value" }
        return result
    }

    /** Decode a CBOR map-of-string-keys into a read-through helper. */
    fun decodeMap(bytes: ByteArray): Map<String, Any?> {
        val decoded = decode(bytes)
        require(decoded is Map<*, *>) { "Expected CBOR map at root, got ${decoded?.javaClass}" }
        @Suppress("UNCHECKED_CAST")
        return decoded as Map<String, Any?>
    }

    private fun write(
        out: ArrayList<Byte>,
        value: Any?,
    ) {
        when (value) {
            null -> throw IllegalArgumentException("CBOR null not supported in this subset")
            is Int -> writeInt(out, value.toLong())
            is Long -> writeInt(out, value)
            is ByteArray -> {
                writeTypeAndLength(out, MAJOR_BYTE_STRING, value.size.toLong())
                value.forEach { out.add(it) }
            }
            is String -> {
                val encoded = value.toByteArray(Charsets.UTF_8)
                writeTypeAndLength(out, MAJOR_TEXT_STRING, encoded.size.toLong())
                encoded.forEach { out.add(it) }
            }
            is List<*> -> {
                writeTypeAndLength(out, MAJOR_ARRAY, value.size.toLong())
                value.forEach { write(out, it) }
            }
            is Map<*, *> -> {
                writeTypeAndLength(out, MAJOR_MAP, value.size.toLong())
                for ((key, mapValue) in value) {
                    require(key is String) { "CBOR map keys must be strings in this subset" }
                    write(out, key)
                    write(out, mapValue)
                }
            }
            is Boolean -> throw IllegalArgumentException("CBOR bool not supported in this subset")
            else -> throw IllegalArgumentException("Unsupported CBOR value: ${value.javaClass}")
        }
    }

    private fun writeInt(
        out: ArrayList<Byte>,
        value: Long,
    ) {
        if (value >= 0) {
            writeTypeAndLength(out, MAJOR_UINT, value)
        } else {
            writeTypeAndLength(out, MAJOR_NEG_INT, -(value + 1))
        }
    }

    private fun writeTypeAndLength(
        out: ArrayList<Byte>,
        major: Int,
        length: Long,
    ) {
        require(length >= 0) { "Length cannot be negative" }
        when {
            length < 24L -> out.add((major or length.toInt()).toByte())
            length < 0x100L -> {
                out.add((major or 24).toByte())
                out.add(length.toByte())
            }
            length < 0x10000L -> {
                out.add((major or 25).toByte())
                out.add((length shr 8).toByte())
                out.add(length.toByte())
            }
            length < 0x100000000L -> {
                out.add((major or 26).toByte())
                out.add((length shr 24).toByte())
                out.add((length shr 16).toByte())
                out.add((length shr 8).toByte())
                out.add(length.toByte())
            }
            else -> {
                out.add((major or 27).toByte())
                for (shift in intArrayOf(56, 48, 40, 32, 24, 16, 8, 0)) {
                    out.add((length ushr shift).toByte())
                }
            }
        }
    }

    private class Reader(private val bytes: ByteArray) {
        private var cursor: Int = 0

        fun exhausted(): Boolean = cursor >= bytes.size

        fun read(): Any? {
            val head = readByte().toInt() and 0xFF
            val major = head and 0xE0
            val info = head and 0x1F
            return when (major) {
                MAJOR_UINT -> readLength(info)
                MAJOR_NEG_INT -> -(readLength(info) + 1L)
                MAJOR_BYTE_STRING -> readBytes(readLength(info).toInt())
                MAJOR_TEXT_STRING -> String(readBytes(readLength(info).toInt()), Charsets.UTF_8)
                MAJOR_ARRAY -> {
                    val length = readLength(info).toInt()
                    List(length) { read() }
                }
                MAJOR_MAP -> {
                    val length = readLength(info).toInt()
                    val map = LinkedHashMap<String, Any?>(length)
                    repeat(length) {
                        val key = read()
                        require(key is String) { "CBOR map key is not a string" }
                        map[key] = read()
                    }
                    map
                }
                else -> throw IllegalArgumentException(
                    "Unsupported CBOR major type 0x${Integer.toHexString(major)}",
                )
            }
        }

        private fun readLength(info: Int): Long =
            when {
                info < 24 -> info.toLong()
                info == 24 -> (readByte().toLong() and 0xFF)
                info == 25 -> readUnsigned(2)
                info == 26 -> readUnsigned(4)
                info == 27 -> readUnsigned(8)
                else -> throw IllegalArgumentException("Indefinite-length CBOR not supported")
            }

        private fun readUnsigned(count: Int): Long {
            var result = 0L
            repeat(count) {
                result = (result shl 8) or (readByte().toLong() and 0xFF)
            }
            return result
        }

        private fun readByte(): Byte {
            if (cursor >= bytes.size) throw IllegalArgumentException("Unexpected end of CBOR input")
            return bytes[cursor++]
        }

        private fun readBytes(length: Int): ByteArray {
            require(cursor + length <= bytes.size) { "CBOR payload truncated" }
            val slice = bytes.copyOfRange(cursor, cursor + length)
            cursor += length
            return slice
        }
    }
}
