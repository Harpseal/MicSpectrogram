package com.harpseal.test.micspectrogram;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import com.harpseal.test.micspectrogram.R;

import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.support.v4.app.NavUtils;

import android.widget.Button;

import android.os.PowerManager;


public class MainMicTestActivity extends Activity {

	AudioBuffer audioBuffer = null;
	SpectrumView spectrumView;
	MicLoopbackCapture micCpature;
	protected PowerManager.WakeLock mWakeLock;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_mic_test);
        
        audioBuffer = new AudioBuffer(256);
        
        //if ()
        if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN)
        	Log.i("ByteOrder","BIG_ENDIAN");
        if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN)
        	Log.i("ByteOrder","LITTLE_ENDIAN");
        //byte[] byteDate = new byte[8];
        //for (int i=0;i<byteDate.length;i++)
        //	byteDate[i] = (byte)i;
        
        //IntBuffer intBuf =
        //		   ByteBuffer.wrap(byteDate)
        		     //.order(ByteOrder.BIG_ENDIAN)
        //		     .asIntBuffer();
        //int[] intData = new int[intBuf.remaining()];
        //intBuf.get(intData);

        spectrumView = (SpectrumView)findViewById(R.id.spectrumView_mic_wave);
        spectrumView.SetBuffer(audioBuffer);
        spectrumView.SetFFT(1024*8);
        spectrumView.StartFFT();
        
        micCpature = new MicLoopbackCapture(audioBuffer);
        audioBuffer.SignalDataAlignCondition();
        micCpature.StartRecording();
        
        //KissFFTWrapper fft = new KissFFTWrapper(1024);
        
        
        /* This code together with the one in onDestroy() 
         * will make the screen be always on until this Activity gets destroyed. */
        //final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        //this.mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "MicSpectrumViewTag");
        //this.mWakeLock.acquire();

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setClickHandlers();
    }
    
    @Override
    public void onDestroy() {
        //this.mWakeLock.release();
    	micCpature.StopRecording();
    	spectrumView.StopFFT();
        super.onDestroy();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main_mic_test, menu);
        return true;
    }

    public void setClickHandlers()
    {
    	//((android.widget.Button)findViewById(R.id.button_mic_control)).setOnClickListener(appOnClickLintenser);
    	((android.view.View)findViewById(R.id.spectrumView_mic_wave)).setOnClickListener(appOnClickLintenser);
    }
    
    private boolean isEnableMic()
    {
    	return true;
    }
    
    private void enableMic(boolean bIsEnable)
    {

    	
    }
    
    private View.OnClickListener appOnClickLintenser = new View.OnClickListener() {
		
		@Override
		public void onClick(View v) {
			enableMic(!isEnableMic());

		}
	};
}
