package com.chesz.floating

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.chesz.R
import kotlin.math.abs
import android.app.Activity

class BubbleService : Service() {

  private lateinit var wm: WindowManager

  // === SINGLE ROOT OVERLAY (botón + panel) ===
  private lateinit var root: FrameLayout
  private lateinit var rootLp: WindowManager.LayoutParams

  private lateinit var bubbleIcon: ImageView
  private lateinit var panelRoot: FrameLayout

  private var panelShown = false
  private var panelDyPx: Int = 0

  // Drag state (sobre el ROOT)
  private var downRawX = 0f
  private var downRawY = 0f
  private var startX = 0
  private var startY = 0
  private var dragging = false
  private var sw = 0
  private var sh = 0
  private var bottomInsetCache = 0

  // Kill area (se mantiene como overlay separado)
  private lateinit var killRoot: FrameLayout
  private lateinit var killCircle: FrameLayout
  private lateinit var killLp: WindowManager.LayoutParams
  private var killShown = false
  private var killHovered = false
  // ===== MediaProjection permission cache =====
  private var mpResultCode: Int? = null
  private var mpData: Intent? = null


  
  // ===== Panel UI refs (permiso captura) =====
  private lateinit var panelTitle: TextView
  private lateinit var permBar: FrameLayout
  private lateinit var permText: TextView

override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == "CHESZ_CAPTURE_PERMISSION_RESULT") {
      mpResultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED)
      @Suppress("DEPRECATION")
      mpData = intent.getParcelableExtra("data")
      root.postDelayed({ runCatching { upgradeToMediaProjection() } }, 500)
      updatePermUi()
    }
    return START_STICKY
  }


  override fun onCreate() {
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
      val channel = android.app.NotificationChannel(channelId, "Chesz Service", android.app.NotificationManager.IMPORTANCE_HIGH)
      val nm = getSystemService(android.app.NotificationManager::class.java)
      nm.createNotificationChannel(channel)
    }
    val notif = android.app.Notification.Builder(this, channelId)
      .setContentTitle("Chesz")
      .setContentText("Servicio de captura activo")
      .setSmallIcon(R.drawable.ic_check_green)
      .build()

    if (android.os.Build.VERSION.SDK_INT >= 34) {
      startForeground(1, notif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    } else {
      startForeground(1, notif)
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    runCatching { wm.removeViewImmediate(root) }
    runCatching { if (killShown) wm.removeViewImmediate(killRoot) }
    runCatching { activeMediaProjection?.stop() }
    activeMediaProjection = null
    mpData = null
    mpResultCode = null
    killShown = false
  }

  private fun overlayType(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    else
      @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
  }

  private fun createRootOverlay() {
    root = FrameLayout(this).apply {
      clipChildren = false
      clipToPadding = false
    }

    // Panel (Estado B) - dentro del root
    panelRoot = buildPanel().apply {
      visibility = View.GONE
    }
    root.addView(panelRoot) // primero: queda abajo

    // Botón (Estado A/B) - dentro del root (AL FINAL para que quede arriba)
    val btnPx = dp(60) // README: 60dp
    bubbleIcon = ImageView(this).apply {
      setImageResource(R.drawable.bubble_icon)
      scaleType = ImageView.ScaleType.FIT_XY
      adjustViewBounds = false
    }
    val bubbleWrap = FrameLayout(this).apply {
      addView(bubbleIcon, FrameLayout.LayoutParams(btnPx, btnPx))
      clipChildren = false
      clipToPadding = false
    }
    root.addView(bubbleWrap) // último child => encima del panel

    // Estado A inicial: root = tamaño botón
    rootLp = WindowManager.LayoutParams(
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.WRAP_CONTENT,
      overlayType(),
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      x = dp(60)
      y = dp(180)
    }

    // Posición interna Estado A
    setStateA_layout()

    // Touch sobre ROOT: arrastra todo; tap alterna panel
    root.setOnTouchListener { _, e ->
      when (e.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          
          dragging = false
          downRawX = e.rawX
          downRawY = e.rawY
          startX = rootLp.x
          startY = rootLp.y
          true
        }

        MotionEvent.ACTION_MOVE -> {
          val dx = (e.rawX - downRawX).toInt()
          val dy = (e.rawY - downRawY).toInt()

          if (!dragging && (abs(dx) + abs(dy) > dp(6))) {
            dragging = true
            showKill(true)
          }

          
val clamped = clampRootToScreen(startX + dx, startY + dy)
          val cx = clamped.first
          val cy = clamped.second
          rootLp.x = cx
          rootLp.y = cy
          root.requestLayout()

    runCatching { wm.updateViewLayout(root, rootLp) }


          if (dragging) {
            val over = isOverKillCenter(bubbleCenterX(), bubbleCenterY())
            if (over != killHovered) {
              killHovered = over
              setKillHover(over)
            }
          }
          true
        }

        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
          
          if (dragging) {
            val shouldKill = isOverKillCenter(bubbleCenterX(), bubbleCenterY())
            if (shouldKill) {
              performKill()
            } else {
              setKillHover(false)
              showKill(false)
            }
          } else {
            togglePanel()
          }
          dragging = false
          true
        }

        else -> false
      }
    }

    wm.addView(root, rootLp)
  }

  private fun togglePanel() {
    val hasPerm = (mpResultCode == android.app.Activity.RESULT_OK) && (mpData != null)
    if (!panelShown) {
      showPanelIfFits()
    }
    if (hasPerm) {
            if (panelTitle.text == "Sshot/") return@setOnTouchListener true // Bloqueo anti-spam
      takeScreenshotOnce()
    }
  }

  // === Estado A: solo botón ===
  private fun setStateA_layout() {
    val btnPx = dp(60)
    rootLp.width = btnPx
    rootLp.height = btnPx
    // Panel hidden
    panelRoot.visibility = View.GONE
    panelShown = false

    // Child positions:
    // panelRoot at 0,0 (no importa porque GONE)
    (panelRoot.layoutParams as? FrameLayout.LayoutParams)?.apply {
      leftMargin = 0; topMargin = 0
    } ?: run {
      panelRoot.layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.WRAP_CONTENT,
        FrameLayout.LayoutParams.WRAP_CONTENT
      )
    }

    // bubbleWrap is root child #1 (after panel), position 0,0
    val bubbleWrap = root.getChildAt(1)
    bubbleWrap.layoutParams = FrameLayout.LayoutParams(btnPx, btnPx).apply {
      leftMargin = 0
      topMargin = 0
    }

    
    // Clamp final: evita overflow al cambiar a Estado A
    val clampedA = clampRootToScreen(rootLp.x, rootLp.y)
    // rootLp.x = dp(60) // ELIMINADO: Evita brinco
    // rootLp.y = dp(180) // ELIMINADO: Mantiene posición

