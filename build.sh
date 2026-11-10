#!/usr/bin/env bash
set -euo pipefail

JAR="server/target/jolt.jar"

usage() {
  echo "Usage: $0 {run|index} [options]"
  echo ""
  echo "Commands:"
  echo "  run     Start Jolt server (indexes at startup)"
  echo "  index   Force full re-index, then start server"
  echo ""
  echo "Options (passed through to jolt):"
  echo "  --home <dir>       Codebase root (default: CWD)"
  echo "  --port <port>      Listen port (default: 9000)"
  echo "  --host <addr>      Bind address (default: localhost)"
  echo "  --index <dir>      Lucene index directory"
  echo "  --log-level <lvl>  TRACE|DEBUG|INFO|WARN|ERROR"
}

if [[ $# -lt 1 ]]; then
  usage
  exit 1
fi

CMD="$1"
shift

case "$CMD" in
  run)
    exec java -jar "$JAR" "$@"
    ;;
  index)
    exec java -jar "$JAR" --reindex "$@"
    ;;
  *)
    echo "Unknown command: $CMD" >&2
    usage
    exit 1
    ;;
esac
