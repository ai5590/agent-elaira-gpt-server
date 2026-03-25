#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${PROJECT_DIR}/.env"
PID_FILE="${SCRIPT_DIR}/start.pid"
LOG_FILE="${SCRIPT_DIR}/start.log"

load_env_file() {
  if [[ -f "${ENV_FILE}" ]]; then
    while IFS= read -r line || [[ -n "${line}" ]]; do
      [[ -z "${line}" || "${line}" =~ ^[[:space:]]*# ]] && continue
      if [[ "${line}" =~ ^[[:space:]]*([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]]; then
        key="${BASH_REMATCH[1]}"
        value="${BASH_REMATCH[2]}"
        if [[ -z "${!key+x}" ]]; then
          export "${key}=${value}"
        fi
      fi
    done < "${ENV_FILE}"
  fi
}

load_env_file

if [[ -f "${PID_FILE}" ]]; then
  old_pid="$(cat "${PID_FILE}")"
  if [[ -n "${old_pid}" ]] && kill -0 "${old_pid}" >/dev/null 2>&1; then
    echo "Already running with PID ${old_pid}. Stop it first: scripts/stop.sh"
    exit 1
  fi
fi

if [[ "${APP_LOGGING_DEBUG_PACKETS:-false}" == "true" ]]; then
  export LOG_LEVEL_CLIENT="${LOG_LEVEL_CLIENT:-DEBUG}"
fi

cd "${PROJECT_DIR}"
nohup ./gradlew bootRun --console=plain >> "${LOG_FILE}" 2>&1 &
pid=$!
echo "${pid}" > "${PID_FILE}"

echo "Started in background. PID=${pid}"
echo "Log: ${LOG_FILE}"
