# Build Lessons Learned

## Issues We Fixed (in order)

1. **Deprecated GitHub Actions (v3)**
   - Fixed: Updated to v4 versions
   - Workflow now uses latest stable actions

2. **Keystore conflict**
   - Fixed: Added rm -f keystore.jks before generation
   - Prevents alias collision

3. **Hardcoded SDK paths**
   - Fixed: Use environment variables with fallback
   - Works on both GitHub Actions and local

4. **Globstar patterns (build/gen/**/R.java)**
   - Fixed: Use find commands instead
   - Cross-shell commport (annative android.app.Notification
   - No AndroidX dependency needed

6. **Missing dex directory**
   - Fixed: Create build/dex in mkdir
   - d8 needs output dir to exist

## Best Practices for New Projects

- Use native Android APIs, avoid AndroidX
- Use find instead of ** globs
- Always create output directories first
- Test builds incrementally
- Keep GitHub Actions updated

## Working Configuration

- API Level: 34
- Build Tools: 34.0.0
- JDK: 17
- Min SDK: 26
- Target SDK: 34

