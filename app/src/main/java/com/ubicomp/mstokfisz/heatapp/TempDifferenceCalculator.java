package com.ubicomp.mstokfisz.heatapp;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.icu.text.DecimalFormat;
import android.icu.text.SimpleDateFormat;
import android.media.MediaScannerConnection;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;
import com.ubicomp.mstokfisz.heatapp.events.MeasurementReadyEvent;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

import static com.ubicomp.mstokfisz.heatapp.RotationHandler.rotateBitmap;

public class TempDifferenceCalculator extends AsyncTask<Void, Void, Void> {
    static Boolean running = false;
    private static LinkedList<MeasurementDataHolder> measurementsQueue;
    private MeasurementDataHolder firstMeasurement;
    private ResultHolder resultsEach2;
    private int width = 0;
    private int height = 0;
    private int numberOfCalculationsPerformed = 0;
    private long elapsedTime;
    private static final String TAG = "TempDiffCalc";
    private static AlertDialog progDialog;
    @SuppressLint("StaticFieldLeak")
    private Context context;

    private class ResultHolder {
        private double[] diffSum;
        private double[] diffFreq; // Frequency of changes

        ResultHolder(MeasurementDataHolder measurement) {
            if (measurement.pointsList != null) {
                diffSum = new double[measurement.pointsList.size()];
                diffFreq = new double[measurement.pointsList.size()];
            } else {
                diffSum = new double[measurement.data.length];
                diffFreq = new double[measurement.data.length];
            }
            Arrays.fill(diffSum, 0);
            Arrays.fill(diffFreq, 0);
            Log.d(TAG,"Initialized");
        }
    }

    @Override
    protected Void doInBackground(Void... Voids) {
        long startTime = System.nanoTime();
        ResultHolder resultsEach3 = null;
        ResultHolder resultsEach5 = null;
        ResultHolder resultsEach10 = null;
        int photosCreated = 0;
        String date = null;
        while (running) {
            if (measurementsQueue != null && measurementsQueue.size() >= 2) { // If there are two frames ready
                MeasurementDataHolder firstMeasurement = measurementsQueue.removeFirst(); // Get first measurement and delete from queue
                MeasurementDataHolder secondMeasurement = measurementsQueue.getFirst(); // Get second (now first) but do not delete from queue
                if(this.firstMeasurement == null) {
                    this.firstMeasurement = firstMeasurement;
                    width = firstMeasurement.width;
                    height = firstMeasurement.height;
                    resultsEach2 = new ResultHolder(firstMeasurement);
                    resultsEach3 = new ResultHolder(firstMeasurement);
                    resultsEach5 = new ResultHolder(firstMeasurement);
                    resultsEach10 = new ResultHolder(firstMeasurement);
                }
                numberOfCalculationsPerformed++;
                compareTwoMeasurements(firstMeasurement, secondMeasurement, resultsEach2);
                if (numberOfCalculationsPerformed % 3 == 0) {
                    compareTwoMeasurements(firstMeasurement, secondMeasurement, resultsEach3);
                }
                if (numberOfCalculationsPerformed % 5 == 0) {
                    compareTwoMeasurements(firstMeasurement, secondMeasurement, resultsEach5);
                    ResultHolder tempHolder = new ResultHolder(this.firstMeasurement);
                    compareTwoMeasurements(this.firstMeasurement, secondMeasurement, tempHolder);
                    if (photosCreated == 0) { // Create new folder for measurements
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ssZ", Locale.getDefault());
                        date = sdf.format(new Date());
                    }
                    photosCreated++;
                    saveBitmap(rotateBitmap(HeatMapGenerator.generateHeatMap(new MeasurementDataHolder(tempHolder.diffSum, Arrays.stream(tempHolder.diffSum).min().getAsDouble(), Arrays.stream(tempHolder.diffSum).max().getAsDouble(), width, height, null))),
                            photosCreated+".png",
                            "dynamicDiffSum"+date);
                }
                if (numberOfCalculationsPerformed % 10 == 0) {
                    compareTwoMeasurements(firstMeasurement, secondMeasurement, resultsEach10);
                }
            }
        }
        if (resultsEach2 != null) {
            elapsedTime = System.nanoTime() - startTime;
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ssZ", Locale.getDefault());
            String formatedDate = sdf.format(new Date());
            saveBitmap(rotateBitmap(HeatMapGenerator.generateHeatMap(new MeasurementDataHolder(resultsEach2.diffSum, Arrays.stream(resultsEach2.diffSum).min().getAsDouble(), Arrays.stream(resultsEach2.diffSum).max().getAsDouble(), width, height, null))),
                    "DifferenceSumEach2.png",
                    formatedDate);
            saveBitmap(rotateBitmap(HeatMapGenerator.generateHeatMap(new MeasurementDataHolder(resultsEach2.diffFreq, Arrays.stream(resultsEach2.diffFreq).min().getAsDouble(), Arrays.stream(resultsEach2.diffFreq).max().getAsDouble(), width, height, null))),
                    "ChangeFrequency.png",
                    formatedDate);
            saveBitmap(rotateBitmap(HeatMapGenerator.generateHeatMap(new MeasurementDataHolder(resultsEach3.diffSum, Arrays.stream(resultsEach3.diffSum).min().getAsDouble(), Arrays.stream(resultsEach3.diffSum).max().getAsDouble(), width, height, null))),
                    "DifferenceSumEach3.png",
                    formatedDate);
            saveBitmap(rotateBitmap(HeatMapGenerator.generateHeatMap(new MeasurementDataHolder(resultsEach5.diffSum, Arrays.stream(resultsEach5.diffSum).min().getAsDouble(), Arrays.stream(resultsEach5.diffSum).max().getAsDouble(), width, height, null))),
                    "DifferenceSumEach5.png",
                    formatedDate);
            saveBitmap(rotateBitmap(HeatMapGenerator.generateHeatMap(new MeasurementDataHolder(resultsEach10.diffSum, Arrays.stream(resultsEach10.diffSum).min().getAsDouble(), Arrays.stream(resultsEach10.diffSum).max().getAsDouble(), width, height, null))),
                    "DifferenceSumEach10.png",
                    formatedDate);
        }
        return null;
    }

