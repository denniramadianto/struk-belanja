package id.my.sir.strukbelanja.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Wrapper ML Kit Text Recognition v2 (bundled, on-device).
 * Model OCR ikut di dalam APK — jalan tanpa internet & tanpa Play Services.
 */
object MlKitOcr {

    fun isAvailable(): Boolean = try {
        Class.forName("com.google.mlkit.vision.text.TextRecognition")
        true
    } catch (e: Throwable) {
        false
    }

    /** Callback di thread utama. Result berisi daftar baris teks. */
    fun recognize(bitmap: Bitmap, cb: (Result<List<String>>) -> Unit) {
        try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { text ->
                    val lines = text.textBlocks
                        .flatMap { b -> b.lines.map { it.text } }
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    cb(Result.success(lines))
                }
                .addOnFailureListener { e -> cb(Result.failure(e)) }
        } catch (e: Throwable) {
            cb(Result.failure(e))
        }
    }

    /** Kecilkan bitmap agar OCR cepat & hemat memori (lebar maks 1600px). */
    fun downscale(src: Bitmap, maxW: Int = 1600): Bitmap {
        if (src.width <= maxW) return src
        val ratio = maxW.toDouble() / src.width
        val h = (src.height * ratio).toInt()
        return Bitmap.createScaledBitmap(src, maxW, h, true)
    }
}
