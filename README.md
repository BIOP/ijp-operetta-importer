PerkinElmer/Revvity Operetta Importer Command and API
======================================

[![](https://github.com/BIOP/ijp-operetta-importer/actions/workflows/build-main.yml/badge.svg)](https://github.com/BIOP/ijp-operetta-importer/actions/workflows/build-main.yml)
[![Maven Scijava Version](https://img.shields.io/github/v/tag/BIOP/ijp-operetta-importer?label=Version-[Maven%20Scijava])](https://maven.scijava.org/#browse/browse:releases:ch%2Fepfl%2Fbiop%2FOperetta_Importer)

## Features

This repo has a clear(ish) API and a nice SciJava `InteractiveCommand`

## Documentation

We are hosting the JavaDoc as GitHub pages at 
https://biop.github.io/ijp-operetta-importer/

The main class you should focus your attention on is [OperettaManager](https://biop.github.io/ijp-operetta-importer/index.html?ch/epfl/biop/operetta/OperettaManager.html)

If you'd like to see some examples on how it can be used, please check the [**Scripts**](https://github.com/BIOP/ijp-operetta-importer/tree/master/Scripts) folder. This contains a few Groovy examples on how to call the Operetta Importer API

## Installation in Fiji
Go to `Help > Update...`

Click `Manage Update Sites`

Activate the `PTBIOP` update site.

## Usage

Go under `Plugins -> BIOP -> Operetta importer -> Operetta importer` or type `Operetta importer` in the search bar.

#### Select input folder
- Select the folder to analyse. It should contain all individual images, WITH the `index.idx.xml` file.
- Click on `Ok`

![image](https://github.com/user-attachments/assets/1566f4ac-a9e1-4eeb-83c6-325b2f749f96)

#### Select data to analyze

1. Click on `Choose Wells` button to select a subset of available wells.
2. Click on `Choose Fields` button to select a subset of available fields.
3. Click on `Preview Well slice` button to get a preview of the data
4. Write down a subset of channels to analyze
5. Write down a subset of slices to analyze
6. Write down a subset of frames to analyze

![image](https://github.com/user-attachments/assets/f4a1669d-ac6f-4689-82f6-5b424aed30b1)

#### Select processing options

7. Choose a downsample factor. BE CAREFUL: DO NOT SELECT 0. If you don't want to do any downsampling, select a downsampling of 1.
8. Check the box if you want to average pixels during downsampling.
9. Select a fusion option
    - Do not fuse fields
    - Fuse with stage coordinates => put fields next ot the other without doing any blending
    - Fuse using Grid/Collection stitching => correctly stitch fields with affine transforms
10. Select a flipping option
    - Do not flip
    - Flip horizontal
    - Flip vertical
    - Flip both
11. Select a projection option
    - No Projection
    - mean
    - min
    - max
    - median
    - std
    - sum
12. Select the min/max values of the intensities

![image](https://github.com/user-attachments/assets/41c8e03b-8b49-4c8e-bfb9-7b16c58a9dd8)


#### Output and Saving

13. Choose a directory where to save the output images
14. Check the box to save the fused images as OME-TIFF and to generate the corresponding companion.ome file. This option only works if you have fused fields together.

![image](https://github.com/user-attachments/assets/b1231572-cbbc-44ac-9455-1bef0a9b7700)

15. A message is written at the bottom of the UI to inform you on the size and time the analysis will take. Finally, you can click on `Process`.

![image](https://github.com/user-attachments/assets/c26bee7c-535b-4299-9652-06b54e2800a0)
