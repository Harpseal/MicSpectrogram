package com.harpseal.test.micspectrogram;

import android.graphics.Bitmap;

public class KissFFTWrapper {
	private long m_pFFTPackage;
	
	public KissFFTWrapper(int nFFT)
	{
		//System.loadLibrary("kissFFT-jni");
		m_pFFTPackage = create(nFFT);
	}
	
	public void dispose () {
		destroy(m_pFFTPackage);
	}
	
	public void forward (short[] samples, float[] spectrum) {
		forward(m_pFFTPackage, samples, spectrum);
	}
	
	public void inverse (float[] samples) {
		inverse(m_pFFTPackage, samples);
	}
	
	public float updateBitmap(float maxFrq,float[] spectrum,int[] frqRemap,Bitmap bitmap)
	{
		return updateBitmap(m_pFFTPackage,maxFrq,spectrum,frqRemap,bitmap);
	}
		
	public void updateBitmapPitch(float pitchS,float pitchE,float fdBMax,float fdBMin,float[] spectrum,float[] fft2Pitch,Bitmap bitmap)
	{
		updateBitmapPitch(m_pFFTPackage,pitchS,pitchE,fdBMax,fdBMin,spectrum,fft2Pitch,bitmap);
	}
 
	
	private static native long create (int numSamples);
	private static native void destroy (long handle);
	private static native void forward(long handle, short[] samples, float[] spectrum);
	private static native void inverse(long handle, float[] samples);
	private static native float updateBitmap(long handle,float maxFrq,float[] spectrum,int[] frqRemap,Bitmap bitmap);
	private static native void updateBitmapPitch(long handle,float pitchS,float pitchE,float fdBMax,float fdBMin,float[] spectrum,float[] fft2Pitch,Bitmap bitmap);
	
	static {
        System.loadLibrary("kissFFT-jni");
    }
	
}
