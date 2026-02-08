package com.chesz.analyzer.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.chesz.analyzer.core.MainService

class MainActivity : Activity() {

    private val REQ_OVERLAY = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkOverlayPermission()
    }

    override fun onResume() {
        super.onResume()
        // Al volver de Settings, si ya hay permiso, arrancar servicio y cerrar Activity
        if (Settings.canDrawOverlays(this)) {
            startFloatingService()
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_OVERLAY && Settings.canDrawOverlays(this)) {
            startFloatingService()
            finish()
        }
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, REQ_OVERLAY)
        } else {
            startFloatingService()
            finish()
        }
    }

    private fun startFloatingService() {
        val intent = Intent(this, MainService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
