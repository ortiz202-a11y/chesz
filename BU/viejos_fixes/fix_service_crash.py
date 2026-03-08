import sys

file_path = 'app/src/main/java/com/chesz/floating/BubbleService.kt'
with open(file_path, 'r') as f:
    content = f.read()

# 1. Modificar onDestroy
old_ondestroy = """  override fun onDestroy() {
    super.onDestroy()
    runCatching { wm.removeViewImmediate(root) }
    runCatching { if (killShown) wm.removeViewImmediate(killRoot) }
    mpData = null
    mpResultCode = null
    killShown = false
  }"""
new_ondestroy = """  override fun onDestroy() {
    super.onDestroy()
    runCatching { wm.removeViewImmediate(root) }
    runCatching { if (killShown) wm.removeViewImmediate(killRoot) }
    runCatching { activeMediaProjection?.stop() }
    activeMediaProjection = null
    mpData = null
    mpResultCode = null
    killShown = false
  }"""
content = content.replace(old_ondestroy, new_ondestroy)

# 2. Modificar onCreate para iniciar el Foreground Service
old_oncreate = """  override fun onCreate() {
    super.onCreate()
    wm = getSystemService(WINDOW_SERVICE) as WindowManager
    createRootOverlay()
    createKillArea()
    updateScreenCache()
  }"""
new_oncreate = """  override fun onCreate() {
    super.onCreate()
    startForegroundForMediaProjection()
    wm = getSystemService(WINDOW_SERVICE) as WindowManager
    createRootOverlay()
    createKillArea()
    updateScreenCache()
  }

  private fun startForegroundForMediaProjection() {
    val channelId = "chesz_channel"
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      val channel = android.app.NotificationChannel(channelId, "Chesz Service", android.app.NotificationManager.IMPORTANCE_LOW)
      val nm = getSystemService(android.app.NotificationManager::class.java)
      nm.createNotificationChannel(channel)
    }
    val notif = android.app.Notification.Builder(this, channelId)
      .setContentTitle("Chesz")
      .setContentText("Servicio de captura activo")
      .setSmallIcon(R.drawable.ic_check_green)
      .build()

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
      startForeground(1, notif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    } else {
      startForeground(1, notif)
    }
  }"""
content = content.replace(old_oncreate, new_oncreate)

# 3. Reemplazar takeScreenshotOnce por la versión invulnerable
idx = content.find("  private fun takeScreenshotOnce() {")
if idx != -1:
    content = content[:idx] + """  private var activeMediaProjection: android.media.projection.MediaProjection? = null

  private fun takeScreenshotOnce() {
    val rc = mpResultCode ?: return
    val data = mpData ?: return

    if (activeMediaProjection == null) {
      val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
      activeMediaProjection = mgr.getMediaProjection(rc, data)
    }
    val mp = activeMediaProjection ?: return

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

        runCatching {
          @Suppress("DEPRECATION")
          val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
          if (dir != null) {
            if (!dir.exists()) dir.mkdirs()
            val out = java.io.File(dir, "chesz_last.png")
            java.io.FileOutputStream(out).use { fos ->
              cropped.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos)
            }
          }
        }.onFailure {
          // Si Android bloquea Downloads por Scoped Storage, lo guarda en la app
          val fallbackDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
          if (fallbackDir != null) {
            val out = java.io.File(fallbackDir, "chesz_last.png")
            java.io.FileOutputStream(out).use { fos ->
              cropped.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos)
            }
          }
        }
        
        cropped.recycle()
        panelTitle.text = "Sshot/"

      } finally {
        runCatching { image.close() }
        runCatching { vd.release() }
        runCatching { reader.close() }
        // Se extirpó el mp.stop() para no quemar el permiso
      }
    }, 200)
  }
}
"""
    with open(file_path, 'w') as f:
        f.write(content)
    print("Integridad 1:1: Lógica de Foreground Service y Sshot Inmortal inyectada.")
else:
    print("Error: No se encontró la función takeScreenshotOnce")
