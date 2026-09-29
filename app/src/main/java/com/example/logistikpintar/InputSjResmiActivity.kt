package com.example.logistikpintar

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.InputFilter
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class InputSjResmiActivity : AppCompatActivity() {

    private lateinit var etTanggalResmi: TextInputEditText
    private lateinit var etNoSjResmi: TextInputEditText
    private lateinit var etVendorResmi: TextInputEditText
    private lateinit var etPekerjaanResmi: TextInputEditText
    private lateinit var containerMaterialResmi: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_input_sj_resmi)

        etTanggalResmi = findViewById(R.id.etTanggalResmi)
        etNoSjResmi = findViewById(R.id.etNoSjResmi)
        etVendorResmi = findViewById(R.id.etVendorResmi)
        etPekerjaanResmi = findViewById(R.id.etPekerjaanResmi)
        containerMaterialResmi = findViewById(R.id.containerMaterialResmi)

        val btnTambahBarangResmi = findViewById<Button>(R.id.btnTambahBarangResmi)
        val btnSimpanSjResmi = findViewById<Button>(R.id.btnSimpanSjResmi)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        btnBack.setOnClickListener { finish() }

        val filterKapital = arrayOf<InputFilter>(InputFilter.AllCaps())
        etNoSjResmi.filters = filterKapital
        etVendorResmi.filters = filterKapital
        etPekerjaanResmi.filters = filterKapital

        // 1. DATE PICKER DIALOG (Default Hari Ini)
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        etTanggalResmi.setText(dateFormat.format(calendar.time))

        etTanggalResmi.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    calendar.set(year, month, dayOfMonth)
                    etTanggalResmi.setText(dateFormat.format(calendar.time))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // 2. TAMBAH BARIS PERTAMA OTOMATIS
        tambahBarisBarang()

        btnTambahBarangResmi.setOnClickListener {
            tambahBarisBarang()
        }

        // 3. LOGIKA SIMPAN KE DATABASE
        btnSimpanSjResmi.setOnClickListener {
            val noSj = etNoSjResmi.text.toString().trim()
            val tanggal = etTanggalResmi.text.toString().trim()
            val vendor = etVendorResmi.text.toString().trim()
            val pekerjaan = etPekerjaanResmi.text.toString().trim()

            if (noSj.isEmpty()) {
                Toast.makeText(this, "Nomor Surat Jalan wajib diisi!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch(Dispatchers.IO) {
                val db = LogistikDatabase.getDatabase(this@InputSjResmiActivity).logistikDao()

                // 1. Simpan Header SJ (Satu kali)
                val sjBaru = SuratJalanEntity(
                    noSj = noSj,
                    tanggal = tanggal,
                    vendor = vendor,
                    pekerjaan = pekerjaan
                )
                val idSj = db.insertSuratJalan(sjBaru)

                var adaMaterialDisimpan = false

                // 2. Looping dan Simpan Semua Material di Container
                for (i in 0 until containerMaterialResmi.childCount) {
                    val rowView = containerMaterialResmi.getChildAt(i)
                    val etNama = rowView.findViewById<EditText>(R.id.etNamaBarangRow)
                    val etQty = rowView.findViewById<EditText>(R.id.etQtyRow)
                    val etSatuan = rowView.findViewById<EditText>(R.id.etSatuanRow)

                    val nama = etNama.text.toString().trim()
                    val qty = etQty.text.toString().trim()
                    val satuan = etSatuan.text.toString().trim()

                    if (nama.isNotEmpty() && qty.isNotEmpty()) {
                        val matBaru = MaterialEntity(
                            idSuratJalan = idSj.toInt(),
                            namaBarang = nama,
                            qty = qty.toIntOrNull() ?: 0,
                            satuan = satuan.ifEmpty { "BH" },
                            status = "AKTIF" // Masuk ke Brankas
                        )
                        db.insertMaterial(matBaru)
                        adaMaterialDisimpan = true
                    }
                }

                withContext(Dispatchers.Main) {
                    if (adaMaterialDisimpan) {
                        Toast.makeText(this@InputSjResmiActivity, "SJ Berhasil Disimpan ke Brankas!", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this@InputSjResmiActivity, "Harap isi minimal satu Nama Barang dan QTY!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun tambahBarisBarang() {
        val rowView = LayoutInflater.from(this).inflate(R.layout.item_input_material, containerMaterialResmi, false)
        val etNamaBarangRow = rowView.findViewById<EditText>(R.id.etNamaBarangRow)
        val etSatuanRow = rowView.findViewById<EditText>(R.id.etSatuanRow)
        val btnHapusBarang = rowView.findViewById<ImageButton>(R.id.btnHapusBarang)

        val filterKapital = arrayOf<InputFilter>(InputFilter.AllCaps())
        etNamaBarangRow.filters = filterKapital
        etSatuanRow.filters = filterKapital

        btnHapusBarang.setOnClickListener {
            if (containerMaterialResmi.childCount > 1) {
                containerMaterialResmi.removeView(rowView)
            } else {
                Toast.makeText(this, "Minimal harus ada satu barang!", Toast.LENGTH_SHORT).show()
            }
        }

        containerMaterialResmi.addView(rowView)
    }
}
