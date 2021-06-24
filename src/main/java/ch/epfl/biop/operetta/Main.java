/*-
 * #%L
 * Hold your horses
 * %%
 * Copyright (C) 2019 - 2021 BIOP
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

@Deprecated
public class Main {

    public static void main(String[] args) {
        // Creates the image
        ImageJ ij = new ImageJ();
        ij.ui().showUI();

            /*OperettaManager opm = new OperettaManager.Builder( )
                    .setId( new File( "X:\\Tiling\\Opertta Tiling Magda\\Images\\Index.idx.xml" ) )
                    .build( );


            // Take the first well.
            Well well = opm.getAvailableWells( ).get( 0 );
            ImgPlus<UnsignedShortType> image = TiledCellReader.createLazyImage( opm, well, 16 );


            //ij.ui( ).show( image );

            ImageJFunctions.wrap(image, "test" ).show();

             */
    }
}
