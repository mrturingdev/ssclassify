#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
#  dev.sh  ·  S.S. Classify  ·  Developer TUI
#  Arrow-key + mouse-click menu.  Device/simulator picker when >1 is available.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

# ── Colours ───────────────────────────────────────────────────────────────────
RESET="\033[0m"; BOLD="\033[1m"; DIM="\033[2m"
RED="\033[31m";  GREEN="\033[32m"; YELLOW="\033[33m"; CYAN="\033[36m"
ARROW="▶"; TICK="✔"; CROSS="✘"; CHECK="✔"; UNCHECK="○"

# ── Terminal ──────────────────────────────────────────────────────────────────
_clear()         { printf "\033[2J\033[H"; }
_hide_cursor()   { tput civis 2>/dev/null || true; }
_show_cursor()   { tput cnorm 2>/dev/null || true; }
_enable_mouse()  { printf '\033[?1000h'; }
_disable_mouse() { printf '\033[?1000l'; }

_cleanup() { _disable_mouse; _show_cursor; printf "\n"; }
trap '_cleanup' EXIT INT TERM

# ── Key reader ────────────────────────────────────────────────────────────────
KEY=""; MOUSE_ROW=0; MOUSE_COL=0

_read_key() {
  KEY=""; MOUSE_ROW=0; MOUSE_COL=0
  local ch s1 s2
  IFS= read -rsn1 ch
  if [[ "$ch" != $'\x1b' ]]; then KEY="$ch"; return; fi

  IFS= read -rsn1 -t 0.15 s1 2>/dev/null || { KEY="ESC"; return; }
  if [[ "$s1" == '[' ]]; then
    IFS= read -rsn1 -t 0.15 s2 2>/dev/null || { KEY="ESC"; return; }
    case "$s2" in
      A) KEY="UP"    ;;
      B) KEY="DOWN"  ;;
      C) KEY="RIGHT" ;;
      D) KEY="LEFT"  ;;
      H) KEY="HOME"  ;;
      F) KEY="END"   ;;
      M) # X10 mouse: 3 bytes (button, col, row each +32)
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
    IFS= read -rsn1 -t 0.15 s2 2>/dev/null || { KEY="ESC"; return; }
    case "$s2" in H) KEY="HOME" ;; F) KEY="END" ;; *) KEY="ESC" ;; esac
  else KEY="ESC"; fi
}

# ── Header / footer ───────────────────────────────────────────────────────────
_header() {
  local sub="${1:-}"
  _clear
  printf "${BOLD}${CYAN}╔══════════════════════════════════════════════╗\n"
  printf            "║   📱  S.S. Classify  ─  DevTools  🛠         ║\n"
  printf            "╚══════════════════════════════════════════════╝${RESET}\n"
  [[ -n "$sub" ]] && printf "  ${DIM}%s${RESET}\n" "$sub"
  printf "\n"
}

# ── Single-select menu ────────────────────────────────────────────────────────
# menu "Subtitle" "item0" "item1" …  → sets MENU_RESULT (0-based index)
MENU_RESULT=0

menu() {
  local sub="$1"; shift
  local items=("$@")
  local count=${#items[@]} cur=0
  local hrows=4; [[ -n "$sub" ]] && hrows=5
  local item_start=$(( hrows + 1 ))

  _hide_cursor; _enable_mouse
  while true; do
    _header "$sub"
    for i in "${!items[@]}"; do
      if (( i == cur )); then
        printf "  ${BOLD}${CYAN} ${ARROW} ${items[$i]}${RESET}\n"
      else
        printf "  ${DIM}   ${items[$i]}${RESET}\n"
      fi
    done
    printf "\n${DIM}  [↑↓] Move   [Enter/Click] Select   [q] Quit${RESET}\n"

    _read_key
    case "$KEY" in
      UP|k)   (( cur > 0 ))          && (( cur-- )) ;;
      DOWN|j) (( cur < count - 1 ))  && (( cur++ )) ;;
      HOME|g) cur=0 ;;
      END|G)  cur=$(( count - 1 )) ;;
      CLICK)
        local idx=$(( MOUSE_ROW - item_start ))
        if (( idx >= 0 && idx < count )); then
          MENU_RESULT=$idx; _disable_mouse; return
        fi ;;
      ''|$'\n') MENU_RESULT=$cur; _disable_mouse; return ;;
      q|Q) _cleanup; exit 0 ;;
    esac
  done
}

