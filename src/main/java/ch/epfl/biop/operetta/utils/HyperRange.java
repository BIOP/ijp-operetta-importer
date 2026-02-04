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
package ch.epfl.biop.operetta.utils;

import ij.ImagePlus;
import ij.ImageStack;
import loci.formats.meta.IMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Class to handle hyperstacks, and store valid ranges as well as reading ranges from metadata
 */
public class HyperRange {

    private static final Logger logger = LoggerFactory.getLogger(HyperRange.class);

    private List<Integer> range_c;
    private List<Integer> range_z;
    private List<Integer> range_t;
    private ImagePlus imp;

    // Lookup map from filename to CZT indices (built from metadata)
    private Map<String, int[]> filenameToCZT = null;

    /**
     * Constructor used internally
     * @param range_c List of valid channels
     * @param range_z List of valid Z slices
     * @param range_t List of valid time points
     */
    HyperRange(List<Integer> range_c, List<Integer> range_z, List<Integer> range_t) {

        this.range_c = range_c;
        this.range_z = range_z;
        this.range_t = range_t;

        updateImagePlusPositions();

    }

    /**
     * Parses a string to get a list of valid channels
     * @param s the string to parse e.g. "1,3,5-7" to [1,3,5,6,7]
     * @return a list of channels
     * @throws NumberFormatException if the string cannot be parsed
     */
    public static List<Integer> parseString(String s) throws NumberFormatException {
        // first split by commas
        List<Integer> range = new ArrayList<>();

        String[] sr = s.split(",");
        Arrays.stream(sr).forEach(r -> {
                    String[] sr2 = r.split(":");
                    if (sr2.length == 2) {
                        List<Integer> subrange = IntStream.rangeClosed(Integer.parseInt(sr2[0].trim()), Integer.parseInt(sr2[1].trim()))
                                .boxed().collect(Collectors.toList());
                        range.addAll(subrange);
                    } else {
                        if (!sr2[0].isEmpty())
                            range.add(Integer.valueOf(sr2[0].trim()));
                    }
                }
        );
        return range;
    }

    /**
     * Needed to make sure that we can get the 1D position of the image we want.
     * For that we cheat and use an imageplus to access its getStackIndex method
     */
    private void updateImagePlusPositions() {
        ImageStack s = ImageStack.create(1, 1, getTotalPlanes(), 8);
        this.imp = new ImagePlus("", s);
        this.imp.setDimensions(range_c.size(), range_z.size(), range_t.size());
    }

    /**
     * In case the range is modified, we need to update not just the range but also the imageplus for the getStackIndex method
     * @param new_range the new range as a string
     */
    public void updateCRange(String new_range) {
        this.range_c = parseString(new_range);
        updateImagePlusPositions();

    }

    /**
     * In case the range is modified, we need to update not just the range but also the imageplus for the getStackIndex method
     * @param new_range the new range as a string
     */
    public void updateZRange(String new_range) {
        this.range_z = parseString(new_range);
        updateImagePlusPositions();

    }
/**
     * In case the range is modified, we need to update not just the range but also the imageplus for the getStackIndex method
 * @param new_range the new range as a string
     */
    public void updateTRange(String new_range) {
        this.range_t = parseString(new_range);
        updateImagePlusPositions();

    }

    /**
     * Get the total number of planes we need
     * @return the total number of planes
     */
    public int getTotalPlanes() {
        return this.range_c.size() * this.range_t.size() * this.range_z.size();
    }

    /**
     * Set a lookup map from filename to CZT indices (built from metadata).
     * @param map Map from filename to int[]{c, z, t} (1-based indices)
     */
    public void setFilenameToCZTMap(Map<String, int[]> map) {
        this.filenameToCZT = map;
    }

