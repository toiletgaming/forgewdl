/*
 * This file is part of the World Downloader API.
 * http://www.minecraftforum.net/forums/mapping-and-modding/minecraft-mods/2520465
 *
 * Copyright (c) 2017 Pokechu22, julialy
 *
 * This project is licensed under the MMPLv2.  The full text of the MMPL can be
 * found in LICENSE.md, or online at https://github.com/iopleke/MMPLv2/blob/master/LICENSE.md
 * For information about this the MMPLv2, see http://stopmodreposts.org/
 *
 * You are free to include the World Downloader API within your own mods, as
 * permitted via the MMPLv2.
 */
package wdl.api;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.storage.SaveHandler;

/**
 * {@link IWDLMod} that edits the player info NBT file.
 */
public interface IPlayerInfoEditor extends IWDLMod {
	/**
	 * Edits the world info NBT before it is saved.
	 *
	 * @param player
	 *            The player that is being saved ({@link wdl.WDL#thePlayer})
	 * @param saveHandler
	 *            The current saveHandler ({@link wdl.WDL#saveHandler}).
	 * @param tag
	 *            The current {@link NBTTagCompound} that is being saved. Edit
	 *            or add info to this.
	 */
	public abstract void editPlayerInfo(EntityPlayerSP player,
			SaveHandler saveHandler, NBTTagCompound tag);
}
