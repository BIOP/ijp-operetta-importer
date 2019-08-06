//#@File id (label="Selected File")
//#@File save_dir (label="Save Location", style="directory")
//#@Integer downsample (label="Downsample Factor", value=1)
//#@Boolean is_projection ( label = "Perform Projection of Data", value=false )
//#@String z_projection_method (label = "Projection Type", choices = {"Average Intensity", "Max Intensity", "Min Intensity", "Sum Slices", "Standard Deviation", "Median"} )


import ch.epfl.biop.operetta.OperettaManager
import ij.IJ

// Minimal example, export everything
def opm = new OperettaManager.Builder()
									.setId( id )
									.setSaveFolder( save_dir )
									.build();

// Process everything
// opm.process( downsample, roi, export_fields_individually);
opm.process( downsample, null, true);

// More complex, with projection
def opm2 = new OperettaManager.Builder( )
                            .setId( id )
                            .doProjection( is_projection )
                            .setProjectionMethod( z_projection_method )
                            .setSaveFolder( save_dir )
                            .build( )

// change the range to be smaller
opm.getRange().updateTRange("1:5")

def allWells = opm.getAvailableWells( )

// Process all wells
allWells.each{ well ->
    def wellImage = opm.getWellImage( well );
    
    IJ.saveAsTiff( wellImage, new File( save_dir, wellImage.getTitle() ).getAbsolutePath() );
}

// Process all at once
//def allFields = opm.getAvailableFieldIds();
//opm.process( allWells, allFields, downsample, null, false )

