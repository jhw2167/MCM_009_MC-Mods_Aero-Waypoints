package com.holybuckets.aerowaypoint.client;

import com.holybuckets.aerowaypoint.client.screen.ModScreens;
import com.holybuckets.aerowaypoint.item.ModItems;
import com.holybuckets.foundation.client.ClientBalmEventRegister;
import com.holybuckets.foundation.client.ClientEventRegistrar;
import net.blay09.mods.balm.api.client.BalmClient;


public class CommonClassClient {

    public static void initClient() {
        ClientEventRegistrar registrar = ClientEventRegistrar.getInstance();
        ClientBalmEventRegister.registerEvents();
        ModRenderers.clientInitialize(BalmClient.getRenderers());
        ModScreens.clientInitialize(BalmClient.getScreens());
        //ModItems.clientInitialize();
    }

    /**
     * Description: Run sample tests methods
     */
    public static void sample()
    {

    }


}