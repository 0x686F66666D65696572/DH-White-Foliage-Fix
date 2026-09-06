package io.hoffmeier.dhwff;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Distant Horizons relocates its shared code into a per-loader package, and the exact prefix has
 * moved between releases. Rather than hardcode one guess and crash when it's wrong, probe the
 * classpath and register only the variant whose target class is actually present.
 *
 * Probing goes through the Mixin bytecode provider rather than Class.forName, because at config
 * time other mods' classes aren't loadable yet.
 */
public class DhwffMixinPlugin implements IMixinConfigPlugin {

    /** mixin class (simple name) -> target class it needs. First match wins. */
    private static final Map<String, String> VARIANTS = new LinkedHashMap<>();

    static {
        VARIANTS.put("TintOverriderNeoForgeMixin",
                "loaderCommon.neoforge.com.seibel.distanthorizons.common.wrappers.block.TintWithoutLevelOverrider");
        VARIANTS.put("TintOverriderFabricMixin",
                "loaderCommon.fabric.com.seibel.distanthorizons.common.wrappers.block.TintWithoutLevelOverrider");
        VARIANTS.put("TintOverriderCommonMixin",
                "com.seibel.distanthorizons.common.wrappers.block.TintWithoutLevelOverrider");
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public List<String> getMixins() {
        List<String> selected = new ArrayList<>();
        for (Map.Entry<String, String> variant : VARIANTS.entrySet()) {
            if (exists(variant.getValue())) {
                DhWhiteFoliageFix.LOGGER.info("Patching DH tint lookup at {}", variant.getValue());
                selected.add(variant.getKey());
                break;
            }
        }
        if (selected.isEmpty()) {
            DhWhiteFoliageFix.LOGGER.warn(
                    "No known Distant Horizons TintWithoutLevelOverrider class found -- doing nothing. "
                            + "DH probably moved it; add the new package to DhwffMixinPlugin.");
        }
        return selected;
    }

    private static boolean exists(String className) {
        try {
            ClassNode node = MixinService.getService().getBytecodeProvider().getClassNode(className);
            return node != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
