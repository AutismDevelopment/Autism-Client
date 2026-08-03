# AUTISM Minimal Addon Template

Smallest useful AUTISM addon example:

- one module;
- one macro action;
- one macro condition.

This template uses the easy API (`SimpleAddon`, `SimpleModule`) and the one-call `registerSimpleAction` / `registerSimpleCondition` helpers.
Use `../advanced` when you need commands, HUD elements, events, raw custom macro serialization, or mixins.

## Build

1. From the AUTISM Client project, publish the API locally:
   ```powershell
   .\gradlew.bat publishToMavenLocal --no-daemon
   ```
2. From this folder, build the addon:
   ```powershell
   .\gradlew.bat build --no-daemon
   ```

The jar lands in `build/libs/`.

## Customize

Recommended (any OS, from the AUTISM Client repo root):

```sh
python addon-templates/addon-toolkit.py setup --template minimal --output ../MyAddon
```

Windows PowerShell works the same: `.\addon-templates\addon-toolkit.ps1 -Action Setup -Template minimal -OutputPath ..\MyAddon`. See `../README.md` for the Gradle (`./gradlew newAddon`) and `.sh` variants.

The toolkit asks for the addon name, output folder, version, and author. Blank version bumps the current
template version by one patch step. It derives the boring stuff and rewrites Gradle files, package paths,
`fabric.mod.json`, and entrypoints in one shot.

Manual path:

1. Rename package `com.example.minimal`.
2. Change `MinimalAddon.ID`.
3. Update `src/main/resources/fabric.mod.json`.
4. Change names/descriptions in `MinimalAddon`.
