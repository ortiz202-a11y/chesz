package com.chesz.analyzer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import com.chesz.analyzer.bubble.BubbleService

class MainActivity : Activity() {

  private val REQ_OVERLAY = 1001

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    if (!Settings.canDrawOverlays(this)) {
      val i = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:$packageName")
      )
      startActivityForResult(i, REQ_OVERLAY)
      return
    }

    startService(Intent(this, BubbleService::class.java))
    finish()
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)

    if (requestCode == REQ_OVERLAY) {
      if (Settings.canDrawOverlays(this)) {
        startService(Intent(this, BubbleService::class.java))
      } else {
        Toast.makeText(this, "Permiso overlay requerido", Toast.LENGTH_LONG).show()
      }
      finish()
    }
  }
}
