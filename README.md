# WillDeep Android

**在手机上查看 WillDeep 会话、发送任务和处理审批。**

Android 原生客户端连接你的 WillDeep Mac 或 Rust Runtime；Agent 执行、文件操作和权限判定留在电脑端。适合离开电脑后查看进度、补充要求及回应待处理操作。

> A native Android companion for WillDeep. Follow desktop Agent sessions, send tasks, and respond to approvals from your phone. Execution and authorization remain on your paired computer.

[官网 willdeep.com](https://willdeep.com) · [下载 APK](https://github.com/xurenlu/willdeep-android/releases) · [Apache-2.0](LICENSE)

```bash
git clone https://github.com/xurenlu/willdeep-android.git
```

## 快速上手

1. 在 [GitHub Releases](https://github.com/xurenlu/willdeep-android/releases) 下载签名 APK 并安装，需要 Android 13 或更新版本。标为 Pre-release 的版本属于候选版本。
2. 在 WillDeep 电脑端启用手机连接，扫描当前安全中继或 HTTPS 网关的配对二维码。
3. 在手机上选择电脑和会话，查看回复、发送任务或处理待确认项。

覆盖安装使用相同签名，保留应用数据；不要为了更新而卸载原应用。官网介绍和桌面端入口见 [willdeep.com](https://willdeep.com)。

## 可以做什么

| 需求 | 能力 |
|---|---|
| 查看运行进度 | 按项目浏览会话、读取回复与运行状态 |
| 继续交代任务 | 发送文字、图片和排队消息 |
| 处理需要关注的事项 | 工具审批、补充回答及补丁确认 |
| 管理多台电脑 | 保存配对、设置本机别名和系统标记 |
| 调整会话配置 | 选择宿主支持的模型、供应商、技能、专家及插件 |

各电脑端支持的命令不同，详见 [协议与能力对照](docs/MOBILE_GATEWAY_REQUIREMENTS.md)。

## 连接与安全

- 网络连接要求 HTTPS/WSS；不再向 HTTP 局域网、Tailscale 或公网地址发送凭据。旧明文配对保留在手机上，但需要重新扫描安全入口；仅设备本机回环地址允许明文用于测试。
- 配对凭据加密存储；应用数据不参与系统云备份或设备迁移，换手机需要重新配对。升级安装不清除现有配对。
- 应用不记录 WebSocket 正文、配对令牌或解析异常中的原始载荷。推送服务默认关闭，配置与边界见 [推送说明](docs/UMENG_PUSH_SETUP.md)。
- 配对二维码和设备令牌相当于远控凭据，请勿分享。已配对手机按宿主的远控权限运行，并非只读访客；不再使用的设备应在电脑端撤销。

## 当前边界

手机端不是独立运行 Agent 的服务。大段 diff 和长命令的完整审阅体验仍需改进；无法完整确认变更时，请回电脑端审阅。HTTPS 保护传输，但不代表中继具备端到端加密；当前中继服务仍属于信任边界。

## 参与开发

使用 JDK 21 和 Android SDK，SDK 路径在未跟踪的 `local.properties` 中配置，或通过 `ANDROID_HOME` 提供。

```bash
git clone https://github.com/xurenlu/willdeep-android.git
cd willdeep-android
./gradlew testReleaseUnitTest lintRelease assembleDebug
```

项目使用 Kotlin、Jetpack Compose、OkHttp、CameraX 和 ML Kit；界面提供简体中文和英文资源。贡献请从 `develop` 创建工作分支，提供相关测试，避免提交凭据和个人设备信息。

正式签名发布需使用维护者的私有签名文件，流程见 [发布与签名](docs/ANDROID_RELEASE.md)。签名私钥、密码及 `local.properties` 不在仓库中。

## 文档

- [产品概览](product-overview.md) · [更新记录](CHANGELOG.md)
- [移动网关协议](docs/MOBILE_GATEWAY_REQUIREMENTS.md)
- [桌面运行配置](docs/MOBILE_RUN_CONTEXT.md)
- [安全边界与核查记录](docs/SECURITY_REVIEW.md)

## 许可证

项目原创代码采用 [Apache License 2.0](LICENSE)。第三方依赖和图标保留各自许可证，参见 [NOTICE](NOTICE)。许可证不授予 WillDeep 名称、商标或官方签名身份的使用权。
