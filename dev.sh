#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
#  dev.sh  ·  S.S. Classify  ·  Developer TUI
#  Arrow-key menu for Android / iOS builds, tests and installs.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

# ── Colours & symbols ─────────────────────────────────────────────────────────
RESET="\033[0m";   BOLD="\033[1m";  DIM="\033[2m"
RED="\033[31m";    GREEN="\033[32m"; CYAN="\033[36m"
ARROW="▶";  TICK="✔";  CROSS="✘"

# ── Terminal helpers ──────────────────────────────────────────────────────────
_clear()       { printf "\033[2J\033[H"; }
_hide_cursor() { tput civis 2>/dev/null || true; }
_show_cursor() { tput cnorm 2>/dev/null || true; }

trap '_show_cursor; echo ""' EXIT INT TERM

# ── Key reader ────────────────────────────────────────────────────────────────
# Reads one keypress and echoes a symbolic name:
#   UP  DOWN  ENTER  HOME  END  ESC  <the character itself>
_read_key() {
  local ch seq1 seq2
  IFS= read -rsn1 ch

  if [[ "$ch" != $'\x1b' ]]; then
    printf '%s' "$ch"
    return
  fi

  # Could be a bare ESC or the start of an escape sequence.
  IFS= read -rsn1 -t 0.15 seq1 2>/dev/null || { printf 'ESC'; return; }

  if [[ "$seq1" == '[' ]]; then
    IFS= read -rsn1 -t 0.15 seq2 2>/dev/null || { printf 'ESC'; return; }
    case "$seq2" in
      A) printf 'UP'   ;;
      B) printf 'DOWN' ;;
      C) printf 'RIGHT';;
      D) printf 'LEFT' ;;
      H) printf 'HOME' ;;
      F) printf 'END'  ;;
      *) printf 'ESC'  ;;
    esac
  elif [[ "$seq1" == 'O' ]]; then
    IFS= read -rsn1 -t 0.15 seq2 2>/dev/null || { printf 'ESC'; return; }
    case "$seq2" in
      H) printf 'HOME' ;;
      F) printf 'END'  ;;
      *) printf 'ESC'  ;;
    esac
  else
    printf 'ESC'
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
  [[ -n "$subtitle" ]] && printf "  ${DIM}%s${RESET}\n" "$subtitle"
  printf "\n"
}

_footer() {
  printf "\n${DIM}  [↑↓] Move   [Enter] Select   [q] Quit${RESET}\n"
}

# ── Arrow-key menu ────────────────────────────────────────────────────────────
# Sets global MENU_RESULT to the 0-based index the user chose.
MENU_RESULT=0
menu() {
  local subtitle="$1"; shift
  local items=("$@")
  local count=${#items[@]}
  local cur=0

  _hide_cursor

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

    local key
    key=$(_read_key)

    case "$key" in
      UP|k)    (( cur > 0 ))           && (( cur-- )) ;;
      DOWN|j)  (( cur < count - 1 ))   && (( cur++ )) ;;
      HOME|g)  cur=0 ;;
      END|G)   cur=$(( count - 1 )) ;;
      ''|ENTER) break ;;
      q|Q)     _show_cursor; exit 0 ;;
    esac
  done

  MENU_RESULT=$cur
}

# ── Command runner ────────────────────────────────────────────────────────────
run_cmd() {
  local label="$1"; shift
  _clear
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
}

# ── iOS helpers ───────────────────────────────────────────────────────────────
IOS_PROJECT="iosApp/iosApp.xcodeproj"
IOS_SCHEME="ImageCategorizer"

_pick_sim() {
  xcrun simctl list devices available --json 2>/dev/null \
    | python3 -c "
import sys, json
devs = json.load(sys.stdin).get('devices', {})
for runtime, devices in devs.items():
    if 'iOS' in runtime:
        for d in devices:
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
  run_cmd "iOS Unit Tests" \
    xcodebuild -project "$IOS_PROJECT" -scheme "$IOS_SCHEME" \
               -destination "platform=iOS Simulator,name=$(_pick_sim)" test
}

# ── Sub-menus ─────────────────────────────────────────────────────────────────
android_menu() {
  local items=(
    "🟢  Build Debug APK"
    "🚀  Build Release APK"
    "🧪  Run Unit Tests (shared)"
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
      3) run_cmd "Build iOS Shared Framework" \
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

# ── Main menu ─────────────────────────────────────────────────────────────────
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
      3) _show_cursor; exit 0 ;;
    esac
  done
}

main
