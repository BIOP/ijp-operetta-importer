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
package ch.epfl.biop.operetta.archive;

import loci.common.services.ServiceFactory;
import loci.formats.ImageReader;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.PixelType;
import ome.xml.model.primitives.NonNegativeInteger;
import ome.xml.model.primitives.PositiveInteger;
import ome.units.UNITS;
import ome.units.quantity.Length;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

/**
 * Converts Harmony 4.9 Archive to OME-XML companion files.
 *
 * Generates companion OME-XML files with:
 * - Physical pixel size (corrected for binning and magnification)
 * - X/Y stage positions for each field
 */
public class CompanionFromArchiveGenerator {

    // 96-well plate standard dimensions (in mm)
    private static final double WELL_SPACING_MM = 9.0;
    private static final double PLATE_OFFSET_X_MM = 14.38;
    private static final double PLATE_OFFSET_Y_MM = 11.24;

    // Hardcoded image parameters (from original script)
    private static final int IMG_X = 1080;
    private static final int IMG_Y = 1080;
    private static final String DIMENSION_ORDER = "XYCZT";

    private String sqlitePath;
    private String xmlPath;
    private String tiffPath;
    private Double pixelSize;
    private boolean flipY = false;
    private boolean swapXY = false;

    private Map<Integer, Position> fieldPositions;
    private double magnification = 1.0;
    private int binningX = 1;
    private int binningY = 1;
    private Double physicalPixelSizeUm = null;

    String suffix = "";

    private static void printUsage() {
        System.out.println("Usage: java -jar harmony-ome-converter.jar <sqlite_path> [options]");
        System.out.println();
        System.out.println("Required:");
        System.out.println("  <sqlite_path>        Path to IMAGES.sqlite database file");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --xml-path <path>    Path to Harmony measurement XML file");
        System.out.println("  --tiff-path <path>   Path to sample TIFF file (for pixel size extraction)");
        System.out.println("  --pixel-size <um>    Physical pixel size in micrometers (alternative to --tiff-path)");
        System.out.println("  --flip-y             Flip Y coordinates (multiply by -1)");
        System.out.println("  --swap-xy            Swap X and Y coordinates");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  java -jar harmony-ome-converter.jar IMAGES.sqlite \\");
        System.out.println("    --xml-path measurement.xml --tiff-path sample.tiff");
    }

    public CompanionFromArchiveGenerator(
            String sqlitePath,
            String xmlPath,
            String tiffPath,
            boolean flipY,
            boolean swapXY,
            String suffixForCompanionFile) {
        this.swapXY = swapXY;
        this.flipY = flipY;
        this.tiffPath = tiffPath;
        this.xmlPath = xmlPath;
        this.sqlitePath = sqlitePath;
        this.suffix = suffixForCompanionFile;
    }

