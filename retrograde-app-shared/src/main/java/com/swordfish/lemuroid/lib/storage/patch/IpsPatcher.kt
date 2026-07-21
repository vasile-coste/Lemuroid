package com.swordfish.lemuroid.lib.storage.patch

/** Applies IPS patches. See https://zerosoft.zophar.net/ips.php for the format spec. */
object IpsPatcher {
    private val HEADER = "PATCH".toByteArray(Charsets.US_ASCII)
    private val EOF_MARKER = "EOF".toByteArray(Charsets.US_ASCII)

    fun apply(
        original: ByteArray,
        patch: ByteArray,
    ): ByteArray {
        require(patch.size >= HEADER.size + EOF_MARKER.size) { "IPS patch is too small to be valid" }
        require(patch.copyOfRange(0, HEADER.size).contentEquals(HEADER)) { "Invalid IPS header" }

        var output = original.copyOf()
        var offset = HEADER.size

        fun ensureCapacity(minSize: Int) {
            if (output.size < minSize) {
                output = output.copyOf(minSize)
            }
        }

        fun readUInt24(pos: Int): Int {
            return ((patch[pos].toInt() and 0xFF) shl 16) or
                ((patch[pos + 1].toInt() and 0xFF) shl 8) or
                (patch[pos + 2].toInt() and 0xFF)
        }

        fun readUInt16(pos: Int): Int {
            return ((patch[pos].toInt() and 0xFF) shl 8) or (patch[pos + 1].toInt() and 0xFF)
        }

        while (true) {
            require(offset + 3 <= patch.size) { "Truncated IPS patch" }

            if (patch.copyOfRange(offset, offset + 3).contentEquals(EOF_MARKER)) {
                offset += 3
                if (offset + 3 <= patch.size) {
                    val truncatedSize = readUInt24(offset)
                    ensureCapacity(truncatedSize)
                    output = output.copyOf(truncatedSize)
                }
                break
            }

            val recordOffset = readUInt24(offset)
            offset += 3

            require(offset + 2 <= patch.size) { "Truncated IPS patch" }
            val size = readUInt16(offset)
            offset += 2

            if (size == 0) {
                require(offset + 3 <= patch.size) { "Truncated IPS RLE record" }
                val runLength = readUInt16(offset)
                offset += 2
                val fillByte = patch[offset]
                offset += 1

                ensureCapacity(recordOffset + runLength)
                for (i in 0 until runLength) {
                    output[recordOffset + i] = fillByte
                }
            } else {
                require(offset + size <= patch.size) { "Truncated IPS literal record" }
                ensureCapacity(recordOffset + size)
                patch.copyInto(output, recordOffset, offset, offset + size)
                offset += size
            }
        }

        return output
    }
}
