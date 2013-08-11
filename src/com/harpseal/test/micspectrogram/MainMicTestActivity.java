package com.harpseal.test.micspectrogram;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import com.harpseal.test.micspectrogram.R;

import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.support.v4.app.NavUtils;

import android.widget.Button;
import android.widget.Toast;

import android.os.PowerManager;


public class MainMicTestActivity extends Activity {

	AudioBuffer audioBuffer = null;
	SpectrumView spectrumView;
	MicLoopbackCapture micCpature;
	protected PowerManager.WakeLock mWakeLock;

	public static final String PREFS_NAME = "MicSpectrumPrefsFile";

	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_mic_test);
        
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        int nFFT = settings.getInt("nFFT", 1024*8*2);
        boolean isEnableQInter = settings.getBoolean("QuadInte", false);
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
        
        float [] displaySettings = spectrumView.GetDisplaySettings();
        for (int i=0;i<displaySettings.length;i++)
        {
        	displaySettings[i] = settings.getFloat("displaySettings"+i, displaySettings[i]);
        }
        spectrumView.SetDisplaySettings(displaySettings);
        
        spectrumView.SetBuffer(audioBuffer);
        spectrumView.SetFFT(nFFT);
        spectrumView.SetEnableQuadraticInterpolation(isEnableQInter);
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
        
        Toast.makeText(MainMicTestActivity.this, "FFT Size: " + (spectrumView.GetFFT()), Toast.LENGTH_SHORT).show();
    }
    
    @Override
    public void onDestroy() {
        //this.mWakeLock.release();
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putInt("nFFT", spectrumView.GetFFT());
        editor.putBoolean("QuadInte", spectrumView.GetEnableQuadraticInterpolation());
        
        float [] displaySettings = spectrumView.GetDisplaySettings();
        for (int i=0;i<displaySettings.length;i++)
        {
        	editor.putFloat("displaySettings"+i, displaySettings[i]);
        }
        
        editor.commit();


        
    	micCpature.StopRecording();
    	spectrumView.StopFFT();
        super.onDestroy();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main_mic_test, menu);
    return true;
    }
    
     
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        //依據itemId來判斷使用者點選哪一個item
        switch(item.getItemId()) {
            case R.id.setting_fftsize_1024:
            	spectrumView.SetFFT(1024);
                break;
            case R.id.setting_fftsize_2048:
            	spectrumView.SetFFT(2048);
                break;
            case R.id.setting_fftsize_4096:
            	spectrumView.SetFFT(4096);
                break;
            case R.id.setting_fftsize_8192:
            	spectrumView.SetFFT(8192);
                break;
            case R.id.setting_fftsize_16384:
            	spectrumView.SetFFT(16384);
                break;
            case R.id.setting_item_quad_inter:
            	spectrumView.SetEnableQuadraticInterpolation(!spectrumView.GetEnableQuadraticInterpolation());
            	Toast.makeText(MainMicTestActivity.this, "Quadratic Interpolation " + (spectrumView.GetEnableQuadraticInterpolation()?"Enabled":"Disabled"), Toast.LENGTH_SHORT).show();
                //結束此程式
                //finish();
                break;
            default:
        }
        return super.onOptionsItemSelected(item);
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
