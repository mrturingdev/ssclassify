#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
#  dev.sh  ·  S.S. Classify  ·  Developer TUI
#  Arrow-key + mouse-click menu.
#  Device/simulator picker when >1 is available.
#  Test Suite runner: list each test case, run multiple, filter, or all.
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
  local ch s1 s2 s3
  IFS= read -rsn1 ch
  if [[ "$ch" != $'\x1b' ]]; then KEY="$ch"; return; fi

  IFS= read -rsn1 -t 0.15 s1 2>/dev/null || { KEY="ESC"; return; }
  if [[ "$s1" == '[' ]]; then
    IFS= read -rsn1 -t 0.15 s2 2>/dev/null || { KEY="ESC"; return; }
    case "$s2" in
      A) KEY="UP"       ;;
      B) KEY="DOWN"     ;;
      C) KEY="RIGHT"    ;;
      D) KEY="LEFT"     ;;
      H) KEY="HOME"     ;;
      F) KEY="END"      ;;
      5)
        IFS= read -rsn1 -t 0.15 s3 2>/dev/null || true
        KEY="PAGEUP"
        ;;
      6)
        IFS= read -rsn1 -t 0.15 s3 2>/dev/null || true
        KEY="PAGEDOWN"
        ;;
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

# ── Single-select menu (with scrollable viewport) ─────────────────────────────
# menu "Subtitle" "item0" "item1" …  → sets MENU_RESULT (0-based index)
MENU_RESULT=0

menu() {
  local sub="$1"; shift
  local items=("$@")
  local count=${#items[@]}
  if (( count == 0 )); then MENU_RESULT=0; return 0; fi

  local cur=0
  local term_lines
  term_lines=$(tput lines 2>/dev/null || echo 24)
  local window_size=$(( term_lines - 10 ))
  (( window_size < 8 )) && window_size=8
  (( window_size > 14 )) && window_size=14

  local window_start=0

  _hide_cursor; _enable_mouse
  while true; do
    if (( cur < window_start )); then
      window_start=$cur
    elif (( cur >= window_start + window_size )); then
      window_start=$(( cur - window_size + 1 ))
    fi
    local window_end=$(( window_start + window_size ))
    (( window_end > count )) && window_end=$count
    local visible_count=$(( window_end - window_start ))

    _header "$sub"
    if (( count > window_size )); then
      printf "  ${DIM}Showing %d-%d of %d${RESET}\n" "$(( window_start + 1 ))" "$window_end" "$count"
      if (( window_start > 0 )); then
        printf "  ${CYAN}▲ (%d more above)${RESET}\n" "$window_start"
      else
        printf "  ${DIM}─ (top of list) ───────────────────────────────────${RESET}\n"
      fi
    fi

    local hrows=4; [[ -n "$sub" ]] && hrows=5
    local item_start=$(( hrows + 1 ))
    if (( count > window_size )); then
      item_start=$(( hrows + 3 ))
    fi

    for (( v=0; v < visible_count; v++ )); do
      local i=$(( window_start + v ))
      if (( i == cur )); then
        printf "  ${BOLD}${CYAN} ${ARROW} %s${RESET}\n" "${items[$i]}"
      else
        printf "  ${DIM}   %s${RESET}\n" "${items[$i]}"
      fi
    done

    if (( count > window_size )); then
      local remaining_below=$(( count - window_end ))
      if (( remaining_below > 0 )); then
        printf "  ${CYAN}▼ (%d more below)${RESET}\n" "$remaining_below"
      else
        printf "  ${DIM}─ (end of list) ───────────────────────────────────${RESET}\n"
      fi
    fi

    printf "\n${DIM}  [↑↓] Move   [Enter/Click] Select   [q] Quit${RESET}\n"

    _read_key
    case "$KEY" in
      UP|k)   (( cur > 0 )) && (( cur-- )) ;;
      DOWN|j) (( cur < count - 1 )) && (( cur++ )) ;;
      PAGEUP|b) cur=$(( cur > window_size ? cur - window_size : 0 )) ;;
      PAGEDOWN|f) cur=$(( cur + window_size < count ? cur + window_size : count - 1 )) ;;
      HOME|g) cur=0; window_start=0 ;;
      END|G)  cur=$(( count - 1 )) ;;
      CLICK)
        if (( count > window_size )); then
          if [[ $MOUSE_ROW -eq $(( item_start - 1 )) ]]; then
            cur=$(( cur > window_size ? cur - window_size : 0 ))
          elif [[ $MOUSE_ROW -eq $(( item_start + visible_count )) ]]; then
            cur=$(( cur + window_size < count ? cur + window_size : count - 1 ))
          else
            local v=$(( MOUSE_ROW - item_start ))
            if (( v >= 0 && v < visible_count )); then
              MENU_RESULT=$(( window_start + v ))
              _disable_mouse; return
            fi
          fi
        else
          local idx=$(( MOUSE_ROW - item_start ))
          if (( idx >= 0 && idx < count )); then
            MENU_RESULT=$idx; _disable_mouse; return
          fi
        fi
        ;;
      ''|$'\n') MENU_RESULT=$cur; _disable_mouse; return ;;
      q|Q) _cleanup; exit 0 ;;
    esac
  done
}

