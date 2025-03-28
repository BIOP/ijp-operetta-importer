package ch.epfl.biop.operetta.companion;


import ome.xml.model.LightPath;
import ome.xml.model.LightSourceSettings;
import ome.xml.model.MapPair;
import ome.xml.model.enums.DimensionOrder;
import ome.units.quantity.Length;
import ome.xml.model.Channel;
import ome.xml.model.Image;
import ome.xml.model.ImagingEnvironment;
import ome.xml.model.Instrument;
import ome.xml.model.MapAnnotation;
import ome.xml.model.ObjectiveSettings;
import ome.xml.model.Pixels;
import ome.xml.model.StageLabel;
import ome.xml.model.TagAnnotation;
import ome.xml.model.TiffData;
import ome.xml.model.UUID;
import ome.xml.model.enums.PixelType;
import ome.xml.model.primitives.NonNegativeInteger;
import ome.xml.model.primitives.PositiveInteger;
import ome.xml.model.primitives.Timestamp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ImageCompanion {
    private String name;
    private Timestamp acquisitionDate;
    private String description;
    private Instrument instrument;
    private ObjectiveSettings objectiveSettings;
    private ImagingEnvironment imagingEnvironment;
    private StageLabel stageLabel;
    private LightSourceSettings lightSourceSettings;
    private LightPath lightPath;
    private int sizeX;
    private int sizeY;
    private int sizeZ;
    private int sizeC;
    private int sizeT;
    private DimensionOrder dimensionOrder;
    private PixelType pixelType;
    private Length pixelSizeX;
    private Length pixelSizeY;
    private List<String> tags;
    private Map<String, Map<String, String>> kvpsMapByNS;
    private Image image;

    /**
     * ImageCompanion Constructor. This constructor is private as you need to use the Builder class
     * to generate the ImageCompanion instance. {@link Builder}
     *
     * @param name                  name of the image
     * @param description           description of the images
     * @param sizeX                 image width
     * @param sizeY                 image height
     * @param sizeZ                 stack size
     * @param sizeC                 nChannels
     * @param sizeT                 nTimepoints
     * @param dimensionOrder        The order of how XYCZT are saved for this image
     * @param pixelType             bit depth
     * @param pixelSizeX            Physical size x
     * @param pixelSizeY            Physical size y
     * @param acquisitionDate       TimeStamp of the acquisition date
     * @param instrument
     * @param imagingEnvironment
     * @param objectiveSettings
     * @param stageLabel
     * @param lightSourceSettings
     * @param lightPath
     */
    private ImageCompanion(String name,
                           String description,
                           int sizeX,
                           int sizeY,
                           int sizeZ,
                           int sizeC,
                           int sizeT,
                           DimensionOrder dimensionOrder,
                           PixelType pixelType,
                           Length pixelSizeX,
                           Length pixelSizeY,
                           Timestamp acquisitionDate,
                           Instrument instrument,
                           ImagingEnvironment imagingEnvironment,
                           ObjectiveSettings objectiveSettings,
                           StageLabel stageLabel,
                           LightSourceSettings lightSourceSettings,
                           LightPath lightPath){

        this.image = null;
        this.name = name;
        this.description = description;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.sizeC = sizeC;
        this.sizeT = sizeT;
        this.dimensionOrder = dimensionOrder;
        this.pixelType = pixelType;
        this.pixelSizeX = pixelSizeX;
        this.pixelSizeY = pixelSizeY;
        this.acquisitionDate = acquisitionDate;
        this.instrument = instrument;
        this.imagingEnvironment = imagingEnvironment;
        this.objectiveSettings = objectiveSettings;
        this.stageLabel = stageLabel;
        this.lightSourceSettings = lightSourceSettings;
        this.lightPath = lightPath;
        this.tags = new ArrayList<>();
        this.kvpsMapByNS = new HashMap<>();
    }


    /**
     * ImageCompanion Constructor. This constructor is private as you need to use the Builder class
     * to generate the ImageCompanion instance. {@link Builder}
     *
     * @param image
     */
    private ImageCompanion(Image image){
        this.image = image;
        this.tags = new ArrayList<>();
        this.kvpsMapByNS = new HashMap<>();
    }


    /**
     * Transform the current imageCompanion into an Image object,
     * compatible with a companion.ome file
     *
     * @return
     */
    public Image createImage(){
        Image image;
        if(this.image != null){
            image = this.image;
        }else{
            image = new Image();
            image.setName(this.name);
            if(this.description != null)
                image.setDescription(this.description);
            if(this.acquisitionDate != null)
                image.setAcquisitionDate(this.acquisitionDate);
            if(this.objectiveSettings != null)
                image.setObjectiveSettings(this.objectiveSettings);
            if(this.imagingEnvironment != null)
                image.setImagingEnvironment(this.imagingEnvironment);
            if(this.stageLabel != null)
                image.setStageLabel(this.stageLabel);
            if(this.instrument != null)
                image.linkInstrument(this.instrument);
            if(this.sizeX > 0 && this.sizeC > 0 && this.sizeT > 0 && this.sizeY > 0 && this.sizeZ > 0 &&
                    this.pixelSizeX != null && this.pixelSizeY != null && this.dimensionOrder != null &&
                    this.pixelType != null){
                // Create <Pixels/>
                Pixels pixels = new Pixels();
                pixels.setID("Pixels:0");
                pixels.setSizeX(new PositiveInteger(this.sizeX));
                pixels.setSizeY(new PositiveInteger(this.sizeY));
                pixels.setSizeZ(new PositiveInteger(this.sizeZ));
                pixels.setSizeC(new PositiveInteger(this.sizeC));
                pixels.setSizeT(new PositiveInteger(this.sizeT));
                pixels.setDimensionOrder(this.dimensionOrder);
                pixels.setType(this.pixelType);
                pixels.setPhysicalSizeX(this.pixelSizeX);
                pixels.setPhysicalSizeY(this.pixelSizeY);

                // Create <TiffData/> under <Pixels/>
                TiffData tiffData = new TiffData();
                tiffData.setFirstC(new NonNegativeInteger(0));
                tiffData.setFirstT(new NonNegativeInteger(0));
                tiffData.setFirstZ(new NonNegativeInteger(0));
                tiffData.setPlaneCount(new NonNegativeInteger(this.sizeC * this.sizeT * this.sizeZ));

                // Create <UUID/> under <TiffData/>
                UUID uuid = new UUID();
                uuid.setFileName(this.name);
                uuid.setValue(java.util.UUID.randomUUID().toString());
                tiffData.setUUID(uuid);

                // Put <TiffData/> under <Pixels/>
                pixels.addTiffData(tiffData);

                // Create <Channel/> under <Pixels/>
                for (int i = 0; i < this.sizeC; i++) {
                    Channel channel = new Channel();
                    channel.setID("Channel:" + i);

                    // Create <LightSourceSettings/> and link to <Channel/>
                    if(this.lightSourceSettings != null)
                        channel.setLightSourceSettings(this.lightSourceSettings);

                    if(this.lightPath != null)
                        channel.setLightPath(this.lightPath);

                    pixels.addChannel(channel);
                }

                // Put <Pixels/> under <Image/>
                image.setPixels(pixels);
            }
        }

        return image;
    }


    /**
     * Transform the tags into a list of TagAnnotation objects,
     * compatible with a companion.ome file
     *
     * @return
     */
    public List<TagAnnotation> createTagAnnotations(){
        // Create <TagAnnotations/> for tags
        List<TagAnnotation> tagAnnotationList = new ArrayList<>();
        int i = 0;
        for(String tag : this.tags) {
            TagAnnotation tagAnnotation = new TagAnnotation();
            tagAnnotation.setID("TagAnnotation:" + i++);
            tagAnnotation.setValue(tag);
            tagAnnotationList.add(tagAnnotation);
        }
        return tagAnnotationList;
    }


    /**
     * Transform the key-values into a list of MapAnnotation objects,
     * compatible with a companion.ome file
     *
     * @return
     */
    public List<MapAnnotation> createMapAnnotations(){
        // Create <MapAnnotations/> for key-values
        List<MapAnnotation> mapAnnotationList = new ArrayList<>();
        int i = 0;
        for(String namespace : this.kvpsMapByNS.keySet()) {
            List<MapPair> kvp = new ArrayList<>();
            Map<String, String> kvpByNS = this.kvpsMapByNS.get(namespace);
            for(String kvpKey: kvpByNS.keySet()){
                kvp.add(new MapPair(kvpKey, kvpByNS.get(kvpKey)));
            }
            MapAnnotation mapAnnotation = new MapAnnotation();
            mapAnnotation.setID("MapAnnotation:" + i++);
            mapAnnotation.setNamespace(namespace);
            mapAnnotation.setValue(kvp);
            mapAnnotationList.add(mapAnnotation);
        }

        return mapAnnotationList;
    }


    public void addTag(String tag) {
        this.tags.add(tag);
    }


    public void addTags(List<String> tags) {
        this.tags.addAll(tags);
    }


    public void addKVPs(Map<String, String> kvps, String namespace) {
        if (this.kvpsMapByNS.containsKey(namespace)) {
            Map<String, String> kvp = this.kvpsMapByNS.get(namespace);
            kvp.putAll(kvps);
            this.kvpsMapByNS.put(namespace, kvp);
        }else{
            this.kvpsMapByNS.put(namespace, kvps);
        }
    }


    public void addKVP(String key, String value, String namespace) {
        if (this.kvpsMapByNS.containsKey(namespace)) {
            Map<String, String> kvp = this.kvpsMapByNS.get(namespace);
            kvp.put(key, value);
            this.kvpsMapByNS.put(namespace, kvp);
        }else{
            Map<String, String> kvp = new HashMap<>();
            kvp.put(key, value);
            this.kvpsMapByNS.put(namespace, kvp);
        }
    }


    /**
     * This Builder class handles creating {@link ImageCompanion} objects for you
     * <p>
     * If you're curious about the Builder Pattern, you can read Joshua Bloch's excellent <a href="https://www.pearson.com/us/higher-education/program/Bloch-Effective-Java-3rd-Edition/PGM1763855.html">Effective Java Book</a>
     * <p>
     * Use
     * When creating a new ImageCompanion object, call the Builder, add all the options and then call the {@link Builder#build()} method
     * <pre>
     * {@code
     * ImageCompanion imageCompanion = new ImageCompanion.Builder()
     * 									    .setName( name )
     * 									    .setSizeX( 512 )
     * 								        //  Other options here
     * 									    .build();
     * }
     * </pre>
     * Mandatory fields
     * <p>
     * <ul>
     * <li> name </li>
     * <li> sizeX </li>
     * <li> sizeY </li>
     * <li> sizeC </li>
     * <li> sizeT </li>
     * <li> sizeZ </li>
     * <li> dimensionOrder </li>
     * <li> pixelType </li>
     * <li> pixelSizeX </li>
     * <li> pixelSizeY </li>
     * </ul>
     * <p>
     *
     */
    public static class Builder{
        private String name = "Image";
        private Timestamp acquisitionDate = null;
        private String description = null;
        private Instrument instrument = null;
        private ObjectiveSettings objectiveSettings = null;
        private ImagingEnvironment imagingEnvironment = null;
        private LightSourceSettings lightSourceSettings = null;
        private LightPath lightPath = null;
        private StageLabel stageLabel = null;
        private int sizeX = -1;
        private int sizeY = -1;
        private int sizeZ = -1;
        private int sizeC = -1;
        private int sizeT = -1;
        private DimensionOrder dimensionOrder = null;
        private PixelType pixelType = null;
        private Length pixelSizeX = null;
        private Length pixelSizeY = null;


        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public Builder setSizeX(int sizeX) {
            this.sizeX = sizeX;
            return this;
        }

        public Builder setSizeY(int sizeY) {
            this.sizeY = sizeY;
            return this;
        }

        public Builder setSizeZ(int sizeZ) {
            this.sizeZ = sizeZ;
            return this;
        }

        public Builder setSizeC(int sizeC) {
            this.sizeC = sizeC;
            return this;
        }

        public Builder setSizeT(int sizeT) {
            this.sizeT = sizeT;
            return this;
        }

        public Builder setDescription(String description) {
            this.description = description;
            return this;
        }

        public Builder setInstrument(Instrument instrument) {
            this.instrument = instrument;
            return this;
        }

        public Builder setAcquisitionDate(Timestamp acquisitionDate) {
            this.acquisitionDate = acquisitionDate;
            return this;
        }

        public Builder setObjectiveSettings(ObjectiveSettings objectiveSettings) {
            this.objectiveSettings = objectiveSettings;
            return this;
        }

        public Builder setImagingEnvironment(ImagingEnvironment imagingEnvironment) {
            this.imagingEnvironment = imagingEnvironment;
            return this;
        }

        public Builder setStageLabel(StageLabel stageLabel) {
            this.stageLabel = stageLabel;
            return this;
        }

        public Builder setLightPath(LightPath lightPath) {
            this.lightPath = lightPath;
            return this;
        }

        public Builder setLightSourceSettings(LightSourceSettings lightSourceSettings) {
            this.lightSourceSettings = lightSourceSettings;
            return this;
        }

        public Builder setPixelType(PixelType pixelType) {
            this.pixelType = pixelType;
            return this;
        }

        public Builder setDimensionOrder(DimensionOrder dimensionOrder) {
            this.dimensionOrder = dimensionOrder;
            return this;
        }

        public Builder setPixelSizeX(Length pixelSizeX) {
            this.pixelSizeX = pixelSizeX;
            return this;
        }

        public Builder setPixelSizeY(Length pixelSizeY) {
            this.pixelSizeY = pixelSizeY;
            return this;
        }

        public ImageCompanion setImage(Image image){
            return new ImageCompanion(image);
        }

        public ImageCompanion build(){
            return new ImageCompanion(this.name,
                    this.description,
                    this.sizeX,
                    this.sizeY,
                    this.sizeZ,
                    this.sizeC,
                    this.sizeT,
                    this.dimensionOrder,
                    this.pixelType,
                    this.pixelSizeX,
                    this.pixelSizeY,
                    this.acquisitionDate,
                    this.instrument,
                    this.imagingEnvironment,
                    this.objectiveSettings,
                    this.stageLabel,
                    this.lightSourceSettings,
                    this.lightPath
            );
        }
    }
}
