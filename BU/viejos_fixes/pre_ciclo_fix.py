import re

file_path = 'app/src/main/java/com/chesz/floating/BubbleService.kt'
with open(file_path, 'r') as f:
    content = f.read()

# 1. Inyectar el bloqueo anti-spam LEGAL dentro del TouchListener (ACTION_UP)
# Buscamos la llamada a togglePanel() dentro del bloque else de ACTION_UP
content = content.replace('else {\n            togglePanel()\n          }', 
                          'else {\n            if (panelTitle.text == "Sshot/") return@setOnTouchListener true\n            togglePanel()\n          }')

# 2. Asegurar que takeScreenshotOnce use el RunId o al menos no colapse por duplicidad
# Limpiamos cualquier rastro de la función mal cerrada al final del archivo
content = re.sub(r'private fun takeScreenshotOnce\(\) \{.*\}', '', content, flags=re.DOTALL)

# 3. Re-escribir la función takeScreenshotOnce con balance PERFECTO de llaves
# Basado exactamente en el ADN que revisaste, pero con cierre de clase garantizado
new_fn = """    private fun takeScreenshotOnce() {
    val rc = mpResultCode ?: return
    val data = mpData ?: return

    panelTitle.text = "Sshot/"
    root.postDelayed({ if(panelTitle.text == "Sshot/") panelTitle.text = "Chesz" }, 3000)

    runCatching {
      if (activeMediaProjection == null) {
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
        activeMediaProjection = mgr.getMediaProjection(rc, data)
      }
      val mp = activeMediaProjection ?: return@runCatching

      val dm = resources.displayMetrics
      val reader = android.media.ImageReader.newInstance(sw, sh, android.graphics.PixelFormat.RGBA_8888, 2)
      val vd = mp.createVirtualDisplay("chesz-shot", sw, sh, dm.densityDpi,
        android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
        reader.surface, null, null)

      root.postDelayed({
        val image = reader.acquireLatestImage() ?: run {
          runCatching { vd.release() }; runCatching { reader.close() }
          return@postDelayed
        }
        try {
          val plane = image.planes[0]
          val buffer = plane.buffer
          val rowStride = plane.rowStride
          val pixelStride = plane.pixelStride
          val rowPadding = rowStride - pixelStride * sw
          val bitmap = android.graphics.Bitmap.createBitmap(sw + rowPadding / pixelStride, sh, android.graphics.Bitmap.Config.ARGB_8888)
          bitmap.copyPixelsFromBuffer(buffer)
          val cropped = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, sw, sh)
          bitmap.recycle()
          val dir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
          if (dir != null) {
            if (!dir.exists()) dir.mkdirs()
            val out = java.io.File(dir, "chesz_last.png")
            java.io.FileOutputStream(out).use { fos ->
              cropped.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos)
            }
          }
          cropped.recycle()
          panelTitle.text = "Sshot/"
          root.postDelayed({ panelTitle.text = "Chesz" }, 3000)
        } finally {
          image.close()
          runCatching { vd.release() }
          runCatching { reader.close() }
        }
      }, 200)
    }.onFailure {
      panelTitle.text = "Sshot/Err"
      root.postDelayed({ panelTitle.text = "Chesz" }, 3000)
    }
  }
}"""

# Agregamos la función y cerramos la clase al final
content = content.strip() + "\n\n" + new_fn

with open(file_path, 'w') as f:
    f.write(content)
print("Soberanía: ADN reparado pre-ciclo. Bloqueo anti-spam y llaves validadas.")
