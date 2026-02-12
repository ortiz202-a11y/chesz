package com.chesz.analyzer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import com.chesz.analyzer.bubble.BubbleService

class MainActivity : Activity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
  }

  override fun onResume() {
    super.onResume()

    // 1) Si NO hay permiso, manda a Settings y NO inicia servicio todavía
    if (!Settings.canDrawOverlays(this)) {
      val i = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:$packageName")
      )
      startActivity(i)
      return
    }

    // 2) Ya con permiso: iniciar como servicio normal (NO foreground)
    startService(Intent(this, BubbleService::class.java))

    // 3) Cierra la Activity (solo era launcher)
    finish()
  }
}
