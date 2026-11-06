// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.qa;

import java.util.*;
import java.util.concurrent.Future;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;

public final class TerrainResidentSceneWorld {
    private static Thread clientOwner;
    private TerrainResidentSceneWorld(){}
    private static void guard(Minecraft mc){
        if(clientOwner==null)clientOwner=Thread.currentThread();else if(clientOwner!=Thread.currentThread())throw new IllegalStateException("Fixture client state requires its original client thread");
        if(!"ArcadiumTest".equals(mc.getSession().getUsername())||!"0".equals(mc.getSession().getAccessToken())
                ||mc.getServer()==null||mc.world==null||mc.player==null||mc.getCurrentServerEntry()!=null)
            throw new IllegalStateException("Use only the owned offline integrated-server terrain fixture");
    }
    public static Future<Map<String,Object>> build(Minecraft mc,TerrainResidentSceneBlueprint.Plan plan){
        guard(mc);var server=mc.getServer();
        return server.executeTask(()->{
            var world=server.getWorld(0);world.getGameRules().set("doDaylightCycle","false");world.getGameRules().set("doMobSpawning","false");world.getGameRules().set("randomTickSpeed","0");
            world.setTimeOfDay(6000);world.getData().setRaining(false);world.getData().setThundering(false);
            world.getData().setRainTime(1000000);world.getData().setThunderTime(1000000);world.getData().setClearWeatherTime(1000000);
            int removed=0,changed=0;
            for(var entity:new ArrayList<>(world.entities))if(!(entity instanceof PlayerEntity)){world.removeEntityNow(entity);removed++;}
            for(var p:plan.blocks())if(world.setBlockState(new BlockPos(p.x(),p.y(),p.z()),block(p.material()).getStateFromMetadata(p.metadata()),3))changed++;
            return Map.of("blueprintSha256",plan.sha256(),"placements",plan.blocks().size(),"changedBlocks",changed,"removedNonPlayerEntities",removed,"nativeServerTask",true,"meshPublicationNotYetQualified",true);
        });
    }
    public static Future<Map<String,Object>> edit(Minecraft mc,TerrainResidentSceneBlueprint.Edit edit){
        guard(mc);var server=mc.getServer();
        return server.executeTask(()->{
            var world=server.getWorld(0);int changed=0;
            for(var p:edit.blocks())if(world.setBlockState(new BlockPos(p.x(),p.y(),p.z()),block(p.material()).getStateFromMetadata(p.metadata()),3))changed++;
            return Map.of("edit",edit.name(),"placements",edit.blocks().size(),"changedBlocks",changed,"nativeServerTask",true,"meshPublicationNotYetQualified",true);
        });
    }
    public static Future<Map<String,Object>> move(Minecraft mc,TerrainResidentSceneBlueprint.Camera camera){
        guard(mc);var server=mc.getServer();
        return server.executeTask(()->{
            var players=server.getWorld(0).players;
            if(players.size()!=1||!(players.getFirst() instanceof net.minecraft.server.entity.living.player.ServerPlayerEntity player)||!"ArcadiumTest".equals(player.getName()))throw new IllegalStateException("Unexpected owned integrated-server player");
            player.abilities.flying=true;player.networkHandler.teleport(camera.x(),camera.y(),camera.z(),camera.yaw(),camera.pitch());
            return Map.of("camera",camera.name(),"position",List.of(player.x,player.y,player.z),"nativeServerTeleport",true,"offlineOnly",true);
        });
    }
    public static void freezeClientCamera(Minecraft mc,TerrainResidentSceneBlueprint.Camera camera){
        guard(mc);mc.player.abilities.flying=true;mc.player.setInvisible(true);
        mc.player.updatePositionAndAngles(camera.x(),camera.y(),camera.z(),camera.yaw(),camera.pitch());
        mc.player.lastX=mc.player.prevX=mc.player.x;mc.player.lastY=mc.player.prevY=mc.player.y;mc.player.lastZ=mc.player.prevZ=mc.player.z;
        mc.player.lastYaw=mc.player.yaw;mc.player.lastPitch=mc.player.pitch;mc.player.velocityX=mc.player.velocityY=mc.player.velocityZ=0;
        mc.options.fov=camera.fov();mc.options.viewBobbing=false;mc.world.setTimeOfDay(6000);mc.world.getData().setRaining(false);mc.world.getData().setThundering(false);
    }
    private static Block block(TerrainResidentSceneBlueprint.Material value){return switch(value){
        case AIR->Blocks.AIR;case STONE->Blocks.STONE;case STONE_BRICKS->Blocks.STONE_BRICKS;case STONE_SLAB->Blocks.STONE_SLAB;
        case DOUBLE_STONE_SLAB->Blocks.DOUBLE_STONE_SLAB;case STONE_STAIRS->Blocks.STONE_STAIRS;case GLASS->Blocks.GLASS;
        case STAINED_GLASS->Blocks.STAINED_GLASS;case LEAVES->Blocks.LEAVES;case FENCE->Blocks.FENCE;case GLOWSTONE->Blocks.GLOWSTONE;case WOOL->Blocks.WOOL;
    };}
}
