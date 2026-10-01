#!/bin/bash
# Build manual APK Struk Belanja tanpa Gradle.
# Pipeline: aapt2 -> kotlinc -> d8 -> zip (dex+assets+so) -> zipalign -> apksigner
# Pemakaian: ./build-apk.sh [nama-output.apk]
set -euo pipefail

PROJ="$(cd "$(dirname "$0")" && pwd)"
export PATH="$HOME/workspace/jdk17/jdk-17.0.11+9/bin:$PATH"
SRC="$PROJ/app/src/main"
SDK="$HOME/workspace/android-sdk"
BT="$SDK/build-tools/34.0.0"
ANDROID_JAR="$SDK/platforms/android-35/android.jar"
KOTLINC="$HOME/workspace/kotlinc/kotlinc/bin/kotlinc"
STDLIB="$HOME/workspace/kotlinc/kotlinc/lib/kotlin-stdlib.jar"
KEYSTORE="$PROJ/release.keystore"
OUT_APK="${1:-$PROJ/app-release.apk}"
BUILD="$PROJ/.build-tmp"

# keystore khusus aplikasi ini (dibuat sekali, tidak di-commit)
if [ ! -f "$KEYSTORE" ]; then
  echo "[0/7] membuat keystore baru..."
  keytool -genkeypair -keystore "$KEYSTORE" -alias appkey \
    -keyalg RSA -keysize 2048 -validity 10950 \
    -storepass android -keypass android \
    -dname "CN=DENNI, OU=StrukBelanja, O=id.my.sir, C=ID" 2>/dev/null
fi

# dependensi AAR
if ! ls "$PROJ/libs"/*.aar >/dev/null 2>&1; then
  "$PROJ/download-deps.sh"
fi

rm -rf "$BUILD"
mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/dex" "$BUILD/assets" "$BUILD/aar"

echo "[1/7] ekstrak AAR (classes.jar, assets, jni)..."
LIBJARS=()
for aar in "$PROJ"/libs/*.aar; do
  name="$(basename "$aar" .aar)"
  d="$BUILD/aar/$name"
  mkdir -p "$d"
  unzip -q -o "$aar" -d "$d"
  [ -f "$d/classes.jar" ] && LIBJARS+=("$d/classes.jar")
  [ -d "$d/assets" ] && cp -r "$d/assets/." "$BUILD/assets/"
done
# JAR polos di libs/ ikut jadi dependensi
for j in "$PROJ"/libs/*.jar; do
  [ -f "$j" ] && LIBJARS+=("$j")
done
# native lib: hanya armeabi-v7a + arm64-v8a (hemat ukuran APK)
for abi in armeabi-v7a arm64-v8a; do
  src="$BUILD/aar/text-recognition-bundled-common-17.0.0/jni/$abi"
  if [ -d "$src" ]; then
    mkdir -p "$BUILD/apklib/lib/$abi"
    cp "$src"/*.so "$BUILD/apklib/lib/$abi/"
  fi
done

echo "[2/7] aapt2 compile (resources)..."
"$BT/aapt2" compile --dir "$SRC/res" -o "$BUILD/res.zip"

echo "[3/7] aapt2 link..."
"$BT/aapt2" link -o "$BUILD/app.apk" -I "$ANDROID_JAR" \
    --manifest "$SRC/AndroidManifest.xml" \
    --min-sdk-version 26 --target-sdk-version 35 \
    -A "$BUILD/assets" \
    --java "$BUILD/gen" \
    "$BUILD/res.zip"

echo "[4/7] kotlinc..."
find "$SRC/java" -name "*.kt" > "$BUILD/sources.txt"
find "$BUILD/gen" -name "R.java" >> "$BUILD/sources.txt"
CP="$ANDROID_JAR:$(IFS=:; echo "${LIBJARS[*]}")"
"$KOTLINC" -cp "$CP" -d "$BUILD/classes" @"$BUILD/sources.txt" > "$BUILD/kotlinc.log" 2>&1
KOTLIN_EXIT=$?
grep -v "^warning:" "$BUILD/kotlinc.log" | head -20 || true
if [ $KOTLIN_EXIT -ne 0 ]; then echo "KOTLINC GAGAL"; exit 1; fi

echo "[5/7] d8 (dex)..."
"$BT/d8" --lib "$ANDROID_JAR" --min-api 26 \
    $(find "$BUILD/classes" -name "*.class") \
    "${LIBJARS[@]}" \
    "$STDLIB" \
    --output "$BUILD/dex"

echo "[6/7] tambah dex + native lib, zipalign..."
(cd "$BUILD/dex" && zip -q -X ../app.apk classes*.dex)
(cd "$BUILD/apklib" && zip -q -r ../app.apk lib)
"$BT/zipalign" -f 4 "$BUILD/app.apk" "$BUILD/app-aligned.apk"

echo "[7/7] apksigner..."
"$BT/apksigner" sign --ks "$KEYSTORE" \
    --ks-pass pass:android --key-pass pass:android \
    --out "$OUT_APK" "$BUILD/app-aligned.apk"
"$BT/apksigner" verify --print-certs "$OUT_APK" | head -4

rm -rf "$BUILD"
echo "SELESAI: $OUT_APK ($(stat -c%s "$OUT_APK") byte)"
