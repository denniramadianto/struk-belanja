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
echo "[deps] selesai: $(ls "$LIBS"/*.aar | wc -l) AAR"
