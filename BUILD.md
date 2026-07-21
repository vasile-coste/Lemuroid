# Prerequisites
- Android Studio (latest stable) or JDK 17+ installed
- Android SDK with API 35 (compileSdkVersion = 35)
- NDK (needed for native Libretro core .so files)
- Gradle 8.10.2 (pulled automatically via the `gradlew` wrapper, no manual install needed)

# Project Setup

## 1. Clone the repository (including submodules)
The project depends on a git submodule for its Libretro cores, so pull it in at clone time:

```
git clone --recurse-submodules https://github.com/Swordfish90/Lemuroid.git
```

If you already cloned without `--recurse-submodules`, fetch it after the fact:

```
git submodule update --init --recursive
```

This submodule (defined in `.gitmodules`) points to https://github.com/Swordfish90/LemuroidCores (branch `master`) and is checked out at `lemuroid-cores/`. It provides the `:bundled-cores` project (`lemuroid-cores/bundled-cores`) that `settings.gradle.kts` includes, plus the per-core projects (e.g. `:lemuroid_core_mgba`, `:lemuroid_core_snes9x`) used by the Play dynamic-feature build. Without it, Gradle sync fails immediately since those included projects won't resolve.

## 2. Local SDK location
Gradle needs to know where your Android SDK lives. Android Studio creates this automatically on first sync; if building from the command line, create `local.properties` in the project root (this file is gitignored, machine-specific, and must not be committed):

```
sdk.dir=/path/to/your/Android/sdk
```

## 3. Dependency repositories (no extra setup required)
All dependency repositories are already declared in the root `build.gradle.kts` under `allprojects { repositories { ... } }` — Gradle resolves everything automatically on first build, nothing to configure by hand:

- `google()` and `mavenCentral()` — standard Android/AndroidX/Kotlin dependencies
- `maven { setUrl("https://jitpack.io") }` — third-party libraries published straight from GitHub (no account/token needed, JitPack builds them from source on first request). See the table below for which libraries come from here.
- `mavenLocal()` — only relevant if you're developing one of the JitPack-sourced libraries locally and want to test against a `mavenLocal()`-published version instead of the published tag.

# Build Steps
1. Set ANDROID_HOME (if not already set)

export ANDROID_HOME=$HOME/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools

2. Build the APK
From the project root (./Lemuroid):


#### The "free bundle" variant bundles cores directly into the APK (no Play dynamic delivery)
`./gradlew :lemuroid-app:assembleFreeBundle`

#### Or for a debug build (faster, no signing required):
`./gradlew :lemuroid-app:assembleFreeBundleDebug`


The APK will be at:
`lemuroid-app/build/outputs/apk/freeBundle/debug/lemuroid-app-freeBundle-debug.apk`
Flavor explanation
The project has two dimensions:

free vs play — free is fully open-source, play uses Google Play dynamic features
bundle vs other — bundle includes all cores inside the APK; play uses dynamic feature modules
Use freeBundle for a self-contained sideloadable APK.

3. Install directly to a connected device (optional)

./gradlew :lemuroid-app:installFreeBundleDebug
Tips
First build will be slow — it downloads Gradle dependencies and compiles native cores
If you hit NDK errors, install NDK via Android Studio → SDK Manager → SDK Tools → NDK (Side by side)
Signing: debug builds use the debug.keystore already in the repo root

# Updating Cores / Changing the Cores Repository

The `.so` binaries for each Libretro core, and the Gradle modules that wrap them, live entirely in the `lemuroid-cores` submodule (the `LemuroidCores` repo) — not in this repo. Lemuroid only references it via `.gitmodules` and by including its projects in [settings.gradle.kts](settings.gradle.kts).

## Updating an existing core to the latest build
Cores are refreshed from libretro's nightly buildbot using [lemuroid-cores/update_cores.ipy](lemuroid-cores/update_cores.ipy), an **IPython** script (uses `!` shell-escape magic, so it must be run with `ipython`/Jupyter, not plain `python`). It also shells out to `wget` and `unzip`, so make sure those are installed.

1. `cd lemuroid-cores` (inside the submodule checkout)
2. Open `update_cores.ipy` and uncomment the core(s) you want to refresh in the `cores` list at the top (e.g. `"genesis_plus_gx"`, `"mgba"`, ...). Leave the rest commented out — the script re-downloads whatever is uncommented.
3. Run it: `ipython -i update_cores.ipy` (or execute it cell-by-cell in Jupyter)
4. For each uncommented core, it downloads the latest nightly `.so` from `https://buildbot.libretro.com/nightly/android/latest/<arch>/` for all four ABIs (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`), unzips it into `lemuroid_core_<name>/src/main/jniLibs/<arch>/`, and re-links the symlink under `bundled-cores/src/main/jniLibs/<arch>/` to point at the new binary.
5. Commit and push the changes **inside the `LemuroidCores` repo** (it's a separate git history from Lemuroid).
6. Back in the main `Lemuroid` repo, bump the submodule pointer to the new commit and commit that pointer update:
   ```
   cd lemuroid-cores && git pull origin master && cd ..
   git add lemuroid-cores
   git commit -m "Update lemuroid-cores submodule"
   ```

Adding a brand-new core (not just updating one) additionally requires registering its per-core project in [settings.gradle.kts](settings.gradle.kts)'s `usePlayDynamicFeatures()` block (for the Play dynamic-delivery build) and wiring it into the app's core/system configuration — the script only generates the module's `build.gradle.kts`/`AndroidManifest.xml` boilerplate and fetches the binary.

## Pointing at a different cores repository (e.g. your own fork)
The submodule URL/branch is declared in [.gitmodules](.gitmodules):

```
[submodule "lemuroid-cores"]
	path = lemuroid-cores
	url = https://github.com/Swordfish90/LemuroidCores
	branch = master
```

To repoint it at a fork or a different branch:

```
git submodule set-url lemuroid-cores https://github.com/<you>/LemuroidCores
git submodule set-branch --branch <your-branch> lemuroid-cores
git submodule sync
git submodule update --init --recursive --remote
git add .gitmodules lemuroid-cores
git commit -m "Point lemuroid-cores submodule at <your fork>"
```

No other file in this repo hardcodes the cores repository URL — `settings.gradle.kts` only references the local `lemuroid-cores/` checkout path, so it works unchanged regardless of which remote the submodule points to.

# Git Dependencies (via JitPack)

All resolved through https://jitpack.io (configured in build.gradle.kts).
Definitions are in buildSrc/src/main/java/deps.kt.

| Library | Package | Version |
|---------|---------|---------|
| LibretroDroid (github.com/Swordfish90/LibretroDroid) | com.github.Swordfish90:LibretroDroid | 0.13.2 |
| compose-settings ui-tiles (github.com/alorma/compose-settings) | com.github.alorma.compose-settings:ui-tiles | 2.1.0 |
| compose-settings ui-tiles-extended (github.com/alorma/compose-settings) | com.github.alorma.compose-settings:ui-tiles-extended | 2.1.0 |
| compose-settings storage-disk (github.com/alorma/compose-settings) | com.github.alorma:compose-settings-storage-disk | 2.0.0 |
| compose-settings storage-memory (github.com/alorma/compose-settings) | com.github.alorma:compose-settings-storage-memory | 2.0.0 |
| PadKit (github.com/Swordfish90/padkit) | io.github.swordfish90:padkit | 1.0.0-beta1 |