/*
 * This file is part of unsup.
 * Copyright © 2020-2025 Una Kearney (unascribed) and contributors
 * https://git.sleeping.town/unascribed/unsup
 *
 * unsup is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * unsup is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with unsup; if not, see <https://www.gnu.org/licenses/>.
 */

package com.unascribed.sup.puppet;

import java.util.List;

import com.unascribed.sup.data.AlertMessageType;
import com.unascribed.sup.data.FlavorGroup;

public interface PuppetDelegate {

	void build();
	
	void setVisible(boolean visible);
	
	void setProgressIndeterminate();
	void setProgressDeterminate();
	void setDone();
	
	void setProgress(int permil);
	
	void setTitle(String title);
	void setSubtitle(String subtitle);
	
	void offerChangeFlavors(String name);
	void openChoiceDialog(String name, String title, String body, String[] options, String def);
	void openMessageDialog(String name, String title, String body, AlertMessageType messageType, String[] options, String def);
	void openFlavorDialog(String name, List<FlavorGroup> groups);

	void setDownloading(String[] files);
	
}
