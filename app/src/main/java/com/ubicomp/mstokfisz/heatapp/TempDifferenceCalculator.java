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
import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

import static com.ubicomp.mstokfisz.heatapp.RotationHandler.rotateBitmap;

public class TempDifferenceCalculator extends AsyncTask<Void, Void, Void> {
    static Boolean running = false;
    private static LinkedList<MeasurementDataHolder> measurementsQueue;
    private double[] diffSum;
    private double[] diffFreq; // Frequency of changes
    private int width = 0;
    private int height = 0;
    private int numberOfCalculationsPerformed = 0;
    private long elapsedTime;
    private static final String TAG = "TempDiffCalc";
    private static AlertDialog progDialog;
    @SuppressLint("StaticFieldLeak")
    private Context context;

    @Override
    protected Void doInBackground(Void... Voids) {
        long startTime = System.nanoTime();
        while (running) {
            if (measurementsQueue != null && measurementsQueue.size() >= 2) { // If there are two frames ready
                MeasurementDataHolder firstMeasurement = measurementsQueue.removeFirst(); // Get first measurement and delete from queue
                MeasurementDataHolder secondMeasurement = measurementsQueue.getFirst(); // Get second (now first) but do not delete from queue
                numberOfCalculationsPerformed++;
                Log.d(TAG, Integer.toString(measurementsQueue.size()));
                if (width == 0 || height == 0 || diffSum == null) { // for first measurement set everything
                    width = firstMeasurement.width;
                    height = firstMeasurement.height;
                    if (firstMeasurement.pointsList != null) {
                        diffSum = new double[firstMeasurement.pointsList.size()];
                        diffFreq = new double[firstMeasurement.pointsList.size()];
                    } else {
                        diffSum = new double[firstMeasurement.data.length];
                        diffFreq = new double[firstMeasurement.data.length];
                    }
                    Arrays.fill(diffSum, 0);
                    Arrays.fill(diffFreq, 0);
                }
                if (firstMeasurement.pointsList != null) { // Face tracking mode
                    for (int i = 0; i < firstMeasurement.pointsList.size(); i++) { // Assume same size for now
                        Integer firstPointNum = firstMeasurement.pointsList.get(i);
                        Integer secondPointNum = secondMeasurement.pointsList.get(i);
                        if (firstPointNum != null && secondPointNum != null) {
                            diffSum[i] += Math.abs(secondMeasurement.data[secondPointNum] - firstMeasurement.data[firstPointNum]); // For now get the absolute value
                            if (secondMeasurement.data[secondPointNum] != firstMeasurement.data[firstPointNum]) { // If the temp changed then add
                                diffFreq[i]++;
                            }
                        }
//                    diffSum[i] += Math.abs((secondMeasurement.data[i] - secondMeasurement.minVal) - (firstMeasurement.data[i] - firstMeasurement.minVal)); // Alternative mode with subtracting minValues
                    }
                } else { // Grab whole scene
                    for (int i = 0; i < firstMeasurement.data.length; i++) { // Assume same size for now
                        diffSum[i] += Math.abs(secondMeasurement.data[i] - firstMeasurement.data[i]); // For now get the absolute value
                        if (secondMeasurement.data[i] != firstMeasurement.data[i]) { // If the temp changed then add
                            diffFreq[i]++;
                        }
                    }
                }

            }
        }
        if (diffFreq != null && diffSum != null) {
            elapsedTime = System.nanoTime() - startTime;
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ssZ", Locale.getDefault());
            String formatedDate = sdf.format(new Date());
            Log.d(TAG, Arrays.toString(diffSum));
            Bitmap diffSumHeatMap = rotateBitmap(HeatMapGenerator.generateHeatMap(new MeasurementDataHolder(diffSum, Arrays.stream(diffSum).min().getAsDouble(), Arrays.stream(diffSum).max().getAsDouble(), width, height, null)));
            Bitmap diffFreqHeatMap = rotateBitmap(HeatMapGenerator.generateHeatMap(new MeasurementDataHolder(diffFreq, Arrays.stream(diffFreq).min().getAsDouble(), Arrays.stream(diffFreq).max().getAsDouble(), width, height, null)));
            String diffSumFileName = "DifferenceSum-" + formatedDate + ".png";
            String diffFreqFileName = "ChangeFrequency-" + formatedDate + ".png";
            File dir = new File(Environment.getExternalStorageDirectory() + File.separator + "HeatApp");
            if (!dir.exists())
                dir.mkdirs();
            File diffSumFile = new File(dir, diffSumFileName);
            File diffFreqFile = new File(dir, diffFreqFileName);
            FileOutputStream fOut;
            try {
                fOut = new FileOutputStream(diffSumFile);
                diffSumHeatMap.compress(Bitmap.CompressFormat.PNG, 100, fOut);
                fOut = new FileOutputStream(diffFreqFile);
                diffFreqHeatMap.compress(Bitmap.CompressFormat.PNG, 100, fOut);
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
        }
        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        if (progDialog != null)
            progDialog.dismiss();
        if (context != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("Measurement statistics")
                    .setMessage("Lowest temp difference sum: "+Arrays.stream(diffSum).min().getAsDouble()+"K\n"
                    +"Highest temp difference sum: "+Arrays.stream(diffSum).max().getAsDouble()+"K\n"
                    +"Average temp difference sum: "+Arrays.stream(diffSum).average().orElse(Double.NaN)+"K\n"
                    +"Number of calculations performed: "+numberOfCalculationsPerformed+"\n"
                    +"Measurement time: "+parseNanoSeconds(elapsedTime))
                    .setCancelable(false)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {

                        }
                    });
            AlertDialog alert = builder.create();
            alert.show();
        }
//            Toast.makeText(context, "Heatmap created succesfully!", Toast.LENGTH_SHORT).show();
    }

    TempDifferenceCalculator(MainActivity context) {
        this.execute();
        running = true;
        measurementsQueue = new LinkedList<>();
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

    @SuppressLint("DefaultLocale")
    private static String parseNanoSeconds(Long nanos){
        DecimalFormat df = new DecimalFormat("00");
        long tempSec = nanos/(1000*1000*1000);
        long sec = tempSec % 60;
        long min = (tempSec /60) % 60;
        long hour = (tempSec /(60*60)) % 24;
        return String.format("%d:%s:%s", hour, df.format(min), df.format(sec));
    }
}
