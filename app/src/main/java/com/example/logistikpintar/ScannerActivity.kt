package com.example.logistikpintar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.OrientationEventListener
import android.view.Surface
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private lateinit var orientationEventListener: OrientationEventListener

    private val bukaGaleriLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            prosesGambarDariGaleri(uri)
        } else {
            Toast.makeText(this, "Batal memilih gambar", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) startCamera()
        else {
            Toast.makeText(this, "Izin kamera ditolak.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner)

        viewFinder = findViewById(R.id.viewFinder)
        val btnAmbilFoto = findViewById<FloatingActionButton>(R.id.btnAmbilFoto)
        val btnGaleri = findViewById<Button>(R.id.btnGaleri)

        cameraExecutor = Executors.newSingleThreadExecutor()

        orientationEventListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val rotation = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                imageCapture?.targetRotation = rotation
            }
        }
        orientationEventListener.enable()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        btnAmbilFoto.setOnClickListener {
            btnAmbilFoto.isEnabled = false
            Toast.makeText(this, "Menajamkan fokus...", Toast.LENGTH_SHORT).show()
            Handler(Looper.getMainLooper()).postDelayed({
                jepretDanBacaTeks()
                btnAmbilFoto.isEnabled = true
            }, 1500)
        }

        btnGaleri.setOnClickListener {
            bukaGaleriLauncher.launch("image/*")
        }
    }

    // 🌟 FUNGSI PENYELAMAT: Membaca teks menyamping dengan menjaga urutan kata per baris (Text.Line)
    private fun ekstrakTeksHorizontal(visionText: Text): String {
        val lines = mutableListOf<Text.Line>()
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                lines.add(line)
            }
        }

        lines.sortBy { it.boundingBox?.centerY() ?: 0 }

        val barisTeks = mutableListOf<MutableList<Text.Line>>()
        for (line in lines) {
            val y = line.boundingBox?.centerY() ?: continue
            val tinggiLine = line.boundingBox?.height() ?: 35
            val toleransi = tinggiLine * 0.8 // Presisi tinggi agar antar baris tidak acak-acakan

            var ditambahkan = false
            for (baris in barisTeks) {
                val yBaris = baris.first().boundingBox?.centerY() ?: 0
                if (Math.abs(y - yBaris) < toleransi) {
                    baris.add(line)
                    ditambahkan = true
                    break
                }
            }
            if (!ditambahkan) {
                barisTeks.add(mutableListOf(line))
            }
        }

        val hasilAkhir = java.lang.StringBuilder()
        for (baris in barisTeks) {
            baris.sortBy { it.boundingBox?.left ?: 0 }
            val teksSebaris = baris.joinToString(" ") { it.text }
            hasilAkhir.append(teksSebaris).append("\n")
        }
        return hasilAkhir.toString()
    }

    private fun kirimHasilTeksKeReview(teksHasil: String, isLandscape: Boolean) {
        val isForHalDua = intent.getBooleanExtra("IS_FOR_HALAMAN_DUA", false)
        if (isForHalDua) {
            val resultIntent = Intent()
            resultIntent.putExtra("HASIL_TEKS", teksHasil)
            resultIntent.putExtra("IS_LANDSCAPE", isLandscape)
            setResult(RESULT_OK, resultIntent)
            finish()
        } else {
            val intent = Intent(this@ScannerActivity, ReviewActivity::class.java)
            intent.putExtra("HASIL_TEKS", teksHasil)
            intent.putExtra("IS_LANDSCAPE", isLandscape)
            startActivity(intent)
            finish()
        }
    }

    private fun prosesGambarDariGaleri(uri: Uri) {
        Toast.makeText(this, "AI sedang membaca foto dari galeri...", Toast.LENGTH_SHORT).show()
        try {
            val image = InputImage.fromFilePath(this, uri)
            val isLandscape = image.width > image.height

            if (!isLandscape) {
                // 🌟 KONDISI PORTRAIT (KODE LAMA/BERJALAN - SANGAT TIDAK DIUBAH)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val teksHorizontal = ekstrakTeksHorizontal(visionText)
                        kirimHasilTeksKeReview(teksHorizontal, false)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "AI Gagal membaca foto: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            } else {
                // 🌟 KONDISI BARU: FOTO LANDSCAPE TERDETEKSI
                prosesLandscapeDariUri(uri)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal memuat foto dari galeri", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch(exc: Exception) {
                Toast.makeText(this, "Kamera gagal dimuat.", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun jepretDanBacaTeks() {
        val imageCapture = imageCapture ?: return
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        val isLandscape = image.width > image.height

                        if (!isLandscape) {
                            // 🌟 KONDISI PORTRAIT (KODE LAMA/BERJALAN - SANGAT TIDAK DIUBAH)
                            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                            recognizer.process(image)
                                .addOnSuccessListener { visionText ->
                                    val teksHorizontal = ekstrakTeksHorizontal(visionText)
                                    kirimHasilTeksKeReview(teksHorizontal, false)
                                }
                                .addOnFailureListener {
                                    Toast.makeText(this@ScannerActivity, "Gagal baca teks", Toast.LENGTH_SHORT).show()
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        } else {
                            // 🌟 KONDISI BARU: FOTO LANDSCAPE TERDETEKSI
                            prosesLandscapeDariCamera(imageProxy)
                        }
                    } else {
                        imageProxy.close()
                    }
                }
                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@ScannerActivity, "Gagal jepret", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // 🌟 FUNGSI BARU: MEMPROSES DAN MENGOPTIMALKAN FOTO LANDSCAPE DENGAN AKURASI TINGGI
    private fun prosesLandscapeDariUri(uri: Uri) {
        Toast.makeText(this, "Mengoptimalkan pembacaan foto landscape...", Toast.LENGTH_SHORT).show()
        val bitmapUtama = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(contentResolver, uri))
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
        } catch (e: Exception) {
            null
        }

        if (bitmapUtama != null) {
            prosesRotasiDanEkstrakLandscape(bitmapUtama) { teksHasil ->
                kirimHasilTeksKeReview(teksHasil, true)
            }
        } else {
            Toast.makeText(this, "Gagal memproses bitmap landscape", Toast.LENGTH_SHORT).show()
        }
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun prosesLandscapeDariCamera(imageProxy: ImageProxy) {
        Toast.makeText(this, "Mengoptimalkan pembacaan foto landscape...", Toast.LENGTH_SHORT).show()
        val bitmap = imageProxy.toBitmap()
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val bitmapUtama = if (rotationDegrees != 0) {
            putarBitmap(bitmap, rotationDegrees.toFloat())
        } else {
            bitmap
        }
        imageProxy.close()

        prosesRotasiDanEkstrakLandscape(bitmapUtama) { teksHasil ->
            kirimHasilTeksKeReview(teksHasil, true)
        }
    }

    private fun putarBitmap(source: Bitmap, angle: Float): Bitmap {
        if (angle == 0f) return source
        val src = if (source.config == Bitmap.Config.HARDWARE) {
            source.copy(Bitmap.Config.ARGB_8888, true)
        } else {
            source
        }
        val matrix = Matrix().apply { postRotate(angle) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    private fun prosesRotasiDanEkstrakLandscape(bitmapUtama: Bitmap, onSelesai: (String) -> Unit) {
        val candidateAngles = listOf(90f, 270f, 0f, 180f)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        var bestText = ""
        var bestScore = -1
        var processedCount = 0

        for (angle in candidateAngles) {
            val rotatedBitmap = putarBitmap(bitmapUtama, angle)
            val image = InputImage.fromBitmap(rotatedBitmap, 0)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val teks = ekstrakTeksHorizontal(visionText)
                    val skor = hitungSkorAkurasiTeks(teks)

                    if (skor > bestScore) {
                        bestScore = skor
                        bestText = teks
                    }
                }
                .addOnCompleteListener {
                    processedCount++
                    if (processedCount == candidateAngles.size) {
                        if (bestText.isBlank()) {
                            val fallbackImage = InputImage.fromBitmap(bitmapUtama, 0)
                            recognizer.process(fallbackImage)
                                .addOnSuccessListener { vt -> onSelesai(ekstrakTeksHorizontal(vt)) }
                                .addOnFailureListener { onSelesai("") }
                        } else {
                            onSelesai(bestText)
                        }
                    }
                }
        }
    }

    private fun hitungSkorAkurasiTeks(teks: String): Int {
        val uppercaseText = teks.uppercase()
        var skor = 0
        if (uppercaseText.contains("SURAT JALAN")) skor += 100
        else if (uppercaseText.contains("SURAT") && uppercaseText.contains("JALAN")) skor += 50

        if (uppercaseText.contains("LOG.")) skor += 80
        else if (uppercaseText.contains("LOG")) skor += 40

        if (uppercaseText.contains("PLN")) skor += 50
        if (uppercaseText.contains("NOMOR") || uppercaseText.contains("NO.")) skor += 30
        if (uppercaseText.contains("TANGGAL")) skor += 30
        if (uppercaseText.contains("PEKERJAAN") || uppercaseText.contains("UNTUK")) skor += 30
        if (uppercaseText.contains("DIBERIKAN") || uppercaseText.contains("KEPADA")) skor += 30
        if (uppercaseText.contains("QUANTITY") || uppercaseText.contains("SATUAN") || uppercaseText.contains("MATERIAL")) skor += 40

        val countKodeMaterial = Regex("""\b(\d{6,8}|[A-Z]{1,3}\d{5,8})\b""").findAll(uppercaseText).count()
        skor += countKodeMaterial * 20

        if (Regex("""\b\d{2}/\d{2}/\d{4}\b""").containsMatchIn(uppercaseText)) skor += 50

        return skor
    }

    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        super.onDestroy()
        orientationEventListener.disable()
        cameraExecutor.shutdown()
    }
}
