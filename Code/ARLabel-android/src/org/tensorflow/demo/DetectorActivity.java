
package org.tensorflow.demo;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.SystemClock;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.text.TextUtils;
import android.util.Size;
import android.util.TypedValue;
import android.view.Display;
import android.view.Surface;
import android.widget.Toast;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import org.tensorflow.demo.OverlayView.DrawCallback;
import org.tensorflow.demo.env.BorderedText;
import org.tensorflow.demo.env.ImageUtils;
import org.tensorflow.demo.env.Logger;
import org.tensorflow.demo.tracking.MultiBoxTracker;
import org.tensorflow.demo.R; // Explicit import needed for internal Google builds.

/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
public class DetectorActivity extends CameraActivity implements OnImageAvailableListener {
  private static final Logger LOGGER = new Logger();

  private static final int TF_OD_API_INPUT_SIZE = 300;
//  private static final String TF_OD_API_MODEL_FILE =                 //lily
//      "file:///android_asset/ssd_mobilenet_v1_android_export.pb";
  private static final String TF_OD_API_MODEL_FILE =
          "file:///android_asset/lily_frozen_inference_graph.pb";
//  private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/coco_labels_list.txt";
  private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/lily_coco_labels_list.txt";


  private enum DetectorMode {
    TF_OD_API, MULTIBOX, YOLO;
  }
  private static final DetectorMode MODE = DetectorMode.TF_OD_API;

  // Minimum detection confidence to track a detection.
  private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.4f;//try 0.3?

  private static final boolean MAINTAIN_ASPECT = MODE == DetectorMode.YOLO;

