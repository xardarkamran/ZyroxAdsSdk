package com.fixton.zyroxadssdk

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.fixton.zyroxadssdk.internal.AdRequestGuard
import com.fixton.zyroxadssdk.internal.isAlive
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/**
 * Loads one banner into [container].
 * A shimmer is shown until the ad loads. On failure the shimmer is removed
 * and the container is hidden.
 *
 * Call [destroy] from the screen's onDestroy.
 */
object BannerAds {

    fun load(
        activity: Activity,
        adUnitId: String,
        container: ViewGroup,
        type: BannerType,
        inlineMaxHeightDp: Int = 250,
        showShimmer: Boolean = true,
        callback: BannerCallback = object : BannerCallback {}
    ) {
        destroy(container)
        if (!activity.isAlive() || !AdRequestGuard.canRequest(activity, adUnitId)) {
            container.visibility = View.GONE
            callback.onFailed("Ad request blocked")
            return
        }
        container.visibility = View.VISIBLE
        if (showShimmer) {
            val shimmer = activity.layoutInflater.inflate(
                R.layout.zyrox_shimmer_banner,
                container,
                false
            )
            shimmer.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                shimmerHeightPx(activity, type, inlineMaxHeightDp)
            )
            container.addView(shimmer)
            container.setTag(R.id.zyrox_shimmer, shimmer)
        }
        val adView = AdView(activity)
        adView.adUnitId = adUnitId
        adView.setAdSize(adSize(activity, type, inlineMaxHeightDp))
        container.setTag(R.id.zyrox_banner_view, adView)
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                if (container.getTag(R.id.zyrox_banner_view) !== adView) {
                    adView.destroy()
                    return
                }
                removeShimmer(container)
                if (adView.parent == null) {
                    container.addView(
                        adView,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )
                }
                container.visibility = View.VISIBLE
                callback.onLoaded()
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                if (container.getTag(R.id.zyrox_banner_view) !== adView) return
                destroy(container)
                container.visibility = View.GONE
                callback.onFailed(adError.message)
            }

            override fun onAdClicked() {
                callback.onClicked()
            }

            override fun onAdImpression() {
                AdsSdk.onImpression("banner")
                callback.onImpression()
            }
        }
        adView.loadAd(request(type))
    }

    fun pause(container: ViewGroup) {
        (container.getTag(R.id.zyrox_banner_view) as? AdView)?.pause()
    }

    fun resume(container: ViewGroup) {
        (container.getTag(R.id.zyrox_banner_view) as? AdView)?.resume()
    }

    fun destroy(container: ViewGroup) {
        (container.getTag(R.id.zyrox_banner_view) as? AdView)?.destroy()
        container.setTag(R.id.zyrox_banner_view, null)
        container.setTag(R.id.zyrox_shimmer, null)
        container.removeAllViews()
    }

    private fun request(type: BannerType): AdRequest {
        val builder = AdRequest.Builder()
        if (type == BannerType.COLLAPSIBLE_BOTTOM || type == BannerType.COLLAPSIBLE_TOP) {
            val extras = Bundle()
            extras.putString(
                "collapsible",
                if (type == BannerType.COLLAPSIBLE_TOP) "top" else "bottom"
            )
            builder.addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
        }
        return builder.build()
    }

    private fun adSize(activity: Activity, type: BannerType, inlineMaxHeightDp: Int): AdSize {
        val width = screenWidthDp(activity)
        return when (type) {
            BannerType.ANCHORED_ADAPTIVE,
            BannerType.COLLAPSIBLE_BOTTOM,
            BannerType.COLLAPSIBLE_TOP ->
                AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, width)

            BannerType.INLINE_ADAPTIVE ->
                AdSize.getInlineAdaptiveBannerAdSize(width, inlineMaxHeightDp)

            BannerType.BANNER -> AdSize.BANNER
            BannerType.LARGE_BANNER -> AdSize.LARGE_BANNER
            BannerType.MEDIUM_RECTANGLE -> AdSize.MEDIUM_RECTANGLE
            BannerType.FULL_BANNER -> AdSize.FULL_BANNER
            BannerType.LEADERBOARD -> AdSize.LEADERBOARD
        }
    }

    private fun shimmerHeightPx(activity: Activity, type: BannerType, inlineMaxHeightDp: Int): Int {
        val dp = when (type) {
            BannerType.BANNER -> 50
            BannerType.LARGE_BANNER -> 100
            BannerType.MEDIUM_RECTANGLE -> 250
            BannerType.FULL_BANNER -> 60
            BannerType.LEADERBOARD -> 90
            BannerType.INLINE_ADAPTIVE -> inlineMaxHeightDp.coerceAtMost(250)
            else -> 60
        }
        return (dp * activity.resources.displayMetrics.density).toInt()
    }

    private fun screenWidthDp(activity: Activity): Int {
        val widthPixels = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.windowManager.currentWindowMetrics.bounds.width()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay.getMetrics(metrics)
            metrics.widthPixels
        }
        val density = activity.resources.displayMetrics.density
        return (widthPixels / density).toInt().coerceAtLeast(1)
    }

    private fun removeShimmer(container: ViewGroup) {
        (container.getTag(R.id.zyrox_shimmer) as? View)?.let { container.removeView(it) }
        container.setTag(R.id.zyrox_shimmer, null)
    }
}
