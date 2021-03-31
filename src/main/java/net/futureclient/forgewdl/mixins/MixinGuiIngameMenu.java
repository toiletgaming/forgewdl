/*
 * Decompiled with CFR 0.150.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.GuiButton
 *  net.minecraft.client.gui.GuiIngameMenu
 *  net.minecraft.client.gui.GuiScreen
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package net.futureclient.forgewdl.mixins;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wdl.WDLHooks;

@Mixin(value={GuiIngameMenu.class})
public abstract class MixinGuiIngameMenu
extends GuiScreen {
    @Inject(method={"initGui"}, at={@At(value="RETURN")})
    private void onInitGui(CallbackInfo ci) {
        WDLHooks.injectWDLButtons((GuiIngameMenu)(Object)this, this.buttonList);
    }

    @Inject(method={"actionPerformed"}, at={@At(value="HEAD")})
    private void onActionPerformed(GuiButton guibutton, CallbackInfo ci) {
        WDLHooks.handleWDLButtonClick((GuiIngameMenu)(Object)this, guibutton);
    }
}

