# 三端配置联调验收

日期：2026-10-13。

## 源码版本

| 端 | 版本 | 位置 |
| --- | --- | --- |
| Android | 1.26.0-rc4 / versionCode 122 | willdeep-android 当前工作树 |
| Rust | 0.98.0-rc4 | willdeep-rs 当前工作树 |
| Mac | 1.417.0-rc8 / build 914 | Xedit 的 codex/mobile-context-alignment 工作树 |

## 自动化

- Rust 手机网关 25 项、协议 32 项、会话存储 20 项、配置控制 API 9 项测试通过。
- Mac 原生网关与配置 57 项测试通过，Go 网关测试及版本一致性检查通过。
- Android 121 项单元测试通过；真实 Rust 和 Mac 能力回包分别交由 Android 解析，不跳过这两项契约测试。
- Android 构建及 lint 通过，lint 0 错误、118 项警告。
- 总控报告：[report.md](../build/mobile-run-context-integration/20261013T081528Z/report.md)、[report.json](../build/mobile-run-context-integration/20261013T081528Z/report.json)。

## 真机

使用实际 Android 手机、独立应用包 com.willdeep.android.contexttest、本机 WebSocket 中继、临时 Rust Daemon、Mac 原生 AppState 和本机模型 HTTP 服务。

- 手机打开选择器，确认四档审批均可见，并修改为完全访问；实际选择 beta 供应商、remote-model 模型和宿主上报的技能。
- Rust 的配置回读和持久化成功，实际模型执行完成，手机显示回复。
- Mac 当前会话供应商、模型和审批模式均发生真实修改，全局默认供应商保持不变，实际模型执行完成，手机显示回复；原生宿主断言 1 项通过。
- 两条手机主请求均使用 remote-model；附加标题请求使用配置的默认模型。
- 已检查真机选择器截图，审批、供应商、模型和技能均有实际内容。
- 真机报告：[report.md](../build/mobile-phone-context/20261013T085120Z/report.md)、[report.json](../build/mobile-phone-context/20261013T085120Z/report.json)。截图在同目录。

真机使用测试配对载荷直接进入扫码后的连接流程；相机扫码识别、生产中继和公网模型服务不在本次验收范围内。正式手机应用的配对和数据未清除。当前交付为源码与本地验证，未发布、推送或替换正式宿主安装。

Rust 暂无 Mac 专家包机制，因此专家目录为空；非空远程专家选择会明确拒绝，不能以假选项宣称完整支持。插件仅上报已批准且启用的实际插件。
