//#@ File id (label="Selected File")
//#@ File save_dir (label="Save Location", style="directory")
//#@ Integer downsample (label="Downsample Factor", value=1)
//#@ Boolean fuse_fields (label="Fuse fields")
//#@ Boolean use_stitching (label="Use Grid/Collection stitching for fusion")
//#@ Boolean is_projection ( label = "Perform Projection of Data", value=false )
//#@ String z_projection_method (label = "Projection Type", choices = {"Average Intensity", "Max Intensity", "Min Intensity", "Sum Slices", "Standard Deviation", "Median"} )


import ch.epfl.biop.operetta.OperettaManager
import ij.IJ

// Minimal example, export everything
def opm = new OperettaManager.Builder()
									.setId( id )
									.setDownsample( downsample )
									.fuseFields( fuse_fields )
									.useStitcher(use_stitcher)
									.setSaveFolder( save_dir )
									.build()

// Process everything
opm.process( )

// Process a few wells (all fields)
def wells = opm.getWells().take(2)
opm.process( wells )

// Process only some well and some fields
def fields = opm.getFieldIds().take(2)
def region = null // to specify to use the the maximally spanning region
opm.process( wells, fields, region )

// More complex, with projection and managing wells by hand
def opm2 = new OperettaManager.Builder( )
                            .setId( id )
							.setDownsample( downsample )
							.fuseFields( fuse_fields )
							.useStitcher(use_stitcher)
                            .setProjectionMethod( z_projection_method )
                            .setSaveFolder( save_dir )
                            .build( )

// change the range to be smaller
opm.getRange().updateTRange("1:5")

def allWells = opm.getAvailableWells( )

// Process all wells
allWells.each{ well ->
    def wellImage = opm.getWellImage( well )
    def imageName = opm.getWellImageName( well)
    IJ.saveAsTiff( wellImage, new File( save_dir, imageName ).getAbsolutePath() );
}