    private void parseArguments(String[] args) {
        sqlitePath = args[0];

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--xml-path":
                    if (i + 1 < args.length) {
                        xmlPath = args[++i];
                    }
                    break;
                case "--tiff-path":
                    if (i + 1 < args.length) {
                        tiffPath = args[++i];
                    }
                    break;
                case "--pixel-size":
                    if (i + 1 < args.length) {
                        pixelSize = Double.parseDouble(args[++i]);
                    }
                    break;
                case "--flip-y":
                    flipY = true;
                    break;
                case "--swap-xy":
                    swapXY = true;
                    break;
            }
        }
    }

    public void run() {
        File tempDir = null;
        try {
            // Validate input paths
            if (!new File(sqlitePath).exists()) {
                throw new RuntimeException("SQLite file does not exist: " + sqlitePath);
            }
            if (xmlPath != null && !new File(xmlPath).exists()) {
                throw new RuntimeException("XML file does not exist: " + xmlPath);
            }
            if (tiffPath != null && !new File(tiffPath).exists()) {
                throw new RuntimeException("TIFF file does not exist: " + tiffPath);
            }

            // Store original paths for final output location
            String originalSqlitePath = sqlitePath;

            // Create temp directory and copy all files there
            tempDir = java.nio.file.Files.createTempDirectory("operetta_companion").toFile();
            System.out.println("Using temp directory: " + tempDir.getAbsolutePath());

            // Copy SQLite file
            File tempSqlite = new File(tempDir, new File(sqlitePath).getName());
            java.nio.file.Files.copy(new File(sqlitePath).toPath(), tempSqlite.toPath());
            String tempSqlitePath = tempSqlite.getAbsolutePath();

            // Copy XML file if provided
            String tempXmlPath = null;
            if (xmlPath != null) {
                File tempXml = new File(tempDir, new File(xmlPath).getName());
                java.nio.file.Files.copy(new File(xmlPath).toPath(), tempXml.toPath());
                tempXmlPath = tempXml.getAbsolutePath();
            }

            // Copy TIFF file if provided
            String tempTiffPath = null;
            if (tiffPath != null) {
                File tempTiff = new File(tempDir, new File(tiffPath).getName());
                java.nio.file.Files.copy(new File(tiffPath).toPath(), tempTiff.toPath());
                tempTiffPath = tempTiff.getAbsolutePath();
            }

            // Parse XML for positions and metadata
            if (tempXmlPath != null) {
                System.out.println("Loading field positions from: " + xmlPath);
                fieldPositions = parseFieldPositions(tempXmlPath);
                System.out.println("Loaded " + fieldPositions.size() + " field positions");

                magnification = getObjectiveMagnification(tempXmlPath);
                System.out.println("Objective magnification: " + magnification + "x");

                int[] binning = getCameraBinning(tempXmlPath);
                binningX = binning[0];
                binningY = binning[1];
                if (binningX != 1 || binningY != 1) {
                    System.out.println("Camera binning: " + binningX + "x" + binningY);
                }

                if (flipY || swapXY) {
                    List<String> transforms = new ArrayList<>();
                    if (flipY) transforms.add("flip Y");
                    if (swapXY) transforms.add("swap X/Y");
                    System.out.println("Coordinate transformations: " + String.join(", ", transforms));
                }
            } else {
                System.out.println("Warning: No XML file provided, position metadata will not be included");
            }

            // Get pixel size
            if (pixelSize != null) {
                physicalPixelSizeUm = pixelSize;
                System.out.println("Using pixel size from argument: " + physicalPixelSizeUm + " µm/pixel (assumed to be corrected)");
            } else if (tempTiffPath != null) {
                System.out.println("Extracting pixel size from: " + tiffPath);
                double cameraPixelSizeUm = getPixelSizeFromTiff(tempTiffPath);
                System.out.println("Camera chip pixel size: " + String.format("%.4f", cameraPixelSizeUm) + " µm/pixel");

                double binnedPixelSizeUm = cameraPixelSizeUm * binningX;
                if (binningX != 1 || binningY != 1) {
                    System.out.println("Binned pixel size (" + binningX + "x" + binningY + "): " +
                            String.format("%.4f", binnedPixelSizeUm) + " µm/pixel");
                }

                if (magnification > 0) {
                    physicalPixelSizeUm = binnedPixelSizeUm / magnification;
                    List<String> corrections = new ArrayList<>();
                    if (binningX != 1 || binningY != 1) {
                        corrections.add(binningX + "x binning");
                    }
                    corrections.add(magnification + "x magnification");
                    System.out.println("Physical pixel size (corrected for " + String.join(", ", corrections) + "): " +
                            String.format("%.4f", physicalPixelSizeUm) + " µm/pixel");
                } else {
                    physicalPixelSizeUm = binnedPixelSizeUm;
                    if (binningX != 1 || binningY != 1) {
                        System.out.println("Warning: No magnification available, using binned pixel size only");
                    } else {
                        System.out.println("Warning: No magnification available, using uncorrected camera pixel size");
                    }
                }
            } else {
                System.out.println("Warning: No TIFF file or pixel size provided, physical size metadata will not be included");
            }

            // Read database and generate companion file in temp directory
            System.out.println("Reading database: " + sqlitePath);
            String tempCompanionPath = generateCompanionFile(tempSqlitePath);

            // Copy companion file back to original location
            File originalDir = new File(originalSqlitePath).getParentFile();
            File tempCompanionFile = new File(tempCompanionPath);
            File finalCompanionFile = new File(originalDir, tempCompanionFile.getName()+suffix);
            java.nio.file.Files.copy(tempCompanionFile.toPath(), finalCompanionFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Companion file written to: " + finalCompanionFile.getAbsolutePath());

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            // Clean up temp directory
            if (tempDir != null && tempDir.exists()) {
                for (File file : tempDir.listFiles()) {
                    file.delete();
                }
                tempDir.delete();
            }
        }
    }

    private Map<Integer, Position> parseFieldPositions(String xmlPath) throws Exception {
        Map<Integer, Position> positions = new HashMap<>();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new File(xmlPath));

        // Find Sublayout/Field elements
        NodeList fieldNodes = doc.getElementsByTagNameNS("http://www.perkinelmer.com/PEHH/HarmonyV5", "Field");

        for (int i = 0; i < fieldNodes.getLength(); i++) {
            Element field = (Element) fieldNodes.item(i);
            NodeList xNodes = field.getElementsByTagNameNS("http://www.perkinelmer.com/PEHH/HarmonyV5", "X");
            NodeList yNodes = field.getElementsByTagNameNS("http://www.perkinelmer.com/PEHH/HarmonyV5", "Y");

            if (xNodes.getLength() > 0 && yNodes.getLength() > 0) {
                double xM = Double.parseDouble(xNodes.item(0).getTextContent());
                double yM = Double.parseDouble(yNodes.item(0).getTextContent());
                // Convert to mm
                positions.put(i + 1, new Position(xM * 1000, yM * 1000));
            }
        }

        if (positions.isEmpty()) {
            throw new RuntimeException("Could not find Field positions in XML file");
        }

        return positions;
    }

    private double getObjectiveMagnification(String xmlPath) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new File(xmlPath));

        // Get the nominal objective magnification from InstrumentDescription/Objectives/Objective/Magnification
        double nominalMagnification = 1.0;
        NodeList instrDescNodes = doc.getElementsByTagNameNS("http://www.perkinelmer.com/PEHH/HarmonyV5", "InstrumentDescription");
        if (instrDescNodes.getLength() > 0) {
            Element instrDesc = (Element) instrDescNodes.item(0);
            NodeList objectivesNodes = instrDesc.getElementsByTagNameNS("http://www.perkinelmer.com/PEHH/HarmonyV5", "Objectives");
            if (objectivesNodes.getLength() > 0) {
                Element objectives = (Element) objectivesNodes.item(0);
                NodeList objectiveNodes = objectives.getElementsByTagNameNS("http://www.perkinelmer.com/PEHH/HarmonyV5", "Objective");
                if (objectiveNodes.getLength() > 0) {
                    Element objective = (Element) objectiveNodes.item(0);
                    NodeList magNodes = objective.getElementsByTagNameNS("http://www.perkinelmer.com/PEHH/HarmonyV5", "Magnification");
                    if (magNodes.getLength() > 0) {
                        nominalMagnification = Double.parseDouble(magNodes.item(0).getTextContent());
                    }
                }
            }
        }

        // Get all Magnification elements to find the correction factor
        NodeList allMagNodes = doc.getElementsByTagNameNS("http://www.perkinelmer.com/PEHH/HarmonyV5", "Magnification");
        double correctionFactor = 1.0;

        // Look for additional magnification factors (there should be exactly 2 if there's a correction)
        if (allMagNodes.getLength() > 1) {
            // Find the one that's NOT the objective magnification
            for (int i = 0; i < allMagNodes.getLength(); i++) {
                double mag = Double.parseDouble(allMagNodes.item(i).getTextContent());
                if (Math.abs(mag - nominalMagnification) > 0.01) {
                    // This is likely the correction factor
                    correctionFactor = mag;
                    System.out.println("Found magnification correction factor: " + correctionFactor + "x");
                    break;
                }
            }
        }

        if (nominalMagnification <= 0) {
            throw new RuntimeException("Could not find objective magnification in XML file");
        }

        // Calculate effective magnification: nominal × correction
        double effectiveMagnification = nominalMagnification * correctionFactor;

        if (correctionFactor != 1.0) {
            System.out.println("Nominal objective: " + nominalMagnification + "x, correction: " +
                    correctionFactor + "x, effective: " +
                    String.format("%.4f", effectiveMagnification) + "x");
        }

        return effectiveMagnification;
    }

    private int[] getCameraBinning(String xmlPath) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new File(xmlPath));

        NodeList binningXNodes = doc.getElementsByTagNameNS("http://www.perkinelmer.com/PEHH/HarmonyV5", "BinningX");
        NodeList binningYNodes = doc.getElementsByTagNameNS("http://www.perkinelmer.com/PEHH/HarmonyV5", "BinningY");

        if (binningXNodes.getLength() > 0 && binningYNodes.getLength() > 0) {
            int binX = Integer.parseInt(binningXNodes.item(0).getTextContent());
            int binY = Integer.parseInt(binningYNodes.item(0).getTextContent());

            // Check if all channels have the same binning
            Set<Integer> allBinX = new HashSet<>();
            Set<Integer> allBinY = new HashSet<>();
            for (int i = 0; i < binningXNodes.getLength(); i++) {
                allBinX.add(Integer.parseInt(binningXNodes.item(i).getTextContent()));
                allBinY.add(Integer.parseInt(binningYNodes.item(i).getTextContent()));
            }

            if (allBinX.size() > 1 || allBinY.size() > 1) {
                System.out.println("Warning: Different binning values found across channels. Using first values: " +
                        binX + "x" + binY);
            }

            return new int[]{binX, binY};
        }

        // No binning found, assume 1x1
        return new int[]{1, 1};
    }

    /**
     * Extracts the plate name from the XML file.
     * Looks for the PlateName element in the Harmony V5 namespace.
     *
     * @param xmlPath path to the XML file
     * @return the plate name, or null if not found
     */
    private String getPlateName(String xmlPath) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new File(xmlPath));

            NodeList plateNameNodes = doc.getElementsByTagNameNS(
                    "http://www.perkinelmer.com/PEHH/HarmonyV5", "PlateName");
            if (plateNameNodes.getLength() > 0) {
                String plateName = plateNameNodes.item(0).getTextContent();
                if (plateName != null && !plateName.trim().isEmpty()) {
                    return plateName.trim();
                }
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not extract PlateName from XML: " + e.getMessage());
        }
        return null;
    }

    private double getPixelSizeFromTiff(String tiffPathToUse) throws Exception {
        // tiffPathToUse is already in an isolated temp folder, so Bio-Formats won't group other files
        ImageReader reader = new ImageReader();
        IMetadata omeMetaIdxOmeXml = MetadataTools.createOMEXMLMetadata();
        reader.setMetadataStore(omeMetaIdxOmeXml);
        try {
            reader.setId(tiffPathToUse);

            // Get metadata store
            IMetadata meta = (IMetadata) reader.getMetadataStore();

            // Try to get physical size from metadata
            Length physicalSizeX = meta.getPixelsPhysicalSizeX(0);
            if (physicalSizeX != null) {
                // Convert to micrometers
                double um = physicalSizeX.value(UNITS.MICROMETER).doubleValue();
                if (um > 0) {
                    return um;
                }
            }

            // Fallback: Try to calculate from TIFF resolution tags
            // Bio-Formats should handle this automatically, but if not available,
            // we might need to parse TIFF tags directly
            throw new RuntimeException("Could not extract pixel size from TIFF metadata");

        } finally {
            reader.close();
        }
    }

    private Position calculateAbsolutePosition(int row, int col, int fieldIndex) {
        // Well center position
        double wellX = PLATE_OFFSET_X_MM + (col * WELL_SPACING_MM);
        double wellY = PLATE_OFFSET_Y_MM + (row * WELL_SPACING_MM);

        // Field offset within well
        double fieldOffsetX = 0;
        double fieldOffsetY = 0;
        if (fieldPositions != null && fieldPositions.containsKey(fieldIndex)) {
            Position fieldPos = fieldPositions.get(fieldIndex);
            fieldOffsetX = fieldPos.x;
            fieldOffsetY = fieldPos.y;
        }

        // Apply transformations
        if (flipY) {
            fieldOffsetY = -fieldOffsetY;
        }
        if (swapXY) {
            double temp = fieldOffsetX;
            fieldOffsetX = fieldOffsetY;
            fieldOffsetY = temp;
        }

        // Absolute position
        double absX = wellX + fieldOffsetX;
        double absY = wellY + fieldOffsetY;

        return new Position(absX, absY);
    }

    private String generateCompanionFile(String sqlitePathToUse) throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqlitePathToUse);
        Statement stmt = conn.createStatement();

        // Get plate name - prefer XML PlateName, fallback to SQLite Measurement (UUID)
        String plateName = null;
        if (xmlPath != null) {
            plateName = getPlateName(xmlPath);
            if (plateName != null) {
                System.out.println("Using plate name from XML: " + plateName);
            }
        }
        if (plateName == null) {
            ResultSet rsConfig = stmt.executeQuery("SELECT Value FROM Config WHERE Name='Measurement'");
            plateName = rsConfig.getString(1);
            rsConfig.close();
            System.out.println("Using plate name from SQLite (Measurement ID): " + plateName);
        }

        // Get all images
        ResultSet rs = stmt.executeQuery("SELECT * FROM Image");

        // Collect all data
        List<ImageData> imageDataList = new ArrayList<>();
        Set<Integer> rows = new HashSet<>();
        Set<Integer> cols = new HashSet<>();
        Set<Integer> fields = new HashSet<>();
        Set<Integer> zPlanes = new HashSet<>();
        Set<Integer> timepoints = new HashSet<>();
        Set<Integer> channels = new HashSet<>();

        while (rs.next()) {
            ImageData data = new ImageData();
            data.row = rs.getInt("Row");
            data.col = rs.getInt("Col");
            data.field = rs.getInt("Field");
            data.plane = rs.getInt("Plane");
            data.channel = rs.getInt("Channel");
            data.timepoint = rs.getInt("SlowKin");
            data.url = rs.getString("Url");

            imageDataList.add(data);

            rows.add(data.row);
            cols.add(data.col);
            fields.add(data.field);
            zPlanes.add(data.plane);
            timepoints.add(data.timepoint);
            channels.add(data.channel);
        }
        rs.close();

        int nRows = rows.size();
        int nCols = cols.size();
        int nFields = fields.size();
        int nZ = zPlanes.size();
        int nT = timepoints.size();
        int nC = channels.size();

        System.out.println("Found " + imageDataList.size() + " images in database");
        System.out.println("Plate dimensions: " + nRows + " rows × " + nCols + " cols, " + nFields + " fields/well");
        System.out.println("Image dimensions: Z=" + nZ + ", C=" + nC + ", T=" + nT);

        // Group images by well and field
        System.out.println("Assembling plate structure...");
        Map<String, List<ImageData>> imageGroups = new HashMap<>();
        for (ImageData data : imageDataList) {
            String key = data.row + "|" + data.col + "|" + data.field;
            imageGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(data);
        }

        // Create OME-XML metadata
        ServiceFactory factory = new ServiceFactory();
        OMEXMLService service = factory.getInstance(OMEXMLService.class);
        IMetadata metadata = service.createOMEXMLMetadata();

        // Set plate metadata
        metadata.setPlateID("Plate:0", 0);
        metadata.setPlateName(plateName, 0);
        metadata.setPlateRows(new PositiveInteger(nRows), 0);
        metadata.setPlateColumns(new PositiveInteger(nCols), 0);

        int imageIndex = 0;
        int wellIndex = 0;
        Map<String, Integer> wellIndexMap = new HashMap<>();
        Map<String, Integer> wellSampleCountMap = new HashMap<>(); // Track well sample count per well

        // Sort keys to ensure consistent ordering
        List<String> sortedKeys = new ArrayList<>(imageGroups.keySet());
        Collections.sort(sortedKeys, (a, b) -> {
            // Parse and sort numerically: row, then col, then field
            String[] partsA = a.split("\\|");
            String[] partsB = b.split("\\|");
            int rowA = Integer.parseInt(partsA[0]);
            int rowB = Integer.parseInt(partsB[0]);
            if (rowA != rowB) return Integer.compare(rowA, rowB);
            int colA = Integer.parseInt(partsA[1]);
            int colB = Integer.parseInt(partsB[1]);
            if (colA != colB) return Integer.compare(colA, colB);
            int fieldA = Integer.parseInt(partsA[2]);
            int fieldB = Integer.parseInt(partsB[2]);
            return Integer.compare(fieldA, fieldB);
        });

        for (String key : sortedKeys) {
            String[] parts = key.split("\\|");
            int row = Integer.parseInt(parts[0]);
            int col = Integer.parseInt(parts[1]);
            int field = Integer.parseInt(parts[2]);

            String wellKey = row + "|" + col;
            int currentWellIndex;

            if (!wellIndexMap.containsKey(wellKey)) {
                currentWellIndex = wellIndex++;
                wellIndexMap.put(wellKey, currentWellIndex);

                // Set well metadata
                metadata.setWellID("Well:" + currentWellIndex, 0, currentWellIndex);
                metadata.setWellRow(new NonNegativeInteger(row), 0, currentWellIndex);
                metadata.setWellColumn(new NonNegativeInteger(col), 0, currentWellIndex);
            } else {
                currentWellIndex = wellIndexMap.get(wellKey);
            }

            // Set image metadata
            metadata.setImageID("Image:" + imageIndex, imageIndex);
            metadata.setImageName(key, imageIndex);
            metadata.setPixelsID("Pixels:" + imageIndex, imageIndex);
            metadata.setPixelsDimensionOrder(DimensionOrder.XYCZT, imageIndex);
            metadata.setPixelsType(PixelType.UINT16, imageIndex);
            metadata.setPixelsSizeX(new PositiveInteger(IMG_X), imageIndex);
            metadata.setPixelsSizeY(new PositiveInteger(IMG_Y), imageIndex);
            metadata.setPixelsSizeZ(new PositiveInteger(nZ), imageIndex);
            metadata.setPixelsSizeC(new PositiveInteger(nC), imageIndex);
            metadata.setPixelsSizeT(new PositiveInteger(nT), imageIndex);

            // Set physical pixel size if available
            if (physicalPixelSizeUm != null) {
                Length physSize = new Length(physicalPixelSizeUm, UNITS.MICROMETER);
                metadata.setPixelsPhysicalSizeX(physSize, imageIndex);
                metadata.setPixelsPhysicalSizeY(physSize, imageIndex);
            }

            // Set channels
            for (int c = 0; c < nC; c++) {
                metadata.setChannelID("Channel:" + imageIndex + ":" + c, imageIndex, c);
                metadata.setChannelSamplesPerPixel(new PositiveInteger(1), imageIndex, c);
            }

            // Link well sample - use sequential index for each well for the API,
            // but set WellSampleIndex to global imageIndex for OperettaManager compatibility
            int wellSampleIndex = wellSampleCountMap.getOrDefault(wellKey, 0);
            wellSampleCountMap.put(wellKey, wellSampleIndex + 1);
            metadata.setWellSampleID("WellSample:" + currentWellIndex + ":" + wellSampleIndex, 0, currentWellIndex, wellSampleIndex);
            metadata.setWellSampleIndex(new NonNegativeInteger(imageIndex), 0, currentWellIndex, wellSampleIndex); // Use global imageIndex
            metadata.setWellSampleImageRef("Image:" + imageIndex, 0, currentWellIndex, wellSampleIndex);

            // Set WellSample position (required for OperettaManager.getWells() filtering)
            if (fieldPositions != null) {
                Position absPos = calculateAbsolutePosition(row, col, field);
                Length posX = new Length(absPos.x / 1000.0, UNITS.METER);
                Length posY = new Length(absPos.y / 1000.0, UNITS.METER);
                metadata.setWellSamplePositionX(posX, 0, currentWellIndex, wellSampleIndex);
                metadata.setWellSamplePositionY(posY, 0, currentWellIndex, wellSampleIndex);
            }

            // Add TiffData and Plane elements
            List<ImageData> imageGroup = imageGroups.get(key);
            int tiffDataIndex = 0;
            for (ImageData data : imageGroup) {
                int z = data.plane - 1; // Convert to 0-indexed
                int c = data.channel;
                int t = data.timepoint;

                // Set plane metadata with position if available
                if (fieldPositions != null) {
                    Position absPos = calculateAbsolutePosition(row, col, field);
                    // Convert to meters for OME-XML
                    Length posX = new Length(absPos.x / 1000.0, UNITS.METER);
                    Length posY = new Length(absPos.y / 1000.0, UNITS.METER);
                    metadata.setPlanePositionX(posX, imageIndex, tiffDataIndex);
                    metadata.setPlanePositionY(posY, imageIndex, tiffDataIndex);
                }

                metadata.setPlaneTheC(new NonNegativeInteger(c), imageIndex, tiffDataIndex);
                metadata.setPlaneTheZ(new NonNegativeInteger(z), imageIndex, tiffDataIndex);
                metadata.setPlaneTheT(new NonNegativeInteger(t), imageIndex, tiffDataIndex);

                // Set TiffData
                metadata.setTiffDataFirstC(new NonNegativeInteger(c), imageIndex, tiffDataIndex);
                metadata.setTiffDataFirstZ(new NonNegativeInteger(z), imageIndex, tiffDataIndex);
                metadata.setTiffDataFirstT(new NonNegativeInteger(t), imageIndex, tiffDataIndex);
                metadata.setTiffDataPlaneCount(new NonNegativeInteger(1), imageIndex, tiffDataIndex);

                // Set UUID for file reference
                String uuid = "urn:uuid:" + UUID.randomUUID().toString();
                metadata.setUUIDFileName(data.url, imageIndex, tiffDataIndex);
                metadata.setUUIDValue(uuid, imageIndex, tiffDataIndex);

                tiffDataIndex++;
            }

            imageIndex++;
        }

        conn.close();

        // Write companion file
        File sqliteFile = new File(sqlitePathToUse);
        String parentDir = sqliteFile.getParent();
        String companionFileName = plateName + ".companion.ome";
        String companionPath = parentDir != null ?
                new File(parentDir, companionFileName).getPath() : companionFileName;

        System.out.println("Writing companion file: " + companionPath);
        String omeXml = service.getOMEXML(metadata);

        try (FileWriter writer = new FileWriter(companionPath)) {
            writer.write(omeXml);
        }

        System.out.println("Done! Companion file created: " + companionPath);
        if (physicalPixelSizeUm != null) {
            System.out.println("  - Physical pixel size: " + String.format("%.4f", physicalPixelSizeUm) + " µm/pixel");
        }
        if (fieldPositions != null) {
            System.out.println("  - X/Y positions included for " + fieldPositions.size() + " fields");
        }
        if (flipY || swapXY) {
            System.out.println("  - Coordinate transformations applied");
        }

        return companionPath;
    }

    // Helper classes
    private static class Position {
        double x;
        double y;

        Position(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    private static class ImageData {
        int row;
        int col;
        int field;
        int plane;
        int channel;
        int timepoint;
        String url;
    }
}
