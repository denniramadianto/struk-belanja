package id.my.sir.strukbelanja

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.*
import id.my.sir.strukbelanja.db.Item
import id.my.sir.strukbelanja.db.StrukDb
import id.my.sir.strukbelanja.util.Format
import kotlin.math.abs

class ItemHistoryActivity : Activity() {

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContentView(R.layout.activity_item_history)

        val name = intent.getStringExtra("name") ?: run { finish(); return }
        val nameNorm = intent.getStringExtra("name_norm") ?: StrukDb.norm(name)
        val db = StrukDb(this)
        val riwayat = db.getHistory(nameNorm)

        findViewById<TextView>(R.id.tvNama).text = "🔎 $name"

        if (riwayat.isEmpty()) {
            findViewById<TextView>(R.id.tvRingkasan).text = "Belum ada riwayat 3 bulan terakhir."
            return
        }

        val harga = riwayat.map { it.unitPrice }
        val termurah = harga.minOrNull() ?: 0L
        val termahal = harga.maxOrNull() ?: 0L
        val rata2 = harga.average().toLong()
        val terakhir = riwayat.first().unitPrice
        // tren: bandingkan pembelian terbaru vs sebelumnya
        val trenStr = if (riwayat.size >= 2) {
            val prev = riwayat[1].unitPrice
            val selisih = terakhir - prev
            val persen = if (prev > 0) selisih * 100.0 / prev else 0.0
            when {
                selisih > 0 -> "📈 Naik ${Format.rp(selisih)} (+${"%.1f".format(persen)}%)"
                selisih < 0 -> "📉 Turun ${Format.rp(abs(selisih))} (${"%.1f".format(persen)}%)"
                else -> "➡️ Sama"
            } + " vs ${Format.tanggalId(riwayat[1].date)}"
        } else "Belum ada pembanding"

        // harga per 100 g/ml (ukuran ternormalisasi)
        val per100 = riwayat.filter { it.sizeAmount > 0 }
            .minByOrNull { it.unitPrice * 100.0 / it.sizeAmount }
            ?.let { "Termurah per 100${it.sizeUnit}: ${Format.rp(it.unitPrice * 100.0 / it.sizeAmount)} (${it.sizeText})" }
            ?: ""

        findViewById<TextView>(R.id.tvRingkasan).text =
            "Termurah: ${Format.rp(termurah)}\n" +
            "Termahal: ${Format.rp(termahal)}\n" +
            "Rata-rata: ${Format.rp(rata2)}\n" +
            "Terakhir: ${Format.rp(terakhir)}\n" +
            "Tren: $trenStr" + (if (per100.isNotEmpty()) "\n$per100" else "")

        findViewById<ListView>(R.id.lvRiwayat).adapter =
            object : ArrayAdapter<Item>(this, R.layout.row_item, riwayat) {
                override fun getView(p: Int, v: View?, parent: android.view.ViewGroup): View {
                    val row = v ?: layoutInflater.inflate(R.layout.row_item, parent, false)
                    val it = riwayat[p]
                    row.findViewById<TextView>(R.id.tvNama).text =
                        "${Format.tanggalId(it.date)} • 🏪 ${it.store.ifEmpty { "-" }}"
                    val size = if (it.sizeText.isNotEmpty()) " • ${it.sizeText}" else ""
                    row.findViewById<TextView>(R.id.tvSub).text =
                        "${Format.qtyStr(it.qty)} × ${Format.rp(it.unitPrice)}$size = " +
                        Format.rp((it.qty * it.unitPrice).toLong())
                    return row
                }
            }
    }
}
