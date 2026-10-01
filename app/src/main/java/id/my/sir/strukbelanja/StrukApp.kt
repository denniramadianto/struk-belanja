package id.my.sir.strukbelanja

import android.app.Application
import com.google.mlkit.common.MlKit

/** Inisialisasi ML Kit sekali saat aplikasi dibuka (wajib sebelum getClient).
 *  Inisialisasi resmi sudah dilakukan MlKitInitProvider (lihat manifest) sebelum
 *  onCreate ini; blok ini hanya pengaman. Tangkap Throwable (termasuk Error
 *  seperti NoClassDefFoundError) agar aplikasi tetap bisa dibuka walau
 *  inisialisasi gagal — OCR yang akan melaporkan errornya, bukan force close. */
class StrukApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            MlKit.initialize(this)
        } catch (_: Throwable) { /* abaikan, provider sudah inisialisasi / OCR lapor error */ }
    }
}
