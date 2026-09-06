#!/usr/bin/env python3
"""AUTISM addon toolkit: scaffold, scan, validate and build addons. Runs anywhere with Python 3.

  python addon-toolkit.py                 # menu
  python addon-toolkit.py setup --template minimal --name "My Addon" --output ../MyAddon
  python addon-toolkit.py scan --project ../MyAddon
  python addon-toolkit.py build --project ../MyAddon
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent
TEMPLATES = ("minimal", "advanced")
TEXT_EXTS = {".java", ".json", ".kts", ".toml", ".properties", ".md", ".yml", ".yaml"}


class ToolkitError(Exception):
    """Expected failure, shown without a traceback."""


def info(msg: str) -> None: print(f"[AUTISM] {msg}")
def ok(msg: str) -> None: print(f"[OK] {msg}")
def warn(msg: str) -> None: print(f"[WARN] {msg}")
def bad(msg: str) -> None: print(f"[FAIL] {msg}")


def path_line(label: str, path) -> None:
    print(f"{label}:")
    print(str(path))


def failure_item(msg: str) -> None:
    if re.match(r"^[A-Za-z]:[\\/]", msg) or msg.startswith("/") or re.match(r"^\.{1,2}[\\/]", msg):
        path_line("Path", msg)
    else:
        bad(f" - {msg}")


def is_interactive(args) -> bool:
    return not getattr(args, "non_interactive", False) and sys.stdin.isatty()


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def write_text(path: Path, text: str) -> None:
    path.write_text(text, encoding="utf-8", newline="")


def to_folder_name(name: str) -> str:
    if not name or not name.strip():
        return "MyAddon"
    parts = re.findall(r"[A-Za-z0-9]+", name)
    if not parts:
        return "MyAddon"
    folder = "".join(p.upper() if len(p) == 1 else p[0].upper() + p[1:] for p in parts)
    return folder[:48]


def to_package_segment(value: str) -> str:
    segment = re.sub(r"[^a-z0-9]+", "", value.lower())
    if not segment:
        segment = "myaddon"
    if not segment[0].isalpha():
        segment = "addon" + segment
    return segment


def to_mod_id(name: str) -> str:
    mod_id = re.sub(r"[^a-z0-9_-]+", "-", name.lower()).strip("-_")
    if not mod_id:
        return "my-autism-addon"
    if not mod_id[0].isalpha():
        mod_id = "addon-" + mod_id
    if len(mod_id) > 64:
        mod_id = mod_id[:64].strip("-_")
    return mod_id


def default_author() -> str:
    return os.environ.get("USERNAME") or os.environ.get("USER") or "You"


def test_mod_id(value: str) -> bool:
    return bool(re.match(r"^[a-z][a-z0-9_-]{1,63}$", value or ""))


def test_java_package(value: str) -> bool:
    return bool(re.match(r"^[a-zA-Z_][a-zA-Z0-9_]*(\.[a-zA-Z_][a-zA-Z0-9_]*)+$", value or ""))


def test_version(value: str) -> bool:
    return bool(re.match(r"^[0-9A-Za-z][0-9A-Za-z._+-]*$", value or ""))


def step_version(version: str) -> str:
    if not version or not version.strip():
        return "1.0.0"
    m = re.match(r"^(.*?)(\d+)(\D*)$", version)
    if not m:
        return version + ".1"
    prefix, digits, suffix = m.group(1), m.group(2), m.group(3)
    nxt = str(int(digits) + 1)
    if len(digits) > 1 and len(nxt) < len(digits):
        nxt = nxt.rjust(len(digits), "0")
    return f"{prefix}{nxt}{suffix}"


def get_toml_version(path: Path, key: str) -> str:
    m = re.search(r'(?m)^\s*' + re.escape(key) + r'\s*=\s*"([^"]+)"', read_text(path))
    return m.group(1) if m else ""


def set_toml_version(path: Path, key: str, value: str) -> None:
    text = read_text(path)
    pattern = r'(?m)^(\s*' + re.escape(key) + r'\s*=\s*)"[^"]*"'
    if re.search(pattern, text):
        text = re.sub(pattern, lambda m: m.group(1) + f'"{value}"', text)
    else:
        text += f'\n{key} = "{value}"\n'
    write_text(path, text)


def set_properties_value(path: Path, key: str, value: str) -> None:
    text = read_text(path)
    pattern = r"(?m)^" + re.escape(key) + r"=.*$"
    if re.search(pattern, text):
        text = re.sub(pattern, f"{key}={value}", text)
    else:
        text += f"\n{key}={value}\n"
    write_text(path, text)


def set_root_project_name(path: Path, name: str) -> None:
    text = read_text(path)
    pattern = r'(?m)^(rootProject\.name\s*=\s*)"[^"]*"'
    if re.search(pattern, text):
        text = re.sub(pattern, lambda m: m.group(1) + f'"{name}"', text)
    else:
        text += f'\nrootProject.name = "{name}"\n'
    write_text(path, text)


def get_json(path: Path):
    return json.loads(read_text(path))


def save_json(path: Path, data) -> None:
    write_text(path, json.dumps(data, indent=2, ensure_ascii=False) + "\n")


def is_under_build_or_gradle(path: Path, project: Path) -> bool:
    try:
        parts = path.relative_to(project).parts
    except ValueError:
        return False
    return "build" in parts or ".gradle" in parts


def resolve_full_path(path: str) -> Path | None:
    if not path or not path.strip():
        return None
    p = Path(path)
    if p.is_absolute():
        return p.resolve()
    return (REPO_ROOT / p).resolve()


def is_shipped_template(path: Path) -> bool:
    full = path.resolve()
    return any(full == (REPO_ROOT / "addon-templates" / name).resolve() for name in TEMPLATES)


def get_first_java_package(project: Path) -> str:
    entry = project / "src/main/resources/fabric.mod.json"
    if entry.is_file():
        data = get_json(entry)
        classes = []
        eps = data.get("entrypoints", {}) if isinstance(data, dict) else {}
        for kind in ("client", "autism"):
            if eps.get(kind):
                classes += list(eps[kind]) if isinstance(eps[kind], list) else [eps[kind]]
        for cls in classes:
            m = re.match(r"^(.+)\.[^.]+$", str(cls))
            if m:
                return m.group(1)
    java_root = project / "src/main/java"
    if java_root.is_dir():
        for java in sorted(java_root.rglob("*.java")):
            m = re.search(r"(?m)^\s*package\s+([^;]+);", read_text(java))
            if m:
                return m.group(1).strip()
    return "com.example.addon"


def class_simple_name(class_name: str, fallback: str) -> str:
    m = re.search(r"\.([^.]+)$", class_name or "")
    return m.group(1) if m else fallback


def replace_in_project_files(project: Path, old: str, new: str) -> None:
    if not old or old == new:
        return
    for file in project.rglob("*"):
        if not file.is_file() or file.suffix not in TEXT_EXTS:
            continue
        if is_under_build_or_gradle(file, project):
            continue
        text = read_text(file)
        if old in text:
            write_text(file, text.replace(old, new))


def move_java_package(project: Path, old_pkg: str, new_pkg: str) -> None:
    if old_pkg == new_pkg:
        return
    src_root = project / "src/main/java"
    old_path = src_root / old_pkg.replace(".", os.sep)
    new_path = src_root / new_pkg.replace(".", os.sep)
    if not old_path.is_dir():
        return
    if new_path.exists():
        warn("Package path already exists. Not moving files.")
        path_line("Path", new_path)
        return
    new_path.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(str(old_path), str(new_path))
    cursor = old_path.parent
    while src_root in cursor.parents and not any(cursor.iterdir()):
        parent = cursor.parent
        cursor.rmdir()
        cursor = parent


def copy_addon_template(template_name: str, target: Path) -> None:
    source = REPO_ROOT / "addon-templates" / template_name
    if not source.is_dir():
        path_line("Missing template", source)
        raise ToolkitError("Template not found.")
    if target.exists():
        warn("Output already exists. Editing it in place.")
        path_line("Path", target)
        return
    target.mkdir(parents=True, exist_ok=True)
    for item in source.rglob("*"):
        if is_under_build_or_gradle(item, source):
            continue
        rel = item.relative_to(source)
        dest = target / rel
        if item.is_dir():
            dest.mkdir(parents=True, exist_ok=True)
        else:
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(item, dest)


def prompt(label: str, default: str, validator=None, interactive: bool = True) -> str:
    if not interactive:
        return default
    while True:
        suffix = f" [{default}]" if default else ""
        try:
            value = input(f"{label}{suffix}: ").strip()
        except EOFError:
            return default
        if not value:
            value = default
        if validator is None or validator(value):
            return value
        warn("Bad value. Fix it.")


def prompt_version(current: str, interactive: bool) -> str:
    base = current if current and current.strip() else "1.0.0"
    nxt = step_version(base)
    if not interactive:
        return nxt
    while True:
        try:
            value = input(f"Version current {base}, blank = {nxt}: ").strip()
        except EOFError:
            return nxt
        if not value:
            return nxt
        if test_version(value):
            return value
        warn("Bad version. Fix it.")


def get_setup_project_path(args, name_for_output: str) -> Path:
    if args.project and args.project.strip():
        project = resolve_full_path(args.project)
        if is_shipped_template(project):
            raise ToolkitError("No. The shipped templates stay clean. Use --output to copy one, or pick a real addon folder.")
        return project
    output = args.output
    if not output or not output.strip():
        default_out = str((REPO_ROOT / ".." / to_folder_name(name_for_output)).resolve())
        output = prompt("Output folder", default_out, lambda v: bool(v and v.strip()), is_interactive(args))
    target = resolve_full_path(output)
    if is_shipped_template(target):
        raise ToolkitError("No. The shipped templates stay clean. Pick a different output folder.")
    copy_addon_template(args.template, target)
    return target


def configure_addon(args) -> Path:
    interactive = is_interactive(args)
    addon_name = args.addon_name
    if not addon_name or not addon_name.strip():
        default_name = "My Advanced Addon" if args.template == "advanced" else "My Addon"
        addon_name = prompt("Addon name", default_name, lambda v: bool(v and v.strip()), interactive)

    project = get_setup_project_path(args, addon_name)
    if not project.is_dir():
        path_line("Missing addon folder", project)
        raise ToolkitError("Addon project not found.")

    fabric_path = project / "src/main/resources/fabric.mod.json"
    versions_path = project / "gradle/libs.versions.toml"
    properties_path = project / "gradle.properties"
    settings_path = project / "settings.gradle.kts"
    for required in (fabric_path, versions_path, properties_path, settings_path):
        if not required.is_file():
            path_line("Missing file", required)
            raise ToolkitError("Missing required addon file.")

    fabric = get_json(fabric_path)
    old_id = str(fabric.get("id", ""))
    old_package = get_first_java_package(project)
    old_version = get_toml_version(versions_path, "mod-version")
    eps = fabric.get("entrypoints", {}) or {}
    old_client = str((eps.get("client") or [f"{old_package}.Init"])[0])
    old_addon = str((eps.get("autism") or [f"{old_package}.Addon"])[0])
    original_mixin = str((fabric.get("mixins") or [""])[0])
    client_simple = class_simple_name(old_client, "Init")
    addon_simple = class_simple_name(old_addon, "Addon")

    advanced = args.advanced

    addon_id = args.addon_id
    if not addon_id or not addon_id.strip():
        suggested = to_mod_id(addon_name) if "template" in old_id else old_id
        addon_id = prompt("Addon id", suggested, test_mod_id, interactive) if advanced else suggested

    addon_version = args.addon_version
    if not addon_version or not addon_version.strip():
        addon_version = prompt_version(old_version or "1.0.0", interactive)

    package = args.package
    if not package or not package.strip():
        suggested_pkg = f"com.{to_package_segment(addon_id)}.addon"
        package = prompt("Java package", suggested_pkg, test_java_package, interactive) if advanced else suggested_pkg

    maven_group = args.maven_group
    if not maven_group or not maven_group.strip():
        maven_group = prompt("Maven group", package, test_java_package, interactive) if advanced else package

    archive_name = args.archive_name
    if not archive_name or not archive_name.strip():
        archive_name = prompt("Jar/archive base name", addon_id, test_mod_id, interactive) if advanced else addon_id

    author = args.author
    if not author or not author.strip():
        author = prompt("Author", default_author(), lambda v: bool(v and v.strip()), interactive)

    description = args.description
    if not description or not description.strip():
        suggested_desc = f"{addon_name} addon for AUTISM Client."
        description = prompt("Description", suggested_desc, lambda v: bool(v and v.strip()), interactive) if advanced else suggested_desc

    replace_in_project_files(project, old_package, package)
    replace_in_project_files(project, old_id, addon_id)
    move_java_package(project, old_package, package)

    fabric = get_json(fabric_path)
    fabric["id"] = addon_id
    fabric["name"] = addon_name
    fabric["version"] = "${version}"
    fabric["description"] = description
    fabric["authors"] = [author]
    fabric.setdefault("entrypoints", {})
    fabric["entrypoints"]["client"] = [f"{package}.{client_simple}"]
    fabric["entrypoints"]["autism"] = [f"{package}.{addon_simple}"]

    if fabric.get("mixins"):
        old_mixin_name = original_mixin or str(fabric["mixins"][0])
        new_mixin_name = f"{addon_id}.mixins.json"
        resource_dir = project / "src/main/resources"
        old_mixin = resource_dir / old_mixin_name
        new_mixin = resource_dir / new_mixin_name
        if old_mixin.is_file() and old_mixin != new_mixin:
            shutil.move(str(old_mixin), str(new_mixin))
        if new_mixin.is_file():
            mixin_json = get_json(new_mixin)
            mixin_json["package"] = f"{package}.mixin"
            save_json(new_mixin, mixin_json)
        fabric["mixins"] = [new_mixin_name]

    save_json(fabric_path, fabric)
    set_toml_version(versions_path, "mod-version", addon_version)
    set_properties_value(properties_path, "maven_group", maven_group)
    set_properties_value(properties_path, "archives_base_name", archive_name)
    set_root_project_name(settings_path, addon_id)

    ok("Addon rewritten.")
    path_line("Addon folder", project)
    print(f"  id:      {addon_id}")
    print(f"  name:    {addon_name}")
    print(f"  version: {addon_version}")
    print(f"  package: {package}")
    print()
    print("Next:")
    print(f"  python addon-templates/addon-toolkit.py build --project \"{project}\"")
    path_line("Jar folder", project / "build" / "libs")
    return project


def test_addon_project(project: Path, allow_template_names: bool = False):
    failures: list[str] = []
    warnings: list[str] = []

    if not project.is_dir():
        failures.append("Project folder does not exist.")
        failures.append(str(project))
        return failures, warnings

    required = [
        "build.gradle.kts", "settings.gradle.kts", "gradle.properties",
        "gradle/libs.versions.toml", "src/main/resources/fabric.mod.json",
    ]
    for item in required:
        if not (project / item).is_file():
            failures.append(f"Missing {item}")
    if not (project / "gradlew.bat").is_file() and not (project / "gradlew").is_file():
        failures.append("Missing gradlew / gradlew.bat")

    fabric_path = project / "src/main/resources/fabric.mod.json"
    if fabric_path.is_file():
        try:
            fabric = get_json(fabric_path)
            if not test_mod_id(str(fabric.get("id", ""))):
                failures.append(f"fabric.mod.json id is invalid: {fabric.get('id')}")
            if not str(fabric.get("name", "")).strip():
                failures.append("fabric.mod.json name is empty.")
            eps = fabric.get("entrypoints", {}) or {}
            if not eps.get("autism"):
                failures.append("Missing autism entrypoint in fabric.mod.json.")
            else:
                for entry in eps["autism"]:
                    cls = project / ("src/main/java/" + str(entry).replace(".", "/") + ".java")
                    if not cls.is_file():
                        failures.append(f"Autism entrypoint class file is missing: {entry}")
            if not (fabric.get("depends", {}) or {}).get("autism"):
                failures.append("fabric.mod.json must depend on autism.")
            if not allow_template_names:
                if re.search(r"template|example", str(fabric.get("id", ""))):
                    warnings.append("Addon id still looks like a template/example.")
                if re.search(r"Template|Example", str(fabric.get("name", ""))):
                    warnings.append("Addon name still looks like a template/example.")
                if "You" in (fabric.get("authors") or []):
                    warnings.append("Author is still 'You'.")
        except json.JSONDecodeError as exc:
            failures.append(f"fabric.mod.json is not valid JSON: {exc}")

    versions_path = project / "gradle/libs.versions.toml"
    if versions_path.is_file():
        if not test_version(get_toml_version(versions_path, "mod-version")):
            failures.append("Addon mod-version is missing or invalid.")
        if not get_toml_version(versions_path, "autism").strip():
            failures.append("AUTISM dependency version is missing from gradle/libs.versions.toml.")

    if (project / ".gradle").exists():
        warnings.append(".gradle cache exists locally. Ignored; do not upload it.")
    if (project / "build").exists():
        warnings.append("build output exists locally. Ignored; do not upload it.")
    if (project / ".github").exists():
        warnings.append(".github exists inside this addon project. Fine for a copied template, but it is not root repo CI unless you move it.")

    leftovers = (
        "autism-minimal-addon-template", "autism-advanced-addon-template",
        "AUTISM Minimal Addon Template", "AUTISM Advanced Addon Template",
    )
    for file in project.rglob("*"):
        if not file.is_file() or file.suffix not in TEXT_EXTS or is_under_build_or_gradle(file, project):
            continue
        text = read_text(file)
        if re.search(r"[ÂÃâ]", text):
            warnings.append(f"Mojibake-looking text in {file}")
        if not allow_template_names:
            for leftover in leftovers:
                if leftover in text:
                    warnings.append(f"Template leftover '{leftover}' in {file}")
                    break
    return failures, warnings


def get_addon_project_path(args) -> Path:
    if args.project and args.project.strip():
        return resolve_full_path(args.project)
    if args.output and args.output.strip():
        target = resolve_full_path(args.output)
        copy_addon_template(args.template, target)
        return target
    return REPO_ROOT / "addon-templates" / args.template


def scan_addon(args) -> None:
    project = get_addon_project_path(args)
    failures, warnings = test_addon_project(project)
    info("Scanned addon project.")
    path_line("Addon folder", project)
    for w in warnings:
        warn(f"Heads up: {w}")
    if failures:
        bad("Scan found broken stuff:")
        for f in failures:
            failure_item(f)
        raise ToolkitError("Scan failed.")
    ok("Scan clean.")


def validate_addon_system(args) -> None:
    failures: list[str] = []
    warnings: list[str] = []

    root_required = [
        "src/main/java/autismclient/addons/AddonManager.java",
        "src/main/java/autismclient/api/AutismAddon.java",
        "src/main/java/autismclient/api/SimpleAddon.java",
        "src/main/java/autismclient/api/AutismAddons.java",
        "src/main/java/autismclient/api/ApiVersion.java",
        "src/main/java/autismclient/api/module/SimpleModule.java",
        "src/main/java/autismclient/api/macro/SimpleAction.java",
        "src/main/java/autismclient/api/macro/SimpleCondition.java",
        "src/main/java/autismclient/api/macro/MacroActionRegistry.java",
        "src/main/java/autismclient/api/macro/MacroPresetRegistry.java",
        "src/main/java/autismclient/api/hud/HudElements.java",
        "src/main/java/autismclient/api/event/AddonEvents.java",
        "src/main/java/autismclient/gui/screen/AutismAddonsScreen.java",
        "src/main/java/autismclient/util/macro/MissingAddonAction.java",
        "addon-templates/README.md",
        "addon-templates/addon-toolkit.py",
        "addon-templates/addon-toolkit.ps1",
        "addon-templates/addon-toolkit.sh",
    ]
    for f in root_required:
        if not (REPO_ROOT / f).is_file():
            failures.append(f"Missing {f}")

    gitignore = REPO_ROOT / ".gitignore"
    if gitignore.is_file():
        ignore_text = read_text(gitignore)
        for pattern in ("!/src/", "!/addon-templates/", "!/addon-templates/**",
                        "/addon-templates/**/build/", "/addon-templates/**/.gradle/", "/mc-src*/"):
            if pattern not in ignore_text:
                failures.append(f".gitignore is missing {pattern}")
    else:
        failures.append("Missing .gitignore")

    main_version = get_toml_version(REPO_ROOT / "gradle/libs.versions.toml", "mod-version")
    for template_name in TEMPLATES:
        project = REPO_ROOT / "addon-templates" / template_name
        t_failures, t_warnings = test_addon_project(project, allow_template_names=True)
        failures += [f"{template_name} template: {x}" for x in t_failures]
        warnings += [f"{template_name} template: {x}" for x in t_warnings]
        target_version = get_toml_version(project / "gradle/libs.versions.toml", "autism")
        if main_version and target_version and main_version != target_version:
            failures.append(f"{template_name} template targets AUTISM {target_version}, main client is {main_version}.")

    for w in warnings:
        warn(f"Heads up: {w}")
    if failures:
        bad("Addon system is broken:")
        for f in failures:
            failure_item(f)
        raise ToolkitError("Addon system validation failed.")
    ok("Addon system clean.")


def run_gradle(project: Path, gradle_args: list[str]) -> None:
    wrapper = project / ("gradlew.bat" if os.name == "nt" else "gradlew")
    if not wrapper.is_file():
        path_line("Missing Gradle wrapper", wrapper)
        raise ToolkitError("Missing Gradle wrapper.")
    cmd = [str(wrapper)] + gradle_args
    if os.name != "nt" and not os.access(wrapper, os.X_OK):
        cmd = ["sh", str(wrapper)] + gradle_args
    result = subprocess.run(cmd, cwd=str(project))
    if result.returncode != 0:
        path_line("Gradle project", project)
        raise ToolkitError(f"Gradle failed with exit code {result.returncode}.")


def publish_api(args) -> None:
    info("Publishing client API to Maven Local.")
    run_gradle(REPO_ROOT, ["publishToMavenLocal", "--no-daemon"])
    ok("Client API published.")


def build_addon(args) -> None:
    project = get_addon_project_path(args)
    if not project.is_dir():
        path_line("Addon folder", project)
        raise ToolkitError("Wrong addon folder.")
    if not (project / "gradlew.bat").is_file() and not (project / "gradlew").is_file():
        path_line("Addon folder", project)
        raise ToolkitError("No Gradle wrapper there. Wrong addon folder.")
    if not args.no_publish:
        publish_api(args)
    info("Building addon.")
    path_line("Addon folder", project)
    run_gradle(project, ["build", "--no-daemon"])
    ok("Addon built.")
    path_line("Jar folder", project / "build" / "libs")


def build_all_templates(args) -> None:
    if not args.no_publish:
        publish_api(args)
    for template_name in TEMPLATES:
        project = REPO_ROOT / "addon-templates" / template_name
        info(f"Building {template_name} template.")
        run_gradle(project, ["build", "--no-daemon"])
        ok(f"{template_name} template built.")


def clean_addon(args) -> None:
    project = get_addon_project_path(args)
    interactive = is_interactive(args)
    for folder in (".gradle", "build"):
        path = project / folder
        if path.exists():
            confirmed = args.yes or (interactive and input(f"Remove {path} ? Type YES: ").strip() == "YES")
            if confirmed:
                shutil.rmtree(path)
                ok("Deleted local build junk.")
                path_line("Path", path)


def menu(args) -> None:
    last_path = ""
    while True:
        print()
        print("AUTISM ADDON KIT")
        print("1. New addon")
        print("2. Build addon")
        print("3. Scan addon")
        print("4. Test shipped templates")
        print("5. Publish client API")
        print("6. Delete local build junk")
        print("0. Leave")
        try:
            choice = input("> ").strip()
        except EOFError:
            return
        try:
            if choice == "1":
                args.template = prompt("Template minimal/advanced", args.template, lambda v: v in TEMPLATES, True)
                args.project = ""
                args.output = ""
                last_path = str(configure_addon(args))
            elif choice == "2":
                default_path = last_path or str((REPO_ROOT / ".." / to_folder_name("")).resolve())
                args.project = prompt("Addon folder", default_path, lambda v: bool(v and v.strip()), True)
                build_addon(args)
            elif choice == "3":
                default_path = last_path or str((REPO_ROOT / ".." / to_folder_name("")).resolve())
                args.project = prompt("Addon folder", default_path, lambda v: bool(v and v.strip()), True)
                scan_addon(args)
            elif choice == "4":
                validate_addon_system(args)
                build_all_templates(args)
            elif choice == "5":
                publish_api(args)
            elif choice == "6":
                default_path = last_path or str((REPO_ROOT / ".." / to_folder_name("")).resolve())
                args.project = prompt("Addon folder", default_path, lambda v: bool(v and v.strip()), True)
                clean_addon(args)
            elif choice == "0":
                return
            else:
                warn("Bad choice.")
        except ToolkitError as exc:
            bad(str(exc))
            try:
                input("Press Enter. The error stays here until you do")
            except EOFError:
                return


def normalize_action(value: str) -> str:
    v = value.strip().lower()
    return {"publishapi": "publish-api", "buildall": "build-all"}.get(v, v)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="AUTISM addon toolkit.")
    parser.add_argument("action", nargs="?", default="menu", type=normalize_action,
                        choices=["menu", "setup", "scan", "validate", "publish-api", "build", "build-all", "clean"])
    parser.add_argument("--template", type=str.lower, choices=list(TEMPLATES), default="minimal")
    parser.add_argument("--project", "--project-path", dest="project")
    parser.add_argument("--output", "--output-path", dest="output")
    parser.add_argument("--addon-id", dest="addon_id")
    parser.add_argument("--name", "--addon-name", dest="addon_name")
    parser.add_argument("--addon-version", dest="addon_version")
    parser.add_argument("--package")
    parser.add_argument("--author")
    parser.add_argument("--description")
    parser.add_argument("--maven-group", dest="maven_group")
    parser.add_argument("--archive-name", dest="archive_name")
    parser.add_argument("--advanced", action="store_true")
    parser.add_argument("--no-publish", action="store_true", dest="no_publish")
    parser.add_argument("--yes", action="store_true")
    parser.add_argument("--non-interactive", action="store_true", dest="non_interactive")
    parser.add_argument("--debug-stack", action="store_true", dest="debug_stack")
    return parser


ACTIONS = {
    "menu": menu,
    "setup": configure_addon,
    "scan": scan_addon,
    "validate": validate_addon_system,
    "publish-api": publish_api,
    "build": build_addon,
    "build-all": build_all_templates,
    "clean": clean_addon,
}


def main(argv: list[str]) -> int:
    args = build_parser().parse_args(argv)
    try:
        ACTIONS[args.action](args)
        return 0
    except ToolkitError as exc:
        bad(str(exc))
        if args.debug_stack:
            raise
        return 1
    except KeyboardInterrupt:
        print()
        return 130


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
