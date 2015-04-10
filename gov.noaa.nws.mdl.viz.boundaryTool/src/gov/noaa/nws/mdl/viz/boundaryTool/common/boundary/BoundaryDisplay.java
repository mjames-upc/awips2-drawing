package gov.noaa.nws.mdl.viz.boundaryTool.common.boundary;

import gov.noaa.nws.mdl.viz.boundaryTool.common.boundary.BoundaryState.BoundaryPolyLine;
import gov.noaa.nws.mdl.viz.boundaryTool.common.boundary.BoundaryState.UserAction;

import java.awt.geom.Point2D;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TimeZone;

import org.eclipse.swt.graphics.RGB;
import org.geotools.referencing.GeodeticCalculator;

import com.raytheon.uf.common.status.IUFStatusHandler;
import com.raytheon.uf.common.status.UFStatus;
import com.raytheon.uf.common.status.UFStatus.Priority;
import com.raytheon.uf.common.time.DataTime;
import com.raytheon.uf.viz.core.DrawableCircle;
import com.raytheon.uf.viz.core.DrawableString;
import com.raytheon.uf.viz.core.IGraphicsTarget;
import com.raytheon.uf.viz.core.IGraphicsTarget.HorizontalAlignment;
import com.raytheon.uf.viz.core.IGraphicsTarget.LineStyle;
import com.raytheon.uf.viz.core.IGraphicsTarget.VerticalAlignment;
import com.raytheon.uf.viz.core.drawables.IRenderable;
import com.raytheon.uf.viz.core.drawables.PaintProperties;
import com.raytheon.uf.viz.core.exception.VizException;
import com.raytheon.uf.viz.core.map.IMapDescriptor;
import com.raytheon.uf.viz.core.map.MapDescriptor;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;

/**
 * Working version of BoundaryDisplay, correctly draws and keeps track of
 * coordinates, speed, and angle.
 * 
 * <pre>
 * 
 * @author Mamoudou Ba
 * @version 1.0
 * 
 * </pre>
 * 
 * April 2011: Substantially modified from A2 "StormTrackDisplay" class
 */

public class BoundaryDisplay implements IRenderable {
    private static final transient IUFStatusHandler statusHandler = UFStatus
            .getHandler(BoundaryDisplay.class);

    private final GeometryFactory gf = new GeometryFactory();

    public final RGB LIGHT_GRAY = new RGB(200, 200, 200);

    private final int editableCircleSize = 50;

    private final int unEditableCircleSize = 90;

    private final String dragTextFormat = "Drag %s to %s";

    // private static final double MAX_DIST = 20000000;

    private final DateFormat timeFormat = new SimpleDateFormat("HH:mm");

    private IMapDescriptor descriptor;

    private BoundaryUIManager manager;

    private int lastFrame = -1;

    public static final double ang = 15.0;

    private DataTime[] currentDisplayedTimes;

    // private Coordinate theAnchorPoint = null;

    /** HashMap to store active boundaries */
    public Map<Integer, LineString> theAnchorLineMap = new HashMap<Integer, LineString>();

    /** HashMap to store active boundaries */
    public Map<Integer, Integer> theAnchorIndexMap = new HashMap<Integer, Integer>();

    private int theAnchorIndex = -1;

    private BoundaryUtil trackUtil;

    // private Boundary boundaryObject;
    boolean timeUpdated = false;

    public BoundaryDisplay(IMapDescriptor descriptor, BoundaryUIManager manager) {
        this.descriptor = descriptor;
        this.timeFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        this.manager = manager;
        trackUtil = this.manager.getTrackUtil();
    }

