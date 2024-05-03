package com.bizlijakaria.jkface;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class age_gender_recognition {
    private static final String TAG = "age_gender_recognition";
    private Interpreter interpreter;
    private int height=0;
    private int width=0;

    private CascadeClassifier cascadeClassifier;

    public age_gender_recognition(AssetManager assetManager, Context context, String modelPath, int inputSize) {
        try {
            // Load TensorFlow Lite model
            interpreter = new Interpreter(loadModelFile(assetManager, modelPath));
            Log.d(TAG, "TensorFlow Lite model loaded successfully");

            // Load Haar cascade classifier for face detection
            loadCascadeClassifier(context);
        } catch (IOException e) {
            Log.e(TAG, "Error loading model or cascade classifier: " + e.getMessage());
            e.printStackTrace();
        }
    }
  public Mat recognizeImage(Mat mat_image)
  {
      Core.flip(mat_image.t(),mat_image,1);

      Mat grayscaleImage=new Mat();
      Imgproc.cvtColor(mat_image,grayscaleImage,Imgproc.COLOR_RGB2GRAY);
       height=grayscaleImage.height();
       width=grayscaleImage.width();
       int absoluteFaceSize=(int)(height*0.1);
      MatOfRect faces=new MatOfRect();
      //hell0

      if(cascadeClassifier!=null)
      {
          cascadeClassifier.detectMultiScale(grayscaleImage,faces,1.1,2,2,
                  new Size(absoluteFaceSize,absoluteFaceSize),new Size()
                  );
      }

      Rect[] faceArray=faces.toArray();
      for(int i=0;i<faceArray.length;i++)
      {
          Imgproc.rectangle(mat_image,faceArray[i].tl(),faceArray[i].br(),new Scalar(0,255,0,255),2);
      }

       Core.flip(mat_image.t(),mat_image,0);

     return mat_image;
  }
    private MappedByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException {
        try (AssetFileDescriptor fileDescriptor = assetManager.openFd(modelPath);
             FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor())) {
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        }
    }

    private void loadCascadeClassifier(Context context) {
        try {
            InputStream is = context.getResources().openRawResource(R.raw.haarcascade_frontalface_alt);
            File cascadeDir = context.getDir("cascade", Context.MODE_PRIVATE);
            File cascadeFile = new File(cascadeDir, "haarcascade_frontalface_alt.xml");
            FileOutputStream os = new FileOutputStream(cascadeFile);

            byte[] buffer = new byte[4096];
            int bytesRead;

            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();

            cascadeClassifier = new CascadeClassifier(cascadeFile.getAbsolutePath());
            if (cascadeClassifier.empty()) {
                Log.e(TAG, "Failed to load Haar cascade classifier");
            } else {
                Log.d(TAG, "Haar cascade classifier loaded successfully");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error loading Haar cascade classifier: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean isModelLoaded() {
        return interpreter != null && cascadeClassifier != null && !cascadeClassifier.empty();
    }
}
