package com.fixton.zyroxadssdk.internal

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.fixton.zyroxadssdk.AdsSdk

internal object AdRequestGuard {

    fun canRequest(context: Context, adUnitId: String): Boolean {
        if (adUnitId.isBlank()) return false
        if (!AdsSdk.canRequest()) return false
        return isOnline(context)
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
