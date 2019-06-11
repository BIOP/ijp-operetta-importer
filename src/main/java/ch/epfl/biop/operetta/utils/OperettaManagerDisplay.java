package ch.epfl.biop.operetta.utils;
import ch.epfl.biop.operetta.OperettaManager;
import org.scijava.display.AbstractDisplay;
import org.scijava.display.Display;
import org.scijava.plugin.Plugin;

@Plugin(type = Display.class)
public class OperettaManagerDisplay extends AbstractDisplay<OperettaManager> {

    public OperettaManagerDisplay( ) {
        super( OperettaManager.class );
    }
}
