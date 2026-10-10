package com.savoo.scclient.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.savoo.scclient.R
import com.savoo.scclient.data.model.GeniusAnnotation
import com.savoo.scclient.ui.haptics.rememberHapticTick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LyricsAnnotationSheet(
    annotations: List<GeniusAnnotation>,
    accent: androidx.compose.ui.graphics.Color,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                stringResource(R.string.lyrics_annotation_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            annotations.forEachIndexed { index, annotation ->
                if (index > 0) {
                    HorizontalDivider(Modifier.padding(vertical = 20.dp))
                }
                AnnotationBlock(annotation = annotation, accent = accent)
            }
        }
    }
}

@Composable
private fun AnnotationBlock(annotation: GeniusAnnotation, accent: androidx.compose.ui.graphics.Color) {
    val uriHandler = LocalUriHandler.current
    val haptic = rememberHapticTick()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(
            Modifier
                .padding(vertical = 14.dp)
                .padding(start = 14.dp)
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(accent),
        )
        Text(
            annotation.fragment.lines().filterNot { it.trim().startsWith("[") }.joinToString("\n").trim(),
            style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
        )
    }

    Spacer(Modifier.height(14.dp))
    Text(
        annotation.body,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )

    Spacer(Modifier.height(14.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (annotation.verified) {
            AnnotationChip(stringResource(R.string.lyrics_annotation_verified), Icons.Filled.Verified)
        }
        if (!annotation.reviewed) {
            AnnotationChip(stringResource(R.string.lyrics_annotation_unreviewed), null)
        }
        if (annotation.votes != 0) {
            AnnotationChip(stringResource(R.string.lyrics_annotation_votes, annotation.votes), null)
        }
    }

    val url = annotation.url
    if (url != null) {
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(
            onClick = { haptic(); runCatching { uriHandler.openUri(url) } },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.lyrics_annotation_open))
        }
    }
}

@Composable
private fun AnnotationChip(text: String, icon: ImageVector?) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}
