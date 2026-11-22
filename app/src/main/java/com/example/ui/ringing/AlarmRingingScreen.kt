package com.example.ui.ringing

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlarmOff
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.models.ActiveAlarm
import com.example.domain.usecases.TimeFormatters
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceHigh
import com.example.ui.theme.SpaceGroteskFontFamily
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AlarmRingingScreen(
    activeAlarms: List<ActiveAlarm>,
    use24Hour: Boolean,
    onSnooze: (ActiveAlarm, Int) -> Unit,
    onDismiss: (ActiveAlarm) -> Unit,
    onDismissAll: () -> Unit
) {
    val currentAlarm = activeAlarms.firstOrNull() ?: return

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectedSnoozeMinutes by remember(currentAlarm.reminderId, currentAlarm.slotIndex) {
        mutableIntStateOf(currentAlarm.snoozeDurationMinutes)
    }

    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "alarm_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val zoneId = ZoneId.systemDefault()
    val zdt = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
    val clockPattern = if (use24Hour) "HH:mm" else "h:mm"
    val amPm = if (use24Hour) "" else zdt.format(DateTimeFormatter.ofPattern("a", Locale.getDefault()))
    val liveClockText = zdt.format(DateTimeFormatter.ofPattern(clockPattern, Locale.getDefault()))
    val currentDateText = TimeFormatters.formatFullDate(LocalDate.now(zoneId))
    val scheduledTimeText = TimeFormatters.formatTime(currentAlarm.scheduledTimeMillis, use24Hour)
    val categoryColor = Color(currentAlarm.categoryColorHex)

    val snoozeIntervals = remember(currentAlarm.snoozeDurationMinutes) {
        listOf(5, 10, 15, 30, currentAlarm.snoozeDurationMinutes)
            .distinct()
            .sorted()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top bar: Status indicator + Category pill + Multi-alarm queue indicator
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .alpha(pulseAlpha)
                            .clip(CircleShape)
                            .background(categoryColor)
                    )
                    Text(
                        text = if (currentAlarm.isSnooze) "SNOOZED ALARM" else stringResource(R.string.alarm_ringing_label),
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFA1A1AA)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    color = categoryColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, categoryColor.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            tint = categoryColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = currentAlarm.categoryName,
                            style = MaterialTheme.typography.labelLarge,
                            color = categoryColor
                        )
                    }
                }

                if (activeAlarms.size > 1) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "${activeAlarms.size} alarms active simultaneously",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFFA78BFA)
                    )
                }
            }

            // Center: Giant Digital Clock + Reminder Title + Description + Scheduled Time
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = liveClockText,
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 72.sp,
                        lineHeight = 76.sp,
                        letterSpacing = (-2).sp,
                        color = Color(0xFFF4F4F5),
                        modifier = Modifier.testTag("alarm_digital_clock")
                    )
                    if (amPm.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = amPm,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFA78BFA),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = currentDateText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFA1A1AA)
                )

                Spacer(modifier = Modifier.height(28.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, ObsidianOutline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "SCHEDULED $scheduledTimeText",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFA78BFA)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = currentAlarm.title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color(0xFFF4F4F5),
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("alarm_title_text")
                        )
                        if (currentAlarm.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = currentAlarm.description,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color(0xFFA1A1AA),
                                textAlign = TextAlign.Center,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("alarm_description_text")
                            )
                        }
                    }
                }
            }

            // Bottom Actions: Snooze Interval Selector + SNOOZE & DISMISS buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "SNOOZE DURATION",
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF71717A)
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    snoozeIntervals.forEach { minutes ->
                        val selected = selectedSnoozeMinutes == minutes
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedSnoozeMinutes = minutes }
                                .testTag("snooze_interval_$minutes"),
                            color = if (selected) Color(0xFF251B42) else ObsidianSurfaceHigh,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (selected) Color(0xFFA78BFA) else ObsidianOutline
                            )
                        ) {
                            Text(
                                text = "${minutes}m",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) Color(0xFFE9DFFE) else Color(0xFFA1A1AA),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { onSnooze(currentAlarm, selectedSnoozeMinutes) },
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .testTag("alarm_snooze_button"),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, ObsidianOutline),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = ObsidianSurface,
                            contentColor = Color(0xFFF4F4F5)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Snooze,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${stringResource(R.string.alarm_snooze)} ${selectedSnoozeMinutes}m",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    Button(
                        onClick = { onDismiss(currentAlarm) },
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .testTag("alarm_dismiss_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFA78BFA),
                            contentColor = Color(0xFF120A2A)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AlarmOff,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.alarm_dismiss),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (activeAlarms.size > 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onDismissAll) {
                        Text(
                            text = "Dismiss all ${activeAlarms.size} alarms",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFFA1A1AA)
                        )
                    }
                }
            }
        }
    }
}
