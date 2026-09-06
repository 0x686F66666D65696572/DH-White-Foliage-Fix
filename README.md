# DH White Foliage Fix (NeoForge port)

Port of JayemCeekay's `dhpolytonecompat` (Fabric 1.20.1) to NeoForge, targeting MC 1.26.2 /
NeoForge 26.2.0.79. Client-side only.

## Getting a jar

I can't compile this for you — my sandbox has no network route to `maven.neoforged.net` or
`libraries.minecraft.net`, so there's no Minecraft toolchain to build against. Pick whichever of
these is less annoying.

### Option A — let GitHub build it (no local setup)

1. Make a new repo, drop this folder's contents in, push.
2. `.github/workflows/build.yml` runs on push.
3. Actions tab -> latest run -> download the `dh-white-foliage-fix` artifact.
4. Unzip, drop the jar in `mods/`.

### Option B — local

Needs JDK 21 on PATH. From this folder:

    gradle build

Jar lands in `build/libs/`.

## What it does

Distant Horizons builds LODs through a fake `BlockAndTintGetter` called
`TintWithoutLevelOverrider`, which holds a `Biome` instance captured at LOD build time. After a
world reload, dimension change, or server reconnect, the biome registry is rebuilt and that
captured instance is no longer the object sitting in the live registry.

Vanilla's `ColorResolver` implementations don't care — they read fields straight off the `Biome`.
Mods that resolve color by looking the biome back up in the registry get `null` and fall through
to `0xFFFFFFFF`. That's the white foliage.

This intercepts `getBlockTint`, maps the stale biome back to the live registry instance by
resource location, and hands the resolver that one instead.

## If the first build fails

Two things I couldn't verify without a DH jar and a 1.26.2 toolchain, and how each is handled:

**DH's package path.** DH relocates shared code into a per-loader package and the prefix has
moved between releases. `DhwffMixinPlugin` probes for three known variants at load time and
registers whichever exists. If none match it logs a warning and does nothing rather than
crashing. If you see that warning, run this and send me the output:

    unzip -o DistantHorizons-*.jar -d dh && find dh -name '*.jar' -exec unzip -l {} \; | grep -i tintwithout

**Minecraft API drift.** Registry access is done reflectively with fallbacks, so a rename there
costs the fix rather than the game. The two calls compiled against MC directly are
`ColorResolver.getColor(Biome, double, double)` and the `getBlockTint` descriptor in the mixin
annotation. If either moved in 1.26.2, the build fails with a compile error naming the exact
line — paste it to me and it's a one-line change.

## Before you bother: check this is actually your bug

Two different problems look identical at distance.

- **All leaves white including vanilla oak, wrong only after a reload or reconnect** — stale
  biome instance. This is the fix.
- **Only specific modded leaves white, wrong from the moment the LOD builds, correct up close** —
  `tintindex` problem. DH averages one texture from the block model, and on two-layer leaf models
  it can grab the untinted overlay. No mixin fixes that; it needs a resource pack override for
  the offending models. Overgrowth, Croptopia, and Abyssal Ocean all add custom leaves.

Your mod list has no Polytone, which is the usual trigger for the first case.
