#!/usr/bin/env bash
# 用 aapt2 --proto-format + bundletool 在本地拼一个 AAB（仅验证格式，正式包建议官方工具链）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CACHE=/root/.cache/android
AAPT2="${AAPT2:-/usr/lib/android-sdk/build-tools/29.0.3/aapt2}"
JAR="${ANDROID_JAR:-$CACHE/android-10/android.jar}"
BT="${BUNDLETOOL:-$CACHE/bundletool-all.jar}"
BUILD="$ROOT/build-bundle"

[ -f "$ROOT/build/dex/classes.dex" ] || { echo "先跑 ./build.sh"; exit 1; }
rm -rf "$BUILD"; mkdir -p "$BUILD/mod"

TARGET_SDK="${TARGET_SDK:-29}"
"$AAPT2" compile --dir "$ROOT/app/res" -o "$BUILD/res.zip"
MANIFEST="$ROOT/app/AndroidManifest.xml"
if [ "$TARGET_SDK" != "29" ]; then
  sed "s/android:targetSdkVersion=\"29\"/android:targetSdkVersion=\"$TARGET_SDK\"/" \
    "$ROOT/app/AndroidManifest.xml" > "$BUILD/AndroidManifest.xml"
  MANIFEST="$BUILD/AndroidManifest.xml"
fi
"$AAPT2" link --proto-format -o "$BUILD/base.apk" -I "$JAR" \
  --manifest "$MANIFEST" "$BUILD/res.zip"

cd "$BUILD/mod"
unzip -q "$BUILD/base.apk"
mkdir -p manifest dex
mv AndroidManifest.xml manifest/
cp "$ROOT/build/dex/classes.dex" dex/classes.dex

python3 - "$BUILD" "$BUILD/mod" <<'PY'
import sys, os, zipfile
build, mod = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(os.path.join(build, "base.zip"), "w", zipfile.ZIP_DEFLATED) as z:
    for root, dirs, files in os.walk(mod):
        for f in files:
            p = os.path.join(root, f)
            z.write(p, os.path.relpath(p, mod))
print("module zip ok")
PY

OUT="$BUILD/app.aab"
if [ "$TARGET_SDK" != "29" ]; then OUT="$BUILD/app-ts$TARGET_SDK.aab"; fi
java -jar "$BT" build-bundle --modules="$BUILD/base.zip" --output="$OUT"
ls -lh "$OUT"
java -jar "$BT" validate --bundle="$OUT" | head -5
