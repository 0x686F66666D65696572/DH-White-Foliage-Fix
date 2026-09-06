package io.hoffmeier.dhwff.mixin;

import io.hoffmeier.dhwff.LiveBiomeLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Variant for DH builds that ship the class as:
 *   loaderCommon.neoforge.com.seibel.distanthorizons.common.wrappers.block.TintWithoutLevelOverrider
 *
 * Only one of the variants in this package is ever registered -- DhwffMixinPlugin probes the
 * classpath at config time and returns whichever target actually exists in the installed DH jar.
 */
@Mixin(targets = "loaderCommon.neoforge.com.seibel.distanthorizons.common.wrappers.block.TintWithoutLevelOverrider", remap = false)
public abstract class TintOverriderNeoForgeMixin {

    @Unique
    private Biome dhwff$liveBiome;

    @Unique
    private boolean dhwff$resolved;

    @Inject(
            method = "getBlockTint(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/ColorResolver;)I",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void dhwff$useLiveRegistryBiome(BlockPos pos, ColorResolver resolver, CallbackInfoReturnable<Integer> cir) {
        if (!this.dhwff$resolved) {
            this.dhwff$liveBiome = LiveBiomeLookup.resolve(this);
            this.dhwff$resolved = true;
        }
        Biome live = this.dhwff$liveBiome;
        if (live != null) {
            cir.setReturnValue(resolver.getColor(live, pos.getX(), pos.getZ()));
        }
    }
}
