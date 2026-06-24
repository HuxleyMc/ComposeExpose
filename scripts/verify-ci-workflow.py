#!/usr/bin/env python3
import sys
from pathlib import Path


ROOT_DIR = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT_DIR / ".github" / "workflows" / "ci.yml"

REQUIRED_SNIPPETS = {
    "push trigger": "push:",
    "pull request trigger": "pull_request:",
    "Java 17 setup": "java-version: '17'",
    "Gradle cache setup": "gradle/actions/setup-gradle",
    "Android SDK setup": "android-actions/setup-android",
    "root formatting": "./gradlew --no-daemon spotlessCheck",
    "demo formatting": "./gradlew --no-daemon -p demo spotlessCheck",
    "root build": "./gradlew --no-daemon clean build",
    "demo smoke": "./scripts/smoke-demo.sh",
    "agent context benchmark": "./scripts/benchmark-agent-context.py --no-build",
    "agent benchmark verifier": "./scripts/verify-agent-benchmark.py",
    "publishing metadata smoke": "./scripts/verify-publishing-metadata.sh",
    "published consumer smoke": "./scripts/smoke-published-consumer.sh",
    "workflow verifier": "./scripts/verify-ci-workflow.py",
}


def main() -> int:
    if not WORKFLOW.is_file():
        print(f"Missing CI workflow: {WORKFLOW.relative_to(ROOT_DIR)}", file=sys.stderr)
        return 1

    text = WORKFLOW.read_text()
    missing = [
        f"{name}: expected snippet {snippet!r}"
        for name, snippet in REQUIRED_SNIPPETS.items()
        if snippet not in text
    ]
    if missing:
        print("CI workflow is missing required gates:", file=sys.stderr)
        for item in missing:
            print(f"- {item}", file=sys.stderr)
        return 1

    print(f"Verified CI workflow gates in {WORKFLOW.relative_to(ROOT_DIR)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
