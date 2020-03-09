package com.qouteall.immersive_portals.mixin.portal_generation;

import com.qouteall.hiding_in_the_bushes.O_O;
import com.qouteall.immersive_portals.ModMain;
import com.qouteall.immersive_portals.portal.BreakableMirror;
import com.qouteall.immersive_portals.portal.nether_portal.NewNetherPortalGenerator;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.FlintAndSteelItem;
import net.minecraft.item.ItemUseContext;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.server.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FlintAndSteelItem.class)
public class MixinFlintAndSteelItem {
    @Inject(method = "Lnet/minecraft/item/FlintAndSteelItem;onItemUse(Lnet/minecraft/item/ItemUseContext;)Lnet/minecraft/util/ActionResultType;", at = @At("HEAD"))
    private void onUseFlintAndSteel(
        ItemUseContext context,
        CallbackInfoReturnable<ActionResultType> cir
    ) {
        IWorld world = context.getWorld();
        if (!world.isRemote()) {
            BlockPos targetPos = context.getPos();
            Direction side = context.getFace();
            BlockPos firePos = targetPos.offset(side);
            Block targetBlock = world.getBlockState(targetPos).getBlock();
            if (O_O.isObsidian(world, targetPos)) {
                NewNetherPortalGenerator.onFireLit(((ServerWorld) world), firePos);
            }
            else if (targetBlock == Blocks.GLASS) {
                BreakableMirror mirror = BreakableMirror.createMirror(
                    ((ServerWorld) world), targetPos, side
                );
            }
            else if (targetBlock == ModMain.portalHelperBlock) {
                boolean result = NewNetherPortalGenerator.activatePortalHelper(
                    ((ServerWorld) world),
                    firePos
                );
        
            }
            else {
                context.getItem().damageItem(1, context.getPlayer(),
                    playerEntity_1x -> playerEntity_1x.sendBreakAnimation(context.getHand())
                );
            }
        }
    }
}
