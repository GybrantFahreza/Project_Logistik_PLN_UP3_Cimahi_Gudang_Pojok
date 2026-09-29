package com.example.logistikpintar

import android.os.Bundle
import android.text.InputFilter
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class InputManualActivity : AppCompatActivity() {

    private lateinit var containerRincian: LinearLayout
    private lateinit var etNamaBarang: EditText
    private lateinit var etSatuan: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_input_manual)

        containerRincian = findViewById(R.id.containerRincian)
        etNamaBarang = findViewById(R.id.etNamaBarang)
        etSatuan = findViewById(R.id.etSatuan)
        val btnSimpan = findViewById<Button>(R.id.btnSimpan)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnTambahRincian = findViewById<Button>(R.id.btnTambahRincian)

        // --- STEP 2: LOGIKA DATE PICKER ---
        val etTanggal = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etTanggalManual)
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        // Set default ke hari ini
        etTanggal.setText(dateFormat.format(calendar.time))

        // Munculkan Kalender saat diklik
        etTanggal.setOnClickListener {
            android.app.DatePickerDialog(this, { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                etTanggal.setText(dateFormat.format(calendar.time))
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        val filterKapital = arrayOf<InputFilter>(InputFilter.AllCaps())
        etNamaBarang.filters = filterKapital
        etSatuan.filters = filterKapital

        btnBack.setOnClickListener { finish() }

        // Tambah baris rincian pertama secara otomatis
        tambahBarisRincian()

        btnTambahRincian.setOnClickListener {
            tambahBarisRincian()
        }

        btnSimpan.setOnClickListener {
            val tanggalSj = etTanggal.text.toString()
            val namaBarang = etNamaBarang.text.toString().trim()
            val satuan = etSatuan.text.toString().trim()
            val timestamp = System.currentTimeMillis()

            if (namaBarang.isEmpty() || satuan.isEmpty()) {
                Toast.makeText(this, "Harap isi Nama Barang dan Satuan!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch(Dispatchers.IO) {
                val db = LogistikDatabase.getDatabase(this@InputManualActivity).logistikDao()
                var index = 1
                var berhasilSimpan = false

                for (i in 0 until containerRincian.childCount) {
                    val rowView = containerRincian.getChildAt(i)
                    val etPekerjaan = rowView.findViewById<EditText>(R.id.etPekerjaanRow)
                    val etQty = rowView.findViewById<EditText>(R.id.etQtyRow)

                    val pekerjaan = etPekerjaan.text.toString().trim()
                    val qtyStr = etQty.text.toString().trim()

                    if (pekerjaan.isNotEmpty() && qtyStr.isNotEmpty()) {
                        val qty = qtyStr.toIntOrNull() ?: 0
                        if (qty > 0) {
                            // Buat No SJ unik per gardu agar konsolidasi Riwayat Harian berjalan sempurna
                            val noSjUnik = "DRAFT-TANPASLIP-$timestamp-$index"
                            val sjBaru = SuratJalanEntity(
                                noSj = noSjUnik,
                                tanggal = tanggalSj,
                                pekerjaan = pekerjaan,
                                vendor = "TANPA SLIP"
                            )
                            val idSj = db.insertSuratJalan(sjBaru)

                            val matBaru = MaterialEntity(
                                idSuratJalan = idSj.toInt(),
                                namaBarang = namaBarang,
                                qty = qty,
                                satuan = satuan,
                                status = "AKTIF"
                            )
                            db.insertMaterial(matBaru)
                            index++
                            berhasilSimpan = true
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    if (berhasilSimpan) {
                        Toast.makeText(this@InputManualActivity, "Data Darurat Tersimpan!", Toast.LENGTH_SHORT).show()
                        finish()
                    } else {
                        Toast.makeText(this@InputManualActivity, "Harap isi rincian gardu & qty dengan benar!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun tambahBarisRincian() {
        val rowView = LayoutInflater.from(this).inflate(R.layout.item_input_rincian, containerRincian, false)
        val btnHapusRow = rowView.findViewById<ImageButton>(R.id.btnHapusRow)
        val etPekerjaanRow = rowView.findViewById<EditText>(R.id.etPekerjaanRow)

        val filterKapital = arrayOf<InputFilter>(InputFilter.AllCaps())
        etPekerjaanRow.filters = filterKapital

        btnHapusRow.setOnClickListener {
            if (containerRincian.childCount > 1) {
                containerRincian.removeView(rowView)
            } else {
                Toast.makeText(this, "Minimal harus ada satu rincian!", Toast.LENGTH_SHORT).show()
            }
        }

        containerRincian.addView(rowView)
    }
}