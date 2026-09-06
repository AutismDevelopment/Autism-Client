# AUTISM Advanced Addon Template

A standalone reference addon for AUTISM Client. It registers one of each supported extension point, so
you can copy it when you need the full API surface.

Start small: keep `ExampleModule`, build once, then add macro actions, commands, HUD elements, events,
or mixins as needed.

## What It Demonstrates

| Extension point | File |
| --- | --- |
| Addon lifecycle | `ExampleAddon` |
| Module | `modules/ExampleModule` |
| Macro action | `macro/ExampleSayAction` |
| Macro condition | `macro/ExampleHeightCondition` + `macro/ExampleCondition` |
| Macro preset | `macro/ExamplePresets` |
| Command | `commands/ExampleCommand` |
| HUD element | `hud/ExampleHud` |
| Event hooks | `events/ExampleEvents` |
| Mixin | `mixin/ExampleMixin` + `autism-advanced-addon-template.mixins.json` |

Presets are first-class addon entries. `ExamplePresets` appears in the macro editor `Presets` picker
under this addon's category, and it can insert any normal stack of macro actions.

Your modules, macro actions, conditions and presets automatically group under a category named after
your addon from `fabric.mod.json`. Custom categories are scoped under your addon so they cannot collide
with built-ins or another addon.

## Build

1. In the AUTISM Client project, publish the API to your local Maven repo:
   ```sh
   ./gradlew publishToMavenLocal
   ```
2. Make sure the `autism` version in `gradle/libs.versions.toml` matches what you published.
3. Build this addon:
   ```sh
   ./gradlew build
   ```

The addon jar lands in `build/libs/`.

## Run

Drop both jars into your `mods/` folder:

- the AUTISM Client jar;
- this addon's jar from `build/libs/`.

## Make It Yours

Recommended (any OS, from the AUTISM Client repo root):

```sh
python addon-templates/addon-toolkit.py setup --template advanced --output ../MyAdvancedAddon
```

Windows PowerShell works the same: `.\addon-templates\addon-toolkit.ps1 -Action Setup -Template advanced -OutputPath ..\MyAdvancedAddon`. See `../README.md` for the Gradle (`./gradlew newAddon`) and `.sh` variants.

The toolkit asks for the addon name, output folder, version, and author. Blank version bumps the current
template version by one patch step. It derives the boring stuff and rewrites Gradle files, package paths,
`fabric.mod.json`, entrypoints, and the mixin config in one shot.

Manual path:

1. Rename the package `com.example.addon`.
2. Change `ExampleAddon.ID`.
3. Update `src/main/resources/fabric.mod.json`: `id`, `name`, `entrypoints`, `mixins`, and `autism:color`.
4. Rename `autism-advanced-addon-template.mixins.json` and update its `package`.
5. Keep `apiVersion()` and the `autism` dependency range honest for the client version you target.

## Project Structure

```text
addon-templates/advanced/
|-- gradle/
|-- src/main/java/com/example/addon/
|   |-- ExampleAddon.java
|   |-- ExampleInit.java
|   |-- modules/ExampleModule.java
|   |-- macro/ExampleSayAction.java
|   |-- macro/ExampleHeightCondition.java
|   |-- macro/ExampleCondition.java
|   |-- macro/ExamplePresets.java
|   |-- commands/ExampleCommand.java
|   |-- hud/ExampleHud.java
|   |-- events/ExampleEvents.java
|   `-- mixin/ExampleMixin.java
|-- src/main/resources/
|   |-- fabric.mod.json
|   `-- autism-advanced-addon-template.mixins.json
|-- build.gradle.kts
`-- gradle.properties
```
