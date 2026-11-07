# 门禁卡助手（B 档工具）说明

一个原生安卓小应用：在手机上读 MIFARE Classic、试密钥、存 dump、写克隆卡。
没有第三方依赖，不需要 Gradle，源码在 `app/`，APK 在 `dist/nfc-tool.apk`。

## 功能：三步，一条主线

| 步骤 | 做什么 |
| --- | --- |
| ① 读基准卡 | 贴原卡 → 显示 UID / 卡型 / 扇区数 → 存成基准 |
| ② 写复制卡 | 贴空白卡 → 先把基准写进去（块 0 的 UID 最后写保护） |
| ③ 自动对比 | 写完**立刻读回**复制卡，和基准逐字节、逐块、逐扇区密钥比对，直接给「完全一致 ✓」或「有差异」 |

## 界面（0.2）

```
┌─────────────────────────────┐
│ 门禁卡助手            ⏱  ⓘ │
├─────────────────────────────┤
│ ①基准卡   ②复制卡   ③比对   │
│ ┌─────────────────────────┐ │
│ │ 基准卡            [已读取]│ │
│ │ A1B2C3D4                │ │
│ │ MIFARE Classic 1K · 16/16│ │
│ └─────────────────────────┘ │
│ ┌─────────────────────────┐ │
│ │ 复制卡            [已写入]│ │
│ │ A1B2C3D4                │ │
│ └─────────────────────────┘ │
│ ┌─────────────────────────┐ │
│ │ 完全一致 ✓               │ │
│ │ 64 块 · 1024 字节 · 0 处不同│ │
│ └─────────────────────────┘ │
│ [    写复制卡并校验    ]     │
│   [重置]        [详情]       │
└─────────────────────────────┘
```

- 首次启动有引导页，勾选「只复制自己有权使用的卡」才能进入
- 主界面三步：读基准卡 → 写复制卡 → 自动比对，写完自动读回
- 右上角：历史记录、关于
- 深色模式跟随系统
- 语言：默认跟随系统，可在「关于 → 语言」里手动切换（17 种）
- 不提供导出；dump 只存在应用私有目录，可在「历史」里查看或删除

## 实现要点

- `NfcAdapter.enableReaderMode` 前台读卡，监听 NFC-A/B/F/V
- `MifareClassic` 做认证和块读写；`authenticateSectorWithKeyA/B` 返回布尔值
- 密钥字典：常见默认密钥 + 厂商密钥，读到 Key B 后自动加入字典（借钥匙）
- 块 0 写入：先试普通写（Gen2/CUID 卡直接可写），不行再试 Gen1a 后门（`0x40` `0x43`）
- 先写数据块、后写扇区尾块，避免把自己锁在外面
- 写完读回逐块比对

实测：CUID 空白卡写入后读回与原卡 1024 字节完全一致。

## 已知限制

- 只支持 MIFARE Classic（1K/4K）。Ultralight/NTAG、DESFire、CPU 卡读不了，会明确提示
- 加密扇区如果字典开不了，手机端做不了 nested/hardnested 攻击
- 卡一直贴着手机时，每次 presence 检测都可能重新触发一次写入（幂等，但会重复写）；写完把卡拿开即可
- targetSdk 35 的边到边适配已加，但还没在 Android 15 真机上回归

## 编译

```bash
cd /root/workspace/nfc-card-copy
./build.sh
```

工具链（都已装好，在 arm64 proot 里可跑）：

- JDK 17（apt）
- `aapt2`、`zipalign`、`apksigner`（apt 的 android-sdk-build-tools）
- `android.jar`（API 29，解压在 `/root/.cache/android/android-10/`）
- `d8`（`/root/.cache/android/r8.jar`）

产物：`build/nfc-tool.apk`，已签名（v1/v2/v3），targetSdk 29，minSdk 21。

## 安装

APK 已放在 `/sdcard/Download/DSH-Go/nfc-tool.apk`。
在「文件管理」里点开 → 允许安装 → MIUI 增强防护会拦，走 `︙ → 单次安装授权` → 输锁屏密码。

## 构建 AAB（给 Play）

```bash
./build.sh                              # APK，targetSdk 29，装手机用
TARGET_SDK=35 ./tools/build_bundle.sh   # AAB，Play 候选包
```

- `dist/nfc-tool.apk`：日常安装/测试
- `dist/card-clone-0.2.aab`：targetSdk 29 的验证包
- `dist/card-clone-0.2-play.aab`：targetSdk 35 的 Play 候选包（bundletool validate 通过）

两份 AAB 都是用 `aapt2 --proto-format` + bundletool 在本机拼的，正式上传前建议用官方
工具链重出一次，并做 Android 15 真机回归（边到边）。
