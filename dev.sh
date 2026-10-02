#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
#  dev.sh  ·  S.S. Classify  ·  Developer TUI
#  Arrow-key menu for Android / iOS builds, tests and installs.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

# ── Colours & symbols ────────────────────────────────────────────────────────
RESET="\033[0m"
BOLD="\033[1m"
DIM="\033[2m"

BLACK="\033[30m"
RED="\033[31m"
GREEN="\033[32m"
YELLOW="\033[33m"
BLUE="\033[34m"
MAGENTA="\033[35m"
CYAN="\033[36m"
WHITE="\033[37m"

BG_BLUE="\033[44m"
BG_CYAN="\033[46m"

TICK="✔"
CROSS="✘"
ARROW="▶"
DOT="•"

# ── Helpers ───────────────────────────────────────────────────────────────────
clear_screen() { printf "\033[2J\033[H"; }

hide_cursor() { tput civis 2>/dev/null || true; }
show_cursor() { tput cnorm 2>/dev/null || true; }

trap 'show_cursor; echo ""' EXIT INT TERM

print_header() {
  local platform="${1:-}"
  clear_screen
  printf "${BOLD}${CYAN}"
  printf "╔══════════════════════════════════════════════╗\n"
  printf "║   📱  S.S. Classify  ─  DevTools  🛠         ║\n"
  printf "╚══════════════════════════════════════════════╝${RESET}\n"
  if [[ -n "$platform" ]]; then
    printf "  ${DIM}Platform:${RESET} ${BOLD}${platform}${RESET}\n"
  fi
  printf "\n"
}

print_footer() {
  printf "\n${DIM}  [↑/↓] Navigate   [Enter] Select   [q] Quit${RESET}\n"
}

# ── Arrow-key menu ────────────────────────────────────────────────────────────
# Usage: menu RESULT_VAR "Title" "item1" "item2" ...
# Returns the 0-based index of the selected item in RESULT_VAR.
menu() {
  local -n _result=$1; shift
  local title="$1"; shift
  local items=("$@")
  local count=${#items[@]}
  local current=0

  hide_cursor

  while true; do
    print_header "$title"
    for i in "${!items[@]}"; do
      if [[ $i -eq $current ]]; then
        printf "  ${BOLD}${CYAN} ${ARROW} ${items[$i]}${RESET}\n"
      else
        printf "  ${DIM}   ${items[$i]}${RESET}\n"
      fi
    done
    print_footer

    # Read a key (handles arrow sequences)
    IFS= read -rsn1 key
    if [[ "$key" == $'\x1b' ]]; then
      IFS= read -rsn2 -t 0.1 rest || rest=""
      key="${key}${rest}"
    fi

    case "$key" in
      $'\x1b[A'|k) (( current > 0 ))           && (( current-- )) ;;   # Up
      $'\x1b[B'|j) (( current < count - 1 ))   && (( current++ )) ;;   # Down
      $'\x1b[H'|g) current=0 ;;                                         # Home
      $'\x1b[F'|G) current=$(( count - 1 )) ;;                          # End
      ''|$'\n')     break ;;                                             # Enter
      q|Q)          show_cursor; exit 0 ;;
    esac
  done

  _result=$current
}

# ── Command runner ─────────────────────────────────────────────────────────────
run_cmd() {
  local label="$1"; shift
  local cmd=("$@")

  clear_screen
  printf "${BOLD}${CYAN}┌─ Running: ${label}${RESET}\n"
  printf "${DIM}  $ ${cmd[*]}${RESET}\n\n"

  local start_ts=$SECONDS
  set +e
  "${cmd[@]}"
  local exit_code=$?
  set -e
  local elapsed=$(( SECONDS - start_ts ))

  printf "\n"
  if [[ $exit_code -eq 0 ]]; then
    printf "${BOLD}${GREEN}${TICK} Done in ${elapsed}s${RESET}\n"
  else
    printf "${BOLD}${RED}${CROSS} Failed (exit ${exit_code}) after ${elapsed}s${RESET}\n"
  fi

  printf "\n${DIM}Press any key to return to the menu…${RESET}"
  IFS= read -rsn1
  show_cursor
}

