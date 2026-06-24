#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOCAL_REPO="$ROOT_DIR/build/local-maven"

cd "$ROOT_DIR"
rm -rf "$LOCAL_REPO"
"$ROOT_DIR/gradlew" --no-daemon publishAllPublicationsToLocalTestRepository >/tmp/compose-expose-publishing-metadata.log

python3 - "$LOCAL_REPO" <<'PY'
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

repo = Path(sys.argv[1])
root_dir = repo.parents[1]
expected = {
    "compose-expose-core",
    "compose-expose-gradle-plugin",
    "compose-expose-ksp",
    "compose-expose-mcp",
    "io.github.huxleymc.composeexpose.gradle.plugin",
}
expected_group = "io.github.huxleymc.composeexpose"

errors = []
if not (root_dir / "LICENSE").is_file():
    errors.append("Missing repository LICENSE file")
published = {}
for pom_path in repo.rglob("*.pom"):
    artifact_id = pom_path.parent.parent.name
    published[artifact_id] = pom_path

missing = sorted(expected.difference(published))
if missing:
    errors.append(f"Missing published POMs: {', '.join(missing)}")

for artifact_id in sorted(expected.intersection(published)):
    pom_path = published[artifact_id]
    artifact_dir = pom_path.parent
    root = ET.parse(pom_path).getroot()
    ns = {"m": root.tag.partition("}")[0].strip("{")} if root.tag.startswith("{") else {}

    def text(path):
        element = root.find(path, ns)
        return "" if element is None or element.text is None else element.text.strip()

    checks = {
        "groupId": text("m:groupId" if ns else "groupId"),
        "name": text("m:name" if ns else "name"),
        "description": text("m:description" if ns else "description"),
        "url": text("m:url" if ns else "url"),
        "license": text("m:licenses/m:license/m:name" if ns else "licenses/license/name"),
        "developer": text("m:developers/m:developer/m:id" if ns else "developers/developer/id"),
        "scm_connection": text("m:scm/m:connection" if ns else "scm/connection"),
        "scm_developer_connection": text("m:scm/m:developerConnection" if ns else "scm/developerConnection"),
        "scm_url": text("m:scm/m:url" if ns else "scm/url"),
    }
    if checks["groupId"] != expected_group:
        errors.append(f"{artifact_id} POM has groupId {checks['groupId']!r}, expected {expected_group!r}")
    for field, value in checks.items():
        if not value:
            errors.append(f"{artifact_id} POM missing {field}")

    if not artifact_id.endswith(".gradle.plugin"):
        required_patterns = [
            f"{artifact_id}-*.jar",
            f"{artifact_id}-*-sources.jar",
            f"{artifact_id}-*-javadoc.jar",
        ]
        for pattern in required_patterns:
            if not list(artifact_dir.glob(pattern)):
                errors.append(f"{artifact_id} missing published artifact matching {pattern}")

if errors:
    raise SystemExit("\n".join(errors))

print(f"Verified publishing metadata for {len(expected)} POMs.")
PY
