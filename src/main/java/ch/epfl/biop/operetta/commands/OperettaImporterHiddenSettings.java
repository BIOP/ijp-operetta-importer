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
package ch.epfl.biop.operetta.commands;

import ij.IJ;
import ij.Prefs;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

/**
 * Settings for the Operetta Importer
 * This factor slightly shrinks the coordinates of the images to ensure that they overlap ever so slightly.
 * The default value was tested for our Operetta and seems to work for most people
 * But it was requested to have it as a parameter.
 */
@SuppressWarnings("FieldMayBeFinal")
@Plugin(type = Command.class, menuPath = "Plugins>BIOP > Operetta Importer > Operetta Importer Settings...")
public class OperettaImporterHiddenSettings implements Command{
    /**
     * Correction factor key for storage in ImageJ prefs
     */
    protected static final String correction_factor = "ch.epfl.biop.operetta.correctionFactor";

    // Parameter for generating a GUI
    @Parameter(label = "XY coordinates correction factor (default is 0.995)", persist = false)
    Double correctionFactor = Prefs.get(correction_factor, 0.995);

    /**
     * All this class does is set the correction factor in the IJ prefs so we can use it.
     */
    @Override
    public void run() {
        Prefs.set(correction_factor, correctionFactor);
        IJ.log("XY coordinates correction factor set to " + correctionFactor);
    }
}
