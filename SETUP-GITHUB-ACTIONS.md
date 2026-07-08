# Building Android APKs with GitHub Actions

Since this workspace is ARM64 and Google only provides x86-64 Android build tools, 
we use GitHub Actions (free x86-64 Ubuntu runners) to build APKs automatically.

## Setup Steps

### 1. Create a GitHub Repository

```bash
cd /workspace/android-build
git init
git add .
git commit -m "Initial commit: Android APK builder"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/android-apk-builder.git
git push -u origin main
```

### 2. GitHub Actions Will Automatically:

- Set up JDK 17
- Install Android SDK (build-tools, platform-tools, API 34)
- Generate a debug keystore
- Run the build script
- Upload the signed APK as an artifact

### 3. Download Your APK

After pushing code:
1. Go to your repo on GitHub
2. Click "Actions" tab
3. Click the latest workflow run
4. Download the "app-signed" artifact
5. Extract the ZIP to get your APK

## Workflow File Created

`.github/workflows/build-apk.yml` - Already included in this project

## Triggers

The build runs on:
- Push to main/master branch
- Pull requests
- Manual trigger (Actions tab > Run workflow)

## What Gets Built

- Project: `projects/ReminderApp/`
- Output: `app-signed.apk`
- Features: Reminders, Notifications, File Storage

## Next Steps

1. Fix MainActivity.java (currently has formatting issues)
2. Push to GitHub
3. Wait 2-3 minutes for build
4. Download APK from Actions artifacts
5. Install on your Vivo S1 or Lenovo Tab M9

## Cost

FREE - GitHub Actions provides 2000 minutes/month for free accounts.
Each build takes about 2-3 minutes.

