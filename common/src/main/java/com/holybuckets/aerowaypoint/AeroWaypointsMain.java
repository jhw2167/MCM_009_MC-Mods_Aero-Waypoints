package com.holybuckets.aerowaypoint;


import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.holybuckets.aerowaypoint.core.ITrackedContrap;
import com.holybuckets.aerowaypoint.core.WaypointManager;
import com.holybuckets.foundation.GeneralConfig;
import com.holybuckets.foundation.HBUtil;
import com.holybuckets.foundation.event.EventRegistrar;
import com.holybuckets.aerowaypoint.config.AeroWaypointConfig;
import com.holybuckets.foundation.event.custom.PlayerInteractEvent;
import com.holybuckets.foundation.event.custom.SimpleMessageEvent;
import com.holybuckets.foundation.networking.SimpleStringMessage;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.event.EventPriority;
import net.blay09.mods.balm.api.event.server.ServerStartingEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

import  static  com.holybuckets.foundation.HBUtil.PlayerUtil;

/**
 * Main instance of the mod, initialize this class statically via commonClass
 * This class will init all major Manager instances and events for the mod
 */
public class AeroWaypointsMain {
    private static boolean DEV_MODE = false;;
    private static AeroWaypointConfig CONFIG;
    public static AeroWaypointsMain INSTANCE;

    public AeroWaypointsMain()
    {
        super();
        INSTANCE = this;
        init();
        // LoggerProject.logInit( "001000", this.getClass().getName() ); // Uncomment if you have a logging system in place
    }

    private void init()
    {

        /*
        Proxy for external APIs which are platform dependent
        this.portalApi = (PortalApi) Balm.platformProxy()
            .withFabric("com.holybuckets.challengetemple.externalapi.FabricPortalApi")
            .withForge("com.holybuckets.challengetemple.externalapi.ForgePortalApi")
            .build();
            */

        //Events
        EventRegistrar registrar = EventRegistrar.getInstance();
        registrar.registerOnSimpleMessage(CREATE_ENTITY_INTERACT_MSG, this::onSimpleStringCreateEntityEvent);
        WaypointManager.init(registrar);


        //register local events
        registrar.registerOnBeforeServerStarted(this::onServerStarting, EventPriority.High);

    }


    private void onServerStarting(ServerStartingEvent e) {
        //CONFIG = Balm.getConfig().getActiveConfig(AeroWaypointConfig.class);
        //this.DEV_MODE = CONFIG.devMode;
        ITrackedContrap.init(GeneralConfig.getInstance());
        this.DEV_MODE = false;
    }


    //clientOnly
    public static void sendSimpleStringCreateEntityEventMessage(Player player, Entity entity, BlockPos pos) {
        JsonObject json = new JsonObject();
        json.addProperty("entityUUID", entity.getStringUUID());
        if(pos != null && !pos.equals(BlockPos.ZERO))
            json.addProperty("blockPos", HBUtil.BlockUtil.positionToString(pos));
        SimpleStringMessage.createAndFire(player, CREATE_ENTITY_INTERACT_MSG, json.toString());
    }


    //ServerOnly
    public static final String CREATE_ENTITY_INTERACT_MSG = "create_entity_interact";
    private void onSimpleStringCreateEntityEvent(SimpleMessageEvent message)
    {
        Player p = message.getPlayer();
        p = PlayerUtil.getPlayer(PlayerUtil.getId(p), PlayerUtil.PlayerNameSpace.SERVER);
        JsonObject json = JsonParser.parseString(message.getContent()).getAsJsonObject();
        String entityUUID = json.get("entityUUID").getAsString();
        BlockPos pos = null;
        if(json.has("blockPos"))
            pos = HBUtil.BlockUtil.stringToBlockPos(json.get("blockPos").getAsString());

        Entity entity = ((ServerLevel) p.level()).getEntity(UUID.fromString(entityUUID));
        if(entity == null) {
        return;
        }

        PlayerInteractEvent.EntityInteract event = new PlayerInteractEvent.EntityInteract(
            p, p.level(),
            InteractionHand.MAIN_HAND, p.getMainHandItem(),
            pos, null, entity,
            (pos==null) ? null : new Vec3(pos.getX(), pos.getY(), pos.getZ())

        );

        WaypointManager.onPlayerEntityInteract(event);
    }

}
