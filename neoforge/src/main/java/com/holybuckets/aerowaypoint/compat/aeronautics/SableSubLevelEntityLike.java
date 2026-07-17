package com.holybuckets.aerowaypoint.compat.aeronautics;

import com.holybuckets.foundation.model.EntityLike;
import dev.ryanhcode.sable.level.SubLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Adapts a Sable {@link SubLevel} (the structure behind a Create: Aeronautics ship)
 * to HBs Foundation's {@link EntityLike}. This is the ONLY class in the mod that
 * imports Sable; Foundation stays Sable-agnostic and only ever sees {@code EntityLike}.
 *
 * <p>The wrapped {@code SubLevel} is queried live on every call so a moving ship's
 * position/orientation stay current — mirrors how the vanilla {@code Entity}-backed
 * {@code EntityLike} in Foundation delegates straight to the live entity.</p>
 *
 * <p><b>VERIFY AGAINST THE SABLE JAR</b> — these are the sub-level accessors this
 * adapter assumes. If Sable names them differently, this class is the only place to
 * fix (method bodies only; the {@code EntityLike} contract above does not change):
 * <ul>
 *   <li>{@code SubLevel#getUUID()} → {@link UUID} identifying the sub-level</li>
 *   <li>{@code SubLevel#position()} → global-space {@link Vec3} of the ship</li>
 *   <li>{@code SubLevel#getYRot()/getXRot()} → orientation in degrees
 *       (Sable stores a quaternion pose; convert if needed)</li>
 *   <li>{@code SubLevel#level()} → parent {@code Level} (for the dimension key)</li>
 *   <li>{@code SubLevel#isRemoved()} → true once disassembled/despawned</li>
 * </ul></p>
 */
public class SableSubLevelEntityLike implements EntityLike {

    private final SubLevel subLevel;

    public SableSubLevelEntityLike(SubLevel subLevel) {
        this.subLevel = subLevel;
    }

    @Override
    public UUID getUUID() {
        return subLevel.getUUID();
    }

    @Override
    public Vec3 position() {
        return subLevel.position();
    }

    @Override
    public float getYRot() {
        return subLevel.getYRot();
    }

    @Override
    public float getXRot() {
        return subLevel.getXRot();
    }

    @Override
    public ResourceLocation dimension() {
        return subLevel.level().dimension().location();
    }

    @Override
    public boolean isValid() {
        return subLevel != null && !subLevel.isRemoved();
    }
}
