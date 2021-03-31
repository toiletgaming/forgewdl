/*
 * Decompiled with CFR 0.150.
 * 
 * Could not load the following classes:
 *  net.minecraft.crash.CrashReport
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 */
package net.futureclient.forgewdl.mixins;

import net.minecraft.crash.CrashReport;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wdl.WDLHooks;

@Mixin(value={CrashReport.class})
public abstract class MixinCrashReport {
    @Inject(method={"populateEnvironment"}, at={@At(value="RETURN")})
    private void onCrashReportPopulateEnvironment(CallbackInfo ci) {
        try {
            WDLHooks.onCrashReportPopulateEnvironment((CrashReport)(Object)this);
        }
        catch (Throwable t) {
            try {
                Logger LOGGER = LogManager.getLogger();
                LOGGER.fatal("World Downloader: Failed to add crash info", t);
                ((CrashReport)(Object)this).getCategory().addCrashSectionThrowable("World Downloader - Fatal error in crash handler (see log)", t);
            }
            catch (Throwable t2) {
                System.err.println("WDL: Double failure adding info to crash report!");
                t.printStackTrace();
                t2.printStackTrace();
            }
        }
    }
}

