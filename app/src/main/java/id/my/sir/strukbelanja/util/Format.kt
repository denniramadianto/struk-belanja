package id.my.sir.strukbelanja.util

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

object Format {
    private val nf = NumberFormat.getInstance(Locale("in", "ID"))

    fun rp(n: Long): String = "Rp " + nf.format(n)
    fun rp(n: Double): String = "Rp " + nf.format(n.toLong())

    fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** "2026-10-02" -> "2 Okt 2026" */
    fun tanggalId(iso: String): String {
        return try {
            val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso) ?: return iso
            val bulan = arrayOf("Jan","Feb","Mar","Apr","Mei","Jun","Jul","Agu","Sep","Okt","Nov","Des")
            val c = Calendar.getInstance().apply { time = d }
            "${c.get(Calendar.DAY_OF_MONTH)} ${bulan[c.get(Calendar.MONTH)]} ${c.get(Calendar.YEAR)}"
        } catch (e: Exception) { iso }
    }

    fun qtyStr(q: Double): String =
        if (q == q.toLong().toDouble()) q.toLong().toString() else q.toString()
}
