package com.chesz

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.chesz.floating.BubbleService

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQ_MP = 200
        private const val PREF_SETUP = "chesz_setup"
        private const val KEY_APP_INFO_SHOWN = "appInfoShown"
    }

    // Persiste entre reinicios completos de la actividad.
    // Una vez que el usuario visitó App Info, no lo volvemos a mandar ahí.
    private val setupPrefs by lazy { getSharedPreferences(PREF_SETUP, MODE_PRIVATE) }
    private var appInfoShown: Boolean
        get() = setupPrefs.getBoolean(KEY_APP_INFO_SHOWN, false)
        set(v) = setupPrefs.edit().putBoolean(KEY_APP_INFO_SHOWN, v).apply()

    // Evita que onResume dispare la solicitud de MP dos veces
    private var mpRequested = false

    override fun onResume() {
        super.onResume()
        if (!mpRequested) checkAndProceed()
    }

    private fun checkAndProceed() {
        when {
            // Servicio ya corriendo: nada que hacer
            BubbleService.isRunning -> finish()

            // Paso 1: permiso de superposición (Display over other apps)
            !Settings.canDrawOverlays(this) -> {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                )
            }

            // Paso 2: App Info — en Android 13+ el usuario debe habilitar
            // "Permitir configuración restringida" antes de poder activar accesibilidad
            !isA11yEnabled() && !appInfoShown -> {
                appInfoShown = true
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            }

            // Paso 3: Accesibilidad — activar ChessboardA11yService
            !isA11yEnabled() -> {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }

            // Paso 4: Captura de pantalla (MediaProjection)
            else -> {
                mpRequested = true
                val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                startActivityForResult(mgr.createScreenCaptureIntent(), REQ_MP)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_MP) {
            val intent = Intent(this, BubbleService::class.java)
            if (resultCode == Activity.RESULT_OK && data != null) {
                // Pasar el token de captura directamente al servicio
                intent.action = "CHESZ_CAPTURE_PERMISSION_RESULT"
                intent.putExtra("resultCode", resultCode)
                intent.putExtra("data", data)
            }
            startService(intent)
            finish()
        }
    }

    private fun isA11yEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.contains("ChessboardA11yService", ignoreCase = true)
    }
}
