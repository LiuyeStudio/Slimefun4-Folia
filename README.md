# Slimefun4-Folia

[![版本](https://img.shields.io/github/v/release/LiuyeStudio/Slimefun4-Folia?include_prereleases&label=%E7%89%88%E6%9C%AC)](https://github.com/LiuyeStudio/Slimefun4-Folia/releases)
[![下载数](https://img.shields.io/github/downloads/LiuyeStudio/Slimefun4-Folia/total?label=%E4%B8%8B%E8%BD%BD)](https://github.com/LiuyeStudio/Slimefun4-Folia/releases)
[![Issues](https://img.shields.io/github/issues/LiuyeStudio/Slimefun4-Folia?label=Issues)](https://github.com/LiuyeStudio/Slimefun4-Folia/issues)
[![许可证](https://img.shields.io/badge/%E8%AE%B8%E5%8F%AF%E8%AF%81-GPLv3-blue)](LICENSE)

**Slimefun 4 的 Folia 兼容分支**，由 [流叶科技工作室（LiuyeStudio）](https://github.com/LiuyeStudio) 维护。

Slimefun 把原版服务器变成一台“无需安装模组的整合包”：从喷气背包、魔法祭坛到核反应堆、能源网络与货运系统，
超过 **500 种**物品与配方全部由一个插件提供。

而这个分支解决的只有一件事：**让 Slimefun 在 Folia 上真正跑起来**。

---

## 本分支做了什么

Folia 取消了全局的 `BukkitScheduler`，把世界切成一个个独立计时的区域（Region）。原版 Slimefun 里那些
“每一刻全局扫一遍所有机器”的逻辑在 Folia 上会直接抛 `UnsupportedOperationException`。本分支逐项改造：

* **区域化调度**：方块机器、护甲效果、辐射、彩虹护甲、太阳能头盔等定时任务全部改为提交给
  `RegionScheduler` / `GlobalRegionScheduler`，机器只在它所在的区域里计时，天然分摊主线程压力。
* **`FoliaTaskQueue`**：用区域调度重写了 dough 的 `TaskQueue`（`city/norain/slimefun4/utils/FoliaTaskQueue.java`），
  `thenRun` / `thenRun(delay)` / `thenRepeatEvery` 的每一步都投递到持有该位置的区域执行。
* **玩家任务注册表**：`PlayerTaskRegistry` 统一管理喷气背包、磁铁、蜜蜂翅膀等“跟随玩家”的任务，玩家跨区域、
  下线、切换世界时都能正确取消与重建，不再出现任务泄漏。
* **指南与配方监听修复**：新增 `SlimefunGuideRecipeListener`，保证在 Folia 的多线程事件模型下指南书与配方预览仍然可靠。
* **上游同步**：保留 Slimefun 4 的 API 与数据格式，附属插件无需修改即可加载（`plugin.yml` 中仍有
  `folia-supported: true`）。

## 环境要求

| 项目 | 要求 |
| --- | --- |
| 服务端 | **Folia**（或 Canvas 等 Folia 分支），也兼容普通 **Paper** |
| Minecraft | 1.21.x（API 基于 Paper `1.21.11`） |
| Java | **21+** |
| 可选依赖 | PlaceholderAPI、WorldEdit、Vault、mcMMO、ItemsAdder、ClearLag、Orebfuscator |

## 下载

前往 [Releases](https://github.com/LiuyeStudio/Slimefun4-Folia/releases) 下载最新的 `Slimefun-Folia.jar`，
丢进服务端的 `plugins/` 目录即可。首次启动会在 `plugins/Slimefun/` 下生成配置、语言文件与数据库。

> 升级前请备份 `plugins/Slimefun/`（尤其是 `database.db` 与 `data-storage/`）。

## 从源码构建

需要 Git、Maven 与 JDK 21：

```bash
git clone https://github.com/LiuyeStudio/Slimefun4-Folia.git
cd Slimefun4-Folia
mvn clean package
```

产物位于 `target/Slimefun-Folia.jar`。构建自定义版本：

```bash
mvn clean package -Drevision=1.0.0
```

常用命令：

* `mvn test` — 运行单元测试
* `mvn spotless:check` / `mvn spotless:apply` — 检查 / 自动修正代码格式（4 空格缩进，120 列）

## 作为依赖使用

```xml
<repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
</repository>

<dependency>
    <groupId>com.github.LiuyeStudio</groupId>
    <artifactId>Slimefun4-Folia</artifactId>
    <version>Folia-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

## 反馈问题

请在 [Issues](https://github.com/LiuyeStudio/Slimefun4-Folia/issues) 中反馈，并附上：

* 服务端类型与版本（Folia / Paper / Canvas）、Java 版本
* Slimefun4-Folia 的版本号
* 完整报错日志（`logs/latest.log`），以及 `plugins/Slimefun/error-reports/` 中生成的文件

## 鸣谢

本分支的开发、适配与发布，感谢以下贡献者：

* **核动豆（NukeBean）** — Folia 区域化调度适配、任务模型重构与持续维护
* **流叶科技工作室（LiuyeStudio）** — 项目维护、构建与发布

## 许可证

本项目以 [GNU GPLv3](LICENSE) 协议开源。它派生自 Slimefun 4（GPLv3）及其社区版本，
按照 GPLv3 的要求，原始版权声明与许可条款继续有效；本仓库的修改同样以 GPLv3 发布。
