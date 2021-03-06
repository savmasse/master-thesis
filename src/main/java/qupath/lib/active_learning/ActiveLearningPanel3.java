package qupath.lib.active_learning;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.print.attribute.standard.DialogTypeSelection;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.apache.commons.math3.ml.clustering.Clusterable;
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer;
import org.deeplearning4j.plot.Tsne;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dimensionalityreduction.PCA;
import org.nd4j.linalg.factory.Nd4j;
import org.opencv.features2d.Features2d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.input.*;
import javafx.scene.effect.*;
import javafx.scene.shape.*;
import javafx.scene.layout.*;
import javafx.scene.control.*;
import javafx.concurrent.*;
import javafx.application.*;
import javafx.stage.*;

import javafx.geometry.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import qupath.lib.classification.FeatureSelectionPanel;
import qupath.lib.classifiers.PathClassificationLabellingHelper;
import qupath.lib.geom.Point2;
import qupath.lib.gui.ImageDataChangeListener;
import qupath.lib.gui.ImageDataWrapper;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.QuPathGUI.GUIActions;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.gui.helpers.PanelToolsFX;
import qupath.lib.gui.helpers.dialogs.ParameterPanelFX;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.plugins.parameters.ParameterChangeListener;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.roi.PointsROI;

public class ActiveLearningPanel3 implements PathObjectHierarchyListener, ImageDataChangeListener<BufferedImage>, ParameterChangeListener {
	
	private static final Logger logger = LoggerFactory.getLogger(ActiveLearningPanel.class);
	
	private GridPane pane;
	private SplitPane splitPane;
	private ParameterPanelFX classPanel, clusterFX;
	private ScatterPlotPane scatterPane;
	
	private FeatureSelectionPanel featureSelectionPanel;
	private List <String> selectedFeatures;
	
	private Button 	btnChangeClass, 
					btnConfirmClass, 
					btnRecluster, 
					btnFocus, 
					btnShowPlot,
					btnFeatureSelect;
	
	private Label lblSample, lblCluster, lblClass;
	
	private QuPathGUI qupath;
	private PathObjectHierarchy hierarchy;
	private ImageData<BufferedImage> imageData;
	
	private ALPathObjectServer pathObjectServer;
	private PathObject currentObject;
	private Map <PathClass, PathAnnotationObject> annotationMap;
	
	private QuPathViewer pathViewer;
	
