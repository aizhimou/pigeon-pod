#!/bin/sh
set -e

# Install sitecustomize.py into Python site-packages if present
# This enables declarative community CDN routing and silent-video format fallback for yt-dlp
SITE_DIR=$(python3 -c "import site; print(site.getsitepackages()[0])" 2>/dev/null || true)
if [ -n "$SITE_DIR" ] && [ -f "/app/scripts/sitecustomize.py" ]; then
    cp /app/scripts/sitecustomize.py "$SITE_DIR/sitecustomize.py"
fi

# Ensure default cdn-rules.json is available in /data if not customized by user
if [ -f "/app/scripts/cdn-rules.json" ] && [ ! -f "/data/cdn-rules.json" ]; then
    mkdir -p /data
    cp /app/scripts/cdn-rules.json /data/cdn-rules.json
fi

# Use exec to ensure Java process receives signals (SIGTERM, etc.) and is PID 1
# This also avoids the "shell-form" mangling issues on certain platforms like Unraid 7
exec java $JAVA_OPTS -jar app.jar "$@"
