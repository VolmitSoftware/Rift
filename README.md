# Rift

World management for Paper: create, import, load, unload, teleport, and recover deleted worlds from quarantine.

Requires Minecraft **26.1 through 26.3**, including patch releases, and **Java 25 or newer**. Folia can use the inspection, configuration, and teleport tools; dynamic world lifecycle operations are disabled.

Run `/rift help` for commands, `/rift config` for settings, and `/rift policy edit` for per-world rules. Configuration, profiles, and installed language files hot-load automatically.

## Build

```shell
./gradlew clean build
```

On Windows, use `gradlew.bat clean build`. The verified distribution is written to `build/libs/` and staged as `../BUILDS/Rift.jar`; `-PbuildsDirectory=<path>` changes the staging directory. A sibling VolmLib checkout is used automatically; `-PuseLocalVolmLib=false` selects the published dependency.

## Links

- [Documentation](https://github.com/VolmitSoftware/docs/blob/master/rift.md)
- [Releases](https://github.com/VolmitSoftware/Rift/releases)
- [Support](https://volmitsoftware.com/discord)
