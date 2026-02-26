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

class BubbleService : Service() {

  private lateinit var wm: WindowManager

  // === SINGLE ROOT OVERLAY (botón + panel) ===
  private lateinit var root: FrameLayout
  private lateinit var rootLp: WindowManager.LayoutParams

  private lateinit var bubbleIcon: ImageView
  private lateinit var panelRoot: FrameLayout

  private var panelShown = false

  // Drag state (sobre el ROOT)
  private var downRawX = 0f
  private var downRawY = 0f
  private var startX = 0
  private var startY = 0
  private var dragging = false

  // Kill area (se mantiene como overlay separado)
  private lateinit var killRoot: FrameLayout
  private lateinit var killCircle: FrameLayout
  private lateinit var killLp: WindowManager.LayoutParams
  private var killShown = false
  private var killHovered = false

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    wm = getSystemService(WINDOW_SERVICE) as WindowManager
    createRootOverlay()
    createKillArea()
  }

  override fun onDestroy() {
    super.onDestroy()
    runCatching { wm.removeViewImmediate(root) }
    runCatching { if (killShown) wm.removeViewImmediate(killRoot) }
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
    val btnPx = dp(80) // README: 80dp
    bubbleIcon = ImageView(this).apply {
      setImageResource(R.drawable.bubble_icon)
      scaleType = ImageView.ScaleType.CENTER_CROP
      adjustViewBounds = true
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
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      x = 0
      y = dp(120)
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

          rootLp.x = startX + dx
          rootLp.y = startY + dy
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
    if (panelShown) {
      hidePanel()
    } else {
      showPanelIfFits()
    }
  }

  // === Estado A: solo botón ===
  private fun setStateA_layout() {
    val btnPx = dp(80)
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

    runCatching { wm.updateViewLayout(root, rootLp) }
  }

  // === Estado B: botón + panel ===
  private fun showPanelIfFits() {
    val dm = resources.displayMetrics
    val btnW = dp(80)
    val btnH = dp(80)
    val panelW = (dm.widthPixels * 0.60f).toInt()
    val panelH = (dm.heightPixels * 0.25f).toInt()

    // README root geometry (single overlay root):
    val rootX = rootLp.x
    val rootY = rootLp.y - (panelH - btnH)
    val rootW = panelW + (btnW / 2)
    val rootH = panelH

    val fits = rootX >= 0 &&
      rootY >= 0 &&
      (rootX + rootW) <= dm.widthPixels &&
      (rootY + rootH) <= dm.heightPixels

    if (!fits) {
      flashBubbleRed()
      return
    }

    // Aplicar root pos/size
    rootLp.x = rootX
    rootLp.y = rootY
    rootLp.width = rootW
    rootLp.height = rootH

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
    runCatching { wm.updateViewLayout(root, rootLp) }
  }

  private fun hidePanel() {
    if (panelShown) {
      val dm = resources.displayMetrics
      val btnH = dp(80)
      val panelH = (dm.heightPixels * 0.25f).toInt()
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
      setPadding(dp(10), dp(10), dp(10), dp(10))
    }

    fun mkLine(t: String): TextView = TextView(this).apply {
      text = t
      setTextColor(0xFFB0B0B0.toInt())
      textSize = 12f
    }

    val title = mkLine("Sshot/Fen/Ai/Done") // placeholder status
    col.addView(title)

    col.addView(space(dp(8)))

    col.addView(mkLine("Apertura italiana boca"))
    col.addView(space(dp(6)))
    col.addView(mkLine("Defensa nórdica Ase 12 100%"))
    col.addView(space(dp(6)))
    col.addView(mkLine("Defensa Nápoles variante coaboanca termux ase 10 90%"))
    col.addView(space(dp(6)))
    col.addView(mkLine("Defensa fuck becerro asesino papaya sangrienta 80%"))

    col.addView(space(dp(10)))

    val close = TextView(this).apply {
      text = "Close"
      gravity = Gravity.CENTER
      setTextColor(Color.WHITE)
      textSize = 12f
      setBackgroundColor(0x66000000)
      setPadding(0, dp(6), 0, dp(6))
      setOnClickListener { hidePanel() }
    }
    col.addView(
      close,
      LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
      )
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

  // ===================== KILL AREA (igual) =====================

  private fun createKillArea() {
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
      scaleType = ImageView.ScaleType.CENTER_INSIDE
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
      x = 0
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
    val w = dp(80)
    // bubble in Estado B está en left=0; en A también.
    return loc[0] + (w / 2f)
  }

  private fun bubbleCenterY(): Float {
    val loc = IntArray(2)
    root.getLocationOnScreen(loc)
    val w = dp(80)
    val dm = resources.displayMetrics
    val panelH = (dm.heightPixels * 0.25f).toInt()
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
}
