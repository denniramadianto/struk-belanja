package id.my.sir.strukbelanja.util

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import id.my.sir.strukbelanja.db.StrukDb
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object BackupUtil {

    /** Ekspor seluruh data ke CSV di folder Download, lalu tawarkan bagikan. */
    fun exportCsv(act: Activity, db: StrukDb) {
        Thread {
            try {
                val nama = "struk-belanja-${Format.today()}.csv"
                val resolver = act.contentResolver
                val cv = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, nama)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/csv")
                }
                val uri = resolver.insert(
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                    ?: throw Exception("gagal membuat file")
                resolver.openOutputStream(uri)?.use { out ->
                    OutputStreamWriter(out, Charsets.UTF_8).use { w ->
                        w.write("tanggal,toko,nama_barang,jumlah,harga_satuan,ukuran,subtotal\n")
                        for (r in db.getReceipts()) {
                            for (it in db.getItems(r.id)) {
                                val sub = (it.qty * it.unitPrice).toLong()
                                w.write("\"${r.date}\",\"${r.store}\",\"${it.name}\",${it.qty},${it.unitPrice},\"${it.sizeText}\",$sub\n")
                            }
                        }
                    }
                }
                act.runOnUiThread {
                    Toast.makeText(act, "CSV tersimpan di Download/$nama", Toast.LENGTH_LONG).show()
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    act.startActivity(Intent.createChooser(share, "Bagikan CSV"))
                }
            } catch (e: Exception) {
                act.runOnUiThread {
                    Toast.makeText(act, "Gagal ekspor: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    /** Kirim file database sebagai dokumen ke Telegram via bot. */
    fun backupTelegram(act: Activity, db: StrukDb) {
        val pref = act.getSharedPreferences("strukbelanja", Context.MODE_PRIVATE)
        val lay = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 20)
        }
        val etToken = EditText(act).apply {
            hint = "Token bot (dari @BotFather)"
            setText(pref.getString("tg_token", ""))
        }
        val etChat = EditText(act).apply {
            hint = "Chat ID kamu"
            setText(pref.getString("tg_chat", ""))
        }
        lay.addView(etToken); lay.addView(etChat)
        AlertDialog.Builder(act)
            .setTitle("✈️ Backup ke Telegram")
            .setMessage("Buat bot sekali via @BotFather, tempel tokennya di sini.")
            .setView(lay)
            .setPositiveButton("Kirim") { _, _ ->
                val token = etToken.text.toString().trim()
                val chat = etChat.text.toString().trim()
                if (token.isEmpty() || chat.isEmpty()) {
                    Toast.makeText(act, "Token & chat ID wajib diisi", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                pref.edit().putString("tg_token", token).putString("tg_chat", chat).apply()
                kirim(act, db, token, chat)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun kirim(act: Activity, db: StrukDb, token: String, chat: String) {
        Toast.makeText(act, "Mengirim backup…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val dbFile = db.dbFile(act)
                val boundary = "----StrukBelanja${System.currentTimeMillis()}"
                val url = URL("https://api.telegram.org/bot$token/sendDocument")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    connectTimeout = 30000; readTimeout = 60000
                }
                conn.outputStream.use { out ->
                    fun part(name: String, value: String) {
                        out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
                    }
                    part("chat_id", chat)
                    part("caption", "Backup Struk Belanja ${Format.today()}")
                    out.write(("--$boundary\r\nContent-Disposition: form-data; name=\"document\"; filename=\"strukbelanja.db\"\r\nContent-Type: application/octet-stream\r\n\r\n").toByteArray())
                    dbFile.inputStream().use { it.copyTo(out) }
                    out.write("\r\n--$boundary--\r\n".toByteArray())
                }
                val ok = conn.responseCode in 200..299
                act.runOnUiThread {
                    Toast.makeText(act,
                        if (ok) "Backup terkirim! ✅" else "Gagal (${conn.responseCode})",
                        Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                act.runOnUiThread {
                    Toast.makeText(act, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}
