#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  scripts/git-commit.sh [-m "commit message"] [--no-add]

Options:
  -m, --message   Commit message (optional; if omitted, script asks interactively)
  --no-add        Do not run 'git add -A' before commit
  -h, --help      Show this help
USAGE
}

COMMIT_AUTHOR_NAME="aidar_script"
COMMIT_AUTHOR_EMAIL="aidar@example.com"

message=""
auto_add="true"

while [[ $# -gt 0 ]]; do
  case "$1" in
    -m|--message)
      if [[ $# -lt 2 ]]; then
        echo "Error: missing value for $1" >&2
        usage
        exit 1
      fi
      message="$2"
      shift 2
      ;;
    --no-add)
      auto_add="false"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Error: unknown argument '$1'" >&2
      usage
      exit 1
      ;;
  esac
done

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "Error: current directory is not a git repository" >&2
  exit 1
fi

git config user.name "$COMMIT_AUTHOR_NAME"
git config user.email "$COMMIT_AUTHOR_EMAIL"

if [[ -z "$message" ]]; then
  read -r -p "Введите комментарий к коммиту: " message
fi

if [[ -z "$message" ]]; then
  echo "Error: commit message is required" >&2
  exit 1
fi

if [[ "$auto_add" == "true" ]]; then
  git add -A
fi

if git diff --cached --quiet; then
  echo "Nothing staged to commit."
  exit 1
fi

branch="$(git branch --show-current 2>/dev/null || true)"
if [[ -n "$branch" ]]; then
  echo "Creating commit on branch: $branch"
fi

git commit -m "$message"

if [[ -z "$branch" ]]; then
  echo "Error: cannot push from detached HEAD (no current branch)." >&2
  exit 1
fi

if git rev-parse --abbrev-ref --symbolic-full-name "@{u}" >/dev/null 2>&1; then
  git push
else
  git push -u origin "$branch"
fi

echo "Commit created and pushed successfully."
