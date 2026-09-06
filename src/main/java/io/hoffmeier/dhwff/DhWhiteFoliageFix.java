package io.hoffmeier.dhwff;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(value = DhWhiteFoliageFix.MOD_ID, dist = Dist.CLIENT)
public class DhWhiteFoliageFix {

    public static final String MOD_ID = "dhwff";
    public static final Logger LOGGER = LoggerFactory.getLogger("DHWhiteFoliageFix");

    public DhWhiteFoliageFix() {
        LOGGER.info("DH white foliage fix loaded");
    }
}
