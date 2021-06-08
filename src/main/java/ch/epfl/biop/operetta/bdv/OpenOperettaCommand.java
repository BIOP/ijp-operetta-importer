package ch.epfl.biop.operetta.bdv;

import ch.epfl.biop.bdv.bioformats.bioformatssource.BioFormatsBdvSource;
import ch.epfl.biop.bdv.bioformats.command.BasicOpenFilesWithBigdataviewerBioformatsBridgeCommand;
import ch.epfl.biop.bdv.bioformats.imageloader.BioFormatsSetupLoader;
import ch.epfl.biop.operetta.OperettaManager;
import ch.epfl.biop.operetta.commands.OperettaImporter;
import ij.IJ;
import loci.formats.IFormatReader;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.apache.commons.io.FileUtils;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.CommandModule;
import org.scijava.command.CommandService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;

import java.io.File;
import java.util.concurrent.Future;
import java.util.function.Supplier;

@Plugin(type = Command.class,
        menuPath = "Plugins>BIOP>Open Operetta Dataset (Bdv)")
public class OpenOperettaCommand implements Command {

    @Parameter(label = "Select the 'Images' folder of your Operetta dataset", style = "directory")
    File folder;

    @Parameter(
            required = false,
            label = "Physical units of the dataset",
            choices = {"MILLIMETER", "MICROMETER", "NANOMETER"}
    )
    public String unit = "MILLIMETER";

    @Parameter
    CommandService cs;

    @Parameter
    SourceAndConverterService sac_service;

    //@Parameter(type = ItemIO.OUTPUT)
    //OperettaManager operettaManager;

    @Override
    public void run() {
        // A few checks and warning for big files
        File f = new File(folder, "Index.idx.xml");
        if (!f.exists()) {
            IJ.log("Error, file "+f.getAbsolutePath()+" not found!");
            return;
        }

        int sizeInMb = (int) ((double)FileUtils.sizeOf(f)/(double)(1024*1024));
        IJ.log("- Opening Operetta dataset "+f.getAbsolutePath()+" (" + sizeInMb + " Mb)");

        File fmemo = new File(folder, ".Index.idx.xml.bfmemo");
        int estimatedOpeningTimeInMin;
        if (!fmemo.exists()) {
            estimatedOpeningTimeInMin = sizeInMb / 30; // 30 Mb per minute
            IJ.log("- No memo file, the first opening will take longer.");
        } else {
            estimatedOpeningTimeInMin = sizeInMb / 300; // 30 Mb per minute
            IJ.log("- Memo file detected.");
        }

        if (estimatedOpeningTimeInMin==0) {
            IJ.log("- Estimated opening time below 1 minute.");
        } else {
            IJ.log("- Estimated opening time = " + estimatedOpeningTimeInMin + " min.");
        }

        Future<CommandModule> openTask = cs.run(BasicOpenFilesWithBigdataviewerBioformatsBridgeCommand.class,true,
                    "files", new File[]{f},
                    "unit", unit,
                    "splitrgbchannels", false
                );

        int countSeconds = 0;
        while (!openTask.isDone()) {
            try {
                Thread.sleep(1000);
                countSeconds++;
            } catch (InterruptedException e) {
                IJ.log("Operetta dataste opening interrupted!");
                return;
            }
            if ((countSeconds % 20)==0) {
                IJ.log("- t = " + countSeconds + " s");
            }
        }

        IJ.log("Done! Opening the dataset took "+countSeconds+" s.");

        try {
            CommandModule open = openTask.get();
            AbstractSpimData asd = (AbstractSpimData) open.getOutput("spimdata");

            sac_service.setSpimDataName(asd, new File(folder.getParent()).getName());

            // Getting a reader to fetch metadata efficiently
            BioFormatsBdvSource firstSource =
                    (BioFormatsBdvSource)
                            ((BioFormatsSetupLoader<?,?>)asd.getSequenceDescription().getImgLoader().getSetupImgLoader(0)).concreteSource;

            //IFormatReader reader = firstSource.getReaderPool().createObject();//.acquire();

            IJ.log("- Starting exporter ... ");

            OperettaManager.Builder opmBuilder =  new OperettaManager.Builder()
                    .readerSupplier(firstSource.getReaderPool()::createObject);

            Supplier<OperettaManager.Builder> opmBuilderSupplier = () -> opmBuilder;

            cs.run(OperettaImporter.class,
                    true,
                    "id", f,
                    "opmBuilderSupplier", opmBuilderSupplier
            );

            //firstSource.getReaderPool().recycle(reader);
            //IJ.log("- All metadata parsed.");*/
        } catch (Exception e) {
            IJ.log("Error in opening task : "+e.getMessage());
            e.printStackTrace();
        }


    }



}
