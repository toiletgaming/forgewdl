package net.futureclient.forgewdl;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(
		modid = "forgewdl",
		name = "World Downloader Mod",
		version = "4.0.1.7"
)
public class Main
{
	@EventHandler
	public void init( FMLInitializationEvent event )
	{
		MinecraftForge.EVENT_BUS.register( new ForgeEventProcessor( ) );
	}
}
