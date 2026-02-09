#!/usr/bin/env bash
#
# reforge — Headless wrapper for the Reforge IntelliJ plugin.
#
# Launches IntelliJ directly (bypassing macOS `open -na` / desktop launchers)
# with isolated config directories so it never conflicts with a running IDE
# instance, and with headless JVM flags so no window or dock icon appears.
#
# Supports macOS and Linux.
#
# Usage:
#   reforge <project-path> <config.yaml> [--dry-run]
#
# Environment variables:
#   IDEA_HOME  — Override IntelliJ installation path
#                macOS: the .app bundle, e.g. "/Applications/IntelliJ IDEA.app"
#                Linux: the installation dir, e.g. "/opt/idea-IU"
#

set -euo pipefail

OS="$(uname -s)"

# ── Helpers ──────────────────────────────────────────────────────────────────

die() { echo "reforge: error: $*" >&2; exit 1; }

# ── Find IntelliJ ───────────────────────────────────────────────────────────

find_idea_home() {
  # 1. Explicit override
  if [[ -n "${IDEA_HOME:-}" ]]; then
    echo "$IDEA_HOME"
    return
  fi

  # 2. Parse the `idea` CLI script to extract the installation path
  local idea_bin
  idea_bin="$(command -v idea 2>/dev/null || true)"
  if [[ -n "$idea_bin" ]]; then
    # Resolve symlinks
    idea_bin="$(readlink -f "$idea_bin" 2>/dev/null || readlink "$idea_bin" 2>/dev/null || echo "$idea_bin")"

    if [[ "$OS" == "Darwin" ]]; then
      # macOS Toolbox script: open -na "/path/to/.../idea" ...
      local extracted
      extracted="$(grep -o '"[^"]*idea"' "$idea_bin" 2>/dev/null | head -1 | tr -d '"' || true)"
      if [[ -n "$extracted" ]]; then
        # Points to Contents/MacOS/idea — walk up to the .app
        local app_path
        app_path="$(dirname "$(dirname "$(dirname "$extracted")")")"
        if [[ -d "$app_path" && "$app_path" == *.app ]]; then
          echo "$app_path"
          return
        fi
      fi
    else
      # Linux: `idea` is typically a symlink to or wrapper around <idea-home>/bin/idea.sh
      # Try to find the installation root from the script/symlink
      local idea_dir
      idea_dir="$(dirname "$(dirname "$idea_bin")")"
      if [[ -f "$idea_dir/bin/idea.sh" ]]; then
        echo "$idea_dir"
        return
      fi
      # If idea_bin itself is idea.sh, go up one level
      idea_dir="$(dirname "$idea_bin")"
      if [[ "$(basename "$idea_dir")" == "bin" ]]; then
        idea_dir="$(dirname "$idea_dir")"
        if [[ -d "$idea_dir/bin" ]]; then
          echo "$idea_dir"
          return
        fi
      fi
    fi
  fi

  # 3. Well-known locations
  if [[ "$OS" == "Darwin" ]]; then
    local -a candidates=(
      "$HOME/Applications/IntelliJ IDEA Ultimate.app"
      "$HOME/Applications/IntelliJ IDEA.app"
      "/Applications/IntelliJ IDEA Ultimate.app"
      "/Applications/IntelliJ IDEA.app"
      "$HOME/Applications/IntelliJ IDEA CE.app"
      "/Applications/IntelliJ IDEA CE.app"
    )
  else
    local -a candidates=()
    # Toolbox installs
    for dir in "$HOME"/.local/share/JetBrains/Toolbox/apps/intellij-idea-ultimate/ch-*; do
      [[ -d "$dir" ]] && candidates+=("$dir")
    done
    for dir in "$HOME"/.local/share/JetBrains/Toolbox/apps/intellij-idea-community/ch-*; do
      [[ -d "$dir" ]] && candidates+=("$dir")
    done
    # Snap installs
    candidates+=("/snap/intellij-idea-ultimate/current")
    candidates+=("/snap/intellij-idea-community/current")
    # Manual installs
    candidates+=("/opt/idea-IU" "/opt/idea-IC" "/opt/intellij" "/usr/local/intellij")
  fi

  for candidate in "${candidates[@]}"; do
    if [[ -d "$candidate" ]]; then
      echo "$candidate"
      return
    fi
  done

  return 1
}

# ── Resolve binary and paths from IDEA_HOME ─────────────────────────────────

resolve_idea_paths() {
  local home="$1"

  if [[ "$OS" == "Darwin" ]]; then
    IDEA_BIN="$home/Contents/MacOS/idea"
    IDEA_VMOPTIONS_BASE="$home/Contents/bin/idea.vmoptions"
  else
    # Linux: binary is bin/idea.sh (or bin/idea), vmoptions in bin/
    if [[ -x "$home/bin/idea.sh" ]]; then
      IDEA_BIN="$home/bin/idea.sh"
    elif [[ -x "$home/bin/idea" ]]; then
      IDEA_BIN="$home/bin/idea"
    else
      die "IntelliJ binary not found in $home/bin/"
    fi
    if [[ -f "$home/bin/idea64.vmoptions" ]]; then
      IDEA_VMOPTIONS_BASE="$home/bin/idea64.vmoptions"
    elif [[ -f "$home/bin/idea.vmoptions" ]]; then
      IDEA_VMOPTIONS_BASE="$home/bin/idea.vmoptions"
    else
      die "Cannot find vmoptions in $home/bin/"
    fi
  fi
}

# ── Find plugin directory ───────────────────────────────────────────────────

find_plugins_dir() {
  local -a plugin_bases=()

  if [[ "$OS" == "Darwin" ]]; then
    for dir in "$HOME/Library/Application Support/JetBrains"/IntelliJIdea*/plugins; do
      [[ -d "$dir" ]] && plugin_bases+=("$dir")
    done
  else
    # Linux: ~/.config/JetBrains/IntelliJIdea*/plugins or ~/.local/share/JetBrains/IntelliJIdea*/plugins
    for dir in "$HOME/.config/JetBrains"/IntelliJIdea*/plugins; do
      [[ -d "$dir" ]] && plugin_bases+=("$dir")
    done
    for dir in "$HOME/.local/share/JetBrains"/IntelliJIdea*/plugins; do
      [[ -d "$dir" ]] && plugin_bases+=("$dir")
    done
  fi

  # Sort descending (newest version first) and find one with reforge
  if [[ ${#plugin_bases[@]} -eq 0 ]]; then
    return 1
  fi

  local sorted
  sorted="$(printf '%s\n' "${plugin_bases[@]}" | sort -rV)"
  while IFS= read -r dir; do
    if [[ -d "$dir/reforge" ]]; then
      echo "$dir"
      return
    fi
  done <<< "$sorted"

  return 1
}

# ── Main ────────────────────────────────────────────────────────────────────

# Validate args
if [[ $# -lt 2 ]]; then
  echo "Usage: reforge <project-path> <config.yaml> [--dry-run]" >&2
  exit 1
fi

# Locate IntelliJ
IDEA_HOME="$(find_idea_home)" || die "Cannot find IntelliJ IDEA. Set IDEA_HOME or install the 'idea' CLI launcher."
resolve_idea_paths "$IDEA_HOME"
[[ -x "$IDEA_BIN" ]] || die "IntelliJ binary not found at $IDEA_BIN"
[[ -f "$IDEA_VMOPTIONS_BASE" ]] || die "Cannot find base vmoptions at $IDEA_VMOPTIONS_BASE"

# Locate plugins
PLUGINS_DIR="$(find_plugins_dir)" || die "Cannot find reforge plugin. Install it first:
  idea installPlugins ch.riesennet.reforge https://raw.githubusercontent.com/notiriel/reforge/main/updatePlugins.xml"

echo "reforge: IntelliJ = $IDEA_HOME" >&2
echo "reforge: plugins  = $PLUGINS_DIR" >&2

# ── Create isolated temp directories ────────────────────────────────────────

TMPDIR_ROOT="$(mktemp -d /tmp/reforge-XXXXXX)"
trap 'rm -rf "$TMPDIR_ROOT"' EXIT

mkdir -p "$TMPDIR_ROOT/config" "$TMPDIR_ROOT/system" "$TMPDIR_ROOT/log"

# ── Write temp idea.properties ──────────────────────────────────────────────

IDEA_PROPERTIES_FILE="$TMPDIR_ROOT/idea.properties"
cat > "$IDEA_PROPERTIES_FILE" <<EOF
idea.config.path=$TMPDIR_ROOT/config
idea.system.path=$TMPDIR_ROOT/system
idea.log.path=$TMPDIR_ROOT/log
idea.plugins.path=$PLUGINS_DIR
EOF

# ── Write temp idea.vmoptions ───────────────────────────────────────────────

IDEA_VMOPTIONS_FILE="$TMPDIR_ROOT/idea.vmoptions"
cp "$IDEA_VMOPTIONS_BASE" "$IDEA_VMOPTIONS_FILE"
{
  echo "-Djava.awt.headless=true"
  [[ "$OS" == "Darwin" ]] && echo "-Dapple.awt.UIElement=true"
} >> "$IDEA_VMOPTIONS_FILE"

# ── Launch ──────────────────────────────────────────────────────────────────

export IDEA_PROPERTIES="$IDEA_PROPERTIES_FILE"
export IDEA_VM_OPTIONS="$IDEA_VMOPTIONS_FILE"

echo "reforge: launching headless IntelliJ..." >&2

exec "$IDEA_BIN" reforge "$@"
