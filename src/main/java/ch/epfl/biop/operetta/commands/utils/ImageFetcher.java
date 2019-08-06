package ch.epfl.biop.operetta.commands.utils;

// This class should return the file that corresponds to the desired well, field, channel, slice and frame. could return the image

import ch.epfl.biop.operetta.OperettaManager;
import ch.epfl.biop.operetta.utils.FCZT;
import ij.IJ;
import ij.ImagePlus;
import ij.process.FloatProcessor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import ome.xml.model.Well;
import ome.xml.model.WellSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Hashtable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ImageFetcher{

    private static Logger logger = LoggerFactory.getLogger( ImageFetcher.class );
    Pattern operetta_pattern = Pattern.compile(".*r(\\d*)c(\\d*)f(\\d*)p(\\d*)-ch(\\d*)sk(\\d*)fk(\\d*).*");

    Hashtable<FCZT, String> files_hash;

    double min_scale;
    double max_scale;


    public ImageFetcher(OperettaManager opm) {
        this.max_scale = opm.getNorm_max();
        this.min_scale = opm.getNorm_min();

        generateHash(opm);
    }

    private void generateHash( OperettaManager opm ) {

        this.files_hash = new Hashtable<>( opm.getReader().getUsedFiles(true).length );


        opm.getAvailableWells().stream().forEach( w -> {
            w.copyWellSampleList( ).stream( ).forEach( s -> {
                opm.getReader( ).setSeries( s.getIndex( ).getValue( ) );
                Arrays.stream( opm.getReader( ).getSeriesUsedFiles( true ) ).parallel( ).forEach( f -> {
                    // Parse the file name
                    Matcher m = operetta_pattern.matcher( f );
                    if ( m.matches( ) ) {
                        int wr = Integer.parseInt( m.group( 1 ) );
                        int wc = Integer.parseInt( m.group( 2 ) );
                        int fi = Integer.parseInt( m.group( 3 ) );

                        int ci = Integer.parseInt( m.group( 5 ) );
                        int zi = Integer.parseInt( m.group( 4 ) );
                        int ti = Integer.parseInt( m.group( 6 ) );
                        FCZT h = new FCZT( s.getIndex().getValue(), ci-1, zi-1, ti-1 );
                        files_hash.put( h, f );
                    }
                } );
            } );
        });

    }


    public RandomAccessibleInterval<UnsignedShortType> getImageFile( WellSample field, int c, int z, int t ) {
        String the_file = this.files_hash.get( new FCZT(field.getIndex().getValue(), c, z, t ));
        if (the_file == null ){
            Well well = field.getWell( );
            logger.warn( "Well R{}C{} has no image at c{} z{} t{} for Fields {}", well.getRow(), well.getColumn(), c,z,t, field.getID() );

        }
        ImagePlus imp = IJ.openImage( the_file );

        if (imp.getProcessor() instanceof FloatProcessor ) {
            imp.getProcessor().setMinAndMax( min_scale, max_scale );
            imp.setProcessor( imp.getProcessor().convertToShortProcessor( true ) );
        }

        RandomAccessibleInterval<UnsignedShortType> img = ImageJFunctions.wrap( imp );

        return img;

    }
}