    /**
     * Look up CZT from the filename map. Tries exact match first, then matches by filename only.
     */
    private int[] lookupCZT(String s) {
        if (filenameToCZT == null) return null;

        // Try exact match first
        if (filenameToCZT.containsKey(s)) {
            return filenameToCZT.get(s);
        }

        // Try matching just the filename portion
        String sFilename = new java.io.File(s).getName();
        for (Map.Entry<String, int[]> entry : filenameToCZT.entrySet()) {
            String entryFilename = new java.io.File(entry.getKey()).getName();
            if (sFilename.equals(entryFilename)) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * Check if the given image name is included in the range
     * @param s the image name
     * @return true if the image name is included in the range
     */
    public boolean includes(String s) {
        int[] czt = lookupCZT(s);
        if (czt != null) {
            return range_c.contains(czt[0]) && range_z.contains(czt[1]) && range_t.contains(czt[2]);
        }
        logger.warn("Could not find CZT for file: {}", s);
        return false;
    }

    /**
     * Get the indexes matching the given image name as a map of "C", "Z", "T" and "I"
     * @param s the image name
     * @return "C", "Z", "T" and "I" indexes
     */
    public Map<String, Integer> getIndexes(String s) {
        Map<String, Integer> indexes = new HashMap<>();

        int[] czt = lookupCZT(s);
        if (czt != null) {
            int c = czt[0];
            int z = czt[1];
            int t = czt[2];

            indexes.put("C", c);
            indexes.put("Z", z);
            indexes.put("T", t);

            int idx = imp.getStackIndex(range_c.indexOf(c) + 1, range_z.indexOf(z) + 1, range_t.indexOf(t) + 1);
            indexes.put("I", idx);
        } else {
            logger.warn("Could not find CZT for file: {}", s);
        }

        return indexes;
    }

    /**
     * Get dimensions as a CZT range in the form of an int array
     * @return int array of size 3 with C,Z,T dimensions
     */
    public int[] getCZTDimensions() {
        return new int[]{this.range_c.size(), this.range_z.size(), this.range_t.size()};

    }

    /**
     * Check if the range is valid for the metadata provided
     * @param metadata OME metadata to test
     * @return a HyperRange with valid ranges
     */
    public HyperRange confirmRange(IMetadata metadata) {
        int cs = metadata.getPixelsSizeC(0).getValue();
        int zs = metadata.getPixelsSizeZ(0).getValue();
        int ts = metadata.getPixelsSizeT(0).getValue();

        this.range_c = range_c.stream().filter(c -> {
            boolean inside = (c >= 1 && c <= cs);
            if (!inside) logger.info("Removed channel {} because it is not in range of data 1-{}.", c, cs);
            return inside;
        }).collect(Collectors.toList());

        this.range_z = range_z.stream().filter(z -> {
            boolean inside = (z >= 1 && z <= zs);
            if (!inside) logger.info("Removed slice {} because it is not in range of data 1-{}.", z, zs);
            return inside;
        }).collect(Collectors.toList());

        this.range_t = range_t.stream().filter(t -> {
            boolean inside = (t >= 1 && t <= ts);
            if (!inside) logger.info("Removed timepoint {} because it is not in range of data 1-{}.", t, ts);
            return inside;
        }).collect(Collectors.toList());


        return this;
    }

    /**
     * Get the range of channels
     * @return List of channels selected
     */
    public List<Integer> getRangeC() {
        return this.range_c;
    }

    /**
     * Set the range of channels
     * @param range_c List of channels selected
     */
    public void setRangeC(List<Integer> range_c) {
        this.range_c = range_c;
        updateImagePlusPositions();

    }
/**
     * Get the range of slices
     * @return List of slices selected
     */
    public List<Integer> getRangeZ() {
        return this.range_z;
    }
/**
     * Set the range of slices
     * @param range_z List of slices selected
     */
    public void setRangeZ(List<Integer> range_z) {
        this.range_z = range_z;
        updateImagePlusPositions();

    }
/**
     * Get the range of timepoints
     * @return List of timepoints selected
     */
    public List<Integer> getRangeT() {
        return this.range_t;
    }
/**
     * Set the range of timepoints
     * @param range_t List of timepoints selected
     */
    public void setRangeT(List<Integer> range_t) {
        this.range_t = range_t;
        updateImagePlusPositions();

    }

    @Override
    public String toString() {
        return String.format("Range :\n\t\tC: %s\n\t\tZ: %s\n\t\tT: %s", range_c.toString(), range_z.toString(), range_t.toString());
    }

    /**
     * Builder class for HyperRange that works with strings and numbers
     */
    public static class Builder {
        private List<Integer> range_c;
        private List<Integer> range_z;
        private List<Integer> range_t;

        /**
         * Set Channel Range using a string
         * @param range_str the range string e.g. "1,2,4-8" for range [1,2,4,5,6,7,8]
         * @return the builder
         */
        public Builder setRangeC(String range_str) {
            if (range_str != null)
                this.range_c = parseString(range_str);
            return this;
        }

        /**
         * Set Channel range between a start and end value (inclusive)
         * @param start start channel (1 based)
         * @param end end channel (1 based)
         * @return the builder
         */
        public Builder setRangeC(int start, int end) {
            this.range_c = IntStream.rangeClosed(start, end).boxed().collect(Collectors.toList());
            return this;
        }

        /**
         * Set Slice Range using a string
         * @param range_str the range string e.g. "1,2,4-8" for range [1,2,4,5,6,7,8]
         * @return the builder
         */
        public Builder setRangeZ(String range_str) {
            if (range_str != null)
                this.range_z = parseString(range_str);
            return this;
        }

        /**
         * Set Slice range between a start and end value (inclusive)
         * @param start start Z slice (1 based)
         * @param end end Z slice (1 based)
         * @return the builder
         */
        public Builder setRangeZ(int start, int end) {
            this.range_z = IntStream.rangeClosed(start, end).boxed().collect(Collectors.toList());
            return this;
        }

        /**
         * Set Time Range using a string
         * @param range_str the range string e.g. "1,2,4-8" for range [1,2,4,5,6,7,8]
         * @return the builder
         */
        public Builder setRangeT(String range_str) {
            if (range_str != null)
                this.range_t = parseString(range_str);
            return this;
        }

        /**
         * Set Timepoint range between a start and end value (inclusive)
         * @param start start time (1 based)
         * @param end end time (1 based)
         * @return the builder
         */
        public Builder setRangeT(int start, int end) {
            this.range_t = IntStream.rangeClosed(start, end).boxed().collect(Collectors.toList());
            return this;
        }

        /**
         * Use the image's metadata to build the range of the image
         * @param meta the image's metadata
         * @return the builder
         */
        public Builder fromMetadata(IMetadata meta) {
            int c = meta.getPixelsSizeC(0).getValue();
            int z = meta.getPixelsSizeZ(0).getValue();
            int t = meta.getPixelsSizeT(0).getValue();

            return setRangeC("1:" + c).setRangeZ("1:" + z).setRangeT("1:" + t);
        }

        /**
         * Build the HyperRange using the defined range lists
         * @return the HyperRange
         */
        public HyperRange build() {
            return new HyperRange(range_c, range_z, range_t);
        }
    }


    public static String prettyPrint( List<Integer> list) {
        StringJoiner joiner = new StringJoiner(",");
        list.forEach(item -> joiner.add(item.toString()));
        return joiner.toString();
    }
}
