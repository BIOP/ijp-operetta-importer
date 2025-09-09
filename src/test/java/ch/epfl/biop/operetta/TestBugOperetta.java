/*-
 * #%L
 * Hold your horses
 * %%
 * Copyright (C) 2019 - 2025 BIOP
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
package ch.epfl.biop.operetta;

import net.imagej.ImageJ;

import java.time.Instant;

/**
 * Test class for Operetta related bugs
 */
public class TestBugOperetta {
    /**
     * Test to check if the parsing of the Operetta XML is correct
     * @param args Not used
     * @throws Exception If something goes wrong in the OME XML parsing
     */
    public static void main(String... args) throws Exception {


        ImageJ ij = new ImageJ();
        ij.ui().showUI();


    }
}
