package ch.epfl.biop.operetta;

import ch.epfl.biop.operetta.utils.CZTRange;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Overlay;
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
import ome.xml.meta.OMEXMLMetadataRoot;
import ome.xml.model.Well;
import ome.xml.model.WellSample;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class OperettaManager {

    private static Logger log = LoggerFactory.getLogger( OperettaManager.class );

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

    private Length px_size;

    private double correction_factor = 0.995;

    public static void main( String[] args ) {
        new ImageJ( );
        //File id = new File("E:\\Sonia - TRONO\\DUX__2019-05-24T12_42_31-Measurement 1\\Images\\Index.idx.xml");
        File id = new File( "X:\\Tiling\\Opertta Tiling Magda\\Images\\Index.idx.xml" );

        OperettaManager op = new Builder( )
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

        log.info( "Time to open one well: {}", sw.stop( ) );
    }

    public long[] getWellTileSize( Well well, int downscale, Roi subregion ) {
        // Get Stack width and height and modify in case there is a subregion

        int stack_width = main_reader.getSizeX( );
        int stack_height = main_reader.getSizeY( );

        if ( subregion != null ) {
            stack_width = subregion.getBounds( ).width;
            stack_height = subregion.getBounds( ).height;
        }

        // Account for downscaling
        stack_width /= downscale;
        stack_height /= downscale;

        // Leave in case the final stack ended up too small
        if ( stack_height <= 1 || stack_width <= 1 ) return null;

        return new long[] {stack_width, stack_height};
    }

    private OperettaManager( IFormatReader reader,
                             CZTRange range,
                             int n_readers,
                             double norm_min,
                             double norm_max,
                             boolean is_projection,
                             int projection_type,
                             File save_folder ) {

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

        this.px_size = metadata.getPixelsPhysicalSizeX( 0 );

    }

    public IFormatReader getReader() {
        return this.main_reader;
    }

    public static class Builder {

        private File id = null;

        private int n_readers = 10;

        private double norm_min = 0;
        private double norm_max = Math.pow( 2, 16 );

        private CZTRange range = null;

        private boolean is_projection = false;
        private int projection_method = ZProjector.MAX_METHOD;

        private File save_folder = new File( System.getProperty( "user.home" ) );

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

        public Builder setSaveFolder( File save_folder ) {
            save_folder.mkdirs( );
            this.save_folder = save_folder;
            return this;
        }

        public OperettaManager build( ) {

            File id = this.id;
            IFormatReader reader = null;

            try {
                // Create the reader
                reader = OperettaManager.createReader( id.getAbsolutePath( ) );

                if ( this.range == null ) {
                    this.range = new CZTRange.Builder( ).fromMetadata( (IMetadata) reader.getMetadataStore( ) ).build( );
                } else {
                    if ( this.range.getTotalPlanes( ) == 0 ) {
                        CZTRange new_range = new CZTRange.Builder( ).fromMetadata( (IMetadata) reader.getMetadataStore( ) ).build( );
                        if ( this.range.getRangeC( ).size( ) != 0 ) new_range.setRangeC( this.range.getRangeC( ) );
                        if ( this.range.getRangeZ( ).size( ) != 0 ) new_range.setRangeZ( this.range.getRangeZ( ) );
                        if ( this.range.getRangeT( ).size( ) != 0 ) new_range.setRangeT( this.range.getRangeT( ) );

                        this.range = new_range;
                    }
                }

                return new OperettaManager( reader,
                        this.range,
                        this.n_readers,
                        this.norm_min,
                        this.norm_max,
                        this.is_projection,
                        this.projection_method,
                        this.save_folder );

            } catch ( Exception e ) {
                log.error( "Issue when creating reader for file {}", id );
                return null;
            }

        }

    }

    public IMetadata getMetadata( ) {
        return this.metadata;
    }

    public List<Well> getAvailableWells( ) {
        OMEXMLMetadataRoot r = (OMEXMLMetadataRoot) metadata.getRoot( );
        return r.getPlate( 0 ).copyWellList( );
    }

    public Well getWell( int row, int column ) {
        Well well = getAvailableWells( ).stream( ).filter( w -> w.getRow( ).getValue( ) == row && w.getColumn( ).getValue( ) == column ).findFirst( ).get( );
        log.info( "Well at R{}-C{} is {}", row, column, well.getID( ) );
        return well;
    }

    public List<Integer> getAvailableFieldIds( ) {
        // find one well
        int n_fields = metadata.getWellSampleCount( 0, 0 );

        return IntStream.range( 0, n_fields ).boxed( ).collect( Collectors.toList( ) );

    }

    public List<WellSample> getAvailableSamples( Well well ) {
        return well.copyWellSampleList( );
    }

    public WellSample getField( Well well, int field_id ) {
        WellSample field = getAvailableSamples( well ).stream( ).filter( s -> s.getIndex( ).getValue( ) == field_id ).findFirst( ).get( );
        log.info( "Field with ID {} is {}", field_id, field.getID( ) );
        return field;
    }

    public String getFinalWellImageName( Well well ) {

        int row = well.getRow( ).getValue( );
        int col = well.getRow( ).getValue( );
        String project = metadata.getPlateName( 0 );

        String name = String.format( "%s - R%d-C%d", project, row, col );

        if ( this.is_projection )
            name += "_Projected";
        return name;

    }

    public String getFinalFieldImageName( WellSample sample ) {
        int row = sample.getWell( ).getRow( ).getValue( );
        int col = sample.getWell( ).getColumn( ).getValue( );
        String field_id = sample.getID( );
        String local_field_id = field_id.substring( field_id.lastIndexOf( ":" ) + 1 );


        String project = sample.getWell( ).getPlate( ).getName( );

        String name = String.format( "%s - R%d-C%d-F%s", project, row, col, local_field_id );

        if ( this.is_projection )
            name += "_Projected";
        return name;

    }

    public List<String> getAvailableFieldsString( ) {
        // find one well
        int n_fields = metadata.getWellSampleCount( 0, 0 );

        List<String> availableFields = IntStream.range( 0, n_fields ).mapToObj( f -> {
            String s = "Field " + f;
            return s;
        } ).collect( Collectors.toList( ) );

        return availableFields;
    }

    public List<String> getAvailableWellsString( ) {
        List<String> wells = getAvailableWells( ).stream( )
                .map( w -> {
                    int row = w.getRow( ).getValue( );
                    int col = w.getColumn( ).getValue( );

                    return "R" + row + "-C" + col;

                } ).collect( Collectors.toList( ) );
        return wells;
    }

    public CZTRange getRange() {
        return this.range; }

    /**
     * Getting an ImagePlus from single Wells
     */
    public ImagePlus getWellImage( Well well ) {
        return makeImagePlus( readSingleWell( well, null, 1, this.range, null ), well );
    }

    public ImagePlus getWellImage( Well well, int downscale ) {
        return makeImagePlus( readSingleWell( well, null, downscale, this.range, null ), well );
    }

    public ImagePlus getWellImage( Well well, int downscale, Roi subregion ) {
        return makeImagePlus( readSingleWell( well, null, downscale, this.range, subregion ), well );
    }

    public ImagePlus getWellImage( Well well, int downscale, CZTRange range, Roi subregion ) {
        return makeImagePlus( readSingleWell( well, null, downscale, range, subregion ), well );
    }

    public ImagePlus getWellImage( Well well, List<WellSample> samples, int downscale, CZTRange range, Roi subregion ) {

        return makeImagePlus( readSingleWell( well, samples, downscale, range, subregion ), well );
    }

    /**
     * Getting an ImagePlus from single Fields
     */
    public ImagePlus getFieldImage( WellSample sample ) {

        return makeImagePlus( readSingleStack( sample, 1, this.range, null ), sample.getWell( ) );
    }

    public ImagePlus getFieldImage( WellSample sample, int downscale ) {
        return makeImagePlus( readSingleStack( sample, downscale, this.range, null ), sample.getWell( ) );
    }

    public ImagePlus getFieldImage( WellSample sample, int downscale, Roi subregion ) {
        return makeImagePlus( readSingleStack( sample, downscale, this.range, subregion ), sample.getWell( ) );
    }

    public ImagePlus getFieldImage( WellSample sample, int downscale, CZTRange range, Roi subregion ) {

        return makeImagePlus( readSingleStack( sample, downscale, range, subregion ), sample.getWell( ) );
    }

    public ImageStack readSingleStack( WellSample sample, final int downscale, CZTRange range, final Roi subregion ) {

        final int series_id = sample.getIndex( ).getValue( ); // This is the series ID

        final int row = sample.getWell( ).getRow( ).getValue( );
        final int column = sample.getWell( ).getColumn( ).getValue( );
        main_reader.setSeries( series_id );

        final CZTRange range2 = range.confirmRange( metadata );
        final int n = range2.getTotalPlanes( );

        boolean do_norm = main_reader.getBitsPerPixel( ) != 16;

        // Get Stack width and height and modify in case there is a subregion

        int stack_width = main_reader.getSizeX( );
        int stack_height = main_reader.getSizeY( );

        if ( subregion != null ) {
            stack_width = subregion.getBounds( ).width;
            stack_height = subregion.getBounds( ).height;
        }

        // Account for downscaling
        stack_width /= downscale;
        stack_height /= downscale;

        // Leave in case the final stack ended up too small
        if ( stack_height <= 1 || stack_width <= 1 ) return null;

        // Create the new stack. We need to create it before because some images might be missing
        final ImageStack stack = ImageStack.create( stack_width, stack_height, n, 16 );


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
            log.error( "Reading Stack " + series_id + " interrupted:", e );
        } catch ( ExecutionException e ) {
            log.error( "Reading Stack " + series_id + " error:", e );
        }
        sw.stop( );
        log.info( "Well " + sample.getWell( ).getID( ) + " stack " + series_id + " took " + ( (double) sw.getElapsedTime( ) / 1000.0 ) + " seconds" );
        return stack;
    }

    public ImageStack readSingleWell( Well well, List<WellSample> samples, final int downscale, CZTRange range, final Roi bounds ) {

        // Get the positions for each field (called a sample by BioFormats) in this well
        if ( samples == null ) samples = well.copyWellSampleList( );

        // Out of these coordinates, keep only those that are intersecting with the bounds
        final List<WellSample> adjusted_samples = getIntersectingSamples( samples, bounds );

        if ( adjusted_samples.size( ) == 0 ) return null;

        int a_sample_id = samples.get( 0 ).getIndex( ).getValue( );
        // We need to know the width and height of a single image
        int sample_width = metadata.getPixelsSizeX( a_sample_id ).getValue( );
        int sample_height = metadata.getPixelsSizeY( a_sample_id ).getValue( );

        // Get extents for the final image
        Point topleft = getTopLeftCoordinates( well.copyWellSampleList( ) );
        Point bottomright = getBottomRightCoordinates( well.copyWellSampleList( ) );

        int well_width = bottomright.x - topleft.x + sample_width;
        int well_height = bottomright.y - topleft.y + sample_height;

        // If there is a region, then the final width and height will be the same
        if ( bounds != null ) {
            well_width = bounds.getBounds( ).width;
            well_height = bounds.getBounds( ).height;
        }

        // Finally, correct for downscaling
        well_width /= downscale;
        well_height /= downscale;

        // Confirm the range based on the available metadata
        final CZTRange range2 = range.confirmRange( metadata );

        final int n = range2.getTotalPlanes( );

        // TODO: Bit depth is hard coded here, but it could be made variable
        final ImageStack wellStack = ImageStack.create( well_width, well_height, n, 16 );

        AtomicInteger ai = new AtomicInteger( 0 );

        adjusted_samples.stream( ).forEachOrdered( sample -> {
            // sample subregion should give the ROI coordiantes for the current sample that we want to read
            Roi subregion = getSampleSubregion( sample, bounds, topleft );

            final Point pos = getSampleAdjustedCoordinates( sample, bounds, subregion, topleft, downscale );
            log.info( String.format( "Sample Position: %d, %d", pos.x, pos.y ) );

            final ImageStack stack = readSingleStack( sample, downscale, range2, subregion );

            if ( stack != null ) {
                for ( int s = 0; s < stack.size( ); s++ ) {
                    wellStack.getProcessor( s + 1 )
                            .copyBits( stack.getProcessor( s + 1 ), pos.x, pos.y, Blitter.COPY );

                    wellStack.setSliceLabel( stack.getSliceLabel( s + 1 ), s + 1 );
                }

                // Use an AtomicInteger so that the log looks nice
                final int field_counter = ai.getAndIncrement( );
                log.info( String.format( "Field %d of %d Copied to Well", field_counter + 1, adjusted_samples.size( ) ) );
            }
        } );


        return wellStack;

    }

    // TODO: Format for selecting valid wells and fields
    public void process( int downscale, Roi region, boolean is_fields_individual ) {
        process( null, null, downscale, region, is_fields_individual );
    }

    public void process( List<Well> wells, List<Integer> samples, int downscale, Roi region, boolean is_fields_individual ) {
        // Process everything
        // decide whether we process wells or fields
        if ( wells == null ) {
            wells = getAvailableWells( );
        }

        List<WellSample> well_samples;
        for ( Well well : wells ) {
            log.info( "Well: {}", well );
            if ( samples != null ) {
                well_samples = samples.stream( ).map( well::getWellSample ).collect( Collectors.toList( ) );
            } else {
                // Get the samples associates with the current well, by index
                well_samples = well.copyWellSampleList( );
            }

            //final List<WellSample> fi = getIntersectingSamples( well_samples, region );

            if ( is_fields_individual ) {
                for ( WellSample sample : well_samples ) {
                    ImagePlus field_image = getFieldImage( sample, downscale, this.range, region );
                    String name = getFinalFieldImageName( sample );
                    if ( field_image != null )
                        IJ.saveAsTiff( field_image, new File( save_folder, name + ".tif" ).getAbsolutePath( ) );
                }
                // Save the positions file
                // Get the positions that were used, just compute them again
                try {
                    writeWellPositionsFile( well_samples, new File( save_folder, getFinalWellImageName( well ) + ".txt" ), downscale );
                } catch ( IOException e ) {
                    e.printStackTrace( );
                }

            } else {
                ImagePlus well_image = getWellImage( well, well_samples, downscale, this.range, region );
                String name = getFinalWellImageName( well );
                if ( well_image != null ) {
                    IJ.saveAsTiff( well_image, new File( save_folder, name + ".tif" ).getAbsolutePath( ) );
                    //well_image.show( );
                }
            }
        }

    }

    @Override
    public String toString( ) {
        return "Operetta Reader on File " + this.id.getName( );
    }


    /*///////////////////////////////////
     * Private methods below ////////////
     *///////////////////////////////////

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

    /**
     * writeWellPositionsFile can write the coodrinates of the selected individually saved wells to a file to use with
     * Plugins > Stitching > Grid/Collection Stitching...
     * @param samples the list of samples (Fields) that will be written to the positions file
     * @param position_file the filename of where the position file will be written
     * @param downscale the downscale with which to adjust the coordinates
     * @throws IOException
     */
    private void writeWellPositionsFile( List<WellSample> samples, File position_file, int downscale ) throws IOException {
        int dim = range.getRangeZ( ).size( ) > 1 && !is_projection ? 3 : 2;

        String z = dim == 3 ? ", 0.0" : "";

        Path path = Paths.get( position_file.getAbsolutePath( ) );

        //Use try-with-resource to get auto-closeable writer instance
        try ( BufferedWriter writer = Files.newBufferedWriter( path ) ) {
            writer.write( "#Define the number of dimensions we are working on:\n" );
            writer.write( "dim = " + dim + "\n" );
            writer.write( "# Define the image coordinates\n" );
            writer.write( "#Define the number of dimensions we are working on:\n" );

            for ( WellSample sample : samples ) {
                String name = getFinalFieldImageName( sample );
                Point pos = getUncalibratedCoordinates( sample );
                writer.write( String.format( "%s.tif;      ;               (%d.0, %d.0%s)\n", name, pos.x / downscale, pos.y / downscale, z ) );
            }
        }
    }

    private boolean isOverlapping( Roi one, Roi other ) {
        return one.getBounds( ).intersects( other.getBounds( ) );
    }

    private static IFormatReader createReader( final String id ) throws IOException, FormatException {

        final IFormatReader imageReader = new OperettaReader( );

        Memoizer memo = new Memoizer( imageReader );

        IMetadata omeMeta = MetadataTools.createOMEXMLMetadata( );
        memo.setMetadataStore( omeMeta );
        memo.setId( id );

        return memo;
    }

    private List<WellSample> getIntersectingSamples( List<WellSample> samples, Roi bounds ) {
        // Coordinates are in pixels
        // bounds are in pixels

        ImagePlus imp = IJ.createImage( "", 500, 500, 1, 8 );
        imp.setOverlay( new Overlay( ) );
        // Coordinates are set to 0 for each well
        if ( bounds == null ) return samples;
        log.info( "Looking for samples intersecting with {}, ", bounds );

        // We are selecting bounds


        imp.getOverlay( ).add( resampleRoi( bounds, 30 ) );


        Point topleft = getTopLeftCoordinates( samples );


        List<WellSample> selected = samples.stream( ).filter( s -> {

            int sample_id = s.getIndex( ).getValue( );
            int x = getUncalibratedPositionX( s ) - topleft.x;
            int y = getUncalibratedPositionY( s ) - topleft.y;
            int w = metadata.getPixelsSizeX( sample_id ).getValue( );
            int h = metadata.getPixelsSizeY( sample_id ).getValue( );

            Roi other = new Roi( x, y, w, h );
            imp.getOverlay( ).add( resampleRoi( other, 30 ) );


            return isOverlapping( bounds, other );

        } ).collect( Collectors.toList( ) );
        //imp.show();
        // Sort through them
        log.info( "Selected Samples: " + selected.toString( ) );
        return selected;
    }

    private Roi resampleRoi( Roi r, int s ) {
        return new Roi( r.getBounds( ).x / s, r.getBounds( ).y / s, r.getBounds( ).width / s, r.getBounds( ).height / s );
    }

    private ImagePlus makeImagePlus( ImageStack stack, Well well ) {
        if ( stack == null ) return null;
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

        String name = getFinalWellImageName( well );

        ImagePlus result = new ImagePlus( name, stack );
        result.show( );
        if ( ( czt[ 0 ] + czt[ 1 ] + czt[ 2 ] ) > 3 )
            result = HyperStackConverter.toHyperStack( result, czt[ 0 ], czt[ 1 ], czt[ 2 ] );

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
            //zp.setStopSlice( result.getNSlices( ) );
            if ( result.getNSlices( ) > 1 || result.getNFrames( ) > 1 ) {
                zp.doHyperStackProjection( false );
                result = zp.getProjection( );
            }
        }
        result.setCalibration( cal );
        return result;

    }

    private Roi getSampleSubregion( WellSample sample, Roi bounds, Point topleft ) {

        // The field always contains the subregion so we avoid checking for overlap
        int x, y, w, h;
        x = 0;
        y = 0;

        int sample_id = sample.getIndex( ).getValue( );

        w = metadata.getPixelsSizeX( sample_id ).getValue( );
        h = metadata.getPixelsSizeY( sample_id ).getValue( );

        Point coordinates = getUncalibratedCoordinates( sample );
        coordinates.translate( -topleft.x, -topleft.y );
        if ( bounds != null ) {

            if ( bounds.getBounds( ).x > coordinates.x ) {
                x = bounds.getBounds( ).x - coordinates.x;
                w -= x;
            }

            if ( bounds.getBounds( ).y > coordinates.y ) {
                y = bounds.getBounds( ).y - coordinates.y;
                h -= y;
            }
        }
        return new Roi( x, y, w, h );
    }

    private Integer getUncalibratedPositionX( WellSample sample ) {
        Length px = sample.getPositionX( );

        if ( px == null ) return null;

        double px_m = px.value( UNITS.NANOMETER ).doubleValue( );

        return Math.toIntExact( Math.round( px_m / px_size.value( UNITS.NANOMETER ).doubleValue( ) * this.correction_factor ) );
    }

    private Integer getUncalibratedPositionY( WellSample sample ) {
        Length px = sample.getPositionY( );

        if ( px == null ) return null;

        double px_m = px.value( UNITS.NANOMETER ).doubleValue( );

        return Math.toIntExact( Math.round( px_m / px_size.value( UNITS.NANOMETER ).doubleValue( ) * this.correction_factor ) );
    }

    private Point getUncalibratedCoordinates( WellSample sample ) {
        Integer px = getUncalibratedPositionX( sample );
        Integer py = getUncalibratedPositionY( sample );
        return new Point( px, py );
    }

    private Point getSampleAdjustedCoordinates( WellSample sample, Roi bounds, Roi subregion, Point topleft, int downscale ) {

        //return new Point(subregion.getBounds().x, subregion.getBounds().y);

        Point pos = getUncalibratedCoordinates( sample );

        // After this, pos is the absolute position of the current sample in pixels and that should be it
        pos.translate( -topleft.x, -topleft.y );

        // Because there are bounds, we might need to refine this position to account for the fact we only
        // took a subregion from the original image
        if ( bounds != null )
            pos.translate( subregion.getBounds( ).x - bounds.getBounds( ).x, subregion.getBounds( ).y - bounds.getBounds( ).y );

        // We need to offset the coordinates by the global minimum (topleft) coordinates
        pos.setLocation( ( pos.x ) / downscale, ( pos.y ) / downscale );
        return pos;


    }

    private Point getTopLeftCoordinates( java.util.List<WellSample> samples ) {
        WellSample minx = samples.stream( ).min( Comparator.comparing( WellSample::getPositionX ) ).get( );
        WellSample miny = samples.stream( ).min( Comparator.comparing( WellSample::getPositionY ) ).get( );
        int px = getUncalibratedPositionX( minx );
        int py = getUncalibratedPositionY( miny );

        return new Point( px, py );

    }

    private Point getBottomRightCoordinates( List<WellSample> samples ) {
        WellSample maxx = samples.stream( ).max( Comparator.comparing( WellSample::getPositionX ) ).get( );
        WellSample maxy = samples.stream( ).max( Comparator.comparing( WellSample::getPositionY ) ).get( );

        int px = getUncalibratedPositionX( maxx );
        int py = getUncalibratedPositionY( maxy );

        return new Point( px, py );

    }
}