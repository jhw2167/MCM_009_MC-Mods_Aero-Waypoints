package com.holybuckets.aerowaypoint.compat;

import com.holybuckets.aerowaypoint.core.TrackedContrapForge;
import com.holybuckets.foundation.model.EntityLike;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;


public class SableEntityResolverNeoForge implements SableEntityResolver {

    public SableEntityResolverNeoForge() {
        super();
    }

    @Override
    public Optional<EntityLike> resolve(UUID uuid, Level level) {
        if (uuid == null || level == null) return Optional.empty();
        SubLevel subLevel = findSubLevel(level, uuid);
        if (subLevel == null || subLevel.isRemoved()) return Optional.empty();
        return Optional.of(new SableSubLevelEntityLike(subLevel));
    }

    private static SubLevel findSubLevel(Level level, UUID uuid) {
        SubLevel found = lookup(level, uuid);
        if (found != null) return found;

        if (level.getServer() != null) {
            for (ServerLevel serverLevel : level.getServer().getAllLevels()) {
                SubLevel sl = lookup(serverLevel, uuid);
                if (sl != null) return sl;
            }
        }
        return null;
    }

    private static SubLevel lookup(Level level, UUID uuid) {
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        return container == null ? null : container.getSubLevel(uuid);
    }
}