	public ActiveLearningPanel3 (final QuPathGUI qupath) {
		
		// -- Set the listeners and image data -- //
		this.qupath = qupath;
		if (qupath == null)
			this.qupath = QuPathGUI.getInstance();
		
		this.qupath.addImageDataChangeListener(this);
		this.imageData = qupath.getImageData();
		
		if (imageData != null) {
			hierarchy = imageData.getHierarchy();
			hierarchy.addPathObjectListener(this);
		}
		pathObjectServer = new ALPathObjectServer(hierarchy);
		pathObjectServer.clusterPathObjects();
		annotationMap = new HashMap<>();
		selectedFeatures = new ArrayList<>();		
		
		// -- Create a new pane -- //
		pane = new GridPane();
		pane.setMinSize(350, 300);
		pane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

		// -- Get access to the main Viewer -- //
		pathViewer = qupath.getViewer();
		pathViewer.centerImage();
		pathViewer.setMagnification(50);
		
		// -- Create buttons -- //
		btnChangeClass = new Button("Change class");
		btnChangeClass.setTooltip(new Tooltip("Change class to the class selected in the list.") );
		btnChangeClass.setOnAction(e -> {
			clickChangeClass();
		});
		btnConfirmClass = new Button ("Confirm");
		btnConfirmClass.setTooltip(new Tooltip ("Keep the current classification.") );
		btnConfirmClass.setOnAction(e -> {
			clickConfirmClass();
		});
		btnFocus = new Button("Focus");
		btnFocus.setTooltip(new Tooltip ("Focus viewer on current object."));
		btnFocus.setOnAction(e -> {
			clickFocus();
		});
		btnRecluster = new Button("Cluster");
		btnRecluster.setTooltip(new Tooltip ("Recluster the data."));
		btnRecluster.setOnAction(e -> {
			clickRecluster();
		});
		btnShowPlot = new Button();
		btnShowPlot.setText("Show plot");
		btnShowPlot.setTooltip(new Tooltip ("Show a scatter plot of the clustered data."));
		btnShowPlot.setOnAction(e -> {
			clickShowPlot();
		});
		
		// -- Handle feature panel -- //
		btnFeatureSelect = new Button();
		btnFeatureSelect.setText("Select features");
		btnFeatureSelect.setTooltip(new Tooltip ("Select the features you want to use in the clustering."));
		btnFeatureSelect.setOnAction(e -> {
			clickFeatureSelect();
		});
		featureSelectionPanel = new FeatureSelectionPanel(qupath);
		
		GridPane classButtonPanel = PanelToolsFX.createColumnGridControls(
				btnConfirmClass,
				btnChangeClass
				);
	
		classButtonPanel.setPadding(new Insets(10, 0, 10, 0));
		classButtonPanel.setMinSize(350, 70);
		classButtonPanel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		
		GridPane miscButtonPanel = PanelToolsFX.createRowGridControls(
				btnFocus,
				btnRecluster,
				btnShowPlot
				);
		miscButtonPanel.setPadding(new Insets(10, 0, 10, 0));
		miscButtonPanel.setMinSize(350, 100);
		miscButtonPanel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		
		// -- Create labels -- //
		lblClass = new Label("Class: (Probability)");
		lblCluster = new Label("Cluster: ");
		lblSample = new Label("Sample: ");
		lblClass.setPadding(new Insets(5, 20, 0, 0));
		lblCluster.setPadding(new Insets(5, 20, 0, 0));
		lblSample.setPadding(new Insets(5, 20, 0, 0));

		GridPane labelPanel = PanelToolsFX.createRowGrid(
				lblClass,
				lblCluster
				);
		labelPanel.setPadding(new Insets(5, 10, 5, 0));
		labelPanel.setMinSize(350, 50);
		labelPanel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		
		// -- Create list of classes -- //
		List <PathClass> pathClassChoices = qupath.getAvailablePathClasses();
		ParameterList classParameterList = new ParameterList()
				.addChoiceParameter("classChoice", "Choose class", pathClassChoices.get(1), pathClassChoices)
				.addBooleanParameter("addTrain", "Add to training set", true, "Add the newly classified item to the training set. Also added when the current class is kept.")
				.addIntParameter("clusterCount", "Number of clusters", 1, "", 1, 5, "Clustering the samples will ensure that different types of outliers are served");
			
		classPanel = new ParameterPanelFX(classParameterList);
		classPanel.addParameterChangeListener(this);
		Pane classChoicePane = classPanel.getPane();
				
		// -- Add everything to the Panel -- //
		pane.add(makeToolbarButtons(), 0, 0);
		pane.add(labelPanel, 0, 1);
		pane.add(classButtonPanel, 0, 2);
		pane.add(classChoicePane, 0, 3);
		pane.add(miscButtonPanel, 0, 5);
		
		// -- Create the split pane -- //
		splitPane = new SplitPane();
		scatterPane = new ScatterPlotPane();
		splitPane.getItems().add(pane);
		//splitPane.getItems().add(sPane.getPane());
		
		// -- Load the first object -- //
		currentObject = setupNext();
	}
	
	private void clickFeatureSelect () {
//		Stage dialog = new Stage();
//		dialog.initOwner(qupath.getStage());
//		dialog.setTitle("Select features");
//		
//		GridPane featurePanel = new GridPane();
//		featurePanel.add(featureSelectionPanel.getPanel(), 0, 0);
//		
//		Button okButton = new Button("OK");
//		okButton.setOnAction(e -> {
//			dialog.close();
//			logger.info("" + featureSelectionPanel.getSelectedFeatures().get(0));
//		});
//		
//		featurePanel.add(okButton, 0, 1);
//		
//		BorderPane bp = new BorderPane(featurePanel);
//		
//		Scene scene = new Scene (bp);
//		dialog.setScene(scene);
//		dialog.show();
		
		Pane featurePane = featureSelectionPanel.getPanel();
		featurePane.setMinSize(500, 200);
		DisplayHelpers.showMessageDialog("Select features", featurePane);
		updateSelectedFeatures();
	}
	
	/**
	 * Update the selected features to the featues that are curently selected in the feature 
	 * selection panel.
	 */
	private void updateSelectedFeatures() {
		selectedFeatures = featureSelectionPanel.getSelectedFeatures();
		pathObjectServer.setSelectedFeatures(selectedFeatures);
		logger.info(selectedFeatures.toString());
	}
	
	
	
