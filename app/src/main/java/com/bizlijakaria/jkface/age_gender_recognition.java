package com.bizlijakaria.jkface;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
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
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Map;
import java.util.TreeMap;

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

            Rect roi=new Rect((int)faceArray[i].tl().x,(int)faceArray[i].tl().y,
                    (int)(faceArray[i].br().x)-(int) (faceArray[i].tl().x),
                    (int)faceArray[i].br().y-(int) (faceArray[i].tl().y));

            Mat cropped=new Mat(grayscaleImage,roi);
            Mat cropped_rgba=new Mat(mat_image,roi);

            Bitmap bitmap=null;
            bitmap=Bitmap.createBitmap(cropped_rgba.cols(),cropped_rgba.rows(),Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(cropped_rgba,bitmap);
            Bitmap scaledBitmap=Bitmap.createScaledBitmap(bitmap,96,96,false);
            ByteBuffer byteBuffer=convertBitmapToByteBuffer(scaledBitmap);
            Object[] input=new Object[1];
            input[0]=byteBuffer;

            Map<Integer,Object> output_map=new TreeMap<>();
            float[][] age=new float[1][1];
            float[][]gender=new float[1][1];

            output_map.put(0,age);
            output_map.put(1,gender);

            interpreter.runForMultipleInputsOutputs(input,output_map);

            Object age_o=output_map.get(0);
            Object gender_o=output_map.get(1);

            int age_value=(int)(float) Array.get(Array.get(age_o,0),0);
            float gender_value=(float) Array.get(Array.get(gender_o,0),0);

            if(gender_value>0.70)
            {
                Imgproc.putText(cropped_rgba,"Female"+age_value,new Point(10,20),1,1.5,new Scalar(255,0,0,255),
                        2 );
            }else
            {
                Imgproc.putText(cropped_rgba,"male"+age_value,new Point(10,20),1,1.5,new Scalar(0,0,255,255),
                        2 );
            }

            Log.d("Age_Gender_recognization","out"+age_value+","+gender_value);
            cropped_rgba.copyTo(new Mat(mat_image,roi));

        }

        Core.flip(mat_image.t(),mat_image,0);

        return mat_image;
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap scaledBitmap) {

        ByteBuffer byteBuffer;
        int size_image=96;

        byteBuffer=ByteBuffer.allocateDirect(4*1*size_image*size_image*3);
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues=new int[size_image*size_image];
        scaledBitmap.getPixels(intValues,0,scaledBitmap.getWidth(),0,0,scaledBitmap.getWidth(),scaledBitmap.getHeight());
        int pixel=0;
        for(int i=0;i<size_image;++i)
        {
            for(int j=0;j<size_image;++j)
            {
                final  int val=intValues[pixel++];
                byteBuffer.putFloat((((val>>16)& 0xFF))/255.0f);
                byteBuffer.putFloat((((val>>8)& 0xFF))/255.0f);
                byteBuffer.putFloat(((val & 0xFF))/255.0f);
            }
        }
        return  byteBuffer;
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
