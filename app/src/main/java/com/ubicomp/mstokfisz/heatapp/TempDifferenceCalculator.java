package com.ubicomp.mstokfisz.heatapp;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.icu.text.SimpleDateFormat;
import android.media.MediaScannerConnection;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;
import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

import static com.ubicomp.mstokfisz.heatapp.RotationHandler.rotateBitmap;

public class TempDifferenceCalculator extends AsyncTask<Void, Void, Void> {
    static Boolean running = false;
    private static LinkedList<MeasurementDataHolder> measurementsQueue = new LinkedList<>();
    private static double[] diffSum;
    private static double[] diffFreq; // Frequency of changes
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
                Log.d(TAG, Integer.toString(measurementsQueue.size()));
                MeasurementDataHolder firstMeasurement = measurementsQueue.removeFirst(); // Get first measurement and delete from queue
                MeasurementDataHolder secondMeasurement = measurementsQueue.getFirst(); // Get second (now first) but do not delete from queue
                if (width == 0 || height == 0 || diffSum == null) { // for first measurement set everything
                    width = firstMeasurement.width;
                    height = firstMeasurement.height;
                    diffSum = new double[firstMeasurement.data.length];
                    diffFreq = new double[firstMeasurement.data.length];
                    Arrays.fill(diffSum, 0);
                    Arrays.fill(diffFreq, 0);
                }
                for (int i = 0; i < firstMeasurement.data.length; i++) { // Assume same size for now
                    diffSum[i] += Math.abs(secondMeasurement.data[i] - firstMeasurement.data[i]); // For now get the absolute value
                    if (secondMeasurement.data[i] != firstMeasurement.data[i]) { // If the temp changed then add
                        diffFreq[i]++;
                    }
//                    diffSum[i] += Math.abs((secondMeasurement.data[i] - secondMeasurement.minVal) - (firstMeasurement.data[i] - firstMeasurement.minVal)); // Alternative mode with subtracting minValues
                }

            }
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ssZ", Locale.getDefault());
        String formatedDate = sdf.format(new Date());
        Log.d(TAG, Arrays.toString(diffSum));
        Bitmap diffSumHeatMap = rotateBitmap(HeatMapGenerator.generateHeatMap(new MeasurementDataHolder(diffSum, Arrays.stream(diffSum).min().getAsDouble(), Arrays.stream(diffSum).max().getAsDouble(), width, height)));
        Bitmap diffFreqHeatMap = rotateBitmap(HeatMapGenerator.generateHeatMap(new MeasurementDataHolder(diffFreq,  Arrays.stream(diffFreq).min().getAsDouble(), Arrays.stream(diffFreq).max().getAsDouble(), width, height)));
        String diffSumFileName = "DifferenceSum-" + formatedDate + ".png";
        String diffFreqFileName = "ChangeFrequency-" + formatedDate + ".png";
        File dir = new File(Environment.getExternalStorageDirectory() + File.separator + "HeatApp");
        if(!dir.exists())
            dir.mkdirs();
        File diffSumFile = new File(dir,diffSumFileName);
        File diffFreqFile = new File(dir, diffFreqFileName);
        FileOutputStream fOut;
        try {
            fOut = new FileOutputStream(diffSumFile);
            diffSumHeatMap.compress(Bitmap.CompressFormat.PNG, 85, fOut);
            fOut = new FileOutputStream(diffFreqFile);
            diffFreqHeatMap.compress(Bitmap.CompressFormat.PNG, 85, fOut);
            fOut.flush();
            fOut.close();
            MediaScannerConnection.scanFile(context,
                    new String[]{diffSumFile.getAbsolutePath(), diffFreqFile.getAbsolutePath()}, null,
                    (path, uri) -> {
                        Log.i("ExternalStorage", "Scanned " + path + ":");
                        Log.i("ExternalStorage", "-> uri=" + uri);
                    });
        } catch (IOException e) {
            Log.d(TAG, "File not saved");
            Log.e(TAG, Objects.requireNonNull(e.getMessage()));
        }
        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        progDialog.dismiss();
        if (context != null)
            Toast.makeText(context, "Heatmap created succesfully!", Toast.LENGTH_SHORT).show();
    }

    TempDifferenceCalculator(MainActivity context) {
        this.execute();
        running = true;
        this.context = context;
    }

    void stop() {
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
