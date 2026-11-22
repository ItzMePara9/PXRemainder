package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.models.PermissionHealth
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SemanticWarning

@Composable
fun PermissionStatusBanner(
    health: PermissionHealth,
    onRequestNotifications: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenFullScreenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (health.allGranted) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("permission_banner"),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, SemanticWarning.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = SemanticWarning,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ALARM PERMISSIONS REQUIRED",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelMedium,
                    color = SemanticWarning
                )
            }

            if (!health.notificationsGranted) {
                PermissionActionItem(
                    title = stringResource(R.string.perm_notifications_title),
                    body = stringResource(R.string.perm_notifications_desc),
                    actionLabel = stringResource(R.string.action_grant),
                    onAction = onRequestNotifications
                )
            }

            if (!health.exactAlarmGranted) {
                if (!health.notificationsGranted) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                PermissionActionItem(
                    title = stringResource(R.string.perm_exact_alarm_title),
                    body = stringResource(R.string.perm_exact_alarm_desc),
                    actionLabel = stringResource(R.string.action_open_settings),
                    onAction = onOpenExactAlarmSettings
                )
            }

            if (!health.fullScreenIntentGranted) {
                if (!health.notificationsGranted || !health.exactAlarmGranted) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                PermissionActionItem(
                    title = stringResource(R.string.perm_fullscreen_title),
                    body = stringResource(R.string.perm_fullscreen_desc),
                    actionLabel = stringResource(R.string.action_open_settings),
                    onAction = onOpenFullScreenSettings
                )
            }
        }
    }
}

@Composable
private fun PermissionActionItem(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onAction,
                border = BorderStroke(1.dp, SemanticWarning.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text(text = actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
