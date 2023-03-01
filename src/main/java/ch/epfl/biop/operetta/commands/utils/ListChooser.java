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
package ch.epfl.biop.operetta.commands.utils;

import ch.epfl.biop.operetta.OperettaManager;
import ome.xml.model.Well;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;
import java.util.stream.Collectors;

public class ListChooser {

    private static final Logger logger = LoggerFactory.getLogger(ListChooser.class);

    static public void create(final String thing_name, final List<String> things, final List<String> selected_things) {
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

    static public void createPlate(List<Well> wells) {

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

    public static void main(String[] args) {

        File id = new File("X:\\Tiling\\Opertta Tiling Magda\\Images\\Index.idx.xml");

        OperettaManager op = new OperettaManager.Builder()
                .setId(id)
                .setProjectionMethod("Max Intensity")
                .setSaveFolder(new File("D:\\Demo"))
                .build();


        List<Well> wells = op.getAvailableWells();

        createPlate(wells);
        //logger.info("Selected Wells {}", selected );


    }
}
