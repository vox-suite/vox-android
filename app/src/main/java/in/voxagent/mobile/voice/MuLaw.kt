package `in`.voxagent.mobile.voice

object MuLaw {
    fun encode(sample: Short): Byte {
        val sign = if (sample < 0) 0x80 else 0
        val value = minOf(kotlin.math.abs(sample.toInt()), 32635) + 132
        var exponent = 7
        var mask = 0x4000
        while (exponent > 0 && value and mask == 0) {
            exponent--
            mask = mask shr 1
        }
        val mantissa = (value shr (exponent + 3)) and 0xf
        return (sign or (exponent shl 4) or mantissa).inv().toByte()
    }

    fun decode(byte: Byte): Short {
        val value = byte.toInt().inv() and 0xff
        val magnitude = (((value and 0xf) shl 3) + 132) shl ((value shr 4) and 7)
        return (if (value and 0x80 != 0) 132 - magnitude else magnitude - 132).toShort()
    }
}

class MuLawPacketizer {
    private var first: Short? = null
    private var frame = ByteArray(160)
    private var offset = 0

    fun accept(samples: ShortArray): List<ByteArray> {
        val result = mutableListOf<ByteArray>()
        for (sample in samples) {
            val previous = first
            if (previous == null) first = sample
            else {
                frame[offset++] = MuLaw.encode(((previous.toInt() + sample.toInt()) / 2).toShort())
                first = null
                if (offset == frame.size) {
                    result.add(frame)
                    frame = ByteArray(160)
                    offset = 0
                }
            }
        }
        return result
    }

    fun clear() {
        first = null
        offset = 0
        frame = ByteArray(160)
    }
}
