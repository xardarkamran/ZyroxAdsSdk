package com.fixton.zyroxadssdk

interface NativeCallback {
    fun onLoaded() {}
    fun onFailed(error: String) {}
    fun onClicked() {}
    fun onImpression() {}
}
