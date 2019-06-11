package ch.epfl.biop.operetta.commands;

import ch.epfl.biop.operetta.OperettaManager;
import ch.epfl.biop.operetta.utils.CZTRange;
import ch.epfl.biop.operetta.utils.ListChooser;
import ch.qos.logback.core.spi.CyclicBufferTracker;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.NonBlockingGenericDialog;
import ij.gui.Roi;
import ij.gui.WaitForUserDialog;
import ij.plugin.ZProjector;
import net.imagej.ImageJ;

import ome.xml.model.Plate;
import ome.xml.model.Well;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.Interactive;
import org.scijava.command.InteractiveCommand;
import org.scijava.module.ModuleItem;
import org.scijava.object.ObjectService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.Button;

import org.scijava.widget.FileWidget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Plugin( type = Command.class, menuPath = "Plugins>BIOP > Operetta Importer..." )
//public class OperettaImporter implements Command {
public class OperettaImporter extends InteractiveCommand {

    private static final Logger logger = LoggerFactory.getLogger( OperettaImporter.class );

    @Parameter( label = "Operetta XML file" )
    private File id;

    @Parameter( label = "Selected Wells. Leave blank for all", required = false )
    private String selected_wells_str = "";

    @Parameter( label = "Choose Wells", callback = "wellChooser", required = false )
    private Button chooseWells;

    @Parameter( label = "Selected Fields. Leave blank for all", required = false )
    private String selected_fields_str = "";

    @Parameter( label = "Fuse Fields", required = false )
    private boolean is_fuse_fields = true;


    @Parameter( label = "Choose Fields", callback = "fieldChooser", required = false )
    private Button chooseFields;

    @Parameter( label = "Roi Coordinates [x,y,w,h]. . Leave blank for full image", required = false )
    private String roi_bounds = "";

    @Parameter( label = "Open Well Slice", callback = "roiChooser", required = false )
    private Button openSlice;

    @Parameter( label = "Get Roi From Open Well", callback = "roiSelector", required = false )
    private Button selectRoi;

    @Parameter( label = "Selected Channels. Leave blank for all", required = false )
    private String selected_channels_str = "";

    @Parameter( label = "Selected Slices. Leave blank for all", required = false )
    private String selected_slices_str = "";

    @Parameter( label = "Selected Timepoints. Leave blank for all", required = false )
    private String selected_timepoints_str = "";

    @Parameter( required = false )
    private CZTRange range;

    @Parameter( label = "Perform Projection of Data" )
    boolean is_projection = false;

    @Parameter( label = "Projection Type", choices = { "Max Intensity", "Mean Intensity", "Median", "Min Intensity" } )
    String z_projection_method = "Max Intensity";

    @Parameter( label = "Save Directory", style = FileWidget.DIRECTORY_STYLE )
    File save_directory;

    @Parameter( label = "Normalization range for 32-bit Images", visibility = ItemVisibility.MESSAGE )
    String norm = "void";

    @Parameter( label = "Min Value" )
    Integer norm_min = 0;

    @Parameter( label = "Max Value" )
    Integer norm_max = (int) Math.pow( 2, 16 ) - 1;

    @Parameter( label = "Process", callback = "doProcess" )
    Button process;

    boolean done = false;

    int downsample = 4;

    //@Parameter(label="Operetta Manager Instance", type = ItemIO.OUTPUT)
    OperettaManager opm;

    List<String> selected_wells_string = new ArrayList<>( );
    List<String> selected_fields_string = new ArrayList<>( );

    private File old_id;
    private ImagePlus roiImage;

    private void roiSelector( ) {
        if ( this.roiImage != null ) {
            Roi roi = this.roiImage.getRoi( );

            if ( roi != null ) {
                this.roi_bounds = String.format( "%d, %d, %d, %d",
                        roi.getBounds( ).x * downsample,
                        roi.getBounds( ).y * downsample,
                        roi.getBounds( ).width * downsample,
                        roi.getBounds( ).height * downsample );
            }
        }
    }

    private void wellChooser( ) {
        if ( this.id != null ) {
            if ( !this.id.equals( old_id ) ) {
                opm = new OperettaManager.Builder( ).setId( this.id ).build( );
                old_id = id;
            }

            ListChooser.create( opm.getAvailableWellsString( ), selected_wells_string );

            selected_wells_str = selected_wells_string.toString( );

            //wellsUpdate( true );

        } else {
            IJ.showMessage( "Image XML file not set" );
        }
    }

    private void fieldChooser( ) {

        if ( this.id != null ) {
            if ( !this.id.equals( old_id ) ) {
                opm = new OperettaManager.Builder( ).setId( this.id ).build( );
                old_id = id;
            }

            ListChooser.create( opm.getAvailableFieldsString( ), selected_fields_string );

            selected_fields_str = selected_fields_string.toString( );

            //wellsUpdate( true );

        } else {
            IJ.showMessage( "Image XML file not set" );
        }
    }

