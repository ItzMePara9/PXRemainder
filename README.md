<div align="center">

# ⏰ PXReminder

### Native Android Alarm & Reminder App

**Remember. Schedule. Never Miss.**

A modern offline-first Android reminder app with persistent alarms, customizable ringtones, full-screen alerts, and smart scheduling.

<br>

![Version](https://img.shields.io/badge/version-1.0.0-6366f1?style=for-the-badge)
![Kotlin](https://img.shields.io/badge/Kotlin-Powered-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-Native-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-UI-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-22c55e?style=for-the-badge)

<br>

**Developed by [ItzMePara9](https://github.com/ItzMePara9)**

</div>

---

## 📖 Overview

**PXReminder** is a native, offline-first Android alarm and reminder application built with **Kotlin**, **Jetpack Compose**, **Material 3**, and **Room**. Reminders are scheduled as real Android alarms powered by `AlarmManager` and a dedicated foreground service (`AlarmService`) so they continue ringing independently of UI lifecycles until snoozed or dismissed.

Everything works locally — no accounts, no servers, no cloud services.

## ✨ Features

- ⏰ **True Alarm-Grade Scheduling** — Uses `AlarmManager.setAlarmClock()` when exact alarm permission is granted, with explicit `setAndAllowWhileIdle()` fallback and a clear **Approximate Mode** warning in the UI.
- 🔔 **Full-Screen Ringing Experience** — Dedicated `AlarmRingingActivity` with lock-screen display (`showWhenLocked`, `turnScreenOn`), live digital clock, multi-alarm queue handling, multiple snooze intervals (`5m`, `10m`, `15m`, `30m`), and dismiss actions.
- 📱 **Foreground Playback Service** — `AlarmService` (`foregroundServiceType="mediaPlayback"`) runs independently of the Activity lifecycle, loops audio via `AudioAttributes.USAGE_ALARM`, manages audio focus, supports gradual volume ramp, and drives continuous waveform vibration.
- 🎵 **Built-in & Custom Ringtones** — 5 bundled PCM WAV alarm tones (`Classic Alarm`, `Digital Pulse`, `Gentle Morning`, `Sharp Beep`, `Rising Tone`) plus zero-permission Storage Access Framework (`OpenDocument`) custom audio selection and system alarm tone picking with live preview.
- 🔁 **Multi-Schedule & Recurring Alarms** — `Never`, `Every day`, `Every weekday`, `Every week`, `Every month` (with month-end clamping), and `Custom days`, plus multiple scheduled date/time slots per reminder.
- 📅 **Calendar, History & Categories** — Monthly calendar with recurring event projection, completed reminder archive, alarm event log, and customizable category icons & colors.
- 🧩 **Home Screen Widget** — `PXReminderWidgetProvider` showing the next upcoming alarm and a quick-create shortcut.
- 💾 **Local JSON Backup & Restore** — Portable offline backup and validated restore for reminders, categories, and DataStore preferences.
- 🌙 **Dark-First & Light Themes** — Obsidian dark-first design inspired by Linear, Raycast, and Nothing OS, alongside a full light theme and system theme option.
- 🔒 **Privacy First** — All data stays on the device.

## 🛠️ Technology Stack

| Technology | Purpose |
|---|---|
| Kotlin | Programming language |
| Jetpack Compose | Native UI |
| Material 3 | Design system |
| Room | Local database |
| AlarmManager | Alarm scheduling |
| Foreground Service | Continuous alarm playback |
| DataStore | Preferences |
| Navigation Compose | Navigation |
| Moshi | JSON backup & restore |

## 🏗️ Architecture

- `data/` — Room entities (`ReminderEntity`, `CategoryEntity`, `AlarmHistoryEntity`), DAOs, `PXReminderDatabase` (with explicit `MIGRATION_1_2`), `ReminderRepository`, and `PreferencesRepository` (DataStore).
- `domain/` — Domain models, `RecurrenceCalculator` (DST-safe recurrence & calendar projection), and `TimeFormatters`.
- `alarm/` — `AlarmScheduler`, `AlarmReceiver`, `AlarmService`, `BootReceiver`, `AlarmNotificationManager`, `AlarmStateHolder`, and `AlarmRingingActivity`.
- `audio/` — `SoundCatalog` and `AlarmAudioManager`.
- `ui/` — Jetpack Compose screens (`dashboard`, `create`, `calendar`, `history`, `settings`, `ringing`), adaptive navigation (`PXReminderAppShell`), and theme system.
- `widget/` — `PXReminderWidgetProvider` and `WidgetUpdater`.
- `utils/` — `PermissionHelper`, `BackupManager` (Moshi JSON), and `CategoryIconCatalog`.

## 📥 Download & Install

**Download the latest APK from GitHub Releases.**
> ![Download](https://github.com/ItzMePara9/PXRemainder/releases/tag/v1.0.0)

1. Download the latest `.apk` file.
2. Open the downloaded APK on your Android device.
3. Allow **Install unknown apps** if Android asks.
4. Tap **Install**.
5. Open PXReminder and grant the required alarm and notification permissions.

## ⚠️ Android & Manufacturer Limitations

1. **Exact Alarms (Android 12+ / API 31+):** `SCHEDULE_EXACT_ALARM` may require user approval in **Settings → Apps → Special app access → Alarms & reminders**. When revoked, Android restricts `setAlarmClock()`; PXReminder transparently displays an **Approximate Mode** warning and schedules with `setAndAllowWhileIdle()`.
2. **Full-Screen Intents (Android 14+ / API 34+):** `USE_FULL_SCREEN_INTENT` can be toggled by the user in system settings. When denied, Android delivers a high-priority ongoing alarm notification with **Snooze** and **Dismiss** actions instead of automatically launching `AlarmRingingActivity`.
3. **OEM Battery Optimizations:** Some manufacturers (e.g., Xiaomi MIUI/HyperOS, Samsung One UI sleeping apps, Oppo ColorOS, Vivo FuntouchOS) aggressively restrict background services or auto-start after reboot. Whitelisting PXReminder from battery optimization ensures reliable `BOOT_COMPLETED` restoration and on-time wakeups.
4. **Device Reboot & Timezone Changes:** `BootReceiver` listens for `BOOT_COMPLETED`, `TIMEZONE_CHANGED`, `TIME_SET`, `DATE_CHANGED`, `MY_PACKAGE_REPLACED`, and `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` to reconstruct and reschedule all active alarms stored in Room.

## 🗺️ Roadmap

- [x] Project concept and architecture.
- [x] Reminder management.
- [x] Alarm scheduling engine.
- [x] Full-screen alarm interface.
- [x] Custom ringtone support.
- [x] Calendar integration.
- [x] Recurring reminders.
- [x] Backup and restore.
- [x] Android home-screen widget.
- [ ] Release APK published to GitHub Releases.

## 🤝 Contributing

Contributions and suggestions are welcome!

1. Fork the repository.
2. Create a feature branch.
3. Commit your changes.
4. Open a Pull Request.

## 📄 License

PXReminder is licensed under the **MIT License**.

Copyright © 2026 **ItzMePara9**.

See [LICENSE](LICENSE) for details.

---

<div align="center">

## ⏰ Developed by ItzMePara9

**Building tools. Creating solutions. Engineering possibilities.**

[GitHub](https://github.com/ItzMePara9)

<br>

If you like PXReminder, consider giving the repository a ⭐.

**Made with ❤️ by ItzMePara9**

</div>
