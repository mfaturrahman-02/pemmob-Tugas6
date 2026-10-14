package com.example.tugas6

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import com.example.tugas6.databinding.ActivityMainBinding
import com.example.tugas6.databinding.DialogVideoQualityBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var currentMode = CameraMode.PHOTO

    // Pilihan kualitas video terkonfigurasi (Default: Back = 1080p, Front = 720p)
    private var backCameraQuality: Quality = Quality.FHD
    private var frontCameraQuality: Quality = Quality.HD

    private var lastCapturedUri: Uri? = null

    // Handler untuk Timer durasi rekaman video
    private var recordingStartTime = 0L
    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            val elapsedMs = System.currentTimeMillis() - recordingStartTime
            val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedMs) % 60
            val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMs) % 60
            val hours = TimeUnit.MILLISECONDS.toHours(elapsedMs)

            val timeString = if (hours > 0) {
                String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
            }

            binding.tvRecordingTimer.text = timeString
            timerHandler.postDelayed(this, 1000)
        }
    }

    private enum class CameraMode {
        PHOTO, VIDEO
    }

    // Peluncur Request Runtime Permissions
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var allGranted = true
            permissions.entries.forEach { entry ->
                if ((entry.key in getRequiredPermissions()) && !entry.value) {
                    allGranted = false
                }
            }

            if (allGranted) {
                startCamera()
                loadLastMediaFromGallery()
            } else {
                Toast.makeText(
                    this,
                    "Izin Kamera dan Mikrofon diperlukan untuk menggunakan aplikasi",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        updateQualityIndicator()

        if (allPermissionsGranted()) {
            startCamera()
            loadLastMediaFromGallery()
        } else {
            requestPermissionsLauncher.launch(getRequiredPermissions())
        }
    }

    private fun setupListeners() {
        // Mode Switcher (Foto / Video)
        binding.tvModePhoto.setOnClickListener {
            if (recording != null) {
                Toast.makeText(this, "Hentikan rekaman terlebih dahulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            switchMode(CameraMode.PHOTO)
        }

        binding.tvModeVideo.setOnClickListener {
            if (recording != null) return@setOnClickListener
            switchMode(CameraMode.VIDEO)
        }

        // Tombol Shutter / Capture
        binding.btnShutterOuter.setOnClickListener { onShutterClicked() }
        binding.btnShutterInner.setOnClickListener { onShutterClicked() }

        // Tombol Ganti Kamera (Front / Back)
        binding.btnSwitchCamera.setOnClickListener {
            if (recording != null) {
                Toast.makeText(this, "Tidak dapat mengganti kamera saat merekam", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            updateQualityIndicator()
            bindCameraUseCases()
        }

        // Tombol Thumbnail Hasil Tangkapan
        binding.btnGalleryThumbnail.setOnClickListener {
            openGallery()
        }

        // Tombol Dialog Kualitas Video (...)
        binding.btnVideoSettings.setOnClickListener {
            if (recording != null) {
                Toast.makeText(this, "Tidak dapat mengubah pengaturan saat merekam", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showVideoQualityDialog()
        }
    }

    private fun switchMode(mode: CameraMode) {
        currentMode = mode
        if (mode == CameraMode.PHOTO) {
            binding.tvModePhoto.setBackgroundResource(R.drawable.bg_timer_pill)
            binding.tvModePhoto.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            binding.tvModeVideo.background = null
            binding.tvModeVideo.setTextColor(0xB0FFFFFF.toInt())

            binding.btnShutterInner.setBackgroundResource(R.drawable.bg_shutter_inner)
            binding.recordingTimerContainer.visibility = View.GONE
        } else {
            binding.tvModeVideo.setBackgroundResource(R.drawable.bg_timer_pill)
            binding.tvModeVideo.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            binding.tvModePhoto.background = null
            binding.tvModePhoto.setTextColor(0xB0FFFFFF.toInt())

            binding.btnShutterInner.setBackgroundResource(R.drawable.bg_shutter_inner)
        }
    }

    private fun onShutterClicked() {
        if (currentMode == CameraMode.PHOTO) {
            takePhoto()
        } else {
            captureVideo()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return

        val preview = Preview.Builder().build().also {
            it.surfaceProvider = binding.viewFinder.surfaceProvider
        }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        // Menentukan kualitas video sesuai orientasi kamera terpilih
        val selectedQuality = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            backCameraQuality
        } else {
            frontCameraQuality
        }

        val qualitySelector = QualitySelector.from(
            selectedQuality,
            FallbackStrategy.lowerQualityOrHigherThan(selectedQuality)
        )

        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build()

        videoCapture = VideoCapture.withOutput(recorder)

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageCapture,
                videoCapture
            )
        } catch (e: Exception) {
            Log.e(TAG, "Gagal mengaitkan use case camera", e)
            Toast.makeText(this, "Gagal membuka kamera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Fitur Ambil Foto
    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_$name.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            .build()

        // Efek Flash visual saat pengambilan foto
        binding.viewFinder.alpha = 0.5f
        binding.viewFinder.animate().alpha(1.0f).duration = 300

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Gagal mengambil foto: ${exc.message}", exc)
                    Toast.makeText(
                        baseContext,
                        "Gagal mengambil foto: ${exc.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = output.savedUri
                    Toast.makeText(baseContext, "Foto berhasil disimpan!", Toast.LENGTH_SHORT).show()
                    savedUri?.let { updateThumbnail(it) }
                }
            }
        )
    }

    // Fitur Rekam Video
    private fun captureVideo() {
        val videoCapture = videoCapture ?: return

        val curRecording = recording
        if (curRecording != null) {
            // Hentikan rekaman jika sedang berlangsung
            curRecording.stop()
            recording = null
            return
        }

        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis())

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "VID_$name.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/Camera")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        val pendingRecording = videoCapture.output.prepareRecording(this, mediaStoreOutputOptions)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            pendingRecording.withAudioEnabled()
        }

        recording = pendingRecording.start(ContextCompat.getMainExecutor(this)) { recordEvent ->
            when (recordEvent) {
                is VideoRecordEvent.Start -> {
                    binding.btnShutterInner.setBackgroundResource(R.drawable.bg_shutter_recording_inner)
                    binding.recordingTimerContainer.visibility = View.VISIBLE
                    startTimer()
                }

                is VideoRecordEvent.Finalize -> {
                    stopTimer()
                    binding.btnShutterInner.setBackgroundResource(R.drawable.bg_shutter_inner)
                    binding.recordingTimerContainer.visibility = View.GONE

                    if (!recordEvent.hasError()) {
                        val uri = recordEvent.outputResults.outputUri
                        Toast.makeText(baseContext, "Video berhasil disimpan!", Toast.LENGTH_SHORT).show()
                        updateThumbnail(uri)
                    } else {
                        recording?.close()
                        recording = null
                        Log.e(TAG, "Error perekaman video: ${recordEvent.error}")
                        Toast.makeText(
                            baseContext,
                            "Gagal merekam video: ${recordEvent.error}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun startTimer() {
        recordingStartTime = System.currentTimeMillis()
        timerHandler.post(timerRunnable)
    }

    private fun stopTimer() {
        timerHandler.removeCallbacks(timerRunnable)
        binding.tvRecordingTimer.text = getString(R.string.timer_default)
    }

    private fun updateThumbnail(uri: Uri) {
        lastCapturedUri = uri
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val bitmap: Bitmap = contentResolver.loadThumbnail(uri, Size(128, 128), null)
                binding.btnGalleryThumbnail.setImageBitmap(bitmap)
            } else {
                binding.btnGalleryThumbnail.setImageURI(uri)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal membuat thumbnail: ${e.message}")
            binding.btnGalleryThumbnail.setImageURI(uri)
        }
    }

    private fun loadLastMediaFromGallery() {
        try {
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DATE_ADDED
            )
            val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

            val queryUri = MediaStore.Files.getContentUri("external")
            contentResolver.query(
                queryUri,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val id = cursor.getLong(idColumn)
                    val uri = Uri.withAppendedPath(queryUri, id.toString())
                    updateThumbnail(uri)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memuat media terakhir: ${e.message}")
        }
    }

    private fun openGallery() {
        val uri = lastCapturedUri
        if (uri != null) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, contentResolver.getType(uri) ?: "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                startActivity(intent)
                return
            } catch (e: Exception) {
                Log.e(TAG, "Gagal membuka media spesifik: ${e.message}")
            }
        }

        val galleryIntent = Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        try {
            startActivity(galleryIntent)
        } catch (_: Exception) {
            Toast.makeText(this, "Aplikasi Galeri tidak ditemukan", Toast.LENGTH_SHORT).show()
        }
    }

    // Dialog Pengaturan Kualitas Video
    private fun showVideoQualityDialog() {
        val dialogBinding = DialogVideoQualityBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .create()

        // Inisialisasi RadioButton Kamera Belakang
        when (backCameraQuality) {
            Quality.FHD -> dialogBinding.rbBackFullHd.isChecked = true
            Quality.HD -> dialogBinding.rbBackHd.isChecked = true
            Quality.SD -> dialogBinding.rbBack288p.isChecked = true
            else -> dialogBinding.rbBackFullHd.isChecked = true
        }

        // Inisialisasi RadioButton Kamera Depan
        when (frontCameraQuality) {
            Quality.HD -> dialogBinding.rbFrontHd.isChecked = true
            Quality.SD -> dialogBinding.rbFront288p.isChecked = true
            Quality.LOWEST -> dialogBinding.rbFront240p.isChecked = true
            else -> dialogBinding.rbFrontHd.isChecked = true
        }

        dialogBinding.btnCancelQuality.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnOkQuality.setOnClickListener {
            // Membaca Pilihan Kamera Belakang
            val backSelectedId = dialogBinding.rgBackCamera.checkedRadioButtonId
            backCameraQuality = when (backSelectedId) {
                R.id.rbBackFullHd -> Quality.FHD
                R.id.rbBackHd -> Quality.HD
                R.id.rbBack288p -> Quality.SD
                else -> Quality.FHD
            }

            // Membaca Pilihan Kamera Depan
            val frontSelectedId = dialogBinding.rgFrontCamera.checkedRadioButtonId
            frontCameraQuality = when (frontSelectedId) {
                R.id.rbFrontHd -> Quality.HD
                R.id.rbFront288p -> Quality.SD
                R.id.rbFront240p -> Quality.LOWEST
                else -> Quality.HD
            }

            updateQualityIndicator()
            bindCameraUseCases()
            Toast.makeText(this, "Kualitas video diperbarui", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateQualityIndicator() {
        val facingText = if (lensFacing == CameraSelector.LENS_FACING_BACK) "Back" else "Front"
        val qualityText = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            getQualityLabel(backCameraQuality)
        } else {
            getQualityLabel(frontCameraQuality)
        }
        binding.tvCurrentQuality.text = getString(R.string.quality_format, facingText, qualityText)
    }

    private fun getQualityLabel(quality: Quality): String {
        return when (quality) {
            Quality.FHD -> "FULL HD (1080p)"
            Quality.HD -> "HD (720p)"
            Quality.SD -> "288P"
            Quality.LOWEST -> "240P"
            else -> "HD"
        }
    }

    private fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        return permissions.toTypedArray()
    }

    private fun allPermissionsGranted() = getRequiredPermissions().all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        timerHandler.removeCallbacks(timerRunnable)
    }

    companion object {
        private const val TAG = "Tugas6Camera"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }
}