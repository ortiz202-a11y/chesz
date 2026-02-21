package com.chesz.analyzer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.chesz.analyzer.floating.FloatingService

/**
 * Minimal entry-point activity.
 * Checks for the SYSTEM_ALERT_WINDOW (overlay) permission and either requests it
 * or starts FloatingService directly. After granting the permission the user
 * can return to the app and tap the button again.
 */
class MainActivity : AppCompatActivity() {

    /** Modern activity-result launcher replacing the deprecated startActivityForResult. */
    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // Result is not used directly; onResume() checks the actual permission state.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btn_enable_overlay).setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                startFloatingService()
            } else {
                requestOverlayPermission()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // If permission was just granted, launch the service and finish the activity
        // so only the floating button is visible.
        if (Settings.canDrawOverlays(this)) {
            startFloatingService()
        }
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingService::class.java)
        startForegroundService(intent)
        Toast.makeText(this, getString(R.string.overlay_started), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }
}
