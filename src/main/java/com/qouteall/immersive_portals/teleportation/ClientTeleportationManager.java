package com.qouteall.immersive_portals.teleportation;

import com.qouteall.immersive_portals.*;
import com.qouteall.immersive_portals.ducks.IEClientPlayNetworkHandler;
import com.qouteall.immersive_portals.ducks.IEClientWorld;
import com.qouteall.immersive_portals.ducks.IEGameRenderer;
import com.qouteall.immersive_portals.ducks.IEMinecraftClient;
import com.qouteall.immersive_portals.network.CtsTeleport;
import com.qouteall.immersive_portals.network.NetworkMain;
import com.qouteall.immersive_portals.portal.Mirror;
import com.qouteall.immersive_portals.portal.Portal;
import com.qouteall.immersive_portals.render.FogRendererContext;
import com.qouteall.immersive_portals.render.MyRenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.screen.DownloadTerrainScreen;
import net.minecraft.client.network.play.ClientPlayNetHandler;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.ClassInheritanceMultiMap;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.dimension.DimensionType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class ClientTeleportationManager {
    Minecraft mc = Minecraft.getInstance();
    private long tickTimeForTeleportation = 0;
    private long lastTeleportGameTime = 0;
    private Vec3d lastPlayerHeadPos = null;
    
    public ClientTeleportationManager() {
        ModMain.preRenderSignal.connectWithWeakRef(
            this, ClientTeleportationManager::manageTeleportation
        );
        ModMain.postClientTickSignal.connectWithWeakRef(
            this, ClientTeleportationManager::tick
        );
    }
    
    private void tick() {
        tickTimeForTeleportation++;
        if (mc.player != null) {
            slowDownPlayerIfCollidingWithPortal();
        }
    }
    
    public void acceptSynchronizationDataFromServer(
        DimensionType dimension,
        Vec3d pos,
        boolean forceAccept
    ) {
        if (!forceAccept) {
            if (isTeleportingFrequently()) {
                return;
            }
        }
        if (mc.player.dimension != dimension) {
            forceTeleportPlayer(dimension, pos);
        }
        getOutOfLoadingScreen(dimension, pos);
    }
    
    private void manageTeleportation() {
        if (mc.world != null && mc.player != null) {
            Vec3d currentHeadPos = mc.player.getEyePosition(MyRenderHelper.partialTicks);
            if (lastPlayerHeadPos != null) {
                if (lastPlayerHeadPos.squareDistanceTo(currentHeadPos) > 100) {
                    Helper.err("The Player is Moving Too Fast!");
                    
                }
                CHelper.getClientNearbyPortals(20).filter(
                    portal -> {
                        return mc.player.dimension == portal.dimension &&
                            portal.isTeleportable() &&
                            portal.isMovedThroughPortal(
                                lastPlayerHeadPos,
                                currentHeadPos
                            );
                    }
                ).findFirst().ifPresent(
                    portal -> teleportPlayer(portal)
                );
            }
            
            //do not use currentHeadPos
            lastPlayerHeadPos = mc.player.getEyePosition(MyRenderHelper.partialTicks);
        }
        else {
            lastPlayerHeadPos = null;
        }
    }
    
    private void teleportPlayer(Portal portal) {
        if (isTeleportingFrequently()) {
            Helper.log("Client Player is teleporting frequently!");
        }
        lastTeleportGameTime = tickTimeForTeleportation;
        
        ClientPlayerEntity player = mc.player;
        
        DimensionType toDimension = portal.dimensionTo;
        
        Vec3d oldPos = player.getPositionVec();
        
        Vec3d newPos = portal.applyTransformationToPoint(oldPos);
        Vec3d newLastTickPos = portal.applyTransformationToPoint(McHelper.lastTickPosOf(player));
        
        ClientWorld fromWorld = mc.world;
        DimensionType fromDimension = fromWorld.dimension.getType();
        
        if (fromDimension != toDimension) {
            ClientWorld toWorld = CGlobal.clientWorldLoader.getOrCreateFakedWorld(toDimension);
            
            changePlayerDimension(player, fromWorld, toWorld, newPos);
        }
        
        player.setPosition(newPos.x, newPos.y, newPos.z);
        McHelper.setPosAndLastTickPos(player, newPos, newLastTickPos);
        
        NetworkMain.sendToServer(new CtsTeleport(
            fromDimension,
            oldPos,
            portal.getUniqueID()
        ));
        
        amendChunkEntityStatus(player);
        
        McHelper.adjustVehicle(player);
    }
    
    private boolean isTeleportingFrequently() {
        if (tickTimeForTeleportation - lastTeleportGameTime <= 10) {
            return true;
        }
        else {
            return false;
        }
    }
    
    private void forceTeleportPlayer(DimensionType toDimension, Vec3d destination) {
        Helper.log("force teleported " + toDimension + destination);
        
        ClientWorld fromWorld = mc.world;
        DimensionType fromDimension = fromWorld.dimension.getType();
        ClientPlayerEntity player = mc.player;
        if (fromDimension == toDimension) {
            player.setPosition(
                destination.x,
                destination.y,
                destination.z
            );
        }
        else {
            ClientWorld toWorld = CGlobal.clientWorldLoader.getOrCreateFakedWorld(toDimension);
            
            changePlayerDimension(player, fromWorld, toWorld, destination);
        }
        
        McHelper.adjustVehicle(player);
        amendChunkEntityStatus(player);
    }
    
    private void changePlayerDimension(
        ClientPlayerEntity player, ClientWorld fromWorld, ClientWorld toWorld, Vec3d destination
    ) {
        Entity vehicle = player.getRidingEntity();
        player.detach();
        
        DimensionType toDimension = toWorld.dimension.getType();
        DimensionType fromDimension = fromWorld.dimension.getType();
        
        ClientPlayNetHandler workingNetHandler = ((IEClientWorld) fromWorld).getNetHandler();
        ClientPlayNetHandler fakedNetHandler = ((IEClientWorld) toWorld).getNetHandler();
        ((IEClientPlayNetworkHandler) workingNetHandler).setWorld(toWorld);
        ((IEClientPlayNetworkHandler) fakedNetHandler).setWorld(fromWorld);
        ((IEClientWorld) fromWorld).setNetHandler(fakedNetHandler);
        ((IEClientWorld) toWorld).setNetHandler(workingNetHandler);
        
        ((IEClientWorld) fromWorld).removeEntityWhilstMaintainingCapability(player);
        player.revive();
        player.world = toWorld;
        
        player.dimension = toDimension;
        player.setPosition(destination.x, destination.y, destination.z);//update bounding box
        
        toWorld.addPlayer(player.getEntityId(), player);
        
        mc.world = toWorld;
        ((IEMinecraftClient) mc).setWorldRenderer(
            CGlobal.clientWorldLoader.getWorldRenderer(toDimension)
        );
        
        toWorld.setScoreboard(fromWorld.getScoreboard());
        
        if (mc.particles != null)
            mc.particles.clearEffects(toWorld);
        
        TileEntityRendererDispatcher.instance.setWorld(toWorld);
        
        IEGameRenderer gameRenderer = (IEGameRenderer) Minecraft.getInstance().gameRenderer;
        gameRenderer.setLightmapTextureManager(CGlobal.clientWorldLoader
            .getDimensionRenderHelper(toDimension).lightmapTexture);
        
        if (vehicle != null) {
            Vec3d vehiclePos = new Vec3d(
                destination.x,
                McHelper.getVehicleY(vehicle, player),
                destination.z
            );
            moveClientEntityAcrossDimension(
                vehicle, toWorld,
                vehiclePos
            );
            player.startRiding(vehicle, true);
        }
        
        Helper.log(String.format(
            "Client Changed Dimension from %s to %s time: %s",
            fromDimension,
            toDimension,
            tickTimeForTeleportation
        ));
        
        //because the teleportation may happen before rendering
        //but after pre render info being updated
        MyRenderHelper.updatePreRenderInfo(MyRenderHelper.partialTicks);
        
        OFInterface.onPlayerTraveled.accept(
            fromDimension, toDimension
        );
        
        FogRendererContext.onPlayerTeleport(fromDimension, toDimension);
    }
    
    private void amendChunkEntityStatus(Entity entity) {
        Chunk worldChunk1 = entity.world.getChunkAt(entity.getPosition());
        IChunk chunk2 = entity.world.getChunk(entity.chunkCoordX, entity.chunkCoordZ);
        removeEntityFromChunk(entity, worldChunk1);
        if (chunk2 instanceof Chunk) {
            removeEntityFromChunk(entity, ((Chunk) chunk2));
        }
        worldChunk1.addEntity(entity);
    }
    
    private void removeEntityFromChunk(Entity entity, Chunk worldChunk) {
        for (ClassInheritanceMultiMap<Entity> section : worldChunk.getEntityLists()) {
            section.remove(entity);
        }
    }
    
    private void getOutOfLoadingScreen(DimensionType dimension, Vec3d playerPos) {
        if (((IEMinecraftClient) mc).getCurrentScreen() instanceof DownloadTerrainScreen) {
            Helper.err("Manually getting out of loading screen. The game is in abnormal state.");
            if (mc.player.dimension != dimension) {
                Helper.err("Manually fix dimension state while loading terrain");
                ClientWorld toWorld = CGlobal.clientWorldLoader.getOrCreateFakedWorld(dimension);
                changePlayerDimension(mc.player, mc.world, toWorld, playerPos);
            }
            mc.player.setPosition(playerPos.x, playerPos.y, playerPos.z);
            mc.displayGuiScreen(null);
        }
    }
    
    private void slowDownPlayerIfCollidingWithPortal() {
        boolean collidingWithPortal = !mc.player.world.getEntitiesWithinAABB(
            Portal.class,
            mc.player.getBoundingBox().grow(1),
            e -> !(e instanceof Mirror)
        ).isEmpty();
        
        if (collidingWithPortal) {
            slowDownIfTooFast(mc.player);
        }
    }
    
    //if player is falling through looping portals, make it slower
    private void slowDownIfTooFast(ClientPlayerEntity player) {
        if (player.getMotion().length() > 0.7) {
            AxisAlignedBB nextTickArea = player.getBoundingBox().offset(player.getMotion()).grow(1);
            List<Portal> nextTickCollidingPortals = player.world.getEntitiesWithinAABB(
                Portal.class,
                nextTickArea,
                e -> e.isTeleportable()
            );
            if (!nextTickCollidingPortals.isEmpty()) {
                player.setMotion(player.getMotion().scale(0.7));
            }
        }
    }
    
    public static void moveClientEntityAcrossDimension(
        Entity entity,
        ClientWorld newWorld,
        Vec3d newPos
    ) {
        ClientWorld oldWorld = (ClientWorld) entity.world;
        oldWorld.removeEntityFromWorld(entity.getEntityId());
        entity.removed = false;
        entity.world = newWorld;
        entity.dimension = newWorld.dimension.getType();
        entity.setPosition(newPos.x, newPos.y, newPos.z);
        newWorld.addEntity(entity.getEntityId(), entity);
    }
}
