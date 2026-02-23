package com.chesz.floating

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import com.chesz.R
import kotlin.math.abs
import kotlin.math.hypot
import android.graphics.Outline
import android.view.ViewOutlineProvider
import android.graphics.Color
import android.widget.TextView

class BubbleService : Service() {

  private lateinit var wm: WindowManager

  // Bubble
  private lateinit var bubbleRoot: FrameLayout
  private lateinit var bubbleIcon: ImageView
  private lateinit var bubbleLp: WindowManager.LayoutParams


  // Panel (Estado B)
  private lateinit var panelRoot: FrameLayout
  private lateinit var panelLp: WindowManager.LayoutParams
  private var panelShown = false

  // Kill area
  private lateinit var killRoot: FrameLayout
  private lateinit var killCircle: FrameLayout
  private lateinit var killLp: WindowManager.LayoutParams
  private var killShown = false
  private var killHovered = false
  private var killHover = false

  // Drag state
  private var downRawX = 0f
  private var downRawY = 0f
  private var startX = 0
  private var startY = 0
  private var dragging = false

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    wm = getSystemService(WINDOW_SERVICE) as WindowManager
    createBubble()
    createKillArea()
      createPanel()
  }

  override fun onDestroy() {
    super.onDestroy()
    runCatching { wm.removeViewImmediate(bubbleRoot) }
    runCatching { if (killShown) wm.removeViewImmediate(killRoot) }
    killShown = false
  }

  private fun overlayType(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    else
      @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
  }

  private fun createBubble() {
    bubbleRoot = FrameLayout(this)
    bubbleIcon = ImageView(this).apply {
      setImageResource(R.drawable.bubble_icon)
      scaleType = ImageView.ScaleType.CENTER_CROP
      adjustViewBounds = true
    }

    val sizePx = dp(60)
    bubbleRoot.addView(
      bubbleIcon,
      FrameLayout.LayoutParams(sizePx, sizePx)
    )

    bubbleLp = WindowManager.LayoutParams(
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

    bubbleRoot.setOnTouchListener { _, e ->
      when (e.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          dragging = false
          downRawX = e.rawX
          downRawY = e.rawY
          startX = bubbleLp.x
          startY = bubbleLp.y
          true
        }

        MotionEvent.ACTION_MOVE -> {
          val dx = (e.rawX - downRawX).toInt()
          val dy = (e.rawY - downRawY).toInt()

          // umbral: aquí nace el DRAG real → recién ahí mostramos kill
          if (!dragging && (abs(dx) + abs(dy) > dp(6))) {
            dragging = true
            showKill(true)
          }

          bubbleLp.x = startX + dx
          bubbleLp.y = startY + dy
          runCatching { wm.updateViewLayout(bubbleRoot, bubbleLp) }

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
          }
          dragging = false
          true
        }

        else -> false
      }
    }

    wm.addView(bubbleRoot, bubbleLp)
  }

  // ---------- Kill Area (standard: bottom center) ----------

  // ---------- Panel (Estado B) ----------
  private fun createPanel() {
    // Panel simple (negro translúcido + textos grises + botón Close)
    panelRoot = FrameLayout(this).apply {
      setBackgroundColor(0xCC000000.toInt())
      alpha = 1f
    }

    val content = FrameLayout(this).apply {
      setPadding(dp(10), dp(10), dp(10), dp(10))
    }

    fun mkLine(t: String): TextView = TextView(this).apply {
      text = t
      setTextColor(0xFFB0B0B0.toInt())
      textSize = 12f
    }

    // Texto de prueba del README (lo dejamos fijo por ahora)
    val t1 = mkLine("Apertura italiana boca")
    val t2 = mkLine("Defensa nórdica Ase 12 100%")
    val t3 = mkLine("Defensa Nápoles variante coaboanca termux ase 10 90%")
    val t4 = mkLine("Defensa fuck becerro asesino papaya sangrienta 80%")

    // Close minimalista (vuelve a Estado A)
    val close = TextView(this).apply {
      text = "Close"
      gravity = Gravity.CENTER
      setTextColor(Color.WHITE)
      textSize = 12f
      setBackgroundColor(0x66000000)
      setPadding(0, dp(6), 0, dp(6))
      setOnClickListener { hidePanel() }
    }

    // Layout vertical simple (sin XML)
    val col = android.widget.LinearLayout(this).apply {
      orientation = android.widget.LinearLayout.VERTICAL
    }
    col.addView(t1)
    col.addView(space(dp(6)))
    col.addView(t2)
    col.addView(space(dp(6)))
    col.addView(t3)
    col.addView(space(dp(6)))
    col.addView(t4)
    col.addView(space(dp(10)))
    col.addView(close, android.widget.LinearLayout.LayoutParams(
      android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
      android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
    ))

    content.addView(col)
    panelRoot.addView(content)

    // size: 60% x 25% de pantalla
    val dm = resources.displayMetrics
    val panelW = (dm.widthPixels * 0.60f).toInt()
    val panelH = (dm.heightPixels * 0.25f).toInt()

    panelLp = WindowManager.LayoutParams(
      panelW,
      panelH,
      overlayType(),
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      x = 0
      y = 0
    }
  }

  private fun space(h: Int): View =
    View(this).apply { layoutParams = FrameLayout.LayoutParams(1, h) }

  private fun togglePanel() {
    if (panelShown) hidePanel() else showPanelIfFits()
  }

  private fun showPanelIfFits() {
    // Calcula geometría según README:
    // rootX = buttonX
    // rootY = buttonY - (panelH - buttonH)
    // rootW = panelW + (buttonW/2)
    // rootH = panelH
    val dm = resources.displayMetrics
    val panelW = panelLp.width
    val panelH = panelLp.height
    val btnW = if (bubbleRoot.width > 0) bubbleRoot.width else dp(60)
    val btnH = if (bubbleRoot.height > 0) bubbleRoot.height else dp(60)

    val rootX = bubbleLp.x
    val rootY = bubbleLp.y - (panelH - btnH)
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

    // Posición del panel: a la derecha, solapando 50% del botón
    panelLp.x = bubbleLp.x + (btnW / 2)
    panelLp.y = bubbleLp.y - (panelH - btnH)

    if (!panelShown) {
      runCatching { wm.addView(panelRoot, panelLp) }
      panelShown = true
    } else {
      runCatching { wm.updateViewLayout(panelRoot, panelLp) }
    }
  }

  private fun hidePanel() {
    if (!panelShown) return
    runCatching { wm.removeViewImmediate(panelRoot) }
    panelShown = false
  }

  private fun updatePanelPositionIfShown() {
    if (!panelShown) return
    // Solo reposiciona con la misma regla (sin revalidar "fits")
    val panelH = panelLp.height
    val btnW = if (bubbleRoot.width > 0) bubbleRoot.width else dp(60)
    val btnH = if (bubbleRoot.height > 0) bubbleRoot.height else dp(60)
    panelLp.x = bubbleLp.x + (btnW / 2)
    panelLp.y = bubbleLp.y - (panelH - btnH)
    runCatching { wm.updateViewLayout(panelRoot, panelLp) }
  }

  private fun flashBubbleRed() {
    // feedback simple: tinte rojo 220ms
    runCatching {
      bubbleIcon.setColorFilter(0xFFFF3333.toInt())
      bubbleIcon.postDelayed({ runCatching { bubbleIcon.clearColorFilter() } }, 220)
    }
  }

  private fun createKillArea() {
    // Contenedor (NO se escala)
    killRoot = FrameLayout(this).apply {
      alpha = 1f
      visibility = View.VISIBLE
      clipChildren = false
      clipToPadding = false
      background = null
      setBackgroundColor(0x00000000) // transparente
    }
  
    val sizePx = dp(100)
  
    // Círculo real (ESTE se escala) + outline oval para que JAMÁS sea cuadrado
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
  
    killRoot.addView(
      killCircle,
      FrameLayout.LayoutParams(sizePx, sizePx, Gravity.CENTER)
    )
    killCircle.addView(
      xIcon,
      FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER)
    )
  
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
    // Importante: animación rápida y consistente (sin “deformarse”)
    killCircle.animate().cancel()
    killCircle.scaleX = killCircle.scaleX // no-op (mantiene estado)
    killCircle.scaleY = killCircle.scaleY
    killCircle.animate()
      .scaleX(target)
      .scaleY(target)
      .setDuration(60)
      .withLayer()
      .start()
  }



  private fun bubbleCenterX(): Float {
      val loc = IntArray(2)
      bubbleRoot.getLocationOnScreen(loc)
      val w = if (bubbleRoot.width > 0) bubbleRoot.width else dp(60)
      return loc[0] + (w / 2f)
    }

    private fun bubbleCenterY(): Float {
      val loc = IntArray(2)
      bubbleRoot.getLocationOnScreen(loc)
      val h = if (bubbleRoot.height > 0) bubbleRoot.height else dp(60)
      return loc[1] + (h / 2f)
    }

  private fun killCenterOnScreen(): Pair<Float, Float> {
    val size = Point()
    @Suppress("DEPRECATION")
    wm.defaultDisplay.getSize(size)

    val cx = size.x / 2f
    val cy = size.y - killLp.y - (killLp.height / 2f)
    return cx to cy
  }

  private fun isOverKillCenter(x: Float, y: Float): Boolean {
      if (!killShown) return false

      val loc = IntArray(2)
      killRoot.getLocationOnScreen(loc)
      val left = loc[0]
      val top = loc[1]
      val right = left + killRoot.width
      val bottom = top + killRoot.height

      // margen extra para que "encima" sea fácil de activar (tuneable)
      val pad = dp(18)

      return (x >= (left - pad) && x <= (right + pad) &&
              y >= (top - pad) && y <= (bottom + pad))
    }

  private fun performKill() {
    // cierre determinista (sin depender de animaciones)
    runCatching { wm.removeViewImmediate(bubbleRoot) }
    runCatching { if (killShown) wm.removeViewImmediate(killRoot) }
    killShown = false
    stopSelf()
  }

  private fun dp(v: Int): Int {
    val d = resources.displayMetrics.density
    return (v * d).toInt()
  }
}