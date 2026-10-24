# AGENTS

面向本仓库（Applied-Energistics-2 GTO Edition，GTO 团队的 AE2 fork）协作与自动化代理的常驻约定。对外介绍见 [README.md](README.md)，本文件只保留常驻规则与规范引用。

## 规范引用（硬性）

本仓与 GTOCore 属同一项目组，Java/Kotlin 改动遵循同一套编码规范，改动前必读：

| 文档 | 作用 |
|---|---|
| [`../GTOCore-Main/CODING_GUIDELINES.md`](../GTOCore-Main/CODING_GUIDELINES.md) | **权威编码规范**：术语、容器规范、非容器规范、物品库存 IO、编解码、网络同步、事件注册等 |
| [`../GTOCore-Main/AGENTS.md`](../GTOCore-Main/AGENTS.md) | 团队协作约定、任务路由与构建校验流程 |

首次编辑或审查 Java/Kotlin 代码前，必须打开 `CODING_GUIDELINES.md` 中与改动相关的章节；改动完成后再按实际 diff 复查一遍。它是性能与正确性约束，不是可选建议。

### 本仓最容易踩的几条（摘自规范，改动自查用）

1. **禁止装箱容器承载原始类型**：`Map<Long, V>`/`Map<Integer, V>`/`Set<Integer>`/`HashMap<Long, V>` 一律换成 fastutil 原始容器（`Long2ObjectOpenHashMap`、`Int2ObjectOpenHashMap`、`LongOpenHashSet` 等）。
2. **声明类型要与实际容器一致**：字段/参数/返回值直接写具体类型（`Long2ObjectOpenHashMap<X>`），不要用 `Map<K, V>` 之类的宽接口接住再到处传——否则全程装箱，`computeIfAbsent`/`merge` 还会退回 `Map` 默认实现（两次哈希查找），并丢失 `fastIterator`/原始迭代器。
3. **对象集合用仓库既有类型**：`ObjectArrayList`、`AEKeyObjectMap`、`AEKeySet`、`KeyCounter`；复制容器优先 `clone()`，能预估元素数就预分配容量，空容器用 `Collections.emptyList()`/`emptySet()`/`emptyMap()`。
4. **热路径零无谓分配**：render/tick/网络包处理与逐配方循环里禁止 streams、`Optional` 链、`.boxed()`、临时集合与字符串 `+` 拼接。
5. **`AEKey` 是引用对象**，只能放进 `AEKeyMap`/`AEKeySet`/`KeyCounter`，不要塞普通 `HashMap`。
6. **非必要不写 `try/catch/finally`**；仅需在正常流程后复位的状态直接顺序赋值。
7. **不做过度防御**：调用契约已保证的前提不重复校验，公共 getter 直接复用已有容器，不返回 `unmodifiableXxx`/`copyOf` 包装。

## 本仓特有约定

- **包边界**：`appeng.*` 是 AE2 上游 fork 的代码，保持上游结构与风格，除功能/性能需要外不做无谓重排；`gto_ae.*` 是本团队的扩展（设施管理、2 合 1 终端、扩展图标与文案），必须完全按上述规范编写。
- **行尾与格式**：`.java` 一律 LF（CRLF 会被 Spotless 判为违规）。提交前依次运行 `.\gradlew.bat spotlessApply`、`.\gradlew.bat spotlessCheck`。
- **文案**：界面文本加到 `gto_ae.core.localization.ExtendedLangs`（中英双语，`name()` 自动转成 `ae2.gto_extension.xxx` 键）。`en_us` 由 datagen 生成；`zh_cn` 等翻译由 Crowdin 同步到 `src/main/resources/assets/ae2/lang/`，不要手工往生成文件里塞文案。
- **生成资源**：`src/generated/resources` 是 datagen 输出。改语言/模型/标签等内容时改真实生成源（如 `ExtendedLangs`、`LocalizationProvider`）后跑 `.\gradlew.bat runData`；手工编辑生成文件只能用于诊断，不作为最终结果。
- **图标**：扩展图标加在 `gto_ae.client.gui.IconsExtended`（`textures/guis/states_iicon.png` 的图集坐标）；终端工具栏按钮用 `addToLeftToolbar(new ToggleButton(...))` 挂载，并在 `updateBeforeRender()` 里同步状态。

## 构建与验证

- Windows 下先设 `JAVA_HOME`（JDK 21）再调用 Gradle，并确认 `$env:JAVA_HOME\bin\java.exe` 存在。
- 常用任务：`compileJava`（改代码）、`spotlessCheck`（格式）、`runData`（生成资源）。
- PowerShell 管道接 `gradlew` 会让 `$LASTEXITCODE` 失真：先把输出存进变量，再读退出码。
- 验证范围与改动相称，不要为纯文档修改跑 Gradle；收尾时报告实际执行过的检查。

## 收尾

- 只处理当前任务范围内的文件。本仓工作区常有并行、未提交的重构，动手前先看 `git status`。
- 不要用 `git checkout --`、`git stash` 等会吞掉他人未提交改动的操作去「清理」无关文件；需要临时对照时先备份并按哈希校验还原。
- 提交只带自己的路径（`git commit --only <paths>`）；推送前先 `git fetch` 对比远端差距。
