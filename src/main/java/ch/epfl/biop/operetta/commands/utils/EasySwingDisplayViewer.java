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
package ch.epfl.biop.operetta.commands.utils;

import org.scijava.display.Display;
import org.scijava.display.event.DisplayDeletedEvent;
import org.scijava.object.ObjectService;
import org.scijava.plugin.Parameter;
import org.scijava.ui.UserInterface;
import org.scijava.ui.swing.SwingUI;
import org.scijava.ui.viewer.AbstractDisplayViewer;
import org.scijava.ui.viewer.DisplayPanel;
import org.scijava.ui.viewer.DisplayViewer;
import org.scijava.ui.viewer.DisplayWindow;

import javax.swing.*;
import java.awt.*;

abstract public class EasySwingDisplayViewer<T> extends
        AbstractDisplayViewer<T> implements DisplayViewer<T>
{
    private final Class<T> classOfObject;

    @Parameter
    ObjectService objectService;

    protected EasySwingDisplayViewer( Class< T > classOfObject )
    {
        this.classOfObject = classOfObject;
    }

    @Override
    public boolean isCompatible(final UserInterface ui) {
        return ui instanceof SwingUI;
    }

    @Override
    public boolean canView(final Display<?> d) {
        Object object = d.get( 0 );
        if(! classOfObject.isInstance( object ) )
            return false;
        T value = ( T ) object;
        return canView( value );
    }

    protected abstract boolean canView( T value );
    protected abstract void redoLayout();
    protected abstract void setLabel(final String s);
    protected abstract void redraw();
    protected abstract JPanel createDisplayPanel(T value);

    @Override
    public void onDisplayDeletedEvent( DisplayDeletedEvent e )
    {
        super.onDisplayDeletedEvent( e );
        objectService.removeObject( getDisplay().get( 0 ) );
    }

    @Override
    public void view(final DisplayWindow w, final Display<?> d) {
        objectService.addObject( d.get( 0 ) );
        super.view(w, d);
        final JPanel content = createDisplayPanel( getDisplay().get(0) );
        setPanel( new SwingDisplayPanel(w, d, this, content) );
    }


    public static class SwingDisplayPanel extends JPanel implements DisplayPanel
    {

        // -- instance variables --

        private final EasySwingDisplayViewer< ? > viewer;
        private final DisplayWindow window;
        private final Display< ? > display;

        // -- PlotDisplayPanel methods --

        public SwingDisplayPanel( DisplayWindow window, Display< ? > display, EasySwingDisplayViewer< ? > viewer, JPanel panel )
        {
            this.window = window;
            this.display = display;
            this.viewer = viewer;
            window.setContent(this);
            setLayout( new BorderLayout() );
            add(panel);
        }

        @Override
        public Display< ? > getDisplay() {
            return display;
        }

        // -- DisplayPanel methods --

        @Override
        public DisplayWindow getWindow() {
            return window;
        }

        @Override
        public void redoLayout()
        {
            viewer.redoLayout();
        }

        @Override
        public void setLabel( String s )
        {
            viewer.setLabel( s );
        }

        @Override
        public void redraw()
        {
            viewer.redraw();
        }
    }
}
