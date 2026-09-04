# Rift

Rift `2.0.0-1.20.1-26.2` is a Bukkit world lifecycle manager built around safe names, recoverable operations, and public APIs. It targets Java 17 bytecode and compiles against Spigot 1.20.1 plus current Paper and Spigot APIs without CraftBukkit or NMS bindings.

## Capabilities

- Create, import, load, unload, inspect, and teleport between worlds.
- Keep validated TOML profiles for managed worlds and optionally load them at startup.
- Reconcile externally deleted worlds during startup by preserving their unprotected profiles under `plugins/Rift/worlds/retired/` and returning them to unmanaged state without repeated load failures.
- Follow the server-selected world layout: classic standalone folders on older Bukkit implementations and standard `dimensions/minecraft/<key>` storage for simple-name worlds on Paper 26.1+.
- Move confirmed deletions into a world-container quarantine and restore them by id.
- Protect selected worlds and always protect the primary world family.
- Hot-reload configuration, the selected TOML language file under `plugins/Rift/languages/`, and world profiles while retaining the last valid state; the shared picker exposes English plus 17 repository translations and materializes only a locale explicitly selected or opened for editing.
- Keep formatting under operator control with `runtime.prefix` and an optional `{prefix}` token on each prefixed message, so the prefix can be hidden globally or message by message.
- Create built-in void worlds with a safe spawn platform and `THE_VOID` biome.
- Edit every persisted setting from a full 54-slot, five-category dashboard, then open VolmLib's grouped, searchable 54-slot language-message editor from its Languages control.
- Choose a personal language independently from the server default, persist that choice by player UUID, reset it at any time, or coordinate server defaults and debug reports with other enabled Volmit plugins through `/volmit plugins`.
- Use configurable HUD-coordinated titles, action bars, and sounds for lifecycle, teleport, and failure feedback.
- Use granular permissions, contextual help, hover explanations, tab completion, Director-framed `/rift status` and paged `/rift list [page]` menus, and nested `/rift debug dump [upload]` support reports.
- Save comprehensive support reports under `plugins/Rift/debug/`, expose Rift through the shared debug-provider menu, and publish to mclo.gs by default with both a configured global switch and a per-command local-only override.
- Report anonymous standard usage metrics through the official bStats single-file runtime for plugin id `33701`, with both Rift-local and global bStats opt-outs.
- Load on Folia with scheduler-safe read, configuration, and teleport features; dynamic world lifecycle commands remain gated until Folia implements world load and unload APIs.

## Build

The build runs on JDK 25 and emits Java 17 class files:

```text
./gradlew clean build
```

On Windows, use `gradlew.bat clean build`. The shaded artifact is written under `build/libs/`.

Use `gradlew.bat buildSwiftSwamp` to run the verification gates and copy the current shaded artifact to `C:\VolmitSoftware\BUILDS\Rift.jar`, matching the other VolmitSoftware plugin builds.

The build uses the local `../VolmLib` composite when available. Set `-PuseLocalVolmLib=false` to resolve the configured remote VolmLib coordinate intentionally.

## Documentation

Operator documentation lives in the central [VolmitSoftware docs repository](https://github.com/VolmitSoftware/docs/tree/master/rift).
