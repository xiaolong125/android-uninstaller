# Android 卸载助手 · Shizuku 版

原生 Android APK，支持 Android 9 或更新版本，仅处理主用户（用户 0）。无需连接 Mac 即可浏览和搜索应用；卸载操作需要 Shizuku 13 或更新版本的授权服务。

## 使用

1. 从 [Shizuku 官网](https://shizuku.rikka.app/download/) 安装 Shizuku。
2. 按 Shizuku 引导启动服务：Android 11 或更新版本可使用无线调试；也可使用电脑 ADB 启动。已 Root 的设备可在 Shizuku 中选择 Root 启动。
3. 安装本项目 Release 中的 `android-uninstaller-shizuku-v1.0.1.apk`。
4. 打开卸载助手，点击“连接 / 授权”，允许 Shizuku 权限。
5. 按应用名称或包名搜索。点击应用，核对完整包名后确认卸载；长按应用可复制包名。

卸载会删除用户 0 下该应用的数据。系统应用仍可能保留系统分区中的本体；卸载系统组件可能影响手机功能。Shizuku 在手机重启后可能需要重新启动。

本工具会先检查精确包名，再调用与 Mac 版原始 JAR 相同的 `deletePackageVersioned` 接口（user 0 / flags 4），等待系统回调，最后重新查询核对。Android 版通过 Shizuku UserService 执行，不需要上传临时 JAR。

只有自身应用 UID 或 shell/root 可以调用服务；服务使用非守护模式，随客户端进程退出而停止。本工具和 Shizuku 自身禁止通过工具卸载。应用未声明 Internet 权限，不向服务器上传已安装应用列表。

## 构建

需要 JDK 17 或更新版本、Android SDK 35（platform 和 build-tools）。设置 `ANDROID_HOME` 指向 SDK，或在本地 `local.properties` 中填写 `sdk.dir`。

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

调试 APK：`app/build/outputs/apk/debug/app-debug.apk`。

```sh
./gradlew :app:assembleRelease
```

Release 默认生成未签名 APK，需要使用自己的密钥通过 Android SDK `apksigner` 签名后安装。已发布 APK 的签名私钥不在仓库中。

## 验证

- 1.0.1 修复桌面图标的额外白色底板和缩放，支持自适应图标及 Android 13 主题图标；已覆盖安装到真机和模拟器。
- 6 项单元测试：中文名称搜索、英文不区分大小写搜索、包名搜索、空搜索、非法包名拦截和完整包名核对。
- Gradle 构建和 Lint 错误检查通过。
- Pixel 6 实测：安装官方 Shizuku 13.6.0，授权、连接服务后，从 APK 界面成功卸载用户指定的 `com.part.sunfics`；界面与 ADB 均核对正式包已移除，同名前缀的 `com.part.sunfics.test` 仍安装。
- 该次真机测试的 Shizuku 实际以 Root 模式运行；未 Root 的 shell 模式尚未在本次测试中验证。不同系统的内部接口兼容性仍需实机验证。

## 依赖

Shizuku API / Provider 13.1.5，遵循 [Shizuku API 的 MIT 许可](https://github.com/RikkaApps/Shizuku-API/blob/master/LICENSE)。官方 Shizuku 管理器不包含在本 APK 内。
