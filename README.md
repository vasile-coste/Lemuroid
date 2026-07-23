# Changes to this fork:
### Cheat support for:
- NES core, ex: [Game Genie format](https://gamegenie.com/cheats/nes/index.html)
- SNES core, ex: [Game Genie format](https://gamegenie.com/cheats/snes/index.html)
- GBA core(mGBA), *GameShark / Action Replay: XXXXXXXX XXXXXXXX (two 8-hex groups separated by space). Multiple codes can be chained with +*
- GBC and GB core(mGBA): *Game Genie (GB): XXX-XXX-XXX (e.g. 01A-23B-4CD) / GameShark: XXXXXXXX (8 hex chars)*
- 3DS for AzaharPlus core, the format is XXXX XXXX, you can find them [here](https://github.com/JourneyOver/CTRPF-AR-CHEAT-CODES/tree/master/Cheats)

### Added turbo buttons:
- NES core, Y -> turbo B, Z -> turbo A
- GBA, GBC and GB core, Y -> turbo B, Z -> turbo A

**Note 1:** turbo slider can be customized with 3 levels:
1. Slow(0)
2. Normal(0.5)
3. Fast(1)

**Note 2:** 
- Some games have a limit on how fast the turbo can be executed
- The settings can be found in the **quick game** menu in the **edit commands**[bellow fast forward button] where the buttons and pad size si set


### Core Updates:
1. added [AzaharPlus](https://github.com/AzaharPlus/AzaharPlus) as offers event more features for 3DS roms(cheats, save state, etc). Default is still citra. Note as the save is not compatible with citra.

Below are the minimum requirements to run AzaharPlus:
```
Operating System: Android 9.0+ (64-bit)
CPU: Snapdragon 835 SoC or better
GPU: OpenGL ES 3.2 or Vulkan 1.1 support
Memory: 2GB of RAM. 4GB is recommended
```
Tested on Samsung S25

Note: 3DS Citra and AzaharPlus only work with arm64-v8a


### Globals:
- Added .ips support for roms(ips patch needs to have the same name as the rom). ex: `game.nes` patch must be `game.ips`. To apply multiple patches just name them like this: `game.1.ips`, `game.2.ips`, etc. Note after adding the patches in the same folder as the rom you must rescan the roms 
- Added 1x, 2x, 3x and 4x fast forward speed 
- Ability to export/import saves. **Note**: *when importing on a slot (if a save was overwriten) the screen image will remain until another save is done on that slot.*
- fixed a Lemuroid bug where some **GBC** roms were mapped as **GB** roms
- added delete option when press and hold on a rom. Note this will also delete it from device. To activate this permission you need to go to settings an reselect your rom folder to be prompted with the permission to write, otherwise the file won't be deleted from device

### Notes:
- as i do not have a sign key the app will appear as LemuroidDebug

### Build the project instructions:
[Build info](/BUILD.md)


### Update Cores instructions:
[Cores update](/CORE_UPDATE.md)

<details>

<summary>Original Lemuroid documentation</summary>

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/com.swordfish.lemuroid/)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=com.swordfish.lemuroid)

## Description

Lemuroid is an open-source emulation project for Android based on Libretro. Its main goal is ease of use, good Android integration and a great user experience.

It originated from a rib of [Retrograde](https://github.com/retrograde/retrograde-android), but graduated to a standalone project integrating [LibretroDroid](https://github.com/Swordfish90/LibretroDroid).

|Screen 1|Screen 2|Screen 3|
|---|---|---|
|![Screen1](https://github.com/Swordfish90/Lemuroid/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/1.jpg)|![Screen2](https://github.com/Swordfish90/Lemuroid/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/2.jpg)|![Screen3](https://github.com/Swordfish90/Lemuroid/blob/master/fastlane/metadata/android/en-US/images/phoneScreenshots/3.jpg)|

### Supported Systems:
- Atari 2600 (A26) ([stella](https://docs.libretro.com/library/stella/))
- Atari 7800 (A78) ([prosystem](https://docs.libretro.com/library/prosystem/))
- Atari Lynx (Lynx) ([handy](https://docs.libretro.com/library/handy/))
- Nintendo (NES) ([fceumm](https://docs.libretro.com/library/fceumm/))
- Super Nintendo (SNES) ([snes9x](https://docs.libretro.com/library/snes9x/))
- Game Boy (GB) ([gambatte](https://docs.libretro.com/library/gambatte/))
- Game Boy Color (GBC) ([gambatte](https://docs.libretro.com/library/gambatte/))
- Game Boy Advance (GBA) ([mgba](https://docs.libretro.com/library/mgba/))
- Sega Genesis (aka Megadrive) ([genesis_plus_gx](https://docs.libretro.com/library/genesis_plus_gx/))
- Sega CD (aka Mega CD) ([genesis_plus_gx](https://docs.libretro.com/library/genesis_plus_gx/))
- Sega Master System (SMS) ([genesis_plus_gx](https://docs.libretro.com/library/genesis_plus_gx/))
- Sega Game Gear (GG) ([genesis_plus_gx](https://docs.libretro.com/library/genesis_plus_gx/))
- Nintendo 64 (N64) ([mupen64plus](https://docs.libretro.com/library/mupen64plus/))
- PlayStation (PSX) ([PCSX-ReARMed](https://docs.libretro.com/library/pcsx_rearmed/))
- PlayStation Portable (PSP) ([ppsspp](https://docs.libretro.com/library/ppsspp/))
- FinalBurn Neo (Arcade) ([fbneo](https://github.com/libretro/FBNeo/))
- Nintendo DS (NDS) ([desmume](https://docs.libretro.com/library/desmume/)/[MelonDS](https://docs.libretro.com/library/melonds/))
- NEC PC Engine (PCE) ([beetle_pce_fast](https://docs.libretro.com/library/beetle_pce_fast/))
- Neo Geo Pocket (NGP) ([mednafen_ngp](https://docs.libretro.com/library/beetle_neopop/))
- Neo Geo Pocket Color (NGC) ([mednafen_ngp](https://docs.libretro.com/library/beetle_neopop/))
- WonderSwan (WS) ([beetle_cygne](https://docs.libretro.com/library/beetle_cygne/))
- WonderSwan Color (WSC) ([beetle_cygne](https://docs.libretro.com/library/beetle_cygne/))
- Nintendo 3DS (3DS) ([citra](https://docs.libretro.com/library/citra/))

### Features:
- Android TV support
- Automatically save and restore game states.
- ROMs scanning and indexing
- Optimized touch controls
- Quick save/load
- Support for Zipped ROMs
- Display simulation (LCD/CRT)
- Gamepad support
- Local multiplayer
- Tilt input
- Customizable touch controls (size and position)
- Cloud save sync
- HD mode

### Languages:
You can help translate Lemuroid in your native language by going here: https://crowdin.com/project/lemuroid

</details>
