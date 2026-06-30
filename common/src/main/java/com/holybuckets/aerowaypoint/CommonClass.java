package com.holybuckets.aerowaypoint;

import com.holybuckets.aerowaypoint.client.config.AeroWaypointClientConfig;
import com.holybuckets.foundation.event.BalmEventRegister;
import com.holybuckets.aerowaypoint.block.ModBlocks;
import com.holybuckets.aerowaypoint.block.be.ModBlockEntities;
import com.holybuckets.aerowaypoint.item.ModItems;
import com.holybuckets.aerowaypoint.menu.ModMenus;
import com.holybuckets.aerowaypoint.platform.Services;
import net.blay09.mods.balm.api.Balm;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;


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
        ModBlocks.initialize(Balm.getBlocks());
        ModBlockEntities.initialize(Balm.getBlockEntities());
        ModItems.initialize(Balm.getItems());
        ModMenus.initialize(Balm.getMenus());
        
        isInitialized = true;
    }

    /**
     * Description: Run sample tests methods
     */
    public static void sample()
    {

    }
}