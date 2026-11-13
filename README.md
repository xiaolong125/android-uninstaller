# Android 卸载助手

免 Root 的 Android 应用卸载工具，同时提供 **Mac 版 DMG** 和 **Android 版 APK**，用于卸载当前主用户（用户 0）下的普通应用和内置应用，包括 `systemHelper`（`com.mobiletools.systemhelper`）等包。是否允许卸载取决于设备系统和包保护策略，不保证所有系统组件都能移除。

## 下载

从 [Releases](https://github.com/xiaolong125/android-uninstaller/releases) 下载对应版本：

| 平台 | 安装包 | 环境要求 | 授权方式 |
| --- | --- | --- | --- |
| Mac | `android-uninstaller-macos-v1.2.0.dmg` | Apple Silicon，macOS 13+ | USB 连接手机，开启 ADB 调试并授权 |
| Android | `android-uninstaller-shizuku-v1.0.1.apk` | Android 9+，Shizuku 13+ | Shizuku 通过无线调试或电脑 ADB 激活，无需 Root |

两版均支持真实应用图标、应用名称及完整包名搜索、卸载前确认、卸载后精确核对和操作日志。

## 免 Root 卸载的含义

Mac 版通过 ADB 执行工具；Android 版通过 Shizuku 获得 shell 权限，激活后可直接在手机上操作。Android 11+ 可按 Shizuku 指引使用无线调试激活；较旧系统可借助电脑 ADB。手机重启后通常需要重新激活 Shizuku。已有 Root 的设备也可选择 Root 模式，但这不是设计上的使用前提。

这里的“卸载内置应用”是移除**用户 0 的安装状态和应用数据**，不修改只读系统分区，也不等于从 ROM 中彻底删除 APK。其他用户、分身空间不在处理范围内，系统更新或恢复出厂设置后应用可能重新出现。

例如，可在搜索框输入 `systemHelper` 或完整包名 `com.mobiletools.systemhelper`，核对目标后执行卸载。返回成功后，工具会重新查询该完整包名是否仍为用户 0 安装；不能只凭命令执行成功判断结果。

请逐个核对系统组件。安全中心（`com.miui.securitycenter`）等包可能关联权限管理、电池设置甚至开机流程；本次小米真机还发现，移除 `com.miui.guardprovider` 后，安全中心的 USB 安装校验因缺少 `guard` Provider 而失败。不要把“可以执行卸载”理解为“删除后一定不影响系统”。

## Mac 版使用

从本仓库的 **Releases** 下载 Mac 版 DMG，双击打开后，将应用拖入 **Applications（应用程序）** 即可安装。

适用于 Apple Silicon Mac（M 系列芯片），macOS 13 或更新版本。已内置 ADB 和 uninstall-user0.jar，无需手动上传或设置终端环境。当前版本 1.2.0。

1. 用 USB 连接手机，开启开发者选项中的 USB 调试，并在手机上允许此 Mac 调试。
2. 点击“刷新设备”，选择目标设备。
3. 点击“读取列表”，按应用名称或包名搜索并选择；也可直接输入完整包名。
4. 点击“卸载并清理”，核对设备和包名后确认。
5. 查看执行结果和日志。应用会重新上传 JAR、执行卸载、核对完整包名，最后清理 JAR；失败时也会尝试清理。

该工具处理 **Android 用户 0**，卸载时会删除该用户的应用数据。其他 Android 用户不会被处理；系统分区中的系统应用本体可能仍保留。不要卸载不熟悉的系统组件。原 JAR 使用 Android 内部接口，兼容性取决于手机系统；界面会保留原始错误日志。

设备断连可能导致清理失败。重新连接后选择同一台设备，点击“清理工具”。操作进行时请保持手机连接并等待完成。

没有安装的完整包名会提示“无需卸载”。核对时只比较完整包名，因此同名前缀的其他应用不会造成误报。原 JAR 要求包名包含点号；例如系统内部的 `android` 包无法通过本工具卸载。

此应用已做本地临时签名，未经过 Apple 开发者签名和公证；若 macOS 阻止打开，可在系统设置 → 隐私与安全性中选择“仍要打开”。

应用列表会显示真实应用图标、应用名称及包名，搜索支持中文名称、英文名称和包名（不区分大小写）。名称读取失败时会提示原因，并保留包名搜索。读取名称所需的只读辅助 JAR 会自动上传并清理。

## Android 版使用

1. 安装 [Shizuku](https://shizuku.rikka.app/download/)，按官方指引通过无线调试或电脑 ADB 启动服务。
2. 安装本仓库 Release 中的 Android APK。
3. 打开卸载助手，点击“连接 / 授权”，允许 Shizuku 权限。
4. 按应用名称或完整包名搜索，核对后确认卸载，并查看最终核对结果。

Android 版无需上传临时 JAR。APK 仅声明应用列表查询和 Shizuku 权限，未声明联网权限。详细说明和构建方法见 [android-app/README.md](android-app/README.md)。

## 源码与重建

仓库包含 SwiftUI 界面、ADB 执行流程、原始 Java 工具源码、资源和测试。安装 Xcode 或 Command Line Tools 后运行：

```sh
./build.sh
```

构建产物位于 `build/Android卸载助手.app`。默认构建 Apple Silicon 版本。

测试仅使用临时模拟 ADB，不连接或卸载真实设备应用：

```sh
swiftc -swift-version 5 Engine.swift Tests.swift -o /tmp/android-uninstaller-tests
/tmp/android-uninstaller-tests
```

ADB 的第三方许可见源码资源目录中的 `ADB-NOTICE.txt`。

## 项目文件

- `App.swift`：原生 SwiftUI 界面、应用名称与包名搜索、应用图标。
- `Engine.swift`：设备发现、ADB 命令执行、卸载核对及工具清理。
- `Tests.swift`：11 项模拟 ADB 工作流测试，不操作真实设备。
- `Resources/AppLabels.java`：只读的应用名称和图标辅助工具源码。
- `Resources/UninstallForUser0.java`：用户 0 卸载工具源码。
- `Resources/`：DEX JAR、ADB 许可声明。ADB 二进制不存入 Git，构建脚本优先复用本机 ADB，缺失时从 Google 官方平台工具包获取。发布的 DMG 已内置 ADB。

Java 工具已预编译内置；常规 Mac 构建不需要 Android SDK。修改 Java 工具时，需要 JDK 和 Android SDK，使用 `javac`（目标 Java 8）、SDK `android.jar` 和 build-tools 的 `d8` 生成 `classes.dex`，再使用 `jar` 封装并替换对应资源。

Mac 图标由 `tools/MakeIcon.swift` 在构建时生成。

## 验证

1.2.0 已在 Pixel 6 上验证应用名称和图标读取、中文名称搜索及辅助工具清理；卸载工作流通过 11 项模拟测试。Android 1.0.1 已通过构建和 Lint，覆盖安装到真机及模拟器；1.0.0 的 6 项单元测试通过，并在 Pixel 6 上通过 Shizuku 成功卸载 `com.part.sunfics`，同名前缀测试包保持安装。该次 Shizuku 真机卸载测试使用 Root 模式，不能作为免 Root shell 模式已实测通过的依据。

应用兼容性仍取决于手机系统的内部接口实现。
