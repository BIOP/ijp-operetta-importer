package ch.epfl.biop.operetta.companion;

/*
 * #%L
 * BSD implementations of Bio-Formats readers and writers
 * %%
 * Copyright (C) 2005 - 2015 Open Microscopy Environment:
 *   - Board of Regents of the University of Wisconsin-Madison
 *   - Glencoe Software, Inc.
 *   - University of Dundee
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 *
 * @author Chris Allan <callan at blackcat dot ca>
 *
 * Code modified and adapted from
 * Original code here : https://github.com/ome/bioformats/blob/master/components/formats-bsd/test/loci/formats/utests/SPWModelMock.java
 *
 * * = AUTHOR INFORMATION =
 * Code written by Rémy Dornier, EPFL - SV -PTECH - BIOP
 * 13.01.2023
 *
 * = COPYRIGHT =
 * © All rights reserved. ECOLE POLYTECHNIQUE FEDERALE DE LAUSANNE, Switzerland, BioImaging And Optics Platform (BIOP), 2023
 *
 * Licensed under the BSD-3-Clause License:
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided
 * that the following conditions are met:
 *      1. Redistributions of source code must retain the above copyright notice,
 *          this list of conditions and the following disclaimer.
 *      2. Redistributions in binary form must reproduce the above copyright notice,
 *          this list of conditions and the following disclaimer
 *          in the documentation and/or other materials provided with the distribution.
 *      3. Neither the name of the copyright holder nor the names of its contributors
 *          may be used to endorse or promote products
 *          derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * Ressources :
 * - https://www.openmicroscopy.org/Schemas/Documentation/Generated/OME-2016-06/ome_xsd.html#BinaryFile
 * - https://www.javatpoint.com/how-to-read-xml-file-in-java
 * - https://stackoverflow.com/questions/11560173/how-can-i-read-xml-attributes-using-java
 */


import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import ome.xml.model.Instrument;
import ome.xml.model.MapAnnotation;
import ome.xml.model.PlateAcquisition;
import ome.xml.model.TagAnnotation;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import ome.xml.model.Image;
import ome.xml.model.OME;
import ome.xml.model.Plate;
import ome.xml.model.StructuredAnnotations;
import ome.xml.model.Well;
import ome.xml.model.WellSample;
import ome.xml.model.primitives.NonNegativeInteger;

/**
 * The purpose of this class is to generate a .companion.ome file to create an OMERO plate object,
 * with ONLY one image per well. This .companion.ome file can be directly uploaded on OMERO via
 * OMERO.insight software, with screen import option.
 *
 */
public class CompanionFileGenerator {

    private StructuredAnnotations annotations;

    /** OME-XML Metadata */
    private OME ome;

    /** XML namespace. */
    private static final String XML_NS = "http://www.openmicroscopy.org/Schemas/OME/2010-06";

    /** XSI namespace. */
    private static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance";

    /** XML schema location. */
    private static final String SCHEMA_LOCATION = "http://www.openmicroscopy.org/Schemas/OME/2010-06/ome.xsd";

    Plate finalPlate = null;
    Map<String, PlateAcquisition> plateAcquisitionMap = new HashMap<>();
    Map<String, Well> wellMap = new HashMap<>();
    Map<String, Image> imageMap = new HashMap<>();
    Instrument instrument =null;
    Map<String, String> imagePlateAcquisitionMap = new HashMap<>();
    Map<String, String> imageWellMap = new HashMap<>();
    Map<String, List<MapAnnotation>> imageKVPsMap = new HashMap<>();
    Map<String, List<TagAnnotation>> imageTagsMap = new HashMap<>();
    Map<String, List<String>> plateWellMap = new HashMap<>();
    int plateIndex = 0;
    int wellIndex = 0;
    int plateAcquisitionIndex = 0;
    int wellSampleIndex = 0;
    int imageIndex = 0;
    int instrumentIndex = 0;

    public CompanionFileGenerator(){

    }

    /**
     * Set the plate object under which well and images are linked
     *
     * @param plateCompanion
     * @return the plate ID
     */
    public String setPlate(PlateCompanion plateCompanion){
        String id = "Plate:"+plateIndex++;
        Plate plate = plateCompanion.createPlate();
        plate.setID(id);
        finalPlate = plate;
        return id;
    }

