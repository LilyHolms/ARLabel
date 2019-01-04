package org.tensorflow.demo.tracking;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.Pair;
import android.util.TypedValue;
import android.widget.Toast;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import org.tensorflow.demo.CameraActivity;
import org.tensorflow.demo.Classifier.Recognition;
import org.tensorflow.demo.ClickView;
import org.tensorflow.demo.env.BorderedText;
import org.tensorflow.demo.env.ImageUtils;
import org.tensorflow.demo.env.Logger;

/**
 * A tracker wrapping ObjectTracker that also handles non-max suppression and matching existing
 * objects to new detections.
 */
public class MultiBoxTracker {
  private final Logger logger = new Logger();

  private static final float TEXT_SIZE_DIP = 18;

  // Maximum percentage of a box that can be overlapped by another box at detection time. Otherwise
  // the lower scored box (new or old) will be removed.
  private static final float MAX_OVERLAP = 0.2f;

  private static final float MIN_SIZE = 16.0f;

  // Allow replacement of the tracked box with new results if
  // correlation has dropped below this level.
  private static final float MARGINAL_CORRELATION = 0.75f;

  // Consider object to be lost if correlation falls below this threshold.
  private static final float MIN_CORRELATION = 0.3f;

  private static final int[] COLORS = {
    Color.BLUE, Color.RED, Color.GREEN, Color.YELLOW, Color.CYAN, Color.MAGENTA, Color.WHITE,
    Color.parseColor("#55FF55"), Color.parseColor("#FFA500"), Color.parseColor("#FF8888"),
    Color.parseColor("#AAAAFF"), Color.parseColor("#FFFFAA"), Color.parseColor("#55AAAA"),
    Color.parseColor("#AA33AA"), Color.parseColor("#0D0068")
  };

  private final Queue<Integer> availableColors = new LinkedList<Integer>();

  public ObjectTracker objectTracker;

  final List<Pair<Float, RectF>> screenRects = new LinkedList<Pair<Float, RectF>>();

  private static class TrackedRecognition {
    ObjectTracker.TrackedObject trackedObject;
    RectF location;
    float detectionConfidence;
    int color;
    String title;
  }

  private final List<TrackedRecognition> trackedObjects = new LinkedList<TrackedRecognition>();

  private final Paint boxPaint = new Paint();

  private final float textSizePx;
  private final BorderedText borderedText;

  private Matrix frameToCanvasMatrix;

  private int frameWidth;
  private int frameHeight;

  private int sensorOrientation;
  private Context context;

  //lily
  private int currentTrackingColor;

