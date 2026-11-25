package app.toque.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.util.concurrent.Executors

/** Chargement d'images économe en mémoire (sous-échantillonnage + cache) pour 1 Go de RAM. */
object ImageLoader {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8 / 1024).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    private val exec = Executors.newFixedThreadPool(2) { r -> Thread(r, "toque-images").apply { priority = Thread.MIN_PRIORITY } }
    private val main = Handler(Looper.getMainLooper())

    /** Oublie les versions en cache d'une photo qui vient d'être remplacée. */
    fun forget(file: File) {
        val prefix = file.absolutePath + "@"
        for (k in cache.snapshot().keys) if (k.startsWith(prefix)) cache.remove(k)
    }

    fun load(view: ImageView, file: File?, targetWidth: Int) {
        view.tag = file?.absolutePath
        view.setImageDrawable(null)
        if (file == null) return
        val key = file.absolutePath + "@" + targetWidth
        cache.get(key)?.let { view.setImageBitmap(it); return }
        exec.execute {
            val bmp = decode(file, targetWidth) ?: return@execute
            cache.put(key, bmp)
            main.post { if (view.tag == file.absolutePath) view.setImageBitmap(bmp) }
        }
    }

    /**
     * Miniature gardée à côté des photos (sous-dossier « miniatures ») : la liste des recettes ne
     * relit plus des photos de 900 pixels pour des vignettes de 200. Refaite si la photo change.
     */
    private fun decode(file: File, targetWidth: Int): Bitmap? = try {
        val thumb = File(File(file.parentFile, "miniatures"), "${file.nameWithoutExtension}-$targetWidth.jpg")
        val fast = if (thumb.isFile && thumb.lastModified() >= file.lastModified()) decodeSampled(thumb, targetWidth) else null
        fast ?: decodeSampled(file, targetWidth)?.let { bmp ->
            val out = if (bmp.width > targetWidth * 5 / 4) {
                Bitmap.createScaledBitmap(bmp, targetWidth, maxOf(1, bmp.height * targetWidth / bmp.width), true).also { if (it !== bmp) bmp.recycle() }
            } else bmp
            try {
                thumb.parentFile?.mkdirs()
                val tmp = File(thumb.path + ".part")
                tmp.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                tmp.renameTo(thumb)
            } catch (_: Exception) {
            }
            out
        }
    } catch (_: Throwable) {
        null
    }

    private fun decodeSampled(file: File, targetWidth: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        })
    }
}
