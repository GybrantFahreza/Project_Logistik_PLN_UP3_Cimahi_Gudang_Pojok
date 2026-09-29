package com.example.logistikpintar

import android.content.Context
import androidx.room.*

// 1. TABEL SURAT JALAN (Header)
@Entity(tableName = "surat_jalan")
data class SuratJalanEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tanggal: String,
    val noSj: String,
    val vendor: String,
    val pekerjaan: String,
    @ColumnInfo(name = "tanggalScan") val tanggalScan: Long = System.currentTimeMillis()
)

// 2. TABEL DAFTAR BARANG
@Entity(
    tableName = "material",
    foreignKeys = [ForeignKey(
        entity = SuratJalanEntity::class,
        parentColumns = ["id"],
        childColumns = ["idSuratJalan"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("idSuratJalan")]
)
data class MaterialEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val idSuratJalan: Int,
    val namaBarang: String,
    var qty: Int,
    val satuan: String,
    var status: String = "AKTIF",
    @ColumnInfo(name = "is_tanpa_slip")
    var isTanpaSlip: Boolean = false
)

// 3. MESIN PINTU BRANKAS (DAO - Data Access Object)
@Dao
interface LogistikDao {
    @Insert
    suspend fun insertSuratJalan(suratJalan: SuratJalanEntity): Long

    @Insert
    suspend fun insertMaterial(material: MaterialEntity): Long

    @Update
    suspend fun updateMaterial(material: MaterialEntity)

    @Query("SELECT DISTINCT s.* FROM surat_jalan s INNER JOIN material m ON s.id = m.idSuratJalan WHERE m.status = 'AKTIF'")
    suspend fun getActiveSuratJalan(): List<SuratJalanEntity>

    @Query("SELECT DISTINCT s.* FROM surat_jalan s INNER JOIN material m ON s.id = m.idSuratJalan WHERE m.status = 'ARSIP'")
    suspend fun getArchivedSuratJalan(): List<SuratJalanEntity>

    @Query("SELECT * FROM surat_jalan")
    suspend fun getAllSuratJalan(): List<SuratJalanEntity>

    @Query("SELECT DISTINCT s.* FROM surat_jalan s INNER JOIN material m ON s.id = m.idSuratJalan WHERE TRIM(s.tanggal) = TRIM(:tanggalTarget)")
    suspend fun getSuratJalanByDate(tanggalTarget: String): List<SuratJalanEntity>

    @Query("SELECT * FROM surat_jalan WHERE id = :id LIMIT 1")
    suspend fun getSuratJalanById(id: Int): SuratJalanEntity?

    @Query("SELECT * FROM material")
    suspend fun getAllMaterial(): List<MaterialEntity>

    @Query("SELECT DISTINCT TRIM(s.tanggal) FROM surat_jalan s INNER JOIN material m ON s.id = m.idSuratJalan WHERE m.status = 'ARSIP' ORDER BY TRIM(s.tanggal) DESC")
    suspend fun getArchivedDates(): List<String>

    @Query("SELECT DISTINCT s.* FROM surat_jalan s INNER JOIN material m ON s.id = m.idSuratJalan WHERE TRIM(s.tanggal) = TRIM(:tanggalTarget) AND m.status = 'ARSIP'")
    suspend fun getArchivedSuratJalanByDate(tanggalTarget: String): List<SuratJalanEntity>

    @Query("SELECT * FROM material WHERE idSuratJalan = :idSj")
    suspend fun getMaterialsBySjId(idSj: Int): List<MaterialEntity>

    @Query("SELECT * FROM material WHERE namaBarang = :nama AND status = :statusCari")
    suspend fun getMaterialsByNameAndStatus(nama: String, statusCari: String): List<MaterialEntity>

    @Query("SELECT * FROM surat_jalan WHERE noSj = :noSj LIMIT 1")
    suspend fun getSuratJalanByNoSj(noSj: String): SuratJalanEntity?

    @Update
    suspend fun updateSuratJalan(suratJalan: SuratJalanEntity)

    @Query("DELETE FROM material WHERE idSuratJalan = :idSj")
    suspend fun deleteMaterialsBySjId(idSj: Int)

    @Delete
    suspend fun deleteSuratJalan(suratJalan: SuratJalanEntity)

    @Query("DELETE FROM material WHERE namaBarang = :nama AND status = 'AKTIF'")
    suspend fun deleteActiveMaterialsByName(nama: String)

    // 🌟 PEMBERSIH OTOMATIS: Hapus header Surat Jalan kosong yang tidak punya material sama sekali
    @Query("DELETE FROM surat_jalan WHERE id NOT IN (SELECT DISTINCT idSuratJalan FROM material)")
    suspend fun deleteOrphanSuratJalan()

    // 🌟 TAHAP 9: Kalkulasi Global
    @Query("SELECT SUM(qty) FROM material WHERE namaBarang = :nama")
    suspend fun getTotalSemuaMaterial(nama: String): Int?

    @Query("SELECT SUM(qty) FROM material WHERE namaBarang = :nama AND status = 'TITIP_GUDANG'")
    suspend fun getTotalSisaGudang(nama: String): Int?

    @Query("SELECT * FROM material WHERE id = :matId LIMIT 1")
    suspend fun getMaterialById(matId: Int): MaterialEntity?

    @Delete
    suspend fun deleteMaterial(material: MaterialEntity)

    @Query("SELECT m.* FROM material m INNER JOIN surat_jalan s ON m.idSuratJalan = s.id WHERE TRIM(s.noSj) = TRIM(:nomorSj)")
    suspend fun getMaterialsByNoSjUtuh(nomorSj: String): List<MaterialEntity>

    @Query("SELECT * FROM surat_jalan WHERE TRIM(noSj) = TRIM(:nomorSj) LIMIT 1")
    suspend fun getSuratJalanByNoSjUtuh(nomorSj: String): SuratJalanEntity?

    @Query("UPDATE material SET status = :newStatus WHERE id IN (:ids)")
    suspend fun updateMaterialStatusBulk(ids: List<Int>, newStatus: String)

    // 🌟 MENDAPATKAN TOTAL SJ BERDASARKAN TANGGAL SCAN (Kerja Hari Ini)
    @Query("SELECT * FROM surat_jalan WHERE tanggalScan >= :startOfTodayMs")
    suspend fun getSuratJalanScannedToday(startOfTodayMs: Long): List<SuratJalanEntity>

    // 🌟 AUTO-TRANSFER GUDANG: Cari semua barang berstatus AKTIF (Hari ini) yang tanggal SCAN-nya SEBELUM awal hari ini
    @Query("SELECT m.* FROM material m INNER JOIN surat_jalan s ON m.idSuratJalan = s.id WHERE m.status = 'AKTIF' AND s.tanggalScan > 0 AND s.tanggalScan < :startOfTodayMs")
    suspend fun getMaterialAktifLewatHari(startOfTodayMs: Long): List<MaterialEntity>

    // 🌟 JALUR DARURAT: Ambil semua barang yang belum ada Slip resmi
    @Query("SELECT * FROM material WHERE idSuratJalan IN (SELECT id FROM surat_jalan WHERE noSj LIKE '%DRAFT%')")
    suspend fun getAllDraftMaterials(): List<MaterialEntity>

    // 🌟 JALUR DARURAT: Ambil semua barang yang belum ada Slip resmi (Dapatkan info SJ juga)
    @Query("SELECT m.*, s.tanggal as tanggalSj, s.pekerjaan as pekerjaanSj FROM material m INNER JOIN surat_jalan s ON m.idSuratJalan = s.id WHERE s.noSj LIKE '%DRAFT%'")
    suspend fun getDraftMaterialsWithInfo(): List<MaterialWithSjInfo>
}

data class MaterialWithSjInfo(
    @Embedded val material: MaterialEntity,
    val tanggalSj: String,
    val pekerjaanSj: String
)

// 4. BRANKAS UTAMA
@Database(entities = [SuratJalanEntity::class, MaterialEntity::class], version = 6, exportSchema = false)
abstract class LogistikDatabase : RoomDatabase() {
    abstract fun logistikDao(): LogistikDao

    companion object {
        @Volatile
        private var INSTANCE: LogistikDatabase? = null

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE surat_jalan ADD COLUMN tanggalScan INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): LogistikDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LogistikDatabase::class.java,
                    "brankas_logistik.db"
                )
                .addMigrations(MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}