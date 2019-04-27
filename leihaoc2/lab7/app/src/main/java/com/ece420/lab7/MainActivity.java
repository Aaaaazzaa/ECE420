package com.ece420.lab7;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.Manifest;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Rect2d;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.tracking.TrackerMIL;
import org.opencv.tracking.TrackerMedianFlow;
import org.opencv.xfeatures2d.SURF;
import java.util.Arrays;
import static org.opencv.core.Core.DECOMP_SVD;
import static org.opencv.core.Core.minMaxLoc;
import static org.opencv.core.Core.sumElems;
import static org.opencv.core.CvType.CV_16S;
import static org.opencv.core.CvType.CV_32F;
import static org.opencv.core.CvType.CV_8S;
import static org.opencv.core.CvType.CV_8SC1;
import static org.opencv.core.CvType.CV_8U;

public class MainActivity<i> extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "MainActivity";

    // UI Variables
    private Button controlButton;
    private Button confirmButton;
    private Button openButton;
    private SeekBar colorSeekbar;
    private SeekBar widthSeekbar;
    private SeekBar heightSeekbar;
    private TextView widthTextview;
    private TextView heightTextview;
    private boolean TrackingSuccess;
    // Declare OpenCV based camera view base
    private CameraBridgeViewBase mOpenCvCameraView;
    // Camera size
    private int myWidth;
    private int myHeight;
    //
    int numCal = 9;
    int widthCal = 3;
    int heightCal = 3;
    int win = 90;
    // TODO: use pointsCal directly
    int[] xOutcome = new int[numCal];
    int[] yOutcome = new int[numCal];
    int[] xPredict = new int[numCal];
    int[] yPredict = new int[numCal];
    // use xPair and yPair to line regression
    private Mat xPair; // CV_16S
    private Mat yPair;
    private Mat xParam;
    private Mat yParam;
    private Point cornerPoint;
    // array of all calibrate points
    //private Point[] pointsCal = new Point[numCal];
    // generate
    // row major order always
    // Mat to store RGBA and Grayscale camera preview frame
    private Mat mRgba;
    private Mat mGray;

    // KCF Tracker variables
    private TrackerMIL myTacker;
    private Rect2d myROI = new Rect2d(0,0,0,0);
    private int myROIWidth = 70;
    private int myROIHeight = 70;
    private Scalar myROIColor = new Scalar(0,0,0);
    private int tracking_flag = -1;
    private static final int PICK_IMAGE = 100;
    Uri imageUri;
    ImageView imageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        super.setRequestedOrientation (ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        // Request User Permission on Camera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED){
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA}, 1);}

        // OpenCV Loader and Avoid using OpenCV Manager
        if (!OpenCVLoader.initDebug()) {
            Log.e(this.getClass().getSimpleName(), "  OpenCVLoader.initDebug(), not working.");
        } else {
            Log.d(this.getClass().getSimpleName(), "  OpenCVLoader.initDebug(), working.");
        }

        // Setup color seek bar
        colorSeekbar = (SeekBar) findViewById(R.id.colorSeekBar);
        colorSeekbar.setProgress(50);
        setColor(50);
        colorSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
            {
                setColor(progress);
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Setup width seek bar
        widthTextview = (TextView) findViewById(R.id.widthTextView);
        widthSeekbar = (SeekBar) findViewById(R.id.widthSeekBar);
        widthSeekbar.setProgress(myROIWidth - 20);
        widthSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
            {
                // Only allow modification when not tracking
                if(tracking_flag == -1) {
                    myROIWidth = progress + 20;
                }
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Setup width seek bar
        heightTextview = (TextView) findViewById(R.id.heightTextView);
        heightSeekbar = (SeekBar) findViewById(R.id.heightSeekBar);
        heightSeekbar.setProgress(myROIHeight - 20);
        heightSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
            {
                // Only allow modification when not tracking
                if(tracking_flag == -1) {
                    myROIHeight = progress + 20;
                }
            }
            public void onStartTrackingTouch(SeekBar seekBar) {}
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Setup control button
        controlButton = (Button)findViewById((R.id.controlButton));
        controlButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (tracking_flag == -1) {
                    // Modify UI
                    controlButton.setText("STOP");
                    widthTextview.setVisibility(View.INVISIBLE);
                    widthSeekbar.setVisibility(View.INVISIBLE);
                    heightTextview.setVisibility(View.INVISIBLE);
                    heightSeekbar.setVisibility(View.INVISIBLE);

                    // Modify tracking flag
                    tracking_flag = 0;
                }
                else if(tracking_flag >= 1){
                    // Modify UI
                    controlButton.setText("START");
                    widthTextview.setVisibility(View.VISIBLE);
                    widthSeekbar.setVisibility(View.VISIBLE);
                    heightTextview.setVisibility(View.VISIBLE);
                    heightSeekbar.setVisibility(View.VISIBLE);
                    confirmButton.setVisibility(View.VISIBLE);
                    // Tear down myTracker
                    myTacker.clear();
                    // Modify tracking flag
                    tracking_flag = -1;
                }
            }
        });

        // Setup control button
        confirmButton = (Button)findViewById((R.id.confirmButton));
        confirmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (tracking_flag == -1) {

                }
                else if(tracking_flag >= 1 && tracking_flag <= numCal){
                    // calibration state
                    tracking_flag ++;
                }
                else{
                    // operation state
                    confirmButton.setVisibility(View.INVISIBLE);
                    tracking_flag = -2;
                }
            }
        });

        // Setup OpenCV Camera View
        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.opencv_camera_preview);
        // Use main camera with 0 or front camera with 1
        mOpenCvCameraView.setCameraIndex(1);
        // Force camera resolution, ignored since OpenCV automatically select best ones
        // mOpenCvCameraView.setMaxFrameSize(1280, 720);
        mOpenCvCameraView.setCvCameraViewListener(this);

        imageView = (ImageView) findViewById(R.id.imageView);
        openButton = (Button) findViewById(R.id.buttonOpen);
        openButton.setOnClickListener( new View.OnClickListener(){
            @Override
            public void onClick(View v){
                openGallery();
                tracking_flag = -2;
                mOpenCvCameraView.setAlpha(.7f);
            }
        });

    }
    private void openGallery() {
        Intent gallery = new Intent (Intent.ACTION_PICK, MediaStore.Images.Media.INTERNAL_CONTENT_URI);
        startActivityForResult( gallery, PICK_IMAGE);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == PICK_IMAGE){
            imageUri = data.getData();
            imageView.setImageURI(imageUri);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    // Helper Function to map single integer to color scalar
    // https://www.particleincell.com/2014/colormap/
    public void setColor(int value) {
        double a=(1-(double)value/100)/0.2;
        int X=(int)Math.floor(a);
        int Y=(int)Math.floor(255*(a-X));
        double newColor[] = {0,0,0};
        switch(X)
        {
            case 0:
                // r=255;g=Y;b=0;
                newColor[0] = 255;
                newColor[1] = Y;
                break;
            case 1:
                // r=255-Y;g=255;b=0
                newColor[0] = 255-Y;
                newColor[1] = 255;
                break;
            case 2:
                // r=0;g=255;b=Y
                newColor[1] = 255;
                newColor[2] = Y;
                break;
            case 3:
                // r=0;g=255-Y;b=255
                newColor[1] = 255-Y;
                newColor[2] = 255;
                break;
            case 4:
                // r=Y;g=0;b=255
                newColor[0] = Y;
                newColor[2] = 255;
                break;
            case 5:
                // r=255;g=0;b=255
                newColor[0] = 255;
                newColor[2] = 255;
                break;
        }
        myROIColor.set(newColor);
        return;
    }

    // OpenCV Camera Functionality Code
    @Override
    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mGray = new Mat(height, width, CvType.CV_8SC1);
        myWidth = width;
        myHeight = height;
        xPair = new Mat(numCal, 2, 3);
        yPair = new Mat(numCal, 2, 3);
        if (tracking_flag != -2 ) {
            xParam = new Mat();
            yParam = new Mat();
        }
        cornerPoint = new Point();
        myROI = new Rect2d(myWidth / 2 - myROIWidth / 2,
                            myHeight / 2 - myROIHeight / 2,
                            myROIWidth,
                            myROIHeight);
        for (int i = 0; i < widthCal; i++){
            for (int j = 0; j < heightCal; j++){
                xOutcome[i * heightCal + j] = 300*i + 200;
                xPair.put(i * heightCal + j, 1, 300*i + 200);
                yOutcome[i * heightCal + j] = 200*j + 150;
                yPair.put(i * heightCal + j, 1, 200*j + 150);
            }
        }

    }

    @Override
    public void onCameraViewStopped() {
        mRgba.release();
        mGray.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        // Timer
        long start = Core.getTickCount();

        // Grab camera frame in rgba and grayscale format
        mRgba = inputFrame.rgba();
        //Core.flip(mRgba, mRgba, 1);
        // Grab camera frame in gray format
        mGray = inputFrame.gray();
        //Core.flip(mGray, mGray, 1);
        // Action based on tracking flag


        if(tracking_flag == -1){
            // Update myROI to keep the window to the center
            myROI.x = myWidth / 3 - myROIWidth / 2;
            myROI.y = myHeight * 2 / 5 - myROIHeight / 2;
            myROI.width = myROIWidth;
            myROI.height = myROIHeight;
        }
        else if(tracking_flag == 0){
            // Initialize KCF Tracker and Start Tracking
            // 1. Create a KCF Tracker
            // 2. Initialize KCF Tracker with grayscale image and ROI
            // 3. Modify tracking flag to start tracking
            // ******************** START YOUR CODE HERE ******************** //
            myTacker = TrackerMIL.create();
            cornerPoint = eyeCorner(mGray, 0.25, myROI, true);
            myTacker.init(mGray, myROI);
            tracking_flag = 1;


            // ******************** END YOUR CODE HERE ******************** //
        }
        else{

            // Update tracking result is succeed
            // If failed, print text "Tracking failure occurred!" at top left corner of the frame
            // Calculate and display "FPS@fps_value" at top right corner of the frame
            // ******************** START YOUR CODE HERE ******************** //
            //TrackingSuccess = myTacker.update(mGray, myROI);
            //if (!TrackingSuccess){
                Imgproc.putText(mRgba,"Tracking Failure", new Point(50,50), Core.FONT_HERSHEY_PLAIN, 2, new Scalar(255,0,0));
            //}
            //Point cornerPoint = eyeCorner(mGray, 0.25, myROI, true);
            Imgproc.circle(mRgba, cornerPoint, 4, new Scalar (0,255,0));
            Point gazeVec = irisCenter(mGray, mRgba, 0.30, myROI, 50,50, 8, true, cornerPoint);

            // ******************** END YOUR CODE HERE ******************** //
//            if (tracking_flag == -2){
//                for (int i = 0; i < myWidth; i++){
//                    for (int j = 0; j < myHeight; j++){
//                        mRgba.put(i,j,0,0,0,128);
//                    }
//                }
//            }
            if (tracking_flag <= numCal && tracking_flag >= 1){
                Imgproc.putText(mRgba,"Calibrating: Please look at the point " + Integer.toString(tracking_flag-1), new Point(50,50), Core.FONT_HERSHEY_PLAIN, 3, new Scalar(255,0,0), 2);
                for (int i = 0; i < numCal; i++){
                    Imgproc.circle(mRgba, new Point(xOutcome[i], yOutcome[i]), 5 , new Scalar(0,0,0), 3);
                }
                Imgproc.circle(mRgba, new Point(xOutcome[tracking_flag-1], yOutcome[tracking_flag-1]), 5 , new Scalar(0,255,0), 3);
                xPair.put(tracking_flag-1,0, gazeVec.x);
                yPair.put(tracking_flag-1,0, gazeVec.y);
                Log.d("Point captured at " + Integer.toString(tracking_flag-1) + ':', Double.toString(gazeVec.x) + Double.toString(gazeVec.y));


            }
            else if (tracking_flag == numCal+1) {
                // line regression
                Imgproc.fitLine(xPair, xParam, Imgproc.CV_DIST_L2, 0, .01, .01);
                Imgproc.fitLine(yPair, yParam, Imgproc.CV_DIST_L2, 0, .01, .01);
                //Log.d("tracking_flag", Integer.toString(tracking_flag));
                tracking_flag ++;
            }

            else if (tracking_flag > numCal+1) {
                Point cursor =  new Point(  xParam.get(3,0)[0] + xParam.get(1,0)[0] / xParam.get(0,0)[0] * (gazeVec.x - xParam.get(2,0)[0]),
                        yParam.get(3,0)[0] + yParam.get(1,0)[0] / yParam.get(0,0)[0] * (gazeVec.y - yParam.get(2,0)[0]) );
                Imgproc.circle(mRgba, cursor, 15, new Scalar(153,51,255), 3);
                Log.d("cursorx", Double.toString(cursor.x));
                Log.d("cursory", Double.toString(cursor.y));

                //Log.d("tracking_flag", Integer.toString(tracking_flag));
//                for (int i = 0; i < 4; i++){
//                    Log.d("xparam " + Integer.toString(i)+ " = ", Double.toString( xParam.get(i,0)[0] ));
//                }
            }
            //
            // Log.d("tracking_flag", Integer.toString(tracking_flag));
        }

        // Draw a rectangle on to the current frame
        Imgproc.rectangle(mRgba,
                          new Point(myROI.x, myROI.y),
                          new Point(myROI.x + myROI.width, myROI.y + myROI.height),
                          myROIColor,4
                );

        // Returned frame will be displayed on the screen
        Log.d("alpha channel of mRgba", Double.toString(mRgba.get(0,0)[3]));
        return mRgba;
    }
    // helper function
    // input grayScale frame, ratio to cut out eye corner, ROI, isLeft
    // output point of detected eye corner (under main frame coordinate)
    private Point eyeCorner(Mat mGray_ , double ratio, Rect2d ROI_, boolean isLeft){
        // get ROI from mGray
        // given ratio find bounds
        int roiNewWidth = (int) (ROI_.width * ratio);
        Rect cornerRoi = new Rect((int) (ROI_.x + ROI_.width - roiNewWidth), (int) ROI_.y, roiNewWidth, (int) ROI_.height);
        Mat cornerImg = new Mat(mGray_, cornerRoi); // reference to subarray
        Mat conved = new Mat();
        // init array
        int[][] intArray = new int[][]{
                { 1, 1, 1,-1,-1,-1},
                { 1, 1,-1,-1,-1,-1},
                { 1,-1,-1,-1,-1,-1},
                { 1, 1, 1, 1, 1, 1}};
        Mat cornerKernel = new Mat(4,6,CvType.CV_8SC1);
        for(int row = 0;row < 4;row++){
            for(int col = 0;col < 6;col++) {
                cornerKernel.put(row, col, intArray[row][col]);
            }
        }
        // filter
        Imgproc.filter2D(cornerImg, conved,-1, cornerKernel);
        int[] temp = new int[1];

        Core.MinMaxLocResult cornerAns = minMaxLoc(conved, null);
        Log.d( Double.toString(cornerAns.maxVal), "max val in c");
        return new Point (cornerAns.maxLoc.x + ROI_.x + ROI_.width - roiNewWidth, cornerAns.maxLoc.y + ROI_.y );
    }
    //
    public int BinCount(int[] arr){
        int len  = arr.length;
        Arrays.sort(arr);
        int max = arr[len-1];
        int h[] = new int[max+1];
        for(int i=0; i<arr.length; i++){
            h[arr[i]] += 1;
        }
        int maxVal = 0;
        int mode = 0 ;
        for(int i = 0; i < h.length; i++){
            if (h[i] > maxVal){
                maxVal = h[i];
                mode = i;
            }
        }
        return mode;
    }

    private int debbule(int[] positiveXEdge, int[] positiveYEdge, int[] negativeXEdge, int[] negativeYEdge, Point[] EdgePoint, int win, boolean isLeft){
        int posLen = positiveXEdge.length;
        int negLen = negativeXEdge.length;
        int pmode = BinCount(positiveXEdge);
        int nmode = BinCount(negativeXEdge);
        int cnt = 0;
        if (nmode > pmode && isLeft){
            Log.e("error", "Iris edge failed, use right eye");
            // don't debbule
            for(int i = 0; i < posLen; i ++){
                EdgePoint[cnt] = new Point (positiveXEdge[i], positiveYEdge[i]);
                cnt ++;
            }
            for (int i = 0; i < negLen; i ++){
                EdgePoint[cnt] = new Point (negativeXEdge[i], negativeYEdge[i]);
                cnt ++;
            }
            return cnt-1;
        }
        for (int i = 0; i < posLen; i ++){
            if (positiveXEdge[i] < pmode + win && positiveXEdge[i] > pmode - win){
                //Log.d("positiveXEdge[i]", Integer.toString(positiveXEdge[i]));
                EdgePoint[cnt] = new Point (positiveXEdge[i], positiveYEdge[i]);
                cnt ++;
            }
        }
        for (int i = 0; i < negLen; i ++){
            if (negativeXEdge[i] < nmode + win && negativeXEdge[i] > nmode - win){
                EdgePoint[cnt] = new Point (negativeXEdge[i], negativeYEdge[i]);
                cnt ++;
            }
        }
        //Log.d("EdgePoint has length", Integer.toString(cnt));
        return cnt-1;
    }

    public float circlefit (Point[] data, Point[] center, int len){
        // take mean
        double xMean = 0;
        double yMean = 0;
        for (int i = 0; i < len; i++){
            Log.d("inside circlefit", Integer.toString(i));
            xMean += data[i].x;
            yMean += data[i].y;
        }
        xMean /= len;
        yMean /= len;
        Log.d("xMean", Double.toString(xMean));
        // u v is data - mean
        double[] u = new double[len];
        double[] v = new double[len];
        for (int i = 0; i < len; i++){
            u[i] = data[i].x - xMean;
            v[i] = data[i].y - yMean;
        }
        // sum of quad arrays
        double Suv = 0;
        double Suu = 0;
        double Svv = 0;
        double[] uu = new double[len];
        double[] vv = new double[len];
        for (int i = 0; i < len; i++){
            Suv += u[i] * v[i];
            Suu += u[i] * u[i];
            Svv += v[i] * v[i];
            uu[i] = u[i] * u[i];
            vv[i] = v[i] * v[i];
        }
        double SuuvSvvv = 0;
        double SuuuSuvv = 0;
        for (int i = 0; i < len; i++){
            SuuvSvvv += uu[i] * v[i] + v[i] * vv[i];
            SuuuSuvv += uu[i] * u[i] + u[i] * vv[i];
        }
        SuuuSuvv /= 2;
        SuuvSvvv /= 2;
        Mat A = new Mat(2,2, CvType.CV_32F);
        Mat b = new Mat(2,1, CvType.CV_32F);
        A.put(0,0,Suu);
        A.put(0,1,Suv);
        A.put(1,0,Suv);
        A.put(1,1,Svv);
        A.put(0,0,Suu);
        b.put(0,0,SuuuSuvv);
        b.put(1,0,SuuvSvvv);
        Mat ans = new Mat();
        boolean solveSuccess = Core.solve(A, b, ans, DECOMP_SVD);
        if (!solveSuccess)
            Log.e("error", "Fail to solve matrix in circleFitting");
        double[] xc_1 = ans.get(0,0);
        double[] yc_1 = ans.get(1,0);

        xc_1[0] += xMean;
        yc_1[0] += yMean;

        Log.d("ans xc_1", Double.toString(xc_1[0]) );
        Log.d("ans yc_1", Double.toString(yc_1[0]) );

        Mat Ri_1 = new Mat(1, len, CV_32F);
        for (int i = 0; i < len; i++){
            Ri_1.put(0, i, (data[i].x - xc_1[0]) * (data[i].x - xc_1[0]) + (data[i].y - yc_1[0]) * (data[i].y - yc_1[0]));
        }
        Mat sqRi_1 = new Mat();
        Core.sqrt(Ri_1, sqRi_1);
        float R  = 0;
        float[] tmp = new float[1];
        for (int i = 0; i < len; i++){
            sqRi_1.get(0, i, tmp);
            R += tmp[0];
        }
        R /= len;
        center[0] = new Point(xc_1[0], yc_1[0]);
        return R;
    }

    private Point irisCenter(Mat mGray_, Mat mRgba_, double ratio, Rect2d ROI_, int negSampleNum, int posSampleNum, int win, boolean isLeft, Point cornerPoint){
        int roiNewWidth = (int) (ROI_.width * (1 - ratio));
        Rect irisRoi = new Rect((int) (ROI_.x), (int) ROI_.y, roiNewWidth, (int) ROI_.height);
        Mat irisImg = new Mat(mGray_, irisRoi); // reference to subarray
        Mat sobelImg = new Mat();
        Imgproc.Sobel(irisImg, sobelImg, CV_16S, 1, 0);
        //populate these array
        int[] negativeXEdge = new int[negSampleNum];
        int[] negativeYEdge = new int[negSampleNum];
        int[] positiveXEdge = new int[posSampleNum];
        int[] positiveYEdge = new int[posSampleNum];
        for (int i = 0; i < negSampleNum; i++){
            // bad run time
            Core.MinMaxLocResult tmp = minMaxLoc(sobelImg, null);
            sobelImg.put( (int) tmp.minLoc.y, (int) tmp.minLoc.x, 0); // 0 cannot be max or min
            negativeXEdge[i] = (int) (tmp.minLoc.x);
            negativeYEdge[i] = (int) (tmp.minLoc.y);
        }
        for (int j = 0; j < posSampleNum; j++){
            Core.MinMaxLocResult tmp = minMaxLoc(sobelImg, null);
            sobelImg.put( (int) tmp.maxLoc.y, (int) tmp.maxLoc.x, 0); // 0 cannot be max or min
            positiveXEdge[j] = (int) (tmp.maxLoc.x);
            Log.d("get sobel", Integer.toString(positiveXEdge[j]));
            positiveYEdge[j] = (int) (tmp.maxLoc.y);
        }
        Point[] EdgePoint = new Point[negSampleNum+posSampleNum];
        Point[] center = new Point[1];
        int validPoints = debbule(positiveXEdge, positiveYEdge, negativeXEdge, negativeYEdge, EdgePoint, win, true);
        for (int i = 0; i < validPoints; i++){
            Imgproc.circle(mRgba_, new Point (EdgePoint[i].x + ROI_.x, EdgePoint[i].y + ROI_.y), 1, new Scalar(0,255,255));
        }
        Log.d("Return, length", Integer.toString(EdgePoint.length));
        float radius = circlefit(EdgePoint, center, validPoints);
        Imgproc.circle(mRgba_, new Point (center[0].x + ROI_.x, center[0].y + ROI_.y), (int) (radius), new Scalar(0,255,0));
        Imgproc.circle(mRgba_, new Point (center[0].x + ROI_.x, center[0].y + ROI_.y), 4, new Scalar(0,0,255));
        // original mapping
//        Imgproc.circle(mRgba_, new Point (10 * (cornerPoint.x - center[0].x - ROI_.x) + 20, -5 * (cornerPoint.y - center[0].y - ROI_.y) + 200), 15, new Scalar(153,51,255), 3);

        //Mat gazeX = new Mat(12, 1, CV_16S);
        //gazeX.put ()


        Imgproc.putText(mRgba_,"Gaze vector = [" + Integer.toString((int) (cornerPoint.x - center[0].x - ROI_.x)) + " , " + Integer.toString((int)(cornerPoint.y - center[0].y - ROI_.y)) + " ]", new Point(50,100), Core.FONT_HERSHEY_PLAIN, 3, new Scalar(0,0,255), 2);

        //Log.d("cursor x", Integer.toString((int) (10 * (cornerPoint.x - center[0].x - ROI_.x) + 20)));
        //Log.d("cursor y", Integer.toString((int) (-5 * (cornerPoint.y - center[0].y - ROI_.y) + 200)));
        return new Point(cornerPoint.x - center[0].x - ROI_.x, cornerPoint.y - center[0].y - ROI_.y);
    }
}
