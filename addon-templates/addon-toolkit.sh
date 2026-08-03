#!/usr/bin/env sh
# Forwards to addon-toolkit.py. Example: ./addon-toolkit.sh setup --template minimal --output ../MyAddon
set -e

DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
TOOLKIT="$DIR/addon-toolkit.py"
if [ ! -f "$TOOLKIT" ]; then
    echo "addon-toolkit.py not found next to this launcher." >&2
    exit 1
fi

# Probe --version (not just existence) so we skip Windows' Store-alias stub that exits 49.
for PY in python3 python py; do
    if "$PY" --version >/dev/null 2>&1; then
        exec "$PY" "$TOOLKIT" "$@"
    fi
done

echo "Python 3 was not found on PATH. Install Python 3, or use the Gradle tasks (./gradlew newAddon, scanAddon, ...)." >&2
exit 1
