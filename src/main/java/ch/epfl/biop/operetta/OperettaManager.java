package ch.epfl.biop.operetta;

import ch.epfl.biop.operetta.utils.CZTRange;
import ch.epfl.biop.operetta.utils.FieldCoordinates;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.plugin.HyperStackConverter;
import ij.plugin.ZProjector;
import ij.process.Blitter;
import ij.process.ImageProcessor;
import loci.formats.FormatException;
import loci.formats.IFormatReader;
import loci.formats.Memoizer;
import loci.formats.MetadataTools;
import loci.formats.in.OperettaReader;
import loci.formats.meta.IMetadata;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.quantity.Time;
import org.perf4j.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class OperettaManager {

    private static final Logger logger = LoggerFactory.getLogger( OperettaManager.class );
    private final File id;
    private final IFormatReader main_reader;

    private IMetadata metadata;

    private List<IFormatReader> readers;
    private int n_readers;

    private CZTRange range;

    private double norm_min;
    private double norm_max;

    private boolean is_projection;
    private int projection_type;

    private File save_folder;

    private OperettaManager( IFormatReader reader,
                             CZTRange range,
                             int n_readers,
                             double norm_min,
                             double norm_max,
                             boolean is_projection,
                             int projection_type,
                             File save_folder) {

        this.id = new File( reader.getCurrentFile( ) );
        this.main_reader = reader;
        this.metadata = (IMetadata) reader.getMetadataStore( );
        this.range = range;
        this.n_readers = n_readers;
        this.norm_max = norm_max;
        this.norm_min = norm_min;
        this.is_projection = is_projection;
        this.projection_type = projection_type;
        this.save_folder = save_folder;
        createReaders( );

    }

    private static IFormatReader createReader( final String id ) throws IOException, FormatException {

        final IFormatReader imageReader = new OperettaReader( );

        Memoizer memo = new Memoizer( imageReader );

        IMetadata omeMeta = MetadataTools.createOMEXMLMetadata( );
        memo.setMetadataStore( omeMeta );
        memo.setId( id );

        return memo;
    }

    public static void main( String[] args ) {
        new ImageJ( );
        //File id = new File("E:\\Sonia - TRONO\\DUX__2019-05-24T12_42_31-Measurement 1\\Images\\Index.idx.xml");
        File id = new File( "X:\\Tiling\\Opertta Tiling Magda\\Images\\Index.idx.xml" );

        OperettaManager op = new Builder( )
                .setId( id )
                .doProjection( true )
                .setSaveFolder( new File("D:\\Demo") )
                .build( );

        StopWatch sw = new StopWatch( );
        sw.start( );
        //ImageStack s = op.readStack(1, 1);
        //Roi region = new Roi(1280, 3680, 5184, 2304);
        //Roi region = new Roi( 100, 100, 500, 500 );
        op.process( 4, null, true );

        //Roi region = null;

        ImageStack s = null;
        //op.updateZRange("1");

        //op.getWellImage( 2, 2, 1, region ).show( );

        logger.info( "Time to open one well: {}", sw.stop( ) );
    }

    private void createReaders( ) {

        // Create multiple readers
        this.readers = IntStream.range( 0, n_readers ).parallel( ).mapToObj( i -> {
            IFormatReader r = new OperettaReader( );

            IFormatReader memo2 = new Memoizer( r );

            IMetadata meta = MetadataTools.createOMEXMLMetadata( );
            memo2.setMetadataStore( meta );
            try {
                memo2.setId( this.id.getAbsolutePath( ) );
            } catch ( FormatException e ) {
                e.printStackTrace( );
            } catch ( IOException e ) {
                e.printStackTrace( );
            }

            return memo2;
        } ).collect( Collectors.toList( ) );

    }

    public void updateCRange( String new_cs ) {
        range.updateCRange( new_cs );
    }

    public void updateZRange( String new_zs ) {
        range.updateZRange( new_zs );
    }

    public void updateTRange( String new_ts ) {
        range.updateTRange( new_ts );
    }

    /**
     * Getting an ImagePlus from single Wells
     */
    // All the get*Image should use readSingleStack or readSingleWell
    public ImagePlus getWellImage( int row, int column ) {
        int well_index = getWellIndex( row, column );

        return makeImagePlus( readSingleWell( well_index, 1, this.range, null ), well_index );
    }

    // All the get*Image should use readSingleStack or readSingleWell
    public ImagePlus getWellImage( int row, int column, int downscale ) {
        int well_index = getWellIndex( row, column );

        return makeImagePlus( readSingleWell( well_index, downscale, this.range, null ), well_index );
    }

    // All the get*Image should use readSingleStack or readSingleWell
    public ImagePlus getWellImage( int row, int column, int downscale, Roi subregion ) {
        int well_index = getWellIndex( row, column );

        return makeImagePlus( readSingleWell( well_index, downscale, this.range, subregion ), well_index );
    }

    public ImagePlus getWellImage( int row, int column, int downscale, CZTRange range, Roi subregion ) {
        int well_index = getWellIndex( row, column );

        return makeImagePlus( readSingleWell( well_index, downscale, range, subregion ), well_index );
    }

    // Make Subset of what we want (including stacks if crops are happening in fields)

    /**
     * Getting an ImagePlus from single Fields
     */
    // All the get*Image should use readSingleStack or readSingleWell
    public ImagePlus getFieldImage( int row, int column, int field ) {
        int well_index = getWellIndex( row, column );

        return makeImagePlus( readSingleStack( well_index, field, 1, this.range, null ), well_index );
    }

    // All the get*Image should use readSingleStack or readSingleWell
    public ImagePlus getFieldImage( int row, int column, int field, int downscale ) {
        int well_index = getWellIndex( row, column );

        return makeImagePlus( readSingleStack( well_index, field, downscale, this.range, null ), well_index );
    }

    // All the get*Image should use readSingleStack or readSingleWell
    public ImagePlus getFieldImage( int row, int column, int field, int downscale, Roi subregion ) {
        int well_index = getWellIndex( row, column );

        return makeImagePlus( readSingleStack( well_index, field, downscale, this.range, subregion ), well_index );
    }

    public ImagePlus getFieldImage( int row, int column, int field, int downscale, CZTRange range, Roi subregion ) {
        int well_index = getWellIndex( row, column );

        return makeImagePlus( readSingleStack( well_index, field, downscale, range, subregion ), well_index );
    }

    // Make Subset of what we want (including stacks if crops are happening in fields)
    public ImageStack readSingleStack( int well_index, int field_index, final int downscale, CZTRange range, final Roi subregion ) {

        final int series_id = metadata.getWellSampleIndex( 0, well_index, field_index ).getValue( ).intValue( );

        final int row = metadata.getWellRow( 0, well_index ).getValue( );
        final int column = metadata.getWellRow( 0, well_index ).getValue( );

        metadata.getPlateColumnNamingConvention( 0 );
        main_reader.setSeries( series_id );

        final CZTRange range2 = range.confirmRange( metadata );

        final int n = range2.getTotalPlanes( );

        boolean do_norm = main_reader.getBitsPerPixel( ) != 16;

        int stack_width = main_reader.getSizeX( );
        int stack_height = main_reader.getSizeY( );


        if ( subregion != null ) {
            stack_width = subregion.getBounds( ).width;
            stack_height = subregion.getBounds( ).height;

        }

        if ( ( stack_height / downscale ) <= 1 || ( stack_width / downscale ) <= 1 ) return null;


        final ImageStack stack = ImageStack.create( stack_width / downscale, stack_height / downscale, n, 16 );

        // Get All files associated with this series
        List<String> files = Arrays.asList( main_reader.getSeriesUsedFiles( false ) )
                .stream( )
                .filter( f -> f.endsWith( ".tiff" ) )
                .collect( Collectors.toList( ) );
        StopWatch sw = new StopWatch( );
        sw.start( );

        ForkJoinPool planeWorkerPool = new ForkJoinPool( n_readers );
        try {
            planeWorkerPool.submit( ( ) -> IntStream.range( 0, files.size( ) )
                    .parallel( )
                    .forEach( i -> {
                        // Check that we want to open it
                        // Infer C Z T from filename
                        Map<String, Integer> plane_indexes = range2.getIndexes( files.get( i ) );
                        if ( range2.includes( files.get( i ) ) ) {
                            ImagePlus imp = IJ.openImage( files.get( i ) );
                            ImageProcessor ip = imp.getProcessor( );
                            if ( do_norm ) {
                                ip.setMinAndMax( norm_min, norm_max );
                                ip = ip.convertToShort( true );
                            }
                            if ( subregion != null ) {
                                ip.setRoi( subregion );
                                ip = ip.crop( );
                            }

                            ip = ip.resize( ip.getWidth( ) / downscale, ip.getHeight( ) / downscale );
                            // logger.info("File {}", files.get( i ));
                            String label = String.format( "R%d-C%d - (c:%d, z:%d, t:%d) - %s", row, column, plane_indexes.get( "C" ), plane_indexes.get( "Z" ), plane_indexes.get( "T" ), new File( files.get( i ) ).getName( ) );

                            stack.setProcessor( ip, plane_indexes.get( "I" ) );
                            stack.setSliceLabel( label, plane_indexes.get( "I" ) );
                            imp.close( );
                            // new ImagePlus("", stack).show();
                        }
                    } ) ).get( );
        } catch ( InterruptedException e ) {
            logger.error( "Reading Stack {} interrupted: {}", series_id, e );
        } catch ( ExecutionException e ) {
            logger.error( "Reading Stack {} error: {}", series_id, e );
        }
        sw.stop( );
        logger.info( "Well {} stack {} took {} seconds", well_index, series_id, (double) sw.getElapsedTime( ) / 1000.0 );
        return stack;
    }

    public int getWellIndex( int row, int column ) {
        List<Integer> wellIdx = IntStream.range( 0, metadata.getWellCount( 0 ) ).boxed( ).filter( w -> metadata.getWellColumn( 0, w ).getValue( ) == ( column ) && metadata.getWellRow( 0, w ).getValue( ) == ( row ) ).collect( Collectors.toList( ) );

        if ( wellIdx.size( ) > 0 ) return wellIdx.get( 0 );

        return -1;
    }

    public ImageStack readSingleWell( final int well_index, final int downscale, CZTRange range, final Roi bounds ) {

        // Need pixel coordinates...
        // Need bounds of the full image to generate the final tile and to figure out overlaps with rois if any
        int x = metadata.getPixelsSizeX( 0 ).getValue( );
        int y = metadata.getPixelsSizeY( 0 ).getValue( );

        // For the well, we should first procure all the images we want and open them directly...


        final CZTRange range2 = range.confirmRange( metadata );

        final int n = range2.getTotalPlanes( );

        // Get the positions for each field (called a sample by BioFormats) in this well
        List<FieldCoordinates> coordinates = getAllWellFieldCoordinates( well_index );

        // Out of these coordinates, keep only those that are intersecting with an ROI
        final List<FieldCoordinates> effective_coordinates = getEffectiveFields( coordinates, bounds );

        if (effective_coordinates.size() == 0 ) return null;
        // Get extents for the final image
        final int minx = effective_coordinates.stream( ).min( Comparator.comparing( FieldCoordinates::getXCoordinate ) ).get( ).getCoordinates( ).x;
        final int miny = effective_coordinates.stream( ).min( Comparator.comparing( FieldCoordinates::getYCoordinate ) ).get( ).getCoordinates( ).y;

        final int maxx = effective_coordinates.stream( ).max( Comparator.comparing( FieldCoordinates::getXCoordinate ) ).get( ).getCoordinates( ).x;
        final int maxy = effective_coordinates.stream( ).max( Comparator.comparing( FieldCoordinates::getYCoordinate ) ).get( ).getCoordinates( ).y;

        // From now on the image will start at 0,0, the bounds themselves, so we must take that into account

        int finalW = ( maxx - minx + x ) / downscale;
        int finalH = ( maxy - miny + y ) / downscale;
        if ( bounds != null ) {
            finalW = bounds.getBounds( ).width / downscale;
            finalH = bounds.getBounds( ).height / downscale;
        }

        final ImageStack wellStack = ImageStack.create( finalW, finalH, n, 16 );
        StopWatch sw = new StopWatch( );
        sw.start( "Well Processing" );
        AtomicInteger ai = new AtomicInteger( 0 );
        effective_coordinates.stream( ).forEachOrdered( f -> {
            final int field_counter = ai.getAndIncrement( );
            Roi subregion = getFieldSubRegion( bounds, f );

            final ImageStack stack = readSingleStack( well_index, f.getField( ), downscale, range2, subregion );
            final Point pos = f.getCoordinates( );

            // modify position of copy as needed
            if ( bounds != null )
                pos.translate( subregion.getBounds( ).x - bounds.getBounds( ).x, subregion.getBounds( ).y - bounds.getBounds( ).y );
            pos.setLocation( ( pos.x ) / downscale, ( pos.y ) / downscale );
            logger.info( sw.lap( "Opening Stack" ) );
            if ( stack != null ) {
                for ( int s = 0; s < stack.size( ); s++ ) {
                    wellStack.getProcessor( s + 1 ).copyBits( stack.getProcessor( s + 1 ), pos.x, pos.y, Blitter.COPY );
                    wellStack.setSliceLabel( stack.getSliceLabel( s + 1 ), s + 1 );
                }
                logger.info( sw.lap( "Copying Stack to Well" ) );

                logger.info( "Field {} of {} Copied to Well", field_counter + 1, effective_coordinates.size( ) );
            }
        } );


        return wellStack;

    }

    private ImagePlus makeImagePlus( ImageStack stack, int well_index ) {
        if (stack == null) return null;
        // Get the dimensions
        int[] czt = this.range.getCZTDimensions( );

        double px_size = 1;
        double px_depth = 1;
        String v_unit = "pixel";
        double px_time = 1;
        String time_unit = "sec";

        // Try to get the Pixel Sizes
        Length apx_size = metadata.getPixelsPhysicalSizeX( 0 );
        if ( apx_size != null ) {
            px_size = apx_size.value( UNITS.MICROMETER ).doubleValue( );
            v_unit = UNITS.MICROMETER.getSymbol( );
        }
        // Try to get the Pixel Sizes
        Length apx_depth = metadata.getPixelsPhysicalSizeZ( 0 );
        if ( apx_depth != null ) {
            px_depth = apx_depth.value( UNITS.MICROMETER ).doubleValue( );
        }

        Time apx_time = metadata.getPixelsTimeIncrement( 0 );
        if ( apx_time != null ) {
            px_time = apx_time.value( UNITS.MILLISECOND ).doubleValue( );
            time_unit = UNITS.MILLISECOND.getSymbol( );
        }

        String name = getFinalWellImageName( well_index );

        ImagePlus result = HyperStackConverter.toHyperStack( new ImagePlus( name, stack ), czt[ 0 ], czt[ 1 ], czt[ 2 ] );

        Calibration cal = new Calibration( result );
        cal.pixelWidth = px_size;
        cal.pixelHeight = px_size;
        cal.pixelDepth = px_depth;
        cal.frameInterval = px_time;
        cal.setXUnit( v_unit );
        cal.setYUnit( v_unit );
        cal.setZUnit( v_unit );
        cal.setTimeUnit( time_unit );

        // Do the projection if needed
        if ( this.is_projection ) {
            ZProjector zp = new ZProjector( );
            zp.setImage( result );
            zp.setMethod( this.projection_type );
            zp.setStopSlice( result.getNSlices( ) );
            if ( result.getNSlices( ) > 1 ) {
                zp.doHyperStackProjection( true );
                result = zp.getProjection( );
            }
        }
        result.setCalibration( cal );
        return result;

    }


    private List<FieldCoordinates> getAllWellFieldCoordinates( int well_index ) {

        int nFields = metadata.getWellSampleCount( 0, well_index );
        double px_size = metadata.getPixelsPhysicalSizeX( 0 ).value( UNITS.MICROMETER ).doubleValue( );

        ArrayList<FieldCoordinates> coordinates = new ArrayList<>( );

        // Iterate through all the fields
        for ( int f1 = 0; f1 < nFields; f1++ ) {
            int current_idx = metadata.getWellSampleIndex( 0, well_index, f1 ).getValue( );
            int nPlanesPerField = metadata.getPlaneCount( current_idx );

            // Because the position might be missing from metadata.getWellSamplePositionX(), we need to look if there is a valid value per plane
            Optional<Integer> nonNullPositionX = IntStream.range( 0, nPlanesPerField ).boxed( ).filter( i -> metadata.getPlanePositionX( current_idx, i ) != null ).findAny( );
            Optional<Integer> nonNullPositionY = IntStream.range( 0, nPlanesPerField ).boxed( ).filter( i -> metadata.getPlanePositionY( current_idx, i ) != null ).findAny( );

            // We found them, so we can add them to out position list
            if ( nonNullPositionX.isPresent( ) && nonNullPositionY.isPresent( ) ) {
                int posX = (int) Math.floor( metadata.getPlanePositionX( current_idx, nonNullPositionX.get( ) ).value( UNITS.MICROMETER ).doubleValue( ) / px_size * 0.992 );
                int posY = (int) Math.floor( metadata.getPlanePositionY( current_idx, nonNullPositionY.get( ) ).value( UNITS.MICROMETER ).doubleValue( ) / px_size * 0.992 );

                coordinates.add(
                        new FieldCoordinates(
                                f1,
                                new Point( posX, posY ),
                                metadata.getPixelsSizeX( current_idx ).getValue( ),
                                metadata.getPixelsSizeY( current_idx ).getValue( ) )
                );
            }
        }

        int s = coordinates.size();
        // It could be that he finds nothing
        if (coordinates.size() == 0 ) return coordinates;

        //Recenter all coordinates to 0
        int raw_minx = coordinates.stream( ).min( Comparator.comparing( FieldCoordinates::getXCoordinate ) ).get( ).getXCoordinate( );
        int raw_miny = coordinates.stream( ).min( Comparator.comparing( FieldCoordinates::getYCoordinate ) ).get( ).getYCoordinate( );

        coordinates.forEach( k -> k.getCoordinates( ).translate( -raw_minx, -raw_miny ) );

        return coordinates;
    }

    private Roi getFieldSubRegion( Roi bounds, FieldCoordinates f ) {
        // The field always contains the subregion so we avoid checking for overlap
        int x, y, w, h;
        x = 0;
        y = 0;

        w = f.getWidth( );
        h = f.getHeight( );

        if ( bounds != null ) {

            if ( bounds.getBounds( ).x > f.getCoordinates( ).x ) {
                x = bounds.getBounds( ).x - f.getCoordinates( ).x;
                w = w - ( x );
            }

            if ( bounds.getBounds( ).y > f.getCoordinates( ).y ) {
                y = bounds.getBounds( ).y - f.getCoordinates( ).y;
                h = h - ( y );
            }
        }
        return new Roi( x, y, w, h );


    }

    private List<FieldCoordinates> getEffectiveFields( List<FieldCoordinates> coordinates, Roi bounds ) {
        // Coordinates are in pixels
        // bounds are in pixels
        logger.debug( "Looking for fields that are included in {}", bounds );

        List<FieldCoordinates> selected = coordinates.stream( ).filter( c -> {
            if ( bounds == null ) return true;

            Roi other = new Roi( c.getXCoordinate( ), c.getYCoordinate( ), c.getWidth(), c.getHeight() );

            return isOverlapping( bounds, other );
        } ).collect( Collectors.toList( ) );
        return selected;
    }

    public String getFinalFieldImageName( int well_index, int field_index ) {
        int row = metadata.getWellRow( 0, well_index ).getValue( );
        int col = metadata.getWellColumn( 0, well_index ).getValue( );
        String project = metadata.getPlateName( 0 );

        String name = String.format( "%s - R%d-C%d-F%d", project, row, col, field_index+1 );

        if ( this.is_projection )
            name += "_Projected";
        return name;

    }

    public String getFinalWellImageName( int well_index ) {
        int row = metadata.getWellRow( 0, well_index ).getValue( );
        int col = metadata.getWellColumn( 0, well_index ).getValue( );
        String project = metadata.getPlateName( 0 );

        String name = String.format( "%s - R%d-C%d", project, row, col );

        if ( this.is_projection )
            name += "_Projected";
        return name;

    }

    // TODO: Format for selecting valid wells and fields
    public void process( int downscale, Roi region, boolean is_fields_individual) {
        process( downscale, region, is_fields_individual, null, null);
    }

    public void process( int downscale, Roi region, boolean is_fields_individual, List<Integer> well_indexes, List<Integer> field_indexes ) {
        // Process everything
        // decide whether we process wells or fields
        if (well_indexes == null ) {
            well_indexes = IntStream.range( 0, metadata.getWellCount( 0 )).boxed().collect( Collectors.toList());
        }

        for (int well: well_indexes) {
            logger.info( "Well Index: {}", well );
            List<FieldCoordinates> fc = getEffectiveFields( getAllWellFieldCoordinates( well ), region);

            if (field_indexes == null) {
                field_indexes = fc.stream().map( f -> f.getField() ).collect( Collectors.toList());
            }

            int row = metadata.getWellRow( 0, well ).getValue( );
            int col = metadata.getWellColumn( 0, well ).getValue( );

            if ( is_fields_individual ) {
                for ( int field : field_indexes ) {
                    ImagePlus field_image = getFieldImage( row, col, field, downscale, this.range, region );
                    String name = getFinalFieldImageName( well, field );
                    if (field_image != null)
                        IJ.saveAsTiff( field_image, new File( save_folder, name + ".tif" ).getAbsolutePath() );
                }
                // Save the positions file
                // Get the positions that were used, just compute them again
                try {
                    writeWellPositionsFile( well, fc, new File(save_folder, getFinalWellImageName( well )+".txt"), downscale );
                } catch ( IOException e ) {
                    e.printStackTrace( );
                }

            } else {
                ImagePlus well_image = getWellImage( row, col, downscale, this.range, region );
                String name = getFinalWellImageName( well );
                if (well_image != null)
                    IJ.saveAsTiff( well_image, new File( save_folder, name + ".tif" ).getAbsolutePath() );
            }
        }

    }

    public void writeWellPositionsFile( int well_index, List<FieldCoordinates> fc, File position_file, int downscale ) throws IOException {
        int dim = range.getRangeZ().size( ) > 1 && !is_projection ? 3 : 2;

        String z = dim == 3 ? ", 0.0" : "";

        Path path = Paths.get( position_file.getAbsolutePath( ) );

        //Use try-with-resource to get auto-closeable writer instance
        try ( BufferedWriter writer = Files.newBufferedWriter( path ) ) {
            writer.write( "#Define the number of dimensions we are working on:\n" );
            writer.write( "dim = " + dim + "\n" );
            writer.write( "# Define the image coordinates\n" );
            writer.write( "#Define the number of dimensions we are working on:\n" );

            for ( FieldCoordinates p : fc ) {
                String name = getFinalFieldImageName( well_index, p.getField( ) );
                writer.write( String.format( "%s.tif;      ;               (%d.0, %d.0%s)\n", name, p.getXCoordinate( ) / downscale, p.getYCoordinate( ) / downscale, z ) );
            }
        }
    }

    private boolean isOverlapping( Roi one, Roi other ) {
        return one.getBounds( ).intersects( other.getBounds( ) );
    }

    @Override
    public String toString( ) {
        return "Operetta Reader on File " + this.id.getName( );
    }

    public static class Builder {

        private File id = null;

        private int n_readers = 10;

        private double norm_min = 0;
        private double norm_max = Math.pow( 2, 16 );

        private CZTRange range = null;

        private boolean is_projection = false;
        private int projection_method = ZProjector.MAX_METHOD;

        private File save_folder = new File( System.getProperty("user.home") );

        public Builder doProjection( boolean do_projection ) {
            this.is_projection = do_projection;
            return this;
        }

        public Builder setProjectionMethod( String method ) {
            if ( Arrays.asList( ZProjector.METHODS ).contains( method ) )
                this.projection_method = Arrays.asList( ZProjector.METHODS ).indexOf( method );
            return this;
        }

        public Builder setNReaders( int n_readers ) {
            this.n_readers = n_readers;
            return this;
        }

        public Builder setNormalization( int min, int max ) {
            this.norm_min = min;
            this.norm_max = max;
            return this;
        }

        public Builder setId( File id ) {
            this.id = id;
            return this;
        }

        public Builder setRange( CZTRange range ) {
            this.range = range;
            return this;
        }

        public Builder setSaveFolder( File save_folder) {
            save_folder.mkdirs();
            this.save_folder = save_folder;
            return this;
        }

        public OperettaManager build( ) {

            File id = this.id;
            IFormatReader reader = null;

            try {
                reader = OperettaManager.createReader( id.getAbsolutePath( ) );

                // Assume we want the whole range for this data
                if ( this.range == null ) {
                    this.range = new CZTRange.Builder( ).fromMetadata( (IMetadata) reader.getMetadataStore( ) ).build( );
                }

                return new OperettaManager( reader,
                        this.range,
                        this.n_readers,
                        this.norm_min,
                        this.norm_max,
                        this.is_projection,
                        this.projection_method,
                        this.save_folder);

            } catch ( Exception e ) {
                logger.error( "Issue when creating reader for file {}: {}", id, e.toString( ) );
                return null;
            }

        }

    }
}