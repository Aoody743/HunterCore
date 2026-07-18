#!/usr/bin/env bash
set -euo pipefail
shopt -s nullglob

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${1:-"$ROOT_DIR/build/huntercore/bundled-plugins"}"
WORK_DIR="$ROOT_DIR/build/huntercore/bundled-work"
RUN_DIR="$WORK_DIR/run-$$"
STAGE_DIR="$RUN_DIR/output"
PLUGINS_DIR="$STAGE_DIR/plugins"
MANIFEST="$STAGE_DIR/bundled-plugins.external.yml"
# Keep checked downloads outside build/: build outputs may be deleted between
# releases, while this cache is safe to retain because every read is rehashed.
DEFAULT_CACHE_HOME="${XDG_CACHE_HOME:-${HOME:-$ROOT_DIR}/.cache}"
CACHE_DIR="${HUNTERCORE_BUNDLED_PLUGIN_CACHE_DIR:-$DEFAULT_CACHE_HOME/huntercore/bundled-plugin-artifacts}"
CURL_CONNECT_TIMEOUT_SECONDS="${HUNTERCORE_BUNDLED_PLUGIN_CONNECT_TIMEOUT_SECONDS:-60}"
CURL_LOW_SPEED_LIMIT_BYTES_PER_SECOND="${HUNTERCORE_BUNDLED_PLUGIN_LOW_SPEED_LIMIT_BYTES_PER_SECOND:-1024}"
CURL_LOW_SPEED_TIME_SECONDS="${HUNTERCORE_BUNDLED_PLUGIN_LOW_SPEED_TIME_SECONDS:-120}"
CURL_DOWNLOAD_ATTEMPTS="${HUNTERCORE_BUNDLED_PLUGIN_DOWNLOAD_ATTEMPTS:-8}"

mkdir -p "$PLUGINS_DIR" "$WORK_DIR"

sha256_file() {
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}' | tr -d '\\'
  else
    sha256sum "$1" | awk '{print $1}' | tr -d '\\'
  fi
}

sha512_file() {
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 512 "$1" | awk '{print $1}' | tr -d '\\'
  else
    sha512sum "$1" | awk '{print $1}' | tr -d '\\'
  fi
}

require_positive_integer() {
  local name="$1"
  local value="$2"

  if [[ ! "$value" =~ ^[1-9][0-9]*$ ]]; then
    echo "$name must be a positive integer, got: $value" >&2
    exit 2
  fi
}

require_positive_integer HUNTERCORE_BUNDLED_PLUGIN_CONNECT_TIMEOUT_SECONDS "$CURL_CONNECT_TIMEOUT_SECONDS"
require_positive_integer HUNTERCORE_BUNDLED_PLUGIN_LOW_SPEED_LIMIT_BYTES_PER_SECOND "$CURL_LOW_SPEED_LIMIT_BYTES_PER_SECOND"
require_positive_integer HUNTERCORE_BUNDLED_PLUGIN_LOW_SPEED_TIME_SECONDS "$CURL_LOW_SPEED_TIME_SECONDS"
require_positive_integer HUNTERCORE_BUNDLED_PLUGIN_DOWNLOAD_ATTEMPTS "$CURL_DOWNLOAD_ATTEMPTS"

digest_file() {
  local algorithm="$1"
  local file="$2"

  case "$algorithm" in
    sha256) sha256_file "$file" ;;
    sha512) sha512_file "$file" ;;
    *)
      echo "Unsupported digest algorithm: $algorithm" >&2
      return 2
      ;;
  esac
}

valid_digest() {
  local algorithm="$1"
  local digest="$2"

  case "$algorithm" in
    sha256) [[ "$digest" =~ ^[0-9a-f]{64}$ ]] ;;
    sha512) [[ "$digest" =~ ^[0-9a-f]{128}$ ]] ;;
    *) return 2 ;;
  esac
}