    public void setDescriptor(MapDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    @Override
    public void paint(IGraphicsTarget target, PaintProperties paintProps)
            throws VizException {
        paint(target, (BoundaryProperties) paintProps);
    }

    private void paint(IGraphicsTarget target, BoundaryProperties paintProps)
            throws VizException {
        BoundaryState currentState = paintProps.getState();

        DataTime[] times = trackUtil.getDataTimes(paintProps.getFramesInfo());
        // short circuit if no data times to paint
        if (times.length == 0) {
            return;
        }
        int currentTimeIndex = this.trackUtil.getCurrentFrame(paintProps
                .getFramesInfo());
        currentState.timeIndex = currentTimeIndex;
        DataTime[] dataTimes = trackUtil.getDataTimes(paintProps
                .getFramesInfo());
        currentState.currentDataTimes = dataTimes;
        int currentFrame = trackUtil
                .getCurrentFrame(paintProps.getFramesInfo());

        currentState.timePoints = currentState.timePointsMap
                .get(currentState.boundaryId);
        // If the state's time points are set and the times don't quite match up
        if (currentState.timePoints != null
                && (currentState.timePoints.length != times.length || !times[currentFrame]
                        .equals(currentState.timePoints[currentFrame].time,
                                true))) {

            int oldPivot = currentState.pivotIndex;
            trackUtil.setPivotIndexes(paintProps.getFramesInfo(), currentState);

            if (currentState.otherPivotIndex == currentState.pivotIndex) {
                if (currentState.displayedPivotIndex == oldPivot) {
                    currentState.pivotIndex = 0;
                } else {
                    currentState.otherPivotIndex = 0;
                }
            }

            if (currentState.displayedPivotIndex == oldPivot) {
                currentState.displayedPivotIndex = currentState.pivotIndex;
            }

            if (currentFrame == currentState.displayedPivotIndex) {
                if (currentState.displayedPivotIndex == currentState.pivotIndex) {
                    currentState.displayedPivotIndex = currentState.otherPivotIndex;
                } else {
                    currentState.displayedPivotIndex = currentState.pivotIndex;
                }
            }

            if (currentState.displayedPivotIndex >= times.length) {
                currentState.displayedPivotIndex = Math.max(0,
                        currentFrame != times.length - 1 ? times.length - 1
                                : times.length - 2);
            }

            currentState.geomChanged = true;
            target.setNeedsRefresh(true);
        } else if (lastFrame != -1 && lastFrame != currentFrame) {
            // This is a newly painted frame, update the display if we need to
            if (currentState.nextPivotIndex != -1) {
                // we acquired a new pivot index while painting the last frame
                currentState.pivotIndex = currentState.nextPivotIndex;
                currentState.otherPivotIndex = currentState.displayedPivotIndex;
                currentState.displayedPivotIndex = currentState.pivotIndex;
                currentState.nextPivotIndex = -1;
            } else if (currentFrame == currentState.displayedPivotIndex) {
                if (currentState.displayedPivotIndex == currentState.pivotIndex) {
                    currentState.displayedPivotIndex = currentState.otherPivotIndex;
                } else {
                    currentState.displayedPivotIndex = currentState.pivotIndex;
                }
            } else if (currentFrame != currentState.displayedPivotIndex) {
                if (currentState.displayedPivotIndex == currentState.otherPivotIndex) {
                    currentState.displayedPivotIndex = currentState.pivotIndex;
                }
            }

            if (currentState.timePoints != null) {

                Coordinate[] anchorCoords = currentState.dragMePointMap.get(
                        currentState.boundaryId).getCoordinates();
                currentState.dragMePoint = new Point[anchorCoords.length];
                for (int k = 0; k < anchorCoords.length; k++) {
                    currentState.dragMePoint[k] = gf
                            .createPoint(anchorCoords[k]);
                }
            }
            currentState.dragMeLine = currentState.boundariesMap
                    .get(currentState.boundaryId);
            if (currentState.dragMeLine != null
                    && currentState.isMovingMap.get(currentState.boundaryId)) {
                currentState.dragMeLine = manager.figureLineFromPoint(
                        currentState.dragMeLine, currentState.dragMePoint);
                currentState.boundariesMap.remove(currentState.boundaryId);
                currentState.boundariesMap.put(currentState.boundaryId,
                        currentState.dragMeLine);
                if (!currentState.isMovingMap.get(currentState.boundaryId)) {
                    currentState.prevBoundary = currentState.dragMeLine;
                }

            }
            currentState.geomChanged = true;
            target.setNeedsRefresh(true);
        }

        switch (currentState.userAction) {
        case READ_ACTIVE_BOUNDARIES: {
            currentState.mode = BoundaryState.Mode.TRACK;
            if (!currentState.boundariesMap.isEmpty()) {
                paintPolyMode(target, paintProps);
            }
            break;
        }

        case EDIT_BOUNDARY: {

            currentState.dragMeLine = currentState.boundariesMap
                    .get(currentState.boundaryId);
            // currentState.mode = Mode.TRACK;
            if (!currentState.boundariesMap.isEmpty()) {
                paintPolyMode(target, paintProps);
            }

            if (!currentState.isMovingMap.get(currentState.boundaryId)) {
                currentState.editedTimeMap.remove(currentState.boundaryId);
                currentState.editedTimeMap.put(currentState.boundaryId,
                        times[currentTimeIndex]);
            }
            break;
        }

        case SAVE: {
            // new thread "saveData" to write the data to the disc
            // when SaveBtn is clicked
            // re-paint polylines after boundary data being saved
            if (!currentState.boundariesMap.isEmpty()) {
                paintPolyMode(target, paintProps);
            }
            currentState.editedLineForMotionComputation = null;
            break;
        }

        case INSERT_BOUNDARY: {
            if (!currentState.boundariesMap.isEmpty()) {
                paintPolyMode(target, paintProps);
            }

            currentState.createTimeMap.remove(currentState.boundaryId);
            currentState.editedTimeMap.remove(currentState.boundaryId);

            currentState.createTimeMap.put(currentState.boundaryId,
                    dataTimes[currentTimeIndex]);
            currentState.editedTimeMap.put(currentState.boundaryId,
                    dataTimes[currentTimeIndex]);

            currentState.userAction = UserAction.EDIT_BOUNDARY;
            currentState.editedLineForMotionComputation = null;
            break;
        }

        case CANCEL_MODIFICATION: {
            if (!currentState.boundariesMap.isEmpty()) {
                paintPolyMode(target, paintProps);
                currentState.dialogObject.setBtnFalseAfterCancelAction();
                currentState.editedLineForMotionComputation = null;
            }
            break;
        }

        case NONE: {
            if (!currentState.boundariesMap.isEmpty()) {
                paintPolyMode(target, paintProps);
                currentState.dialogObject.setBtnFalseAfterCancelAction();
            }
            break;
        }
        }

        lastFrame = currentFrame;
    }

    /**
     * Paints the boundary track display
     * 
     * @param target
     * @param paintProps
     */
    private void paintPolyMode(IGraphicsTarget target,
            BoundaryProperties paintProps) throws VizException {
        BoundaryState currentState = paintProps.getState();

        switch (currentState.mode) {

        case DRAG_ME: {
            paintDragMeLine(target, paintProps);
            paintDragMeText(target, paintProps, currentState.dragMeLine);
            break;
        }

        case TRACK: {
            generateTrackInfo(currentState, paintProps);
            paintDragMeLine(target, paintProps);
            if (trackUtil.getDataTimes(paintProps.getFramesInfo()).length == 1) {
                paintDragMeText(target, paintProps, currentState.dragMeLine);
            }

            // Ama: Jan 11, 2011: Enabling send button when user modifies a
            // stationary boundary line
            if (!currentState.isMovingMap.get(currentState.boundaryId)
                    && currentState.prevBoundary != null) {
                Coordinate[] prevCoords = currentState.prevBoundary
                        .getCoordinates();
                Coordinate[] currentCoords = currentState.dragMeLine
                        .getCoordinates();
                boolean vertexMoved = false;
                boolean lengthChanged = false;
                if (prevCoords.length == currentCoords.length) {
                    for (int i = 0; i < prevCoords.length; i++) {
                        if (prevCoords[i].x != currentCoords[i].x
                                || prevCoords[i].y != currentCoords[i].y) {
                            vertexMoved = true;
                        }

                    }
                } else {
                    lengthChanged = true;
                }

                if (lengthChanged || vertexMoved) {
                    currentState.dialogObject.setBtnTrueAfterCancelAction();
                }
            }

            break;
        }

        // end of switch
        }
        currentState.dragMeGeom = currentState.dragMeLine;
    }

    /**
     * Like paintDragMePoint, this will paint the drag line which is a little
     * different than drawing a line then drawing points. If editable, points
     * will look like drpaintPolyModeag me points but it not, will be smaller
     * filled in circle
     * 
     * @param target
     * @param paintProps
     * @throws VizException
     */
    private void paintDragMeLine(IGraphicsTarget target,
            BoundaryProperties paintProps) throws VizException {
        BoundaryState state = paintProps.getState();
        final double circleSize = state.isEditable() ? editableCircleSize
                * paintProps.getZoomLevel() : unEditableCircleSize
                * paintProps.getZoomLevel();

        // get the magnification from the state
        float magnification = state.magnification;
        LineString line = state.boundariesMap.get(state.boundaryId);

        if (line == null) {
            GeodeticCalculator gc = new GeodeticCalculator(descriptor.getCRS());
            int numPoints = state.numDragMePoints;
            double[] worldPixel = descriptor.pixelToWorld(paintProps.getView()
                    .getExtent().getCenter());
            double lengthInMeters = state.distanceThreshold
                    * state.lineOfStormsLength;
            gc.setStartingGeographicPoint(worldPixel[0], worldPixel[1]);
            double startAngle = state.angle;
            double firstAngle = Double.NaN;
            double secondAngle = Double.NaN;
            if (Double.isNaN(startAngle)) {
                firstAngle = 180;
                secondAngle = 360;
            } else {
                firstAngle = startAngle - 90;
                secondAngle = startAngle + 90;
            }
            gc.setDirection(trackUtil.adjustAngle(firstAngle), lengthInMeters);
            Coordinate c1 = new Coordinate(gc.getDestinationGeographicPoint()
                    .getX(), gc.getDestinationGeographicPoint().getY());

            gc.setStartingGeographicPoint(worldPixel[0], worldPixel[1]);
            gc.setDirection(trackUtil.adjustAngle(secondAngle), lengthInMeters);
            Coordinate c3 = new Coordinate(gc.getDestinationGeographicPoint()
                    .getX(), gc.getDestinationGeographicPoint().getY());

            Coordinate[] coords = null;
            // unless explicitly set to 2, assume 3 points
            if (numPoints == 2) {
                coords = new Coordinate[] { c1, c3 };
            } else {
                coords = new Coordinate[] { c1,
                        new Coordinate(worldPixel[0], worldPixel[1]), c3 };
            }
            line = new GeometryFactory().createLineString(coords);

            if (state.dragMePoint != null) {
                if (state.dragMePointMap.get(state.boundaryId) == null)
                    state.dragMePointMap.put(state.boundaryId, line);
                Coordinate[] dragLineCoords = state.dragMePointMap.get(
                        state.boundaryId).getCoordinates();
                state.dragMePoint = new Point[dragLineCoords.length];
                for (int k = 0; k < dragLineCoords.length; k++) {
                    state.dragMePoint[k] = gf.createPoint(dragLineCoords[k]);
                }
                state.dragMeLine = manager.figureLineFromPoint(line,
                        state.dragMePoint);
                line = state.dragMeLine;
                state.boundariesMap.remove(state.boundaryId);
                state.boundariesMap.put(state.boundaryId, line);
            } else {
                state.dragMeLine = line;
                state.boundariesMap.remove(state.boundaryId);
                state.boundariesMap.put(state.boundaryId, line);
            }
        }

        // Time to paint the line. Again, need to handle
        // if mouseDownGeom set or not
        RGB lineColor = state.mouseDownGeom != null ? LIGHT_GRAY : state.color;

        // KS - Need to loop over all lines rather than just paint one

        int moveIndex = this.trackUtil.getCurrentFrame(paintProps
                .getFramesInfo());
        BoundaryPolyLine[] currentTimePoints;
        LineString boundary;
        Iterator<Integer> iterator = state.boundariesMap.keySet().iterator();
        while (iterator.hasNext()) {

            int boundaryId = iterator.next();
            if (state.isMovingMap.get(boundaryId)) {
                currentTimePoints = state.timePointsMap.get(boundaryId);
                boundary = currentTimePoints[moveIndex].polyline;

            } else {
                boundary = state.boundariesMap.get(boundaryId);
            }

            Coordinate[] coords = boundary.getCoordinates();
            if (boundaryId == state.boundaryId
                    && state.userAction != UserAction.SAVE
                    && state.userAction != UserAction.READ_ACTIVE_BOUNDARIES
                    && state.userAction != UserAction.DELETE_BOUNDARY
                    && state.userAction != UserAction.NONE
                    && state.userAction != UserAction.CANCEL_MODIFICATION) {
                paintLine(target, boundary.getCoordinates(), lineColor,
                        state.isEditable(), circleSize, LineStyle.SOLID);

            } else {

                paintLine(target, boundary.getCoordinates(), lineColor, false,
                        unEditableCircleSize, LineStyle.SOLID);
                lineColor = state.color;
            }

            // draw boundary Id text

            Coordinate last = coords[coords.length - 1];
            Coordinate toUseId = null;
            toUseId = new Coordinate(last.x, last.y);
            String text = "" + boundaryId;
            paintTextAtPoint(target, text, toUseId, state.color,
                    circleSize * 3, ang, magnification);
        }

        if (state.mouseDownGeom != null) {
            // Paint the dragging geom
            paintLine(target, state.mouseDownGeom.getCoordinates(),
                    state.color, state.isEditable(), circleSize,
                    LineStyle.DOTTED);
            lineColor = LIGHT_GRAY;
        }
    }

    /**
     * Paints a line with points at each vertex in the line on the screen
     * 
     * @param target
     * @param coords
     *            coordinates in the line
     * @param color
     *            color of the line
     * @param editable
     *            if the line is editable
     * @throws VizException
     */
    @SuppressWarnings("deprecation")
    private void paintLine(IGraphicsTarget target, Coordinate[] coords,
            RGB color, boolean editable, double circleSize, LineStyle style)
            throws VizException {
        DrawableCircle circle = new DrawableCircle();
        circle.basics.color = color;
        circle.radius = circleSize;
        circle.filled = true;

        Coordinate lastCoord = null;
        for (int i = 0; i < coords.length; ++i) {
            Coordinate currCoord = coords[i];
            if (currCoord != null) {
                // paint point
                if (editable) {
                    paintPoint(target, currCoord, color, circleSize);
                } else {
                    double[] p1 = descriptor.worldToPixel(new double[] {
                            currCoord.x, currCoord.y });
                    circle.setCoordinates(p1[0], p1[1]);
                    target.drawCircle(circle);
                }

                // paint line if lastCoord not null
                if (lastCoord != null) {
                    double[] p1 = descriptor.worldToPixel(new double[] {
                            currCoord.x, currCoord.y });
                    double[] p2 = descriptor.worldToPixel(new double[] {
                            lastCoord.x, lastCoord.y });

                    target.drawLine(p1[0], p1[1], 0.0, p2[0], p2[1], 0.0,
                            color, 3.0f, style);
                }
            }
            lastCoord = currCoord;
        }
    }

    /**
     * 
     * @param target
     * @param point
     * @param color
     * @param circleSize
     * @throws VizException
     */
    private void paintPoint(IGraphicsTarget target, Coordinate point,
            RGB color, double circleSize) throws VizException {
        if (point == null) {
            return;
        }
        // Paint the point
        double[] p1 = descriptor
                .worldToPixel(new double[] { point.x, point.y });

        if (p1 == null) {
            return;
        }

        DrawableCircle circle = new DrawableCircle();
        circle.basics.color = color;
        circle.setCoordinates(p1[0], p1[1]);
        circle.radius = 2 * circleSize;
        target.drawCircle(circle);

        circle.filled = true;
        circle.radius = circleSize;
        target.drawCircle(circle);
    }

    /**
     * Paint the drag me text
     * 
     * @param target
     * @param paintProps
     * @throws VizException
     */
    private void paintDragMeText(IGraphicsTarget target,
            BoundaryProperties paintProps, Geometry dragMeGeom)
            throws VizException {
        final double circleSize = editableCircleSize
                * paintProps.getZoomLevel();

        BoundaryState state = paintProps.getState();
        // get the magnification from the state
        float magnification = state.magnification;

        String text = String.format(dragTextFormat, state.userAction.dragWhat,
                state.thingToDragTo);
        Geometry geom = dragMeGeom;
        Geometry mouseDownGeom = state.mouseDownGeom;
        RGB geomColor = mouseDownGeom != null ? LIGHT_GRAY : state.color;

        // draw text using this drag geom
        Coordinate[] coords = geom.getCoordinates();
        Coordinate toUse = null;
        Coordinate toUseId = null;
        if (coords.length == 1) {
            toUse = coords[0];
        } else if (coords.length > 1) {
            // pick half way between last 2
            Coordinate last = coords[coords.length - 1];
            Coordinate secondLast = coords[coords.length - 2];

            toUse = new Coordinate(last.x - (last.x - secondLast.x) / 2, last.y
                    - (last.y - secondLast.y) / 2);
            toUseId = new Coordinate(last.x, last.y);
        }
        paintTextAtPoint(target, text, toUse, geomColor, circleSize * 3, ang,
                magnification);
        text = "" + state.boundaryId;
        paintTextAtPoint(target, text, toUseId, state.color, circleSize * 3,
                ang, magnification);

        if (mouseDownGeom != null) {
            // draw text using mouse geom
            coords = mouseDownGeom.getCoordinates();
            toUse = null;
            if (coords.length == 1) {
                toUse = coords[0];
            } else if (coords.length > 1) {
                // pick half way between last 2
                Coordinate last = coords[coords.length - 1];
                Coordinate secondLast = coords[coords.length - 2];

                toUse = new Coordinate(last.x - (last.x - secondLast.x) / 2,
                        last.y - (last.y - secondLast.y) / 2);
            }
            paintTextAtPoint(target, text, toUse, state.color, circleSize * 3,
                    ang, magnification);
        }

    }

    /**
     * Paints text at an offset from a point with left alignment
     * 
     * @param target
     * @param text
     * @param point
     * @param color
     * @param radiusFromPoint
     * @param angle
     * @throws VizException
     */
    private void paintTextAtPoint(IGraphicsTarget target, String text,
            Coordinate point, RGB color, double radiusFromPoint, double angle,
            float magnification) throws VizException {
        paintTextAtPoint(target, text, point, color, radiusFromPoint, angle,
                HorizontalAlignment.LEFT, VerticalAlignment.BOTTOM,
                magnification);
    }

    /**
     * Paints text at an offset from a point
     * 
     * @param target
     * @param text
     * @param point
     * @param color
     * @param radiusFromPoint
     * @param angle
     * @param hAlignment
     *            horizontal alignment
     * @param vAlignment
     *            vertical alignment
     * 
     * @throws VizException
     */
    private void paintTextAtPoint(IGraphicsTarget target, String text,
            Coordinate point, RGB color, double radiusFromPoint, double angle,
            HorizontalAlignment hAlignment, VerticalAlignment vAlignment,
            float magnification) throws VizException {
        // get screen location of point
        double[] p1 = descriptor
                .worldToPixel(new double[] { point.x, point.y });

        // get screen location of where to draw text using point
        double[] labelLoc = target.getPointOnCircle(p1[0], p1[1], 0.0,
                radiusFromPoint, angle);
        DrawableString str = new DrawableString(text, color);
        str.horizontalAlignment = hAlignment;
        str.verticallAlignment = vAlignment;
        // set the string magnification
        str.magnification = magnification;
        str.setCoordinates(labelLoc[0], labelLoc[1]);

        // draw the string
        target.drawStrings(str);
    }

    /**
     * Generates the start track info from the state
     * 
     * @param currentState
     */
    private void generateTrackInfo(BoundaryState currentState,
            PaintProperties paintProps) throws VizException {
        int frameCount = trackUtil.getFrameCount(paintProps.getFramesInfo());
        int currFrame = trackUtil.getCurrentFrame(paintProps.getFramesInfo());
        DataTime[] times = trackUtil.getDataTimes(paintProps.getFramesInfo());
        // Ama: October 4, 2011
        // when time updates, update position for all active moving boundaries
        // at the current time
        //
        int currentBoundaryId = currentState.boundaryId;

        boolean update = isUpdatedDataTimes(paintProps);
        // Create a map holding the active boundaries
        // Since boundariesMap is being modified, cannot use "iterator"
        // using "iterator" statement while the list is modified causes
        // "ConcurrentModificationException"

        Map<Integer, Integer> activeBoundariesMap = new HashMap<Integer, Integer>();

        for (int i : currentState.boundariesMap.keySet()) {
            if (currentState.isMovingMap.get(i)) {
                if (currentState.existingBoundaryNotEmptyMap.get(i) == null) {
                    currentState.existingBoundaryNotEmptyMap.put(i, false);
                }
                activeBoundariesMap.put(i, i);
            }

        }

        // Now looping moving boundaries to update their position if the time
        // update
        for (int bndId : activeBoundariesMap.keySet()) {
            if (update || currentState.existingBoundaryNotEmptyMap.get(bndId)) {
                currentState.boundaryId = bndId;

            } else if (bndId != currentState.boundaryId) {
                continue;
            }

            try {
                boolean moved = false;
                currentState.timePoints = currentState.timePointsMap
                        .get(currentState.boundaryId);

                if (currentState.timePointsMap.get(currentState.boundaryId) == null
                        || currentState.timePoints.length != frameCount
                        || currentState.newDuration != -1
                        || update
                        || currentState.existingBoundaryNotEmptyMap
                                .get(currentState.boundaryId)) {
                    // if "update = true" then we need to remove the current
                    // boundary from timePointMap
                    // and theAnchorLineMap
                    if (currentState.timePoints != null
                            && currentState.timePoints.length != frameCount) {
                        currentState.timePoints = null;

                        currentState.timePointsMap
                                .remove(currentState.boundaryId);
                        currentState.timePointsMap.put(currentState.boundaryId,
                                currentState.timePoints);
                    }

                    if (currentState.timePointsMap.get(currentState.boundaryId) == null) {
                        if (this.theAnchorLineMap.get(currentState.boundaryId) != null) {
                            this.theAnchorLineMap
                                    .remove(currentState.boundaryId);
                        }
                    }

                    if (currentState.newDuration != -1) {
                        currentState.duration = currentState.newDuration;
                        currentState.newDuration = -1;
                    }

                    if (this.theAnchorLineMap.get(currentState.boundaryId) == null
                            && currentState.existingBoundaryNotEmptyMap
                                    .get(currentState.boundaryId) == false) {
                        this.theAnchorLineMap.remove(currentState.boundaryId);

                        this.theAnchorLineMap.put(currentState.boundaryId,
                                currentState.dragMePointMap
                                        .get(currentState.boundaryId));
                        currentState.dragMePointMap
                                .remove(currentState.boundaryId);

                        currentState.dragMePointMap.put(
                                currentState.boundaryId, this.theAnchorLineMap
                                        .get(currentState.boundaryId));
                        currentState.timePoints = null;
                        currentState.timePointsMap
                                .remove(currentState.boundaryId);
                        currentState.timePointsMap.put(currentState.boundaryId,
                                currentState.timePoints);
                        currentState.lineMovedMap
                                .remove(currentState.boundaryId);
                        currentState.lineMovedMap.put(currentState.boundaryId,
                                false);
                        // currentState.resetAnchor = false;
                    } else if (update && currentState.timePoints != null) {
                        this.theAnchorLineMap.remove(currentState.boundaryId);
                        this.theAnchorLineMap.put(currentState.boundaryId,
                                updateAnchorLineMap(currentState, paintProps));
                        currentState.timePoints = null;
                        currentState.timePointsMap
                                .remove(currentState.boundaryId);
                        currentState.timePointsMap.put(currentState.boundaryId,
                                currentState.timePoints);
                    } else if (currentState.existingBoundaryNotEmptyMap
                            .get(currentState.boundaryId)
                            && currentState.timePoints == null) {

                        this.theAnchorLineMap.put(currentState.boundaryId,
                                updateAnchorLineMap(currentState, paintProps));
                        currentState.timePoints = null;
                        currentState.timePointsMap
                                .remove(currentState.boundaryId);
                        currentState.timePointsMap.put(currentState.boundaryId,
                                currentState.timePoints);
                        currentState.lineMovedMap.put(currentState.boundaryId,
                                false);
                    }

                    generateNewTrackInfo(currentState, currFrame, paintProps);
                    currentDisplayedTimes = trackUtil.getDataTimes(paintProps
                            .getFramesInfo());

                    currentState.displayedIndexAtStartMotionCompute = this.trackUtil
                            .getCurrentFrame(paintProps.getFramesInfo());

                    generateTrackInfo(currentState, paintProps);
                } else {
                    if (currentState.lineMovedMap.get(currentState.boundaryId)) {
                        if (currentState.userAction == UserAction.EDIT_BOUNDARY) {
                            currentState.editedTimeMap
                                    .remove(currentState.boundaryId);
                            currentState.editedTimeMap.put(
                                    currentState.boundaryId, times[currFrame]);
                            if (currentState.geomChanged == true) {
                                currentState.dialogObject
                                        .setBtnTrueAfterCancelAction();
                            }
                        }
                        this.theAnchorLineMap.remove(currentState.boundaryId);
                        this.theAnchorLineMap.put(currentState.boundaryId,
                                currentState.dragMePointMap
                                        .get(currentState.boundaryId));
                        generateExistingTrackInfo(currentState, paintProps);
                        currentState.lineMovedMap
                                .remove(currentState.boundaryId);
                        currentState.lineMovedMap.put(currentState.boundaryId,
                                false);
                        currentState.originalTrack = false;
                        moved = true;
                    }

                    currentDisplayedTimes = trackUtil.getDataTimes(paintProps
                            .getFramesInfo());
                    if (moved) {
                        this.theAnchorIndex = currFrame;
                        currentState.timePoints = currentState.timePointsMap
                                .get(currentState.boundaryId);
                        this.theAnchorLineMap.remove(currentState.boundaryId);
                        this.theAnchorLineMap
                                .put(currentState.boundaryId,
                                        currentState.timePoints[theAnchorIndex].polyline);
                    }
                }

            } catch (ImpossibleTrackException e) {
                statusHandler.handle(Priority.PROBLEM,
                        "Track produced an impossible scenario", e);
            }
            currentState.timePoints = currentState.timePointsMap
                    .get(currentState.boundaryId);
            if (currentState.timePoints != null) {
                LineString dragPointLine = currentState.timePoints[currFrame].polyline;
                Coordinate[] dragLineCoords = dragPointLine.getCoordinates();
                currentState.dragMePoint = new Point[dragLineCoords.length];
                for (int k = 0; k < dragLineCoords.length; k++) {
                    currentState.dragMePoint[k] = gf
                            .createPoint(dragLineCoords[k]);
                }
                currentState.dragMePointMap.remove(currentState.boundaryId);

                currentState.dragMePointMap.put(currentState.boundaryId,
                        dragPointLine);

                if (currentState.boundariesMap.get(currentState.boundaryId) != null) {

                    currentState.dragMeLine = manager.figureLineFromPoint(
                            currentState.boundariesMap
                                    .get(currentState.boundaryId),
                            currentState.dragMePoint);
                    currentState.boundariesMap.remove(currentState.boundaryId);
                    currentState.boundariesMap.put(currentState.boundaryId,
                            currentState.dragMeLine);
                }
            } else {
                currentState.mode = BoundaryState.Mode.DRAG_ME;
                currentState.dragMePointMap.remove(currentState.boundaryId);

                currentState.dragMePointMap.put(currentState.boundaryId,
                        this.theAnchorLineMap.get(currentState.boundaryId));
                Coordinate[] dragLineCoords = this.theAnchorLineMap.get(
                        currentState.boundaryId).getCoordinates();
                currentState.dragMePoint = new Point[dragLineCoords.length];
                for (int k = 0; k < dragLineCoords.length; k++) {
                    currentState.dragMePoint[k] = gf
                            .createPoint(dragLineCoords[k]);
                }
            }
        }
        currentState.boundaryId = currentBoundaryId;
    }

    private void generateExistingTrackInfo(BoundaryState state,
            PaintProperties paintProps) {
        int moveIndex = this.trackUtil.getCurrentFrame(paintProps
                .getFramesInfo());
        int pivotIndex = state.displayedPivotIndex;

        BoundaryPolyLine startCoordForMotion;
        BoundaryPolyLine endCoordForMotion;

        state.timePoints = state.timePointsMap.get(state.boundaryId);

        state.timePoints[moveIndex].polyline = state.dragMePointMap
                .get(state.boundaryId);

        int startCoordIndex = pivotIndex < moveIndex ? pivotIndex : moveIndex;
        int endCoordIndex = pivotIndex < moveIndex ? moveIndex : pivotIndex;

        if (startCoordIndex < moveIndex) {
            startCoordIndex = moveIndex;
        }

        BoundaryPolyLine startCoord = state.timePoints[startCoordIndex];
        BoundaryPolyLine endCoord = state.timePoints[endCoordIndex];

        Coordinate[] lineStartCoords = startCoord.polyline.getCoordinates();
        Coordinate[] lineEndCoords = endCoord.polyline.getCoordinates();
        Coordinate[] endVertices = new Coordinate[lineEndCoords.length];

        double angle = 0.0;
        double oppositeAngle = 0.0;
        double speed = 0.0;
        if (state.vertexSpeedMap.get(state.boundaryId) != null) {
            state.vertexSpeed = state.vertexSpeedMap.get(state.boundaryId);
            state.vertexAngle = state.vertexAngleMap.get(state.boundaryId);
        }
        BoundaryPolyLine[] timePoints = new BoundaryPolyLine[state.timePoints.length];

        // Motion computation for moving boundary
        // editedLineForMotionComputation: original boundary at creation time
        if (state.editedLineForMotionComputation != null) {
            startCoordForMotion = state.timePoints[moveIndex];
            endCoordForMotion = state.timePoints[state.displayedIndexAtStartMotionCompute];

            Coordinate[] lineStartCoordsForMotion;

            lineStartCoordsForMotion = startCoordForMotion.polyline
                    .getCoordinates();
            lineEndCoords = state.editedLineForMotionComputation
                    .getCoordinates();
            GeodeticCalculator gc = new GeodeticCalculator();
            for (int j = 0; j < lineStartCoordsForMotion.length; j++) {

                try {

                    if (state.displayedIndexAtStartMotionCompute > moveIndex) {
                        gc.setStartingGeographicPoint(
                                lineStartCoordsForMotion[j].x,
                                lineStartCoordsForMotion[j].y);
                        gc.setDestinationGeographicPoint(lineEndCoords[j].x,
                                lineEndCoords[j].y);
                    } else {
                        gc.setStartingGeographicPoint(lineEndCoords[j].x,
                                lineEndCoords[j].y);
                        gc.setDestinationGeographicPoint(
                                lineStartCoordsForMotion[j].x,
                                lineStartCoordsForMotion[j].y);

                    }
                    angle = gc.getAzimuth();
                    oppositeAngle = trackUtil.adjustAngle(angle + 180);
                    speed = gc.getOrthodromicDistance()
                            / trackUtil.timeBetweenDataTimes(
                                    startCoordForMotion.time,
                                    endCoordForMotion.time);
                    state.vertexAngle[j] = (float) angle;
                    state.vertexSpeed[j] = (float) speed;

                    // Tempory fix to prevent a resource error because
                    if (Double.isNaN(speed)) {
                        return;
                    }

                } catch (ArrayIndexOutOfBoundsException a) {
                    statusHandler
                            .handle(Priority.PROBLEM,
                                    "Oops! You tried to add/delete a vertex while"
                                            + " in process of computing the boundary motion"
                                            + " PLEASE DO NOT add any new vertex during motion computation"
                                            + " Click Clear button and restart your boundary creation again ",
                                    a);
                    return;
                }
            }
        }

        timePoints[startCoordIndex] = startCoord;
        timePoints[endCoordIndex] = endCoord;

        for (int i = timePoints.length - 1; i >= 0; --i) {
            if (i == startCoordIndex || i == endCoordIndex) {
                continue;
            }

            DataTime coordTime = state.timePoints[i].time;

            GeodeticCalculator gc = new GeodeticCalculator();
            for (int j = 0; j < lineStartCoords.length; j++) {

                oppositeAngle = trackUtil
                        .adjustAngle(state.vertexAngle[j] + 180);
                gc.setStartingGeographicPoint(lineStartCoords[j].x,
                        lineStartCoords[j].y);

                double distance = state.vertexSpeed[j]
                        * trackUtil.timeBetweenDataTimes(startCoord.time,
                                coordTime);
                if (i > startCoordIndex) {
                    gc.setDirection(state.vertexAngle[j], distance);
                } else if (i < startCoordIndex) {
                    gc.setDirection(oppositeAngle, distance);
                }
                Point2D point = gc.getDestinationGeographicPoint();
                endVertices[j] = new Coordinate(point.getX(), point.getY());
            }
            if (state.vertexSpeedMap.get(state.boundaryId) != null) {
                state.vertexSpeedMap.remove(state.boundaryId);
                state.vertexAngleMap.remove(state.boundaryId);
                state.vertexAngleMap.put(state.boundaryId, state.vertexAngle);
                state.vertexSpeedMap.put(state.boundaryId, state.vertexSpeed);
            } else {
                state.vertexAngleMap.put(state.boundaryId, state.vertexAngle);
                state.vertexSpeedMap.put(state.boundaryId, state.vertexSpeed);

            }

            Coordinate[] newLineCoords = new Coordinate[lineEndCoords.length];
            for (int k = 0; k < lineEndCoords.length; k++) {
                newLineCoords[k] = endVertices[k];

            }
            LineString line = new GeometryFactory()
                    .createLineString(newLineCoords);
            timePoints[i] = new BoundaryPolyLine(line, coordTime);
        }
        state.timePointsMap.remove(state.boundaryId);

        state.timePointsMap.put(state.boundaryId, timePoints);
    }

    private void generateNewTrackInfo(BoundaryState state, int anchorIndex,
            PaintProperties paintProps) throws ImpossibleTrackException {
        double oppositeAngle;
        int frameCount = trackUtil.getFrameCount(paintProps.getFramesInfo());
        state.timePoints = state.timePointsMap.get(state.boundaryId);

        if (state.timePointsMap.get(state.boundaryId) != null) {
            theAnchorIndex = frameCount
                    - (state.timePoints.length - theAnchorIndex);
            if (theAnchorIndex >= 0 && theAnchorIndex < frameCount) {
                anchorIndex = theAnchorIndex;
            } else {
                theAnchorIndex = 0;
            }
            LineString polyline = state.timePoints[state.timePoints.length
                    - (frameCount - anchorIndex)].polyline;
            theAnchorLineMap.remove(state.boundaryId);
            theAnchorLineMap.put(state.boundaryId, polyline);
        } else {
            theAnchorIndex = anchorIndex;
        }
        DataTime[] dataTimes = trackUtil.getDataTimes(paintProps
                .getFramesInfo());
        BoundaryPolyLine[] timePoints = new BoundaryPolyLine[frameCount];
        BoundaryPolyLine anchor = new BoundaryPolyLine(
                this.theAnchorLineMap.get(state.boundaryId),
                dataTimes[anchorIndex]);

        timePoints[anchorIndex] = anchor;
        if (state.timePointsMap.get(state.boundaryId) == null
                || isUpdatedDataTimes(paintProps)) {

            // Compute/retrieve vertex coordinate of the starting position of
            // the line
            Coordinate[] lineCoords = anchor.polyline.getCoordinates();
            Coordinate[] vertices = new Coordinate[lineCoords.length];

            float[] vertexSpeed = new float[lineCoords.length];
            for (int i = 0; i < lineCoords.length; i++) {
                vertexSpeed[i] = (float) 0.0;
            }
            state.vertexSpeed = state.vertexSpeedMap.get(state.boundaryId);
            state.vertexAngle = state.vertexAngleMap.get(state.boundaryId);

            if (state.vertexSpeed == null) {
                state.vertexSpeed = new float[lineCoords.length];
                state.vertexAngle = new float[lineCoords.length];
            }
            for (int i = timePoints.length - 1; i >= 0; --i) {
                if (i == anchorIndex) {
                    continue;
                }

                DataTime coordTime = dataTimes[i];

                for (int j = 0; j < lineCoords.length; j++) {

                    // If uninitialized, grab from tools data manager

                    Coordinate[] coords = state.boundariesMap.get(
                            state.boundaryId).getCoordinates();
                    if (state.vertexSpeed == null || state.vertexAngle == null) {
                        state.vertexSpeed[j] = vertexSpeed[j];

                        // if (state.userAction == UserAction.INSERT_BOUNDARY) {
                        // // calculate based on poly line
                        GeodeticCalculator gc = new GeodeticCalculator();
                        gc.setStartingGeographicPoint(coords[j].x, coords[j].y);
                        gc.setDestinationGeographicPoint(
                                coords[coords.length - 1].x,
                                coords[coords.length - 1].y);
                        state.vertexAngle[j] = (float) trackUtil.adjustAngle(gc
                                .getAzimuth() - 90);
                    }
                    oppositeAngle = trackUtil
                            .adjustAngle(state.vertexAngle[j] + 180);

                    GeodeticCalculator gc = new GeodeticCalculator();
                    gc.setStartingGeographicPoint(lineCoords[j].x,
                            lineCoords[j].y);

                    double distance = state.vertexSpeed[j]
                            * trackUtil.timeBetweenDataTimes(anchor.time,
                                    coordTime);

                    if (i > anchorIndex) {
                        gc.setDirection(state.vertexAngle[j], distance);
                    } else if (i < anchorIndex) {
                        gc.setDirection(oppositeAngle, distance);
                    }
                    Point2D point = gc.getDestinationGeographicPoint();

                    vertices[j] = new Coordinate(point.getX(), point.getY());
                }
                if (state.vertexSpeedMap.get(state.boundaryId) != null) {
                    state.vertexSpeedMap.remove(state.boundaryId);
                    state.vertexAngleMap.remove(state.boundaryId);
                    state.vertexAngleMap.put(state.boundaryId,
                            state.vertexAngle);
                    state.vertexSpeedMap.put(state.boundaryId,
                            state.vertexSpeed);
                } else {
                    state.vertexAngleMap.put(state.boundaryId,
                            state.vertexAngle);
                    state.vertexSpeedMap.put(state.boundaryId,
                            state.vertexSpeed);
                }
                Coordinate[] newLineCoords = new Coordinate[lineCoords.length];
                for (int j = 0; j < vertices.length; j++) {
                    newLineCoords[j] = vertices[j];

                }
                LineString line = new GeometryFactory()
                        .createLineString(newLineCoords);
                timePoints[i] = new BoundaryPolyLine(line, coordTime);
            }
        } else {
            timePoints = state.timePointsMap.get(state.boundaryId);
        }

        if (state.duration == -1) {
            state.duration = trackUtil.timeBetweenDataTimes(dataTimes[0],
                    dataTimes[dataTimes.length - 1]) / 60;
        }
        state.timePointsMap.remove(state.boundaryId);
        state.timePointsMap.put(state.boundaryId, timePoints);
    }

    private LineString updateAnchorLineMap(BoundaryState currentState,
            PaintProperties paintProps) {
        int currFrame = trackUtil.getCurrentFrame(paintProps.getFramesInfo());
        BoundaryPolyLine sc = null;
        if (currentState.existingBoundaryNotEmptyMap
                .get(currentState.boundaryId)) {
            sc = new BoundaryPolyLine(
                    currentState.boundariesMap.get(currentState.boundaryId),
                    currentState.creationFileTime);
            if (currentState.existingBoundaryNotEmptyMap != null)
                currentState.existingBoundaryNotEmptyMap
                        .remove(currentState.boundaryId);
            currentState.existingBoundaryNotEmptyMap.put(
                    currentState.boundaryId, false);
        } else {
            sc = currentState.timePoints[currFrame];
        }
        DataTime[] dataTimes = trackUtil.getDataTimes(paintProps
                .getFramesInfo());
        double distance;

        Coordinate[] coords = sc.polyline.getCoordinates();
        Coordinate[] vertices = new Coordinate[coords.length];
        float[] vertexSpeed = currentState.vertexSpeedMap
                .get(currentState.boundaryId);
        float[] vertexAngle = currentState.vertexAngleMap
                .get(currentState.boundaryId);
        for (int j = 0; j < coords.length; j++) {
            GeodeticCalculator gc = new GeodeticCalculator();
            gc.setStartingGeographicPoint(coords[j].x, coords[j].y);
            distance = vertexSpeed[j]
                    * trackUtil.timeBetweenDataTimes(sc.time,
                            dataTimes[currFrame]);
            gc.setDirection(vertexAngle[j], distance);
            Point2D point = gc.getDestinationGeographicPoint();
            vertices[j] = new Coordinate(point.getX(), point.getY());
        }
        Coordinate[] newLineCoords = new Coordinate[vertices.length];
        for (int j = 0; j < vertices.length; j++) {
            newLineCoords[j] = vertices[j];

        }
        return new GeometryFactory().createLineString(newLineCoords);

    }

    public boolean isUpdatedDataTimes(PaintProperties paintProps) {
        DataTime[] uniqueTimes = trackUtil.getDataTimes(paintProps
                .getFramesInfo());
        if (currentDisplayedTimes == null) {
            return false;
        }
        int len = currentDisplayedTimes.length;
        return uniqueTimes.length != len
                || (!uniqueTimes[0].equals(currentDisplayedTimes[0]) && !uniqueTimes[len - 1]
                        .equals(currentDisplayedTimes[len - 1]));
    }

}