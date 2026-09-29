package com.example.logistikpintar

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

object MaterialDictionary {

    data class MasterItem(val kode: String, val deskripsi: String, val satuan: String)

    // Menggunakan HashMap untuk pencarian super cepat O(1) berdasarkan Kode Material
    private val masterMap = HashMap<String, MasterItem>()
    private var isInitialized = false

    /**
     * Membaca file material_list.csv dari folder assets secara asynchronous.
     * Cukup dipanggil sekali saat aplikasi atau ReviewActivity terbuka.
     */
    fun init(context: Context) {
        if (isInitialized) return

        Thread {
            try {
                val inputStream = context.assets.open("material_list.csv")
                val reader = BufferedReader(InputStreamReader(inputStream))

                // Baca baris pertama (header) dan abaikan
                reader.readLine()

                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val rawLine = line ?: continue
                    if (rawLine.isBlank()) continue

                    // Parse CSV dengan pemisah koma. Karena nilai dibungkus tanda kutip,
                    // kita split berdasarkan kombinasi kutip dan koma: ","
                    val tokens = rawLine.split("\",\"")
                    if (tokens.size >= 3) {
                        // Kolom 1: Material Code
                        var kode = tokens[0]
                        // Kolom 2: Material Description
                        var deskripsi = tokens[1]
                        // Kolom 3: Satuan
                        var satuan = tokens[2]

                        // Bersihkan tanda kutip yang tersisa
                        kode = kode.replace("\"", "").trim()
                        deskripsi = deskripsi.replace("\"", "").trim()
                        satuan = satuan.replace("\"", "").trim()

                        // Terjemahkan satuan SAP/ERP ("U" / "M" dsb) ke satuan logistik kita
                        val satuanNormal = normalisasiSatuanMaster(satuan)

                        if (kode.isNotBlank() && deskripsi.isNotBlank()) {
                            masterMap[kode] = MasterItem(kode, deskripsi, satuanNormal)
                        }
                    }
                }
                reader.close()
                isInitialized = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    /**
     * Mengambil data material yang 100% akurat berdasarkan Nomor Kode.
     * Mengembalikan null jika kode tidak ditemukan di CSV.
     */
    fun getMaterialByCode(kodeKotor: String): MasterItem? {
        if (!isInitialized || masterMap.isEmpty()) return null
        
        // Pastikan kode hanya berisi angka untuk pencocokan
        val cleanKode = kodeKotor.filter { it.isDigit() }
        return masterMap[cleanKode]
    }

    private fun normalisasiSatuanMaster(sat: String): String {
        val u = sat.uppercase().trim()
        return when (u) {
            "U", "UNT", "UNIT" -> "UNIT"
            "M", "MTR", "METER" -> "M"
            "SET", "S3T" -> "SET"
            "PC", "PCS" -> "PCS"
            "BT", "BTG", "BATANG" -> "BTG"
            "ROL", "ROLL" -> "ROL"
            "EA", "BH", "BUAH" -> "BH"
            else -> "BH" // Default
        }
    }
}