	/**
	 * Handle the change class button click.
	 */
	private void clickChangeClass () {
		
		// Serve the next PathObject and set all UI 
		PathObject next = setupNext();
		
		// Set the class of the currentObject
		currentObject.setPathClass( (PathClass) classPanel.getParameters().getChoiceParameterValue("classChoice") , 1.0); 
		
		// Add current object to training set if required
		if (classPanel.getParameters().getBooleanParameterValue("addTrain"))
			addTotTraining(currentObject);
		
		// Update currentObject
		currentObject = next;
		
	}
	
	/**
	 * Handle the confirm class button click.
	 */
	private void clickConfirmClass () {
		
		// Set probability to 1 so item goes to back of queue
		currentObject.setPathClass(currentObject.getPathClass(), 1.0);
		
		// Serve the next PathObject and set all UI 
		PathObject next = setupNext();
		
		// Add to training set if required
		if (classPanel.getParameters().getBooleanParameterValue("addTrain"))
			addTotTraining(currentObject);
		
		// Update currentObject
		currentObject = next;
	}
	
	/**
	 * The data is reclustered when the user clicks the recluster button
	 */
	private void clickRecluster () {
		clusterData();
	}
	
	/**
	 * Restore the focus of the viewer to the current object
	 */
	private void clickFocus () {
		
		// Center viewer around current object
		pathViewer.setCenterPixelLocation(currentObject.getROI().getCentroidX(), currentObject.getROI().getCentroidY());
		// Select the current object
		hierarchy.getSelectionModel().setSelectedObject(currentObject);
		
		PathClassificationLabellingHelper.getAnnotationsForClass(hierarchy, currentObject.getPathClass()).add(currentObject);
		hierarchy.fireObjectClassificationsChangedEvent(this, Collections.singleton(currentObject));
	}
	
	private void clickShowPlot () {
		
		// Show plot
		if (splitPane.getItems().size() < 2) {
			splitPane.getItems().add(scatterPane.getPane());
			btnShowPlot.setText("Hide plot");
			
			// Update the graph when opened
			scatterPane.processClusterData(pathObjectServer.getClusteredDataMap());
		}
		else { // Hide the plot
			splitPane.getItems().remove(scatterPane.getPane());
			btnShowPlot.setText("Show plot");
		}
		
	}
	
	/**
	 * Serve the next object and update the UI to match this new object.
	 * 
	 * @return PathObject
	 */
	private PathObject setupNext () {
		
		PathObject next = pathObjectServer.serveNext();		
		
		pathViewer.setCenterPixelLocation(next.getROI().getCentroidX(), next.getROI().getCentroidY());
		// Repaint the image after a change
		pathViewer.repaintEntireImage();
		// Select new object
		hierarchy.getSelectionModel().setSelectedObject(next);
		
		// Set the current class label
//		lblClass.setText("Class: " + next.getPathClass());
//		if (next.getClassProbability() != Double.NaN) {
//			double rounded = (int) (next.getClassProbability() * 1000) / 1000.0;
//			lblClass.setText( lblClass.getText() + " (" + rounded + ")");
//		}
		
		// Set the sample and cluster label
		updateLabels(next);
		
		return next;
	}
	
	public void closePanel() {
		
		// Stop the listeners
		hierarchy.removePathObjectListener(this);
		classPanel.removeParameterChangeListener(this);
	}
	
	private void updateLabels (PathObject next) {
		lblClass.setText("Class: " + next.getPathClass());
		if (!Double.isNaN(next.getClassProbability())) {
			double rounded = (int) (next.getClassProbability() * 1000) / 1000.0;
			lblClass.setText( lblClass.getText() + " (" + rounded + ")");
		}
		lblCluster.setText("Cluster: " + (pathObjectServer.getCurrentCluster()+1) + " of " + pathObjectServer.getClusterCount());
	}
	
	private ToolBar makeToolbarButtons() {
		if (qupath == null)
			return null;
		
		ToolBar toolbar = new ToolBar();
		toolbar.getItems().addAll(
				qupath.getActionToggleButton(GUIActions.SHOW_ANNOTATIONS, true),
				qupath.getActionToggleButton(GUIActions.SHOW_OBJECTS, true),
				qupath.getActionToggleButton(GUIActions.FILL_OBJECTS, true),
				qupath.getActionToggleButton(GUIActions.SHOW_GRID, true),
				new Separator(Orientation.VERTICAL),
				qupath.getActionToggleButton(GUIActions.BRIGHTNESS_CONTRAST, true),
				new Separator(Orientation.VERTICAL),
				btnFeatureSelect
				);
		return toolbar;
	}
	
