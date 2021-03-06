package qupath.lib.active_learning;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import qupath.lib.objects.PathObject;

/**
 * Propose samples by clustering with a certain clustering algorithm.
 * 
 * @author Sam Vanmassenhove
 *
 */
public class ClusteringSampleProposal extends AbstractSampleProposal {
	
	private AbstractClusterer clusterer;
	private Map <Integer, Iterator<ClusterableObject>> iteratorMap;
	private Map <Integer, List<ClusterableObject>> clusterMap;
	private int currentCluster, clusterCount;
	
	public ClusteringSampleProposal(final List<PathObject> pathObjects, final List<String> featureNames, final int clusterCount) {
		super(pathObjects);
		clusterer = new KMeansClusterer(pathObjects, featureNames, clusterCount);
		this.clusterCount = clusterCount;
		iteratorMap = new HashMap<>();
		
		initialize();
	}
	
	private void initialize () {
		iteratorMap.clear();
		clusterer.cluster();
		clusterMap = clusterer.getClusterMap();
		
		for (Integer i : clusterMap.keySet()) {
			Iterator<ClusterableObject> it = clusterMap.get(i).iterator();
			iteratorMap.put(i, it);
		}
	}
	
	@Override
	public PathObject serveObject() {		
		
		Iterator<ClusterableObject> it;
		
		int attempts = 0;
		while ( !(it = iteratorMap.get(currentCluster)).hasNext() && attempts < iteratorMap.size()) {
			currentCluster++;
			if (currentCluster >= clusterCount)
				currentCluster = 0;
			attempts++;
		}
		
		if (attempts >= iteratorMap.size()) {
			logger.info("End of training samples: reclustering");
			reset();
			return serveObject();
		}
		
		// Increment the current cluster
		currentCluster++;
		if (currentCluster >= clusterCount)
			currentCluster = 0;
		
		return it.next().getPathObject();
	}

	@Override
	public String getName() {
		return "Clustering";
	}

	@Override
	protected void reset() {
		initialize();
	}
	
}