#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -gt 1 ]]; then
  echo "Usage: $0 [third-party/hunt-engine/target/HuntEngine.jar]" >&2
  exit 64
fi

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENGINE_DIR="$ROOT_DIR/third-party/hunt-engine"
ENGINE_JAR="${1:-}"

fail() {
  echo "HuntEngine vendor verification failed: $*" >&2
  exit 1
}

require_file() {
  [[ -f "$1" ]] || fail "required file is missing: ${1#$ROOT_DIR/}"
}

require_line() {
  local file="$1"
  local expected="$2"
  grep -Fqx "$expected" "$file" || fail "expected '$expected' in ${file#$ROOT_DIR/}"
}

zip_contains() {
  unzip -Z1 "$1" "$2" 2>/dev/null | grep -Fxq "$2"
}

zip_entry_count() {
  unzip -Z1 "$1" | awk -v entry="$2" '$0 == entry { count += 1 } END { print count + 0 }'
}

for required in \
  "$ENGINE_DIR/LICENSE" \
  "$ENGINE_DIR/NOTICE" \
  "$ENGINE_DIR/UPSTREAM.md" \
  "$ENGINE_DIR/CHANGES.md" \
  "$ENGINE_DIR/README.md" \
  "$ENGINE_DIR/THIRD_PARTY_LICENSES" \
  "$ENGINE_DIR/common-files/src/main/resources/THIRD_PARTY_LICENSES" \
  "$ENGINE_DIR/gradle.properties" \
  "$ENGINE_DIR/gradle/wrapper/gradle-wrapper.properties" \
  "$ENGINE_DIR/build.gradle.kts" \
  "$ENGINE_DIR/bukkit/paper-loader/build.gradle.kts" \
  "$ENGINE_DIR/bukkit/loader/build.gradle.kts"; do
  require_file "$required"
done

[[ ! -e "$ENGINE_DIR/.git" ]] || fail "vendored source must not contain a nested .git directory"
[[ ! -e "$ENGINE_DIR/.gitmodules" ]] || fail "vendored source must not declare upstream submodules"
[[ ! -e "$ENGINE_DIR/wiki" ]] || fail "upstream wiki must not be vendored"
[[ ! -e "$ENGINE_DIR/client-mod" ]] || fail "upstream client-mod must not be vendored"
[[ ! -e "$ENGINE_DIR/proxy" ]] || fail "external upstream proxy submodule must not be vendored"

require_line "$ENGINE_DIR/gradle.properties" "project_version=2.9.16"
require_line "$ENGINE_DIR/gradle.properties" "huntengine_upstream_version=26.7.3"
require_line "$ENGINE_DIR/gradle.properties" "huntengine_upstream_commit=22fe37c023ba348bac6602302dbcc08bee6d4860"
require_line "$ENGINE_DIR/gradle.properties" "paper_version=26.2.build.10-alpha"
require_line "$ENGINE_DIR/gradle/wrapper/gradle-wrapper.properties" "distributionSha256Sum=9c0f7faeeb306cb14e4279a3e084ca6b596894089a0638e68a07c945a32c9e14"
if grep -Eq '^paper_version=.*\+' "$ENGINE_DIR/gradle.properties"; then
  fail "paper_version must be a fixed coordinate, not a dynamic + selector"
fi

grep -Fq 'Fixed source commit: `22fe37c023ba348bac6602302dbcc08bee6d4860`' "$ENGINE_DIR/UPSTREAM.md" \
  || fail "UPSTREAM.md does not record the pinned source commit"
grep -Fq 'Pinned Paper API coordinate: `io.papermc.paper:paper-api:26.2.build.10-alpha`' "$ENGINE_DIR/UPSTREAM.md" \
  || fail "UPSTREAM.md does not record the fixed Paper API coordinate"
grep -Fq 'GNU GENERAL PUBLIC LICENSE' "$ENGINE_DIR/LICENSE" \
  || fail "LICENSE is not the GPLv3 text"
grep -Fq 'HuntEngine is a modified HunterCraft distribution' "$ENGINE_DIR/NOTICE" \
  || fail "NOTICE does not identify the modified distribution"