	/**
	 * Add a PathObject to the training set. The object is added to a point annotation 
	 * after first checking whether its not already in another Active Learning 
	 * point annotation.
	 * 
	 * @param p: {@link PathObject} to add to the training set.
	 */
	private void addTotTraining (PathObject p) {
		
		List <PathObject> annotationList = new ArrayList<>();
		PointsROI point = new PointsROI(p.getROI().getCentroidX(), p.getROI().getCentroidY());
		PathAnnotationObject annotation = null;
		
		// Check if annotation already exists
		if (!annotationMap.containsKey(p.getPathClass())) {
			
			// Check if an annotation already exists from a previous active learning session
			annotationList = hierarchy.getDescendantObjects(hierarchy.getRootObject(), annotationList, PathAnnotationObject.class);
			for (PathObject a : annotationList) {
				if (! (a instanceof PathAnnotationObject) )
					continue;
				
				if (a.getDisplayedName().equals("Active Learning") && a.getPathClass().equals(p.getPathClass())) {
					annotation = (PathAnnotationObject) a;
					logger.info("Discovered previous active learning annotation.");
//							hierarchy.removeObject(annotation, true);
				}
			}
			
			// If we have found a previous annotation, then continue with this one; otherwise make a new one
			if (annotation == null) {
				annotation = new PathAnnotationObject();
				annotation.setName("Active Learning");
				annotation.setPathClass(p.getPathClass());
				annotation.setROI(point);
				
				// Add the new annotation to the hierarchy
				hierarchy.addPathObject(annotation, true);

			}
			
			// Check if the point we want to add is already present in another annotation
			removeDuplicate(p, point);

			// Add a new pathObject as a point
			//PathObject temp = new PathDetectionObject(point);
			//temp.setPathClass(p.getPathClass());
			//annotation.addPathObject(temp);

			// Add to the map
			annotationMap.put(p.getPathClass(), annotation);
		}
		else {
			
			// Check if the point we want to add is already present in another annotation
			removeDuplicate(p, point);
			
			// We already have an annotation in the map
			annotation = annotationMap.get(p.getPathClass());
			
			// Set the ROI of the annotation
			List <Point2> points = new ArrayList<>();
			points.add(point.getPointList().get(0));
			points.addAll(annotation.getROI().getPolygonPoints());
			
			annotation.setROI( new PointsROI (points) );	
			
		}

		// Force an update; but turn local listener off so we don't constantly recluster
		hierarchy.removePathObjectListener(this);
		hierarchy.fireObjectClassificationsChangedEvent(this, Collections.singleton(annotation));
		hierarchy.addPathObjectListener(this); // Turn back on
		
		// Update the map
		annotationMap.put(p.getPathClass(), annotation);
	}
	
	private void removeDuplicate (PathObject p, PointsROI point) {
		
		Point2 newPoint = new Point2(point.getCentroidX(), point.getCentroidY());
		PathObject toRemove = null;
		PathAnnotationObject tempAnnot = null;
		
		// Go through the current annotations and remove the new point if its already in there
		for (PathAnnotationObject a : annotationMap.values()) {
			
			if (a.equals(annotationMap.get(p.getPathClass())))
				continue;
			
			// Now find the corresponding pathObject to remove
			for (PathObject pathObject : a.getChildObjects()) {
				// Check if the centroid of the point in the annotation is the same as the new point we want to add
				if (pathObject.getROI().getCentroidX() == point.getCentroidX() && pathObject.getROI().getCentroidY() == point.getCentroidY()) {
					toRemove = pathObject;
					tempAnnot = a;
					break; // Assume there's only one duplicate, we've found it so we can leave the loop
				}
			}
		}
		// Remove and update the ROI
		if (tempAnnot != null && toRemove != null) {

			// Remove the pathObject
			tempAnnot.removePathObject(toRemove);
			
			// Update the annotion ROI
			List <Point2> points = new ArrayList<>(tempAnnot.getROI().getPolygonPoints());
//			logger.info("Size before: " + points.size());
			points.remove(newPoint);
//			logger.info("Size after: " + points.size());
			tempAnnot.setROI(new PointsROI(points));
			
			// Update the map
			annotationMap.put(tempAnnot.getPathClass(), tempAnnot);
			
//			logger.info("Removed pathObject from the annotation " + tempAnnot.getPathClass());
		}
	}
	
	public SplitPane getPane () {
		//return pane;
		return splitPane;
	}
	
	private void updateViewer () {
		pathViewer.setImageData(imageData);
		pathViewer.repaint();
	}
		
