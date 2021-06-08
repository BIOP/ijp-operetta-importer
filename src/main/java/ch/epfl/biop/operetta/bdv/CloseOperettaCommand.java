package ch.epfl.biop.operetta.bdv;

import bdv.viewer.SourceAndConverter;
import mpicbg.spim.data.generic.AbstractSpimData;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import sc.fiji.bdvpg.scijava.services.SourceAndConverterService;
import sc.fiji.bdvpg.services.SourceAndConverterServices;

import java.util.List;

/**
 * TODO : Improve 'specificity' this command doesn't care whether the dataset is from an operetta dataset
 */

@Plugin(type = Command.class,
        menuPath = "Plugins>BIOP>Close Operetta Dataset (Bdv)")
public class CloseOperettaCommand implements Command {

    @Parameter(label = "Choose the Operetta dataset to close")
    SourceAndConverter sac;

    @Parameter
    SourceAndConverterService source_service;

    @Override
    public void run() {
        if (source_service.getMetadata(sac, SourceAndConverterService.SPIM_DATA_INFO)==null) {
            System.err.println("No BDVDataset associated to the chosen source - Aborting save command");
            return;
        }

        AbstractSpimData asd =
                ((SourceAndConverterService.SpimDataInfo)SourceAndConverterServices.getSourceAndConverterService()
                        .getMetadata(sac, SourceAndConverterService.SPIM_DATA_INFO)).asd;

        List<SourceAndConverter> sources = source_service.getSourceAndConverterFromSpimdata(asd);

        source_service.remove(sources.toArray(new SourceAndConverter[0]));
    }

}
