package com.chesz.analyzer.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import com.chesz.analyzer.core.MainService

class MainActivity : Activity() {

    private val REQ_OVERLAY = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!canDrawOverlays()) {
            Toast.makeText(this, "Activa permiso de superposición (overlay) para chesz", Toast.LENGTH_LONG).show()
            requestOverlayPermission()
            return
        }

        startCoreService()
        finish()
    }

    override fun onResume() {
        super.onResume()
        // Cuando vuelves de Settings, revisa otra vez.
        if (canDrawOverlays()) {
            startCoreService()
            finish()
        }
    }

    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, REQ_OVERLAY)
        }
    }

    private fun startCoreService() {
        val i = Intent(this, MainService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(i)
        } else {
            startService(i)
        }
    }
}
