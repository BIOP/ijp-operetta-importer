package ch.epfl.biop.operetta.companion;


import ome.xml.model.Plate;
import ome.xml.model.enums.NamingConvention;
import ome.xml.model.primitives.PositiveInteger;


public class PlateCompanion {
    private final String name;
    private final NamingConvention columnNamingConvention;
    private final NamingConvention rowNamingConvention;
    private final int rows;
    private final int columns;
    private final String description;
    private final Plate plate;


    /**
     * PlateCompanion Constructor. This constructor is private as you need to use the Builder class
     * to generate the PlateCompanion instance. {@link Builder}
     *
     * @param plate the Plate object if there is one
     * @param name plate name that will appear on OMERO
     * @param columnNamingConvention if rows are indexed by LETTER or NUMBER
     * @param rowNamingConvention if columns are indexed by LETTER or NUMBER
     * @param rows number of rows in the plate
     * @param columns number of columns in the plate
     * @param description plate description that will appear on omero
     */
    private PlateCompanion(Plate plate,
                           String name,
                           NamingConvention columnNamingConvention,
                           NamingConvention rowNamingConvention,
                           int rows,
                           int columns,
                           String description){

        this.plate= plate;
        this.name = name;
        this.columnNamingConvention = columnNamingConvention;
        this.rowNamingConvention = rowNamingConvention;
        this.rows = rows;
        this.columns = columns;
        this.description = description;
    }

    /**
     * Transform the current plateCompanion into a Plate object,
     * compatible with a companion.ome file
     *
     * @return the Plate object
     */
    protected Plate createPlate(){
        Plate plate;
        if(this.plate != null){
            plate = this.plate;
        }else{
            plate = new Plate();
            plate.setName(this.name);
            plate.setRows(new PositiveInteger(this.rows));
            plate.setColumns(new PositiveInteger(this.columns));
            plate.setRowNamingConvention(this.columnNamingConvention);
            plate.setColumnNamingConvention(this.rowNamingConvention);
            if(this.description != null)
                plate.setDescription(this.description);
        }

        return plate;
    }

    /**
     * This Builder class handles creating {@link PlateCompanion} objects for you
     * <p>
     * If you're curious about the Builder Pattern, you can read Joshua Bloch's excellent <a href="https://www.pearson.com/us/higher-education/program/Bloch-Effective-Java-3rd-Edition/PGM1763855.html">Effective Java Book</a>
     * <p>
     * Use
     * When creating a new PlateCompanion object, call the Builder, add all the options and then call the {@link Builder#build()} method
     * <pre>
     * {@code
     * PlateCompanion plateCompanion = new PlateCompanion.Builder()
     * 									    .setName( name )
     * 									    .setNRows( 8 )
     * 								        //  Other options here
     * 									    .build();
     * }
     * </pre>
     * Mandatory fields
     * <p>
     * <ul>
     * <li> name </li>
     * <li> columnNamingConvention </li>
     * <li> rowNamingConvention </li>
     * <li> rows </li>
     * <li> columns </li>
     * </ul>
     * <p>
     *
     */
    public static class Builder{
        private String name = "Plate";
        private NamingConvention columnNamingConvention = NamingConvention.NUMBER;
        private NamingConvention rowNamingConvention = NamingConvention.LETTER;
        private int rows = 8;
        private int columns = 12;
        private String description = null;
        private Plate plate = null;

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setColumnNamingConvention(NamingConvention columnNamingConvention) {
            this.columnNamingConvention = columnNamingConvention;
            return this;
        }

        public Builder setRowNamingConvention(NamingConvention rowNamingConvention) {
            this.rowNamingConvention = rowNamingConvention;
            return this;
        }

        public Builder setNRows(int rows) {
            this.rows = rows;
            return this;
        }

        public Builder setNColumns(int columns) {
            this.columns = columns;
            return this;
        }

        public Builder setDescription(String description) {
            this.description = description;
            return this;
        }

        public Builder setPlate(Plate plate){
            this.plate = plate;
            return this;
        }

        public PlateCompanion build(){
            return new PlateCompanion(this.plate,
                    this.name,
                    this.columnNamingConvention,
                    this.rowNamingConvention,
                    this.rows,
                    this.columns,
                    this.description);
        }
    }
}

