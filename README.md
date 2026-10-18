# maestro-runner repros

Two Android bugs in [maestro-runner](https://github.com/devicelab-dev/maestro-runner) 1.1.29, each with a
minimal Compose app screen, a flow that shows it, a side-by-side video and a fix:

| Bug | Flow | Video | Fix |
|---|---|---|---|
| `launchApp: clearState` sometimes loses its launch: Android kills the new process | [`flows/clear-state.yaml`](flows/clear-state.yaml) | [`videos/clear-state.mp4`](videos/clear-state.mp4) | branch [`fix/android-clearstate-launch-race`](https://github.com/tSquaredd/maestro-runner/tree/fix/android-clearstate-launch-race) |
| `scrollUntilVisible` stops on a row that hangs past its scroll container, and the tap misses | [`flows/scroll-past-container.yaml`](flows/scroll-past-container.yaml) | [`videos/scroll-past-container.mp4`](videos/scroll-past-container.mp4) | branch [`fix/scroll-past-container`](https://github.com/tSquaredd/maestro-runner/tree/fix/scroll-past-container) |

Both turned up in a real app's release-gate tests on a CI emulator. The app here has nothing else in it.

## Setup

- An Android emulator: API 34, `google_apis`, the `pixel_6` profile (1080×2400, gesture navigation), with
  animations off:
  ```
  for k in window_animation_scale transition_animation_scale animator_duration_scale; do adb shell settings put global $k 0; done
  ```
- The app: `./gradlew :app:installDebug`
- maestro-runner 1.1.29: `curl -fsSL https://open.devicelab.dev/install/maestro-runner | bash -s -- --version 1.1.29`

Run a flow with `maestro-runner --driver devicelab test flows/<flow>.yaml`, or record it with
[`scripts/run-repro.sh`](scripts/run-repro.sh), which made the videos:

```
scripts/run-repro.sh <maestro-runner binary> flows/clear-state.yaml out/clear-state
SHOW_POINTER=1 scripts/run-repro.sh <maestro-runner binary> flows/scroll-past-container.yaml out/scroll
```

It saves the screen recording, maestro-runner's output, and the app's process lifecycle from logcat.

To run a fix, build its branch: `go build -o maestro-runner-fix .` in a clone of
`tSquaredd/maestro-runner`, then run it with `MAESTRO_RUNNER_HOME=<the clone>` so it finds `drivers/`.

## 1. `launchApp: clearState` loses its launch

[`flows/clear-state.yaml`](flows/clear-state.yaml) runs `launchApp: clearState: true` 20 times while the
app is on screen, waiting for the home screen after each.

**Expected:** every launch shows the home screen.

**Actual:** now and then a launch never shows. The flow waits on the launcher and fails. In logcat,
Android kills the process the launch just started:

```
08:01:26.542 I/ActivityManager: Force stopping com.example.maestrorunnerrepros appid=10192 user=0: clear data
08:01:26.543 I/ActivityManager: Killing 31242:com.example.maestrorunnerrepros/u0a192 (adj 0): stop com.example.maestrorunnerrepros due to clear data
08:01:26.563 I/ActivityTaskManager: START u0 {act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER] … pkg=com.example.maestrorunnerrepros …}
08:01:26.800 I/ActivityTaskManager: Destroy timeout of remove-task, attempt to kill Task{6fd056d #142 type=standard A=10192:com.example.maestrorunnerrepros}
08:01:26.800 I/ActivityManager: Killing 31393:com.example.maestrorunnerrepros/u0a192 (adj 905): remove task
```

**Why:** clearing an app whose screens are showing force-stops it and removes its task while its
activities are still closing. Android waits for them to be destroyed, and when that wait times out
(`Destroy timeout of remove-task`, about a second later) it kills every process the app has. A launch
inside that second is killed with them. maestro-runner launches right after the clear, so it lands in
that second. Whether the launch is killed comes down to timing. Here 4 of 5 runs of the flow failed; on
a slower CI emulator, 6 of 15 flows that started this way failed. The timeout itself fires on nearly
every clear of a running app: 14 of the 15 clears in the recorded run, and 20 of 20 in a run whose
launches all happened to survive.

A force-stop just before the clear isn't enough: the clear still hit the timeout in 7 of 8 tries, because
the windows were still closing.

**The fix** force-stops the app and waits until its activities and windows are gone before clearing it,
for `launchApp: clearState` and `clearState`, on the DeviceLab and UIAutomator2 drivers. With it, the flow
passes 20 of 20 with no `Destroy timeout` at all.

## 2. `scrollUntilVisible` stops on a row past its scroll container

[`flows/scroll-past-container.yaml`](flows/scroll-past-container.yaml) opens a bottom sheet whose list ends
above the gesture bar. The list's last row, Delete, starts with only its top edge showing. The flow
scrolls to Delete, taps it, and waits for the Deleted dialog.

**Expected:** `scrollUntilVisible` scrolls Delete into the list, and the tap opens the dialog.

**Actual:** `scrollUntilVisible` returns at once, without scrolling. The tap lands on the gesture bar (the
pointer trace in the video), and the dialog never opens:

```
Step 2 completed successfully (78ms): scrollUntilVisible: id="repro.deleteButton"
FindAndClick hit for #repro.deleteButton bounds=[63,2295][1017,2400] (w=954 h=105) center=(540,2347)
Step 4 failed (5014ms): extendedWaitUntil: visible id="repro.deletedDialog" - Error: Element '#repro.deletedDialog' not visible within 5s
```

**Why:** the hierarchy reports the row past the list, to the bottom of the screen:

```
resource-id="repro.list"          bounds="[63,63][1017,2337]"
resource-id="repro.deleteButton"  bounds="[63,2295][1017,2400]"
```

Measured against the screen the row is 100% visible, so the scroll stops. Inside the list only 42 of its
105 reported pixels show, and the tap goes to its centre, y 2347, on the gesture bar. The
[#164](https://github.com/devicelab-dev/maestro-runner/issues/164) check doesn't catch it: it looks for a
row flush with its container's edge, and this one runs 63 px past it. A `ModalBottomSheet` with a
`LazyColumn` kept clear of the navigation bar with `navigationBarsPadding()` reports its rows this way; the
same list on a plain screen is clipped to the list (the home screen's other two buttons show it).

**The fix** also measures the row against its scroll container, and scrolls on until the container shows
it. If the list can't scroll further, the row is accepted as before, so no flow that passes today can
fail. With it, the flow scrolls the row in (bounds `[63,1959][1017,2274]`) and the dialog opens.
DeviceLab, UIAutomator2 and Appium drivers.
