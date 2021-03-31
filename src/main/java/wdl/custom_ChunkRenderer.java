package wdl;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.futureclient.forgewdl.accessor.IRenderManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

public class custom_ChunkRenderer
{
	private static boolean state = false;
	private static final Field chunkMapping = ReflectionHelper.findField( ChunkProviderClient.class, "field_73236_b", "loadedChunks", "b" );
	private static List< Chunk > chunks = new ArrayList< >( );
	
	// for rendering
	private static boolean dirty = true;
	public static int list = -1337;

	public static void addChunk( Chunk chunk )
	{
		// are we already rendering this chunk?
		for( Chunk _chunk : chunks )
		{
			if( chunk.x == _chunk.x &&
				chunk.z == _chunk.z )
				return;
		}

		chunks.add( chunk );
		dirty = true;
	}
	
	// stolen from kami
	public static void render( )
	{
		if( dirty )
		{
			GL11.glNewList( list, GL11.GL_COMPILE );
			
			GL11.glPushMatrix( );
			GL11.glEnable( GL11.GL_LINE_SMOOTH );
			GL11.glDisable( GL11.GL_DEPTH_TEST );
			GL11.glDisable( GL11.GL_TEXTURE_2D );
			GL11.glDepthMask( false );
			GL11.glBlendFunc( GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA );
			GL11.glEnable( GL11.GL_BLEND );
			GL11.glLineWidth( 1.0F );
			
			for( Chunk chunk : chunks )
			{
				double posX = chunk.x * 16;
                double posY = 0;
                double posZ = chunk.z * 16;

                GL11.glColor3f( .9f, .1f, .1f );

                GL11.glBegin( GL11.GL_LINE_LOOP );
                GL11.glVertex3d( posX, posY, posZ );
                GL11.glVertex3d( posX + 16, posY, posZ );
                GL11.glVertex3d( posX + 16, posY, posZ + 16 );
                GL11.glVertex3d( posX, posY, posZ + 16 );
                GL11.glVertex3d( posX, posY, posZ );
                GL11.glEnd( );
			}
			
			GL11.glDisable( GL11.GL_BLEND );
			GL11.glDepthMask( true );
			GL11.glEnable( GL11.GL_TEXTURE_2D );
			GL11.glEnable( GL11.GL_DEPTH_TEST );
			GL11.glDisable( GL11.GL_LINE_SMOOTH );
			GL11.glPopMatrix( );
            GL11.glColor4f( 1, 1, 1, 1 );

            GL11.glEndList( );
            dirty = false;
		}
		
		double x = ( ( IRenderManager )Minecraft.getMinecraft( ).getRenderManager( ) ).getRenderPosX( );
		double y = -( ( ( IRenderManager )Minecraft.getMinecraft( ).getRenderManager( ) ).getRenderPosY( ) );
		double z = ( ( IRenderManager )Minecraft.getMinecraft( ).getRenderManager( ) ).getRenderPosZ( );
		
		GL11.glTranslated( -x, y, -z );
		GL11.glCallList( list );
		GL11.glTranslated( x, -y, z );
	}
	
	public static boolean getState( )
	{
		return state;
	}
	
	public static void setState( boolean state )
	{
		if( !state )
		{
			GL11.glDeleteLists( 1, 1 );
			chunks.clear( );
			dirty = true;
		}
		else
		{
			try
			{
				Long2ObjectMap< Chunk > loadedchunks =
						( Long2ObjectMap< Chunk > )chunkMapping.get(
								Minecraft.getMinecraft( ).world.getChunkProvider( ) );
				
				for( Chunk _chunk : loadedchunks.values( ) )
					addChunk( _chunk );
			}
			catch( Exception e )
			{
				e.printStackTrace( );
			}
		}
		
		custom_ChunkRenderer.state = state;
	}
}
