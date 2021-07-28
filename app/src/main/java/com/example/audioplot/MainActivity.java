package com.example.audioplot;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jtransforms.fft.DoubleFFT_1D;



public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private static final String[] permissions = {Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                                    Manifest.permission.READ_EXTERNAL_STORAGE};
    private boolean audioRecordingPermissionGranted = false;

    Recorder recorder;
    FreqGraphUpdater freqGraph;
    TimeGraphUpdater timeGraph;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);

        freqGraph = new FreqGraphUpdater();
        freqGraph.chart = findViewById(R.id.chart1);
        freqGraph.setProperties();
        new Thread(freqGraph).start();

        timeGraph = new TimeGraphUpdater();
        timeGraph.chart = findViewById(R.id.chart2);
        timeGraph.setProperties();
        new Thread(timeGraph).start();

        recorder = new Recorder();
        new Thread(recorder).start();

/*        String fileName = "AudioPCM.txt";
        File file = new File(getExternalFilesDir(null), fileName);

        recorder.startRecording(file);
        freqGraph.setRecording(true);
        timeGraph.setRecording(true);
        new Thread(recorder).start();*/

        Button btn = findViewById(R.id.button);
        btn.performClick();
        btn.setVisibility(View.GONE);

        getSupportActionBar().hide();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            audioRecordingPermissionGranted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
        }

        if (!audioRecordingPermissionGranted) {
            finish();
        }
    }

    public void Record(View v) {
        if (recorder.recording){
            stopRecording();
        }
        else {
            startRecording();
        }
    }


    public void startRecording() {
        TextView btn = findViewById(R.id.button);
        btn.setText(R.string.record_stop);

        String fileName = "AudioPCM.txt";
        File file = new File(getExternalFilesDir(null), fileName);

        recorder.startRecording(file);
        freqGraph.setRecording(true);
        timeGraph.setRecording(true);
        new Thread(recorder).start();


    }

     public void stopRecording(){
        recorder.stopRecording();
        TextView btn = findViewById(R.id.button);
        btn.setText(R.string.record_start);
    }
}

class Recorder implements Runnable{
    private static final String TAG = "Recorder";
    AudioRecord recorder;
    DoubleFFT_1D fft;
    private final static int L = 4096;


    //Define the parameters for recording audio
    public static int sampleRateInHz = 8000;
    int audioSource = MediaRecorder.AudioSource.MIC;
    int channelConfig = AudioFormat.CHANNEL_IN_MONO;
    int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    int bufferSizeInBytes = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
    public static short[] buffer = new short[L];
    double[] signal_re_img = new double[2*L];
    public static double[] P2 = new double[L];
    public boolean recording = false;
    File saveTo;

    public Recorder(){
        recorder = new AudioRecord(audioSource,
                                    sampleRateInHz,
                                    channelConfig,
                                    audioFormat,
                                    bufferSizeInBytes);
        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) { // check for proper initialization
            Log.e(TAG, "error initializing ");
            return;
        }
        fft = new DoubleFFT_1D(L);
    }

    public void startRecording(File file){
        saveTo = file;
        recorder.startRecording();
        recording = true;
    }

    public void stopRecording(){
        recorder.stop();
        recording = false;
    }

    public static int getLength(){
        return L;
    }

/*
    public void saveRecording() throws IOException{
        FileOutputStream outputStream = new FileOutputStream(saveTo);
        DataOutputStream dos = new DataOutputStream(outputStream);
        //Toast.makeText(getBaseContext(), Integer.toString(samplesRead), Toast.LENGTH_SHORT).show();
        try {
            for (int i =0; i<P2.length; i++){
                dos.writeBytes(Double.toString(P2[i])+System.lineSeparator());
            }
            //Toast.makeText(getBaseContext(), "File Saved Successfully", Toast.LENGTH_SHORT).show();
        } finally {
            dos.close();
        }
    }
*/

    public synchronized void transform(short[] data){
        for(int i=0; i<L; i++){
            signal_re_img[i*2] = data[i]; //real component
            signal_re_img[i*2+1] = 0; //imaginary component
        }
        fft.complexForward(signal_re_img);

        for(int i=0; i<L; i++){

            double re = signal_re_img[2*i]/L;
            double im = signal_re_img[2*i+1]/L;
            P2[i] = Math.sqrt(re*re+im*im);
        }

    }

    public void run(){
        while(recording){
            recorder.read(buffer, 0, bufferSizeInBytes);
            transform(buffer);
        }
    }
}

class FreqGraphUpdater implements Runnable{
    public LineChart chart;
    private boolean recording = false;


