# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Fiji/ImageJ plugin for importing and processing high-content screening data from PerkinElmer/Revvity Operetta microscopes. It provides both an interactive GUI and a programmatic API supporting multiple Harmony versions (V5, V5 Flatfield, V6).

## Build Commands

```bash
# Build the project
mvn clean install

# CI build (uses SciJava scripts)
.github/build.sh
```

The project uses Maven with SciJava Parent POM and targets Java 8. Releases deploy to the SciJava Maven repository and are installed in Fiji via the "PTBIOP" update site.

## Architecture

### Core Components

**OperettaManager.java** (`ch.epfl.biop.operetta`) - The central API class (1948 lines)
- Uses Builder pattern for configuration
- Orchestrates the entire import/processing workflow
- Contains inner `Utilities` class for coordinate transformation and calibration
- Key methods: `getWells()`, `getFieldImage()`, `getWellImage()`, `process()`
- Supports parallel TIFF loading via ForkJoinPool (10 threads)

**Commands Package** (`ch.epfl.biop.operetta.commands`) - SciJava plugin commands
- `OperettaImporter`: Entry point - folder selection and XML auto-detection
- `OperettaImporterInteractive`: Full GUI with well/field/range selection, processing options
- `OperettaImporterHiddenSettings`: XY coordinate correction factor preference

**Companion Package** (`ch.epfl.biop.operetta.companion`) - OME-TIFF metadata generation
- `CompanionFileGenerator`, `PlateCompanion`, `WellCompanion`, `ImageCompanion`
- Creates OME-XML companion files for exports

**Utils Package** (`ch.epfl.biop.operetta.utils`)
- `HyperRange`: Parses range strings like "1,3,5:10" for C/Z/T selection
- `FCZT`: Immutable tuple for Fields/Channels/Z/Time coordinates

### Supported Features
- XML versions: Index.idx.xml (V5), Index.flex.xml (V5 Flatfield), Index.xml (V6)
- Downsampling with optional averaging
- Field fusion: simple (stage coordinates) or advanced (Grid/Collection Stitching)
- Z-projection: Max, Min, Mean, Median, Sum, StdDev
- Image flipping, intensity normalization
- OME-TIFF export with companion metadata
- Task monitoring and cancellation

### Data Flow
1. User selects folder containing Operetta export
2. `OperettaImporter` detects XML file type, creates BioFormats reader (with memoization)
3. `OperettaImporterInteractive` presents GUI for parameter selection
4. `OperettaManager` built via Builder with validated parameters
5. `process()` iterates through wells/fields, loading TIFFs in parallel
6. Processing applied (flip, normalize, downsample, fuse, project)
7. Output saved as TIFF or OME-TIFF

## Programmatic API Usage

```java
OperettaManager opm = new OperettaManager.Builder()
    .setId(new File("path/to/Index.xml"))
    .setSaveFolder(outputDir)
    .setDownsample(4)
    .fuseFields(true)
    .useStitcher(true)
    .build();

opm.process(); // Process all wells
// or
opm.process(selectedWells, selectedFields, region);
```

See `Scripts/` folder for Groovy examples demonstrating API usage patterns.

## Key Dependencies

- ImageJ/Fiji ecosystem (imagej, imagej-legacy)
- Bio-Formats (ome.formats-gpl) for Operetta XML/TIFF reading
- Stitching plugin (sc.fiji:Stitching_)
- ijp-kheops for OME-TIFF export

## Code Conventions

- GPL v3 license header on all source files
- SLF4J logging (not System.out)
- Builder pattern for complex object construction
- SciJava @Plugin annotations for Fiji integration
