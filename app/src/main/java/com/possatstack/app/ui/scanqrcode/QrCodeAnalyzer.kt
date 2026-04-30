package com.possatstack.app.ui.scanqrcode

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * CameraX [ImageAnalysis.Analyzer] that decodes QR codes from the YUV
 * preview stream using ZXing. Reports the first successful decode through
 * [onDecoded]; the caller is responsible for stopping analysis afterwards
 * (e.g. by detaching the analyzer or unbinding the use case) — repeated
 * calls are otherwise harmless because [reported] gates additional emits.
 */
class QrCodeAnalyzer(
    private val onDecoded: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    private val reader =
        MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true,
                ),
            )
        }

    @Volatile private var reported = false

    override fun analyze(image: ImageProxy) {
        if (reported) {
            image.close()
            return
        }
        try {
            val text = decode(image)
            if (text != null) {
                reported = true
                onDecoded(text)
            }
        } catch (_: Throwable) {
            // ZXing throws NotFoundException on every empty frame; ignore.
        } finally {
            image.close()
        }
    }

    private fun decode(image: ImageProxy): String? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val source =
            PlanarYUVLuminanceSource(
                bytes,
                plane.rowStride,
                image.height,
                0,
                0,
                image.width,
                image.height,
                false,
            )
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        return reader.decodeWithState(bitmap).text
    }
}
