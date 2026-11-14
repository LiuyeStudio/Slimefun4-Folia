# Slimefun4-Folia

**English** | [简体中文](README_zh-CN.md)

[![Version](https://img.shields.io/github/v/release/LiuyeStudio/Slimefun4-Folia?include_prereleases&label=version)](https://github.com/LiuyeStudio/Slimefun4-Folia/releases)
[![Downloads](https://img.shields.io/github/downloads/LiuyeStudio/Slimefun4-Folia/total)](https://github.com/LiuyeStudio/Slimefun4-Folia/releases)
[![Issues](https://img.shields.io/github/issues/LiuyeStudio/Slimefun4-Folia?label=Issues)](https://github.com/LiuyeStudio/Slimefun4-Folia/issues)
[![License](https://img.shields.io/badge/license-GPLv3-blue)](LICENSE)

**A Folia-compatible fork of Slimefun 4**, maintained by [LiuyeStudio](https://github.com/LiuyeStudio).

Slimefun turns a vanilla server into a modpack without a single mod installed: jetpacks, magic altars,
nuclear reactors, energy networks and cargo systems — over **500 items and recipes**, all from one plugin.

This fork solves exactly one thing: **making Slimefun actually run on Folia**.

---

## What this fork changes

Folia removes the global `BukkitScheduler` and splits the world into independently ticking regions.
Upstream Slimefun's "scan every machine on the server every tick" logic throws
`UnsupportedOperationException` on Folia. This fork reworks that piece by piece:

* **Region-aware scheduling** — block machines, armor effects, radiation, rainbow armor and the solar
  helmet now schedule through `RegionScheduler` / `GlobalRegionScheduler`, so a machine only ticks inside
  the region that owns it and the main thread load is spread naturally.
* **`FoliaTaskQueue`** — a region-aware rewrite of dough's `TaskQueue`
  (`city/norain/slimefun4/utils/FoliaTaskQueue.java`); every `thenRun` / `thenRun(delay)` /
  `thenRepeatEvery` step is dispatched to the region that owns the location.
* **Player task registry** — `PlayerTaskRegistry` owns all "follow the player" tasks (jetpack, magnet,
  bee wings, …), so they are cancelled and rebuilt correctly when a player crosses a region border,
  logs out or changes worlds, instead of leaking.
* **Guide and recipe listener fixes** — `SlimefunGuideRecipeListener` keeps the guide book and recipe
  previews reliable under Folia's multi-threaded event model.
* **Upstream compatibility** — the Slimefun 4 API and data formats are preserved, so addons load
  unmodified (`plugin.yml` still declares `name: Slimefun` and `folia-supported: true`).

## Requirements

| Item | Requirement |
| --- | --- |
| Server | **Folia** (or a Folia fork such as Canvas); plain **Paper** also works |
| Minecraft | 1.21.x (API built against Paper `1.21.11`) |
| Java | **21+** |
| Optional plugins | PlaceholderAPI, WorldEdit, Vault, mcMMO, ItemsAdder, ClearLag, Orebfuscator |

## Download

Grab the latest `Slimefun-Folia.jar` from [Releases](https://github.com/LiuyeStudio/Slimefun4-Folia/releases)
and drop it into your server's `plugins/` directory. The first start generates the configuration,
language files and database under `plugins/Slimefun/`.

> Back up `plugins/Slimefun/` before upgrading (especially `database.db` and `data-storage/`).

## Building from source

You need Git, Maven and JDK 21:

```bash
git clone https://github.com/LiuyeStudio/Slimefun4-Folia.git
cd Slimefun4-Folia
mvn clean package
```

The jar is written to `target/Slimefun-Folia.jar`. To build a custom version:

```bash
mvn clean package -Drevision=1.0.0
```

Useful commands:

* `mvn test` — run the unit tests
* `mvn spotless:check` / `mvn spotless:apply` — verify / fix code formatting (4 spaces, 120 columns)

## Using it as a dependency

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

## Reporting issues

Please use [Issues](https://github.com/LiuyeStudio/Slimefun4-Folia/issues) and include:

* your server software and version (Folia / Paper / Canvas) and Java version
* the Slimefun4-Folia version
* the full error log (`logs/latest.log`) and any file generated in `plugins/Slimefun/error-reports/`

## Credits

Development, Folia porting and releases of this fork are thanks to:

* **NukeBean** — Folia region scheduling, task model rework and ongoing maintenance
* **LiuyeStudio** — project maintenance, builds and releases

## License

This project is released under the [GNU GPLv3](LICENSE). It is derived from Slimefun 4 (GPLv3) and its
community editions; as required by the GPLv3, the original copyright notices and license terms remain in
effect, and the modifications in this repository are licensed under the GPLv3 as well.
