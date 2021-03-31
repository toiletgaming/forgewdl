package net.futureclient.forgewdl.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import net.futureclient.forgewdl.accessor.IRenderManager;
import net.minecraft.client.renderer.entity.RenderManager;

@Mixin( RenderManager.class )
public abstract class MixinRenderManager implements IRenderManager
{
	@Shadow
	private double renderPosX;
	
	@Shadow
	private double renderPosY;
	
	@Shadow
	private double renderPosZ;
	
	@Override
	public double getRenderPosX( )
	{
		return renderPosX;
	}
	
	@Override
	public double getRenderPosY( )
	{
		return renderPosY;
	}
	
	@Override
	public double getRenderPosZ( )
	{
		return renderPosZ;
	}
}
