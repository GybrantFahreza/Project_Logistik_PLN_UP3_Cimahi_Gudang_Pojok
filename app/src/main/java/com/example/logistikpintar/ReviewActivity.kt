package com.example.logistikpintar

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReviewActivity : AppCompatActivity() {

    private val scanHalamanDuaLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val teksMentahHal2 = result.data?.getStringExtra("HASIL_TEKS") ?: ""
            val isLandscapeHal2 = result.data?.getBooleanExtra("IS_LANDSCAPE", false) ?: false
            if (teksMentahHal2.isNotBlank()) {
                prosesHasilScanHalamanDua(teksMentahHal2, isLandscapeHal2)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_review)

        val etTanggal = findViewById<EditText>(R.id.etTanggal)
        val etNoSj = findViewById<EditText>(R.id.etNoSj)
        val etVendor = findViewById<EditText>(R.id.etVendor)
        val etPekerjaan = findViewById<EditText>(R.id.etPekerjaan)
        val containerMaterial = findViewById<LinearLayout>(R.id.containerMaterial)
        val btnHapusQty = findViewById<Button>(R.id.btnHapusQty)
        val btnTambahBarang = findViewById<Button>(R.id.btnTambahBarang)
        val btnSimpanBrankas = findViewById<Button>(R.id.btnSimpanBrankas)

        btnTambahBarang.visibility = View.GONE

        btnHapusQty.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("⚠️ Kosongkan Semua QTY?")
                .setMessage("Apakah Anda yakin ingin menghapus seluruh angka Quantity pada form ini? Anda harus mengisinya kembali secara manual.")
                .setPositiveButton("YA, HAPUS") { _, _ ->
                    for (i in 0 until containerMaterial.childCount) {
                        val barisView = containerMaterial.getChildAt(i)
                        val etQty = barisView.findViewById<EditText>(R.id.etQty)
                        etQty.setText("")
                        applyFieldStyle(etQty, "?")
                    }
                    Toast.makeText(this, "Semua QTY berhasil dikosongkan!", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("BATAL", null)
                .show()
        }

        btnTambahBarang.setOnClickListener {
            tambahBarisMaterialManual(containerMaterial, "", "", "BH")
            Toast.makeText(this, "Baris material baru ditambahkan", Toast.LENGTH_SHORT).show()
        }

        val filterKapital = arrayOf<InputFilter>(InputFilter.AllCaps())
        etTanggal.filters = filterKapital
        etNoSj.filters = filterKapital
        etVendor.filters = filterKapital
        etPekerjaan.filters = filterKapital

        attachValidationWatcher(etTanggal, "?")
        attachValidationWatcher(etNoSj, "?")
        attachValidationWatcher(etVendor, "?")
        attachValidationWatcher(etPekerjaan, "?")

        // 🌟 INIT KAMUS MATERIAL BERBASIS KODE (CSV)
        MaterialDictionary.init(this)

        val teksMentahAi = intent.getStringExtra("HASIL_TEKS") ?: ""
        val isLandscape = intent.getBooleanExtra("IS_LANDSCAPE", false)

        if (teksMentahAi.isNotBlank()) {
            Toast.makeText(this, "AI Sedang Menyusun Tabel...", Toast.LENGTH_SHORT).show()

            // 🌟 JIKA LANDSCAPE GUNAKAN PARSER LANDSCAPE, JIKA PORTRAIT GUNAKAN PARSER ASLI 100% TANPA DIUBAH
            val dataSj = if (isLandscape) {
                bedahDataMenjadiTabelLandscape(teksMentahAi)
            } else {
                bedahDataMenjadiTabel(teksMentahAi)
            }

            etTanggal.setText(if (isFieldEmptyOrQuestion(dataSj.tanggal)) "" else dataSj.tanggal)
            etNoSj.setText(if (isFieldEmptyOrQuestion(dataSj.noSj)) "" else dataSj.noSj)
            etVendor.setText(if (isFieldEmptyOrQuestion(dataSj.vendor)) "" else dataSj.vendor)
            etPekerjaan.setText(if (isFieldEmptyOrQuestion(dataSj.pekerjaan)) "" else dataSj.pekerjaan)

            applyFieldStyle(etTanggal, "?")
            applyFieldStyle(etNoSj, "?")
            applyFieldStyle(etVendor, "?")
            applyFieldStyle(etPekerjaan, "?")

            containerMaterial.removeAllViews()
            if (dataSj.materials.isNotEmpty()) {
                btnTambahBarang.visibility = View.GONE
                for (i in dataSj.materials.indices) {
                    val material = dataSj.materials[i]
                    tambahBarisMaterialManual(containerMaterial, material.nama, material.qty, material.satuan)
                }
            } else {
                // 🌟 KONDISI KHUSUS: Hanya jika daftar material sama sekali tidak terbaca oleh AI,
                // tampilkan tombol tambah baris manual dan buat 1 baris form kosong.
                btnTambahBarang.visibility = View.VISIBLE
                tambahBarisMaterialManual(containerMaterial, "", "", "BH")
                Toast.makeText(this, "⚠️ Daftar Material tidak terdeteksi. Silakan isi form manual di bawah.", Toast.LENGTH_LONG).show()
            }

            if (dataSj.adaHalamanLanjutan) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("📄 Halaman 2 Terdeteksi !")
                    .setMessage("Aplikasi membaca bahwa Surat Jalan ini memiliki Lanjutan Halaman (Halaman 2).\n\nSilakan tekan tombol '📷 SCAN / FOTO HALAMAN 2' di bawah ini untuk mengambil foto Halaman 2 agar seluruh material dari Halaman 1 dan Halaman 2 dikumpulkan terlebih dahulu!")
                    .setPositiveButton("📷 SCAN / FOTO HALAMAN 2") { _, _ ->
                        val intentHal2 = Intent(this@ReviewActivity, ScannerActivity::class.java)
                        intentHal2.putExtra("IS_FOR_HALAMAN_DUA", true)
                        scanHalamanDuaLauncher.launch(intentHal2)
                    }
                    .setNegativeButton("PROSES HALAMAN 1 SAJA", null)
                    .show()
            }
        }

        btnSimpanBrankas.setOnClickListener {
            val listKolomKosong = mutableListOf<String>()

            // Validasi Field Header
            if (isFieldEmptyOrQuestion(etTanggal.text.toString())) {
                listKolomKosong.add("• Tanggal Surat (Kosong / ?)")
                applyFieldStyle(etTanggal, "?")
            }
            if (isFieldEmptyOrQuestion(etNoSj.text.toString())) {
                listKolomKosong.add("• Nomor Surat Jalan (Kosong / ?)")
                applyFieldStyle(etNoSj, "?")
            }
            if (isFieldEmptyOrQuestion(etVendor.text.toString())) {
                listKolomKosong.add("• Diberikan Kepada (Kosong / ?)")
                applyFieldStyle(etVendor, "?")
            }
            if (isFieldEmptyOrQuestion(etPekerjaan.text.toString())) {
                listKolomKosong.add("• Untuk Pekerjaan (Kosong / ?)")
                applyFieldStyle(etPekerjaan, "?")
            }

            // Validasi Material Items
            if (containerMaterial.childCount == 0) {
                listKolomKosong.add("• Daftar Material (Belum ada material yang terscan)")
            } else {
                for (i in 0 until containerMaterial.childCount) {
                    val barisView = containerMaterial.getChildAt(i)
                    val etNamaBarang = barisView.findViewById<EditText>(R.id.etNamaBarang)
                    val etQty = barisView.findViewById<EditText>(R.id.etQty)
                    val etSatuan = barisView.findViewById<EditText>(R.id.etSatuan)

                    val noUrut = i + 1
                    val nama = etNamaBarang.text.toString().trim()
                    val qtyStr = etQty.text.toString().trim()
                    val satuan = etSatuan.text.toString().trim()

                    if (isFieldEmptyOrQuestion(nama)) {
                        listKolomKosong.add("• Material No. $noUrut: Nama Barang (Kosong / ?)")
                        applyFieldStyle(etNamaBarang, "?")
                    }
                    if (isFieldEmptyOrQuestion(qtyStr)) {
                        listKolomKosong.add("• Material No. $noUrut: QTY (?)")
                        applyFieldStyle(etQty, "?")
                    }
                    if (isFieldEmptyOrQuestion(satuan)) {
                        listKolomKosong.add("• Material No. $noUrut: Satuan (Kosong / ?)")
                        applyFieldStyle(etSatuan, "?")
                    }
                }
            }

            if (listKolomKosong.isNotEmpty()) {
                val pesan = StringBuilder()
                pesan.append("Tidak dapat menyimpan ke Brankas karena masih terdapat kolom yang belum terisi / terscan:\n\n")
                listKolomKosong.forEach { item ->
                    pesan.append(item).append("\n")
                }
                pesan.append("\nMohon lengkapi data pada kolom yang berwarna merah terlebih dahulu!")

                MaterialAlertDialogBuilder(this)
                    .setTitle("⚠️ Data Belum Lengkap!")
                    .setMessage(pesan.toString())
                    .setPositiveButton("SAYA MENGERTI", null)
                    .show()
            } else {
                MaterialAlertDialogBuilder(this)
                    .setTitle("💾 Konfirmasi Simpan ke Brankas")
                    .setMessage("Apakah rincian data Surat Jalan dan daftar material sudah sesuai dan tepat untuk disimpan ke Brankas?")
                    .setPositiveButton("YA, SUDAH BENAR") { _, _ ->
                        prosesSimpanKeBrankas(etTanggal, etNoSj, etVendor, etPekerjaan, containerMaterial)
                    }
                    .setNegativeButton("PERIKSA KEMBALI", null)
                    .show()
            }
        }
    }

    private fun isFieldEmptyOrQuestion(value: String?): Boolean {
        val trimmed = value?.trim() ?: ""
        return trimmed.isEmpty() || trimmed == "?" || trimmed == "[?]" || trimmed == "-"
    }

    private fun applyFieldStyle(editText: EditText, defaultHint: String = "?") {
        val pLeft = editText.paddingLeft
        val pTop = editText.paddingTop
        val pRight = editText.paddingRight
        val pBottom = editText.paddingBottom

        val text = editText.text.toString().trim()
        val isError = isFieldEmptyOrQuestion(text)

        if (isError) {
            if (text == "?" || text == "[?]" || text == "-") {
                editText.setText("")
            }
            editText.hint = defaultHint
            editText.setHintTextColor(Color.parseColor("#D32F2F"))
            editText.setTextColor(Color.parseColor("#D32F2F"))
            editText.setBackgroundResource(R.drawable.bg_edit_text_error)
        } else {
            editText.setTextColor(Color.parseColor("#1F2937"))
            editText.setHintTextColor(Color.parseColor("#6B7280"))
            editText.setBackgroundResource(R.drawable.bg_edit_text_normal)
        }

        editText.setPadding(pLeft, pTop, pRight, pBottom)
    }

    private fun attachValidationWatcher(editText: EditText, defaultHint: String = "?") {
        applyFieldStyle(editText, defaultHint)
        editText.addTextChangedListener(object : TextWatcher {
            private var isUpdating = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isUpdating) return
                isUpdating = true
                applyFieldStyle(editText, defaultHint)
                isUpdating = false
            }
        })
    }

    private fun tambahBarisMaterialManual(
        containerMaterial: LinearLayout,
        nama: String = "",
        qty: String = "",
        satuan: String = "BH"
    ) {
        val index = containerMaterial.childCount
        val barisView = LayoutInflater.from(this).inflate(R.layout.item_material, containerMaterial, false)

        val tvNoUrut = barisView.findViewById<TextView>(R.id.tvNoUrut)
        val etNamaBarang = barisView.findViewById<EditText>(R.id.etNamaBarang)
        val etQty = barisView.findViewById<EditText>(R.id.etQty)
        val etSatuan = barisView.findViewById<EditText>(R.id.etSatuan)

        val filterKapital = arrayOf<InputFilter>(InputFilter.AllCaps())
        etNamaBarang.filters = filterKapital
        etSatuan.filters = filterKapital

        tvNoUrut.text = String.format(java.util.Locale.getDefault(), "%d.", index + 1)

        val namaTampil = if (isFieldEmptyOrQuestion(nama)) "" else nama
        etNamaBarang.setText(namaTampil)

        val qtyTampil = if (isFieldEmptyOrQuestion(qty)) "" else qty
        etQty.setText(qtyTampil)

        val satuanTampil = if (isFieldEmptyOrQuestion(satuan)) "" else satuan
        etSatuan.setText(satuanTampil)

        attachValidationWatcher(etNamaBarang, "?")
        attachValidationWatcher(etQty, "?")
        attachValidationWatcher(etSatuan, "?")

        containerMaterial.addView(barisView)
    }

    private fun prosesSimpanKeBrankas(
        etTanggal: EditText,
        etNoSj: EditText,
        etVendor: EditText,
        etPekerjaan: EditText,
        containerMaterial: LinearLayout
    ) {
        val tgl = etTanggal.text.toString().trim()
        val no = etNoSj.text.toString().trim()
        val ven = etVendor.text.toString().trim()
        val pek = etPekerjaan.text.toString().trim()

        val listMaterialSimpan = mutableListOf<MaterialEntity>()
        for (i in 0 until containerMaterial.childCount) {
            val barisView = containerMaterial.getChildAt(i)
            val etNama = barisView.findViewById<EditText>(R.id.etNamaBarang)
            val etQty = barisView.findViewById<EditText>(R.id.etQty)
            val etSatuan = barisView.findViewById<EditText>(R.id.etSatuan)

            val nama = etNama.text.toString().trim()
            val qtyStr = etQty.text.toString().trim()
            val satuan = etSatuan.text.toString().trim()

            val qty = if (qtyStr.isNotEmpty()) qtyStr.toIntOrNull() ?: 0 else 0

            if (nama.isNotEmpty()) {
                listMaterialSimpan.add(MaterialEntity(namaBarang = nama, qty = qty, satuan = satuan, idSuratJalan = 0))
            }
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val db = LogistikDatabase.getDatabase(this@ReviewActivity).logistikDao()
            // 🌟 1. Bersihkan Surat Jalan Kosong (0 material) secara otomatis
            db.deleteOrphanSuratJalan()

            val sjExist = db.getSuratJalanByNoSjUtuh(no) ?: db.getSuratJalanByNoSj(no)
            if (sjExist != null) {
                val materialsOld = db.getMaterialsBySjId(sjExist.id)
                val countOld = materialsOld.size

                // 🌟 Jika Surat Jalan lama 0 material, hapus header kosongnya dan izinkan simpan baru!
                if (countOld == 0) {
                    db.deleteSuratJalan(sjExist)
                } else {
                    val statusLokasi = if (materialsOld.any { it.status == "ARSIP" }) {
                        "Sudah Masuk Laporan Logistik (Tanggal Laporan: ${sjExist.tanggal})"
                    } else {
                        "Masih Tersimpan di Brankas ($countOld Material Terdaftar)"
                    }

                    withContext(Dispatchers.Main) {
                        MaterialAlertDialogBuilder(this@ReviewActivity)
                            .setTitle("⚠️ Surat Jalan Sudah Pernah Diinput!")
                            .setMessage(
                                "Surat Jalan dengan nomor '$no' sudah terdaftar di dalam database.\n\n" +
                                        "• Tanggal Terdaftar: ${sjExist.tanggal}\n" +
                                        "• Status / Lokasi Data: $statusLokasi\n" +
                                        "• Diberikan Kepada: ${sjExist.vendor}\n" +
                                        "• Untuk Pekerjaan: ${sjExist.pekerjaan}\n\n" +
                                        "Mohon periksa kembali nomor Surat Jalan Anda. Jika ingin mengedit atau menghapusnya, silakan lakukan langsung melalui menu Brankas / Laporan Logistik."
                            )
                            .setPositiveButton("SAYA MENGERTI", null)
                            .show()
                    }
                    return@launch
                }
            }

            val idSjBaru = db.insertSuratJalan(SuratJalanEntity(tanggal = tgl, noSj = no, vendor = ven, pekerjaan = pek))
            listMaterialSimpan.forEach { material ->
                db.insertMaterial(material.copy(idSuratJalan = idSjBaru.toInt()))
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(this@ReviewActivity, "✅ Data tersimpan aman di Brankas!", Toast.LENGTH_LONG).show()
                val intent = Intent(this@ReviewActivity, BrankasActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            }
        }
    }

    private fun prosesHasilScanHalamanDua(teksMentahHal2: String, isLandscapeHal2: Boolean) {
        val etNoSj = findViewById<EditText>(R.id.etNoSj)

        val dataSj2 = if (isLandscapeHal2) {
            bedahDataMenjadiTabelLandscape(teksMentahHal2)
        } else {
            bedahDataMenjadiTabel(teksMentahHal2)
        }

        val noSj1 = etNoSj.text.toString().trim()
        val noSj2 = dataSj2.noSj.trim()

        val clean1 = noSj1.replace(Regex("""[^A-Z0-9]"""), "")
        val clean2 = noSj2.replace(Regex("""[^A-Z0-9]"""), "")

        val isNomorSama = clean2.isBlank() || clean1 == clean2 || clean1.contains(clean2) || clean2.contains(clean1)

        if (isNomorSama) {
            gabungkanMaterialHalamanDua(dataSj2.materials)
        } else {
            MaterialAlertDialogBuilder(this)
                .setTitle("⚠️ Nomor Surat Jalan Berbeda!")
                .setMessage(
                    "Nomor Surat Jalan pada Halaman 2 ('${dataSj2.noSj}') berbeda dengan Halaman 1 ('$noSj1').\n\n" +
                            "Apakah Anda yakin ingin tetap menggabungkan material dari Halaman 2 ini?"
                )
                .setPositiveButton("YA, TETAP GABUNGKAN") { _, _ ->
                    gabungkanMaterialHalamanDua(dataSj2.materials)
                }
                .setNegativeButton("BATAL", null)
                .show()
        }
    }

    private fun gabungkanMaterialHalamanDua(materialsHal2: List<ItemMaterial>) {
        val containerMaterial = findViewById<LinearLayout>(R.id.containerMaterial)
        if (materialsHal2.isNotEmpty()) {
            var jumlahDitambah = 0
            for (material in materialsHal2) {
                if (material.nama.isNotBlank()) {
                    tambahBarisMaterialManual(containerMaterial, material.nama, material.qty, material.satuan)
                    jumlahDitambah++
                }
            }
            Toast.makeText(
                this,
                "✅ Berhasil menggabungkan $jumlahDitambah Material dari Halaman 2! Total: ${containerMaterial.childCount} Material.",
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(this, "⚠️ Tidak ada material baru yang terdeteksi pada Halaman 2.", Toast.LENGTH_SHORT).show()
        }
    }

    data class DataSuratJalan(
        val tanggal: String, val noSj: String, val vendor: String, val pekerjaan: String,
        val materials: List<ItemMaterial>, val adaHalamanLanjutan: Boolean
    )
    data class ItemMaterial(val nama: String, val qty: String, val satuan: String)

    /**
     * Pembersihan cerdas untuk teks kotor hasil OCR (Sesuai 3 Aturan / Rules)
     */
    private fun pembersihanTeksKotor(text: String): String {
        var clean = text

        // Rule 1: Ganti : jadi ;
        clean = clean.replace(":", ";")

        // Rule 2: Ganti U aneh/berekor jadi U biasa
        clean = clean.replace(Regex("""[µÜú]"""), "U")

        // Rule 3: Smart O vs 0
        // Jika 0 diapit huruf (contoh: C0NDUCT0R), ubah jadi O
        clean = clean.replace(Regex("""([A-Z])0([A-Z])"""), "$1O$2")
        // Ulangi sekali lagi jaga-jaga ada 0 beruntun
        clean = clean.replace(Regex("""([A-Z])0([A-Z])"""), "$1O$2")
        
        // Jika O besar bersebelahan dengan angka (contoh: 2O atau O2), ubah jadi 0
        clean = clean.replace(Regex("""(\d)O"""), "$10")
        clean = clean.replace(Regex("""O(\d)"""), "0$1")

        // Rule 4: Hapus titik koma (;) di akhir nama material agar tidak membingungkan/duplikat
        clean = clean.replace(Regex(""";+$"""), "").trim()

        return clean
    }

    private fun normalisasiSatuan(satuanRaw: String, teksBarang: String): String {
        val sat = satuanRaw.uppercase().trim()
        return when (sat) {
            "BH", "BE", "BL", "8L", "3L", "3H", "8H", "EH", "B", "BN" -> "BH"
            "SET", "S3T", "SFT", "SJT" -> "SET"
            "M", "MTR", "METER" -> "M"
            "BTG", "BT", "BATANG" -> "BTG"
            "ROL", "ROLL" -> "ROL"
            "PCS", "PC" -> "PCS"
            "UNIT", "U", "UNT" -> "UNIT"
            else -> {
                when {
                    teksBarang.contains("CONDUCTOR") || teksBarang.contains("KABEL") || teksBarang.contains("CABLE") -> "M"
                    teksBarang.contains("PIPE") || teksBarang.contains("PIPA") -> "BTG"
                    teksBarang.contains("TRAVERS") || teksBarang.contains("POLE") -> "SET"
                    else -> "BH"
                }
            }
        }
    }

    // 🌟 KODE ASLI BEDAH DATA UNTUK PORTRAIT (100% UTUH DAN TIDAK DIUBAH SAMA SEKALI)
    private fun bedahDataMenjadiTabel(teksMentah: String): DataSuratJalan {
        val teks = teksMentah.uppercase()
        val adaLanjutan = teks.contains("LANJUT HALAMAN")

        val tanggal = Regex("""\b\d{2}/\d{2}/\d{4}\b""").find(teks)?.value ?: ""

        val noSjRaw = Regex("""\d{3,4}[.,\s]*[A-Z]+[I|\\.!1l\s/]*LOG[^\n]+""").find(teks)?.value?.trim()
            ?: Regex("""[A-Z0-9.,\s/|-]*LOG[^\n]+""").find(teks)?.value?.trim() ?: ""

        val noSj = noSjRaw.replace(",", ".").replace(Regex("""\b(TP|SJ|SPK|KR|SUTR)[I|\\.!1l\s/]*LOG"""), "$1/LOG")
            .replace(Regex("""([A-Z]+)I[\s/]*LOG"""), "$1/LOG").replace(Regex("""([A-Z]+)\|[\s/]*LOG"""), "$1/LOG")
            .replace(Regex("""/F[O0]"""), "/F0").replace(Regex("""\bF[O0]"""), "F0").replace("FO", "F0")

        // 3. Vendor / Diberikan Kepada (Logika Landscape disesuaikan untuk Portrait)
        var vendor = ""
        if (teks.contains("DIBERIKAN KEPADA")) {
            val sthKepada = teks.substringAfter("DIBERIKAN KEPADA").substringBefore("\n").trimStart(':', '=', ' ', '\t')
            val vendorOnLine = if (sthKepada.contains("UNTUK PEKERJAAN")) {
                sthKepada.substringBefore("UNTUK PEKERJAAN")
            } else if (sthKepada.contains("PEKERJAAN")) {
                sthKepada.substringBefore("PEKERJAAN")
            } else {
                sthKepada
            }

            val cleanK = vendorOnLine
                .replace("|", " ").replace("\\", " ")
                .replace(Regex("""\bCIMAH\b""", RegexOption.IGNORE_CASE), "CIMAHI")
                .replace(Regex("""\b(UNTUK|PEKERJAAN|PERSEDIAAN|GUDANG|JENIS|NAMA|MATERIAL|SECURITY|ARSIP|QUANTITY|SATUAN|NOMOR|SLIP|TANGGAL|\bVGG\b|\bSPK\b|\bKR\b|\bSUTM\b|\bSUTR\b|\bRCP\b|\bTMJ\b|\(PT|PT\b).*"""), "")
                .replace("PERSERO", "").replace("UID", "").replace("JAWA BARAT", "").replace("PT PLN", "")
                .trim()
            vendor = cleanK.split(" ").filter { it.isNotBlank() }.distinct().joinToString(" ")
        }

        if (vendor.isBlank() || vendor == "PLN" || vendor == "PLN JAWA" || vendor.contains("JAWA")) {
            val vendorTarget = Regex("""(PLN UP3 [A-Z ]+|PLN ULP [A-Z ]+|CIMAHI SELATAN|CIMAHI KOTA|PADALARANG|CILILIN|LEMBANG|PURWAKARTA|PRIMACIBABAT|CIBABAT)""").find(teks)?.value ?: ""
            if (vendorTarget.isNotBlank()) {
                val vClean = vendorTarget
                    .replace(Regex("""\b(PERSERO|UID|JAWA|BARAT|PERSADA|ADI|RACHINDO|BPRK|SPK|SUTR|KR|TOTAL|DAYA|PRGA)\b.*$"""), "")
                    .trim()
                vendor = vClean.split(" ").filter { it.isNotBlank() }.distinct().joinToString(" ")
            }
        }

        vendor = vendor
            .replace(Regex("""\bCIMAH\b""", RegexOption.IGNORE_CASE), "CIMAHI")
            .replace("|", " ").replace("\\", " ")
            .replace(Regex("""\b(UTAMA|PERSADA|ADI|RACHINDO|BPRK|SPK|SUTR|KR|TOTAL|DAYA|PRGA|PERSERO|UID|JAWA|BARAT)\b.*$"""), "")
            .trim()

        vendor = vendor
            .replace(Regex("""\b(PADALARANG|RAJAMANDALA|CILILIN|LEMBANG|PURWAKARTA|SUKABUMI|CIMAHI SELATAN|CIMAHI KOTA|CIMAHI|CIBABAT|PRIMACIBABAT)\b.*$"""), "$1")
            .replace(Regex("""\s+"""), " ")
            .trim()

        vendor = vendor.split(" ").filter { it.isNotBlank() }.distinct().joinToString(" ")

        // 4. Untuk Pekerjaan (Multi-Baris Logika Landscape disesuaikan untuk Portrait)
        val listPekerjaan = mutableListOf<String>()

        val regexPekerjaan = Regex("""(CROSSING[A-Z0-9.\-(); ]+|\bKR[-.\s/][A-Z0-9.\-(); ]+|\bSPK[A-Z0-9.\-(); ]+|\bSUTR[A-Z0-9.\-(); ]+|\bSUTM[A-Z0-9.\-(); ]+|PERSEDIAAN[A-Z0-9.\-(); ]+|\bSTO\b[A-Z0-9.\-(); ]*|MATERIAL STO[A-Z0-9.\-(); ]*|PEMELIHARAAN[A-Z0-9.\-(); ]+|\bGANTI\b[A-Z0-9.\-(); ]*|\bPENGGANTIAN\b[A-Z0-9.\-(); ]*|\bPASANG\b[A-Z0-9.\-(); ]*|\bDUKUNGAN\b[A-Z0-9.\-(); ]*|\bREHABILITASI\b[A-Z0-9.\-(); ]*|\bGANGGUAN\b[A-Z0-9.\-(); ]*)""", RegexOption.IGNORE_CASE)
        val temuanAwal = regexPekerjaan.find(teks)

        if (temuanAwal != null) {
            val sisaTeks = teks.substring(temuanAwal.range.first).trim()
            val barisBerikutnyaList = sisaTeks.split("\n").map { it.trim() }

            for (i in barisBerikutnyaList.indices) {
                var baris = barisBerikutnyaList[i]

                val isStop = baris.contains("NAMA MATERIAL") || baris.contains("QUANTITY") || baris.contains("SATUAN") || baris.contains("NOMOR SLIP") || baris.startsWith("NO ") || baris.startsWith("NO.") || baris.contains("DIBUAT OLEH") || baris.contains("DISETUJUI OLEH") || baris.contains("SECURITY")

                if (isStop && i > 0) break

                if (baris.contains("BERDASARKAN")) {
                    baris = baris.replace(Regex("""BERDASARKAN\s*\d+"""), " ").trim()
                }
                if (baris.contains("UNTUK PEKERJAAN")) {
                    baris = baris.substringAfter("UNTUK PEKERJAAN").trim()
                } else if (baris.contains("PEKERJAAN")) {
                    baris = baris.substringAfter("PEKERJAAN").trim()
                } else if (baris.contains("DIBERIKAN KEPADA")) {
                    baris = baris.substringAfter("DIBERIKAN KEPADA").trim()
                }

                baris = baris
                    .replace("UNTUK SECURITY", " ")
                    .replace("UNTUK", " ")
                    .replace("PEKERJAAN", " ")
                    .replace(Regex("""\b(JENIS|SURAT|JALAN|NORMAL|TRANSFER|POSTING|TANGGAL|NOMOR)\b"""), " ")
                    .replace(Regex("""\s+"""), " ")
                    .trim()

                if (baris.isNotBlank() && baris.length < 80) {
                    if (listPekerjaan.isEmpty() || !listPekerjaan.last().contains(baris)) {
                        listPekerjaan.add(baris)
                    }
                }
            }
        }

        var pekerjaan = listPekerjaan.joinToString(" ").trim()

        if (pekerjaan.isBlank()) {
            val sthPekerjaan = if (teks.contains("UNTUK PEKERJAAN")) {
                teks.substringAfter("UNTUK PEKERJAAN")
            } else if (teks.contains("PEKERJAAN")) {
                teks.substringAfter("PEKERJAAN")
            } else ""

            if (sthPekerjaan.isNotBlank()) {
                val barisPekerjaan = sthPekerjaan.trimStart(':', '=', ' ', '\t', '\r', '\n').substringBefore("\n").trim()
                val cleanP = barisPekerjaan
                    .replace(Regex("""(BERDASARKAN|DIBERIKAN|KEPADA|JENIS|NORMAL|QUANTITY|NOMOR|SLIP|TANGGAL|NAMA|MATERIAL|SECURITY|ARSIP|DIBUAT|DISETUJUI).*"""), "")
                    .trim()
                if (cleanP.isNotBlank() && cleanP.length > 2 && !cleanP.contains("SURAT JALAN")) {
                    pekerjaan = cleanP
                }
            }
        }

        pekerjaan = pekerjaan
            .replace(Regex("""\bCIMAH\b""", RegexOption.IGNORE_CASE), "CIMAHI")
            .replace(Regex("""\bRESERVASI[-:\s]*\d*\b""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\bBERDASARKAN[-:\s]*\d*\b""", RegexOption.IGNORE_CASE), " ")
            .replace("|", " ").replace("\\", " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val isPersediaanAtauSto = pekerjaan.startsWith("PERSEDIAAN") || pekerjaan.startsWith("STO") || pekerjaan.contains("MATERIAL STO")

        if (isPersediaanAtauSto) {
            if (vendor.isNotBlank()) {
                val duplicateVendorPattern = Regex("""(${Regex.escape(vendor)})\s+\1""", RegexOption.IGNORE_CASE)
                pekerjaan = pekerjaan.replace(duplicateVendorPattern, "$1")
                pekerjaan = pekerjaan.replace(Regex("""\bPERSEDIAAN GUDANG PLN ULP\b""", RegexOption.IGNORE_CASE), "PERSEDIAAN GUDANG")
            }
        } else {
            if (vendor.isNotBlank()) {
                pekerjaan = pekerjaan.replace(Regex("""\bPLN\s+ULP\s+${Regex.escape(vendor)}\b""", RegexOption.IGNORE_CASE), " ")
                pekerjaan = pekerjaan.replace(Regex("""\bPLN\s+UP3\s+${Regex.escape(vendor)}\b""", RegexOption.IGNORE_CASE), " ")
                pekerjaan = pekerjaan.replace(Regex("""\b${Regex.escape(vendor)}\b""", RegexOption.IGNORE_CASE), " ")

                val vendorCleanName = vendor
                    .replace(Regex("""\b(PLN|ULP|UP3)\b""", RegexOption.IGNORE_CASE), "")
                    .trim()
                if (vendorCleanName.isNotBlank()) {
                    pekerjaan = pekerjaan.replace(Regex("""\bPLN\s+ULP\s+${Regex.escape(vendorCleanName)}\b""", RegexOption.IGNORE_CASE), " ")
                    pekerjaan = pekerjaan.replace(Regex("""\bPLN\s+UP3\s+${Regex.escape(vendorCleanName)}\b""", RegexOption.IGNORE_CASE), " ")
                }
            }
            pekerjaan = pekerjaan.replace(Regex("""\bPLN\s+ULP\s+[A-Z ]+$""", RegexOption.IGNORE_CASE), " ")
            pekerjaan = pekerjaan.replace(Regex("""\bPLN\s+UP3\s+[A-Z ]+$""", RegexOption.IGNORE_CASE), " ")
        }

        val kataList = pekerjaan.split(" ")
        val kataBersih = mutableListOf<String>()
        for (k in kataList) {
            if (k.isNotBlank() && (kataBersih.isEmpty() || kataBersih.last() != k)) {
                kataBersih.add(k)
            }
        }
        pekerjaan = kataBersih.joinToString(" ").trim()

        pekerjaan = pekerjaan
            .replace(Regex("""\b(ULP\s+[A-Z\s]+)\s+\1\b""", RegexOption.IGNORE_CASE), "$1")
            .replace(Regex("""\b(UP3\s+[A-Z\s]+)\s+\1\b""", RegexOption.IGNORE_CASE), "$1")
            .replace(Regex("""\b(CIMAHI SELATAN)\s+\1\b""", RegexOption.IGNORE_CASE), "$1")
            .replace(Regex("""\b(CIMAHI KOTA)\s+\1\b""", RegexOption.IGNORE_CASE), "$1")
            .replace(Regex("""\b(PADALARANG)\s+\1\b""", RegexOption.IGNORE_CASE), "$1")
            .replace(Regex("""\b(CILILIN)\s+\1\b""", RegexOption.IGNORE_CASE), "$1")
            .replace(Regex("""\b(RAJAMANDALA)\s+\1\b""", RegexOption.IGNORE_CASE), "$1")
            .replace(Regex("""\b(PRIMA CIBABAT)\s+\1\b""", RegexOption.IGNORE_CASE), "$1")
            .replace(Regex("""\b(LEMBANG)\s+\1\b""", RegexOption.IGNORE_CASE), "$1")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val finalMaterials = mutableListOf<ItemMaterial>()

        val teksTabelMaterial = if (teks.contains("NAMA MATERIAL")) {
            teks.substringAfter("NAMA MATERIAL")
        } else if (teks.contains("QUANTITY")) {
            teks.substringAfter("QUANTITY")
        } else if (teks.contains("SATUAN")) {
            teks.substringAfter("SATUAN")
        } else {
            teks
        }

        val barisTeks = teksTabelMaterial.split("\n")

        val unitPatternStr = """(BH|BE|BL|8L|3L|3H|8H|EH|B|BN|SET|S3T|SFT|SJT|M|MTR|BTG|BT|ROL|ROLL|PCS|PC|ZAK|KG|UNIT|UNT|\bU\b)"""
        // Menangkap [Kode Material] di Group 1, dan [Sisa Deskripsi] di Group 2
        val regexKunciMaterial = Regex("""\b([A-Z]{1,3}\d{5,8}|\d{6,8})\b\s*[-:]?\s*(.+)""", RegexOption.IGNORE_CASE)
        val regexQtySatuan = Regex("""\b(\d{1,5})\s*${unitPatternStr}\b""", RegexOption.IGNORE_CASE)
        val regexSatuanSaja = Regex("""\b${unitPatternStr}\b""", RegexOption.IGNORE_CASE)

        for (baris in barisTeks) {
            val lineUpper = baris.trim().uppercase()

            val isForbiddenLine = lineUpper.contains("UNTUK PEKERJAAN") || lineUpper.contains("PEKERJAAN") ||
                    lineUpper.contains("BERDASARKAN") || lineUpper.contains("DIBERIKAN KEPADA") ||
                    lineUpper.contains("SURAT JALAN") || lineUpper.contains("DIBUAT OLEH") ||
                    lineUpper.contains("DISETUJUI OLEH") || lineUpper.contains("SECURITY")

            if (isForbiddenLine) continue
            val temuanKunci = regexKunciMaterial.find(baris)

            if (temuanKunci != null) {
                val kodeKotor = temuanKunci.groupValues[1].trim()
                var teksBarangUtuh = temuanKunci.groupValues[2].trim()

                teksBarangUtuh = teksBarangUtuh
                    .replace(Regex("""\b\d{6,}\b.*$"""), "")
                    .replace(Regex("""\b(QUANTITY|SATUAN|NOMOR|SLIP|KETERANGAN|NO|NAMA|MATERIAL|DIBUAT|DISETUJUI)\b.*$"""), "")
                    .trim()

                var qty = "[?]"
                var satuan = ""

                val temuanQS = regexQtySatuan.find(teksBarangUtuh)
                if (temuanQS != null) {
                    qty = temuanQS.groupValues[1]
                    satuan = normalisasiSatuan(temuanQS.groupValues[2], teksBarangUtuh)
                } else {
                    val temuanS = regexSatuanSaja.findAll(teksBarangUtuh).lastOrNull()
                    if (temuanS != null) {
                        satuan = normalisasiSatuan(temuanS.value, teksBarangUtuh)
                        val teksSebelumSatuan = teksBarangUtuh.substring(0, temuanS.range.first).trim()
                        val temuanAngka = Regex("""\b\d{1,5}\b$""").find(teksSebelumSatuan)
                        if (temuanAngka != null) {
                            qty = temuanAngka.value
                        }
                    } else {
                        val temuanAngkaUjung = Regex("""[;\s]+(\d{1,5})[-=:;,\s.]*$""").find(teksBarangUtuh)
                        if (temuanAngkaUjung != null) {
                            qty = temuanAngkaUjung.groupValues[1]
                            satuan = normalisasiSatuan("", teksBarangUtuh)
                        }
                    }
                }

                var namaBersih = teksBarangUtuh
                if (qty != "[?]" && satuan.isNotBlank()) {
                    namaBersih = namaBersih.replace(Regex("""\b$qty\b.*$"""), "")
                } else if (qty != "[?]") {
                    namaBersih = namaBersih.replace(Regex("""\b$qty\b.*$"""), "")
                } else if (satuan.isNotBlank()) {
                    namaBersih = namaBersih.replace(Regex("""\b$satuan\b.*$"""), "")
                }

                namaBersih = namaBersih
                    .replace(Regex("""\b(QUANTITY|SATUAN|NOMOR|SLIP|KETERANGAN|NO|NAMA|MATERIAL|DIBUAT|DISETUJUI)\b.*$"""), "")
                    .replace(Regex("""\bTRF\s+DIS[:;]D3?""", RegexOption.IGNORE_CASE), "TRF DIST;")
                    .replace(Regex("""\bTRF\s+DIST[:;]D""", RegexOption.IGNORE_CASE), "TRF DIST;")
                    .replace("|", "I")
                    .replace(Regex(""";\s*[A-Z]\b\s*$"""), "")
                    .replace(Regex("""\b[A-Z]\b\s*$"""), "")
                    .replace(Regex("""[-=:;,\s.]+$"""), "")
                    .replace(Regex("""\s+"""), " ")
                    .trim()

                if (satuan.isBlank()) {
                    satuan = "BH"
                }

                if (namaBersih.isNotBlank() && !namaBersih.contains("PEKERJAAN") && !namaBersih.contains("PERSEDIAAN")) {
                    
                    // 🌟 INTEGRASI KAMUS EKSAS BERBASIS KODE
                    val masterItem = MaterialDictionary.getMaterialByCode(kodeKotor)
                    if (masterItem != null) {
                        // Jika kode ketemu, timpa 100% dengan nama resmi CSV, dan hapus titik koma (;) di akhir
                        namaBersih = masterItem.deskripsi.replace(Regex(""";+$"""), "").trim()
                        if (satuan == "BH" || satuan.isBlank()) {
                            satuan = masterItem.satuan
                        }
                    } else {
                        // Jika kode tidak ketemu di CSV, lakukan pembersihan manual (Fallback)
                        namaBersih = pembersihanTeksKotor(namaBersih)
                    }
                    
                    finalMaterials.add(ItemMaterial(namaBersih, qty, satuan))
                }
            }
        }

        return DataSuratJalan(tanggal, noSj, vendor, pekerjaan, finalMaterials, adaLanjutan)
    }

    // 🌟 KHUSUS LANDSCAPE: PARSER DEDIKASI YANG DIRENCANAKAN KHUSUS FOTO LANDSCAPE
    private fun bedahDataMenjadiTabelLandscape(teksMentah: String): DataSuratJalan {
        val teks = teksMentah.uppercase()
        val adaLanjutan = teks.contains("LANJUT HALAMAN")

        // 1. Tanggal
        val tanggal = Regex("""\b\d{2}/\d{2}/\d{4}\b""").find(teks)?.value ?: ""

        // 2. Nomor Surat Jalan
        val noSjRaw = Regex("""\d{3,4}[.,\s]*[A-Z]+[I|\\.!1l\s/]*LOG[^\n]+""").find(teks)?.value?.trim()
            ?: Regex("""[A-Z0-9.,\s/|-]*LOG[^\n]+""").find(teks)?.value?.trim() ?: ""

        var noSj = noSjRaw.replace(",", ".").replace(Regex("""\b(TP|SJ|SPK|KR|SUTR)[I|\\.!1l\s/]*LOG"""), "$1/LOG")
            .replace(Regex("""([A-Z]+)I[\s/]*LOG"""), "$1/LOG").replace(Regex("""([A-Z]+)\|[\s/]*LOG"""), "$1/LOG")
            .replace(Regex("""/F[O0]"""), "/F0").replace(Regex("""\bF[O0]"""), "F0").replace("FO", "F0")

        noSj = Regex("""(/\d{4})\b.*$""").replace(noSj, "$1")
        noSj = noSj.replace(Regex("""\b(TRANSFER|POSTING|POST|SATUAN|SET|BH|JENIS|NORMAL|UNTUK|PEKERJAAN|DIBERIKAN|KEPADA|TANGGAL|BERDASARKAN|QUANTITY|NOMOR|SLIP|KETERANGAN).*"""), "").trim()

        // 3. Vendor / Diberikan Kepada
        var vendor = ""
        if (teks.contains("DIBERIKAN KEPADA")) {
            val sthKepada = teks.substringAfter("DIBERIKAN KEPADA").substringBefore("\n").trimStart(':', '=', ' ', '\t')
            val vendorOnLine = if (sthKepada.contains("UNTUK PEKERJAAN")) {
                sthKepada.substringBefore("UNTUK PEKERJAAN")
            } else if (sthKepada.contains("PEKERJAAN")) {
                sthKepada.substringBefore("PEKERJAAN")
            } else {
                sthKepada
            }

            val cleanK = vendorOnLine
                .replace("|", " ").replace("\\", " ")
                .replace(Regex("""\bCIMAH\b""", RegexOption.IGNORE_CASE), "CIMAHI")
                .replace(Regex("""\b(UNTUK|PEKERJAAN|PERSEDIAAN|GUDANG|JENIS|NAMA|MATERIAL|SECURITY|ARSIP|QUANTITY|SATUAN|NOMOR|SLIP|TANGGAL|\bVGG\b|\bSPK\b|\bKR\b|\bSUTM\b|\bSUTR\b|\bRCP\b|\bTMJ\b|\(PT|PT\b).*"""), "")
                .replace("PERSERO", "").replace("UID", "").replace("JAWA BARAT", "").replace("PT PLN", "")
                .trim()
            vendor = cleanK.split(" ").filter { it.isNotBlank() }.distinct().joinToString(" ")
        }

        if (vendor.isBlank() || vendor == "PLN" || vendor == "PLN JAWA" || vendor.contains("JAWA")) {
            val vendorTarget = Regex("""(PLN UP3 [A-Z ]+|PLN ULP [A-Z ]+|CIMAHI SELATAN|CIMAHI KOTA|PADALARANG|CILILIN|LEMBANG|PRIMACIBABAT|CIBABAT)""").find(teks)?.value ?: ""
            if (vendorTarget.isNotBlank()) {
                val vClean = vendorTarget
                    .replace(Regex("""\b(PERSERO|UID|JAWA|BARAT|PERSADA|ADI|RACHINDO|BPRK|SPK|SUTR|KR|TOTAL|DAYA|PRGA)\b.*$"""), "")
                    .trim()
                vendor = vClean.split(" ").filter { it.isNotBlank() }.distinct().joinToString(" ")
            }
        }

        vendor = vendor
            .replace(Regex("""\bCIMAH\b""", RegexOption.IGNORE_CASE), "CIMAHI")
            .replace("|", " ").replace("\\", " ")
            .replace(Regex("""\b(UTAMA|PERSADA|ADI|RACHINDO|BPRK|SPK|SUTR|KR|TOTAL|DAYA|PRGA|PERSERO|UID|JAWA|BARAT)\b.*$"""), "")
            .trim()

        vendor = vendor
            .replace(Regex("""\b(PADALARANG|RAJAMANDALA|CILILIN|LEMBANG|SUKABUMI|CIMAHI SELATAN|CIMAHI KOTA|CIMAHI|CIBABAT|PRIMACIBABAT)\b.*$"""), "$1")
            .replace(Regex("""\s+"""), " ")
            .trim()

        vendor = vendor.split(" ").filter { it.isNotBlank() }.distinct().joinToString(" ")

        // 4. Untuk Pekerjaan (Multi-Baris Landscape)
        val listPekerjaan = mutableListOf<String>()

        val regexPekerjaan = Regex("""(CROSSING[A-Z0-9.\-(); ]+|\bKR[-.\s/][A-Z0-9.\-(); ]+|\bSPK[A-Z0-9.\-(); ]+|\bSUTR[A-Z0-9.\-(); ]+|PERSEDIAAN[A-Z0-9.\-(); ]+|\bSTO\b[A-Z0-9.\-(); ]*|MATERIAL STO[A-Z0-9.\-(); ]*|PEMELIHARAAN[A-Z0-9.\-(); ]+|\bGANTI\b[A-Z0-9.\-(); ]*|\bPENGGANTIAN\b[A-Z0-9.\-(); ]*|\bPASANG\b[A-Z0-9.\-(); ]*)""")
        val temuanAwal = regexPekerjaan.find(teks)

        if (temuanAwal != null) {
            val sisaTeks = teks.substring(temuanAwal.range.first).trim()
            val barisBerikutnyaList = sisaTeks.split("\n").map { it.trim() }

            for (i in barisBerikutnyaList.indices) {
                var baris = barisBerikutnyaList[i]

                val isStop = baris.contains("NAMA MATERIAL") || baris.contains("QUANTITY") || baris.contains("SATUAN") || baris.contains("NOMOR SLIP") || baris.startsWith("NO ") || baris.startsWith("NO.") || baris.contains("DIBUAT OLEH") || baris.contains("DISETUJUI OLEH") || baris.contains("SECURITY")

                if (isStop && i > 0) break

                if (baris.contains("BERDASARKAN")) {
                    baris = baris.replace(Regex("""BERDASARKAN\s*\d+"""), " ").trim()
                }
                if (baris.contains("UNTUK PEKERJAAN")) {
                    baris = baris.substringAfter("UNTUK PEKERJAAN").trim()
                } else if (baris.contains("PEKERJAAN")) {
                    baris = baris.substringAfter("PEKERJAAN").trim()
                } else if (baris.contains("DIBERIKAN KEPADA")) {
                    baris = baris.substringAfter("DIBERIKAN KEPADA").trim()
                }

                baris = baris
                    .replace("UNTUK SECURITY", " ")
                    .replace("UNTUK", " ")
                    .replace("PEKERJAAN", " ")
                    .replace(Regex("""\b(JENIS|SURAT|JALAN|NORMAL|TRANSFER|POSTING|TANGGAL|NOMOR)\b"""), " ")
                    .replace(Regex("""\s+"""), " ")
                    .trim()

                if (baris.isNotBlank() && baris.length < 80) {
                    if (listPekerjaan.isEmpty() || !listPekerjaan.last().contains(baris)) {
                        listPekerjaan.add(baris)
                    }
                }
            }
        }

        var pekerjaan = listPekerjaan.joinToString(" ").trim()

        if (pekerjaan.isBlank()) {
            val sthPekerjaan = if (teks.contains("UNTUK PEKERJAAN")) {
                teks.substringAfter("UNTUK PEKERJAAN")
            } else if (teks.contains("PEKERJAAN")) {
                teks.substringAfter("PEKERJAAN")
            } else ""

            if (sthPekerjaan.isNotBlank()) {
                val barisPekerjaan = sthPekerjaan.trimStart(':', '=', ' ', '\t').substringBefore("\n").trim()
                val cleanP = barisPekerjaan
                    .replace(Regex("""(BERDASARKAN|DIBERIKAN|KEPADA|JENIS|NORMAL|QUANTITY|NOMOR|SLIP|TANGGAL|NAMA|MATERIAL|SECURITY|ARSIP|DIBUAT|DISETUJUI).*"""), "")
                    .trim()
                if (cleanP.isNotBlank() && cleanP.length > 2 && !cleanP.contains("SURAT JALAN")) {
                    pekerjaan = cleanP
                }
            }
        }

        pekerjaan = pekerjaan
            .replace(Regex("""\bCIMAH\b""", RegexOption.IGNORE_CASE), "CIMAHI")
            .replace(Regex("""\bRESERVASI[-:\s]*\d*\b""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\bBERDASARKAN[-:\s]*\d*\b""", RegexOption.IGNORE_CASE), " ")
            .replace("|", " ").replace("\\", " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val isPersediaanAtauSto = pekerjaan.startsWith("PERSEDIAAN") || pekerjaan.startsWith("STO") || pekerjaan.contains("MATERIAL STO")

        if (isPersediaanAtauSto) {
            // Untuk PERSEDIAAN GUDANG / STO KE: Nama ULP/UP3 adalah bagian resmi pekerjaan, jangan dihapus!
            if (vendor.isNotBlank()) {
                val duplicateVendorPattern = Regex("""(${Regex.escape(vendor)})\s+\1""", RegexOption.IGNORE_CASE)
                pekerjaan = pekerjaan.replace(duplicateVendorPattern, "$1")
                pekerjaan = pekerjaan.replace(Regex("""\bPERSEDIAAN GUDANG PLN ULP\b""", RegexOption.IGNORE_CASE), "PERSEDIAAN GUDANG")
            }
        } else {
            // Untuk pekerjaan proyek (SPK / KR / SUTR / CROSSING / GANTI / PASANG): Hapus kebocoran vendor di akhir
            if (vendor.isNotBlank()) {
                pekerjaan = pekerjaan.replace(Regex("""\bPLN\s+ULP\s+${Regex.escape(vendor)}\b""", RegexOption.IGNORE_CASE), " ")
                pekerjaan = pekerjaan.replace(Regex("""\bPLN\s+UP3\s+${Regex.escape(vendor)}\b""", RegexOption.IGNORE_CASE), " ")
                pekerjaan = pekerjaan.replace(Regex("""\b${Regex.escape(vendor)}\b""", RegexOption.IGNORE_CASE), " ")

                val vendorCleanName = vendor
                    .replace(Regex("""\b(PLN|ULP|UP3)\b""", RegexOption.IGNORE_CASE), "")
                    .trim()
                if (vendorCleanName.isNotBlank()) {
                    pekerjaan = pekerjaan.replace(Regex("""\bPLN\s+ULP\s+${Regex.escape(vendorCleanName)}\b""", RegexOption.IGNORE_CASE), " ")
                    pekerjaan = pekerjaan.replace(Regex("""\bPLN\s+UP3\s+${Regex.escape(vendorCleanName)}\b""", RegexOption.IGNORE_CASE), " ")
                }
            }
            pekerjaan = pekerjaan.replace(Regex("""\bPLN\s+ULP\s+[A-Z ]+$""", RegexOption.IGNORE_CASE), " ")
            pekerjaan = pekerjaan.replace(Regex("""\bPLN\s+UP3\s+[A-Z ]+$""", RegexOption.IGNORE_CASE), " ")
        }

        val kataList = pekerjaan.split(" ")
        val kataBersih = mutableListOf<String>()
        for (k in kataList) {
            if (k.isNotBlank() && (kataBersih.isEmpty() || kataBersih.last() != k)) {
                kataBersih.add(k)
            }
        }
        pekerjaan = kataBersih.joinToString(" ").trim()

        pekerjaan = pekerjaan
            .replace(Regex("""\b(ULP\s+[A-Z\s]+)\s+\1\b""", RegexOption.IGNORE_CASE), "$1")
            .replace(Regex("""\b(UP3\s+[A-Z\s]+)\s+\1\b""", RegexOption.IGNORE_CASE), "$1")
            .replace(Regex("""\b(CIMAHI SELATAN)\s+\1\b""", RegexOption.IGNORE_CASE), "$1")
            .replace(Regex("""\b(CIMAHI KOTA)\s+\1\b""", RegexOption.IGNORE_CASE), "$1")
            .replace(Regex("""\b(PADALARANG)\s+\1\b""", RegexOption.IGNORE_CASE), "$1")
            .replace(Regex("""\b(CILILIN)\s+\1\b""", RegexOption.IGNORE_CASE), "$1")
            .replace(Regex("""\b(RAJAMANDALA)\s+\1\b""", RegexOption.IGNORE_CASE), "$1")
            .replace(Regex("""\b(PRIMA CIBABAT)\s+\1\b""", RegexOption.IGNORE_CASE), "$1")
            .replace(Regex("""\b(LEMBANG)\s+\1\b""", RegexOption.IGNORE_CASE), "$1")
            .replace(Regex("""\s+"""), " ")
            .trim()

        // 5. Daftar Material Landscape
        val finalMaterials = mutableListOf<ItemMaterial>()

        val teksTabelMaterial = if (teks.contains("NAMA MATERIAL")) {
            teks.substringAfter("NAMA MATERIAL")
        } else if (teks.contains("QUANTITY")) {
            teks.substringAfter("QUANTITY")
        } else {
            teks
        }

        val barisTeks = teksTabelMaterial.split("\n")
        val regexKodeMaterial = Regex("""\b([A-Z]{1,3}\d{5,8}|\d{6,8})\b""", RegexOption.IGNORE_CASE)
        val unitPatternStr = """(BH|BE|BL|8L|3L|3H|8H|EH|B|SET|S3T|SFT|SJT|M|MTR|BTG|BT|ROL|ROLL|PCS|PC|ZAK|KG|UNIT)"""

        for (baris in barisTeks) {
            val matches = regexKodeMaterial.findAll(baris).toList()

            if (matches.isNotEmpty()) {
                for (idx in matches.indices) {
                    val currentMatch = matches[idx]
                    val startIndex = currentMatch.range.first
                    val endIndex = if (idx + 1 < matches.size) matches[idx + 1].range.first else baris.length

                    val segmentBaris = baris.substring(startIndex, endIndex).trim()

                    val temuanUtuh = Regex("""\b([A-Z]{1,3}\d{5,8}|\d{6,8})\b\s*[-:]?\s*(.+)""", RegexOption.IGNORE_CASE).find(segmentBaris)
                    if (temuanUtuh != null) {
                        var teksBarangUtuh = temuanUtuh.groupValues[2].trim()
                        teksBarangUtuh = teksBarangUtuh.replace(Regex("""^[-.:;\s]+"""), "").trim()

                        teksBarangUtuh = teksBarangUtuh
                            .replace(Regex("""\b\d{9,12}\b"""), " ")
                            .replace(Regex("""\b(QUANTITY|SATUAN|NOMOR|SLIP|KETERANGAN|NO|NAMA|MATERIAL|DIBUAT|DISETUJUI)\b"""), " ")
                            .replace(Regex("""\s+"""), " ")
                            .trim()

                        var qty = "[?]"
                        var satuan = ""

                        // Step 1: Match DIGIT + UNIT at tail end (memungkinkan trailing dashes/punctuation)
                        val regexQtySatuanValid = Regex("""\b(\d{1,5})\s*${unitPatternStr}\b[-=:;,\s.]*$""", RegexOption.IGNORE_CASE)
                        val matchValid = regexQtySatuanValid.find(teksBarangUtuh)

                        if (matchValid != null) {
                            qty = matchValid.groupValues[1]
                            satuan = normalisasiSatuan(matchValid.groupValues[2], teksBarangUtuh)
                            teksBarangUtuh = teksBarangUtuh.substring(0, matchValid.range.first).trim()
                        } else {
                            // Step 2: Match UNIT ONLY at tail end
                            val regexUnitOnly = Regex("""\b${unitPatternStr}\b[-=:;,\s.]*$""", RegexOption.IGNORE_CASE)
                            val matchUnitOnly = regexUnitOnly.find(teksBarangUtuh)

                            if (matchUnitOnly != null) {
                                satuan = normalisasiSatuan(matchUnitOnly.groupValues[1], teksBarangUtuh)
                                teksBarangUtuh = teksBarangUtuh.substring(0, matchUnitOnly.range.first).trim()

                                val regexStandaloneQty = Regex("""[;\s]+(\d{1,5})\s*$""")
                                val matchQty = regexStandaloneQty.find(teksBarangUtuh)
                                if (matchQty != null) {
                                    qty = matchQty.groupValues[1]
                                    teksBarangUtuh = teksBarangUtuh.substring(0, matchQty.range.first).trim()
                                }
                            } else {
                                // Step 3: Match DIGIT ONLY at tail end
                                val regexDigitOnly = Regex("""[;\s]+(\d{1,5})[-=:;,\s.]*$""")
                                val matchDigit = regexDigitOnly.find(teksBarangUtuh)
                                if (matchDigit != null) {
                                    qty = matchDigit.groupValues[1]
                                    satuan = normalisasiSatuan("", teksBarangUtuh)
                                    teksBarangUtuh = teksBarangUtuh.substring(0, matchDigit.range.first).trim()
                                }
                            }
                        }

                        // Delete backslashes \ completely from material name
                        teksBarangUtuh = teksBarangUtuh.replace("\\", "")

                        // Always strip leftover unit words, pipe noise "|", and edge noise "-8|" at tail end
                        val namaBersih = teksBarangUtuh
                            .replace(Regex("""\b(QUANTITY|SATUAN|NOMOR|SLIP|KETERANGAN|NO|NAMA|MATERIAL|DIBUAT|DISETUJUI)\b"""), " ")
                            .replace(Regex("""\b${unitPatternStr}\b[-=:;,\s.]*$""", RegexOption.IGNORE_CASE), "")
                            .replace(Regex("""[|\s\\/]+$"""), "")
                            .replace(Regex("""[-;:\s|\\/]+\d*[-;:\s|\\/]*$"""), "")
                            .replace(Regex("""[-=:;,\s.|\\/]+$"""), "")
                            .replace(Regex("""\s+"""), " ")
                            .trim()

                        if (satuan.isBlank()) {
                            satuan = normalisasiSatuan("", teksBarangUtuh)
                        }

                        if (namaBersih.isNotBlank() && !namaBersih.contains("PEKERJAAN") && !namaBersih.contains("PERSEDIAAN") && !namaBersih.contains("SURAT JALAN")) {
                            
                        var finalNamaBersih = namaBersih
                        var finalSatuan = satuan

                        val masterItem = MaterialDictionary.getMaterialByCode(currentMatch.value)
                        if (masterItem != null) {
                            finalNamaBersih = masterItem.deskripsi.replace(Regex(""";+$"""), "").trim()
                            if (finalSatuan == "BH" || finalSatuan.isBlank()) {
                                finalSatuan = masterItem.satuan
                            }
                        } else {
                            finalNamaBersih = pembersihanTeksKotor(finalNamaBersih)
                        }
                        
                        finalMaterials.add(ItemMaterial(finalNamaBersih, qty, finalSatuan))
                        }
                    }
                }
            }
        }

        // 🌟 FALLBACK LANDSCAPE: Jika tidak ada kode material berangka (misal Surat Jalan 1019 dengan item CT;20KV...)
        if (finalMaterials.isEmpty()) {
            for (baris in barisTeks) {
                val lineUpper = baris.trim().uppercase()

                val isForbiddenLine = lineUpper.contains("SURAT JALAN") ||
                        lineUpper.contains("DIBUAT OLEH") || lineUpper.contains("DISETUJUI OLEH") || lineUpper.contains("PETUGAS") ||
                        lineUpper.contains("PENGEMUDI") || lineUpper.contains("SECURITY") || lineUpper.contains("SEMUA RESIKO") ||
                        lineUpper.contains("NOMOR SLIP") || lineUpper.isBlank()

                if (!isForbiddenLine) {
                    val regexQtySatuanValid = Regex("""\b(\d{1,5})\s*${unitPatternStr}\b[-=:;,\s.]*$""", RegexOption.IGNORE_CASE)
                    val matchValid = regexQtySatuanValid.find(baris)

                    if (matchValid != null) {
                        val qty = matchValid.groupValues[1]
                        val satuan = normalisasiSatuan(matchValid.groupValues[2], baris)
                        var namaBarang = baris.substring(0, matchValid.range.first).trim()
                        namaBarang = namaBarang.replace("\\", "")

                        // Clean left-side header leaks (e.g. "PERSEDIAAN GUDANG ULP PRIMA CIBABAT")
                        namaBarang = namaBarang
                            .replace(Regex("""^.*?\b(PERSEDIAAN GUDANG[A-Z0-9.\-:\s]*|PERSEDIAAN[A-Z0-9.\-:\s]*)\b""", RegexOption.IGNORE_CASE), " ")
                            .replace(Regex("""^.*?\b(UNTUK PEKERJAAN|UNTUK SECURITY|DIBERIKAN KEPADA|BERDASARKAN)\b""", RegexOption.IGNORE_CASE), " ")
                            .replace(Regex("""^.*?\b(PLN UP3 [A-Z ]+|PLN ULP [A-Z ]+|UP3 [A-Z ]+|ULP [A-Z ]+)\b""", RegexOption.IGNORE_CASE), " ")
                            .replace(Regex("""^\d{1,2}\s*[-.:;]?\s*"""), " ")
                            .replace(Regex("""^([A-Z]{1,3}\d{5,8}|\d{6,8})\s*[-.:;]?\s*"""), " ")
                            .replace(Regex("""^\d{1,2}\s*[-.:;]?\s*"""), " ")
                            .replace(Regex("""\b(QUANTITY|SATUAN|NOMOR|SLIP|KETERANGAN|NO|NAMA|MATERIAL|DIBUAT|DISETUJUI)\b"""), " ")
                            .replace(Regex("""[|\s\\/]+$"""), "")
                            .replace(Regex("""[-;:\s|\\/]+\d*[-;:\s|\\/]*$"""), "")
                            .replace(Regex("""[-=:;,\s.|\\/]+$"""), "")
                            .replace(Regex("""\s+"""), " ")
                            .trim()

                        if (namaBarang.isNotBlank() && namaBarang.length > 2) {
                            namaBarang = pembersihanTeksKotor(namaBarang)
                            finalMaterials.add(ItemMaterial(namaBarang, qty, satuan))
                        }
                    }
                }
            }
        }

        return DataSuratJalan(tanggal, noSj, vendor, pekerjaan, finalMaterials, adaLanjutan)
    }
}
