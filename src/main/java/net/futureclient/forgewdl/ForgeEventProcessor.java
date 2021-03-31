package net.futureclient.forgewdl;

import org.lwjgl.opengl.GL11;

import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import wdl.custom_ChunkRenderer;

public class ForgeEventProcessor
{
	@SubscribeEvent
	public void onRender( RenderWorldLastEvent event )
	{
		if( !custom_ChunkRenderer.getState( ) ) return;
		
		if( custom_ChunkRenderer.list == -1337 )
			custom_ChunkRenderer.list = GL11.glGenLists( 1 );
		
		custom_ChunkRenderer.render( );
	}
	
	@SubscribeEvent
	public void onChunkLoad( ChunkEvent.Load event )
	{
		if( custom_ChunkRenderer.getState( ) )
			custom_ChunkRenderer.addChunk( event.getChunk( ) );
	}
}
