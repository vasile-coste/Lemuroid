# Building AzaharPlus and integrating a new libretro core into Lemuroid

This document has two parts:

1. **How the AzaharPlus core was built** for this project (a concrete, worked example).
2. **The general checklist** for wiring any new libretro core into Lemuroid, using that example.

## Part 1 — Building the AzaharPlus libretro core for Android

[AzaharPlus](https://github.com/AzaharPlus/AzaharPlus) is a fork of the Citra/Azahar 3DS emulator
with extra features (ZipPass, cheats, amiibo generation, etc.). It already ships its own libretro
frontend at `src/citra_libretro/` (CMake target `azahar_libretro`), built by its own CI
(`.github/workflows/libretro.yml`) — only `arm64-v8a` is built for Android there, matching
Lemuroid's existing Citra core (`supportedOnlyArchitectures = setOf("arm64-v8a")`).

### 1. Install the toolchain

The CI pins `NDK 26.2.11394342` and `CMake 3.30.3`. Install both via `sdkmanager`:

```
sdkmanager --sdk_root="$ANDROID_HOME" "ndk;26.2.11394342" "cmake;3.30.3"
```

### 2. Fetch submodules

AzaharPlus vendors Boost, dynarmic, Vulkan headers, and dozens of other dependencies as git
submodules. A plain clone does not pull these in:

```
cd AzaharPlus
git submodule update --init --recursive
```

### 3. Configure and build

Mirrors the CI's `android` job exactly:

```
export ANDROID_HOME=$HOME/Library/Android/sdk
export NDK_ROOT=$ANDROID_HOME/ndk/26.2.11394342
CMAKE=$ANDROID_HOME/cmake/3.30.3/bin/cmake

$CMAKE -DENABLE_LIBRETRO=ON -DANDROID_PLATFORM=android-21 \
  -DCMAKE_TOOLCHAIN_FILE=$NDK_ROOT/build/cmake/android.toolchain.cmake \
  -DANDROID_STL=c++_static -DANDROID_ABI=arm64-v8a . -B build/android-arm64-v8a

$CMAKE --build build/android-arm64-v8a --target azahar_libretro --config Release -j$(sysctl -n hw.ncpu)
```

`ENABLE_LIBRETRO=ON` forces Qt/SDL2/other incompatible options off automatically
(`CMakeLists.txt`'s `_LIBRETRO_INCOMPATIBLE_OPTIONS` handling) — no other flags are needed.

Output: `build/android-arm64-v8a/bin/Release/azahar_libretro_android.so` (~448MB unstripped).

### 4. Strip the binary

Prebuilt libretro cores ship stripped; match that:

```
$NDK_ROOT/toolchains/llvm/prebuilt/darwin-x86_64/bin/llvm-strip --strip-unneeded \
  build/android-arm64-v8a/bin/Release/azahar_libretro_android.so
```

This brought the binary down from ~448MB to ~42MB (comparable to the existing Citra core's ~32MB).

### 5. Source patches required

Building for this specific NDK r26c / CMake 3.30.3 combination surfaced four small bugs in
AzaharPlus's own libretro/Android wiring — none of these are Lemuroid-specific, they're gaps in
upstream's own build coverage (their CI may use a different toolchain version that masks them, or
these code paths are simply undertested for the libretro target). Full diff:

```diff
diff --git a/src/citra_libretro/CMakeLists.txt b/src/citra_libretro/CMakeLists.txt
@@ -41,11 +41,13 @@ target_link_libraries(citra_core PRIVATE libretro)
 target_link_libraries(video_core PRIVATE libretro)
 target_link_libraries(audio_core PRIVATE libretro)
 target_link_libraries(input_common PRIVATE libretro)
+target_link_libraries(network PRIVATE libretro)
 target_compile_definitions(citra_common PRIVATE HAVE_LIBRETRO)
 target_compile_definitions(citra_core PRIVATE HAVE_LIBRETRO)
 target_compile_definitions(video_core PRIVATE HAVE_LIBRETRO)
 target_compile_definitions(audio_core PRIVATE HAVE_LIBRETRO)
 target_compile_definitions(input_common PRIVATE HAVE_LIBRETRO)
+target_compile_definitions(network PRIVATE HAVE_LIBRETRO)

diff --git a/src/common/android_utils.h b/src/common/android_utils.h
@@ -76,8 +76,8 @@ enum class AndroidOpenMode {
 class AndroidBuildFlavors {
 public:
-    static constexpr std::string GOOGLEPLAY = "googlePlay";
-    static constexpr std::string VANILLA = "vanilla";
+    static const inline std::string GOOGLEPLAY = "googlePlay";
+    static const inline std::string VANILLA = "vanilla";
 };

diff --git a/src/core/zip_pass.cpp b/src/core/zip_pass.cpp
@@ -7,6 +7,9 @@
 #include "core/hle/kernel/shared_page.h"
 #include <cryptopp/osrng.h>
 #include "core/system_titles.h"
+#ifdef ANDROID
+#include "common/android_utils.h"
+#endif
@@ -46,7 +49,7 @@ int exportZipPass(std::string path)
 				std::string real_name = directory + DIR_SEP + v_name;
-#ifdef ANDROID
+#if defined(ANDROID) && !defined(HAVE_LIBRETRO)
 				real_name = AndroidUtils::TranslateFilePath(real_name);
 #endif
@@ -415,8 +418,8 @@ int importQueuedZipPass()
 			std::string zip_path = file;
-
-#ifdef ANDROID
+#if defined(ANDROID) && !defined(HAVE_LIBRETRO)
 			zip_path = AndroidUtils::TranslateFilePath(file);
 #endif

diff --git a/src/network/room_member.cpp b/src/network/room_member.cpp
@@ -451,7 +451,7 @@ void RoomMember::RoomMemberImpl::HandleAzaharPlusPecificPacket(const ENetEvent*
 			std::string zip_path = path;
-#ifdef ANDROID
+#if defined(ANDROID) && !defined(HAVE_LIBRETRO)
 			zip_path = AndroidUtils::TranslateFilePath(path);
 #endif
@@ -536,7 +536,7 @@ void RoomMember::RoomMemberImpl::HandleAzaharPlusPecificPacket(const ENetEvent*
 					std::string zip_path = path;
-#ifdef ANDROID
+#if defined(ANDROID) && !defined(HAVE_LIBRETRO)
 						zip_path = AndroidUtils::TranslateFilePath(path);
 #endif
```

**Why these were needed:** `android_utils.cpp`/`.h` (the JNI-backed Android Storage Access
Framework layer) is deliberately excluded from libretro builds —
`src/common/CMakeLists.txt` has `if (ANDROID AND NOT ENABLE_LIBRETRO)` around it, since a libretro
core has no JNI/SAF context of its own (Lemuroid's `SystemCoreConfig.supportsLibretroVFS = true`
means the frontend already hands the core plain native paths). But the fork's newer ZipPass
StreetPass-exchange feature (`zip_pass.cpp`, `room_member.cpp`) calls
`AndroidUtils::TranslateFilePath(...)` under a bare `#ifdef ANDROID` with no `ENABLE_LIBRETRO`
check, so it references a symbol that simply isn't compiled in for this build. The fix skips path
translation for libretro builds (paths are already native there) rather than trying to compile in
the JNI-dependent Android storage layer. The `network` target was also missing both the
`HAVE_LIBRETRO` define and the `libretro` link dependency that every sibling target
(`citra_common`, `citra_core`, `video_core`, `audio_core`, `input_common`) already had — without
it, the guard above can't even see `HAVE_LIBRETRO` is set, and `libretro.h` isn't on the include
path once it can.

If AzaharPlus upstream fixes these, this patch step can be dropped; until then, reapply it after
pulling upstream changes (`git diff` above is the exact reference).

### Cheat support (AzaharPlus only, not plain Citra)

`src/citra_libretro/citra_libretro.cpp`'s `retro_cheat_reset()`/`retro_cheat_set()` were empty
no-op stubs (confirmed by reading the source, then re-confirmed on the built `.so` via
`llvm-nm -D | grep retro_cheat` before and after the patch). Citra's own cheat engine
(`src/core/cheats/cheats.h`'s `Cheats::CheatEngine`, driven by `Cheats::GatewayCheat`) already
exists and is wired into every frontend automatically — `Core::System`'s generic load path calls
`cheat_engine.LoadCheatFile(title_id)` / `cheat_engine.Connect(process->process_id)` for any
frontend, libretro included — it just was never connected to the libretro cheat API. Patch:

```diff
+#include "core/cheats/cheats.h"
+#include "core/cheats/gateway_cheat.h"
...
-void retro_cheat_reset() {}
+void retro_cheat_reset() {
+    auto& system = Core::System::GetInstance();
+    if (!system.IsPoweredOn()) return;
+    auto& cheat_engine = system.CheatEngine();
+    while (!cheat_engine.GetCheats().empty()) {
+        cheat_engine.RemoveCheat(0);
+    }
+}

-void retro_cheat_set(unsigned index, bool enabled, const char* code) {}
+void retro_cheat_set(unsigned index, bool enabled, const char* code) {
+    auto& system = Core::System::GetInstance();
+    if (!system.IsPoweredOn()) return;
+    auto cheat = std::make_shared<Cheats::GatewayCheat>(
+        "Cheat " + std::to_string(index), std::string(code), std::string());
+    cheat->SetEnabled(enabled);
+    system.CheatEngine().AddCheat(std::move(cheat));
+}
```

Cheat code format is Citra's own **Gateway** format: one or more lines of `XXXXXXXX YYYYYYYY`
(8 hex digits, space, 8 hex digits) per cheat — parsed by `Cheats::GatewayCheat`'s string
constructor, which splits on `\n` and validates each line is exactly 17 characters.

This is **not** something the pre-existing bundled Citra core (`lemuroid_core_citra`) gets for
free — that's a different upstream binary (from the official libretro buildbot) whose
`retro_cheat_set`/`reset` are presumably still stubs too (not verified, out of scope — we don't
have its source). Lemuroid's `BaseGameActivity.kt` therefore gates 3DS cheat availability on the
**selected core**, not just the system:
`system.id == SystemID.NINTENDO_3DS && systemCoreConfig.coreID == CoreID.AZAHARPLUS`. This is the
first system where the cheats guard needed to be core-aware rather than system-aware — see
`cheats_feature` project notes for the general pattern.

## Part 2 — Integrating a new libretro core into Lemuroid (general checklist)

Once you have a core's `.so` (built as above, or fetched from a buildbot/release), wiring it into
Lemuroid follows the same pattern for every core. Below, `<name>` is the `CoreID.coreName` (e.g.
`azaharplus`), and everything happens inside the `lemuroid-cores` git submodule except steps 6-8.

1. **Create `lemuroid-cores/lemuroid_core_<name>/`**, mirroring an existing single-ABI core module
   (e.g. `lemuroid_core_citra`):
   - `build.gradle.kts` — `com.android.dynamic-feature` plugin,
     `namespace = "com.swordfish.lemuroid.core.<name>"`,
     `missingDimensionStrategy("opensource", "play")` / `("cores", "dynamic")`,
     `doNotStrip("*/*/*_libretro_android.so")`, depends on `project(":lemuroid-app")`.
   - `src/main/AndroidManifest.xml` — dynamic-feature manifest template,
     `dist:title="@string/core_name_<name>"`.
   - `src/main/jniLibs/<abi>/lib<name>_libretro_android.so` for every ABI you actually support; for
     ABIs you don't (e.g. 32-bit or x86 for a heavy/64-bit-only core), still create an **empty
     0-byte placeholder** file at that path so the module's per-ABI packaging stays consistent —
     it's gated at runtime by `supportedOnlyArchitectures`, not by the file's absence.

2. **Register the module**:
   - `settings.gradle.kts`: add `":lemuroid_core_<name>"` to the `include(...)` list, and
     `project(":lemuroid_core_<name>").projectDir = File("lemuroid-cores/lemuroid_core_<name>")` —
     inside the `usePlayDynamicFeatures()` block if the core is meant for Play's dynamic delivery.
   - `lemuroid-app/build.gradle.kts`: add `":lemuroid_core_<name>"` to `dynamicFeatures.addAll(...)`.

3. **Add `bundled-cores` symlinks** (for the "bundle" flavor, where every core ships inside the
   APK): in `lemuroid-cores/bundled-cores/src/main/jniLibs/<abi>/`, symlink
   `lib<name>_libretro_android.so -> ../../../../../lemuroid_core_<name>/src/main/jniLibs/<abi>/lib<name>_libretro_android.so`
   for every ABI (including the empty placeholders).

4. **Add a `core_names.xml` string**: `lemuroid-app/src/main/res/values/core_names.xml` —
   `<string name="core_name_<name>" translatable="false"><name></string>` (referenced by the
   dynamic-feature manifest's `dist:title`).

5. **Add a `CoreID` enum entry**:
   `retrograde-app-shared/.../lib/library/CoreID.kt` —
   ```kotlin
   MY_CORE(
       "<name>",
       "<Display Name>",
       "lib<name>_libretro_android.so",
   ),
   ```

6. **Add or extend a `SystemCoreConfig`** in `retrograde-app-shared/.../lib/library/GameSystem.kt`:
   - New system: add one `SystemCoreConfig(CoreID.MY_CORE, ...)` to that system's `GameSystem(...)`
     entry.
   - Alternative core for an existing system (this project's AzaharPlus case): add a **second**
     `SystemCoreConfig` to that system's config list. Order matters — `systemCoreConfigs.first()`
     is the fallback default (see `CoresSelection.getDefaultCoreForSystem`), so put whichever core
     should stay default first.
   - Set `controllerConfigs`, `defaultSettings`/`exposedSettings` (the libretro core-option
     variable names — check the core's own source for its actual variable prefix before assuming
     compatibility with a sibling core), `statesSupported`, `supportsLibretroVFS`, and
     `supportedOnlyArchitectures` to match what the core actually supports.
   - If the new core's save/state format is **incompatible** with an existing alternative for the
     same system, add a migration handler analogous to
     `retrograde-app-shared/.../lib/migration/DesmumeMigrationHandler.kt` and wire it into
     `CoresSelection.getDefaultCoreForSystem`. Not needed when the cores share the same
     frontend/save conventions (e.g. AzaharPlus + Citra both use `citra_libretro`'s conventions).

7. **Nothing else needs core-specific code.** `CoresSelection`, `CoreUpdateWork`, the mobile
   `CoresSelectionScreen`/`CoresSelectionViewModel`, and the TV `CoresSelectionPreferences` already
   resolve/list/download cores generically from `GameSystem.all()` / `systemCoreConfigs` — any
   system with 2+ configs automatically becomes switchable in the "Change Cores" settings screen,
   and (as of this change) every system appears there for visibility even with just one core.

8. **Commit order**: everything under `lemuroid-cores/` is a separate git repository (the
   `LemuroidCores` submodule) — commit there first, then bump the submodule pointer in the main
   `Lemuroid` repo. See `BUILD.md`'s "Updating Cores / Changing the Cores Repository" section for
   the full submodule commit/push flow, and for how to point the submodule at a fork if you need to
   publish your own core outside the upstream `LemuroidCores` repo (relevant for the "free" flavor,
   whose `CoreUpdaterImpl` downloads cores from `github.com/Swordfish90/LemuroidCores` by
   convention).

9. **Verify**: `./gradlew :lemuroid-app:assembleFreeBundleDebug`, then install and check
   Settings → Change Cores lists/switches correctly, and that a game actually boots with the new
   core selected.

## Part 3 — Second worked example: SameBoy (Game Boy / Game Boy Color) — currently REMOVED

**Status: reverted from the app.** After the audio sample-rate fix below, on-device testing found
SameBoy still unstable and failing to load some ROMs — likely more assumptions in its libretro port
that don't hold for a direct (non-RetroArch) Android host like LibretroDroid, beyond just the audio
rate. All Gradle/Kotlin wiring (`CoreID.SAMEBOY`, the GB/GBC `SystemCoreConfig` entries, the
`lemuroid_core_sameboy` module registration in `settings.gradle.kts`/`lemuroid-app/build.gradle.kts`,
the `bundled-cores` symlinks, and the `core_names.xml`/`strings.xml` entries) has been removed from
the app. The built `.so` files and the `lemuroid_core_sameboy` module directory itself were left in
place under `lemuroid-cores/` (just unregistered, so Gradle no longer sees them) in case this is
revisited — the section below is kept as a build reference, not a description of current behavior.

[SameBoy](https://github.com/LIJI32/SameBoy) is a second alternative core added the same way, for
`SystemID.GB` and `SystemID.GBC` alongside the existing Gambatte core (Gambatte stays first/default
in both). It's a good contrast case to Part 1: a small, pure-C core using the classic NDK
`ndk-build`/`Android.mk` build system instead of CMake, and it builds cleanly for **all four ABIs**
(`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`) — no `supportedOnlyArchitectures` restriction needed,
unlike the arm64-only AzaharPlus core.

### Build steps

1. **Install `rgbds`** (`brew install rgbds`) — SameBoy ships its own open-source Game Boy boot
   ROMs (`BootROMs/*.asm`), assembled with the Game Boy dev toolchain rather than using Nintendo's
   copyrighted ones. No external BIOS files are needed at runtime; the boot ROMs get compiled
   directly into the core binary as byte arrays.
2. **Build the boot ROMs first** — required before the libretro build, and not triggered
   automatically by `ndk-build`:
   ```
   cd SameBoy
   make bootroms
   ```
   This produces `build/bin/BootROMs/*.bin` (7 files: `dmg_boot`, `cgb_boot`, `cgb0_boot`,
   `mgb_boot`, `agb_boot`, `sgb_boot`, `sgb2_boot`).
3. **Build the libretro core** via `ndk-build` (SameBoy's own `libretro/jni/Android.mk` +
   `Application.mk`, not CMake):
   ```
   cd SameBoy/libretro
   $ANDROID_HOME/ndk/26.2.11394342/ndk-build \
     NDK_PROJECT_PATH="$(pwd)" APP_BUILD_SCRIPT=jni/Android.mk NDK_APPLICATION_MK=jni/Application.mk \
     BOOTROMS_DIR="$(pwd)/../build/bin/BootROMs" \
     -j$(sysctl -n hw.ncpu)
   ```
   Output: `libs/<abi>/libretro.so` for all four ABIs (~200-300KB each — much smaller than
   AzaharPlus, as expected for a lightweight GB/GBC core with no 3D GPU emulation).
4. **Strip and rename** each ABI's `.so` to `libsameboy_libretro_android.so`, same as Part 1.

### Source patch required: `libretro/jni/Android.mk`

```diff
 LOCAL_PATH := $(call my-dir)

-CORE_DIR := $(LOCAL_PATH)/../..
+CORE_DIR := $(abspath $(LOCAL_PATH)/../..)

 ...
-override BOOTROMS_DIR := $(shell cd ../.. && realpath -m $(BOOTROMS_DIR))
+override BOOTROMS_DIR := $(abspath $(BOOTROMS_DIR))
```

**Why:** `CORE_DIR := $(LOCAL_PATH)/../..` stays a *relative* string (e.g. `"jni/../.."`) rather
than an absolute path. The Android NDK build system automatically prepends `$(LOCAL_PATH)/` to
every entry in `LOCAL_SRC_FILES` unless that entry is already absolute — since `SOURCES_C`
(from `Makefile.common`) builds each source path as `$(CORE_DIR)/Core/gb.c`, a relative `CORE_DIR`
means every source ends up double-prefixed (`jni/jni/../../Core/gb.c`), which doesn't resolve to
any real file and fails with `No rule to make target`. Using Make's built-in `$(abspath ...)`
(needs no external binary) makes `CORE_DIR` absolute, which both resolves correctly on its own and
stops the NDK's auto-prefixing from ever triggering. The second line's `realpath -m` also isn't
available on macOS's BSD `realpath` (only GNU's) — `abspath` sidesteps that portability problem too
and doesn't require the boot ROMs directory to already exist.

### GB/GBC-specific integration notes

- SameBoy's libretro core options use a completely different variable prefix (`sameboy_*`) than
  Gambatte's (`gambatte_*`) — confirmed via `SameBoy/libretro/libretro_core_options.inc` — so,
  unlike AzaharPlus/Citra sharing `citra_*` variables, the `SystemCoreConfig` for SameBoy needed its
  own `exposedSettings`/`defaultSettings` written from scratch rather than copied from the sibling
  core.
- No migration handler was added for save-file compatibility between Gambatte and SameBoy — Game
  Boy battery saves are raw cartridge SRAM dumps (a hardware-defined format, not a core-specific
  container), so they're expected to carry over between cores for the same ROM. This mirrors the
  same assumption made for Citra/AzaharPlus, not independently verified per game/mapper.

### Runtime crash found on-device: audio sample rate

Launching a GBC ROM with SameBoy crashed the game process immediately (`SIGSEGV`/`SEGV_ACCERR`,
confirmed via `adb logcat`/tombstone). The backtrace pointed entirely inside Lemuroid's own
`liblibretrodroid.so`, not SameBoy:

```
#00 libc.so (__memset_aarch64_nt)
#01 liblibretrodroid.so (oboe::FifoBuffer::readNow)
#02 liblibretrodroid.so (libretrodroid::Audio::onAudioReady)
```

Root cause: `libretro/libretro.c`'s `init_for_current_model()` sets the APU's output rate to
`GB_get_clock_rate(...) / 2` (~2.1 MHz) on every platform except Wii U, which already special-cases
a normal `WIIU_SAMPLE_RATE` (48000) instead. The extreme rate is meant for frontends layered on
RetroArch's own audio resampler — it downsamples whatever a core reports to the actual output
device rate. LibretroDroid has no such resampler: it sizes its Oboe/AAudio audio FIFO buffer
assuming a normal rate (tens of kHz), so real audio data arriving at ~2 MHz overflows it almost
instantly.

Fix — added an `ANDROID`-specific branch alongside the existing `WIIU` one, using the same fixed
48000 Hz rate:

```diff
-#ifdef WIIU
+#if defined(WIIU)
     GB_set_sample_rate(&gameboy[i], WIIU_SAMPLE_RATE);
+#elif defined(ANDROID)
+    GB_set_sample_rate(&gameboy[i], 48000);
 #else
     GB_set_sample_rate(&gameboy[i], GB_get_clock_rate(&gameboy[i]) / 2);
 #endif
```

General takeaway for future cores: if a core targets RetroArch's audio pipeline by default,
check whether it reports a "convenience" native rate expecting the frontend to resample — verify
via `retro_get_system_av_info`'s `timing.sample_rate` (or the equivalent core-specific setter) and
compare against what a normal audio device rate looks like (8kHz-192kHz range) before assuming a
direct crash means the *frontend* is broken.

# Updating the cores in build
Quick one-liner (since .gitmodules already pins the submodule to branch = master):

git submodule update --remote --merge lemuroid-cores
git add lemuroid-cores
git commit -m "Bump lemuroid-cores submodule to latest"
git push


Manual/inspect-first (what we did earlier when I found the AzaharPlus core was missing):

cd lemuroid-cores
git fetch origin
git log HEAD..origin/master --oneline   # see what's new before taking it
git checkout origin/master              # or: git merge origin/master
cd ..
git status                                # confirms "lemuroid-cores (new commits)"
git add lemuroid-cores
git commit -m "Bump lemuroid-cores submodule to include <whatever changed>"
git push
