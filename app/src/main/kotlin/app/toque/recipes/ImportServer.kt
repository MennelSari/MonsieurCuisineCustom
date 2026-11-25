package app.toque.recipes

import android.os.Handler
import android.os.Looper
import app.toque.core.json.Json
import app.toque.core.json.obj
import app.toque.core.json.str
import app.toque.core.recipes.Cookomix
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Import depuis le téléphone : tant que l'appli tourne, le robot répond sur le Wi-Fi de la maison
 * (http://IP-du-robot:8766). La page permet de coller un lien Cookomix ; un raccourci iPhone
 * (menu Partager de Safari) peut aussi envoyer le lien directement (POST /importer, corps JSON
 * {"url": "…"}, texte brut ou formulaire). Seuls les liens cookomix.com/recettes sont acceptés, et
 * seulement depuis le réseau local. La recette est enregistrée dans « Mes recettes ».
 */
class ImportServer(private val importer: CookomixImport) {
    /** Recette reçue (titre, identifiant), signalée sur le fil principal. */
    var onImported: (String, String) -> Unit = { _, _ -> }

    private var server: ServerSocket? = null
    private val main = Handler(Looper.getMainLooper())

    val address: String? get() = wifiIp()?.let { "http://$it:$PORT" }

    @Synchronized
    fun start() {
        if (server != null) return
        val s = try { ServerSocket(PORT) } catch (_: Exception) { return }
        server = s
        Thread({
            while (!s.isClosed) {
                val client = try { s.accept() } catch (_: SocketException) { break } catch (_: Exception) { continue }
                Thread({
                    try { handle(client) } catch (_: Exception) { } finally { try { client.close() } catch (_: Exception) { } }
                }, "import-telephone-client").apply { isDaemon = true }.start()
            }
        }, "import-telephone").apply { isDaemon = true }.start()
    }

    @Synchronized
    fun stop() {
        try { server?.close() } catch (_: Exception) { }
        server = null
    }

    private fun handle(sock: Socket) {
        sock.soTimeout = 20_000
        val out = sock.getOutputStream()
        fun send(status: String, type: String, body: String) {
            val b = body.toByteArray(Charsets.UTF_8)
            out.write("HTTP/1.1 $status\r\nContent-Type: $type; charset=utf-8\r\nContent-Length: ${b.size}\r\nConnection: close\r\n\r\n".toByteArray())
            out.write(b)
            out.flush()
        }
        if (!isLocal(sock.inetAddress)) { send("403 Forbidden", "text/plain", "Réseau local uniquement."); return }
        val input = BufferedInputStream(sock.getInputStream())
        val request = readLine(input) ?: return
        val headers = HashMap<String, String>()
        while (true) {
            val l = readLine(input) ?: return
            if (l.isEmpty()) break
            val i = l.indexOf(':')
            if (i > 0) headers[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
        }
        val parts = request.split(' ')
        val method = parts.getOrNull(0).orEmpty()
        val path = parts.getOrNull(1).orEmpty().substringBefore('?')
        if (method == "GET" && (path == "/" || path.isEmpty())) { send("200 OK", "text/html", page(null)); return }
        if (method != "POST" || !path.startsWith("/importer")) { send("404 Not Found", "text/plain", "Page inconnue."); return }

        val length = (headers["content-length"]?.toIntOrNull() ?: 0).coerceIn(0, 16 * 1024)
        val body = ByteArray(length)
        var read = 0
        while (read < length) { val n = input.read(body, read, length - read); if (n < 0) break; read += n }
        val text = String(body, 0, read, Charsets.UTF_8).trim()
        val type = headers["content-type"].orEmpty()
        val link = when {
            type.contains("json") -> (try { Json.parse(text) } catch (_: Exception) { null }).obj().str("url")
            type.contains("x-www-form-urlencoded") -> text.split('&').firstOrNull { it.startsWith("url=") }?.removePrefix("url=")?.let { URLDecoder.decode(it, "UTF-8") }
            else -> text
        }.orEmpty().let { extractLink(it) }
        val wantsHtml = type.contains("x-www-form-urlencoded")
        if (link == null) {
            val msg = "Ce n'est pas un lien de recette Cookomix (cookomix.com/recettes/…)."
            if (wantsHtml) send("400 Bad Request", "text/html", page(msg, false)) else send("400 Bad Request", "text/plain", msg)
            return
        }
        // Import sur le fil de l'appli, la réponse attend le résultat (30 s au plus).
        val done = CountDownLatch(1)
        var result: String? = null
        var ok = false
        importer.preview(link) { p, err ->
            if (p == null) { result = err ?: "Import impossible."; done.countDown() }
            else importer.save(p) {
                ok = true
                result = "« ${p.recipe.title} » est sur le robot, dans Mes recettes (${p.recipe.steps.count { it.machine != null }} réglages machine)."
                onImported(p.recipe.title, p.recipe.id)
                done.countDown()
            }
        }
        if (!done.await(30, TimeUnit.SECONDS)) result = "Le robot met du temps à répondre : la recette arrivera peut-être quand même."
        val msg = result ?: "Import impossible."
        if (wantsHtml) send(if (ok) "200 OK" else "502 Bad Gateway", "text/html", page(msg, ok))
        else send(if (ok) "200 OK" else "502 Bad Gateway", "text/plain", msg)
    }

    /** Premier lien Cookomix trouvé dans un texte (le partage iOS envoie parfois « titre + lien »). */
    private fun extractLink(s: String): String? =
        Regex("(https?://)?(www\\.)?cookomix\\.com/recettes/[^\\s\"'<>]+", RegexOption.IGNORE_CASE).find(s)?.value
            ?.takeIf { Cookomix.isCookomixUrl(it) }

    private fun isLocal(a: InetAddress?): Boolean = a != null && (a.isSiteLocalAddress || a.isLoopbackAddress || a.isLinkLocalAddress)

    private fun readLine(input: InputStream): String? {
        val b = ByteArrayOutputStream()
        while (true) {
            val c = input.read()
            if (c < 0) return if (b.size() == 0) null else b.toString("UTF-8")
            if (c == '\n'.code) return b.toString("UTF-8").trimEnd('\r')
            b.write(c)
            if (b.size() > 8192) return null
        }
    }

    private fun page(message: String?, ok: Boolean = true): String {
        val note = message?.let { "<p class=\"${if (ok) "ok" else "ko"}\">${esc(it)}</p>" }.orEmpty()
        return """<!doctype html><html lang="fr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>Envoyer au robot</title>
<style>body{margin:0;padding:24px;background:#F6F5F2;color:#121212;font-family:system-ui,sans-serif}
h1{font-size:26px;letter-spacing:-.5px;margin:0 0 8px}p{color:#5E5B55;font-size:16px;line-height:1.45}
input,button{display:block;width:100%;box-sizing:border-box;border-radius:28px;padding:17px 20px;font-size:17px;border:none;margin-top:12px}
input{background:#fff;border:2px solid #121212}button{background:#121212;color:#fff;font-weight:800}
.ok{background:#E4EFE8;color:#1F5C3A;border-radius:16px;padding:12px 16px;font-weight:700}
.ko{background:#FDECE3;color:#8F330B;border-radius:16px;padding:12px 16px;font-weight:700}</style></head>
<body><h1>Envoyer une recette au robot</h1>$note
<p>Copiez le lien d'une recette Cookomix, collez-le ici, puis envoyez.</p>
<form method="post" action="/importer"><input name="url" type="url" inputmode="url" placeholder="https://www.cookomix.com/recettes/…" required>
<button type="submit">Envoyer au robot</button></form>
<p style="font-size:14px">Astuce : ajoutez cette page à l'écran d'accueil du téléphone.</p></body></html>"""
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    companion object {
        const val PORT = 8766

        fun wifiIp(): String? = try {
            NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                .sortedBy { if (it.name.startsWith("wlan")) 0 else 1 }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
        } catch (_: Exception) { null }
    }
}
