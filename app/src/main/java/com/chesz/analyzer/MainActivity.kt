package com.chesz.analyzer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import com.chesz.analyzer.bubble.BubbleService

class MainActivity : Activity() {
    private var openedSettings = false
    private val h = Handler(Looper.getMainLooper())
    private var tries = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        h.removeCallbacksAndMessages(null)

        if (Settings.canDrawOverlays(this)) {
            startService(Intent(this, BubbleService::class.java))
            h.postDelayed({ finishAndRemoveTask() }, 250)
            return
        }

        if (!openedSettings) {
            openedSettings = true
            Toast
                .makeText(
                    this,
                    "Da permisos a chesz para 'Mostrar sobre otras apps'. Actívalo y regresa.",
                    Toast.LENGTH_LONG,
                ).show()

            val i =
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                )
            startActivity(i)
            return
        }

        // Workaround: algunos Android tardan en reflejar el permiso al volver
        tries = 0
        h.postDelayed(::pollPermissionThenStart, 250)
    }

    private fun pollPermissionThenStart() {
        if (Settings.canDrawOverlays(this)) {
            startService(Intent(this, BubbleService::class.java))
            h.postDelayed({ finishAndRemoveTask() }, 250)
            return
        }

        tries++
        if (tries >= 12) {
            finishAndRemoveTask()
            return
        }
        h.postDelayed(::pollPermissionThenStart, 250)
    }

    override fun onDestroy() {
        super.onDestroy()
        h.removeCallbacksAndMessages(null)
    }
}
