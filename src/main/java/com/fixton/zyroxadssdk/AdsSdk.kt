package com.fixton.zyroxadssdk

import android.app.Application
import com.google.android.gms.ads.MobileAds

/**
 * Entry point for Zyrox Ads SDK.
 *
 * Call [init] once, after the user has answered the ads consent form.
 * Pass [adsEnabled] so a premium user never requests an ad.
 */
object AdsSdk {

    @Volatile
    private var adsEnabled: () -> Boolean = { true }

    @Volatile
    private var impressionListener: (String) -> Unit = {}

    @Volatile
    private var initialized = false

    /**
     * @param adsEnabled return false to block every format (premium, remote off switch, consent not granted).
     * @param onImpression optional analytics hook. The value is the format name.
     */
    fun init(
        application: Application,
        adsEnabled: () -> Boolean = { true },
        onImpression: (String) -> Unit = {}
    ) {
        this.adsEnabled = adsEnabled
        this.impressionListener = onImpression
        if (!initialized) {
            initialized = true
            MobileAds.initialize(application)
        }
    }

    internal fun canRequest(): Boolean = adsEnabled()

    internal fun onImpression(format: String) {
        impressionListener(format)
    }
}
