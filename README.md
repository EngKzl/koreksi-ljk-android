# Koreksi LJK — Android (Kotlin)

Aplikasi Android untuk mengoreksi lembar jawaban (LJK) pilihan ganda A–E dari foto/scan. Dibuat untuk SMKS PGRI 2 Jambi.

- Mesin pembaca LJK (OMR) ditulis sendiri dalam Kotlin, tanpa library pihak ketiga, dan berjalan **offline**.
- Aturan: jawaban sah = tanda **silang (X)**; batal = tanda **=** di atas silang atau kotak dihitamkan; lebih dari satu silang = **GANDA** (salah).
- Fitur: ujian & kunci per paket, cetak LJK (PDF), koreksi foto/scan massal, koreksi manual, rekap & Excel, analisis butir (P, D, pengecoh, KR-20), cadangan data (kompatibel dengan versi web).

## Pembaruan otomatis
Setiap push ke `main` → GitHub Actions menguji mesin OMR, membangun APK bertanda tangan, dan menerbitkan **GitHub Release**.
Worker Cloudflare `koreksi-ljk` menyediakan `https://ljk.pak-eng.net/api/android/latest` (info rilis terbaru) dan
`https://ljk.pak-eng.net/unduh` (APK terbaru). Aplikasi mengecek endpoint itu saat dibuka dan menawarkan pembaruan.

## Kunci tanda tangan
Secret repo `KEYSTORE_B64` berisi file `release.jks` (base64). Simpan file kunci aslinya baik-baik: tanpa kunci yang sama,
versi baru tidak bisa dipasang menimpa versi lama.
