#!/usr/bin/env python3
import sys
from pathlib import Path


ROOT_DIR = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT_DIR / ".github" / "workflows" / "release.yml"
BUILD_BUNDLE = ROOT_DIR / "scripts" / "build-central-bundle.sh"
UPLOAD_BUNDLE = ROOT_DIR / "scripts" / "upload-central-bundle.sh"

REQUIRED_WORKFLOW_SNIPPETS = {
    "manual dispatch": "workflow_dispatch:",
    "version input": "version:",
    "dry run input": "dry_run:",
    "namespace": "io.github.huxleymc",
    "root build": "./gradlew --no-daemon clean build",
    "metadata verification": "./scripts/verify-publishing-metadata.sh",
    "bundle build": "./scripts/build-central-bundle.sh",
    "central upload": "./scripts/upload-central-bundle.sh",
    "central username secret": "CENTRAL_PORTAL_USERNAME",
    "central password secret": "CENTRAL_PORTAL_PASSWORD",
    "signing key secret": "SIGNING_IN_MEMORY_KEY",
    "signing password secret": "SIGNING_IN_MEMORY_KEY_PASSWORD",
}

REQUIRED_BUILD_SNIPPETS = {
    "version argument": "VERSION=\"${1:?version is required}\"",
    "release version guard": "*-SNAPSHOT",
    "central publish task": "publishAllPublicationsToCentralBundleRepository",
    "version property": "-PcomposeExposeVersion",
    "zip bundle": "central-bundle.zip",
}

REQUIRED_UPLOAD_SNIPPETS = {
    "publisher upload endpoint": "https://central.sonatype.com/api/v1/publisher/upload",
    "bearer token": "Authorization: Bearer",
    "bundle form": "bundle=@",
    "publishing type": "publishingType=",
}


def main() -> int:
    errors = []
    errors += require_file(WORKFLOW, REQUIRED_WORKFLOW_SNIPPETS)
    errors += require_file(BUILD_BUNDLE, REQUIRED_BUILD_SNIPPETS)
    errors += require_file(UPLOAD_BUNDLE, REQUIRED_UPLOAD_SNIPPETS)
    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1
    print("Verified Maven Central release workflow and bundle scripts.")
    return 0


def require_file(path: Path, snippets: dict[str, str]) -> list[str]:
    if not path.is_file():
        return [f"Missing required file: {path.relative_to(ROOT_DIR)}"]
    text = path.read_text()
    return [
        f"{path.relative_to(ROOT_DIR)} missing {name}: expected snippet {snippet!r}"
        for name, snippet in snippets.items()
        if snippet not in text
    ]


if __name__ == "__main__":
    raise SystemExit(main())
