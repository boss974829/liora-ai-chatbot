#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
SDK=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}
if [ -z "$SDK" ]; then
  echo "Set ANDROID_SDK_ROOT to your Android SDK." >&2
  exit 1
fi
BT="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-34/android.jar"
OUT=$(CDPATH= cd -- "$ROOT/.." && pwd)/dist/build
rm -rf "$OUT"
mkdir -p "$OUT/classes" "$(dirname "$OUT")/../dist"
javac --release 11 -classpath "$JAR" -d "$OUT/classes" "$ROOT/src/app/liora/companion/MainActivity.java"
"$BT/d8" --min-api 24 --lib "$JAR" --output "$OUT" $(find "$OUT/classes" -name '*.class')
"$BT/aapt2" compile --dir "$ROOT/res" -o "$OUT/compiled.zip"
"$BT/aapt2" link \
  -o "$OUT/unsigned.apk" \
  -I "$JAR" \
  --manifest "$ROOT/AndroidManifest.xml" \
  -R "$OUT/compiled.zip" \
  --min-sdk-version 24 \
  --target-sdk-version 34 \
  --version-code 1 \
  --version-name 1.0 \
  --auto-add-overlay
ROOT="$ROOT" OUT="$OUT" python3 - <<'PY'
import os, zipfile
root = os.environ["ROOT"]
out = os.environ["OUT"]
apk = os.path.join(out, "unsigned.apk")
with zipfile.ZipFile(apk, "a") as z:
    assets = os.path.join(root, "assets")
    for dirpath, _, files in os.walk(assets):
        for name in files:
            full = os.path.join(dirpath, name)
            rel = os.path.relpath(full, root).replace(os.sep, "/")
            z.write(full, rel)
    z.write(os.path.join(out, "classes.dex"), "classes.dex")
print("packed")
PY
DEST=$(CDPATH= cd -- "$ROOT/.." && pwd)/dist/Liora.apk
"$BT/zipalign" -p -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
if [ ! -f "$OUT/debug.keystore" ]; then
  keytool -genkeypair -keystore "$OUT/debug.keystore" -storepass android -keypass android \
    -alias liora -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Liora"
fi
"$BT/apksigner" sign --ks "$OUT/debug.keystore" --ks-pass pass:android --key-pass pass:android \
  --out "$DEST" "$OUT/aligned.apk"
"$BT/apksigner" verify "$DEST"
ls -lh "$DEST"
