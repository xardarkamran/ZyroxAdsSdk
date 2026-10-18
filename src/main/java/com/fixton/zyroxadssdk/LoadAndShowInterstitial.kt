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
        if (!AdRequestGuard.canRequest(context, adUnitId)) {
            onLoaded(false)
            return
        }
        if (freshAd() != null && loadedUnitId == adUnitId) {
            onLoaded(true)
            return
        }
        if (loading && loadedUnitId == adUnitId) {
            pendingLoads.add(onLoaded)
            return
        }
        ad = null
        if (loading) dispatch(false)
        loadedUnitId = adUnitId
        pendingLoads.add(onLoaded)
        val token = ++requestToken
        loading = true
        InterstitialAd.load(
            context.applicationContext,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    if (token != requestToken) return
                    loading = false
                    ad = interstitialAd
                    loadedUnitId = adUnitId
                    loadedAtMs = SystemClock.elapsedRealtime()
                    dispatch(true)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    if (token != requestToken) return
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
