/*
 * Decompiled with CFR 0.150.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.multiplayer.WorldClient
 *  net.minecraft.entity.Entity
 *  net.minecraft.profiler.Profiler
 *  net.minecraft.world.World
 *  net.minecraft.world.WorldProvider
 *  net.minecraft.world.storage.ISaveHandler
 *  net.minecraft.world.storage.WorldInfo
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package net.futureclient.forgewdl.mixins;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.profiler.Profiler;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.WorldInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wdl.WDLHooks;

@Mixin(value={WorldClient.class})
public abstract class MixinWorldClient
extends World {
    protected MixinWorldClient(ISaveHandler saveHandlerIn, WorldInfo info, WorldProvider providerIn, Profiler profilerIn, boolean client) {
        super(saveHandlerIn, info, providerIn, profilerIn, client);
    }

    @Inject(method={"tick"}, at={@At(value="RETURN")})
    private void onTick(CallbackInfo ci) {
        WDLHooks.onWorldClientTick((WorldClient)(Object)this);
    }

    @Inject(method={"doPreChunk"}, at={@At(value="HEAD")})
    private void onDoPreChunk(int p_73025_1_, int p_73025_2_, boolean p_73025_3_, CallbackInfo ci) {
        WDLHooks.onWorldClientDoPreChunk((WorldClient)(Object)this, p_73025_1_, p_73025_2_, p_73025_3_);
    }

    @Inject(method={"removeEntityFromWorld"}, at={@At(value="HEAD")})
    private void onRemoveEntityFromWorld(int p_73028_1_, CallbackInfoReturnable<Entity> ci) {
        WDLHooks.onWorldClientRemoveEntityFromWorld((WorldClient)(Object)this, p_73028_1_);
    }
}

