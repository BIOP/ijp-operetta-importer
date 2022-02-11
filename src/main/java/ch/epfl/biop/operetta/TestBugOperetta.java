package ch.epfl.biop.operetta;

import loci.formats.MetadataTools;
import loci.formats.in.OperettaReader;
import loci.formats.meta.IMetadata;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.xml.meta.OMEXMLMetadataRoot;

import java.time.Instant;

public class TestBugOperetta {

    public final static void main(String... args) throws Exception {
        String operettaDatasetPath = "Z:\\public\\radiana.ferrero_UPDEPLA\\BUG_operetta_importer\\Images\\Index.idx.xml";
        //String operettaDatasetPath = "Z:\\temp-Nico\\20211006\\RPE1_mScarletCep63_GFPCep135_CollagenMatek35mmBin2_7hpostCent__2021-10-05T19_11_56-Measurement 2\\Images\\Index.idx.xml";
        //String operettaDatasetPath = "C:\\Users\\chiarutt\\Downloads\\f\\Index.idx.xml";
        BIOPOperettaReader reader = new BIOPOperettaReader();

        IMetadata meta = MetadataTools.createOMEXMLMetadata();
        reader.setMetadataStore(meta);

        reader.setId(operettaDatasetPath);

        System.out.println("Third Parsing Operetta XML Done : "+ java.sql.Timestamp.from(Instant.now()));

        // Only one plate
        int idxPlate = 0;
        int nWells = meta.getWellCount(idxPlate);

        OMEXMLMetadataRoot root = (OMEXMLMetadataRoot) (meta.getRoot());
        Length l = root.getPlate(0).getWell(1).getWellSample(0).getPositionX();
        System.out.println(l);

        for (int iWell=0;iWell<nWells;iWell++) {
            //System.out.println("iWell = "+iWell);
            double origin_X = meta.getWellSamplePositionX(idxPlate, iWell,1).value(UNITS.MICROMETER).doubleValue();
            double origin_Y = meta.getWellSamplePositionY(idxPlate, iWell,1).value(UNITS.MICROMETER).doubleValue();
            System.out.println("\t Origin X= "+origin_X);
            System.out.println("\t Origin Y= "+origin_Y);
        }

    }
}
