package com.holybuckets.aerowaypoint;

import com.holybuckets.aerowaypoint.compat.SableEntityResolver;
import com.holybuckets.foundation.event.BalmEventRegister;
import com.holybuckets.aerowaypoint.platform.Services;
import com.holybuckets.foundation.model.EntityLikeResolver;
import net.blay09.mods.balm.api.Balm;


public class CommonClass {

    public static boolean isInitialized = false;
    public static void init()
    {
        if (isInitialized)
            return;

        //Initialize Foundations
        com.holybuckets.foundation.FoundationInitializers.commonInitialize();

        if (Services.PLATFORM.isModLoaded(Constants.MOD_ID)) {
            Constants.LOG.info("Hello to " + Constants.MOD_NAME + "!");
        }

        //RegisterConfigs
        AeroWaypointsMain.INSTANCE = new AeroWaypointsMain();
        BalmEventRegister.registerEvents();
        BalmEventRegister.registerCommands();
        //ModBlocks.initialize(Balm.getBlocks());
        //ModBlockEntities.initialize(Balm.getBlockEntities());
        //ModItems.initialize(Balm.getItems());
        //ModMenus.initialize(Balm.getMenus());

        EntityLikeResolver.register( (SableEntityResolver) Balm.platformProxy()
            .withNeoForge("com.holybuckets.aerowaypoint.compat.SableEntityResolverNeoForge")
            // DNE rn .withFabric("com.holybuckets.aerowaypoint.compat.SableEntityResolverFabric")
            .build());

        isInitialized = true;
    }

    /**
     * Description: Run sample tests methods
     */
    public static void sample()
    {

    }
}