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
package ch.epfl.biop.operetta;

import ch.epfl.biop.operetta.utils.HyperRange;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.plugin.HyperStackConverter;
import ij.plugin.ZProjector;
import ij.process.*;
import loci.formats.*;
import loci.formats.in.MinimalTiffReader;
import loci.formats.meta.IMetadata;
import net.imglib2.Point;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.quantity.Time;
import ome.xml.meta.OMEXMLMetadataRoot;
import ome.xml.model.Well;
import ome.xml.model.WellSample;
import org.perf4j.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Operetta Manager class
 * This class handles all the Operetta logic to extract data
 * <p>
 * The entry point is to call the {@link Builder} class to create the correct OperettaManager object
 */
public class OperettaManager {

    private static final Logger log = LoggerFactory.getLogger(OperettaManager.class);

    private final File id;
    private final IFormatReader main_reader;

    private final IMetadata metadata;

    private final HyperRange range;

    private final double norm_min;
    private final double norm_max;

    private final boolean is_projection;
    private final int projection_type;
    private final File save_folder;
    private final Length px_size;
    private final double correction_factor = 0.995;
    private final boolean flip_horizontal;
    private final boolean flip_vertical;

    /**
     * OperettaManager Constructor. This constructor is private as you need to use the Builder class
     * to generate the OperettaManager instance. {@link Builder}
     *
     * @param reader          the IFormatReader we will be using
     * @param range           the range of the data in channels slices and frames
     * @param norm_min        the intensity which will be rescaled to 0
     * @param norm_max        the intensity which will be rescaled to 65535
     * @param is_projection   whether we will perform a Z projection
     * @param projection_type the String type of the Z projection
     * @param save_folder     the folder where the exported data should go
     * @see Builder
     */
    private OperettaManager(IFormatReader reader,
                            HyperRange range,
                            double norm_min,
                            double norm_max,
                            boolean flip_horizontal,
                            boolean flip_vertical,
                            boolean is_projection,
                            int projection_type,
                            File save_folder) {

        this.id = new File(reader.getCurrentFile());
        this.main_reader = reader;
        this.metadata = (IMetadata) reader.getMetadataStore();
        this.range = range;
        this.norm_max = norm_max;
        this.norm_min = norm_min;
        this.flip_horizontal = flip_horizontal;
        this.flip_vertical = flip_vertical;

        this.is_projection = is_projection;
        this.projection_type = projection_type;
        this.save_folder = save_folder;

        this.px_size = metadata.getPixelsPhysicalSizeX(0);

    }

    /**
     * The name can have special characters which we replace with underscores, for the file name export
     *
     * @param nameUnsafe The original name that should be sanitized
     * @return a name with all unsafe characters turned to "_"
     */
    private static String safeName(String nameUnsafe) {
        return nameUnsafe.replaceAll("[^\\w\\s\\-_]", "_");
    }

