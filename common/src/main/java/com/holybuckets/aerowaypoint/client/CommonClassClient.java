package com.holybuckets.aerowaypoint.client;

import com.holybuckets.aerowaypoint.client.config.AeroWaypointClientConfig;
import com.holybuckets.aerowaypoint.client.core.WaypointManagerClient;
import com.holybuckets.aerowaypoint.client.screen.ModScreens;
import com.holybuckets.foundation.client.ClientBalmEventRegister;
import com.holybuckets.foundation.client.ClientEventRegistrar;
import com.holybuckets.foundation.event.EventRegistrar;
import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.client.BalmClient;
import net.blay09.mods.balm.api.event.client.ConnectedToServerEvent;
import net.blay09.mods.balm.api.event.server.ServerStartingEvent;


public class CommonClassClient {

    public static AeroWaypointClientConfig CONFIG;

    public static void initClient() {

        Balm.getConfig().registerConfig(AeroWaypointClientConfig.class);

        ClientEventRegistrar registrar = ClientEventRegistrar.getInstance();
        registrar.registerOnConnectedToServer(CommonClassClient::onConnectedToServer);
        EventRegistrar.getInstance().registerOnBeforeServerStarted(CommonClassClient::onBeforeServerStart);

        ClientBalmEventRegister.registerEvents();
        ModRenderers.clientInitialize(BalmClient.getRenderers());
        ModScreens.clientInitialize(BalmClient.getScreens());
        //ModItems.clientInitialize();
        WaypointManagerClient.init(registrar);
    }

    private static void onConnectedToServer(ConnectedToServerEvent event) {
        CONFIG = Balm.getConfig().getActiveConfig(AeroWaypointClientConfig.class);
    }

    private static void onBeforeServerStart(ServerStartingEvent event) {
        CONFIG = Balm.getConfig().getActiveConfig(AeroWaypointClientConfig.class);
    }


}