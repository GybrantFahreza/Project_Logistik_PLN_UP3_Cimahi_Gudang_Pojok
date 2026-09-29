package com.example.logistikpintar

import android.app.Activity
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LogistikHelper {
    suspend fun prosesPecahBarang(db: LogistikDao, namaBarang: String, jumlahDiambil: Int, statusAwal: String, statusTujuan: String, statusSisa: String) {
        withContext(Dispatchers.IO) {
            var sisaDiambil = jumlahDiambil
            val listMat = db.getMaterialsByNameAndStatus(namaBarang, statusAwal)
            for (mat in listMat) {
                if (sisaDiambil <= 0) break
                if (sisaDiambil >= mat.qty) {
                    mat.status = statusTujuan
                    db.updateMaterial(mat)
                    sisaDiambil -= mat.qty
                } else {
                    val sisaGudang = mat.qty - sisaDiambil
                    mat.qty = sisaDiambil
                    mat.status = statusTujuan
                    db.updateMaterial(mat)
                    
                    val matSisa = mat.copy(id = 0, qty = sisaGudang, status = statusSisa)
                    db.insertMaterial(matSisa)
                    sisaDiambil = 0
                }
            }
        }
    }

    suspend fun prosesPecahBarangByIds(db: LogistikDao, materialIds: List<Int>, jumlahDiambil: Int, statusAwal: String, statusTujuan: String, statusSisa: String) {
        withContext(Dispatchers.IO) {
            var sisaDiambil = jumlahDiambil
            for (matId in materialIds) {
                if (sisaDiambil <= 0) break
                val mat = db.getMaterialById(matId) ?: continue
                if (mat.status != statusAwal) continue

                if (sisaDiambil >= mat.qty) {
                    mat.status = statusTujuan
                    db.updateMaterial(mat)
                    sisaDiambil -= mat.qty
                } else {
                    val sisaGudang = mat.qty - sisaDiambil
                    mat.qty = sisaDiambil
                    mat.status = statusTujuan
                    db.updateMaterial(mat)

                    val matSisa = mat.copy(id = 0, qty = sisaGudang, status = statusSisa)
                    db.insertMaterial(matSisa)
                    sisaDiambil = 0
                }
            }
        }
    }

    fun bukaHalaman(currentActivity: Activity, targetClass: Class<*>) {
        if (currentActivity.javaClass == targetClass) return
        val intent = Intent(currentActivity, targetClass)
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        currentActivity.startActivity(intent)
        @Suppress("DEPRECATION")
        currentActivity.overridePendingTransition(0, 0)
        currentActivity.finish()
        @Suppress("DEPRECATION")
        currentActivity.overridePendingTransition(0, 0)
    }
}