# AUTISM Client Addon Templates

These are the official addon templates for AUTISM Client.

Put these in the same repo as the client source. They are standalone Fabric mod projects, but they depend on the AUTISM Client API being published to your local Maven repo first.

This is not some magical separate SDK. Build the client API locally, then build your addon.

## Templates

- `minimal/` - This is the one you should probably start with.

It gives you the clean basic setup without throwing the entire client architecture at your face. It includes:

one module
one simple macro action
one simple wait condition
the beginner-friendly API setup

It is intentionally small. You can actually read it without needing to reverse engineer the entire client.

- `advanced/` - This is the full reference template.

Use this when you want to see how the bigger stuff is supposed to be wired. It includes:

modules
macro actions
macro conditions
presets
commands
HUD elements
events
a mixin

Addon presets are fully supported. They show up in the macro editor `Presets` picker under your addon's
own category, the same way addon modules/actions/conditions do.

Category names are intentionally simple: your addon name is the public category. If an API lets you pass a
custom category label, treat that as local organization only. The user-facing UI still groups your stuff under
the addon name so the menu does not turn into a junk drawer.

Use this when you want to see how everything works together, or when the minimal template stops being enough.

## Build Flow

From the main AUTISM Client folder, publish the API locally:

.\gradlew.bat publishToMavenLocal --no-daemon

Then build the template you want.

Minimal:

cd addon-templates\minimal
.\gradlew.bat build --no-daemon

Advanced:

cd addon-templates\advanced
.\gradlew.bat build --no-daemon

Your addon jar will be in:

build/libs/

That is the file you load as the addon.

## Toolkit

The toolkit renames addon projects for you so you don't hand-edit Gradle files into a cursed broken
state. It's one cross-platform program (`addon-toolkit.py`) with a launcher for each platform - they all
do the exact same thing, so pick whatever fits your OS.

Windows (PowerShell), same commands as before:

.\addon-templates\addon-toolkit.ps1
.\addon-templates\addon-toolkit.ps1 -Action Setup -Template minimal -OutputPath ..\MyAddon
.\addon-templates\addon-toolkit.ps1 -Action Scan -ProjectPath ..\MyAddon
.\addon-templates\addon-toolkit.ps1 -Action BuildAll

Linux / macOS / anywhere with Python 3:

python addon-templates/addon-toolkit.py
python addon-templates/addon-toolkit.py setup --template minimal --output ../MyAddon
python addon-templates/addon-toolkit.py scan --project ../MyAddon
python addon-templates/addon-toolkit.py build-all

(./addon-templates/addon-toolkit.sh ... is the same thing if you'd rather run a shell script.)

Through Gradle. Building needs only the JDK; the scaffold/scan tasks call the Python toolkit:

./gradlew buildAllTemplates                                              # no Python needed
./gradlew newAddon -Pname="My Addon" -Ptemplate=minimal -Pout=../MyAddon  # needs Python
./gradlew scanAddon -Pproject=../MyAddon                                  # needs Python
./gradlew validateAddons                                                  # needs Python

Setup asks for:

addon name
output folder
version
author

If you leave the version blank, it bumps the current template version by one.

It also generates the annoying stuff automatically:

addon id
package name
Maven group
jar name
description
Fabric entrypoints
mixin references

This matters because half renaming a mod is one of the easiest ways to make Gradle look like it hates you personally.

If you want to set every value yourself, use:

.\addon-templates\addon-toolkit.ps1 -Action Setup -Template advanced -Advanced

Do that only if you actually need control over everything. Or edit the files directly.


## Public Repo Rule

Commit the source, Gradle wrapper files, and config files.

Do not commit:

.gradle/
build/

They are already ignored by the root .gitignore.

If it is generated trash from your machine, it probably does not belong in the repo.
