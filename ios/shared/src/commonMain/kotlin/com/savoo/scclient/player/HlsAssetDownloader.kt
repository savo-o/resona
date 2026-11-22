package com.savoo.scclient.player

import com.savoo.scclient.compat.File

internal expect suspend fun downloadHlsAsset(url: String, title: String, onProgress: ((Float) -> Unit)?): File
