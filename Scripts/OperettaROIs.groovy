//#@File id (label="Selected File")
//#@File save_dir (label="Save Location", style="directory")
//#@Integer downsample (label="Downsample Factor", value=1)


import ch.epfl.biop.operetta.OperettaManager
import ij.gui.Roi
import ij.IJ

// Minimal example, export everything
def opm = new OperettaManager.Builder()
									.setId( id )
									.setSaveFolder( save_dir )
									.build();


def allWells = opm.getWells()

def xpos = [2700, 2700, 2700, 2700, 2700 ]
def ypos = [300, 14500, 29500, 45000, 74000 ]
def width_full = 17500
def height_full = 5000

// Process all wells
allWells.each { well ->
	

	// Process Each ROI
	ypos.eachWithIndex { y, i ->
		def fields = opm.getFields(well)

		def roi = new Roi( xpos[i], ypos[i], width_full, height_full )

		// Get only the fields that intersect with the ROI
		fields = opm.getIntersectingFields(fields, roi)

		// Save each field
		fields.each { field ->
			def one_field = opm.getFieldImage( field, downsample )

			IJ.saveAsTiff(one_field, new File(save_dir, one_field.getTitle()).getAbsolutePath())

		}
		//Save the positions file
		opm.writeWellPositionsFile(fields, new File(save_dir, sprintf("ROI-%2d.txt", i)), downsample)
	}
}