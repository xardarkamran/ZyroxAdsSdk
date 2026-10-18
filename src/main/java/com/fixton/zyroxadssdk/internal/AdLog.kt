package com.fixton.zyroxadssdk.internal

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log

/**
 * Debug-only trail for one ad.
 * A release app is not debuggable, so these lines are not written.
 */
internal object AdLog {

    private const val TAG = "ZyroxAds"

    fun request(context: Context, name: String, unitId: String, detail: String = "") {
        write(context, name, unitId, "request", detail)
    }

    fun skipped(context: Context, name: String, unitId: String, reason: String) {
        write(context, name, unitId, "skipped", reason)
    }

    fun loaded(context: Context, name: String, unitId: String) {
        write(context, name, unitId, "loaded", "")
    }

    fun failed(context: Context, name: String, unitId: String, error: String) {
        write(context, name, unitId, "failed", error)
    }

    fun impression(context: Context, name: String, unitId: String) {
        write(context, name, unitId, "impression", "")
    }

    fun dismissed(context: Context, name: String, unitId: String) {
        write(context, name, unitId, "dismissed", "")
    }

    fun destroyed(context: Context, name: String, unitId: String) {
        write(context, name, unitId, "destroyed", "")
    }

    fun ignored(context: Context, name: String, unitId: String, reason: String) {
        write(context, name, unitId, "ignored", reason)
    }

    private fun write(
        context: Context,
        name: String,
        unitId: String,
        event: String,
        detail: String
    ) {
        if (!isDebugApp(context)) return
        val extra = if (detail.isBlank()) "" else " | $detail"
        Log.d(TAG, "$name | $event | $unitId$extra")
    }

    private fun isDebugApp(context: Context): Boolean {
        return context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }
}
