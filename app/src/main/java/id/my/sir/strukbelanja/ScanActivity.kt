package id.my.sir.strukbelanja

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.*
import com.google.mlkit.common.MlKit
import id.my.sir.strukbelanja.ocr.MlKitOcr
import id.my.sir.strukbelanja.parser.ReceiptParser
import java.io.File
import java.io.FileOutputStream

class ScanActivity : Activity() {

    private lateinit var ivFoto: ImageView
    private lateinit var tvStatus: TextView
    private lateinit var tvHasilOcr: TextView
    private lateinit var svHasil: ScrollView
    private lateinit var btnOcr: Button
    private lateinit var btnLanjut: Button

    private var fotoFile: File? = null
    private var cameraUri: Uri? = null
    private var ocrLines: List<String> = emptyList()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_scan)
        try { MlKit.initialize(this) } catch (_: Exception) {}

        ivFoto = findViewById(R.id.ivFoto)
        tvStatus = findViewById(R.id.tvStatus)
        tvHasilOcr = findViewById(R.id.tvHasilOcr)
        svHasil = findViewById(R.id.svHasil)
        btnOcr = findViewById(R.id.btnOcr)
        btnLanjut = findViewById(R.id.btnLanjut)

        findViewById<Button>(R.id.btnKamera).setOnClickListener { ambilFoto() }
        findViewById<Button>(R.id.btnGaleri).setOnClickListener { dariGaleri() }
        btnOcr.setOnClickListener { jalankanOcr() }
        btnLanjut.setOnClickListener { lanjut() }
        findViewById<Button>(R.id.btnManual).setOnClickListener {
            // input manual tanpa foto
            startActivity(Intent(this, EditItemsActivity::class.java))
        }
    }

    private fun ambilFoto() {
        val nama = "struk_${System.currentTimeMillis()}.jpg"
        val cv = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, nama)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/StrukBelanja")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
        if (uri == null) {
            Toast.makeText(this, "Gagal menyiapkan kamera", Toast.LENGTH_SHORT).show()
            return
        }
        cameraUri = uri
        val i = Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, uri)
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        startActivityForResult(i, REQ_KAMERA)
    }

    private fun dariGaleri() {
        val i = Intent(Intent.ACTION_GET_CONTENT).setType("image/*")
        startActivityForResult(Intent.createChooser(i, "Pilih foto struk"), REQ_GALERI)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (res != RESULT_OK) return
        when (req) {
            REQ_KAMERA -> cameraUri?.let { salinKeInternal(it) }
            REQ_GALERI -> data?.data?.let { salinKeInternal(it) }
        }
    }

    /** Salin foto ke penyimpanan internal aplikasi agar tidak hilang. */
    private fun salinKeInternal(uri: Uri) {
        try {
            val f = File(filesDir, "struk_${System.currentTimeMillis()}.jpg")
            contentResolver.openInputStream(uri)?.use { inp ->
                FileOutputStream(f).use { out -> inp.copyTo(out) }
            }
            fotoFile = f
            ivFoto.setImageURI(Uri.fromFile(f))
            btnOcr.isEnabled = true
            tvStatus.text = "Foto siap. Ketuk \"Baca Struk\" untuk membaca isinya."
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membaca foto: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun jalankanOcr() {
        val f = fotoFile ?: return
        if (!MlKitOcr.isAvailable()) {
            tvStatus.text = "Mesin OCR tidak tersedia di HP ini. Pakai \"Input manual\" saja."
            return
        }
        btnOcr.isEnabled = false
        tvStatus.text = "🔍 Membaca struk… tunggu sebentar."
        Thread {
            try {
                val bmp = BitmapFactory.decodeFile(f.absolutePath) ?: throw Exception("foto rusak")
                val kecil = MlKitOcr.downscale(bmp)
                MlKitOcr.recognize(kecil) { res ->
                    runOnUiThread {
                        btnOcr.isEnabled = true
                        res.onSuccess { lines ->
                            ocrLines = lines
                            svHasil.visibility = View.VISIBLE
                            tvHasilOcr.text = lines.joinToString("\n")
                            val n = ReceiptParser.parseText(lines.joinToString("\n")).size
                            tvStatus.text = "Selesai! Terdeteksi ±$n barang. Ketuk Lanjut untuk memeriksa."
                            btnLanjut.visibility = View.VISIBLE
                        }.onFailure { e ->
                            tvStatus.text = "Gagal membaca: ${e.message}. Coba foto ulang atau input manual."
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    btnOcr.isEnabled = true
                    tvStatus.text = "Gagal: ${e.message}"
                }
            }
        }.start()
    }

    private fun lanjut() {
        val items = ReceiptParser.parseText(ocrLines.joinToString("\n"))
        TempData.parsed = items
        TempData.fotoPath = fotoFile?.absolutePath
        startActivity(Intent(this, EditItemsActivity::class.java))
    }

    companion object {
        const val REQ_KAMERA = 11
        const val REQ_GALERI = 12
    }
}

/** Penampung sementara hasil OCR antar-activity. */
object TempData {
    var parsed: List<id.my.sir.strukbelanja.parser.ParsedItem> = emptyList()
    var fotoPath: String? = null
}