    public void setProperties(){
        chart.getDescription().setEnabled(true);
        chart.getDescription().setTextColor(Color.GRAY);
        chart.getDescription().setTextSize(12);

        //Since this is a static graph, all forms of Zooming and dragging is disabled
        chart.setTouchEnabled(false);
        chart.setDragEnabled(false);
        chart.setScaleEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setPinchZoom(false);
        chart.setDrawGridBackground(false);
        chart.setBorderColor(Color.BLACK);

        LineData data1 = new LineData();
        chart.setData(data1);

        chart.getAxisLeft().setAxisMinimum(0f);
        chart.getAxisLeft().setAxisMaximum(500f);
        chart.getAxisLeft().setDrawLabels(false);
        chart.getAxisRight().setAxisMinimum(0f);
        chart.getAxisRight().setAxisMaximum(500f);
        chart.getAxisRight().setDrawLabels(false);
        chart.getXAxis().setDrawGridLines(false);
        chart.getXAxis().setDrawLabels(false);
        chart.getAxisLeft().setDrawAxisLine(false);
        chart.getAxisRight().setDrawAxisLine(false);
        chart.getLegend().setEnabled(false);
    }

    public void setRecording(boolean isRecording){
        recording = isRecording;
    }

    private synchronized void addEntry() {
        List<Entry> entryList = new ArrayList<>();
        for(int i = 0; i<Recorder.getLength()/2; i++){
            entryList.add(new Entry(i, (float) (Recorder.P2[i])));
        }

        LineDataSet lineDataSet = new LineDataSet(entryList, "Frequency Domain Graph");
        lineDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        lineDataSet.setDrawCircles(false);
        lineDataSet.setColor(Color.RED);
        List <ILineDataSet> dataSets = new ArrayList<>();
        dataSets.add(lineDataSet);
        LineData lineData = new LineData(dataSets);
        chart.setData(lineData);
        chart.getDescription().setText(String.format(Locale.US , "%.2f",getPrincipalFreq()));
        chart.setDrawBorders(false);
        // let the chart know it's data has changed
        chart.notifyDataSetChanged();
        chart.invalidate();
    }

    private double getPrincipalFreq(){
        double max = 0;
        int index = 0;
        for(int i=0; i<Recorder.getLength()/2; i++){
            if(Recorder.P2[i] > max) {
                max = Recorder.P2[i];
                index = i;
            }
        }
        return  Recorder.sampleRateInHz*((float)index/Recorder.getLength());
    }

    public void run(){
        while(true) {
            if (recording) {
                addEntry();
                //SystemClock.sleep(30);
            }
        }
    }

}

class TimeGraphUpdater implements Runnable{
    public LineChart chart;
    private boolean recording = false;


    public void setProperties(){
        chart.getDescription().setEnabled(false);

        //Since this is a static graph, all forms of Zooming and dragging is disabled
        chart.setTouchEnabled(false);
        chart.setDragEnabled(false);
        chart.setScaleEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setPinchZoom(false);
        chart.setDrawGridBackground(false);
        chart.setDrawBorders(false);

        LineData data1 = new LineData();
        chart.setData(data1);

        chart.getAxisLeft().setAxisMinimum(-30000f);
        chart.getAxisLeft().setAxisMaximum(+30000f);
        chart.getAxisLeft().setDrawLabels(false);
        chart.getAxisLeft().setDrawGridLines(false);
        chart.getAxisRight().setAxisMinimum(-30000f);
        chart.getAxisRight().setAxisMaximum(+30000f);
        chart.getAxisRight().setDrawLabels(false);
        chart.getAxisRight().setDrawGridLines(false);
        chart.getXAxis().setDrawGridLines(false);
        chart.getXAxis().setDrawLabels(false);
        chart.getAxisLeft().setDrawAxisLine(false);
        chart.getAxisRight().setDrawAxisLine(false);
        chart.getXAxis().setDrawAxisLine(false);
        chart.getLegend().setEnabled(false);
    }

    public void setRecording(boolean isRecording){
        recording = isRecording;
    }

    private synchronized void addEntry() {
        List<Entry> entryList = new ArrayList<>();
        for(int i = 0; i<Recorder.buffer.length/8; i++){
            entryList.add(new Entry(i, Recorder.buffer[i]));
        }

        LineDataSet lineDataSet = new LineDataSet(entryList, "Time Domain Graph");
        lineDataSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        lineDataSet.setDrawCircles(false);
        lineDataSet.setColor(Color.RED);
        List <ILineDataSet> dataSets = new ArrayList<>();
        dataSets.add(lineDataSet);
        LineData lineData = new LineData(dataSets);
        chart.setData(lineData);

        // let the chart know it's data has changed
        chart.notifyDataSetChanged();
        chart.invalidate();
    }

    public void run(){
        while(true) {
            if (recording) {
                addEntry();
                SystemClock.sleep(20);
            }
        }
    }

}
