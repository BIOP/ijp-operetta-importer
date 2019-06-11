package ch.epfl.biop.operetta.commands;

import ch.epfl.biop.operetta.OperettaManager;
import ch.epfl.biop.operetta.utils.CZTRange;
import ij.IJ;
import ij.gui.Roi;
import ij.plugin.ZProjector;
import net.imagej.ImageJ;
import org.scijava.command.Command;
import org.scijava.object.ObjectService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.Button;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.File;

@Plugin(type = Command.class, menuPath = "Plugins>BIOP > Operetta Importer...")
public class OperettaImporter implements Command {

    private static final Logger logger = LoggerFactory.getLogger(OperettaImporter.class);

    @Parameter
    private File id;

    @Parameter
    private ObjectService os;

    @Parameter(required = false)
    private Roi bounds = null;

    @Parameter(required = false)
    private CZTRange range;

    @Parameter(label = "Keep Operetta Manager in Object Store")
    boolean is_keep;

    @Parameter(label = "Perform Z Projection of Data")
    boolean is_z_project;

    @Parameter(label = "Projection Type", choices =  {"Max Intensity", "Mean Intensity", "Median", "Min Intensity" } )
    String z_projection_method;
    
    @Parameter(label = "Choose Wells", callback = "wellChooser")
    Button chooseWells;

    private void wellChooser( ) {

    }


    @Override
    public void run() {
        OperettaManager opm = null;

        opm = new OperettaManager.Builder().setId(id).build();


        if (os.getObjects(OperettaManager.class).size() > 0) {
            os.getObjects(OperettaManager.class).stream().forEach(it -> os.removeObject(it) );
        }
            os.addObject( opm );

    }

    /**
     * This main function serves for development purposes.
     * It allows you to run the plugin immediately out of
     * your integrated development environment (IDE).
     *
     * @param args whatever, it's ignored
     * @throws Exception
     */
    public static void main(final String... args) throws Exception {
        // create the ImageJ application context with all available services
        final ImageJ ij = new ImageJ();
        ij.ui().showUI();

        // invoke the plugin
        ij.command().run(OperettaImporter.class, true);
    }

    private static String[] getProjectionTypes() {
        return ZProjector.METHODS;
    }
}
