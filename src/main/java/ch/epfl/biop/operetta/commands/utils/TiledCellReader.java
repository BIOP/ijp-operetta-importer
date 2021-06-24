/*-
 * #%L
 * Hold your horses
 * %%
 * Copyright (C) 2019 - 2021 BIOP
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
import ij.IJ;
import net.imagej.ImgPlus;
import net.imagej.axis.Axes;
import net.imglib2.Point;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.ReadOnlyCachedCellImgFactory;
import net.imglib2.cache.img.ReadOnlyCachedCellImgOptions;
import net.imglib2.cache.img.SingleCellArrayImg;
import net.imglib2.img.Img;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import ome.xml.model.Well;
import ome.xml.model.WellSample;
import org.perf4j.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This is an implementation of ImgLib2's CellLoader to see how to best load data in a lazy way
 * The result is a virtual stack we can browse of a whole well. It is responsive enough, but should be
 * used with a downsampling of 16 at least...
 */
public class TiledCellReader implements CellLoader<UnsignedShortType> {

    private static final Logger logger = LoggerFactory.getLogger(TiledCellReader.class);
    // This needs the reader and access to the metadata
    private final ImageFetcher image_fetcher;
    private final OperettaManager manager;
    private final int downscale;

    private Well well;


    public TiledCellReader(OperettaManager manager, Well well, int downscale) {
        this.downscale = downscale;
        this.manager = manager;
        image_fetcher = new ImageFetcher(manager);
        this.well = well;

    }

    public static ImgPlus<UnsignedShortType> createLazyImage(OperettaManager opm, Well well, int downscale) {
        int[] czt = opm.getRange().getCZTDimensions();
        IJ.log("CZT:" + czt[0] + "," + czt[1] + "," + czt[2]);
        long[] xy = getWellTileSize(opm, well, downscale);

        long[] dimensions = new long[]{xy[0], xy[1], czt[0], czt[1], czt[2]};

        List<Long> list = Arrays.stream(dimensions).boxed().collect(Collectors.toList());
        logger.info(list.toString());

        ReadOnlyCachedCellImgFactory roccif = new ReadOnlyCachedCellImgFactory(ReadOnlyCachedCellImgOptions.options()
                .cellDimensions((int) xy[0], (int) xy[1], 1));

        Img<UnsignedShortType> image = roccif.create(dimensions, new UnsignedShortType(), new TiledCellReader(opm, well, downscale));

        ImgPlus<UnsignedShortType> img = new ImgPlus<>(image);
        img.axis(0).setType(Axes.X);
        img.axis(1).setType(Axes.Y);
        img.axis(2).setType(Axes.CHANNEL);
        img.axis(3).setType(Axes.Z);
        img.axis(4).setType(Axes.TIME);

        return img;
    }

    private static long[] getWellTileSize(OperettaManager opm, Well well, int downscale) {
        // Get the positions for each field (called a sample by BioFormats) in this well
        List<WellSample> fields = well.copyWellSampleList();

        int a_field_id = fields.get(0).getIndex().getValue();
        // We need to know the width and height of a single image
        int sample_width = opm.getMetadata().getPixelsSizeX(a_field_id).getValue();
        int sample_height = opm.getMetadata().getPixelsSizeY(a_field_id).getValue();

        // Get extents for the final image
        Point topleft = opm.getTopLeftCoordinates(fields);
        Point bottomright = opm.getBottomRightCoordinates(fields);

        long well_width = bottomright.getLongPosition(0) - topleft.getLongPosition(0) + sample_width;
        long well_height = bottomright.getLongPosition(1) - topleft.getLongPosition(1) + sample_height;

        // Finally, correct for downscaling
        well_width /= downscale;
        well_height /= downscale;

        return new long[]{well_width, well_height};
    }

    @Override
    public void load(SingleCellArrayImg<UnsignedShortType, ?> cell) throws Exception {

        //Pick up a timer
        StopWatch stopWatch = new StopWatch("lazycellreader", "Lazy Cell Reader Stopwatch");
        stopWatch.start();
        // x and y are the first two dimensions
        int c = (int) cell.min(2);
        int z = (int) cell.min(3);
        int t = (int) cell.min(4);


        logger.info("Loading Well {}, Plane c{} z{} t{}", well.getID(), c, z, t);


        // We will load the entire stack so we will then make sure we can do this plane by plane
        // So we need all the fields associated with the current well
        List<WellSample> samples = well.copyWellSampleList();

        // Need information on the coordinates, thanks to the manager
        Point topleft = manager.getTopLeftCoordinates(samples);

        // Because this *Could* be a 5D image, we drop the dimensions that are equal to 1 before doing the copy
        IntervalView<UnsignedShortType> full_well_plane = (IntervalView<UnsignedShortType>) Views.dropSingletonDimensions(cell);

        // Load the images in parallel for each sample at each plane
        samples.parallelStream().forEach(sample -> {

            // Get the positions of the sample for the full well
            final Point pos = manager.getFieldAdjustedCoordinates(sample, null, null, topleft, downscale);

            // Pick up the image, from a file hash
            RandomAccessibleInterval<UnsignedShortType> single_field;
            //Make sure it is converted to 16-bit without an OP
            //Converters.convert( single_field, RealUnsignedShortConverter)
            single_field = image_fetcher.getImageFile(sample, c, z, t);
            // Nice functionality, you can downscale and translate the image very easily
            single_field = Views.subsample(single_field, downscale, downscale);
            single_field = Views.translate(single_field, pos.getLongPosition(0), pos.getLongPosition(1));

            // This copies the pixels in the right position
            LoopBuilder.setImages(single_field, Views.interval(full_well_plane, single_field)).forEachPixel((i, o) -> o.set(i));

        });

        String stop = stopWatch.stop();
        logger.info(stop);
    }

    /**
     * Sets the wel to load
     *
     * @param well an omexml well
     */
    public void setWell(Well well) {
        this.well = well;
    }

}