runCatching { wm.updateViewLayout(root, rootLp) }
  }

  // === Estado B: botón + panel ===
  private fun showPanelIfFits() {
    val dm = resources.displayMetrics
    val btnW = dp(60)
    val btnH = dp(60)
    val panelW = (dm.widthPixels * 0.55f).toInt()
    val panelH = (dm.heightPixels * 0.15f).toInt()

    // README root geometry (single overlay root):
    val rootX = rootLp.x
    val rootY = rootLp.y - (panelH - btnH)
    val rootW = panelW + (btnW / 2)
    val rootH = panelH
    // Bounds reales (misma lógica que clampRootToScreen)
    val (sw, sh) = this.sw to this.sh
    val minY = 0

    val bottomInset = bottomInsetCache

    val maxY = (sh - bottomInset).coerceAtLeast(minY)

    val fits = rootX >= 0 &&
      rootY >= minY &&
      (rootX + rootW) <= sw &&
      (rootY + rootH) <= maxY
    if (!fits) {
      flashBubbleRed()
      return
    }

    // Aplicar root pos/size
    rootLp.x = rootX
    rootLp.y = rootY
    rootLp.width = rootW
    rootLp.height = rootH

    
    // Clamp final (Estado B): con root ya redimensionado (panel incluido)
    val clampedB = clampRootToScreen(rootLp.x, rootLp.y)
    rootLp.x = clampedB.first
    rootLp.y = clampedB.second

// Layout interno:
    // panel at (btnW/2, 0)
    panelRoot.visibility = View.VISIBLE
    panelRoot.layoutParams = FrameLayout.LayoutParams(panelW, panelH).apply {
      leftMargin = (btnW / 2)
      topMargin = 0
    }

    // botón at (0, panelH - btnH)  (ancla inferior izq)
    val bubbleWrap = root.getChildAt(1)
    bubbleWrap.layoutParams = FrameLayout.LayoutParams(btnW, btnH).apply {
      leftMargin = 0
      topMargin = (panelH - btnH)
    }

    panelShown = true

    updatePermUi()

    root.requestLayout()
    runCatching { wm.updateViewLayout(root, rootLp) }
  }

  private fun hidePanel() {
    if (panelShown) {
      val dm = resources.displayMetrics
      val btnH = dp(60)
      val panelH = (dm.heightPixels * 0.15f).toInt()
      // Compensar: mover el root hacia abajo lo que el panel ocupaba arriba del botón
      rootLp.y = rootLp.y + (panelH - btnH)
    }
    setStateA_layout()
  }

  private fun buildPanel(): FrameLayout {
    val panel = FrameLayout(this).apply {
      setBackgroundColor(0xCC000000.toInt())
      alpha = 1f
      clipChildren = false
      clipToPadding = false
    }

    val col = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(10), dp(0), dp(10), dp(0))
    }

    fun mkLine(t: String): TextView = TextView(this).apply {
      text = t
      setTextColor(0xFFB0B0B0.toInt())
      textSize = 12f
    }

    val title = mkLine("") // placeholder status
      title.textSize = 11f
      title.includeFontPadding = false
      title.maxLines = 1
      title.ellipsize = android.text.TextUtils.TruncateAt.END
      title.setPadding(0, 0, 0, 0)
      title.gravity = android.view.Gravity.CENTER_HORIZONTAL
    col.addView(title)



    // ===== Barra permiso captura (manual) =====
    panelTitle = title

    // Botón principal: 50dp alto, blanco, texto grande + ✓ verde
    permText = TextView(this).apply {
      text = "Pedir Permiso"
      setTextColor(0xFF000000.toInt())
      textSize = 13f
      includeFontPadding = false
      maxLines = 1
      ellipsize = android.text.TextUtils.TruncateAt.END
    }

    val permIcon = ImageView(this).apply {
      setImageResource(R.drawable.ic_check_green)
    }

    val permRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = android.view.Gravity.CENTER
      addView(permText, LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      ))
      addView(permIcon, LinearLayout.LayoutParams(dp(22), dp(22)).apply {
        leftMargin = dp(10)
      })
    }

    val bg = android.graphics.drawable.GradientDrawable().apply {
      shape = android.graphics.drawable.GradientDrawable.RECTANGLE
      cornerRadius = dp(12).toFloat()
      setColor(0xFFFFFFFF.toInt())
    }

    permBar = FrameLayout(this).apply {
      background = bg
      setOnClickListener { requestCapturePermission() }
      setPadding(dp(16), 0, dp(16), 0)
    }

    permBar.addView(
      permRow,
      FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
      ).apply { gravity = android.view.Gravity.CENTER }
    )

    col.addView(
      permBar,
      LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        dp(50)
      ).apply {
        topMargin = dp(8)
      }
    )

