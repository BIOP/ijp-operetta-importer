package ch.epfl.biop.operetta.utils;

import ch.epfl.biop.operetta.OperettaManager;
import ij.IJ;
import ij.ImagePlus;
import loci.formats.IFormatReader;
import loci.formats.meta.IMetadata;
import net.imglib2.cache.img.*;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.loops.LoopBuilder;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Util;
import net.imglib2.view.IntervalView;
import net.imglib2.view.Views;
import ome.xml.model.Well;
import ome.xml.model.WellSample;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TiledCellReader implements CellLoader <UnsignedShortType> {

    // This needs the reader and access to the metadata
    private final IFormatReader reader;
    private final IMetadata meta;
    private final CZTRange range;
    private final OperettaManager manager;

    // Attempt to read all plane data of a well into a virtual image, assume well is fixed for now
    public TiledCellReader(OperettaManager manager) {
        this.manager = manager;
        this.reader = manager.getReader();
        this.meta = (IMetadata) reader.getMetadataStore();
        this.range = new CZTRange.Builder().fromMetadata(meta).build();
    }


    @Override
    public void load(SingleCellArrayImg<UnsignedShortType, ?> cell) throws Exception {
        int x = (int) cell.min( 0 );
        int y = (int) cell.min( 1 );
        int c = (int) cell.min( 2 );
        int z = (int) cell.min( 3 );
        int t = (int) cell.min( 4 );

        // The issue is that we used to load the whole stack, now we just need one plane
        // So we need all the fields associated with the current well
        Well well = manager.getAvailableWells().stream().findFirst().get(); // Get the first one

        List<WellSample> samples = well.copyWellSampleList();

        // these are the ones we need to open
        // Need to figure out the indexing in advance of a parallel loop
        List<String> sampleFiles = samples.stream().map(s -> {
            int id = s.getIndex().getValue();
            reader.setSeries(id);
            List<String> files = Arrays.asList(reader.getSeriesUsedFiles(false));
            String file = files.get(reader.getIndex(c, z, t));
            //Need the positions, later
            return file;
        }).collect(Collectors.toList());


        sampleFiles.parallelStream().forEach(file -> {
            ImagePlus image = IJ.openImage(file);
            Img<UnsignedShortType> in = ImageJFunctions.wrapShort(image);
            // How to slice when more than one dimension is needed ?Views.
            IntervalView<UnsignedShortType> out = Views.hyperSlice(cell, 2, (int) z);
            LoopBuilder.setImages(in, out).forEachPixel( (i, o) -> o.set(i));

        });


    }

    public static void main(String[] args) {

        ReadOnlyCachedCellImgFactory roccif = new ReadOnlyCachedCellImgFactory(ReadOnlyCachedCellImgOptions.options()
                .cellDimensions(171, 196, 1));

        // Creates the image
        Img<UnsignedShortType> image = roccif.create(new long[]{171, 196, 5}, new UnsignedShortType(), new DemoReaderCellImg(files));

        ImageJFunctions.show(image);
    }

}