    /**
     * Add a new well object under which some images are linked
     *
     * @param wellCompanion
     * @return the well iD
     */
    public String addWell(WellCompanion wellCompanion){
        String id = "Well:"+wellIndex++;
        Well well = wellCompanion.createWell();
        well.setID(id);
        wellMap.put(id, well);
        return id;
    }

    /**
     * Create a plate Acquisition object linked to the current plate.
     * The current plate has to be set first. So, we need to call {@link CompanionFileGenerator#setPlate(PlateCompanion)}
     * before calling this method.
     *
     * @param plateAcquisitionName
     * @return the plate acquisition ID
     */
    public String createPlateAcquisition(String plateAcquisitionName){
        if(finalPlate == null)
            throw new RuntimeException("You first need to set the plate by calling setPlate() method");

        String id = "PlateAcquisition:"+ plateAcquisitionIndex++;
        PlateAcquisition plateAcquisition = new PlateAcquisition();
        plateAcquisition.setID(id);
        plateAcquisition.setName(plateAcquisitionName);

        // link the plate acquisition to the current plate
        plateAcquisition.setPlate(finalPlate);
        finalPlate.addPlateAcquisition(plateAcquisition);

        plateAcquisitionMap.put(id, plateAcquisition);
        return id;
    }

    /**
     * Add a new Image object, linked to a certain well and a certain plate acquisition.
     *
     * @param imageCompanion
     * @param wellId
     * @param plateAcquisitionId
     * @return the image ID
     */
    public String addImage(ImageCompanion imageCompanion, String wellId, String plateAcquisitionId){
        String id = "Image:"+imageIndex++;
        Image image = imageCompanion.createImage();
        image.setID(id);
        imageMap.put(id, image);
        imageWellMap.put(id, wellId);
        imagePlateAcquisitionMap.put(id, plateAcquisitionId);

        // create tags and key-value pairs
        List<MapAnnotation> keyValues = imageCompanion.createMapAnnotations();
        List<TagAnnotation> tags = imageCompanion.createTagAnnotations();
        if(keyValues != null && !keyValues.isEmpty())
            imageKVPsMap.put(id, keyValues);
        if(tags != null && !tags.isEmpty())
            imageTagsMap.put(id, tags);
        return id;
    }

    /**
     * Set the instrument object related to the acquisition
     *
     * @param instrument
     * @return the instrument ID
     */
    public String setInstrument(Instrument instrument){
        String id = "Instrument:"+instrumentIndex++;
        instrument.setID(id);
        this.instrument = instrument;
        return id;
    }

