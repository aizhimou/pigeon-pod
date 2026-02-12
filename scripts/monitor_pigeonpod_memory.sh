#!/usr/bin/env bash
set -euo pipefail

INTERVAL_SECONDS="${1:-1}"
DURATION_SECONDS="${2:-0}"
OUTPUT_FILE="${3:-pigeonpod-memory-$(date +%Y%m%d-%H%M%S).csv}"
PIGEONPOD_PORT="${PIGEONPOD_PORT:-8080}"
MANUAL_JAVA_PID="${PIGEONPOD_JAVA_PID:-}"

if ! [[ "$INTERVAL_SECONDS" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
  echo "Invalid interval: $INTERVAL_SECONDS"
  echo "Usage: $0 [interval_seconds=1] [duration_seconds=0] [output_file=auto]"
  exit 1
fi

if ! [[ "$DURATION_SECONDS" =~ ^[0-9]+$ ]]; then
  echo "Invalid duration: $DURATION_SECONDS"
  echo "Usage: $0 [interval_seconds=1] [duration_seconds=0] [output_file=auto]"
  exit 1
fi

export LC_ALL=C

function has_command() {
  command -v "$1" >/dev/null 2>&1
}

function csv_escape() {
  local raw="${1:-}"
  raw="${raw//$'\n'/ }"
  raw="${raw//$'\r'/ }"
  raw="${raw//\"/\"\"}"
  printf '%s' "$raw"
}

function truncate_text() {
  local raw="${1:-}"
  local max_len="${2:-220}"
  if [[ "${#raw}" -le "$max_len" ]]; then
    printf '%s' "$raw"
    return
  fi
  printf '%s…' "${raw:0:max_len}"
}

function find_pigeonpod_java_pid() {
  if [[ -n "$MANUAL_JAVA_PID" ]]; then
    echo "$MANUAL_JAVA_PID"
    return
  fi

  local pid=""
  if has_command lsof; then
    local port_pid
    port_pid="$(lsof -nP -iTCP:"$PIGEONPOD_PORT" -sTCP:LISTEN -t 2>/dev/null | head -n 1 || true)"
    if [[ -n "$port_pid" ]]; then
      local cmdline
      cmdline="$(ps -o command= -p "$port_pid" 2>/dev/null || true)"
      if echo "$cmdline" | awk 'BEGIN{IGNORECASE=1} /java/ {found=1} END{exit found?0:1}'; then
        echo "$port_pid"
        return
      fi
    fi
  fi

  if has_command jcmd; then
    pid="$(jcmd -l 2>/dev/null | awk '
      BEGIN { IGNORECASE=1 }
      /top\.asimov\.pigeon|org\.springframework\.boot\.loader|app\.jar|pigeonpod/ { print $1; exit }
    ')"
  fi
  if [[ -z "$pid" ]]; then
    pid="$(ps -axo pid=,command= | awk '
      BEGIN { IGNORECASE=1 }
      /java/ &&
      /(top\.asimov\.pigeon|org\.springframework\.boot\.loader|app\.jar|pigeonpod)/ &&
      !/(org\.jetbrains\.jps|compile-server|IntelliJ IDEA)/ {
        print $1;
        exit
      }
    ')"
  fi
  echo "$pid"
}

function find_yt_dlp_pids() {
  local java_pid="${1:-}"
  local pids=""

  if [[ -n "$java_pid" ]]; then
    pids="$(ps -axo pid=,ppid=,command= | awk -v jpid="$java_pid" '
      BEGIN { IGNORECASE=1 }
      $2 == jpid {
        cmd=$0
        if (cmd ~ /java/) next
        if (cmd ~ /(^|[[:space:]\/])(yt-dlp|yt_dlp)([[:space:]]|$)/ ||
            cmd ~ /python[0-9.]*[[:space:]]+-m[[:space:]]+yt_dlp/) {
          print $1
        }
      }
    ')"
  fi

  if [[ -z "$pids" ]]; then
    pids="$(ps -axo pid=,command= | awk '
      BEGIN { IGNORECASE=1 }
      {
        cmd=$0
        if (cmd ~ /java/) next
        if (cmd ~ /(^|[[:space:]\/])(yt-dlp|yt_dlp)([[:space:]]|$)/ ||
            cmd ~ /python[0-9.]*[[:space:]]+-m[[:space:]]+yt_dlp/) {
          print $1
        }
      }
    ')"
  fi

  echo "$pids"
}

function process_metrics_single() {
  local pid="${1:-}"
  if [[ -z "$pid" ]]; then
    echo $'\t\t\t'
    return
  fi

  ps -o pid=,rss=,%mem=,command= -p "$pid" 2>/dev/null | awk '
    {
      pid=$1; rss=$2; mem=$3;
      $1=""; $2=""; $3="";
      cmd=substr($0, 2);
      printf "%s\t%s\t%s\t%s\n", pid, rss, mem, cmd;
      found=1
    }
    END {
      if (!found) printf "\t\t\t\n"
    }
  '
}

function process_metrics_multi_sum() {
  local pids_raw="${1:-}"
  if [[ -z "$pids_raw" ]]; then
    echo $'-\t0\t0.00'
    return
  fi

  local pid_csv
  pid_csv="$(echo "$pids_raw" | tr '\n' ',' | sed 's/,$//')"
  if [[ -z "$pid_csv" ]]; then
    echo $'-\t0\t0.00'
    return
  fi

  ps -o pid=,rss=,%mem= -p "$pid_csv" 2>/dev/null | awk '
    {
      rss += $2;
      mem += $3;
      if (pids == "") pids=$1; else pids=pids "|" $1;
      found=1
    }
    END {
      if (!found) {
        printf "-\t0\t0.00\n";
      } else {
        printf "%s\t%d\t%.2f\n", pids, rss, mem;
      }
    }
  '
}

echo "timestamp,epoch,java_pid,java_rss_kb,java_mem_pct,java_cmd,yt_dlp_pids,yt_dlp_rss_kb_total,yt_dlp_mem_pct_total,note" > "$OUTPUT_FILE"

echo "Sampling started."
echo "interval=${INTERVAL_SECONDS}s duration=${DURATION_SECONDS}s output=${OUTPUT_FILE} port=${PIGEONPOD_PORT}"
echo "Press Ctrl+C to stop."

START_EPOCH="$(date +%s)"

while true; do
  NOW_EPOCH="$(date +%s)"
  NOW_TS="$(date '+%Y-%m-%d %H:%M:%S')"

  JAVA_PID="$(find_pigeonpod_java_pid)"
  IFS=$'\t' read -r JAVA_PID_VALUE JAVA_RSS_KB JAVA_MEM_PCT JAVA_CMD <<< "$(process_metrics_single "$JAVA_PID")"

  YT_DLP_PIDS_RAW="$(find_yt_dlp_pids "$JAVA_PID")"
  IFS=$'\t' read -r YT_DLP_PIDS YT_DLP_RSS_TOTAL YT_DLP_MEM_TOTAL <<< "$(process_metrics_multi_sum "$YT_DLP_PIDS_RAW")"
  if [[ "$YT_DLP_PIDS" == "-" ]]; then
    YT_DLP_PIDS=""
  fi

  JAVA_CMD="$(truncate_text "$JAVA_CMD" 220)"

  NOTE=""
  if [[ -z "$JAVA_PID_VALUE" ]]; then
    NOTE="java_not_found"
  elif [[ -z "$YT_DLP_PIDS" ]]; then
    NOTE="yt_dlp_not_found"
  fi

  printf "\"%s\",%s,%s,%s,%s,\"%s\",%s,%s,%s,\"%s\"\n" \
    "$NOW_TS" "$NOW_EPOCH" \
    "$JAVA_PID_VALUE" "${JAVA_RSS_KB:-}" "${JAVA_MEM_PCT:-}" "$(csv_escape "${JAVA_CMD:-}")" \
    "${YT_DLP_PIDS:-}" "${YT_DLP_RSS_TOTAL:-0}" "${YT_DLP_MEM_TOTAL:-0.00}" \
    "$(csv_escape "$NOTE")" >> "$OUTPUT_FILE"

  if [[ "$DURATION_SECONDS" -gt 0 ]]; then
    ELAPSED=$((NOW_EPOCH - START_EPOCH))
    if [[ "$ELAPSED" -ge "$DURATION_SECONDS" ]]; then
      break
    fi
  fi

  sleep "$INTERVAL_SECONDS"
done

echo "Sampling finished. File: $OUTPUT_FILE"
