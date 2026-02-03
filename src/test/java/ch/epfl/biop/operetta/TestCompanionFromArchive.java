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
                sqlitePath, xmlPath, tiffPath, flipY, swapXY);

        generator.run();

    }
}
