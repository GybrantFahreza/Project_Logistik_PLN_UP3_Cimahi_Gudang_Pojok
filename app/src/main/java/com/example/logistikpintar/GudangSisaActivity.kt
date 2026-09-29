package com.example.logistikpintar

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.google.android.material.bottomnavigation.BottomNavigationView

class GudangSisaActivity : AppCompatActivity() {

    private var backPressedTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gudang_sisa)

        // 🌟 DUA KALI TEKAN BACK UNTUK KELUAR APLIKASI
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    finish()
                } else {
                    Toast.makeText(this@GudangSisaActivity, "Tekan 1 kali lagi jika ingin keluar", Toast.LENGTH_SHORT).show()
                    backPressedTime = System.currentTimeMillis()
                }
            }
        })

        val rv = findViewById<RecyclerView>(R.id.rvGudangSisa)
        rv.layoutManager = LinearLayoutManager(this)

        loadData(rv)

        // Center Scan Button (+)
        findViewById<View>(R.id.btnCenterScan).setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
        }

        // Bottom Navigation
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.selectedItemId = R.id.nav_gudang
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
                R.id.nav_gudang -> true
                else -> false
            }
        }

        // 🌟 Logika Double Tab
        val tabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.tabLayoutGudang)
        tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> loadData(rv, "TITIP_GUDANG")
                    1 -> loadData(rv, "BELUM_DIAMBIL")
                }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        val rv = findViewById<RecyclerView>(R.id.rvGudangSisa)
        val tabLayout = findViewById<com.google.android.material.tabs.TabLayout>(R.id.tabLayoutGudang)
        if (rv != null && tabLayout != null) {
            val statusFilter = if (tabLayout.selectedTabPosition == 0) "TITIP_GUDANG" else "BELUM_DIAMBIL"
            loadData(rv, statusFilter)
        }
    }

    private fun loadData(rv: RecyclerView, filterStatus: String = "TITIP_GUDANG") {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = LogistikDatabase.getDatabase(this@GudangSisaActivity).logistikDao()
            val allMaterial = db.getAllMaterial()
            
            val map = mutableMapOf<String, DataGudang>()
            for (m in allMaterial) {
                if (m.status != filterStatus) continue
                
                val sj = db.getSuratJalanById(m.idSuratJalan)
                val pekerjaanInfo = sj?.pekerjaan ?: "???"
                // Tetap simpan string pekerjaan utuh dengan PT nya di rincian
                val info = "- $pekerjaanInfo: ${m.qty} ${m.satuan} (${sj?.tanggal ?: "-"})"
                
                if (map.containsKey(m.namaBarang)) {
                    val d = map[m.namaBarang]!!
                    d.totalQty += m.qty
                    d.rincian.add(info)
                } else {
                    map[m.namaBarang] = DataGudang(m.namaBarang, m.qty, m.satuan, mutableListOf(info))
                }
            }
            
            val list = map.values.toList()
            withContext(Dispatchers.Main) {
                rv.adapter = GudangAdapter(list) { data ->
                    tampilkanDialogAmbil(data, filterStatus)
                }
            }
        }
    }

    private fun tampilkanDialogAmbil(data: DataGudang, sourceStatus: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_ambil_gudang, null)
        val tvNamaMaterial = dialogView.findViewById<TextView>(R.id.tvNamaMaterialDialog)
        val tvStokMaks = dialogView.findViewById<TextView>(R.id.tvStokMaksDialog)
        val tvRincianDetail = dialogView.findViewById<TextView>(R.id.tvRincianDetailDialog)
        val etQtyNaikTruk = dialogView.findViewById<EditText>(R.id.etQtyNaikTruk)

        tvNamaMaterial.text = data.nama
        tvStokMaks.text = "Tersedia di Gudang: ${data.totalQty} ${data.satuan}"
        tvRincianDetail.text = data.rincian.joinToString("\n")

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton("NAIKKAN TRUK (ARSIP)", null)
            .setNegativeButton("BATAL", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val qtyStr = etQtyNaikTruk.text.toString().trim()
            val qty = qtyStr.toIntOrNull() ?: 0

            if (qty > 0 && qty <= data.totalQty) {
                dialog.dismiss()
                lifecycleScope.launch(Dispatchers.IO) {
                    val db = LogistikDatabase.getDatabase(this@GudangSisaActivity).logistikDao()
                    
                    // Eksekusi Logika Pemotongan Gudang

                    
                    // Kita akan pecah barang berdasarkan history asalnya
                    // Supaya kalau ambilnya setengah-setengah, tidak corrupt datanya.
                    LogistikHelper.prosesPecahBarang(db, data.nama, qty, sourceStatus, "ARSIP", sourceStatus)
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@GudangSisaActivity, "Barang berhasil dinaikkan ke truk dan diarsipkan!", Toast.LENGTH_SHORT).show()
                        val rv = findViewById<RecyclerView>(R.id.rvGudangSisa)
                        loadData(rv, sourceStatus) // Refresh otomatis
                    }
                }
            } else if (qty > data.totalQty) {
                Toast.makeText(this, "Jumlah ($qty) melebihi stok gudang (${data.totalQty} ${data.satuan})!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Masukkan jumlah QTY minimal 1!", Toast.LENGTH_SHORT).show()
            }
        }
    }



    data class DataGudang(val nama: String, var totalQty: Int, val satuan: String, val rincian: MutableList<String>)

    class GudangAdapter(private val list: List<DataGudang>, private val onClick: (DataGudang) -> Unit) : RecyclerView.Adapter<GudangAdapter.VH>() {
        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvNama = v.findViewById<TextView>(R.id.tvNamaBarangBrankas)
            val tvQty = v.findViewById<TextView>(R.id.tvTotalQty)
            val tvSat = v.findViewById<TextView>(R.id.tvSatuanBrankas)
            val tvRincian = v.findViewById<TextView>(R.id.tvRincianGardu)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_brankas, parent, false)
            v.findViewById<View>(R.id.cbNaikTruk).visibility = View.GONE
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val d = list[position]
            holder.tvNama.text = d.nama
            holder.tvQty.text = d.totalQty.toString()
            holder.tvSat.text = d.satuan
            holder.tvRincian.text = d.rincian.joinToString("\n")
            holder.itemView.setOnClickListener { onClick(d) }
        }

        override fun getItemCount() = list.size
    }
}