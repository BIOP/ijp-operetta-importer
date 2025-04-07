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
package ch.epfl.biop.operetta.companion;

import ome.xml.model.Well;
import ome.xml.model.primitives.NonNegativeInteger;

public class WellCompanion {
    private final int row;
    private final int column;
    private final Well well;

    /**
     * WellCompanion Constructor. This constructor is private as you need to use the Builder class
     * to generate the WellCompanion instance. {@link Builder}
     *
     * @param well the Well object if there is one
     * @param row the row index of the well
     * @param column the column index of the well
     */
    private WellCompanion(Well well,
                          int row,
                          int column){

        this.well = well;
        this.row = row;
        this.column = column;
    }

    /**
     * Transform the current wellCompanion into a Well object,
     * compatible with a companion.ome file
     *
     * @return the Well object
     */
    protected Well createWell(){
        Well well;
        if(this.well != null){
            well = this.well;
        }else{
            well = new Well();
            well.setRow(new NonNegativeInteger(this.row));
            well.setColumn(new NonNegativeInteger(this.column));
        }

        return well;
    }


    /**
     * This Builder class handles creating {@link WellCompanion} objects for you
     * <p>
     * If you're curious about the Builder Pattern, you can read Joshua Bloch's excellent <a href="https://www.pearson.com/us/higher-education/program/Bloch-Effective-Java-3rd-Edition/PGM1763855.html">Effective Java Book</a>
     * <p>
     * Use
     * When creating a new WellCompanion object, call the Builder, add all the options and then call the {@link Builder#build()} method
     * <pre>
     * {@code
     * WellCompanion wellCompanion = new WellCompanion.Builder()
     * 									    .setRow( 0 )
     * 								        //  Other options here
     * 									    .build();
     * }
     * </pre>
     * Mandatory fields
     * <p>
     * <ul>
     * <li> row </li>
     * <li> column </li>
     * </ul>
     * <p>
     *
     */
    public static class Builder{
        private int row = 8;
        private int column = 12;
        private Well well = null;

        public Builder setRow(int row) {
            this.row = row;
            return this;
        }

        public Builder setColumn(int column) {
            this.column = column;
            return this;
        }

        public Builder setWell(Well well){
            this.well = well;
            return this;
        }

        public WellCompanion build(){
            return new WellCompanion(this.well,
                    this.row,
                    this.column);
        }
    }
}
