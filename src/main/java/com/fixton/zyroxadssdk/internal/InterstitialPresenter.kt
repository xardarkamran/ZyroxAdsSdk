package com.fixton.zyroxadssdk.internal

import android.app.Activity
import com.fixton.zyroxadssdk.AdsSdk
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.interstitial.InterstitialAd

/**
 * Shared show path for every interstitial type.
 * The dialog is always shown before the ad. The ad reference is cleared only
 * after a real show attempt, so a blocked show can be retried.
 */
internal object InterstitialPresenter {

    fun show(
        activity: Activity,
        ad: InterstitialAd,
        onConsumed: () -> Unit,
        onDismissed: () -> Unit,
        onFailed: () -> Unit,
        afterClose: () -> Unit
    ) {
        if (!activity.isAlive() || FullScreenGate.isShowing) {
            onDismissed()
            return
        }
        FullScreenGate.isShowing = true
        ShowingAdDialog.show(activity) {
            if (!activity.isAlive()) {
                FullScreenGate.isShowing = false
                onDismissed()
                return@show
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
                    close(shown = true)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    close(shown = false)
                }

                private fun close(shown: Boolean) {
                    if (closed) return
                    closed = true
                    FullScreenGate.isShowing = false
                    onConsumed()
                    if (shown) {
                        onDismissed()
                    } else {
                        onFailed()
                    }
                    afterClose()
                }
            }
            try {
                ad.show(activity)
            } catch (_: Exception) {
                FullScreenGate.isShowing = false
                onConsumed()
                onFailed()
                afterClose()
            }
        }
    }
}
