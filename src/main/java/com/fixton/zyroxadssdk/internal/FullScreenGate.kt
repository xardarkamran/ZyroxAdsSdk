package com.fixton.zyroxadssdk.internal

/**
 * Keeps two full-screen ads from opening at the same time.
 * Also skips the next app-open ad when the user is returning from an ad.
 */
internal object FullScreenGate {
    @Volatile
    var isShowing: Boolean = false

    @Volatile
    var skipNextAppOpen: Boolean = false
}
