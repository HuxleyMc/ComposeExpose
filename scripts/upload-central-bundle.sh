#!/usr/bin/env bash
set -euo pipefail

BUNDLE="${1:?bundle path is required}"
VERSION="${2:?version is required}"
PUBLISHING_TYPE="${3:-USER_MANAGED}"

: "${CENTRAL_PORTAL_USERNAME:?CENTRAL_PORTAL_USERNAME is required}"
: "${CENTRAL_PORTAL_PASSWORD:?CENTRAL_PORTAL_PASSWORD is required}"

if [[ ! -f "$BUNDLE" ]]; then
  echo "Bundle does not exist: $BUNDLE" >&2
  exit 1
fi

AUTH_TOKEN="$(printf '%s:%s' "$CENTRAL_PORTAL_USERNAME" "$CENTRAL_PORTAL_PASSWORD" | base64 | tr -d '\n')"
UPLOAD_URL="https://central.sonatype.com/api/v1/publisher/upload?name=ComposeExpose-$VERSION&publishingType=$PUBLISHING_TYPE"

curl --fail-with-body \
  --request POST \
  --header "Authorization: Bearer $AUTH_TOKEN" \
  --form "bundle=@$BUNDLE;type=application/octet-stream" \
  "$UPLOAD_URL"
