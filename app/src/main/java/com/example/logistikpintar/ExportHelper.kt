package com.example.logistikpintar

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object ExportHelper {

    private fun drawMultilineText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        paint: TextPaint,
        width: Int
    ): Int {
        val staticLayout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, paint, width, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false)
        }

        canvas.save()
        canvas.translate(x, y)
        staticLayout.draw(canvas)
        canvas.restore()

        return staticLayout.height
    }

    /**
     * Tulis CSV untuk Backup Dataset (Flattened) langsung ke OutputStream SAF
     * Header: No_SJ,Tanggal,Vendor,Pekerjaan,Nama_Barang,Qty,Satuan,Status
     */
    suspend fun writeCSVToStream(dataSj: List<SuratJalanEntity>, dataMaterial: List<MaterialEntity>, outputStream: OutputStream): Boolean = withContext(Dispatchers.IO) {
        try {
            val writer = outputStream.bufferedWriter()
            
            // Header Baru Sesuai Dataset Backup
            writer.write("No_SJ,Tanggal,Vendor,Pekerjaan,Nama_Barang,Qty,Satuan,Status\n")
            
            dataSj.forEach { sj ->
                val materials = dataMaterial.filter { it.idSuratJalan == sj.id }
                materials.forEach { mat ->
                    val noSj = "\"${sj.noSj.replace("\"", "'")}\""
                    val tgl = "\"${sj.tanggal}\""
                    val ven = "\"${sj.vendor.replace("\"", "'")}\""
                    val pek = "\"${sj.pekerjaan.replace("\"", "'")}\""
                    val nama = "\"${mat.namaBarang.replace("\"", "'")}\""
                    val qty = mat.qty
                    val sat = "\"${mat.satuan}\""
                    val status = "\"${mat.status}\""
                    
                    writer.write("$noSj,$tgl,$ven,$pek,$nama,$qty,$sat,$status\n")
                }
            }
            writer.flush()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Tulis PDF Report A4 Landscape (842 x 595) dengan multi-line text & dynamic row height
     */
    suspend fun writePDFToStream(judul: String, dataSj: List<SuratJalanEntity>, dataMaterial: List<MaterialEntity>, outputStream: OutputStream): Boolean = withContext(Dispatchers.IO) {
        try {
            val pdfDocument = PdfDocument()
            val paint = Paint()
            val paintBold = Paint().apply { isFakeBoldText = true; color = Color.BLACK }
            val paintLine = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = Color.LTGRAY }
            
            val textPaint = TextPaint().apply {
                color = Color.BLACK
                textSize = 9f
                isAntiAlias = true
            }

            val textPaintBold = TextPaint().apply {
                color = Color.BLACK
                textSize = 9f
                isFakeBoldText = true
                isAntiAlias = true
            }

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(842, 595, pageNumber).create() // A4 Landscape
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas
            var y = 40f

            // Header Dokumen
            paintBold.textSize = 16f
            canvas.drawText("LAPORAN LOGISTIK PINTAR", 20f, y, paintBold)
            y += 20f
            paint.textSize = 10f
            canvas.drawText(judul, 20f, y, paint)
            y += 35f

            // X Coordinates & Column Widths
            val xNama = 25f
            val colNamaWidth = 280
            val xQty = 325f
            val xSat = 395f
            val xStatus = 465f
            val xProyek = 565f
            val colProyekWidth = 250

            val sortedSj = dataSj.sortedWith(compareBy({ it.tanggal }, { it.noSj }))
            
            sortedSj.forEach { sj ->
                val materials = dataMaterial.filter { it.idSuratJalan == sj.id }
                if (materials.isEmpty()) return@forEach

                // Cek apakah muat di halaman ini untuk header SJ (butuh ~50f)
                if (y > 500f) {
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(842, 595, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    y = 40f
                }

                // Gambar Header Surat Jalan
                canvas.drawRect(20f, y - 12f, 822f, y + 6f, Paint().apply { color = Color.parseColor("#F3F4F6") })
                paintBold.textSize = 10f
                canvas.drawText("No SJ: ${sj.noSj} | Tgl: ${sj.tanggal} | Diberikan Kepada: ${sj.vendor}", 25f, y, paintBold)
                y += 20f

                // Header Tabel
                paintBold.textSize = 9f
                canvas.drawText("NAMA BARANG", xNama, y, paintBold)
                canvas.drawText("QTY", xQty, y, paintBold)
                canvas.drawText("SAT", xSat, y, paintBold)
                canvas.drawText("STATUS", xStatus, y, paintBold)
                canvas.drawText("UNTUK PEKERJAAN", xProyek, y, paintBold)
                y += 5f
                canvas.drawLine(20f, y, 822f, y, paintLine)
                y += 12f

                materials.forEach { mat ->
                    // Cek batas bawah kertas sebelum menggambar baris
                    if (y > 520f) {
                        pdfDocument.finishPage(page)
                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(842, 595, pageNumber).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        y = 40f
                        
                        // Header lanjutan di halaman baru
                        canvas.drawText("NAMA BARANG (Lanjutan SJ: ${sj.noSj})", xNama, y, textPaintBold)
                        canvas.drawText("QTY", xQty, y, textPaintBold)
                        canvas.drawText("SAT", xSat, y, textPaintBold)
                        canvas.drawText("STATUS", xStatus, y, textPaintBold)
                        canvas.drawText("UNTUK PEKERJAAN", xProyek, y, textPaintBold)
                        y += 5f
                        canvas.drawLine(20f, y, 822f, y, paintLine)
                        y += 12f
                    }

                    // 1. Gambar teks multi-baris dan dapatkan tingginya
                    val hNama = drawMultilineText(canvas, mat.namaBarang, xNama, y, textPaint, colNamaWidth)
                    val hProyek = drawMultilineText(canvas, sj.pekerjaan, xProyek, y, textPaint, colProyekWidth)

                    // 2. Gambar teks kolom tunggal (posisi Y sejajar baris pertama)
                    canvas.drawText(mat.qty.toString(), xQty, y + 10f, textPaint)
                    canvas.drawText(mat.satuan, xSat, y + 10f, textPaint)
                    canvas.drawText(mat.status, xStatus, y + 10f, textPaint)

                    // 3. Hitung tinggi maksimum baris + padding
                    val maxRowHeight = maxOf(hNama, hProyek, 14)
                    y += maxRowHeight + 6f

                    canvas.drawLine(20f, y - 4f, 822f, y - 4f, Paint().apply { color = Color.parseColor("#EEEEEE"); strokeWidth = 0.5f; style = Paint.Style.STROKE })
                }
                y += 18f
            }

            pdfDocument.finishPage(page)
            pdfDocument.writeTo(outputStream)
            pdfDocument.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Legacy support for RiwayatActivity.kt
     */
    suspend fun generatePDF(context: Context, judul: String, dataSj: List<SuratJalanEntity>, dataMaterial: List<MaterialEntity>): File? = withContext(Dispatchers.IO) {
        try {
            val file = File(context.externalCacheDir, "temp_report.pdf")
            val os = FileOutputStream(file)
            if (writePDFToStream(judul, dataSj, dataMaterial, os)) file else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun generateCSV(context: Context, dataSj: List<SuratJalanEntity>, dataMaterial: List<MaterialEntity>): File? = withContext(Dispatchers.IO) {
        try {
            val file = File(context.externalCacheDir, "temp_backup.csv")
            val os = FileOutputStream(file)
            if (writeCSVToStream(dataSj, dataMaterial, os)) file else null
        } catch (e: Exception) {
            null
        }
    }

    fun shareFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
            type = mimeType
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Bagikan File"))
    }
}