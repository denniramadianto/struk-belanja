package id.my.sir.strukbelanja

import android.app.Application
import com.google.mlkit.common.MlKit

/** Inisialisasi ML Kit sekali saat aplikasi dibuka (wajib sebelum getClient). */
class StrukApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            MlKit.initialize(this)
        } catch (_: Exception) { /* abaikan, dicoba lagi di ScanActivity */ }
    }
}
