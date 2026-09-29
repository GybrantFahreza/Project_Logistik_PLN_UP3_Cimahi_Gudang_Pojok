package com.example.logistikpintar

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class RiwayatListActivity : AppCompatActivity() {

    private var backPressedTime: Long = 0
    private var pendingStartDate: Long? = null
    private var pendingEndDate: Long? = null
    private var pendingStatuses: Set<String>? = null

    private val exportPdfLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { prosesDanSimpanPdf(it, pendingStartDate, pendingEndDate, pendingStatuses) }
    }

    private val exportCsvLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { prosesDanSimpanCsv(it, pendingStartDate, pendingEndDate, pendingStatuses) }
    }

    private val restoreCsvLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            importCsv(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_riwayat_list)

        // 🌟 DUA KALI TEKAN BACK UNTUK KELUAR APLIKASI
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    finish()
                } else {
                    Toast.makeText(this@RiwayatListActivity, "Tekan 1 kali lagi jika ingin keluar", Toast.LENGTH_SHORT).show()
                    backPressedTime = System.currentTimeMillis()
                }
            }
        })

        val rvDaftarTanggal = findViewById<RecyclerView>(R.id.rvDaftarTanggal)
        rvDaftarTanggal.layoutManager = LinearLayoutManager(this)

        val fabExportMenu = findViewById<FloatingActionButton>(R.id.fabExportMenu)
        fabExportMenu.setOnClickListener {
            showExportMenu()
        }

        if (intent.getBooleanExtra("OPEN_EXPORT_MENU", false)) {
            showExportMenu()
        }

        loadDataTanggal(rvDaftarTanggal)

        // Center Scan Button (+)
        findViewById<View>(R.id.btnCenterScan).setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
        }

        // Bottom Navigation
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.selectedItemId = R.id.nav_history
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    LogistikHelper.bukaHalaman(this, MainActivity::class.java)
                    true
                }
                R.id.nav_history -> true
                R.id.nav_brankas -> {
                    LogistikHelper.bukaHalaman(this, BrankasActivity::class.java)
                    true
                }
                R.id.nav_gudang -> {
                    LogistikHelper.bukaHalaman(this, GudangSisaActivity::class.java)
                    true
                }
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val rv = findViewById<RecyclerView>(R.id.rvDaftarTanggal)
        if (rv != null) {
            loadDataTanggal(rv)
        }
    }

    private fun loadDataTanggal(rv: RecyclerView) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = LogistikDatabase.getDatabase(this@RiwayatListActivity).logistikDao()
            val allSj = db.getAllSuratJalan()
            val allMaterial = db.getAllMaterial()

            val mapGroup = HashMap<String, Boolean>()
            val mapGroupDraft = HashMap<String, Int>()
            val mapGroupTitip = HashMap<String, Int>()
            
            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())

            for (m in allMaterial) {
                if (m.status != "ARSIP" && m.status != "TITIP_GUDANG") continue
                val sj = allSj.find { it.id == m.idSuratJalan } ?: continue
                var tgl = sj.tanggal
                if (tgl.isBlank()) {
                    val dateObj = java.util.Date(sj.tanggalScan)
                    tgl = sdf.format(dateObj)
                }
                
                mapGroup[tgl] = true

                if (m.status == "ARSIP" && (m.isTanpaSlip || sj.noSj.contains("DRAFT"))) {
                    mapGroupDraft[tgl] = (mapGroupDraft[tgl] ?: 0) + 1
                }
                if (m.status == "TITIP_GUDANG") {
                    mapGroupTitip[tgl] = (mapGroupTitip[tgl] ?: 0) + 1
                }
            }

            val listSummary = mutableListOf<SummaryTanggal>()
            for (tgl in mapGroup.keys) {
                val totalDraft = mapGroupDraft[tgl] ?: 0
                val totalTitip = mapGroupTitip[tgl] ?: 0
                listSummary.add(SummaryTanggal(tgl, totalDraft, totalTitip))
            }
            
            // Urutkan berdasarkan tanggal terbaru
            val listFinal = listSummary.sortedByDescending { it.tanggal }

            withContext(Dispatchers.Main) {
                rv.adapter = TanggalAdapter(listFinal) { data ->
                    tampilkanDialogPilihLaporanPT(data.tanggal)
                }
            }
        }
    }

    // 🌟 DIALOG DRILL-DOWN: Pilih Laporan Keseluruhan atau Khusus per PT
    private fun tampilkanDialogPilihLaporanPT(tanggalDipilih: String) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Light_NoTitleBar)
        dialog.setContentView(R.layout.dialog_pilih_laporan_pt)

        val btnClose = dialog.findViewById<android.widget.ImageButton>(R.id.btnCloseDialogLaporan)
        val tvJudulTanggal = dialog.findViewById<TextView>(R.id.tvJudulTanggal)
        val cardLaporanTotal = dialog.findViewById<View>(R.id.cardLaporanTotal)
        val rvDaftarPTLaporan = dialog.findViewById<RecyclerView>(R.id.rvDaftarPTLaporan)

        tvJudulTanggal.text = "Laporan Tanggal $tanggalDipilih"
        rvDaftarPTLaporan.layoutManager = LinearLayoutManager(this)

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        // Aksi Tombol Keseluruhan
        cardLaporanTotal.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this@RiwayatListActivity, RiwayatActivity::class.java)
            intent.putExtra("TANGGAL_DIPILIH", tanggalDipilih)
            intent.putExtra("FILTER_PT", "SEMUA")
            startActivity(intent)
        }

        // Mengambil Daftar PT yang ada di tanggal tersebut
        lifecycleScope.launch(Dispatchers.IO) {
            val db = LogistikDatabase.getDatabase(this@RiwayatListActivity).logistikDao()
            val allSj = db.getAllSuratJalan()
            val allMaterial = db.getAllMaterial()

            // 🌟 Set untuk melacak idSuratJalan unik per PT
            val mapPT = mutableMapOf<String, MutableSet<Int>>()

            for (m in allMaterial) {
                if (m.status != "ARSIP") continue
                val sj = allSj.find { it.id == m.idSuratJalan } ?: continue
                if (sj.tanggal == tanggalDipilih) {
                    // Ekstrak nama PT
                    val pekerjaanAsli = sj.pekerjaan
                    val matchPT = Regex("""\((PT\.[^)]+|CV\.[^)]+|CV [^)]+|PT [^)]+)\)""", RegexOption.IGNORE_CASE).find(pekerjaanAsli)
                    val namaBersihPT = if (matchPT != null) {
                        matchPT.groupValues[1].uppercase()
                            .replace(Regex("""\bPT\.\s*"""), "PT ")
                            .replace(Regex("""\bCV\.\s*"""), "CV ")
                            .replace(Regex("""\s+"""), " ")
                            .trim()
                    } else if (pekerjaanAsli.contains("PERSEDIAAN")) {
                        "PERSEDIAAN GUDANG"
                    } else {
                        pekerjaanAsli
                    }

                    if (!mapPT.containsKey(namaBersihPT)) {
                        mapPT[namaBersihPT] = mutableSetOf()
                    }
                    mapPT[namaBersihPT]?.add(sj.id)
                }
            }

            val listFolderPT = mapPT.map { FolderPT(it.key, it.value.size, emptyList()) }.sortedBy { it.namaPT }

            withContext(Dispatchers.Main) {
                rvDaftarPTLaporan.adapter = AdapterFolderPT(listFolderPT) { folder ->
                    dialog.dismiss()
                    val intent = Intent(this@RiwayatListActivity, RiwayatActivity::class.java)
                    intent.putExtra("TANGGAL_DIPILIH", tanggalDipilih)
                    intent.putExtra("FILTER_PT", folder.namaPT)
                    startActivity(intent)
                }
            }
        }
        
        dialog.show()
    }

    private fun showExportMenu() {
        val bottomSheetDialog = BottomSheetDialog(this@RiwayatListActivity)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_export, null)

        view.findViewById<View>(R.id.btnOptExportPdf).setOnClickListener {
            bottomSheetDialog.dismiss()
            showTimeFilterDialog(isPdf = true)
        }

        view.findViewById<View>(R.id.btnOptExportCsv).setOnClickListener {
            bottomSheetDialog.dismiss()
            showTimeFilterDialog(isPdf = false)
        }

        view.findViewById<View>(R.id.btnOptImportCsv).setOnClickListener {
            bottomSheetDialog.dismiss()
            restoreCsvLauncher.launch("text/*")
        }

        bottomSheetDialog.setContentView(view)
        bottomSheetDialog.show()
    }

    private fun showTimeFilterDialog(isPdf: Boolean) {
        val options = arrayOf("Semua Data", "Pilih Rentang Waktu", "Pilih Status Material", "Pilih Rentang Waktu & Status")
        MaterialAlertDialogBuilder(this)
            .setTitle("Filter Data Export")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> triggerExportLauncher(isPdf, null, null, null)
                    1 -> showDateRangePicker(isPdf, thenSelectStatus = false)
                    2 -> showStatusSelectionDialog(isPdf, null, null)
                    3 -> showDateRangePicker(isPdf, thenSelectStatus = true)
                }
            }.show()
    }

    private fun showStatusSelectionDialog(isPdf: Boolean, startDate: Long?, endDate: Long?) {
        val statusOptions = arrayOf("AKTIF (Brankas)", "ARSIP (Terproses)", "TITIP_GUDANG (Gudang Sisa)")
        val statusValues = arrayOf("AKTIF", "ARSIP", "TITIP_GUDANG")
        val checkedItems = booleanArrayOf(true, true, true)

        MaterialAlertDialogBuilder(this)
            .setTitle("Pilih Status Material")
            .setMultiChoiceItems(statusOptions, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("PROSES EXPORT") { _, _ ->
                val selectedSet = mutableSetOf<String>()
                for (i in checkedItems.indices) {
                    if (checkedItems[i]) {
                        selectedSet.add(statusValues[i])
                    }
                }
                if (selectedSet.isEmpty()) {
                    Toast.makeText(this, "Pilih minimal 1 status!", Toast.LENGTH_SHORT).show()
                } else {
                    triggerExportLauncher(isPdf, startDate, endDate, selectedSet)
                }
            }
            .setNegativeButton("BATAL", null)
            .show()
    }

    private fun showDateRangePicker(isPdf: Boolean, thenSelectStatus: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = LogistikDatabase.getDatabase(this@RiwayatListActivity).logistikDao()
            val allSj = db.getAllSuratJalan()

            val sdfUtc = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

            val validDatesUtcMillis = HashSet<Long>()
            for (sj in allSj) {
                try {
                    val parsed = sdfUtc.parse(sj.tanggal.trim())
                    if (parsed != null) {
                        validDatesUtcMillis.add(parsed.time)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            withContext(Dispatchers.Main) {
                val builder = MaterialDatePicker.Builder.dateRangePicker()
                    .setTitleText("Pilih Rentang Tanggal")
                    .setTheme(R.style.CustomMaterialCalendarTheme)

                if (validDatesUtcMillis.isNotEmpty()) {
                    val minDate = validDatesUtcMillis.minOrNull()!!
                    val maxDate = validDatesUtcMillis.maxOrNull()!!

                    val constraintsBuilder = com.google.android.material.datepicker.CalendarConstraints.Builder()
                        .setStart(minDate)
                        .setEnd(maxDate)
                        .setOpenAt(maxDate)
                        .setValidator(DateHistoryValidator(validDatesUtcMillis))

                    builder.setCalendarConstraints(constraintsBuilder.build())
                }

                val dateRangePicker = builder.build()
                dateRangePicker.addOnPositiveButtonClickListener { selection ->
                    val startDate = selection.first
                    val endDate = selection.second
                    if (thenSelectStatus) {
                        showStatusSelectionDialog(isPdf, startDate, endDate)
                    } else {
                        triggerExportLauncher(isPdf, startDate, endDate, null)
                    }
                }
                dateRangePicker.show(supportFragmentManager, "DATE_PICKER")
            }
        }
    }

    private fun triggerExportLauncher(isPdf: Boolean, startDate: Long?, endDate: Long?, statuses: Set<String>?) {
        pendingStartDate = startDate
        pendingEndDate = endDate
        pendingStatuses = statuses

        val timestamp = SimpleDateFormat("ddMMyyyy_HHmm", Locale.getDefault()).format(Date())

        if (isPdf) {
            exportPdfLauncher.launch("Laporan_Logistik_$timestamp.pdf")
        } else {
            exportCsvLauncher.launch("Dataset_Logistik_$timestamp.csv")
        }
    }

    private fun parseDateToMillis(dateStr: String): Long? {
        return try {
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            sdf.parse(dateStr.trim())?.time
        } catch (e: Exception) {
            null
        }
    }

    private fun prosesDanSimpanCsv(uri: Uri, startDate: Long?, endDate: Long?, statuses: Set<String>?) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = LogistikDatabase.getDatabase(this@RiwayatListActivity).logistikDao()
                val allSj = db.getAllSuratJalan()
                val allMat = db.getAllMaterial()

                val filteredSjByDate = if (startDate != null && endDate != null) {
                    val endOfDay = endDate + (24 * 60 * 60 * 1000 - 1)
                    allSj.filter { sj ->
                        val t = parseDateToMillis(sj.tanggal)
                        t != null && t in startDate..endOfDay
                    }
                } else {
                    allSj
                }

                val filteredMat = if (statuses != null) {
                    allMat.filter { it.status in statuses }
                } else {
                    allMat
                }

                val sjWithMatIds = filteredMat.map { it.idSuratJalan }.toSet()
                val finalSj = filteredSjByDate.filter { it.id in sjWithMatIds }

                var isSuccess = false
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    isSuccess = ExportHelper.writeCSVToStream(finalSj, filteredMat, outputStream)
                }

                withContext(Dispatchers.Main) {
                    if (isSuccess) {
                        tampilkanOpsiBuka(uri, "text/csv")
                    } else {
                        Toast.makeText(this@RiwayatListActivity, "Gagal menulis file CSV", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RiwayatListActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun prosesDanSimpanPdf(uri: Uri, startDate: Long?, endDate: Long?, statuses: Set<String>?) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = LogistikDatabase.getDatabase(this@RiwayatListActivity).logistikDao()
                val allSj = db.getAllSuratJalan()
                val allMat = db.getAllMaterial()

                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

                val filteredSjByDate = if (startDate != null && endDate != null) {
                    val endOfDay = endDate + (24 * 60 * 60 * 1000 - 1)
                    allSj.filter { sj ->
                        val t = parseDateToMillis(sj.tanggal)
                        t != null && t in startDate..endOfDay
                    }
                } else {
                    allSj
                }

                val filteredMat = if (statuses != null) {
                    allMat.filter { it.status in statuses }
                } else {
                    allMat
                }

                val sjWithMatIds = filteredMat.map { it.idSuratJalan }.toSet()
                val finalSj = filteredSjByDate.filter { it.id in sjWithMatIds }

                val judulBuilder = StringBuilder()
                if (startDate != null && endDate != null) {
                    judulBuilder.append("Periode: ${sdf.format(Date(startDate))} - ${sdf.format(Date(endDate))}")
                } else {
                    judulBuilder.append("Periode: Semua Data")
                }

                if (statuses != null) {
                    judulBuilder.append(" | Status: ${statuses.joinToString(", ")}")
                }

                var isSuccess = false
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    isSuccess = ExportHelper.writePDFToStream(judulBuilder.toString(), finalSj, filteredMat, outputStream)
                }

                withContext(Dispatchers.Main) {
                    if (isSuccess) {
                        tampilkanOpsiBuka(uri, "application/pdf")
                    } else {
                        Toast.makeText(this@RiwayatListActivity, "Gagal menulis file PDF", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RiwayatListActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun tampilkanOpsiBuka(uri: Uri, mimeType: String) {
        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val rootView = findViewById<View>(android.R.id.content)
        Snackbar.make(rootView, "File berhasil disimpan!", Snackbar.LENGTH_LONG)
            .setAction("BUKA") {
                try {
                    startActivity(openIntent)
                } catch (e: android.content.ActivityNotFoundException) {
                    Toast.makeText(this@RiwayatListActivity, "Tidak ada aplikasi untuk membuka file ini.", Toast.LENGTH_SHORT).show()
                }
            }.show()
    }

    private fun parseCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        var inQuotes = false
        val sb = StringBuilder()
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    tokens.add(sb.toString().trim())
                    sb.clear()
                }
                else -> sb.append(ch)
            }
        }
        tokens.add(sb.toString().trim())
        return tokens
    }

    private fun importCsv(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val reader = inputStream?.bufferedReader()
                val lines = reader?.readLines() ?: emptyList()
                val db = LogistikDatabase.getDatabase(this@RiwayatListActivity).logistikDao()

                if (lines.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@RiwayatListActivity, "File CSV Kosong!", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                var successCount = 0
                lines.drop(1).forEach { line ->
                    if (line.isBlank()) return@forEach
                    val col = parseCsvLine(line)

                    // Format Header Wajib: No_SJ,Tanggal,Vendor,Pekerjaan,Nama_Barang,Qty,Satuan,Status
                    if (col.size >= 8) {
                        val noSj = col[0]
                        val tanggal = col[1]
                        val vendor = col[2]
                        val pekerjaan = col[3]
                        val namaBarang = col[4]
                        val qty = col[5].toIntOrNull() ?: 0
                        val satuan = col[6]
                        val status = col[7].ifEmpty { "ARSIP" }

                        val existingSj = db.getSuratJalanByNoSjUtuh(noSj)
                        val idSj = if (existingSj != null) {
                            existingSj.id
                        } else {
                            val newId = db.insertSuratJalan(
                                SuratJalanEntity(
                                    noSj = noSj,
                                    tanggal = tanggal,
                                    vendor = vendor,
                                    pekerjaan = pekerjaan
                                )
                            )
                            newId.toInt()
                        }

                        db.insertMaterial(
                            MaterialEntity(
                                idSuratJalan = idSj,
                                namaBarang = namaBarang,
                                qty = qty,
                                satuan = satuan,
                                status = status
                            )
                        )
                        successCount++
                    } else if (col.size >= 7) { // Fallback untuk format lama jika ada
                        val tanggal = col[0]
                        val noSj = col[1]
                        val vendor = col[2]
                        val pekerjaan = col[3]
                        val namaBarang = col[4]
                        val qty = col[5].toIntOrNull() ?: 0
                        val satuan = col[6]

                        val existingSj = db.getSuratJalanByNoSjUtuh(noSj)
                        val idSj = if (existingSj != null) {
                            existingSj.id
                        } else {
                            val newId = db.insertSuratJalan(
                                SuratJalanEntity(
                                    noSj = noSj,
                                    tanggal = tanggal,
                                    vendor = vendor,
                                    pekerjaan = pekerjaan
                                )
                            )
                            newId.toInt()
                        }

                        db.insertMaterial(
                            MaterialEntity(
                                idSuratJalan = idSj,
                                namaBarang = namaBarang,
                                qty = qty,
                                satuan = satuan,
                                status = "ARSIP"
                            )
                        )
                        successCount++
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RiwayatListActivity, "Restore Berhasil ($successCount data)!", Toast.LENGTH_SHORT).show()
                    recreate()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RiwayatListActivity, "Gagal Restore: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    data class SummaryTanggal(
        val tanggal: String,
        val totalDraft: Int,
        val totalTitip: Int
    )

    class TanggalAdapter(
        private val listTanggal: List<SummaryTanggal>,
        private val onClick: (SummaryTanggal) -> Unit
    ) : RecyclerView.Adapter<TanggalAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTanggal: TextView = view.findViewById(R.id.tvTanggalItem)
            val chipStatus: com.google.android.material.chip.Chip = view.findViewById(R.id.chipItemCount)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tanggal, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val summary = listTanggal[position]
            holder.tvTanggal.text = summary.tanggal
            
            // Logika Keterangan Status Terpadu
            val statusList = mutableListOf<String>()
            
            if (summary.totalDraft > 0) {
                statusList.add("Belum Jadi Slip (${summary.totalDraft})")
            }

            if (summary.totalTitip > 0) {
                statusList.add("Dititip (${summary.totalTitip})")
            }
            
            if (statusList.isEmpty()) {
                holder.chipStatus.text = "✅ SELESAI"
                holder.chipStatus.setChipBackgroundColorResource(android.R.color.holo_green_light)
                holder.chipStatus.setTextColor(android.graphics.Color.WHITE)
            } else {
                holder.chipStatus.text = statusList.joinToString(" | ")
                if (summary.totalDraft > 0) {
                    holder.chipStatus.setChipBackgroundColorResource(android.R.color.holo_red_light)
                    holder.chipStatus.setTextColor(android.graphics.Color.WHITE)
                } else {
                    holder.chipStatus.setChipBackgroundColorResource(android.R.color.holo_orange_light)
                    holder.chipStatus.setTextColor(android.graphics.Color.BLACK)
                }
            }

            holder.itemView.setOnClickListener { onClick(summary) }
        }

        override fun getItemCount() = listTanggal.size
    }
}

class DateHistoryValidator(private val validDatesMillis: Set<Long>) : com.google.android.material.datepicker.CalendarConstraints.DateValidator {

    constructor(parcel: android.os.Parcel) : this(
        HashSet<Long>().apply {
            val count = parcel.readInt()
            for (i in 0 until count) {
                add(parcel.readLong())
            }
        }
    )

    override fun isValid(date: Long): Boolean {
        return validDatesMillis.contains(date)
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: android.os.Parcel, flags: Int) {
        dest.writeInt(validDatesMillis.size)
        for (time in validDatesMillis) {
            dest.writeLong(time)
        }
    }

    companion object {
        @JvmField
        val CREATOR = object : android.os.Parcelable.Creator<DateHistoryValidator> {
            override fun createFromParcel(parcel: android.os.Parcel): DateHistoryValidator {
                return DateHistoryValidator(parcel)
            }

            override fun newArray(size: Int): Array<DateHistoryValidator?> {
                return arrayOfNulls(size)
            }
        }
    }
}