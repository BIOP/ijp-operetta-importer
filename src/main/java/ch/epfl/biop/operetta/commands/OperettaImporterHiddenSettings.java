package ch.epfl.biop.operetta.commands;

import ij.IJ;
import ij.Prefs;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@SuppressWarnings("FieldMayBeFinal")
@Plugin(type = Command.class, menuPath = "Plugins>BIOP > Operetta Importer > Operetta Importer Settings...")
public class OperettaImporterHiddenSettings implements Command{
    protected static final String correction_factor = "ch.epfl.biop.operetta.correctionFactor";

    @Parameter(label = "XY coordinates correction factor (default is 0.995)", persist = false)
    Double correctionFactor = Prefs.get(correction_factor, 0.995);

    @Override
    public void run() {
        Prefs.set(correction_factor, correctionFactor);
        IJ.log("XY coordinates correction factor set to " + correctionFactor);
    }
}
