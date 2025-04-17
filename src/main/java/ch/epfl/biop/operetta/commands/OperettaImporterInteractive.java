/*-
 * #%L
 * Hold your horses
 * %%
 * Copyright (C) 2019 - 2025 BIOP
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
import ch.epfl.biop.operetta.utils.HyperRange;
import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import net.imagej.ImageJ;
import ome.xml.model.Well;
import ome.xml.model.WellSample;
import org.scijava.Context;
import org.scijava.Initializable;
import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.InteractiveCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.Button;
import org.scijava.widget.FileWidget;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Interactive command to import Operetta data with a gui
 */
@SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
@Plugin(type = Command.class)
public class OperettaImporterInteractive extends InteractiveCommand implements Initializable {

    @Parameter(required = false)
    OperettaManager.Builder opm_builder;

    OperettaManager opm;
    List<String> selected_wells_string = new ArrayList<>();
    List<String> selected_fields_string = new ArrayList<>();

    private enum FLIP_MODE {
        NONE("Do not flip", false, false),
        HORIZONTAL("Flip horizontal", true, false),
        VERTICAL("Flip vertical", false, true),
        BOTH("Flip both", true, true);

        FLIP_MODE(String name, boolean flipH, boolean flipV) {
            this.name = name;
            this.flipH = flipH;
            this.flipV = flipV;
        }

        @Override
        public String toString() { return this.name; }
        final String name;
        final boolean flipH;
        final boolean flipV;
    }

    private enum FUSE_MODE {
        NONE("Do not fuse fields", false, false),
        NAIVE("Fuse using stage coordinates (no blending)", true, false),
        STITCHING("Fuse using 'Grid/Collection Stitching' plugin", true, true);

        FUSE_MODE(String name, boolean fuse_fields, boolean stitch_fields) {
            this.name = name;
            this.fuse_fields = fuse_fields;
            this.stitch_fields = stitch_fields;
        }

        @Override
        public String toString() { return this.name; }
        final String name;
        final boolean fuse_fields;
        final boolean stitch_fields;
    }

    @Parameter(label = "<html><b>Input data<b/><html>", visibility = ItemVisibility.MESSAGE, persist = false, style = "message", required = false)
    String inputDataMsg = "";

    @Parameter(label = "Selected Wells. Leave blank for all", callback = "updateMessage", required = false, persist = false)
    private String selected_wells_str = "";

    @Parameter(label = "Choose Wells", callback = "wellChooser", required = false, persist = false)
    private Button choose_wells;

    @Parameter(label = "Selected Fields. Leave blank for all", callback = "updateMessage", required = false, persist = false)
    private String selected_fields_str = "";

    @Parameter(label = "Choose Fields", callback = "fieldChooser", required = false, persist = false)
    private Button choose_fields;

    @Parameter(label = "Preview Well slice", callback = "previewWell", required = false, persist = false)
    private Button open_slice;

    @Parameter(label = "Select ranges", callback = "updateMessage", visibility = ItemVisibility.MESSAGE, persist = false, required = false)
    String range = "You can use commas or colons to separate ranges. eg. '1:10' or '1,3,5,8' ";

    @Parameter(label = "Select channels. Leave blank for all", callback = "updateMessage", required = false)
    private String selected_channels_str = "";

    @Parameter(label = "Select slices. Leave blank for all", callback = "updateMessage", required = false)
    private String selected_slices_str = "";

    @Parameter(label = "Select timepoints. Leave blank for all", callback = "updateMessage", required = false)
    private String selected_timepoints_str = "";


    @Parameter(label = "<html><b>Processing<b/><html>", visibility = ItemVisibility.MESSAGE, persist = false, style = "message", required = false)
    String processingMsg = "";

    @Parameter(label = "Downsample factor", callback = "updateMessage")
    int downsample = 4;

    @Parameter(label = "Use averaging when downsampling", callback = "updateMessage")
    boolean use_averaging = false;

    @Parameter(label = "Fuse Fields", callback = "updateMessage", required = false)
    private FUSE_MODE fuse_mode = FUSE_MODE.NONE;

    @Parameter(label = "Flip images", callback = "updateMessage", required = false)
    private FLIP_MODE flip_mode = FLIP_MODE.NONE;

