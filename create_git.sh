#!/usr/bin/env bash

set -euo pipefail

GITHUB_USER="ai5590"
TOKEN_VAR_NAME="GIT_AI5590_CLASSIC_API_KEY"

print_line() {
  echo "------------------------------------------------------------"
}

abort() {
  echo
  echo "Ошибка: $1" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || abort "Не найдена команда '$1'. Установи её и запусти скрипт снова."
}

get_token() {
  if [[ -z "${GIT_AI5590_CLASSIC_API_KEY:-}" ]]; then
    abort "Не задана переменная окружения ${TOKEN_VAR_NAME}.
Перед запуском выполни:
export ${TOKEN_VAR_NAME}=\"ТВОЙ_GITHUB_TOKEN\""
  fi
}

show_intro() {
  print_line
  echo "Этот скрипт создаст новый репозиторий в GitHub в аккаунте '${GITHUB_USER}',"
  echo "затем инициализирует git в текущей папке (если нужно),"
  echo "добавит файлы, кроме самого этого скрипта, создаст первый commit и отправит проект в GitHub."
  echo
  echo "Скрипт работает с содержимым ТЕКУЩЕЙ папки:"
  echo "  $(pwd)"
  echo
  echo "Для авторизации используется переменная окружения:"
  echo "  ${TOKEN_VAR_NAME}"
  print_line
  echo
}

ask_repo_name() {
  local repo_name
  read -r -p "Введите имя нового репозитория в GitHub: " repo_name
  repo_name="$(echo "$repo_name" | xargs)"

  [[ -n "$repo_name" ]] || abort "Имя репозитория не может быть пустым."

  if [[ ! "$repo_name" =~ ^[A-Za-z0-9._-]+$ ]]; then
    abort "Имя репозитория содержит недопустимые символы.
Разрешены: буквы, цифры, точка, дефис, подчёркивание."
  fi

  REPO_NAME="$repo_name"
}

ask_visibility() {
  local answer
  echo
  read -r -p "Сделать репозиторий публичным? [y/N]: " answer
  answer="${answer:-N}"

  case "$answer" in
    y|Y|yes|YES|да|Да|ДА)
      REPO_PRIVATE="false"
      REPO_VISIBILITY_TEXT="public"
      ;;
    *)
      REPO_PRIVATE="true"
      REPO_VISIBILITY_TEXT="private"
      ;;
  esac
}

ask_confirmation() {
  echo
  print_line
  echo "Будет выполнено:"
  echo "1. Создание GitHub-репозитория '${GITHUB_USER}/${REPO_NAME}' (${REPO_VISIBILITY_TEXT})"
  echo "2. Подготовка git в текущей папке"
  echo "3. Commit файлов из текущей папки, кроме самого этого скрипта"
  echo "4. Push в ветку main"
  print_line
  echo
  read -r -p "Продолжить? [y/N]: " confirm
  confirm="${confirm:-N}"

  case "$confirm" in
    y|Y|yes|YES|да|Да|ДА) ;;
    *) echo "Отменено пользователем."; exit 0 ;;
  esac
}

check_not_inside_wrong_git_repo() {
  if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    local top
    top="$(git rev-parse --show-toplevel)"
    if [[ "$top" != "$(pwd)" ]]; then
      abort "Ты запустил скрипт внутри уже существующего git-репозитория, но не в его корне.
Корень репозитория:
  $top

Либо перейди в корень этого репозитория, либо запусти скрипт в папке, которая не вложена в другой git-репозиторий."
    fi
  fi
}

create_github_repo() {
  echo
  echo "Создаю репозиторий в GitHub..."

  local http_code
  local response_body_file

  response_body_file="$(mktemp)"

  http_code="$(
    curl -sS \
      -o "$response_body_file" \
      -w "%{http_code}" \
      -X POST "https://api.github.com/user/repos" \
      -H "Accept: application/vnd.github+json" \
      -H "Authorization: Bearer ${GIT_AI5590_CLASSIC_API_KEY}" \
      -H "X-GitHub-Api-Version: 2022-11-28" \
      -d "$(cat <<JSON
{
  "name": "${REPO_NAME}",
  "private": ${REPO_PRIVATE},
  "auto_init": false
}
JSON
)"
  )"

  if [[ "$http_code" != "201" ]]; then
    echo
    echo "GitHub API вернул ошибку. HTTP code: $http_code"
    echo "Ответ сервера:"
    cat "$response_body_file"
    rm -f "$response_body_file"
    abort "Не удалось создать репозиторий '${GITHUB_USER}/${REPO_NAME}'."
  fi

  rm -f "$response_body_file"
  echo "Репозиторий успешно создан: https://github.com/${GITHUB_USER}/${REPO_NAME}"
}

get_script_paths() {
  SCRIPT_PATH="$(realpath "${BASH_SOURCE[0]}")"
  PROJECT_PATH="$(pwd -P)"

  SCRIPT_INSIDE_PROJECT="false"
  SCRIPT_RELATIVE_PATH=""

  case "$SCRIPT_PATH" in
    "$PROJECT_PATH"/*)
      SCRIPT_INSIDE_PROJECT="true"
      SCRIPT_RELATIVE_PATH="${SCRIPT_PATH#$PROJECT_PATH/}"
      ;;
    *)
      SCRIPT_INSIDE_PROJECT="false"
      ;;
  esac
}

prepare_git_repo() {
  echo
  echo "Подготавливаю git в текущей папке..."

  if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    echo "Git уже инициализирован."
  else
    git init
    echo "Git инициализирован."
  fi

  get_script_paths

  if [[ "$SCRIPT_INSIDE_PROJECT" == "true" ]]; then
    echo "Скрипт находится внутри проекта и будет исключён из commit:"
    echo "  $SCRIPT_RELATIVE_PATH"
    git add . ":!$SCRIPT_RELATIVE_PATH"
  else
    git add .
  fi

  if git diff --cached --quiet; then
    echo "В staged нет изменений. Возможно, файлы уже были закоммичены ранее."
  else
    git commit -m "Initial commit"
    echo "Создан commit: Initial commit"
  fi

  git branch -M main

  local remote_url="https://${GITHUB_USER}:${GIT_AI5590_CLASSIC_API_KEY}@github.com/${GITHUB_USER}/${REPO_NAME}.git"

  if git remote get-url origin >/dev/null 2>&1; then
    echo "Remote 'origin' уже существует. Обновляю URL..."
    git remote set-url origin "$remote_url"
  else
    git remote add origin "$remote_url"
  fi
}

push_to_github() {
  echo
  echo "Отправляю проект в GitHub..."

  git push -u origin main

  echo
  echo "Готово."
  echo "Репозиторий: https://github.com/${GITHUB_USER}/${REPO_NAME}"
}

cleanup_remote_url() {
  echo
  echo "Убираю токен из remote URL, чтобы он не светился в git config..."

  local safe_url="https://github.com/${GITHUB_USER}/${REPO_NAME}.git"
  git remote set-url origin "$safe_url"

  echo "Теперь origin = ${safe_url}"
}

main() {
  require_command git
  require_command curl
  require_command realpath
  get_token
  check_not_inside_wrong_git_repo
  show_intro
  ask_repo_name
  ask_visibility
  ask_confirmation
  create_github_repo
  prepare_git_repo
  push_to_github
#  cleanup_remote_url
}

main "$@"
