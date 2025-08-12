#!/usr/bin/env bash
set -euo pipefail

# If a Spring Boot fat JAR is present at /app/app.jar, start the Java API.
# Otherwise, run the Haskell scheduler (default behavior of this image).

if [[ -f "/app/app.jar" ]]; then
  echo "[start.sh] Detected /app/app.jar. Launching Java Spring Boot API..."
  export SCHEDULER_HS_CMD="/app/genetic-schedule"
  export SCHEDULER_HS_SCRIPT=""
  exec java -jar /app/app.jar
else
  echo "[start.sh] No /app/app.jar found. Launching Haskell scheduler binary..."
  echo "[start.sh] Tip: To run the Java API, copy or mount your Spring Boot fat JAR to /app/app.jar"
  exec /app/genetic-schedule
fi
