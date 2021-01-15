package com.qouteall.hiding_in_the_bushes;

import com.qouteall.immersive_portals.Global;
import com.qouteall.immersive_portals.Helper;
import com.qouteall.immersive_portals.McHelper;
import com.qouteall.immersive_portals.chunk_loading.MyClientChunkManager;
import com.qouteall.immersive_portals.portal.custom_portal_gen.PortalGenInfo;
import com.qouteall.immersive_portals.portal.nether_portal.BlockPortalShape;
import com.qouteall.immersive_portals.portal.nether_portal.NetherPortalGeneration;
import net.minecraft.block.BlockState;
import net.minecraft.client.multiplayer.ClientChunkProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class O_O {
    public static boolean isForge() {
        return true;
    }
    
    @OnlyIn(Dist.CLIENT)
    public static void onPlayerChangeDimensionClient(
        RegistryKey<World> from, RegistryKey<World> to
    ) {
    
    }
    
    @OnlyIn(Dist.CLIENT)
    public static void segregateClientEntity(
        ClientWorld fromWorld,
        Entity entity
    ) {
        ((IEClientWorld_MA) fromWorld).removeEntityWhilstMaintainingCapability(entity);
        entity.revive();
    }
    
    public static void segregateServerEntity(
        ServerWorld fromWorld,
        Entity entity
    ) {
        fromWorld.removeEntity(entity, true);
        entity.revive();
    }
    
    public static void segregateServerPlayer(
        ServerWorld fromWorld,
        ServerPlayerEntity player
    ) {
        fromWorld.removePlayer(player, true);
        player.revive();
    }
    
    public static void onPlayerTravelOnServer(
        ServerPlayerEntity player,
        RegistryKey<World> from,
        RegistryKey<World> to
    ) {
        Global.serverTeleportationManager.isFiringMyChangeDimensionEvent = true;
        net.minecraftforge.fml.hooks.BasicEventHooks.firePlayerChangedDimensionEvent(
            player, from, to
        );
        Global.serverTeleportationManager.isFiringMyChangeDimensionEvent = false;
    }
    
    public static void loadConfigFabric() {
        //nothing
    }
    
    public static boolean isObsidian(BlockState blockState) {
        return blockState.isPortalFrame(DummyWorldReader.instance, BlockPos.ZERO);
    }
    
    public static final boolean isReachEntityAttributesPresent = false;
    
    public static boolean detectOptiFine() {
        try {
            //do not load other optifine classes that loads vanilla classes
            //that would load the class before mixin
            Class.forName("optifine.ZipResourceProvider");
            return true;
        }
        catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    public static void postClientChunkUnloadEvent(Chunk chunk) {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
            new net.minecraftforge.event.world.ChunkEvent.Unload(chunk)
        );
    }
    
    public static void postClientChunkLoadEvent(Chunk chunk) {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(
            new net.minecraftforge.event.world.ChunkEvent.Load(chunk)
        );
    }
    
    public static boolean isDedicatedServer() {
        return FMLEnvironment.dist == Dist.DEDICATED_SERVER;
    }
    
    public static void postPortalSpawnEventForge(PortalGenInfo info) {
        ServerWorld world = McHelper.getServer().getWorld(info.from);
        BlockPortalShape shape = info.fromShape;
    
        MinecraftForge.EVENT_BUS.post(
            new BlockEvent.PortalSpawnEvent(
                world,
                shape.anchor,
                world.getBlockState(shape.anchor),
                null
            )
        );
    }
    
    @OnlyIn(Dist.CLIENT)
    public static ClientChunkProvider createMyClientChunkManager(
        ClientWorld world, int dis
    ) {
        return new MyClientChunkManager(world, dis);
    }
}
