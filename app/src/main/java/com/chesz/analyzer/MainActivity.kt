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

    if (!Settings.canDrawOverlays(this)) {
      val i = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:$packageName")
      )
      startActivity(i)
      return
    }

    startService(Intent(this, BubbleService::class.java))
    finish()
  }
}
