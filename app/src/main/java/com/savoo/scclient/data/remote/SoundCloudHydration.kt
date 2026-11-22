package com.savoo.scclient.data.remote

import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

private val HYDRATION = Regex("""window\.__sc_hydration\s*=\s*(\[[\s\S]*])\s*;?\s*$""")

internal fun soundCloudHydration(html: String): List<JSONObject> =
    Jsoup.parse(html).select("script").flatMap { script ->
        val match = HYDRATION.find(script.data()) ?: return@flatMap emptyList()
        val entries = runCatching { JSONArray(match.groupValues[1]) }.getOrNull() ?: return@flatMap emptyList()
        (0 until entries.length()).mapNotNull { entries.optJSONObject(it) }
    }