	@Override
	public void imageDataChanged(ImageDataWrapper<BufferedImage> source, ImageData<BufferedImage> imageDataOld,
			ImageData<BufferedImage> imageDataNew) {
		
		this.setImageData(imageDataOld, imageDataNew);
		this.updateViewer();		
	}
	
	public void setImageData(final ImageData<BufferedImage> imageDataOld, final ImageData<BufferedImage> imageDataNew) {
		
		if (imageDataOld == imageDataNew)
			return;
		if (imageDataOld != null)
			imageDataOld.getHierarchy().removePathObjectListener(this);
		if (imageDataNew != null)
			imageDataNew.getHierarchy().addPathObjectListener(this);
		
		this.imageData = imageDataNew;
	}
	
	public void clusterData () {
		
		// Update the list of objects in the server
		pathObjectServer.updatePathObjects(hierarchy);
		
		// Try clustering the data inside a task
		ProgressBar progressBar = new ProgressBar();
		Stage dialog = new Stage();
		dialog.initOwner(qupath.getStage());
		dialog.initModality(Modality.APPLICATION_MODAL);
		ClusterTask clusterTask = new ClusterTask(dialog, pane, scatterPane, pathObjectServer);
		progressBar.progressProperty().bind(clusterTask.progressProperty());
		
		BorderPane paneDialog = new BorderPane();
		paneDialog.setPadding(new Insets(5,5,5,5));
		paneDialog.setMaxWidth(Double.MAX_VALUE);
		paneDialog.setMinWidth(300);
		paneDialog.setTop(progressBar);
		
		dialog.setTitle("Reducing dimensions and clustering...");
		dialog.setScene(new Scene(paneDialog));
		dialog.sizeToScene();
		
		qupath.createSingleThreadExecutor(this).submit(clusterTask);
		qupath.createSingleThreadExecutor(this).submit(clusterTask);
		
		pane.setCursor(Cursor.WAIT);
		dialog.show();
	}
	
	/**
	 * Handle the hierarchy change
	 */
	@Override
	public void hierarchyChanged(PathObjectHierarchyEvent event) {
		this.hierarchy = event.getHierarchy();
		
		// Update the object list and recluster
		int clusterCount = classPanel.getParameters().getIntParameterValue("clusterCount");
		
		// Update the objects but don't cluster
		pathObjectServer.updatePathObjects(hierarchy);
		
		// Cluster the data
		// clusterData();
	}

	@Override
	public void parameterChanged(ParameterList parameterList, String key, boolean isAdjusting) {
		
		// Change the clusterCount when the slider is adjusted
		if (key.equals("clusterCount")) {
			int clusterCount = classPanel.getParameters().getIntParameterValue(key);
			pathObjectServer.setClusterCount(clusterCount);
			//pathObjectServer.clusterPathObjects();
		}
	}
	
	
	/**
	 * 
	 * <p>
	 * Helper class for the serving of PathObjects to be displayed by the Viewer in 
	 * the Active Learning panel. This class will perform a clustering on the classified 
	 * PathObjects in the current hierarchy and alternate between clusters when
	 * serving objects.
	 * </p><p>
	 * This clustering is based on the random sample server in the standard QuPath release.
	 * </p>
	 * @author Sam Vanmassenhove
	 *
	 */
	static class ALPathObjectServer {
		
		private final Logger logger = LoggerFactory.getLogger(ALPathObjectServer.class);
		
		private List <PathObject> pathObjects;
		private Map <Integer, List <PathObject>> clusteredMap;
		private Map <Integer, List <ClusterableObject>> clusteredDataMap;
		private Map <Integer, Iterator<PathObject>> itMap;
		
		private Integer currentCluster = Integer.valueOf(0);
		private PathObject currentObject;
		private int clusterCount = 1;
		private List<String> selectedFeatures;
		
		public ALPathObjectServer (PathObjectHierarchy hierarchy) {
			
			// Init 
			pathObjects = new ArrayList<>();
			clusteredMap = new HashMap<>();
			clusteredDataMap = new HashMap<>();
			itMap = new HashMap<>();
			
			// Load initial samples
			updatePathObjects(hierarchy);
			
			// If no features were selected, assume we want to use shape
			if (selectedFeatures == null)
				selectedFeatures = Arrays.asList("Nucleus: Area", "Nucleus: Perimeter", "Nucleus: Eccentricity");
		} 
		
		public ALPathObjectServer (PathObjectHierarchy hierarchy, List<String> selectedFeatures) {
			this(hierarchy);
			this.selectedFeatures = selectedFeatures;
		}
		
