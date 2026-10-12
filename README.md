# mcopt for Minecraft 1.8.9

This branch is the 1.8.9 port of mcopt. Everything is in the mod and on by default: install the jar and play. There are
no JVM flags to add and nothing in the launcher to change.

**Early alpha:** expect bugs, and back up your worlds.

## Requirements

- Minecraft 1.8.9 with the Legacy Fabric loader (Fabric Loader 0.16 or newer)
- Java 8 or newer (what the launcher ships for 1.8.9 works)
- A Mac, ideally Apple Silicon. The Metal renderer and the native world generation are macOS only.

## Install

1. Install Legacy Fabric for 1.8.9: https://legacyfabric.net/ (or in Prism Launcher: new instance, 1.8.9, loader Legacy
   Fabric).
2. Download the mcopt 1.8.9 jar from [Releases](../../releases).
3. Put it in the `mods` folder (`~/Library/Application Support/minecraft/mods` on a Mac; in Prism: Edit the instance >
   Mods > Add file).
4. Start Minecraft with the Fabric profile.

## Numbers

fps spinning, same test world, render distance 16, VSync off, Max Framerate unlimited, Fancy graphics, VBOs on. Two
runs each:

| Mac | Window | vanilla | mcopt, Metal off | mcopt, Metal on |
|---|---|---|---|---|
| MacBook Air M5 (10-core GPU, 24 GB) | fullscreen (1470x956) | 214-242 / 1% low 133-143 | 250-312 / 1% low 123-173 | 346-500 / 1% low 209-274 |
| MacBook Air M5 (10-core GPU, 24 GB) | windowed (854x480) | 213-250 / 1% low 127-148 | 277-280 / 1% low 155-160 | 519-526 / 1% low 268-273 |

"vanilla" is the unmodified game, measured by the same bench with every other part of the mod left out
(`-Dmcopt.vanilla=true`). "Metal off" is this mod with `metal=false`: the OpenGL path, with every other change below
still on. The Air has no fan,
so its GPU clock moves between runs. The second fullscreen pair (250 and 346) ran slower for both setups.

## Changes in this fork

These changes are by dysnasia and are on by default. To turn one off, set its key to `false` in `config/mcopt.properties`.
The first launch writes that file with every key and a comment for each.

### Metal terrain

The solid and cutout terrain layers (everything but water, ice, stained glass and slime) are drawn with Metal instead of
OpenGL. On a Mac, OpenGL runs on top of Metal, so this removes the translation layer for most of the frame's geometry.
Finished chunk meshes go into large Metal buffers. The GPU culls hidden chunk pieces itself against a depth pyramid from
the previous frame's visible set, and draws the rest with indirect draws. The result is copied into the game's frame
with its depth, so entities, water, particles and the hand draw over it as before. Off switch: `metal=false`.

### Faster rendering on the OpenGL side

- Chunk sections draw as indexed triangles instead of quads, which the macOS driver otherwise converts on the CPU
  every frame.
- The visibility walk over the chunk grid (setupTerrain) runs over flat arrays instead of allocating per section.
- The entity pass looks up each chunk column once per frame instead of once per section.
- More chunk-mesh worker threads, matched to the core count, and the workers are kept fed every frame.
- Fancy clouds are built once and reused until they scroll a whole cell or change color, instead of being rebuilt from
  4,000 quads in 128 draws every frame. They look the same.

### Faster world generation

The improved Perlin noise behind every overworld, nether and end density octave runs in native code instead of Java. The
library is built for both Apple Silicon and Intel Macs and ships inside the jar.

The blocks come out the same. At startup the mod checks the native code against the game's own on random inputs and
falls back to Java if anything differs. In testing, 9.4 million checked noise values showed 0 differences, and the terrain
hash of 600 spawn chunks was the same with native noise, Java noise, and the two mixed chunk by chunk.

Each overworld terrain chunk takes 5 to 11% less time (1.24 to 1.31 ms against 1.38 to 1.40 ms). That's from 600 spawn
chunks per run on a dedicated server, where every chunk got one of the two noises at random in the same process. 1.8.9
generates chunks on the server thread, so this time comes straight off world loading. Off switch: `nativeNoise=false`.

### FPS counter

Shows `NN fps` in the top-left corner while you play. It hides with the HUD (F1) and while F3 is open, since F3 shows
its own counter. On a Mac keyboard, use fn + F1 and fn + F3. Off switch: `fpsHud=false`.

## For developers

- `-Dmcopt.fps.bench=true` runs the fps bench behind the numbers above; with `-Dmcopt.vanilla=true` it measures the
  unmodified game.
- `./gradlew build` builds the jar. On a Mac it also builds the native library with clang (`-ffp-contract=off`, no
  fast-math, which bit-exactness requires).
- `-Dmcopt.noise.verify=true` runs both the native and the Java noise on every call and logs the mismatch count.
- `-Dmcopt.gen.bench=true` times each overworld chunk with native or Java noise picked at random, and logs both means
  and a terrain hash that must stay the same from run to run.

## Licence and credits

[Apache License 2.0](LICENSE). Keep the [NOTICE](NOTICE) file with any copy, and credit mcopt.
