package com.fixton.zyroxadssdk

enum class BannerType {
    /** Anchored adaptive banner sized to the current screen width. */
    ANCHORED_ADAPTIVE,

    /** Inline adaptive banner. Pass max height to [BannerAds.load]. */
    INLINE_ADAPTIVE,

    /** Collapsible banner anchored to the bottom of the container. */
    COLLAPSIBLE_BOTTOM,

    /** Collapsible banner anchored to the top of the container. */
    COLLAPSIBLE_TOP,

    /** 320x50. */
    BANNER,

    /** 320x100. */
    LARGE_BANNER,

    /** 300x250. */
    MEDIUM_RECTANGLE,

    /** 468x60. */
    FULL_BANNER,

    /** 728x90. */
    LEADERBOARD
}
