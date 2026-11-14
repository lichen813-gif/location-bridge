# 手动系统位置（Pixel 0.5.6-public）

在应用中填写纬度、经度和位置精度，选择 WGS84 或 GCJ-02，然后点击“使用这个位置（模拟定位）”。坐标只保存在应用私有设置中，不写入公开诊断。地址需要先转换为坐标；本版本没有内置地址搜索或地图选点，也不能定位到楼宇内的具体房间。

首次使用需要系统定位开启、精确定位权限，并在开发者选项的“选择模拟位置信息应用”中选择“定位桥接”。手动模式无需百度 AK，也不依赖 GMS 内部混淆类。它通过 Android 公开 test-provider API 提供 GPS 和网络位置，系统和 GMS 融合输出是否被具体应用采用仍取决于该应用。

这是明确标记的模拟位置：所有手动点都使用 `Location.setMock(true)`，不隐藏模拟标记，也不作为百度真实观测处理。每两秒重新发布一个模拟样本，时间表示样本发布时间，不是卫星或网络观测时间；精度为用户指定的模拟参数。默认精度 100 米，不承诺实际测量精度。没有后台发送坐标给百度的需求。

启用时自动百度定位暂停。常驻通知提供“停止”操作，也可在应用中点击“停止手动位置，恢复自动定位”；系统测试提供者移除后恢复原 GPS、网络提供者。0.5.6 会进一步调用官方 GMS `setMockMode(false)`，并主动请求最大缓存年龄为零的真实融合位置。只接受停止之后产生且不带模拟标记的结果，才显示“已恢复真实融合定位”；60 秒未成功会明确提示失败。客户端自己的天气内容缓存仍可能需要刷新。

手动模式不会在手机或进程重启后自动继续。异常中断会显示“上次手动定位中断”，需点击停止完成清理。清理失败时保持“恢复未完成”，不声称已恢复；重新授予本应用模拟定位授权后可重试。停止失败不会自动重新启用手动点。

当前只在 Pixel Android 17 变体加入此功能，Q25 版本保持原样。自动定位通道仍有原系统/GMS兼容范围；新增手动模式不代表已适配任意新版 GMS 的真实网络补充接口。

接口依据：

- [Android LocationManager：addTestProvider、setTestProviderLocation、removeTestProvider](https://developer.android.com/reference/android/location/LocationManager)
- [Location.isMock](https://developer.android.com/reference/android/location/Location#isMock())

本地主机回归：

```sh
python3 tools/test_manual_position.py
```

此测试不发布位置，检查非法输入、坐标转换、更新、部分初始化失败、发布失败、清理失败重试及进程恢复清理。真机验证记录另见 `docs/validation/manual-position-20261114.json`。
