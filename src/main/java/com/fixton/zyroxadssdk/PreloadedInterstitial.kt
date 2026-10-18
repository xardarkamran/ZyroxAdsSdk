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
 * One shared interstitial for the whole app.
 *
 * Load it once (for example on the splash screen). Any later screen can call
 * [show] with the same loaded ad. After the user closes it, the same ad unit
 * id is loaded again.
 *
 * If the ad is not ready, [show] skips the dialog and calls [onDismissed].
 */
object PreloadedInterstitial {

    private const val EXPIRY_MS = 60 * 60 * 1000L

    private var ad: InterstitialAd? = null
    private var loadedAtMs = 0L
    private var loading = false
    private var adUnitId: String = ""
    private var appContext: Context? = null
    private val pendingLoads = mutableListOf<(Boolean) -> Unit>()

    fun isReady(): Boolean = freshAd() != null

    fun load(context: Context, adUnitId: String, onLoaded: (Boolean) -> Unit = {}) {
        appContext = context.applicationContext
        this.adUnitId = adUnitId
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
            afterClose = { reload() }
        )
    }

    private fun reload() {
        val context = appContext ?: return
        val id = adUnitId
        if (id.isBlank()) return
        load(context, id)
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
