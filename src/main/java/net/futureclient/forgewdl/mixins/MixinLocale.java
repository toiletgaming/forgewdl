/*
 * Decompiled with CFR 0.150.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.resources.IResourceManager
 *  net.minecraft.client.resources.Locale
 *  net.minecraft.util.ResourceLocation
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package net.futureclient.forgewdl.mixins;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.Locale;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Locale.class})
public abstract class MixinLocale {
    private final ResourceLocation location = new ResourceLocation("wdl/lang/en_us.lang");

    @Shadow
    protected abstract void loadLocaleData(InputStream var1) throws IOException;

    @Inject(method={"loadLocaleDataFiles"}, at={@At(value="RETURN")})
    private void postLoad(IResourceManager resourceManager, List<String> languageList, CallbackInfo callbackInfo) {
        try {
            InputStream inputStream = Minecraft.getMinecraft().getResourceManager().getResource(this.location).getInputStream();
            this.loadLocaleData(inputStream);
        }
        catch (IOException iOException) {
            // empty catch block
        }
    }
}

