package id.my.sir.strukbelanja

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import id.my.sir.strukbelanja.db.Item
import id.my.sir.strukbelanja.db.Receipt
import id.my.sir.strukbelanja.db.StrukDb
import id.my.sir.strukbelanja.util.BackupUtil
import id.my.sir.strukbelanja.util.Format

class MainActivity : Activity() {

    private lateinit var db: StrukDb
    private lateinit var etCari: EditText
    private lateinit var lvCari: ListView
    private lateinit var tvHasilCariLabel: TextView
    private lateinit var lvStruk: ListView
    private var hasilCari: List<Item> = emptyList()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_main)
        db = StrukDb(this)

        etCari = findViewById(R.id.etCari)
        lvCari = findViewById(R.id.lvCari)
        tvHasilCariLabel = findViewById(R.id.tvHasilCariLabel)
        lvStruk = findViewById(R.id.lvStruk)

        findViewById<Button>(R.id.btnFoto).setOnClickListener {
            startActivity(Intent(this, ScanActivity::class.java))
        }
        findViewById<Button>(R.id.btnExport).setOnClickListener {
            BackupUtil.exportCsv(this, db)
        }
        findViewById<Button>(R.id.btnTelegram).setOnClickListener {
            BackupUtil.backupTelegram(this, db)
        }

        etCari.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b2: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b2: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { cari(s.toString()) }
        })

        lvCari.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            val it = hasilCari[pos]
            startActivity(Intent(this, ItemHistoryActivity::class.java).apply {
                putExtra("name", it.name)
                putExtra("name_norm", StrukDb.norm(it.name))
            })
        }

        lvStruk.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            val r = (lvStruk.adapter.getItem(pos) as Receipt)
            startActivity(Intent(this, ReceiptDetailActivity::class.java).apply {
                putExtra("id", r.id)
            })
        }
    }

    override fun onResume() {
        super.onResume()
        muatStruk()
        cari(etCari.text.toString())
    }

    private fun cari(q: String) {
        if (q.trim().length < 2) {
            lvCari.visibility = View.GONE
            tvHasilCariLabel.visibility = View.GONE
            return
        }
        hasilCari = db.searchItems(q)
        tvHasilCariLabel.visibility = View.VISIBLE
        lvCari.visibility = View.VISIBLE
        tvHasilCariLabel.text = if (hasilCari.isEmpty()) "Tidak ketemu — coba kata kunci lain"
            else "Hasil pencarian (${hasilCari.size})"
        lvCari.adapter = object : ArrayAdapter<Item>(this, R.layout.row_item, hasilCari) {
            override fun getView(pos: Int, v: View?, parent: android.view.ViewGroup): View {
                val row = v ?: layoutInflater.inflate(R.layout.row_item, parent, false)
                val it = hasilCari[pos]
                row.findViewById<TextView>(R.id.tvNama).text = it.name
                val size = if (it.sizeText.isNotEmpty()) " • ${it.sizeText}" else ""
                row.findViewById<TextView>(R.id.tvSub).text =
                    "${Format.rp(it.unitPrice)}$size • terakhir ${Format.tanggalId(it.date)}"
                return row
            }
        }
    }

    private fun muatStruk() {
        val list = db.getReceipts()
        lvStruk.adapter = object : ArrayAdapter<Receipt>(this, R.layout.row_stru, list) {
            override fun getView(pos: Int, v: View?, parent: android.view.ViewGroup): View {
                val row = v ?: layoutInflater.inflate(R.layout.row_stru, parent, false)
                val r = list[pos]
                val toko = r.store.ifEmpty { "(Tanpa nama toko)" }
                row.findViewById<TextView>(R.id.tvToko).text = "🏪 $toko"
                row.findViewById<TextView>(R.id.tvTanggal).text =
                    "${Format.tanggalId(r.date)} • ${r.itemCount} barang"
                row.findViewById<TextView>(R.id.tvTotal).text = Format.rp(r.total)
                return row
            }
        }
        if (list.isEmpty()) {
            Toast.makeText(this, "Belum ada struk. Ketuk ＋Struk untuk mulai.", Toast.LENGTH_LONG).show()
        }
    }
}
