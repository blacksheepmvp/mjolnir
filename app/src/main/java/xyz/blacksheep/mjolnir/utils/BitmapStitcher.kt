package xyz.blacksheep.mjolnir.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import kotlin.math.max

object BitmapStitcher {

    fun stitch(top: Bitmap, bottom: Bitmap): Bitmap {
        val width = max(top.width, bottom.width)
        val height = top.height + bottom.height

        val combined = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(combined)

        // Transparent background
        canvas.drawColor(Color.TRANSPARENT)

        // Draw top bitmap centered
        val topLeft = (width - top.width) / 2f
        canvas.drawBitmap(top, topLeft, 0f, null)

        // Draw bottom bitmap centered
        val bottomLeft = (width - bottom.width) / 2f
        canvas.drawBitmap(bottom, bottomLeft, top.height.toFloat(), null)

        return combined
    }

    // Hooks for future decoration (currently no-op)
    fun addBorders(bitmap: Bitmap): Bitmap = bitmap
    fun addSpacing(bitmap: Bitmap): Bitmap = bitmap
    fun addBackground(bitmap: Bitmap): Bitmap = bitmap
}
