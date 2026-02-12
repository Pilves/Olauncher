# Olauncher

A minimal, text-based Android launcher designed to help you use your phone less and more intentionally.

No icons, no clutter — just the apps you need, the habits you want to build, and the tools to stay in control of your screen time.

## Features

### Minimal Home Screen
- Text-only home screen with up to 8 pinned apps
- Swipe gestures: up for app drawer, down for notifications or search, left/right for configurable actions
- Date, time, battery, and weather at a glance
- Dark, light, or system theme with automatic scheduling (manual times or sunrise/sunset)
- Configurable text size, alignment, and daily wallpaper rotation

### Widgets
- Add multiple widgets to your home screen (long-press > Add widget)
- Per-widget height control with 5 size presets
- Swap, remove, resize, reorder, or place widgets above or below your app list
- Search and filter in the widget picker

### Digital Wellbeing
- **Focus Mode** — Timed sessions (25m, 1h, 2h) that block all but 5 whitelisted apps
- **Grayscale Mode** — One-tap grayscale filter over the entire launcher
- **Screen Time Graph** — 7-day usage bar chart, tap the home screen time display to view
- **Screen Time Limits** — Soft per-app time limits with toast warnings when exceeded
- **Bad Habit Apps** — Mark distracting apps with a daily time limit; a confirmation dialog asks "Open anyway?" once the limit is reached
- **Habit Streaks** — Mark apps as daily habits; streak counter shown in the app drawer, resets if you skip a day
- **Configurable Double-Tap** — Lock screen, open app, notifications, search, camera, flashlight, or nothing

### App Drawer
- Auto-launch apps by typing in the search bar
- Per-app daily usage time shown next to each app
- Sort apps by usage time
- Long-press menu: delete, rename, hide, mark as habit, mark as bad habit, app info
- App renaming and per-profile (work profile) support

### Gestures & Shortcuts
- **Custom Swipe Actions** — Left and right swipes can open an app, notifications, search, lock screen, camera, flashlight, or do nothing
- **Gesture Letters** — Draw letter shapes (A, C, L, M, N, O, S, V, W, Z) on the home screen to launch assigned apps
- **Swipe-Up Apps** — Assign a per-slot swipe-up companion app to any home screen slot
- **App Folders** — Group up to 4 apps into a named folder on any home screen slot
- **Quick Notes** — Pin a note to a home screen slot, tap to edit

### Personalization
- **Icon Packs** — Load icons from third-party ADW-compatible icon packs
- **Weather Display** — Current temperature on the home screen date line via Open-Meteo (free, no API key)
- **Theme Schedule** — Automatic dark/light switching by time or sunrise/sunset
- **Settings Backup & Restore** — Export/import all settings as JSON; Android auto-backup enabled

## Building

```
# Requires Android SDK (min SDK 24, target 35)
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Origins

Originally forked from [Olauncher](https://github.com/tanujnotes/Olauncher) by [tanujnotes](https://github.com/tanujnotes). This project has since diverged significantly with a full digital wellbeing suite, multi-widget system, gesture shortcuts, and many other features.

## License

[GNU GPLv3](https://www.gnu.org/licenses/gpl-3.0.en.html)
