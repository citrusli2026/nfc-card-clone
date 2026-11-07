# Card Clone

**English** | [简体中文](README.zh.md)

Copy a MIFARE Classic access card onto a blank card: **read the original → write a blank → verify automatically**.

NFC only. Fully offline. MIFARE Classic 1K / 4K.

<p align="center">
  <img src="assets/icon-512.png" width="120" alt="icon">
</p>

![Card Clone](assets/feature-1024x500.png)

## Features

- **Three steps**: read a baseline card, write the copy, compare automatically
- **Verified, not assumed**: after writing, the app reads the copy back and compares it byte by byte, block by block and sector key by sector key. You get a clear "Identical" or "Differences found", with the exact blocks and sector keys that differ
- **17 languages**: English, Simplified/Traditional Chinese, Japanese, Korean, Spanish, Portuguese (BR), French, German, Italian, Russian, Arabic, Hindi, Indonesian, Vietnamese, Thai, Turkish. Switch in-app under About → Language, default follows the system
- **Dark mode**: follows the system
- **History**: every read and write is stored locally, viewable and deletable
- **Offline**: no network, no uploads; card data stays in the app's private storage
- **No third-party libraries**: no AndroidX, ~250 KB APK

## Download

- [Releases](https://github.com/citrusli2026/nfc-card-clone/releases/latest): APK built by CI on every tag
- [`dist/nfc-tool.apk`](dist/nfc-tool.apk): local build

The two are signed with different keys (CI uses an ephemeral key), so switching between them requires uninstalling first.

## Supported cards

| Card | Read | Write | Notes |
| --- | --- | --- | --- |
| MIFARE Classic 1K / 4K | ✅ | ✅ | Main target; full copy if the keys open |
| MIFARE Ultralight / NTAG | ❌ | ❌ | Not supported |
| CPU card / DESFire | ❌ | ❌ | Mutual authentication; a phone cannot copy it |
| 125 kHz ID card | ❌ | ❌ | Phone NFC does not operate at 125 kHz |

Writing a usable copy needs a **UID-writable blank card** (CUID / FUID / Gen2). Ordinary blanks keep their factory UID and will not open the same door.

## Usage

1. Tap the original card, press **Read baseline**
2. Tap the UID-writable blank card, press **Write & verify**
3. Wait for the green **Identical** banner, then try it on the door

If the copy does not open the door, the reader probably checks more than the UID
(rolling codes, card type, a whitelist), or the original is a CPU card.

## Build

No Gradle needed: `aapt2 + javac + d8 + apksigner`.

```bash
./build.sh            # -> build/nfc-tool.apk
./tools/test.sh       # comparison logic tests, no phone required
```

Requirements:

| Dependency | Notes |
| --- | --- |
| JDK 17 | `apt install openjdk-17-jdk-headless` |
| `aapt2`, `zipalign`, `apksigner` | Debian/Ubuntu `android-sdk-build-tools` ships arm64 builds; otherwise use the Android SDK ones |
| `android.jar` | API 29 (`platforms/android-29/android.jar`) |
| `r8.jar` (contains d8) | `com.android.tools:r8` from Google Maven |

Every path can be overridden with an environment variable (defaults are at the top of `build.sh`):

```bash
ANDROID_JAR=/path/to/android.jar \
AAPT2=/path/to/aapt2 \
D8_JAR=/path/to/r8.jar \
APKSIGNER=/path/to/apksigner \
ZIPALIGN=/path/to/zipalign \
./build.sh
```

> Defaults assume arm64 Linux (e.g. proot Ubuntu on a phone); on x86_64 just point the variables at your SDK.

## Layout

```
├── app/                Android sources (Java + res, 17 languages)
├── build.sh            one-shot APK build
├── tools/
│   ├── test.sh         comparison unit tests
│   ├── verify_dump.py  byte-level diff of two dumps (runs on desktop)
│   ├── build_bundle.sh AAB packaging
│   └── make_*.py       icon / store image generation
├── docs/
│   ├── principle.md    what is inside a card, why encrypted cards cannot be copied
│   ├── usage.md        app behaviour and implementation notes
│   ├── card-guide.md   card identification and manual
│   └── play/           optional Google Play materials (listing, privacy policy, checklist)
├── assets/             icon and banner
└── dist/nfc-tool.apk   prebuilt APK
```

## Notes

- Personal tool, not on any app store; sideload the APK
- Runtime logs exist in two sets only: Chinese on Chinese systems, English otherwise
- No export feature; dumps stay in the app's private directory (delete them from History)

## Disclaimer

Use this tool only for cards you are authorized to use. Copying someone else's access card or bypassing an access control system may be illegal and may violate local rules. You are responsible for how you use it.

## License

MIT, see [LICENSE](LICENSE).
