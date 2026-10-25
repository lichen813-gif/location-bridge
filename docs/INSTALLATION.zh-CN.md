# 安装、使用与恢复

本说明适用于已经 Root 并安装 Vector 的兼容手机。安装脚本不会解锁 Bootloader、刷写镜像、清除数据或自动重启。

## 一、准备电脑与文件

1. 安装官方 Android SDK Platform-Tools，确认电脑可以使用 `adb`，再准备 Python 3.9 或更新版本。
2. 手机开启 USB 调试，解锁屏幕，在授权弹窗中允许电脑。
3. 在电脑执行 `adb devices -l`，记下目标手机序列号。后续将 `SERIAL` 替换成该值，多台手机时必须明确选择。
4. 克隆仓库，或下载仓库源码 ZIP 并解压：

   ```sh
   git clone https://github.com/lichen813-gif/location-bridge.git
   cd location-bridge
   ```

5. 从对应 Release 下载 APK、同名 `.apk.json` 和 `SHA256SUMS`。下面所有命令都在仓库目录内运行，APK 路径按实际下载位置填写。

## 二、只读核对兼容性

Pixel：

```sh
python3 tools/install.py --serial SERIAL --variant pixel-android17 --check
```

Q25：

```sh
python3 tools/install.py --serial SERIAL --variant q25-android14 --check
```

手机可能出现 Root 授权提示，此处授权对象为调试 shell。脚本读取系统、GMS APK 校验值和 Vector 状态，不修改手机。出现 `compatible: true` 后继续。

找不到 adb 时加 `--adb /实际路径/adb`。USB 显示 `unauthorized` 时先在手机授权。若 GMS 或系统不匹配，请停止安装并提供版本信息进行适配，不要强制绕过。

## 三、准备自己的百度 AK

