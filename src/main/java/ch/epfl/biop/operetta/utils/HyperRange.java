package ch.epfl.biop.operetta.utils;

import ij.ImagePlus;
import ij.ImageStack;
import loci.formats.meta.IMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class HyperRange {

    private static final Logger logger = LoggerFactory.getLogger( HyperRange.class);

    private List<Integer> range_c;
    private List<Integer> range_z;
    private List<Integer> range_t;
    private final Pattern czt_pattern = Pattern.compile(".*p(\\d*)-ch(\\d*)sk(\\d*)fk(\\d*).*");

    private ImagePlus imp;

    public void setRangeC( List<Integer> range_c ) {
        this.range_c = range_c;
        updateImagePlusPositions();

    }

    public void setRangeZ( List<Integer> range_z ) {
        this.range_z = range_z;
        updateImagePlusPositions();

    }

    public void setRangeT( List<Integer> range_t ) {
        this.range_t = range_t;
        updateImagePlusPositions();

    }

    HyperRange( List<Integer> range_c, List<Integer> range_z, List<Integer> range_t) {

        this.range_c = range_c;
        this.range_z = range_z;
        this.range_t = range_t;

        updateImagePlusPositions();

    }

    private void updateImagePlusPositions() {
        ImageStack s = ImageStack.create(1, 1, getTotalPlanes(), 8);
        this.imp = new ImagePlus("", s);
        this.imp.setDimensions(range_c.size(), range_z.size(), range_t.size());
    }

    public void updateCRange(String new_range) {
        this.range_c = parseString( new_range );
        updateImagePlusPositions();

    }

    public void updateZRange(String new_range) {
        this.range_z = parseString(new_range );
        updateImagePlusPositions();

    }

    public void updateTRange(String new_range) {
        this.range_t = parseString(new_range);
        updateImagePlusPositions();

    }

    public int getTotalPlanes() {
        return this.range_c.size() * this.range_t.size() * this.range_z.size();
    }

    public boolean includes(String s) {
        Matcher m = czt_pattern.matcher(s);

        if (m.find()) {
            int c = Integer.parseInt(m.group(2));
            int z = Integer.parseInt(m.group(1));
            int t = Integer.parseInt(m.group(3));

            return range_c.contains(c) && range_z.contains(z) && range_t.contains(t);
        }

        return false;
    }

    public Map<String, Integer> getIndexes(String s) {

        Map<String, Integer> indexes = new HashMap<>();

        Matcher m = czt_pattern.matcher(s);

        if (m.find()) {
            int c = Integer.parseInt(m.group(2));
            indexes.put("C", c);

            int z = Integer.parseInt(m.group(1));
            indexes.put("Z", z);

            int t = Integer.parseInt(m.group(3));
            indexes.put("T", t);

            // This is assuming we want an index that is continuous and starting from 1 but what if that's not the case?
            //int idx = imp.getStackIndex(c, z, t);//int idx = imp.getStackIndex(c, z, t);

            int idx = imp.getStackIndex(range_c.indexOf( c )+1, range_z.indexOf( z )+1, range_t.indexOf( t )+1);
            indexes.put("I", idx);

        }

        return indexes;

    }

    public int[] getCZTDimensions() {
        return new int[]{this.range_c.size(), this.range_z.size(), this.range_t.size()};

    }

    public HyperRange confirmRange( IMetadata metadata ) {
        int cs = metadata.getPixelsSizeC(0).getValue();
        int zs = metadata.getPixelsSizeZ(0).getValue();
        int ts = metadata.getPixelsSizeT(0).getValue();

        this.range_c = range_c.stream().filter( c -> {
            boolean inside = (c>=1 && c<=cs);
            if (!inside) logger.info("Removed channel {} because it is not in range of data 1-{}.", c, cs);
            return inside;
        } ).collect(Collectors.toList());

        this.range_z = range_z.stream().filter( z -> {
            boolean inside = (z>=1 && z<=zs);
            if (!inside) logger.info("Removed slice {} because it is not in range of data 1-{}.", z, zs);
            return inside;
        } ).collect(Collectors.toList());

        this.range_t = range_t.stream().filter( t -> {
            boolean inside = (t>=1 && t<=ts);
            if (!inside) logger.info("Removed timepoint {} because it is not in range of data 1-{}.", t, ts);
            return inside;
        } ).collect(Collectors.toList());



        return this;
    }
    public static List<Integer> parseString(String s) {
        // first split by commas
        List<Integer> range = new ArrayList<>();

        String[] sr = s.split(",");
        Arrays.stream(sr).forEach(r -> {
                    String[] sr2 = r.split(":");
                    if (sr2.length == 2) {
                        List<Integer> subrange = IntStream.rangeClosed(Integer.valueOf(sr2[0].trim() ), Integer.valueOf(sr2[1].trim()))
                                .boxed().collect(Collectors.toList());
                        range.addAll(subrange);
                    } else {
                        if(sr2[0].length() > 0)
                        range.add(Integer.valueOf(sr2[0].trim()));
                    }
                }
        );
        return range;
    }

    public List<Integer> getRangeC( ) {
        return this.range_c;
    }
    public List<Integer> getRangeZ( ) {
        return this.range_z;
    }
    public List<Integer> getRangeT( ) {
        return this.range_t;
    }

    @Override
    public String toString( ) {
        return String.format( "Range :\n\t\tC: %s\n\t\tZ: %s\n\t\tT: %s", range_c.toString(), range_z.toString(), range_t.toString() );
    }


    public static class Builder {
        private List<Integer> range_c;
        private List<Integer> range_z;
        private List<Integer> range_t;

        public Builder setRangeC(String range_str) {
            if (range_str != null)
                this.range_c = parseString(range_str);
            return this;
        }

        public Builder setRangeC(int start, int end) {
            this.range_c = IntStream.rangeClosed( start, end ).boxed().collect( Collectors.toList());
            return this;
        }

        public Builder setRangeZ(String range_str) {
            if (range_str != null)
                this.range_z = parseString(range_str);
            return this;
        }

        public Builder setRangeZ(int start, int end) {
            this.range_z = IntStream.rangeClosed( start, end ).boxed().collect( Collectors.toList());
            return this;
        }

        public Builder setRangeT(String range_str) {
            if (range_str != null)
                this.range_t = parseString(range_str);
            return this;
        }

        public Builder setRangeT(int start, int end) {
            this.range_t = IntStream.rangeClosed( start, end ).boxed().collect( Collectors.toList());
            return this;
        }

        public Builder fromMetadata(IMetadata meta) {
            int c = meta.getPixelsSizeC(0).getValue();
            int z = meta.getPixelsSizeZ(0).getValue();
            int t = meta.getPixelsSizeT(0).getValue();

            return setRangeC("1:"+c).setRangeZ("1:"+z).setRangeT("1:"+t);
        }


        public HyperRange build() {
            return new HyperRange(range_c, range_z, range_t);
        }
    }
}
