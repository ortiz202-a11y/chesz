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
import com.chesz.analyzer.R

class BubbleService : Service() {

  private lateinit var wm: WindowManager
  private var bubbleView: View? = null
  private var closeView: View? = null

  private lateinit var bubbleLp: WindowManager.LayoutParams
  private lateinit var closeLp: WindowManager.LayoutParams

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

    // ✅ CLAVE: si cierras, NO debe revivir
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
    try { bubbleView?.let { wm.removeViewImmediate(it) } } catch (_: Throwable) {}
    try { closeView?.let { wm.removeViewImmediate(it) } } catch (_: Throwable) {}
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

  private fun createCloseZone() {
    val size = dp(84)

    val root = FrameLayout(this).apply {
      visibility = View.GONE
      setBackgroundColor(0x00000000)

      addView(FrameLayout(this@BubbleService).apply {
        setBackgroundColor(0xCCFF0000.toInt())
        // círculo de la X
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

  private fun createBubble() {
    // ✅ CircleCrop real: sin “halo” por clipToOutline
    val iconView = CircleCropView(this).apply {
      setImageResource(R.drawable.bubble_icon)
    }

    val container = FrameLayout(this).apply {
      setBackgroundColor(0x00000000)
      addView(iconView, FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
      ))
    }

    bubbleLp = WindowManager.LayoutParams(
      dp(56),
      dp(56),
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

          if (moved && isInsideClose(ev.rawX, ev.rawY)) {
            shutdown()
          } else if (isTap) {
            shutdown()
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

  private fun showClose(show: Boolean) {
    closeView?.visibility = if (show) View.VISIBLE else View.GONE
  }

  private fun isInsideClose(rawX: Float, rawY: Float): Boolean {
    val close = closeView ?: return false
    if (close.visibility != View.VISIBLE) return false

    val loc = IntArray(2)
    close.getLocationOnScreen(loc)
    val left = loc[0]
    val top = loc[1]
    val right = left + close.width
    val bottom = top + close.height

    return rawX >= left && rawX <= right && rawY >= top && rawY <= bottom
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

      // centerCrop matrix
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
}
