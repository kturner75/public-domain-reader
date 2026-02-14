#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
POLL_INTERVAL_SECONDS="${POLL_INTERVAL_SECONDS:-5}"
MAX_WAIT_MINUTES_PER_BOOK="${MAX_WAIT_MINUTES_PER_BOOK:-0}"
CONTINUE_ON_ERROR=false

TOP_20_GUTENBERG_IDS=(
  1342 2701 84 345 11 1260 768 174 98 1184
  2554 1399 1661 1727 996 135 2600 28054 120 25
)

usage() {
  cat <<EOF
Usage:
  scripts/pregen_quizzes_top20.sh [options]

Options:
  --api-base-url <url>               API base URL (default: ${API_BASE_URL}).
  --poll-interval-seconds <n>        Poll interval for chapter quiz status checks (default: ${POLL_INTERVAL_SECONDS}).
  --max-wait-minutes-per-book <n>    Max wait per book. Use 0 for auto timeout by chapter count (default: ${MAX_WAIT_MINUTES_PER_BOOK}).
  --continue-on-error                Continue processing remaining books after a failure.
  --help                             Show help.

Behavior:
  1) Ensures each top-20 Gutenberg book is imported via /api/import/gutenberg/{id}.
  2) Resolves local bookId from the import response.
  3) Runs scripts/pregen_quizzes_book.sh for each book.
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
    --api-base-url)
      [[ $# -ge 2 ]] || fail "--api-base-url requires a value"
      API_BASE_URL="$2"
      shift 2
      ;;
    --poll-interval-seconds)
      [[ $# -ge 2 ]] || fail "--poll-interval-seconds requires a value"
      POLL_INTERVAL_SECONDS="$2"
      shift 2
      ;;
    --max-wait-minutes-per-book)
      [[ $# -ge 2 ]] || fail "--max-wait-minutes-per-book requires a value"
      MAX_WAIT_MINUTES_PER_BOOK="$2"
      shift 2
      ;;
    --continue-on-error)
      CONTINUE_ON_ERROR=true
      shift
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

[[ "$POLL_INTERVAL_SECONDS" =~ ^[0-9]+$ ]] || fail "--poll-interval-seconds must be an integer"
[[ "$MAX_WAIT_MINUTES_PER_BOOK" =~ ^[0-9]+$ ]] || fail "--max-wait-minutes-per-book must be an integer"
[[ "$POLL_INTERVAL_SECONDS" -gt 0 ]] || fail "--poll-interval-seconds must be > 0"

require_command curl
require_command jq

QUIZ_BOOK_SCRIPT="${SCRIPT_DIR}/pregen_quizzes_book.sh"
[[ -x "$QUIZ_BOOK_SCRIPT" ]] || fail "Missing executable script: ${QUIZ_BOOK_SCRIPT}"

echo "Top-20 quiz pre-generation start"
echo "  API: ${API_BASE_URL}"
echo "  Books: ${#TOP_20_GUTENBERG_IDS[@]}"

success_count=0
failure_count=0

for gutenberg_id in "${TOP_20_GUTENBERG_IDS[@]}"; do
  echo ""
  echo "=================================================="
  echo "Processing Gutenberg ID: ${gutenberg_id}"
  echo "=================================================="

  http_code="$(
    curl --silent --show-error \
      --output /tmp/pregen_quizzes_top20_import.$$ \
      --write-out '%{http_code}' \
      -X POST "${API_BASE_URL}/api/import/gutenberg/${gutenberg_id}"
  )"

  body="$(cat /tmp/pregen_quizzes_top20_import.$$ 2>/dev/null || true)"
  rm -f /tmp/pregen_quizzes_top20_import.$$

  case "$http_code" in
    200|409)
      ;;
    *)
      echo "Import failed for Gutenberg ID ${gutenberg_id}: HTTP ${http_code} ${body}" >&2
      failure_count=$((failure_count + 1))
      if [[ "$CONTINUE_ON_ERROR" == "true" ]]; then
        continue
      fi
      exit 2
      ;;
  esac

  book_id="$(printf '%s' "$body" | jq -r '.bookId // empty')"
  message="$(printf '%s' "$body" | jq -r '.message // "no-message"')"
  if [[ -z "$book_id" ]]; then
    echo "No bookId returned for Gutenberg ID ${gutenberg_id}. Body: ${body}" >&2
    failure_count=$((failure_count + 1))
    if [[ "$CONTINUE_ON_ERROR" == "true" ]]; then
      continue
    fi
    exit 2
  fi

  echo "Import status: HTTP ${http_code} (${message}), bookId=${book_id}"

  if "$QUIZ_BOOK_SCRIPT" \
      --api-base-url "$API_BASE_URL" \
      --book-id "$book_id" \
      --poll-interval-seconds "$POLL_INTERVAL_SECONDS" \
      --max-wait-minutes "$MAX_WAIT_MINUTES_PER_BOOK"; then
    success_count=$((success_count + 1))
  else
    echo "Quiz pre-generation failed for Gutenberg ID ${gutenberg_id} (bookId=${book_id})" >&2
    failure_count=$((failure_count + 1))
    if [[ "$CONTINUE_ON_ERROR" == "true" ]]; then
      continue
    fi
    exit 2
  fi
done

echo ""
echo "Top-20 quiz pre-generation summary"
echo "  Success: ${success_count}"
echo "  Failed: ${failure_count}"

if [[ "$failure_count" -gt 0 ]]; then
  exit 2
fi

exit 0
