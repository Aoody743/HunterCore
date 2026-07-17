#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -ne 5 ]]; then
  echo "Usage: $0 <release-paperclip.jar> <server.jar> <original-bundler.jar> <generated-bundler.jar> <web-panel.zip>" >&2
  exit 64
fi

release_jar="$1"
server_jar="$2"
original_bundler_jar="$3"
generated_bundler_jar="$4"
web_panel_zip="$5"
max_release_bytes="${HUNTERCORE_RELEASE_MAX_BYTES:-220000000}"

if [[ ! "$max_release_bytes" =~ ^[1-9][0-9]*$ ]]; then
  echo "HUNTERCORE_RELEASE_MAX_BYTES must be a positive integer, got: $max_release_bytes" >&2
  exit 2
fi

for artifact in "$release_jar" "$server_jar" "$original_bundler_jar" "$generated_bundler_jar" "$web_panel_zip"; do
  if [[ ! -f "$artifact" ]]; then
    echo "Required release artifact is missing: $artifact" >&2
    exit 1
  fi
done

release_size="$(wc -c < "$release_jar" | tr -d '[:space:]')"
if (( release_size >= max_release_bytes )); then
  echo "Release jar is $release_size bytes, expected less than $max_release_bytes bytes." >&2
  exit 1
fi

sha256_stream() {
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 | awk '{print $1}'
  else
    sha256sum | awk '{print $1}'
  fi
}

zip_contains() {
  unzip -Z1 "$1" "$2" 2>/dev/null | grep -Fxq "$2"
}

zip_entry_sha256() {
  unzip -p "$1" "$2" | sha256_stream
}

verify_zip() {
  local artifact="$1"
  jar tf "$artifact" >/dev/null
  unzip -tq "$artifact" >/dev/null
}

verify_zip "$release_jar"
verify_zip "$server_jar"
verify_zip "$original_bundler_jar"
verify_zip "$generated_bundler_jar"
unzip -tq "$web_panel_zip" >/dev/null

while IFS=$'\t' read -r expected_hash _coordinate library_path; do
  [[ -z "$expected_hash" || -z "$library_path" ]] && continue
  entry="META-INF/libraries/$library_path"
  source_jar="$release_jar"
  if ! zip_contains "$source_jar" "$entry"; then
    source_jar="$generated_bundler_jar"
  fi
  if ! zip_contains "$source_jar" "$entry"; then
    echo "Library listed in META-INF/libraries.list is absent from both release and generated bundler jars: $library_path" >&2
    exit 1
  fi
  actual_hash="$(zip_entry_sha256 "$source_jar" "$entry")"
  if [[ "$actual_hash" != "$expected_hash" ]]; then
    echo "Library hash mismatch for $library_path: expected $expected_hash got $actual_hash" >&2
    exit 1
  fi
done < <(unzip -p "$release_jar" META-INF/libraries.list)

while IFS=$'\t' read -r kind original_hash patch_hash output_hash original_path patch_path output_path; do
  [[ -z "$kind" ]] && continue
  if [[ -z "$original_hash" || -z "$patch_hash" || -z "$output_hash" || -z "$original_path" || -z "$patch_path" || -z "$output_path" ]]; then
    echo "Malformed Paperclip patch entry: $kind $original_path" >&2
    exit 1
  fi
  case "$kind" in
    libraries) entry_base="META-INF/libraries" ;;
    versions) entry_base="META-INF/versions" ;;
    *)
      echo "Unknown Paperclip patch entry kind: $kind" >&2
      exit 1
      ;;
  esac
  patch_entry="$entry_base/$patch_path"
  if ! zip_contains "$release_jar" "$patch_entry"; then
    echo "Patch verification entry is missing: $patch_entry in $release_jar" >&2
    exit 1
  fi
  actual_patch_hash="$(zip_entry_sha256 "$release_jar" "$patch_entry")"
  if [[ "$actual_patch_hash" != "$patch_hash" ]]; then
    echo "Patch verification hash mismatch for $patch_path: expected $patch_hash got $actual_patch_hash" >&2
    exit 1
  fi

  original_entry="$entry_base/$original_path"
  if ! zip_contains "$original_bundler_jar" "$original_entry"; then
    echo "Patch original is missing from the original bundler: $original_entry" >&2
    exit 1
  fi
  actual_original_hash="$(zip_entry_sha256 "$original_bundler_jar" "$original_entry")"
  if [[ "$actual_original_hash" != "$original_hash" ]]; then
    echo "Patch original hash mismatch for $original_path: expected $original_hash got $actual_original_hash" >&2
    exit 1
  fi

  # Paperclip downloads original_path at runtime and applies patch_entry. The
  # resulting output_path is held by createBundlerJar, not originalBundlerJar.
  output_entry="$entry_base/$output_path"
  if ! zip_contains "$generated_bundler_jar" "$output_entry"; then
    echo "Patch output is missing from the generated bundler: $output_entry" >&2
    exit 1
  fi
  actual_output_hash="$(zip_entry_sha256 "$generated_bundler_jar" "$output_entry")"
  if [[ "$actual_output_hash" != "$output_hash" ]]; then
    echo "Patch output hash mismatch for $output_path: expected $output_hash got $actual_output_hash" >&2
    exit 1
  fi
done < <(unzip -p "$release_jar" META-INF/patches.list)

