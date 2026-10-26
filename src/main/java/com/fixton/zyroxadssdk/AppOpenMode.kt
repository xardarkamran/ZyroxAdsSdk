package com.fixton.zyroxadssdk

/** How [AppOpenAds.attach] gets the ad it shows when the app returns to the foreground. */
enum class AppOpenMode {
    /** Loads an ad in advance, and the next one after each is closed; the return shows it at once. */
    PRELOAD,

    /**
     * Loads nothing in advance. On each return the "Showing ad" screen is shown while an ad is requested, and the ad
     * is shown once it loads. No request is spent on an ad that is never shown.
     */
    ON_DEMAND
}
