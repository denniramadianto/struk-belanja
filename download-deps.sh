#!/bin/bash
# Download dependensi AAR ML Kit dari Google Maven repo.
# Dipanggil otomatis oleh build-apk.sh bila folder libs/ kosong.
set -euo pipefail
PROJ="$(cd "$(dirname "$0")" && pwd)"
LIBS="$PROJ/libs"
BASE="https://dl.google.com/dl/android/maven2"
mkdir -p "$LIBS"

dl() { # dl <url-path> <nama-file>
  local f="$LIBS/$2"
  [ -f "$f" ] && { echo "  ada: $2"; return; }
  echo "  unduh: $2"
  curl -sL --max-time 180 -o "$f" "$BASE/$1" || { echo "GAGAL: $2"; rm -f "$f"; exit 1; }
}

echo "[deps] mengunduh AAR ML Kit..."
dl "com/google/mlkit/text-recognition/16.0.1/text-recognition-16.0.1.aar" \
   "text-recognition-16.0.1.aar"
dl "com/google/mlkit/text-recognition-bundled-common/17.0.0/text-recognition-bundled-common-17.0.0.aar" \
   "text-recognition-bundled-common-17.0.0.aar"
dl "com/google/mlkit/common/18.11.0/common-18.11.0.aar" \
   "common-18.11.0.aar"
dl "com/google/mlkit/vision-common/17.3.0/vision-common-17.3.0.aar" \
   "vision-common-17.3.0.aar"
dl "com/google/mlkit/vision-interfaces/16.3.0/vision-interfaces-16.3.0.aar" \
   "vision-interfaces-16.3.0.aar"
echo "[deps] mengunduh JAR androidx.lifecycle..."
if [ ! -f "$LIBS/lifecycle-common-2.6.1.jar" ]; then
  curl -sL --max-time 120 -o "$LIBS/lifecycle-common-2.6.1.jar" \
    "$BASE/androidx/lifecycle/lifecycle-common/2.6.1/lifecycle-common-2.6.1.jar"
fi
dl "com/google/android/gms/play-services-tasks/18.2.0/play-services-tasks-18.2.0.aar" \
   "play-services-tasks-18.2.0.aar"
dl "com/google/android/gms/play-services-basement/18.4.0/play-services-basement-18.4.0.aar" \
   "play-services-basement-18.4.0.aar"
dl "com/google/android/gms/play-services-base/18.5.0/play-services-base-18.5.0.aar" \
   "play-services-base-18.5.0.aar"
dl "com/google/android/gms/play-services-mlkit-text-recognition/19.0.1/play-services-mlkit-text-recognition-19.0.1.aar" \
   "play-services-mlkit-text-recognition-19.0.1.aar"
dl "com/google/android/gms/play-services-mlkit-text-recognition-common/19.1.0/play-services-mlkit-text-recognition-common-19.1.0.aar" \
   "play-services-mlkit-text-recognition-common-19.1.0.aar"
dl "com/google/android/datatransport/transport-api/2.2.1/transport-api-2.2.1.aar" \
   "transport-api-2.2.1.aar"
dl "com/google/android/datatransport/transport-backend-cct/2.3.3/transport-backend-cct-2.3.3.aar" \
   "transport-backend-cct-2.3.3.aar"
dl "com/google/android/datatransport/transport-runtime/2.2.6/transport-runtime-2.2.6.aar" \
   "transport-runtime-2.2.6.aar"
# Dependensi inisialisasi ML Kit (MlKitContext butuh firebase-components;
# tanpanya MlKit.initialize() -> NoClassDefFoundError -> force close saat dibuka)
dl "com/google/firebase/firebase-components/16.1.0/firebase-components-16.1.0.aar" \
   "firebase-components-16.1.0.aar"
dl "com/google/firebase/firebase-annotations/16.2.0/firebase-annotations-16.2.0.jar" \
   "firebase-annotations-16.2.0.jar"
dl "com/google/firebase/firebase-encoders/16.1.0/firebase-encoders-16.1.0.jar" \
   "firebase-encoders-16.1.0.jar"
dl "com/google/firebase/firebase-encoders-json/17.1.0/firebase-encoders-json-17.1.0.aar" \
   "firebase-encoders-json-17.1.0.aar"
if [ ! -f "$LIBS/annotation-1.5.0.jar" ]; then
  curl -sL --max-time 120 -o "$LIBS/annotation-1.5.0.jar" \
    "$BASE/androidx/annotation/annotation/1.5.0/annotation-1.5.0.jar"
fi
# Dependensi vision-common (dipakai InputImage/fromBitmap -> odml MlImage)
dl "com/google/android/odml/image/1.0.0-beta1/image-1.0.0-beta1.aar" \
   "image-1.0.0-beta1.aar"
dl "androidx/exifinterface/exifinterface/1.0.0/exifinterface-1.0.0.aar" \
   "exifinterface-1.0.0.aar"
# androidx.core: ContextCompat dipakai model loader ML Kit
dl "androidx/core/core/1.13.0/core-1.13.0.aar" \
   "core-1.13.0.aar"
# Dependensi init ML Kit (javax.inject dari Maven Central)
if [ ! -f "$LIBS/javax.inject-1.jar" ]; then
  curl -sL --max-time 120 -o "$LIBS/javax.inject-1.jar" \
    "https://repo1.maven.org/maven2/javax/inject/javax.inject/1/javax.inject-1.jar"
fi
echo "[deps] selesai: $(ls "$LIBS"/*.aar | wc -l) AAR"
