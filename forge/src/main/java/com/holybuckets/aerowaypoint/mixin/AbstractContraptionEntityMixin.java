package com.holybuckets.aerowaypoint.mixin;

import com.holybuckets.foundation.event.EventRegistrar;
import com.holybuckets.foundation.event.custom.PlayerInteractEvent;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.simibubi.create.content.contraptions.AbstractContraptionEntity", remap = false)
public class AbstractContraptionEntityMixin {

    @Inject(method = "handlePlayerInteraction", at = @At("HEAD"))
    private void hbs$onContraptionInteract(Player player, BlockPos localPos, Direction side,
                                           InteractionHand hand, CallbackInfoReturnable<Boolean> cir) {
        AbstractContraptionEntity self = (AbstractContraptionEntity) (Object) this;
        EventRegistrar.getInstance().onPlayerInteract(
            new PlayerInteractEvent.EntityInteract(
                player, player.level(), hand, player.getItemInHand(hand),
                localPos, side, self, null
            )
        );
    }
}