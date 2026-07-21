package com.holybuckets.aerowaypoint.mixin;

import com.holybuckets.aerowaypoint.AeroWaypointsMain;
import com.holybuckets.aerowaypoint.compat.SableSubLevelEntityLike;
import com.holybuckets.aerowaypoint.core.WaypointManager;
import com.holybuckets.foundation.model.EntityLikeResolver;
import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Detects goggles right-clicks on an assembled physics assembler to toggle a ship waypoint.
@Mixin(value = PhysicsAssemblerBlock.class, remap = false)
public class PhysicsAssemblerBlockMixin {

    // Server-authoritative: only act on the server for a real player holding the toggle item.
    @Inject(method = "useWithoutItem", at = @At("RETURN"), cancellable = true)
    private void hbs$onUse(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit,
                           CallbackInfoReturnable<InteractionResult> cir) {
        if (level.isClientSide) return;
        if (!(player instanceof ServerPlayer) || player instanceof DeployerFakePlayer) return;
        if (!hbs$isHoldingToggle(player)) return;

        // Only assembled assemblers belong to a sub-level; a loose block has none.
        SubLevel subLevel = Sable.HELPER.getContaining(level, pos);
        if (subLevel == null) return;

        AeroWaypointsMain.onSublevelInteract(player, new SableSubLevelEntityLike(subLevel));
        cir.setReturnValue(InteractionResult.SUCCESS);
    }

    // True when the player's main hand holds the waypoint toggle item (Create goggles).
    private static boolean hbs$isHoldingToggle(Player player) {
        Item toggle = WaypointManager.WAYPOINT_TOGGLE_ITEM;
        return toggle != null && player.getMainHandItem().getItem() == toggle;
    }
}