external_manifest="META-INF/huntercore/bundled-plugins.external.yml"
if ! zip_contains "$server_jar" "$external_manifest"; then
  echo "Bundled external plugin manifest is missing from the server jar." >&2
  exit 1
fi

builtin_manifest="META-INF/huntercore/bundled-plugins.yml"
huntengine_resource="META-INF/huntercore/bundled-plugins/HuntEngine.jar"
if ! zip_contains "$server_jar" "$builtin_manifest"; then
  echo "Bundled built-in plugin manifest is missing from the server jar." >&2
  exit 1
fi
if ! unzip -p "$server_jar" "$builtin_manifest" | grep -Eq '^  - id: hunt-engine$'; then
  echo "Built-in plugin manifest does not declare HuntEngine." >&2
  exit 1
fi
if unzip -p "$server_jar" "$builtin_manifest" | grep -Eqi '(^|[[:space:]])hunter-assets([[:space:]]|$)|HunterAssets\.jar'; then
  echo "Built-in plugin manifest still declares the retired HunterAssets plugin." >&2
  exit 1
fi
if ! zip_contains "$server_jar" "$huntengine_resource"; then
  echo "Bundled HuntEngine resource is missing from the server jar." >&2
  exit 1
fi
huntengine_tmp="$(mktemp)"
trap 'rm -f "$huntengine_tmp"' EXIT
unzip -p "$server_jar" "$huntengine_resource" > "$huntengine_tmp"
verify_zip "$huntengine_tmp"
if ! unzip -p "$huntengine_tmp" paper-plugin.yml | grep -Eq '^name: HuntEngine$'; then
  echo "Bundled HuntEngine jar is not branded as HuntEngine." >&2
  exit 1
fi
if unzip -p "$huntengine_tmp" paper-plugin.yml | grep -Eq '^name: (CraftEngine|HunterAssets)$'; then
  echo "Bundled HuntEngine jar has an unexpected legacy plugin name." >&2
  exit 1
fi
if ! zip_contains "$huntengine_tmp" 'proxy.jarinjar'; then
  echo "Bundled HuntEngine jar is missing its proxy diagnostic resource." >&2
  exit 1
fi
if ! zip_contains "$huntengine_tmp" 'net/momirealms/craftengine/proxy/BukkitProxy.class'; then
  echo "Bundled HuntEngine jar is missing the bootstrap BukkitProxy class." >&2
  exit 1
fi
if ! zip_contains "$huntengine_tmp" 'me/lucko/jarrelocator/JarRelocator.class'; then
  echo "Bundled HuntEngine jar is missing jar-relocator required for offline content imports." >&2
  exit 1
fi
for legal_entry in \
  META-INF/huntengine/LICENSE \
  META-INF/huntengine/NOTICE \
  META-INF/huntengine/UPSTREAM.md \
  META-INF/huntengine/CHANGES.md \
  META-INF/huntengine/THIRD_PARTY_LICENSES; do
  if ! zip_contains "$huntengine_tmp" "$legal_entry"; then
    echo "Bundled HuntEngine jar is missing required legal entry: $legal_entry" >&2
    exit 1
  fi
done
if ! unzip -p "$huntengine_tmp" META-INF/huntengine/LICENSE | grep -Fq 'GNU GENERAL PUBLIC LICENSE'; then
  echo "Bundled HuntEngine jar does not contain GPLv3 text." >&2
  exit 1
fi
if ! unzip -p "$huntengine_tmp" META-INF/huntengine/UPSTREAM.md | grep -Fq '22fe37c023ba348bac6602302dbcc08bee6d4860'; then
  echo "Bundled HuntEngine jar does not contain its pinned upstream provenance." >&2
  exit 1
fi

external_plugins=0
while IFS=$'\t' read -r resource expected_hash; do
  [[ -z "$resource" || -z "$expected_hash" ]] && continue
  external_plugins=$((external_plugins + 1))
  if ! zip_contains "$server_jar" "$resource"; then
    echo "Bundled external plugin resource is missing: $resource" >&2
    exit 1
  fi
  actual_hash="$(zip_entry_sha256 "$server_jar" "$resource")"
  if [[ "$actual_hash" != "$expected_hash" ]]; then
    echo "Bundled external plugin hash mismatch for $resource: expected $expected_hash got $actual_hash" >&2
    exit 1
  fi
done < <(
  unzip -p "$server_jar" "$external_manifest" |
    awk '/^    resource: / { resource=$2 } /^    sha256: / { if (resource != "") print resource "\t" $2 }'
)
if [[ "$external_plugins" -eq 0 ]]; then
  echo "Bundled external plugin manifest contains no verifiable SHA-256 entries." >&2
  exit 1
fi

for entry in index.html assets/app.css assets/app.js assets/panel-bg.jpg README.txt; do
  if ! zip_contains "$web_panel_zip" "$entry"; then
    echo "Web panel archive is missing required entry: $entry" >&2
    exit 1
  fi
done
if ! unzip -p "$web_panel_zip" index.html | grep -F 'data-panel-mode="frontend"' >/dev/null; then
  echo "Web panel index.html is not configured for frontend mode." >&2
  exit 1
fi

echo "Verified HunterCore release integrity:"
echo "  release jar: $release_jar"
echo "  server jar:  $server_jar"
echo "  web panel:   $web_panel_zip"
