# 发布与打包

## 本地出包

```bash
./build.sh          # build/nfc-tool.apk
./tools/test.sh     # 逻辑测试
```

## 用 GitHub Actions 出 Release（推荐）

打一个 `v` 开头的 tag 就会自动构建并建 Release：

```bash
git tag v0.2
git push origin v0.2
```

工作流：`.github/workflows/release.yml`

1. 装 JDK 17 + Android SDK（platform 35 / build-tools 35）
2. 跑 `./build.sh`（CI 里用临时密钥签名，产物是可直接安装的 debug 签名包）
3. 用 `softprops/action-gh-release` 建 Release，附上 `card-clone-v0.2.apk`

需要仓库开启 **Actions** 与 **Read and write permissions**（Settings → Actions → General → Workflow permissions）。

## 手动建 Release

没有 Actions 也可以：

1. 本地 `./build.sh`
2. GitHub → Releases → Draft a new release
3. Tag 填 `v0.2`，上传 `build/nfc-tool.apk`，写说明

## 版本号

`app/AndroidManifest.xml` 里的 `versionCode` / `versionName`，改完重新出包。
