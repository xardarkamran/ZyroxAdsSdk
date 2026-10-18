package com.fixton.zyroxadssdk

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.fixton.zyroxadssdk.internal.AdRequestGuard
import com.fixton.zyroxadssdk.internal.FullScreenGate
import com.fixton.zyroxadssdk.internal.ShowingAdDialog
import com.fixton.zyroxadssdk.internal.isAlive
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Loads on the button click, shows a "Showing Ad" dialog while the request
 * runs, then shows the ad. The dialog stays at least 1.5 seconds.
 *
 * Nothing is cached. The next click starts a new request.
 */
object OnDemandInterstitial {

    private val handler = Handler(Looper.getMainLooper())
    private var loading = false

    fun show(
        activity: Activity,
        adUnitId: String,
        onDismissed: () -> Unit = {},
        onFailed: () -> Unit = {}
    ) {
        if (!activity.isAlive() || loading || FullScreenGate.isShowing) {
            onFailed()
            return
        }
        if (!AdRequestGuard.canRequest(activity, adUnitId)) {
            onFailed()
            return
        }
        loading = true
        FullScreenGate.isShowing = true
        val startedAt = SystemClock.elapsedRealtime()
        if (!ShowingAdDialog.showNow(activity)) {
            loading = false
            FullScreenGate.isShowing = false
            onFailed()
            return
        }
        var delivered = false
        InterstitialAd.load(
            activity.applicationContext,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    if (delivered) return
                    val wait = (ShowingAdDialog.DELAY_MS - (SystemClock.elapsedRealtime() - startedAt))
                        .coerceAtLeast(0L)
                    handler.postDelayed({
                        if (delivered) return@postDelayed
                        delivered = true
                        loading = false
                        ShowingAdDialog.dismiss()
                        present(activity, interstitialAd, onDismissed, onFailed)
                    }, wait)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    if (delivered) return
                    delivered = true
                    loading = false
                    FullScreenGate.isShowing = false
                    ShowingAdDialog.dismiss()
                    onFailed()
                }
            }
        )
    }

    private fun present(
        activity: Activity,
        ad: InterstitialAd,
        onDismissed: () -> Unit,
        onFailed: () -> Unit
    ) {
        if (!activity.isAlive()) {
            FullScreenGate.isShowing = false
            onFailed()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            private var closed = false

            override fun onAdShowedFullScreenContent() {
                FullScreenGate.isShowing = true
                FullScreenGate.skipNextAppOpen = true
                AdsSdk.onImpression("interstitial")
            }

            override fun onAdClicked() {
                FullScreenGate.skipNextAppOpen = true
            }

            override fun onAdDismissedFullScreenContent() {
                finish(shown = true)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                finish(shown = false)
            }

            private fun finish(shown: Boolean) {
                if (closed) return
                closed = true
                FullScreenGate.isShowing = false
                if (shown) onDismissed() else onFailed()
            }
        }
        try {
            ad.show(activity)
        } catch (_: Exception) {
            FullScreenGate.isShowing = false
            onFailed()
        }
    }
}
