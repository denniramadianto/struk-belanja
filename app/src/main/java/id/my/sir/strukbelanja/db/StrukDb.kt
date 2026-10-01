package id.my.sir.strukbelanja.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Receipt(
    val id: Long, val store: String, val date: String,
    val total: Long, val photoPath: String?, val itemCount: Int = 0
)

data class Item(
    val id: Long = 0, val receiptId: Long = 0,
    val name: String, val qty: Double, val unitPrice: Long,
    val sizeText: String = "", val sizeAmount: Double = 0.0, val sizeUnit: String = "",
    val date: String = "", val store: String = ""
)

class StrukDb(ctx: Context) : SQLiteOpenHelper(ctx, "strukbelanja.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE receipts(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            store TEXT NOT NULL DEFAULT '',
            date TEXT NOT NULL DEFAULT '',
            total INTEGER NOT NULL DEFAULT 0,
            photo_path TEXT DEFAULT '',
            created_at INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE items(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            receipt_id INTEGER NOT NULL,
            name TEXT NOT NULL,
            name_norm TEXT NOT NULL,
            qty REAL NOT NULL DEFAULT 1,
            unit_price INTEGER NOT NULL DEFAULT 0,
            size_text TEXT NOT NULL DEFAULT '',
            size_amount REAL NOT NULL DEFAULT 0,
            size_unit TEXT NOT NULL DEFAULT '',
            created_at INTEGER NOT NULL)""")
        db.execSQL("CREATE INDEX idx_items_norm ON items(name_norm)")
        db.execSQL("CREATE INDEX idx_items_receipt ON items(receipt_id)")
        db.execSQL("CREATE INDEX idx_receipts_date ON receipts(date)")
    }

    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}

    companion object {
        fun norm(s: String): String =
            s.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
    }

    fun insertReceipt(store: String, date: String, total: Long, photoPath: String?): Long {
        val cv = ContentValues().apply {
            put("store", store); put("date", date); put("total", total)
            put("photo_path", photoPath ?: ""); put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insert("receipts", null, cv)
    }

    fun insertItem(receiptId: Long, name: String, qty: Double, unitPrice: Long,
                   sizeText: String, sizeAmount: Double, sizeUnit: String) {
        val cv = ContentValues().apply {
            put("receipt_id", receiptId); put("name", name); put("name_norm", norm(name))
            put("qty", qty); put("unit_price", unitPrice)
            put("size_text", sizeText); put("size_amount", sizeAmount); put("size_unit", sizeUnit)
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insert("items", null, cv)
    }

    fun getReceipts(): List<Receipt> {
        val out = mutableListOf<Receipt>()
        readableDatabase.rawQuery(
            """SELECT r.id, r.store, r.date, r.total, r.photo_path,
                      (SELECT COUNT(*) FROM items i WHERE i.receipt_id = r.id)
               FROM receipts r ORDER BY r.date DESC, r.id DESC""", null).use { c ->
            while (c.moveToNext()) out.add(Receipt(
                c.getLong(0), c.getString(1), c.getString(2),
                c.getLong(3), c.getString(4).ifEmpty { null }, c.getInt(5)))
        }
        return out
    }

    fun getReceipt(id: Long): Receipt? {
        readableDatabase.rawQuery(
            "SELECT id, store, date, total, photo_path FROM receipts WHERE id=?",
            arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) return Receipt(
                c.getLong(0), c.getString(1), c.getString(2), c.getLong(3),
                c.getString(4).ifEmpty { null })
        }
        return null
    }

    fun getItems(receiptId: Long): List<Item> {
        val out = mutableListOf<Item>()
        readableDatabase.rawQuery(
            """SELECT i.id, i.name, i.qty, i.unit_price, i.size_text,
                      i.size_amount, i.size_unit, r.date, r.store
               FROM items i JOIN receipts r ON r.id = i.receipt_id
               WHERE i.receipt_id=? ORDER BY i.id""",
            arrayOf(receiptId.toString())).use { c ->
            while (c.moveToNext()) out.add(Item(
                c.getLong(0), receiptId, c.getString(1), c.getDouble(2), c.getLong(3),
                c.getString(4) ?: "", c.getDouble(5), c.getString(6) ?: "",
                c.getString(7), c.getString(8)))
        }
        return out
    }

    fun deleteReceipt(id: Long): String? {
        var photo: String? = null
        getReceipt(id)?.photoPath?.let { photo = it }
        writableDatabase.delete("items", "receipt_id=?", arrayOf(id.toString()))
        writableDatabase.delete("receipts", "id=?", arrayOf(id.toString()))
        return photo
    }

    /** Cari barang: cocokkan tiap kata kunci ke name_norm. */
    fun searchItems(query: String, limit: Int = 50): List<Item> {
        val words = norm(query).split(" ").filter { it.length >= 2 }
        if (words.isEmpty()) return emptyList()
        // tahap 1: semua kata harus cocok (AND) — presisi tinggi utk singkatan "PEPSO SENS EXP"
        val andResult = searchItemsWhere(
            words.joinToString(" AND ") { "i.name_norm LIKE ?" },
            words.map { "%$it%" }.toTypedArray(), limit)
        if (andResult.isNotEmpty() || words.size == 1) return andResult
        // tahap 2: bila AND tidak kena (urutan kata beda / ada kata ekstra di struk),
        // pakai OR, urut by jumlah kata cocok lalu tanggal terbaru
        val orWhere = words.joinToString(" OR ") { "i.name_norm LIKE ?" }
        val score = words.joinToString(" + ") { "(CASE WHEN i.name_norm LIKE '%$it%' THEN 1 ELSE 0 END)" }
        return searchItemsWhere("($orWhere)", words.map { "%$it%" }.toTypedArray(), limit, score)
    }

    private fun searchItemsWhere(where: String, args: Array<String>, limit: Int,
                                 scoreExpr: String? = null): List<Item> {
        val orderBy = if (scoreExpr != null) "($scoreExpr) DESC, MAX(r.date) DESC" else "MAX(r.date) DESC"
        val out = mutableListOf<Item>()
        // satu baris per barang unik: ambil pembelian terakhir
        readableDatabase.rawQuery(
            """SELECT i.name, i.name_norm, MAX(r.date),
                      (SELECT i2.unit_price FROM items i2 JOIN receipts r2 ON r2.id=i2.receipt_id
                        WHERE i2.name_norm=i.name_norm ORDER BY r2.date DESC, r2.id DESC LIMIT 1),
                      (SELECT i2.size_text FROM items i2 JOIN receipts r2 ON r2.id=i2.receipt_id
                        WHERE i2.name_norm=i.name_norm ORDER BY r2.date DESC, r2.id DESC LIMIT 1)
               FROM items i JOIN receipts r ON r.id=i.receipt_id
               WHERE $where GROUP BY i.name_norm ORDER BY $orderBy LIMIT $limit""",
            args).use { c ->
            while (c.moveToNext()) out.add(Item(
                name = c.getString(0), qty = 1.0, unitPrice = c.getLong(3),
                sizeText = c.getString(4) ?: "", date = c.getString(2)))
        }
        return out
    }

    /** Riwayat pembelian satu barang (name_norm), 90 hari terakhir, terbaru dulu. */
    fun getHistory(nameNorm: String, days: Int = 90): List<Item> {
        val out = mutableListOf<Item>()
        readableDatabase.rawQuery(
            """SELECT i.name, i.qty, i.unit_price, i.size_text, i.size_amount,
                      i.size_unit, r.date, r.store
               FROM items i JOIN receipts r ON r.id=i.receipt_id
               WHERE i.name_norm=? AND r.date >= date('now','-$days days')
               ORDER BY r.date DESC, r.id DESC""",
            arrayOf(nameNorm)).use { c ->
            while (c.moveToNext()) out.add(Item(
                name = c.getString(0), qty = c.getDouble(1), unitPrice = c.getLong(2),
                sizeText = c.getString(3) ?: "", sizeAmount = c.getDouble(4),
                sizeUnit = c.getString(5) ?: "", date = c.getString(6), store = c.getString(7)))
        }
        return out
    }

    fun dbFile(ctx: Context) = ctx.getDatabasePath("strukbelanja.db")
}