// Close: pegado abajo + icono centrado + RECORTE REAL L/R (6dp)
      val closeH = dp(28) // barra delgada


      val closeW = (resources.displayMetrics.widthPixels * 0.30f).toInt()
      val close = ImageView(this).apply {
        setImageResource(R.drawable.close)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        adjustViewBounds = true
        setPadding(dp(4), dp(2), dp(4), dp(2))
        setOnClickListener { hidePanel() }
      }

      // Empuja el Close al fondo del panel (LinearLayout vertical)
      col.addView(
        View(this),
        LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT,
          0,
          1f
        )
      )
      // Contenedor del Close: RECORTE REAL con márgenes L/R (6dp) y centrado
      val closeBar = FrameLayout(this)

      val closeBg = FrameLayout(this).apply {
        setBackgroundColor(0x66000000) // fondo del botón aquí (no en el ImageView)
      }

      closeBg.addView(
        close,
        FrameLayout.LayoutParams(
          FrameLayout.LayoutParams.MATCH_PARENT,
          FrameLayout.LayoutParams.MATCH_PARENT
        )
      )

      closeBar.addView(
        closeBg,
        FrameLayout.LayoutParams(
          FrameLayout.LayoutParams.MATCH_PARENT,
          closeH
        )
      )

      col.addView(
        closeBar,
        LinearLayout.LayoutParams(
          closeW,
          closeH
        ).apply {
          gravity = android.view.Gravity.CENTER_HORIZONTAL
        }
      )
