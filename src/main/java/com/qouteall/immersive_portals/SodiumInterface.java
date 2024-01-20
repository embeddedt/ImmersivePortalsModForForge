package com.qouteall.immersive_portals;

import com.qouteall.immersive_portals.chunk_loading.MyClientChunkManager;
import net.minecraft.client.multiplayer.ClientChunkProvider;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;

import java.util.function.BiFunction;
import java.util.function.Function;

@OnlyIn(Dist.CLIENT)
public class SodiumInterface {
    public static boolean isSodiumPresent = ModList.get().isLoaded("embeddium");
    
}
