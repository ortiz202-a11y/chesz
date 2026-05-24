package com.chesz

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.chesz.floating.BubbleService

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQ_OVERLAY = 123
        private const val REQ_APP_INFO = 124
        private const val REQ_ACCESSIBILITY = 125
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Settings.canDrawOverlays(this)) {
            startActivityForResult(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                REQ_OVERLAY,
            )
        } else {
            checkA11yFlow()
        }
    }

    private fun checkA11yFlow() {
        if (isA11yEnabled()) {
            launchService()
        } else {
            startActivityForResult(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                },
                REQ_APP_INFO,
            )
        }
    }

    private fun openAccessibilitySettings() {
        startActivityForResult(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            REQ_ACCESSIBILITY,
        )
    }

    private fun isA11yEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.contains("ChessboardA11yService", ignoreCase = true)
    }

    private fun launchService() {
        startService(Intent(this, BubbleService::class.java))
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_OVERLAY -> {
                if (Settings.canDrawOverlays(this)) checkA11yFlow() else finish()
            }
            REQ_APP_INFO -> {
                openAccessibilitySettings()
            }
            REQ_ACCESSIBILITY -> {
                launchService()
            }
        }
    }
}
