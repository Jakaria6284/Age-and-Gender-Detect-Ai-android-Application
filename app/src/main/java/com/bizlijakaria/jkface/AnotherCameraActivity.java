package com.bizlijakaria.jkface;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayInputStream;
import java.io.IOException;


import java.io.ByteArrayOutputStream;

public class AnotherCameraActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {
    private static final String TAG = "CameraActivity";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 101;
    private static final int IMAGE_CAPTURE_DELAY = 3000; // 3 seconds

    private Mat mRgba;
    private Mat mGray;
    private FaceDetection faceDetection;
    private CameraBridgeViewBase mOpenCvCameraView;
    private ImageView capturedImageView;
    private boolean isImageCaptured = false;
    private Handler handler;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    Log.i(TAG, "OpenCV loaded successfully");
                    if (mOpenCvCameraView != null) {
                        mOpenCvCameraView.enableView();
                    } else {
                        Log.e(TAG, "Camera view (mOpenCvCameraView) is null in LoaderCallback");
                    }
                    break;
                default:
                    super.onManagerConnected(status);
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // Hide the action bar and keep the screen on
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_camera);

        // Initialize ImageView
        capturedImageView = findViewById(R.id.captured_image);

        // Initialize Handler
        handler = new Handler();

        // Check camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            // Request camera permission if not granted
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            // Initialize OpenCV camera view
            initializeCameraView();
        }

        faceDetection = new FaceDetection(this);

        // Check if the Haar cascade classifier is loaded successfully
        if (faceDetection.isClassifierLoaded()) {
            Log.d(TAG, "Haar cascade classifier loaded successfully");
        } else {
            Log.e(TAG, "Haar cascade classifier failed to load");
        }
    }

    private void initializeCameraView() {
        mOpenCvCameraView = findViewById(R.id.frame_Surface);
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
            mOpenCvCameraView.setCvCameraViewListener(this);

            mOpenCvCameraView.setCameraPermissionGranted(); // Ensure camera permission is granted
            mOpenCvCameraView.enableView(); // Start camera preview
        } else {
            Log.e(TAG, "Camera view (mOpenCvCameraView) is null in initializeCameraView");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, initialize camera view
                initializeCameraView();
            } else {
                // Permission denied, show a message or handle it gracefully
                Log.w(TAG, "Camera permission denied");
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.disableView(); // Stop camera preview
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.disableView(); // Release camera resources
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mGray = new Mat(height, width, CvType.CV_8UC1);
    }

    @Override
    public void onCameraViewStopped() {
        Log.i(TAG, "Camera view stopped");
        mRgba.release();
        mGray.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();

        if (faceDetection != null && faceDetection.isClassifierLoaded()) {
            Mat result = faceDetection.detectFaces(mRgba);
            Rect[] facesArray = faceDetection.getFacesArray();
            if (facesArray.length > 0 && !isImageCaptured) {
                isImageCaptured = true;
                handler.postDelayed(() -> captureImage(result), IMAGE_CAPTURE_DELAY);
            }
        }

        return mRgba;
    }

    private void captureImage(Mat frame) {
        // Convert the Mat to a Bitmap
        Bitmap bitmap = Bitmap.createBitmap(frame.cols(), frame.rows(), Bitmap.Config.ARGB_8888);
        org.opencv.android.Utils.matToBitmap(frame, bitmap);

        // Rotate the Bitmap by 90 degrees if necessary
         Matrix matrix = new Matrix();
       //matrix.postRotate(90); // Adjust the rotation angle as needed
        Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        // Convert the rotated Bitmap to a byte array
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        rotatedBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        byte[] byteArray = stream.toByteArray();

        // Start the second activity and pass the byte array
        Intent intent = new Intent(this, DisplayImageActivity.class);
        intent.putExtra("captured_image", byteArray);
        startActivity(intent);
        finish();

        Log.i(TAG, "Image captured, rotated, and intent started successfully");
    }

}
