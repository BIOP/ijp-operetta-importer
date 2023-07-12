/*-
 * #%L
 * Hold your horses
 * %%
 * Copyright (C) 2019 - 2023 BIOP
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
import ch.epfl.biop.operetta.commands.utils.ListChooser;
import ch.epfl.biop.operetta.utils.HyperRange;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import net.imagej.ImageJ;
import ome.xml.model.Well;
import ome.xml.model.WellSample;
import org.scijava.Initializable;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.InteractiveCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.Button;
import org.scijava.widget.FileWidget;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@SuppressWarnings("FieldMayBeFinal")
@Plugin(type = Command.class)
public class OperettaImporterInteractive extends InteractiveCommand implements Initializable {
    @Parameter(required = false)
    OperettaManager.Builder opmBuilder;
    OperettaManager opm;
    List<String> selected_wells_string = new ArrayList<>();
    List<String> selected_fields_string = new ArrayList<>();

    private enum FLIP_MODE {
        NONE("None", false, false),
        HORIZONTAL("Flip horizontal", true, false),
        VERTICAL("Flip vertical", false, true),
        BOTH("Flip both", true, true);

        FLIP_MODE(String name, boolean flipH, boolean flipV) {
            this.name = name;
            this.flipH = flipH;
            this.flipV = flipV;
        }

        String getName() { return this.name; }

        final String name;
        final boolean flipH;
        final boolean flipV;
    }

    @Parameter(label = "Downsample factor", callback = "updateMessage")
    int downsample = 4;
    @Parameter(label = "Save directory", style = FileWidget.DIRECTORY_STYLE)
    File save_directory = new File(System.getProperty("user.home") + File.separator);

    @Parameter(label = "Selected wells. Leave blank for all", callback = "updateMessage", required = false, persist = false)
    private String selected_wells_str = "";
    @Parameter(label = "Choose Wells", callback = "wellChooser", required = false, persist = false)
    private Button chooseWells;
    @Parameter(label = "Selected fields. Leave blank for all", callback = "updateMessage", required = false, persist = false)
    private String selected_fields_str = "";
    @Parameter(label = "Choose fields", callback = "fieldChooser", required = false, persist = false)
    private Button chooseFields;
    @Parameter(label = "Fuse fields", callback = "updateMessage", required = false)
    private boolean is_fuse_fields = true;
    @Parameter(label = "Preview well slice", callback = "previewWell", required = false, persist = false)
    private Button openSlice;

    @Parameter(label = "Flip images", callback = "updateMessage", required = false)
    private FLIP_MODE flipMode = FLIP_MODE.NONE;


    @Parameter(label = "Select ranges", callback = "updateMessage", visibility = ItemVisibility.MESSAGE, persist = false, required = false)
    String range = "You can use commas or colons to separate ranges. eg. '1:10' or '1,3,5,8' ";

    @Parameter(label = "Selected channels. Leave blank for all", callback = "updateMessage", required = false)
    private String selected_channels_str = "";
    @Parameter(label = "Selected slices. Leave blank for all", callback = "updateMessage", required = false)
    private String selected_slices_str = "";
    @Parameter(label = "Selected timepoints. Leave blank for all", callback = "updateMessage", required = false)
    private String selected_timepoints_str = "";

    @Parameter(label = "Perform Projection of Data", choices = {"No Projection", "Average Intensity", "Max Intensity", "Min Intensity", "Sum Slices", "Standard Deviation", "Median"}, callback = "updateMessage")
    String z_projection_method;

    @Parameter(label = "Choose Data Range", visibility = ItemVisibility.MESSAGE, persist = false, required = false)
    String norm = "Useful if you have digital phase images which could be 32-bit";
    @Parameter(label = "Min Value")
    Integer norm_min = 0;
    @Parameter(label = "Max Value")
    Integer norm_max = (int) Math.pow(2, 16) - 1;

    @Parameter(label = "Update Data Estimation", callback = "updateMessage", persist = false)
    Button updateMessage;

    @Parameter(visibility = ItemVisibility.MESSAGE, persist = false, style = "message")
    String taskSummary = "Click on 'Update Data Estimation'";

    @Parameter(label = "Process", callback = "doProcess", persist = false)
    Button process;

    private String getMessage(long bytes_in, long bytes_out, String name, String oriSize, String exportSize) {
        DecimalFormat df = new DecimalFormat("#0.0");

        double gb_in = ((double) bytes_in) / (1024 * 1024 * 1024);
        double gb_out = ((double) bytes_out) / (1024 * 1024 * 1024);

        double theo_min_time_minutes = ((gb_in + gb_out) / (128.0 / 1024.0)) / 60.0;


        String message = "<html>"// Process task: <br/>"
                + oriSize + "<br/>"
                + exportSize + "<br/>"
                + "Operetta Dataset " + name + "<ul>";

        if (gb_in < 0.1) {
            message += "<li>Read: less than 100 Mb</li>";
        } else {
            message += "<li>Read: " + df.format(gb_in) + " Gb</li>";
        }

        if (gb_out < 0.1) {
            message += "<li>Write: less than 100 Mb</li></ul>";
        } else {
            message += "<li>Write: " + df.format(gb_out) + " Gb</li></ul>";
        }

        if (theo_min_time_minutes < 1) {
            message += "Theoretical minimal duration on Gb connection: below 1 min.<br/>";
        } else if (theo_min_time_minutes > 60) {
            DecimalFormat df2 = new DecimalFormat("#0");
            int nHours = (int) (theo_min_time_minutes / 60);
            double nMin = theo_min_time_minutes - 60 * nHours;
            message += "Theoretical minimal duration on Gb connection: <strong>" + nHours + "h " + df2.format(nMin) + " min.</strong><br/>";
        } else {
            message += "Theoretical limit minimal duration on Gb connection: <strong>" + df.format(theo_min_time_minutes) + " min.</strong><br/>";
        }

        double estimated_min_time_minutes = theo_min_time_minutes * 4; // A la louche

        if (estimated_min_time_minutes < 1) {
            message += "Estimated duration on Gb connection: <strong>below 1 min.</strong><br/>";
        } else if (estimated_min_time_minutes > 60) {
            DecimalFormat df2 = new DecimalFormat("#0");
            int nHours = (int) (estimated_min_time_minutes / 60);
            double nMin = estimated_min_time_minutes - 60 * nHours;
            message += "Estimated duration on Gb connection: <strong>" + nHours + "h " + df2.format(nMin) + " min.</strong>";
        } else {
            message += "Estimated duration on Gb connection: <strong>" + df.format(estimated_min_time_minutes) + " min.</strong><br/>";
        }

        message += "</html>";
        return message;
    }

    private void updateMessage() {
        try {


            HyperRange range = new HyperRange.Builder()
                    .setRangeC(this.selected_channels_str)
                    .setRangeZ(this.selected_slices_str)
                    .setRangeT(this.selected_timepoints_str)
                    .build();

            opm = opmBuilder
                    .setRange(range)
                    .flipHorizontal(flipMode.flipH)
                    .flipVertical(flipMode.flipV)
                    .setProjectionMethod(this.z_projection_method)
                    .setNormalization(norm_min, norm_max)
                    .build();

            List<String> selected_wells = opm.getAvailableWellsString();
            List<String> selected_fields = opm.getAvailableFieldsString();

            int oriWellsNumber = selected_wells.size();
            int oriFieldsNumber = selected_fields.size();

            if (!selected_wells_str.equals("")) {
                selected_wells = stringToList(selected_wells_str);
            }

            if (!selected_fields_str.equals("")) {
                selected_fields = stringToList(selected_fields_str);
            }

            // Get the actual field and well ids
            List<Well> wells = selected_wells.stream().map(w -> {
                int row = getRow(w);
                int col = getColumn(w);
                return opm.getWell(row, col);
            }).collect(Collectors.toList());

            List<Integer> field_ids = selected_fields.stream().map(w -> Integer.parseInt(w.trim().split(" ")[1]) - 1).collect(Collectors.toList());

            long[] bytes = opm.getIOBytes(wells, field_ids, this.downsample, !is_fuse_fields);

            long[] dimsIO = opm.getIODimensions(downsample);

            String oriSize = "<strong>Original Size</strong>: W: " + oriWellsNumber + ", F:" + oriFieldsNumber + ", X:" + dimsIO[0] + ", Y:" + dimsIO[1] + ", Z:" + dimsIO[2] + ", C:" + dimsIO[3] + ", T:" + dimsIO[4];
            String exportSize = "<strong>Exported Size</strong>: W: " + selected_wells.size() + ", F:" + selected_fields.size() + ", X:" + dimsIO[5] + ", Y:" + dimsIO[6] + ", Z:" + dimsIO[7] + ", C:" + dimsIO[8] + ", T:" + dimsIO[9];

            taskSummary = getMessage(bytes[0], bytes[1], opm.getPlateName(), oriSize, exportSize);

        } catch (Exception e) {
            taskSummary = "Error " + e.getMessage();
        }

    }


    private void wellChooser() {
        opm = opmBuilder.build();
        ListChooser.create("Wells", opm.getAvailableWellsString(), selected_wells_string);
        selected_wells_str = selected_wells_string.toString();
        if (selected_wells_str.equals("[]")) selected_wells_str = "";
        updateMessage();
    }

    private void fieldChooser() {
        opm = opmBuilder.build();
        ListChooser.create("Fields", opm.getAvailableFieldsString(), selected_fields_string);
        selected_fields_str = selected_fields_string.toString();
        if (selected_slices_str.equals("[]")) selected_fields_str = "";
        updateMessage();
    }

    private List<String> stringToList(String str) {
        String[] split = str.replaceAll("\\[|\\]", "").split(",");

        List<String> result = Arrays.asList(split).stream().collect(Collectors.toList());

        return result;
    }

    private void previewWell() {

        opm = opmBuilder
                .setProjectionMethod(z_projection_method)
                .build();

        // If there is a range, update it, otherwise choose the first timepoint and the first z
        if (!this.selected_slices_str.equals("")) {
            opm.getRange().updateZRange(selected_slices_str);
        } else if (this.selected_slices_str.equals("") && this.z_projection_method.equals("No Projection")) {
            opm.getRange().updateZRange("1:1");
        }

        if (!this.selected_timepoints_str.equals("")) {
            opm.getRange().updateTRange(selected_timepoints_str);
        } else if (this.selected_timepoints_str.equals("") && this.z_projection_method.equals("No Projection")) {
            opm.getRange().updateTRange("1:1");
        }

        // Choose well to display
        String selected_well;


        // Get the first well that is selected
        if (selected_wells_str.length() != 0)
            selected_well = stringToList(selected_wells_str).get(0);
        else {
            selected_well = opm.getAvailableWellsString().get(0);
        }

        int row = getRow(selected_well);
        int col = getColumn(selected_well);
        Well well = opm.getWell(row, col);


        ImagePlus sample;
        if (!is_fuse_fields && !selected_fields_str.equals("")) {
            WellSample field = opm.getField(well, getFields().get(0));

            sample = opm.getFieldImage(field, 8);

        } else {
            sample = opm.getWellImage(well, 8);

        }
        sample.show();
        IJ.log("Downsampled well opening done.");

        updateMessage();
    }

    int getRow(String well_str) {
        Pattern p = Pattern.compile("R(\\d+)-C(\\d+)");
        Matcher m = p.matcher(well_str);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return -1;
    }

    int getColumn(String well_str) {
        Pattern p = Pattern.compile("R(\\d+)-C(\\d+)");
        Matcher m = p.matcher(well_str);
        if (m.find()) {
            return Integer.parseInt(m.group(2));
        }
        return -1;
    }

    private List<Integer> getFields() {
        if (selected_fields_string.size() != 0) {
            List<Integer> field_ids = selected_fields_string.stream().map(w -> Integer.parseInt(w.trim().split(" ")[1]) - 1).collect(Collectors.toList());
            return field_ids;
        } else {
            return opm.getAvailableFieldIds();
        }
    }

    public void doProcess() {
        HyperRange range = new HyperRange.Builder()
                .setRangeC(this.selected_channels_str)
                .setRangeZ(this.selected_slices_str)
                .setRangeT(this.selected_timepoints_str)
                .build();

        opm = opmBuilder
                .setRange(range)
                .setProjectionMethod(this.z_projection_method)
                .setSaveFolder(this.save_directory)
                .setNormalization(norm_min, norm_max)

                .build();

        // Get Wells and Fields

        List<String> selected_wells = opm.getAvailableWellsString();
        List<String> selected_fields = opm.getAvailableFieldsString();


        if (!selected_wells_str.equals("")) {
            selected_wells = stringToList(selected_wells_str);
        }


        if (!selected_fields_str.equals("")) {
            selected_fields = stringToList(selected_fields_str);
        }

        // Get the actual field and well ids
        List<Well> wells = selected_wells.stream().map(w -> {
            int row = getRow(w);
            int col = getColumn(w);
            return opm.getWell(row, col);
        }).collect(Collectors.toList());

        List<Integer> field_ids = selected_fields.stream().map(w -> Integer.parseInt(w.trim().split(" ")[1]) - 1).collect(Collectors.toList());

        // Write the associated macro command in new thread to allow for proper logging
        new Thread(() -> opm.process(wells, field_ids, this.downsample, null, !is_fuse_fields)).start(); // region is always null in the interactive command

    }

    public static void main(final String... args) throws Exception {

        // create the ImageJ application context with all available services

        final ImageJ ij = new ImageJ();
        ij.ui().showUI();

        // invoke the plugin
        // ij.command().run(OperettaImporter.class, true);
    }

    public void initialize(){
        updateMessage();
    }
}