# ── iOS helpers ────────────────────────────────────────────────────────────────
IOS_PROJECT="iosApp/iosApp.xcodeproj"
IOS_SCHEME="ImageCategorizer"

# Pick the first available iPhone simulator
_pick_simulator() {
  xcrun simctl list devices available --json 2>/dev/null \
    | python3 -c "
import sys, json
devs = json.load(sys.stdin)['devices']
for runtime, devices in devs.items():
    if 'iOS' in runtime:
        for d in devices:
            if d.get('isAvailable') and 'iPhone' in d.get('name',''):
                print(d['name'])
                raise SystemExit
" 2>/dev/null || echo "iPhone 16"
}

ios_build() {
  local config="$1"   # Debug | Release
  local dest
  if [[ "$config" == "Release" ]]; then
    dest="generic/platform=iOS"
  else
    local sim
    sim=$(_pick_simulator)
    dest="platform=iOS Simulator,name=${sim}"
  fi
  run_cmd "iOS ${config} Build" \
    xcodebuild \
      -project "$IOS_PROJECT" \
      -scheme "$IOS_SCHEME" \
      -configuration "$config" \
      -destination "$dest" \
      build
}

ios_test() {
  local sim
  sim=$(_pick_simulator)
  run_cmd "iOS Unit Tests" \
    xcodebuild \
      -project "$IOS_PROJECT" \
      -scheme "$IOS_SCHEME" \
      -destination "platform=iOS Simulator,name=${sim}" \
      test
}

# ── Android sub-menu ──────────────────────────────────────────────────────────
android_menu() {
  local choice
  local items=(
    "🟢  Build Debug APK"
    "🚀  Build Release APK"
    "🧪  Run Unit Tests"
    "📲  Install Debug on Device"
    "🔍  Lint Check"
    "← Back"
  )

  while true; do
    menu choice "Android" "${items[@]}"
    case $choice in
      0) run_cmd "Android Debug Build"   ./gradlew :androidApp:assembleDebug ;;
      1) run_cmd "Android Release Build" ./gradlew :androidApp:assembleRelease ;;
      2) run_cmd "Android Unit Tests"    ./gradlew :shared:testDebugUnitTest ;;
      3) run_cmd "Install Debug on Device" ./gradlew :androidApp:installDebug ;;
      4) run_cmd "Android Lint"          ./gradlew :androidApp:lintDebug ;;
      5) return ;;
    esac
  done
}

# ── iOS sub-menu ──────────────────────────────────────────────────────────────
ios_menu() {
  local choice
  local items=(
    "🟢  Build Debug (Simulator)"
    "🚀  Build Release (Device)"
    "🧪  Run Tests (Simulator)"
    "🔗  Build KMM Shared Framework"
    "← Back"
  )

  while true; do
    menu choice "iOS" "${items[@]}"
    case $choice in
      0) ios_build "Debug" ;;
      1) ios_build "Release" ;;
      2) ios_test ;;
      3) run_cmd "Build iOS Shared Framework" \
           ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64 ;;
      4) return ;;
    esac
  done
}

# ── "Both" sub-menu ────────────────────────────────────────────────────────────
both_menu() {
  local choice
  local items=(
    "🧪  Run All Unit Tests"
    "🟢  Build Both Debug"
    "🧹  Clean All"
    "← Back"
  )

  while true; do
    menu choice "Android + iOS" "${items[@]}"
    case $choice in
      0)
        run_cmd "Android Unit Tests" ./gradlew :shared:testDebugUnitTest
        ios_test
        ;;
      1)
        run_cmd "Android Debug Build" ./gradlew :androidApp:assembleDebug
        ios_build "Debug"
        ;;
      2)
        run_cmd "Clean All" ./gradlew clean
        ;;
      3) return ;;
    esac
  done
}

# ── Main menu ─────────────────────────────────────────────────────────────────
main() {
  local choice
  local items=(
    "🤖  Android"
    "🍎  iOS"
    "🔀  Android + iOS"
    "🚪  Quit"
  )

  while true; do
    menu choice "" "${items[@]}"
    case $choice in
      0) android_menu ;;
      1) ios_menu ;;
      2) both_menu ;;
      3) show_cursor; exit 0 ;;
    esac
  done
}

main
