#!/usr/bin/env bash
set -euo pipefail

# Validation flow:
# 1. Verify the draft directory and the required special wiki pages exist.
# 2. Check that each normal page has an H1 title and that its file name matches that title.
# 3. Parse internal markdown links and ensure they point to existing draft pages.
# 4. Fail fast with a compact list of issues so publishing is blocked before wiki sync.
#
# The goal is not to lint every markdown style detail. The goal is to catch the
# mistakes that would make the published wiki structurally broken:
# missing pages, broken internal navigation, and drift between page title and file name.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Default wiki draft source directory. Can be overridden with --source.
SOURCE_DIR="$REPO_ROOT/dev-docs/wiki-drafts"

function usage() {
  cat <<'EOF'
Usage: scripts/check-wiki-drafts.sh [--source <dir>] [--help]

Validate wiki draft markdown files before publishing.

Checks:
  - required special pages exist
  - internal wiki links point to existing draft pages
  - file names and H1 page titles are consistent
EOF
}

function fail() {
  printf '[check-wiki-drafts] ERROR: %s\n' "$*" >&2
  exit 1
}

function log() {
  printf '[check-wiki-drafts] %s\n' "$*"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --source)
      [[ $# -ge 2 ]] || fail "--source requires a value"
      SOURCE_DIR="$2"
      shift 2
      ;;
    --help)
      usage
      exit 0
      ;;
    *)
      fail "Unknown option: $1"
      ;;
  esac
done

[[ -d "$SOURCE_DIR" ]] || fail "Source directory does not exist: $SOURCE_DIR"

# Required GitHub Wiki special pages and homepage.
# If any of these are missing, publishing would produce an incomplete wiki shell.
declare -a REQUIRED_FILES=("Home.md" "_Sidebar.md" "_Footer.md")
for required in "${REQUIRED_FILES[@]}"; do
  [[ -f "$SOURCE_DIR/$required" ]] || fail "Missing required wiki draft: $required"
done

function trim() {
  local value="${1:-}"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "$value"
}

function normalize_title_to_slug() {
  # GitHub Wiki page file names are slug-like.
  # This normalization mirrors the page naming style used in the draft directory.
  local title
  title="$(trim "${1:-}")"
  title="${title//\//-}"
  title="${title//:/-}"
  title="${title//  / }"
  title="${title// /-}"
  printf '%s' "$title"
}

declare -a ERRORS=()

function add_error() {
  ERRORS+=("$1")
}

while IFS= read -r file; do
  basename="$(basename "$file")"
  slug="${basename%.md}"
  if [[ "$slug" == "_Sidebar" || "$slug" == "_Footer" ]]; then
    # Special GitHub Wiki support pages do not follow the normal H1-to-file-name rule.
    continue
  fi

  # Enforce that each publishable page begins with an H1.
  h1_line="$(sed -n 's/^# //p;q' "$file")"
  if [[ -z "$h1_line" ]]; then
    add_error "$basename: missing H1 title"
    continue
  fi

  expected_slug="$(normalize_title_to_slug "$h1_line")"
  if [[ "$slug" == "Home" ]]; then
    # GitHub Wiki requires the homepage file to be named Home.md.
    # Its visible title is allowed to differ from the file name.
    continue
  fi
  if [[ "$expected_slug" != "$slug" ]]; then
    add_error "$basename: H1 title '$h1_line' does not match file name slug '$slug' (expected '$expected_slug.md')"
  fi
done < <(find "$SOURCE_DIR" -maxdepth 1 -type f -name '*.md' | sort)

while IFS= read -r match; do
  # rg -n -o returns: path:line:matched_markdown_link
  file="${match%%:*}"
  remainder="${match#*:}"
  line="${remainder%%:*}"
  raw_link="${remainder#*:}"

  link_target="${raw_link#*](}"
  link_target="${link_target%)}"

  # External URLs and same-page anchors are outside the draft-page existence check.
  if [[ "$link_target" =~ ^https?:// ]] || [[ "$link_target" =~ ^mailto: ]] || [[ "$link_target" =~ ^# ]]; then
    continue
  fi

  # Only validate the page portion of wiki links.
  # Example:
  #   Feed-Settings-Explained#common-misunderstandings
  # becomes:
  #   Feed-Settings-Explained
  page_target="${link_target%%#*}"
  if [[ -z "$page_target" ]]; then
    add_error "$(basename "$file"):$line: invalid internal wiki link target '$link_target'"
    continue
  fi

  if [[ "$page_target" == *.md ]]; then
    page_target="${page_target%.md}"
  fi

  if [[ ! -f "$SOURCE_DIR/$page_target.md" ]]; then
    add_error "$(basename "$file"):$line: internal wiki link points to missing page '$link_target'"
  fi
done < <(rg -n -o '\[[^]]+\]\([^)]+\)' "$SOURCE_DIR"/*.md)

if [[ ${#ERRORS[@]} -gt 0 ]]; then
  log "Validation failed with ${#ERRORS[@]} issue(s):"
  for error in "${ERRORS[@]}"; do
    printf '  - %s\n' "$error" >&2
  done
  exit 1
fi

# A zero exit code means the draft directory is safe to hand off to publish-wiki.sh.
log "Wiki drafts look valid."
