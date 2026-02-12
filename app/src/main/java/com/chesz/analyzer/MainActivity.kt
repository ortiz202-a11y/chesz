package com.chesz.analyzer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.chesz.analyzer.bubble.BubbleService

class MainActivity : Activity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
  }

  override fun onResume() {
    super.onResume()

    if (!Settings.canDrawOverlays(this)) {
      startActivity(
        Intent(
          Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
          Uri.parse("package:$packageName")
        )
      )
      return
    }

    val svc = Intent(this, BubbleService::class.java)
    if (Build.VERSION.SDK_INT >= 26) {
      startForegroundService(svc)
    } else {
      startService(svc)
    }
    finish()
  }
}
