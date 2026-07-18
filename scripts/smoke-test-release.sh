#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <HunterCore release jar>" >&2
  exit 2
fi

jar_path="$1"
if [[ ! -f "$jar_path" ]]; then
  echo "HunterCore release jar not found: $jar_path" >&2
  exit 1
fi

jar_path="$(cd "$(dirname "$jar_path")" && pwd)/$(basename "$jar_path")"
java_bin="${JAVA_BIN:-java}"
startup_timeout="${HUNTERCORE_SMOKE_TIMEOUT_SECONDS:-300}"
shutdown_timeout="${HUNTERCORE_SMOKE_SHUTDOWN_SECONDS:-60}"
work_dir="${HUNTERCORE_SMOKE_DIR:-$(mktemp -d "${TMPDIR:-/tmp}/huntercore-smoke.XXXXXX")}" 
keep_dir="${HUNTERCORE_KEEP_SMOKE_DIR:-false}"
log_file="$work_dir/server.log"
server_pid=""

server_running() {
  [[ -n "$server_pid" ]] && kill -0 "$server_pid" 2>/dev/null || return 1
  # A terminated child remains a zombie until the parent calls wait; kill -0
  # still succeeds for it, so inspect the process state before sleeping again.
  ! ps -o stat= -p "$server_pid" 2>/dev/null | tr -d '[:space:]' | grep -q '^Z'
}

stop_server() {
  local grace_seconds="${1:-10}"
  local second

  [[ -n "$server_pid" ]] || return 0
  if ! server_running; then
    wait "$server_pid" 2>/dev/null || true
    server_pid=""
    return 0
  fi

  kill -TERM "$server_pid" 2>/dev/null || true
  for ((second = 0; second < grace_seconds; second++)); do
    if ! server_running; then
      wait "$server_pid" 2>/dev/null || true
      server_pid=""
      return 0
    fi
    sleep 1
  done

  # Do not let a third-party plugin's non-daemon thread leave CI stuck in the
  # EXIT trap. The earlier log assertion still reports the useful failure.
  kill -KILL "$server_pid" 2>/dev/null || true
  wait "$server_pid" 2>/dev/null || true
  server_pid=""
}

cleanup() {
  stop_server 10
  if [[ "$keep_dir" != "true" ]]; then
    rm -rf "$work_dir"
  fi
}
trap cleanup EXIT INT TERM

mkdir -p "$work_dir"
cp "$jar_path" "$work_dir/server.jar"
printf 'eula=true\n' > "$work_dir/eula.txt"
cat > "$work_dir/server.properties" <<'PROPERTIES'
online-mode=false
server-port=0
enable-query=false
enable-rcon=false
enable-status=false
view-distance=2
simulation-distance=2
spawn-protection=0
max-tick-time=-1
level-name=smoke-world
motd=HunterCore cold-start smoke test
PROPERTIES

(
  cd "$work_dir"
  exec "$java_bin" \
    -Xms1G \
    -Xmx3G \
    -Dfile.encoding=UTF-8 \
    -Duser.language=en \
    -Duser.country=US \
    -jar server.jar \
    nogui
) > "$log_file" 2>&1 &
server_pid=$!

ready=false
for ((second = 0; second < startup_timeout; second++)); do
  if grep -Fq 'Done (' "$log_file" 2>/dev/null; then
    ready=true
    break
  fi
  if ! server_running; then
    echo "HunterCore exited before completing cold startup." >&2
    tail -n 240 "$log_file" >&2 || true
    exit 1
  fi
  sleep 1
done

if [[ "$ready" != "true" ]]; then
  echo "HunterCore did not complete cold startup within ${startup_timeout}s." >&2
  tail -n 240 "$log_file" >&2 || true
  exit 1
fi

if grep -Fq 'Unexpected registry minecraft:attribute size' "$log_file"; then
  echo "HunterCore hit the stale attribute registry size guard." >&2
  tail -n 240 "$log_file" >&2 || true
  exit 1
fi

# A completed server startup is insufficient when a bundled plugin was
# disabled while enabling. In particular this catches linkage failures caused
# by a plugin compiled against an older CraftBukkit revision.
if grep -Eq 'Error occurred while enabling|ExceptionInInitializerError|Unexpected registry minecraft:attribute size|Could not load .* plugin' "$log_file"; then
  echo "A bundled plugin failed during HunterCore cold startup." >&2
  tail -n 240 "$log_file" >&2 || true
  exit 1
fi

kill -TERM "$server_pid" 2>/dev/null || true
for ((second = 0; second < shutdown_timeout; second++)); do
  if ! server_running; then
    break
  fi
  sleep 1
done

if server_running; then
  echo "HunterCore did not stop within ${shutdown_timeout}s after the smoke test." >&2
  tail -n 240 "$log_file" >&2 || true
  stop_server 1
  exit 1
fi

wait "$server_pid" 2>/dev/null || true
server_pid=""
echo "HunterCore cold-start smoke test passed: $jar_path"
