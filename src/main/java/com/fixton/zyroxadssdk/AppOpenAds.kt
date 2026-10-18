package com.fixton.zyroxadssdk

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.fixton.zyroxadssdk.internal.AdLog
import com.fixton.zyroxadssdk.internal.AdRequestGuard
import com.fixton.zyroxadssdk.internal.FullScreenGate
import com.fixton.zyroxadssdk.internal.isAlive
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd

/**
 * App-open ads. [load] and [show] are manual.
 * [attach] loads once and shows when the app returns to the foreground.
 *
 * Put the splash activity in [skipActivities]. An app-open ad is not shown
 * while an interstitial is on screen, and it is not shown on the return
 * from an ad click.
 */
object AppOpenAds : DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {

    private const val NAME = "App open"
    private const val EXPIRY_MS = 4 * 60 * 60 * 1000L

    private var application: Application? = null
    private var adUnitId: String = ""
    private var ad: AppOpenAd? = null
    private var loadedAtMs = 0L
    private var loading = false
    private var currentActivity: Activity? = null
    private var skipActivities: Set<Class<out Activity>> = emptySet()
    private var attached = false

    fun load(context: Context, adUnitId: String, onLoaded: (Boolean) -> Unit = {}) {
        val logContext = context.applicationContext
        this.adUnitId = adUnitId
        if (AdRequestGuard.skipIfBlocked(logContext, NAME, adUnitId)) {
            onLoaded(false)
            return
        }
        if (isReady()) {
            AdLog.skipped(logContext, NAME, adUnitId, "already loaded, no new request")
            onLoaded(true)
            return
        }
        if (loading) {
            AdLog.skipped(logContext, NAME, adUnitId, "already loading, no new request")
            return
        }
        loading = true
        AdLog.request(logContext, NAME, adUnitId)
        AppOpenAd.load(
            logContext,
            adUnitId,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(appOpenAd: AppOpenAd) {
                    loading = false
                    ad = appOpenAd
                    loadedAtMs = SystemClock.elapsedRealtime()
                    AdLog.loaded(logContext, NAME, adUnitId)
                    onLoaded(true)
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    loading = false
                    ad = null
                    AdLog.failed(logContext, NAME, adUnitId, loadAdError.message)
                    onLoaded(false)
                }
            }
        )
    }

    fun show(activity: Activity, onDismissed: () -> Unit = {}) {
        val ready = freshAd()
        if (ready == null || !activity.isAlive() || FullScreenGate.isShowing) {
            AdLog.skipped(activity, NAME, adUnitId, "not shown, no request")
            onDismissed()
            return
        }
        present(activity, ready, onDismissed)
    }

    /**
     * Shows automatically when the process comes to the foreground.
     * Activities in [skipActivities] never host the ad.
     */
    fun attach(
        application: Application,
        adUnitId: String,
        skipActivities: Set<Class<out Activity>> = emptySet()
    ) {
        this.application = application
        this.adUnitId = adUnitId
        this.skipActivities = skipActivities
        if (!attached) {
            attached = true
            application.registerActivityLifecycleCallbacks(this)
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        }
        load(application, adUnitId)
    }

    fun isReady(): Boolean = freshAd() != null

    override fun onStart(owner: LifecycleOwner) {
        val activity = currentActivity ?: return
        if (skipActivities.any { it.isInstance(activity) }) {
            AdLog.skipped(activity, NAME, adUnitId, "skipped activity, no request")
            return
        }
        if (FullScreenGate.skipNextAppOpen) {
            FullScreenGate.skipNextAppOpen = false
            AdLog.skipped(activity, NAME, adUnitId, "returning from an ad, no request")
            return
        }
        if (FullScreenGate.isShowing) {
            AdLog.skipped(activity, NAME, adUnitId, "full-screen ad already open, no request")
            return
        }
        show(activity)
    }

    private fun present(activity: Activity, ready: AppOpenAd, onDismissed: () -> Unit) {
        FullScreenGate.isShowing = true
        ready.fullScreenContentCallback = object : FullScreenContentCallback() {
            private var closed = false

            override fun onAdShowedFullScreenContent() {
                FullScreenGate.isShowing = true
                AdsSdk.onImpression("app_open")
                AdLog.impression(activity, NAME, adUnitId)
            }

            override fun onAdClicked() {
                FullScreenGate.skipNextAppOpen = true
            }

            override fun onAdDismissedFullScreenContent() {
                finish(dismissed = true)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                finish(dismissed = false)
            }

            private fun finish(dismissed: Boolean) {
                if (closed) return
                closed = true
                ad = null
                loadedAtMs = 0L
                FullScreenGate.isShowing = false
                if (dismissed) {
                    AdLog.dismissed(activity, NAME, adUnitId)
                } else {
                    AdLog.failed(activity, NAME, adUnitId, "show failed")
                }
                onDismissed()
                val context = application ?: activity.applicationContext
                val id = adUnitId
                if (id.isNotBlank()) load(context, id)
            }
        }
        try {
            ready.show(activity)
        } catch (_: Exception) {
            ad = null
            FullScreenGate.isShowing = false
            AdLog.failed(activity, NAME, adUnitId, "show failed")
            onDismissed()
        }
    }

    private fun freshAd(): AppOpenAd? {
        val current = ad ?: return null
        if (SystemClock.elapsedRealtime() - loadedAtMs > EXPIRY_MS) {
            ad = null
            return null
        }
        return current
    }

    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity === activity) currentActivity = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
