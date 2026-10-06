/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

// Adapted for native NeoForge 26.3 on 2026-10-06.

import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;

import net.neoforged.fml.loading.FMLLoader;

public enum BBEModCompat
{
	;
	
	private static final VersionRange BROKEN_BBE_VERSIONS;
	static
	{
		try
		{
			BROKEN_BBE_VERSIONS =
				VersionRange.createFromVersionSpec("[1.3.2,1.3.4)");
		}catch(InvalidVersionSpecificationException e)
		{
			throw new ExceptionInInitializerError(e);
		}
	}
	
	public static boolean isBrokenBBEInstalled()
	{
		// This also runs during mixin selection, before runtime ModList exists.
		return FMLLoader.getCurrent().getLoadingModList().getMods().stream()
			.filter(mod -> mod.getModId().equals("betterblockentities"))
			.anyMatch(
				mod -> BROKEN_BBE_VERSIONS.containsVersion(mod.getVersion()));
	}
}