# ── Multi-select menu ─────────────────────────────────────────────────────────
# multiselect "Subtitle" "item0" "item1" …
# → sets MULTI_RESULT (space-separated 0-based indices of checked items)
# Returns 1 if user cancelled (chose ← Cancel or 'q')
MULTI_RESULT=""

multiselect() {
  local sub="$1"; shift
  local items=("$@")
  local count=${#items[@]} cur=0
  local -a sel; for i in "${!items[@]}"; do sel[$i]=0; done
  local hrows=4; [[ -n "$sub" ]] && hrows=5
  # +1 for the "── Select ──" separator line above items
  local item_start=$(( hrows + 2 ))

  _hide_cursor; _enable_mouse
  while true; do
    _header "$sub"
    printf "  ${DIM}Space to toggle, Enter to confirm${RESET}\n\n"
    for i in "${!items[@]}"; do
      local icon="${UNCHECK}"
      [[ ${sel[$i]} -eq 1 ]] && icon="${BOLD}${GREEN}${CHECK}${RESET}"
      if (( i == cur )); then
        printf "  ${BOLD}${CYAN} ${ARROW} [%b] %s${RESET}\n" "$icon" "${items[$i]}"
      else
        printf "  ${DIM}   [%b] %s${RESET}\n" "$icon" "${items[$i]}"
      fi
    done
    printf "\n${DIM}  [↑↓] Move   [Space/Click] Toggle   [Enter] Confirm   [q] Quit${RESET}\n"

    _read_key
    case "$KEY" in
      UP|k)   (( cur > 0 ))          && (( cur-- )) ;;
      DOWN|j) (( cur < count - 1 ))  && (( cur++ )) ;;
      ' ')    [[ ${sel[$cur]} -eq 1 ]] && sel[$cur]=0 || sel[$cur]=1 ;;
      CLICK)
        local idx=$(( MOUSE_ROW - item_start ))
        if (( idx >= 0 && idx < count )); then
          cur=$idx
          [[ ${sel[$cur]} -eq 1 ]] && sel[$cur]=0 || sel[$cur]=1
        fi ;;
      ''|$'\n')
        MULTI_RESULT=""
        for i in "${!sel[@]}"; do
          [[ ${sel[$i]} -eq 1 ]] && MULTI_RESULT+="$i "
        done
        MULTI_RESULT="${MULTI_RESULT% }"  # trim trailing space
        _disable_mouse; return 0 ;;
      q|Q) _disable_mouse; return 1 ;;
    esac
  done
}

# ── Command runner ────────────────────────────────────────────────────────────
run_cmd() {
  local label="$1"; shift
  _clear; _show_cursor
  printf "${BOLD}${CYAN}┌─ %s${RESET}\n${DIM}  \$ %s${RESET}\n\n" "$label" "$*"
  local t=$SECONDS
  set +e; "$@"; local rc=$?; set -e
  local elapsed=$(( SECONDS - t ))
  printf "\n"
  (( rc == 0 )) \
    && printf "${BOLD}${GREEN}  ${TICK} Done in ${elapsed}s${RESET}\n" \
    || printf "${BOLD}${RED}  ${CROSS} Failed (exit ${rc}) after ${elapsed}s${RESET}\n"
  printf "\n${DIM}  Press any key to return…${RESET}"; IFS= read -rsn1
  _hide_cursor
}

_warn() {
  _clear
  printf "\n  ${YELLOW}⚠  %s${RESET}\n\n${DIM}  Press any key…${RESET}" "$1"
  IFS= read -rsn1
}

# ─────────────────────────────────────────────────────────────────────────────
#  ANDROID  helpers
# ─────────────────────────────────────────────────────────────────────────────
IOS_PROJECT="iosApp/iosApp.xcodeproj"
IOS_SCHEME="ImageCategorizer"