# ── Multi-select menu (scrollable viewport, Select All/Clear/Invert) ──────────
# multiselect "Subtitle" "item0" "item1" …
# → sets MULTI_RESULT (space-separated 0-based indices of checked items)
# Returns 1 if user cancelled ('q' or ESC)
MULTI_RESULT=""

multiselect() {
  local sub="$1"; shift
  local items=("$@")
  local count=${#items[@]}
  if (( count == 0 )); then
    _warn "No items to select."
    MULTI_RESULT=""; return 1
  fi

  local cur=0
  local -a sel
  for i in "${!items[@]}"; do sel[$i]=0; done

  local term_lines
  term_lines=$(tput lines 2>/dev/null || echo 24)
  local window_size=$(( term_lines - 12 ))
  (( window_size < 8 )) && window_size=8
  (( window_size > 14 )) && window_size=14

  local window_start=0

  _hide_cursor; _enable_mouse
  while true; do
    if (( cur < window_start )); then
      window_start=$cur
    elif (( cur >= window_start + window_size )); then
      window_start=$(( cur - window_size + 1 ))
    fi
    local window_end=$(( window_start + window_size ))
    (( window_end > count )) && window_end=$count
    local visible_count=$(( window_end - window_start ))

    local checked_count=0
    for s in "${sel[@]}"; do (( s == 1 )) && (( checked_count++ )); done

    _header "$sub"
    printf "  ${DIM}Showing %d-%d of %d  (Checked: ${BOLD}%d${RESET}${DIM}) | [a] All  [c] Clear  [i] Invert${RESET}\n" \
      "$(( window_start + 1 ))" "$window_end" "$count" "$checked_count"

    if (( window_start > 0 )); then
      printf "  ${CYAN}▲ (%d more above)${RESET}\n" "$window_start"
    else
      printf "  ${DIM}─ (top of list) ───────────────────────────────────${RESET}\n"
    fi

    local hrows=4; [[ -n "$sub" ]] && hrows=5
    local item_start=$(( hrows + 3 ))

    for (( v=0; v < visible_count; v++ )); do
      local i=$(( window_start + v ))
      local icon="${UNCHECK}"
      [[ ${sel[$i]} -eq 1 ]] && icon="${BOLD}${GREEN}${CHECK}${RESET}"
      if (( i == cur )); then
        printf "  ${BOLD}${CYAN} ${ARROW} [%b] %s${RESET}\n" "$icon" "${items[$i]}"
      else
        printf "  ${DIM}   [%b] %s${RESET}\n" "$icon" "${items[$i]}"
      fi
    done

    local remaining_below=$(( count - window_end ))
    if (( remaining_below > 0 )); then
      printf "  ${CYAN}▼ (%d more below)${RESET}\n" "$remaining_below"
    else
      printf "  ${DIM}─ (end of list) ───────────────────────────────────${RESET}\n"
    fi

    printf "\n${DIM}  [↑↓] Move   [Space/Click] Toggle   [Enter] Run/Confirm   [q] Cancel${RESET}\n"

    _read_key
    case "$KEY" in
      UP|k)
        (( cur > 0 )) && (( cur-- ))
        ;;
      DOWN|j)
        (( cur < count - 1 )) && (( cur++ ))
        ;;
      PAGEUP|b)
        cur=$(( cur > window_size ? cur - window_size : 0 ))
        ;;
      PAGEDOWN|f)
        cur=$(( cur + window_size < count ? cur + window_size : count - 1 ))
        ;;
      HOME|g)
        cur=0; window_start=0
        ;;
      END|G)
        cur=$(( count - 1 ))
        ;;
      ' ')
        [[ ${sel[$cur]} -eq 1 ]] && sel[$cur]=0 || sel[$cur]=1
        ;;
      a|A)
        for i in "${!sel[@]}"; do sel[$i]=1; done
        ;;
      c|C)
        for i in "${!sel[@]}"; do sel[$i]=0; done
        ;;
      i|I)
        for i in "${!sel[@]}"; do
          [[ ${sel[$i]} -eq 1 ]] && sel[$i]=0 || sel[$i]=1
        done
        ;;
      CLICK)
        if [[ $MOUSE_ROW -eq $(( item_start - 1 )) ]]; then
          cur=$(( cur > window_size ? cur - window_size : 0 ))
        elif [[ $MOUSE_ROW -eq $(( item_start + visible_count )) ]]; then
          cur=$(( cur + window_size < count ? cur + window_size : count - 1 ))
        else
          local v=$(( MOUSE_ROW - item_start ))
          if (( v >= 0 && v < visible_count )); then
            local idx=$(( window_start + v ))
            cur=$idx
            [[ ${sel[$idx]} -eq 1 ]] && sel[$idx]=0 || sel[$idx]=1
          fi
        fi
        ;;
      ''|$'\n')
        # If no items explicitly checked, treat highlighted item as chosen
        if (( checked_count == 0 )); then
          sel[$cur]=1
        fi
        MULTI_RESULT=""
        for i in "${!sel[@]}"; do
          [[ ${sel[$i]} -eq 1 ]] && MULTI_RESULT+="$i "
        done
        MULTI_RESULT="${MULTI_RESULT% }"
        _disable_mouse; return 0
        ;;
      q|Q|ESC)
        _disable_mouse; MULTI_RESULT=""; return 1
        ;;
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

