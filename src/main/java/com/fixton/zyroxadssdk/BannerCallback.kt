package com.fixton.zyroxadssdk

interface BannerCallback {
    fun onLoaded() {}
    fun onFailed(error: String) {}
    fun onClicked() {}
    fun onImpression() {}
}
