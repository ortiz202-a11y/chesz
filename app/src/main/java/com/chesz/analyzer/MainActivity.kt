package com.chesz.analyzer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import com.chesz.analyzer.bubble.BubbleService

class MainActivity : Activity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
  }

  override fun onResume() {
    super.onResume()

    if (!Settings.canDrawOverlays(this)) {
      Toast.makeText(
        this,
        "Dar permisos a chesz para mostrar sobre otras apps",
        Toast.LENGTH_LONG
      ).show()

      val i = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:$packageName")
      )
      startActivity(i)
      return
    }

    // Permiso concedido → iniciar servicio
    startService(Intent(this, BubbleService::class.java))

    // Cerrar Activity completamente (sin dejar rastro)
    finishAndRemoveTask()
  }
}
