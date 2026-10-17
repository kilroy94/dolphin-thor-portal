## Dolphin Thor Portal

This is a simple fork of Dolphin that accomplishes three things:

1. Adds dual screen support for use on the AYN Thor
2. Adds a significantly improved user interface for the emulated Skylanders Portal of Power on that second screen
3. Adds a forced offline mode to the emulator settings, which makes Dolphin stop whining about not being able to read a particular SSL certificate when booting many games in the Skylanders series.

## Launching the Portal

1. Launch Dolphin like normal.
2. Launch a Skylanders title of your choosing.
3. Once the game has loaded, open Dolphin's menu (the back button on your device) and select "Lower-screen portal"
4. Dolphin will ask which display to use. There should only be one option, so pick that one. (In my experience, it's called "Screen 2 ID-4, but I haven't tested this on another Thor to confirm that that's consistent)
5. You'll see the main interface of the Portal on the lower screen now.

## Using the Portal

1. After following the above steps, tap on one of the slots you'd like to add a figure to.
    a. By default, the interface only shows four slots (i.e. a Skylander and an Item, for two players in co-op or arena). The Portal supports up to sixteen slots (presumably for Trap Team and Swap Force... but I haven't played that far). You can access those slots by tapping "All 16 Spots" and paging through.
2. You'll see that a new interface appears with two options: Existing Saves or Create new save. 
    a. If you already have Skylanders saved on your device, you'll immediately see them below and can simply tap one to select it. Then, hit "Place saved figure" to add it to the previously selected slot.
    b. If you have no saved Skylanders, or want to create a new Skylander, tap "Create new save," then find the figure you want to use. Tap it, then hit "Name & create." By default, the save file will be the name of the Skylander, followed by a sequential number if there are duplicates. You have the opportunity to name it something different if you want (i.e. the path you've chosen for that Skylander). Then, hit "Create & place" to proceed.
3. Once back on the main Portal screen, you can tap a placed figure to remove or replace it. Hitting "Replace" takes you to the previously described menus to pick a different figure to put on the Portal.

## File Management

Currently, all .sky files reside in Dolphin Thor Portal's folder on Android, in the "Skylanders" folder. This fork does nothing different from regular Dolphin in terms of the actual emulation itself, meaning you should be able to throw in any pre-existing .sky files into this folder and they'll just work. Similarly, if you want to delete a Skylander's file, just go into this folder and delete the relevant file.

## Offline Mode

For some reason, Skylanders and many other Activision titles for the Wii seem to try and connect to the internet (or at least check for an internet connection?) when launched. I suppose this isn't a problem on a real Wii, but Dolphin throws a real hissy fit about it. You'll get an error along the lines of "IOS: Could not read a file required for SSL services. Please refer to https://dolphin-emu.org/docs/guides/wii-network-guide/" Despite the wonderfully helpful network guide giving easy to follow steps and a single button supposedly solving the problem in Dolphin, the error persists. You could, of course, just hit "Ignore for session" and the game continues starting up like normal. But that's annoying! The following steps disable as much of Dolphin's emulated network stack as possible, which seems to make the popup go away. Unfortunately, Skylanders still takes ages to boot, but at least it doesn't hang indefinitely until you close the popup...

1. Open the Settings menu.
2. Open Config.
3. Open Wii.
4. Enable "Offline Wii Networking."

## AI Disclosure

This fork was entirely vibe coded with Codex. I am not a programmer in the slightest, but I was pretty unhappy with how poorly the UI was done for the emulated Portal on Android specifically. It looks and works pretty great on desktop, with its own dedicated window and all, but the default Android experience was simply untenable. It's literally a single giant dropdown menu! When my mom discovered that she could play one of her favorite games on the Thor, she was ecstatic, but was immediately frustrated by how Dolphin's interface handled arguably the most important part of Skylanders. I hated seeing her so disappointed, so I figured Codex could figure something out. And by golly, it did.


## Artwork

Skylanders card artwork comes from [Skylanders Image Generator](https://github.com/skylandersNFC/Skylanders-Image-Generator/tree/a8c0bde4d3bd89918f30daa5cb04afb28147b115).

- **Sobersu - Sobersu_Logo:** Spyro's Adventure, Giants and Trap Team.
- **Mr. Shadow - Mr_Shadow and related styles:** Swap Force, SuperChargers and Imaginators.

Original creator credits and the image inventory are preserved in [Assets/SkylandersArtwork](Assets/SkylandersArtwork). Artwork remains credited to its original creators and is separate from Dolphin's code license.

## Source

Based on [Dolphin Emulator](https://github.com/dolphin-emu/dolphin), release **2609a**, revision [`409881c34357d0f105fd473167d15ab0bd9c1628`](https://github.com/dolphin-emu/dolphin/commit/409881c34357d0f105fd473167d15ab0bd9c1628).

Dolphin source code is licensed under the **GNU General Public License, version 2 or later**. See [COPYING](COPYING) and the notices in individual source files.