    private List<String> stringToList( String str ) {
        String[] split = str.replaceAll( "\\[|\\]", "" ).split( "," );

        List<String> result = Arrays.asList( split ).stream( ).collect( Collectors.toList( ) );

        return result;
    }


    private void roiChooser( ) {
        opm = new OperettaManager.Builder( ).setId( this.id ).build( );

        opm.updateTRange( "1:1" );
        opm.updateZRange( "1:1" );

        String selected_well;

        if ( selected_wells_str.length( ) != 0 )
            selected_well = stringToList( selected_wells_str ).get( 0 );
        else {
            selected_well = opm.getAvailableWellsString( ).get( 0 );
        }

        int row = getRow( selected_well );
        int col = getColumn( selected_well );

        ImagePlus sample = opm.getWellImage( row, col, 4 );
        sample.show( );

        this.roiImage = sample;

    }

    int getRow( String well_str ) {
        Pattern p = Pattern.compile( "R(\\d)-C(\\d)" );
        Matcher m = p.matcher( well_str );
        if ( m.find( ) ) {
            return Integer.parseInt( m.group( 1 ) );
        }
        return -1;
    }

    int getColumn( String well_str ) {
        Pattern p = Pattern.compile( "R(\\d)-C(\\d)" );
        Matcher m = p.matcher( well_str );
        if ( m.find( ) ) {
            return Integer.parseInt( m.group( 2 ) );
        }
        return -1;
    }

    @Override
    public void run( ) {

        if ( done ) {
            CZTRange range = new CZTRange.Builder( )
                    .setRangeC( this.selected_channels_str )
                    .setRangeZ( this.selected_slices_str )
                    .setRangeT( this.selected_timepoints_str )
                    .build( );


            opm = new OperettaManager
                    .Builder( )
                    .setId( id )
                    .setNReaders( 10 )
                    .setRange( range )
                    .setProjectionMethod( this.z_projection_method )
                    .doProjection( this.is_projection )
                    .setSaveFolder( this.save_directory )
                    .setNormalization( norm_min, norm_max )

                    .build( );


        }
    }


    public void doProcess( ) {


        CZTRange range = new CZTRange.Builder( )
                .setRangeC( this.selected_channels_str )
                .setRangeZ( this.selected_slices_str )
                .setRangeT( this.selected_timepoints_str )
                .build( );

        opm = new OperettaManager
                .Builder( )
                .setId( id )
                .setNReaders( 10 )
                .setRange( range )
                .setProjectionMethod( this.z_projection_method )
                .doProjection( this.is_projection )
                .setSaveFolder( this.save_directory )
                .setNormalization( norm_min, norm_max )

                .build( );

        // Get Wells and Fields

        List<String> selected_wells = opm.getAvailableWellsString( );
        List<String> selected_fields = opm.getAvailableFieldsString( );


        if (!selected_wells_str.equals( "" )) {
            selected_wells = stringToList( selected_wells_str );
        }


        if (!selected_fields_str.equals( "" )) {
            selected_fields = stringToList( selected_fields_str );
        }

        // Get the actual field and well ids
        List<Integer> well_ids = selected_wells.stream().map( w -> {
            int row = getRow( w );
            int col = getColumn( w );
            return opm.getWellIndex( row, col );
        } ).collect( Collectors.toList());

        List<Integer> field_ids = selected_fields.stream().map( w -> Integer.parseInt( w.trim().split( " " )[1]) ).collect( Collectors.toList());


        Roi roi = parseRoi( roi_bounds );

        opm.process( this.downsample, roi, !is_fuse_fields, well_ids, field_ids);

    }

    private Roi parseRoi( String roi_string ) {

        Roi bounds = null;
        if (roi_string.length() != 0 ) {
            String[] s = roi_string.split(",");
            if (s.length == 4)
                bounds = new Roi( Integer.parseInt( s[0] ),  Integer.parseInt( s[1] ),  Integer.parseInt( s[2] ),  Integer.parseInt( s[3] ) );
        }
        return bounds;
    }

    /**
     * This main function serves for development purposes.
     * It allows you to run the plugin immediately out of
     * your integrated development environment (IDE).
     *
     * @param args whatever, it's ignored
     * @throws Exception
     */
    public static void main( final String... args ) throws Exception {
        // create the ImageJ application context with all available services
        final ImageJ ij = new ImageJ( );
        ij.ui( ).showUI( );

        // invoke the plugin
        ij.command( ).run( OperettaImporter.class, true );
    }
}
