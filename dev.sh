#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
#  dev.sh  ·  S.S. Classify  ·  Developer TUI
#  Arrow-key + mouse-click menu for Android / iOS builds, tests and installs.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

# ── Colours ───────────────────────────────────────────────────────────────────
RESET="\033[0m"; BOLD="\033[1m"; DIM="\033[2m"
RED="\033[31m";  GREEN="\033[32m"; CYAN="\033[36m"
ARROW="▶"; TICK="✔"; CROSS="✘"

# ── Terminal ──────────────────────────────────────────────────────────────────
_clear()         { printf "\033[2J\033[H"; }
_hide_cursor()   { tput civis 2>/dev/null || true; }
_show_cursor()   { tput cnorm 2>/dev/null || true; }
_enable_mouse()  { printf '\033[?1000h'; }   # X10 basic mouse reporting
_disable_mouse() { printf '\033[?1000l'; }

_cleanup() { _disable_mouse; _show_cursor; printf "\n"; }
trap '_cleanup' EXIT INT TERM

# ── Global key state ──────────────────────────────────────────────────────────
KEY=""          # symbolic key name after _read_key
MOUSE_ROW=0     # 1-based terminal row when KEY=CLICK
MOUSE_COL=0     # 1-based terminal col when KEY=CLICK

# Read one keypress into KEY (and MOUSE_ROW/COL for clicks).
# Called as a plain function — NOT via $() to keep stdin = terminal.
_read_key() {
  KEY=""; MOUSE_ROW=0; MOUSE_COL=0

  local ch
  IFS= read -rsn1 ch   # block until one byte arrives

  # Non-escape: return it as-is
  if [[ "$ch" != $'\x1b' ]]; then
    KEY="$ch"
    return
  fi

  # Escape: peek at next byte (150 ms timeout)
  local s1
  IFS= read -rsn1 -t 0.15 s1 2>/dev/null || { KEY="ESC"; return; }

  if [[ "$s1" == '[' ]]; then
    local s2
    IFS= read -rsn1 -t 0.15 s2 2>/dev/null || { KEY="ESC"; return; }
    case "$s2" in
      A) KEY="UP"    ;;
      B) KEY="DOWN"  ;;
      C) KEY="RIGHT" ;;
      D) KEY="LEFT"  ;;
      H) KEY="HOME"  ;;
      F) KEY="END"   ;;
      M) # X10 mouse event: 3 more bytes (button, col, row) each offset +32
        local mb mc mr
        IFS= read -rsn1 -t 0.15 mb 2>/dev/null || { KEY="ESC"; return; }
        IFS= read -rsn1 -t 0.15 mc 2>/dev/null || { KEY="ESC"; return; }
        IFS= read -rsn1 -t 0.15 mr 2>/dev/null || { KEY="ESC"; return; }
        local btn=$(( $(printf '%d' "'$mb") - 32 ))
        MOUSE_COL=$(( $(printf '%d' "'$mc") - 32 ))
        MOUSE_ROW=$(( $(printf '%d' "'$mr") - 32 ))
        [[ $btn -eq 0 ]] && KEY="CLICK" || KEY="MOUSE"
        ;;
      *) KEY="ESC" ;;
    esac
  elif [[ "$s1" == 'O' ]]; then
    local s2
    IFS= read -rsn1 -t 0.15 s2 2>/dev/null || { KEY="ESC"; return; }
    case "$s2" in
      H) KEY="HOME" ;;
      F) KEY="END"  ;;
      *) KEY="ESC"  ;;
    esac
  else
    KEY="ESC"
  fi
}

# ── Header / footer ───────────────────────────────────────────────────────────
_header() {
  local subtitle="${1:-}"
  _clear
  printf "${BOLD}${CYAN}"
  printf "╔══════════════════════════════════════════════╗\n"
  printf "║   📱  S.S. Classify  ─  DevTools  🛠         ║\n"
  printf "╚══════════════════════════════════════════════╝${RESET}\n"
  if [[ -n "$subtitle" ]]; then
    printf "  ${DIM}%s${RESET}\n" "$subtitle"
  fi
  printf "\n"
}

_footer() { printf "\n${DIM}  [↑↓] Move   [Enter/Click] Select   [q] Quit${RESET}\n"; }

# ── Arrow-key + click menu ────────────────────────────────────────────────────
# Usage:  menu "Subtitle" "Item 0" "Item 1" …
# Result: sets MENU_RESULT to the 0-based index chosen.
MENU_RESULT=0

menu() {
  local subtitle="$1"; shift
  local items=("$@")
  local count=${#items[@]}
  local cur=0

  # How many rows does _header() print?
  # 3 box lines + optional subtitle + 1 blank line
  local hrows=4
  [[ -n "$subtitle" ]] && hrows=5
  # Menu items start at row (hrows + 1) in 1-based terminal coordinates.
  local item_start=$(( hrows + 1 ))

  _hide_cursor
  _enable_mouse

  while true; do
    _header "$subtitle"
    for i in "${!items[@]}"; do
      if (( i == cur )); then
        printf "  ${BOLD}${CYAN} ${ARROW} ${items[$i]}${RESET}\n"
      else
        printf "  ${DIM}   ${items[$i]}${RESET}\n"
      fi
    done
    _footer

    _read_key   # sets KEY, MOUSE_ROW, MOUSE_COL

    case "$KEY" in
      UP|k)   (( cur > 0 ))          && (( cur-- )) ;;
      DOWN|j) (( cur < count - 1 ))  && (( cur++ )) ;;
      HOME|g) cur=0 ;;
      END|G)  cur=$(( count - 1 )) ;;

      CLICK)
        # Map click row → item index (0-based)
        local idx=$(( MOUSE_ROW - item_start ))
        if (( idx >= 0 && idx < count )); then
          MENU_RESULT=$idx
          _disable_mouse
          return
        fi
        ;;

      ''|$'\n')   # Enter key
        MENU_RESULT=$cur
        _disable_mouse
        return
        ;;

      q|Q) _cleanup; exit 0 ;;
    esac
  done
}

