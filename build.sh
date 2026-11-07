#!/usr/bin/env bash
# 在 arm64 proot Ubuntu 里编译安卓 APK：aapt2 + javac + d8(r8.jar) + apksigner，不需要 Gradle。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
CACHE=/root/.cache/android
BUILD="$ROOT/build"

ANDROID_JAR="${ANDROID_JAR:-$CACHE/android-10/android.jar}"
AAPT2="${AAPT2:-/usr/lib/android-sdk/build-tools/29.0.3/aapt2}"
D8_JAR="${D8_JAR:-$CACHE/r8.jar}"
APKSIGNER="${APKSIGNER:-/usr/bin/apksigner}"
ZIPALIGN="${ZIPALIGN:-/usr/bin/zipalign}"
KS="${KEYSTORE:-$CACHE/debug.keystore}"
TARGET_SDK="${TARGET_SDK:-29}"
MANIFEST="$ROOT/app/AndroidManifest.xml"

for f in "$ANDROID_JAR" "$AAPT2" "$D8_JAR" "$APKSIGNER"; do
  [ -e "$f" ] || { echo "缺少 $f"; exit 1; }
done

rm -rf "$BUILD"
mkdir -p "$BUILD/classes" "$BUILD/dex" "$BUILD/gen"

echo "== 1/5 资源编译 =="
if [ "$TARGET_SDK" != "29" ]; then
  sed "s/android:targetSdkVersion=\"29\"/android:targetSdkVersion=\"$TARGET_SDK\"/" \
    "$ROOT/app/AndroidManifest.xml" > "$BUILD/AndroidManifest.xml"
  MANIFEST="$BUILD/AndroidManifest.xml"
fi
"$AAPT2" compile --dir "$ROOT/app/res" -o "$BUILD/res.zip"
"$AAPT2" link -o "$BUILD/base.apk" -I "$ANDROID_JAR" \
  --manifest "$MANIFEST" \
  "$BUILD/res.zip" --java "$BUILD/gen"

echo "== 2/5 javac =="
find "$ROOT/app/src" -name '*.java' > "$BUILD/sources.txt"
find "$BUILD/gen" -name '*.java' >> "$BUILD/sources.txt"
if ! javac -source 8 -target 8 -encoding UTF-8 -classpath "$ANDROID_JAR" \
  -d "$BUILD/classes" @"$BUILD/sources.txt" > "$BUILD/javac.log" 2>&1; then
  cat "$BUILD/javac.log"; exit 1
fi
grep -E "error" "$BUILD/javac.log" || true

echo "== 3/5 d8 =="
find "$BUILD/classes" -name '*.class' > "$BUILD/classes.txt"
java -cp "$D8_JAR" com.android.tools.r8.D8 --lib "$ANDROID_JAR" --min-api 21 \
  --output "$BUILD/dex" @"$BUILD/classes.txt"

python3 - "$BUILD" <<'PY'
import sys, zipfile, os
build = sys.argv[1]
with zipfile.ZipFile(os.path.join(build, "base.apk"), "a", zipfile.ZIP_DEFLATED) as z:
    z.write(os.path.join(build, "dex", "classes.dex"), "classes.dex")
print("classes.dex 已加入")
PY

echo "== 4/5 zipalign =="
"$ZIPALIGN" -f 4 "$BUILD/base.apk" "$BUILD/aligned.apk"

echo "== 5/5 签名 =="
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass android \
    -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 \
    -validity 10000 -dname "CN=Android Debug,O=Android,C=US" 2>/dev/null
fi

"$APKSIGNER" sign --ks "$KS" --ks-pass pass:android \
  --key-pass pass:android --ks-key-alias androiddebugkey \
  --out "$BUILD/nfc-tool.apk" "$BUILD/aligned.apk"

"$APKSIGNER" verify --print-certs "$BUILD/nfc-tool.apk" | head -3
ls -lh "$BUILD/nfc-tool.apk"
