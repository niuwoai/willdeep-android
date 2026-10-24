# 三端待发消息队列

版本：Android `1.26.0-rc8`、Mac `1.423.0-rc4`、Rust `0.115.0-rc3`。Rust 主工作区以远端 develop 的 `4130388`（`0.115.0-rc2`）为基线，包含只读查询不启动 Runtime 的最新修复。

## 行为与协议

| 操作 | 三端约定 |
|---|---|
| 列表 | `state.snapshot.queued_messages` 返回稳定 ID、所属会话、预览、完整可编辑正文及附件数量；隐藏内部唤醒指令和已被调度认领的轮次。 |
| 编辑 | `queue.update` / `action: edit` 带 `message_id`、`text`、`expected_text`；保留 ID、顺序、附件、来源与执行上下文。 |
| 过期编辑 | 宿主原文变化、消息已开始或不属于目标会话时拒绝；Android 保留草稿并刷新真实队列，不把操作失败误报为连接断开。 |
| 删除、清空 | 仅改变所属会话的待发用户消息；不取消正在执行的任务，不删除内部指令。Android 等待宿主快照回读。 |
| 立即发送 | 指定消息排到队首，包含排到内部唤醒消息之前；取消旧活动轮次，取消完成后通过正常串行调度启动。 |
| 断线、超时 | Android 保留编辑草稿；20 秒未收到确认解除保存状态，并提示刷新后重试。 |
| 旧宿主 | 缺少完整 `text` 或明确 `can_edit: false` 时不开放编辑，避免把截断预览覆盖为完整消息。 |

Mac 返回 `command.error`，Rust 返回 `error`；Android 统一解析为同一种错误事件。宿主附加上下文不会进入手机编辑框；更新正文时保留原上下文。

Rust 队列读取和修改均通过 Runtime 控制面。修改与 `claim_next` 按相同锁顺序执行，持久化副本成功后才发布内存状态；新增 API 继续走白名单、幂等与 Drain 闸门。

Rust 手机中继沿用现有架构，管理已经提交到 Runtime 的待发轮次；终端输入框中尚未提交到 Runtime 的本地草稿不属于网关对象。

## 复跑

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
CARGO_TARGET_DIR=/private/tmp/willdeep-mobile-queue-target \
ruby scripts/verify_mobile_queue.rb
```

验证器会先运行真实 Rust 中继/控制面与 Mac AppState/router 测试，再把两端生成的快照交给 Android 解码和编辑命令测试。各阶段保留日志，并生成 `build/mobile-queue-verification/report.json`、`report.md`；复跑时将旧结果归档至 `history/`，不会把旧快照当成本次证据。

验证器默认使用三个主工作区：`/Users/rocky/Sites/willdeep-rs`、`/Users/rocky/Sites/Xedit`、`/Users/rocky/Sites/willdeep-android`。Rust 工作分支为 `codex/mobile-queue-primary-latest`，Mac 为 `codex/mobile-queue-primary`。Rust 原有输入引用、二维码、失败诊断等改动与最新基线三方整合；新版 Esc 中断和已抽取模块采用最新实现。59 个原改动文件均经逐字节备份验证，备份位于 Rust `.build/integration-backups/mobile-queue-before-latest-20261024/`，其他未跟踪评测与日志产物保留原位。

## 验证记录

- Rust 网关：最终 34 项通过，包含真实 WebSocket 中继上的 add/edit/remove/clear/send_now、重启恢复、附件保留、过期与跨会话拒绝、认领/删除竞争、内部指令保护、持久化失败和重试幂等。
- Rust 主目录补充回归：TUI 317 项通过、2 项既有忽略；公共协议 37 项、客户端 8 项、只读 Runtime 查询 2 项、会话存储 22 项、事件脱敏 11 项、手机配置/二维码 10 项（另有 1 项忽略）、输入编辑 10 项、工作区版本一致性 1 项通过。详见 `build/mobile-queue-verification/primary-preservation.json` 和 `primary-preservation.md`。
- Mac 网关 31 项通过；版本一致性检查通过。
- Android Release 单测共 141 项：139 项通过、2 项可选宿主能力 fixture 测试跳过；其中两端真实队列快照的编辑兼容性测试已实际执行并通过。
- Android Release APK、交互测试 APK 构建与 lint 通过；lint 为 0 错误、119 项警告。最终联合报告状态为 `passed`，见 `build/mobile-queue-verification/report.json`。
- Android 已增加两项 Compose 交互测试并编译为测试 APK。本机没有连接设备及已安装的模拟器镜像，因此设备交互测试未执行，不计为通过。

过程中保留的失败与修正：Rust 暴露并修复了旧字符串命令 ID 重试不幂等；Mac 首轮构建因缺少固定版本插件子模块中止，初始化后复测；新增 Mac 用例按既有 `command.error` 信封修正断言；两个 Rust 真实网络测试在受限沙箱中无法监听回环端口，改在允许本机监听的环境复测通过。主目录复跑曾命中 Mac 成功结果缓存，缺少本轮快照，验证器按失败终止；现使用 `--force` 重新执行测试并保留编译缓存，确保 Android 每轮消费新快照。

本次已将改动移回主工作区；未安装、替换或重启现有 Runtime / Mac / Android 应用，未提交、推送或发布。
