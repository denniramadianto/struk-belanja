# 🧾 Struk Belanja

**Foto struk → tersimpan rapi → bandingkan harga kapan saja.**

Struk Belanja adalah aplikasi Android untuk mencatat daftar belanjaan langsung dari **foto struk/nota**.
Cukup foto struk setelah belanja, aplikasi membaca isinya (OCR), kamu koreksi bila perlu, lalu simpan.
Bulan depan saat belanja lagi, tinggal **cari nama barang** — misal *"Pepsodent Sensitive Expert"* —
dan langsung terlihat **daftar harga 1–3 bulan terakhir** beserta perbandingannya (naik/turun, beda toko)
lengkap dengan **ukuran kemasan (ml/gram)**.

> 💡 Dibuat untuk menjawab pertanyaan klasik: *"Bulan lalu beli ini harganya berapa ya? Naik atau turun?"*

---

## ✨ Fitur

> ⚠️ **Status v1.0:** APK sudah berhasil di-build dan lolos verifikasi signature.
> Belum diuji di HP fisik — kabari kalau ada yang force close / OCR tidak jalan,
> sertakan merek HP & versi Android.

| Fitur | Keterangan |
|---|---|
| 📷 **Foto struk** | Ambil foto langsung dari kamera atau pilih dari galeri |
| 🔍 **OCR offline** | Isi struk dibaca otomatis di HP tanpa internet (ML Kit on-device) |
| ✏️ **Koreksi manual** | Hasil bacaan bisa diedit/ditambah/dihapus sebelum disimpan |
| 💾 **Simpan offline** | Semua data tersimpan di database HP (SQLite), tanpa akun |
| 🔎 **Pencarian barang** | Cari nama barang → riwayat harga 3 bulan terakhir |
| 📊 **Perbandingan harga** | Harga termurah, termahal, rata-rata, tren naik/turun, beda toko |
| 📏 **Ukuran kemasan** | Ukuran ml/gram ikut terbaca & bisa dibandingkan (harga per 100 g/ml) |
| 📤 **Ekspor CSV** | Unduh seluruh data belanja sebagai CSV |
| ✈️ **Backup Telegram** | Kirim file cadangan ke Telegram sekali ketuk |
| ☁️ **Sync Google Drive** | *(roadmap)* Backup & sinkron otomatis via Google Drive |

---

## 🚀 Cara Pakai

1. Buka aplikasi, ketuk **＋ Foto Struk**.
2. Foto struk belanjaan (usahakan lurus & terang).
3. Ketuk **Baca Struk** — tunggu beberapa detik sampai daftar barang muncul.
4. Koreksi nama, jumlah, dan harga bila ada yang salah baca, isi nama toko.
5. Ketuk **Simpan**. Selesai! 🎉

**Cek harga barang:**
1. Ketik nama barang di kolom pencarian (misal: `pepsodent`).
2. Pilih barang → muncul riwayat pembelian 3 bulan terakhir:
   tanggal, toko, harga, ukuran, plus ringkasan termurah/termahal/tren.

Detail panduan ada di [docs/PANDUAN.md](docs/PANDUAN.md).

---

## 🛠️ Teknologi

- **Bahasa:** Kotlin
- **Build:** Manual tanpa Gradle — pipeline `aapt2 → kotlinc → d8 → zipalign → apksigner` via `build-apk.sh`
- **OCR:** ML Kit Text Recognition v2 (bundled, on-device — tanpa internet, tanpa Play Services)
- **Database:** SQLite (via `android.database.sqlite`)
- **Min SDK:** 26 (Android 8.0) · **Target SDK:** 35 (Android 15)

Arsitektur & skema database: [docs/ARSITEKTUR.md](docs/ARSITEKTUR.md)

---

## 🏗️ Cara Build

```bash
./build-apk.sh [nama-output.apk]
```

Keystore rilis **tidak** ikut ke repo (lihat `.gitignore`). Build memakai keystore lokal milik developer.

---

## 🗺️ Roadmap

- [x] v1.0 — Foto struk, OCR offline, koreksi & simpan, pencarian + riwayat harga 3 bulan
- [ ] v1.1 — Sinkronisasi Google Drive (backup otomatis)
- [ ] v1.2 — Mode OCR online (opsional, akurasi lebih tinggi)
- [ ] v1.3 — Statistik belanja bulanan & barang yang paling sering naik harga

---

## 🔒 Privasi

Seluruh data belanja **hanya tersimpan di HP kamu**. Tidak ada akun, tidak ada server, tidak ada iklan.
Foto struk dan database tidak dikirim ke mana pun kecuali kamu sendiri yang menekan
tombol Ekspor / Backup Telegram / Sync Drive.

---

## 📄 Lisensi

MIT — lihat [LICENSE](LICENSE).

Copyright © 2026 DENNI
