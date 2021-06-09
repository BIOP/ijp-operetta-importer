package ch.epfl.biop.operetta.commands;

import ch.epfl.biop.operetta.OperettaManager;
import ij.IJ;
import loci.formats.IFormatReader;
import org.apache.commons.io.FileUtils;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;

/**
 * Entry point to command {@link OperettaImporterInteractive}.
 * Because the initial parsing can take a while, it is better for the user to have an estimation
 * of the time it will take to open the Operetta dataset, before launching the interactive command
 */
@Plugin( type = Command.class, menuPath = "Plugins>BIOP > Operetta Importer..." )
public class OperettaImporter implements Command {

    // Useful to display the label of the folder parameter
    @Parameter(visibility = ItemVisibility.MESSAGE)
    String message = "BIOP Operetta Importer";

    @Parameter(label = "Select the 'Images' folder of your Operetta dataset", style = "directory")
    File folder;

    @Parameter
    CommandService cs;

    @Override
    public void run() {
        // A few checks and warning for big files
        File f = new File(folder, "Index.idx.xml");
        if (!f.exists()) {
            IJ.log("Error, file "+f.getAbsolutePath()+" not found!");
            return;
        }

        int sizeInMb = (int) ((double) FileUtils.sizeOf(f)/(double)(1024*1024));
        IJ.log("- Opening Operetta dataset "+f.getAbsolutePath()+" (" + sizeInMb + " Mb)");

        File fmemo = new File(folder, ".Index.idx.xml.bfmemo");
        int estimatedOpeningTimeInMin;
        if (!fmemo.exists()) {
            estimatedOpeningTimeInMin = sizeInMb / 30; // 30 Mb per minute
            IJ.log("- No memo file, the first opening will take longer.");
        } else {
            estimatedOpeningTimeInMin = sizeInMb / 600; // 60 Mb per minute
            IJ.log("- Memo file detected.");
        }

        if (estimatedOpeningTimeInMin==0) {
            IJ.log("- Estimated opening time below 1 minute.");
        } else {
            IJ.log("- Estimated opening time = " + estimatedOpeningTimeInMin + " min.");
        }

        final IFormatReader[] reader = new IFormatReader[1];
        Thread t = new Thread(() -> reader[0] = OperettaManager.createReader(f.getAbsolutePath()));

        t.start();
        int countSeconds = 0;
        while (t.isAlive()) {
            try {
                Thread.sleep(1000);
                countSeconds++;
            } catch (InterruptedException e) {
                IJ.log("Operetta dataset opening interrupted!");
                return;
            }
            if ((countSeconds % 20)==0) {
                IJ.log("- t = " + countSeconds + " s");
            }
        }

        IJ.log("Done! Opening the dataset took "+countSeconds+" s.");

        OperettaManager.Builder opmBuilder =  new OperettaManager.Builder()
                .reader(reader[0]);

        cs.run(OperettaImporterInteractive.class,true,      "opmBuilder", opmBuilder);

    }
}
