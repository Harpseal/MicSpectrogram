#include <jni.h>
#define FIXED_POINT 16
#include "kiss_fftr.h"
#include <android/bitmap.h>
#include <android/log.h>
#include <stdio.h>
#include <math.h>

#ifndef MIN
#define MIN(a,b)  ((a) > (b) ? (b) : (a))
#endif

#define ENABLE_ShowPCM 0

typedef struct KissFFTPackage
{
	kiss_fftr_cfg config_forward;
	kiss_fftr_cfg config_inverse;
	kiss_fft_cpx* spectrum;
#if ENABLE_ShowPCM
	short *samples;
#endif
	int numSamples;
	int numSpectrumSize;
}KissFFTPackage;

#define MAX_SHORT 32767.0f

static inline float scale( kiss_fft_scalar val )
{
	if( val < 0 )
	return val * ( 1 / 32768.0f );
	else
	return val * ( 1 / 32767.0f );
}

namespace AudioFFTUtility
{
	const float cfNoteCNeg1Freq = 8.17579891564f;
	const float cfNoteCFreq[11] = {
		cfNoteCNeg1Freq*    2, cfNoteCNeg1Freq*    4,cfNoteCNeg1Freq*    8,
		cfNoteCNeg1Freq*   16, cfNoteCNeg1Freq*   32,cfNoteCNeg1Freq*   64,
		cfNoteCNeg1Freq*  128, cfNoteCNeg1Freq*  256,cfNoteCNeg1Freq*  512,
		cfNoteCNeg1Freq* 1024, cfNoteCNeg1Freq* 2048};
	const float cfLog2 = log(2);
	//float log2f( float n )
	//{
 //   // log(n)/log(2) is log2.
	//	return log( n ) / cfLog2;
	//}
	inline float Mag2dB(float mag){return (20*log10(mag));}
	inline float Freq2Pitch(float freq){return 69+12*(log(freq/440.f)/cfLog2);}
	inline float Pitch2Freq(float pitch){return 440.f*powf(2,(pitch-69.f)/12.f);}
};


#ifndef MIN
#define MIN(a,b)  ((a) > (b) ? (b) : (a))
#endif

#ifndef MAX
#define MAX(a,b)  ((a) < (b) ? (b) : (a))
#endif

extern "C" jlong Java_com_harpseal_test_micspectrogram_KissFFTWrapper_create(JNIEnv* env, jclass thiz,int nFFT)
{
	KissFFTPackage* fft = new KissFFTPackage();
	fft->config_forward = kiss_fftr_alloc(nFFT,0,NULL,NULL);
	fft->config_inverse = kiss_fftr_alloc(nFFT,1,NULL,NULL);
	fft->spectrum = (kiss_fft_cpx*)malloc(sizeof(kiss_fft_cpx) * nFFT);
#if ENABLE_ShowPCM
	fft->samples = new short[nFFT];
	memset(fft->samples,0,sizeof(short)*nFFT);
#endif
	fft->numSamples = nFFT;
	fft->numSpectrumSize = nFFT/2+1;
	return (jlong)fft;
}

extern "C" void Java_com_harpseal_test_micspectrogram_KissFFTWrapper_destroy(JNIEnv* env, jclass thiz, jlong handle)
{
	KissFFTPackage* fft = (KissFFTPackage*)handle;
	free(fft->config_forward);
	free(fft->config_inverse);
	free(fft->spectrum);
	//free(fft);
#if ENABLE_ShowPCM
	delete [] fft->samples;
#endif
	delete fft;
}

extern "C" void Java_com_harpseal_test_micspectrogram_KissFFTWrapper_forward(JNIEnv* env, jclass clazz, jlong handle, jshortArray obj_samples, jfloatArray obj_spectrum) {
	short* samples = (short*)env->GetPrimitiveArrayCritical(obj_samples, 0);
	float* spectrum = (float*)env->GetPrimitiveArrayCritical(obj_spectrum, 0);



	KissFFTPackage* fft = (KissFFTPackage*)handle;

#if ENABLE_ShowPCM
	memcpy(fft->samples, samples, sizeof(short)*fft->numSamples);
#endif

	kiss_fftr( fft->config_forward, (kiss_fft_scalar*)samples, fft->spectrum );

	int len = fft->numSamples / 2 + 1;
	for( int i = 0; i < len; i++ )
	{
		float re = scale(fft->spectrum[i].r) * fft->numSamples;
		float im = scale(fft->spectrum[i].i) * fft->numSamples;

		if( i > 0 )
		spectrum[i] = sqrtf(re*re + im*im);
		else
		spectrum[i] = sqrtf(re*re + im*im);
	}

	env->ReleasePrimitiveArrayCritical(obj_samples, samples, 0);
	env->ReleasePrimitiveArrayCritical(obj_spectrum, spectrum, 0);

}

