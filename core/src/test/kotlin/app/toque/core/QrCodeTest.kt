package app.toque.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Écrit des QR codes en PNG (build/qr/) : outils/verifier-qr.sh les relit avec un vrai lecteur (zbarimg). */
class QrCodeTest {
    private val samples = listOf(
        "http://192.168.1.42:8765/abcdef",
        "http://10.0.0.2:8765/abcdef",
        "https://www.cookomix.com/recettes/crumble-poulet-courgettes-thermomix/",
        "Ab",
    )

    @Test fun tailles() {
        assertEquals(29, QrCode.encode(samples[0]).size) // version 3 (31 octets en correction M)
        assertEquals(21, QrCode.encode("Ab").size)        // version 1
    }

    @Test fun images() {
        val dir = File("build/qr").apply { mkdirs() }
        for ((i, s) in samples.withIndex()) {
            val q = QrCode.encode(s)
            val scale = 8; val quiet = 4
            val n = (q.size + quiet * 2) * scale
            val img = BufferedImage(n, n, BufferedImage.TYPE_INT_RGB)
            for (y in 0 until n) for (x in 0 until n) {
                val mx = x / scale - quiet; val my = y / scale - quiet
                val dark = mx in 0 until q.size && my in 0 until q.size && q.dark(mx, my)
                img.setRGB(x, y, if (dark) 0 else 0xFFFFFF)
            }
            ImageIO.write(img, "png", File(dir, "qr$i.png"))
            File(dir, "qr$i.txt").writeText(s)
        }
    }
}
