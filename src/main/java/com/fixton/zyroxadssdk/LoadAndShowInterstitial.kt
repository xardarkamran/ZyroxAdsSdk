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
 * Load when the screen opens. Show later, on a button click.
 *
 * After the user closes the ad it is not loaded again.
 * If the ad is not ready, [show] skips the dialog and calls [onDismissed].
 */
object LoadAndShowInterstitial {

    private const val NAME = "Interstitial load-and-show"
    private const val EXPIRY_MS = 60 * 60 * 1000L

    private var ad: InterstitialAd? = null
    private var loadedUnitId: String = ""
    private var loadedAtMs = 0L
    private var loading = false
    private var requestToken = 0
    private val pendingLoads = mutableListOf<(Boolean) -> Unit>()

    fun isReady(): Boolean = freshAd() != null

    /**
     * Loads [adUnitId]. A ready ad for the same id is kept.
     * A ready ad for a different id is dropped and this id is loaded instead.
     */
    fun load(context: Context, adUnitId: String, onLoaded: (Boolean) -> Unit = {}) {
        val logContext = context.applicationContext
        if (AdRequestGuard.skipIfBlocked(logContext, NAME, adUnitId)) {
            onLoaded(false)
            return
        }
        if (freshAd() != null && loadedUnitId == adUnitId) {
            AdLog.skipped(logContext, NAME, adUnitId, "already loaded, no new request")
            onLoaded(true)
            return
        }
        if (loading && loadedUnitId == adUnitId) {
            pendingLoads.add(onLoaded)
            AdLog.skipped(logContext, NAME, adUnitId, "already loading, no new request")
            return
        }
        val replaced = loading
        ad = null
        if (loading) dispatch(false)
        loadedUnitId = adUnitId
        pendingLoads.add(onLoaded)
        val token = ++requestToken
        loading = true
        AdLog.request(
            logContext,
            NAME,
            adUnitId,
            if (replaced) "replaced the in-flight request" else ""
        )
        InterstitialAd.load(
            logContext,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    if (token != requestToken) {
                        AdLog.ignored(logContext, NAME, adUnitId, "late response, request was replaced")
                        return
                    }
                    loading = false
                    ad = interstitialAd
                    loadedUnitId = adUnitId
                    loadedAtMs = SystemClock.elapsedRealtime()
                    AdLog.loaded(logContext, NAME, adUnitId)
                    dispatch(true)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    if (token != requestToken) {
                        AdLog.ignored(logContext, NAME, adUnitId, "late response, request was replaced")
                        return
                    }
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
            AdLog.skipped(activity, NAME, loadedUnitId, "not loaded, no request")
            onDismissed()
            return
        }
        InterstitialPresenter.show(
            activity = activity,
            ad = ready,
            adName = NAME,
            adUnitId = loadedUnitId,
            onConsumed = {
                ad = null
                loadedUnitId = ""
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
