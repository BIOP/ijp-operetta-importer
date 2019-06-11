package ch.epfl.biop.operetta.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ListChooser {
    private static final Logger logger = LoggerFactory.getLogger( ListChooser.class);

    static public void create( final List<String> wells, final List<String> selectedWells ) {

        final JPanel dialogPanel = new JPanel( );

        final BoxLayout col1Layout = new BoxLayout( dialogPanel, BoxLayout.PAGE_AXIS );

        JLabel label = new JLabel( "Select wells to process:", SwingConstants.LEFT );
        label.setAlignmentX( Component.LEFT_ALIGNMENT );
        label.setPreferredSize( new Dimension( 50, 40 ) );


        JList list = new JList( wells.toArray( ) );
        JScrollPane scroll_list = new JScrollPane( list );
        scroll_list.setAlignmentX( Component.LEFT_ALIGNMENT );

        scroll_list.setVerticalScrollBarPolicy( JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED );
        list.setPreferredSize( new Dimension( 50, 200 ) );


        dialogPanel.setLayout( col1Layout );
        dialogPanel.add( label );
        dialogPanel.add( scroll_list );
        dialogPanel.setVisible( true );

        final int result = JOptionPane.showConfirmDialog( null, dialogPanel,
                "Please select one or more wells",
                JOptionPane.OK_CANCEL_OPTION );

        if ( result == JOptionPane.OK_OPTION ) {
            // Return the values
            int[] res = list.getSelectedIndices( );
            selectedWells.clear();
            selectedWells.addAll( Arrays.stream( res ).boxed( ).map( i -> wells.get( i ) ).collect( Collectors.toList( ) ) ) ;
        }
    }

    public static void main( String[] args ) {
        List<String> wells = new ArrayList<>(  );
        wells.add("One");
        wells.add("Two");
        wells.add("Three");


        ArrayList<String> selected = new ArrayList<String>( );

        create( wells, selected);

        logger.info("Selected Wells {}", selected );



    }
}
