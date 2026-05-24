package com.chesz

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object AppLog {
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var logFile: File? = null
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        if (logFile != null) return
        val dir = context.getExternalFilesDir(null) ?: return
        dir.mkdirs()
        logFile = File(dir, "log_full.txt")
        log("AppLog", "=== SESIÓN INICIADA ===")
    }

    fun log(tag: String, msg: String) {
        android.util.Log.d(tag, msg)
        val file = logFile ?: return
        executor.submit {
            runCatching {
                val ts = fmt.format(Date())
                file.appendText("[$ts] $tag: $msg\n")
            }
        }
    }
}
