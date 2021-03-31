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

import net.minecraft.entity.Entity;

import com.google.common.collect.Multimap;

/**
 * Interface for WDL mods that handle entities that have non-standard purposes. <br/>
 * This is for holograms and such that are vanilla minecraft entities being
 * (ab)used for new purposes. Use {@link IEntityAdder} for new entities.
 *
 * @deprecated Use {@link IEntityManager} instead
 */
@Deprecated
public interface ISpecialEntityHandler extends IWDLMod {
	/**
	 * Gets the special entities handled by this mod.
	 *
	 * @return A map of vanilla entity name to special entity names. Key is the
	 *         vanilla entity, values are the overriding entities.
	 */
	public abstract Multimap<String, String> getSpecialEntities();

	/**
	 * Gets the name for the given special entity, if it is one. If it is not a
	 * special entity handled by this mod, return <code>null</code>. <br/>
	 * The given entity will always have its name be one of the keys in the map
	 * from {@link #getSpecialEntities()}, and the return value should be in the
	 * list from the value of that map for said key (or <code>null</code>).
	 *
	 * @param entity
	 *            The entity to use.
	 * @return The special entity name, or null if it is not a special entity
	 *         for this mod.
	 */
	public abstract String getSpecialEntityName(Entity entity);

	/**
	 * Gets the category for the given special entity. <br/>
	 * The given name will always be in the list of values for one of the keys
	 * of {@link #getSpecialEntities()}. <br/>
	 * The category is used in the entities gui.
	 *
	 * @param name
	 *            The name of the special entity.
	 * @return The category.
	 */
	public abstract String getSpecialEntityCategory(String name);

	/**
	 * Gets the default track distance for the given entity.<br/>
	 * The given name will always be in the list of values for one of the keys
	 * of {@link #getSpecialEntities()}.
	 *
	 * @param name The name of the entity.
	 * @return The track distance, or -1 to use the default one.
	 */
	public abstract int getSpecialEntityTrackDistance(String name);
}
