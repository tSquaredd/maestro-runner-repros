#!/usr/bin/env bash
# Runs one repro flow with a maestro-runner binary, recording the screen.
#
#   scripts/run-repro.sh <maestro-runner binary> <flow file> <output dir> [device serial]
#
# Writes to <output dir>: screen.mp4 (taps shown), run.log (maestro-runner's output), and
# logcat.txt (the app's process lifecycle: clears, launches, Android's remove-task kills).
# The device needs the app installed: ./gradlew :app:installDebug
#
# SHOW_POINTER=1 also turns on Android's pointer location, which keeps the last tap's position and
# coordinates on screen, so a quick tap shows in the video.
set -uo pipefail
runner=$1 flow=$2 out=$3 serial=${4:-$(adb devices | awk 'NR==2 {print $1}')}
app=com.example.maestrorunnerrepros
mkdir -p "$out"
adb -s "$serial" shell settings put system show_touches 1
adb -s "$serial" shell settings put system pointer_location "${SHOW_POINTER:-0}"
# Start the video from the launcher, not whatever the last run left on screen.
adb -s "$serial" shell input keyevent KEYCODE_HOME
sleep 1
adb -s "$serial" logcat -c
adb -s "$serial" shell rm -f /sdcard/repro.mp4
adb -s "$serial" shell screenrecord --bit-rate 6000000 /sdcard/repro.mp4 &
recorder=$!
sleep 1
MAESTRO_RUNNER_NO_TELEMETRY=1 "$runner" --device "$serial" --driver devicelab test --output "$out/report" "$flow" > "$out/run.log" 2>&1
status=$?
# Keep recording a moment, so the video shows how the flow ended.
sleep 3
adb -s "$serial" shell pkill -INT screenrecord
wait "$recorder" 2>/dev/null
sleep 1
adb -s "$serial" pull /sdcard/repro.mp4 "$out/screen.mp4" > /dev/null 2>&1
adb -s "$serial" shell settings put system show_touches 0
adb -s "$serial" shell settings put system pointer_location 0
adb -s "$serial" logcat -d -v time | grep -E "$app" | grep -E 'Force stopping|Killing|Destroy timeout|remove task|START u0|Displayed|start not valid' > "$out/logcat.txt"
echo "exit $status; destroy timeouts: $(grep -c 'Destroy timeout' "$out/logcat.txt"); launches killed: $(grep -c 'remove task' "$out/logcat.txt")"
exit $status
