# Slow Pitch Scoreboard

A single-screen Android scoreboard app built with Kotlin + Jetpack Compose, matching the
black-background / big-digit design you shared.

## How to open and run it

1. Install [Android Studio](https://developer.android.com/studio) (free) if you don't have it.
2. Unzip this project, then in Android Studio choose **File > Open** and select the
   `SlowPitchScoreboard` folder.
3. Let it sync. This project doesn't ship a Gradle wrapper jar (a binary file), so on first
   open Android Studio will offer to generate one for you automatically — accept that prompt
   (or it will just sync using Android Studio's own bundled Gradle). This requires your
   computer to have internet access the first time.
4. Plug in an Android phone (with USB debugging on) or start an emulator, then press the
   green **Run** button.

## Controls

- **Score** (big number): tap to add a run, swipe left to undo one.
- **Inning** ("Top 1" / "Bot 1" / "Top 2" ...): tap to advance a half-inning, swipe left to go back one.
- **Outs** (dots under "Outs"): tap to add an out. Tapping on the 3rd out clears the dots
  back to zero *and* advances the half-inning at the same time. Swiping left removes an out;
  swiping left at zero outs undoes the last half-inning rollover (back to 2 outs).
- **HR**: tap either team's number to add a home run, swipe left to remove one.
- **Timer** (top left, "50:00"): tap it to open a small adjuster with **-1 min / +1 min**,
  a **Start/Pause** toggle, and **Reset to 50:00**.
- **⋮ menu** (top right): **Reset All** — clears score, HRs, outs, inning, and timer back to
  their starting values (asks for confirmation first).

## A couple of assumptions I made

- The timer counts down for real once you hit **Start** in its adjuster — the original ask
  covered adjusting/resetting it, but a "countdown timer" needs a way to actually start, so
  I added a Start/Pause toggle in that same popup.
- "Reset All" and the 3rd-out rollover felt worth a quick confirmation/is-obvious-enough
  design, so Reset All asks you to confirm first (easy to remove if you'd rather it be instant).
- Orientation is left unlocked (`fullSensor`) since the layout is fully proportional and works
  either way — lock it to landscape or portrait in `AndroidManifest.xml` if you want it fixed.
- No custom launcher icon yet — it'll use a default system icon. Easy to add later via
  Android Studio's **Image Asset** tool (right-click `res` > New > Image Asset).

## Project structure

```
SlowPitchScoreboard/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/slowpitchscoreboard/MainActivity.kt   <- all the UI + logic lives here
│       └── res/values/ (strings.xml, themes.xml)
├── build.gradle.kts
└── settings.gradle.kts
```

Everything (layout, gestures, state) is in the one `MainActivity.kt` file since this is a
single-screen app — easiest place to tweak colors, font sizes, or add fields.
