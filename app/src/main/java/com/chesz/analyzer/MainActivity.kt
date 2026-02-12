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

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
  }

  override fun onResume() {
    super.onResume()

    // Ya con permiso: iniciar servicio y cerrar Activity (con delay para que el overlay alcance a dibujar)
    if (Settings.canDrawOverlays(this)) {
      startService(Intent(this, BubbleService::class.java))

      Handler(Looper.getMainLooper()).postDelayed({
        finishAndRemoveTask()
      }, 350)

      return
    }

    // Sin permiso: mandar a Settings SOLO UNA VEZ
    if (!openedSettings) {
      openedSettings = true
      Toast.makeText(
        this,
        "Da permisos a chesz para 'Mostrar sobre otras apps'. Actívalo y regresa.",
        Toast.LENGTH_LONG
      ).show()

      val i = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:$packageName")
      )
      startActivity(i)
      return
    }

    // Si regresó sin dar permiso, salimos limpio
    finishAndRemoveTask()
  }
}
