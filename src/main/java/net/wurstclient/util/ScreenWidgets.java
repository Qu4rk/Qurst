/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import java.util.List;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

/** Native widget enumeration for Wurst screens and vanilla screen mixins. */
public enum ScreenWidgets
{
	;
	
	public static List<AbstractWidget> getWidgets(Screen screen)
	{
		return screen.children().stream()
			.filter(AbstractWidget.class::isInstance)
			.map(AbstractWidget.class::cast).toList();
	}
}
