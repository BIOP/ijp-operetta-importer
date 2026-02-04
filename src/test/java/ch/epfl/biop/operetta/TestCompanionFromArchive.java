/*-
 * #%L
 * Hold your horses
 * %%
 * Copyright (C) 2019 - 2026 BIOP
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

import ch.epfl.biop.operetta.archive.CompanionFromArchiveGenerator;

public class TestCompanionFromArchive {
    /**
     * Test to check if the parsing of the Operetta XML is correct
     * @param args Not used
     * @throws Exception If something goes wrong in the OME XML parsing
     */
    public static void main(String... args) throws Exception {

        boolean swapXY = false;
        boolean flipY = false;
        String folder = "F:/archive/";

        String foldersqlite = "N:/public/linda.wedemann_GR-SCHUHMACHER/ArchiveIssue/Harmony-Archive/IMAGES/b60a54b4-c5de-4eb2-bcdd-12d4980b3973/";
        String folderXML = "N:/public/linda.wedemann_GR-SCHUHMACHER/ArchiveIssue/Harmony-Archive/XML/MEASUREMENT/";

        String xmlPath = folderXML+"b60a54b4-c5de-4eb2-bcdd-12d4980b3973.xml";
        String sqlitePath = foldersqlite+"IMAGES.sqlite";
        String tiffPath = foldersqlite+"00a614f9-eb33-469e-a565-b839d4049dc3.tiff";

        CompanionFromArchiveGenerator generator = new CompanionFromArchiveGenerator(
                sqlitePath, xmlPath, tiffPath, flipY, swapXY, ".lazy");

        generator.run();

    }
}
