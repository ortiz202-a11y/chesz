import sys
import os

file_path = 'app/src/main/java/com/chesz/floating/BubbleService.kt'

with open(file_path, 'r') as f:
    lines = f.readlines()

new_lines = []
skip_next = 0

for i, line in enumerate(lines):
    if skip_next > 0:
        skip_next -= 1
        continue

    # 1. Vaciar el texto inicial del Title
    if 'val title = mkLine("Sshot/Fen/Ai/Done")' in line:
        new_lines.append('    val title = mkLine("")\n')
        
    # 2. Lógica de captura al soltar (ACTION_UP)
    elif 'if (!panelShown) {' in line and 'showPanelIfFits()' in lines[i+1]:
        new_lines.append('            val hasPerm = (mpResultCode == android.app.Activity.RESULT_OK) && (mpData != null)\n')
        new_lines.append('            if (!panelShown) {\n')
        new_lines.append('              showPanelIfFits()\n')
        new_lines.append('            }\n')
        new_lines.append('            if (hasPerm) {\n')
        new_lines.append('              takeScreenshotOnce()\n')
        new_lines.append('            }\n')
        skip_next = 2

    # 3. Eliminar la sobreescritura de textos en updatePermUi()
    elif 'permText.text = if (ok)' in line:
        new_lines.append('    panelTitle.text = ""\n')
        skip_next = 1 # Salta la línea de panelTitle.text antigua
        
    else:
        new_lines.append(line)

# 4. Inyectar takeScreenshotOnce() antes de la llave de cierre final
for i in range(len(new_lines)-1, -1, -1):
    if new_lines[i].strip() == "}":
        new_lines.insert(i, """
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
""")
        break

with open(file_path, 'w') as f:
    f.writelines(new_lines)

print("Integridad 1:1: Interfaz depurada y función Sshot a Downloads inyectada.")