    private void compareTwoMeasurements(MeasurementDataHolder firstMeasurement, MeasurementDataHolder secondMeasurement, ResultHolder resHolder) {
        if (firstMeasurement.pointsList != null) { // Face tracking mode
            for (int pointNum = 0; pointNum < firstMeasurement.pointsList.size(); pointNum++) { // Assume same size for now
                Integer firstPointNum = firstMeasurement.pointsList.get(pointNum);
                Integer secondPointNum = secondMeasurement.pointsList.get(pointNum);
                if (firstPointNum != null && secondPointNum != null) {
                    resHolder.diffSum[pointNum] += Math.abs(secondMeasurement.data[secondPointNum] - firstMeasurement.data[firstPointNum]); // For now get the absolute value
                    if (secondMeasurement.data[secondPointNum] != firstMeasurement.data[firstPointNum]) { // If the temp changed then add
                        resHolder.diffFreq[pointNum]++;
                    }
                }
//                    diffSum[i] += Math.abs((secondMeasurement.data[i] - secondMeasurement.minVal) - (firstMeasurement.data[i] - firstMeasurement.minVal)); // Alternative mode with subtracting minValues
            }
        } else { // Grab whole scene
            for (int pointNum = 0; pointNum < firstMeasurement.data.length; pointNum++) { // Assume same size for now
                resHolder.diffSum[pointNum] += Math.abs(secondMeasurement.data[pointNum] - firstMeasurement.data[pointNum]); // For now get the absolute value
                if (secondMeasurement.data[pointNum] != firstMeasurement.data[pointNum]) { // If the temp changed then add
                    resHolder.diffFreq[pointNum]++;
                }
            }
        }
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        if (progDialog != null)
            progDialog.dismiss();
        if (context != null) {
            if (resultsEach2 != null) {
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle("Measurement statistics")
                        .setMessage("Lowest temp difference sum: " + Arrays.stream(resultsEach2.diffSum).min().getAsDouble() + "K\n"
                                + "Highest temp difference sum: " + Arrays.stream(resultsEach2.diffSum).max().getAsDouble() + "K\n"
                                + "Average temp difference sum: " + Arrays.stream(resultsEach2.diffSum).average().orElse(Double.NaN) + "K\n"
                                + "Number of calculations performed: " + numberOfCalculationsPerformed + "\n"
                                + "Measurement time: " + parseNanoSeconds(elapsedTime))
                        .setCancelable(false)
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {

                            }
                        });
                AlertDialog alert = builder.create();
                alert.show();
            } else {
                Toast.makeText(context, "No data to create heatmap!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    TempDifferenceCalculator(MainActivity context) {
        this.execute();
        running = true;
        measurementsQueue = new LinkedList<>();
        this.context = context;
        EventBus.getDefault().register(this);
    }

    void stop() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setCancelable(false); // if you want user to wait for some process to finish,
        builder.setView(R.layout.loading_dialog);
        progDialog = builder.create(); // Show a dialog if not finished when stopped;
        progDialog.show();
        running = false;
        this.firstMeasurement = null;
        EventBus.getDefault().unregister(this);
    }

    @Subscribe
    public void onNewMeauserement(MeasurementReadyEvent event) {
        measurementsQueue.add(event.measurementDataHolder);
    }

    @SuppressLint("DefaultLocale")
    private static String parseNanoSeconds(Long nanos){
        DecimalFormat df = new DecimalFormat("00");
        long tempSec = nanos/(1000*1000*1000);
        long sec = tempSec % 60;
        long min = (tempSec /60) % 60;
        long hour = (tempSec /(60*60)) % 24;
        return String.format("%d:%s:%s", hour, df.format(min), df.format(sec));
    }

    private void saveBitmap(Bitmap bmp, String fileName, String folderName) {
        File dir = new File(Environment.getExternalStorageDirectory() + File.separator + "HeatApp" + File.separator + folderName);
        if (!dir.exists())
            dir.mkdirs();
        File file = new File(dir, fileName);
        FileOutputStream fOut;
        try {
            fOut = new FileOutputStream(file);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fOut);
            fOut.flush();
            fOut.close();
            MediaScannerConnection.scanFile(context,
                    new String[]{file.getAbsolutePath()}, null,
                    (path, uri) -> {
                        Log.i("ExternalStorage", "Scanned " + path + ":");
                        Log.i("ExternalStorage", "-> uri=" + uri);
                    });
        } catch (IOException e) {
            Log.d(TAG, "File not saved");
            Log.e(TAG, Objects.requireNonNull(e.getMessage()));
        }
    }
}
