package io.hoffmeier.dhwff;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Everything DH-specific is done reflectively so that a DH internal rename breaks one lookup
 * instead of crashing the game. The only hard coupling to DH lives in the mixin annotation.
 */
public final class LiveBiomeLookup {

    private static final Pattern RESOURCE_LOCATION = Pattern.compile("([a-z0-9_.-]+):([a-z0-9_./-]+)");

    /** Per-class field probe results. index 0 = id source, 1 = biome wrapper, 2 = stale Biome. */
    private static final Map<Class<?>, Field[]> PROBE_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Method> SERIAL_METHOD_CACHE = new ConcurrentHashMap<>();

    private static Registry<Biome> cachedRegistry;
    private static Map<ResourceLocation, Biome> cachedById;

    private LiveBiomeLookup() {
    }

    /**
     * @param tintOverrider a DH TintWithoutLevelOverrider instance
     * @return the live registry Biome to use instead, or null if no substitution is needed
     */
    public static Biome resolve(Object tintOverrider) {
        try {
            Registry<Biome> registry = registry();
            if (registry == null) {
                return null;
            }

            Field[] probe = PROBE_CACHE.computeIfAbsent(tintOverrider.getClass(), LiveBiomeLookup::probe);

            Biome stale = probe[2] == null ? null : (Biome) probe[2].get(tintOverrider);
            if (stale != null && registry.getKey(stale) != null) {
                // Already the live instance -- DH's own path is correct, don't touch it.
                return null;
            }

            ResourceLocation id = readId(tintOverrider, probe);
            if (id == null) {
                return null;
            }

            Biome live = byId(registry).get(id);
            return live == stale ? null : live;
        } catch (Throwable t) {
            DhWhiteFoliageFix.LOGGER.debug("Biome re-resolve failed", t);
            return null;
        }
    }

    private static ResourceLocation readId(Object tintOverrider, Field[] probe) throws IllegalAccessException {
        // Preferred: DH already stores an identifier
        if (probe[0] != null) {
            Object raw = probe[0].get(tintOverrider);
            if (raw instanceof ResourceLocation rl) {
                return rl;
            }
            if (raw instanceof ResourceKey<?> key) {
                return key.location();
            }
        }

        // Fallback: pull it out of the BiomeWrapper's serial string
        if (probe[1] != null) {
            Object wrapper = probe[1].get(tintOverrider);
            if (wrapper != null) {
                Method serial = SERIAL_METHOD_CACHE.computeIfAbsent(wrapper.getClass(), LiveBiomeLookup::serialMethod);
                if (serial != null) {
                    Object value = serial.invoke(wrapper);
                    if (value instanceof String s) {
                        Matcher m = RESOURCE_LOCATION.matcher(s);
                        if (m.find()) {
                            return ResourceLocation.tryParse(m.group());
                        }
                    }
                }
            }
        }

        return null;
    }

    private static Field[] probe(Class<?> type) {
        Field[] found = new Field[3];
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                Class<?> ft = f.getType();
                if (found[0] == null && (ft == ResourceLocation.class || ft == ResourceKey.class)) {
                    f.setAccessible(true);
                    found[0] = f;
                } else if (found[1] == null && ft.getSimpleName().contains("BiomeWrapper")) {
                    f.setAccessible(true);
                    found[1] = f;
                } else if (found[2] == null && Biome.class.isAssignableFrom(ft)) {
                    f.setAccessible(true);
                    found[2] = f;
                }
            }
        }
        return found;
    }

    private static Method serialMethod(Class<?> wrapperType) {
        for (String name : new String[]{"getSerialString", "serialize", "toString"}) {
            try {
                Method m = wrapperType.getMethod(name);
                if (m.getReturnType() == String.class) {
                    m.setAccessible(true);
                    return m;
                }
            } catch (NoSuchMethodException ignored) {
                // try next
            }
        }
        return null;
    }

    /**
     * Reflective on purpose. This accessor was renamed registryOrThrow -> lookupOrThrow in 1.21.2
     * and there's no guarantee it held still through 1.26. Reflection means a rename costs us the
     * fix, not the game.
     */
    @SuppressWarnings("unchecked")
    private static Registry<Biome> registry() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return null;
        }

        Object access = mc.level.registryAccess();
        for (String name : new String[]{"lookupOrThrow", "registryOrThrow", "registry"}) {
            try {
                Method m = access.getClass().getMethod(name, ResourceKey.class);
                m.setAccessible(true);
                Object result = m.invoke(access, Registries.BIOME);
                if (result instanceof java.util.Optional<?> opt) {
                    result = opt.orElse(null);
                }
                if (result instanceof Registry<?> reg) {
                    return (Registry<Biome>) reg;
                }
            } catch (Throwable ignored) {
                // try next
            }
        }
        return null;
    }

    private static Map<ResourceLocation, Biome> byId(Registry<Biome> registry) {
        if (registry == cachedRegistry && cachedById != null) {
            return cachedById;
        }
        Map<ResourceLocation, Biome> map = new HashMap<>();
        for (Map.Entry<ResourceKey<Biome>, Biome> entry : registry.entrySet()) {
            map.put(entry.getKey().location(), entry.getValue());
        }
        cachedRegistry = registry;
        cachedById = map;
        return map;
    }
}
