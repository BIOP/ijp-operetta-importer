package ch.epfl.biop.ometiff;
/*
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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import loci.common.DataTools;
import loci.common.Location;
import loci.common.RandomAccessInputStream;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.CoreMetadata;
import loci.formats.CoreMetadataList;
import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.IFormatReader;
import loci.formats.MetadataTools;
import loci.formats.MissingLibraryException;
import loci.formats.Modulo;
import loci.formats.in.MinimalTiffReader;
import loci.formats.in.OMETiffReader;
import loci.formats.ome.OMEPyramidStore;
import loci.formats.ome.OMEXMLMetadata;
import loci.formats.services.OMEXMLService;
import loci.formats.services.OMEXMLServiceImpl;
import loci.formats.tiff.IFD;
import loci.formats.tiff.PhotoInterp;
import loci.formats.tiff.TiffParser;

import ome.xml.model.MapPair;
import ome.xml.model.primitives.NonNegativeInteger;
import ome.xml.model.primitives.PositiveInteger;
import ome.xml.model.primitives.Timestamp;

/**
 * LazyOMETiffReader is a variant of OMETiffReader that defers file validation
 * until planes are actually accessed. This dramatically improves performance
 * when opening datasets with many files, especially on network storage.
 *
 * Key differences from OMETiffReader:
 * - File existence checks are skipped during initFile()
 * - File type validation (isThisType) is deferred to first openBytes() access
 * - Files are assumed valid until proven otherwise
 *
 * Use setTrustMetadata(true) to skip ALL validation (even during openBytes).
 */
public class LazyOMETiffReader extends OMETiffReader {

    // -- Fields --

    /** Set of file paths that have been validated. */
    private Set<String> validatedFiles = new HashSet<>();

    /** Set of file paths known to be invalid/missing. */
    private Set<String> invalidFiles = new HashSet<>();

    /** Whether to skip file validation entirely (trust OME-XML completely). */
    private boolean trustMetadata = true;

    /** Service for OME-XML operations. */
    private OMEXMLService lazyService;

    /** OME-XML metadata. */
    private OMEXMLMetadata lazyMeta;

    /** Path to metadata file for binary-only datasets. */
    private String lazyMetadataFile;

    /** Cached reflection access to OMETiffPlane fields (transient - not serializable). */
    private transient Field planeIdField;
    private transient Field planeReaderField;
    private transient Field planeIfdField;
    private transient Field planeExistsField;
    private transient Field planeCertainField;

    /** Reflection access to parent's info field (transient - not serializable). */
    private transient Field infoField;

    // -- Constructor --

    // Suffix for lazy companion files
    public static final String LAZY_SUFFIX = "companion.ome.lazy";

    public LazyOMETiffReader() {
        super();
        suffixNecessary = true;
        suffixSufficient = true;
        initReflection();
    }

    // -- IFormatReader API methods --

    @Override
    public boolean isThisType(String name, boolean open) {
        // Only handle .companion.ome.lazy files - explicit opt-in for lazy loading
        return checkSuffix(name, LAZY_SUFFIX);
    }

    @Override
    public boolean isThisType(RandomAccessInputStream stream) throws IOException {
        // This reader only handles files identified by .lazy suffix
        return false;
    }

    /**
     * Check if a filename has the companion.ome or companion.ome.lazy suffix.
     */
    private boolean isCompanionFile(String name) {
        return checkSuffix(name, "companion.ome") || checkSuffix(name, LAZY_SUFFIX);
    }

    // -- LazyOMETiffReader API --

    /**
     * Set whether to trust the OME-XML metadata completely, skipping all
     * file validation. When true, files are assumed to exist and be valid.
     * Invalid files will only be discovered when actually reading pixels.
     *
     * @param trust true to skip all validation
     */
    public void setTrustMetadata(boolean trust) {
        this.trustMetadata = trust;
    }

    public boolean getTrustMetadata() {
        return trustMetadata;
    }

    // -- IFormatReader API methods --

