# Olauncher (Modified Fork)

A minimal, text-based Android launcher. Forked from [tanujnotes/Olauncher](https://github.com/tanujnotes/Olauncher) with added features and cleanup.

## What's different from upstream

### Multi-widget support
- Add multiple widgets to your home screen (long-press > Add widget)
- Swap, remove, resize, or reorder widgets (long-press on a widget)
- Per-widget height control: Small (100dp), Medium (200dp), Large (300dp)
- Choose widget placement: above or below your app list (in Settings)
- Search/filter in the widget picker to quickly find widgets

### Custom gestures
- Swipe left and right can be mapped to different actions, not just apps
- Available actions: Open app, Notifications, Search, Lock screen, Camera, Flashlight, None
- Configure in Settings under the Gestures section

### Per-app screen time in app drawer
- Daily usage time shown next to each app in the drawer
- Sort apps by usage time (toggle in Settings, requires usage permission)
- Requires usage access permission (same as the home screen total)

### Settings backup & restore
- Export all settings to a JSON file in your Downloads folder
- Import settings from a previously exported file
- Widget data is excluded (device-specific and not transferable)
- Android auto-backup enabled for seamless cloud restore

### Code quality
- Prefs.kt refactored with indexed accessors, eliminating 160+ lines of boilerplate
- Deprecated `onActivityResult`/`startActivityForResult` migrated to `ActivityResultLauncher`
- All widget error strings moved to `strings.xml` for localization
- Widget picker uses theme-aware colors (no more invisible text in dark mode)

### Cleanup
- Removed custom dialog overlay system, uses standard Android AlertDialogs
- Removed permanently-visible first-run tips that were never dismissed
- Screen time display moved inline next to the clock instead of floating
- Removed promotional links and cross-app advertising

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
