#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="${1:?version is required}"
REPO_DIR="$ROOT_DIR/build/central-portal/repository"
OUT_DIR="$ROOT_DIR/build/central-portal"
BUNDLE="$OUT_DIR/compose-expose-$VERSION-central-bundle.zip"

case "$VERSION" in
  *-SNAPSHOT)
    echo "Maven Central releases must not use a SNAPSHOT version: $VERSION" >&2
    exit 1
    ;;
esac

rm -rf "$REPO_DIR" "$BUNDLE"
mkdir -p "$OUT_DIR"

"$ROOT_DIR/gradlew" \
  --no-daemon \
  publishAllPublicationsToCentralBundleRepository \
  -PcomposeExposeVersion="$VERSION"

python3 - "$REPO_DIR" "$BUNDLE" <<'PY'
import sys
import zipfile
from pathlib import Path

repo = Path(sys.argv[1])
bundle = Path(sys.argv[2])
if not repo.is_dir():
    raise SystemExit(f"Central bundle repository does not exist: {repo}")

files = sorted(path for path in repo.rglob("*") if path.is_file())
if not files:
    raise SystemExit(f"Central bundle repository is empty: {repo}")

with zipfile.ZipFile(bundle, "w", compression=zipfile.ZIP_DEFLATED) as archive:
    for path in files:
        archive.write(path, path.relative_to(repo).as_posix())

print(f"Wrote {bundle}")
PY