extern "C" void Java_com_harpseal_test_micspectrogram_KissFFTWrapper_inverse(JNIEnv* env, jclass clazz, jlong handle, jfloatArray obj_samples) {
    kiss_fft_scalar* samples = (kiss_fft_scalar*)env->GetPrimitiveArrayCritical(obj_samples, 0);

    KissFFTPackage* fft = (KissFFTPackage*)handle;

    kiss_fftri( fft->config_inverse, fft->spectrum, samples );

    for(int i=0;i<fft->numSamples;i++) {
        samples[i] = samples[i] / (float)fft->numSamples;
    }

    env->ReleasePrimitiveArrayCritical(obj_samples, samples, 0);

}

extern "C" void Java_com_harpseal_test_micspectrogram_KissFFTWrapper_getRealPart(JNIEnv* env, jclass clazz, jlong handle, jshortArray obj_real) {
	short* real = (short*)env->GetPrimitiveArrayCritical(obj_real, 0);

	KissFFTPackage* fft = (KissFFTPackage*)handle;
	for( int i = 0; i < fft->numSamples / 2; i++ )
		real[i] = fft->spectrum[i].r;

	env->ReleasePrimitiveArrayCritical(obj_real, real, 0);
}

extern "C" void Java_com_harpseal_test_micspectrogram_KissFFTWrapper_getImagPart(JNIEnv* env, jclass clazz, jlong handle, jshortArray obj_imag) {
	short* imag = (short*)env->GetPrimitiveArrayCritical(obj_imag, 0);

	//@line:132

	KissFFTPackage* fft = (KissFFTPackage*)handle;
	for( int i = 0; i < fft->numSamples / 2; i++ )
		imag[i] = fft->spectrum[i].i;

	env->ReleasePrimitiveArrayCritical(obj_imag, imag, 0);
}

extern "C" void Java_com_harpseal_test_micspectrogram_KissFFTWrapper_updateBitmapPitch(JNIEnv* env, jclass clazz,
																						 jlong handle,
																						 jfloat pitchS,jfloat pitchE,
																						 jfloat fdBMax,jfloat fdBMin,
																						 jfloatArray obj_spectrum,jfloatArray obj_fft2Pitch,jobject bitmap)
{
	float* spectrum = (float*)env->GetPrimitiveArrayCritical(obj_spectrum, 0);
	float* afFFT2Pitch = (float*)env->GetPrimitiveArrayCritical(obj_fft2Pitch, 0);

	AndroidBitmapInfo info;
	AndroidBitmap_getInfo(env, bitmap, &info);
	const int width = info.width;
	const int height = info.height;

	int iImgPosCur,iImgPosNext;
	float fVCur,fVNext;

	KissFFTPackage* fft = (KissFFTPackage*)handle;

	unsigned char *pv;
	AndroidBitmap_lockPixels(env, bitmap, (void**)&pv);

	int nHalfFFT = fft->numSamples/2+1;
	memmove((void*)(pv+4*width),(void*)pv,sizeof(unsigned char)*4*width*(height-1));
	memset(pv,0,sizeof(unsigned char)*4*width);

	iImgPosNext = (afFFT2Pitch[ 1 ] - pitchS)/(pitchE - pitchS)*(width-1);

	fVNext = spectrum[ 1 ];
	fVNext = fVNext>=0.0001? MAX(AudioFFTUtility::Mag2dB(fVNext),fdBMin) : fdBMin;
	fVNext = 1-(fVNext-fdBMax)/(fdBMin-fdBMax);
	for (int i=1;i<nHalfFFT-1;i++)
	{

		iImgPosCur = iImgPosNext;
		fVCur = fVNext;
		iImgPosNext = (afFFT2Pitch[ i+1 ] - pitchS)/(pitchE - pitchS)*(width-1);


		//fVNext = Mag2dB(GetFFTOutputMagnitude(i+1));
		//fVNext = fVNext>=0.0001? Mag2dB(fVNext) : fdBLow;
		fVNext = spectrum[ i+1 ];
		fVNext = fVNext>=0.0001? MAX(AudioFFTUtility::Mag2dB(fVNext),fdBMin) : fdBMin;
		fVNext = 1-(fVNext-fdBMax)/(fdBMin-fdBMax);
		if (iImgPosNext == iImgPosCur)
			fVNext = MAX(fVCur,fVNext);
		else if (iImgPosCur>=0 && iImgPosNext<width)
		{
			for (int p=iImgPosCur;p<iImgPosNext;p++)
			{
				pv[p*4+0] = pv[p*4+1] = pv[p*4+2] = 254*MIN(((fVCur-fVNext)*(1.f-float(p-iImgPosCur)/(iImgPosNext-iImgPosCur))+fVNext),1);
				//pv[p*4+2] = 255;
				pv[p*4+3] = 255;
			}
		}

	}


	AndroidBitmap_unlockPixels(env, bitmap);
	env->ReleasePrimitiveArrayCritical(obj_spectrum, spectrum, 0);
	env->ReleasePrimitiveArrayCritical(obj_fft2Pitch, afFFT2Pitch, 0);
}

