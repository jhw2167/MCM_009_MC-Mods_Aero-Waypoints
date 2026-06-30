package com.holybuckets.aerowaypoint.client.config;

import com.holybuckets.aerowaypoint.Constants;
import net.blay09.mods.balm.api.config.reflection.Comment;
import net.blay09.mods.balm.api.config.reflection.Config;

import java.util.Locale;


@Config(value = Constants.MOD_ID, type = "client")
public class AeroWaypointClientConfig {

    @Comment("devMode==true disables portal spawns so the player can build and save new challenges")
    public boolean devMode = false;

    public WaypointRendering waypointRendering = new WaypointRendering();

    public enum WaypointVisibility {
        ALWAYS, GOGGLES, CROUCH, CROUCH_TOGGLE, GOGGLES_AND_CROUCH
    }

    public enum TagVisibility {
        ALWAYS, GOGGLES, LOOKING, LOOKING_AND_GOGGLES
    }


    public static class WaypointRendering {

        @Comment("When waypoint beams are visible. Options: " +
            "ALWAYS, GOGGLES, CROUCH, CROUCH_TOGGLE, GOGGLES_AND_CROUCH")
        public String waypointVisibility = WaypointVisibility.ALWAYS.name();

        @Comment("When waypoint name-tag labels are visible. Options: " +
            "ALWAYS, GOGGLES, LOOKING, LOOKING_AND_GOGGLES")
        public String tagVisibility = TagVisibility.ALWAYS.name();

    }

    public WaypointVisibility getWaypointVisibility() {
        return parseOrDefault(waypointRendering.waypointVisibility,
            WaypointVisibility.class, WaypointVisibility.ALWAYS);
    }

    public TagVisibility getTagVisibility() {
        return parseOrDefault(waypointRendering.tagVisibility,
            TagVisibility.class, TagVisibility.ALWAYS);
    }

    private static <E extends Enum<E>> E parseOrDefault(String raw, Class<E> type, E fallback) {
        if (raw == null) return fallback;
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }



    public WaypointRendering displayConfig = new WaypointRendering();
}