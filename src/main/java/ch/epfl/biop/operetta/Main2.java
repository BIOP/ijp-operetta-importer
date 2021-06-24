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
package ch.epfl.biop.operetta;

import ij.ImageJ;
import ij.ImageStack;
import org.perf4j.StopWatch;

import java.io.File;

@Deprecated
public class Main2 {

    public static void main(String[] args) {
        new ImageJ();
        //File id = new File("E:\\Sonia - TRONO\\DUX__2019-05-24T12_42_31-Measurement 1\\Images\\Index.idx.xml");
        File id = new File("X:\\Tiling\\Opertta Tiling Magda\\Images\\Index.idx.xml");

        OperettaManager op = new OperettaManager.Builder()
                .setId(id)
                .doProjection(true)
                .setSaveFolder(new File("D:\\Demo"))
                .build();

        StopWatch sw = new StopWatch();
        sw.start();
        //ImageStack s = op.readStack(1, 1);
        //Roi region = new Roi(1280, 3680, 5184, 2304);
        //Roi region = new Roi( 100, 100, 500, 500 );
        op.process(4, null, false);

        //Roi region = null;

        ImageStack s = null;
        //op.updateZRange("1");

        System.out.println("Time to open one well: " + sw.stop());
    }
}
