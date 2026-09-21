# Rift

Rift `2.0.0-26.x` is a Paper world lifecycle manager built around safe names, recoverable operations, and public APIs. It requires Minecraft 26.x or newer and Java 25; older Bukkit, Spigot, Paper, and Java releases are intentionally unsupported.

## Capabilities

- Create, import, load, unload, inspect, and teleport between worlds.
- Keep validated TOML profiles for managed worlds and optionally load them at startup.
- Reconcile externally deleted worlds during startup by preserving their unprotected profiles under `plugins/Rift/worlds/retired/` and returning them to unmanaged state without repeated load failures.
- Create worlds with `rift:<key>` identities under `dimensions/rift/<key>` and import existing Paper dimensions without back-port storage fallbacks.
- Move confirmed deletions into a world-container quarantine and restore them by id.
- Protect selected worlds and always protect the primary world family.
- Enforce hot-reloadable per-world difficulty, PvP, game rules, safe spawn, borders, entry permissions, respawn routing, and operator tags through `/rift policy`; edit them in the 54-slot `/rift policy edit` menu or the configuration dashboard's World Policies shortcut.
- Hot-reload configuration, the selected TOML language file under `plugins/Rift/languages/`, and world profiles while retaining the last valid state; the shared picker exposes English plus 17 repository translations and materializes only a locale explicitly selected or opened for editing.
- Keep formatting under operator control with `runtime.prefix` and an optional `{prefix}` token on each prefixed message, so the prefix can be hidden globally or message by message.
- Create built-in void worlds with a safe spawn platform and `THE_VOID` biome.
- Edit every persisted setting from a full 54-slot, five-category dashboard, then open VolmLib's grouped, searchable 54-slot language-message editor from its Languages control.
- Choose a personal language independently from the server default, persist that choice by player UUID, reset it at any time, or coordinate server defaults and debug reports with other enabled Volmit plugins through `/volmit plugins`.
- Use configurable HUD-coordinated titles, action bars, and sounds for lifecycle, teleport, and failure feedback.
- Use granular permissions, contextual help, hover explanations, tab completion, Director-framed `/rift status`, `/rift check <name> [page]`, and paged `/rift list [page]` menus, plus nested `/rift debug dump [upload]` support reports.
- Stop managing an unprotected world with `/rift unmanage <name>` without unloading it, moving it, or changing its world files.
- Save comprehensive support reports under `plugins/Rift/debug/`, expose Rift through the shared debug-provider menu, and publish to mclo.gs by default with both a configured global switch and a per-command local-only override.
- Report anonymous standard usage metrics through the official bStats single-file runtime for plugin id `33701`, with both Rift-local and global bStats opt-outs.
- Load on Folia with scheduler-safe read, configuration, and teleport features; dynamic world lifecycle commands remain gated until Folia implements world load and unload APIs.

## Build

The build runs on JDK 25 and emits Java 25 class files:

```text
./gradlew clean build
```

On Windows, use `gradlew.bat clean build`. The shaded artifact is written under `build/libs/`; after tests and artifact verification pass, the build also stages `C:\VolmitSoftware\BUILDS\Rift.jar`.

`gradlew.bat buildSwiftSwamp` runs the same verified staging workflow. Failed verification leaves the previously staged jar unchanged.

The build uses the local `../VolmLib` composite when available. Set `-PuseLocalVolmLib=false` to resolve the configured remote VolmLib coordinate intentionally.

## Documentation

Operator documentation lives in the central [VolmitSoftware docs repository](https://github.com/VolmitSoftware/docs/tree/master/rift).