  private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);

  private static final boolean SAVE_PREVIEW_BITMAP = false;
  private static final float TEXT_SIZE_DIP = 10;

  private Integer sensorOrientation;

  private Classifier detector;

  private long lastProcessingTimeMs;
  private Bitmap rgbFrameBitmap = null;
  private Bitmap croppedBitmap = null;
  private Bitmap cropCopyBitmap = null;

  private boolean computingDetection = false;

  private long timestamp = 0;

  private Matrix frameToCropTransform;//仿射变换矩阵 https://docs.microsoft.com/zh-cn/dotnet/api/system.drawing.drawing2d.matrix?redirectedfrom=MSDN&view=netframework-4.7.2
  private Matrix cropToFrameTransform;

  private MultiBoxTracker tracker;

  private byte[] luminanceCopy;

  private BorderedText borderedText;
  @Override
  public void onPreviewSizeChosen(final Size size, final int rotation) {
    final float textSizePx =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
    borderedText = new BorderedText(textSizePx);
    borderedText.setTypeface(Typeface.MONOSPACE);

    tracker = new MultiBoxTracker(this);

    int cropSize = TF_OD_API_INPUT_SIZE;
      try {
        detector = TensorFlowObjectDetectionAPIModel.create(
            getAssets(), TF_OD_API_MODEL_FILE, TF_OD_API_LABELS_FILE, TF_OD_API_INPUT_SIZE);
        cropSize = TF_OD_API_INPUT_SIZE;
      } catch (final IOException e) {
        LOGGER.e("Exception initializing classifier!", e);
        Toast toast =
            Toast.makeText(
                getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
        toast.show();
        finish();
      }


    previewWidth = size.getWidth();
    previewHeight = size.getHeight();

    sensorOrientation = rotation - getScreenOrientation();//横屏rotation:90, getScreenOrientation:1(90)。竖屏rotation:90, getScreenOrientation:0(0)
    LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

    LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
    rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
    croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

    frameToCropTransform =
        ImageUtils.getTransformationMatrix(
            previewWidth, previewHeight,
            cropSize, cropSize,
            sensorOrientation, MAINTAIN_ASPECT);

    cropToFrameTransform = new Matrix();
    frameToCropTransform.invert(cropToFrameTransform);//将frameToCropTransform的逆矩阵存入cropToFrameTransform

    trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
    trackingOverlay.addCallback(//trackingOverlay.postInvalidate()时执行回调
        new DrawCallback() {
          @Override
          public void drawCallback(final Canvas canvas) {
            tracker.draw(canvas);//lily流程.绘制检测结果的重要函数 MultiBoxTracker::
            if (isDebug()) {//debug时 画在preview处的细框
              tracker.drawDebug(canvas);
            }
          }
        });

    addCallback(
        new DrawCallback() {
          @Override
          public void drawCallback(final Canvas canvas) {
            if (!isDebug()) {
              return;
            }
            final Bitmap copy = cropCopyBitmap;
            if (copy == null) {
              return;
            }

            final int backgroundColor = Color.argb(100, 0, 0, 0);
            canvas.drawColor(backgroundColor);

            final Matrix matrix = new Matrix();
            final float scaleFactor = 2;
            matrix.postScale(scaleFactor, scaleFactor);
            matrix.postTranslate(
                canvas.getWidth() - copy.getWidth() * scaleFactor,
                canvas.getHeight() - copy.getHeight() * scaleFactor);
            canvas.drawBitmap(copy, matrix, new Paint());//画cropped图

            final Vector<String> lines = new Vector<String>();
            if (detector != null) {
              final String statString = detector.getStatString();
              final String[] statLines = statString.split("\n");
              for (final String line : statLines) {
                lines.add(line);
              }
            }
            lines.add("");

            lines.add("Frame: " + previewWidth + "x" + previewHeight);
            lines.add("Crop: " + copy.getWidth() + "x" + copy.getHeight());
            lines.add("View: " + canvas.getWidth() + "x" + canvas.getHeight());
            lines.add("Rotation: " + sensorOrientation);
            lines.add("Inference time: " + lastProcessingTimeMs + "ms");

            borderedText.drawLines(canvas, 10, canvas.getHeight() - 10, lines);//画横屏时左下角说明文字?
          }
        });
  }

  OverlayView trackingOverlay;

  @Override
  protected void processImage() {//检测入口
    ++timestamp;
    final long currTimestamp = timestamp;
    byte[] originalLuminance = getLuminance();
    tracker.onFrame(
        previewWidth,
        previewHeight,
        getLuminanceStride(),
        sensorOrientation,
        originalLuminance,
        timestamp);
    trackingOverlay.postInvalidate();//调用tracker.draw(canvas);  更新tracking而非detection结果？

    // No mutex needed as this method is not reentrant.
    if (computingDetection) {
      readyForNextImage();
      return;
    }
    computingDetection = true;
    LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");
    //将一帧图片的rgbBytes数组 赋值给 bitmap中像素点
    rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);//CameraActivity::getRgbBytes()

    if (luminanceCopy == null) {
      luminanceCopy = new byte[originalLuminance.length];
    }
    System.arraycopy(originalLuminance, 0, luminanceCopy, 0, originalLuminance.length);
    readyForNextImage();

    final Canvas canvas = new Canvas(croppedBitmap);
    canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);//画cropped图
    // For examining the actual TF input.
    if (SAVE_PREVIEW_BITMAP) {
      ImageUtils.saveBitmap(croppedBitmap);
    }

    runInBackground(
        new Runnable() {
          @Override
          public void run() {
            LOGGER.i("Running detection on image " + currTimestamp);
            final long startTime = SystemClock.uptimeMillis();
            final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);//获得croppedBitmap上的检测结果。RectF(左上右下)
//            System.out.println("lily123: cropped Recognition results"+results);
            //TensorFlowObjectDetectionAPIModel::recognizeImage
            lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

            cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
            final Canvas canvas = new Canvas(cropCopyBitmap);
            final Paint paint = new Paint();
            paint.setColor(Color.RED);
            paint.setStyle(Style.STROKE);
            paint.setStrokeWidth(2.0f);

            float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;

            final List<Classifier.Recognition> mappedRecognitions =
                new LinkedList<Classifier.Recognition>();

            for (final Classifier.Recognition result : results) {
              String labelString = result.getTitle();
              if(!CameraActivity.checkedhash.containsKey(labelString)){//lily
                continue;
              }
              if(!CameraActivity.checkedhash.get(labelString)){//lily
                continue;
              }
              final RectF location = result.getLocation();
              if (location != null && result.getConfidence() >= minimumConfidence) {
                System.out.println("lily123: cropped Recognition results"+currTimestamp+":"+result);
                canvas.drawRect(location, paint);//画cropped图中的检测框
                cropToFrameTransform.mapRect(location);//对cropped location进行cropToFrameTransform变换，获得frame系中的物体坐标
                result.setLocation(location);
                mappedRecognitions.add(result);//frame系中的物体坐标list
              }
            }
            System.out.println("luminanceCopy: "+Arrays.toString(luminanceCopy));
            tracker.trackResults(mappedRecognitions, luminanceCopy, currTimestamp);//lily流程.重点方法MultiBoxTracker::processResults
            trackingOverlay.postInvalidate();//非UI线程刷新界面. 执行tracker.draw(canvas); 调用tracker.draw(canvas);

            requestRender();
            computingDetection = false;
          }
        });
  }

  @Override
  protected int getLayoutId() {
    return R.layout.camera_connection_fragment_tracking;
  }

  @Override
  protected Size getDesiredPreviewFrameSize() {
    return DESIRED_PREVIEW_SIZE;
  }

  @Override
  public void onSetDebug(final boolean debug) {
    detector.enableStatLogging(debug);
  }
}
