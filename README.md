# ZyroxAdsSdk

Android library for AdMob app-open, interstitial, banner, and native ads.

Ad unit ids stay in each app. This library does not ship production ids.

## Gradle

Add JitPack, then the dependency.

```gradle
maven { url 'https://jitpack.io' }

implementation 'com.github.xardarkamran:ZyroxAdsSdk:1.0.2'
```

Call `AdsSdk.init` only after the ads consent form has finished.

```kotlin
AdsSdk.init(
    application = this,
    adsEnabled = { !isPremium }
)
```

When `adsEnabled` returns false, no request is sent and the screen callback still runs.

## Interstitial

Every interstitial shows a full-screen **Showing Ad...** dialog for 1.5 seconds before the ad. If the ad is not ready, the dialog is skipped and the dismiss callback runs so navigation is not blocked. Two full-screen ads are never shown together.

### Load and show

Load when the screen opens. Show on a button click. The ad is not loaded again after dismiss.

```kotlin
LoadAndShowInterstitial.load(this, adUnitId) { loaded -> }

LoadAndShowInterstitial.show(this) {
    goNext()
}
```

### On demand

The click starts the request. The dialog stays on screen while the ad loads, and for at least 1.5 seconds. Nothing is cached.

```kotlin
OnDemandInterstitial.show(
    activity = this,
    adUnitId = adUnitId,
    onDismissed = { goNext() },
    onFailed = { goNext() }
)
```

### Preloaded

One shared ad for the whole app. Load it once. Show it from any screen. After dismiss, the same ad unit id loads again.

```kotlin
PreloadedInterstitial.load(this, adUnitId)

PreloadedInterstitial.show(this) {
    goNext()
}
```

## App open

Put the splash activity in `skipActivities`. The ad is not shown while an interstitial is visible, and it is not shown when the user is returning from an ad.

```kotlin
AppOpenAds.attach(
    application = this,
    adUnitId = openAppId,
    skipActivities = setOf(SplashActivity::class.java)
)
```

## Banner

Shimmer is removed when the ad loads and when it fails. On failure the container is hidden.

```kotlin
BannerAds.load(
    activity = this,
    adUnitId = bannerId,
    container = binding.bannerFrame,
    type = BannerType.COLLAPSIBLE_BOTTOM,
    callback = object : BannerCallback {
        override fun onFailed(error: String) {}
    }
)
```

Call `BannerAds.destroy(container)` in `onDestroy`. Also call `pause` and `resume` with the activity.

`BannerType`: `ANCHORED_ADAPTIVE`, `INLINE_ADAPTIVE`, `COLLAPSIBLE_BOTTOM`, `COLLAPSIBLE_TOP`, `BANNER`, `LARGE_BANNER`, `MEDIUM_RECTANGLE`, `FULL_BANNER`, `LEADERBOARD`.

Place a collapsible banner at the top or bottom of the screen.

## Native

```kotlin
NativeAds.load(
    activity = this,
    adUnitId = nativeId,
    container = binding.nativeFrame,
    style = NativeStyle.LARGE_MEDIA
)
```

To load on one screen and display on a later screen:

```kotlin
NativeAds.preload(this, nativeId, NativeStyle.MEDIUM_MEDIA)

NativeAds.showPreloaded(this, container, nativeId, NativeStyle.MEDIUM_MEDIA)
```

`showPreloaded` shows that ad only when the ad unit id and style match. It does not send a new request. If the preload is still running, the container shows a shimmer and then the ad.

`NativeStyle`:

| Style | Layout |
|---|---|
| `LARGE_MEDIA` | Full-width media on top, icon and text, full-width button |
| `MEDIUM_MEDIA` | 120dp media on the left, text and button on the right |
| `LARGE` | Large layout without media |
| `MEDIUM` | Icon, text, and button, without media |
| `SMALL` | Compact row without media |

Call `NativeAds.destroy(container)` in `onDestroy`. Shimmer is removed on load and on failure. On failure the container is hidden.

## Colors

Override these color resources in the app to match the app theme:

`zyrox_ad_background`, `zyrox_ad_headline`, `zyrox_ad_body`, `zyrox_ad_cta`, `zyrox_ad_cta_text`, `zyrox_ad_badge`, `zyrox_ad_badge_text`.

Override `zyrox_showing_ad` to change the dialog text.
