//#@File id (label="Selected File")
//#@File save_dir (label="Save Location", style="directory")
//#@Integer downsample (label="Downsample Factor", value=1)
//#@Boolean is_projection ( label = "Perform Projection of Data", value=4 )
//#@String z_projection_method (label = "Projection Type", choices = {"Average Intensity", "Max Intensity", "Min Intensity", "Sum Slices", "Standard Deviation", "Median"} )


import ch.epfl.biop.operetta.OperettaManager
import ch.epfl.biop.operetta.utils.CZTRange

def opm = new OperettaManager.Builder( )
                            .setId( id )
                            .doProjection( is_projection )
                            .setProjectionMethod( z_projection_method )
                            .setSaveFolder( save_dir )
                            .build( )


// change the range to be smaller
opm.getRange().updateTRange("1:5")

def allWells = opm.getAvailableWells( )
def allFields = opm.getAvailableFieldIds( )


opm.process( allWells, allFields, downsample, null, false )