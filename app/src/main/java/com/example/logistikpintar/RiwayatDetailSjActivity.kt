package com.example.logistikpintar

import android.os.Bundle
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RiwayatDetailSjActivity : AppCompatActivity() {

    private lateinit var etNoSj: EditText
    private lateinit var etVendor: EditText
    private lateinit var etPekerjaan: EditText
    private lateinit var rvMaterials: RecyclerView
    private lateinit var btnTambah: Button
    private lateinit var btnSimpan: Button
    private lateinit var btnEnterEdit: Button
    private lateinit var tvTitle: TextView
    
    private var noSjString: String = ""
    private var currentSjHeader: SuratJalanEntity? = null
    private val listMaterials = mutableListOf<MaterialEntity>()
    private var isEditMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_riwayat_detail_sj)

        noSjString = (intent.getStringExtra("NO_SJ") ?: "").trim()
        
        etNoSj = findViewById(R.id.etDetailNoSj)
        etVendor = findViewById(R.id.etDetailVendor)
        etPekerjaan = findViewById(R.id.etDetailPekerjaan)
        rvMaterials = findViewById(R.id.rvDetailSjMaterials)
        btnTambah = findViewById(R.id.btnTambahItemDetail)
        btnSimpan = findViewById(R.id.btnSimpanPerubahan)
        btnEnterEdit = findViewById(R.id.btnEnterEditMode)
        tvTitle = findViewById(R.id.tvDetailTitle)

        val filterKapital = arrayOf<InputFilter>(InputFilter.AllCaps())
        etNoSj.filters = filterKapital
        etVendor.filters = filterKapital
        etPekerjaan.filters = filterKapital

        rvMaterials.layoutManager = LinearLayoutManager(this)

        loadInitialData()

        // Tombol Masuk Mode Edit
        btnEnterEdit.setOnClickListener {
            isEditMode = true
            updateUIByMode()
        }

        // Tombol Tambah Item
        btnTambah.setOnClickListener {
            // Karena ini konsolidasi, idSuratJalan mungkin tidak menentu jika ada banyak.
            // Kita bisa pakai ID dari entitas pertama yang ditemukan.
            lifecycleScope.launch(Dispatchers.IO) {
                val db = LogistikDatabase.getDatabase(this@RiwayatDetailSjActivity).logistikDao()
                val sjHeader = db.getSuratJalanByNoSjUtuh(noSjString)
                val targetIdSj = sjHeader?.id ?: -1
                
                withContext(Dispatchers.Main) {
                    listMaterials.add(MaterialEntity(idSuratJalan = targetIdSj, namaBarang = "", qty = 0, satuan = ""))
                    rvMaterials.adapter?.notifyItemInserted(listMaterials.size - 1)
                }
            }
        }

        // Tombol Simpan Perubahan
        btnSimpan.setOnClickListener {
            simpanKonfirmasi()
        }
    }

    private fun loadInitialData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = LogistikDatabase.getDatabase(this@RiwayatDetailSjActivity).logistikDao()
            db.deleteOrphanSuratJalan()
            
            // 🌟 STEP 3: Gunakan query Utuh (Berdasarkan Nomor SJ)
            val sjHeader = db.getSuratJalanByNoSjUtuh(noSjString)
            val materials = db.getMaterialsByNoSjUtuh(noSjString)
            
            currentSjHeader = sjHeader
            listMaterials.clear()
            listMaterials.addAll(materials)

            withContext(Dispatchers.Main) {
                sjHeader?.let { dataSj ->
                    etNoSj.setText(dataSj.noSj)
                    etNoSj.setTextColor(android.graphics.Color.parseColor("#1F2937"))
                    etVendor.setText(dataSj.vendor)
                    etPekerjaan.setText(dataSj.pekerjaan)
                }
                updateUIByMode()
            }
        }
    }

    private fun updateUIByMode() {
        if (isEditMode) {
            tvTitle.text = "📝 EDIT SURAT JALAN"
            etNoSj.isEnabled = true
            etVendor.isEnabled = true
            etPekerjaan.isEnabled = true
            btnTambah.visibility = View.VISIBLE
            btnSimpan.visibility = View.VISIBLE
            btnEnterEdit.visibility = View.GONE
        } else {
            tvTitle.text = "📄 RINCIAN SURAT JALAN"
            etNoSj.isEnabled = false
            etVendor.isEnabled = false
            etPekerjaan.isEnabled = false
            btnTambah.visibility = View.GONE
            btnSimpan.visibility = View.GONE
            btnEnterEdit.visibility = View.VISIBLE
        }
        
        rvMaterials.adapter = EditMaterialAdapter(listMaterials, isEditMode) { pos ->
            hapusItemKonfirmasi(pos)
        }
    }

    private fun hapusItemKonfirmasi(position: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle("🗑️ Hapus Item?")
            .setMessage("Apakah Anda yakin ingin menghapus barang ini secara permanen dari daftar?")
            .setPositiveButton("YA, HAPUS") { _, _ ->
                listMaterials.removeAt(position)
                rvMaterials.adapter?.notifyDataSetChanged()
                Toast.makeText(this, "Item dihapus dari daftar", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("BATAL", null)
            .show()
    }

    private fun simpanKonfirmasi() {
        val no = etNoSj.text.toString().trim()
        if (no.isEmpty()) {
            Toast.makeText(this, "Nomor SJ tidak boleh kosong!", Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("💾 Simpan Perubahan?")
            .setMessage("Apakah semua informasi sudah sesuai? Data lama akan diperbarui secara permanen.")
            .setPositiveButton("YA, SIMPAN") { _, _ ->
                sedotDataDariTabelDanSimpan()
            }
            .setNegativeButton("CEK LAGI", null)
            .show()
    }

    private fun sedotDataDariTabelDanSimpan() {
        val materialFinal = mutableListOf<MaterialEntity>()
        for (i in 0 until listMaterials.size) {
            val holder = rvMaterials.findViewHolderForAdapterPosition(i) as? EditMaterialAdapter.ViewHolder
            if (holder != null) {
                val nama = holder.etNama.text.toString().trim()
                val qtyStr = holder.etQty.text.toString().trim()
                val satuan = holder.etSatuan.text.toString().trim()
                val qty = qtyStr.toIntOrNull() ?: 0
                
                if (nama.isNotEmpty()) {
                    listMaterials[i] = listMaterials[i].copy(
                        namaBarang = nama,
                        qty = qty,
                        satuan = satuan
                    )
                }
            }
            materialFinal.add(listMaterials[i])
        }
        prosesSimpan(materialFinal)
    }

    private fun prosesSimpan(materialBaru: List<MaterialEntity>) {
        val no = etNoSj.text.toString().trim()
        val ven = etVendor.text.toString().trim()
        val pek = etPekerjaan.text.toString().trim()

        lifecycleScope.launch(Dispatchers.IO) {
            val db = LogistikDatabase.getDatabase(this@RiwayatDetailSjActivity).logistikDao()
            
            // Karena ini konsolidasi, update header mungkin perlu dilakukan ke SEMUA halaman
            // Namun instruksi tidak eksplisit tentang ini. Kita update yang utama saja (atau halaman pertama).
            val sjHeader = db.getSuratJalanByNoSjUtuh(noSjString) ?: return@launch
            
            val updatedSj = sjHeader.copy(noSj = no, vendor = ven, pekerjaan = pek)
            db.updateSuratJalan(updatedSj)
            
            // Untuk material, karena kita melakukan konsolidasi saat LOAD, 
            // simpan kembali bisa jadi tricky jika ada Page 1 & Page 2.
            // Paling aman untuk UX "Utuh" adalah mengupdate barang yang ada.
            
            for (item in materialBaru) {
                if (item.id != 0) {
                    val matLama = db.getMaterialById(item.id)
                    val statusAsli = matLama?.status ?: "AKTIF"
                    db.updateMaterial(item.copy(status = statusAsli))
                } else {
                    db.insertMaterial(item.copy(status = "ARSIP"))
                }
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(this@RiwayatDetailSjActivity, "✅ Perubahan Berhasil Disimpan!", Toast.LENGTH_LONG).show()
                noSjString = no // Update string pencarian jika nomor SJ berubah
                isEditMode = false
                loadInitialData()
            }
        }
    }

    // 🌟 STEP 3: LOGIKA SPLIT DATA DI ACTIVITY (RETUR KE GUDANG SISA)
    fun prosesTitipGudang(material: MaterialEntity, qtyTitip: Int) {
        if (qtyTitip <= 0 || qtyTitip > material.qty) {
            Toast.makeText(this, "Jumlah tidak valid!", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val db = LogistikDatabase.getDatabase(this@RiwayatDetailSjActivity).logistikDao()

            if (qtyTitip == material.qty) {
                // KONDISI A: Batal berangkat semua. Cukup ubah status.
                material.status = "TITIP_GUDANG"
                db.updateMaterial(material)
            } else {
                // KONDISI B: Batal sebagian (Split Data)
                // 1. Kurangi Qty material asli (yang tetap berangkat / ARSIP)
                material.qty = material.qty - qtyTitip
                db.updateMaterial(material)

                // 2. Buat duplikat material baru khusus untuk yang dititip
                val materialBaru = material.copy(
                    id = 0, // id 0 agar Room auto-generate ID baru untuk duplikat ini
                    qty = qtyTitip,
                    status = "TITIP_GUDANG"
                )
                db.insertMaterial(materialBaru)
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(this@RiwayatDetailSjActivity, "Barang berhasil dikembalikan ke Gudang Sisa!", Toast.LENGTH_SHORT).show()
                loadInitialData()
            }
        }
    }

    fun pelunasanAdministrasiMaterial(material: MaterialEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = LogistikDatabase.getDatabase(this@RiwayatDetailSjActivity).logistikDao()
            material.isTanpaSlip = false
            db.updateMaterial(material)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@RiwayatDetailSjActivity, "Status Administrasi LUNAS (Slip Diterima)!", Toast.LENGTH_SHORT).show()
                loadInitialData()
            }
        }
    }

    fun ubahStatusKeluarTanpaSlipMaterial(material: MaterialEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = LogistikDatabase.getDatabase(this@RiwayatDetailSjActivity).logistikDao()
            material.isTanpaSlip = true
            db.updateMaterial(material)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@RiwayatDetailSjActivity, "Status '${material.namaBarang}' diubah menjadi BELUM JADI SLIP!", Toast.LENGTH_SHORT).show()
                loadInitialData()
            }
        }
    }

    fun tampilkanOpsiMenuDialogMaterial(material: MaterialEntity) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_opsi_aksi_material, null)
        val tvNamaMaterial = dialogView.findViewById<TextView>(R.id.tvNamaMaterialOpsi)
        val btnTitipGudang = dialogView.findViewById<View>(R.id.btnOptTitipGudang)
        val btnUbahStatus = dialogView.findViewById<View>(R.id.btnOptUbahStatusSlip)
        val tvStatusIcon = dialogView.findViewById<TextView>(R.id.tvOptStatusIcon)
        val tvStatusTitle = dialogView.findViewById<TextView>(R.id.tvOptStatusTitle)
        val tvStatusSubtitle = dialogView.findViewById<TextView>(R.id.tvOptStatusSubtitle)

        tvNamaMaterial.text = material.namaBarang

        if (material.isTanpaSlip) {
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
            val dialogTitipView = LayoutInflater.from(this).inflate(R.layout.dialog_titip_gudang, null)
            val tvTitle = dialogTitipView.findViewById<TextView>(R.id.tvTitleDialog)
            val tvNamaMaterialTitip = dialogTitipView.findViewById<TextView>(R.id.tvNamaMaterialDialog)
            val tvInfoQty = dialogTitipView.findViewById<TextView>(R.id.tvInfoQtyMax)
            val tilQtyTitip = dialogTitipView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilQtyTitip)
            val etQtyTitip = dialogTitipView.findViewById<EditText>(R.id.etQtyTitip)

            tvTitle?.text = "Barang Tidak Muat (Titip Gudang)"
            tvNamaMaterialTitip?.text = material.namaBarang
            tvInfoQty?.text = "Jumlah saat ini: ${material.qty} ${material.satuan}"
            tilQtyTitip?.hint = "Jumlah yg Ditinggal di Gudang"

            MaterialAlertDialogBuilder(this)
                .setView(dialogTitipView)
                .setPositiveButton("SIMPAN") { _, _ ->
                    val qtyTitipStr = etQtyTitip.text.toString().trim()
                    if (qtyTitipStr.isNotEmpty()) {
                        val qtyTitip = qtyTitipStr.toIntOrNull() ?: 0
                        prosesTitipGudang(material, qtyTitip)
                    }
                }
                .setNegativeButton("BATAL", null)
                .show()
        }

        btnUbahStatus.setOnClickListener {
            dialog.dismiss()
            if (material.isTanpaSlip) {
                pelunasanAdministrasiMaterial(material)
            } else {
                ubahStatusKeluarTanpaSlipMaterial(material)
            }
        }
    }

    inner class EditMaterialAdapter(
        private val list: MutableList<MaterialEntity>,
        private val canEdit: Boolean,
        private val onDelete: (Int) -> Unit
    ) : RecyclerView.Adapter<EditMaterialAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvNo: TextView = view.findViewById(R.id.tvNoUrut)
            val etNama: EditText = view.findViewById(R.id.etNamaBarang)
            val etQty: EditText = view.findViewById(R.id.etQty)
            val etSatuan: EditText = view.findViewById(R.id.etSatuan)
            val btnHapus: Button = view.findViewById(R.id.btnHapusItem)
            val tvPekerjaan: TextView = view.findViewById(R.id.tvGarduPekerjaan)
            val tvBadgeTanpaSlip: TextView? = view.findViewById(R.id.tvBadgeTanpaSlip)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_material_edit, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = list[position]
            holder.tvNo.text = "${position + 1}."
            
            holder.etNama.setText(item.namaBarang)
            holder.etQty.setText(if (item.qty > 0) item.qty.toString() else "")
            holder.etSatuan.setText(item.satuan)
            
            // 🌟 STEP 3: Tampilkan Pekerjaan dari Header & Badge Tanpa Slip
            holder.tvPekerjaan.text = "Untuk Pekerjaan: ${currentSjHeader?.pekerjaan ?: "-"}"
            holder.tvBadgeTanpaSlip?.visibility = if (item.isTanpaSlip) View.VISIBLE else View.GONE

            holder.etNama.isEnabled = canEdit
            holder.etQty.isEnabled = canEdit
            holder.etSatuan.isEnabled = canEdit
            holder.btnHapus.visibility = if (canEdit) View.VISIBLE else View.GONE

            if (canEdit) {
                holder.etNama.setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) list[holder.bindingAdapterPosition] = list[holder.bindingAdapterPosition].copy(namaBarang = holder.etNama.text.toString().trim())
                }
                holder.etQty.setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) {
                        val qty = holder.etQty.text.toString().toIntOrNull() ?: 0
                        list[holder.bindingAdapterPosition] = list[holder.bindingAdapterPosition].copy(qty = qty)
                    }
                }
                holder.etSatuan.setOnFocusChangeListener { _, hasFocus ->
                    if (!hasFocus) list[holder.bindingAdapterPosition] = list[holder.bindingAdapterPosition].copy(satuan = holder.etSatuan.text.toString().trim())
                }
                holder.btnHapus.setOnClickListener { onDelete(holder.bindingAdapterPosition) }
            } else {
                // 🌟 STEP 4: EVENT KLIK LAMA (LONG PRESS) -> POP-UP PILIHAN MENU
                holder.itemView.setOnLongClickListener {
                    val pos = holder.bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) {
                        val material = list[pos]
                        val context = holder.itemView.context
                        if (context is RiwayatDetailSjActivity) {
                            context.tampilkanOpsiMenuDialogMaterial(material)
                        }
                    }
                    true
                }
            }
        }

        override fun getItemCount() = list.size
    }
}