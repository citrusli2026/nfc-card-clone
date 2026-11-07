#!/usr/bin/env bash
# 离线跑核心逻辑测试（不需要手机）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR="${ANDROID_JAR:-/root/.cache/android/android-10/android.jar}"
OUT="${TMPDIR:-/tmp}/nfccopy-test"
rm -rf "$OUT"; mkdir -p "$OUT"
javac -encoding UTF-8 -cp "$JAR:$ROOT/build/classes" -d "$OUT" "$ROOT/tools/test/CompareTest.java"
java -cp "$OUT:$ROOT/build/classes:$JAR" CompareTest
