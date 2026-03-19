package com.chesz

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import com.chesz.floating.BubbleService

class CapturePermissionActivity : Activity() {
    companion object {
        const val REQ = 7001
        const val ACTION_RESULT = "CHESZ_CAPTURE_PERMISSION_RESULT"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mgr.createScreenCaptureIntent(), REQ)
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        val i =
            Intent(this, BubbleService::class.java).apply {
                action = ACTION_RESULT
                putExtra("resultCode", resultCode)
                putExtra("data", data)
            }
        startService(i)

        finish()
    }
}
