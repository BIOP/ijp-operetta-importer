/*-
 * #%L
 * Hold your horses
 * %%
 * Copyright (C) 2019 - 2024 BIOP
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

import ch.epfl.biop.operetta.commands.OperettaImporter;
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
import mpicbg.models.InvertibleBoundable;
import mpicbg.models.TranslationModel2D;
import mpicbg.models.TranslationModel3D;
import mpicbg.stitching.CollectionStitchingImgLib;
import mpicbg.stitching.ImageCollectionElement;
import mpicbg.stitching.ImagePlusTimePoint;
import mpicbg.stitching.StitchingParameters;
import mpicbg.stitching.fusion.Fusion;
import net.imglib2.Point;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.FloatType;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.units.quantity.Time;
import ome.xml.meta.MetadataRetrieve;
import ome.xml.meta.OMEXMLMetadataRoot;
import ome.xml.model.Well;
import ome.xml.model.WellSample;
import org.perf4j.StopWatch;
import org.scijava.task.Task;
import org.scijava.task.TaskService;
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
    private final double correction_factor;
    private final boolean flip_horizontal;
    private final boolean flip_vertical;
    private final int downsample;
    private final boolean fuse_fields;
    private final boolean use_stitcher;
    private StitchingParameters stitching_parameters;
    private final Utilities utils;
    private final TaskService taskService; // Task monitoring and cancellation
    private final boolean use_averaging;

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
                            int downsample,
                            boolean use_averaging,
                            HyperRange range,
                            double norm_min,
                            double norm_max,
                            boolean flip_horizontal,
                            boolean flip_vertical,
                            boolean is_projection,
                            int projection_type,
                            File save_folder,
                            double correction_factor,
                            boolean fuse_fields,
                            boolean use_stitcher,
                            StitchingParameters stitching_parameters,
                            TaskService taskService) {

        this.id = new File(reader.getCurrentFile());
        this.main_reader = reader;
        this.metadata = (IMetadata) reader.getMetadataStore();
        this.downsample = downsample;
        this.use_averaging = use_averaging;
        this.range = range;
        this.norm_max = norm_max;
        this.norm_min = norm_min;
        this.flip_horizontal = flip_horizontal;
        this.flip_vertical = flip_vertical;

        this.is_projection = is_projection;
        this.projection_type = projection_type;
        this.save_folder = save_folder;
        this.correction_factor = correction_factor;

        this.fuse_fields = fuse_fields;
        this.use_stitcher = use_stitcher;
        this.stitching_parameters = stitching_parameters;
        this.px_size = metadata.getPixelsPhysicalSizeX(0);
        this.utils = new Utilities();
        this.taskService = taskService;
    }

    /**
     * Return the name of the plate with special characters replaced
     *
     * @return the safe name of the plate
     */
    public String getPlateName() {
        return utils.safeName(metadata.getPlateName(0));
    }

    /**
     * Return the BioFormats metadata associated with this image series
     *
     * @return the Metadata
     */
    public MetadataRetrieve getMetadata() {
        return this.metadata;
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
     * Returns the list of all Wells in the Experiment
     * This is currently configured to work only with one plate, but this method could be extended to work with
     * Experiments containing multiple plates.
     *
     * @return a List of wells
     */
    public List<Well> getWells() {
        OMEXMLMetadataRoot r = (OMEXMLMetadataRoot) metadata.getRoot();
        // Filters out wells which contain no fields -> not imaged
        return r.getPlate(0)
                .copyWellList()
                .stream()
                .filter(well -> well.copyWellSampleList().get(0).getPositionX() != null) // a weird way to check that the well was indeed imaged, see https://forum.image.sc/t/operetta-reader-bug-positions-not-parsed-found/61818
                .collect(Collectors.toList());
    }

    /**
     * convenience method to recover the well associated to a given row and column (0 indexed for both)
     *
     * @param row    the row (0 indexed) of the well
     * @param column the column (0 indexed) of the well
     * @return the well that matches the provided Row, Column indexes
     */
    public Well getWell(int row, int column) {
        Optional<Well> maybeWell = getWells().stream().filter(w -> w.getRow().getValue() == row - 1 && w.getColumn().getValue() == column - 1).findFirst();

        if (maybeWell.isPresent())
            return maybeWell.get();
        log.info("Well at R{}-C{} is not found", row, column);
        return null;
    }

    /**
     * Returns a list of Field Ids (Integers) for the Experiment. We cannot return a list of Fields ({@link WellSample}s in BioFormats
     * slang) because these are unique to each {@link Well}. The Ids, however, are the same between all {@link Well}.
     *
     * @return a list of Ids that corresponds to all available fields per well
     */
    public List<Integer> getFieldIds() {
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
    public List<WellSample> getFields(Well well) {
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
        Optional<WellSample> wellSampleOptional = getFields(well).stream().filter(s -> s.getIndex().getValue() == field_id).findFirst();
        if (wellSampleOptional.isPresent())
            return wellSampleOptional.get();
        log.warn("Field Id " + field_id + "was not found");
        return null;

    }

    /**
     * Convenience method to get the final name of a well based on all the user parameters passed
     * Useful for when the fields in a well are stitched together
     *
     * @param well the well to get the name from
     * @return the name of the well image to use
     */
    public String getWellImageName(Well well) {

        int row = well.getRow().getValue() + 1;
        int col = well.getColumn().getValue() + 1;
        String project = getPlateName();

        String name = String.format("%s - R%02d-C%02d", project, row, col);

        if (this.downsample > 1) name += "_Downsample-" + this.downsample;
        if (this.is_projection) name += "_Projected-" + ZProjector.METHODS[this.projection_type];
        if (this.use_stitcher) name += "_Stitched";

        return name;
    }


    /**
     * Returns a usable image name that reflects the field that was selected
     *
     * @param field the field (WellSample) to get the name from, this contains the well information directly
     * @return the name of the image related to this Field (in a specific well)
     */
    public String getFieldImageName(WellSample field) {
        int row = field.getWell().getRow().getValue() + 1;
        int col = field.getWell().getColumn().getValue() + 1;
        String field_id = field.getID();
        String local_field_id = field_id.substring(field_id.lastIndexOf(":") + 1);

        String project = utils.safeName(field.getWell().getPlate().getName());

        String name = String.format("%s - R%02d-C%02d-F%s", project, row, col, local_field_id);
        if (this.downsample > 1) name += "_Downsample-" + this.downsample;
        if (this.is_projection) name += "_Projected-" + ZProjector.METHODS[this.projection_type];

        return name;

    }

    private ImagePlus getStitchedWellImage(Well well, List<WellSample> fields, Roi subregion) {

        String imageName = getWellImageName(well);

        // Build file for well stitching
        File stitchingfFile = new File(save_folder, imageName + ".txt");

        if (fields == null) fields = getFields(well);
        if (subregion != null) fields = getIntersectingFields(fields, subregion);

        AtomicInteger index = new AtomicInteger(0);

        ArrayList<ImageCollectionElement> stitchingElements = new ArrayList<>();

        fields.forEach(f -> {
            ImagePlus field = getFieldImage(f);

            Point positionXY = utils.getUncalibratedCoordinates(f);

            int dimensionality = field.getNSlices() > 1 ? 3 : 2;

            float[] offset = dimensionality > 2 ? new float[]{0.0f, 0.0f, 0.0f} : new float[]{0.0f, 0.0f};

            if (positionXY != null) {
                offset[0] = (float) positionXY.getLongPosition(0) / this.downsample;
                offset[1] = (float) positionXY.getLongPosition(1) / this.downsample;

            }
            ImageCollectionElement element = new ImageCollectionElement(stitchingfFile, index.getAndIncrement());
            element.setOffset(offset);
            element.setDimensionality(dimensionality);
            element.setImagePlus(field);
            element.setModel(dimensionality == 2 ? new TranslationModel2D() : new TranslationModel3D());
            stitchingElements.add(element);
        });
        if (this.stitching_parameters == null) {
            stitching_parameters = new StitchingParameters();

            stitching_parameters.channel1 = 0;
            stitching_parameters.channel2 = 0;
            stitching_parameters.timeSelect = 0;
            stitching_parameters.checkPeaks = 5;
            stitching_parameters.fusionMethod = 0;
            stitching_parameters.regThreshold = 0.3;
            stitching_parameters.relativeThreshold = 2.5;
            stitching_parameters.absoluteThreshold = 3.5;
            stitching_parameters.dimensionality = stitchingElements.get(0).getDimensionality();
        }

        // Align tiles
        int size = stitchingElements.size();
        ArrayList<ImagePlusTimePoint> optimized;
        ArrayList<ImagePlus> images = new ArrayList<>(size);
        ArrayList<InvertibleBoundable> models = new ArrayList<>(size);

        optimized = CollectionStitchingImgLib.stitchCollection(stitchingElements, stitching_parameters);

        for (ImagePlusTimePoint imagePlusTimePoint : optimized) {
            images.add(imagePlusTimePoint.getImagePlus());
            models.add((InvertibleBoundable) imagePlusTimePoint.getModel());
        }

        try {
            ImagePlus fused;
            switch (images.get(0).getBitDepth()) {
                case 8:
                    UnsignedByteType fusedImageByte = new UnsignedByteType();
                    fused = Fusion.fuse(fusedImageByte, images, models, stitchingElements.get(0).getDimensionality(), false, 0, null, false, false, false);
                    break;
                case 32:
                    FloatType fusedImageFloat = new FloatType();
                    fused = Fusion.fuse(fusedImageFloat, images, models, stitchingElements.get(0).getDimensionality(), false, 0, null, false, false, false);
                    break;
                default:
                    UnsignedShortType fusedImageShort = new UnsignedShortType();
                    fused = Fusion.fuse(fusedImageShort, images, models, stitchingElements.get(0).getDimensionality(), false, 0, null, false, false, false);
                    break;
            }

            fused.setTitle(imageName);

            // Make it look like the original image, in terms of calibration and colors
            ImagePlus ref = images.get(0);
            for (int s = 0; s < ref.getStackSize(); s++) {
                LUT lut = ref.getStack().getProcessor(s + 1).getLut();
                fused.getStack().getProcessor(s + 1).setLut(lut);
            }
            fused.setCalibration(ref.getCalibration());
            fused.setProperties(ref.getPropertiesAsArray());
            fused.setProperty("Stitching", "Using OperettaImporter");
            for (int c = 0; c < ref.getNChannels(); c++) {
                fused.setPosition(c + 1, 1, 1);
                fused.resetDisplayRange();
            }
            return fused;
        } finally {
            images.forEach(ImagePlus::close);
        }
    }

    /**
     * Overloaded method, for simplification
     *
     * @param well The well to export. All fields will be stitched
     * @return the resulting ImagePlus ( C,Z,T Hyperstack ), calibrated
     */
    public ImagePlus getWellImage(Well well) {
        return getWellImage(well, null, null);
    }

    /**
     * Returns a stitched stack for the given well and associated fields
     *
     * @param well   the well to export
     * @param fields the fields that we want to use for this well
     * @param bounds a ROI describing the subregion we want to export (pixel coordinates)
     * @return an ImageStack
     */
    public ImagePlus getWellImage(Well well, List<WellSample> fields, final Roi bounds) {

        if (this.use_stitcher) {
            return getStitchedWellImage(well, fields, bounds);
        }

        // Get the positions for each field (called a sample by BioFormats) in this well
        if (fields == null) fields = well.copyWellSampleList();
        // Out of these coordinates, keep only those that are intersecting with the bounds
        final List<WellSample> adjusted_fields = getIntersectingFields(fields, bounds);
        // Problem with ROI bounds is that they are typically given relative to the fused image

        if (adjusted_fields.isEmpty()) return null;

        int a_field_id = fields.get(0).getIndex().getValue();

        // We need to know the width and height of a single image
        int sample_width = metadata.getPixelsSizeX(a_field_id).getValue();
        int sample_height = metadata.getPixelsSizeY(a_field_id).getValue();

        // Get extents for the final image
        Point topLeftCoordinates = utils.getTopLeftCoordinates(fields);
        Point bottomRightCoordinates = utils.getBottomRightCoordinates(fields);

        // If we can't find the coordinates, we have no way of knowing the size of the final image
        if (topLeftCoordinates == null || bottomRightCoordinates == null) {
            log.error("Could not find coordinates for well " + well);
            return null;
        }

        long well_width = bottomRightCoordinates.getLongPosition(0) - topLeftCoordinates.getLongPosition(0) + sample_width;
        long well_height = bottomRightCoordinates.getLongPosition(1) - topLeftCoordinates.getLongPosition(1) + sample_height;

        // If there is a region, then the final width and height will be the same
        if (bounds != null) {
            well_width = bounds.getBounds().width;
            well_height = bounds.getBounds().height;
        }

        // Finally, correct for downscaling
        well_width /= this.downsample;
        well_height /= this.downsample;

        // Confirm the range based on the available metadata
        final HyperRange range2 = range.confirmRange(metadata);

        final int n = range2.getTotalPlanes();

        // TODO: Bit depth is hard coded here, but it could be made variable
        final ImageStack wellStack = ImageStack.create((int) well_width, (int) well_height, n, 16);

        AtomicInteger ai = new AtomicInteger(0);

        adjusted_fields.forEach(field -> {
            // sample subregion should give the ROI coordinates for the current sample that we want to read
            Roi subregion = getFieldSubregion(field, bounds, topLeftCoordinates);

            final int field_counter = ai.getAndIncrement();

            if (subregion != null) {
                final Point pos = utils.getFieldAdjustedCoordinates(field, bounds, subregion, topLeftCoordinates);
                log.info(String.format("Sample Position: %d, %d", pos.getLongPosition(0), pos.getLongPosition(1)));

                final ImageStack stack = getFieldImage(field, subregion).getStack();

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

        if (wellStack == null) return null;

        int[] czt = this.range.getCZTDimensions();

        String imageName = getWellImageName(well);
        ImagePlus result = new ImagePlus(imageName, wellStack);

        if ((czt[0] + czt[1] + czt[2]) > 3)
            result = HyperStackConverter.toHyperStack(result, czt[0], czt[1], czt[2]);

        Calibration cal = new Calibration(result);
        Calibration meta = utils.getCalibration();
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
            result = zp.getProjection();
        }
        // Do the calibration for the origin
        Point point = utils.getTopLeftCoordinatesUm(fields);

        cal.xOrigin = point.getDoublePosition(0) / cal.pixelWidth; // That's supposed to be in pixels
        cal.yOrigin = point.getDoublePosition(1) / cal.pixelHeight;

        result.setCalibration(cal);

        return result;
    }

    /**
     * Exports the current field as an ImagePlus
     *
     * @param field the Field to export
     * @return a calibrated ImagePlus
     */
    public ImagePlus getFieldImage(WellSample field) {
        return getFieldImage(field, null);
    }

    /**
     * Exports the current field as an ImagePlus
     *
     * @param field     the Field to export, get is through the {@link OperettaManager#getFields(Well)}
     * @param subregion an arbitrary square ROI to extract from the provided fields
     * @return a calibrated ImagePlus
     */
    public ImagePlus getFieldImage(WellSample field, Roi subregion) {

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
        stack_width /= this.downsample;
        stack_height /= this.downsample;

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

                                // Add option to downsample with averaging
                                ip = ip.resize(ip.getWidth() / this.downsample, ip.getHeight() / this.downsample, this.use_averaging);

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


        if (stack == null) return null;

        int[] czt = this.range.getCZTDimensions();

        String imageName = getFieldImageName(field);
        ImagePlus result = new ImagePlus(imageName, stack);

        if ((czt[0] + czt[1] + czt[2]) > 3)
            result = HyperStackConverter.toHyperStack(result, czt[0], czt[1], czt[2]);

        Calibration cal = new Calibration(result);
        Calibration meta = utils.getCalibration();
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
            result = zp.getProjection();
        }
        // Do the calibration for the origin
        Point point = utils.getTopLeftCoordinatesUm(Collections.singletonList(field));

        cal.xOrigin = point.getDoublePosition(0) / cal.pixelWidth; // That's supposed to be in pixels
        cal.yOrigin = point.getDoublePosition(1) / cal.pixelHeight;

        result.setCalibration(cal);

        sw.stop();
        log.info("Field " + field.getID() + " stack " + series_id + " took " + ((double) sw.getElapsedTime() / 1000.0) + " seconds");
        return result;
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
     * Processes all fields in all wells based on the desired builder options.
     * @see OperettaManager.Builder
     */
    public void process() { process( null, null, null ); }

    /**
     * Processes all fields in the selected wells based on the desired builder options.
     * @see OperettaManager.Builder
     *
     * @param wells the list of Wells we want to process
     */
    public void process(List<Well> wells) { process( wells, null, null ); }

    /**
     * this method tries to simplify the processing for a full export
     *
     * @param region an optional Roi to export, set to null for whole image
     */
    public void process(Roi region) {
        process(null, null, region);
    }

    /**
     * this method tries to simplify the processing for a full export
     *
     * @param wells  all the Wells to process as a list
     * @param fields all the Field IDs to process, as a list, set to null to process all
     * @param region an optional Roi to export, set to null for whole image
     */
    public void process(List<Well> wells, List<Integer> fields, Roi region) {
        // Process everything
        // decide whether we process wells or fields
        if (wells == null) {
            wells = getWells();
        }

        List<WellSample> well_fields;
        AtomicInteger iWell = new AtomicInteger();

        Instant global_start = Instant.now();

        Task taskWell = null;
        Task taskField = null;
        if (taskService != null) {
            taskWell = taskService.createTask("Export of " + wells.size() + " well(s)");
            taskWell.setProgressMaximum(wells.size());
            taskWell.start();
        }

        double percentageCompleteness;

        try {
            for (Well well : wells) {
                if (taskWell != null) {
                    if (taskWell.isCanceled()) {
                        IJ.log("The export task has been cancelled: " + taskWell.getCancelReason());
                        return;
                    }
                    taskWell.setStatusMessage("- Well " + well.getID());
                }
                log.info("Well: {}", well);
                IJ.log("- Well " + well.getID() + " (" + iWell + "/" + wells.size() + " )");//);
                Instant well_start = Instant.now();

                if (fields != null) {
                    well_fields = fields.stream().map(well::getWellSample).collect(Collectors.toList());
                } else {
                    // Get the samples associates with the current well, by index
                    well_fields = well.copyWellSampleList();
                }

                // Work on each field independently
                if (!this.fuse_fields) {
                    if (region != null) {
                        well_fields = getIntersectingFields(well_fields, region);
                    }

                    if (taskService != null) {
                        taskField = taskService.createTask("Export " + well_fields.size() + " Fields");
                        taskField.start();
                        taskField.setProgressMaximum(well_fields.size());
                    }
                    AtomicInteger iField = new AtomicInteger();
                    try {
                        for (WellSample field : well_fields) {
                            if (taskField != null) {
                                if (taskField.isCanceled()) {
                                    assert taskWell != null;
                                    taskWell.cancel("Downstream task cancelled.");
                                    break;
                                }
                                taskField.setStatusMessage("- " + field.getID());
                            }
                            iField.incrementAndGet();
                            IJ.log("\t - Field " + field.getID() + " (" + iField + "/" + well_fields.size() + ")");//);
                            ImagePlus field_image = getFieldImage(field, null);
                            String name = getFieldImageName(field);
                            if (field_image != null)
                                IJ.saveAsTiff(field_image, new File(save_folder, name + ".tif").getAbsolutePath());
                            percentageCompleteness = (iWell.get() / (double) wells.size() + iField.get() / (double) (well_fields.size() * wells.size())) * 100;
                            utils.printTimingMessage(global_start, percentageCompleteness);
                            if (taskField != null) taskField.setProgressValue(iField.get());
                        }
                        // Save the positions file
                        // Get the positions that were used, just compute them again
                        try {
                            writeWellPositionsFile(well_fields, new File(save_folder, getWellImageName(well) + ".txt"));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                    } finally {
                        if (taskField != null) {
                            taskField.finish();
                        }
                    }
                } else {
                    // Need to give all the fields, otherwise we will get the origin wrong
                    ImagePlus well_image = this.getWellImage(well, well_fields, region);
                    String name = getWellImageName(well);
                    if (well_image != null) {
                        IJ.saveAsTiff(well_image, new File(save_folder, name + ".tif").getAbsolutePath());
                    }
                }
                Instant ends = Instant.now();
                IJ.log(" - Well processed in " + Duration.between(well_start, ends).getSeconds() + " s.");
                iWell.incrementAndGet();
                if (taskWell != null) taskWell.setProgressValue(iWell.get());
                percentageCompleteness = (iWell.get() / (double) wells.size()) * 100;
                utils.printTimingMessage(global_start, percentageCompleteness);
            }

            Instant global_ends = Instant.now();
            IJ.log(" DONE! All wells processed in " + (Duration.between(global_start, global_ends).getSeconds() / 60) + " min.");
        } finally {
            if (taskWell != null) {
                taskWell.finish();
            }
            if (taskField != null) {
                taskField.finish();
            }
        }
    }


    @Override
    public String toString() {
        int nWells = getWells().size();
        int nFields = getFieldIds().size();
        long[] dims = utils.getIODimensions();

        String expName;
        try {
            expName = getPlateName();
        } catch (IndexOutOfBoundsException e) {
            expName = "Unknown";
        }

        String dataInfo = String.format("Operetta plate '%s' contains: \n\t %d Wells, each containing \n\t %d Fields, with dimensions \n\t (X, Y, Z, C, T) = (%d, %d, %d, %d, %d)\n\tFile location: '%s'", expName, nWells, nFields, dims[0], dims[1], dims[2], dims[3], dims[4], this.id.getParentFile().getAbsolutePath());

        // Build some information about the state of the Importer
        dataInfo += "\n\n Operetta Manager parameters:\n";

        dataInfo += String.format("- Downsample factor: %d\n", downsample);
        dataInfo += String.format("\t- Use averaging when downsampling: %b\n", use_averaging);

        dataInfo += String.format("- Fusing fields: %b\n- Use Grid/Collection Stitching for fusion: %b\n\n", this.fuse_fields, this.use_stitcher);
        dataInfo += String.format("- Tile position correction factor: %.2f\n\t- Horizontal camera flip: %b\n\t- Vertical camera flip: %b\n\t", this.correction_factor, this.flip_horizontal, this.flip_vertical);
        dataInfo += String.format("- 32-bit Digital Phase Contrast image normalization\n\t\t- Min: %.2f\n\t\t- Max: %.2f\n\n", this.norm_min, this.norm_max);

        dataInfo += String.format("- Performing projection: %b\n\t- Projection type: %s\n\n", this.is_projection, ZProjector.METHODS[this.projection_type]);
        dataInfo += String.format("- Selected ranges:\n\t- Z: %s", HyperRange.prettyPrint(this.range.getRangeZ()));
        dataInfo += String.format("\n\t- C: %s", HyperRange.prettyPrint(this.range.getRangeC()));
        dataInfo += String.format("\n\t- T: %s", HyperRange.prettyPrint(this.range.getRangeT()));


        return dataInfo;
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
     * @throws IOException error in case of problem working with the positions file
     */
    public void writeWellPositionsFile(List<WellSample> samples, File position_file) throws IOException {
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
                String name = getFieldImageName(sample);
                Point pos = utils.getUncalibratedCoordinates(sample);
                if (pos != null) {
                    writer.write(String.format("%s.tif;      ;               (%d.0, %d.0%s)\n", name, pos.getLongPosition(0) / this.downsample, pos.getLongPosition(1) / this.downsample, z));
                } else {
                    writer.write(String.format("# %s.tif;      ;               (NaN, NaN%s)\n", name, z));
                }
            }
        }
    }

    /**
     * Finds fields related to the bounds that were given, to limit the number of files to export
     *
     * @param fields the fields to check intersections in
     * @param bounds the roi for which we are looking for the intersecting fields
     * @return a List of fields (WellSample s) that intersect with the given Roi
     */
    public List<WellSample> getIntersectingFields(List<WellSample> fields, Roi bounds) {
        // Coordinates are in pixels
        // bounds are in pixels

        // Coordinates are set to 0 for each well
        if (bounds == null) return fields;
        log.info("Looking for samples intersecting with {}, ", bounds);

        Point topLeftCoordinates = utils.getTopLeftCoordinates(fields);

        if (topLeftCoordinates == null) {
            log.error("No coordinates found for fields " + fields.toString() + " -> returning all fields.");
            return fields;
        }

        List<WellSample> selected = fields.stream().filter(s -> {

            int sample_id = s.getIndex().getValue();

            Long pX = utils.getUncalibratedPositionX(s);
            if (pX == null) return false;

            Long pY = utils.getUncalibratedPositionY(s);
            if (pY == null) return false;

            long x = pX - topLeftCoordinates.getLongPosition(0);
            long y = pY - topLeftCoordinates.getLongPosition(1);
            int w = metadata.getPixelsSizeX(sample_id).getValue();
            int h = metadata.getPixelsSizeY(sample_id).getValue();

            Roi other = new Roi(x, y, w, h);

            return utils.isOverlapping(bounds, other);

        }).collect(Collectors.toList());
        //imp.show();
        // Sort through them
        IJ.log("Selected Samples: " + selected);
        return selected;
    }

    /**
     * This determines the bounds of an ROI for a single field, for the export
     *
     * @param field   the field
     * @param bounds  the roi bounds
     * @param topLeftCoordinates the top left coordinate, as a Point()
     * @return the Roi, modified to fit the bounds
     */
    private Roi getFieldSubregion(WellSample field, Roi bounds, Point topLeftCoordinates) {

        // The field always contains the subregion, so we avoid checking for overlap
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

        Point coordinates = utils.getUncalibratedCoordinates(field);

        if (coordinates == null) return null;

        coordinates.move(new long[]{-topLeftCoordinates.getLongPosition(0), -topLeftCoordinates.getLongPosition(1)});
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

    public Utilities getUtilities() {
        return this.utils;
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

        private double correction_factor = 0.995;

        private boolean is_projection = false;
        private int projection_method = ZProjector.MAX_METHOD;

        private File save_folder = new File(System.getProperty("user.home"));

        private IFormatReader reader = null;

        private boolean flip_horizontal = false;
        private boolean flip_vertical = false;

        private TaskService taskService = null;
        private int downsample = 1;
        private boolean is_fuse_fields = false;
        private boolean is_use_stitcher = false;
        private StitchingParameters stitching_parameters = null;
        private boolean use_averaging = false;

        public Builder setStitchingParameters(StitchingParameters stitching_parameters) {
            this.stitching_parameters = stitching_parameters;
            this.is_use_stitcher = true;
            this.is_fuse_fields = true;
            return this;
        }

        public Builder fuseFields(boolean is_fuse_fields) {
            this.is_fuse_fields = is_fuse_fields;
            if( !is_fuse_fields) this.is_use_stitcher = false;
            return this;
        }

        public Builder useStitcher( boolean is_use_stitcher) {
            this.is_use_stitcher = is_use_stitcher;
            if (is_use_stitcher)  this.is_fuse_fields = true;
            return this;
        }

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
         * The projection method to use
         *
         * @param method String that matches one of the strings in
         *               <a href="https://imagej.nih.gov/ij/developer/api/ij/plugin/ZProjector.html#METHODS">the ZProjector</a>
         * @return a Builder object, to continue building parameters
         */
        public Builder setProjectionMethod(String method) {
            if (Arrays.asList(ZProjector.METHODS).contains(method)) {
                this.projection_method = Arrays.asList(ZProjector.METHODS).indexOf(method);
                this.is_projection = true;
            } else {
                this.is_projection = false;
            }
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
         * This sets the id (the path to the image file), as per BioFormat's definition
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
         * As an alternative to using an id, when the dataset is big, one can provide
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
         * Can provide a range (Channels, Slices and Time points) to use for export. If none are provided, will
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
            boolean success = save_folder.mkdirs();
            if (!success) {
                if (!save_folder.exists()) {
                    throw new RuntimeException("Could not create output folder " + save_folder.getAbsolutePath());
                }
                if (!save_folder.isDirectory()) {
                    throw new RuntimeException("The destination path is not a folder (" + save_folder.getAbsolutePath() + ")");
                }
            }
            this.save_folder = save_folder;
            return this;
        }

        /**
         * Add the possibility to overwrite the coordinates correction factor in the builder
         * And not affect the stored value in the {@link ch.epfl.biop.operetta.commands.OperettaImporterHiddenSettings} class
         *
         * @param correction_factor the correction factor (Keep it close to 1)
         * @return a Builder object, to continue building parameters
         */
        public Builder coordinatesCorrectionFactor(double correction_factor) {
            this.correction_factor = correction_factor;
            return this;
        }

        /**
         * Set the downsampling from the builder
         *
         * @param downsample a downsample factor &gt; 1 explaining by how much we should reduce the XY image
         * @return a Builder object, to continue building parameters
         */
        public Builder setDownsample(int downsample) {
            this.downsample = downsample;
            return this;
        }

        /**
         * Use averaging when downsampling the images
         * @param use_averaging true if we use averaging when downsampling the images
         * @return a Builder object, to continue building parameters
         */
        public Builder useAveraging( boolean use_averaging) {
            this.use_averaging = use_averaging;
            return this;
        }

        /**
         * Optional: adds a way to monitor the progression of a process using scijava TaskService
         *
         * @param taskService a service that monitors the progression of a process
         * @return this Builder, to continue building options
         */
        public Builder setTaskService(TaskService taskService) {
            this.taskService = taskService;
            return this;
        }

        /**
         * The build method handles creating an {@link OperettaManager} object from all the settings that were provided.
         * This is done so that everything, like the {@link HyperRange} that is defined is valid.
         *
         * @return the instance to the OperettaManager.
         */
        public OperettaManager build() {

            if (this.id != null) {
                File id = this.id;

                if (this.id.isDirectory()) {
                    XMLFILE file = null;
                    for (XMLFILE version : XMLFILE.values()) {
                        File candidate = new File(this.id, version.getIndexFileName());
                        if (candidate.exists()) {
                            file = version;
                            break;
                        }
                    }
                    // A few checks and warning for big files
                    if (file == null) {
                        log.error("o matching Index files found in " + this.id.getAbsolutePath());
                        log.error("Implemented valid Index files:");
                        for (XMLFILE version : XMLFILE.values()) {
                            log.error("\t" + version.getIndexFileName() + " (" + version.getDescription() + ")");
                        }
                        return null;
                    }
                    // If we found the file, set it
                    id = new File(this.id, file.getIndexFileName());
                }

                try {
                    // Create the reader
                    if (reader == null) {
                        reader = createReader(id.getAbsolutePath());
                    }
                } catch (Exception e) {
                    log.error("Issue when creating reader for file {}", id);
                    return null;
                }
            }
             if (this.range == null) {
                    this.range = new HyperRange.Builder().fromMetadata((IMetadata) reader.getMetadataStore()).build();
                } else {
                    if (this.range.getTotalPlanes() == 0) {
                        HyperRange new_range = new HyperRange.Builder().fromMetadata((IMetadata) reader.getMetadataStore()).build();
                        if (!this.range.getRangeC().isEmpty()) new_range.setRangeC(this.range.getRangeC());
                        if (!this.range.getRangeZ().isEmpty()) new_range.setRangeZ(this.range.getRangeZ());
                        if (!this.range.getRangeT().isEmpty()) new_range.setRangeT(this.range.getRangeT());

                        this.range = new_range;
                    }
                }

                if (this.save_folder == null) {
                    log.warn("You did not specify a save path for the Operetta Manager object");
                }

                return new OperettaManager(reader,
                        this.downsample,
                        this.use_averaging,
                        this.range,
                        this.norm_min,
                        this.norm_max,
                        this.flip_horizontal,
                        this.flip_vertical,
                        this.is_projection,
                        this.projection_method,
                        this.save_folder,
                        this.correction_factor,
                        this.is_fuse_fields,
                        this.is_use_stitcher,
                        this.stitching_parameters,
                        this.taskService);
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

    }

    public class Utilities {
        /**
         * The name can have special characters which we replace with underscores, for the file name export
         *
         * @param nameUnsafe The original name that should be sanitized
         * @return a name with all unsafe characters turned to "_"
         */
        private String safeName(String nameUnsafe) {
            return nameUnsafe.replaceAll("[^\\w\\s\\-_]", "_");
        }

        /**
         * Print some information about how long the processing is
         *
         * @param start                  When the processing started
         * @param percentageCompleteness how far along we are along with teh processing globally. For instance how
         *                               many wells are already processed
         */
        private void printTimingMessage(Instant start, double percentageCompleteness) {
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
         * Get the image calibration from the OMEXML Metadata
         *
         * @return an ImageJ Calibration to use on an ImagePlus
         */
        public Calibration getCalibration() {
            // Get the dimensions
            int[] czt = range.getCZTDimensions();

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

            // Trick to get the times: It is the deltaT between time points of any image, so we need the deltaT if the second time point
            // which is ( nChannels * nSlices ) planes later

            if (range.getCZTDimensions()[2] > 1) { // We need at least a second time point !!
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
         * Returns the X position of the field in pixels
         *
         * @param field the field where we want the X position
         * @return the X position of the field
         */
        private Long getUncalibratedPositionX(WellSample field) {
            Length px = field.getPositionX();

            if (px == null) return null;

            double px_m = px.value(UNITS.MICROMETER).doubleValue();

            return Math.round(px_m / px_size.value(UNITS.MICROMETER).doubleValue() * correction_factor);
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

            return Math.round(px_m / px_size.value(UNITS.MICROMETER).doubleValue() * correction_factor);
        }

        /**
         * Returns the position of the field in pixels as a Point
         *
         * @param field the field for which we need to find the coordinates
         * @return a 2D Point with the xy pixel position of the field
         */
        public Point getUncalibratedCoordinates(WellSample field) {
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
         * @param subregion a sub roi
         * @param topLeftCoordinates   the top left coordinate point
         * @return the new coordinate for the given Field
         */
        public Point getFieldAdjustedCoordinates(WellSample field, Roi bounds, Roi subregion, Point topLeftCoordinates) {

            //return new Point(subregion.getBounds().x, subregion.getBounds().y);

            Point pos = getUncalibratedCoordinates(field);

            if (pos == null) {
                log.error("Could not find position for field " + field);
                throw new RuntimeException("Could not find position for field " + field);
            }

            // After this, pos is the absolute position of the current sample in pixels and that should be it
            pos.move(new long[]{-topLeftCoordinates.getLongPosition(0), -topLeftCoordinates.getLongPosition(1)});

            // Because there are bounds, we might need to refine this position to account for the fact we only
            // took a subregion from the original image
            if (bounds != null)
                pos.move(new long[]{subregion.getBounds().x - bounds.getBounds().x, subregion.getBounds().y - bounds.getBounds().y});

            // We need to offset the coordinates by the global minimum (top left) coordinates
            pos.setPosition(new long[]{(pos.getLongPosition(0)) / downsample, (pos.getLongPosition(1)) / downsample});
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

            if (fields.isEmpty()) {
                System.err.println("Cannot find coordinates");
                return null;
            }

            Optional<WellSample> minx = fields.stream().min(Comparator.comparing(WellSample::getPositionX));
            Optional<WellSample> miny = fields.stream().min(Comparator.comparing(WellSample::getPositionY));

            if (!minx.isPresent() || !miny.isPresent()) {
                log.info("Could not find top left coordinates for fields");
                return null;
            }

            Long px = getUncalibratedPositionX(minx.get());
            Long py = getUncalibratedPositionY(miny.get());
            return new Point(px, py);
        }

        /**
         * Returns the top left coordinates as a point
         *
         * @param fields the fields we should get the coordinates for
         * @return a point with the xy pixel coordinates
         */
        public Point getTopLeftCoordinatesUm(java.util.List<WellSample> fields) {
            fields = fields.stream()
                    .filter(sample -> sample.getPositionX() != null)
                    .filter(sample -> sample.getPositionX().value() != null)
                    .collect(Collectors.toList());

            Optional<WellSample> minx = fields.stream().min(Comparator.comparing(WellSample::getPositionX));
            Optional<WellSample> miny = fields.stream().min(Comparator.comparing(WellSample::getPositionY));

            if (!minx.isPresent() || !miny.isPresent()) {
                log.info("Could not find top left coordinates for fields");
                return new Point(0, 0);
            }

            Long px = minx.get().getPositionX().value(UNITS.MICROMETER).longValue();
            Long py = miny.get().getPositionY().value(UNITS.MICROMETER).longValue();

            return new Point(px, py);
        }

        /**
         * Returns the bottom right coordinates as a point
         *
         * @param fields the fields we should get the coordinates for
         * @return a point with the xy pixel coordinates
         */
        public Point getBottomRightCoordinates(List<WellSample> fields) {
            fields = fields.stream().filter(sample -> sample.getPositionY() != null).collect(Collectors.toList());

            if (!fields.isEmpty()) {
                Optional<WellSample> maxX = fields.stream().max(Comparator.comparing(WellSample::getPositionX));
                Optional<WellSample> maxY = fields.stream().max(Comparator.comparing(WellSample::getPositionY));

                if (!maxX.isPresent() || !maxY.isPresent()) {
                    log.info("Could not find top left coordinates for fields");
                    return null;
                }

                Long px = getUncalibratedPositionX(maxX.get());
                Long py = getUncalibratedPositionY(maxY.get());

                if (px != null && py != null) {
                    return new Point(px, py);
                } else {
                    return null;
                }
            } else {
                System.err.println("All fields are uncalibrated!");
                return null;
            }
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
         * TODO : fix estimation of output bytes - check if this is correct
         *
         * @param wells                all the Wells to process as a list
         * @param fields               all the Field IDs to process, as a list, set to null to process all
         * @return array of bytes that will be read from the dataset and written to the export folder when calling {@link OperettaManager#process(List, List, Roi)}
         */
        public long[] getIOBytes(List<Well> wells, List<Integer> fields) {

            long nWells = wells.size();
            long nFields = fields.size();
            long nTotalPlanes = range.getTotalPlanes(); // c / z / t
            long sX = main_reader.getSizeX();
            long sY = main_reader.getSizeY();

            // Output : more complicated
            // nWells is identical

            long nFieldsOut = nFields;

            //long nTotalPlanesOut = range.getTotalPlanes(); // c / z / t
            long sXOut = main_reader.getSizeX() / downsample;
            long sYOut = main_reader.getSizeY() / downsample;
            long sZOut = is_projection ? 1 : getRange().getRangeZ().size();
            long sCOut = getRange().getRangeC().size();
            long sTOut = getRange().getRangeT().size();

            return new long[]{sX * sY * nTotalPlanes * nFields * nWells * (long) 2, sXOut * sYOut * sZOut * sCOut * sTOut * nFieldsOut * nWells * (long) 2}; // Assuming 16 bits images
        }

        /**
         * Returns all dimensions for the data that we wish to export for size and time estimations
         *
         * @return an array with the full dataset sizes(sX, sY, sZ, sC, sT) and selected or
         * downscaled sizes (sXOut, sYOut, sZOut, sCOut, sTOut)
         */

        public long[] getIODimensions() {
            long sX = main_reader.getSizeX();
            long sY = main_reader.getSizeY();
            long sZ = main_reader.getSizeZ();
            long sC = main_reader.getSizeC();
            long sT = main_reader.getSizeT();

            long sXOut = main_reader.getSizeX() / downsample;
            long sYOut = main_reader.getSizeY() / downsample;
            long sZOut = is_projection ? 1 : getRange().getRangeZ().size();
            long sCOut = getRange().getRangeC().size();
            long sTOut = getRange().getRangeT().size();

            return new long[]{sX, sY, sZ, sC, sT, sXOut, sYOut, sZOut, sCOut, sTOut};
        }
    }
}