panel.addView(
      col,
      FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
      )
    )
    return panel
  }

  private fun space(h: Int): View =
    View(this).apply { layoutParams = FrameLayout.LayoutParams(1, h) }

  private fun flashBubbleRed() {
    runCatching {
      bubbleIcon.setColorFilter(0xFFFF3333.toInt())
      bubbleIcon.postDelayed({ runCatching { bubbleIcon.clearColorFilter() } }, 220)
    }
  }

  private fun requestCapturePermission() {
    hidePanel()
    val pi = Intent(this, com.chesz.CapturePermissionActivity::class.java).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(pi)
  }


  private fun upgradeToMediaProjection() {
    val channelId = "chesz_channel"
    val notif = android.app.Notification.Builder(this, channelId)
      .setContentTitle("Chesz")
      .setContentText("Captura de pantalla habilitada")
      .setSmallIcon(R.drawable.ic_check_green)
      .build()
    if (android.os.Build.VERSION.SDK_INT >= 29) {
      startForeground(1, notif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }
  }

  private fun updatePermUi() {
    if (!this::permBar.isInitialized) return
    val ok = (mpResultCode == Activity.RESULT_OK) && (mpData != null)
    permBar.visibility = if (mpData != null) View.GONE else View.VISIBLE
    panelTitle.text = ""
  }

// ===================== KILL AREA (igual) =====================

  private fun createKillArea() {
    updateScreenCache()
    killRoot = FrameLayout(this).apply {
      alpha = 1f
      visibility = View.VISIBLE
      clipChildren = false
      clipToPadding = false
      background = null
      setBackgroundColor(0x00000000)
    }

    val sizePx = dp(100)

    killCircle = FrameLayout(this).apply {
      background = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.OVAL
        setColor(0xCCFF0000.toInt())
      }
      clipToOutline = true
      outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
          outline.setOval(0, 0, view.width, view.height)
        }
      }
      scaleX = 1f
      scaleY = 1f
    }

    val xIcon = ImageView(this).apply {
      setImageResource(android.R.drawable.ic_delete)
      setColorFilter(0xFFFFFFFF.toInt())
      scaleType = ImageView.ScaleType.FIT_XY
    }

    killRoot.addView(killCircle, FrameLayout.LayoutParams(sizePx, sizePx, Gravity.CENTER))
    killCircle.addView(xIcon, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER))

    killLp = WindowManager.LayoutParams(
      sizePx,
      sizePx,
      overlayType(),
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
      x = 0 // CORREGIDO: Centro matemático
      y = dp(40)
    }
  }

  private fun showKill(show: Boolean) {
    if (show) {
      if (!killShown) {
        runCatching { wm.addView(killRoot, killLp) }
        killShown = true
      }
    } else {
      if (!killShown) return
      runCatching { wm.removeViewImmediate(killRoot) }
      killShown = false
    }
  }

  private fun setKillHover(hover: Boolean) {
    val target = if (hover) 1.40f else 1.0f
    killCircle.animate().cancel()
    killCircle.animate()
      .scaleX(target)
      .scaleY(target)
      .setDuration(60)
      .withLayer()
      .start()
  }

  private fun bubbleCenterX(): Float {
    val loc = IntArray(2)
    root.getLocationOnScreen(loc)
    val w = dp(60)
    // bubble in Estado B está en left=0; en A también.
    return loc[0] + (w / 2f)
  }

  private fun bubbleCenterY(): Float {
    val loc = IntArray(2)
    root.getLocationOnScreen(loc)
    val w = dp(60)
    val dm = resources.displayMetrics
    val panelH = (dm.heightPixels * 0.15f).toInt()
    val topInRoot = if (panelShown) (panelH - w) else 0
    return loc[1] + topInRoot + (w / 2f)
  }

  private fun isOverKillCenter(x: Float, y: Float): Boolean {
    if (!killShown) return false
    val loc = IntArray(2)
    killRoot.getLocationOnScreen(loc)
    val left = loc[0]
    val top = loc[1]
    val right = left + killRoot.width
    val bottom = top + killRoot.height
    val pad = dp(18)
    return (x >= (left - pad) && x <= (right + pad) &&
      y >= (top - pad) && y <= (bottom + pad))
  }

  private fun performKill() {
    runCatching { wm.removeViewImmediate(root) }
    runCatching { if (killShown) wm.removeViewImmediate(killRoot) }
    killShown = false
    stopSelf()
  }

  private fun dp(v: Int): Int {
    val d = resources.displayMetrics.density
    return (v * d).toInt()
  }

  // ===== Helpers: límites reales de pantalla + clamp del overlay root =====
  private fun screenRealSize(): kotlin.Pair<Int, Int> {
    return if (android.os.Build.VERSION.SDK_INT >= 30) {
      // Para overlays: máximo = área real completa (incluye system bars)
      val b = wm.maximumWindowMetrics.bounds
      b.width().coerceAtLeast(1) to b.height().coerceAtLeast(1)
    } else {
      val pt = android.graphics.Point()
      @Suppress("DEPRECATION")
      wm.defaultDisplay.getRealSize(pt)
      pt.x.coerceAtLeast(1) to pt.y.coerceAtLeast(1)
    }
  }

  private fun clampRootToScreen(x: Int, y: Int): kotlin.Pair<Int, Int> {
    val (sw, sh) = this.sw to this.sh
    val w = if (rootLp.width > 0) rootLp.width else dp(60)
    val h = if (rootLp.height > 0) rootLp.height else dp(60)

    val maxX = (sw - w).coerceAtLeast(0)

    // Tu regla: el TOP real es 0 (no respetar status bar/cutout como límite)
    val minY = 0

    // Para no salir por abajo: restamos navegación (API30+)
    val bottomInset = bottomInsetCache

    val maxY = (sh - h - bottomInset).coerceAtLeast(minY)

    return x.coerceIn(0, maxX) to y.coerceIn(minY, maxY)
  }


  private fun updateScreenCache() {
    val size = screenRealSize()
    sw = size.first
    sh = size.second
    if (android.os.Build.VERSION.SDK_INT >= 30) {
        val metrics = wm.maximumWindowMetrics
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
            android.view.WindowInsets.Type.navigationBars()
        )
        bottomInsetCache = insets.bottom
    }
  }

  private var activeMediaProjection: android.media.projection.MediaProjection? = null

  private fun takeScreenshotOnce() {
    val rc = mpResultCode ?: return
    panelTitle.text = "Sshot/"

        val internalFile = java.io.File(getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "chesz_last.png")
        if (internalFile.exists()) {
            panelTitle.text = "Exist: ${internalFile.length() / 1024}KB"
        } else {
            panelTitle.text = "Exist: NO"
        }
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

    mp.registerCallback(object : android.media.projection.MediaProjection.Callback() {}, null)
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
          val dir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
          if (dir != null) {
            if (!dir.exists()) dir.mkdirs()
          if (dir != null && !dir.exists()) dir.mkdirs()
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
        image.close()
        runCatching { vd.release() }
        runCatching { reader.close() }
        // Se extirpó el mp.stop() para no quemar el permiso
      }
    }, 200)
  }
}
