package com.savoo.scclient.data.remote

import com.savoo.scclient.compat.JSONArray
import com.savoo.scclient.compat.JSONObject
import com.fleeksoft.ksoup.Ksoup

private val HYDRATION = Regex("""window\.__sc_hydration\s*=\s*(\[[\s\S]*])\s*;?\s*$""")

internal fun soundCloudHydration(html: String): List<JSONObject> =
    Ksoup.parse(html).select("script").flatMap { script ->
        val match = HYDRATION.find(script.data()) ?: return@flatMap emptyList()
        val entries = runCatching { JSONArray(match.groupValues[1]) }.getOrNull() ?: return@flatMap emptyList()
        (0 until entries.length()).mapNotNull { entries.optJSONObject(it) }
    }
