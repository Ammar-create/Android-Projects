# Android APK Builder with GitHub Actions

Build Android APKs automatically using GitHub Actions (bypasses ARM64 limitation).

## Quick Start

1. Push this repo to GitHub
2. GitHub Actions builds the APK automatically
3. Download from Actions artifacts
4. Install on your Android device

See `SETUP-GITHUB-ACTIONS.md` for detailed instructions.

## Project Structure

- `projects/ReminderApp/` - Sample app with reminders, notifications, file storage
- `.github/workflows/build-apk.yml` - GitHub Actions workflow
- `build.sh` - Build script (runs on GitHub Actions)

## What This App Does

- Set reminders with AlarmManager
- Show notifications
- Save files to `/sdcard/ReminderApp/`
- Material Design UI

## Requirements

- GitHub account (free)
- Android device to install APK

Build time: 2-3 minutes per push
