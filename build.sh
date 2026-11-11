#!/usr/bin/env bash
set -euo pipefail

JAR="server/target/jolt.jar"

build_server() {
  echo "==> Building server"
  mvn -f server/pom.xml package -DskipTests -q
  echo "==> Done: $JAR"
}

usage() {
  echo "Usage: $0 [command] [options]"
  echo ""
  echo "Commands:"
  echo "  (none)    Build server JAR"
  echo "  build     Build server JAR"
  echo "  run       Start Jolt server"
  echo "  dev       Run from source with live-reload (open browser with ?dev)"
  echo "  index     Force full re-index, then start server"
  echo ""
  echo "Options (passed through to jolt for run/index):"
  echo "  --home <dir>       Codebase root (default: CWD)"
  echo "  --port <port>      Listen port (default: 9000)"
  echo "  --host <addr>      Bind address (default: localhost)"
  echo "  --index <dir>      Lucene index directory"
  echo "  --log-level <lvl>  TRACE|DEBUG|INFO|WARN|ERROR"
}

if [[ $# -lt 1 ]]; then
  build_server
  exit 0
fi

CMD="$1"
JAVA_OPTS="--add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED"
shift

case "$CMD" in
  build)   build_server ;;
  run)     exec java ${JAVA_OPTS} -jar "$JAR" "$@" ;;
  dev)     # Start Javalin in background, then Python HMR dev server in foreground
           mvn -f server/pom.xml compile exec:java -Dexec.args="$@" -q &
           JAVLIN_PID=$!
           trap "kill $JAVLIN_PID 2>/dev/null" EXIT
           # Wait for Javalin to be ready (poll /api/status)
           for i in $(seq 1 30); do
             if curl -s "http://localhost:9000/api/status" >/dev/null 2>&1; then break; fi
             sleep 1
           done
           exec python3 dev_server.py --javalin-port 9000 "$@" ;;
  index)   exec java ${JAVA_OPTS} -jar "$JAR" --reindex "$@" ;;
  *)       echo "Unknown command: $CMD" >&2; usage; exit 1 ;;
esac