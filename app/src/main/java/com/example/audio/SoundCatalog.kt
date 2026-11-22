package com.example.audio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.R
import com.example.domain.models.AlarmSoundOption

object SoundCatalog {

    const val URI_CLASSIC_ALARM = "builtin:classic_alarm"
    const val URI_DIGITAL_PULSE = "builtin:digital_pulse"
    const val URI_GENTLE_MORNING = "builtin:gentle_morning"
    const val URI_SHARP_BEEP = "builtin:sharp_beep"
    const val URI_RISING_TONE = "builtin:rising_tone"

    val builtInSounds: List<AlarmSoundOption> = listOf(
        AlarmSoundOption(
            uri = URI_CLASSIC_ALARM,
            title = "Classic Alarm",
            subtitle = "Crisp twin-tone cadence",
            isBuiltIn = true,
            rawResId = R.raw.classic_alarm
        ),
        AlarmSoundOption(
            uri = URI_DIGITAL_PULSE,
            title = "Digital Pulse",
            subtitle = "Minimalist rhythmic pulse",
            isBuiltIn = true,
            rawResId = R.raw.digital_pulse
        ),
        AlarmSoundOption(
            uri = URI_GENTLE_MORNING,
            title = "Gentle Morning",
            subtitle = "Warm harmonic chime",
            isBuiltIn = true,
            rawResId = R.raw.gentle_morning
        ),
        AlarmSoundOption(
            uri = URI_SHARP_BEEP,
            title = "Sharp Beep",
            subtitle = "High-clarity alert",
            isBuiltIn = true,
            rawResId = R.raw.sharp_beep
        ),
        AlarmSoundOption(
            uri = URI_RISING_TONE,
            title = "Rising Tone",
            subtitle = "Ascending frequency sweep",
            isBuiltIn = true,
            rawResId = R.raw.rising_tone
        )
    )

    fun resolveRawResId(soundUri: String): Int? =
        builtInSounds.firstOrNull { it.uri == soundUri }?.rawResId

    fun resolveTitle(context: Context, soundUri: String, fallbackTitle: String = "Custom Sound"): String {
        builtInSounds.firstOrNull { it.uri == soundUri }?.let { return it.title }
        return try {
            val uri = Uri.parse(soundUri)
            if (uri.scheme == "content") {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) {
                            val name = cursor.getString(idx)
                            if (!name.isNullOrBlank()) {
                                return name.substringBeforeLast(".")
                            }
                        }
                    }
                }
            }
            val ringtone = android.media.RingtoneManager.getRingtone(context, uri)
            ringtone?.getTitle(context)?.takeIf { it.isNotBlank() } ?: fallbackTitle
        } catch (_: Exception) {
            fallbackTitle
        }
    }
}
