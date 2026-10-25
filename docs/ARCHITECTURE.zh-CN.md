# 实现与版本适配

## 两条接入路径

独立应用使用百度 SDK 9.7.0 的网络定位模式，关闭其 GNSS 与系统网络回退，避免提供者回调进入自身。GCJ-02 转换为 WGS84，使用真实观测时间和保守精度，不使用 Android 模拟定位接口。

第一条路径是系统 `network` 提供者。Vector 在系统服务选择时校验本应用签名、组件、权限、系统版本与用户解锁状态，选择本应用的服务；原选择方法仍执行。服务 Binder 只接受 system_server 的控制调用。

第二条路径是官方 GMS 内部网络定位回调。GMS 有独立的融合链路，只替换系统 network 未必足以满足其客户端。模块先校验完整 GMS APK 哈希，再核验方法与字段结构；原始请求、停止逻辑和回调都继续执行。

GMS 有定位需求时附加标准 network 监听，接收本应用标记的真实新观测，送回原回调并交给官方融合引擎。Google 原有更新若同样新鲜且更准确，则优先保留。

## 两个版本的差异

Pixel 当前 GMS 验证使用原生 NlpReport；Q25 当前配置验证使用旧 Location 列表。两者都保留运行时格式开关，但内部类名、方法和 APK 指纹分别固定。没有通过替换/清除 GMS 或安装旧 microG 完成接入。

Android 14 与 17 的系统选择器和 Binder 数据布局分别在真机只读核对。系统 network 使用同一个应用包名；两个变体在不同手机上使用，不能在同一手机当作两个独立应用并排安装。

## 新鲜度与调度

- 拒绝 null、非法坐标、非法精度、模拟标记、超过 30 秒或明显来自未来的观测。
- 保留测量字段，只把百度 `wf/cl` 来源标签转换为 GMS 接受的 `wifi/cell`。
- 去重依据原始观测时钟，不把缓存改为当前时间。
- SDK 的手动重复请求曾返回旧观测，因此按需求创建新会话，旧回调由会话编号失效。
- 连续位置最短约 10 秒；长周期请求完成后停止 SDK 和前台通知。

## 公开版配置

原私用构建把 AK 编入 APK。公开版去掉该构建输入，新增本机私有 Key 设置、明确的数据处理授权、清除与停用按钮。

Key 设置变化会重建 SDK 会话，并同步本提供者的 allowed 状态。日志不输出 AK；异常诊断只记录类型，避免第三方错误消息夹带凭据。AK 不写入 Intent、公开文件或源码。

## 启用和恢复

两个开关为 `lichen_location_bridge_enabled`、`lichen_location_bridge_gms_enabled`。作用域仅为 `system/0` 和 `com.google.android.gms/0`。更新系统/GMS 后需要核对指纹与结构，不能用“同为 Android 14/17”推断兼容。

项目借鉴了 [XposedUnifiedNlp](https://github.com/Rawi01/XposedUnifiedNlp) 的服务接入思路，使用 [libxposed API 102](https://github.com/libxposed/api) 编写新的适配，没有安装或打包旧项目的 GmsCore 变体。