首次申请请先阅读 README 中的[完整 AK 申请流程](../README.md#申请百度-ak)，包含控制台入口、各字段填写方法及鉴权排查。

在[百度地图开放平台](https://lbsyun.baidu.com/)创建 **Android 应用**，启用 **Android 定位 SDK**。不需要为此桥接勾选导航、地图渲染等所有产品。

- PackageName：`io.github.lichen.locationbridge`。
- 本仓库已签名 Release APK 的发布版 SHA1：
  `0D:1D:3F:FE:3E:FC:ED:4E:95:3A:4A:A7:76:41:83:26:80:62:88:33`。
- 使用同一签名测试时，开发版 SHA1 可以填相同值。
- 自己重新签名构建时，必须使用**自己的证书 SHA1**，不能照抄上述值。

参考[官方注册说明](https://lbsyun.baidu.com/apiconsole/key)与[集成说明](https://lbsyun.baidu.com/docs/android?title=android-locsdk/guide/create-project/android-studio)。AK 不要放入公开 issue、日志或提交记录。

## 四、安装与启用

将 APK 与同名 `.apk.json` 放在同一目录。以本次公开预览包为例：

Pixel：

```sh
python3 tools/install.py --serial SERIAL --variant pixel-android17 --apk /实际路径/location-bridge-pixel-android17-0.5.3-public.apk
```

Q25：

```sh
python3 tools/install.py --serial SERIAL --variant q25-android14 --apk /实际路径/location-bridge-q25-android14-0.6.1-public.apk
```

脚本会再次检查兼容性和 APK 校验值，然后：

- 普通安装/覆盖安装 APK，使用 `--no-incremental`，不清除原有数据。
- 授予本应用精确、后台定位及通知权限，单独加入电池优化豁免。
- 启用本模块，设置两个作用域：`system/0`、`com.google.android.gms/0`。
- 开启本项目的两个全局开关。

它不改其他模块、代理节点、锁屏或系统省电设置，也不会立即重启手机。若中途失败，先检查输出；已经完成的步骤不会自动清空或覆盖其他数据，可以排除原因后重新执行。

手机上继续操作：

1. 打开“定位桥接”或“定位桥接 Q25”。填写自己的 AK，阅读并勾选数据处理授权，点击“保存 AK 和使用授权”。
2. 确认系统定位开启，Wi-Fi 扫描可用，网络正常。首次测试先保持屏幕解锁。
3. 点击“开始两分钟室内测试”。权限弹窗选择精确位置；后台权限可在系统应用权限页核对。
4. 等待“已取得网络位置”、新定位次数增加。新装且尚未重启时，“系统已绑定”可能仍为否，这属于独立 SDK 测试阶段。
5. 点击“结束手动测试”，再在 Vector 中核对本模块启用、作用域正确。不要额外勾选地图、KWGT、微信或工作资料里的 GMS。
6. 通过手机电源菜单正常重启，开机后手动首次解锁。系统随后按需绑定桥接，无需每次手动点开始测试。

## 五、验证应用

### Google 地图

打开地图并点当前位置按钮。先看桥接的新定位次数及位置年龄，再检查地图的位置是否正确。只看到一个旧蓝点不能证明本次定位成功。

### KWGT

在 KWGT 的位置设置使用“自动/GPS 定位”，授予其精确及后台定位权限。这里的界面名称不意味着位置一定来自卫星；官方 GMS 可以返回网络融合位置。

进入“设置 → 天气 → 强制更新”。等待更新完成，并检查新的更新时间。手机静止时，KWGT 可能保留相同坐标的旧接收时间；需要结合桥接观测次数及天气更新时间判断。

### Google 天气

Pixel 测试机使用原有独立 Google 天气。进入“当前位置”卡片并下拉刷新，核对更新时间，不要只查看手动收藏的城市。

Q25 原测试机未安装独立 Google 天气，当前没有该应用的 Q25 测试结论。

### Clash 与网络

桥接的位置来源是百度；Google 天气、地点名称、地图内容等业务仍需正常联网。若有位置但天气不更新，先检查 VPN/代理是否真正运行。

需要开机后自动恢复 Clash 时，可在 Clash 的“设置 → 应用 → 自动重启”中启用，再做独立重启测试。Pixel 原测试已验证此设置；Q25 本轮仅验证了手动开启 VPN。不要将“Clash 后台进程存在”等同于“VPN 已建立”。

## 六、日常使用与公开版升级

桥接按照系统/GMS 的请求周期工作；连续地图请求最短约 10 秒一次，长周期请求取得位置后会停止 SDK 会话，避免无需求时持续扫描。

公开版不从旧的内置 Key 二进制中迁移 AK。从原私用版升级后，需要在应用内自行填写 AK；Google 账户和其他应用数据无需清除。同签名升级可以保留本应用原有设置。更换签名时系统可能拒绝覆盖，请先确认签名，不要为了升级清除 GMS 或 Google 账户。

## 停用与恢复

### 只停止百度定位

应用中点击“清除 AK 并停止百度定位”。这会清除本应用保存的 AK 和授权，停止 SDK，不会清除 Google 账户或其他应用数据。系统提供者选择仍需下一步才能完整回退。

### 仅关闭 GMS 附加监听

将脚本复制到手机后以 Root 执行：

```sh
adb -s SERIAL push scripts/disable-gms-supplement.sh /data/local/tmp/location-bridge-disable-gms.sh
adb -s SERIAL shell su -c 'sh /data/local/tmp/location-bridge-disable-gms.sh'
```

它仅关闭 `lichen_location_bridge_gms_enabled`，原 GMS 请求与 GPS 继续执行。

### 完整停用桥接

```sh
adb -s SERIAL push scripts/rollback.sh /data/local/tmp/location-bridge-rollback.sh
adb -s SERIAL shell su -c 'sh /data/local/tmp/location-bridge-rollback.sh'
```

该脚本只停用本模块、自己的组件和电池豁免，不会卸载或清除 GMS、账户、地图及 KWGT。若系统仍显示旧提供者绑定，手动重启并首次解锁。确认恢复后，才按需要卸载“定位桥接”。

完整系统回退在 Q25 本轮未实测；Pixel 原版曾验证原提供者回退，公开版仍应在实际设备上核对。

## 常见问题

| 现象 | 检查方法 |
| --- | --- |
| 无法安装 | 核对 APK 变体、最低系统版本与签名；Android 14 不能安装 Pixel Android 17 包 |
| 尚未开始或提示未授权 | 先填写 AK、保存授权，检查精确定位与系统定位开关 |
| 百度鉴权失败 | 核对 Android 类型 AK、包名、实际 APK 的证书 SHA1，以及定位服务权限/额度 |
| 独立测试有位置，其他应用没有 | 核对 Vector API、系统/GMS 指纹、两个作用域和重启后的绑定状态 |
| 位置正确，天气不更新 | 分别检查天气提供商、Google 服务联网/代理和应用自身刷新状态 |
| GMS 更新后失效 | 版本指纹保护可能已拒绝适配；先检查，不能仅修改版本号跳过验证 |
| 待机后 SDK 不运行 | 检查本应用后台权限和电池豁免；不要关闭整个系统省电机制 |

反馈问题时提供型号、系统 Build、GMS/Vector 版本及不含坐标的状态页即可。不要上传全量定位转储、账户数据库、AK、签名私钥或未经检查的截图。
