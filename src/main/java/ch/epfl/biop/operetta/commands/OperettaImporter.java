/*-
 * #%L
 * Hold your horses
 * %%
 * Copyright (C) 2019 - 2022 BIOP
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
package ch.epfl.biop.operetta.commands;

import ch.epfl.biop.operetta.OperettaManager;
import ij.IJ;
import loci.formats.FormatException;
import loci.formats.IFormatReader;
import org.apache.commons.io.FileUtils;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;
import java.io.IOException;

/**
 * Entry point to command {@link OperettaImporterInteractive}.
 * Because the initial parsing can take a while, it is better for the user to have an estimation
 * of the time it will take to open the Operetta dataset, before launching the interactive command
 */
@Plugin(type = Command.class, menuPath = "Plugins>BIOP > Operetta Importer...")
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
        XMLFILE file = null;
        for (XMLFILE version : XMLFILE.values()) {
            File candidate = new File(folder, version.getIndexFileName());
            if (candidate.exists()) {
                file = version;
                break;
            }
        }
        // A few checks and warning for big files
        if (file == null) {
            IJ.log("Error, no matching Index files found in " + folder.getAbsolutePath());
            IJ.log("Implemented valid Index files:");
            for (XMLFILE version : XMLFILE.values()) {
                IJ.log("\t" + version.getIndexFileName() + " (" + version.getDescription() + ")");
            }
            return;
        }

        // Open the file
        File f = new File(folder, file.getIndexFileName());
        int sizeInMb = (int) ((double) FileUtils.sizeOf(f) / (double) (1024 * 1024));
        IJ.log("- Opening Operetta dataset " + f.getAbsolutePath() + " (" + sizeInMb + " Mb)");

        File fmemo = new File(folder, "." + file.getIndexFileName() + ".bfmemo");
        int estimatedOpeningTimeInMin;
        if (!fmemo.exists()) {
            estimatedOpeningTimeInMin = sizeInMb / 30; // 30 Mb per minute
            IJ.log("- No memo file, the first opening will take longer.");
        } else {
            estimatedOpeningTimeInMin = sizeInMb / 600; // 60 Mb per minute
            IJ.log("- Memo file detected.");
        }

        if (estimatedOpeningTimeInMin == 0) {
            IJ.log("- Estimated opening time below 1 minute.");
        } else {
            IJ.log("- Estimated opening time = " + estimatedOpeningTimeInMin + " min.");
        }

        final IFormatReader[] reader = new IFormatReader[1];
        File finalF = f;
        Thread t = new Thread(() -> {
            try {
                reader[0] = OperettaManager.createReader(finalF.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
            } catch (FormatException e) {
                e.printStackTrace();
            }
        });

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
            if ((countSeconds % 20) == 0) {
                IJ.log("- t = " + countSeconds + " s");
            }
        }

        if (reader[0] == null) {
            IJ.log("Error during reader creation, please retry or post your issue in forum.image.sc.");
        } else {
            IJ.log("Done! Opening the dataset took " + countSeconds + " s.");

            OperettaManager.Builder opmBuilder = new OperettaManager.Builder()
                    .reader(reader[0]);

            cs.run(OperettaImporterInteractive.class, true, "opmBuilder", opmBuilder);
        }

    }

    /**
     * List the types of valid XML files we should be looking for
     */
    private enum XMLFILE {
        V5("Index.idx.xml", "PerkinElmer Harmony V5"),
        V5FLEX("Index.flex.xml", "PerkinElmer Harmony V5 Flatfield data"),
        V6("Index.xml", "PerkinElmer Harmony V6");

        private final String description;
        private final String indexFileName;

        XMLFILE(String indexFileName, String description) {
            this.indexFileName = indexFileName;
            this.description = description;
        }

        private String getIndexFileName() {
            return this.indexFileName;
        }

        public String getDescription() {
            return this.description;
        }

    }
}
