#!/bin/bash
echo "=== Android Build Environment Verification ==="
echo ""
echo "Java version:"
java -version 2>&1 | head -1
echo ""
echo "Android SDK Manager:"
export ANDROID_HOME=/workspace/android-build/sdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --version
echo ""
echo "Build tools available:"
ls -1 $ANDROID_HOME/build-tools/34.0.0/ | grep -E "aapt2|d8|zipalign|apksigner"
echo ""
echo "Platform:"
ls -1 $ANDROID_HOME/platforms/
echo ""
echo "Total SDK size:"
du -sh $ANDROID_HOME
echo ""
echo "✅ Installation complete! Ready to build Android apps."
