package com.fixton.zyroxadssdk

enum class NativeStyle {
    /** Full-width media on top, icon and text below, full-width call to action. */
    LARGE_MEDIA,

    /** Square media on the left, text and call to action on the right. */
    MEDIUM_MEDIA,

    /** Same text block as [LARGE_MEDIA], without a media view. */
    LARGE,

    /** Icon, headline, body, and a full-width call to action. No media. */
    MEDIUM,

    /** Compact row: icon, text, rating, and a side call to action. No media. */
    SMALL
}
