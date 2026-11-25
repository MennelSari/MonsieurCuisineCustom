package app.toque.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import app.toque.core.QrCode

/** QR code dessiné en noir sur blanc, avec la marge blanche réglementaire (4 modules). */
class QrView(c: Context, text: String) : View(c) {
    private val qr = QrCode.encode(text)
    private val paint = Paint().apply { color = Color.BLACK; isAntiAlias = false }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.WHITE)
        val cells = qr.size + 8
        val cell = minOf(width, height) / cells.toFloat()
        val ox = (width - cell * cells) / 2f + cell * 4
        val oy = (height - cell * cells) / 2f + cell * 4
        for (y in 0 until qr.size) for (x in 0 until qr.size) if (qr.dark(x, y)) {
            canvas.drawRect(ox + x * cell, oy + y * cell, ox + (x + 1) * cell + 0.5f, oy + (y + 1) * cell + 0.5f, paint)
        }
    }
}
