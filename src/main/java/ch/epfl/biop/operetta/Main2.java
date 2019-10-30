package ch.epfl.biop.operetta;

import ch.epfl.biop.operetta.commands.utils.TiledCellReader;
import ij.ImageJ;
import ij.ImageStack;
import net.imagej.ImgPlus;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import ome.xml.model.Well;
import org.perf4j.StopWatch;

import java.io.File;

@Deprecated
public class Main2 {

    public static void main( String[] args ) {
        new ImageJ( );
        //File id = new File("E:\\Sonia - TRONO\\DUX__2019-05-24T12_42_31-Measurement 1\\Images\\Index.idx.xml");
        File id = new File( "X:\\Tiling\\Opertta Tiling Magda\\Images\\Index.idx.xml" );

        OperettaManager op = new OperettaManager.Builder( )
                .setId( id )
                .doProjection( true )
                .setSaveFolder( new File( "D:\\Demo" ) )
                .build( );

        StopWatch sw = new StopWatch( );
        sw.start( );
        //ImageStack s = op.readStack(1, 1);
        //Roi region = new Roi(1280, 3680, 5184, 2304);
        //Roi region = new Roi( 100, 100, 500, 500 );
        op.process( 4, null, false );

        //Roi region = null;

        ImageStack s = null;
        //op.updateZRange("1");

        System.out.println("Time to open one well: "+sw.stop( ) );
    }
}
