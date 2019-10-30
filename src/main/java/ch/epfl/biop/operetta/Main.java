package ch.epfl.biop.operetta;

import net.imagej.ImageJ;

@Deprecated
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
