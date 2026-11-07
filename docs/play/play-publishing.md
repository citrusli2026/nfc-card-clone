# Google Play 上架方案

目标：把「门禁卡助手 / Card Clone」发布到 Google Play（全球，不含中国大陆——Play 在中国大陆不可用）。

## 一、账号与资质

- Google Play 开发者账号：一次性 25 美元，需要可用的支付方式（国内双币卡通常可以）
- 需要身份验证（个人身份证/护照 + 地址）
- 中国大陆开发者账号还需要额外的资质材料，审核周期更长；如果只是个人作品，用个人账号即可
- 隐私政策必须有一个可公开访问的 URL（GitHub Pages / 语雀 / 自建站点都行）

## 二、技术清单

| 项目 | Play 要求 | 当前状态 |
| --- | --- | --- |
| targetSdk | 新应用需 35（2025-08 起） | **29，需要升级** |
| 构建产物 | AAB（不能传 APK） | 有本地方案，推荐 CI |
| 签名 | 上传密钥 + Play App Signing | 目前是 debug 密钥，需更换 |
| 64 位 | 必须支持 | 纯 Java 无 native，天然满足 |
| 权限 | NFC 是普通权限，无需特殊声明 | 已声明 |
| 广告 ID | 不使用 `AD_ID` | 满足 |
| 数据安全表单 | 声明不收集数据 | 与实现一致 |

## 三、targetSdk 35 改造（必须做）

1. **边到边（edge-to-edge）**：Android 15 强制，状态栏/导航栏透明，页面要自己处理 insets，
   否则标题会被状态栏盖住。改法：给根布局加 `OnApplyWindowInsetsListener`，或
   `fitsSystemWindows`。
2. **行为变更回归**：前台服务、隐式广播、存储（我们已用 MediaStore + 应用私有目录，影响小）。
3. **重新构建**：需要 API 35 的 `aapt2`。本机（arm64）只有 build-tools 29 的 aapt2，
   解析不了 API 35 的 `resources.arsc`，所以正式包建议在 x86_64 机器或 CI 上出。

## 四、AAB 方案

### 方案 A（推荐）：GitHub Actions

仓库里已经写好 `.github/workflows/build.yml`：推送到 `main` 或手动触发，会：

1. 装 JDK 17 + Android SDK（platform 35 / build-tools 35）
2. 出一份 targetSdk 29 的 APK（装机测试用）
3. 出一份 targetSdk 35 的 AAB（Play 候选）
4. 把两个产物传到 Artifacts

脚本用的是仓库里的 `build.sh` / `tools/build_bundle.sh`，通过环境变量
（`ANDROID_JAR`、`AAPT2`、`D8_JAR`、`APKSIGNER`、`ZIPALIGN`、`KEYSTORE`、`BUNDLETOOL`）
切换工具链，本地和 CI 共用一套逻辑。

> 注意：这个 workflow 还没在 CI 上跑过（本机连不上 GitHub Actions）。第一次跑如果报错，
> 多半是 build-tools 里的 `apksigner` 是脚本、需要 `bash` 调用，或 sdkmanager 路径不同，
> 按报错改环境变量即可。

正式上传 Play 时，把 `KEYSTORE` 换成仓库 secret 里的上传密钥，并用 `apksigner` 签 AAB
（Play 要求上传包用上传密钥签名）。

### 方案 B（本机验证用）：aapt2 proto + bundletool

```bash
# 1) proto 资源
aapt2 link --proto-format -o base.apk -I android.jar \
  --manifest app/AndroidManifest.xml build/res.zip
# 2) 组装 module
#    manifest/AndroidManifest.xml  dex/classes.dex  res/...  resources.pb
# 3) 打包
bundletool build-bundle --modules=base.zip --output=app.aab
```

`tools/build_bundle.sh` 是这条链路的脚本。它只能证明格式对，**正式包仍建议用官方工具链**，
因为 targetSdk 35 的资源编译需要新版 aapt2。

**本机验证结果（2026-11-07）**：

- `./tools/build_bundle.sh` → `dist/card-clone-0.2.aab`（targetSdk 29）
- `TARGET_SDK=35 ./tools/build_bundle.sh` → `dist/card-clone-0.2-play.aab`（targetSdk 35，Play 候选）

两个包都通过 `bundletool validate`。Play 候选包还差一次 Android 15 真机回归（边到边），
以及用官方工具链重出。

## 五、商店素材

| 素材 | 规格 | 状态 |
| --- | --- | --- |
| 应用图标 | 512×512 PNG | ✅ `assets/icon-512.png` |
| 特征图 | 1024×500 PNG/JPEG | ✅ `assets/feature-1024x500.png` |
| 手机截图 | 至少 2 张，建议 4–8 张 | ⏳ 需要真机截图 |
| 短描述 | ≤80 字符 | ✅ 中英各一份 |
| 完整描述 | ≤4000 字符 | ✅ 中英各一份 |
| 隐私政策 | 公开 URL | ✅ 文本已写，待托管 |
| 分类 | 工具（Tools） | 建议 |
| 内容分级 | 问卷填写，无用户内容 | 待填 |

## 六、审核风险与应对

Play 对「门禁卡复制」这类工具会重点看是否帮助未授权访问。同类应用（MIFARE Classic Tool）
在 Play 上架多年，说明类目可行，但文案和功能边界要收得住：

- **文案**：强调「备份自己的卡」「离线」「可验证」，不出现「破解」「万能卡」「复制别人的卡」
- **应用内**：首启免责确认 + 关于页免责声明（已实现）
- **功能**：只做常见密钥字典尝试，不内置攻击算法（nested/hardnested 不做），不宣传破解
- **权限**：只用 NFC，不申请存储/位置/通讯录
- 如果被拒：先申诉说明用途；仍不行则改走 F-Droid 或官网 APK 分发

## 七、发布节奏

1. **内部测试（Internal testing）**：先传 AAB，最多 100 人，快速验证
2. **封闭测试（Closed testing）**：10–20 个真实用户，跑两三周
3. **正式发布（Production）**：分批放量

## 八、待办清单

- [x] targetSdk 35 可构建（aapt2 链接通过，边到边 insets 已加）
- [x] CI 工作流写好（`.github/workflows/build.yml`，未实跑）
- [x] 本机 AAB 链路跑通（bundletool validate 通过）
- [ ] Android 15 真机回归 targetSdk 35 版本
- [ ] 生成上传密钥（不要用 debug 密钥）
- [ ] 真机截图 4–8 张（浅色/深色各来几张）
- [ ] 隐私政策托管成 URL
- [ ] Play Console：建应用、填商店信息、数据安全、内容分级、传 AAB
