# 贡献指南

在向 Slimefun4-Folia 提交代码前，请先阅读本指南。

## 设置环境

本仓库使用 [Spotless](https://github.com/diffplug/spotless)（Palantir Java Format）统一代码风格，
首次克隆后请执行 `mvn spotless:apply` 初始化格式。

`.editorconfig` 描述了基础样式：UTF-8、LF 换行、4 空格缩进、单行 120 字符。请勿在提交中夹带无关的格式化改动。

## 分支

请基于 `main` 分支开发，并通过 Pull Request 提交。

## 提交信息规范

本项目**强制使用** [约定式提交](https://www.conventionalcommits.org/zh-hans/v1.0.0/)：

```
<类型>[可选 范围]: <描述>
```

例如：`feat(machine): add region-aware ticker` 或 `fix(task): cancel player tasks on region change`。

支持的类型前缀：`feat|fix|docs|style|refactor|ci|chore|perf|build|test|revert|trans`。

如果提交解决了某个 Issue，请在提交信息中声明，例如 `Fixes #12`。

## 代码规范

* **4 空格缩进**，单行不超过 120 字符，保持现有包结构（不要为了“整洁”重命名历史包名）。
* 涉及调度、任务、事件监听的代码必须在 **Folia** 上验证：不得直接使用 `BukkitScheduler`
  （`Bukkit.getScheduler()` / `runTaskAsynchronously` 等），请使用 `FoliaTaskQueue`、
  `PlayerTaskRegistry` 或 `Slimefun.runAtRegion(...)` 之类的区域化调度入口。
* 提交前必须执行 `mvn spotless:apply`，否则会被格式检查拦截。

## 测试

* 行为变更与缺陷修复请补充 JUnit 5 测试，测试类命名为 `*Test`，包结构与主代码保持一致。
* 本地至少执行 `mvn test`；提 PR 前请执行 `mvn clean package` 确认可以完整构建。
