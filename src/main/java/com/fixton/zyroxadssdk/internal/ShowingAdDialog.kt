package com.fixton.zyroxadssdk.internal

import android.app.Activity
import android.app.Dialog
import android.os.Handler
import android.os.Looper
import android.view.Window
import android.view.WindowManager
import com.fixton.zyroxadssdk.R

/**
 * Full-screen notice shown before an interstitial so the ad does not appear
 * on the same frame as the button click.
 */
internal object ShowingAdDialog {

    const val DELAY_MS = 1500L

    private val handler = Handler(Looper.getMainLooper())
    private var dialog: Dialog? = null
    private var pending: Runnable? = null

    fun show(activity: Activity, onFinished: () -> Unit) {
        dismiss()
        if (!activity.isAlive()) {
            onFinished()
            return
        }
        val created = Dialog(activity, android.R.style.Theme_Light_NoTitleBar_Fullscreen)
        try {
            created.requestWindowFeature(Window.FEATURE_NO_TITLE)
            created.setCancelable(false)
            created.setCanceledOnTouchOutside(false)
            created.setContentView(R.layout.zyrox_dialog_showing_ad)
            created.window?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            created.show()
        } catch (_: Exception) {
            onFinished()
            return
        }
        dialog = created
        val task = Runnable {
            pending = null
            dismiss()
            onFinished()
        }
        pending = task
        handler.postDelayed(task, DELAY_MS)
    }

    /** Shows the dialog and leaves it up until [dismiss]. */
    fun showNow(activity: Activity): Boolean {
        dismiss()
        if (!activity.isAlive()) return false
        val created = Dialog(activity, android.R.style.Theme_Light_NoTitleBar_Fullscreen)
        return try {
            created.requestWindowFeature(Window.FEATURE_NO_TITLE)
            created.setCancelable(false)
            created.setCanceledOnTouchOutside(false)
            created.setContentView(R.layout.zyrox_dialog_showing_ad)
            created.window?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            created.show()
            dialog = created
            true
        } catch (_: Exception) {
            false
        }
    }

    fun dismiss() {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
        try {
            dialog?.takeIf { it.isShowing }?.dismiss()
        } catch (_: Exception) {
        }
        dialog = null
    }
}

internal fun Activity.isAlive(): Boolean = !isFinishing && !isDestroyed
