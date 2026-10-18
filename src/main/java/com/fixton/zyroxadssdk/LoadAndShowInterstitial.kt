package com.fixton.zyroxadssdk

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import com.fixton.zyroxadssdk.internal.AdRequestGuard
import com.fixton.zyroxadssdk.internal.InterstitialPresenter
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Load when the screen opens. Show later, on a button click.
 *
 * After the user closes the ad it is not loaded again.
 * If the ad is not ready, [show] skips the dialog and calls [onDismissed].
 */
object LoadAndShowInterstitial {

    private const val EXPIRY_MS = 60 * 60 * 1000L

    private var ad: InterstitialAd? = null
    private var loadedAtMs = 0L
    private var loading = false
    private val pendingLoads = mutableListOf<(Boolean) -> Unit>()

    fun isReady(): Boolean = freshAd() != null

    fun load(context: Context, adUnitId: String, onLoaded: (Boolean) -> Unit = {}) {
        if (!AdRequestGuard.canRequest(context, adUnitId)) {
            onLoaded(false)
            return
        }
        if (freshAd() != null) {
            onLoaded(true)
            return
        }
        pendingLoads.add(onLoaded)
        if (loading) return
        loading = true
        InterstitialAd.load(
            context.applicationContext,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    loading = false
                    ad = interstitialAd
                    loadedAtMs = SystemClock.elapsedRealtime()
                    dispatch(true)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    loading = false
                    ad = null
                    dispatch(false)
                }
            }
        )
    }

    fun show(activity: Activity, onDismissed: () -> Unit = {}) {
        val ready = freshAd()
        if (ready == null) {
            onDismissed()
            return
        }
        InterstitialPresenter.show(
            activity = activity,
            ad = ready,
            onConsumed = {
                ad = null
                loadedAtMs = 0L
            },
            onDismissed = onDismissed,
            onFailed = onDismissed,
            afterClose = {}
        )
    }

    private fun dispatch(loaded: Boolean) {
        val callbacks = pendingLoads.toList()
        pendingLoads.clear()
        callbacks.forEach { it(loaded) }
    }

    private fun freshAd(): InterstitialAd? {
        val current = ad ?: return null
        if (SystemClock.elapsedRealtime() - loadedAtMs > EXPIRY_MS) {
            ad = null
            return null
        }
        return current
    }
}
