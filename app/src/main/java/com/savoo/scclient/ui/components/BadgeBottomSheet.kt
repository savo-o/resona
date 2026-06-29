package com.savoo.scclient.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class BadgeInfo(
    val name: String,
    val title: String,
    val description: String,
)

val badgeInfoMap = mapOf(
    "developer" to BadgeInfo(
        name = "developer",
        title = "Developer Badge",
        description = "This user is a developer of Resona.",
    ),
    "supporter" to BadgeInfo(
        name = "supporter",
        title = "Supporter Badge",
        description = "This user is a supporter of Resona.",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadgeBottomSheet(
    badge: String,
    profileName: String,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val info = badgeInfoMap[badge] ?: BadgeInfo(badge, badge, "Unknown badge.")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = info.title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "$profileName received the ${info.name} badge.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = info.description,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}
