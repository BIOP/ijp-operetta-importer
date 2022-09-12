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

import loci.formats.IFormatReader;
import loci.formats.ImageReader;
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
        IFormatReader reader = new ImageReader();

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
