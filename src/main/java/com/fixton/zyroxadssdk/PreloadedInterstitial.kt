package com.fixton.zyroxadssdk

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import com.fixton.zyroxadssdk.internal.AdLog
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

    private const val NAME = "Interstitial preloaded"
    private const val EXPIRY_MS = 60 * 60 * 1000L

    private var ad: InterstitialAd? = null
    private var loadedAtMs = 0L
    private var loading = false
    private var adUnitId: String = ""
    private var appContext: Context? = null
    private val pendingLoads = mutableListOf<(Boolean) -> Unit>()

    fun isReady(): Boolean = freshAd() != null

    fun load(context: Context, adUnitId: String, onLoaded: (Boolean) -> Unit = {}) {
        val logContext = context.applicationContext
        appContext = logContext
        this.adUnitId = adUnitId
        if (AdRequestGuard.skipIfBlocked(logContext, NAME, adUnitId)) {
            onLoaded(false)
            return
        }
        if (freshAd() != null) {
            AdLog.skipped(logContext, NAME, adUnitId, "already loaded, no new request")
            onLoaded(true)
            return
        }
        pendingLoads.add(onLoaded)
        if (loading) {
            AdLog.skipped(logContext, NAME, adUnitId, "already loading, no new request")
            return
        }
        loading = true
        AdLog.request(logContext, NAME, adUnitId)
        InterstitialAd.load(
            logContext,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    loading = false
                    ad = interstitialAd
                    loadedAtMs = SystemClock.elapsedRealtime()
                    AdLog.loaded(logContext, NAME, adUnitId)
                    dispatch(true)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    loading = false
                    ad = null
                    AdLog.failed(logContext, NAME, adUnitId, loadAdError.message)
                    dispatch(false)
                }
            }
        )
    }

    fun show(activity: Activity, onDismissed: () -> Unit = {}) {
        val ready = freshAd()
        if (ready == null) {
            AdLog.skipped(activity, NAME, adUnitId, "not loaded, no request")
            onDismissed()
            return
        }
        InterstitialPresenter.show(
            activity = activity,
            ad = ready,
            adName = NAME,
            adUnitId = adUnitId,
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
