package com.possatstack.app.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Generates a square QR code [Bitmap] for the given [content].
 *
 * The resulting bitmap uses pure black and white pixels with no margin,
 * suitable for display inside a padded Compose container.
 */
fun generateQrBitmap(content: String, sizePx: Int): Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 0)
    val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
        }
    }
    return bitmap
}