		private void updatePathObjects (PathObjectHierarchy hierarchy) {
			
			// Create a list of all objects in the current hierarchy
			List <PathObject> completeList = hierarchy.getFlattenedObjectList(null);
			List <PathObject> tempList = new ArrayList<>();

			for (PathObject p : completeList) {
				
				// Filter out annotations; we only want detections in the list
				if (p instanceof PathAnnotationObject) 
					continue;
				
				// Filter out unclassified objects
				if (p.getPathClass() == null || p.getPathClass().equals(PathClassFactory.getPathClass("Image")) )
					continue;
				
				// Filter out PointROIs
				if (p.getROI() instanceof PointsROI)
					continue;
				
				// Add to the actual list
				tempList.add(p);
			}
			
			// Do some subsampling in the tempList to get a workable amount of samples.
			// pathObjects = subSample(tempList);
			pathObjects = tempList;
		}
		
		private List <PathObject> subSample (List <PathObject> pathObjects) {

			if (pathObjects.size() <= 500) 
				return pathObjects;
			
			Random rand = new Random (System.currentTimeMillis());
			List <Integer> previousNumbers = new ArrayList<>();
			List <PathObject> res = new ArrayList<>();
			
			// Get 500 random samples
			for (int i = 0; i < 500; i++) {
				
				int number = 0;
				while ( previousNumbers.contains( number = Integer.valueOf(rand.nextInt(pathObjects.size())) ) ) {
					previousNumbers.add(number);
				}
				
				res.add(pathObjects.get(number));
			}
			
			return res;
		}
		
		public List <PathObject> getObjects () {
			return pathObjects;
		}
		
		public void setClusterCount (final int clusterCount) {
			this.clusterCount = clusterCount;
		}
		
		public int getClusterCount () {
			return clusterCount;
		}
		
		public int getCurrentCluster () {
			return currentCluster;
		}
		
		public Map<Integer, List<ClusterableObject>> getClusteredDataMap () {
			return clusteredDataMap;
		}
		
		public void clusterPathObjects () {
			
			// Check if any data available
			if (pathObjects.isEmpty() || pathObjects.size() == 1) {
				return;
			}
			
			// Clear the current clusters
			clusteredDataMap.clear();
			clusteredMap.clear();
			itMap.clear();
			
			if (clusterCount < 2) {
				
				// Just sort the objects by probability
				Collections.sort(pathObjects, new Comparator<PathObject>() {
				    @Override
				    public int compare(PathObject lhs, PathObject rhs) {
				        // -1 - less than, 1 - greater than, 0 - equal, all inversed for descending
				        return Double.compare(lhs.getClassProbability(), rhs.getClassProbability()); // Use double compare to safely handle NaN and Infinity
				    }
				});
								
				// Set the map
				clusteredMap.put(0, pathObjects);
				itMap.put(0, pathObjects.iterator());
				
				logger.info("Requested cluster count is zero. The list was sorted but not clustered.");
				
				return;
			}
			
			// Setup the clusterer: currently uses KMeans
			KMeansPlusPlusClusterer<ClusterableObject> km = new KMeansPlusPlusClusterer<>(clusterCount);
			List<ClusterableObject> clusterableObjects = new ArrayList<>();
			
			// Create a complete feature matrix of the PathObjects
			Nd4j.ENFORCE_NUMERICAL_STABILITY = true; // These three lines fix a lot of errors; no idea why they occur so also not a clue why this fixes it...
			Nd4j.setDataType(org.nd4j.linalg.api.buffer.DataBuffer.Type.DOUBLE); 
			Nd4j.factory().setDType(org.nd4j.linalg.api.buffer.DataBuffer.Type.DOUBLE); 	
			
			INDArray matrix = createFeatureMatrix();
			
			// Perform dimensionality reduction: either tsne or pca
			INDArray reduced;
			reduced = PCA.pca(matrix, 2, true);
			
			// Set the points of the Clusterable Objects
			for (int i = 0; i < pathObjects.size(); i++) {
				PathObject p = pathObjects.get(i);
				clusterableObjects.add(new ClusterableObject(p, reduced.getRow(i).dup().data().asDouble()));
			}
			logger.info("Added " + clusterableObjects.size() + " to the clusterable list.");
			
			List<CentroidCluster<ClusterableObject>> results = km.cluster(clusterableObjects);
			logger.info("Clustered objects");
			
			int i = 0;
			for (CentroidCluster<ClusterableObject> cenCluster : results) {
				Integer label = Integer.valueOf(i);
				List <PathObject> objects = new  ArrayList<>();
				for (ClusterableObject c : cenCluster.getPoints()) {
					objects.add(c.getPathObject());
				}
				clusteredMap.put(label, objects);
				clusteredDataMap.put(label, cenCluster.getPoints()); // TODO Consider only using this map instead of this one AND the clusteredMap
				i++;
			}
			
			// Now sort each cluster according to probability
			for (List<PathObject> pList : clusteredMap.values()) {
				//List <PathObject> newList = new ArrayList<>();
				
				Collections.sort(pList, new Comparator<PathObject>() {
				    @Override
				    public int compare(PathObject lhs, PathObject rhs) {
				        // -1 - less than, 1 - greater than, 0 - equal, all inversed for descending
				        return Double.compare(lhs.getClassProbability(), rhs.getClassProbability()); // Use double compare to safely handle NaN and Infinity
				    }
				});
			}
			
			// Create a new iterator for each cluster
			for (Integer k : clusteredMap.keySet()) {
				itMap.put(k, clusteredMap.get(k).iterator());
			}
			
			// Let user know what happened
			for (Integer k : clusteredMap.keySet()) {
				logger.info(" Cluster " + k + ": " + clusteredMap.get(k).size() + " items.");
			}
		}
		