extern "C" jfloat Java_com_harpseal_test_micspectrogram_KissFFTWrapper_updateBitmap(JNIEnv* env, jclass clazz,
																					jlong handle,
																					jfloat maxFrq,
																					jfloatArray obj_spectrum,jintArray obj_frqremap,jobject bitmap)
{

	//__android_log_print(ANDROID_LOG_INFO, "KissFFTWrapper_JNI", "00");
	float* spectrum = (float*)env->GetPrimitiveArrayCritical(obj_spectrum, 0);
	int* pFrqRemap = (int*)env->GetPrimitiveArrayCritical(obj_frqremap, 0);

	AndroidBitmapInfo info;
	AndroidBitmap_getInfo(env, bitmap, &info);
	const int width = info.width;
	const int height = info.height;

	unsigned char *pPixelData;
	AndroidBitmap_lockPixels(env, bitmap, (void**)&pPixelData);

	KissFFTPackage* fft = (KissFFTPackage*)handle;

#if ENABLE_ShowPCM
	memset(pPixelData,0,sizeof(unsigned char)*width*height*4);
	int row,posx,posy,value,dir;
	int rowHeight = height/ceil((float)fft->numSamples/width);
	unsigned char *pPixelDataCur;

	__android_log_print(ANDROID_LOG_INFO, "KissFFTWrapper_JNI", "rh: "+ rowHeight);
	for (int s=0;s<fft->numSamples;s++)
	{
		row = s/width;
		if ((row+1) * rowHeight >= height)
			break;
		posx = s%width;
		posy = row * rowHeight + rowHeight/2;
		short swapByteValue = fft->samples[s];
		//swapByteValue = ((swapByteValue & 0xff00) >> 8) | ((swapByteValue & 0x00ff) << 8);
		value = float(swapByteValue)/32768.f*(rowHeight/2);
		if (value<0)
			dir = -1;
		else
			dir = 1;

		value = abs(value);
		if (value>rowHeight/2-1)
			value=rowHeight/2-1;

		if (value==0)
			value = 1;

		for (int l=0;l<value;l++)
		{
			pPixelDataCur = pPixelData + ((posy+dir*l)*width+posx)*4;
			pPixelDataCur[0] = pPixelDataCur[2] = 0;
			pPixelDataCur[1] = 255;
			pPixelDataCur[3] = 255;
		}
	}




#else
	float v,vMax,vMin;
	vMax = 0;vMin=99999999;
	for( int i = 0; i < fft->numSpectrumSize; i++ )
	{
		if (vMax<spectrum[i])
			vMax=spectrum[i];
		if (vMin>spectrum[i])
			vMin=spectrum[i];
	}
	if (maxFrq<vMax)
		maxFrq=vMax;

	float scaleColorSum;
	int scaleCount;
	scaleCount = 0;
	scaleColorSum = 0;
	unsigned char *pPixelDataCur;

	for (int ifft=0,y=0,c=0;ifft<fft->numSpectrumSize&&y<height;ifft++,c++)
	{
		v = spectrum[ifft];

		scaleCount++;
		scaleColorSum+=v;
		if (c>=pFrqRemap[y]-1)
		{
			c=-1;
			pPixelDataCur = pPixelData + ((height-1-y)*width+0)*4;
			memmove(pPixelDataCur,
					pPixelDataCur + 4,
					sizeof(unsigned char)*(width-1)*4);
			v = scaleColorSum/scaleCount;

			v=(MIN((v-vMin)/(maxFrq*0.4-vMin),1))*254.0;//*(m_FFTW.fRawMaxCur/MAX(m_FFTW.fRawMax,0.1));

			pPixelDataCur+=(width-1)*4;

			pPixelDataCur[0] = pPixelDataCur[1] = pPixelDataCur[2] = v;
			pPixelDataCur[3] = 255;
			y++;
			scaleCount = 0;
			scaleColorSum = 0;
		}
	}
#endif
	AndroidBitmap_unlockPixels(env, bitmap);
	env->ReleasePrimitiveArrayCritical(obj_spectrum, spectrum, 0);
	env->ReleasePrimitiveArrayCritical(obj_frqremap, pFrqRemap, 0);

	return maxFrq;
}

