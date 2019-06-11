package ch.epfl.biop.operetta.utils;

import ch.epfl.biop.operetta.OperettaManager;
import org.scijava.display.AbstractDisplay;
import org.scijava.display.Display;
import org.scijava.plugin.Plugin;
import org.scijava.ui.viewer.DisplayViewer;

import javax.swing.*;
import java.awt.*;

@Plugin(type = DisplayViewer.class)
public class OperettaManagerViewer extends EasySwingDisplayViewer<OperettaManager> {

        public OperettaManagerViewer() {
            super(OperettaManager.class);
        }

    @Override
    protected boolean canView( OperettaManager value ) {
        return true;
    }

    @Override
    protected void redoLayout( ) {

    }

    @Override
    protected void setLabel( String s ) {

    }

    @Override
    protected void redraw( ) {

    }

    @Override
    protected JPanel createDisplayPanel( OperettaManager value ) {
        final JPanel panel = new JPanel();
        final String fileName = value.toString();
        JLabel labelName = new JLabel(fileName );
        panel.add( labelName );
        return panel;
    }
}
