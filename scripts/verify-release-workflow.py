#!/usr/bin/env python3
import re
import sys
from pathlib import Path


ROOT_DIR = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT_DIR / ".github" / "workflows" / "release.yml"
BUILD_BUNDLE = ROOT_DIR / "scripts" / "build-central-bundle.sh"
UPLOAD_BUNDLE = ROOT_DIR / "scripts" / "upload-central-bundle.sh"
PUBLIC_RELEASE_FILES = [ROOT_DIR / "README.md", WORKFLOW]

REQUIRED_WORKFLOW_SNIPPETS = {
    "push trigger": "push:",
    "tag trigger": "tags:",
    "version tag pattern": "- 'v*'",
    "namespace": "io.github.huxleymc",
    "root build": "./gradlew --no-daemon clean build",
    "metadata verification": "./scripts/verify-publishing-metadata.sh",
    "tag version extraction": "${GITHUB_REF_NAME#v}",
    "bundle build": "./scripts/build-central-bundle.sh",
    "central upload": "./scripts/upload-central-bundle.sh",
    "central username secret": "CENTRAL_PORTAL_USERNAME",
    "central password secret": "CENTRAL_PORTAL_PASSWORD",
    "signing key secret": "SIGNING_IN_MEMORY_KEY",
    "signing password secret": "SIGNING_IN_MEMORY_KEY_PASSWORD",
}

FORBIDDEN_WORKFLOW_SNIPPETS = {
    "manual dispatch": "workflow_dispatch:",
    "dry run input": "dry_run:",
    "workflow input version": "inputs.version",
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
    errors += forbid_file(WORKFLOW, FORBIDDEN_WORKFLOW_SNIPPETS)
    errors += require_file(BUILD_BUNDLE, REQUIRED_BUILD_SNIPPETS)
    errors += require_file(UPLOAD_BUNDLE, REQUIRED_UPLOAD_SNIPPETS)
    errors += reject_public_namespace_ids(PUBLIC_RELEASE_FILES)
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


def forbid_file(path: Path, snippets: dict[str, str]) -> list[str]:
    if not path.is_file():
        return []
    text = path.read_text()
    return [
        f"{path.relative_to(ROOT_DIR)} contains forbidden {name}: snippet {snippet!r}"
        for name, snippet in snippets.items()
        if snippet in text
    ]


def reject_public_namespace_ids(paths: list[Path]) -> list[str]:
    uuid_pattern = re.compile(
        r"\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b",
    )
    errors = []
    for path in paths:
        if not path.is_file():
            continue
        text = path.read_text()
        if "CENTRAL_NAMESPACE_ID" in text:
            errors.append(f"{path.relative_to(ROOT_DIR)} exposes CENTRAL_NAMESPACE_ID")
        if uuid_pattern.search(text):
            errors.append(f"{path.relative_to(ROOT_DIR)} exposes a UUID-like namespace id")
    return errors


if __name__ == "__main__":
    raise SystemExit(main())