		private INDArray createFeatureMatrix () {
			
			// Create a list
			List <INDArray> arrayList = new ArrayList<>();
			INDArray ind = null;
			
			// Go through the objects
			for (PathObject p : pathObjects) {
				 
				// Read all meaningful measurements
				List <Double> temp = new ArrayList<>();
				for (int i = 0; i < p.getMeasurementList().size(); i++) {

					if (selectedFeatures.contains( p.getMeasurementList().getMeasurementName(i) ) ) {
						if (p.getMeasurementList().getMeasurementValue(i) != Double.NaN)
							temp.add (p.getMeasurementList().getMeasurementValue(i));
						else 
							temp.add (Double.valueOf(0));
					}
				}
				
				Double [] d = new Double [temp.size()];
				temp.toArray(d);
				ind = Nd4j.create(ArrayUtils.toPrimitive(d));
				arrayList.add(ind);
			}
			
			// Create a matrix from the array
			INDArray matrix = Nd4j.create(arrayList, new int [] {arrayList.size(), arrayList.get(0).length()});
			
			// Return the matrix
			return matrix;
		}
		
		public PathObject serveNext () {
			
			// Serve from the current cluster
			Iterator<PathObject> it;
			
			// If the iterator is at its end, go to the next cluster
			int attempts = 0;			
			while ( !(it = itMap.get(currentCluster)).hasNext() && attempts < itMap.size()) {
				
				logger.info("Arrived at end of cluster " + currentCluster);
				currentCluster++;
				if (currentCluster >= clusterCount)
					currentCluster = 0;
				
				attempts++;
			}
			
			// If all clusters are empty then serve the current object
			PathObject servedObject;
			if (attempts >= itMap.size()) {
				logger.info("All clusters are empty. Recluster or add more detections to continue.");
				return currentObject;
			}
			
			// Else continue serving objects from the next cluster
			servedObject = it.next();

			logger.info("Served PathObject from cluster n° " + currentCluster + ".");
			
			// Increment the current cluster
			currentCluster++;
			if (currentCluster >= clusterCount)
				currentCluster = 0;
			
			currentObject = servedObject;
			
			return servedObject;
		}
		
		public void setSelectedFeatures (List<String> selectedFeatures) { 
			this.selectedFeatures = selectedFeatures;
		}
		
	}
	
	/**
	 * @author Pete Bankhead
	 */
	static class ClusterableObject implements Clusterable {
		
		private PathObject pathObject;
		private double[] point; // Features for the clustering
		
		public ClusterableObject(final PathObject pathObject, final List<String> measurements) {
			this.pathObject = pathObject;
			point = new double[measurements.size()];
			MeasurementList ml = pathObject.getMeasurementList();
			for (int i = 0; i < measurements.size(); i++) {
				point[i] = ml.getMeasurementValue(measurements.get(i));
			}
		}
		
		public ClusterableObject(final PathObject pathObject, final double[] features) {
			this.pathObject = pathObject;
			point = features.clone();
		}
		
		public PathObject getPathObject() {
			return pathObject;
		}

		@Override
		public double[] getPoint() {
			return point;
		}
	
	}
	
	/**
	 * Helper class to create the panel for the scatter plot to show the clustered data
	 * 
	 * @author Sam Vanmassenhove
	 *
	 */
	static class ScatterPlotPane {
		
