package com.fixton.zyroxadssdk

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import com.fixton.zyroxadssdk.internal.AdLog
import com.fixton.zyroxadssdk.internal.AdRequestGuard
import com.fixton.zyroxadssdk.internal.isAlive
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * Loads one native ad into [container] using [style].
 * A matching shimmer is shown until the ad loads or fails.
 * On failure the shimmer is removed and the container is hidden.
 *
 * Call [destroy] from the screen's onDestroy.
 */
object NativeAds {

    private const val EXPIRY_MS = 60 * 60 * 1000L

    private var cachedAd: NativeAd? = null
    private var cachedUnitId: String = ""
    private var cachedStyle: NativeStyle? = null
    private var cachedAtMs = 0L
    private var preloading = false
    private var preloadToken = 0
    private val preloadCallbacks = mutableListOf<(Boolean) -> Unit>()
    private var showCallback: NativeCallback = object : NativeCallback {}

    /**
     * Loads a native ad and keeps it until [showPreloaded].
     * Call this on an earlier screen so the next screen only displays the ad.
     */
    fun preload(
        context: Context,
        adUnitId: String,
        style: NativeStyle,
        onLoaded: (Boolean) -> Unit = {}
    ) {
        val name = preloadName(style)
        val logContext = context.applicationContext
        if (AdRequestGuard.skipIfBlocked(logContext, name, adUnitId)) {
            onLoaded(false)
            return
        }
        if (freshCached(adUnitId, style) != null) {
            AdLog.skipped(logContext, name, adUnitId, "already loaded, no new request")
            onLoaded(true)
            return
        }
        if (preloading && cachedUnitId == adUnitId && cachedStyle == style) {
            preloadCallbacks.add(onLoaded)
            AdLog.skipped(logContext, name, adUnitId, "already loading, no new request")
            return
        }
        val replaced = preloading
        cachedAd?.destroy()
        cachedAd = null
        if (preloading) dispatchPreload(false)
        preloadCallbacks.add(onLoaded)
        cachedUnitId = adUnitId
        cachedStyle = style
        val token = ++preloadToken
        preloading = true
        AdLog.request(
            logContext,
            name,
            adUnitId,
            if (replaced) "replaced the in-flight request" else ""
        )
        buildLoader(logContext, adUnitId, style) { nativeAd ->
            if (token != preloadToken) {
                nativeAd.destroy()
                AdLog.ignored(logContext, name, adUnitId, "late response, request was replaced")
                return@buildLoader
            }
            cachedAd?.destroy()
            cachedAd = nativeAd
            cachedAtMs = SystemClock.elapsedRealtime()
            preloading = false
            AdLog.loaded(logContext, name, adUnitId)
            dispatchPreload(true)
        }.withAdListener(object : AdListener() {
            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                if (token != preloadToken) {
                    AdLog.ignored(logContext, name, adUnitId, "late response, request was replaced")
                    return
                }
                preloading = false
                cachedAd = null
                AdLog.failed(logContext, name, adUnitId, loadAdError.message)
                dispatchPreload(false)
            }

            override fun onAdClicked() {
                showCallback.onClicked()
            }

            override fun onAdImpression() {
                AdsSdk.onImpression("native")
                AdLog.impression(logContext, name, adUnitId)
                showCallback.onImpression()
            }
        }).build().loadAd(AdRequest.Builder().build())
    }

    /**
     * Displays the ad from [preload]. No new request is sent.
     * If the preload is still running, the container waits and then shows it.
     */
    fun showPreloaded(
        activity: Activity,
        container: ViewGroup,
        adUnitId: String,
        style: NativeStyle,
        callback: NativeCallback = object : NativeCallback {}
    ) {
        val name = preloadName(style)
        destroy(container)
        if (!activity.isAlive()) {
            callback.onFailed("Activity is not available")
            return
        }
        showCallback = callback
        val ready = freshCached(adUnitId, style)
        if (ready != null) {
            AdLog.skipped(activity, name, adUnitId, "showing cached ad, no new request")
            cachedAd = null
            present(activity, container, ready, style, name, adUnitId, callback)
            return
        }
        if (!preloading || cachedUnitId != adUnitId || cachedStyle != style) {
            AdLog.skipped(activity, name, adUnitId, "not loaded, no new request")
            container.visibility = View.GONE
            callback.onFailed("Native ad is not loaded")
            return
        }
        AdLog.skipped(activity, name, adUnitId, "waiting for the in-flight preload, no new request")
        container.visibility = View.VISIBLE
        val shimmer = activity.layoutInflater.inflate(shimmerLayout(style), container, false)
        container.addView(shimmer)
        container.setTag(R.id.zyrox_shimmer, shimmer)
        val loadToken = Any()
        container.setTag(R.id.zyrox_load_token, loadToken)
        preloadCallbacks.add { loaded ->
            if (container.getTag(R.id.zyrox_load_token) !== loadToken) return@add
            removeShimmer(container)
            val ad = freshCached(adUnitId, style)
            if (!loaded || ad == null || !activity.isAlive()) {
                destroy(container)
                container.visibility = View.GONE
                callback.onFailed("Native ad failed to load")
                return@add
            }
            cachedAd = null
            present(activity, container, ad, style, name, adUnitId, callback)
        }
    }

    fun load(
        activity: Activity,
        adUnitId: String,
        container: ViewGroup,
        style: NativeStyle,
        showShimmer: Boolean = true,
        callback: NativeCallback = object : NativeCallback {}
    ) {
        val name = loadName(style)
        val logContext = activity.applicationContext
        destroy(container)
        if (!activity.isAlive()) {
            container.visibility = View.GONE
            callback.onFailed("Activity is not available")
            return
        }
        if (AdRequestGuard.skipIfBlocked(logContext, name, adUnitId)) {
            container.visibility = View.GONE
            callback.onFailed("Ad request blocked")
            return
        }
        val token = Any()
        container.setTag(R.id.zyrox_load_token, token)
        container.visibility = View.VISIBLE
        if (showShimmer) {
            val shimmer = activity.layoutInflater.inflate(shimmerLayout(style), container, false)
            container.addView(shimmer)
            container.setTag(R.id.zyrox_shimmer, shimmer)
        }
        val videoOptions = VideoOptions.Builder()
            .setStartMuted(true)
            .setClickToExpandRequested(false)
            .setCustomControlsRequested(false)
            .build()
        val options = NativeAdOptions.Builder()
            .setVideoOptions(videoOptions)
            .setRequestMultipleImages(false)
            .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
            .setMediaAspectRatio(mediaAspect(style))
            .build()
        val loader = AdLoader.Builder(activity, adUnitId)
            .forNativeAd { nativeAd ->
                if (container.getTag(R.id.zyrox_load_token) !== token || !activity.isAlive()) {
                    nativeAd.destroy()
                    AdLog.ignored(logContext, name, adUnitId, "late response, request was replaced")
                    return@forNativeAd
                }
                val previous = container.getTag(R.id.zyrox_native_ad) as? NativeAd
                previous?.destroy()
                val adView = activity.layoutInflater.inflate(adLayout(style), container, false) as NativeAdView
                bind(nativeAd, adView)
                removeShimmer(container)
                container.removeAllViews()
                container.addView(adView)
                container.setTag(R.id.zyrox_native_ad, nativeAd)
                rememberLog(container, name, adUnitId)
                container.visibility = View.VISIBLE
                AdLog.loaded(logContext, name, adUnitId)
                callback.onLoaded()
            }
            .withNativeAdOptions(options)
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    if (container.getTag(R.id.zyrox_load_token) !== token) {
                        AdLog.ignored(logContext, name, adUnitId, "late response, request was replaced")
                        return
                    }
                    AdLog.failed(logContext, name, adUnitId, loadAdError.message)
                    destroy(container)
                    container.visibility = View.GONE
                    callback.onFailed(loadAdError.message)
                }

                override fun onAdClicked() {
                    callback.onClicked()
                }

                override fun onAdImpression() {
                    AdsSdk.onImpression("native")
                    AdLog.impression(logContext, name, adUnitId)
                    callback.onImpression()
                }
            })
            .build()
        AdLog.request(logContext, name, adUnitId)
        loader.loadAd(AdRequest.Builder().build())
    }

    fun destroy(container: ViewGroup) {
        val shown = container.getTag(R.id.zyrox_native_ad) as? NativeAd
        val name = container.getTag(R.id.zyrox_log_name) as? String
        val unitId = container.getTag(R.id.zyrox_log_unit) as? String
        shown?.destroy()
        if (shown != null && !name.isNullOrBlank() && !unitId.isNullOrBlank()) {
            AdLog.destroyed(container.context, name, unitId)
        }
        container.setTag(R.id.zyrox_native_ad, null)
        container.setTag(R.id.zyrox_log_name, null)
        container.setTag(R.id.zyrox_log_unit, null)
        container.setTag(R.id.zyrox_shimmer, null)
        container.setTag(R.id.zyrox_load_token, null)
        container.removeAllViews()
    }

    private fun present(
        activity: Activity,
        container: ViewGroup,
        nativeAd: NativeAd,
        style: NativeStyle,
        name: String,
        adUnitId: String,
        callback: NativeCallback
    ) {
        val adView = activity.layoutInflater.inflate(adLayout(style), container, false) as NativeAdView
        bind(nativeAd, adView)
        removeShimmer(container)
        container.removeAllViews()
        container.addView(adView)
        container.setTag(R.id.zyrox_native_ad, nativeAd)
        rememberLog(container, name, adUnitId)
        container.visibility = View.VISIBLE
        callback.onLoaded()
    }

    private fun rememberLog(container: ViewGroup, name: String, unitId: String) {
        container.setTag(R.id.zyrox_log_name, name)
        container.setTag(R.id.zyrox_log_unit, unitId)
    }

    private fun preloadName(style: NativeStyle): String = "Native preload ${style.name}"

    private fun loadName(style: NativeStyle): String = "Native load ${style.name}"

    private fun buildLoader(
        context: Context,
        adUnitId: String,
        style: NativeStyle,
        onAd: (NativeAd) -> Unit
    ): AdLoader.Builder {
        val videoOptions = VideoOptions.Builder()
            .setStartMuted(true)
            .setClickToExpandRequested(false)
            .setCustomControlsRequested(false)
            .build()
        val options = NativeAdOptions.Builder()
            .setVideoOptions(videoOptions)
            .setRequestMultipleImages(false)
            .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
            .setMediaAspectRatio(mediaAspect(style))
            .build()
        return AdLoader.Builder(context, adUnitId)
            .forNativeAd(onAd)
            .withNativeAdOptions(options)
    }

    private fun freshCached(adUnitId: String, style: NativeStyle): NativeAd? {
        val current = cachedAd ?: return null
        if (cachedUnitId != adUnitId || cachedStyle != style) return null
        if (SystemClock.elapsedRealtime() - cachedAtMs > EXPIRY_MS) {
            current.destroy()
            cachedAd = null
            return null
        }
        return current
    }

    private fun dispatchPreload(loaded: Boolean) {
        val callbacks = preloadCallbacks.toList()
        preloadCallbacks.clear()
        callbacks.forEach { it(loaded) }
    }

    private fun bind(nativeAd: NativeAd, adView: NativeAdView) {
        val headline = adView.findViewById<TextView>(R.id.zyrox_ad_headline)
        val body = adView.findViewById<TextView>(R.id.zyrox_ad_body)
        val cta = adView.findViewById<Button>(R.id.zyrox_ad_cta)
        val icon = adView.findViewById<ImageView>(R.id.zyrox_ad_icon)
        val media = adView.findViewById<MediaView>(R.id.zyrox_ad_media)
        val stars = adView.findViewById<RatingBar>(R.id.zyrox_ad_stars)

        adView.headlineView = headline
        headline.text = nativeAd.headline.orEmpty()

        if (media != null) {
            adView.mediaView = media
            nativeAd.mediaContent?.let { media.mediaContent = it }
            media.setImageScaleType(ImageView.ScaleType.CENTER_CROP)
        }

        if (body != null) {
            adView.bodyView = body
            if (nativeAd.body.isNullOrBlank()) {
                body.visibility = View.GONE
            } else {
                body.visibility = View.VISIBLE
                body.text = nativeAd.body
            }
        }

        if (cta != null) {
            adView.callToActionView = cta
            if (nativeAd.callToAction.isNullOrBlank()) {
                cta.visibility = View.INVISIBLE
            } else {
                cta.visibility = View.VISIBLE
                cta.text = nativeAd.callToAction
            }
        }

        if (icon != null) {
            adView.iconView = icon
            val drawable = nativeAd.icon?.drawable
            if (drawable == null) {
                icon.visibility = View.GONE
            } else {
                icon.setImageDrawable(drawable)
                icon.visibility = View.VISIBLE
            }
        }

        if (stars != null) {
            adView.starRatingView = stars
            val rating = nativeAd.starRating
            if (rating == null) {
                stars.visibility = View.GONE
            } else {
                stars.rating = rating.toFloat()
                stars.visibility = View.VISIBLE
            }
        }

        adView.setNativeAd(nativeAd)
    }

    private fun adLayout(style: NativeStyle): Int = when (style) {
        NativeStyle.LARGE_MEDIA -> R.layout.zyrox_native_large_media
        NativeStyle.MEDIUM_MEDIA -> R.layout.zyrox_native_medium_media
        NativeStyle.LARGE -> R.layout.zyrox_native_large
        NativeStyle.MEDIUM -> R.layout.zyrox_native_medium
        NativeStyle.SMALL -> R.layout.zyrox_native_small
    }

    private fun shimmerLayout(style: NativeStyle): Int = when (style) {
        NativeStyle.LARGE_MEDIA -> R.layout.zyrox_shimmer_native_large_media
        NativeStyle.MEDIUM_MEDIA -> R.layout.zyrox_shimmer_native_medium_media
        NativeStyle.LARGE -> R.layout.zyrox_shimmer_native_large
        NativeStyle.MEDIUM -> R.layout.zyrox_shimmer_native_medium
        NativeStyle.SMALL -> R.layout.zyrox_shimmer_native_small
    }

    private fun mediaAspect(style: NativeStyle): Int {
        return when (style) {
            NativeStyle.MEDIUM_MEDIA -> NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_SQUARE
            NativeStyle.LARGE_MEDIA -> NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_LANDSCAPE
            else -> NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_ANY
        }
    }

    private fun removeShimmer(container: ViewGroup) {
        (container.getTag(R.id.zyrox_shimmer) as? View)?.let { container.removeView(it) }
        container.setTag(R.id.zyrox_shimmer, null)
    }
}
