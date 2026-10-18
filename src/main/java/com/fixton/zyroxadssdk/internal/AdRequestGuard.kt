package com.fixton.zyroxadssdk.internal

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.fixton.zyroxadssdk.AdsSdk

internal object AdRequestGuard {

    fun canRequest(context: Context, adUnitId: String): Boolean {
        return blockReason(context, adUnitId) == null
    }

    /**
     * Logs a skipped request in debug and returns true when no request may be sent.
     */
    fun skipIfBlocked(context: Context, name: String, adUnitId: String): Boolean {
        val reason = blockReason(context, adUnitId) ?: return false
        AdLog.skipped(context, name, adUnitId, "$reason, no request")
        return true
    }

    private fun blockReason(context: Context, adUnitId: String): String? {
        if (adUnitId.isBlank()) return "empty ad unit id"
        if (!AdsSdk.canRequest()) return "ads disabled"
        if (!isOnline(context)) return "device offline"
        return null
    }

    private fun isOnline(context: Context): Boolean {
        return try {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = manager.activeNetwork ?: return false
                val capabilities = manager.getNetworkCapabilities(network) ?: return false
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                @Suppress("DEPRECATION")
                manager.activeNetworkInfo?.isConnected == true
            }
        } catch (_: Exception) {
            false
        }
    }
}
