package com.savoo.scclient.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.res.pluralStringResource
import com.savoo.scclient.R

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun followersCountText(count: Long): String =
    pluralStringResource(R.plurals.followers_count, count.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(), count)
