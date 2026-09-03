package com.savoo.scclient.debug

import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageResult

class ScreenshotModeInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        if (ScreenshotModeState.enabled.value) {
            return ErrorResult(
                drawable = null,
                request = chain.request,
                throwable = ScreenshotModeSuppressedException(),
            )
        }
        return chain.proceed(chain.request)
    }
}

class ScreenshotModeSuppressedException : Exception("Screenshot mode: artwork loading suppressed")
