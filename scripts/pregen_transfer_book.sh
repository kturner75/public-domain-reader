#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

MAVEN_BIN="${MAVEN_BIN:-mvn}"
API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
SOURCE="${SOURCE:-gutenberg}"
GUTENBERG_ID=""

SKIP_PREGEN=false
SKIP_EXPORT=false
SYNC_ASSETS=false
SYNC_DIRECTION="${SYNC_DIRECTION:-up}"

EXPORT_DIR="${EXPORT_DIR:-${ROOT_DIR}/data/transfers}"
EXPORT_PATH=""

IMPORT_DB_URL=""
IMPORT_DB_USER="${IMPORT_DB_USER:-sa}"
IMPORT_DB_PASSWORD="${IMPORT_DB_PASSWORD:-}"
IMPORT_CONFLICT="${IMPORT_CONFLICT:-skip}"
IMPORT_APPLY=false

usage() {
  cat <<EOF
Usage:
  scripts/pregen_transfer_book.sh --gutenberg-id <id> [options]

Required:
  --gutenberg-id <id>              Project Gutenberg ID.

Options:
  --api-base-url <url>             Pre-generation API base URL (default: ${API_BASE_URL}).
  --skip-pregen                    Skip API pre-generation step.
  --skip-export                    Skip recap export step.
  --export-path <path>             Output recap JSON path.
  --export-dir <path>              Export directory when --export-path is not set (default: ${EXPORT_DIR}).

  --import-db-url <jdbc-url>       Optional target DB URL for recap import.
  --import-db-user <user>          Target DB user (default: ${IMPORT_DB_USER}).
  --import-db-password <pass>      Target DB password (default: empty).
  --import-on-conflict <mode>      skip|overwrite (default: ${IMPORT_CONFLICT}).
  --import-apply                   Apply DB writes. Default is dry-run import only.

  --sync-assets                    Run scripts/sync_spaces.sh after export/import.
  --sync-direction <up|down>       Passed to sync script (default: ${SYNC_DIRECTION}).
  --help                           Show this message.

Notes:
  - Pre-generation uses POST /api/pregen/gutenberg/{id} and blocks until complete.
  - Export writes recaps for --book-source-id <id> using CacheTransferRunner.
  - If --import-db-url is set, script runs import dry-run, and runs apply when --import-apply is present.
EOF
}

