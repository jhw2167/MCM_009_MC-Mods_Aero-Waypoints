package com.holybuckets.aerowaypoint.compat;

import com.holybuckets.foundation.model.EntityLike;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class SableSubLevelEntityLike implements EntityLike {

    private final SubLevel subLevel;

    public SableSubLevelEntityLike(SubLevel subLevel) {
        this.subLevel = subLevel;
    }

    public SubLevel getSubLevel() {
        return subLevel;
    }

    @Override
    public UUID getUUID() {
        return subLevel.getUniqueId();
    }

    @Override
    public Vec3 position() {
        BoundingBox3dc bounds = subLevel.boundingBox();
        double x = (bounds.minX() + bounds.maxX()) * 0.5;
        double y = (bounds.minY() + bounds.maxY()) * 0.5;
        double z = (bounds.minZ() + bounds.maxZ()) * 0.5;
        return new Vec3(x, y, z);
    }

    @Override
    public float getYRot() {
        return 0f;
    }

    @Override
    public float getXRot() {
        return 0f;
    }

    @Override
    public ResourceLocation dimension() {
        return subLevel.getLevel().dimension().location();
    }

    @Override
    public boolean isValid() {
        return subLevel != null && !subLevel.isRemoved();
    }
}
