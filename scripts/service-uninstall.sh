#!/usr/bin/env bash
set -euo pipefail

SERVICE_NAME="agent-elaira-gpt-server.service"
UNIT_FILE="/etc/systemd/system/${SERVICE_NAME}"

if systemctl list-unit-files | grep -q "^${SERVICE_NAME}"; then
  sudo systemctl stop "${SERVICE_NAME}" || true
  sudo systemctl disable "${SERVICE_NAME}" || true
fi

if [[ -f "${UNIT_FILE}" ]]; then
  sudo rm -f "${UNIT_FILE}"
fi

sudo systemctl daemon-reload
echo "Removed ${SERVICE_NAME}"
