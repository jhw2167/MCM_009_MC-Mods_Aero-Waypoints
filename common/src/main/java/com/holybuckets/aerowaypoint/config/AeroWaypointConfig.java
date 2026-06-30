package com.holybuckets.aerowaypoint.config;

import com.holybuckets.aerowaypoint.Constants;
import net.blay09.mods.balm.api.config.reflection.Comment;
import net.blay09.mods.balm.api.config.reflection.Config;


@Config(Constants.MOD_ID)
public class AeroWaypointConfig {

    @Comment("devMode==true disables portal spawns so the player can build and save new challenges")
    public boolean devMode = false;
    @Comment("Where the loot rules json configuration can be found. This file determines what loot is available in each level of pool")
    public String lootRulesConfig = "config/challengeTempleslootRules.json";


    public static class WaypointRendering {

        @Comment("Satellite will not operate below this y level")
        public int minSatelliteWorkingHeight = 256;
    }

    public WaypointRendering displayConfig = new WaypointRendering();
}