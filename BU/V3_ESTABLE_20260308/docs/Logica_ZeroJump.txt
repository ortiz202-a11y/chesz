package com.chesz.floating

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
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

  private lateinit var root: FrameLayout
  private lateinit var rootLp: WindowManager.LayoutParams

  private lateinit var bubbleIcon: ImageView
  private lateinit var panelRoot: FrameLayout

  private var panelShown = false

  // Drag state
  private var downRawX = 0f
  private var downRawY = 0f
  private var startX = 0
  private var startY = 0
  private var dragging = false

  // Kill area
  private lateinit var killRoot: FrameLayout
  private lateinit var killCircle: FrameLayout
  private lateinit var killLp: WindowManager.LayoutParams
  private var killShown = false
  private var killHovered = false

  // Guardamos la posición del BOTÓN siempre
  private var bubbleX = 0
  private var bubbleY = 0

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    wm = getSystemService(WINDOW_SERVICE) as WindowManager
    bubbleY = dp(120)
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
    val btnPx = dp(80)

    root = FrameLayout(this).apply {
      clipChildren = false
      clipToPadding = false
    }

    // Panel (inicialmente GONE)
    panelRoot = buildPanel().apply {
      visibility = View.GONE
    }
    root.addView(panelRoot)

    // Botón
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
    root.addView(bubbleWrap)

    // Root siempre del tamaño del botón en Estado A
    rootLp = WindowManager.LayoutParams(
      btnPx,
      btnPx,
      overlayType(),
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      x = bubbleX
      y = bubbleY
    }

    // Posición interna Estado A
    applyStateA()

    // Touch
    root.setOnTouchListener { _, e ->
      when (e.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          dragging = false
          downRawX = e.rawX
          downRawY = e.rawY
          startX = bubbleX
          startY = bubbleY
          true
        }

        MotionEvent.ACTION_MOVE -> {
          val dx = (e.rawX - downRawX).toInt()
          val dy = (e.rawY - downRawY).toInt()

          if (!dragging && (abs(dx) + abs(dy) > dp(6))) {
            dragging = true
            showKill(true)
          }

          bubbleX = startX + dx
          bubbleY = startY + dy
          applyCurrentLayout()

          if (dragging) {
            val over = isOverKillCenter(
              bubbleX + dp(80) / 2f,
              bubbleY + dp(80) / 2f
            )
            if (over != killHovered) {
              killHovered = over
              setKillHover(over)
            }
          }
          true
        }

        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
          if (dragging) {
            val shouldKill = isOverKillCenter(
              bubbleX + dp(80) / 2f,
              bubbleY + dp(80) / 2f
            )
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
      showPanel()
    }
  }

  // ================================================================
  //  CLAVE: rootLp.x/y SIEMPRE = posición del botón
  //  El panel crece hacia arriba con topMargin negativo
  //  FLAG_LAYOUT_NO_LIMITS permite dibujar fuera del root
  // ================================================================

  private fun applyStateA() {
    val btnPx = dp(80)
    panelRoot.visibility = View.GONE
    panelShown = false

    val bubbleWrap = root.getChildAt(1)
    bubbleWrap.layoutParams = FrameLayout.LayoutParams(btnPx, btnPx).apply {
      leftMargin = 0
      topMargin = 0
    }

    rootLp.x = bubbleX
    rootLp.y = bubbleY
    rootLp.width = btnPx
    rootLp.height = btnPx
    runCatching { wm.updateViewLayout(root, rootLp) }
  }

  private fun showPanel() {
    val dm = resources.displayMetrics
    val btnW = dp(80)
    val btnH = dp(80)
    val panelW = (dm.widthPixels * 0.60f).toInt()
    val panelH = (dm.heightPixels * 0.25f).toInt()

    // Verificar que el panel cabe en pantalla
    val panelLeft = bubbleX + (btnW / 2)
    val panelTop = bubbleY - (panelH - btnH)

    val fits = panelLeft >= 0 &&
      panelTop >= 0 &&
      (panelLeft + panelW) <= dm.widthPixels &&
      (bubbleY + btnH) <= dm.heightPixels

    if (!fits) {
      flashBubbleRed()
      return
    }

    // Root sigue en la posición del botón, mismo tamaño del botón
    rootLp.x = bubbleX
    rootLp.y = bubbleY
    rootLp.width = btnW
    rootLp.height = btnH

    // Botón: siempre en (0,0) del root — NO SE MUEVE
    val bubbleWrap = root.getChildAt(1)
    bubbleWrap.layoutParams = FrameLayout.LayoutParams(btnW, btnH).apply {
      leftMargin = 0
      topMargin = 0
    }

    // Panel: crece hacia arriba y a la derecha FUERA del root
    // topMargin negativo = sube por encima del root
    panelRoot.visibility = View.VISIBLE
    panelRoot.layoutParams = FrameLayout.LayoutParams(panelW, panelH).apply {
      leftMargin = (btnW / 2)
      topMargin = -(panelH - btnH)
    }

    panelShown = true
    runCatching { wm.updateViewLayout(root, rootLp) }
  }

  private fun hidePanel() {
    // bubbleX/bubbleY no cambian — cero brinco
    applyStateA()
  }

  private fun applyCurrentLayout() {
    rootLp.x = bubbleX
    rootLp.y = bubbleY
    runCatching { wm.updateViewLayout(root, rootLp) }
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

    val title = mkLine("Sshot/Fen/Ai/Done")
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

  // ===================== KILL AREA =====================

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
