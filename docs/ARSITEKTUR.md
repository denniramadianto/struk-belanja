# 🏛️ Arsitektur Struk Belanja

Dokumen ini menjelaskan cara kerja aplikasi dari sisi teknis.

## Alur kerja aplikasi

```
┌──────────┐    ┌──────────┐    ┌───────────┐    ┌────────────┐    ┌─────────┐
│  Kamera / │───▶│  OCR     │───▶│  Parser   │───▶│  Koreksi   │───▶│ SQLite  │
│  Galeri   │    │ (ML Kit) │    │  struk    │    │  manual    │    │  (HP)   │
└──────────┘    └──────────┘    └───────────┘    └────────────┘    └────┬────┘
                                                                      │
                              ┌──────────────┐                        │
                              │ Pencarian    │◀───────────────────────┘
                              │ barang       │
                              └──────┬───────┘
                                     ▼
                         Riwayat harga 3 bulan
                         + perbandingan + ukuran
```

1. **Foto** — `ACTION_IMAGE_CAPTURE` (aplikasi kamera bawaan HP) atau pilih dari galeri.
   Foto disimpan di penyimpanan internal aplikasi.
2. **OCR** — ML Kit Text Recognition v2 **bundled**: model OCR ikut di dalam APK,
   jalan 100% di HP tanpa internet dan tanpa Google Play Services.
3. **Parser struk** — teks mentah diubah jadi daftar barang lewat heuristic khusus
   format struk Indonesia (lihat bawah).
4. **Koreksi manual** — pengguna memeriksa & memperbaiki hasil parse sebelum disimpan.
   Ini lapisan akurasi terakhir yang paling penting.
5. **Penyimpanan** — SQLite lokal, tanpa server.

## Skema database

```sql
receipts
  id          INTEGER PRIMARY KEY
  store       TEXT            -- nama toko (mis. "Indomaret")
  date        TEXT            -- tanggal belanja, format YYYY-MM-DD
  total       INTEGER         -- total struk dalam rupiah
  photo_path  TEXT            -- lokasi foto struk di penyimpanan internal
  created_at  INTEGER         -- timestamp simpan

items
  id          INTEGER PRIMARY KEY
  receipt_id  INTEGER         -- FK ke receipts.id
  name        TEXT            -- nama barang apa adanya dari struk
  name_norm   TEXT            -- nama ternormalisasi utk pencarian (lowercase, spasi tunggal)
  qty         REAL            -- jumlah
  unit_price  INTEGER         -- harga satuan dalam rupiah
  size_text   TEXT            -- ukuran mentah, mis. "110G", "250ML"
  size_amount REAL            -- angka ukuran ternormalisasi
  size_unit   TEXT            -- 'g' atau 'ml' (kg→g, l→ml dikonversi)
  created_at  INTEGER
```

**Pencarian barang** memakai `name_norm` dengan `LIKE '%kata%'` (case-insensitive),
sehingga `pepsodent` cocok dengan `PEPSODENT SENSITIF 110G`.

**Perbandingan harga** dihitung dari `unit_price` per pembelian dalam 90 hari terakhir:
termurah, termahal, rata-rata, harga terakhir, dan selisih vs pembelian sebelumnya
(naik/turun dalam % dan rupiah). Harga per 100 g/ml dihitung bila `size_amount` ada.

## Parser struk Indonesia

Struk Indonesia umumnya punya pola baris seperti:

```
PEPSODENT SENSITIF 110G
2 X 18.500                    37.000
```

atau satu baris:

```
INDOMIE GORENG 85G  3x 3.200
```

Heuristic parser (`ReceiptParser.kt`):

1. Setiap baris dipecah jadi token; token harga dikenali dari pola angka
   (`18.500`, `37.000`, `Rp 25.900`).
2. Pola jumlah: `2 X`, `2x`, `X2`, `QTY 2`.
3. Sisa token = nama barang.
4. Ukuran diekstrak dari nama via regex: `(\d+(?:[.,]\d+)?)\s?(ml|l|gr|g|kg)\b`
   → dinormalisasi ke gram/ml.
5. Baris total/kembalian/diskon (`TOTAL`, `TUNAI`, `KEMBALI`, `DISC`) diabaikan.

Parser bersifat *best-effort* — hasil yang meragukan ditandai agar mudah dikoreksi
di layar edit. Akurasi akhir dijamin oleh langkah koreksi manual.

## Struktur proyek

```
struk-belanja/
├── app/src/main/
│   ├── AndroidManifest.xml
│   ├── java/id/my/sir/strukbelanja/
│   │   ├── MainActivity.kt        # beranda: daftar struk + pencarian barang
│   │   ├── ScanActivity.kt        # foto → OCR
│   │   ├── EditItemsActivity.kt   # koreksi hasil parse → simpan
│   │   ├── ReceiptDetailActivity.kt
│   │   ├── ItemHistoryActivity.kt # riwayat harga + perbandingan
│   │   ├── db/StrukDb.kt          # SQLite helper + skema
│   │   ├── ocr/MlKitOcr.kt        # wrapper ML Kit
│   │   └── parser/ReceiptParser.kt
│   └── res/{layout,values,drawable,xml}/
├── libs/                  # AAR ML Kit (diunduh via download-deps.sh, tidak di-commit)
├── build-apk.sh           # pipeline build manual tanpa Gradle
├── download-deps.sh       # unduh dependensi dari Maven
└── docs/
```

## Build manual (tanpa Gradle)

Pipeline: `aapt2 compile → aapt2 link → kotlinc → d8 → zipalign → apksigner`
(lihat `build-apk.sh`). Dependensi AAR diekstrak: `classes.jar` masuk classpath
kotlinc & input d8, `res/` digabung saat `aapt2 compile`, `.so` disalin ke `lib/`
di dalam APK.
