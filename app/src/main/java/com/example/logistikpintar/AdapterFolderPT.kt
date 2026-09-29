package com.example.logistikpintar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AdapterFolderPT(
    private var listData: List<FolderPT>,
    private val onClick: (FolderPT) -> Unit
) : RecyclerView.Adapter<AdapterFolderPT.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNamaPT = view.findViewById<TextView>(R.id.tvNamaPT)
        val tvJumlahSJ = view.findViewById<TextView>(R.id.tvJumlahSJ)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_folder_pt, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val folder = listData[position]
        holder.tvNamaPT.text = folder.namaPT
        holder.tvJumlahSJ.text = "${folder.jumlahSj} Surat Jalan"

        holder.itemView.setOnClickListener {
            onClick(folder)
        }
    }

    override fun getItemCount() = listData.size

    fun updateData(newList: List<FolderPT>) {
        listData = newList
        notifyDataSetChanged()
    }
}

data class FolderPT(
    val namaPT: String,
    val jumlahSj: Int,
    val listDataKonsolidasi: List<BrankasActivity.DataKonsolidasi>
)