package ch.epfl.biop.operetta.utils;

import ch.epfl.biop.operetta.OperettaManager;
import ij.IJ;
import ij.ImagePlus;
import loci.formats.IFormatReader;
import loci.formats.meta.IMetadata;
import net.imglib2.Interval;
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

import java.io.File;
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
        int x = (int) cell.min( 2 );
        int y = (int) cell.min( 3 );
        int c = (int) cell.min( 2 );
        int z = (int) cell.min( 3 );
        int t = (int) cell.min( 4 );
        int w = (int) cell.min( 5 );

        // The issue is that we used to load the whole stack, now we just need one plane
        // So we need all the fields associated with the current well
        Well well = manager.getAvailableWells().get(w);

        List<WellSample> samples = well.copyWellSampleList();
        long[] xy = manager.getWellTileSize(well, 1, null);
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
            long[] min = {0,0,c,z,t};
            //xmin is the corrected coordinates
            long[] max = {xy[1],xy[2],c,z,t};
            IntervalView<UnsignedShortType> out2 = new IntervalView(cell, min, max);
           // out2= (IntervalView<UnsignedShortType>) Views.dropSingletonDimensions(out2);

            //IntervalView<UnsignedShortType> out = Views.hyperSlice(cell, 2, (int) z);
            LoopBuilder.setImages(in, out2).forEachPixel( (i, o) -> o.set(i));

        });


    }

    public static void main(String[] args) {


        OperettaManager opm = new OperettaManager.Builder()
                .setId(new File("X:\\Tiling\\Opertta Tiling Magda\\Images\\Index.idx.xml"))
                .build();

        Img<UnsignedShortType> image = createImage(opm);

        // Creates the image

        ImageJFunctions.show(image);
    }

    private static Img<UnsignedShortType> createImage(OperettaManager opm) {
        int[] czt =  opm.getRange().getCZTDimensions();
        long[] xy = opm.getWellTileSize(opm.getAvailableWells().get(0), 1, null);
        long nw = opm.getAvailableWells().size();

        long[] dimensions = new long[] {xy[0], xy[1], czt[0], czt[1], czt[2], nw};
        ReadOnlyCachedCellImgFactory roccif = new ReadOnlyCachedCellImgFactory(ReadOnlyCachedCellImgOptions.options()
                .cellDimensions((int) xy[0], (int) xy[1], 1));

        Img<UnsignedShortType> image = roccif.create(dimensions, new UnsignedShortType(), new TiledCellReader(opm));
        return image;
    }

}
