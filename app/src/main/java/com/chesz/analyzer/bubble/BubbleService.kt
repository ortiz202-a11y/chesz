package com.chesz.analyzer.bubble

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.*
import android.util.DisplayMetrics
import android.view.*
import android.widget.FrameLayout
import com.chesz.analyzer.R
import kotlin.math.abs

class BubbleService : Service() {

  // =============================
  // CONFIGURACIÓN EDITABLE FUTURA
  // =============================
  private val PANEL_W_RATIO = 0.60f
  private val PANEL_H_RATIO = 0.25f

  private lateinit var wm: WindowManager
  private var root: View? = null
  private var panel: View? = null
  private var buttonContainer: View? = null
  private var lp: WindowManager.LayoutParams? = null

  private var anchorBtnX = 0
  private var anchorBtnY = 200

  private var isPanelOpen = false
  private var btnW = 0
  private var btnH = 0
  private var panelW = 0
  private var panelH = 0

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    wm = getSystemService(WINDOW_SERVICE) as WindowManager
    startForegroundCompat()
    inflateOverlay()
    attachDragToButtonOnly()
    attachCloseIfPresent()
    addRootToWindow()
  }

  override fun onDestroy() {
    super.onDestroy()
    root?.let { runCatching { wm.removeView(it) } }
  }

  private fun inflateOverlay() {
    val inflater = LayoutInflater.from(this)
    val v = inflater.inflate(R.layout.overlay_root, null)

    root = v
    panel = v.findViewById(R.id.panelContainer)
    buttonContainer = v.findViewById(R.id.floatingButtonContainer)

    panel?.visibility = View.GONE
    isPanelOpen = false

    v.post {
      btnW = buttonContainer?.width ?: 0
      btnH = buttonContainer?.height ?: 0
    }
  }

  private fun addRootToWindow() {
    val type =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
      else WindowManager.LayoutParams.TYPE_PHONE

    val params = WindowManager.LayoutParams(
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.WRAP_CONTENT,
      type,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      PixelFormat.TRANSLUCENT
    )

    params.gravity = Gravity.TOP or Gravity.START
    params.x = anchorBtnX
    params.y = anchorBtnY

    lp = params
    wm.addView(root, params)
  }

  // =============================
  // DRAG SOLO EN BOTÓN
  // =============================
  private fun attachDragToButtonOnly() {
    val btn = buttonContainer ?: return

    var startRawX = 0f
    var startRawY = 0f
    var startAnchorX = 0
    var startAnchorY = 0
    var moved = false

    btn.setOnTouchListener { _, ev ->
      when (ev.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          moved = false
          startRawX = ev.rawX
          startRawY = ev.rawY
          startAnchorX = anchorBtnX
          startAnchorY = anchorBtnY
          true
        }

        MotionEvent.ACTION_MOVE -> {
          val dx = (ev.rawX - startRawX).toInt()
          val dy = (ev.rawY - startRawY).toInt()
          if (abs(dx) > 6 || abs(dy) > 6) moved = true

          anchorBtnX = startAnchorX + dx
          anchorBtnY = startAnchorY + dy
          applyPositionForCurrentState()
          true
        }

        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
          if (!moved) togglePanelAttempt()
          true
        }

        else -> false
      }
    }
  }

  // =============================
  // PANEL OPEN/CLOSE V1
  // =============================
  private fun togglePanelAttempt() {
    if (isPanelOpen) {
      closePanel()
      return
    }

    calculatePanelSize()

    if (!canOpenPanelNow()) {
      feedbackCantOpen()
      return
    }

    openPanel()
  }

  private fun calculatePanelSize() {
    val (sw, sh) = getScreenSizePx()

    panelW = (sw * PANEL_W_RATIO).toInt()
    panelH = (sh * PANEL_H_RATIO).toInt()

    val lpPanel = panel?.layoutParams
    lpPanel?.width = panelW
    lpPanel?.height = panelH
    panel?.layoutParams = lpPanel
  }

  private fun openPanel() {
    isPanelOpen = true
    panel?.visibility = View.VISIBLE
    applyPositionForCurrentState()
  }

  private fun closePanel() {
    isPanelOpen = false
    panel?.visibility = View.GONE
    applyPositionForCurrentState()
  }

  private fun applyPositionForCurrentState() {
    val params = lp ?: return

    val rootY =
      if (isPanelOpen) anchorBtnY - (panelH - btnH)
      else anchorBtnY

    params.x = anchorBtnX
    params.y = rootY

    root?.let { wm.updateViewLayout(it, params) }
  }

  private fun canOpenPanelNow(): Boolean {
    val (sw, sh) = getScreenSizePx()
    val overlap = btnW / 2

    val rootX = anchorBtnX
    val rootY = anchorBtnY - (panelH - btnH)
    val rootW = panelW + overlap
    val rootH = panelH

    if (rootX < 0) return false
    if (rootY < 0) return false
    if (rootX + rootW > sw) return false
    if (rootY + rootH > sh) return false

    return true
  }

  private fun feedbackCantOpen() {
    val vib = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      vib.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
      @Suppress("DEPRECATION")
      vib.vibrate(45)
    }
  }

  private fun getScreenSizePx(): Pair<Int, Int> {
    return if (Build.VERSION.SDK_INT >= 30) {
      val m = wm.currentWindowMetrics.bounds
      Pair(m.width(), m.height())
    } else {
      @Suppress("DEPRECATION")
      val dm = DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }
      Pair(dm.widthPixels, dm.heightPixels)
    }
  }

  private fun attachCloseIfPresent() {
    val r = root ?: return
    val close = r.findViewById<View?>(R.id.btnClose) ?: return
    close.setOnClickListener { closePanel() }
  }

  private fun startForegroundCompat() {
    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    val channelId = "chesz_overlay"

    if (Build.VERSION.SDK_INT >= 26) {
      nm.createNotificationChannel(
        NotificationChannel(channelId, "CHESZ Overlay", NotificationManager.IMPORTANCE_MIN)
      )
    }

    val notif =
      if (Build.VERSION.SDK_INT >= 26) {
        Notification.Builder(this, channelId)
          .setContentTitle("CHESZ")
          .setContentText("Overlay activo")
          .setSmallIcon(android.R.drawable.ic_menu_info_details)
          .build()
      } else {
        @Suppress("DEPRECATION")
        Notification.Builder(this)
          .setContentTitle("CHESZ")
          .setContentText("Overlay activo")
          .setSmallIcon(android.R.drawable.ic_menu_info_details)
          .build()
      }

    if (Build.VERSION.SDK_INT >= 29) {
      startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    } else {
      startForeground(1, notif)
    }
  }
}
