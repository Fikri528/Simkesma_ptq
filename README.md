# SIMKESMA UKM-PTQ

Sistem Informasi Kesekretariatan Mahasiswa UKM-PTQ (Universitas Malikussaleh).
Kotlin • XML Layout • Material 3 • MVVM sederhana • Firebase Authentication + Realtime Database • ZXing QR.

> Status: kode ditulis lengkap tetapi **belum pernah di-build/diuji di Android Studio oleh pembuatnya**.
> Jalankan Build → Rebuild Project; bila ada error kompilasi kecil, kirim log-nya untuk diperbaiki.

## 1. Menjalankan
1. Buka folder ini di Android Studio (Koala atau lebih baru, JDK 17). Tunggu Gradle sync.
2. Di Firebase Console buat project, lalu **Add app → Android** dengan package `id.ac.unimal.simkesma`.
3. Unduh `google-services.json` dan **timpa** file placeholder `app/google-services.json`.
4. Aktifkan **Authentication → Sign-in method → Email/Password**.
5. Buat **Realtime Database**, lalu tempel isi `database.rules.json` di tab *Rules* dan Publish.
6. Run di perangkat/emulator (minSdk 29 / Android 10).

## 2. Akun admin
- Admin ditentukan oleh email `radikafikrigo2020@gmail.com` (konstanta `ADMIN_EMAIL` di `util/Utils.kt` dan di `database.rules.json`).
- Daftarkan akun itu lewat layar **Daftar Anggota** di aplikasi (atau buat di Firebase Auth). Saat pertama masuk, aplikasi membuat profil `users/{uid}` dengan `role = admin`.
- Kata sandi **tidak** disimpan di kode atau database. Ganti kata sandi yang pernah dibagikan lewat chat.
- Admin dapat tambah/ubah/hapus semua data. Anggota biasa hanya dapat membaca, konfirmasi hadir rapat, presensi, dan mengubah profil sendiri.
- Registrasi anggota langsung aktif (tanpa persetujuan admin).
- Saat admin pertama membuka Dashboard, 7 divisi awal dibuat otomatis jika node `divisi` masih kosong: Agama, Humas, Infokom, Kaderisasi, Kesekretariatan, Kewirausahaan, Pelatihan dan Pendidikan.

## 3. Konten yang bisa diedit admin
Pengumuman, "Tadabbur Singkat" (ikon pensil di Dashboard), periode kepengurusan, anggota, divisi, surat, rapat/notula, program kerja, arsip.

## 4. Penyimpanan berkas
Tanpa Firebase Storage (butuh paket Blaze). Berkas (surat, PDF, arsip) disimpan sebagai Base64 di `files/{id}` pada Realtime Database, **maksimal 700 KB per berkas**. Unduhan tersimpan di `Download/SIMKESMA`. Untuk berkas besar, ganti `Repo.saveFile/loadFile` dengan Firebase Storage.

## 5. QR Presensi
Admin membuka detail rapat → **Tampilkan QR Presensi**. Anggota: Kesekretariatan → Presensi QR → **Pindai**. Jika izin kamera ditolak, tampil penjelasan, tombol Pengaturan, dan alternatif input kode manual.

## 6. Signed APK + distribusi
1. Android Studio: Build → Generate Signed Bundle / APK → APK → buat keystore baru (simpan aman, jangan di-commit).
2. Hasil: `app/release/app-release.apk`.
3. Distribusi: GitHub → Releases → Draft new release → lampirkan APK; atau Firebase App Distribution.

## 7. Struktur database
```
users/{uid}              nama, nim, email, divisi, telepon, role
anggota/{id}             namaLengkap, nim, email, telepon, divisi, jabatan, status, createdAt
divisi/{id}              nama, deskripsi, ketua, sekretaris
surat_masuk/{id}         nomorSurat, pengirim, perihal, tanggal, kategori, disposisi, lampiranNama
surat_keluar/{id}        nomorSurat, tujuan, perihal, tanggal, kategori, status, statusTtd, lampiranNama
rapat/{id}               judul, jenis, tanggal, jam, lokasi, pemimpin, kewajiban, agenda, status
rapat/{id}/peserta/{uid} nama (konfirmasi hadir)
notula/{rapatId}         isi, penulis
program_kerja/{id}       nama, deskripsi, divisi, tanggal, anggaran, progres, status
kehadiran/{id}           uid, nama, rapatId, rapatJudul, lokasi, waktu, metode, status
arsip_dokumen/{id}       judul, kategori, tanggal, deskripsi, berkasNama, uploaderNama
pengumuman/{id}          judul, isi, tanggal
pengaturan/periode, pengaturan/tadabbur{teks,sumber}
files/{id}               nama, mime, data(Base64)
```
Struktur organisasi dihitung dari `anggota.jabatan` + `anggota.divisi` + `pengaturan/periode`.

## 8. Catatan desain
Font memakai sans-serif sistem (referensi memakai Plus Jakarta Sans; tambahkan lewat *Downloadable Fonts* bila ingin persis). Logo UKM-PTQ dipakai sebagai ikon aplikasi, header, login, dan empty state; ikon fungsional (cari, tambah, dll.) tetap ikon Material.
