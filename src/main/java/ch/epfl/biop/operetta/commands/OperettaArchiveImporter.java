/*-
 * #%L
 * Hold your horses
 * %%
 * Copyright (C) 2019 - 2026 BIOP
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
import ch.epfl.biop.operetta.archive.CompanionFromArchiveGenerator;
import ij.IJ;
import loci.formats.FormatException;
import loci.formats.IFormatReader;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;
import java.io.IOException;

/**
 * Entry point for importing Operetta Archive (Harmony 4.9) data.
 * This command generates a companion file from the archive and then
 * launches {@link OperettaImporterInteractive} for further processing.
 */
@Plugin(type = Command.class, menuPath = "Plugins>BIOP > Operetta Importer > Operetta Archive Importer...")
public class OperettaArchiveImporter implements Command {

    @Parameter(visibility = ItemVisibility.MESSAGE)
    String message = "BIOP Operetta Archive Importer";

    @Parameter(label = "Select the folder containing the images of your Operetta archive", style = "directory",
            description = "The folder containing IMAGES.sqlite and the TIFF files")
    File imagesFolder;

    @Parameter(label = "Select the matching XML file for this archive", style = "open",
            description = "The XML file from the Harmony archive (e.g., measurement.xml)")
    File xmlFile;

    @Parameter(label = "Flip Y coordinates", description = "Flip Y coordinates if images appear mirrored")
    boolean flipY = false;

    @Parameter(label = "Swap X/Y coordinates", description = "Swap X and Y coordinates if needed")
    boolean swapXY = false;

    @Parameter
    CommandService cs;

    @Override
    public void run() {
        // Validate inputs
        if (!xmlFile.exists()) {
            IJ.log("Error: XML file does not exist: " + xmlFile.getAbsolutePath());
            return;
        }

        File sqliteFile = new File(imagesFolder, "IMAGES.sqlite");
        if (!sqliteFile.exists()) {
            IJ.log("Error: IMAGES.sqlite not found in " + imagesFolder.getAbsolutePath());
            return;
        }

        // Find a TIFF file for pixel size extraction
        File tiffFile = findTiffFile(imagesFolder);
        if (tiffFile == null) {
            IJ.log("Error: No TIFF files found in " + imagesFolder.getAbsolutePath());
            return;
        }

        IJ.log("=== Operetta Archive Importer ===");
        IJ.log("XML file: " + xmlFile.getAbsolutePath());
        IJ.log("SQLite file: " + sqliteFile.getAbsolutePath());
        IJ.log("Sample TIFF: " + tiffFile.getAbsolutePath());

        // Step 1: Check if companion file already exists, otherwise generate it
        File companionFile = findCompanionFile(imagesFolder);
        if (companionFile != null && companionFile.exists()) {
            IJ.log("Companion file already exists: " + companionFile.getAbsolutePath());
        } else {
            IJ.log("Generating companion file...");
            CompanionFromArchiveGenerator generator = new CompanionFromArchiveGenerator(
                    sqliteFile.getAbsolutePath(),
                    xmlFile.getAbsolutePath(),
                    tiffFile.getAbsolutePath(),
                    flipY,
                    swapXY
            );

            generator.run();

            // Find the generated companion file
            companionFile = findCompanionFile(imagesFolder);
            if (companionFile == null) {
                IJ.log("Error: Companion file was not generated");
                return;
            }

            IJ.log("Companion file created: " + companionFile.getAbsolutePath());
        }

        // Step 2: Open companion file with Bio-Formats (append .lazy for lazy loader)
        String companionPathLazy = companionFile.getAbsolutePath() + ".lazy";
        IJ.log("Opening companion file with Bio-Formats: " + companionPathLazy);
        final IFormatReader[] reader = new IFormatReader[1];

        Thread t = new Thread(() -> {
            try {
                reader[0] = OperettaManager.Builder.createReader(companionPathLazy);
            } catch (IOException | FormatException e) {
                IJ.log("Error creating reader: " + e.getMessage());
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
                IJ.log("Opening interrupted!");
                return;
            }
            if ((countSeconds % 20) == 0) {
                IJ.log("- t = " + countSeconds + " s");
            }
        }

        if (reader[0] == null) {
            IJ.log("Error: Failed to create reader from companion file. Please retry or post your issue in forum.image.sc.");
            return;
        }

        IJ.log("Done! Opening took " + countSeconds + " s.");

        // Step 3: Launch interactive command
        OperettaManager.Builder opmBuilder = new OperettaManager.Builder()
                .reader(reader[0]);

        cs.run(OperettaImporterInteractive.class, true, "opm_builder", opmBuilder);
    }

    private File findTiffFile(File folder) {
        File[] files = folder.listFiles((dir, name) ->
                name.toLowerCase().endsWith(".tiff") || name.toLowerCase().endsWith(".tif"));
        if (files != null && files.length > 0) {
            return files[0];
        }
        return null;
    }

    private File findCompanionFile(File folder) {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".companion.ome"));
        if (files != null && files.length > 0) {
            return files[0];
        }
        return null;
    }
}
