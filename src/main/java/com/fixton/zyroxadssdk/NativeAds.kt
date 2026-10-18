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
        if (!AdRequestGuard.canRequest(context, adUnitId)) {
            onLoaded(false)
            return
        }
        if (freshCached(adUnitId, style) != null) {
            onLoaded(true)
            return
        }
        if (preloading && cachedUnitId == adUnitId && cachedStyle == style) {
            preloadCallbacks.add(onLoaded)
            return
        }
        cachedAd?.destroy()
        cachedAd = null
        if (preloading) dispatchPreload(false)
        preloadCallbacks.add(onLoaded)
        cachedUnitId = adUnitId
        cachedStyle = style
        val token = ++preloadToken
        preloading = true
        buildLoader(context.applicationContext, adUnitId, style) { nativeAd ->
            if (token != preloadToken) {
                nativeAd.destroy()
                return@buildLoader
            }
            cachedAd?.destroy()
            cachedAd = nativeAd
            cachedAtMs = SystemClock.elapsedRealtime()
            preloading = false
            dispatchPreload(true)
        }.withAdListener(object : AdListener() {
            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                if (token != preloadToken) return
                preloading = false
                cachedAd = null
                dispatchPreload(false)
            }

            override fun onAdClicked() {
                showCallback.onClicked()
            }

            override fun onAdImpression() {
                AdsSdk.onImpression("native")
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
        destroy(container)
        if (!activity.isAlive()) {
            callback.onFailed("Activity is not available")
            return
        }
        showCallback = callback
        val ready = freshCached(adUnitId, style)
        if (ready != null) {
            cachedAd = null
            present(activity, container, ready, style, callback)
            return
        }
        if (!preloading || cachedUnitId != adUnitId || cachedStyle != style) {
            container.visibility = View.GONE
            callback.onFailed("Native ad is not loaded")
            return
        }
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
            present(activity, container, ad, style, callback)
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
        destroy(container)
        if (!activity.isAlive() || !AdRequestGuard.canRequest(activity, adUnitId)) {
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
                container.visibility = View.VISIBLE
                callback.onLoaded()
            }
            .withNativeAdOptions(options)
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    if (container.getTag(R.id.zyrox_load_token) !== token) return
                    destroy(container)
                    container.visibility = View.GONE
                    callback.onFailed(loadAdError.message)
                }

                override fun onAdClicked() {
                    callback.onClicked()
                }

                override fun onAdImpression() {
                    AdsSdk.onImpression("native")
                    callback.onImpression()
                }
            })
            .build()
        loader.loadAd(AdRequest.Builder().build())
    }

    fun destroy(container: ViewGroup) {
        (container.getTag(R.id.zyrox_native_ad) as? NativeAd)?.destroy()
        container.setTag(R.id.zyrox_native_ad, null)
        container.setTag(R.id.zyrox_shimmer, null)
        container.setTag(R.id.zyrox_load_token, null)
        container.removeAllViews()
    }

    private fun present(
        activity: Activity,
        container: ViewGroup,
        nativeAd: NativeAd,
        style: NativeStyle,
        callback: NativeCallback
    ) {
        val adView = activity.layoutInflater.inflate(adLayout(style), container, false) as NativeAdView
        bind(nativeAd, adView)
        removeShimmer(container)
        container.removeAllViews()
        container.addView(adView)
        container.setTag(R.id.zyrox_native_ad, nativeAd)
        container.visibility = View.VISIBLE
        callback.onLoaded()
    }

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
