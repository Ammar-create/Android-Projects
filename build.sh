#!/bin/bash
set -e

# Use ANDROID_HOME from environment if set, otherwise use local path
ANDROID_HOME=${ANDROID_HOME:-/workspace/android-build/sdk}
JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-arm64}
BUILD_TOOLS=$ANDROID_HOME/build-tools/34.0.0
PLATFORM=$ANDROID_HOME/platforms/android-34

PROJECT_DIR=$1
if [ -z "$PROJECT_DIR" ]; then
  echo "Usage: ./build.sh <project_dir>"
  exit 1
fi

cd $PROJECT_DIR
BUILD_DIR=build
mkdir -p $BUILD_DIR/{obj,gen,apk,dex}

echo "Step 1: Compile resources with aapt2..."
$BUILD_TOOLS/aapt2 compile --dir res -o $BUILD_DIR/compiled_resources.zip

echo "Step 2: Link resources..."
$BUILD_TOOLS/aapt2 link \
  -o $BUILD_DIR/apk/app-unsigned.apk \
  -I $PLATFORM/android.jar \
  --manifest AndroidManifest.xml \
  --java $BUILD_DIR/gen \
  $BUILD_DIR/compiled_resources.zip \
  --auto-add-overlay

echo "Step 3: Compile Java sources..."
find src -name "*.java" > $BUILD_DIR/sources.txt
find $BUILD_DIR/gen -name "*.java" >> $BUILD_DIR/sources.txt
javac -d $BUILD_DIR/obj \
  -classpath $PLATFORM/android.jar \
  -sourcepath src \
  @$BUILD_DIR/sources.txt

echo "Step 4: Convert to DEX..."
find $BUILD_DIR/obj -name "*.class" > $BUILD_DIR/classes.txt
$BUILD_TOOLS/d8 --lib $PLATFORM/android.jar \
  --output $BUILD_DIR/dex \
  @$BUILD_DIR/classes.txt

echo "Step 5: Add DEX to APK..."
cd $BUILD_DIR/dex
zip -q ../../$BUILD_DIR/apk/app-unsigned.apk classes.dex
cd ../..

echo "Step 6: Align APK..."
$BUILD_TOOLS/zipalign -f 4 \
  $BUILD_DIR/apk/app-unsigned.apk \
  $BUILD_DIR/apk/app-aligned.apk

echo "Step 7: Sign APK..."
$BUILD_TOOLS/apksigner sign \
  --ks keystore.jks \
  --ks-pass pass:android \
  --out $BUILD_DIR/apk/app-signed.apk \
  $BUILD_DIR/apk/app-aligned.apk

echo "Done! APK: $BUILD_DIR/apk/app-signed.apk"