    /**
     * Print some information about how long the processing is
     *
     * @param start                  When the processing started
     * @param percentageCompleteness how far along we are along with teh processing globally. For instance how
     *                               many wells are already processed
     */
    private static void printTimingMessage(Instant start, double percentageCompleteness) {
        long s = Duration.between(start, Instant.now()).getSeconds();
        String elapsedTime = String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, (s % 60));
        double sPerPC = s / percentageCompleteness;
        long sRemaining = (long) ((100 - percentageCompleteness) * sPerPC);
        String remainingTime = String.format("%d:%02d:%02d", sRemaining / 3600, (sRemaining % 3600) / 60, (sRemaining % 60));
        LocalDateTime estimateDoneJob = LocalDateTime.now().plus(Duration.ofSeconds(sRemaining));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        long nDays = sRemaining / (3600 * 24);
        String daysMessage = "";
        if (nDays == 1) {
            daysMessage += " tomorrow.";
        }
        if (nDays == 1) {
            daysMessage += " in " + nDays + " days.";
        }
        String formatDateTime = estimateDoneJob.format(formatter);
        IJ.log(" -  Task " + (int) (percentageCompleteness) + " % completed. Elapsed time:" + elapsedTime + ". Estimated remaining time: " + remainingTime + ". Job done at around " + formatDateTime + daysMessage);
    }

    /**
     * Initializes the reader for this series and makes sure to use Memoization
     *
     * @param id the String path to the xml file
     * @return a BioFormats Reader with memoization
     * @throws IOException     an error while reading the data
     * @throws FormatException and error regarding the data's format
     */
    public static IFormatReader createReader(final String id) throws IOException, FormatException {
        log.debug("Getting new reader for " + id);
        IFormatReader reader = new ImageReader();
        reader.setFlattenedResolutions(false); // For compatibility with bdv-playground
        Memoizer memo = new Memoizer(reader);
        IMetadata omeMetaIdxOmeXml = MetadataTools.createOMEXMLMetadata();
        memo.setMetadataStore(omeMetaIdxOmeXml);
        log.debug("setId for reader " + id);
        org.apache.commons.lang.time.StopWatch watch = new org.apache.commons.lang.time.StopWatch();
        watch.start();
        memo.setId(id);
        watch.stop();
        log.debug("id set in " + (int) (watch.getTime() / 1000L) + " s");
        return memo;
    }

    /**
     * returns the minimum value to use for the normalization of images,
     * in case you have 32-bit (digital phase contrast) images in this Operetta Database
     *
     * @return the minimum value for normalization
     */
    public double getNormMin() {
        return norm_min;
    }

    /**
     * returns the maximum value to use for the normalization of images,
     * in case you have 32-bit (digital phase contrast) images in this Operetta Database
     *
     * @return the maximum value for normalization
     */
    public double getNormMax() {
        return norm_max;
    }

    /**
     * Returns the reader used to extract metadata from the Operetta Format
     * You could use this in order to access Bioformat's reader options
     *
     * @return a reader that is initialized to your dataset
     */
    public IFormatReader getReader() {
        return this.main_reader;
    }

    /**
     * Returns the <a href="https://downloads.openmicroscopy.org/bio-formats/5.9.2/api/loci/formats/meta/IMetadata.html">IMetadata</a>
     * object which we can use to access all information from the Experiment
     *
     * @return this Experiment's metadata
     */
    public IMetadata getMetadata() {
        return this.metadata;
    }

    private boolean isFlipX() {
        return flip_horizontal;
    }

    private boolean isFlipY() {
        return flip_vertical;
    }

    /**
     * Returns the list of all Wells in the Experiment
     * This is currently configured to work only with one plate, but this method could be extended to work with
     * Experiments containing multiple plates.
     *
     * @return a List of wells
     */
    public List<Well> getAvailableWells() {
        OMEXMLMetadataRoot r = (OMEXMLMetadataRoot) metadata.getRoot();
        // Filters out wells which contain no fields -> not imaged
        List<Well> nonEmptyWells = r.getPlate(0)
                .copyWellList()
                .stream()
                .filter(well -> well.copyWellSampleList().get(0).getPositionX() != null) // a weird way to check that the well was indeed imaged, see https://forum.image.sc/t/operetta-reader-bug-positions-not-parsed-found/61818
                .collect(Collectors.toList());
        return nonEmptyWells;
    }

    /**
     * convenience method to recover the well associated to a given row and column (0 indexed for both)
     *
     * @param row    the row (0 indexed) of the well
     * @param column the column (0 indexed) of the well
     * @return the well that matches the provided Row, Column indexes
     */
    public Well getWell(int row, int column) {
        Well well = getAvailableWells().stream().filter(w -> w.getRow().getValue() == row - 1 && w.getColumn().getValue() == column - 1).findFirst().get();
        log.info("Well at R{}-C{} is {}", row, column, well.getID());
        return well;
    }

    /**
     * Returns a list of Field Ids (Integers) for the Experiment. We cannot return a list of Fields ({@link WellSample}s in Bioformats
     * slang) because these are unique to each {@link Well}. The Ids, however, are the same between all {@link Well}.
     *
     * @return a list of Ids that corresponds to all available fields per well
     */
    public List<Integer> getAvailableFieldIds() {
        // find one well
        int n_fields = metadata.getWellSampleCount(0, 0);

        return IntStream.range(1, n_fields + 1).boxed().collect(Collectors.toList());

    }

    /**
     * For a given well, what are all the fields {@link WellSample} contained within
     *
     * @param well the selected well
     * @return a list of Fields (WellSamples)
     */
    public List<WellSample> getAvailableFields(Well well) {
        return well.copyWellSampleList();
    }

    /**
     * Get the Field ({@link WellSample}) corresponding to the provided field_id in the given well
     *
     * @param well     the well to query
     * @param field_id the id of the field
     * @return the field corresponding to the ID
     */
    public WellSample getField(Well well, int field_id) {
        WellSample field = getAvailableFields(well).stream().filter(s -> s.getIndex().getValue() == field_id).findFirst().get();
        log.info("Field with ID {} is {}", field_id, field.getID());
        return field;
    }

    /**
     * Convenience method to get the final name of a well based on all the user parameters passed
     * Useful for when the fields in a well are stitched together
     *
     * @param well the well to get the name from
     * @return the name of the well image to use
     */
    public String getFinalWellImageName(Well well) {

        int row = well.getRow().getValue() + 1;
        int col = well.getColumn().getValue() + 1;
        String project = getPlateName();

        String name = String.format("%s - R%02d-C%02d", project, row, col);

        if (this.is_projection)
            name += "_Projected";
        return name;

    }

    /**
     * Return the name of the plate with special characters replaced
     *
     * @return the safe name of the plate
     */
    public String getPlateName() {
        return safeName(metadata.getPlateName(0));
    }

    /**
     * Returns a usable image name that reflects the field that was selected
     *
     * @param field the field (WellSample) to get the name from, this contains the well information directly
     * @return the name of the image related to this Field (in a specific well
     */
    public String getFinalFieldImageName(WellSample field) {
        int row = field.getWell().getRow().getValue() + 1;
        int col = field.getWell().getColumn().getValue() + 1;
        String field_id = field.getID();
        String local_field_id = field_id.substring(Integer.valueOf(field_id.lastIndexOf(":")) + 1);


        String project = safeName(field.getWell().getPlate().getName());

        String name = String.format("%s - R%02d-C%02d-F%s", project, row, col, local_field_id);

        if (this.is_projection)
            name += "_Projected";
        return name;

    }

    /**
     * Returns the available Fields as a String list with format [Field #, Field #,...]
     *
     * @return a list of field ids as Strings
     */
    public List<String> getAvailableFieldsString() {
        // find one well
        int n_fields = (metadata).getWellSampleCount(0, 0);

        List<String> availableFields = IntStream.range(0, n_fields).mapToObj(f -> {
            String s = "Field " + (f + 1);
            return s;
        }).collect(Collectors.toList());

        return availableFields;
    }

    /**
     * Returns the available Wells as a String list with format [R#-C#, R#-C#,...]
     *
     * @return aa list of Strings with the Well names
     */
    public List<String> getAvailableWellsString() {
        List<String> wells = getAvailableWells().stream()
                .map(w -> {
                    int row = w.getRow().getValue() + 1;
                    int col = w.getColumn().getValue() + 1;

                    return "R" + row + "-C" + col;

                }).collect(Collectors.toList());
        return wells;
    }

    /**
     * returns the range that is being exported
     *
     * @return a C Z T range object
     */
    public HyperRange getRange() {
        return this.range;
    }

    /**
     * Overloaded method, for simplification
     * See {@link OperettaManager#getWellImage(Well, List, int, HyperRange, Roi)} for a complete breakdown
     *
     * @param well The well to export. All fields will be stitched
     * @return the resulting ImagePlus ( C,Z,T Hyperstack ), calibrated
     */
    public ImagePlus getWellImage(Well well) {
        return makeImagePlus(readSingleWell(well, null, 1, this.range, null), well.copyWellSampleList(), null, getFinalWellImageName(well));
    }

    /**
     * Overloaded method, for simplification
     * See {@link OperettaManager#getWellImage(Well, List, int, HyperRange, Roi)} for a complete breakdown
     *
     * @param well      The well to export. All fields will be stitched
     * @param downscale the downscale factor
     * @return the resulting ImagePlus ( C,Z,T Hyperstack ), calibrated
     */
    public ImagePlus getWellImage(Well well, int downscale) {
        return makeImagePlus(readSingleWell(well, null, downscale, this.range, null), well.copyWellSampleList(), null, getFinalWellImageName(well));
    }

    /**
     * Overloaded method, for simplification
     * See {@link OperettaManager#getWellImage(Well, List, int, HyperRange, Roi)} for a complete breakdown
     *
     * @param well      The well to export. All fields will be stitched
     * @param downscale the downscale factor
     * @param subregion a square ROI to extract
     * @return the resulting ImagePlus ( C,Z,T Hyperstack ), calibrated
     */
    public ImagePlus getWellImage(Well well, int downscale, Roi subregion) {
        return makeImagePlus(readSingleWell(well, null, downscale, this.range, subregion), well.copyWellSampleList(), null, getFinalWellImageName(well));
    }

    /**
     * Overloaded method, for simplification
     * See {@link OperettaManager#getWellImage(Well, List, int, HyperRange, Roi)} for a complete breakdown
     *
     * @param well      The well to export. All fields will be stitched
     * @param downscale the downscale factor
     * @param range     the C Z T range to extract, as a {@link HyperRange}
     * @param subregion a square ROI to extract
     * @return the resulting ImagePlus ( C,Z,T Hyperstack ), calibrated
     */
    public ImagePlus getWellImage(Well well, int downscale, HyperRange range, Roi subregion) {
        return makeImagePlus(readSingleWell(well, null, downscale, range, subregion), well.copyWellSampleList(), range, getFinalWellImageName(well));
    }

    /**
     * Exports the current well for the selected samples at the selected C Z T coordinates and for the given subregion
     * into an ImagePlus
     *
     * @param well      The well to export. All fields will be stitched
     * @param fields    the fields to export
     * @param downscale the downscale factor
     * @param range     the C Z T range to extract, as a {@link HyperRange}
     * @param subregion a square ROI to extract
     * @return a calibrated ImagePlus
     */
    public ImagePlus getWellImage(Well well, List<WellSample> fields, int downscale, HyperRange range, Roi subregion) {

        return makeImagePlus(readSingleWell(well, fields, downscale, range, subregion), well.copyWellSampleList(), range, getFinalWellImageName(well));
    }

    /**
     * Exports the current field as an ImagePlus
     *
     * @param field the Field to export, get is through the metadata {@link OperettaManager#getMetadata()}
     * @return a calibrated ImagePlus
     */
    public ImagePlus getFieldImage(WellSample field) {
        return makeImagePlus(readSingleStack(field, 1, this.range, null), Collections.singletonList(field), null, getFinalFieldImageName(field));
    }

    public ImagePlus getFieldImage(WellSample field, int downscale) {
        return makeImagePlus(readSingleStack(field, downscale, this.range, null), Collections.singletonList(field), null, getFinalFieldImageName(field));
    }

    public ImagePlus getFieldImage(WellSample field, int downscale, Roi subregion) {
        return makeImagePlus(readSingleStack(field, downscale, this.range, subregion), Collections.singletonList(field), null, getFinalFieldImageName(field));
    }

    public ImagePlus getFieldImage(WellSample field, int downscale, HyperRange range, Roi subregion) {

        return makeImagePlus(readSingleStack(field, downscale, range, subregion), Collections.singletonList(field), range, getFinalFieldImageName(field));
    }

    /**
     * Method to read a single stack from a field
     *
     * @param field     the field to export
     * @param downscale the downscale factor
     * @param range     the range in C Z T to use
     * @param subregion an optional subregion roi, set to null for none
     * @return an ImageStack
     */
    public ImageStack readSingleStack(WellSample field, final int downscale, HyperRange range, final Roi subregion) {

        final int series_id = field.getIndex().getValue(); // This is the series ID

        final int row = field.getWell().getRow().getValue();
        final int column = field.getWell().getColumn().getValue();
        main_reader.setSeries(series_id);

        final HyperRange range2 = range.confirmRange(metadata);
        final int n = range2.getTotalPlanes();

        boolean do_norm = main_reader.getBitsPerPixel() != 16;

        // Get Stack width and height and modify in case there is a subregion

        int stack_width = main_reader.getSizeX();
        int stack_height = main_reader.getSizeY();

        if (subregion != null) {
            stack_width = subregion.getBounds().width;
            stack_height = subregion.getBounds().height;
        }

        // Account for downscaling
        stack_width /= downscale;
        stack_height /= downscale;

        // Leave in case the final stack ended up too small
        if (stack_height <= 1 || stack_width <= 1) return null;

        // Create the new stack. We need to create it before because some images might be missing
        final ImageStack stack = ImageStack.create(stack_width, stack_height, n, 16);


        List<String> files = Arrays.stream(main_reader.getSeriesUsedFiles(false))
                .filter(f -> f.endsWith(".tiff"))
                .collect(Collectors.toList());
        StopWatch sw = new StopWatch();
        sw.start();

        ForkJoinPool planeWorkerPool = new ForkJoinPool(10);
        try {
            planeWorkerPool.submit(() -> IntStream.range(0, files.size())
                    .parallel()
                    .forEach(i -> {
                        // Check that we want to open it
                        // Infer C Z T from filename

                        Map<String, Integer> plane_indexes = range2.getIndexes(files.get(i));
                        if (range2.includes(files.get(i))) {
                            //IJ.log("files.get( "+i+" )+"+files.get( i ));
                            ImageProcessor ip = openTiffFileAsImageProcessor(files.get(i));

                            if (ip == null) {
                                log.error("Could not open {}", files.get(i));
                            } else {

                                if (flip_horizontal) {
                                    ip.flipHorizontal();
                                }

                                if (flip_vertical) {
                                    ip.flipVertical();
                                }

                                if (do_norm) {
                                    ip.setMinAndMax(norm_min, norm_max);
                                    ip = ip.convertToShort(true);
                                }

                                if (subregion != null) {
                                    ip.setRoi(subregion);
                                    ip = ip.crop();
                                }

                                ip = ip.resize(ip.getWidth() / downscale, ip.getHeight() / downscale);

                                // logger.info("File {}", files.get( i ));
                                String label = String.format("R%d-C%d - (c:%d, z:%d, t:%d) - %s", row, column, plane_indexes.get("C"), plane_indexes.get("Z"), plane_indexes.get("T"), new File(files.get(i)).getName());
                                //IJ.log("plane_indexes.get( \"I\" ): " +plane_indexes.get( "I" ));
                                stack.setProcessor(ip, plane_indexes.get("I"));
                                stack.setSliceLabel(label, plane_indexes.get("I"));
                            }
                        }
                    })).get();
        } catch (InterruptedException e) {
            log.error("Reading Stack " + series_id + " interrupted:", e);
        } catch (ExecutionException e) {
            log.error("Reading Stack " + series_id + " error:", e);
        }
        sw.stop();
        log.info("Well " + field.getWell().getID() + " stack " + series_id + " took " + ((double) sw.getElapsedTime() / 1000.0) + " seconds");
        return stack;
    }

    /**
     * Internal single tiff plane reader. We assume all tiff images are single
     * plane images that can be 8, 16 or 32 bits.
     *
     * @param id the path to the tiff image, which will be opened by MinimalTiffReader
     * @return an ImageProcessor class corresponding ot he bit depth of the image plane
     */
    private ImageProcessor openTiffFileAsImageProcessor(String id) {

        try (IFormatReader reader = new MinimalTiffReader()) {
            reader.setId(id);
            // These images have a single plane, so it's always going to be series 0, plane 0
            reader.setSeries(0);

            int width = reader.getSizeX();
            int height = reader.getSizeY();
            byte[] bytes = reader.openBytes(0);

            // Some amusing operations to get the right ImageProcessor
            ByteOrder byteOrder = reader.isLittleEndian() ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;

            switch (reader.getPixelType()) {
                case FormatTools.UINT8:
                    return new ByteProcessor(width, height, bytes, null);

                case FormatTools.UINT16:
                    ByteBuffer buffer = ByteBuffer.allocate(width * height * 2);
                    buffer.put(bytes);

                    short[] shorts = new short[width * height];
                    buffer.flip();
                    buffer.order(byteOrder).asShortBuffer().get(shorts);

                    return new ShortProcessor(width, height, shorts, null);

                case FormatTools.FLOAT:
                    ByteBuffer forFloatBuffer = ByteBuffer.allocate(width * height * 4);
                    forFloatBuffer.put(bytes);

                    float[] floats = new float[width * height];
                    forFloatBuffer.flip();
                    forFloatBuffer.order(byteOrder).asFloatBuffer().get(floats);

                    return new FloatProcessor(width, height, floats, null);

                default:
                    // DEATH
                    log.error("No idea what the data type is for image " + id + " : " + reader.getPixelType());
                    return null;
            }

        } catch (IOException | FormatException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    /**
     * Returns a stitched stack for the given well and associated fields
     *
     * @param well      the well to export
     * @param fields    the fields to read
     * @param downscale the downsample factor
     * @param range     the CZT range we want to read
     * @param bounds    a ROI describing the subregion we want to export (pixel coordinates)
     * @return an ImageStack
     */
    private ImageStack readSingleWell(Well well, List<WellSample> fields, final int downscale, HyperRange range, final Roi bounds) {

        // Get the positions for each field (called a sample by BioFormats) in this well
        if (fields == null) fields = well.copyWellSampleList();

        // Out of these coordinates, keep only those that are intersecting with the bounds
        final List<WellSample> adjusted_fields = getIntersectingFields(fields, bounds);
        // Problem with ROI bounds is that they are typically given relative to the fused image

        if (adjusted_fields.size() == 0) return null;

        int a_field_id = fields.get(0).getIndex().getValue();
        // We need to know the width and height of a single image
        int sample_width = metadata.getPixelsSizeX(a_field_id).getValue();
        int sample_height = metadata.getPixelsSizeY(a_field_id).getValue();

        // Get extents for the final image
        Point topleft = getTopLeftCoordinates(fields);
        Point bottomright = getBottomRightCoordinates(fields);

        long well_width = bottomright.getLongPosition(0) - topleft.getLongPosition(0) + sample_width;
        long well_height = bottomright.getLongPosition(1) - topleft.getLongPosition(1) + sample_height;

        // If there is a region, then the final width and height will be the same
        if (bounds != null) {
            well_width = bounds.getBounds().width;
            well_height = bounds.getBounds().height;
        }

        // Finally, correct for downscaling
        well_width /= downscale;
        well_height /= downscale;

        // Confirm the range based on the available metadata
        final HyperRange range2 = range.confirmRange(metadata);

        final int n = range2.getTotalPlanes();

        // TODO: Bit depth is hard coded here, but it could be made variable
        final ImageStack wellStack = ImageStack.create((int) well_width, (int) well_height, n, 16);

        AtomicInteger ai = new AtomicInteger(0);

        adjusted_fields.stream().forEachOrdered(field -> {
            // sample subregion should give the ROI coordinates for the current sample that we want to read
            Roi subregion = getFieldSubregion(field, bounds, topleft);

            final int field_counter = ai.getAndIncrement();

            if (subregion != null) {
                final Point pos = getFieldAdjustedCoordinates(field, bounds, subregion, topleft, downscale);
                log.info(String.format("Sample Position: %d, %d", pos.getLongPosition(0), pos.getLongPosition(1)));

                final ImageStack stack = readSingleStack(field, downscale, range2, subregion);

                if (stack != null) {
                    for (int s = 0; s < stack.size(); s++) {
                        wellStack.getProcessor(s + 1)
                                .copyBits(stack.getProcessor(s + 1), (int) pos.getLongPosition(0), (int) pos.getLongPosition(1), Blitter.COPY);

                        wellStack.setSliceLabel(stack.getSliceLabel(s + 1), s + 1);
                    }

                    // Use an AtomicInteger so that the log looks nice
                    log.info(String.format("Field %d of %d Copied to Well", field_counter + 1, adjusted_fields.size()));
                }
            } else {
                log.warn(String.format("Field %d of %d not found.", field_counter + 1, adjusted_fields.size()));
            }
        });

        return wellStack;

    }

    /**
     * this method tries to simplify the processing for a full export
     *
     * @param downscale            the downscale factor
     * @param region               an optional Roi to export, set to null for whole image
     * @param is_fields_individual export each field individually or as a stitched well
     */
    public void process(int downscale, Roi region, boolean is_fields_individual) {
        process(null, null, downscale, region, is_fields_individual);
    }

    /**
     * this method tries to simplify the processing for a full export
     *
     * @param wells                all the Wells to process as a list
     * @param fields               all the Field IDs to process, as a list, set to null to process all
     * @param downscale            the downscale factor
     * @param region               an optional Roi to export, set to null for whole image
     * @param is_fields_individual export each field individually or as a stitched well
     */
    public void process(List<Well> wells, List<Integer> fields, int downscale, Roi region, boolean is_fields_individual) {
        // Process everything
        // decide whether we process wells or fields
        if (wells == null) {
            wells = getAvailableWells();
        }

        List<WellSample> well_fields;
        AtomicInteger iWell = new AtomicInteger();

        Instant global_start = Instant.now();

        double percentageCompleteness = 0;

        for (Well well : wells) {
            log.info("Well: {}", well);
            IJ.log("- Well " + well.getID() + " (" + iWell + "/" + wells.size() + " )");//);
            Instant well_start = Instant.now();

            if (fields != null) {
                well_fields = fields.stream().map(well::getWellSample).collect(Collectors.toList());
            } else {
                // Get the samples associates with the current well, by index
                well_fields = well.copyWellSampleList();
            }

            if (is_fields_individual) {
                if (region != null) {
                    well_fields = getIntersectingFields(well_fields, region);
                }

                AtomicInteger iField = new AtomicInteger();
                for (WellSample field : well_fields) {
                    iField.incrementAndGet();
                    IJ.log("\t - Field " + field.getID() + " (" + iField + "/" + well_fields.size() + ")");//);
                    ImagePlus field_image = getFieldImage(field, downscale, this.range, null);
                    String name = getFinalFieldImageName(field);
                    if (field_image != null)
                        IJ.saveAsTiff(field_image, new File(save_folder, name + ".tif").getAbsolutePath());
                    percentageCompleteness = (iWell.get() / (double) wells.size() + iField.get() / (double) (well_fields.size() * wells.size())) * 100;
                    printTimingMessage(global_start, percentageCompleteness);
                }
                // Save the positions file
                // Get the positions that were used, just compute them again
                try {
                    writeWellPositionsFile(well_fields, new File(save_folder, getFinalWellImageName(well) + ".txt"), downscale);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                // Need to give all the fields, otherwise we will get the origin wrong
                ImagePlus well_image = getWellImage(well, well.copyWellSampleList(), downscale, this.range, region);
                String name = getFinalWellImageName(well);
                if (well_image != null) {
                    IJ.saveAsTiff(well_image, new File(save_folder, name + ".tif").getAbsolutePath());
                }
            }
            Instant ends = Instant.now();
            IJ.log(" - Well processed in " + Duration.between(well_start, ends).getSeconds() + " s.");
            iWell.incrementAndGet();
            percentageCompleteness = (iWell.get() / (double) wells.size()) * 100;
            printTimingMessage(global_start, percentageCompleteness);
        }

        Instant global_ends = Instant.now();
        IJ.log(" DONE! All wells processed in " + (Duration.between(global_start, global_ends).getSeconds() / 60) + " min.");

    }

    /**
     * TODO : fix estimation of output bytes - check if this is correct
     *
     * @param wells                all the Wells to process as a list
     * @param fields               all the Field IDs to process, as a list, set to null to process all
     * @param downscale            the downscale factor
     * @param region               an optional Roi to export, set to null for whole image
     * @param is_fields_individual export each field individually or as a stitched well
     * @return number of bytes that will be read from the dataset and written to the export folder when calling {@link OperettaManager#process(List, List, int, Roi, boolean)}
     */
    public long[] getIOBytes(List<Well> wells, List<Integer> fields, int downscale, Roi region, boolean is_fields_individual) {

        long nWells = wells.size();
        long nFields = fields.size();
        long nTotalPlanes = range.getTotalPlanes(); // c / z / t
        long sX = main_reader.getSizeX();
        long sY = main_reader.getSizeY();

        // Output : more complicated
        // nWells is identical

        long nFieldsOut = nFields;

        //long nTotalPlanesOut = range.getTotalPlanes(); // c / z / t
        long sXOut = main_reader.getSizeX() / downscale;
        long sYOut = main_reader.getSizeY() / downscale;
        long sZOut = is_projection ? 1 : getRange().getRangeZ().size();
        long sCOut = getRange().getRangeC().size();
        long sTOut = getRange().getRangeT().size();

        return new long[]{sX * sY * nTotalPlanes * nFields * nWells * (long) 2, sXOut * sYOut * sZOut * sCOut * sTOut * nFieldsOut * nWells * (long) 2}; // Assuming 16 bits images
    }

    /**
     * Returns all dimensions for the data that we wish to export for size and time estimations
     *
     * @param downscale an eventual downscale factor
     * @return an array with the full dataset sizes(sX, sY, sZ, sC, sT) and selected or
     * downscaled sizes (sXOut, sYOut, sZOut, sCOut, sTOut)
     */

    public long[] getIODimensions(int downscale) {
        long sX = main_reader.getSizeX();
        long sY = main_reader.getSizeY();
        long sZ = main_reader.getSizeZ();
        long sC = main_reader.getSizeC();
        long sT = main_reader.getSizeT();

        long sXOut = main_reader.getSizeX() / downscale;
        long sYOut = main_reader.getSizeY() / downscale;
        long sZOut = is_projection ? 1 : getRange().getRangeZ().size();
        long sCOut = getRange().getRangeC().size();
        long sTOut = getRange().getRangeT().size();

        return new long[]{sX, sY, sZ, sC, sT, sXOut, sYOut, sZOut, sCOut, sTOut};
    }

    @Override
    public String toString() {
        return "Operetta File " + this.id.getName();
    }


    /*///////////////////////////////////
     * Private methods below ////////////
     *///////////////////////////////////

    /**
     * writeWellPositionsFile can write the coordinates of the selected individually saved wells to a file to use with
     * Plugins &gt; Stitching &gt; Grid/Collection Stitching...
     *
     * @param samples       the list of samples (Fields) that will be written to the positions file
     * @param position_file the filename of where the position file will be written
     * @param downscale     the downscale with which to adjust the coordinates
     * @throws IOException error in case of problem working with the positions file
     */
    public void writeWellPositionsFile(List<WellSample> samples, File position_file, int downscale) throws IOException {
        int dim = range.getRangeZ().size() > 1 && !is_projection ? 3 : 2;

        String z = dim == 3 ? ", 0.0" : "";

        Path path = Paths.get(position_file.getAbsolutePath());

        //Use try-with-resource to get auto-closeable writer instance
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write("#Define the number of dimensions we are working on:\n");
            writer.write("dim = " + dim + "\n");
            writer.write("# Define the image coordinates\n");
            writer.write("#Define the number of dimensions we are working on:\n");

            for (WellSample sample : samples) {
                String name = getFinalFieldImageName(sample);
                Point pos = getUncalibratedCoordinates(sample);
                if (pos != null) {
                    writer.write(String.format("%s.tif;      ;               (%d.0, %d.0%s)\n", name, pos.getLongPosition(0) / downscale, pos.getLongPosition(1) / downscale, z));
                } else {
                    writer.write(String.format("# %s.tif;      ;               (NaN, NaN%s)\n", name, z));
                }
            }
        }
    }

    /**
     * Check Rois for overlap, as rectangles only
     *
     * @param one   the first roi
     * @param other the second roi
     * @return true if there is an overlap
     */
    private boolean isOverlapping(Roi one, Roi other) {
        return one.getBounds().intersects(other.getBounds());
    }

    /**
     * Finds fields related to the bounds that were given, so as to limit the number of files to export
     *
     * @param fields the fileds to check intersections in
     * @param bounds the roi for which we are looking for the intersecting fields
     * @return a List of fields (WellSample s) that intersect with the given Roi
     */
    public List<WellSample> getIntersectingFields(List<WellSample> fields, Roi bounds) {
        // Coordinates are in pixels
        // bounds are in pixels

        // Coordinates are set to 0 for each well
        if (bounds == null) return fields;
        log.info("Looking for samples intersecting with {}, ", bounds);

        Point topleft = getTopLeftCoordinates(fields);

        List<WellSample> selected = fields.stream().filter(s -> {

            int sample_id = s.getIndex().getValue();
            long x = getUncalibratedPositionX(s) - topleft.getLongPosition(0);
            long y = getUncalibratedPositionY(s) - topleft.getLongPosition(1);
            int w = metadata.getPixelsSizeX(sample_id).getValue();
            int h = metadata.getPixelsSizeY(sample_id).getValue();

            Roi other = new Roi(x, y, w, h);

            return isOverlapping(bounds, other);

        }).collect(Collectors.toList());
        //imp.show();
        // Sort through them
        IJ.log("Selected Samples: " + selected);
        return selected;
    }

    /**
     * reduces or enlarges ROI coordinates to match resampling
     *
     * @param r the roi
     * @param s the downsample factor
     * @return a new Roi with adjusted size
     */
    private Roi resampleRoi(Roi r, int s) {
        return new Roi(r.getBounds().x / s, r.getBounds().y / s, r.getBounds().width / s, r.getBounds().height / s);
    }

    /**
     * Convenience function to make an ImagePlus out of the exported stack, with proper metadata
     * Will also handle the Z Projection if any
     *
     * @param stack  the Stack produced
     * @param fields all the fields that make up this image
     * @param range  the range, for metadata purposes, can be null
     * @param name   the final name for the ImagePlus
     * @return a calibrated ImagePlus
     */
    private ImagePlus makeImagePlus(ImageStack stack, List<WellSample> fields, HyperRange range, String name) {
        if (stack == null) return null;

        int[] czt = range == null ? this.range.getCZTDimensions() : range.getCZTDimensions();

        ImagePlus result = new ImagePlus(name, stack);
        //result.show( );
        if ((czt[0] + czt[1] + czt[2]) > 3)
            result = HyperStackConverter.toHyperStack(result, czt[0], czt[1], czt[2]);

        Calibration cal = new Calibration(result);
        Calibration meta = getCalibration();
        cal.pixelWidth = meta.pixelWidth;
        cal.pixelHeight = meta.pixelHeight;
        cal.pixelDepth = meta.pixelDepth;
        cal.frameInterval = meta.frameInterval;
        cal.setXUnit(meta.getXUnit());
        cal.setYUnit(meta.getYUnit());
        cal.setZUnit(meta.getZUnit());
        cal.setTimeUnit(meta.getTimeUnit());
        // Do the projection if needed
        if ((this.is_projection) && (result.getNSlices() > 1)) {
            ZProjector zp = new ZProjector();
            zp.setImage(result);
            zp.setMethod(this.projection_type);
            zp.setStopSlice(result.getNSlices());
            if (result.getNSlices() > 1 || result.getNFrames() > 1) {
                zp.doHyperStackProjection(true);
            }
            ImagePlus result2 = zp.getProjection();
            result = result2;
        }
        // Do the calibration for the origin
        Point point = getTopLeftCoordinatesUm(fields);

        cal.xOrigin = point.getDoublePosition(0);
        cal.yOrigin = point.getDoublePosition(1);

        result.setCalibration(cal);

        return result;
    }

    /**
     * This determines the bounds of an ROI for a single field, for the export
     *
     * @param field   the field
     * @param bounds  the roi bounds
     * @param topleft the topleft coordinate, as a Point()
     * @return the Roi, modified to fit the bounds
     */
    private Roi getFieldSubregion(WellSample field, Roi bounds, Point topleft) {

        // The field always contains the subregion so we avoid checking for overlap
        long x, y, w, h;
        x = 0;
        y = 0;
        int sample_id = field.getIndex().getValue();

        if ((metadata.getPixelsSizeX(sample_id) != null) && (metadata.getPixelsSizeY(sample_id) != null)) {
            w = metadata.getPixelsSizeX(sample_id).getValue();
            h = metadata.getPixelsSizeY(sample_id).getValue();
        } else {
            return null;
        }

        Point coordinates = getUncalibratedCoordinates(field);

        if (coordinates == null) return null;

        coordinates.move(new long[]{-topleft.getLongPosition(0), -topleft.getLongPosition(1)});
        if (bounds != null) {

            if (bounds.getBounds().x > coordinates.getLongPosition(0)) {
                x = bounds.getBounds().x - coordinates.getLongPosition(0);
                w -= x;
            }

            if (bounds.getBounds().y > coordinates.getLongPosition(1)) {
                y = bounds.getBounds().y - coordinates.getLongPosition(1);
                h -= y;
            }
        }
        return new Roi(x, y, w, h);
    }

    /**
     * Returns the X position of the field in pixels
     *
     * @param field the field where we want the X position
     * @return the X position of the field
     */
    private Long getUncalibratedPositionX(WellSample field) {
        Length px = field.getPositionX();

        if (px == null) return null;

        double px_m = px.value(UNITS.MICROMETER).doubleValue();

        return Math.round(px_m / px_size.value(UNITS.MICROMETER).doubleValue() * this.correction_factor);
    }

    /**
     * Returns the Y position of the field in pixels
     *
     * @param field the field where we want the Y position
     * @return the Y position of the field
     */
    private Long getUncalibratedPositionY(WellSample field) {
        Length px = field.getPositionY();

        if (px == null) return null;

        double px_m = px.value(UNITS.MICROMETER).doubleValue();

        return Math.round(px_m / px_size.value(UNITS.MICROMETER).doubleValue() * this.correction_factor);
    }

    /**
     * Returns the position of the field in pixels as a Point
     *
     * @param field the field for which we need to coordinates
     * @return a 2D Point with the xy pixel position of the fieldgit staguit
     */
    private Point getUncalibratedCoordinates(WellSample field) {
        Long px = getUncalibratedPositionX(field);
        Long py = getUncalibratedPositionY(field);
        if ((px == null) || (py == null)) {
            return null;
        }
        return new Point(px, py);
    }

    /**
     * Returns the final coordinates of a field based on all given arguments.
     *
     * @param field     the field (WellSample)
     * @param bounds    a roi, null if none
     * @param subregion a subroi
     * @param topleft   the top left coordinate point
     * @param downscale the downsample factor
     * @return the new coordinate for the given Field
     */
    public Point getFieldAdjustedCoordinates(WellSample field, Roi bounds, Roi subregion, Point topleft, int downscale) {

        //return new Point(subregion.getBounds().x, subregion.getBounds().y);

        Point pos = getUncalibratedCoordinates(field);

        // After this, pos is the absolute position of the current sample in pixels and that should be it
        pos.move(new long[]{-topleft.getLongPosition(0), -topleft.getLongPosition(1)});

        // Because there are bounds, we might need to refine this position to account for the fact we only
        // took a subregion from the original image
        if (bounds != null)
            pos.move(new long[]{subregion.getBounds().x - bounds.getBounds().x, subregion.getBounds().y - bounds.getBounds().y});

        // We need to offset the coordinates by the global minimum (topleft) coordinates
        pos.setPosition(new long[]{(pos.getLongPosition(0)) / downscale, (pos.getLongPosition(1)) / downscale});
        return pos;
    }

    /**
     * Returns the top left coordinates as a point
     *
     * @param fields the fields we should get the coordinates for
     * @return a point with the xy pixel coordinates
     */
    public Point getTopLeftCoordinates(java.util.List<WellSample> fields) {
        fields = fields.stream().filter(sample -> sample.getPositionX() != null).collect(Collectors.toList());

        if (fields.size() == 0) {
            System.err.println("Cannot find coordinates");
            return null;
        }

        WellSample minx = fields.stream().min(Comparator.comparing(WellSample::getPositionX)).get();
        WellSample miny = fields.stream().min(Comparator.comparing(WellSample::getPositionY)).get();

        Long px = getUncalibratedPositionX(minx);
        Long py = getUncalibratedPositionY(miny);
        Point p = new Point(px, py);
        //IJ.log("Top Left Point: " + p);

        return p;
    }

    /**
     * Returns the top left coordinates as a point
     *
     * @param fields the fields we should get the coordinates for
     * @return a point with the xy pixel coordinates
     */
    public Point getTopLeftCoordinatesUm(java.util.List<WellSample> fields) {
        fields = fields.stream().filter(sample -> sample.getPositionX() != null).collect(Collectors.toList());

        WellSample minx = fields.stream().min(Comparator.comparing(WellSample::getPositionX)).get();
        WellSample miny = fields.stream().min(Comparator.comparing(WellSample::getPositionY)).get();

        Long px = minx.getPositionX().value(UNITS.MICROMETER).longValue();
        Long py = miny.getPositionY().value(UNITS.MICROMETER).longValue();
        Point p = new Point(px, py);
        //IJ.log("Top Left Point: " + p);

        return p;
    }


    /**
     * Returns the bottom right coordinates as a point
     *
     * @param fields the fields we should get the coordinates for
     * @return a point with the xy pixel coordinates
     */
    public Point getBottomRightCoordinates(List<WellSample> fields) {
        fields = fields.stream().filter(sample -> sample.getPositionY() != null).collect(Collectors.toList());

        if (fields.size() != 0) {
            WellSample maxx = fields.stream().max(Comparator.comparing(WellSample::getPositionX)).get();
            WellSample maxy = fields.stream().max(Comparator.comparing(WellSample::getPositionY)).get();

            Long px = getUncalibratedPositionX(maxx);
            Long py = getUncalibratedPositionY(maxy);

            if (px != null && py != null) {
                return new Point(px, py);
            } else {
                return new Point(0, 0);
            }
        } else {
            System.err.println("All fields are uncalibrated!");
            return new Point(0, 0);
        }
    }

    /**
     * Get the image calibration from the OMEXML Metadata
     *
     * @return an ImageJ Calibration to use on an ImagePlus
     */
    public Calibration getCalibration() {
        // Get the dimensions
        int[] czt = this.range.getCZTDimensions();

        double px_size = 1;
        double px_depth = 1;
        String v_unit = "pixel";
        double px_time = 1;
        String time_unit = "sec";

        // Try to get the Pixel Sizes
        Length apx_size = metadata.getPixelsPhysicalSizeX(0);
        if (apx_size != null) {
            px_size = apx_size.value(UNITS.MICROMETER).doubleValue();
            v_unit = UNITS.MICROMETER.getSymbol();
        }
        // Try to get the Pixel Sizes
        Length apx_depth = metadata.getPixelsPhysicalSizeZ(0);
        if (apx_depth != null) {
            px_depth = apx_depth.value(UNITS.MICROMETER).doubleValue();
        }

        // Trick to get the times: It is the deltaT between timepoints of any image, so we need the deltaT if the second timepoint
        // which is ( nChannels * nSlices ) planes later

        if (range.getCZTDimensions()[2] > 1) { // We need at least a second timepoint !!
            Time apx_time = metadata.getPixelsTimeIncrement(0) == null ? metadata.getPlaneDeltaT(0, czt[0] * czt[1]) : null;
            if (apx_time != null) {
                px_time = apx_time.value(UNITS.SECOND).doubleValue();
                time_unit = UNITS.SECOND.getSymbol();
            }
        }

        Calibration cal = new Calibration();
        cal.pixelWidth = px_size;
        cal.pixelHeight = px_size;
        cal.pixelDepth = px_depth;
        cal.frameInterval = px_time;
        cal.setXUnit(v_unit);
        cal.setYUnit(v_unit);
        cal.setZUnit(v_unit);
        cal.setTimeUnit(time_unit);

        return cal;
    }

    /**
     * This Builder class handles creating {@link OperettaManager} objects for you
     * <p>
     * If you're curious about the Builder Pattern, you can read Joshua Bloch's excellent <a href="https://www.pearson.com/us/higher-education/program/Bloch-Effective-Java-3rd-Edition/PGM1763855.html">Effective Java Book</a>
     * <p>
     * Use
     * When creating a new OperettaManager object, call the Builder, add all the options and then call the {@link Builder#build()} method
     * <pre>
     * * {@code
     * * OperettaManager opm = new OperettaManager.Builder()
     * 									.setId( id )
     * 									.setSaveFolder( save_dir )
     * 								//  Other options here
     * 									.build();
     * * }
     * * </pre>
     */
    public static class Builder {

        private File id = null;

        private double norm_min = 0;
        private double norm_max = Math.pow(2, 16);

        private HyperRange range = null;

        private boolean is_projection = false;
        private int projection_method = ZProjector.MAX_METHOD;

        private File save_folder = new File(System.getProperty("user.home"));

        private IFormatReader reader = null;

        private boolean flip_horizontal = false;
        private boolean flip_vertical = false;

        /**
         * Flip the individual tiles Horizontally.
         * This information is encoded by PerkinElmer in a transformation matrix
         * But we do not have access to it from Bioformats and Bioformats only uses it for positioning
         * So flips in the coordinates (-1 in the matrix) are not actually handled.
         *
         * @param isFlip is the data flipped Horizontally
         * @return a Builder object, to continue building parameters
         */
        public Builder flipHorizontal(boolean isFlip) {
            this.flip_horizontal = isFlip;
            return this;
        }

        /**
         * Flip the individual tiles Vertically.
         * This information is encoded by PerkinElmer in a transformation matrix
         * But we do not have access to it from Bioformats and Bioformats only uses it for positioning
         * So flips in the coordinates (-1 in the matrix) are not actually handled.
         *
         * @param isFlip is the data flipped Vertically
         * @return a Builder object, to continue building parameters
         */
        public Builder flipVertical(boolean isFlip) {
            this.flip_vertical = isFlip;
            return this;
        }

        /**
         * Determines whether the OperettaManager will Z Project the data before saving it, using {@link Builder#setProjectionMethod(String)}
         *
         * @param do_projection true if we wish to perform a Z projection
         * @return a Builder object, to continue building parameters
         */
        public Builder doProjection(boolean do_projection) {
            this.is_projection = do_projection;
            return this;
        }

        /**
         * The projection method to use if we are using {@link Builder#doProjection(boolean)}
         *
         * @param method String that matches one of the strings in
         *               <a href="https://imagej.nih.gov/ij/developer/api/ij/plugin/ZProjector.html#METHODS">the ZProjector</a>
         * @return a Builder object, to continue building parameters
         */
        public Builder setProjectionMethod(String method) {
            if (Arrays.asList(ZProjector.METHODS).contains(method))
                this.projection_method = Arrays.asList(ZProjector.METHODS).indexOf(method);
            return this;
        }

        /**
         * Sets the values for min-max normalization
         * In the case of digital phase images, these are in 32-bits and ImageJ cannot mix 32-bit images with 16-bit images
         * (the standard Operetta bit depth).
         * So we set the min and max display range that will be converted to 0-65535
         *
         * @param min value of the digital phase image that will be set to 0
         * @param max value of the digital phase image that will be set to 65535
         * @return a Builder object, to continue building parameters
         */
        public Builder setNormalization(int min, int max) {
            this.norm_min = min;
            this.norm_max = max;
            return this;
        }

        /**
         * This sets the id (the path to the image file), as per Bioformat's definition
         * In the case of Operetta Data, the ID is the 'Index.idx.xml' file you get when you export it.
         * This is usually provided as an absolute path
         *
         * @param id the full path of 'Index.idx.xml', in String format
         * @return a Builder object, to continue building parameters
         */
        public Builder setId(File id) {
            this.id = id;
            return this;
        }

        /**
         * As an alternative to using a id, when the dataset is big, one can provide
         * am already existing reader, which is a way to optimise the opening of a dataset
         *
         * @param reader the reader
         * @return a Builder object, to continue building parameters
         */
        public Builder reader(IFormatReader reader) {
            this.reader = reader;
            return this;
        }

        /**
         * Can provide a range (Channels, Slices and Timepoints) to use for export. If none are provided, will
         * export the full range of the data
         *
         * @param range the HyperRange object, see corresponding class
         * @return a Builder object, to continue building parameters
         */
        public Builder setRange(HyperRange range) {
            this.range = range;
            return this;
        }

        /**
         * Provides the save folder to which data will be exported to
         *
         * @param save_folder the folder. If not created, this method will try to create it.
         * @return a Builder object, to continue building parameters
         */
        public Builder setSaveFolder(File save_folder) {
            save_folder.mkdirs();
            this.save_folder = save_folder;
            return this;
        }

        /**
         * The build method handles creating an {@link OperettaManager} object from all the settings that were provided.
         * This is done so that everything, like the {@link HyperRange} that is defined is valid.
         *
         * @return the instance to the OperettaManager.
         */
        public OperettaManager build() {

            File id = this.id;

            try {
                // Create the reader
                if (reader == null) {
                    reader = OperettaManager.createReader(id.getAbsolutePath());
                }

                //log.info( "Current range is {}", range );
                if (this.range == null) {
                    this.range = new HyperRange.Builder().fromMetadata((IMetadata) reader.getMetadataStore()).build();
                } else {
                    if (this.range.getTotalPlanes() == 0) {
                        HyperRange new_range = new HyperRange.Builder().fromMetadata((IMetadata) reader.getMetadataStore()).build();
                        if (this.range.getRangeC().size() != 0) new_range.setRangeC(this.range.getRangeC());
                        if (this.range.getRangeZ().size() != 0) new_range.setRangeZ(this.range.getRangeZ());
                        if (this.range.getRangeT().size() != 0) new_range.setRangeT(this.range.getRangeT());

                        this.range = new_range;
                    }
                }

                if (this.save_folder == null) {
                    //TODO
                }

                return new OperettaManager(reader,
                        this.range,
                        this.norm_min,
                        this.norm_max,
                        this.flip_horizontal,
                        this.flip_vertical,
                        this.is_projection,
                        this.projection_method,
                        this.save_folder);

            } catch (Exception e) {
                log.error("Issue when creating reader for file {}", id);
                return null;
            }

        }

    }
}