ANDROID_SERIALS=()

_pick_android_targets() {
  local prompt="${1:-Select Android target}"
  local allow_multi="${2:-yes}"

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

_ios_destinations() {
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

  xcrun xctrace list devices 2>/dev/null \
    | grep -v Simulator \
    | grep -E '\([0-9A-Fa-f-]{36}\)' \
    | grep -iE '(iPhone|iPad)' \
    | while IFS= read -r line; do
        udid=$(echo "$line" | grep -oE '[0-9A-Fa-f-]{36}' | tail -1)
        name=$(echo "$line" | sed 's/ ([^)]*) ([0-9A-Fa-f-]*)$//' | sed 's/^ *//')
        if [[ -n "$udid" && -n "$name" ]]; then
          printf "platform=iOS,id=%s|%s [Device]\n" "$udid" "$name"
        fi
      done
}

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
#  TEST SUITE DISCOVERY & RUNNERS
# ─────────────────────────────────────────────────────────────────────────────

# Outputs TSV: task \t class \t method \t filter \t module \t label
_list_all_tests() {
  python3 - << 'EOF'
import os, re

modules = [
    ("shared", ":shared:testDebugUnitTest", "shared"),
    ("aicore", ":aicore:testDebugUnitTest", "aicore"),
    ("androidApp", ":androidApp:testDebugUnitTest", "androidApp")
]

for mod_name, task, path_prefix in modules:
    for root, dirs, files in os.walk(path_prefix):
        if '/build/' in root or '/.gradle/' in root: continue
        for f in sorted(files):
            if f.endswith('Test.kt') or f.endswith('Tests.kt'):
                full_path = os.path.join(root, f)
                with open(full_path, 'r', encoding='utf-8') as fh:
                    content = fh.read()
                cls_match = re.search(r'(?:class|object)\s+([a-zA-Z0-9_]+)', content)
                cls_name = cls_match.group(1) if cls_match else f.replace('.kt', '')
                pattern = r'@Test(?:\s*\(.*?\))?\s*(?:(?:suspend|override|open|internal|private)\s+)*fun\s+(?:`([^`]+)`|([a-zA-Z0-9_]+))'
                for m in re.finditer(pattern, content):
                    method_name = m.group(1) or m.group(2)
                    filt = f"*{cls_name}.{method_name}*"
                    lbl = f"[{mod_name}] {cls_name} ➔ {method_name}"
                    print(f"{task}\t{cls_name}\t{method_name}\t{filt}\t{mod_name}\t{lbl}")
EOF
}

# Outputs TSV: task \t class \t count \t filter \t module \t label
_list_all_classes() {
  python3 - << 'EOF'
import os, re

modules = [
    ("shared", ":shared:testDebugUnitTest", "shared"),
    ("aicore", ":aicore:testDebugUnitTest", "aicore"),
    ("androidApp", ":androidApp:testDebugUnitTest", "androidApp")
]

class_data = {}

for mod_name, task, path_prefix in modules:
    for root, dirs, files in os.walk(path_prefix):
        if '/build/' in root or '/.gradle/' in root: continue
        for f in sorted(files):
            if f.endswith('Test.kt') or f.endswith('Tests.kt'):
                full_path = os.path.join(root, f)
                with open(full_path, 'r', encoding='utf-8') as fh:
                    content = fh.read()
                cls_match = re.search(r'(?:class|object)\s+([a-zA-Z0-9_]+)', content)
                cls_name = cls_match.group(1) if cls_match else f.replace('.kt', '')
                pattern = r'@Test(?:\s*\(.*?\))?\s*(?:(?:suspend|override|open|internal|private)\s+)*fun\s+(?:`([^`]+)`|([a-zA-Z0-9_]+))'
                methods = re.findall(pattern, content)
                if methods:
                    key = (task, cls_name, mod_name)
                    class_data[key] = class_data.get(key, 0) + len(methods)

for (task, cls_name, mod_name), count in sorted(class_data.items(), key=lambda x: (x[0][2], x[0][1])):
    filt = f"*{cls_name}*"
    lbl = f"[{mod_name}] {cls_name} ({count} tests)"
    print(f"{task}\t{cls_name}\t{count}\t{filt}\t{mod_name}\t{lbl}")
EOF
}

_run_all_tests() {
  run_cmd "All Unit Tests (All Modules)" ./gradlew testDebugUnitTest
}

_pick_and_run_test_cases() {
  local kw="${1:-}"
  local tasks=() classes=() methods=() filters=() labels=()

  while IFS=$'\t' read -r t c m f mod lbl; do
    if [[ -n "$kw" ]]; then
      local lower_kw lower_target
      lower_kw=$(echo "$kw" | tr '[:upper:]' '[:lower:]')
      lower_target=$(echo "$c $m $mod" | tr '[:upper:]' '[:lower:]')
      [[ "$lower_target" != *"$lower_kw"* ]] && continue
    fi
    tasks+=("$t")
    classes+=("$c")
    methods+=("$m")
    filters+=("$f")
    labels+=("$lbl")
  done < <(_list_all_tests)

  local count=${#labels[@]}
  if (( count == 0 )); then
    if [[ -n "$kw" ]]; then
      _warn "No test cases matched '$kw'."
    else
      _warn "No test cases found."
    fi
    return 1
  fi

  local title="Select Test Cases (${count})"
  [[ -n "$kw" ]] && title="Tests matching '$kw' (${count})"

  multiselect "$title" "${labels[@]}" || return

  if [[ -z "$MULTI_RESULT" ]]; then
    _warn "No test cases selected."
    return
  fi

  declare -A task_filters
  local sel_count=0
  for idx in $MULTI_RESULT; do
    local t="${tasks[$idx]}"
    local f="${filters[$idx]}"
    task_filters["$t"]+="$f "
    (( sel_count++ ))
  done

  local cmd=(./gradlew)
  for t in "${!task_filters[@]}"; do
    cmd+=("$t")
    for f in ${task_filters[$t]}; do
      cmd+=("--tests" "$f")
    done
  done

  run_cmd "Running Selected Tests (${sel_count} tests)" "${cmd[@]}"
}

_pick_and_run_test_classes() {
  local tasks=() classes=() counts=() filters=() labels=()

  while IFS=$'\t' read -r t c cnt f mod lbl; do
    tasks+=("$t")
    classes+=("$c")
    counts+=("$cnt")
    filters+=("$f")
    labels+=("$lbl")
  done < <(_list_all_classes)

  local count=${#labels[@]}
  if (( count == 0 )); then
    _warn "No test suites found."
    return 1
  fi

  multiselect "Select Test Suites (${count} classes)" "${labels[@]}" || return

  if [[ -z "$MULTI_RESULT" ]]; then
    _warn "No test suites selected."
    return
  fi

  declare -A task_filters
  local sel_count=0
  for idx in $MULTI_RESULT; do
    local t="${tasks[$idx]}"
    local f="${filters[$idx]}"
    task_filters["$t"]+="$f "
    (( sel_count++ ))
  done

  local cmd=(./gradlew)
  for t in "${!task_filters[@]}"; do
    cmd+=("$t")
    for f in ${task_filters[$t]}; do
      cmd+=("--tests" "$f")
    done
  done

  run_cmd "Running Selected Suites (${sel_count} suites)" "${cmd[@]}"
}

_filter_and_run_test_cases() {
  _clear; _show_cursor
  printf "${BOLD}${CYAN}┌─ Filter Test Cases${RESET}\n"
  printf "${DIM}  Type keyword to match class or method name (e.g. Blank, QR, Ocr, Widget, Repo):${RESET}\n\n"
  printf "  ${BOLD}Search:${RESET} "
  local kw
  read -r kw || return
  _hide_cursor
  [[ -z "$kw" ]] && return
  _pick_and_run_test_cases "$kw"
}

test_suite_menu() {
  local items=(
    "🚀  Run All Tests (90 tests across all modules)"
    "🎯  Select Individual Test Cases (90 tests)"
    "📦  Select by Test Suite / Class (20 classes)"
    "🔍  Filter Test Cases by Keyword..."
    "← Back"
  )
  while true; do
    menu "Test Suite Runner" "${items[@]}"
    case $MENU_RESULT in
      0) _run_all_tests ;;
      1) _pick_and_run_test_cases "" ;;
      2) _pick_and_run_test_classes ;;
      3) _filter_and_run_test_cases ;;
      4) return ;;
    esac
  done
}

