/*-
 * #%L
 * Hold your horses
 * %%
 * Copyright (C) 2019 - 2023 BIOP
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package ch.epfl.biop.operetta.commands.utils;

import ch.epfl.biop.operetta.OperettaManager;
import org.scijava.plugin.Plugin;
import org.scijava.ui.swing.viewer.EasySwingDisplayViewer;
import org.scijava.ui.viewer.DisplayViewer;

import javax.swing.*;

@Plugin(type = DisplayViewer.class)
public class OperettaManagerViewer extends EasySwingDisplayViewer<OperettaManager> {

    public OperettaManagerViewer() {
        super(OperettaManager.class);
    }

    @Override
    protected boolean canView(OperettaManager value) {
        return true;
    }

    @Override
    protected void redoLayout() {

    }

    @Override
    protected void setLabel(String s) {

    }

    @Override
    protected void redraw() {

    }

    @Override
    protected JPanel createDisplayPanel(OperettaManager value) {
        final JPanel panel = new JPanel();
        final String fileName = value.toString();
        JLabel labelName = new JLabel(fileName);
        panel.add(labelName);
        return panel;
    }
}
