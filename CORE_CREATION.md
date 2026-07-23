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


# Updating the cores in build
Quick one-liner (since .gitmodules already pins the submodule to branch = master):
```bash
git submodule update --remote --merge lemuroid-cores
git add lemuroid-cores
git commit -m "Bump lemuroid-cores submodule to latest"
git push
```
