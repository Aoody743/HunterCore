#!/bin/bash
set -e

prop() {
  grep "${1}" gradle.properties | cut -d'=' -f2 | sed 's/\r//'
}

[ -z "$API_URL" ] && { echo "Error: API_URL not set"; exit 1; }
[ -z "$API_KEY" ] && { echo "Error: API_KEY not set"; exit 1; }
[ -z "$PROJECT_KEY" ] && { echo "Error: PROJECT_KEY not set"; exit 1; }

MC_VERSION=$(prop mcVersion)
[ -z "$MC_VERSION" ] && { echo "Error: mcVersion not found in gradle.properties"; exit 1; }

VERSION_BRANCH=${VERSION_BRANCH:-"ver/$MC_VERSION"}

if [ "$(prop experimental)" = "true" ]; then
  CHANNEL="BETA"
else
  CHANNEL="STABLE"
fi

LAST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "")
if [ -n "$LAST_TAG" ]; then
  COMMIT_COUNT=$(git log --oneline "$VERSION_BRANCH" ^"$LAST_TAG" 2>/dev/null | wc -l | tr -d ' ')
else
  COMMIT_COUNT=$(git log --oneline "$VERSION_BRANCH" 2>/dev/null | wc -l | tr -d ' ')
fi

if [ "$COMMIT_COUNT" -gt 0 ]; then
  COMMITS_JSON="["
  FIRST=true
  while IFS= read -r sha && IFS= read -r message && IFS= read -r time; do
    if [ "$FIRST" = true ]; then
      FIRST=false
    else
      COMMITS_JSON="$COMMITS_JSON,"
    fi

    message=$(echo "$message" | sed 's/\\/\\\\/g' | sed 's/"/\\"/g' | sed 's/\t/\\t/g' | tr -d '\n\r')
    COMMITS_JSON="$COMMITS_JSON{\"sha\":\"$sha\",\"message\":\"$message\",\"time\":\"$time\"}"
  done < <(git log --pretty='%H%n%s%n%cI' "$VERSION_BRANCH" -"$COMMIT_COUNT")
  COMMITS_JSON="$COMMITS_JSON]"
else
  COMMITS_JSON="[]"
fi

METADATA=$(cat <<EOF
{
  "channel": "$CHANNEL",
  "commits": $COMMITS_JSON
}
EOF
)

echo "Uploading: $PROJECT_KEY $MC_VERSION ($CHANNEL)"

UPLOAD_RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST "$API_URL/v2/projects/$PROJECT_KEY/versions/$MC_VERSION/builds/upload" \
  -H "x-api-key: $API_KEY" \
  -F "file=@$JAR_FILE" \
  -F "metadata=$METADATA")

HTTP_BODY=$(echo "$UPLOAD_RESPONSE" | head -n -1)
HTTP_CODE=$(echo "$UPLOAD_RESPONSE" | tail -n 1)

if [ "$HTTP_CODE" -eq 200 ] || [ "$HTTP_CODE" -eq 201 ]; then
  BUILD_ID=$(echo "$HTTP_BODY" | grep -o '"id":[0-9]*' | grep -o '[0-9]*')
  echo "✓ Build #$BUILD_ID uploaded successfully"
  exit 0
else
  echo "✗ Upload failed (HTTP $HTTP_CODE)"
  echo "$HTTP_BODY"
  exit 1
fi
