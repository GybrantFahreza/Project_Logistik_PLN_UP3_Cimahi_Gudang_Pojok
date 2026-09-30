package com.example.logistikpintar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RiwayatActivity : AppCompatActivity() {

    private lateinit var rvRiwayat: RecyclerView
    private lateinit var rvDaftarSj: RecyclerView
    private lateinit var tvInfoTanggal: TextView
    private lateinit var tvInfoNoSj: TextView
    private lateinit var tvInfoVendor: TextView
    private lateinit var tvInfoPekerjaan: TextView
    private var tanggalSelected: String = ""

    // 🌟 Launcher untuk Simpan PDF ke Folder Bebas (SAF)
    private val exportPdfLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            prosesSimpanPdfKeUri(uri)
        } else {
            Toast.makeText(this, "Penyimpanan PDF dibatalkan.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_riwayat)

        rvRiwayat = findViewById(R.id.rvRiwayat)
        rvRiwayat.layoutManager = LinearLayoutManager(this)

        rvDaftarSj = findViewById(R.id.rvDaftarSj)
        rvDaftarSj.layoutManager = LinearLayoutManager(this)

        tvInfoTanggal = findViewById(R.id.tvInfoTanggal)
        tvInfoNoSj = findViewById(R.id.tvInfoNoSj)
        tvInfoVendor = findViewById(R.id.tvInfoVendor)
        tvInfoPekerjaan = findViewById(R.id.tvInfoPekerjaan)
        val btnExportPdf = findViewById<android.widget.Button>(R.id.btnExportPdfToday)

        tanggalSelected = intent.getStringExtra("TANGGAL_DIPILIH") ?: return

        // Center Scan Button (+)
        findViewById<View>(R.id.btnCenterScan).setOnClickListener {
            startActivity(android.content.Intent(this, ScannerActivity::class.java))
        }

        // Bottom Navigation
        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.selectedItemId = R.id.nav_history
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

        btnExportPdf.setOnClickListener {
            val filterPt = intent.getStringExtra("FILTER_PT") ?: "SEMUA"
            
            lifecycleScope.launch(Dispatchers.IO) {
                val db = LogistikDatabase.getDatabase(this@RiwayatActivity).logistikDao()
                val semuaSjRaw = db.getSuratJalanByDate(tanggalSelected)
                val semuaMaterialRaw = db.getAllMaterial()
                
                val semuaSj = if (filterPt == "SEMUA") {
                    semuaSjRaw
                } else {
                    semuaSjRaw.filter { sj ->
                        val matchPT = Regex("""\((PT\.[^)]+|CV\.[^)]+|CV [^)]+|PT [^)]+)\)""", RegexOption.IGNORE_CASE).find(sj.pekerjaan)
                        val namaBersihPT = if (matchPT != null) {
                            matchPT.groupValues[1].uppercase()
                                .replace(Regex("""\bPT\.\s*"""), "PT ")
                                .replace(Regex("""\bCV\.\s*"""), "CV ")
                                .replace(Regex("""\s+"""), " ")
                                .trim()
                        } else if (sj.pekerjaan.contains("PERSEDIAAN")) {
                            "PERSEDIAAN GUDANG"
                        } else {
                            sj.pekerjaan
                        }
                        namaBersihPT == filterPt
                    }
                }

                val sjIds = semuaSj.map { it.id }.toSet()
                val filteredMat = semuaMaterialRaw.filter { it.idSuratJalan in sjIds }

                val judulPdf = if (filterPt == "SEMUA") "Laporan_Logistik_$tanggalSelected" 
                               else "Laporan_Logistik_${filterPt}_$tanggalSelected"
                val judulBersih = judulPdf.replace("/", "-").replace(" ", "_")

                withContext(Dispatchers.Main) {
                    exportPdfLauncher.launch("$judulBersih.pdf")
                }
            }
        }

        loadData()
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    private fun loadData() {
        val filterPt = intent.getStringExtra("FILTER_PT") ?: "SEMUA"

        lifecycleScope.launch(Dispatchers.IO) {
            val db = LogistikDatabase.getDatabase(this@RiwayatActivity).logistikDao()
            db.deleteOrphanSuratJalan()
            // Ambil semua surat jalan (aktif + arsip) di tanggal tersebut untuk memfilter material
            val semuaSjTanggalRaw = db.getSuratJalanByDate(tanggalSelected)
            val semuaSjArsipRaw = db.getArchivedSuratJalanByDate(tanggalSelected)
            val semuaMaterialRaw = db.getAllMaterial()

            // 🌟 IMPLEMENTASI FILTER KETAT PER PT
            val semuaSjTanggal = if (filterPt == "SEMUA") {
                semuaSjTanggalRaw
            } else {
                semuaSjTanggalRaw.filter { sj ->
                    val matchPT = Regex("""\((PT\.[^)]+|CV\.[^)]+|CV [^)]+|PT [^)]+)\)""", RegexOption.IGNORE_CASE).find(sj.pekerjaan)
                    val namaBersihPT = if (matchPT != null) {
                        matchPT.groupValues[1].uppercase()
                            .replace(Regex("""\bPT\.\s*"""), "PT ")
                            .replace(Regex("""\bCV\.\s*"""), "CV ")
                            .replace(Regex("""\s+"""), " ")
                            .trim()
                    } else if (sj.pekerjaan.contains("PERSEDIAAN")) {
                        "PERSEDIAAN GUDANG"
                    } else {
                        sj.pekerjaan
                    }
                    namaBersihPT == filterPt
                }
            }

            val semuaSjArsip = if (filterPt == "SEMUA") {
                semuaSjArsipRaw
            } else {
                semuaSjTanggal // Yang arsip juga terfilter sama
            }

            // 🌟 PASTIKAN sjIds DIAMBIL DARI SURAT JALAN YANG SUDAH TER-FILTER (semuaSjArsip)
            val sjIds = semuaSjArsip.map { it.id }.toSet()
            val semuaMaterialTanggal = semuaMaterialRaw.filter { it.idSuratJalan in sjIds }

            val noSjGabung = semuaSjArsip.map { it.noSj }.distinct().joinToString("\n")
            val vendorGabung = semuaSjArsip.map { it.vendor }.distinct().joinToString("\n")
            val pekerjaanGabung = semuaSjArsip.map { it.pekerjaan }.distinct().joinToString("\n")

            val mapKonsolidasi = mutableMapOf<String, DataKonsolidasi>()

            for (material in semuaMaterialTanggal) {
                if (material.status != "ARSIP") continue

                val asalSj = semuaSjArsip.find { it.id == material.idSuratJalan }
                if (asalSj != null) {
                    val pekerjaan = asalSj.pekerjaan
                    val kunci = material.namaBarang

                    val infoJob = "- $pekerjaan: ${material.qty} ${material.satuan}"

                    if (mapKonsolidasi.containsKey(kunci)) {
                        val dataLama = mapKonsolidasi[kunci]!!
                        dataLama.totalQty += material.qty
                        dataLama.rincianPekerjaan.add(infoJob)
                        if (material.isTanpaSlip) dataLama.isTanpaSlip = true
                        dataLama.listMaterialIds.add(material.id)
                    } else {
                        val rincianAwal = mutableListOf(infoJob)
                        mapKonsolidasi[kunci] = DataKonsolidasi(
                            namaBarang = material.namaBarang,
                            totalQty = material.qty,
                            satuan = material.satuan,
                            rincianPekerjaan = rincianAwal,
                            isDraft = false,
                            isTanpaSlip = material.isTanpaSlip,
                            listMaterialIds = mutableListOf(material.id)
                        )
                    }
                }
            }

            val listFinal = mapKonsolidasi.values.toList()
            for (data in listFinal) {
                val totalSemua = semuaMaterialTanggal.filter { it.namaBarang == data.namaBarang }.sumOf { it.qty }
                val totalSisa = semuaMaterialTanggal.filter { it.namaBarang == data.namaBarang && it.status == "TITIP_GUDANG" }.sumOf { it.qty }
                
                data.totalSjAsli = totalSemua
                data.sisaGudang = totalSisa
            }

            // Sub-list 1: Menunggu Slip (isTanpaSlip == true)
            val listMenungguSlip = semuaMaterialTanggal.filter { it.isTanpaSlip }.map { mat ->
                val sj = semuaSjTanggal.find { it.id == mat.idSuratJalan }
                MaterialWithSjInfoData(
                    materialId = mat.id,
                    idSuratJalan = mat.idSuratJalan,
                    namaBarang = mat.namaBarang,
                    qty = mat.qty,
                    satuan = mat.satuan,
                    pekerjaan = sj?.pekerjaan ?: "-",
                    noSj = sj?.noSj ?: "-",
                    isTanpaSlip = mat.isTanpaSlip
                )
            }

            // Sub-list 2: Gudang Sisa (status == "TITIP_GUDANG")
            val listGudangSisa = semuaMaterialTanggal.filter { it.status == "TITIP_GUDANG" }.map { mat ->
                val sj = semuaSjTanggal.find { it.id == mat.idSuratJalan }
                MaterialWithSjInfoData(
                    materialId = mat.id,
                    idSuratJalan = mat.idSuratJalan,
                    namaBarang = mat.namaBarang,
                    qty = mat.qty,
                    satuan = mat.satuan,
                    pekerjaan = sj?.pekerjaan ?: "-",
                    noSj = sj?.noSj ?: "-",
                    isTanpaSlip = mat.isTanpaSlip
                )
            }

            withContext(Dispatchers.Main) {
                // Update Title Sesuai Filter PT
                val tvTitleLaporan = findViewById<TextView>(R.id.tvTitleRiwayat)
                if (filterPt != "SEMUA") {
                    tvTitleLaporan?.text = "Laporan Logistik\n$filterPt"
                } else {
                    tvTitleLaporan?.text = "Laporan Logistik"
                }

                tvInfoTanggal.text = tanggalSelected
                tvInfoNoSj.text = if (noSjGabung.isNotBlank()) noSjGabung else "-"
                tvInfoVendor.text = if (vendorGabung.isNotBlank()) vendorGabung else "-"
                tvInfoPekerjaan.text = if (pekerjaanGabung.isNotBlank()) pekerjaanGabung else "-"
                tvInfoNoSj.setTextColor(android.graphics.Color.BLACK)
                findViewById<TextView>(R.id.tvTotalItemHeader).text = "${listFinal.size} Material"

                rvRiwayat.adapter = RiwayatAdapter(listFinal)

                // 1. DAFTAR SURAT JALAN
                val listSjResmi = semuaSjArsip.distinctBy { it.noSj.trim() }
                rvDaftarSj.adapter = SuratJalanListAdapter(listSjResmi, { sj ->
                    val intent = android.content.Intent(this@RiwayatActivity, RiwayatDetailSjActivity::class.java)
                    intent.putExtra("NO_SJ", sj.noSj.trim())
                    intent.putExtra("TANGGAL_DIPILIH", tanggalSelected)
                    startActivity(intent)
                }, { sj ->
                    hapusSjKonfirmasi(sj)
                })

                // 2. DAFTAR MENUNGGU SLIP
                val rvDaftarTanpaSlip = findViewById<RecyclerView>(R.id.rvDaftarTanpaSlip)
                val tvTitleTanpaSlip = findViewById<TextView>(R.id.tvTitleTanpaSlip)

                if (listMenungguSlip.isNotEmpty()) {
                    tvTitleTanpaSlip.visibility = View.VISIBLE
                    rvDaftarTanpaSlip.visibility = View.VISIBLE
                    rvDaftarTanpaSlip.layoutManager = LinearLayoutManager(this@RiwayatActivity)
                    rvDaftarTanpaSlip.adapter = MaterialInfoAdapter(
                        listMenungguSlip,
                        badgeText = "⚠️ BELUM JADI SLIP",
                        badgeBgColor = "#FFEBEE",
                        badgeTextColor = "#D32F2F"
                    ) { item ->
                        MaterialAlertDialogBuilder(this@RiwayatActivity)
                            .setTitle("Konfirmasi Administrasi")
                            .setMessage("Apakah material '${item.namaBarang}' sudah ada Slip Pengeluaran Barang?")
                            .setPositiveButton("SUDAH") { _, _ ->
                                pelunasanAdministrasi(item.namaBarang, listOf(item.materialId))
                            }
                            .setNegativeButton("BELUM", null)
                            .show()
                    }
                } else {
                    tvTitleTanpaSlip.visibility = View.GONE
                    rvDaftarTanpaSlip.visibility = View.GONE
                }

                // 3. DAFTAR GUDANG SISA (TITIP)
                val rvDaftarGudangSisa = findViewById<RecyclerView>(R.id.rvDaftarGudangSisa)
                val tvTitleGudangSisa = findViewById<TextView>(R.id.tvTitleGudangSisa)

                if (listGudangSisa.isNotEmpty()) {
                    tvTitleGudangSisa.visibility = View.VISIBLE
                    rvDaftarGudangSisa.visibility = View.VISIBLE
                    rvDaftarGudangSisa.layoutManager = LinearLayoutManager(this@RiwayatActivity)
                    rvDaftarGudangSisa.adapter = MaterialInfoAdapter(
                        listGudangSisa,
                        badgeText = "📦 BARANG BELUM DIAMBIL",
                        badgeBgColor = "#FEF3C7",
                        badgeTextColor = "#D97706"
                    ) { item ->
                        tampilkanDialogAmbilGudang(item)
                    }
                } else {
                    tvTitleGudangSisa.visibility = View.GONE
                    rvDaftarGudangSisa.visibility = View.GONE
                }
            }
        }
    }

    private fun prosesSimpanPdfKeUri(uri: android.net.Uri) {
        val filterPt = intent.getStringExtra("FILTER_PT") ?: "SEMUA"

        lifecycleScope.launch(Dispatchers.IO) {
            val db = LogistikDatabase.getDatabase(this@RiwayatActivity).logistikDao()
            val semuaSjRaw = db.getSuratJalanByDate(tanggalSelected)
            val semuaMaterialRaw = db.getAllMaterial()

            val semuaSj = if (filterPt == "SEMUA") {
                semuaSjRaw
            } else {
                semuaSjRaw.filter { sj ->
                    val matchPT = Regex("""\((PT\.[^)]+|CV\.[^)]+|CV [^)]+|PT [^)]+)\)""", RegexOption.IGNORE_CASE).find(sj.pekerjaan)
                    val namaBersihPT = if (matchPT != null) {
                        matchPT.groupValues[1].uppercase()
                            .replace(Regex("""\bPT\.\s*"""), "PT ")
                            .replace(Regex("""\bCV\.\s*"""), "CV ")
                            .replace(Regex("""\s+"""), " ")
                            .trim()
                    } else if (sj.pekerjaan.contains("PERSEDIAAN")) {
                        "PERSEDIAAN GUDANG"
                    } else {
                        sj.pekerjaan
                    }
                    namaBersihPT == filterPt
                }
            }

            val sjIds = semuaSj.map { it.id }.toSet()
            val filteredMat = semuaMaterialRaw.filter { it.idSuratJalan in sjIds }

            val judulPdf = if (filterPt == "SEMUA") "Laporan Logistik Tanggal $tanggalSelected"
            else "Laporan Logistik $filterPt - Tanggal $tanggalSelected"

            // Buat File PDF Sementara di Cache
            val tempFile = ExportHelper.generatePDF(this@RiwayatActivity, judulPdf, semuaSj, filteredMat)

            if (tempFile != null && tempFile.exists()) {
                try {
                    // Salin (copy) file PDF sementara ke direktori yang dipilih user
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        tempFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        tampilkanOpsiBuka(uri, "application/pdf")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@RiwayatActivity, "❌ Gagal menyimpan PDF: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    tempFile.delete() // Hapus cache PDF setelah selesai disalin
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RiwayatActivity, "❌ Gagal men-generate PDF", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun tampilkanOpsiBuka(uri: android.net.Uri, mimeType: String) {
        val openIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val rootView = findViewById<View>(android.R.id.content)
        Snackbar.make(rootView, "✅ PDF Berhasil Disimpan!", Snackbar.LENGTH_LONG)
            .setAction("BUKA") {
                try {
                    startActivity(openIntent)
                } catch (e: android.content.ActivityNotFoundException) {
                    Toast.makeText(this@RiwayatActivity, "Tidak ada aplikasi untuk membuka PDF ini.", Toast.LENGTH_SHORT).show()
                }
            }.show()
    }

    private fun hapusSjKonfirmasi(sj: SuratJalanEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle("🗑️ Hapus Surat Jalan?")
            .setMessage("Apakah Anda yakin ingin menghapus Surat Jalan nomor '${sj.noSj}' secara permanen?")
            .setPositiveButton("YA, HAPUS") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val db = LogistikDatabase.getDatabase(this@RiwayatActivity).logistikDao()
                    val listMaterialTerhapus = db.getMaterialsBySjId(sj.id)
                    db.deleteSuratJalan(sj)
                    
                    withContext(Dispatchers.Main) {
                        loadData()
                        val snackbar = Snackbar.make(findViewById(android.R.id.content), "Surat Jalan '${sj.noSj}' terhapus", Snackbar.LENGTH_LONG)
                        snackbar.setAction("UNDO") {
                            lifecycleScope.launch(Dispatchers.IO) {
                                val idBaruHeader = db.insertSuratJalan(sj.copy(id = 0))
                                listMaterialTerhapus.forEach {
                                    db.insertMaterial(it.copy(id = 0, idSuratJalan = idBaruHeader.toInt()))
                                }
                                withContext(Dispatchers.Main) { loadData() }
                            }
                        }
                        snackbar.show()
                    }
                }
            }
            .setNegativeButton("BATAL", null)
            .show()
    }

    fun tampilkanDialogAmbilGudang(item: MaterialWithSjInfoData) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_ambil_gudang, null)
        val tvNamaMaterial = dialogView.findViewById<TextView>(R.id.tvNamaMaterialDialog)
        val tvStokMaks = dialogView.findViewById<TextView>(R.id.tvStokMaksDialog)
        val tvRincianDetail = dialogView.findViewById<TextView>(R.id.tvRincianDetailDialog)
        val etQtyNaikTruk = dialogView.findViewById<EditText>(R.id.etQtyNaikTruk)

        tvNamaMaterial.text = item.namaBarang
        tvStokMaks.text = "Tersedia di Gudang: ${item.qty} ${item.satuan}"
        tvRincianDetail.text = "- ${item.pekerjaan}: ${item.qty} ${item.satuan} ($tanggalSelected)"

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton("NAIKKAN TRUK (ARSIP)", null)
            .setNegativeButton("BATAL", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val qtyStr = etQtyNaikTruk.text.toString().trim()
            val qty = qtyStr.toIntOrNull() ?: 0

            if (qty > 0 && qty <= item.qty) {
                dialog.dismiss()
                lifecycleScope.launch(Dispatchers.IO) {
                    val db = LogistikDatabase.getDatabase(this@RiwayatActivity).logistikDao()
                    LogistikHelper.prosesPecahBarangByIds(db, listOf(item.materialId), qty, "TITIP_GUDANG", "ARSIP", "TITIP_GUDANG")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@RiwayatActivity, "Barang ($qty ${item.satuan}) berhasil masuk ARSIP!", Toast.LENGTH_SHORT).show()
                        recreate()
                    }
                }
            } else if (qty > item.qty) {
                Toast.makeText(this, "Jumlah ($qty) melebihi stok gudang (${item.qty} ${item.satuan})!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Masukkan jumlah QTY minimal 1!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    data class MaterialWithSjInfoData(
        val materialId: Int,
        val idSuratJalan: Int,
        val namaBarang: String,
        val qty: Int,
        val satuan: String,
        val pekerjaan: String,
        val noSj: String,
        val isTanpaSlip: Boolean
    )

    class MaterialInfoAdapter(
        private val list: List<MaterialWithSjInfoData>,
        private val badgeText: String,
        private val badgeBgColor: String,
        private val badgeTextColor: String,
        private val onClick: ((MaterialWithSjInfoData) -> Unit)? = null
    ) : RecyclerView.Adapter<MaterialInfoAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvNama = view.findViewById<TextView>(R.id.tvNamaBarangBrankas)
            val tvTotalQty = view.findViewById<TextView>(R.id.tvTotalQty)
            val tvSatuan = view.findViewById<TextView>(R.id.tvSatuanBrankas)
            val tvRincian = view.findViewById<TextView>(R.id.tvRincianGardu)
            val cbNaikTruk = view.findViewById<CheckBox>(R.id.cbNaikTruk)
            val tvBadge = view.findViewById<TextView>(R.id.tvBadgeTanpaSlipBrankas)
            val tvSisaWarning = view.findViewById<TextView>(R.id.tvSisaWarning)
            val vSideIndicator = view.findViewById<View>(R.id.vSideIndicator)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_brankas, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            holder.tvNama.text = item.namaBarang
            holder.tvTotalQty.text = item.qty.toString()
            holder.tvSatuan.text = item.satuan
            holder.tvRincian.text = "Untuk Pekerjaan: ${item.pekerjaan}"
            holder.cbNaikTruk.visibility = View.GONE
            holder.tvSisaWarning.visibility = View.GONE

            holder.tvBadge?.visibility = View.VISIBLE
            holder.tvBadge?.text = badgeText
            holder.tvBadge?.setBackgroundColor(android.graphics.Color.parseColor(badgeBgColor))
            holder.tvBadge?.setTextColor(android.graphics.Color.parseColor(badgeTextColor))
            holder.vSideIndicator?.setBackgroundColor(android.graphics.Color.parseColor(badgeTextColor))

            holder.itemView.setOnClickListener {
                onClick?.invoke(item)
            }
        }

        override fun getItemCount() = list.size
    }

    fun prosesTitipGudangRiwayat(namaBarang: String, totalQty: Int, satuan: String, materialIds: List<Int>) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_titip_gudang, null)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvTitleDialog)
        val tvNamaMaterial = dialogView.findViewById<TextView>(R.id.tvNamaMaterialDialog)
        val tvInfoQty = dialogView.findViewById<TextView>(R.id.tvInfoQtyMax)
        val tilQtyTitip = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilQtyTitip)
        val etQtyTitip = dialogView.findViewById<EditText>(R.id.etQtyTitip)

        tvTitle?.text = "Barang Tidak Muat (Titip Gudang)"
        tvNamaMaterial?.text = namaBarang
        tvInfoQty?.text = "Jumlah saat ini (ARSIP): $totalQty $satuan"
        tilQtyTitip?.hint = "Jumlah yg Ditinggal di Gudang"

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton("SIMPAN", null)
            .setNegativeButton("BATAL", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val qtyTitipStr = etQtyTitip.text.toString().trim()
            val qtyTitip = qtyTitipStr.toIntOrNull() ?: 0

            if (qtyTitip > 0 && qtyTitip <= totalQty) {
                dialog.dismiss()
                lifecycleScope.launch(Dispatchers.IO) {
                    val db = LogistikDatabase.getDatabase(this@RiwayatActivity).logistikDao()
                    LogistikHelper.prosesPecahBarangByIds(db, materialIds, qtyTitip, "ARSIP", "TITIP_GUDANG", "ARSIP")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@RiwayatActivity, "Barang berhasil dikembalikan ke Gudang Sisa!", Toast.LENGTH_SHORT).show()
                        recreate()
                    }
                }
            } else if (qtyTitip > totalQty) {
                Toast.makeText(this, "Jumlah ($qtyTitip) melebihi total barang ($totalQty $satuan)!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Masukkan angka QTY minimal 1!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun pelunasanAdministrasi(namaBarang: String, materialIds: List<Int>? = null) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = LogistikDatabase.getDatabase(this@RiwayatActivity).logistikDao()
            val materials = db.getAllMaterial().filter { 
                it.namaBarang == namaBarang && 
                it.isTanpaSlip && 
                (materialIds == null || it.id in materialIds)
            }
            for (mat in materials) {
                mat.isTanpaSlip = false
                db.updateMaterial(mat)
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@RiwayatActivity, "Status administrasi $namaBarang LUNAS (Slip Diterima)!", Toast.LENGTH_SHORT).show()
                recreate()
            }
        }
    }

    fun ubahStatusKeluarTanpaSlip(namaBarang: String, materialIds: List<Int>? = null) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = LogistikDatabase.getDatabase(this@RiwayatActivity).logistikDao()
            val materials = db.getAllMaterial().filter { 
                it.namaBarang == namaBarang && 
                it.status == "ARSIP" && 
                (materialIds == null || it.id in materialIds)
            }
            for (mat in materials) {
                mat.isTanpaSlip = true
                db.updateMaterial(mat)
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@RiwayatActivity, "Status '$namaBarang' diubah menjadi BELUM JADI SLIP!", Toast.LENGTH_SHORT).show()
                recreate()
            }
        }
    }

    fun tampilkanOpsiMenuDialog(data: DataKonsolidasi) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_opsi_aksi_material, null)
        val tvNamaMaterial = dialogView.findViewById<TextView>(R.id.tvNamaMaterialOpsi)
        val btnTitipGudang = dialogView.findViewById<View>(R.id.btnOptTitipGudang)
        val btnUbahStatus = dialogView.findViewById<View>(R.id.btnOptUbahStatusSlip)
        val tvStatusIcon = dialogView.findViewById<TextView>(R.id.tvOptStatusIcon)
        val tvStatusTitle = dialogView.findViewById<TextView>(R.id.tvOptStatusTitle)
        val tvStatusSubtitle = dialogView.findViewById<TextView>(R.id.tvOptStatusSubtitle)

        tvNamaMaterial.text = data.namaBarang

        if (data.isTanpaSlip) {
            tvStatusIcon.text = "✅"
            tvStatusIcon.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#ECFDF5"))
            tvStatusTitle.text = "Konfirmasi Slip Diterima (Lunas)"
            tvStatusSubtitle.text = "Tandai administrasi slip fisik material ini sudah lunas"
        } else {
            tvStatusIcon.text = "📄"
            tvStatusIcon.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FFEBEE"))
            tvStatusTitle.text = "Ubah Status Jadi Belum Ada Slip"
            tvStatusSubtitle.text = "Tandai material ini belum terbit slip fisiknya"
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setNegativeButton("BATAL", null)
            .create()

        dialog.show()

        btnTitipGudang.setOnClickListener {
            dialog.dismiss()
            prosesTitipGudangRiwayat(data.namaBarang, data.totalQty, data.satuan, data.listMaterialIds)
        }

        btnUbahStatus.setOnClickListener {
            dialog.dismiss()
            if (data.isTanpaSlip) {
                pelunasanAdministrasi(data.namaBarang, data.listMaterialIds)
            } else {
                ubahStatusKeluarTanpaSlip(data.namaBarang, data.listMaterialIds)
            }
        }
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
        val listMaterialIds: MutableList<Int> = mutableListOf()
    )

    class RiwayatAdapter(private val listData: List<DataKonsolidasi>) : RecyclerView.Adapter<RiwayatAdapter.ViewHolder>() {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvNama = view.findViewById<TextView>(R.id.tvNamaBarangBrankas)
            val tvTotalQty = view.findViewById<TextView>(R.id.tvTotalQty)
            val tvSatuan = view.findViewById<TextView>(R.id.tvSatuanBrankas)
            val tvRincian = view.findViewById<TextView>(R.id.tvRincianGardu)
            val cbNaikTruk = view.findViewById<CheckBox>(R.id.cbNaikTruk)
            val tvSisaWarning: TextView = view.findViewById(R.id.tvSisaWarning)
            val tvBadgeTanpaSlip: TextView? = view.findViewById(R.id.tvBadgeTanpaSlipBrankas)
            val vSideIndicator: View? = view.findViewById(R.id.vSideIndicator)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_brankas, parent, false)
            return ViewHolder(view)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val data = listData[position]
            holder.tvNama.text = if (data.isDraft) "📝 ${data.namaBarang} (DRAFT)" else data.namaBarang
            holder.tvNama.setTextColor(if (data.isDraft) android.graphics.Color.parseColor("#D32F2F") else android.graphics.Color.BLACK)
            holder.tvTotalQty.text = data.totalQty.toString()
            holder.tvSatuan.text = data.satuan

            if (data.rincianPekerjaan.size > 1) {
                val rincianBersih = data.rincianPekerjaan.map { it.removePrefix("- ").trim() }
                holder.tvRincian.text = "Untuk Pekerjaan (${data.rincianPekerjaan.size} Surat Jalan):\n" + rincianBersih.joinToString("\n") { "• $it" }
            } else {
                val rincianTunggal = (data.rincianPekerjaan.firstOrNull() ?: "").removePrefix("- ").trim()
                holder.tvRincian.text = "Untuk Pekerjaan: $rincianTunggal"
            }
            holder.cbNaikTruk.visibility = View.GONE

            val cardView = holder.itemView as? com.google.android.material.card.MaterialCardView

            // Default Style (Material Normal)
            cardView?.setCardBackgroundColor(android.graphics.Color.WHITE)
            holder.vSideIndicator?.setBackgroundColor(android.graphics.Color.parseColor("#1976D2"))

            // 🌟 1. BARANG BELUM DIAMBIL (BADGE KUNING/AMBER + KARTU AMBER + STRIP ORANGE)
            if (data.sisaGudang > 0) {
                holder.tvSisaWarning.visibility = View.VISIBLE
                holder.tvSisaWarning.text = "📦 BARANG BELUM DIAMBIL: ${data.sisaGudang} ${data.satuan}"
                holder.tvSisaWarning.setTextColor(android.graphics.Color.parseColor("#D97706"))
                holder.tvSisaWarning.setBackgroundColor(android.graphics.Color.parseColor("#FEF3C7"))

                cardView?.setCardBackgroundColor(android.graphics.Color.parseColor("#FFFBEB"))
                holder.vSideIndicator?.setBackgroundColor(android.graphics.Color.parseColor("#F59E0B"))
            } else {
                holder.tvSisaWarning.visibility = View.GONE
            }

            // 🌟 2. BELUM JADI SLIP (BADGE MERAH + KARTU MERAH MUDA + STRIP MERAH)
            if (data.isTanpaSlip || data.isDraft) {
                holder.tvBadgeTanpaSlip?.visibility = View.VISIBLE
                holder.tvBadgeTanpaSlip?.text = "⚠️ BELUM JADI SLIP"
                holder.tvBadgeTanpaSlip?.setTextColor(android.graphics.Color.parseColor("#D32F2F"))
                holder.tvBadgeTanpaSlip?.setBackgroundColor(android.graphics.Color.parseColor("#FFEBEE"))

                cardView?.setCardBackgroundColor(android.graphics.Color.parseColor("#FFF5F5"))
                holder.vSideIndicator?.setBackgroundColor(android.graphics.Color.parseColor("#D32F2F"))
            } else {
                holder.tvBadgeTanpaSlip?.visibility = View.GONE
            }

            // 🌟 KLIK LAMA (LONG PRESS): POP-UP PILIHAN MENU
            holder.itemView.setOnLongClickListener {
                val context = holder.itemView.context
                if (context is RiwayatActivity) {
                    context.tampilkanOpsiMenuDialog(data)
                }
                true
            }

            // 🌟 Pop-up Detail Material saat diklik pendek
            holder.itemView.setOnClickListener {
                val context = holder.itemView.context
                val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_detail_material_riwayat, null)

                val tvNamaMaterial = dialogView.findViewById<TextView>(R.id.tvNamaMaterialDetail)
                val tvBadgeDraft = dialogView.findViewById<TextView>(R.id.tvBadgeDraft)
                val tvTotalQty = dialogView.findViewById<TextView>(R.id.tvTotalQtyDetail)
                val tvRincian = dialogView.findViewById<TextView>(R.id.tvRincianPekerjaanDetail)
                val cardStatus = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardStatusDelivery)
                val tvStatusIcon = dialogView.findViewById<TextView>(R.id.tvStatusIcon)
                val tvStatusText = dialogView.findViewById<TextView>(R.id.tvStatusText)
                val btnPelunasanDetail = dialogView.findViewById<Button>(R.id.btnPelunasanDetail)

                tvNamaMaterial.text = data.namaBarang

                if (data.isTanpaSlip || data.isDraft) {
                    tvBadgeDraft.visibility = View.VISIBLE
                    tvBadgeDraft.text = "📄 BELUM JADI SLIP RESMI"
                    btnPelunasanDetail?.visibility = View.VISIBLE
                } else {
                    tvBadgeDraft.visibility = View.GONE
                    btnPelunasanDetail?.visibility = View.GONE
                }

                tvTotalQty.text = "${data.totalQty} ${data.satuan}"
                tvRincian.text = data.rincianPekerjaan.joinToString("\n")

                if (data.sisaGudang > 0) {
                    cardStatus.setCardBackgroundColor(android.graphics.Color.parseColor("#FFFBEB"))
                    cardStatus.strokeColor = android.graphics.Color.parseColor("#FDE68A")
                    tvStatusIcon.text = "⚠️"
                    tvStatusText.text = "Masih ada ${data.sisaGudang} ${data.satuan} yang belum diambil di Gudang Sisa. (Total SJ: ${data.totalSjAsli} ${data.satuan})"
                    tvStatusText.setTextColor(android.graphics.Color.parseColor("#92400E"))
                } else {
                    cardStatus.setCardBackgroundColor(android.graphics.Color.parseColor("#ECFDF5"))
                    cardStatus.strokeColor = android.graphics.Color.parseColor("#A7F3D0")
                    tvStatusIcon.text = "✅"
                    tvStatusText.text = "Status: Semua barang dari Surat Jalan sudah terkirim."
                    tvStatusText.setTextColor(android.graphics.Color.parseColor("#065F46"))
                }

                val alertDialog = MaterialAlertDialogBuilder(context)
                    .setView(dialogView)
                    .setPositiveButton("TUTUP", null)
                    .create()

                btnPelunasanDetail?.setOnClickListener {
                    alertDialog.dismiss()
                    if (context is RiwayatActivity) {
                        context.pelunasanAdministrasi(data.namaBarang, data.listMaterialIds)
                    }
                }

                alertDialog.show()
            }
        }

        override fun getItemCount() = listData.size
    }

    class SuratJalanListAdapter(private val listSj: List<SuratJalanEntity>, private val onClick: (SuratJalanEntity) -> Unit, private val onLongClick: (SuratJalanEntity) -> Unit) : RecyclerView.Adapter<SuratJalanListAdapter.ViewHolder>() {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvNoSj = view.findViewById<TextView>(R.id.tvNoSjItem)
            val tvVendor = view.findViewById<TextView>(R.id.tvVendorItem)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sj_arsip, parent, false)
            return ViewHolder(view)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val sj = listSj[position]
            val isDraft = sj.noSj.contains("DRAFT")
            val cardView = holder.itemView as? androidx.cardview.widget.CardView
            
            if (isDraft) {
                holder.tvNoSj.text = "📝 DRAFT (TANPA SLIP)"
                holder.tvNoSj.setTextColor(android.graphics.Color.parseColor("#D32F2F"))
                holder.tvVendor.text = "Untuk Pekerjaan: ${sj.pekerjaan}"
                cardView?.setCardBackgroundColor(android.graphics.Color.parseColor("#FFF5F5"))
            } else {
                holder.tvNoSj.text = "No SJ: ${sj.noSj}"
                holder.tvNoSj.setTextColor(android.graphics.Color.BLACK)
                holder.tvVendor.text = "Diberikan Kepada: ${sj.vendor}"
                cardView?.setCardBackgroundColor(android.graphics.Color.WHITE)
            }
            
            holder.itemView.setOnClickListener { onClick(sj) }
            holder.itemView.setOnLongClickListener { onLongClick(sj); true }
        }
        override fun getItemCount() = listSj.size
    }
}
