package com.chesz.analyzer

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.chesz.analyzer.floating.FloatingService

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val button = Button(this).apply {
            text = "ARRANCAR BURBUJA"
            setOnClickListener {
                if (checkPermission()) {
                    startService(Intent(this@MainActivity, FloatingService::class.java))
                }
            }
        }
        setContentView(button)
    }

    private fun checkPermission(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, 0)
            return false
        }
        return true
    }
}