checksum_matches() {
  local algorithm="$1"
  local expected_digest="$2"
  local file="$3"

  [[ -f "$file" ]] || return 1
  if ! valid_digest "$algorithm" "$expected_digest"; then
    echo "Invalid $algorithm digest: $expected_digest" >&2
    return 2
  fi
  [[ "$(digest_file "$algorithm" "$file")" == "$expected_digest" ]]
}

cache_path_for() {
  local algorithm="$1"
  local digest="$2"
  printf '%s/%s/%s/%s\n' "$CACHE_DIR" "$algorithm" "${digest:0:2}" "$digest"
}

write_checked_file_atomically() {
  local algorithm="$1"
  local expected_digest="$2"
  local source="$3"
  local target="$4"
  local target_dir tmp

  if ! checksum_matches "$algorithm" "$expected_digest" "$source"; then
    echo "Refusing to install an invalid $algorithm artifact: $source" >&2
    return 1
  fi

  target_dir="$(dirname "$target")"
  mkdir -p "$target_dir"
  tmp="$(mktemp "$target_dir/.${target##*/}.tmp.XXXXXX")"
  if ! cp "$source" "$tmp"; then
    rm -f "$tmp"
    return 1
  fi
  if ! checksum_matches "$algorithm" "$expected_digest" "$tmp"; then
    rm -f "$tmp"
    echo "Refusing to install a corrupted copy of $source" >&2
    return 1
  fi
  if ! mv -f "$tmp" "$target"; then
    rm -f "$tmp"
    return 1
  fi
}

store_checked_cache() {
  local algorithm="$1"
  local expected_digest="$2"
  local source="$3"
  local cache_file cache_parent tmp

  if ! checksum_matches "$algorithm" "$expected_digest" "$source"; then
    echo "Refusing to cache an invalid $algorithm artifact: $source" >&2
    return 1
  fi

  cache_file="$(cache_path_for "$algorithm" "$expected_digest")"
  cache_parent="$(dirname "$cache_file")"
  if ! mkdir -p "$cache_parent"; then
    echo "Unable to create bundled plugin cache at $cache_parent; continuing without a cache write." >&2
    return 0
  fi

  if [[ -f "$cache_file" ]]; then
    if checksum_matches "$algorithm" "$expected_digest" "$cache_file"; then
      return 0
    fi
    echo "Discarding corrupt bundled plugin cache entry: $cache_file" >&2
    if ! rm -f "$cache_file"; then
      echo "Unable to remove corrupt cache entry $cache_file; continuing without a cache write." >&2
      return 0
    fi
  fi

  tmp="$(mktemp "$cache_parent/.${expected_digest}.tmp.XXXXXX")"
  if ! cp "$source" "$tmp"; then
    rm -f "$tmp"
    echo "Unable to copy $source into the bundled plugin cache; continuing without a cache write." >&2
    return 0
  fi
  if ! checksum_matches "$algorithm" "$expected_digest" "$tmp"; then
    rm -f "$tmp"
    echo "Refusing to cache a corrupted copy of $source" >&2
    return 1
  fi
  if ! mv -f "$tmp" "$cache_file"; then
    rm -f "$tmp"
    echo "Unable to publish bundled plugin cache entry $cache_file; continuing without a cache write." >&2
  fi
}

restore_checked_cache() {
  local algorithm="$1"
  local expected_digest="$2"
  local target="$3"
  local cache_file

  cache_file="$(cache_path_for "$algorithm" "$expected_digest")"
  [[ -f "$cache_file" ]] || return 1
  if ! checksum_matches "$algorithm" "$expected_digest" "$cache_file"; then
    echo "Discarding corrupt bundled plugin cache entry: $cache_file" >&2
    rm -f "$cache_file" || true
    return 1
  fi
  if ! write_checked_file_atomically "$algorithm" "$expected_digest" "$cache_file" "$target"; then
    return 1
  fi
  echo "Restored verified bundled artifact from cache: ${target##*/}" >&2
}

prior_output_sha256() {
  local prior_output="$1"
  local file_name="$2"
  local prior_manifest="$prior_output/bundled-plugins.external.yml"

  [[ -f "$prior_manifest" ]] || return 1
  awk -v file_name="$file_name" '
    $1 == "file:" { selected = ($2 == file_name); next }
    selected && $1 == "sha256:" { print $2; exit }
  ' "$prior_manifest"
}

adopt_prior_output() {
  local algorithm="$1"
  local expected_digest="$2"
  local target="$3"
  local prior_output prior_file declared_sha256

  # An interrupted run leaves its staged output behind. It can be reused only
  # when both its manifest SHA-256 and this script's pinned digest agree.
  for prior_output in "$OUT_DIR" "$WORK_DIR"/run-*/output; do
    [[ "$prior_output" != "$STAGE_DIR" && -d "$prior_output" ]] || continue
    prior_file="$prior_output/plugins/${target##*/}"
    [[ -f "$prior_file" ]] || continue
    declared_sha256="$(prior_output_sha256 "$prior_output" "${target##*/}" || true)"
    if ! valid_digest sha256 "$declared_sha256"; then
      continue
    fi
    if ! checksum_matches sha256 "$declared_sha256" "$prior_file" \
      || ! checksum_matches "$algorithm" "$expected_digest" "$prior_file"; then
      echo "Refusing to adopt prior bundled output with an invalid declared checksum: ${target##*/}" >&2
      continue
    fi
    if ! store_checked_cache "$algorithm" "$expected_digest" "$prior_file"; then
      return 1
    fi
    if ! write_checked_file_atomically "$algorithm" "$expected_digest" "$prior_file" "$target"; then
      return 1
    fi
    echo "Adopted verified bundled artifact from prior output: ${target##*/}" >&2
    return 0
  done
  return 1
}

restore_or_adopt_checked_artifact() {
  local algorithm="$1"
  local expected_digest="$2"
  local target="$3"

  restore_checked_cache "$algorithm" "$expected_digest" "$target" \
    || adopt_prior_output "$algorithm" "$expected_digest" "$target"
}

publish_checked_artifact() {
  local algorithm="$1"
  local expected_digest="$2"
  local source="$3"
  local target="$4"

  if ! checksum_matches "$algorithm" "$expected_digest" "$source"; then
    echo "$algorithm mismatch for ${target##*/}" >&2
    return 1
  fi
  store_checked_cache "$algorithm" "$expected_digest" "$source" \
    || echo "Unable to persist verified bundled artifact ${target##*/}; continuing without a cache write." >&2
  write_checked_file_atomically "$algorithm" "$expected_digest" "$source" "$target"
}

curl_to_file() {
  local url="$1"
  local target="$2"
  local attempts="${3:-$CURL_DOWNLOAD_ATTEMPTS}"
  local attempt status resume

  require_positive_integer curl_attempts "$attempts"

  for attempt in $(seq 1 "$attempts"); do
    resume=0
    if [[ -s "$target" ]]; then
      resume=1
    fi

    set +e
    # Keep retry control here instead of curl's --retry loop: curl can reopen
    # -o from byte zero internally, whereas the next outer attempt can resume
    # the checked temporary file with -C -.
    if [[ "$resume" == "1" ]]; then
      curl --http1.1 -fL --connect-timeout "$CURL_CONNECT_TIMEOUT_SECONDS" --speed-limit "$CURL_LOW_SPEED_LIMIT_BYTES_PER_SECOND" --speed-time "$CURL_LOW_SPEED_TIME_SECONDS" --continue-at - -o "$target" "$url"
    else
      curl --http1.1 -fL --connect-timeout "$CURL_CONNECT_TIMEOUT_SECONDS" --speed-limit "$CURL_LOW_SPEED_LIMIT_BYTES_PER_SECOND" --speed-time "$CURL_LOW_SPEED_TIME_SECONDS" -o "$target" "$url"
    fi
    status="$?"
    set -e

    if [[ "$status" == "0" ]]; then
      return 0
    fi

    # A server that does not implement byte ranges rejects -C -. Discard only
    # that incomplete temporary file and retry from byte zero on the next pass.
    if [[ "$resume" == "1" && "$status" == "33" ]]; then
      rm -f "$target"
    fi

    sleep "$attempt"
  done

  return "$status"
}

download_checked_file() {
  local algorithm="$1"
  local url="$2"
  local target="$3"
  local expected_digest="${4:-}"
  local tmp actual_digest

  if [[ -n "$expected_digest" ]]; then
    if ! valid_digest "$algorithm" "$expected_digest"; then
      echo "Invalid expected $algorithm digest for $url" >&2
      return 2
    fi
    if restore_or_adopt_checked_artifact "$algorithm" "$expected_digest" "$target"; then
      return
    fi
  fi

  tmp="$(mktemp "$WORK_DIR/download.XXXXXX")"
  if ! curl_to_file "$url" "$tmp"; then
    rm -f "$tmp"
    return 1
  fi
  if [[ -n "$expected_digest" ]]; then
    actual_digest="$(digest_file "$algorithm" "$tmp")"
    if [[ "$actual_digest" != "$expected_digest" ]]; then
      rm -f "$tmp"
      echo "$algorithm mismatch for $url: expected $expected_digest got $actual_digest" >&2
      return 1
    fi
    if ! publish_checked_artifact "$algorithm" "$expected_digest" "$tmp" "$target"; then
      rm -f "$tmp"
      return 1
    fi
    rm -f "$tmp"
    return
  fi

  mv "$tmp" "$target"
}

download_file() {
  download_checked_file sha256 "$1" "$2" "${3:-}"
}

download_file_sha512() {
  download_checked_file sha512 "$1" "$2" "$3"
}

run_with_timeout() {
  local timeout_seconds="$1"
  shift

  if command -v timeout >/dev/null 2>&1; then
    timeout "$timeout_seconds" "$@"
    return
  fi
  if command -v gtimeout >/dev/null 2>&1; then
    gtimeout "$timeout_seconds" "$@"
    return
  fi

  # macOS does not ship GNU timeout. gh performs the HTTP request in-process,
  # so terminating its PID is sufficient to release this fallback path.
  "$@" &
  local command_pid="$!"
  (
    sleep "$timeout_seconds"
    if kill -0 "$command_pid" >/dev/null 2>&1; then
      kill "$command_pid" >/dev/null 2>&1 || true
    fi
  ) &
  local watchdog_pid="$!"
  local status
  if wait "$command_pid"; then
    status=0
  else
    status="$?"
  fi
  kill "$watchdog_pid" >/dev/null 2>&1 || true
  wait "$watchdog_pid" >/dev/null 2>&1 || true
  return "$status"
}

download_github_release_asset() {
  local repo="$1"
  local tag="$2"
  local pattern="$3"
  local target="$4"
  local expected_sha="$5"

  if ! valid_digest sha256 "$expected_sha"; then
    echo "Invalid expected SHA-256 digest for $repo $tag $pattern" >&2
    return 2
  fi
  if restore_or_adopt_checked_artifact sha256 "$expected_sha" "$target"; then
    return
  fi

  if command -v gh >/dev/null 2>&1 && gh auth status --hostname github.com >/dev/null 2>&1; then
    local tmp_dir="$WORK_DIR/gh-release-${repo//\//-}-$tag"
    rm -rf "$tmp_dir"
    mkdir -p "$tmp_dir"
    local gh_status
    local gh_timeout_seconds="${GH_RELEASE_DOWNLOAD_TIMEOUT_SECONDS:-120}"
    if [[ ! "$gh_timeout_seconds" =~ ^[1-9][0-9]*$ ]]; then
      echo "GH_RELEASE_DOWNLOAD_TIMEOUT_SECONDS must be a positive integer, got: $gh_timeout_seconds" >&2
      exit 2
    fi
    set +e
    run_with_timeout "$gh_timeout_seconds" gh release download "$tag" --repo "$repo" --pattern "$pattern" --dir "$tmp_dir" --clobber
    gh_status="$?"
    set -e
    local downloaded_candidates=("$tmp_dir"/$pattern)
    local downloaded="${downloaded_candidates[0]-}"
    if [[ "$gh_status" == "0" && -f "$downloaded" ]]; then
      if ! publish_checked_artifact sha256 "$expected_sha" "$downloaded" "$target"; then
        echo "SHA-256 mismatch for $repo $tag $pattern" >&2
        return 1
      fi
      return
    fi
    echo "gh release download failed for $repo $tag $pattern; retrying with curl." >&2
  fi
  download_file "https://github.com/$repo/releases/download/$tag/$pattern" "$target" "$expected_sha"
}

manifest_header() {
  cat > "$MANIFEST" <<'YAML'
plugins:
YAML
}

manifest_entry() {
  local id="$1"
  local name="$2"
  local version="$3"
  local file_name="$4"
  local source="$5"
  local sha
  sha="$(sha256_file "$PLUGINS_DIR/$file_name")"
  cat >> "$MANIFEST" <<YAML
  - id: $id
    name: $name
    version: "$version"
    file: $file_name
    source: "$source"
    resource: META-INF/huntercore/bundled-plugins/$file_name
    sha256: $sha
YAML
}

prepare_coreprotect() {
  # CoreProtect Community Edition v24.0 adds the Minecraft 26.2 compatibility
  # fixes required by the HunterCore 2.9.16 release. Keep the exact source
  # commit pinned so the locally built artifact is reproducible and can be
  # retained in the content-addressed bundled-plugin cache below.
  local commit="b5f534fd2c735c6f094cda8ca50a66324e81b048"
  local version="24.0"
  local source_dir="$WORK_DIR/CoreProtect-$commit"
  local mvn_cmd
  local file_name="CoreProtect-$version.jar"
  local output_jar="$PLUGINS_DIR/$file_name"
  local pointer_dir="$CACHE_DIR/builds/coreprotect"
  local pointer_file="$pointer_dir/$commit.sha256"

  if [[ -f "$pointer_file" ]]; then
    local cached_digest
    cached_digest="$(tr -d '[:space:]' < "$pointer_file")"
    if valid_digest sha256 "$cached_digest" && restore_checked_cache sha256 "$cached_digest" "$output_jar"; then
      manifest_entry "coreprotect" "CoreProtect" "$version" "$file_name" "https://github.com/PlayPro/CoreProtect/commit/$commit"
      return
    fi
    rm -f "$pointer_file"
  fi

  if [[ ! -d "$source_dir/.git" ]] || [[ "$(git -C "$source_dir" rev-parse HEAD 2>/dev/null || true)" != "$commit" ]]; then
    rm -rf "$source_dir"
    git init -q "$source_dir"
    git -C "$source_dir" remote add origin https://github.com/PlayPro/CoreProtect.git
    git -C "$source_dir" fetch -q --depth 1 origin "$commit"
    git -C "$source_dir" checkout -q --detach "$commit"
  fi

  if command -v mvn >/dev/null 2>&1; then
    mvn_cmd="mvn"
  else
    local maven_version="3.9.11"
    local maven_home="$WORK_DIR/apache-maven-$maven_version"
    if [[ ! -x "$maven_home/bin/mvn" ]]; then
      local archive="$WORK_DIR/apache-maven-$maven_version-bin.tar.gz"
      download_file_sha512 \
        "https://archive.apache.org/dist/maven/maven-3/$maven_version/binaries/apache-maven-$maven_version-bin.tar.gz" \
        "$archive" \
        "bcfe4fe305c962ace56ac7b5fc7a08b87d5abd8b7e89027ab251069faebee516b0ded8961445d6d91ec1985dfe30f8153268843c89aa392733d1a3ec956c9978"
      tar -xzf "$archive" -C "$WORK_DIR"
    fi
    mvn_cmd="$maven_home/bin/mvn"
  fi

  (cd "$source_dir" && "$mvn_cmd" -q -DskipTests -Dproject.branch=development package)

  local jar_path="$source_dir/target/CoreProtect-$version.jar"
  if [[ ! -f "$jar_path" ]]; then
    local coreprotect_candidates=("$source_dir"/target/CoreProtect-*.jar)
    for candidate in "${coreprotect_candidates[@]}"; do
      if [[ "$candidate" != *sources* && "$(basename "$candidate")" != original-* ]]; then
        jar_path="$candidate"
        break
      fi
    done
  fi
  if [[ -z "$jar_path" || ! -f "$jar_path" ]]; then
    echo "CoreProtect jar was not produced by Maven." >&2
    exit 1
  fi

  cp "$jar_path" "$output_jar"
  local built_digest
  built_digest="$(sha256_file "$output_jar")"
  store_checked_cache sha256 "$built_digest" "$output_jar"
  mkdir -p "$pointer_dir"
  local pointer_tmp
  pointer_tmp="$(mktemp "$pointer_dir/.${commit}.tmp.XXXXXX")"
  printf '%s\n' "$built_digest" > "$pointer_tmp"
  mv -f "$pointer_tmp" "$pointer_file"
  manifest_entry "coreprotect" "CoreProtect" "$version" "$file_name" "https://github.com/PlayPro/CoreProtect/commit/$commit"
}

manifest_header

download_github_release_asset \
  "ViaVersion/ViaVersion" \
  "5.11.0" \
  "ViaVersion-5.11.0.jar" \
  "$PLUGINS_DIR/ViaVersion-5.11.0.jar" \
  "18d19e90fc9467d68128c076630ae8700449c901402a3ef421837ce006bc8cae"
manifest_entry "viaversion" "ViaVersion" "5.11.0" "ViaVersion-5.11.0.jar" "https://github.com/ViaVersion/ViaVersion/releases/tag/5.11.0"

download_github_release_asset \
  "ViaVersion/ViaBackwards" \
  "5.11.0" \
  "ViaBackwards-5.11.0.jar" \
  "$PLUGINS_DIR/ViaBackwards-5.11.0.jar" \
  "b21983d561e3f92df257683f0133ab6c68ec68175e8acfd82c6231723bf83587"
manifest_entry "viabackwards" "ViaBackwards" "5.11.0" "ViaBackwards-5.11.0.jar" "https://github.com/ViaVersion/ViaBackwards/releases/tag/5.11.0"

download_github_release_asset \
  "ViaVersion/ViaRewind" \
  "4.1.3" \
  "ViaRewind-4.1.3.jar" \
  "$PLUGINS_DIR/ViaRewind-4.1.3.jar" \
  "2d5970d22b4711c9ab2800932326c7b08acdace25ed7c6bbb8f6ea81054962b4"
manifest_entry "viarewind" "ViaRewind" "4.1.3" "ViaRewind-4.1.3.jar" "https://github.com/ViaVersion/ViaRewind/releases/tag/4.1.3"

download_file \
  "https://download.geysermc.org/v2/projects/geyser/versions/2.11.0/builds/1200/downloads/spigot" \
  "$PLUGINS_DIR/Geyser-Spigot-2.11.0-b1200.jar" \
  "392e5cf85b801397eaef336b98f83d69ad95d3ab000b3f590fd56b73a707b2e4"
manifest_entry "geyser" "Geyser-Spigot" "2.11.0-b1200" "Geyser-Spigot-2.11.0-b1200.jar" "https://download.geysermc.org/v2/projects/geyser/versions/2.11.0/builds/1200"

download_file \
  "https://download.geysermc.org/v2/projects/floodgate/versions/2.2.5/builds/138/downloads/spigot" \
  "$PLUGINS_DIR/floodgate-spigot-2.2.5-b138.jar" \
  "44bdb908e2fb4ff1b974d5313d048a625a21555a9844cfb86256a98e8e1c6bd1"
manifest_entry "floodgate" "Floodgate" "2.2.5-b138" "floodgate-spigot-2.2.5-b138.jar" "https://download.geysermc.org/v2/projects/floodgate/versions/2.2.5/builds/138"

download_file_sha512 \
  "https://cdn.modrinth.com/data/lJFOpcEj/versions/nt0GWT1y/ImageFrame-2026.1.4.0.jar" \
  "$PLUGINS_DIR/ImageFrame-2026.1.4.0.jar" \
  "2a510fa5906e26331351fb69da6b19ca08d82ffb3ea34781cde8de44eed25a18e43862e6144a3bcbc8bf2184ee3a975fe65ee10689ff07039d23076fda35f58a"
manifest_entry "imageframe" "ImageFrame" "2026.1.4.0" "ImageFrame-2026.1.4.0.jar" "https://modrinth.com/plugin/imageframe/version/2026.1.4"

download_github_release_asset \
  "BlueMap-Minecraft/BlueMap" \
  "v5.22" \
  "bluemap-5.22-paper.jar" \
  "$PLUGINS_DIR/bluemap-5.22-paper.jar" \
  "9128b0b2c6939c5c0352b878805f0b10dd4dc2bf58fc31d1af260902f9b94d05"
manifest_entry "bluemap" "BlueMap" "5.22" "bluemap-5.22-paper.jar" "https://github.com/BlueMap-Minecraft/BlueMap/releases/tag/v5.22"

download_file_sha512 \
  "https://cdn.modrinth.com/data/fALzjamp/versions/MdY6JATr/Chunky-Bukkit-1.5.3.jar" \
  "$PLUGINS_DIR/Chunky-Bukkit-1.5.3.jar" \
  "43ffecc6e6a734b752da41575bbb316526c124c3f878942437d5133c377bfbd9b78bda975520dc074d7158c15dade58a444ccd0fd8d8a25d165b6fc450140422"
manifest_entry "chunky" "Chunky" "1.5.3" "Chunky-Bukkit-1.5.3.jar" "https://modrinth.com/plugin/chunky/version/1.5.3"

download_file_sha512 \
  "https://cdn.modrinth.com/data/lKEzGugV/versions/pIvQcXW8/PlaceholderAPI-2.12.3.jar" \
  "$PLUGINS_DIR/PlaceholderAPI-2.12.3.jar" \
  "f048d55b633fd816c08e2e4472bd54a75fc4d13534682e6e7745408253d2f393706efdc389d12ca2cf28d4dc035a9afdda3eed9ecde51c7e332831391d9b6479"
manifest_entry "placeholderapi" "PlaceholderAPI" "2.12.3" "PlaceholderAPI-2.12.3.jar" "https://modrinth.com/plugin/placeholderapi/version/2.12.3"

download_file \
  "https://github.com/SkinsRestorer/SkinsRestorer/releases/download/15.12.4/SkinsRestorer.jar" \
  "$PLUGINS_DIR/SkinsRestorer-15.12.4.jar" \
  "56fed7d9fa5862356851307cdb20707adb5d43f0dd6451a0225ebbd03e8d04a0"
manifest_entry "skinsrestorer" "SkinsRestorer" "15.12.4" "SkinsRestorer-15.12.4.jar" "https://github.com/SkinsRestorer/SkinsRestorer/releases/tag/15.12.4"

download_github_release_asset \
  "MilkBowl/Vault" \
  "1.7.3" \
  "Vault.jar" \
  "$PLUGINS_DIR/Vault-1.7.3.jar" \
  "a6b5ed97f43a5cf5bbaf00a7c8cd23c5afc9bd003f849875af8b36e6cf77d01d"
manifest_entry "vault" "Vault" "1.7.3" "Vault-1.7.3.jar" "https://github.com/MilkBowl/Vault/releases/tag/1.7.3"

download_file \
  "https://github.com/dmulloy2/ProtocolLib/releases/download/5.4.0/ProtocolLib.jar" \
  "$PLUGINS_DIR/ProtocolLib-5.4.0.jar" \
  "ee2e7ab9b5386f2d103081c4d108e61b1035df2ca692b53d6e2409fb1f5caccf"
manifest_entry "protocollib" "ProtocolLib" "5.4.0" "ProtocolLib-5.4.0.jar" "https://github.com/dmulloy2/ProtocolLib/releases/tag/5.4.0"

download_file_sha512 \
  "https://cdn.modrinth.com/data/1u6JkXh5/versions/yDUBafTJ/worldedit-bukkit-7.4.3.jar" \
  "$PLUGINS_DIR/worldedit-bukkit-7.4.3.jar" \
  "43d2f8865a06d63c71d2b8bc0ded66c2c7f5e413db1a64bc2f665db3bf25ec28ae40789884af4bdaf40740a6632196494d2897fa77675f0babfaf79f18400853"
manifest_entry "worldedit" "WorldEdit" "7.4.3" "worldedit-bukkit-7.4.3.jar" "https://modrinth.com/plugin/worldedit/version/7.4.3"

download_file_sha512 \
  "https://cdn.modrinth.com/data/DKY9btbd/versions/pI4UHLJL/worldguard-bukkit-7.0.17.jar" \
  "$PLUGINS_DIR/worldguard-bukkit-7.0.17.jar" \
  "5da539cf8618079f9e7f5a0ab578c62d31ebd88e35b39ca047a2f9115e397e0d4dd781a94ca373e93333fce3371f7eaf503cd0b6abb6363085e3f4968ea51e4b"
manifest_entry "worldguard" "WorldGuard" "7.0.17" "worldguard-bukkit-7.0.17.jar" "https://modrinth.com/plugin/worldguard/version/7.0.17"

download_github_release_asset \
  "Multiverse/Multiverse-Core" \
  "5.7.1" \
  "multiverse-core-5.7.1.jar" \
  "$PLUGINS_DIR/multiverse-core-5.7.1.jar" \
  "90a39133f36240b28739b7c100492371702f8ad1d7f7621b028f8d2af49fe1c3"
manifest_entry "multiverse-core" "Multiverse-Core" "5.7.1" "multiverse-core-5.7.1.jar" "https://github.com/Multiverse/Multiverse-Core/releases/tag/5.7.1"

download_file_sha512 \
  "https://cdn.modrinth.com/data/Vebnzrzj/versions/MBSY8toc/LuckPerms-Bukkit-5.5.53.jar" \
  "$PLUGINS_DIR/LuckPerms-Bukkit-5.5.53.jar" \
  "a0e087adfc1c7b9fab8fdb5a430a3331a2ca30bfc72818bb7e65ce9baff051a1490834be8cbb9cbeda292f2e92c44cf03fb829ebeb233ade9d666ed908e49ad5"
manifest_entry "luckperms" "LuckPerms" "5.5.53" "LuckPerms-Bukkit-5.5.53.jar" "https://modrinth.com/plugin/luckperms/version/5.5.53-bukkit"

prepare_coreprotect
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"
cp -R "$STAGE_DIR"/. "$OUT_DIR"/

echo "Prepared bundled plugin jars in $PLUGINS_DIR"
