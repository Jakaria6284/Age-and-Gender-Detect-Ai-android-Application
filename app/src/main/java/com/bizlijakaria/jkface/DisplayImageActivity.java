package com.bizlijakaria.jkface;

import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.airbnb.lottie.LottieAnimationView;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.Arrays;

public class DisplayImageActivity extends AppCompatActivity {

    TextView result, confidence;
    private static final int REQUEST_IMAGE_PICK = 1;

    ImageView imageView;
    LottieAnimationView lottieAnimationView;
    int imageSize = 224; // Input size adjusted to 96x96
    Button uploadButton, detail;
    Bitmap bitmap;
    int[] intValues = new int[imageSize * imageSize];
    private int currentRotation = 0; // Track current rotation angle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_image);

        result = findViewById(R.id.result);
        confidence = findViewById(R.id.confidence);
        imageView = findViewById(R.id.display_image_view);
        uploadButton = findViewById(R.id.button2);
        detail = findViewById(R.id.detail);

        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Open gallery to select an image
                Intent galleryIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(galleryIntent, REQUEST_IMAGE_PICK);
            }
        });

        detail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Rotate the image by 90 degrees
                currentRotation = (currentRotation + 90) % 360;
                rotateImage(currentRotation);
            }
        });

        // Get the byte array from the Intent
        byte[] byteArray = getIntent().getByteArrayExtra("captured_image");

        if (byteArray != null) {
            // Convert the byte array back to a Bitmap
            bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
            imageView.setImageBitmap(bitmap);
        } else {
            Toast.makeText(this, "Null image", Toast.LENGTH_SHORT).show();
        }

        if (bitmap != null) {
            processAndClassifyImage(bitmap);
        } else {
            Log.e("DetectionActivity", "No bitmap received in the intent");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == REQUEST_IMAGE_PICK) {
            if (data != null && data.getData() != null) {
                Uri selectedImageUri = data.getData();
                try {
                    Bitmap selectedBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), selectedImageUri);
                    processAndClassifyImage(selectedBitmap);
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void processAndClassifyImage(Bitmap image) {
        int dimension = Math.min(image.getWidth(), image.getHeight());
        Bitmap thumbnail = ThumbnailUtils.extractThumbnail(image, dimension, dimension);
        imageView.setImageBitmap(thumbnail);

        Bitmap scaledBitmap = Bitmap.createScaledBitmap(thumbnail, imageSize, imageSize, false);
        classifyImage(scaledBitmap);
    }

    private void preprocessImage(Bitmap bitmap, ByteBuffer buffer) {
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        int pixel = 0;
        for (int i = 0; i < imageSize; ++i) {
            for (int j = 0; j < imageSize; ++j) {
                final int val = intValues[pixel++];

                // Scale pixel values to range 0 to 1
                buffer.putFloat(((val >> 16) & 0xFF) / 255.0f);
                buffer.putFloat(((val >> 8) & 0xFF) / 255.0f);
                buffer.putFloat((val & 0xFF) / 255.0f);
            }
        }
    }

    public void classifyImage(Bitmap image) {
        try {
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4); // Adjust based on your device
            Interpreter model = new Interpreter(loadModelFile(), options);

            ByteBuffer inputBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3);
            inputBuffer.order(ByteOrder.nativeOrder());
            preprocessImage(image, inputBuffer);

            float[][] output = new float[1][12]; // Assuming 12 classes

            model.run(inputBuffer, output);

            Log.d("ModelOutput", "Model output: " + Arrays.toString(output[0]));

            int maxIndex = argmax(output[0]);
            float confidenceValue = output[0][maxIndex];
            String[] classes = {"Aishwarya rai","Angelina jolie ","Arnold schwarzenegger"," Bhuvan bam"," Brad pitt","Courteney Cox","David Schwimmer","Dhoni","Hardik pandya","",""};
            String predictedClass = classes[maxIndex];

            result.setText(predictedClass);
            confidence.setText(String.format("%.1f%%", confidenceValue * 100));

            model.close();
        } catch (IOException e) {
            Log.e("Model", "Error loading model: " + e.getMessage());
        }
    }

    private int argmax(float[] array) {
        int maxIndex = 0;
        for (int i = 1; i < array.length; i++) {
            if (array[i] > array[maxIndex]) {
                maxIndex = i;
            }
        }
        return maxIndex;
    }

    private ByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = getAssets().openFd("model3.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private void rotateImage(int rotation) {
        if (bitmap != null) {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);
            Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            imageView.setImageBitmap(rotatedBitmap);
        }
    }
}