# ─────────────────────────────────────────────────────────────────────────────
#  Build / run actions
# ─────────────────────────────────────────────────────────────────────────────

android_build() {
  local cfg="$1"
  run_cmd "Android ${cfg} Build" ./gradlew ":androidApp:assemble${cfg}"
}

android_install() {
  _pick_android_targets "Install on which device(s)?" "yes" || return
  for serial in "${ANDROID_SERIALS[@]}"; do
    run_cmd "Install Debug → ${serial}" \
      env ANDROID_SERIAL="$serial" ./gradlew :androidApp:installDebug
  done
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
    "🧪  Unit Tests (Select / All)"
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
      2) test_suite_menu ;;
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
    "🧪  Shared Unit Tests (Select / All)"
    "🔗  Build KMM Shared Framework"
    "← Back"
  )
  while true; do
    menu "iOS" "${items[@]}"
    case $MENU_RESULT in
      0) ios_build Debug ;;
      1) ios_build Release ;;
      2) test_suite_menu ;;
      3) ios_framework ;;
      4) return ;;
    esac
  done
}

both_menu() {
  local items=(
    "🚀  Run All Unit Tests"
    "🧪  Select & Run Specific Tests..."
    "🟢  Build Both Debug"
    "🧹  Clean All"
    "← Back"
  )
  while true; do
    menu "Android + iOS" "${items[@]}"
    case $MENU_RESULT in
      0) _run_all_tests ;;
      1) test_suite_menu ;;
      2) android_build Debug; ios_build Debug ;;
      3) run_cmd "Clean All" ./gradlew clean ;;
      4) return ;;
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
    "🧪  Test Suite (Select / All)"
    "🚪  Quit"
  )
  while true; do
    menu "" "${items[@]}"
    case $MENU_RESULT in
      0) android_menu ;;
      1) ios_menu ;;
      2) both_menu ;;
      3) test_suite_menu ;;
      4) _cleanup; exit 0 ;;
    esac
  done
}

main
