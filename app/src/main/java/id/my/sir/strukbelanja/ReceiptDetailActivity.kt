package id.my.sir.strukbelanja

import android.app.Activity
import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import id.my.sir.strukbelanja.db.Item
import id.my.sir.strukbelanja.db.StrukDb
import id.my.sir.strukbelanja.util.Format
import java.io.File

class ReceiptDetailActivity : Activity() {

    private lateinit var db: StrukDb
    private var receiptId: Long = 0

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_receipt_detail)
        db = StrukDb(this)
        receiptId = intent.getLongExtra("id", 0)
        val r = db.getReceipt(receiptId) ?: run { finish(); return }

        findViewById<TextView>(R.id.tvJudul).text =
            "🏪 ${r.store.ifEmpty { "(Tanpa nama toko)" }} • ${Format.tanggalId(r.date)}"

        val iv = findViewById<ImageView>(R.id.ivFoto)
        if (r.photoPath != null && File(r.photoPath).exists()) {
            iv.visibility = View.VISIBLE
            iv.setImageURI(Uri.fromFile(File(r.photoPath)))
        } else {
            iv.visibility = View.GONE
        }

        val items = db.getItems(receiptId)
        findViewById<ListView>(R.id.lvItem).adapter =
            object : ArrayAdapter<Item>(this, R.layout.row_item, items) {
                override fun getView(p: Int, v: View?, parent: android.view.ViewGroup): View {
                    val row = v ?: layoutInflater.inflate(R.layout.row_item, parent, false)
                    val it = items[p]
                    row.findViewById<TextView>(R.id.tvNama).text = it.name
                    val size = if (it.sizeText.isNotEmpty()) " • ${it.sizeText}" else ""
                    row.findViewById<TextView>(R.id.tvSub).text =
                        "${Format.qtyStr(it.qty)} × ${Format.rp(it.unitPrice)}$size"
                    return row
                }
            }

        findViewById<Button>(R.id.btnHapus).setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage("Hapus struk ini beserta ${items.size} barangnya?")
                .setPositiveButton("Hapus") { _, _ ->
                    val foto = db.deleteReceipt(receiptId)
                    foto?.let { try { File(it).delete() } catch (e: Exception) {} }
                    finish()
                }
                .setNegativeButton("Batal", null)
                .show()
        }
    }
}
