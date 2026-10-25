# 兼容范围

## 必要条件

- 已有 Root。原测试环境使用 Magisk 30.7；本项目不提供解锁、刷机或获取 Root 的流程。
- Vector 2.2 / API 102，具备系统进程 Hook 能力。旧 Xposed API 93 模块接口不能直接替代。
- ARM64，精确与后台定位权限，系统定位打开，允许本应用后台按需运行。
- 可用的百度 Android 定位 AK，绑定**实际安装 APK 的签名 SHA1 和包名**。
- 若测试 Google 天气等在线业务，还需正常访问对应服务器。Clash 仅承担网络访问，不负责生成位置。

## Pixel 版本

- 设备：Pixel 10 Pro XL，`ro.product.device=mustang`。
- Android 17 / API 37，Build `CP3A.261005.005`。
- GMS `26.41.63 (260400-1003390101)` / versionCode `264163035`。
- GMS base APK SHA256：
  `3d281d7ec8467cb94b33ba350a25b2dfbb92f3918a2dbfca8925e496dc75abde`。
- 原本机验证框架为 Vector 2.2 / API 102。

## Q25 版本

- 设备：Zinwa Q25，系统厂商标识 Xelex，`ro.product.device=Q25`。
- Android 14 / API 34，Build `UP1A.231005.007`，测试机 incremental `2026124`。
- 系统指纹：
  `Xelex/Xelex10_Ultra/Xelex10_Ultra:14/20240427/UP1v:user/release-keys`。
- GMS `26.40.32 (190400-995630914)` / versionCode `264032029`。
- GMS base APK SHA256：
  `489ae6b16a2dc4aa69ecbaab3a8f1e3735247a6fe91b92ba270b6c30d7a02820`。

GMS 版本名称相同也可能是不同 APK 变体，最终以 SHA256 为准。系统与 GMS 会在加载时检查；不支持的 GMS 不安装内部适配。安装脚本遇到不同指纹会拒绝继续，不能仅改数字绕过。

## 不在当前验证范围内

其他机型/ROM、不同 GMS APK、32 位设备、旧框架接口，以及工作资料用户 10 的独立 GMS Hook。原有系统与工作资料数据不需要清除。

系统网络提供者与 GMS 补充有独立开关，但不能把“系统定位已有坐标”直接等同于“所有 GMS 客户端都已取得新位置”。适配新环境需要单独验证这两条通道。
