# Camera & Video App

Aplikasi Android sederhana untuk mengambil foto dan merekam video menggunakan kamera perangkat. Aplikasi dibuat dengan Kotlin, AndroidX CameraX, dan tampilan berbasis XML dengan View Binding.

## Fitur

- Menampilkan pratinjau kamera.
- Mengambil foto dan menyimpannya ke folder `DCIM/Camera`.
- Merekam dan menghentikan video, dengan indikator durasi rekaman.
- Beralih antara kamera depan dan belakang.
- Mengatur kualitas video untuk masing-masing kamera. Pilihan resolusi disesuaikan dengan kualitas yang didukung perangkat.
- Menampilkan thumbnail media terakhir dan membuka media melalui aplikasi galeri.
- Meminta izin kamera, mikrofon, dan akses media sesuai versi Android.

## Struktur Proyek

```text
.
|-- app/
|   `-- src/
|       |-- main/
|       |   |-- java/com/example/tugas6/MainActivity.kt  # Logika kamera, foto, video, galeri, dan izin
|       |   |-- res/layout/                              # Layout layar utama dan dialog kualitas video
|       |   |-- res/drawable/                            # Ikon dan drawable antarmuka
|       |   |-- res/values/                              # String, warna, dan tema
|       |   `-- AndroidManifest.xml                      # Activity, fitur perangkat, dan izin
|       |-- test/                                        # Unit test
|       `-- androidTest/                                 # Instrumentation test
|-- gradle/                                              # Wrapper dan katalog versi dependensi
|-- build.gradle.kts                                     # Konfigurasi Gradle tingkat proyek
`-- settings.gradle.kts                                  # Nama proyek dan modul Gradle
```

Direktori `build/`, `.gradle/`, `.idea/`, dan file lokal Android Studio merupakan hasil kerja atau konfigurasi lokal dan tidak diperlukan sebagai sumber aplikasi.

## Teknologi

- Kotlin dan Android SDK
- AndroidX CameraX (Camera2, Lifecycle, Video, View)
- AndroidX AppCompat dan Activity
- Material Components dan ConstraintLayout
- Gradle Kotlin DSL

## Menjalankan Aplikasi

1. Buka proyek ini di Android Studio dan tunggu sinkronisasi Gradle selesai.
2. Hubungkan perangkat Android atau jalankan emulator yang memiliki kamera.
3. Jalankan konfigurasi `app`.
4. Berikan izin kamera dan mikrofon saat diminta. Izin media juga digunakan untuk menampilkan media terakhir di galeri.

Persyaratan SDK proyek: `minSdk 24`, `targetSdk 35`, dan `compileSdk 36`. Rekaman dan foto disimpan melalui `MediaStore` di `DCIM/Camera`.

