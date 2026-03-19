#!/usr/bin/env bash
set -euo pipefail

sudo systemctl stop agent-elaira-gpt-server.service
sudo systemctl status agent-elaira-gpt-server.service --no-pager || true