# ── Command runner ────────────────────────────────────────────────────────────
run_cmd() {
  local label="$1"; shift
  _clear
  _show_cursor
  printf "${BOLD}${CYAN}┌─ %s${RESET}\n" "$label"
  printf "${DIM}  \$ %s${RESET}\n\n" "$*"

  local t=$SECONDS
  set +e; "$@"; local rc=$?; set -e
  local elapsed=$(( SECONDS - t ))

  printf "\n"
  if (( rc == 0 )); then
    printf "${BOLD}${GREEN}  ${TICK} Done in ${elapsed}s${RESET}\n"
  else
    printf "${BOLD}${RED}  ${CROSS} Failed (exit ${rc}) after ${elapsed}s${RESET}\n"
  fi

  printf "\n${DIM}  Press any key to return to the menu…${RESET}"
  IFS= read -rsn1
  _hide_cursor
}

# ── iOS helpers ───────────────────────────────────────────────────────────────
IOS_PROJECT="iosApp/iosApp.xcodeproj"
IOS_SCHEME="ImageCategorizer"

_pick_sim() {
  xcrun simctl list devices available --json 2>/dev/null \
    | python3 -c "
import sys, json
devs = json.load(sys.stdin).get('devices', {})
for rt, ds in devs.items():
    if 'iOS' in rt:
        for d in ds:
            if d.get('isAvailable') and 'iPhone' in d.get('name',''):
                print(d['name']); raise SystemExit
" 2>/dev/null || echo "iPhone 16"
}

ios_build() {
  local cfg="$1"
  local dest
  if [[ "$cfg" == "Release" ]]; then
    dest="generic/platform=iOS"
  else
    dest="platform=iOS Simulator,name=$(_pick_sim)"
  fi
  run_cmd "iOS ${cfg} Build" \
    xcodebuild -project "$IOS_PROJECT" -scheme "$IOS_SCHEME" \
               -configuration "$cfg" -destination "$dest" build
}

ios_test() {
  run_cmd "iOS Tests" \
    xcodebuild -project "$IOS_PROJECT" -scheme "$IOS_SCHEME" \
               -destination "platform=iOS Simulator,name=$(_pick_sim)" test
}

# ── Sub-menus ─────────────────────────────────────────────────────────────────
android_menu() {
  local items=(
    "🟢  Build Debug APK"
    "🚀  Build Release APK"
    "🧪  Run Unit Tests"
    "📲  Install Debug on Device"
    "🔍  Lint Check"
    "← Back"
  )
  while true; do
    menu "Android" "${items[@]}"
    case $MENU_RESULT in
      0) run_cmd "Android Debug Build"     ./gradlew :androidApp:assembleDebug ;;
      1) run_cmd "Android Release Build"   ./gradlew :androidApp:assembleRelease ;;
      2) run_cmd "Android Unit Tests"      ./gradlew :shared:testDebugUnitTest ;;
      3) run_cmd "Install Debug on Device" ./gradlew :androidApp:installDebug ;;
      4) run_cmd "Android Lint"            ./gradlew :androidApp:lintDebug ;;
      5) return ;;
    esac
  done
}

ios_menu() {
  local items=(
    "🟢  Build Debug (Simulator)"
    "🚀  Build Release (Device)"
    "🧪  Run Tests (Simulator)"
    "🔗  Build KMM Shared Framework"
    "← Back"
  )
  while true; do
    menu "iOS" "${items[@]}"
    case $MENU_RESULT in
      0) ios_build "Debug" ;;
      1) ios_build "Release" ;;
      2) ios_test ;;
      3) run_cmd "KMM iOS Framework" \
           ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 ;;
      4) return ;;
    esac
  done
}

both_menu() {
  local items=(
    "🧪  Run All Unit Tests"
    "🟢  Build Both Debug"
    "🧹  Clean All"
    "← Back"
  )
  while true; do
    menu "Android + iOS" "${items[@]}"
    case $MENU_RESULT in
      0) run_cmd "Android Unit Tests" ./gradlew :shared:testDebugUnitTest
         ios_test ;;
      1) run_cmd "Android Debug Build" ./gradlew :androidApp:assembleDebug
         ios_build "Debug" ;;
      2) run_cmd "Clean All" ./gradlew clean ;;
      3) return ;;
    esac
  done
}

# ── Main ──────────────────────────────────────────────────────────────────────
main() {
  local items=(
    "🤖  Android"
    "🍎  iOS"
    "🔀  Android + iOS"
    "🚪  Quit"
  )
  while true; do
    menu "" "${items[@]}"
    case $MENU_RESULT in
      0) android_menu ;;
      1) ios_menu ;;
      2) both_menu ;;
      3) _cleanup; exit 0 ;;
    esac
  done
}

main
