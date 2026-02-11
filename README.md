# Olauncher (Modified Fork)

A minimal, text-based Android launcher. Forked from [tanujnotes/Olauncher](https://github.com/tanujnotes/Olauncher) with extensive new features and polish while preserving the minimalist philosophy.

## What's different from upstream

### Multi-widget support
- Add multiple widgets to your home screen (long-press > Add widget)
- Swap, remove, resize, or reorder widgets (long-press on a widget)
- Per-widget height control: Small, Medium, Large, Extra large, Full
- Choose widget placement: above or below your app list
- Search/filter in the widget picker
- Widget providers saved for automatic restore after reinstall

### Focus Mode
- Timed focus sessions: 25 minutes, 1 hour, 2 hours, or until manually disabled
- Whitelist up to 5 apps that remain accessible during focus
- Blocked apps show a toast notification instead of launching
- Toggle from the home long-press menu or Settings > Wellbeing

### Grayscale Mode
- One-tap grayscale filter over the entire launcher
- Hardware-accelerated color matrix for zero performance impact
- Toggle from the home long-press menu or Settings > Wellbeing

### App Folders
- Group up to 4 apps into a named folder on any home screen slot
- Tap a folder to expand it as a bottom sheet with app list
- Folder contents launch normally with full gesture support

### Quick Notes
- Assign any home screen slot as a quick note
- Tap to open a bottom sheet editor, note preview shown on the home screen
- Notes persist across sessions

### Weather Display
- Current temperature shown on the home screen date line
- Uses Open-Meteo API (free, no API key required)
- Configure latitude/longitude in Settings > Wellbeing
- 1-hour cache to minimize network usage

### Screen Time Limits
- Set soft time limits per app: 15m, 30m, 1h, 2h, or unlimited
- Toast warning when limit is exceeded (never blocks launch)
- Configure from the screen time graph dialog

### Screen Time Graph
- Tap the screen time display to see a 7-day usage bar chart
- Visual breakdown by day with hours/minutes labels

### Habit Streaks
- Mark any app as a habit in the app drawer (long-press menu)
- Daily streak counter shown next to usage time in the drawer
- Streaks reset if you skip a day

### Gesture Letters
- Draw letter shapes on the home screen to launch assigned apps
- Supports 10 letters: A, C, L, M, N, O, S, V, W, Z
- Assign apps to letters in Settings > Wellbeing
- Semi-transparent trail drawn during gesture

### Swipe-Up Apps
- Assign a per-slot swipe-up app to any home screen app
- Swipe up on an app to launch its assigned companion app
- Falls back to the app drawer if no swipe-up app is set

### Configurable Double-Tap
- Double-tap action is no longer limited to lock screen
- Available actions: Lock screen, Open app, Notifications, Search, Camera, Flashlight, None
- Configure in Settings > Wellbeing

### Theme Schedule
- Automatic dark/light theme switching
- Three modes: Manual, Scheduled (set light/dark times), Sunrise/Sunset (location-based)
- Uses WorkManager for reliable background switching

### Custom Gestures
- Swipe left and right can be mapped to different actions, not just apps
- Available actions: Open app, Notifications, Search, Lock screen, Camera, Flashlight, None

### Icon Packs
- Load icons from third-party icon packs (ADW-compatible)
- Show app icons as compound drawables on home screen text

### Per-app Screen Time in App Drawer
- Daily usage time shown next to each app in the drawer
- Sort apps by usage time (toggle in Settings)
- Usage-sorted order cached for instant display

### Settings Backup & Restore
- Export all settings to a JSON file in Downloads
- Import settings from a previously exported file
- Widget and device-specific data excluded from export
- Android auto-backup enabled

### UI Polish
- All menus use Material bottom sheets with drag handles
- Consistent theme-aware styling throughout
- Wellbeing settings section for all mindfulness features

### Code Quality
- Prefs.kt refactored with indexed accessors
- Deprecated APIs migrated to ActivityResultLauncher
- All strings in strings.xml for localization readiness
- Thread-safe singleton managers for all new features

## Features (from original)

- Text-only home screen with up to 8 pinned apps
- Swipe gestures: up for app drawer, left/right for configurable actions, down for notifications or search
- Double-tap to lock screen
- Auto-launch apps by typing in the drawer
- App renaming, hiding, and per-profile support
- Daily wallpaper rotation
- Date, time, and battery on home screen
- Dark / light / system theme
- Configurable text size and alignment

## Building

```
# Requires Android SDK (min SDK 24, target 35)
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## License

[GNU GPLv3](https://www.gnu.org/licenses/gpl-3.0.en.html)

Based on [Olauncher](https://github.com/tanujnotes/Olauncher) by [tanujnotes](https://github.com/tanujnotes).
