# 门禁卡助手 · Card Clone

[English](README.md) | **简体中文**

把门禁卡复制到空白卡上的安卓工具：**读原卡 → 写空白卡 → 自动比对**。

只用 NFC，全程离线，支持 MIFARE Classic 1K / 4K。

<p align="center">
  <img src="assets/icon-512.png" width="120" alt="icon">
</p>

![Card Clone](assets/feature-1024x500.png)

## 功能

- **三步流程**：读基准卡 → 写复制卡 → 自动比对，每一步都有明确结果
- **不是「写完了」就算**：写完立刻读回，逐字节、逐块、逐扇区密钥核对。直接告诉你「完全一致」还是「有差异」，并指出具体是哪个块、哪个扇区的密钥不同
- **17 种语言**：英、简繁中文、日、韩、西、葡、法、德、意、俄、阿、印地、印尼、越、泰、土。可在「关于 → 语言」里切换，默认跟随系统
- **深色模式**：跟随系统
- **历史记录**：每次读写都存档，可查看密钥、删除
- **离线**：不联网、不上传，卡数据只存在应用私有目录
- **零第三方依赖**：不用 AndroidX，APK 约 250 KB

## 下载

- [Releases](https://github.com/citrusli2026/nfc-card-clone/releases/latest)：CI 构建的 APK，每次打 tag 自动更新
- 仓库里的 [`dist/nfc-tool.apk`](dist/nfc-tool.apk)：本地构建版本

两个包签名不同（CI 每次用临时密钥），互相覆盖安装需要先卸载。

## 支持的卡

| 卡型 | 读 | 写 | 说明 |
| --- | --- | --- | --- |
| MIFARE Classic 1K / 4K | ✅ | ✅ | 主目标；密钥能打开就能完整复制 |
| MIFARE Ultralight / NTAG | ❌ | ❌ | 不在支持范围 |
| CPU 卡 / DESFire | ❌ | ❌ | 双向认证，手机复制不了 |
| 125kHz ID 卡 | ❌ | ❌ | 手机 NFC 不支持该频段 |

复制到空白卡需要 **UID 可写卡**（CUID / FUID / Gen2）。普通空白卡 UID 是出厂固定的，写不进原卡 UID。

## 使用

1. 贴原卡，点「读基准卡」
2. 贴 UID 可写空白卡，点「写复制卡并校验」
3. 等横幅显示绿色的「完全一致」，拿去刷门禁

如果刷不开：可能是读卡器校验了更多东西（滚动码、卡型、白名单），或者原卡是 CPU 卡。

## 自己构建

不需要 Gradle，`aapt2 + javac + d8 + apksigner` 直接出包。

```bash
./build.sh            # 出 APK 到 build/nfc-tool.apk
./tools/test.sh       # 比对逻辑的单元测试，不需要手机
```

需要：

| 依赖 | 说明 |
| --- | --- |
| JDK 17 | `apt install openjdk-17-jdk-headless` |
| `aapt2` / `zipalign` / `apksigner` | Debian/Ubuntu 的 `android-sdk-build-tools` 自带 arm64 版；其他平台用 Android SDK 里的 |
| `android.jar` | API 29（`platforms/android-29/android.jar`） |
| `r8.jar`（含 d8） | Google Maven 的 `com.android.tools:r8` |

所有路径都能用环境变量覆盖（默认值写在 `build.sh` 顶部）：

```bash
ANDROID_JAR=/path/to/android.jar \
AAPT2=/path/to/aapt2 \
D8_JAR=/path/to/r8.jar \
APKSIGNER=/path/to/apksigner \
ZIPALIGN=/path/to/zipalign \
./build.sh
```

> 默认路径按 arm64 Linux 写的（比如手机上的 proot Ubuntu），x86_64 机器上换成对应路径即可。

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
├── assets/             图标与横幅
└── dist/nfc-tool.apk   已构建的安装包
```

## 说明

- 个人工具，没有上架应用商店；直接装 APK 即可
- 运行日志只做两套：中文系统显示中文，其余语言显示英文
- 不提供导出功能，dump 只留在应用私有目录（可在历史里删除）

## 免责声明

本工具仅用于复制你本人有权使用的卡。复制他人门禁卡、绕过门禁系统可能违法，也可能违反所在场所的规定，使用者自行承担全部责任。

## License

MIT，见 [LICENSE](LICENSE)。
