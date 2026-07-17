package com.holybuckets.aerowaypoint.compat.aeronautics;

import com.holybuckets.aerowaypoint.Constants;
import com.holybuckets.aerowaypoint.platform.Services;
import com.holybuckets.foundation.model.EntityLikeResolver;
import net.blay09.mods.balm.api.Balm;

import java.util.ArrayList;
import java.util.List;

/**
 * Sable / Create: Aeronautics {@link EntityLikeResolver}.
 *
 * <p>Wired exactly like Create contraptions are (see
 * {@link com.holybuckets.aerowaypoint.core.ITrackedContrap}): a platform-neutral
 * interface in {@code common}, whose real implementation is built at runtime via
 * {@link Balm#platformProxy()} and then handed to HBs Foundation's resolver list.
 * Foundation never sees Sable — it only ever calls {@code EntityLikeResolver}.</p>
 *
 * <p>Only the NeoForge implementation touches Sable. Fabric gets a no-op, and the
 * whole thing is skipped unless Sable is actually loaded.</p>
 *
 * <p>Assumed HBs Foundation API (package {@code com.holybuckets.foundation.model}):
 * <ul>
 *   <li>{@code interface EntityLikeResolver { Optional<EntityLike> resolve(UUID, Level); static void register(EntityLikeResolver); }}</li>
 * </ul></p>
 */
public interface SableEntityResolver extends EntityLikeResolver {

    List<SableEntityResolver> GENERATOR = new ArrayList<>(1);

    /** Mod id of Sable, the sub-level backend behind Create: Aeronautics ships. */
    String SABLE_MOD_ID = "sable";

    /**
     * Build the platform implementation (NeoForge only) and register it with
     * Foundation. Safe to skip on platforms/instances where Sable is absent.
     * Idempotent — clears and rebuilds the single-element GENERATOR list.
     */
    static void init() {
        if (!Services.PLATFORM.isModLoaded(SABLE_MOD_ID)) return;

        GENERATOR.clear();
        GENERATOR.add((SableEntityResolver) Balm.platformProxy()
            .withForge("com.holybuckets.aerowaypoint.compat.aeronautics.SableEntityResolverNeoForge")
            .withFabric("com.holybuckets.aerowaypoint.compat.aeronautics.SableEntityResolverFabric")
            .build());

        EntityLikeResolver.register(GENERATOR.get(0));
        Constants.LOG.info("Registered Sable SubLevel EntityLikeResolver with HBs Foundation");
    }
}
