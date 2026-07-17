package com.holybuckets.aerowaypoint.compat.aeronautics;

import com.holybuckets.foundation.model.EntityLike;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * Fabric no-op. Sable is a NeoForge-only dependency in this project, so on Fabric
 * there are never any sub-levels to resolve. Present only so the Balm platform
 * proxy has a Fabric target; in practice {@link SableEntityResolver#init()} bails
 * out before this is ever built because Sable is not loaded.
 */
public class SableEntityResolverFabric implements SableEntityResolver {

    public SableEntityResolverFabric() {
        super();
    }

    @Override
    public Optional<EntityLike> resolve(UUID uuid, Level level) {
        return Optional.empty();
    }
}
