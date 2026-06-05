#!/bin/sh
set -e

# Use exec to ensure Java process receives signals (SIGTERM, etc.) and is PID 1
# This also avoids the "shell-form" mangling issues on certain platforms like Unraid 7
exec java $JAVA_OPTS -jar app.jar "$@"