    @Parameter(label = "Perform projection", choices = {"No Projection", "Average Intensity", "Max Intensity", "Min Intensity", "Sum Slices", "Standard Deviation", "Median"}, callback = "updateMessage")
    String z_projection_method;

    @Parameter(label = "Choose pixel data range", visibility = ItemVisibility.MESSAGE, persist = false, required = false)
    String norm = "Useful if you have digital phase images which could be 32-bit";

    @Parameter(label = "Min Value")
    Integer norm_min = 0;

    @Parameter(label = "Max Value")
    Integer norm_max = (int) Math.pow(2, 16) - 1;


    @Parameter(label = "<html><b>Output and saving<b/><html>", visibility = ItemVisibility.MESSAGE, persist = false, style = "message", required = false)
    String outputMsg = "";

    @Parameter(label = "Save directory", style = FileWidget.DIRECTORY_STYLE)
    File save_directory = new File(System.getProperty("user.home") + File.separator);

    @Parameter(label = "Save OME-TIFF fused fields & companion.ome", callback = "updateMessage", required = false)
    private boolean save_as_ome_tiff = false;


    @Parameter(visibility = ItemVisibility.MESSAGE, persist = false, style = "message")
    String task_summary = "Summary";

    @Parameter(label = "Process", callback = "doProcess", persist = false)
    Button process;

    @Parameter
    Context ctx;

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

            double correctionFactor = Prefs.get(OperettaImporterHiddenSettings.correction_factor_key, 0.995);

            if (flip_mode == null) {
                flip_mode = FLIP_MODE.NONE;
            }

            if (z_projection_method == null) {
                z_projection_method = "Max Intensity";
            }

            if (norm_min == null) {
                norm_min = 0;
            }

            if (norm_max == null) {
                norm_max = 65535;
            }

            if (fuse_mode == null) {
                fuse_mode = FUSE_MODE.NONE;
            }

            opm = opm_builder
                    .setRange(range)
                    .setDownsample(downsample)
                    .useAveraging(use_averaging)
                    .flipHorizontal(flip_mode.flipH)
                    .flipVertical(flip_mode.flipV)
                    .saveAsOMETIFF(save_as_ome_tiff)
                    .setProjectionMethod(this.z_projection_method)
                    .setNormalization(norm_min, norm_max)
                    .coordinatesCorrectionFactor(correctionFactor)
                    .fuseFields(fuse_mode.fuse_fields)
                    .useStitcher(fuse_mode.stitch_fields)
                    .setContext(ctx)
                    .build();

            List<String> selected_wells = getAvailableWellsString( opm );
            List<String> selected_fields = getAvailableFieldsString( opm );

            int oriWellsNumber = selected_wells.size();
            int oriFieldsNumber = selected_fields.size();

            if (!selected_wells_str.isEmpty()) {
                selected_wells = stringToList(selected_wells_str);
            }

            if (!selected_fields_str.isEmpty()) {
                selected_fields = stringToList(selected_fields_str);
            }

            // Get the actual field and well ids
            List<Well> wells = selected_wells.stream().map(w -> {
                int row = getRow(w);
                int col = getColumn(w);
                return opm.getWell(row, col);
            }).collect(Collectors.toList());

            List<Integer> field_ids = selected_fields.stream().map(w -> Integer.parseInt(w.trim().split(" ")[1]) - 1).collect(Collectors.toList());

            long[] bytes = opm.getUtilities().getIOBytes(wells, field_ids);

            long[] dimsIO = opm.getUtilities().getIODimensions();

            String oriSize = "<strong>Original Size</strong>: W: " + oriWellsNumber + ", F:" + oriFieldsNumber + ", X:" + dimsIO[0] + ", Y:" + dimsIO[1] + ", Z:" + dimsIO[2] + ", C:" + dimsIO[3] + ", T:" + dimsIO[4];
            String exportSize = "<strong>Exported Size</strong>: W: " + selected_wells.size() + ", F:" + selected_fields.size() + ", X:" + dimsIO[5] + ", Y:" + dimsIO[6] + ", Z:" + dimsIO[7] + ", C:" + dimsIO[8] + ", T:" + dimsIO[9];