fail() {
  echo "Error: $*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Missing required command: $1"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --gutenberg-id)
      [[ $# -ge 2 ]] || fail "--gutenberg-id requires a value"
      GUTENBERG_ID="$2"
      shift 2
      ;;
    --api-base-url)
      [[ $# -ge 2 ]] || fail "--api-base-url requires a value"
      API_BASE_URL="$2"
      shift 2
      ;;
    --skip-pregen)
      SKIP_PREGEN=true
      shift
      ;;
    --skip-export)
      SKIP_EXPORT=true
      shift
      ;;
    --export-path)
      [[ $# -ge 2 ]] || fail "--export-path requires a value"
      EXPORT_PATH="$2"
      shift 2
      ;;
    --export-dir)
      [[ $# -ge 2 ]] || fail "--export-dir requires a value"
      EXPORT_DIR="$2"
      shift 2
      ;;
    --import-db-url)
      [[ $# -ge 2 ]] || fail "--import-db-url requires a value"
      IMPORT_DB_URL="$2"
      shift 2
      ;;
    --import-db-user)
      [[ $# -ge 2 ]] || fail "--import-db-user requires a value"
      IMPORT_DB_USER="$2"
      shift 2
      ;;
    --import-db-password)
      [[ $# -ge 2 ]] || fail "--import-db-password requires a value"
      IMPORT_DB_PASSWORD="$2"
      shift 2
      ;;
    --import-on-conflict)
      [[ $# -ge 2 ]] || fail "--import-on-conflict requires a value"
      IMPORT_CONFLICT="$2"
      shift 2
      ;;
    --import-apply)
      IMPORT_APPLY=true
      shift
      ;;
    --sync-assets)
      SYNC_ASSETS=true
      shift
      ;;
    --sync-direction)
      [[ $# -ge 2 ]] || fail "--sync-direction requires a value"
      SYNC_DIRECTION="$2"
      shift 2
      ;;
    --help)
      usage
      exit 0
      ;;
    *)
      fail "Unknown argument: $1"
      ;;
  esac
done

[[ -n "${GUTENBERG_ID}" ]] || fail "--gutenberg-id is required"
[[ "${SOURCE}" == "gutenberg" ]] || fail "Only SOURCE=gutenberg is supported right now"
[[ "${IMPORT_CONFLICT}" == "skip" || "${IMPORT_CONFLICT}" == "overwrite" ]] || fail "--import-on-conflict must be skip or overwrite"
[[ "${SYNC_DIRECTION}" == "up" || "${SYNC_DIRECTION}" == "down" ]] || fail "--sync-direction must be up or down"

require_command curl
require_command "${MAVEN_BIN}"

if [[ -z "${EXPORT_PATH}" ]]; then
  ts="$(date -u +%Y%m%dT%H%M%SZ)"
  EXPORT_PATH="${EXPORT_DIR}/recaps-${SOURCE}-${GUTENBERG_ID}-${ts}.json"
fi

run_cache_transfer() {
  local command="$1"
  shift
  "${MAVEN_BIN}" -q -DskipTests org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
    -Dexec.mainClass=org.example.reader.cli.CacheTransferRunner \
    -Dexec.args="${command} $*"
}

echo "Workflow start"
echo "  Gutenberg ID: ${GUTENBERG_ID}"
echo "  API base URL: ${API_BASE_URL}"
echo "  Export path : ${EXPORT_PATH}"

if [[ "${SKIP_PREGEN}" == "false" ]]; then
  echo ""
  echo "[1/4] Pre-generating illustrations, portraits, and recaps..."
  curl --fail --silent --show-error \
    -X POST "${API_BASE_URL}/api/pregen/gutenberg/${GUTENBERG_ID}" >/dev/null
  echo "Pre-generation completed."
else
  echo ""
  echo "[1/4] Skipped pre-generation (--skip-pregen)"
fi

if [[ "${SKIP_EXPORT}" == "false" ]]; then
  echo ""
  echo "[2/4] Exporting recap transfer JSON..."
  mkdir -p "$(dirname "${EXPORT_PATH}")"
  run_cache_transfer export "--feature recaps --book-source-id ${GUTENBERG_ID} --apply --output \"${EXPORT_PATH}\""
  [[ -f "${EXPORT_PATH}" ]] || fail "Expected export file not found: ${EXPORT_PATH}"
  echo "Exported recap bundle: ${EXPORT_PATH}"
else
  echo ""
  echo "[2/4] Skipped export (--skip-export)"
fi

if [[ -n "${IMPORT_DB_URL}" ]]; then
  [[ -f "${EXPORT_PATH}" ]] || fail "Import requires recap export file at ${EXPORT_PATH}"

  echo ""
  echo "[3/4] Import dry-run into target DB..."
  run_cache_transfer import "--feature recaps --input \"${EXPORT_PATH}\" --on-conflict ${IMPORT_CONFLICT} --db-url \"${IMPORT_DB_URL}\" --db-user \"${IMPORT_DB_USER}\" --db-password \"${IMPORT_DB_PASSWORD}\""

  if [[ "${IMPORT_APPLY}" == "true" ]]; then
    echo "Applying import..."
    run_cache_transfer import "--feature recaps --input \"${EXPORT_PATH}\" --on-conflict ${IMPORT_CONFLICT} --apply --db-url \"${IMPORT_DB_URL}\" --db-user \"${IMPORT_DB_USER}\" --db-password \"${IMPORT_DB_PASSWORD}\""
  else
    echo "Skipped import apply (use --import-apply to write)."
  fi
else
  echo ""
  echo "[3/4] Skipped import (set --import-db-url to enable)."
fi

if [[ "${SYNC_ASSETS}" == "true" ]]; then
  echo ""
  echo "[4/4] Syncing assets via scripts/sync_spaces.sh (${SYNC_DIRECTION})..."
  SYNC_DIRECTION="${SYNC_DIRECTION}" DATA_DIR="${ROOT_DIR}/data" "${ROOT_DIR}/scripts/sync_spaces.sh"
else
  echo ""
  echo "[4/4] Skipped asset sync (use --sync-assets to enable)."
fi

echo ""
echo "Workflow complete."
