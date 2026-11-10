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
  index)   exec java ${JAVA_OPTS} -jar "$JAR" --reindex "$@" ;;
  *)       echo "Unknown command: $CMD" >&2; usage; exit 1 ;;
esac