            task_summary = getMessage(bytes[0], bytes[1], opm.getPlateName(), oriSize, exportSize);

        } catch (Exception e) {
            task_summary = "Error " + e.getMessage();
            e.printStackTrace();
        }

    }


    private void wellChooser() {
        opm = opm_builder.build();
        ListChooser.create("Wells", getAvailableWellsString( opm ), selected_wells_string);
        selected_wells_str = selected_wells_string.toString();
        if (selected_wells_str.equals("[]")) selected_wells_str = "";
        updateMessage();
    }

    private void fieldChooser() {
        opm = opm_builder.build();
        ListChooser.create("Fields", getAvailableFieldsString( opm ), selected_fields_string);
        selected_fields_str = selected_fields_string.toString();
        if (selected_slices_str.equals("[]")) selected_fields_str = "";
        updateMessage();
    }

    private List<String> stringToList(String str) {
        String[] split = str.replaceAll("\\[|\\]", "").split(",");

        return Arrays.stream(split).collect(Collectors.toList());
    }

    private void previewWell() {

        opm = opm_builder
                .setProjectionMethod(z_projection_method)
                .setDownsample(8)
                .build();

        // If there is a range, update it, otherwise choose the first timepoint and the first z
        if (!this.selected_slices_str.isEmpty()) {
            opm.getRange().updateZRange(selected_slices_str);
        } else if (this.z_projection_method.equals("No Projection")) {
            opm.getRange().updateZRange("1:1");
        }

        if (!this.selected_timepoints_str.isEmpty()) {
            opm.getRange().updateTRange(selected_timepoints_str);
        } else if (this.z_projection_method.equals("No Projection")) {
            opm.getRange().updateTRange("1:1");
        }

        // Choose well to display
        String selected_well;


        // Get the first well that is selected
        if (!selected_wells_str.isEmpty())
            selected_well = stringToList(selected_wells_str).get(0);
        else {
            selected_well = getAvailableWellsString(opm).get(0);
        }

        int row = getRow(selected_well);
        int col = getColumn(selected_well);
        Well well = opm.getWell(row, col);


        ImagePlus sample;
        if (!fuse_mode.fuse_fields && !selected_fields_str.isEmpty()) {
            WellSample field = opm.getField(well, getFields().get(0));

            sample = opm.getFieldImage(field);

        } else {
            sample = opm.getWellImage(well);

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
        if (!selected_fields_string.isEmpty()) {
            List<Integer> field_ids = selected_fields_string.stream().map(w -> Integer.parseInt(w.trim().split(" ")[1]) - 1).collect(Collectors.toList());
            return field_ids;
        } else {
            return opm.getFieldIds();
        }
    }

    /**
     * Run the processing after pressing on the "Process" button
     */
    public void doProcess() {
        HyperRange range = new HyperRange.Builder()
                .setRangeC(this.selected_channels_str)
                .setRangeZ(this.selected_slices_str)
                .setRangeT(this.selected_timepoints_str)
                .build();

        double correctionFactor = Prefs.get(OperettaImporterHiddenSettings.correction_factor_key, 0.995);

        opm = opm_builder
                .setRange(range)
                .setDownsample(downsample)
                .useAveraging(use_averaging)
                .setProjectionMethod(this.z_projection_method)
                .setSaveFolder(this.save_directory)
                .setNormalization(norm_min, norm_max)
                .coordinatesCorrectionFactor(correctionFactor)
                .build();

        // Get Wells and Fields

        List<String> selected_wells = getAvailableWellsString(opm);
        List<String> selected_fields = getAvailableFieldsString(opm);


        if (!selected_wells_str.isEmpty()) {
            selected_wells = stringToList(selected_wells_str);
        }


        if (!selected_fields_str.isEmpty()) {
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
        new Thread(() -> opm.process(wells, field_ids, null)).start(); // region is always null in the interactive command

    }

    /**
     * Returns the available Fields as a String list with format [Field #, Field #,...]
     *
     * @param opm the {@link OperettaManager} instance to use
     * @return a list of field ids as Strings
     */
    public List<String> getAvailableFieldsString(OperettaManager opm ) {
        // find one well
        int n_fields = opm.getMetadata().getWellSampleCount(0, 0);

        return IntStream.range(0, n_fields).mapToObj(f -> {
            String s = "Field " + (f + 1);
            return s;
        }).collect(Collectors.toList());
    }

    /**
     * Returns the available Wells as a String list with format [R#-C#, R#-C#,...]
     *
     * @param opm the {@link OperettaManager} instance to use
     * @return aa list of Strings with the Well names
     */
    public List<String> getAvailableWellsString(OperettaManager opm ) {
        List<String> wells = opm.getWells().stream()
                .map(w -> {
                    int row = w.getRow().getValue() + 1;
                    int col = w.getColumn().getValue() + 1;

                    return "R" + row + "-C" + col;

                }).collect(Collectors.toList());
        return wells;
    }

    /**
     * Test the plugin
     * @param args ignored
     * @throws Exception if something goes wrong
     */
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

    /**
     * Very simple class to create a list chooser in order to choose Wells or Fields for the Interactive command.
     * Used internally by the Interactive command
     */
    public static class ListChooser {

        /**
         * Creates a list chooser with a GUI
         * @param thing_name Name of the thing to choose would be wells or fields
         * @param things List of things to choose from Wells or Fields
         * @param selected_things what was selected after running the chooser
         */
        public static void create(final String thing_name, final List<String> things, final List<String> selected_things) {
            final JPanel dialogPanel = new JPanel();

            final BoxLayout col1Layout = new BoxLayout(dialogPanel, BoxLayout.PAGE_AXIS);

            JLabel label = new JLabel("Select " + thing_name + " to process:", SwingConstants.LEFT);
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
            label.setPreferredSize(new Dimension(50, 40));


            JList<String> list = new JList<>(things.toArray(new String[0]));
            JScrollPane scroll_list = new JScrollPane(list);
            scroll_list.setAlignmentX(Component.LEFT_ALIGNMENT);

            scroll_list.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);


            dialogPanel.setLayout(col1Layout);
            dialogPanel.add(label);
            dialogPanel.add(scroll_list);

            final int result = JOptionPane.showConfirmDialog(null, dialogPanel,
                    "Please select one or more " + thing_name,
                    JOptionPane.OK_CANCEL_OPTION);

            if (result == JOptionPane.OK_OPTION) {
                // Return the values
                int[] res = list.getSelectedIndices();
                selected_things.clear();
                selected_things.addAll(Arrays.stream(res).boxed().map(things::get).collect(Collectors.toList()));
            }
        }

        /**
         * Creates a plate chooser with a GUI. Not used for now.
         * @param wells List of wells
         */
        static protected void createPlate(List<Well> wells) {

            int nrows = (int) wells.stream().map(w -> w.getRow().getValue()).distinct().count();
            int ncols = (int) wells.stream().map(w -> w.getColumn().getValue()).distinct().count();

            // Make rows

            Vector<Vector<String>> data = new Vector<>();
            Vector<String> col_names = new Vector<>();

            for (int r = 0; r < nrows; r++) {

                Vector<String> row = new Vector<>();

                for (int c = 0; c < ncols; c++) {
                    row.addElement(String.format("R%d-C%d", r, c));
                }

                data.addElement(row);
                col_names.addElement("");
            }

            JTable table = new JTable(data, col_names);

            table.setRowHeight(50);

            for (int c = 0; c < ncols; c++) {
                table.getColumnModel().getColumn(c).setPreferredWidth(50);
            }

            DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
            centerRenderer.setHorizontalAlignment(JLabel.CENTER);
            centerRenderer.setVerticalAlignment(JLabel.CENTER);
            table.setDefaultRenderer(String.class, centerRenderer);

            table.setCellSelectionEnabled(true);
            table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            table.setRowSelectionAllowed(true);
            table.setColumnSelectionAllowed(true);


            final JPanel dialogPanel = new JPanel();
            dialogPanel.add(table);

            final int result = JOptionPane.showConfirmDialog(null, dialogPanel,
                    "Please select one or more elements",
                    JOptionPane.OK_CANCEL_OPTION);

        }

    }
}
