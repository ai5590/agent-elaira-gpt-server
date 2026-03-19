#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${PROJECT_DIR}/.env.service"
SERVICE_HOME="${HOME}/.agent-elaira-gpt-server"
SERVICE_ENV_FILE="${SERVICE_HOME}/agent-elaira-gpt-server.env"

mkdir -p "${SERVICE_HOME}"

if [[ -f "${ENV_FILE}" ]]; then
  cp "${ENV_FILE}" "${SERVICE_ENV_FILE}"
  chmod 600 "${SERVICE_ENV_FILE}"
fi

sudo systemctl restart agent-elaira-gpt-server.service
sudo systemctl status agent-elaira-gpt-server.service --no-pager
