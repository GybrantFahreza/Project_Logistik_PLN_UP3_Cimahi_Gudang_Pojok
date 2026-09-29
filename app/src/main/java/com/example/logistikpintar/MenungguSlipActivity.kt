package com.example.logistikpintar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MenungguSlipActivity : AppCompatActivity() {

    private lateinit var rvMenungguSlip: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menunggu_slip)

        rvMenungguSlip = findViewById(R.id.rvMenungguSlip)
        rvMenungguSlip.layoutManager = LinearLayoutManager(this)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        loadData()
    }

    private fun loadData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = LogistikDatabase.getDatabase(this@MenungguSlipActivity).logistikDao()
            val listDraftMaterial = db.getAllDraftMaterials()
            
            val mapDate = mutableMapOf<String, DraftDate>()
            
            for (mat in listDraftMaterial) {
                val sj = db.getSuratJalanById(mat.idSuratJalan) ?: continue
                
                val dateGroup = mapDate.getOrPut(sj.tanggal) { DraftDate(sj.tanggal) }
                
                var itemGroup = dateGroup.listGroup.find { it.namaBarang == mat.namaBarang }
                if (itemGroup == null) {
                    itemGroup = DraftGroup(namaBarang = mat.namaBarang)
                    dateGroup.listGroup.add(itemGroup)
                }
                
                itemGroup.totalQty += mat.qty
                itemGroup.satuan = mat.satuan
                itemGroup.daftarPekerjaan.add(sj.pekerjaan)
                itemGroup.listDetail.add(DraftDetail(sj, mat))
            }
            
            val finalDataList = mapDate.values.toList().sortedByDescending { it.tanggal }

            withContext(Dispatchers.Main) {
                rvMenungguSlip.adapter = SlipDateAdapter(finalDataList) { material ->
                    showUpdateSjDialog(material)
                }
            }
        }
    }

    private fun showUpdateSjDialog(dataMaterial: MaterialEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = LogistikDatabase.getDatabase(this@MenungguSlipActivity).logistikDao()
            val draftSjLama = db.getSuratJalanById(dataMaterial.idSuratJalan) ?: return@launch

            withContext(Dispatchers.Main) {
                val dialogView = layoutInflater.inflate(R.layout.dialog_update_sj, null)
                val etNoSjResmi = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etNoSjResmi)
                val etVendor = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etVendor)
                val etPekerjaan = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etPekerjaan)

                etPekerjaan.setText(draftSjLama.pekerjaan)

                MaterialAlertDialogBuilder(this@MenungguSlipActivity)
                    .setView(dialogView)
                    .setPositiveButton("UPDATE") { _, _ ->
                        val inputNoSj = etNoSjResmi.text.toString().trim()
                        val inputVendor = etVendor.text.toString().trim()
                        val inputPekerjaan = etPekerjaan.text.toString().trim()

                        if (inputNoSj.isNotEmpty()) {
                            lifecycleScope.launch(Dispatchers.IO) {
                                val existingSj = db.getSuratJalanByNoSjUtuh(inputNoSj)

                                if (existingSj != null) {
                                    db.updateMaterial(dataMaterial.copy(idSuratJalan = existingSj.id))
                                    db.deleteSuratJalan(draftSjLama)
                                } else {
                                    db.updateSuratJalan(draftSjLama.copy(
                                        noSj = inputNoSj,
                                        vendor = if(inputVendor.isNotEmpty()) inputVendor else "INTERNAL",
                                        pekerjaan = inputPekerjaan
                                    ))
                                }

                                withContext(Dispatchers.Main) {
                                    Toast.makeText(this@MenungguSlipActivity, "SJ Berhasil Diupdate!", Toast.LENGTH_SHORT).show()
                                    loadData()
                                }
                            }
                        }
                    }
                    .setNegativeButton("BATAL", null)
                    .show()
            }
        }
    }

    data class DraftDetail(val sj: SuratJalanEntity, val material: MaterialEntity)

    data class DraftGroup(
        val namaBarang: String,
        var totalQty: Int = 0,
        var satuan: String = "",
        val daftarPekerjaan: MutableSet<String> = mutableSetOf(),
        val listDetail: MutableList<DraftDetail> = mutableListOf(),
        var isExpanded: Boolean = false
    )

    data class DraftDate(
        val tanggal: String,
        val listGroup: MutableList<DraftGroup> = mutableListOf(),
        var isExpanded: Boolean = false
    )

    class SlipDateAdapter(
        private val list: List<DraftDate>,
        private val onDetailClick: (MaterialEntity) -> Unit
    ) : RecyclerView.Adapter<SlipDateAdapter.DateViewHolder>() {

        class DateViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTanggal = view.findViewById<TextView>(R.id.tvTanggalHeader)
            val ivExpand = view.findViewById<ImageView>(R.id.ivExpandDate)
            val rvGrup = view.findViewById<RecyclerView>(R.id.rvGrupBarang)
            val cardHeader = view.findViewById<View>(R.id.cardDateHeader)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DateViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_slip_date, parent, false)
            return DateViewHolder(view)
        }

        override fun onBindViewHolder(holder: DateViewHolder, position: Int) {
            val data = list[position]
            holder.tvTanggal.text = data.tanggal
            
            holder.rvGrup.layoutManager = LinearLayoutManager(holder.itemView.context)
            holder.rvGrup.adapter = SlipGroupAdapter(data.listGroup, onDetailClick)
            
            holder.cardHeader.setOnClickListener {
                data.isExpanded = !data.isExpanded
                notifyItemChanged(position)
            }
            
            holder.rvGrup.visibility = if (data.isExpanded) View.VISIBLE else View.GONE
            holder.ivExpand.rotation = if (data.isExpanded) 180f else 0f
        }

        override fun getItemCount() = list.size
    }

    class SlipGroupAdapter(
        private val list: List<DraftGroup>,
        private val onDetailClick: (MaterialEntity) -> Unit
    ) : RecyclerView.Adapter<SlipGroupAdapter.GroupViewHolder>() {

        class GroupViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvNama = view.findViewById<TextView>(R.id.tvNamaBarangGroup)
            val tvQty = view.findViewById<TextView>(R.id.tvTotalQtyGroup)
            val tvProyek = view.findViewById<TextView>(R.id.tvRangkumanProyek)
            val containerDetail = view.findViewById<LinearLayout>(R.id.llContainerDetail)
            val cardHeader = view.findViewById<View>(R.id.cardGroupHeader)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_slip_group, parent, false)
            return GroupViewHolder(view)
        }

        override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
            val data = list[position]
            holder.tvNama.text = data.namaBarang
            holder.tvQty.text = "Total: ${data.totalQty} ${data.satuan}"
            holder.tvProyek.text = "Untuk Pekerjaan: ${data.daftarPekerjaan.joinToString(", ")}"
            
            holder.containerDetail.removeAllViews()
            for (detail in data.listDetail) {
                val detailView = LayoutInflater.from(holder.itemView.context).inflate(R.layout.item_slip_detail, holder.containerDetail, false)
                val tvInfo = detailView.findViewById<TextView>(R.id.tvDetailInfo)
                tvInfo.text = "- ${detail.material.qty} ${detail.material.satuan} | Untuk Pekerjaan: ${detail.sj.pekerjaan}"
                
                detailView.setOnClickListener {
                    onDetailClick(detail.material)
                }
                holder.containerDetail.addView(detailView)
            }

            holder.cardHeader.setOnClickListener {
                data.isExpanded = !data.isExpanded
                notifyItemChanged(position)
            }
            holder.containerDetail.visibility = if (data.isExpanded) View.VISIBLE else View.GONE
        }

        override fun getItemCount() = list.size
    }
}