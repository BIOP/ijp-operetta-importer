/*-
 * #%L
 * Hold your horses
 * %%
 * Copyright (C) 2019 - 2025 BIOP
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
    private final String name;
    private final Timestamp acquisitionDate;
    private final String description;
    private final Instrument instrument;
    private final ObjectiveSettings objectiveSettings;
    private final ImagingEnvironment imagingEnvironment;
    private final StageLabel stageLabel;
    private final LightSourceSettings lightSourceSettings;
    private final LightPath lightPath;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int sizeC;
    private final int sizeT;
    private final DimensionOrder dimensionOrder;
    private final PixelType pixelType;
    private final Length pixelSizeX;
    private final Length pixelSizeY;
    private final List<String> tags;
    private final Map<String, Map<String, String>> kvpsMapByNS;
    private final Image image;
    private final List<Channel> channels;
    private final Map<String, String> globalMetadata;

    /**
     * ImageCompanion Constructor. This constructor is private as you need to use the Builder class
     * to generate the ImageCompanion instance. {@link Builder}
     *
     * @param image                 the Image object
     * @param name                  name of the image that will be displayed on OMERO
     * @param description           description of the images that will be displayed on OMERO
     * @param sizeX                 image width
     * @param sizeY                 image height
     * @param sizeZ                 stack size
     * @param sizeC                 nChannels
     * @param sizeT                 nTimepoints
     * @param dimensionOrder        The order of how XYCZT are saved for this image
     * @param pixelType             image bit depth
     * @param pixelSizeX            Physical size x
     * @param pixelSizeY            Physical size y
     * @param acquisitionDate       TimeStamp of the acquisition date
     * @param instrument
     * @param imagingEnvironment
     * @param objectiveSettings
     * @param stageLabel
     * @param lightSourceSettings
     * @param lightPath
     * @param channels              list of Channel objects
     * @param globalMetadata        Any metadata to add under the image description
     * @param tags                  List of tags to link to the image on OMERO
     * @param kvpsMapByNS           Map of metadata to add on OMERO, in the specified namespace
     */
    private ImageCompanion(Image image,
                           String name,
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
                           LightPath lightPath,
                           List<Channel> channels,
                           Map<String, String> globalMetadata,
                           List<String> tags,
                           Map<String, Map<String, String>> kvpsMapByNS){

        this.image = image;
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
        this.tags = tags;
        this.kvpsMapByNS = kvpsMapByNS;
        this.globalMetadata = globalMetadata;
        this.channels = channels;
    }


    /**
     * Transform the current imageCompanion into an Image object,
     * compatible with a companion.ome file
     *
     * @return the Image object
     */
    protected Image createImage(){
        Image image;
        if(this.image != null){
            image = this.image;
        }else{
            image = new Image();
            image.setName(this.name);
            if(this.description != null) {
                image.setDescription(this.description);
            }
            if(!this.globalMetadata.isEmpty()){
                String metadata = "";
                for (String key: this.globalMetadata.keySet()){
                    metadata += "\n"+key+": "+this.globalMetadata.get(key);
                }
                if(this.description != null) {
                    image.setDescription(this.description + "\n" + metadata);
                }
                else {
                    image.setDescription(metadata);
                }
            }
            if(this.acquisitionDate != null) {
                image.setAcquisitionDate(this.acquisitionDate);
            }
            if(this.objectiveSettings != null) {
                image.setObjectiveSettings(this.objectiveSettings);
            }
            if(this.imagingEnvironment != null) {
                image.setImagingEnvironment(this.imagingEnvironment);
            }
            if(this.stageLabel != null) {
                image.setStageLabel(this.stageLabel);
            }
            if(this.instrument != null) {
                image.linkInstrument(this.instrument);
            }
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
                if(this.channels.size() != this.sizeC) {
                    for (int i = 0; i < this.sizeC; i++) {
                        Channel channel = new Channel();
                        channel.setID("Channel:" + i);

                        // Create <LightSourceSettings/> and link to <Channel/>
                        if (this.lightSourceSettings != null) {
                            channel.setLightSourceSettings(this.lightSourceSettings);
                        }

                        if (this.lightPath != null) {
                            channel.setLightPath(this.lightPath);
                        }

                        pixels.addChannel(channel);
                    }
                }else{
                    for (Channel channel: this.channels) {
                        pixels.addChannel(channel);
                    }
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
    protected List<TagAnnotation> createTagAnnotations(){
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
    protected List<MapAnnotation> createMapAnnotations(){
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
        private List<Channel> channels = new ArrayList<>();
        private Map<String, String> globalMetadata = new HashMap<>();
        private List<String> tags = new ArrayList<>();
        private Map<String, Map<String, String>> kvpsMapByNS = new HashMap<>();
        private Image image = null;


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

        public Builder setImage(Image image){
            this.image = image;
            return this;
        }

        public Builder addChannel(Channel channel){
            this.channels.add(channel);
            return this;
        }

        public Builder addChannels(List<Channel> channels){
            this.channels.addAll(channels);
            return this;
        }

        public Builder addGlobalMetadata(Map<String, String> globalMetadata){
            this.globalMetadata.putAll(globalMetadata);
            return this;
        }

        public Builder addGlobalMetadata(String key, String value){
            this.globalMetadata.put(key, value);
            return this;
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
            Map<String, String> kvp;
            if (this.kvpsMapByNS.containsKey(namespace)) {
                kvp = this.kvpsMapByNS.get(namespace);
            }else{
                kvp = new HashMap<>();
            }
            kvp.put(key, value);
            this.kvpsMapByNS.put(namespace, kvp);
        }

        public ImageCompanion build(){
            return new ImageCompanion(this.image,
                    this.name,
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
                    this.lightPath,
                    this.channels,
                    this.globalMetadata,
                    this.tags,
                    this.kvpsMapByNS
            );
        }
    }
}