# Prints "serial|label" lines for every connected adb device
_android_devices() {
  adb devices -l 2>/dev/null | awk '
    NR>1 && NF>1 && $2=="device" {
      serial=$1; label=""
      for(i=3;i<=NF;i++) {
        if($i~/^model:/)  { split($i,a,":"); label=a[2] }
      }
      gsub(/_/," ",label)
      if(label=="") label=serial
      # Mark emulators
      if(serial~/^emulator-/) label="[Emulator] " label
      print serial "|" label " (" serial ")"
    }'
}

# Picker: sets ANDROID_SERIALS (array) — empty means cancelled
ANDROID_SERIALS=()

_pick_android_targets() {
  local prompt="${1:-Select Android target}"
  local allow_multi="${2:-yes}"   # yes = multi-select, no = single

  local serials=() labels=()
  while IFS='|' read -r s l; do
    serials+=("$s"); labels+=("$l")
  done < <(_android_devices)

  local n=${#serials[@]}

  if (( n == 0 )); then
    _warn "No Android devices or emulators connected."
    ANDROID_SERIALS=(); return 1
  fi

  if (( n == 1 )); then
    ANDROID_SERIALS=("${serials[0]}"); return 0
  fi

  # Multiple available
  if [[ "$allow_multi" == "yes" ]]; then
    multiselect "$prompt" "${labels[@]}" || { ANDROID_SERIALS=(); return 1; }
    if [[ -z "$MULTI_RESULT" ]]; then
      _warn "No device selected."; ANDROID_SERIALS=(); return 1
    fi
    ANDROID_SERIALS=()
    for idx in $MULTI_RESULT; do
      ANDROID_SERIALS+=("${serials[$idx]}")
    done
  else
    # Single-select + "All" option
    local menu_items=("All connected (${n})" "${labels[@]}" "← Cancel")
    menu "$prompt" "${menu_items[@]}"
    local r=$MENU_RESULT
    if (( r == 0 )); then
      ANDROID_SERIALS=("${serials[@]}")
    elif (( r <= n )); then
      ANDROID_SERIALS=("${serials[$((r-1))]}")
    else
      ANDROID_SERIALS=(); return 1
    fi
  fi
}

# ─────────────────────────────────────────────────────────────────────────────
#  iOS  helpers
# ─────────────────────────────────────────────────────────────────────────────

# Prints "dest_string|label" lines for simulators + physical devices
_ios_destinations() {
  # Simulators
  xcrun simctl list devices available --json 2>/dev/null | python3 -c "
import sys, json, re
devs = json.load(sys.stdin).get('devices', {})
out = []
for rt, ds in sorted(devs.items(), reverse=True):
    if 'iOS' not in rt: continue
    m = re.search(r'iOS-(\d+)-(\d+)', rt)
    ver = f'{m.group(1)}.{m.group(2)}' if m else rt.split('.')[-1]
    for d in ds:
        if not d.get('isAvailable'): continue
        name = d['name']
        dest = f'platform=iOS Simulator,name={name}'
        out.append(f'{dest}|{name} (iOS {ver}) [Sim]')
for line in out[:12]:
    print(line)
" 2>/dev/null

  # Physical devices via xctrace (only iPhone/iPad, not Mac)
  xcrun xctrace list devices 2>/dev/null \
    | grep -v Simulator \
    | grep -E '\([0-9A-Fa-f-]{36}\)' \
    | grep -iE '(iPhone|iPad)' \
    | while IFS= read -r line; do
        # "Device Name (OS version) (UDID)"
        udid=$(echo "$line" | grep -oE '[0-9A-Fa-f-]{36}' | tail -1)
        name=$(echo "$line" | sed 's/ ([^)]*) ([0-9A-Fa-f-]*)$//' | sed 's/^ *//')
        if [[ -n "$udid" && -n "$name" ]]; then
          printf "platform=iOS,id=%s|%s [Device]\n" "$udid" "$name"
        fi
      done
}

# Picker: sets IOS_DESTINATION string for xcodebuild
IOS_DESTINATION=""

_pick_ios_destination() {
  local prompt="${1:-Select iOS target}"
  local dests=() labels=()
  while IFS='|' read -r d l; do
    dests+=("$d"); labels+=("$l")
  done < <(_ios_destinations)

  local n=${#dests[@]}

  if (( n == 0 )); then
    _warn "No iOS simulators or devices found."
    IOS_DESTINATION=""; return 1
  fi

  if (( n == 1 )); then
    IOS_DESTINATION="${dests[0]}"; return 0
  fi

  menu "$prompt" "${labels[@]}" "← Cancel"
  local r=$MENU_RESULT
  if (( r < n )); then
    IOS_DESTINATION="${dests[$r]}"
  else
    IOS_DESTINATION=""; return 1
  fi
}

# ─────────────────────────────────────────────────────────────────────────────
#  Build / test actions
# ─────────────────────────────────────────────────────────────────────────────

android_build() {
  local cfg="$1"  # Debug | Release
  run_cmd "Android ${cfg} Build" ./gradlew ":androidApp:assemble${cfg}"
}

android_install() {
  _pick_android_targets "Install on which device(s)?" "yes" || return
  for serial in "${ANDROID_SERIALS[@]}"; do
    run_cmd "Install Debug → ${serial}" \
      env ANDROID_SERIAL="$serial" ./gradlew :androidApp:installDebug
  done
}

android_unit_test() {
  run_cmd "Android Unit Tests" ./gradlew :shared:testDebugUnitTest
}

android_instrumented_test() {
  _pick_android_targets "Run instrumented tests on?" "yes" || return
  for serial in "${ANDROID_SERIALS[@]}"; do
    run_cmd "Instrumented Tests → ${serial}" \
      env ANDROID_SERIAL="$serial" ./gradlew :androidApp:connectedAndroidTest
  done
}

android_lint() {
  run_cmd "Android Lint" ./gradlew :androidApp:lintDebug
}

ios_build() {
  local cfg="$1"
  if [[ "$cfg" == "Release" ]]; then
    run_cmd "iOS Release Build" \
      xcodebuild -project "$IOS_PROJECT" -scheme "$IOS_SCHEME" \
                 -configuration Release -destination "generic/platform=iOS" build
  else
    _pick_ios_destination "Build Debug on which simulator/device?" || return
    run_cmd "iOS Debug Build → ${IOS_DESTINATION}" \
      xcodebuild -project "$IOS_PROJECT" -scheme "$IOS_SCHEME" \
                 -configuration Debug -destination "$IOS_DESTINATION" build
  fi
}

ios_test() {
  _pick_ios_destination "Run tests on which simulator/device?" || return
  run_cmd "iOS Tests → ${IOS_DESTINATION}" \
    xcodebuild -project "$IOS_PROJECT" -scheme "$IOS_SCHEME" \
               -destination "$IOS_DESTINATION" test
}

ios_framework() {
  run_cmd "Build KMM iOS Framework" \
    ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
}

# ─────────────────────────────────────────────────────────────────────────────
#  Sub-menus
# ─────────────────────────────────────────────────────────────────────────────

android_menu() {
  local items=(
    "🟢  Build Debug APK"
    "🚀  Build Release APK"
    "🧪  Unit Tests (JVM)"
    "🔬  Instrumented Tests (device)"
    "📲  Install Debug on Device(s)"
    "🔍  Lint Check"
    "← Back"
  )
  while true; do
    menu "Android" "${items[@]}"
    case $MENU_RESULT in
      0) android_build Debug ;;
      1) android_build Release ;;
      2) android_unit_test ;;
      3) android_instrumented_test ;;
      4) android_install ;;
      5) android_lint ;;
      6) return ;;
    esac
  done
}

ios_menu() {
  local items=(
    "🟢  Build Debug (Simulator / Device)"
    "🚀  Build Release (Archive)"
    "🧪  Run Tests"
    "🔗  Build KMM Shared Framework"
    "← Back"
  )
  while true; do
    menu "iOS" "${items[@]}"
    case $MENU_RESULT in
      0) ios_build Debug ;;
      1) ios_build Release ;;
      2) ios_test ;;
      3) ios_framework ;;
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
      0) android_unit_test; ios_test ;;
      1) android_build Debug; ios_build Debug ;;
      2) run_cmd "Clean All" ./gradlew clean ;;
      3) return ;;
    esac
  done
}

# ─────────────────────────────────────────────────────────────────────────────
#  Main
# ─────────────────────────────────────────────────────────────────────────────
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
