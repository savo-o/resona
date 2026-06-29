package com.savoo.scclient.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BadgeEntry(
    val badges: List<String> = emptyList(),
)
