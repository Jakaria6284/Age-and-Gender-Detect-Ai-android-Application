package com.bizlijakaria.jkface;

import android.content.Context;
import android.util.Log;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class FaceDetection {
    private static final String TAG = "FaceDetection";
    private CascadeClassifier cascadeClassifier;
    private Rect[] facesArray;

    public FaceDetection(Context context) {
        try {
            loadCascadeClassifier(context);
        } catch (IOException e) {
            Log.e(TAG, "Error loading cascade classifier: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Mat detectFaces(Mat matImage) {
        Core.flip(matImage.t(), matImage, 1);

        Mat grayscaleImage = new Mat();
        Imgproc.cvtColor(matImage, grayscaleImage, Imgproc.COLOR_RGB2GRAY);
        int height = grayscaleImage.height();
        int absoluteFaceSize = (int) (height * 0.1);
        MatOfRect faces = new MatOfRect();

        if (cascadeClassifier != null) {
            cascadeClassifier.detectMultiScale(grayscaleImage, faces, 1.1, 2, 2,
                    new Size(absoluteFaceSize, absoluteFaceSize), new Size());
        }

        facesArray = faces.toArray();
        for (Rect face : facesArray) {
            Imgproc.rectangle(matImage, face.tl(), face.br(), new Scalar(0, 255, 0, 255), 2);
        }

        Core.flip(matImage.t(), matImage, 0);
        return matImage;
    }

    private void loadCascadeClassifier(Context context) throws IOException {
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
    }

    public boolean isClassifierLoaded() {
        return cascadeClassifier != null && !cascadeClassifier.empty();
    }

    public Rect[] getFacesArray() {
        return facesArray;
    }
}