    /**
     * read images & metadata, build a companion.ome file and save in the image folder.
     *
     * @param parentPath path to the destination folder
     * @param filename name of the companion.ome file
     *
     * @throws Exception
     */
    public void buildCompanionFromImageFolder(String parentPath, String filename) throws Exception {

        // generate OME-XML metadata file
        build();

        // create xml document
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder parser = factory.newDocumentBuilder();
        Document document = parser.newDocument();

        // Produce a valid OME DOM element hierarchy
        Element root = this.ome.asXMLElement(document);
        CompanionFileGenerator.postProcess(root, document);

        // Produce string XML
        try(OutputStream outputStream = new FileOutputStream(parentPath + File.separator + filename + ".companion.ome")){
            outputStream.write(CompanionFileGenerator.asString(document).getBytes());
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    /**
     * create the full XML metadata (plate, images, channels, annotations....)
     */
    private void build() {
        // create a new OME-XML metadata instance
        ome = new OME();

        // create a new structured annotation node (for key-values, tags...)
        annotations = new StructuredAnnotations();

        if(instrument != null)
            ome.addInstrument(instrument);

        // create Plate node
        if(finalPlate == null)
            throw new RuntimeException("You first need to set the plate by calling setPlate() method");
        ome.addPlate(makePlate(finalPlate, finalPlate.getID()));

        for (String imageId : imageMap.keySet()) {
            Image image = imageMap.get(imageId);

            List<MapAnnotation> kvps = new ArrayList<>();
            if(imageKVPsMap.containsKey(imageId))
                kvps = imageKVPsMap.get(imageId);

            List<TagAnnotation> tags = new ArrayList<>();
            if(imageTagsMap.containsKey(imageId))
                tags = imageTagsMap.get(imageId);

            // create an Image node in the ome-xml
            ome.addImage(makeImage(image, instrument, kvps, tags));
        }

        // add annotation nodes
        ome.setStructuredAnnotations(annotations);
    }

    /**
     * create an image xml-element, populated with annotations, channel, pixels and path elements
     *
     * @param image the Image object to convert
     * @param instrument the Instrument object used to acquire the image
     * @param keyValues the list of KVPs to link to the current image
     * @param tags the list of tags to link to the current image
     * @return Image xml-element
     */
    private Image makeImage(Image image, Instrument instrument, List<MapAnnotation> keyValues, List<TagAnnotation> tags) {
        int currentSize = annotations.sizeOfTagAnnotationList();
        for(TagAnnotation tagAnnotation: tags) {
            tagAnnotation.setID("TagAnnotation:"+currentSize++);
            annotations.addTagAnnotation(tagAnnotation);
            image.linkAnnotation(tagAnnotation);
        }

        currentSize = annotations.sizeOfMapAnnotationList();
        for(MapAnnotation mapAnnotation: keyValues) {
            mapAnnotation.setID("MapAnnotation:"+currentSize++);
            annotations.addMapAnnotation(mapAnnotation); // add the KeyValues to the general structured annotation element
            image.linkAnnotation(mapAnnotation);
        }

        if(instrument != null)
            image.linkInstrument(instrument);

        return image;
    }


    /**
     * create a Plate xml-element, populated with wells and their attributes
     *
     * @param plate the Plate object to convert
     * @param plateId the plate id
     * @return Plate xml-element
     */
    private Plate makePlate(Plate plate, String plateId) {

        // for each image (one image per well)
        for (String imageId : imageMap.keySet()) {
            String plateAcquisitionId = imagePlateAcquisitionMap.get(imageId);

            PlateAcquisition plateAcquisition = plateAcquisitionMap.get(plateAcquisitionId);

            String wellId = imageWellMap.get(imageId);
            Well well = wellMap.get(wellId);

            if(!plateWellMap.containsKey(plateId)){
                List<String> wellList = new ArrayList<>();
                wellList.add(wellId);
                plateWellMap.put(plateId, wellList);
                plate.addWell(well);
            }else{
                List<String> wellList = plateWellMap.get(plateId);
                if(!wellList.contains(wellId)){
                    wellList.add(wellId);
                    plateWellMap.put(plateId, wellList);
                    plate.addWell(well);
                }
            }

            // Create <WellSample/>
            WellSample sample = new WellSample();
            sample.setID("WellSample:" + wellSampleIndex++);
            sample.setIndex(new NonNegativeInteger(wellSampleIndex));

            // link the wellSample to the current image
            sample.linkImage(imageMap.get(imageId));
            imageMap.get(imageId).linkWellSample(sample);

            // link the well sample to the plate acquisition
            sample.linkPlateAcquisition(plateAcquisition);
            plateAcquisition.linkWellSample(sample);

            // Put <WellSample/> under <Well/>
            well.addWellSample(sample);
        }
        return plate;
    }

    /**
     * convert the XML metadata document into string
     *
     * @param document
     * @return
     *
     * @throws TransformerException
     * @throws UnsupportedEncodingException
     */
    private static String asString(Document document) throws TransformerException, UnsupportedEncodingException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();

        //Setup indenting to "pretty print"
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        Source source = new DOMSource(document);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Result result = new StreamResult(new OutputStreamWriter(os, "utf-8"));
        transformer.transform(source, result);

        return os.toString();
    }

    /**
     * add document header
     *
     * @param root
     * @param document
     */
    private static void postProcess(Element root, Document document) {
        root.setAttribute("xmlns", XML_NS);
        root.setAttribute("xmlns:xsi", XSI_NS);
        root.setAttribute("xsi:schemaLocation", XML_NS + " " + SCHEMA_LOCATION);
        root.setAttribute("UUID", java.util.UUID.randomUUID().toString());
        document.appendChild(root);
    }
}
