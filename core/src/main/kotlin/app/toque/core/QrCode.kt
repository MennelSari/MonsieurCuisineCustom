package app.toque.core

/**
 * Générateur de QR code minimal (norme ISO/IEC 18004), sans bibliothèque : mode octets, correction
 * d'erreur M, versions 1 à 6 (jusqu'à 106 octets, largement assez pour une adresse locale),
 * masque 0. D'après l'algorithme de référence de Project Nayuki (licence MIT).
 */
class QrCode private constructor(val size: Int, private val modules: Array<BooleanArray>) {
    /** Vrai si le module (x, y) est noir. */
    fun dark(x: Int, y: Int): Boolean = modules[y][x]

    companion object {
        private val ECC_PER_BLOCK_M = intArrayOf(-1, 10, 16, 26, 18, 24, 16)
        private val BLOCKS_M = intArrayOf(-1, 1, 1, 1, 2, 2, 4)
        private const val FORMAT_ECC_M = 0
        private const val MASK = 0

        /** QR code du texte (UTF-8), ou exception si trop long. */
        fun encode(text: String): QrCode {
            val data = text.toByteArray(Charsets.UTF_8)
            var version = 1
            while (version <= 6 && 4 + 8 + data.size * 8 > dataCodewords(version) * 8) version++
            require(version <= 6) { "texte trop long pour le QR code" }
            val cap = dataCodewords(version) * 8
            val bits = ArrayList<Boolean>()
            fun put(v: Int, n: Int) { for (i in n - 1 downTo 0) bits += (v ushr i) and 1 != 0 }
            put(0b0100, 4)
            put(data.size, 8)
            for (b in data) put(b.toInt() and 0xFF, 8)
            put(0, minOf(4, cap - bits.size))
            put(0, (8 - bits.size % 8) % 8)
            var pad = 0xEC
            while (bits.size < cap) { put(pad, 8); pad = pad xor 0xEC xor 0x11 }
            val codewords = ByteArray(bits.size / 8) { i -> (0 until 8).fold(0) { acc, k -> (acc shl 1) or (if (bits[i * 8 + k]) 1 else 0) }.toByte() }
            return build(version, interleave(version, codewords))
        }

        private fun rawModules(ver: Int): Int {
            var r = (16 * ver + 128) * ver + 64
            if (ver >= 2) {
                val n = ver / 7 + 2
                r -= (25 * n - 10) * n - 55
                if (ver >= 7) r -= 36
            }
            return r
        }

        private fun dataCodewords(ver: Int) = rawModules(ver) / 8 - ECC_PER_BLOCK_M[ver] * BLOCKS_M[ver]

        private fun mul(x: Int, y: Int): Int {
            var z = 0
            for (i in 7 downTo 0) {
                z = (z shl 1) xor ((z ushr 7) * 0x11D)
                z = z xor (((y ushr i) and 1) * x)
            }
            return z
        }

        private fun divisor(degree: Int): IntArray {
            val r = IntArray(degree); r[degree - 1] = 1
            var root = 1
            for (i in 0 until degree) {
                for (j in r.indices) {
                    r[j] = mul(r[j], root)
                    if (j + 1 < r.size) r[j] = r[j] xor r[j + 1]
                }
                root = mul(root, 0x02)
            }
            return r
        }

        private fun remainder(data: ByteArray, div: IntArray): IntArray {
            val r = IntArray(div.size)
            for (b in data) {
                val factor = (b.toInt() and 0xFF) xor r[0]
                System.arraycopy(r, 1, r, 0, r.size - 1); r[r.size - 1] = 0
                for (i in r.indices) r[i] = r[i] xor mul(div[i], factor)
            }
            return r
        }

        private fun interleave(ver: Int, data: ByteArray): ByteArray {
            val numBlocks = BLOCKS_M[ver]
            val eccLen = ECC_PER_BLOCK_M[ver]
            val raw = rawModules(ver) / 8
            val numShort = numBlocks - raw % numBlocks
            val shortLen = raw / numBlocks
            val div = divisor(eccLen)
            val blocks = ArrayList<IntArray>()
            var k = 0
            for (i in 0 until numBlocks) {
                val len = shortLen - eccLen + if (i < numShort) 0 else 1
                val dat = data.copyOfRange(k, k + len); k += len
                val block = IntArray(shortLen + 1)
                for (j in dat.indices) block[j] = dat[j].toInt() and 0xFF
                val ecc = remainder(dat, div)
                System.arraycopy(ecc, 0, block, block.size - eccLen, eccLen)
                blocks += block
            }
            val out = ArrayList<Byte>()
            for (i in 0 until blocks[0].size) for (j in blocks.indices) {
                if (i != shortLen - eccLen || j >= numShort) out += blocks[j][i].toByte()
            }
            return out.toByteArray()
        }

        private fun build(ver: Int, codewords: ByteArray): QrCode {
            val size = ver * 4 + 17
            val m = Array(size) { BooleanArray(size) }
            val fn = Array(size) { BooleanArray(size) }
            fun set(x: Int, y: Int, d: Boolean) { m[y][x] = d; fn[y][x] = true }
            // Motifs de synchronisation
            for (i in 0 until size) { set(6, i, i % 2 == 0); set(i, 6, i % 2 == 0) }
            // Repères de position
            for ((cx, cy) in listOf(3 to 3, size - 4 to 3, 3 to size - 4)) {
                for (dy in -4..4) for (dx in -4..4) {
                    val x = cx + dx; val y = cy + dy
                    if (x in 0 until size && y in 0 until size) {
                        val dist = maxOf(Math.abs(dx), Math.abs(dy))
                        set(x, y, dist != 2 && dist != 4)
                    }
                }
            }
            // Repères d'alignement
            if (ver >= 2) {
                val n = ver / 7 + 2
                val step = (ver * 4 + n * 2 + 1) / (n * 2 - 2) * 2
                val pos = IntArray(n); pos[0] = 6
                var p = size - 7
                for (i in n - 1 downTo 1) { pos[i] = p; p -= step }
                for (i in 0 until n) for (j in 0 until n) {
                    if ((i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0)) continue
                    for (dy in -2..2) for (dx in -2..2) set(pos[i] + dx, pos[j] + dy, maxOf(Math.abs(dx), Math.abs(dy)) != 1)
                }
            }
            // Zone du format (réservée, écrite après)
            fun format() {
                val data = (FORMAT_ECC_M shl 3) or MASK
                var rem = data
                repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
                val bits = ((data shl 10) or rem) xor 0x5412
                fun bit(i: Int) = (bits ushr i) and 1 != 0
                for (i in 0..5) set(8, i, bit(i))
                set(8, 7, bit(6)); set(8, 8, bit(7)); set(7, 8, bit(8))
                for (i in 9 until 15) set(14 - i, 8, bit(i))
                for (i in 0 until 8) set(size - 1 - i, 8, bit(i))
                for (i in 8 until 15) set(8, size - 15 + i, bit(i))
                set(8, size - 8, true)
            }
            format()
            // Données en zigzag
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vert in 0 until size) for (j in 0..1) {
                    val x = right - j
                    val upward = ((right + 1) and 2) == 0
                    val y = if (upward) size - 1 - vert else vert
                    if (!fn[y][x] && i < codewords.size * 8) {
                        m[y][x] = ((codewords[i ushr 3].toInt() ushr (7 - (i and 7))) and 1) != 0
                        i++
                    }
                }
                right -= 2
            }
            // Masque 0 : (x + y) pair
            for (y in 0 until size) for (x in 0 until size) if (!fn[y][x] && (x + y) % 2 == 0) m[y][x] = !m[y][x]
            format()
            return QrCode(size, m)
        }
    }
}
