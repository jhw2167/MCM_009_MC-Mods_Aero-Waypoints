package com.holybuckets.aerowaypoint.compat.aeronautics;

import com.holybuckets.foundation.model.EntityLike;
import dev.ryanhcode.sable.level.SubLevel;
import dev.ryanhcode.sable.level.SubLevelManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

/**
 * NeoForge {@link SableEntityResolver}: resolves a waypoint's UUID to a Sable
 * {@link SubLevel} (Create: Aeronautics ship) and wraps it in
 * {@link SableSubLevelEntityLike}. Returns empty for any UUID Sable doesn't know,
 * so Foundation's resolver list simply falls through to the next resolver.
 *
 * <p><b>VERIFY AGAINST THE SABLE JAR</b> — the sub-level lookup below is the single
 * Sable-specific seam. Confirm how Sable tracks sub-levels by UUID:
 * <ul>
 *   <li>Global (ships can cross dimensions): a server-wide manager, e.g.
 *       {@code SubLevelManager.get(server).get(uuid)} — used below.</li>
 *   <li>Per-level: iterate {@code server.getAllLevels()} and query each
 *       {@code ServerLevel}'s sub-level manager for the UUID.</li>
 * </ul>
 * Only {@link #findSubLevel(Level, UUID)} should need editing.</p>
 */
public class SableEntityResolverNeoForge implements SableEntityResolver {

    public SableEntityResolverNeoForge() {
        super();
    }

    @Override
    public Optional<EntityLike> resolve(UUID uuid, Level level) {
        if (uuid == null || level == null) return Optional.empty();
        SubLevel subLevel = findSubLevel(level, uuid);
        if (subLevel == null) return Optional.empty();
        return Optional.of(new SableSubLevelEntityLike(subLevel));
    }

    /**
     * The one Sable-specific lookup. Checks the given level first, then (server-side, when
     * a server is available) scans all dimensions since ships may change dimension.
     */
    private static SubLevel findSubLevel(Level level, UUID uuid) {
        // Preferred: look in the level we were handed.
        SubLevel found = SubLevelManager.get(level).get(uuid);
        if (found != null) return found;

        // Fallback: dimension-agnostic scan when running with a server (client dedicated
        // instances have no server, so this is skipped there).
        if (level.getServer() != null) {
            for (ServerLevel serverLevel : level.getServer().getAllLevels()) {
                SubLevel sl = SubLevelManager.get(serverLevel).get(uuid);
                if (sl != null) return sl;
            }
        }
        return null;
    }
}
