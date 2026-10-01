package id.my.sir.strukbelanja

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.*
import id.my.sir.strukbelanja.db.StrukDb
import id.my.sir.strukbelanja.parser.ParsedItem
import id.my.sir.strukbelanja.util.Format

class EditItemsActivity : Activity() {

    private lateinit var db: StrukDb
    private lateinit var etToko: EditText
    private lateinit var etTanggal: EditText
    private lateinit var lvItem: ListView
    private lateinit var tvTotal: TextView
    private val items = mutableListOf<ParsedItem>()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_edit_items)
        db = StrukDb(this)

        etToko = findViewById(R.id.etToko)
        etTanggal = findViewById(R.id.etTanggal)
        lvItem = findViewById(R.id.lvItem)
        tvTotal = findViewById(R.id.tvTotal)
        etTanggal.setText(Format.today())

        items.addAll(TempData.parsed)
        TempData.parsed = emptyList()

        findViewById<Button>(R.id.btnTambah).setOnClickListener { dialogItem(null) }
        findViewById<Button>(R.id.btnSimpan).setOnClickListener { simpan() }

        lvItem.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            dialogItem(pos)
        }
        lvItem.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, pos, _ ->
            AlertDialog.Builder(this)
                .setMessage("Hapus \"${items[pos].name}\"?")
                .setPositiveButton("Hapus") { _, _ -> items.removeAt(pos); refresh() }
                .setNegativeButton("Batal", null)
                .show()
            true
        }
        refresh()
    }

    private fun dialogItem(pos: Int?) {
        val v = layoutInflater.inflate(R.layout.dialog_edit_item, null)
        val etNama = v.findViewById<EditText>(R.id.etNama)
        val etQty = v.findViewById<EditText>(R.id.etQty)
        val etHarga = v.findViewById<EditText>(R.id.etHarga)
        if (pos != null) {
            val it = items[pos]
            etNama.setText(it.name)
            etQty.setText(Format.qtyStr(it.qty))
            etHarga.setText(it.unitPrice.toString())
        } else {
            etQty.setText("1")
        }
        AlertDialog.Builder(this)
            .setTitle(if (pos == null) "Tambah barang" else "Ubah barang")
            .setView(v)
            .setPositiveButton("OK") { _, _ ->
                val nama = etNama.text.toString().trim()
                val qty = etQty.text.toString().replace(",", ".").toDoubleOrNull() ?: 1.0
                val harga = etHarga.text.toString().filter { it.isDigit() }.toLongOrNull() ?: 0L
                if (nama.isEmpty() || harga <= 0) {
                    Toast.makeText(this, "Nama & harga wajib diisi", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                // ekstrak ulang ukuran dari nama bila berubah
                val parsed = id.my.sir.strukbelanja.parser.ReceiptParser.parseLine("$nama $harga")
                val sizeText = parsed?.sizeText ?: ""
                val sizeAmount = parsed?.sizeAmount ?: 0.0
                val sizeUnit = parsed?.sizeUnit ?: ""
                val item = ParsedItem(nama.uppercase(), qty, harga, sizeText, sizeAmount, sizeUnit)
                if (pos == null) items.add(item) else items[pos] = item
                refresh()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun refresh() {
        lvItem.adapter = object : ArrayAdapter<ParsedItem>(this, R.layout.row_item, items) {
            override fun getView(p: Int, v: View?, parent: android.view.ViewGroup): View {
                val row = v ?: layoutInflater.inflate(R.layout.row_item, parent, false)
                val it = items[p]
                row.findViewById<TextView>(R.id.tvNama).text = it.name
                val size = if (it.sizeText.isNotEmpty()) " • ${it.sizeText}" else ""
                row.findViewById<TextView>(R.id.tvSub).text =
                    "${Format.qtyStr(it.qty)} × ${Format.rp(it.unitPrice)}$size = ${Format.rp((it.qty * it.unitPrice).toLong())}"
                return row
            }
        }
        val total = items.sumOf { (it.qty * it.unitPrice).toLong() }
        tvTotal.text = "Total: ${Format.rp(total)}"
    }

    private fun simpan() {
        if (items.isEmpty()) {
            Toast.makeText(this, "Daftar barang masih kosong", Toast.LENGTH_SHORT).show()
            return
        }
        val toko = etToko.text.toString().trim()
        var tgl = etTanggal.text.toString().trim()
        if (!tgl.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            Toast.makeText(this, "Format tanggal harus YYYY-MM-DD", Toast.LENGTH_SHORT).show()
            return
        }
        val total = items.sumOf { (it.qty * it.unitPrice).toLong() }
        val id = db.insertReceipt(toko, tgl, total, TempData.fotoPath)
        TempData.fotoPath = null
        for (it in items) {
            db.insertItem(id, it.name, it.qty, it.unitPrice, it.sizeText, it.sizeAmount, it.sizeUnit)
        }
        Toast.makeText(this, "Tersimpan! 🎉", Toast.LENGTH_SHORT).show()
        finish()
    }
}
