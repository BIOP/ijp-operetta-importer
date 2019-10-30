//#@File id (label="Selected File")
//#@File save_dir (label="Save Location", style="directory")
//#@String sources_str (label="Source Channels", value="1")
//#@String targets_str (label="Target Channels", value="2")


import ch.epfl.biop.operetta.OperettaManager
import ch.epfl.biop.operetta.utils.HyperRange
import groovyx.gpars.GParsPool
import ij.IJ
import ij.ImagePlus
import ij.plugin.ChannelSplitter
import ij.plugin.RGBStackMerge

// Create the directories
def source_dir = new File ( save_dir, 'source' )
def target_dir = new File ( save_dir, 'target' )

source_dir.mkdirs()
target_dir.mkdirs()


// Start the Operetta Manager
def opm = new OperettaManager.Builder( )
                            .setId( id )
                            .setSaveFolder( save_dir )
                            .build( )

// Pick up the channels to use as source
def sources = HyperRange.parseString( sources_str )
def targets = HyperRange.parseString( targets_str )


println( sources )



def allWells = opm.getAvailableWells( )

//Process all wells

allWells.each{ well ->
    def fields = opm.getAvailableSamples( well )
	
	
GParsPool.withPool( 6 ) {
	fields.eachParallel { field ->
		def image = opm.getFieldImage( field )
		//image.show()
		
		ImagePlus[] channels = ChannelSplitter.split( image )

		def source_images = sources.collect{ return channels[ it-1 ] }
		def target_images = targets.collect{ return channels[ it-1 ] }

		def source = source_images[0]
		def target = target_images[0]
		
		if (source_images.size() > 1 )
			source = RGBStackMerge.mergeChannels( source_images as ImagePlus[], false )

		if (target_images.size() > 1 )
			target = RGBStackMerge.mergeChannels( target_images as ImagePlus[], false )

		def hash = image.getTitle().digest('SHA-1')

		IJ.saveAsTiff( source, new File( source_dir, hash ).getAbsolutePath() )

		IJ.saveAsTiff( target, new File( target_dir, hash ).getAbsolutePath() )
		println ( sprintf( "Field %s done, saved as %s", image.getTitle(), hash ) )
	}
}
}