		private GridPane pane = new GridPane();
        private NumberAxis xAxis;
        private NumberAxis yAxis; 
        private ScatterChart<Number,Number> sc;
        
		public ScatterPlotPane() {
			
			// Initiate the plot and axes
			initScatterPlot(10, 10, 1, 1);
			
	        XYChart.Series<Number, Number> series1 = new XYChart.Series<>();
	        series1.setName("Series 1");
	        List <XYChart.Data<Number, Number>> data = new ArrayList<>();
	        for (int i = 0; i < 10; i++) {
	        	data.add(new XYChart.Data<Number, Number>(i,i));
	        }
	        series1.getData().addAll(data);
	        
	        // Add the series to the plot
	        addSeries(series1);
	        
	        pane.add(sc, 0, 0);
		}
		
		private void initScatterPlot (int xMax, int yMax, int xStep, int yStep) {
			
	        // Create axes
	        xAxis = new NumberAxis(0, xMax, xStep);
	        yAxis = new NumberAxis(0, yMax, yStep);
	        
			// Create the chart
			sc = new ScatterChart<Number,Number>(xAxis,yAxis);
	        sc.setPrefSize(500, 500);
	        sc.setMinSize(500, 400);
	        
	        // Set titles
	        xAxis.setLabel("x-axis");                
	        yAxis.setLabel("y-axis");
	        sc.setTitle("Clustering plot");
		}
		
		private void processClusterData (Map <Integer, List <ClusterableObject>> clusterMap) {
			
			// First clear all previous data
			sc.getData().clear();
						
			// Go through the clusters and create a series for each cluster
			for (int i = 0; i < clusterMap.size(); i++) {
				List <ClusterableObject> clusterableObjects = clusterMap.get(i);
				
				// Create a new series
				XYChart.Series<Number, Number> series = new Series<>();
				series.setName("Cluster " + (i+1));
				
				// Fill series with data
				for (ClusterableObject c : clusterableObjects) {
					
					XYChart.Data<Number, Number> data = new XYChart.Data(c.getPoint()[0], c.getPoint()[1], c);
			        Region plotpoint = new Region();
			        plotpoint.setShape(new Circle(6.0));
			        data.setNode(plotpoint);
			        
					series.getData().add(data);
				}
				
				// Add series to the plot
				addSeries(series);
			}
			
	        // Handle mouse click events
	        for (Series <Number, Number> series: sc.getData()){
	            for (XYChart.Data<Number, Number> item: series.getData()){
	                item.getNode().setOnMousePressed((MouseEvent event) -> {
//	                    
	                	// Select the clicked item in the Viewer
	                    PathObject p = ((ClusterableObject) item.getExtraValue()).getPathObject();
	                    QuPathGUI.getInstance().getImageData().getHierarchy().getSelectionModel().setSelectedObject(p);
	                });
	            }
	        }
			
			// Update the axes: set the range automatically
			xAxis.autoRangingProperty().set(true);
			yAxis.autoRangingProperty().set(true);
		}
		
		public void addSeries (Series <Number, Number> series) {
	        sc.getData().addAll(series);
		}
		
		public GridPane getPane () {
			return pane;
		}
	}
	
	class ClusterTask extends Task<Void> {

		private Stage dialog;
		private int nIter, perplexity;
		private boolean tsne;
		private GridPane parentPane;
		private ScatterPlotPane scatterPane;
		private ALPathObjectServer pathObjectServer;
		
		public ClusterTask (Stage dialog,  GridPane parentPane, ScatterPlotPane scatterPane, ALPathObjectServer pathObjectServer) {
			this.dialog = dialog;
			this.parentPane = parentPane;
			this.scatterPane = scatterPane;
			this.pathObjectServer = pathObjectServer;
		}
		
		@Override
		protected Void call() throws Exception {

			logger.info("Started clustering task...");
			
			// Cluster the data
			pathObjectServer.clusterPathObjects();
			
			Platform.runLater(new Runnable() {
				  @Override public void run() {

					  if (splitPane.getItems().size() > 1)
							scatterPane.processClusterData(pathObjectServer.getClusteredDataMap());
					  
					  // Update the labels
					  updateLabels(currentObject);
				  }
				});			
			
			return null;
		}
		
		@Override
		public void done() {
			if (Platform.isFxApplicationThread()) {
				dialog.close();
				
				parentPane.setCursor(Cursor.DEFAULT);
				logger.info("Dimensionality reduction and clustering completed");
			}
			else
				Platform.runLater(() -> done());
		}
		
	}
	
}