# 更新日志

本文件记录 Slimefun4-Folia 分支自身的变更。上游 Slimefun 4 的物品与玩法内容随分支同步，不再逐条重复列出。

## [Folia] - 首个公开版本

### 新增

* `FoliaTaskQueue`：基于区域调度的任务队列，替代 dough 的 `TaskQueue`，
  支持 `thenRun`、延时执行与以 tick 为周期的重复执行。
* `PlayerTaskRegistry`：统一登记与取消跟随玩家的任务（喷气背包、磁铁、蜜蜂翅膀等），
  玩家跨区域传送、切换世界或下线时自动重建 / 清理。
* `SlimefunGuideRecipeListener`：指南书与配方预览在 Folia 多线程事件模型下的兼容处理。

### 变更

* 方块机器与多方块结构的计时全部改为区域化调度，机器只在所属区域中 tick。
* 护甲、辐射、彩虹护甲、太阳能头盔等全局任务改为提交到 `GlobalRegionScheduler`。
* `plugin.yml` 声明 `folia-supported: true`，服务端为 Folia / Canvas 时不再拒绝加载。
* 构建目标升级到 Java 21 与 Paper 1.21.x API，产物固定为 `target/Slimefun-Folia.jar`。

### 修复

* 修复 Folia 环境下因 `BukkitScheduler` 不可用导致的 `UnsupportedOperationException`
  与任务静默丢失。
* 修复区块卸载、区域迁移时残留的机器任务。