grep -Fq 'META-INF/huntengine/' "$ENGINE_DIR/NOTICE" \
  || fail "NOTICE does not describe binary legal notices"
grep -Fq 'include("net/momirealms/craftengine/proxy/**")' "$ENGINE_DIR/bukkit/paper-loader/build.gradle.kts" \
  || fail "Paper loader does not restrict expanded proxy contents"
grep -Fq 'include("net/momirealms/craftengine/proxy/**")' "$ENGINE_DIR/bukkit/loader/build.gradle.kts" \
  || fail "Legacy loader does not restrict expanded proxy contents"
grep -Fq 'me.lucko:jar-relocator:${v("jar_relocator_version")}' "$ENGINE_DIR/buildSrc/src/main/kotlin/net/momirealms/Libs.kt" \
  || fail "offline runtime does not include jar-relocator for content imports"
if grep -Fq 'dependsOn(tasks.clean)' "$ENGINE_DIR/build.gradle.kts"; then
  fail "Java compilation must not invoke clean"
fi

if [[ -z "$ENGINE_JAR" ]]; then
  echo "Verified HuntEngine vendored source (artifact verification skipped)."
  exit 0
fi

case "$ENGINE_JAR" in
  /*) ;;
  *) ENGINE_JAR="$ROOT_DIR/$ENGINE_JAR" ;;
esac
require_file "$ENGINE_JAR"
command -v unzip >/dev/null 2>&1 || fail "unzip is required to inspect HuntEngine.jar"
unzip -tq "$ENGINE_JAR" >/dev/null || fail "HuntEngine.jar is not a valid zip archive"

for entry in \
  paper-plugin.yml \
  proxy.jarinjar \
  net/momirealms/craftengine/proxy/BukkitProxy.class \
  me/lucko/jarrelocator/JarRelocator.class \
  META-INF/huntengine/LICENSE \
  META-INF/huntengine/NOTICE \
  META-INF/huntengine/UPSTREAM.md \
  META-INF/huntengine/CHANGES.md \
  META-INF/huntengine/THIRD_PARTY_LICENSES; do
  zip_contains "$ENGINE_JAR" "$entry" || fail "HuntEngine.jar is missing $entry"
done

if ! unzip -p "$ENGINE_JAR" paper-plugin.yml | grep -Eq '^name: HuntEngine$'; then
  fail "HuntEngine.jar paper-plugin.yml is not branded as HuntEngine"
fi
if unzip -p "$ENGINE_JAR" paper-plugin.yml | grep -Eq '^name: (CraftEngine|HunterAssets)$'; then
  fail "HuntEngine.jar retains an invalid legacy plugin name"
fi
if ! unzip -p "$ENGINE_JAR" META-INF/huntengine/LICENSE | grep -Fq 'GNU GENERAL PUBLIC LICENSE'; then
  fail "HuntEngine.jar does not contain the GPLv3 text"
fi
if ! unzip -p "$ENGINE_JAR" META-INF/huntengine/UPSTREAM.md | grep -Fq '22fe37c023ba348bac6602302dbcc08bee6d4860'; then
  fail "HuntEngine.jar does not contain the pinned upstream provenance"
fi
if ! unzip -p "$ENGINE_JAR" craft-engine.properties | grep -Eq '^git-version=22fe37c0$'; then
  fail "HuntEngine.jar does not contain the pinned build metadata"
fi

for entry in proxy.jarinjar net/momirealms/craftengine/proxy/BukkitProxy.class; do
  [[ "$(zip_entry_count "$ENGINE_JAR" "$entry")" == "1" ]] \
    || fail "HuntEngine.jar contains duplicate $entry entries"
done
duplicates="$(unzip -Z1 "$ENGINE_JAR" | LC_ALL=C sort | uniq -d)"
[[ -z "$duplicates" ]] || fail "HuntEngine.jar contains duplicate archive entries: ${duplicates//$'\n'/, }"

echo "Verified HuntEngine vendored source and offline-deployable artifact: ${ENGINE_JAR#$ROOT_DIR/}"
