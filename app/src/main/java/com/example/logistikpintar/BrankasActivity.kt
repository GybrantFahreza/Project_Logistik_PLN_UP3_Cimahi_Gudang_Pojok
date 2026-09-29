package com.example.logistikpintar

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.widget.PopupMenu
import androidx.cardview.widget.CardView
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BrankasActivity : AppCompatActivity() {

    private var backPressedTime: Long = 0
    private lateinit var rvBrankas: RecyclerView
    private lateinit var layoutNormalHeader: View
    private lateinit var layoutSelectionHeader: View
    private lateinit var tvSelectedTitle: TextView
    private var activeSjList: List<SuratJalanEntity> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_brankas)

        // 🌟 DUA KALI TEKAN BACK UNTUK KELUAR APLIKASI
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    finish()
                } else {
                    Toast.makeText(this@BrankasActivity, "Tekan 1 kali lagi jika ingin keluar", Toast.LENGTH_SHORT).show()
                    backPressedTime = System.currentTimeMillis()
                }
            }
        })

        rvBrankas = findViewById(R.id.rvBrankas)
        rvBrankas.layoutManager = LinearLayoutManager(this)



        // Bind Header Layouts
        layoutNormalHeader = findViewById(R.id.layoutNormalHeader)
        layoutSelectionHeader = findViewById(R.id.layoutSelectionHeader)
        tvSelectedTitle = findViewById(R.id.tvSelectedTitle)

        val btnCancelSelection = findViewById<ImageButton>(R.id.btnCancelSelection)
        val btnOverflowMenu = findViewById<ImageButton>(R.id.btnOverflowMenu)

        // 🌟 Button Pratinjau List Surat Jalan Masuk
        val cardPreviewSj = findViewById<View>(R.id.cardPreviewSj)
        cardPreviewSj?.setOnClickListener {
            if (activeSjList.isEmpty()) {
                Toast.makeText(this, "Belum ada Surat Jalan masuk yang aktif hari ini.", Toast.LENGTH_SHORT).show()
            } else {
                tampilkanDialogPreviewSjList(activeSjList)
            }
        }

        // Center Scan Button (+)
        findViewById<View>(R.id.btnCenterScan).setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
        }

        // Bottom Navigation
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.selectedItemId = R.id.nav_brankas
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> {
                    LogistikHelper.bukaHalaman(this, MainActivity::class.java)
                    true
                }
                R.id.nav_history -> {
                    LogistikHelper.bukaHalaman(this, RiwayatListActivity::class.java)
                    true
                }
                R.id.nav_brankas -> true
                R.id.nav_gudang -> {
                    LogistikHelper.bukaHalaman(this, GudangSisaActivity::class.java)
                    true
                }
                else -> false
            }
        }

        // 🌟 Batal Seleksi ('X' Icon)
        btnCancelSelection.setOnClickListener {
            exitSelectionMode()
        }

        // 🌟 Popup Menu (Icon 3 Titik Vertikal ⋮)
        btnOverflowMenu.setOnClickListener { view ->
            val adapter = rvBrankas.adapter as? BrankasAdapter ?: return@setOnClickListener
            val selectedItems = adapter.getSelectedItems()
            if (selectedItems.isEmpty()) return@setOnClickListener

            val popup = PopupMenu(this, view)
            popup.menu.add(0, 1, 0, "Edit Jumlah QTY")
            popup.menu.add(0, 2, 1, "Titip Gudang")
            popup.menu.add(0, 3, 2, "Tanpa Slip")
            popup.menu.add(0, 4, 3, "Hapus")

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    1 -> { // ✏️ Edit Jumlah QTY
                        if (selectedItems.size == 1) {
                            tampilkanDialogEdit(selectedItems[0])
                        } else {
                            tampilkanDialogEditMulti(selectedItems)
                        }
                        true
                    }
                    2 -> { // 📦 Titip Gudang
                        if (selectedItems.size == 1) {
                            tampilkanDialogSplit(selectedItems[0])
                        } else {
                            tampilkanDialogSplitMulti(selectedItems)
                        }
                        true
                    }
                    3 -> { // ⚠️ Tanpa Slip (Tanpa Input QTY)
                        prosesTanpaSlipBatch(selectedItems)
                        true
                    }
                    4 -> { // 🗑️ Hapus
                        prosesHapusBatch(selectedItems)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    override fun onResume() {
        super.onResume()
        cekTransferGudangOtomatis()
        loadData()
    }

    /**
     * Mengecek barang berstatus AKTIF yang tanggal SCAN-nya SEBELUM awal hari ini, lalu ubah statusnya jadi BELUM_DIAMBIL
     */
    private fun cekTransferGudangOtomatis() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = LogistikDatabase.getDatabase(this@BrankasActivity).logistikDao()
            
            // Dapatkan waktu millis untuk 00:00:00 hari ini
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val startOfTodayMs = cal.timeInMillis
            
            val barangTelat = db.getMaterialAktifLewatHari(startOfTodayMs)
            if (barangTelat.isNotEmpty()) {
                val ids = barangTelat.map { it.id }
                db.updateMaterialStatusBulk(ids, "BELUM_DIAMBIL")
            }
        }
    }

    private fun loadData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = LogistikDatabase.getDatabase(this@BrankasActivity).logistikDao()
            val semuaMaterial = db.getAllMaterial()
            val semuaSj = db.getAllSuratJalan()



            val listKonsolidasi = mutableListOf<DataKonsolidasi>()
            val mapKonsolidasi = HashMap<String, DataKonsolidasi>()
            
            for (material in semuaMaterial) {
                if (material.status != "AKTIF") continue
                
                val asalSj = semuaSj.find { it.id == material.idSuratJalan }
                val isSjDraft = asalSj?.noSj?.contains("DRAFT") == true
                val pekerjaan = asalSj?.pekerjaan ?: "???"

                val kunci = material.namaBarang

                if (mapKonsolidasi.containsKey(kunci)) {
                    val dataLama = mapKonsolidasi[kunci]!!
                    dataLama.totalQty += material.qty
                    dataLama.rincianPekerjaan.add("- $pekerjaan: ${material.qty} ${material.satuan}")
                    dataLama.materialIds.add(material.id)
                    if (isSjDraft) dataLama.isDraft = true
                    if (material.isTanpaSlip) dataLama.isTanpaSlip = true
                } else {
                    val rincianAwal = mutableListOf("- $pekerjaan: ${material.qty} ${material.satuan}")
                    mapKonsolidasi[kunci] = DataKonsolidasi(
                        namaBarang = material.namaBarang,
                        totalQty = material.qty,
                        satuan = material.satuan,
                        rincianPekerjaan = rincianAwal,
                        isDraft = isSjDraft,
                        isTanpaSlip = material.isTanpaSlip,
                        materialIds = mutableListOf(material.id)
                    )
                }
            }

            val listFolderPT = mutableListOf<FolderPT>()
            
            // 🌟 EKSTRAK NAMA PT/VENDOR MURNI DARI STRING PEKERJAAN
            // Karena nama pekerjaan di tiap SJ bisa beda depannya (contoh: "SPK.0143 KR.061 (PT.TOTAL DAYA UTAMA)" vs "SUTM G.BJM ULP.RDL SPK... (PT.TOTAL DAYA UTAMA)")
            // Kita harus mengelompokkan berdasarkan nama di dalam kurung (PT.XXX)
            val groupedByPT = semuaMaterial.filter { it.status == "AKTIF" }.groupBy { mat ->
                val pekerjaanAsli = semuaSj.find { it.id == mat.idSuratJalan }?.pekerjaan ?: "TIDAK DIKETAHUI"
                
                // Cari teks di dalam kurung, biasanya "PT. ..."
                val matchPT = Regex("""\((PT\.[^)]+|CV\.[^)]+|CV [^)]+|PT [^)]+)\)""", RegexOption.IGNORE_CASE).find(pekerjaanAsli)
                
                if (matchPT != null) {
                    var namaBersihPT = matchPT.groupValues[1].uppercase()
                    // 🌟 NORMALISASI PENULISAN PT: Buang titik setelah PT/CV dan hapus spasi berlebih
                    namaBersihPT = namaBersihPT
                        .replace(Regex("""\bPT\.\s*"""), "PT ")
                        .replace(Regex("""\bCV\.\s*"""), "CV ")
                        .replace(Regex("""\s+"""), " ")
                        .trim()
                    namaBersihPT
                } else if (pekerjaanAsli.contains("PERSEDIAAN")) {
                    "PERSEDIAAN GUDANG"
                } else {
                    pekerjaanAsli // Fallback jika tidak ada pola PT
                }
            }
            
            for ((namaPT, listMat) in groupedByPT) {
                // Di dalam satu PT, kita lakukan konsolidasi barang yang sama
                val mapKonsoPT = mutableMapOf<String, DataKonsolidasi>()
                
                // Kumpulkan semua SJ unik yang berelasi dengan PT ini
                val listIdSjUntukPTIni = listMat.map { it.idSuratJalan }.distinct()
                val sjTerkaitPT = semuaSj.filter { it.id in listIdSjUntukPTIni }
                
                for (material in listMat) {
                    val asalSj = sjTerkaitPT.find { it.id == material.idSuratJalan }
                    val kunci = material.namaBarang
                    val isSjDraft = asalSj?.noSj?.contains("DRAFT") == true
                    val rincianSjAsli = asalSj?.pekerjaan ?: "TIDAK DIKETAHUI"
                    
                    if (mapKonsoPT.containsKey(kunci)) {
                        val dataLama = mapKonsoPT[kunci]!!
                        dataLama.totalQty += material.qty
                        dataLama.rincianPekerjaan.add("- $rincianSjAsli: ${material.qty} ${material.satuan}")
                        dataLama.materialIds.add(material.id)
                        if (isSjDraft) dataLama.isDraft = true
                        if (material.isTanpaSlip) dataLama.isTanpaSlip = true
                    } else {
                        val rincianAwal = mutableListOf("- $rincianSjAsli: ${material.qty} ${material.satuan}")
                        mapKonsoPT[kunci] = DataKonsolidasi(
                            namaBarang = material.namaBarang,
                            totalQty = material.qty,
                            satuan = material.satuan,
                            rincianPekerjaan = rincianAwal,
                            isDraft = isSjDraft,
                            isTanpaSlip = material.isTanpaSlip,
                            materialIds = mutableListOf(material.id)
                        )
                    }
                }
                
                val listKonsoPT = mapKonsoPT.values.toList().sortedBy { it.isDraft }
                listFolderPT.add(FolderPT(namaPT, sjTerkaitPT.size, listKonsoPT))
            }
            
            val listFinalFolder = listFolderPT.sortedBy { it.namaPT }

            activeSjList = semuaSj

            // 🌟 HITUNG TOTAL BARANG (BUKAN SJ) YANG STATUSNYA BELUM DIAMBIL
            // Sesuai permintaan: Dihitung berdasarkan jenis item konsolidasi
            val mapBarangBelumDiambil = mutableMapOf<String, Int>()
            semuaMaterial.filter { it.status == "BELUM_DIAMBIL" }.forEach {
                mapBarangBelumDiambil[it.namaBarang] = (mapBarangBelumDiambil[it.namaBarang] ?: 0) + it.qty
            }
            val totalItemBelumDiambil = mapBarangBelumDiambil.size

            withContext(Dispatchers.Main) {
                // Notifikasi Barang Belum Diambil Kemarin
                if (totalItemBelumDiambil > 0) {
                    val tvReminder = findViewById<TextView>(R.id.tvReminderGudang)
                    tvReminder?.visibility = View.VISIBLE
                    tvReminder?.text = "⚠️ Ada $totalItemBelumDiambil Barang Dari Kemarin Belum Diambil!\nKlik Untuk Mengecek."
                    tvReminder?.setOnClickListener {
                        startActivity(Intent(this@BrankasActivity, GudangSisaActivity::class.java))
                    }
                }

                // 🌟 QUERY SJ HARI INI SAJA UNTUK BANNER REKAP MENGGUNAKAN TANGGAL SCAN (REALTIME)
                val cal = java.util.Calendar.getInstance()
                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                val startOfTodayMs = cal.timeInMillis
                
                val sjHariIniSaja = db.getSuratJalanScannedToday(startOfTodayMs)

                // Badge Jumlah SJ Hari Ini
                val tvCountBadge = findViewById<TextView>(R.id.tvPreviewSjCountBadge)
                tvCountBadge?.text = "${sjHariIniSaja.size} SJ >"
                
                // Update global reference agar klik pop-up menampilkan SJ hari ini saja
                activeSjList = sjHariIniSaja

                // 🌟 Menggunakan Adapter Baru (AdapterFolderPT)
                rvBrankas.adapter = AdapterFolderPT(listFinalFolder) { folderTerkunci ->
                    tampilkanDialogIsiFolderPT(folderTerkunci)
                }
                
                updateSelectionHeader()
            }
        }
    }
    
    fun loadBrankasData() {
        loadData()
    }

    // 🌟 DIALOG DETAIL FOLDER PT
    private fun tampilkanDialogIsiFolderPT(folder: FolderPT) {
        val dialog = android.app.Dialog(this, android.R.style.Theme_Light_NoTitleBar)
        dialog.setContentView(R.layout.dialog_isi_folder_pt)
        
        // Simpan state dialog yang sedang aktif agar bisa di-refresh
        activeFolderDialog = dialog
        activeFolderPT = folder

        val btnClose = dialog.findViewById<ImageButton>(R.id.btnCloseDialog)
        val tvJudulPT = dialog.findViewById<TextView>(R.id.tvJudulPT)
        val etSearchMaterial = dialog.findViewById<EditText>(R.id.etSearchMaterial)
        val rvIsiFolder = dialog.findViewById<RecyclerView>(R.id.rvIsiFolder)
        val btnTutupBukuPT = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnTutupBukuPT)
        val btnMenuFolder = dialog.findViewById<ImageButton>(R.id.btnMenuFolder)
        
        val layoutSelectionPTHeader = dialog.findViewById<View>(R.id.layoutSelectionPTHeader)
        val tvSelectedPTTitle = dialog.findViewById<TextView>(R.id.tvSelectedPTTitle)
        val btnCancelSelectionPT = dialog.findViewById<ImageButton>(R.id.btnCancelSelectionPT)

        tvJudulPT.text = folder.namaPT
        rvIsiFolder.layoutManager = LinearLayoutManager(this)

        val adapterFolder = BrankasAdapter(
            listData = folder.listDataKonsolidasi,
            onSelectionChanged = {
                // Update header di dalam dialog
                val adapter = rvIsiFolder.adapter as BrankasAdapter
                val count = adapter.getSelectedItems().size
                if (count > 0) {
                    layoutSelectionPTHeader.visibility = View.VISIBLE
                    tvSelectedPTTitle.text = "$count Item Terpilih"
                } else {
                    layoutSelectionPTHeader.visibility = View.GONE
                }
            },
            onItemLongClick = { dataKonsolidasi -> tampilkanInfoMaterial(dataKonsolidasi) }
        )
        rvIsiFolder.adapter = adapterFolder

        // 🌟 IMPLEMENTASI LIVE SEARCH DI DALAM DIALOG FOLDER PT
        etSearchMaterial.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim().lowercase()
                
                if (query.isEmpty()) {
                    // Jika kosong, kembalikan ke daftar asli
                    adapterFolder.updateData(folder.listDataKonsolidasi)
                } else {
                    // Filter berdasarkan Nama, QTY, atau Pekerjaan
                    val filteredList = folder.listDataKonsolidasi.filter { item ->
                        val namaMatch = item.namaBarang.lowercase().contains(query)
                        val qtyMatch = item.totalQty.toString().contains(query)
                        val pekerjaanMatch = item.rincianPekerjaan.any { it.lowercase().contains(query) }
                        
                        namaMatch || qtyMatch || pekerjaanMatch
                    }
                    adapterFolder.updateData(filteredList)
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        btnCancelSelectionPT.setOnClickListener {
            adapterFolder.clearSelection()
            layoutSelectionPTHeader.visibility = View.GONE
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
            activeFolderDialog = null
            activeFolderPT = null
        }
        
        dialog.setOnDismissListener {
            activeFolderDialog = null
            activeFolderPT = null
        }

        // Menu Overflow di Dialog Folder PT
        btnMenuFolder.setOnClickListener { view ->
            val selectedItems = adapterFolder.getSelectedItems()
            if (selectedItems.isEmpty()) return@setOnClickListener

            val popup = PopupMenu(this, view)
            popup.menu.add(0, 1, 0, "Edit Jumlah QTY")
            popup.menu.add(0, 2, 1, "Titip Gudang")
            popup.menu.add(0, 3, 2, "Tanpa Slip")
            popup.menu.add(0, 4, 3, "Hapus")

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    1 -> {
                        if (selectedItems.size == 1) tampilkanDialogEdit(selectedItems[0])
                        else tampilkanDialogEditMulti(selectedItems)
                        true
                    }
                    2 -> {
                        if (selectedItems.size == 1) tampilkanDialogSplit(selectedItems[0])
                        else tampilkanDialogSplitMulti(selectedItems)
                        true
                    }
                    3 -> {
                        prosesTanpaSlipBatch(selectedItems)
                        true
                    }
                    4 -> {
                        prosesHapusBatch(selectedItems)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        btnTutupBukuPT.setOnClickListener {
            val selectedItems = adapterFolder.getSelectedItems()
            if (selectedItems.isEmpty()) {
                Toast.makeText(this, "Pilih minimal 1 barang untuk Tutup Buku!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Perbaikan Bug Jumlah Dialog Arsip
            // Menampilkan jumlah ITEM (baris) yang di-ceklis, bukan jumlah pecahan material (Surat Jalan/ID).
            val countItems = selectedItems.size
            val selectedIds = adapterFolder.getCheckedMaterialIds()

            MaterialAlertDialogBuilder(this)
                .setTitle("Tutup Buku")
                .setMessage("Arsip $countItems barang ke Riwayat Harian?")
                .setPositiveButton("YA, ARSIPKAN") { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        val db = LogistikDatabase.getDatabase(this@BrankasActivity).logistikDao()
                        for (id in selectedIds) {
                            val mat = db.getMaterialById(id)
                            if (mat != null) {
                                mat.status = "ARSIP"
                                db.updateMaterial(mat)
                            }
                        }
                        withContext(Dispatchers.Main) {
                            dialog.dismiss()
                            loadBrankasData()
                        }
                    }
                }
                .setNegativeButton("BATAL", null)
                .show()
        }

        dialog.show()
    }

    private fun exitSelectionMode() {
        val adapter = rvBrankas.adapter as? BrankasAdapter ?: return
        adapter.clearSelection()
        updateSelectionHeader()
    }

    private fun updateSelectionHeader() {
        val adapter = rvBrankas.adapter as? BrankasAdapter ?: return
        val count = adapter.getSelectedItems().size
        if (count > 0) {
            layoutNormalHeader.visibility = View.GONE
            layoutSelectionHeader.visibility = View.VISIBLE
            tvSelectedTitle.text = "$count Item Terpilih"
        } else {
            layoutNormalHeader.visibility = View.VISIBLE
            layoutSelectionHeader.visibility = View.GONE
        }
    }

    // 🌟 DIALOG PRATINJAU LIST SURAT JALAN MASUK HARI INI
    private fun tampilkanDialogPreviewSjList(sjList: List<SuratJalanEntity>) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_preview_sj_list, null)
        val rvList = dialogView.findViewById<RecyclerView>(R.id.rvDialogSjList)
        rvList.layoutManager = LinearLayoutManager(this)

        var dialogRef: AlertDialog? = null

        val adapter = PreviewSjAdapter(sjList) { sj ->
            dialogRef?.dismiss()
            val intent = Intent(this@BrankasActivity, RiwayatDetailSjActivity::class.java)
            intent.putExtra("NO_SJ", sj.noSj)
            startActivity(intent)
        }
        rvList.adapter = adapter

        dialogRef = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton("TUTUP", null)
            .create()

        dialogRef?.show()
    }

    // 🌟 DIALOG EDIT QTY UNTUK 1 ITEM
    private fun tampilkanDialogEdit(data: DataKonsolidasi) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = LogistikDatabase.getDatabase(this@BrankasActivity).logistikDao()
            val materials = db.getMaterialsByNameAndStatus(data.namaBarang, "AKTIF").filter { it.id in data.materialIds }

            val sjList = materials.map { mat ->
                Pair(mat, db.getSuratJalanById(mat.idSuratJalan))
            }

            withContext(Dispatchers.Main) {
                val dialogView = LayoutInflater.from(this@BrankasActivity).inflate(R.layout.dialog_edit_qty, null)
                val tvNamaMaterial = dialogView.findViewById<TextView>(R.id.tvNamaMaterialEdit)
                val containerEditRows = dialogView.findViewById<LinearLayout>(R.id.containerEditRows)

                tvNamaMaterial.text = data.namaBarang

                val listInputEdit = mutableListOf<Pair<MaterialEntity, EditText>>()

                for (pair in sjList) {
                    val mat = pair.first
                    val sj = pair.second

                    val rowView = LayoutInflater.from(this@BrankasActivity).inflate(R.layout.item_edit_qty_row, containerEditRows, false)
                    val tvPekerjaanRow = rowView.findViewById<TextView>(R.id.tvPekerjaanRow)
                    val etQtyEditRow = rowView.findViewById<EditText>(R.id.etQtyEditRow)

                    tvPekerjaanRow.text = "Untuk Pekerjaan: ${sj?.pekerjaan ?: "Umum"}"
                    etQtyEditRow.setText(mat.qty.toString())

                    containerEditRows.addView(rowView)
                    listInputEdit.add(Pair(mat, etQtyEditRow))
                }

                val dialog = MaterialAlertDialogBuilder(this@BrankasActivity)
                    .setView(dialogView)
                    .setPositiveButton("UPDATE", null)
                    .setNegativeButton("BATAL", null)
                    .create()

                dialog.show()

                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    var inputValid = true
                    for (pair in listInputEdit) {
                        val strVal = pair.second.text.toString().trim()
                        val valInt = strVal.toIntOrNull()
                        if (valInt == null || valInt <= 0) {
                            inputValid = false
                            break
                        }
                    }

                    if (inputValid) {
                        dialog.dismiss()
                        prosesEditBarang(listInputEdit)
                    } else {
                        Toast.makeText(this@BrankasActivity, "Masukkan jumlah QTY yang valid (lebih dari 0)!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // 🌟 DIALOG EDIT QTY UNTUK BANYAK ITEM (> 1 ITEM) - SESUAI GAMBAR 3 MOCKUP
    private fun tampilkanDialogEditMulti(selectedItems: List<DataKonsolidasi>) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_qty_multi, null)
        val containerMultiEditRows = dialogView.findViewById<LinearLayout>(R.id.containerMultiEditRows)

        val listInputEditMulti = mutableListOf<Pair<DataKonsolidasi, EditText>>()

        for (item in selectedItems) {
            val rowView = LayoutInflater.from(this).inflate(R.layout.item_edit_qty_multi_row, containerMultiEditRows, false)
            val tvNama = rowView.findViewById<TextView>(R.id.tvNamaBarangMultiRow)
            val tvPekerjaan = rowView.findViewById<TextView>(R.id.tvPekerjaanMultiRow)
            val etQty = rowView.findViewById<EditText>(R.id.etQtyMultiRow)

            tvNama.text = item.namaBarang
            if (item.rincianPekerjaan.size > 1) {
                val rincianBersih = item.rincianPekerjaan.map { it.removePrefix("- ").trim() }
                tvPekerjaan.text = "Untuk Pekerjaan (${item.rincianPekerjaan.size} SJ):\n" + rincianBersih.joinToString("\n") { "• $it" }
            } else {
                val rincianTunggal = (item.rincianPekerjaan.firstOrNull() ?: "").removePrefix("- ").trim()
                tvPekerjaan.text = "Untuk Pekerjaan: $rincianTunggal"
            }
            etQty.setText(item.totalQty.toString())

            containerMultiEditRows.addView(rowView)
            listInputEditMulti.add(Pair(item, etQty))
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton("UPDATE SEMUA", null)
            .setNegativeButton("BATAL", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            var valid = true
            for (pair in listInputEditMulti) {
                val str = pair.second.text.toString().trim()
                val valInt = str.toIntOrNull()
                if (valInt == null || valInt <= 0) {
                    valid = false
                    break
                }
            }

            if (valid) {
                dialog.dismiss()
                prosesEditMultiBatch(listInputEditMulti)
            } else {
                Toast.makeText(this, "Masukkan angka QTY yang valid (lebih dari 0)!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private var activeFolderDialog: android.app.Dialog? = null
    private var activeFolderPT: FolderPT? = null

    private fun refreshDataDibelakangLayar() {
        // Panggil reload global
        loadData()
        
        // 🌟 PERBAIKAN: Jika ada dialog PT yang sedang terbuka, kita harus refresh isinya juga!
        activeFolderPT?.let { folderLama ->
            lifecycleScope.launch(Dispatchers.IO) {
                // Tarik data mentah terbaru dari database
                val db = LogistikDatabase.getDatabase(this@BrankasActivity).logistikDao()
                val semuaSj = db.getActiveSuratJalan()
                val semuaMaterial = db.getAllMaterial()
                
                // Cari ulang list material untuk PT ini
                val groupedByPT = semuaMaterial.filter { it.status == "AKTIF" }.groupBy { mat ->
                    val pekerjaanAsli = semuaSj.find { it.id == mat.idSuratJalan }?.pekerjaan ?: "TIDAK DIKETAHUI"
                    val matchPT = Regex("""\((PT\.[^)]+|CV\.[^)]+|CV [^)]+|PT [^)]+)\)""", RegexOption.IGNORE_CASE).find(pekerjaanAsli)
                    if (matchPT != null) {
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
                }
                
                val listMatTerbaru = groupedByPT[folderLama.namaPT] ?: emptyList()
                val listIdSjUntukPTIni = listMatTerbaru.map { it.idSuratJalan }.distinct()
                val sjTerkaitPT = semuaSj.filter { it.id in listIdSjUntukPTIni }
                
                // Rakit ulang Data Konsolidasi
                val mapKonsoPT = mutableMapOf<String, DataKonsolidasi>()
                for (material in listMatTerbaru) {
                    val asalSj = sjTerkaitPT.find { it.id == material.idSuratJalan }
                    val kunci = material.namaBarang
                    val rincianSjAsli = asalSj?.pekerjaan ?: "TIDAK DIKETAHUI"
                    
                    if (mapKonsoPT.containsKey(kunci)) {
                        val dataLamaKonso = mapKonsoPT[kunci]!!
                        dataLamaKonso.totalQty += material.qty
                        dataLamaKonso.rincianPekerjaan.add("- $rincianSjAsli: ${material.qty} ${material.satuan}")
                        dataLamaKonso.materialIds.add(material.id)
                    } else {
                        mapKonsoPT[kunci] = DataKonsolidasi(
                            namaBarang = material.namaBarang,
                            totalQty = material.qty,
                            satuan = material.satuan,
                            rincianPekerjaan = mutableListOf("- $rincianSjAsli: ${material.qty} ${material.satuan}"),
                            isDraft = asalSj?.noSj?.contains("DRAFT") == true,
                            isTanpaSlip = material.isTanpaSlip,
                            materialIds = mutableListOf(material.id)
                        )
                    }
                }
                
                val listKonsoTerbaru = mapKonsoPT.values.toList().sortedBy { it.isDraft }
                activeFolderPT = FolderPT(folderLama.namaPT, sjTerkaitPT.size, listKonsoTerbaru)
                
                // Update Adapter Dialog
                withContext(Dispatchers.Main) {
                    val rvIsiFolder = activeFolderDialog?.findViewById<RecyclerView>(R.id.rvIsiFolder)
                    val adapter = rvIsiFolder?.adapter as? BrankasAdapter
                    adapter?.updateData(listKonsoTerbaru)
                    adapter?.clearSelection() // Reset ceklis agar aman
                    
                    // Sembunyikan Header Ceklis ("1 Item Terpilih")
                    activeFolderDialog?.findViewById<View>(R.id.layoutSelectionPTHeader)?.visibility = View.GONE
                }
            }
        }
    }

    private fun prosesEditMultiBatch(listInput: List<Pair<DataKonsolidasi, EditText>>) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = LogistikDatabase.getDatabase(this@BrankasActivity).logistikDao()
            for (pair in listInput) {
                val data = pair.first
                val qtyBaru = pair.second.text.toString().trim().toIntOrNull() ?: data.totalQty
                if (qtyBaru != data.totalQty && qtyBaru > 0) {
                    val materials = db.getMaterialsByNameAndStatus(data.namaBarang, "AKTIF").filter { it.id in data.materialIds }
                    if (materials.isNotEmpty()) {
                        val matUtama = materials[0]
                        matUtama.qty = qtyBaru
                        db.updateMaterial(matUtama)
                    }
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@BrankasActivity, "✅ QTY ${listInput.size} barang berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                exitSelectionMode()
                loadBrankasData()
            }
        }
    }

    private fun tampilkanInfoMaterial(data: DataKonsolidasi) {
        val rincianBersih = data.rincianPekerjaan.map { it.removePrefix("- ").trim() }
        val rincianGabung = rincianBersih.joinToString("\n") { "• $it" }

        val pesan = "Nama Barang:\n${data.namaBarang}\n\n" +
                "Total: ${data.totalQty} ${data.satuan}\n\n" +
                "Daftar Surat Jalan / Untuk Pekerjaan:\n$rincianGabung"

        MaterialAlertDialogBuilder(this)
            .setTitle("Detail Informasi Material")
            .setMessage(pesan)
            .setPositiveButton("TUTUP", null)
            .show()
    }

    private fun prosesEditBarang(listInput: List<Pair<MaterialEntity, EditText>>) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = LogistikDatabase.getDatabase(this@BrankasActivity).logistikDao()
            for (pair in listInput) {
                val matAsli = pair.first
                val inputEditStr = pair.second.text.toString()
                val qtyBaru = if (inputEditStr.isNotEmpty()) inputEditStr.toIntOrNull() ?: matAsli.qty else matAsli.qty

                if (qtyBaru != matAsli.qty && qtyBaru > 0) {
                    matAsli.qty = qtyBaru
                    db.updateMaterial(matAsli)
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@BrankasActivity, "Data Berhasil Diperbarui!", Toast.LENGTH_SHORT).show()
                refreshDataDibelakangLayar()
            }
        }
    }

    // 🌟 DIALOG TITIP GUDANG UNTUK 1 ITEM
    private fun tampilkanDialogSplit(data: DataKonsolidasi) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_atur_jumlah_dibawa, null)
        val tvNamaMaterial = dialogView.findViewById<TextView>(R.id.tvNamaMaterialDialog)
        val tvStokMaks = dialogView.findViewById<TextView>(R.id.tvStokMaksDialog)
        val etQtyDibawa = dialogView.findViewById<EditText>(R.id.etQtyDibawa)

        tvNamaMaterial.text = data.namaBarang
        tvStokMaks.text = "Total Surat Jalan: ${data.totalQty} ${data.satuan}"

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton("SIMPAN JUMLAH DIBAWA", null)
            .setNegativeButton("BATAL", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val qtyInputStr = etQtyDibawa.text.toString().trim()
            val qtyInput = qtyInputStr.toIntOrNull() ?: 0

            if (qtyInput > 0 && qtyInput < data.totalQty) {
                dialog.dismiss()
                lifecycleScope.launch(Dispatchers.IO) {
                    val db = LogistikDatabase.getDatabase(this@BrankasActivity).logistikDao()
                    LogistikHelper.prosesPecahBarangByIds(db, data.materialIds, qtyInput, "AKTIF", "AKTIF", "TITIP_GUDANG")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@BrankasActivity, "Berhasil! $qtyInput ${data.satuan} dibawa, sisanya (${data.totalQty - qtyInput} ${data.satuan}) disisihkan ke Gudang Sisa.", Toast.LENGTH_LONG).show()
                        refreshDataDibelakangLayar()
                    }
                }
            } else if (qtyInput > data.totalQty) {
                Toast.makeText(this, "Jumlah ($qtyInput) melebihi total barang (${data.totalQty} ${data.satuan})!", Toast.LENGTH_LONG).show()
            } else if (qtyInput == data.totalQty) {
                Toast.makeText(this, "Semua barang (${data.totalQty} ${data.satuan}) diset dibawa hari ini.", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Masukkan jumlah QTY minimal 1!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 🌟 DIALOG TITIP GUDANG UNTUK BANYAK ITEM (> 1 ITEM)
    private fun tampilkanDialogSplitMulti(selectedItems: List<DataKonsolidasi>) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_split_multi, null)
        val containerMultiSplitRows = dialogView.findViewById<LinearLayout>(R.id.containerMultiSplitRows)

        val listInputSplit = mutableListOf<Pair<DataKonsolidasi, EditText>>()

        for (item in selectedItems) {
            val rowView = LayoutInflater.from(this).inflate(R.layout.item_split_multi_row, containerMultiSplitRows, false)
            val tvNama = rowView.findViewById<TextView>(R.id.tvNamaBarangSplitRow)
            val tvStokMaks = rowView.findViewById<TextView>(R.id.tvStokMaksSplitRow)
            val etQtyDibawa = rowView.findViewById<EditText>(R.id.etQtyDibawaSplitRow)

            tvNama.text = item.namaBarang
            tvStokMaks.text = "Total Material: ${item.totalQty} ${item.satuan}"
            etQtyDibawa.setText(item.totalQty.toString())

            containerMultiSplitRows.addView(rowView)
            listInputSplit.add(Pair(item, etQtyDibawa))
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton("SIMPAN SEMUA", null)
            .setNegativeButton("BATAL", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            var valid = true
            for (pair in listInputSplit) {
                val inputStr = pair.second.text.toString().trim()
                val valInt = inputStr.toIntOrNull()
                if (valInt == null || valInt <= 0 || valInt > pair.first.totalQty) {
                    valid = false
                    break
                }
            }

            if (valid) {
                dialog.dismiss()
                prosesSplitMultiBatch(listInputSplit)
            } else {
                Toast.makeText(this, "Masukkan angka QTY dibawa yang valid (1 s.d Total Material)!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun prosesSplitMultiBatch(listInput: List<Pair<DataKonsolidasi, EditText>>) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = LogistikDatabase.getDatabase(this@BrankasActivity).logistikDao()
            for (pair in listInput) {
                val item = pair.first
                val qtyDibawa = pair.second.text.toString().trim().toIntOrNull() ?: item.totalQty
                if (qtyDibawa > 0 && qtyDibawa < item.totalQty) {
                    LogistikHelper.prosesPecahBarangByIds(db, item.materialIds, qtyDibawa, "AKTIF", "AKTIF", "TITIP_GUDANG")
                }
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@BrankasActivity, "✅ Berhasil atur Titip Gudang untuk ${listInput.size} barang!", Toast.LENGTH_SHORT).show()
                exitSelectionMode()
                loadBrankasData()
            }
        }
    }

    // 🌟 KONDISI 6: KELUAR TANPA SLIP (TANPA INPUT QTY) BATCH / SINGLE
    private fun prosesTanpaSlipBatch(selectedItems: List<DataKonsolidasi>) {
        if (selectedItems.isEmpty()) return

        val adaYangBelum = selectedItems.any { !it.isTanpaSlip }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_list, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvConfirmTitle)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvConfirmSubtitle)
        val tvFooterNote = dialogView.findViewById<TextView>(R.id.tvConfirmFooterNote)
        val containerRows = dialogView.findViewById<LinearLayout>(R.id.containerConfirmRows)

        if (adaYangBelum) {
            tvTitle.text = "⚠️ Tandai Tanpa Slip?"
            tvSubtitle.text = "Tandai ${selectedItems.size} barang terpilih berikut sebagai TANPA SLIP?"
            tvFooterNote.visibility = View.VISIBLE
            tvFooterNote.text = "Barang akan ditandai belum memiliki Slip Resmi."
        } else {
            tvTitle.text = "Batal Status Tanpa Slip?"
            tvSubtitle.text = "Batalkan status Tanpa Slip untuk ${selectedItems.size} barang terpilih berikut?"
            tvFooterNote.visibility = View.GONE
        }

        for (item in selectedItems) {
            val rowView = LayoutInflater.from(this).inflate(R.layout.item_confirm_row, containerRows, false)
            val tvNama = rowView.findViewById<TextView>(R.id.tvNamaConfirmRow)
            val tvQty = rowView.findViewById<TextView>(R.id.tvQtyConfirmRow)

            tvNama.text = item.namaBarang
            tvQty.text = "${item.totalQty} ${item.satuan}"

            containerRows.addView(rowView)
        }

        val btnPosText = if (adaYangBelum) "TANDAI TANPA SLIP" else "BATALKAN TANPA SLIP"

        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton(btnPosText) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val db = LogistikDatabase.getDatabase(this@BrankasActivity).logistikDao()
                    for (item in selectedItems) {
                        val materials = db.getMaterialsByNameAndStatus(item.namaBarang, "AKTIF").filter { it.id in item.materialIds }
                        for (mat in materials) {
                            mat.isTanpaSlip = adaYangBelum
                            db.updateMaterial(mat)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        val msg = if (adaYangBelum) "✅ ${selectedItems.size} barang ditandai TANPA SLIP!" else "✅ Status Tanpa Slip dibatalkan!"
                        Toast.makeText(this@BrankasActivity, msg, Toast.LENGTH_SHORT).show()
                        refreshDataDibelakangLayar()
                    }
                }
            }
            .setNegativeButton("BATAL", null)
            .show()
    }

    // 🌟 PROSES HAPUS BATCH / SINGLE
    private fun prosesHapusBatch(selectedItems: List<DataKonsolidasi>) {
        if (selectedItems.isEmpty()) return

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_list, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvConfirmTitle)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvConfirmSubtitle)
        val tvFooterNote = dialogView.findViewById<TextView>(R.id.tvConfirmFooterNote)
        val containerRows = dialogView.findViewById<LinearLayout>(R.id.containerConfirmRows)

        tvTitle.text = "🗑️ Hapus ${selectedItems.size} Barang"
        tvSubtitle.text = "Yakin ingin menghapus ${selectedItems.size} barang terpilih dari Brankas?"
        tvFooterNote.visibility = View.GONE

        for (item in selectedItems) {
            val rowView = LayoutInflater.from(this).inflate(R.layout.item_confirm_row, containerRows, false)
            val tvNama = rowView.findViewById<TextView>(R.id.tvNamaConfirmRow)
            val tvQty = rowView.findViewById<TextView>(R.id.tvQtyConfirmRow)

            tvNama.text = item.namaBarang
            tvQty.text = "${item.totalQty} ${item.satuan}"

            containerRows.addView(rowView)
        }

        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton("HAPUS") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val db = LogistikDatabase.getDatabase(this@BrankasActivity).logistikDao()
                    for (item in selectedItems) {
                        for (matId in item.materialIds) {
                            val mat = db.getMaterialById(matId)
                            if (mat != null && mat.status == "AKTIF") {
                                db.deleteMaterial(mat)
                            }
                        }
                    }
                    db.deleteOrphanSuratJalan()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@BrankasActivity, "✅ ${selectedItems.size} barang berhasil dihapus!", Toast.LENGTH_SHORT).show()
                        refreshDataDibelakangLayar()
                    }
                }
            }
            .setNegativeButton("BATAL", null)
            .show()
    }

    data class DataKonsolidasi(
        val namaBarang: String,
        var totalQty: Int,
        val satuan: String,
        val rincianPekerjaan: MutableList<String>,
        var isChecked: Boolean = false,
        var sisaGudang: Int = 0,
        var totalSjAsli: Int = 0,
        var isDraft: Boolean = false,
        var isTanpaSlip: Boolean = false,
        val materialIds: MutableList<Int> = mutableListOf()
    )

    class BrankasAdapter(
        private var listData: List<DataKonsolidasi>,
        private val onSelectionChanged: () -> Unit,
        private val onItemLongClick: (DataKonsolidasi) -> Unit
    ) : RecyclerView.Adapter<BrankasAdapter.ViewHolder>() {

        var isSelectionMode = false

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvNama = view.findViewById<TextView>(R.id.tvNamaBarangBrankas)
            val tvTotalQty = view.findViewById<TextView>(R.id.tvTotalQty)
            val tvSatuan = view.findViewById<TextView>(R.id.tvSatuanBrankas)
            val tvRincian = view.findViewById<TextView>(R.id.tvRincianGardu)
            val cbNaikTruk = view.findViewById<CheckBox>(R.id.cbNaikTruk)
            val tvBadgeTanpaSlipBrankas = view.findViewById<TextView>(R.id.tvBadgeTanpaSlipBrankas)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_brankas, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val data = listData[position]
            val cardView = holder.itemView as CardView

            // Background Card
            if (data.isChecked) {
                cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#E3F2FD"))
                holder.tvSatuan.setTextColor(android.graphics.Color.parseColor("#1976D2"))
            } else if (data.isDraft) {
                cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#FFF5F5"))
                holder.tvSatuan.setTextColor(android.graphics.Color.RED)
            } else {
                cardView.setCardBackgroundColor(android.graphics.Color.WHITE)
                holder.tvSatuan.setTextColor(android.graphics.Color.GRAY)
            }

            holder.tvNama.text = data.namaBarang
            holder.tvTotalQty.text = data.totalQty.toString()
            holder.tvSatuan.text = data.satuan

            if (data.rincianPekerjaan.size > 1) {
                val rincianBersih = data.rincianPekerjaan.map { it.removePrefix("- ").trim() }
                holder.tvRincian.text = "Untuk Pekerjaan (${data.rincianPekerjaan.size} Surat Jalan):\n" + rincianBersih.joinToString("\n") { "• $it" }
            } else {
                val rincianTunggal = (data.rincianPekerjaan.firstOrNull() ?: "").removePrefix("- ").trim()
                holder.tvRincian.text = "Untuk Pekerjaan: $rincianTunggal"
            }

            holder.tvBadgeTanpaSlipBrankas.visibility = if (data.isTanpaSlip) View.VISIBLE else View.GONE

            holder.cbNaikTruk.setOnCheckedChangeListener(null)
            holder.cbNaikTruk.isChecked = data.isChecked

            if (data.isChecked) {
                holder.tvNama.paintFlags = holder.tvNama.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                holder.tvNama.setTextColor(android.graphics.Color.GRAY)
            } else {
                holder.tvNama.paintFlags = holder.tvNama.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                holder.tvNama.setTextColor(android.graphics.Color.BLACK)
            }

            holder.cbNaikTruk.setOnCheckedChangeListener { _, isChecked ->
                data.isChecked = isChecked
                if (isChecked) isSelectionMode = true
                else if (listData.none { it.isChecked }) isSelectionMode = false

                notifyItemChanged(position)
                onSelectionChanged()
            }

            // 🌟 Aksi tekan biasa (Short Click) untuk Ceklis (Toggling Checkbox)
            holder.itemView.setOnClickListener {
                holder.cbNaikTruk.isChecked = !holder.cbNaikTruk.isChecked
            }

            // 🌟 Aksi tekan dan tahan (Long Click) untuk Pop-Up Informasi Material
            holder.itemView.setOnLongClickListener {
                onItemLongClick(data)
                true // Kembalikan true agar tidak trigger setOnClickListener di atas
            }
        }

        override fun getItemCount() = listData.size

        fun getSelectedItems(): List<DataKonsolidasi> {
            return listData.filter { it.isChecked }
        }

        fun getCheckedMaterialIds(): List<Int> {
            return listData.filter { it.isChecked }.flatMap { it.materialIds }
        }

        fun clearSelection() {
            isSelectionMode = false
            listData.forEach { it.isChecked = false }
            notifyDataSetChanged()
        }

        fun updateData(newList: List<DataKonsolidasi>) {
            listData = newList
            notifyDataSetChanged()
        }
    }

    // 🌟 ADAPTER UNTUK DIALOG PRATINJAU SURAT JALAN MASUK
    class PreviewSjAdapter(
        private val listSj: List<SuratJalanEntity>,
        private val onClick: (SuratJalanEntity) -> Unit
    ) : RecyclerView.Adapter<PreviewSjAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvNoSj = view.findViewById<TextView>(R.id.tvNoSjPreview)
            val tvVendor = view.findViewById<TextView>(R.id.tvVendorPreview)
            val tvPekerjaan = view.findViewById<TextView>(R.id.tvPekerjaanPreview)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_preview_sj, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val sj = listSj[position]
            val isDraft = sj.noSj.contains("DRAFT")

            if (isDraft) {
                holder.tvNoSj.text = "📝 DRAFT (TANPA SLIP)"
                holder.tvNoSj.setTextColor(android.graphics.Color.parseColor("#D32F2F"))
                holder.tvVendor.text = "Diberikan Kepada: BELUM ADA SLIP"
            } else {
                holder.tvNoSj.text = "No SJ: ${sj.noSj}"
                holder.tvNoSj.setTextColor(android.graphics.Color.parseColor("#111827"))
                holder.tvVendor.text = "Diberikan Kepada: ${if (sj.vendor.isNotBlank()) sj.vendor else "-"}"
            }

            holder.tvPekerjaan.text = "Untuk Pekerjaan: ${if (sj.pekerjaan.isNotBlank()) sj.pekerjaan else "-"}"

            holder.itemView.setOnClickListener { onClick(sj) }
        }

        override fun getItemCount() = listSj.size
    }
}
