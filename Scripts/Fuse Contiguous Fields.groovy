//#@ File id (label="Folder containing Operetta Index file", style="Directory")
//#@ File save_directory (label="Save directory", style="Directory")

def overlapRatio = 0.1

def opm = new OperettaManager.Builder().setId( id )
        .useStitcher(true)
        .setDownsample(2)
        .useAveraging(true)
        .setSaveFolder(save_directory)
        .build()

//opm.getRange().updateCRange("4")
opm.getRange().updateTRange("1:3")
def wells = opm.getWells();

wells.take(1).each { well ->
    def fields = opm.getFields( well )
    def topLeft = opm.getUtilities().getTopLeftCoordinates( fields )

    def rois = fields.withIndex().collect{ field, idx ->
        def fieldID = field.getIndex().getValue()
        def (sX, sY, sZ, sC, sT, sXOut, sYOut, sZOut, sCOut, sTOut) = opm.getUtilities().getIODimensions()
        def origin = opm.getUtilities().getFieldAdjustedCoordinates(field, null, null, topLeft)

        def x = origin.getLongPosition(0)
        def y = origin.getLongPosition(1)
        def w = sXOut
        def h = sYOut

        def extraW = w * overlapRatio
        def extraH = h * overlapRatio
        x -= extraW/2
        y -= extraH/2
        w += extraW
        h += extraH
        def roi = new Roi( x, y, w, h )

        return [id:idx, field:field, roi:roi]
    }

    // Define candidates that are available
    def candidates = rois.collect()

    // Define a list that will contain the discovered clusters
    def clusters = []

    // While there are available candidates
    while (!candidates.isEmpty() ) {
        // Take the last candidate as seed.
        seed = candidates.pop()

        // Prepare a queue which will contain all the fields to explore
        def Q = [] as Queue
        // Initialize this queue with the seed point
        Q.offer(seed)

        // Initialize this cluster, which contains the seed. This will contain the final list of fields that are part of this cluster
        def cluster = [seed]

        // Explore around the seedpoint, adding to the queue
        while( !Q.isEmpty() ) {
            // Get the first element of the queue, as we will insert new points to visit at the end
            seed = Q.poll()
            // Find all rois that overlap with the current one
            def neighbors = candidates.findAll{ opm.getUtilities().isOverlapping( seed.roi, it.roi ) }

            // If neighbors were found, add them to the queue, so they can be visited as well, remove them from the candidates and add them to the growing cluster list
            if (neighbors.size() > 0) {
                println( "Found ${neighbors.size()} neighbors" )
                Q.addAll( neighbors )
                candidates.removeAll( neighbors )
                cluster.addAll( neighbors )
            }
        }
        // When the queue is empty, we have a cluster ready, this will loop if there are candidates left
        clusters.add( cluster )
    }

    // We have the clusters, now export
    def fieldsClusters = clusters.collect{ it.collect{ it.field } }

    println "Exporting clustered fields from Well ${well}"

    fieldsClusters.eachWithIndex() {f, i ->

        println "Cluster ${i+1} is of size ${f.size()}"

        def imp = opm.getWellImage( well, f, null )

        def name = opm.getWellImageName( well ) + "_cluster-" + (i+1)
        IJ.saveAsTiff(imp, new File( save_directory, name + ".tif" ).getAbsolutePath() )

        imp.close()
    }
}

import ch.epfl.biop.operetta.OperettaManager
import ij.IJ
import ij.gui.Roi
