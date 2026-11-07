# 门禁卡助手 · Card Clone

把门禁卡复制到空白卡上的安卓工具：**读原卡 → 写空白卡 → 自动比对**。

只支持 MIFARE Classic（1K / 4K），只用 NFC，全程离线。

<p align="center">
  <img src="assets/icon-512.png" width="120" alt="icon">
</p>

## 功能

- **三步流程**：读基准卡 → 写复制卡 → 自动比对，每一步都有明确结果
- **自动校验**：写完立刻读回，逐字节、逐块、逐扇区密钥核对，直接告诉你「完全一致」还是「有差异」，差异会指出哪个块、哪个扇区
- **17 种语言**：简繁中文、英、日、韩、西、葡、法、德、意、俄、阿、印地、印尼、越、泰、土；可在「关于 → 语言」手动切换，默认跟随系统
- **深色模式**：跟随系统
- **历史记录**：每次读写都存档，可查看密钥、删除
- **离线**：不联网、不上传，卡数据只存在应用私有目录
- **零第三方依赖**：不用 AndroidX，APK 约 250 KB

## 下载

- 直接装：[`dist/nfc-tool.apk`](dist/nfc-tool.apk)（debug 签名，自用没问题）
- 需要正式签名，用 `./build.sh` 自己出包

## 支持的卡

| 卡型 | 读 | 写 | 说明 |
| --- | --- | --- | --- |
| MIFARE Classic 1K / 4K | ✅ | ✅ | 主目标；密钥能打开就能完整复制 |
| MIFARE Ultralight / NTAG | ❌ | ❌ | 不在支持范围 |
| CPU 卡 / DESFire | ❌ | ❌ | 双向认证，手机复制不了 |
| 125kHz ID 卡 | ❌ | ❌ | 手机 NFC 不支持该频段 |

复制到空白卡需要 **UID 可写卡**（CUID / FUID / Gen2），普通空白卡写不进 UID。

## 使用

1. 贴原卡，点「读基准卡」，读出 UID 和全部扇区
2. 贴 UID 可写空白卡，点「写复制卡并校验」
3. 等横幅显示「完全一致」，拿去刷

如果刷不开：可能是读卡器校验了更多东西（滚动码、卡型、白名单），或者原卡是 CPU 卡。

## 自己构建

不需要 Gradle，`aapt2 + javac + d8 + apksigner` 直接出包。

```bash
./build.sh            # 出 APK 到 build/nfc-tool.apk
./tools/test.sh       # 比对逻辑的单元测试（不需要手机）
```

需要：

| 依赖 | 说明 |
| --- | --- |
| JDK 17 | `apt install openjdk-17-jdk-headless` |
| `aapt2` / `zipalign` / `apksigner` | Debian/Ubuntu 的 `android-sdk-build-tools` 包自带 arm64 版 |
| `android.jar` | API 29，放 `/root/.cache/android/android-10/` |
| `r8.jar`（含 d8） | `/root/.cache/android/r8.jar` |
| `bundletool`（可选） | 打包 AAB 用 |

路径都能用环境变量覆盖：`ANDROID_JAR`、`AAPT2`、`D8_JAR`、`APKSIGNER`、`ZIPALIGN`、`KEYSTORE`。

> 脚本默认按 arm64 Linux（如手机上的 proot Ubuntu）写的，x86_64 机器上把 `AAPT2` 等指向对应路径即可。

## 目录

```
├── app/                安卓源码（Java + res，17 种语言）
├── build.sh            一键出 APK
├── tools/
│   ├── test.sh         比对逻辑单元测试
│   ├── verify_dump.py  两份 dump 的逐字节对比（可在电脑上跑）
│   ├── build_bundle.sh 打 AAB
│   └── make_*.py       图标与商店图生成
├── docs/
│   ├── principle.md    原理：卡里有什么、为什么手机复制不了加密卡
│   ├── usage.md        App 使用与实现说明
│   ├── card-guide.md   卡型识别与操作手册
│   └── play/           可选的 Google Play 上架材料（文案、隐私政策、清单）
├── assets/             图标与商店图
└── dist/nfc-tool.apk   已构建的安装包
```

## 说明

- 这是个人工具，没有上架应用商店；直接装 APK 即可
- 运行日志只做中英两套：中文系统显示中文，其余语言显示英文
- 不提供导出功能，dump 只留在应用私有目录（可在历史里删除）

## 免责声明

本工具仅用于复制你本人有权使用的卡。复制他人门禁卡、绕过门禁系统可能违法，也可能违反所在场所的规定，使用者自行承担全部责任。

## License

MIT，见 [LICENSE](LICENSE)。

---

## English

**Card Clone** reads a MIFARE Classic card, writes an identical copy to a UID-writable blank card, then reads it back and verifies byte by byte, block by block and key by key.

- Android 5.0+ (API 21), NFC required, works offline, no third-party libraries
- MIFARE Classic 1K/4K only; Ultralight, DESFire/CPU cards and 125 kHz cards are not supported
- 17 UI languages, dark mode, history, in-app language switch
- Build: `./build.sh` (aapt2 + javac + d8 + apksigner, no Gradle)

Use it only on cards you are authorized to copy.
