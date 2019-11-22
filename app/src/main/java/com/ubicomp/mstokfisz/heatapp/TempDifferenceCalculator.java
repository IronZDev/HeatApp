package com.ubicomp.mstokfisz.heatapp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.icu.text.SimpleDateFormat;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;
import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

public class TempDifferenceCalculator extends AsyncTask<Void, Void, Void> {
    private static Boolean running = true;
    private static LinkedList<MeasurementDataHolder> measurementsQueue = new LinkedList<MeasurementDataHolder>();
    private static double[] diffSum;
    private static int width = 0;
    private static int height = 0;
    private static final String TAG = "TempDiffCalc";
    private static AlertDialog progDialog;
    @SuppressLint("StaticFieldLeak")
    private Context context;

    @Override
    protected Void doInBackground(Void... Voids) {
        while (running) {
            if (measurementsQueue != null && measurementsQueue.size() >= 2) { // If there are two frames ready
                MeasurementDataHolder firstMeasurement = measurementsQueue.getFirst(); // Get first measurement and delete from queue
                MeasurementDataHolder secondMeasurement = measurementsQueue.peekFirst(); // Get second (now first) but do not delete from queue
                if (width == 0 || height == 0 || diffSum == null) { // for first measurement set everything
                    width = firstMeasurement.width;
                    height = firstMeasurement.height;
                    diffSum = new double[firstMeasurement.data.length];
                    Arrays.fill(diffSum, 0.0);
                }
                for (int i = 0; i < firstMeasurement.data.length; i++) { // Assume same size for now
                    diffSum[i] += Math.abs(secondMeasurement.data[i] - firstMeasurement.data[i]); // For now get the absolute value
                }

            }
        }
        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ssZ", Locale.getDefault());
        String formatedDate = sdf.format(new Date());
        Bitmap differencesHeatMap = HeatMapGenerator.generateHeatMap(new MeasurementDataHolder(diffSum, Arrays.stream(diffSum).min().getAsDouble(), Arrays.stream(diffSum).max().getAsDouble(), width, height));
        String file_path = Environment.getExternalStorageDirectory().getAbsolutePath() +
                "/FaceDetection";
        Log.d(TAG, "File saved to "+file_path);
        String fileName = "FLIROne-" + formatedDate + ".png";
        File dir = new File(file_path);
        if(!dir.exists())
            dir.mkdirs();
        File file = new File(dir,fileName);
        FileOutputStream fOut = null;
        try {
            fOut = new FileOutputStream(file);
            differencesHeatMap.compress(Bitmap.CompressFormat.PNG, 85, fOut);
            fOut.flush();
            fOut.close();
            MediaScannerConnection.scanFile(context,
                    new String[]{file_path + "/" + fileName}, null,
                    new MediaScannerConnection.OnScanCompletedListener() {
                        @Override
                        public void onScanCompleted(String path, Uri uri) {
                            Log.i("ExternalStorage", "Scanned " + path + ":");
                            Log.i("ExternalStorage", "-> uri=" + uri);
                        }

                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
        progDialog.dismiss();
        if (context != null)
            Toast.makeText(context, "Heatmap created succesfully!", Toast.LENGTH_SHORT).show();
    }

    TempDifferenceCalculator(MainActivity context) {
        this.execute();
        this.context = context;
        FaceDetector.calculateTempDifference = true;
    }

    void stop() {
        FaceDetector.calculateTempDifference = false;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setCancelable(false); // if you want user to wait for some process to finish,
        builder.setView(R.layout.loading_dialog);
        progDialog = builder.create(); // Show a dialog if not finished when stopped;
        progDialog.show();
        running = false;
        EventBus.getDefault().unregister(this);
    }

    static void newMeasurement(MeasurementDataHolder measurement) {
        measurementsQueue.add(measurement);
    }
}
