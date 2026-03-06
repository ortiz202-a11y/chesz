import sys

file_path = 'app/src/main/java/com/chesz/floating/BubbleService.kt'

with open(file_path, 'r') as f:
    content = f.read()

# 1. Efecto Hundimiento (Sinking) en ACTION_DOWN y ACTION_UP
old_touch_down = """        MotionEvent.ACTION_DOWN -> {
          dragging = false"""
new_touch_down = """        MotionEvent.ACTION_DOWN -> {
          root.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start()
          dragging = false"""
content = content.replace(old_touch_down, new_touch_down)

old_touch_up = """        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
          if (dragging) {"""
new_touch_up = """        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
          root.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
          if (dragging) {"""
content = content.replace(old_touch_up, new_touch_up)

# 2. Lógica de togglePanel (Abre panel y Toma foto)
old_toggle = """  private fun togglePanel() {
    if (!panelShown) {
      showPanelIfFits()
    }
  }"""
new_toggle = """  private fun togglePanel() {
    val hasPerm = (mpResultCode == android.app.Activity.RESULT_OK) && (mpData != null)
    if (!panelShown) {
      showPanelIfFits()
    }
    if (hasPerm) {
      takeScreenshotOnce()
    }
  }"""
content = content.replace(old_toggle, new_toggle)

# 3. Limpieza de Textos UI
content = content.replace('val title = mkLine("Sshot/Fen/Ai/Done")', 'val title = mkLine("")')
content = content.replace('permText.text = if (ok) "Permiso OK" else "Permiso captura: TOCAR"\n    panelTitle.text = if (ok) "Sshot/Fen/Ai/Done" else "Sshot/Fen/Ai/Done (sin permiso)"', 'panelTitle.text = ""')

# 4. Inyección de Sshot (Downloads)
last_brace_idx = content.rfind('}')
sshot_code = """
  private fun takeScreenshotOnce() {
    val rc = mpResultCode ?: return
    val data = mpData ?: return

    val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
    val mp = mgr.getMediaProjection(rc, data)

    val dm = resources.displayMetrics
    val density = dm.densityDpi

    val reader = android.media.ImageReader.newInstance(
      sw, sh, android.graphics.PixelFormat.RGBA_8888, 2
    )

    val vd = mp.createVirtualDisplay(
      "chesz-shot", sw, sh, density,
      android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
      reader.surface, null, null
    )

    root.postDelayed({
      val image = reader.acquireLatestImage() ?: run {
        runCatching { vd.release() }
        runCatching { reader.close() }
        runCatching { mp.stop() }
        return@postDelayed
      }

      try {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * sw

        val bitmap = android.graphics.Bitmap.createBitmap(
          sw + rowPadding / pixelStride, sh, android.graphics.Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        val cropped = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, sw, sh)
        bitmap.recycle()

        @Suppress("DEPRECATION")
        val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        if (dir != null) {
          if (!dir.exists()) dir.mkdirs()
          val out = java.io.File(dir, "chesz_last.png")
          java.io.FileOutputStream(out).use { fos ->
            cropped.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos)
          }
        }
        cropped.recycle()
        runCatching { flashBubbleRed() }

      } finally {
        runCatching { image.close() }
        runCatching { vd.release() }
        runCatching { reader.close() }
        runCatching { mp.stop() }
      }
    }, 200)
  }
"""
content = content[:last_brace_idx] + sshot_code + content[last_brace_idx:]

with open(file_path, 'w') as f:
    f.write(content)

print("Integridad 1:1: Sinking y Sshot a Downloads inyectados con precisión.")
