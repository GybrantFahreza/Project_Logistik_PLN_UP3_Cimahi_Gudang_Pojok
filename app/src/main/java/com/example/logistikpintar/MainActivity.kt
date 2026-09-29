package com.example.logistikpintar

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private var backPressedTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 🌟 DUA KALI TEKAN BACK UNTUK KELUAR APLIKASI
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    finish()
                } else {
                    Toast.makeText(this@MainActivity, "Tekan 1 kali lagi jika ingin keluar", Toast.LENGTH_SHORT).show()
                    backPressedTime = System.currentTimeMillis()
                }
            }
        })

        // Menu Grid Cards
        findViewById<View>(R.id.cardScan).setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
        }

        findViewById<View>(R.id.cardInputSjResmi)?.setOnClickListener {
            startActivity(Intent(this, InputSjResmiActivity::class.java))
        }

        findViewById<View>(R.id.cardRiwayat).setOnClickListener {
            LogistikHelper.bukaHalaman(this, RiwayatListActivity::class.java)
        }

        findViewById<View>(R.id.cardBrankas).setOnClickListener {
            LogistikHelper.bukaHalaman(this, BrankasActivity::class.java)
        }

        findViewById<View>(R.id.cardGudangSisa).setOnClickListener {
            LogistikHelper.bukaHalaman(this, GudangSisaActivity::class.java)
        }

        findViewById<View>(R.id.cardBackup)?.setOnClickListener {
            val intent = Intent(this, RiwayatListActivity::class.java)
            intent.putExtra("OPEN_EXPORT_MENU", true)
            startActivity(intent)
        }

        // Center Scan Button (+)
        findViewById<View>(R.id.btnCenterScan).setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
        }

        // Bottom Navigation
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.selectedItemId = R.id.nav_dashboard
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> true
                R.id.nav_history -> {
                    LogistikHelper.bukaHalaman(this, RiwayatListActivity::class.java)
                    true
                }
                R.id.nav_brankas -> {
                    LogistikHelper.bukaHalaman(this, BrankasActivity::class.java)
                    true
                }
                R.id.nav_gudang -> {
                    LogistikHelper.bukaHalaman(this, GudangSisaActivity::class.java)
                    true
                }
                else -> false
            }
        }
    }
}
