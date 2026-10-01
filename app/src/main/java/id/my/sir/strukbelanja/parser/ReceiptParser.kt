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
 */
object ReceiptParser {

    private val priceTok = Regex("""(?i)(?:rp\s*)?(\d{1,3}(?:\.\d{3})+|\d+)(?:,\d{2})?\b""")
    private val qtyTok = Regex("""(?i)\b(\d+(?:[.,]\d+)?)\s*[xX]\b|\b[xX]\s*(\d+(?:[.,]\d+)?)\b""")
    private val sizeTok = Regex("""(?i)\b(\d+(?:[.,]\d+)?)\s?(ml|l|gr|g|kg)\b""")
    private val skipLine = Regex("""(?i)\b(total|tunai|cash|kembali|kembalian|disc|diskon|potongan|ppn|pajak|subtotal|
        |bayar|debit|kredit|kartu|voucher|promo|hemat|selamat|terima kasih|belanja|kasir|struk|nota|
        |tanggal|jam|no\.|nomor|item|qty|harga|jumlah|toko|alamat|telp|npwp)\b""".replace("\n", ""))

    fun parseLine(raw: String): ParsedItem? {
        var line = raw.trim()
        if (line.length < 3) return null
        if (skipLine.containsMatchIn(line)) return null

        // kumpulkan semua token harga (qty dihapus dulu supaya "2" tidak terbaca sbg harga
        // dan "X" tidak yatim di nama)
        var work = line
        // qty: pola "2 X" / "2x" / "X2" — dari baris asli
        var qty = 1.0
        qtyTok.find(work)?.let { m ->
            val q = m.groupValues[1].ifEmpty { m.groupValues[2] }.replace(",", ".").toDoubleOrNull()
            if (q != null && q > 0 && q < 1000) qty = q
        }
        work = qtyTok.replace(work, " ")
        val prices = priceTok.findAll(work).map { m ->
            m.groupValues[1].replace(".", "").toLongOrNull() ?: 0L
        }.filter { it > 0 }.toList()
        if (prices.isEmpty()) return null

        // harga satuan: bila ada >=2 harga, ambil yang kedua dari belakang
        // (pola umum: "2 X 18.500  37.000" -> 18.500 harga satuan, 37.000 total).
        // Bila 1 harga + pola qty ("3x 3.200") -> harga itu HARGA SATUAN
        // (konvensi struk Indonesia: "3x 3.200" = @3.200).
        val unitPrice: Long = if (prices.size >= 2) prices[prices.size - 2] else prices[0]
        if (unitPrice <= 0) return null

        // nama = baris tanpa token harga & qty
        var name = work
        priceTok.replace(name, " ").let { name = it }
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
            val hasPrice = priceTok.containsMatchIn(line)
            val letters = line.filter { it.isLetter() }.length
            if (!hasPrice && letters >= 2) {
                // baris nama kandidat — timpa yg lama (header toko ikut tertimpa)
                pendingName = line
                continue
            }
            if (hasPrice) {
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
