package com.chesz.analyzer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import com.chesz.analyzer.bubble.BubbleService

class MainActivity : Activity() {

  companion object {
    private const val REQ_OVERLAY = 1001
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
  }

  override fun onResume() {
    super.onResume()

    // Si ya hay permiso, iniciar servicio y salir
    if (Settings.canDrawOverlays(this)) {
      startService(Intent(this, BubbleService::class.java))
      finishAndRemoveTask()
      return
    }

    // Si NO hay permiso, avisar y mandar a Settings
    Toast.makeText(
      this,
      "Da permisos a chesz para 'Mostrar sobre otras apps'. Actívalo y regresa.",
      Toast.LENGTH_LONG
    ).show()

    val i = Intent(
      Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
      Uri.parse("package:$packageName")
    )

    @Suppress("DEPRECATION")
    startActivityForResult(i, REQ_OVERLAY)
  }

  @Deprecated("Deprecated in Java")
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)

    if (requestCode == REQ_OVERLAY) {
      if (Settings.canDrawOverlays(this)) {
        startService(Intent(this, BubbleService::class.java))
        finishAndRemoveTask()
      } else {
        Toast.makeText(this, "Permiso no concedido.", Toast.LENGTH_SHORT).show()
      }
    }
  }
}