    @Override
    public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h)
            throws FormatException, IOException
    {
        FormatTools.checkPlaneParameters(this, no, buf.length, x, y, w, h);

        // Get plane info via reflection since OMETiffPlane is private
        Object plane = getInfoPlane(series, no);
        String planeId = getPlaneId(plane);
        IFormatReader planeReader = getPlaneReader(plane);

        // Validate file on first access if not already validated
        if (planeId != null && !trustMetadata && !validatedFiles.contains(planeId)) {
            validateFile(plane, planeId, planeReader);
        }

        // Check if file is known to be invalid - return fill color
        if (planeId != null && invalidFiles.contains(planeId)) {
            Arrays.fill(buf, getFillColor());
            return buf;
        }

        // Delegate to parent implementation
        return super.openBytes(no, buf, x, y, w, h);
    }

    @Override
    protected void initFile(String id) throws FormatException, IOException {
        System.out.println("lazy.................");
        initFileLazy(id);
    }

    @Override
    public void close(boolean fileOnly) throws IOException {
        super.close(fileOnly);
        if (!fileOnly) {
            validatedFiles.clear();
            invalidFiles.clear();
            lazyMetadataFile = null;
        }
    }

    @Override
    public int getOptimalTileWidth() {
        FormatTools.assertId(currentId, true, 1);
        CoreMetadata currentCore = core.get(series, resolution);
        if (currentCore instanceof LazyOMETiffCoreMetadata) {
            int tw = ((LazyOMETiffCoreMetadata) currentCore).tileWidth;
            return tw > 0 ? tw : getSizeX();
        }
        return getSizeX();
    }

    @Override
    public int getOptimalTileHeight() {
        FormatTools.assertId(currentId, true, 1);
        CoreMetadata currentCore = core.get(series, resolution);
        if (currentCore instanceof LazyOMETiffCoreMetadata) {
            int th = ((LazyOMETiffCoreMetadata) currentCore).tileHeight;
            return th > 0 ? th : getSizeY();
        }
        return getSizeY();
    }

    // -- Internal FormatReader API methods --

    /**
     * Lazy version of initFile that skips file validation.
     * This is a modified copy of OMETiffReader.initFile() with validation removed.
     */
    protected void initFileLazy(String id) throws FormatException, IOException {
        // normalize file name - call grandparent's initFile
        // We need to call SubResolutionFormatReader.initFile, not OMETiffReader.initFile
        initFileBase(normalizeFilename(null, id));
        id = currentId;
        String dir = new File(id).getParent();

        // parse and populate OME-XML metadata
        String fileName = new Location(id).getAbsoluteFile().getAbsolutePath();
        if (!new File(fileName).exists()) {
            fileName = currentId;
        }
        String xml;
        IFD firstIFD = null;

        boolean companion = false;
        if (isCompanionFile(fileName)) {
            xml = DataTools.readFile(fileName);
            companion = true;
        }
        else {
            firstIFD = getFirstIFDLazy(fileName);
            xml = firstIFD.getComment();
        }

        if (lazyService == null) setupServiceLazy();
        try {
            lazyMeta = lazyService.createOMEXMLMetadata(xml);
            if (companion) {
                String firstTIFF = lazyMeta.getUUIDFileName(0, 0);
                firstIFD = getFirstIFDLazy(new Location(dir, firstTIFF).getAbsolutePath());
                lazyMetadataFile = fileName;
            }
        }
        catch (ServiceException se) {
            throw new FormatException(se);
        }

        String metadataPath = null;
        if (lazyMetadataFile == null) {
            try {
                metadataPath = lazyMeta.getBinaryOnlyMetadataFile();
            }
            catch (NullPointerException e) {
            }
        }

        if (metadataPath != null) {
            // this is a binary-only file
            // overwrite XML with what is in the companion OME-XML file
            Location path = new Location(dir, metadataPath);
            if (path.exists()) {
                lazyMetadataFile = Paths.get(path.toString()).normalize().toString();
                xml = readMetadataFileLazy();

                try {
                    lazyMeta = lazyService.createOMEXMLMetadata(xml);
                    dir = path.getParentFile().getAbsolutePath();
                    currentId = lazyMetadataFile;
                }
                catch (ServiceException se) {
                    throw new FormatException(se);
                }
                catch (NullPointerException e) {
                    lazyMetadataFile = null;
                    metadataPath = null;
                }
            }
        }

        // Set the OME-XML metadata as the metadata store so it can be retrieved via getMetadataStore()
        metadataStore = lazyMeta;

        boolean hasSPW = lazyMeta.getPlateCount() > 0;

        for (int i=0; i<lazyMeta.getImageCount(); i++) {
            int sizeC = lazyMeta.getPixelsSizeC(i).getValue();
            lazyService.removeChannels(lazyMeta, i, sizeC);
        }

        // if flattened resolutions are requested, remove resolution annotations
        if (hasFlattenedResolutions()) {
            try {
                for (int i=0; i<lazyMeta.getMapAnnotationCount(); i++) {
                    if (lazyMeta.getMapAnnotationNamespace(i).equals(OMEPyramidStore.NAMESPACE)) {
                        lazyMeta.setMapAnnotationValue(new ArrayList<MapPair>(), i);
                    }
                }
            }
            catch (NullPointerException e) {
                LOGGER.trace("Could not remove resolution annotations", e);
            }
        }

        Hashtable originalMetadata = lazyService.getOriginalMetadata(lazyMeta);
        if (originalMetadata != null) metadata = originalMetadata;

        LOGGER.trace(xml);

        if (lazyMeta.getRoot() == null) {
            throw new FormatException("Could not parse OME-XML from TIFF comment");
        }

        String[] acquiredDates = new String[lazyMeta.getImageCount()];
        for (int i=0; i<acquiredDates.length; i++) {
            Timestamp acquisitionDate = lazyMeta.getImageAcquisitionDate(i);
            if (acquisitionDate != null) {
                acquiredDates[i] = acquisitionDate.getValue();
            }
        }

        String currentUUID = lazyMeta.getUUID();

        lazyService.convertMetadata(lazyMeta, metadataStore);

        // determine series count from Image and Pixels elements
        int seriesCount = lazyMeta.getImageCount();
        core.clear();
        for (int i=0; i<seriesCount; i++) {
            core.add(new LazyOMETiffCoreMetadata());
        }
        // Create properly typed array via reflection since OMETiffPlane is private
        setInfo(createInfoArray(seriesCount));

        // compile list of file/UUID mappings
        Hashtable<String, String> files = new Hashtable<>();
        for (int i=0; i<seriesCount; i++) {
            int tiffDataCount = lazyMeta.getTiffDataCount(i);
            for (int td=0; td<tiffDataCount; td++) {
                String uuid = null;
                try {
                    uuid = lazyMeta.getUUIDValue(i, td);
                }
                catch (NullPointerException e) { }
                String filename = null;
                if (uuid == null) {
                    uuid = "";
                    filename = id;
                }
                else {
                    filename = lazyMeta.getUUIDFileName(i, td);
                    // LAZY: Skip existence check here - assume file exists
                    // if (!new Location(dir, filename).exists()) filename = null;
                    if (filename == null) {
                        if (uuid.equals(currentUUID) || currentUUID == null) {
                            filename = id;
                        } else {
                            filename = "";
                        }
                    }
                    else {
                        filename = normalizeFilename(dir, filename);
                    }
                    if (filename.equals(id) && currentUUID == null) {
                        currentUUID = uuid;
                    }
                }

                // LAZY: Don't fail on missing files during init, defer to openBytes
                if (filename.isEmpty()) {
                    String msg = "Missing file " + lazyMeta.getUUIDFileName(i, td) +
                            " associated with UUID " + uuid + ".";
                    if (failOnMissingTIFF()) {
                        // Still throw if explicitly configured to fail
                        throw new FormatException(msg);
                    } else {
                        LOGGER.debug("Lazy mode: deferring validation of {}", lazyMeta.getUUIDFileName(i, td));
                        filename = normalizeFilename(dir, lazyMeta.getUUIDFileName(i, td));
                    }
                }

                String existing = files.get(uuid);
                if (existing == null) {
                    files.put(uuid, filename);
                } else if (!existing.equals(filename)) {
                    throw new FormatException("Inconsistent filenames for UUID " + uuid);
                }
            }
        }

        // build list of used files
        Enumeration en = files.keys();
        int numUUIDs = files.size();
        HashSet fileSet = new HashSet();
        for (int i=0; i<numUUIDs; i++) {
            String uuid = (String) en.nextElement();
            String filename = files.get(uuid);
            fileSet.add(filename);
        }
        used = new String[fileSet.size()];
        Iterator iter = fileSet.iterator();
        for (int i=0; i<used.length; i++) used[i] = (String) iter.next();

        LOGGER.info("Lazy mode: {} files referenced, skipping validation", used.length);

        // process TiffData elements
        Hashtable<String, IFormatReader> readers = new Hashtable<>();
        boolean adjustedSamples = false;

        for (int i=0; i<seriesCount; i++) {
            int s = i;
            LOGGER.debug("Image[{}] - processing metadata only", i);

            String order = lazyMeta.getPixelsDimensionOrder(i).toString();

            PositiveInteger samplesPerPixel = null;
            if (lazyMeta.getChannelCount(i) > 0) {
                samplesPerPixel = lazyMeta.getChannelSamplesPerPixel(i, 0);
            }
            int samples = samplesPerPixel == null ? -1 : samplesPerPixel.getValue();
            int tiffSamples = firstIFD.getSamplesPerPixel();

            if (adjustedSamples || (samples != tiffSamples && (i == 0 || samples < 0))) {
                LOGGER.warn("SamplesPerPixel mismatch: OME={}, TIFF={}", samples, tiffSamples);
                samples = tiffSamples;
                adjustedSamples = true;
            }
            else {
                adjustedSamples = false;
            }

            if (adjustedSamples && lazyMeta.getChannelCount(i) <= 1) {
                adjustedSamples = false;
            }

            int effSizeC = lazyMeta.getPixelsSizeC(i).getValue();
            if (!adjustedSamples) {
                effSizeC /= samples;
            }
            if (effSizeC == 0) effSizeC = 1;
            if (effSizeC * samples != lazyMeta.getPixelsSizeC(i).getValue()) {
                effSizeC = lazyMeta.getPixelsSizeC(i).getValue();
            }
            int sizeT = lazyMeta.getPixelsSizeT(i).getValue();
            int sizeZ = lazyMeta.getPixelsSizeZ(i).getValue();
            int num = effSizeC * sizeT * sizeZ;

            // Create plane info array using reflection to create OMETiffPlane instances
            Object[] planes = createPlaneArray(num);
            for (int no=0; no<num; no++) {
                initPlane(planes[no]);
            }

            int tiffDataCount = lazyMeta.getTiffDataCount(i);
            Boolean zOneIndexed = null;
            Boolean cOneIndexed = null;
            Boolean tOneIndexed = null;

            // pre-scan TiffData indices
            for (int td=0; td<tiffDataCount; td++) {
                NonNegativeInteger firstC = lazyMeta.getTiffDataFirstC(i, td);
                NonNegativeInteger firstT = lazyMeta.getTiffDataFirstT(i, td);
                NonNegativeInteger firstZ = lazyMeta.getTiffDataFirstZ(i, td);
                int c = firstC == null ? 0 : firstC.getValue();
                int t = firstT == null ? 0 : firstT.getValue();
                int z = firstZ == null ? 0 : firstZ.getValue();

                if (c >= effSizeC && cOneIndexed == null) cOneIndexed = true;
                else if (c == 0) cOneIndexed = false;
                if (z >= sizeZ && zOneIndexed == null) zOneIndexed = true;
                else if (z == 0) zOneIndexed = false;
                if (t >= sizeT && tOneIndexed == null) tOneIndexed = true;
                else if (t == 0) tOneIndexed = false;

                if (c == 0 && z == 0 && t == 0) break;
            }

            for (int td=0; td<tiffDataCount; td++) {
                String filename = null;
                String uuid = null;
                try {
                    filename = lazyMeta.getUUIDFileName(i, td);
                } catch (NullPointerException e) { }
                try {
                    uuid = lazyMeta.getUUIDValue(i, td);
                } catch (NullPointerException e) { }

                NonNegativeInteger tdIFD = lazyMeta.getTiffDataIFD(i, td);
                int ifdIndex = tdIFD == null ? 0 : tdIFD.getValue();
                NonNegativeInteger numPlanes = lazyMeta.getTiffDataPlaneCount(i, td);
                NonNegativeInteger firstC = lazyMeta.getTiffDataFirstC(i, td);
                NonNegativeInteger firstT = lazyMeta.getTiffDataFirstT(i, td);
                NonNegativeInteger firstZ = lazyMeta.getTiffDataFirstZ(i, td);
                int c = firstC == null ? 0 : firstC.getValue();
                int t = firstT == null ? 0 : firstT.getValue();
                int z = firstZ == null ? 0 : firstZ.getValue();

                if (cOneIndexed != null && cOneIndexed) c--;
                if (zOneIndexed != null && zOneIndexed) z--;
                if (tOneIndexed != null && tOneIndexed) t--;

                if (z >= sizeZ || c >= effSizeC || t >= sizeT) {
                    LOGGER.warn("Found invalid TiffData: Z={}, C={}, T={}", z, c, t);
                    break;
                }

                int index = FormatTools.getIndex(order, sizeZ, effSizeC, sizeT, num, z, c, t);
                int count = numPlanes == null ? 1 : numPlanes.getValue();
                if (count == 0) {
                    core.set(s, 0, null);
                    break;
                }

                // get reader object for this filename
                if (filename == null) {
                    if (uuid == null) filename = id;
                    else filename = files.get(uuid);
                }
                else {
                    filename = normalizeFilename(dir, filename);
                }
                IFormatReader r = readers.get(filename);
                if (r == null) {
                    r = new MinimalTiffReader();
                    readers.put(filename, r);
                }

                // LAZY: Skip file existence check - assume exists
                // populate plane index -> IFD mapping
                for (int q=0; q<count; q++) {
                    int no = index + q;
                    setPlaneReader(planes[no], r);
                    setPlaneId(planes[no], filename);
                    setPlaneIfd(planes[no], ifdIndex + q);
                    setPlaneCertain(planes[no], true);
                    setPlaneExists(planes[no], true); // Assume exists until proven otherwise
                }
                if (numPlanes == null) {
                    for (int no=index+1; no<num; no++) {
                        if (getPlaneCertain(planes[no])) break;
                        setPlaneReader(planes[no], r);
                        setPlaneId(planes[no], filename);
                        setPlaneIfd(planes[no], getPlaneIfd(planes[no-1]) + 1);
                        setPlaneExists(planes[no], true);
                    }
                }
            }

            if (core.get(s, 0) == null) continue;

            // LAZY: Skip the entire validation loop (lines 1061-1092 in original)
            // This is the main performance gain - we don't validate 220K files

            setInfoSeries(s, planes);

            // Populate core metadata from OME-XML (trust metadata, minimal file access)
            LazyOMETiffCoreMetadata m = (LazyOMETiffCoreMetadata) core.get(s, 0);
            try {
                m.sizeX = lazyMeta.getPixelsSizeX(i).getValue();
                m.sizeY = lazyMeta.getPixelsSizeY(i).getValue();
                m.sizeZ = lazyMeta.getPixelsSizeZ(i).getValue();
                m.sizeC = lazyMeta.getPixelsSizeC(i).getValue();
                m.sizeT = lazyMeta.getPixelsSizeT(i).getValue();
                m.pixelType = FormatTools.pixelTypeFromString(lazyMeta.getPixelsType(i).toString());
                m.imageCount = num;
                m.dimensionOrder = lazyMeta.getPixelsDimensionOrder(i).toString();
                m.orderCertain = true;
                PhotoInterp photo = firstIFD.getPhotometricInterpretation();
                m.rgb = samples > 1 || photo == PhotoInterp.RGB;
                if ((samples != m.sizeC && (samples % m.sizeC) != 0 &&
                        (m.sizeC % samples) != 0) || m.sizeC == 1 || adjustedSamples)
                {
                    m.sizeC *= samples;
                }

                if (m.sizeZ * m.sizeT * m.sizeC > m.imageCount && !m.rgb) {
                    if (m.sizeZ == m.imageCount) {
                        m.sizeT = 1;
                        m.sizeC = 1;
                    }
                    else if (m.sizeT == m.imageCount) {
                        m.sizeZ = 1;
                        m.sizeC = 1;
                    }
                    else if (m.sizeC == m.imageCount) {
                        m.sizeT = 1;
                        m.sizeZ = 1;
                    }
                }

                m.littleEndian = firstIFD.isLittleEndian();
                m.interleaved = false;
                m.indexed = photo == PhotoInterp.RGB_PALETTE &&
                        firstIFD.getIFDValue(IFD.COLOR_MAP) != null;
                if (m.indexed) {
                    m.rgb = false;
                }
                m.falseColor = true;
                m.metadataComplete = true;
                if (lazyMeta.getPixelsSignificantBits(i) != null) {
                    m.bitsPerPixel = lazyMeta.getPixelsSignificantBits(i).getValue();
                }

                // Get tile dimensions from firstIFD (assume uniform)
                m.tileWidth = (int) firstIFD.getTileWidth();
                m.tileHeight = (int) firstIFD.getTileLength();
            }
            catch (NullPointerException exc) {
                throw new FormatException("Incomplete Pixels metadata", exc);
            }
        }

        // Remove null CoreMetadata entries
        CoreMetadataList seriesList = new CoreMetadataList();
        final List<Object[]> planeInfo = new ArrayList<>();
        int currentSeriesIdx = 0;
        Object[][] currentInfo = getInfo();
        for (int i=0; i<core.size(); i++) {
            if (core.get(i, 0) == null) continue;
            seriesList.add();
            planeInfo.add(currentInfo[i]);
            for (int j=0; j<core.size(i); j++) {
                seriesList.add(currentSeriesIdx, core.get(i, j));
            }
            currentSeriesIdx++;
        }
        core = seriesList;
        // Rebuild info array with proper type
        Object[][] newInfo = createInfoArray(planeInfo.size());
        for (int i = 0; i < planeInfo.size(); i++) {
            newInfo[i] = planeInfo.get(i);
        }
        setInfo(newInfo);

        if (getImageCount() == 1) {
            LazyOMETiffCoreMetadata ms0 = (LazyOMETiffCoreMetadata) core.get(0, 0);
            ms0.sizeZ = 1;
            if (!ms0.rgb) {
                ms0.sizeC = 1;
            }
            ms0.sizeT = 1;
        }

        for (int i=0; i<core.size(); i++) {
            LazyOMETiffCoreMetadata m = (LazyOMETiffCoreMetadata) core.get(i, 0);
            Modulo z = lazyService.getModuloAlongZ(lazyMeta, i);
            if (z != null) m.moduloZ = z;
            Modulo c = lazyService.getModuloAlongC(lazyMeta, i);
            if (c != null) m.moduloC = c;
            Modulo t = lazyService.getModuloAlongT(lazyMeta, i);
            if (t != null) m.moduloT = t;
        }

        MetadataTools.populatePixels(metadataStore, this, false, false);
        for (int i=0; i<lazyMeta.getImageCount(); i++) {
            for (int p=0; p<lazyMeta.getPlaneCount(i); p++) {
                NonNegativeInteger zVal = lazyMeta.getPlaneTheZ(i, p);
                NonNegativeInteger cVal = lazyMeta.getPlaneTheC(i, p);
                NonNegativeInteger tVal = lazyMeta.getPlaneTheT(i, p);

                if (zVal == null) {
                    metadataStore.setPlaneTheZ(new NonNegativeInteger(0), i, p);
                }
                if (cVal == null) {
                    metadataStore.setPlaneTheC(new NonNegativeInteger(0), i, p);
                }
                if (tVal == null) {
                    metadataStore.setPlaneTheT(new NonNegativeInteger(0), i, p);
                }
            }
        }
        for (int i=0; i<acquiredDates.length; i++) {
            if (acquiredDates[i] != null) {
                metadataStore.setImageAcquisitionDate(new Timestamp(acquiredDates[i]), i);
            }
        }

        LOGGER.info("Lazy initialization complete - {} series, {} total files", core.size(), used.length);
    }

    /**
     * Call the base initFile (from FormatReader), skipping OMETiffReader's version.
     */
    private void initFileBase(String id) throws FormatException, IOException {
        // This mimics what SubResolutionFormatReader.initFile does
        close();
        currentId = id;
        metadata = new Hashtable<>();
        core = new CoreMetadataList();
        // Don't set metadataStore here - will be set to lazyMeta after OME-XML parsing
    }

    // -- Reflection helpers --

    /** Ensure reflection fields are initialized (needed after deserialization). */
    private void ensureReflectionInitialized() {
        if (infoField == null) {
            initReflection();
        }
    }

    private void initReflection() {
        try {
            Class<?> planeClass = Class.forName("loci.formats.in.OMETiffReader$OMETiffPlane");
            planeIdField = planeClass.getDeclaredField("id");
            planeIdField.setAccessible(true);
            planeReaderField = planeClass.getDeclaredField("reader");
            planeReaderField.setAccessible(true);
            planeIfdField = planeClass.getDeclaredField("ifd");
            planeIfdField.setAccessible(true);
            planeExistsField = planeClass.getDeclaredField("exists");
            planeExistsField.setAccessible(true);
            planeCertainField = planeClass.getDeclaredField("certain");
            planeCertainField.setAccessible(true);

            // Access to parent's info field
            infoField = OMETiffReader.class.getDeclaredField("info");
            infoField.setAccessible(true);
        } catch (Exception e) {
            LOGGER.error("Failed to initialize reflection for lazy validation", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Object[][] createInfoArray(int seriesCount) {
        try {
            Class<?> planeClass = Class.forName("loci.formats.in.OMETiffReader$OMETiffPlane");
            Class<?> planeArrayClass = java.lang.reflect.Array.newInstance(planeClass, 0).getClass();
            return (Object[][]) java.lang.reflect.Array.newInstance(planeArrayClass, seriesCount);
        } catch (Exception e) {
            LOGGER.error("Failed to create info array", e);
            return null;
        }
    }

    private void setInfo(Object[][] infoArray) {
        ensureReflectionInitialized();
        try {
            infoField.set(this, infoArray);
        } catch (Exception e) {
            LOGGER.error("Failed to set info field", e);
        }
    }

    private Object[][] getInfo() {
        ensureReflectionInitialized();
        try {
            return (Object[][]) infoField.get(this);
        } catch (Exception e) {
            LOGGER.error("Failed to get info field", e);
            return null;
        }
    }

    private void setInfoSeries(int s, Object[] planes) {
        ensureReflectionInitialized();
        try {
            Object[][] currentInfo = (Object[][]) infoField.get(this);
            currentInfo[s] = planes;
        } catch (Exception e) {
            LOGGER.error("Failed to set info series", e);
        }
    }

    private Object[] getInfoSeries(int s) {
        ensureReflectionInitialized();
        try {
            Object[][] currentInfo = (Object[][]) infoField.get(this);
            return currentInfo[s];
        } catch (Exception e) {
            LOGGER.error("Failed to get info series", e);
            return null;
        }
    }

    private Object getInfoPlane(int s, int no) {
        ensureReflectionInitialized();
        try {
            Object[][] currentInfo = (Object[][]) infoField.get(this);
            return currentInfo[s][no];
        } catch (Exception e) {
            LOGGER.error("Failed to get info plane", e);
            return null;
        }
    }

    private Object[] createPlaneArray(int size) {
        try {
            Class<?> planeClass = Class.forName("loci.formats.in.OMETiffReader$OMETiffPlane");
            Object[] array = (Object[]) java.lang.reflect.Array.newInstance(planeClass, size);
            java.lang.reflect.Constructor<?> ctor = planeClass.getDeclaredConstructor(OMETiffReader.class);
            ctor.setAccessible(true);
            for (int i = 0; i < size; i++) {
                array[i] = ctor.newInstance(this);
            }
            return array;
        } catch (Exception e) {
            LOGGER.error("Failed to create plane array", e);
            return new Object[size];
        }
    }

    private void initPlane(Object plane) {
        // Planes are initialized with defaults, just ensure ifd is set
        setPlaneIfd(plane, -1);
    }

    private String getPlaneId(Object plane) {
        ensureReflectionInitialized();
        try { return (String) planeIdField.get(plane); }
        catch (Exception e) { return null; }
    }

    private void setPlaneId(Object plane, String id) {
        ensureReflectionInitialized();
        try { planeIdField.set(plane, id); }
        catch (Exception e) { }
    }

    private IFormatReader getPlaneReader(Object plane) {
        ensureReflectionInitialized();
        try { return (IFormatReader) planeReaderField.get(plane); }
        catch (Exception e) { return null; }
    }

    private void setPlaneReader(Object plane, IFormatReader reader) {
        ensureReflectionInitialized();
        try { planeReaderField.set(plane, reader); }
        catch (Exception e) { }
    }

    private int getPlaneIfd(Object plane) {
        ensureReflectionInitialized();
        try { return planeIfdField.getInt(plane); }
        catch (Exception e) { return -1; }
    }

    private void setPlaneIfd(Object plane, int ifd) {
        ensureReflectionInitialized();
        try { planeIfdField.setInt(plane, ifd); }
        catch (Exception e) { }
    }

    private boolean getPlaneExists(Object plane) {
        ensureReflectionInitialized();
        try { return planeExistsField.getBoolean(plane); }
        catch (Exception e) { return true; }
    }

    private void setPlaneExists(Object plane, boolean exists) {
        ensureReflectionInitialized();
        try { planeExistsField.setBoolean(plane, exists); }
        catch (Exception e) { }
    }

    private boolean getPlaneCertain(Object plane) {
        ensureReflectionInitialized();
        try { return planeCertainField.getBoolean(plane); }
        catch (Exception e) { return false; }
    }

    private void setPlaneCertain(Object plane, boolean certain) {
        ensureReflectionInitialized();
        try { planeCertainField.setBoolean(plane, certain); }
        catch (Exception e) { }
    }

    // -- Helper methods --

    private String normalizeFilename(String dir, String name) {
        Location file = new Location(dir, name);
        // LAZY: Don't check existence, just return the path
        return file.getAbsolutePath();
    }

    private void setupServiceLazy() throws FormatException {
        try {
            ServiceFactory factory = new ServiceFactory();
            lazyService = factory.getInstance(OMEXMLService.class);
        }
        catch (DependencyException de) {
            throw new MissingLibraryException(OMEXMLServiceImpl.NO_OME_XML_MSG, de);
        }
    }

    private String readMetadataFileLazy() throws IOException {
        LOGGER.debug("Reading metadata from {}", lazyMetadataFile);
        if (checkSuffix(lazyMetadataFile, "ome.tiff") ||
                checkSuffix(lazyMetadataFile, "ome.tif") ||
                checkSuffix(lazyMetadataFile, "ome.tf2") ||
                checkSuffix(lazyMetadataFile, "ome.tf8") ||
                checkSuffix(lazyMetadataFile, "ome.btf")) {
            try (RandomAccessInputStream in = new RandomAccessInputStream(lazyMetadataFile)) {
                TiffParser parser = new TiffParser(in);
                return parser.getComment();
            }
        }
        return DataTools.readFile(lazyMetadataFile);
    }

    private static IFD getFirstIFDLazy(String fname) throws IOException {
        try (RandomAccessInputStream ras = new RandomAccessInputStream(fname, 16)) {
            TiffParser tp = new TiffParser(ras);
            return tp.getFirstIFD();
        }
    }

    /**
     * Validate a file on first access.
     */
    private void validateFile(Object plane, String filePath, IFormatReader reader) {
        if (filePath == null || validatedFiles.contains(filePath) || invalidFiles.contains(filePath)) {
            return;
        }

        try {
            // Quick existence check
            if (!new Location(filePath).exists()) {
                LOGGER.warn("File does not exist (lazy check): {}", filePath);
                invalidFiles.add(filePath);
                setPlaneExists(plane, false);
                return;
            }

            // Validate it's a TIFF
            if (reader != null) {
                try (RandomAccessInputStream test = new RandomAccessInputStream(filePath, 16)) {
                    if (!reader.isThisType(test)) {
                        LOGGER.warn("Invalid TIFF file (lazy check): {}", filePath);
                        invalidFiles.add(filePath);
                        setPlaneExists(plane, false);
                        return;
                    }
                }
            }

            validatedFiles.add(filePath);
            LOGGER.debug("Validated file (lazy): {}", filePath);

        } catch (IOException e) {
            LOGGER.warn("Error validating file: {} - {}", filePath, e.getMessage());
            invalidFiles.add(filePath);
            setPlaneExists(plane, false);
        }
    }

    // -- Debug methods --

    /**
     * Get debug info about a specific plane (for diagnostics).
     * @param seriesIndex the series index
     * @param planeIndex the plane index within the series
     * @return String with file path, IFD index, and exists flag
     */
    public String getPlaneDebugInfo(int seriesIndex, int planeIndex) {
        Object plane = getInfoPlane(seriesIndex, planeIndex);
        if (plane == null) {
            return "Plane is null";
        }
        String id = getPlaneId(plane);
        int ifd = getPlaneIfd(plane);
        boolean exists = getPlaneExists(plane);
        boolean certain = getPlaneCertain(plane);
        IFormatReader reader = getPlaneReader(plane);
        return String.format("File: %s, IFD: %d, exists: %b, certain: %b, reader: %s",
                id, ifd, exists, certain, reader != null ? reader.getClass().getSimpleName() : "null");
    }

    // -- Inner classes --

    private class LazyOMETiffCoreMetadata extends CoreMetadata {
        int tileWidth;
        int tileHeight;

        LazyOMETiffCoreMetadata() {
            super();
        }
    }
}
