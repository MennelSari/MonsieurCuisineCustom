package app.toque.recipes

import android.os.Handler
import android.os.Looper
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

/**
 * Petit serveur web, ouvert seulement pendant l'édition d'une recette : le téléphone ouvre
 * l'adresse affichée (même Wi-Fi), prend le plat en photo et l'envoie ; la photo est enregistrée
 * dans [target]. Une seule page, un seul envoi à la fois, 12 Mo au plus, jeton dans l'adresse pour
 * qu'un autre appareil du réseau ne tombe pas dessus par hasard.
 */
class PhotoReceiver(private val target: File, private val onPhoto: (File) -> Unit) {
    private var server: ServerSocket? = null
    private val main = Handler(Looper.getMainLooper())
    private val token = (1..6).map { "abcdefghjkmnpqrstuvwxyz23456789".random() }.joinToString("")
    var url: String = ""
        private set

    /** Démarre le serveur ; renvoie l'adresse à ouvrir sur le téléphone, ou null sans Wi-Fi. */
    fun start(): String? {
        val ip = wifiIp() ?: return null
        val s = ServerSocket(PORT)
        server = s
        url = "http://$ip:$PORT/$token"
        Thread({
            while (!s.isClosed) {
                val client = try { s.accept() } catch (_: SocketException) { break }
                try { handle(client) } catch (_: Exception) { } finally { try { client.close() } catch (_: Exception) { } }
            }
        }, "photo-recette").apply { isDaemon = true }.start()
        return url
    }

    fun stop() {
        try { server?.close() } catch (_: Exception) { }
        server = null
    }

    private fun handle(sock: Socket) {
        sock.soTimeout = 30_000
        val input = BufferedInputStream(sock.getInputStream())
        val requestLine = readLine(input) ?: return
        val headers = HashMap<String, String>()
        while (true) {
            val l = readLine(input) ?: return
            if (l.isEmpty()) break
            val i = l.indexOf(':')
            if (i > 0) headers[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
        }
        val parts = requestLine.split(' ')
        val method = parts.getOrNull(0).orEmpty()
        val path = parts.getOrNull(1).orEmpty()
        val out = sock.getOutputStream()
        fun reply(status: String, html: String) {
            val body = html.toByteArray(Charsets.UTF_8)
            out.write("HTTP/1.1 $status\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
            out.write(body)
            out.flush()
        }
        if (!path.startsWith("/$token")) { reply("404 Not Found", page("Adresse inconnue", "<p>Scannez de nouveau le code affiché sur le robot.</p>")); return }
        if (method == "GET") { reply("200 OK", page("Photo de la recette", FORM)); return }
        if (method != "POST") { reply("405 Method Not Allowed", page("Erreur", "")); return }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        if (length <= 0 || length > MAX_BYTES) { reply("413 Payload Too Large", page("Photo trop lourde", "<p>12 Mo au plus.</p>")); return }
        val boundary = headers["content-type"]?.substringAfter("boundary=", "")?.trim('"').orEmpty()
        if (boundary.isEmpty()) { reply("400 Bad Request", page("Erreur", "")); return }
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) break
            read += n
        }
        val image = extractFile(body.copyOf(read), boundary)
        if (image == null || image.size < 100) { reply("400 Bad Request", page("Aucune photo reçue", FORM)); return }
        target.parentFile?.mkdirs()
        val tmp = File(target.path + ".tmp")
        tmp.writeBytes(image)
        tmp.renameTo(target)
        main.post { onPhoto(target) }
        reply("200 OK", page("Photo envoyée ✓", "<p>Elle s'affiche sur le robot. Vous pouvez en envoyer une autre.</p>$FORM"))
    }

    /** Contenu du premier champ fichier d'un corps multipart/form-data. */
    private fun extractFile(body: ByteArray, boundary: String): ByteArray? {
        val delim = "--$boundary".toByteArray()
        var start = indexOf(body, delim, 0)
        while (start >= 0) {
            val headEnd = indexOf(body, "\r\n\r\n".toByteArray(), start)
            if (headEnd < 0) return null
            val head = String(body, start, headEnd - start, Charsets.ISO_8859_1)
            val next = indexOf(body, ("\r\n--$boundary").toByteArray(), headEnd + 4)
            if (next < 0) return null
            if (head.contains("filename=", ignoreCase = true)) return body.copyOfRange(headEnd + 4, next)
            start = next + 2
        }
        return null
    }

    private fun indexOf(a: ByteArray, p: ByteArray, from: Int): Int {
        outer@ for (i in from..a.size - p.size) {
            for (j in p.indices) if (a[i + j] != p[j]) continue@outer
            return i
        }
        return -1
    }

    private fun readLine(input: InputStream): String? {
        val b = ByteArrayOutputStream()
        while (true) {
            val c = input.read()
            if (c < 0) return if (b.size() == 0) null else b.toString("ISO-8859-1")
            if (c == '\n'.code) return b.toString("ISO-8859-1").trimEnd('\r')
            b.write(c)
            if (b.size() > 8192) return null
        }
    }

    private fun page(title: String, content: String) = """<!doctype html><html lang="fr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>$title</title>
<style>body{margin:0;padding:24px;background:#F6F5F2;color:#121212;font-family:system-ui,sans-serif}
h1{font-size:26px;letter-spacing:-.5px}p{color:#5E5B55;font-size:16px;line-height:1.45}
label,button{display:block;width:100%;box-sizing:border-box;border-radius:30px;padding:18px;font-size:18px;font-weight:800;text-align:center;border:none;margin-top:14px}
label{background:#FFFFFF}button{background:#121212;color:#FFFFFF}input{display:none}</style></head>
<body><h1>$title</h1>$content</body></html>"""

    private companion object {
        const val PORT = 8765
        const val MAX_BYTES = 12 * 1024 * 1024
        const val FORM = """<p>Prenez votre plat en photo, puis envoyez-la au robot.</p>
<form method="post" enctype="multipart/form-data"><label>📷 Prendre ou choisir une photo<input type="file" name="photo" accept="image/*" capture="environment" onchange="this.form.querySelector('button').textContent='Envoyer au robot'"></label>
<button type="submit">Envoyer au robot</button></form>"""

        fun wifiIp(): String? = try {
            NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                .sortedBy { if (it.name.startsWith("wlan")) 0 else 1 }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
        } catch (_: Exception) { null }
    }
}