  public MultiBoxTracker(final Context context) {
    this.context = context;
    for (final int color : COLORS) {
      availableColors.add(color);
    }

    boxPaint.setColor(Color.RED);
    boxPaint.setStyle(Style.STROKE);
    boxPaint.setStrokeWidth(12.0f);
    boxPaint.setStrokeCap(Cap.ROUND);
    boxPaint.setStrokeJoin(Join.ROUND);
    boxPaint.setStrokeMiter(100);

    textSizePx =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, context.getResources().getDisplayMetrics());
    borderedText = new BorderedText(textSizePx);
  }

  private Matrix getFrameToCanvasMatrix() {
    return frameToCanvasMatrix;
  }

  public synchronized void drawDebug(final Canvas canvas) {
    final Paint textPaint = new Paint();
    textPaint.setColor(Color.WHITE);
    textPaint.setTextSize(60.0f);

    final Paint boxPaint = new Paint();
    boxPaint.setColor(Color.RED);
    boxPaint.setAlpha(200);
    boxPaint.setStyle(Style.STROKE);

    for (final Pair<Float, RectF> detection : screenRects) {
      final RectF rect = detection.second;
      canvas.drawRect(rect, boxPaint);
      canvas.drawText("" + detection.first, rect.left, rect.top, textPaint);
      borderedText.drawText(canvas, rect.centerX(), rect.centerY(), "" + detection.first);
    }

    if (objectTracker == null) {
      return;
    }

    // Draw correlations.
    for (final TrackedRecognition recognition : trackedObjects) {
      final ObjectTracker.TrackedObject trackedObject = recognition.trackedObject;

      final RectF trackedPos = trackedObject.getTrackedPositionInPreviewFrame();

      if (getFrameToCanvasMatrix().mapRect(trackedPos)) {
        final String labelString = String.format("%.2f", trackedObject.getCurrentCorrelation());
        borderedText.drawText(canvas, trackedPos.right, trackedPos.bottom, labelString);
      }
    }

    final Matrix matrix = getFrameToCanvasMatrix();
    objectTracker.drawDebug(canvas, matrix);
  }

  public synchronized void trackResults(
      final List<Recognition> results, final byte[] frame, final long timestamp) {
    logger.i("Processing %d results from %d", results.size(), timestamp);
    processResults(timestamp, results, frame);
  }

  //lily
  private void myDrawHelper(Canvas canvas, TrackedRecognition recognition){
    final RectF trackedPos =
            (objectTracker != null)
                    ? recognition.trackedObject.getTrackedPositionInPreviewFrame()
                    : new RectF(recognition.location);

    getFrameToCanvasMatrix().mapRect(trackedPos);
    boxPaint.setColor(recognition.color);

    //lily: important changes !!!

//      final String labelString =
//          !TextUtils.isEmpty(recognition.title)
//              ? String.format("%s %.2f", recognition.title, recognition.detectionConfidence)
//              : String.format("%.2f", recognition.detectionConfidence);
    //lily
//      borderedText.drawText(canvas, trackedPos.left + cornerSize, trackedPos.bottom, labelString);
//      final String labelString = String.format("%s", recognition.title);
    final String labelString =
            !TextUtils.isEmpty(recognition.title)
                    ? String.format("%s", recognition.title)
                    : "";

    System.out.println("lily touchevent: detection results"+":"+trackedPos);
    // final float cornerSize = Math.min(trackedPos.width(), trackedPos.height()) / 8.0f;
    final float cornerSize = 0;
    canvas.drawRoundRect(trackedPos, cornerSize, cornerSize, boxPaint);
    System.out.println("sensor1:"+CameraActivity.sensorAngle[1]);
    System.out.println("sensor2:"+CameraActivity.sensorAngle[2]);
    System.out.println("sensor3:"+Math.toDegrees(Math.atan(CameraActivity.sensorAngle[1]/CameraActivity.sensorAngle[2])) );
    float rotateDegree = (float)(90 - Math.toDegrees(Math.atan(CameraActivity.sensorAngle[1]/CameraActivity.sensorAngle[2])));
    if(rotateDegree>=90){
      rotateDegree=rotateDegree-180;
    }
    canvas.rotate(rotateDegree,(trackedPos.left +trackedPos.right)/2, (trackedPos.bottom+trackedPos.top)/2);
    borderedText.drawText(canvas, (trackedPos.left +trackedPos.right)/2, (trackedPos.bottom+trackedPos.top)/2, labelString);
    canvas.rotate(-rotateDegree,(trackedPos.left +trackedPos.right)/2, (trackedPos.bottom+trackedPos.top)/2);
  }

  public synchronized void draw(final Canvas canvas) {
    final boolean rotated = sensorOrientation % 180 == 90;//横屏true
    final float multiplier =
        Math.min(canvas.getHeight() / (float) (rotated ? frameWidth : frameHeight),
                 canvas.getWidth() / (float) (rotated ? frameHeight : frameWidth));

    frameToCanvasMatrix =
        ImageUtils.getTransformationMatrix(
            frameWidth,
            frameHeight,
            (int) (multiplier * (rotated ? frameHeight : frameWidth)),
            (int) (multiplier * (rotated ? frameWidth : frameHeight)),
            sensorOrientation,
            false);

    if (CameraActivity.isConfirmed==2){
      for (final TrackedRecognition recognition : trackedObjects){
        if(recognition.color==currentTrackingColor){
          myDrawHelper(canvas, recognition);
          break;
        }
      }
    }else if (CameraActivity.isConfirmed==1){
      int count = -1;
      double distance = 0;
      int indexShortestDistance = 0;
      for (final TrackedRecognition recognition : trackedObjects){
        count++;
        final RectF trackedPos =
                (objectTracker != null)
                        ? recognition.trackedObject.getTrackedPositionInPreviewFrame()
                        : new RectF(recognition.location);
        getFrameToCanvasMatrix().mapRect(trackedPos);
        float centerX = trackedPos.centerX();
        float centerY = trackedPos.centerY();
        float[] clickCenter = ClickView.clickCenter;
        if(count==0){
          distance = Math.pow(centerX-clickCenter[0],2) + Math.pow(centerY-clickCenter[1],2);
          System.out.println("lily insta" + recognition.title + distance);
        }else {
          double current_distance = Math.pow(centerX-clickCenter[0],2) + Math.pow(centerY-clickCenter[1],2);
          if(distance > current_distance ){
            distance = current_distance;
            indexShortestDistance = count;
          }
          System.out.println("lily insta" + recognition.title + current_distance);
        }

      }
      currentTrackingColor = trackedObjects.get(indexShortestDistance).color;
      myDrawHelper(canvas, trackedObjects.get(indexShortestDistance));

      CameraActivity.isConfirmed=2;
    }else if (CameraActivity.isConfirmed==0) {
      for (final TrackedRecognition recognition : trackedObjects) {
        myDrawHelper(canvas, recognition);
      }
    }
  }

  private boolean initialized = false;

  public synchronized void onFrame(
      final int w,
      final int h,
      final int rowStride,
      final int sensorOrientation,
      final byte[] frame,
      final long timestamp) {
    if (objectTracker == null && !initialized) {
      ObjectTracker.clearInstance();

      logger.i("Initializing ObjectTracker: %dx%d", w, h);
      objectTracker = ObjectTracker.getInstance(w, h, rowStride, true);
      frameWidth = w;
      frameHeight = h;
      this.sensorOrientation = sensorOrientation;
      initialized = true;

      if (objectTracker == null) {
        String message =
            "Object tracking support not found. "
                + "See tensorflow/examples/android/README.md for details.";
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        logger.e(message);
      }
    }

    if (objectTracker == null) {
      return;
    }

    objectTracker.nextFrame(frame, null, timestamp, null, true);

    // Clean up any objects not worth tracking any more.
    final LinkedList<TrackedRecognition> copyList =
        new LinkedList<TrackedRecognition>(trackedObjects);
    for (final TrackedRecognition recognition : copyList) {
      final ObjectTracker.TrackedObject trackedObject = recognition.trackedObject;
      final float correlation = trackedObject.getCurrentCorrelation();
      if (correlation < MIN_CORRELATION) {
        logger.v("Removing tracked object %s because NCC is %.2f", trackedObject, correlation);
        trackedObject.stopTracking();
        trackedObjects.remove(recognition);

        availableColors.add(recognition.color);
      }
    }
  }

  private void processResults(
      final long timestamp, final List<Recognition> results, final byte[] originalFrame) {
    final List<Pair<Float, Recognition>> rectsToTrack = new LinkedList<Pair<Float, Recognition>>();

    screenRects.clear();
    final Matrix rgbFrameToScreen = new Matrix(getFrameToCanvasMatrix());

    for (final Recognition result : results) {
      if (result.getLocation() == null) {
        continue;
      }
      final RectF detectionFrameRect = new RectF(result.getLocation());

      final RectF detectionScreenRect = new RectF();
      rgbFrameToScreen.mapRect(detectionScreenRect, detectionFrameRect);
      System.out.println("lily===detectionScreenRect:"+detectionScreenRect);
      System.out.println("lily===detectionFrameRect"+detectionFrameRect);
      logger.v(
          "Result! Frame: " + result.getLocation() + " mapped to screen:" + detectionScreenRect);

      screenRects.add(new Pair<Float, RectF>(result.getConfidence(), detectionScreenRect));

      if (detectionFrameRect.width() < MIN_SIZE || detectionFrameRect.height() < MIN_SIZE) {
        logger.w("Degenerate rectangle! " + detectionFrameRect);
        continue;
      }

      rectsToTrack.add(new Pair<Float, Recognition>(result.getConfidence(), result));
    }

    if (rectsToTrack.isEmpty()) {
      logger.v("Nothing to track, aborting.");
      return;
    }

    if (objectTracker == null) { //lily:如果objectTracker为空，初始化 trackedObjects：要跟踪的物体们，含颜色(instance)信息
      trackedObjects.clear();
      for (final Pair<Float, Recognition> potential : rectsToTrack) {
        final TrackedRecognition trackedRecognition = new TrackedRecognition();
        trackedRecognition.detectionConfidence = potential.first;
        trackedRecognition.location = new RectF(potential.second.getLocation());
        trackedRecognition.trackedObject = null;
        trackedRecognition.title = potential.second.getTitle();
        trackedRecognition.color = COLORS[trackedObjects.size()];
        trackedObjects.add(trackedRecognition);

        if (trackedObjects.size() >= COLORS.length) {
          break;
        }
      }
      return;
    }

    logger.i("%d rects to track", rectsToTrack.size());
    for (final Pair<Float, Recognition> potential : rectsToTrack) {
      //lily
      //如果不加，在点击checkbox时，会继续跟踪
      if(!CameraActivity.checkedhash.containsKey(potential.second.getTitle())){//lily
        System.out.println(potential.second.getTitle());
        continue;
      }
      if(!CameraActivity.checkedhash.get(potential.second.getTitle())){//lily
        System.out.println(potential.second.getTitle());
        continue;
      }
      handleDetection(originalFrame, timestamp, potential);
    }
  }

  private void handleDetection( //potential 单个detection结果,
      final byte[] frameCopy, final long timestamp, final Pair<Float, Recognition> potential) {
    final ObjectTracker.TrackedObject potentialObject =
        objectTracker.trackObject(potential.second.getLocation(), timestamp, frameCopy); //objectTracker？？？potentialObject:tracking的结果

    final float potentialCorrelation = potentialObject.getCurrentCorrelation();
    logger.v(
        "Tracked object went from %s to %s with correlation %.2f",
        potential.second, potentialObject.getTrackedPositionInPreviewFrame(), potentialCorrelation);

    if (potentialCorrelation < MARGINAL_CORRELATION) {
      logger.v("Correlation too low to begin tracking %s.", potentialObject);
      potentialObject.stopTracking();            //???
      return;
    }

    final List<TrackedRecognition> removeList = new LinkedList<TrackedRecognition>();

    float maxIntersect = 0.0f;

    // This is the current tracked object whose color we will take. If left null we'll take the
    // first one from the color queue.
    TrackedRecognition recogToReplace = null;

    // Look for intersections that will be overridden by this object or an intersection that would
    // prevent this one from being placed.
    for (final TrackedRecognition trackedRecognition : trackedObjects) { //trackedObjects:detection的结果，新的要tracking的物体
      final RectF a = trackedRecognition.trackedObject.getTrackedPositionInPreviewFrame();
      final RectF b = potentialObject.getTrackedPositionInPreviewFrame();
      final RectF intersection = new RectF();
      final boolean intersects = intersection.setIntersect(a, b);

      final float intersectArea = intersection.width() * intersection.height();
      final float totalArea = a.width() * a.height() + b.width() * b.height() - intersectArea;
      final float intersectOverUnion = intersectArea / totalArea;

      // If there is an intersection with this currently tracked box above the maximum overlap
      // percentage allowed, either the new recognition needs to be dismissed or the old
      // recognition needs to be removed and possibly replaced with the new one.
      if (intersects && intersectOverUnion > MAX_OVERLAP) {
        if (potential.first < trackedRecognition.detectionConfidence
            && trackedRecognition.trackedObject.getCurrentCorrelation() > MARGINAL_CORRELATION) {
          // If track for the existing object is still going strong and the detection score was
          // good, reject this new object.
          potentialObject.stopTracking();     //使用之前跟踪的结果继续跟踪
          return;
        } else {
          removeList.add(trackedRecognition);  //使用detection的跟踪结果

          // Let the previously tracked object with max intersection amount donate its color to
          // the new object.
          if (intersectOverUnion > maxIntersect) {
            maxIntersect = intersectOverUnion;
            recogToReplace = trackedRecognition;
          }
        }
      }
    }

    // If we're already tracking the max object and no intersections were found to bump off,
    // pick the worst current tracked object to remove, if it's also worse than this candidate
    // object.
    if (availableColors.isEmpty() && removeList.isEmpty()) {
      for (final TrackedRecognition candidate : trackedObjects) {
        if (candidate.detectionConfidence < potential.first) {
          if (recogToReplace == null
              || candidate.detectionConfidence < recogToReplace.detectionConfidence) {
            // Save it so that we use this color for the new object.
            recogToReplace = candidate;
          }
        }
      }
      if (recogToReplace != null) {
        logger.v("Found non-intersecting object to remove.");
        removeList.add(recogToReplace);
      } else {
        logger.v("No non-intersecting object found to remove");
      }
    }

    // Remove everything that got intersected.
    for (final TrackedRecognition trackedRecognition : removeList) {
      logger.v(
          "Removing tracked object %s with detection confidence %.2f, correlation %.2f",
          trackedRecognition.trackedObject,
          trackedRecognition.detectionConfidence,
          trackedRecognition.trackedObject.getCurrentCorrelation());
      trackedRecognition.trackedObject.stopTracking();
      trackedObjects.remove(trackedRecognition);
      if (trackedRecognition != recogToReplace) {
        availableColors.add(trackedRecognition.color);
      }
    }

    if (recogToReplace == null && availableColors.isEmpty()) {
      logger.e("No room to track this object, aborting.");
      potentialObject.stopTracking();
      return;
    }

    // Finally safe to say we can track this object.
    logger.v(
        "Tracking object %s (%s) with detection confidence %.2f at position %s",
        potentialObject,
        potential.second.getTitle(),
        potential.first,
        potential.second.getLocation());
    final TrackedRecognition trackedRecognition = new TrackedRecognition();
    trackedRecognition.detectionConfidence = potential.first;
    trackedRecognition.trackedObject = potentialObject;
    trackedRecognition.title = potential.second.getTitle();

    // Use the color from a replaced object before taking one from the color queue.
    trackedRecognition.color =
        recogToReplace != null ? recogToReplace.color : availableColors.poll();
    trackedObjects.add(trackedRecognition);
  }
}
