package ch.epfl.biop.operetta;

import ch.epfl.biop.operetta.commands.utils.TiledCellReader;
import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import ome.xml.model.Well;

import java.io.File;

public class Main {

        public static void main( String[] args ) {
            // Creates the image
            ImageJ ij = new ImageJ( );
            ij.ui( ).showUI( );

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
