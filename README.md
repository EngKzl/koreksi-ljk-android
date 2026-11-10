# Koreksi LJK — Android (Kotlin)

Aplikasi Android untuk mengoreksi lembar jawaban (LJK) pilihan ganda A–E dari foto/scan. Dibuat untuk SMKS PGRI 2 Jambi.

- Membaca **LJK asli sekolah** (dua tabel 25 baris × A–E, soal 1–50) langsung dari foto/scan, tanpa penanda khusus.
- Mesin pembaca (OMR) ditulis sendiri dalam Kotlin, tanpa library pihak ketiga, dan berjalan **offline**.
- Aturan: jawaban = huruf yang **disilang (X)** atau **dilingkari**; silang yang **dicoret garis mendatar** = dibatalkan; lebih dari satu jawaban = **GANDA** (salah); tanpa jawaban = kosong (salah).
- Rekap **per kelas** (X/XI/XII · TSM, TKR, DKV, MP, AK, BR · rombel). Nama siswa diisi guru setelah scan.
- Fitur lain: kunci per paket (ketik atau pindai), koreksi manual per soal, Excel, analisis butir (P, D, pengecoh, KR-20), cadangan data.

## Web + Android, satu rilis
- `app/` = aplikasi Android (Kotlin). `web/` = versi web (JavaScript) dengan mesin baca yang sama (`web/omr.js` adalah port dari `omr/Omr.kt` — ubah keduanya bersamaan).
- Setiap push ke `main` → GitHub Actions menguji mesin OMR, membangun APK dan web, lalu menerbitkan **satu GitHub Release** `v1.0.N` berisi `KoreksiLJK.apk` + berkas web. Keduanya menampilkan `© Pak-eng_v1.0.N` yang sama.
- Worker Cloudflare `koreksi-ljk` (lihat `worker.js`) tidak menyimpan isi web: ia mengambil berkas web dari rilis terbaru, jadi **https://ljk.pak-eng.net** ikut diperbarui otomatis. Ia juga menyediakan `/api/android/latest` (dicek aplikasi saat dibuka) dan `/unduh` (APK terbaru).

## Kunci tanda tangan
File `app/release.jks` (alias `ljk`, kata sandi `koreksiljk`) disimpan di repo atas persetujuan pemilik; secret `KEYSTORE_B64` (opsional) akan menggantikannya. Simpan file kunci aslinya baik-baik: tanpa kunci yang sama,
versi baru tidak bisa dipasang menimpa versi lama.
