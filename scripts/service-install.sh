#!/usr/bin/env bash
set -euo pipefail

SERVICE_NAME="agent-elaira-gpt-server.service"
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UNIT_FILE="/etc/systemd/system/${SERVICE_NAME}"
JAR_PATH="${PROJECT_DIR}/build/libs/dialogue-api-0.0.1-SNAPSHOT.jar"
ENV_FILE="${PROJECT_DIR}/.env.service"
SERVICE_HOME="${HOME}/.agent-elaira-gpt-server"
LAUNCHER_PATH="${SERVICE_HOME}/run-agent-elaira-gpt-server.sh"
SERVICE_ENV_FILE="${SERVICE_HOME}/agent-elaira-gpt-server.env"
DATA_DIR="${PROJECT_DIR}/data"

if [[ ! -f "${JAR_PATH}" ]]; then
  echo "Jar not found: ${JAR_PATH}"
  echo "Build the project first with: gradle bootJar"
  exit 1
fi

mkdir -p "${SERVICE_HOME}"
mkdir -p "${DATA_DIR}"

if [[ ! -f "${ENV_FILE}" ]]; then
  cat > "${ENV_FILE}" <<EOF
# Environment variables for agent-elaira-gpt-server.service
# Fill in real values before starting the service.
OPENAI_API_KEY=
EOF
  chmod 600 "${ENV_FILE}"
  echo "Created environment file: ${ENV_FILE}"
  echo "Set OPENAI_API_KEY in this file before using the service in production."
fi

cp "${ENV_FILE}" "${SERVICE_ENV_FILE}"
chmod 600 "${SERVICE_ENV_FILE}"

cat > "${LAUNCHER_PATH}" <<EOF
#!/usr/bin/env bash
set -euo pipefail
cd "${PROJECT_DIR}"
exec /usr/bin/java -jar "${JAR_PATH}"
EOF

chmod 700 "${LAUNCHER_PATH}"

sudo tee "${UNIT_FILE}" > /dev/null <<EOF
[Unit]
Description=Dialogue API Service
After=network.target

[Service]
Type=simple
EnvironmentFile=-${SERVICE_ENV_FILE}
ExecStart=${LAUNCHER_PATH}
Restart=always
RestartSec=5
User=$(id -un)
Environment=SPRING_PROFILES_ACTIVE=default

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable "${SERVICE_NAME}"
sudo systemctl restart "${SERVICE_NAME}"
sudo systemctl status "${SERVICE_NAME}" --no-pager
