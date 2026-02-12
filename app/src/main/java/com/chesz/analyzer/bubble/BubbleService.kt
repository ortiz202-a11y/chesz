package com.chesz.analyzer.bubble

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.hypot
import com.chesz.analyzer.R

class BubbleService : Service() {

  private lateinit var wm: WindowManager
  private var bubbleView: View? = null
  private var closeView: View? = null
  private var panelView: View? = null

  private lateinit var bubbleLp: WindowManager.LayoutParams
  private lateinit var closeLp: WindowManager.LayoutParams
  private lateinit var panelLp: WindowManager.LayoutParams

  private var downRawX = 0f
  private var downRawY = 0f
  private var downX = 0
  private var downY = 0
  private var moved = false
  private var downTime = 0L

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (!Settings.canDrawOverlays(this)) {
      shutdown()
      return START_NOT_STICKY
    }

    wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager

    if (bubbleView == null) {
      createCloseZone()
      createBubble()
    }

    return START_NOT_STICKY
  }

  override fun onDestroy() {
    super.onDestroy()
    removeViews()
  }

  private fun shutdown() {
    removeViews()
    stopSelf()
  }

  private fun removeViews() {
    if (!::wm.isInitialized) return
    try { panelView?.let { wm.removeViewImmediate(it) } } catch (_: Throwable) {}
    try { bubbleView?.let { wm.removeViewImmediate(it) } } catch (_: Throwable) {}
    try { closeView?.let { wm.removeViewImmediate(it) } } catch (_: Throwable) {}
    panelView = null
    bubbleView = null
    closeView = null
  }

  private fun windowType(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    else
      @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
  }

  private fun baseFlags(): Int {
    return (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
  }

  // =========================
  // CLOSE ZONE (X)
  // =========================
  private fun createCloseZone() {
    val size = dp(96)

    val root = FrameLayout(this).apply {
      visibility = View.GONE
      setBackgroundColor(0x00000000)

      addView(FrameLayout(this@BubbleService).apply {
        setBackgroundColor(0xCC000000.toInt()) // fondo oscuro
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
          override fun getOutline(view: View, outline: Outline) {
            outline.setOval(0, 0, view.width, view.height)
          }
        }

        addView(TextView(this@BubbleService).apply {
          text = "X"
          textSize = 28f
          setTextColor(0xFFFFFFFF.toInt())
          gravity = Gravity.CENTER
          layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
          )
        })
      }, FrameLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER })
    }

    closeLp = WindowManager.LayoutParams(
      size,
      size,
      windowType(),
      baseFlags(),
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
      y = dp(28)
    }

    closeView = root
    wm.addView(root, closeLp)
  }

  private fun showClose(show: Boolean) {
    closeView?.visibility = if (show) View.VISIBLE else View.GONE
  }

  // hit circular REAL (no rectángulo)
  private fun isInsideCloseCircle(rawX: Float, rawY: Float): Boolean {
    val close = closeView ?: return false
    if (close.visibility != View.VISIBLE) return false

    val loc = IntArray(2)
    close.getLocationOnScreen(loc)
    val cx = loc[0] + close.width / 2f
    val cy = loc[1] + close.height / 2f
    val r = close.width.coerceAtMost(close.height) / 2f

    return hypot(rawX - cx, rawY - cy) <= r
  }

  // =========================
  // PANEL (burbuja desplegada)
  // =========================
  private fun isPanelOpen(): Boolean = panelView != null

  private fun openPanel() {
    if (panelView != null) return

    val w = dp(280)
    val h = dp(360)

    val root = FrameLayout(this).apply {
      // tarjeta blanca redondeada
      background = RoundRectDrawable(0xFFFFFFFF.toInt(), dp(28).toFloat())
      setPadding(dp(18), dp(18), dp(18), dp(18))

      addView(TextView(this@BubbleService).apply {
        text = "Sshot/"
        textSize = 18f
        setTextColor(0xFF111111.toInt())
      })
    }

    // Panel recibe ACTION_OUTSIDE para cerrar al tocar pantalla
    root.setOnTouchListener { _, ev ->
      if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
        closePanel()
        true
      } else {
        // tocar dentro no hace nada (consume para que no “pase” raro)
        true
      }
    }

    panelLp = WindowManager.LayoutParams(
      w,
      h,
      windowType(),
      baseFlags() or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.CENTER
    }

    panelView = root
    wm.addView(root, panelLp)
  }

  private fun closePanel() {
    val p = panelView ?: return
    try { wm.removeViewImmediate(p) } catch (_: Throwable) {}
    panelView = null
  }

  // =========================
  // BUBBLE
  // =========================
  private fun createBubble() {
    val iconView = CircleCropView(this).apply {
      setImageResource(R.drawable.bubble_icon)
    }

    val container = FrameLayout(this).apply {
      setBackgroundColor(0x00000000)
      elevation = 0f
      addView(iconView, FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
      ))
    }

    bubbleLp = WindowManager.LayoutParams(
      dp(80),
      dp(80),
      windowType(),
      baseFlags(),
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      x = dp(16)
      y = dp(220)
    }

    container.setOnTouchListener { v, ev ->
      when (ev.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          downRawX = ev.rawX
          downRawY = ev.rawY
          downX = bubbleLp.x
          downY = bubbleLp.y
          moved = false
          downTime = System.currentTimeMillis()
          showClose(false)
          true
        }

        MotionEvent.ACTION_MOVE -> {
          val dx = (ev.rawX - downRawX).toInt()
          val dy = (ev.rawY - downRawY).toInt()

          if (!moved && (abs(dx) > dp(4) || abs(dy) > dp(4))) {
            moved = true
            showClose(true)
          }

          bubbleLp.x = downX + dx
          bubbleLp.y = downY + dy
          wm.updateViewLayout(v, bubbleLp)
          true
        }

        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
          val elapsed = System.currentTimeMillis() - downTime
          val isTap = (!moved && elapsed < 250)

          if (moved && isInsideCloseCircle(ev.rawX, ev.rawY)) {
            // ✅ SOLO AQUÍ se mata el servicio completo
            shutdown()
          } else if (isTap) {
            // ✅ TAP = abre/cierra panel (NO mata servicio)
            if (isPanelOpen()) closePanel() else openPanel()
            showClose(false)
          } else {
            showClose(false)
          }
          true
        }

        else -> false
      }
    }

    bubbleView = container
    wm.addView(container, bubbleLp)
  }

  private fun dp(v: Int): Int {
    val d = resources.displayMetrics.density
    return (v * d).toInt()
  }

  // =========================
  // CircleCrop real (shader)
  // =========================
  private class CircleCropView(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shader: BitmapShader? = null
    private var bitmap: Bitmap? = null

    fun setImageResource(resId: Int) {
      val d = context.resources.getDrawable(resId, context.theme)
      bitmap = drawableToBitmap(d)
      rebuildShader()
      invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
      super.onSizeChanged(w, h, oldw, oldh)
      rebuildShader()
    }

    private fun rebuildShader() {
      val b = bitmap ?: return
      if (width <= 0 || height <= 0) return

      shader = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

      val m = Matrix()
      val scale = maxOf(width.toFloat() / b.width, height.toFloat() / b.height)
      val dx = (width - b.width * scale) * 0.5f
      val dy = (height - b.height * scale) * 0.5f
      m.setScale(scale, scale)
      m.postTranslate(dx, dy)
      shader!!.setLocalMatrix(m)

      paint.shader = shader
    }

    override fun onDraw(canvas: Canvas) {
      super.onDraw(canvas)
      val r = minOf(width, height) * 0.5f
      canvas.drawCircle(width * 0.5f, height * 0.5f, r, paint)
    }

    private fun drawableToBitmap(d: android.graphics.drawable.Drawable): Bitmap {
      val w = if (d.intrinsicWidth > 0) d.intrinsicWidth else 512
      val h = if (d.intrinsicHeight > 0) d.intrinsicHeight else 512
      val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
      val c = Canvas(b)
      d.setBounds(0, 0, c.width, c.height)
      d.draw(c)
      return b
    }
  }

  // fondo redondeado simple (sin libs)
  private class RoundRectDrawable(
    private val color: Int,
    private val radius: Float
  ) : android.graphics.drawable.Drawable() {

    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = this@RoundRectDrawable.color }
    private val r = RectF()

    override fun draw(canvas: Canvas) {
      r.set(bounds)
      canvas.drawRoundRect(r, radius, radius, p)
    }

    override fun setAlpha(alpha: Int) { p.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { p.colorFilter = colorFilter }
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
  }
}
