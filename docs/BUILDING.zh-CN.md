# 从源码构建

构建脚本适用于 macOS/Linux，要求 Python 3.9+、JDK 21、官方 Android SDK Platform 37 和 Build-Tools 37.0.0。SDK 平台目录同时兼容 `android-37`、`android-37.0`。

```sh
export JAVA_HOME=/你的/JDK21/目录
export ANDROID_SDK_ROOT=/你的/Android/sdk
```

## 依赖

`tools/dependencies.lock.json` 固定了每个 AAR/JAR 的官方 Maven 或上游 Release URL 与 SHA256。首次构建下载到忽略目录 `.cache/`；每次构建都会校验，不从未知下载站取包。

libxposed API 102 仅用于编译，由 Vector 在运行时提供，不打入 APK。百度 SDK 使用 ARM64 原生库；OkHttp 4.12.0、Okio 3.6.0、Kotlin 1.9.10 等沿用已验证组合。

## 签名构建

准备自己的 PKCS12/JKS 签名库，长期保管。自建版本会自动把本次签名证书 SHA256 编入系统候选服务校验，不能冒用发布者签名。

通过终端隐式输入密码，避免把密码写进仓库或命令历史：

```sh
read -s LB_KEYSTORE_PASSWORD
export LB_KEYSTORE_PASSWORD
python3 tools/build.py --variant all --keystore /私有目录/release.p12 --key-alias locationbridge
unset LB_KEYSTORE_PASSWORD
```

可以分别使用 `--variant pixel-android17` 或 `--variant q25-android14`。产物位于 `dist/`，包含 APK、SHA256 元数据和签名验证输出。最终校验可执行：

```sh
python3 tools/check_release.py
```

自己的 AK 仍只在手机运行时填写，不是构建参数。将新证书的 SHA1 与包名绑定到自己的百度 AK。不要将签名库、密码或 AK 提交到 Git。

## 不签名构建

```sh
python3 tools/build.py --variant all --unsigned
```

这适合核对编译；未签名 APK 不能直接安装，且没有可用的签名身份来启用系统提供者。正式使用请重新执行带签名的构建，而不是仅在最终 APK 外部补签。

## 只读真机测试

对已构建的签名 Q25 版本：

```sh
python3 tools/test_runtime.py --serial SERIAL --variant q25-android14
```

Pixel 使用对应变体名称。脚本仅把只读测试包临时放入 `/data/local/tmp`，通过独立进程检查 Key 格式、无凭据异常消息、ProviderRequest 协议与位置新鲜度策略，最后删除测试包。不会安装/替换应用、发布位置、清除数据或修改系统配置。

这类测试不能替代真实安装后的 UI、位置链路、重启和长期耗电回归。
