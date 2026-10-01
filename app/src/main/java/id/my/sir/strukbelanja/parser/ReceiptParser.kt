package id.my.sir.strukbelanja.parser

/** Hasil parse satu baris struk. */
data class ParsedItem(
    var name: String,
    var qty: Double,
    var unitPrice: Long,
    var sizeText: String = "",
    var sizeAmount: Double = 0.0,
    var sizeUnit: String = ""
)

/**
 * Parser heuristic untuk struk belanja Indonesia.
 * Contoh baris yang dikenali:
 *   "PEPSODENT SENSITIF 110G"
 *   "2 X 18.500                      37.000"
 *   "INDOMIE GORENG 85G  3x 3.200"
 *   "00002972 BIMOLI CLASSIC PCH 2L" + "2 @43.200  86.400"  (swalayan: kode SKU di depan)
 *   "00100917 SUNLIGHT LIME REF 2X610GR" + "2 @17.900  35.800"
 */
object ReceiptParser {

    private val priceTok = Regex("""(?i)(?:rp\s*)?(\d{1,3}(?:\.\d{3})+|\d+)(?:,\d{2})?\b""")
    private val qtyTok = Regex("""(?i)\b(\d+(?:[.,]\d+)?)\s*[xX]\b|\b[xX]\s*(\d+(?:[.,]\d+)?)\b|\b(\d+(?:[.,]\d+)?)\s*@""")
    private val packTok = Regex("""(?i)\b(\d+)[xX](?=\d)""")  // "2X610GR" -> qty 2, sisa "610GR"
    private val sizeTok = Regex("""(?i)\b(\d+(?:[.,]\d+)?)\s?(ml|l|gr|g|kg)\b""")
    private val skuTok = Regex("""^\d{5,}\s*""")  // kode barang swalayan di awal baris
    private val dateTimeTok = Regex("""\d{1,2}[-/]\d{1,2}[-/]\d{2,4}|\b\d{1,2}:\d{2}(?::\d{2})?\b""")
    private val skipLine = Regex("""(?i)\b(total|tunai|cash|kembali|kembalian|disc|diskon|potongan|ppn|pajak|subtotal|amount|bayar|debit|kredit|kartu|voucher|promo|hemat|selamat|terima kasih|belanja|kasir|struk|nota|tanggal|jam|no|nomor|items?|qty|harga|jumlah|toko|alamat|telp|npwp)\b""")

    /** Token harga, kecuali angka yang merupakan bagian ukuran ("60 GR"). */
    private fun priceMatches(s: String): List<MatchResult> {
        val sizeRanges = sizeTok.findAll(s).map { it.range }.toList()
        return priceTok.findAll(s).filter { pm ->
            sizeRanges.none { sr -> pm.range.first <= sr.last && sr.first <= pm.range.last }
        }.toList()
    }
    private fun hasPrice(s: String): Boolean = priceMatches(s).isNotEmpty()

    fun parseLine(raw: String): ParsedItem? {
        var line = raw.trim()
        if (line.length < 3) return null
        if (skipLine.containsMatchIn(line)) return null
        if (dateTimeTok.containsMatchIn(line)) return null

        var work = skuTok.replace(line, "")  // buang kode SKU "00002972 ..."
        if (work.length < 3) return null

        // qty: pola "2 X" / "2x" / "X2" / "2 @" / "2X610GR" — dari baris asli
        var qty = 1.0
        packTok.find(work)?.let { m ->
            m.groupValues[1].toDoubleOrNull()?.let { q -> if (q > 0 && q < 1000) qty = q }
        }
        work = packTok.replace(work, "")
        qtyTok.find(work)?.let { m ->
            val q = m.groupValues[1].ifEmpty { m.groupValues[2].ifEmpty { m.groupValues[3] } }
                .replace(",", ".").toDoubleOrNull()
            if (q != null && q > 0 && q < 1000) qty = q
        }
        // kumpulkan semua token harga (qty dihapus dulu supaya "2" tidak terbaca sbg harga)
        work = qtyTok.replace(work, " ")
        val pms = priceMatches(work)
        val prices = pms.map { m ->
            m.groupValues[1].replace(".", "").toLongOrNull() ?: 0L
        }.filter { it > 0 }
        if (prices.isEmpty()) return null

        // harga satuan = harga TERAKHIR, kecuali harga terakhir = qty x harga sebelumnya
        // (artinya yg terakhir itu total baris, mis. "2 X 18.500  37.000").
        var unitPrice: Long = prices.last()
        if (prices.size >= 2 && qty > 1) {
            val cand = prices[prices.size - 2]
            val total = prices.last().toDouble()
            if (kotlin.math.abs(total - qty * cand) <= maxOf(2.0, total * 0.01)) {
                unitPrice = cand
            }
        }
        if (unitPrice <= 0) return null

        // nama = baris tanpa token harga (hapus berdasar range, dari belakang,
        // supaya angka ukuran seperti "60 GR" tidak ikut terbuang)
        val sb = StringBuilder(work)
        for (pm in pms.sortedByDescending { it.range.first }) {
            sb.replace(pm.range.first, pm.range.last + 1, " ")
        }
        var name = sb.toString()
        name = name.replace(Regex("""[@*\-_=]+"""), " ")
            .replace(Regex("""\s+"""), " ").trim { it in " .,:" }
        if (name.filter { it.isLetter() }.length < 2) return null

        val item = ParsedItem(name.uppercase(), qty, unitPrice)
        // ukuran dari nama: "110G", "250 ML", "1L", "800GR"
        sizeTok.find(name)?.let { m ->
            val amount = m.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
            var unit = m.groupValues[2].lowercase()
            var normAmount = amount
            var normUnit = unit
            when (unit) {
                "kg" -> { normAmount = amount * 1000; normUnit = "g" }
                "l" -> { normAmount = amount * 1000; normUnit = "ml" }
                "gr" -> normUnit = "g"
            }
            if (normAmount > 0) {
                item.sizeText = m.value.uppercase().replace(" ", "")
                item.sizeAmount = normAmount
                item.sizeUnit = normUnit
            }
        }
        return item
    }

    /** Parse teks OCR multi-baris; gabungkan baris nama + baris harga bila terpisah. */
    fun parseText(text: String): List<ParsedItem> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val out = mutableListOf<ParsedItem>()
        var pendingName: String? = null
        for (line in lines) {
            if (skipLine.containsMatchIn(line)) { pendingName = null; continue }
            if (dateTimeTok.containsMatchIn(line)) { pendingName = null; continue }
            val deSku = skuTok.replace(line, "")  // kode SKU bukan harga
            val hp = hasPrice(deSku)
            val letters = deSku.filter { it.isLetter() }.length
            if (!hp && letters >= 2) {
                // baris nama kandidat — timpa yg lama (header toko ikut tertimpa)
                pendingName = deSku
                continue
            }
            if (hp) {
                var work = line
                // baris murni harga/qty ("2 X 18.500  37.000") -> gabung dgn nama sebelumnya
                if (pendingName != null && letters < 3) work = "$pendingName $line"
                pendingName = null
                parseLine(work)?.let { out.add(it) }
            } else {
                pendingName = null
            }
        }
        return out
    }
}
