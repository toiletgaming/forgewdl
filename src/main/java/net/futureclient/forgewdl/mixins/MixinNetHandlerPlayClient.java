/*
 * Decompiled with CFR 0.150.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.network.NetHandlerPlayClient
 *  net.minecraft.network.play.INetHandlerPlayClient
 *  net.minecraft.network.play.server.SPacketBlockAction
 *  net.minecraft.network.play.server.SPacketChat
 *  net.minecraft.network.play.server.SPacketCustomPayload
 *  net.minecraft.network.play.server.SPacketMaps
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package net.futureclient.forgewdl.mixins;

import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.SPacketBlockAction;
import net.minecraft.network.play.server.SPacketChat;
import net.minecraft.network.play.server.SPacketCustomPayload;
import net.minecraft.network.play.server.SPacketMaps;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wdl.WDLHooks;

@Mixin(value={NetHandlerPlayClient.class})
public abstract class MixinNetHandlerPlayClient
implements INetHandlerPlayClient {
    @Inject(method={"handleChat"}, at={@At(value="RETURN")})
    private void onHandleChat(SPacketChat p_147251_1_, CallbackInfo ci) {
        WDLHooks.onNHPCHandleChat((NetHandlerPlayClient)(Object)this, p_147251_1_);
    }

    @Inject(method={"handleBlockAction"}, at={@At(value="RETURN")})
    private void onHandleBlockAction(SPacketBlockAction packetIn, CallbackInfo ci) {
        WDLHooks.onNHPCHandleBlockAction((NetHandlerPlayClient)(Object)this, packetIn);
    }

    @Inject(method={"handleMaps"}, at={@At(value="RETURN")})
    private void onHandleMaps(SPacketMaps packetIn, CallbackInfo ci) {
        WDLHooks.onNHPCHandleMaps((NetHandlerPlayClient)(Object)this, packetIn);
    }

    @Inject(method={"handleCustomPayload"}, at={@At(value="RETURN")})
    private void onHandleCustomPayload(SPacketCustomPayload packetIn, CallbackInfo ci) {
        WDLHooks.onNHPCHandleCustomPayload((NetHandlerPlayClient)(Object)this, packetIn);
    }
}

