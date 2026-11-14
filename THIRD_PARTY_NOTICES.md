# 第三方组件

仓库自有适配代码沿用根目录 GPL-3.0。以下组件按各自上游许可分发和使用；不把上游组件宣称为本项目原创。

| 组件 | 版本 | 来源与许可信息 |
| --- | --- | --- |
| BaiduMapSDK_Location | 9.7.0 | 百度发布的 Maven Central 工件；POM 标注 Apache License 2.0；定位服务另受百度平台条款约束 |
| 百度 common / protobuf | 1.0.43 / 1.0.7 | 同一官方 Maven 命名空间，许可按上游工件 |
| libxposed API | 102.0.0 | [上游](https://github.com/libxposed/api)，Apache-2.0；仅编译，不打入 APK |
| OkHttp | 4.12.0 | [上游](https://github.com/square/okhttp)，Apache-2.0 |
| Okio | 3.6.0 | [上游](https://github.com/square/okio)，Apache-2.0 |
| Kotlin stdlib | 1.9.10 | [上游](https://github.com/JetBrains/kotlin)，Apache-2.0 |
| JetBrains annotations | 13.0 | Maven Central，上游 JetBrains 注解库 |

确切下载 URL 和 SHA256 见 `tools/dependencies.lock.json`；Apache 许可证副本见 `third_party/LICENSE-APACHE-2.0.txt`。构建 APK 包含本项目许可证和第三方声明。

使用者需要自行申请合法的 Android 定位 AK 并履行百度 SDK/平台要求的数据处理与隐私告知。参考[官方集成文档](https://lbsyun.baidu.com/docs/android?title=android-locsdk/guide/create-project/android-studio)与[隐私接口说明](https://lbsyun.baidu.com/docs/android?title=android-locsdk/android-privacy)。

Google Play 服务、Android ROM 和地图应用未随本项目分发。仓库不含 Google APK、反编译源码或手机私有日志；适配仅提供与实际安装版本互操作的接口信息。

## Pixel 0.5.6：官方 GMS 模拟模式退出

Pixel 变体额外打包 Google Play services Location 客户端 SDK 21.3.0，以及 base 18.5.0、basement 18.4.0、tasks 18.2.0。它们来自 Google Maven，按上游 POM 标注的 [Android SDK License](https://developer.android.com/studio/terms.html) 使用。它们是客户端库，不是手机上的 Google Play 服务 APK。

对应传递依赖包括 AndroidX（Apache-2.0）、Kotlin Coroutines 1.7.3（Apache-2.0）、JetBrains annotations 23.0.0（Apache-2.0）；确切工件、来源和 SHA256 固定在依赖锁文件。仅 Pixel 打包这些新增依赖，Q25 仍沿用原依赖集合。
