package com.harpseal.test.micspectrogram;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.os.Bundle;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Paint.Style;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.nio.IntBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.locks.ReentrantLock;

import java.lang.Math;
import java.util.Arrays;

public class SpectrumView extends View {

	private AudioBuffer m_buffer = null;

	private int m_nFFT = 0;
	private KissFFTWrapper m_FFT = null;

	private Thread m_FFTThread = null;
	private boolean m_isFFTLooping = false;

	private ReentrantLock m_lockFFT = new ReentrantLock();
	private ReentrantLock m_lockDraw = new ReentrantLock();

	private final int m_nBitmapPitchS = 12 * (0 + 1);// C0
	private final int m_nBitmapPitchE = 12 * (10 + 1);// C10
	private final int m_nBitmapWidth = 1024;
	private final int m_nBitmapHeight = 512;
	private Bitmap m_bitmap = null;

	private float m_fFrqMax;

	private float m_fOverlapRatio = 0.5f;
	private float m_fDesiredFPS = 30.f;

	private float m_fFFTTimeOnDraw = 0.f;
	private float m_fFFTTimeOnDrawUpdate = 0.f;
	private long m_lFFTTimeOnDrawUpdateCurTime = 0;

	private float m_fFFTTimeFFT = 0.f;
	private float m_fFFTTimeFFTUpdate = 0.f;
	private long m_lFFTTimeFFTUpdateCurTime = 0;

	private float[] m_afSpectrumBuffer = null;
	private float[] m_afFFT2Pitch = null;
	int m_nSourceSampleRate = 44100;

	private float m_fBufferUsage = 0;

	private float m_fFFTPitchMax;
	private float m_fFFTPitchMin;

	private float m_fFFTFreqMax;
	private float m_fFFTFreqMin;

	private float m_fdBMax = 50;
	private float m_fdBMin = -10;

	private float m_fSpectrogramTimeScale = 1;

	public final float cfLog2 = (float) Math.log(2.0);

	public float Mag2dB(float mag) {
		return (20.f * (float) Math.log10(mag));
	}

	public float Freq2Pitch(float freq) {
		return 69 + 12 * ((float) Math.log(freq / 440.f) / cfLog2);
	}

	public float Pitch2Freq(float pitch) {
		return 440.f * (float) Math.pow(2.f, (pitch - 69.f) / 12.f);
	}

	private int m_nFinger;
	private float[] m_afMousePos = new float[2];
	private float[] m_afMouseDis = new float[2];
	private float m_fTouchFFTPitchMax;
	private float m_fTouchFFTPitchMin;

	private boolean m_isEnableQuadraticInterpolation = false;

	public SpectrumView(Context context) {
		super(context);
		m_buffer = null;
		ClearFFT();
		m_bitmap = Bitmap.createBitmap(m_nBitmapWidth, m_nBitmapHeight,
				Bitmap.Config.ARGB_8888);
		m_fFrqMax = 0;
		Log.i("ViewSize",
				"witdh: " + this.getWidth() + " height: " + this.getHeight());

		m_fTouchFFTPitchMax = m_fFFTPitchMax = 12 * (7 + 1); // C7
		m_fTouchFFTPitchMin = m_fFFTPitchMin = 12 * (2 + 1); // C2

		m_fFFTFreqMax = Pitch2Freq(m_fFFTPitchMax);
		m_fFFTFreqMin = Pitch2Freq(m_fFFTPitchMin);

		m_nFinger = 0;
	}

	public SpectrumView(Context context, AttributeSet set) {
		super(context, set);
		m_buffer = null;
		ClearFFT();
		// m_bitmap =
		// Bitmap.createBitmap(this.getWidth(),this.getHeight(),Bitmap.Config.ARGB_8888);
		m_bitmap = Bitmap.createBitmap(m_nBitmapWidth, m_nBitmapHeight,
				Bitmap.Config.ARGB_8888);
		m_fFrqMax = 0;
		Log.i("ViewSize",
				"witdh: " + this.getWidth() + " height: " + this.getHeight());

		m_fTouchFFTPitchMax = m_fFFTPitchMax = 12 * (7 + 1); // C7
		m_fTouchFFTPitchMin = m_fFFTPitchMin = 12 * (2 + 1); // C2

		m_fFFTFreqMax = Pitch2Freq(m_fFFTPitchMax);
		m_fFFTFreqMin = Pitch2Freq(m_fFFTPitchMin);

		m_nFinger = 0;

	}

	@Override
	public boolean onTouchEvent(MotionEvent MEvent) {

		int motionaction = MEvent.getAction();
		float disX, disY, range;
		Log.i("onTouchEvent", "motionaction: " + motionaction);
		switch (motionaction & MotionEvent.ACTION_MASK) {
		case MotionEvent.ACTION_DOWN:
			m_nFinger = 1;
			m_afMousePos[0] = MEvent.getX(0);
			m_afMousePos[1] = MEvent.getY(0);

			break;
		case MotionEvent.ACTION_UP:
			m_nFinger = 0;
			break;

		case MotionEvent.ACTION_POINTER_DOWN:
			m_nFinger++;
			if (m_nFinger == 2) {
				m_afMouseDis[0] = (float) Math.abs(MEvent.getX(0)
						- MEvent.getX(1));
				m_afMouseDis[1] = (float) Math.abs(MEvent.getY(0)
						- MEvent.getY(1));
				m_fTouchFFTPitchMax = m_fFFTPitchMax;
				m_fTouchFFTPitchMin = m_fFFTPitchMin;
				//m_fTouchSpectrogramTimeScale = m_fSpectrogramTimeScale;
			} else if (m_nFinger == 3) {
				m_afMousePos[0] = MEvent.getX(0);
				m_afMousePos[1] = MEvent.getY(0);
			}
			break;
		case MotionEvent.ACTION_POINTER_UP:
			m_nFinger--;
			if (m_nFinger == 1) {
				m_afMousePos[0] = MEvent.getX(0);
				m_afMousePos[1] = MEvent.getY(0);
			} else if (m_nFinger == 2) {
				m_afMouseDis[0] = (float) Math.abs(MEvent.getX(0)
						- MEvent.getX(1));
				m_afMouseDis[1] = (float) Math.abs(MEvent.getY(0)
						- MEvent.getY(1));
				m_fTouchFFTPitchMax = m_fFFTPitchMax;
				m_fTouchFFTPitchMin = m_fFFTPitchMin;
				//m_fTouchSpectrogramTimeScale = m_fSpectrogramTimeScale;
			}
			m_nFinger=0;
			break;

		case MotionEvent.ACTION_MOVE:
			if (this.getWidth() == 0 || this.getHeight() == 0)
				break;
			if (m_nFinger == 1) {
				disX = MEvent.getX(0) - m_afMousePos[0];
				disY = MEvent.getY(0) - m_afMousePos[1];
				m_afMousePos[0] = MEvent.getX(0);
				m_afMousePos[1] = MEvent.getY(0);

				float disdB, disPitch;
				disdB = disY / (float) (this.getHeight())
						* (m_fdBMax - m_fdBMin);
				disPitch = disX / (float) (this.getWidth())
						* (m_fFFTPitchMax - m_fFFTPitchMin);
				m_fdBMax += disdB;
				m_fdBMin += disdB;

				if (m_fdBMax < 10) {
					range = m_fdBMax - m_fdBMin;
					m_fdBMax = 10;
					m_fdBMin = 10 - range;
				} else if (m_fdBMin > 20) {
					range = m_fdBMax - m_fdBMin;
					// m_fdBMax+=(m_fdBMin - (-10));
					m_fdBMin = 20;
					m_fdBMax = range + m_fdBMin;

				} else if (m_fdBMin < -7) {
					range = m_fdBMax - m_fdBMin;
					// m_fdBMax+=(m_fdBMin - (-10));
					m_fdBMin = -7;
					m_fdBMax = range + m_fdBMin;

				}

				m_fFFTPitchMax -= disPitch;
				m_fFFTPitchMin -= disPitch;

				if (m_fFFTPitchMin < 0) {
					m_fFFTPitchMax -= m_fFFTPitchMin;
					m_fFFTPitchMin = 0;
				}
				if (m_fFFTPitchMax > 12 * (10 + 1)) {
					m_fFFTPitchMin -= (m_fFFTPitchMax - 12 * (10 + 1));
					m_fFFTPitchMax = 12 * (10 + 1);
				}
				m_fFFTFreqMax = Pitch2Freq(m_fFFTPitchMax);
				m_fFFTFreqMin = Pitch2Freq(m_fFFTPitchMin);
			} else if (m_nFinger == 2) {
				disX = (float) Math.abs(MEvent.getX(0) - MEvent.getX(1))
						/ m_afMouseDis[0];
				disY = (float) Math.abs(MEvent.getY(0) - MEvent.getY(1))
						/ m_afMouseDis[1];
				float pitchMax, pitchMin, pitchCenter, pitchRange;
				float dBMax, dBMin, dBCenter, dBRange;
				if (m_afMouseDis[1] > m_afMouseDis[0]) {
					dBCenter = (m_fdBMax + m_fdBMin) / 2;
					dBRange = (m_fdBMax - m_fdBMin)
							/ disY;

					if (dBRange < 30)
						dBRange = 30;

					if (dBRange > 80)
						dBRange = 80;

					dBMax = dBCenter + dBRange / 2;
					dBMin = dBCenter - dBRange / 2;

					if (dBMax < 10) {
						range = dBMax - dBMin;
						dBMax = 10;
						dBMin = 10 - range;
					} else if (dBMin > 20) {
						range = dBMax - dBMin;
						// m_fdBMax+=(m_fdBMin - (-10));
						dBMin = 20;
						dBMax = range + dBMin;

					} else if (m_fdBMin < -7) {
						range = m_fdBMax - m_fdBMin;
						// m_fdBMax+=(m_fdBMin - (-10));
						dBMin = -7;
						dBMax = range + dBMin;
					}
					m_fdBMax = dBMax;
					m_fdBMin = dBMin;
					
				} else {
					pitchCenter = (m_fTouchFFTPitchMax + m_fTouchFFTPitchMin) / 2;
					pitchRange = (m_fTouchFFTPitchMax - m_fTouchFFTPitchMin)
							/ disX;

					if (pitchRange < 14)
						pitchRange = 14;

					if (pitchRange > 12 * (10 + 1))
						pitchRange = 12 * (10 + 1);

					pitchMax = pitchCenter + pitchRange / 2;
					pitchMin = pitchCenter - pitchRange / 2;

					if (pitchMin < 0) {
						pitchMax -= pitchMin;
						pitchMin = 0;
					}
					if (pitchMax > 12 * (10 + 1)) {
						pitchMin -= (pitchMax - 12 * (10 + 1));
						pitchMax = 12 * (10 + 1);
					}

					m_fFFTPitchMax = pitchMax;
					m_fFFTPitchMin = pitchMin;

					m_fFFTFreqMax = Pitch2Freq(m_fFFTPitchMax);
					m_fFFTFreqMin = Pitch2Freq(m_fFFTPitchMin);
				}
			} else if (m_nFinger == 3)
			{
        		disX = MEvent.getX(0)-m_afMousePos[0];
        		disY = MEvent.getY(0)-m_afMousePos[1];
            	m_afMousePos[0] = MEvent.getX(0);
            	m_afMousePos[1] = MEvent.getY(0);  
            	
				float timeScale = m_fSpectrogramTimeScale + disY / this.getHeight() * 4;
				if (timeScale < (float)this.getHeight()/((float)m_nBitmapHeight)) {
					timeScale = (float)this.getHeight()/((float)m_nBitmapHeight);
				}
				if (timeScale > 10)
					timeScale = 10;
				m_fSpectrogramTimeScale = timeScale;
			}
			break;
		}
		this.postInvalidate();
		return true;

	}

	@Override
	protected void onDraw(Canvas canvas) {
		// super.onDraw(canvas);

		long ffttime;
		ffttime = System.currentTimeMillis();

		int ciMargin = 10;
		int rectX, rectY, rectW, rectH;

		rectX = rectY = ciMargin;
		rectW = this.getWidth() - ciMargin * 2;
		rectH = this.getHeight() - ciMargin * 2 - 20;

		int ciStringMinPx = 50;
		float fDataStart, fDataRange;

		fDataStart = m_fFFTPitchMin;
		fDataRange = m_fFFTPitchMax - m_fFFTPitchMin;

		char ccNotes[] = { 'C', 'D', 'E', 'F', 'G', 'A', 'B' };
		char ccCents[] = { 2, 2, 1, 2, 2, 2, 1 };
		char ccCentsWhiteKey[] = { 0, 2, 4, 5, 7, 9, 11 };

		m_lockDraw.lock();
		if (m_bitmap != null) {
			float scaleX, scaleY, transX, transY;
			transX = (m_fFFTPitchMin - m_nBitmapPitchS)
					/ (m_nBitmapPitchE - m_nBitmapPitchS)
					* (m_nBitmapWidth - 1);
			transY = 0;

			scaleX = (m_fFFTPitchMax - m_nBitmapPitchS)
					/ (m_nBitmapPitchE - m_nBitmapPitchS)
					* (m_nBitmapWidth - 1) - transX;
			scaleX = ((float) rectW) / scaleX;
			scaleY = scaleX * m_fSpectrogramTimeScale;

			transX *= -scaleX;
			transX += rectX;

			// float fCurFPS = 44100.f/(m_nFFT*m_fOverlapRatio);
			// scaleY = (this.getHeight()/15)/fCurFPS;
			scaleY = m_fSpectrogramTimeScale;
			if (scaleY * m_nBitmapHeight < this.getHeight()) {
				scaleY *= (float) (this.getHeight())
						/ (scaleY * m_nBitmapHeight);
			}

			Matrix matrix = new Matrix();
			// matrix.postScale((float)rectH/(float)m_nBitmapWidth,(float)rectW/(float)m_nBitmapHeight);

			matrix.postScale(scaleX, scaleY);
			matrix.postTranslate(transX, transY);

			canvas.drawBitmap(m_bitmap, matrix, null);// new Paint()
			// canvas.drawBitmap(m_bitmap, 0, 0, null);
		}
		// else
		// m_bitmap =
		// Bitmap.createBitmap(this.getWidth(),this.getHeight(),Bitmap.Config.ARGB_8888);
		// m_bitmap.mBuffer[0]=0;
		m_lockDraw.unlock();

		Paint paintStroke = new Paint();
		paintStroke.setStyle(Paint.Style.STROKE);
		paintStroke.setStrokeWidth(1);
		paintStroke.setColor(Color.BLACK);

		Paint paintFillWave = new Paint();
		paintFillWave.setStyle(Paint.Style.FILL);
		paintFillWave.setColor(Color.YELLOW);

		Paint paintFillPitch = new Paint();
		paintFillPitch.setStyle(Paint.Style.FILL);
		paintFillPitch.setColor(Color.WHITE);

		paintStroke.setTextSize(20);
		paintFillPitch.setTextSize(20);

		float fVSum, fVAvgPre;
		float fVCount;
		int iImgPosPre, iImgPosCur, iImgPosNext;
		float fVPre, fVCur, fVNext;

		// char tbuffer[64];
		for (int p = 0; p < fDataStart + fDataRange; p++)// =ccCents[c])
		{
			// c=(c+1)%7;
			// c=(c+1)%12;
			if (p < fDataStart)
				continue;
			iImgPosCur = (int) (((float) p - fDataStart) / fDataRange * (float) rectW);

			float point0_x, point0_y, point1_x, point1_y;
			point0_x = rectX + iImgPosCur;
			point0_y = rectY;
			point1_x = rectX + iImgPosCur;
			point1_y = rectY + rectH;

			int note = p % 12;
			if (note == 0) {
				paintStroke.setStrokeWidth(5);
				canvas.drawLine(point0_x, point0_y, point1_x, point1_y,
						paintStroke);
				paintFillPitch.setStrokeWidth(3);
				paintFillPitch.setColor(Color.WHITE);
				canvas.drawLine(point0_x, point0_y, point1_x, point1_y,
						paintFillPitch);

				canvas.drawText("C" + (p / 12 - 1), point1_x - 10,
						point1_y + 25, paintStroke);
				canvas.drawText("C" + (p / 12 - 1), point1_x - 10,
						point1_y + 25, paintFillPitch);
				// m_pWin.pD2D->m_pRenderTarget->DrawLine(point0,point1,m_pBrushBlackAlpha,3);
				// m_pWin.pD2D->m_pRenderTarget->DrawLine(point0,point1,m_pBrushWhite,1);
				// int slen = _stprintf_s(tbuffer,64,_T("C%d"),p/12-1);
				// D2D1_RECT_F layoutRect =
				// D2D1::RectF((point1.x-3)*m_fDPIScaleX,(point1.y-5)*m_fDPIScaleY,(point1.x+100)*m_fDPIScaleX,(point1.y+100)*m_fDPIScaleY);
				// m_pWin.pD2D->m_pRenderTarget->DrawText(tbuffer,slen,m_pWriteTextFormat,layoutRect,m_pBrushWhite);

				// cvPutText(pImg,buffer,cvPoint(rectX+iImgPosCur,rectY+rectH+20),&cvFont(1,1),CV_RGB(255,255,255));
			} else if (note == 2 || note == 4 || note == 5 || note == 7
					|| note == 9 || note == 11) {
				paintFillPitch.setStrokeWidth(1);
				paintFillPitch.setColor(Color.WHITE);
				canvas.drawLine(point0_x, point0_y, point1_x, point1_y,
						paintFillPitch);
			} else {
				paintFillPitch.setStrokeWidth(2);
				paintFillPitch.setColor(0xFF606060);
				canvas.drawLine(point0_x, point0_y, point1_x, point1_y,
						paintFillPitch);
			}

			// printf("%d[%d],",p,iImgPosCur);
		}

		m_lockFFT.lock();
		if (m_afSpectrumBuffer != null) {
			float fFreqGap = ((float) m_nSourceSampleRate) / m_nFFT;
			int iMag0, iMag1;
			iMag0 = (int) Math.floor(m_fFFTFreqMin / fFreqGap);
			iMag1 = (int) Math.ceil(m_fFFTFreqMax / fFreqGap);

			if (iMag0 < 0)
				iMag0 = 0;
			while (m_afFFT2Pitch[iMag0] < 0)
				iMag0++;

			if (iMag1 >= m_nFFT / 2 + 1)
				iMag1 = m_nFFT / 2;

			fVSum = fVCount = 0;
			iImgPosPre = iImgPosCur = iImgPosNext = 0;

			fVNext = m_afSpectrumBuffer[iMag0];
			fVNext = fVNext >= 0.0001 ? Math.max(Mag2dB(fVNext), m_fdBMin)
					: m_fdBMin;

			fVPre = fVCur = fVNext;
			fVAvgPre = 0;

			paintFillPitch.setColor(Color.YELLOW);

			if (m_isEnableQuadraticInterpolation) {
				for (int i = iMag0; i < iMag1; i++) {
					iImgPosPre = iImgPosCur;
					iImgPosCur = iImgPosNext;
					fVPre = fVCur;
					fVCur = fVNext;
					iImgPosNext = (int) ((m_afFFT2Pitch[i + 1] - fDataStart)
							/ fDataRange * rectW);

					// fVNext = Mag2dB(GetFFTOutputMagnitude(i+1));
					// fVNext = fVNext>=0.0001? Mag2dB(fVNext) : fdBLow;
					fVNext = m_afSpectrumBuffer[i + 1];
					fVNext = fVNext >= 0.0001 ? Math.max(Mag2dB(fVNext),
							m_fdBMin) : m_fdBMin;

					if (fVPre != m_fdBMin || fVCur != m_fdBMin) {
						float point0_x, point0_y, point1_x, point1_y;
						if (fVCur > fVPre && fVCur > fVNext
								&& iImgPosCur - iImgPosPre > 2
								&& iImgPosNext - iImgPosCur > 2) {
							float a0, a1, a2;
							float xfloor, xceil, xp, fVPitch;
							a0 = fVPre
									/ ((iImgPosPre - iImgPosCur) * (iImgPosPre - iImgPosNext));
							a1 = fVCur
									/ ((iImgPosCur - iImgPosPre) * (iImgPosCur - iImgPosNext));
							a2 = fVNext
									/ ((iImgPosNext - iImgPosPre) * (iImgPosNext - iImgPosCur));

							int ps, pe;
							ps = (int) Math.ceil(m_afFFT2Pitch[Math.max(i - 1,
									1)]);
							pe = (int) Math.floor(m_afFFT2Pitch[i + 1]);

							if (ps <= pe) {
								float x0, x1, x2;
								x0 = iImgPosPre;
								x1 = iImgPosCur;
								x2 = iImgPosNext;
								for (int p = ps; p <= pe; p++) {
									xp = (p - fDataStart) / fDataRange * rectW;
									fVCur = a0 * (xp - x1) * (xp - x2) + a1
											* (xp - x0) * (xp - x2) + a2
											* (xp - x0) * (xp - x1);
									iImgPosCur = (int) xp;

									point0_x = rectX + iImgPosPre;
									point0_y = rectY
											+ ((fVPre - m_fdBMax) / (m_fdBMin - m_fdBMax))
											* rectH;
									point1_x = rectX + iImgPosCur;
									point1_y = rectY + (fVCur - m_fdBMax)
											/ (m_fdBMin - m_fdBMax) * rectH;
									paintStroke.setStrokeWidth(5);
									canvas.drawLine(point0_x, point0_y,
											point1_x, point1_y, paintStroke);
									paintFillPitch.setStrokeWidth(3);
									paintFillPitch.setColor(0xFFFFFF80);
									canvas.drawLine(point0_x, point0_y,
											point1_x, point1_y, paintFillPitch);

									iImgPosPre = iImgPosCur;
									fVPre = fVCur;
								}
							}

						}
						//else 
						{
							point0_x = rectX + iImgPosPre;
							point0_y = rectY
									+ ((fVPre - m_fdBMax) / (m_fdBMin - m_fdBMax))
									* rectH;
							point1_x = rectX + iImgPosCur;
							point1_y = rectY + (fVCur - m_fdBMax)
									/ (m_fdBMin - m_fdBMax) * rectH;

							paintStroke.setStrokeWidth(5);
							canvas.drawLine(point0_x, point0_y, point1_x,
									point1_y, paintStroke);
							paintFillPitch.setStrokeWidth(3);
							canvas.drawLine(point0_x, point0_y, point1_x,
									point1_y, paintFillPitch);
							paintFillPitch.setColor(Color.YELLOW);
						}
					}
				}
			} else {
				for (int i = iMag0; i < iMag1; i++) {
					iImgPosCur = iImgPosNext;
					fVCur = fVNext;
					iImgPosNext = (int) ((m_afFFT2Pitch[i + 1] - fDataStart)
							/ fDataRange * rectW);

					// fVNext = Mag2dB(GetFFTOutputMagnitude(i+1));
					// fVNext = fVNext>=0.0001? Mag2dB(fVNext) : fdBLow;
					fVNext = m_afSpectrumBuffer[i + 1];
					fVNext = fVNext >= 0.0001 ? Math.max(Mag2dB(fVNext),
							m_fdBMin) : m_fdBMin;

					if (fVCur != m_fdBMin || fVNext != m_fdBMin) {
						float point0_x, point0_y, point1_x, point1_y;

						point0_x = rectX + iImgPosCur;
						point0_y = rectY
								+ ((fVCur - m_fdBMax) / (m_fdBMin - m_fdBMax))
								* rectH;
						point1_x = rectX + iImgPosNext;
						point1_y = rectY + (fVNext - m_fdBMax)
								/ (m_fdBMin - m_fdBMax) * rectH;
						// m_pWin.pD2D->m_pRenderTarget->DrawLine(point0,point1,m_pBrushBlackAlpha,4);
						// m_pWin.pD2D->m_pRenderTarget->DrawLine(point0,point1,m_pBrushYellow,2);

						paintStroke.setStrokeWidth(5);
						canvas.drawLine(point0_x, point0_y, point1_x, point1_y,
								paintStroke);
						paintFillPitch.setStrokeWidth(3);
						paintFillPitch.setColor(Color.YELLOW);
						canvas.drawLine(point0_x, point0_y, point1_x, point1_y,
								paintFillPitch);

					}

				}
			}

		}

		m_lockFFT.unlock();

		if (m_nFinger == 1 || m_nFinger == 2) {
			paintStroke.setStrokeWidth(5);
			paintFillPitch.setStrokeWidth(3);
			paintFillPitch.setColor(Color.WHITE);

			canvas.drawText(String.format("%.2f", m_fdBMax) + " dB", rectX
					+ rectW - 80, rectY + 20, paintStroke);
			canvas.drawText(String.format("%.2f", m_fdBMax) + " dB", rectX
					+ rectW - 80, rectY + 20, paintFillPitch);
			canvas.drawText(String.format("%.2f", m_fdBMin) + " dB", rectX
					+ rectW - 80, rectY + rectH, paintStroke);
			canvas.drawText(String.format("%.2f", m_fdBMin) + " dB", rectX
					+ rectW - 80, rectY + rectH, paintFillPitch);
		}

		paintStroke.setStyle(Paint.Style.STROKE);
		paintStroke.setStrokeWidth(1);
		paintStroke.setColor(Color.MAGENTA);
		paintStroke.setTextSize(30);
		int textYPos = 0;
		canvas.drawText(
				"BufferUsage : "
						+ String.format("%6.2f", m_fBufferUsage)
						+ " % ("
						+ String.format("%6.2f",
								(m_nFFT * m_fOverlapRatio * 1000.f) / 44100.f)
						+ "-" + String.format("%6.2f", m_fOverlapRatio) + ")",
				10, textYPos += 35, paintStroke);
		canvas.drawText(
				"FFT : " + String.format("%.2f", m_fFFTTimeFFT) + " ms", 10,
				textYPos += 35, paintStroke);
		canvas.drawText(
				"FFTUpdate : " + String.format("%.2f", m_fFFTTimeFFTUpdate)
						+ " ms", 10, textYPos += 35, paintStroke);
		canvas.drawText("OnDraw : " + String.format("%.2f", m_fFFTTimeOnDraw)
				+ " ms", 10, textYPos += 35, paintStroke);
		canvas.drawText(
				"OnDrawUpdate : "
						+ String.format("%.2f", m_fFFTTimeOnDrawUpdate) + " ms",
				10, textYPos += 35, paintStroke);

		canvas.drawText("Fingers : " + m_nFinger + "  " + String.format("%.2f", m_fSpectrogramTimeScale), 10, textYPos += 35,
				paintStroke);

		ffttime = System.currentTimeMillis() - ffttime;
		if (m_fFFTTimeOnDraw != 0.0)
			m_fFFTTimeOnDraw = ((float) ffttime) * 0.1f + m_fFFTTimeOnDraw
					* 0.9f;
		else
			m_fFFTTimeOnDraw = (float) ffttime;

		if (m_lFFTTimeOnDrawUpdateCurTime != 0) {
			ffttime = System.currentTimeMillis()
					- m_lFFTTimeOnDrawUpdateCurTime;
			if (m_fFFTTimeOnDrawUpdate != 0.0)
				m_fFFTTimeOnDrawUpdate = ((float) ffttime) * 0.1f
						+ m_fFFTTimeOnDrawUpdate * 0.9f;
			else
				m_fFFTTimeOnDrawUpdate = (float) ffttime;
		}
		m_lFFTTimeOnDrawUpdateCurTime = System.currentTimeMillis();
	}

	// @Override
	// protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec){
	// int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
	// int parentHeight = MeasureSpec.getSize(heightMeasureSpec);
	// this.setMeasuredDimension(parentWidth/2, parentHeight);
	// this.setLayoutParams(new LayoutParams(parentWidth/2,parentHeight));
	// super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	// }

	private void ClearFFT() {
		m_lockFFT.lock();
		m_nFFT = 0;
		m_FFT = null;
		m_lockFFT.unlock();
	}

	public void SetBuffer(AudioBuffer buffer) {
		m_buffer = buffer;
	}

	public int GetFFT() {
		return m_nFFT;
	}

	public void SetFFT(int nFFT) {
		// ClearFFT();

		if (nFFT == m_nFFT)
			return;
		m_lockFFT.lock();
		m_FFT = new KissFFTWrapper(nFFT);
		m_nFFT = nFFT;
		m_lockFFT.unlock();
		// ByteBuffer byteBuffer = ByteBuffer.allocate(nFFT * 4);
		// IntBuffer intBuffer = byteBuffer.asIntBuffer();

	}

	public float[] GetDisplaySettings() {
		return new float[] { m_fFFTPitchMax, m_fFFTPitchMin, m_fFFTFreqMax,
				m_fFFTFreqMin, m_fdBMax, m_fdBMin, m_fSpectrogramTimeScale };
	}

	public void SetDisplaySettings(float[] settings) {
		if (settings.length != GetDisplaySettings().length)
			return;
		m_fFFTPitchMax = settings[0];
		m_fFFTPitchMin = settings[1];
		m_fFFTFreqMax = settings[2];
		m_fFFTFreqMin = settings[3];
		m_fdBMax = settings[4];
		m_fdBMin = settings[5];
		m_fSpectrogramTimeScale = settings[6];
	}

	public boolean GetEnableQuadraticInterpolation() {
		return m_isEnableQuadraticInterpolation;
	}

	public void SetEnableQuadraticInterpolation(boolean isEnable) {
		m_isEnableQuadraticInterpolation = isEnable;
	}

	public void StartFFT() {
		if (m_buffer != null && m_FFT != null) {
			if (m_FFTThread != null)
				StopFFT();
			m_isFFTLooping = true;

			m_FFTThread = new Thread(new Runnable() {

				@Override
				public void run() {
					FFTLoop();
				}
			}, "FFT Thread");

			m_FFTThread.start();
		}
	}

	private void FFTLoop() {
		while (true) {
			m_lockDraw.lock();
			if (m_bitmap != null) {
				m_lockDraw.unlock();
				break;
			}
			m_lockDraw.unlock();
			try {
				Thread.sleep(100);
			} catch (InterruptedException ex) {
				System.out.println(ex.toString());
			}
		}

		// ByteBuffer FFTBufferByte = null;
		byte[] abFFTBufferByte = null;
		int bufferSizeInBytes = -1;
		int nNewSamplesInBytes, nOldSamplesInBytes;

		// ShortBuffer FFTBufferShort = null;
		short[] asFFTBufferShort = null;
		// IntBuffer FFTBufferInt = null;

		// int [] aiFreqRemap = new int [m_bitmap.getHeight()];

		// ByteBuffer bitmapBufferByte =
		// ByteBuffer.allocate(m_bitmap.getWidth()*4);
		// IntBuffer bitmapBufferInt = bitmapBufferByte.asIntBuffer();
		// Arrays.fill(bitmapBufferInt.array(),(int)0x000000FF);

		int[] aiBitmapWBuffer = new int[m_bitmap.getWidth()];
		Arrays.fill(aiBitmapWBuffer, (int) 0xFF000000);

		try {
			for (int y = 0; y < m_bitmap.getHeight(); y++)
				m_bitmap.setPixels(aiBitmapWBuffer, 0, m_bitmap.getWidth(), 0,
						y, m_bitmap.getWidth(), 1);
		} catch (java.lang.Exception e) {
			Log.e("SpectrumView", "java.nio.Exception: " + e.toString());
		}

		// m_buffer.WaitSetDataAlignCondition();

		nNewSamplesInBytes = (int) ((float) m_nFFT * m_fOverlapRatio);
		nOldSamplesInBytes = m_nFFT - nNewSamplesInBytes;

		nNewSamplesInBytes *= m_buffer.GetBlockAlign();
		nOldSamplesInBytes *= m_buffer.GetBlockAlign();

		m_nSourceSampleRate = m_buffer.GetSampleRate();

		float fCurrentUpdateTime, fCurrentSettingTime, fDesirtTime;
		fCurrentSettingTime = (m_nFFT * m_fOverlapRatio * 1000.f)
				/ m_nSourceSampleRate;
		fDesirtTime = 1000.f / m_fDesiredFPS;

		while (m_isFFTLooping) {
			long ffttime;
			ffttime = System.currentTimeMillis();

			fCurrentUpdateTime = Math.max(m_fFFTTimeFFT, m_fFFTTimeOnDraw);

			if (m_fFFTTimeFFT != 0 && m_fFFTTimeOnDraw != 0) {
				if ((fCurrentUpdateTime + 10 < fCurrentSettingTime && fDesirtTime < fCurrentSettingTime)
						|| (fCurrentUpdateTime > fCurrentSettingTime)) {
					fCurrentUpdateTime += 10;
					if (fCurrentUpdateTime < fDesirtTime)
						fCurrentUpdateTime = fDesirtTime;
					fCurrentSettingTime = fCurrentUpdateTime;
					m_fOverlapRatio = fCurrentUpdateTime * m_nSourceSampleRate
							/ (m_nFFT * 1000.f);
					if (m_fOverlapRatio > 1)
						m_fOverlapRatio = 1;
					if (m_fOverlapRatio < 0.125f)
						m_fOverlapRatio = 0.125f;

					nNewSamplesInBytes = Math.max(
							(int) ((float) m_nFFT * m_fOverlapRatio), 128);
					nOldSamplesInBytes = m_nFFT - nNewSamplesInBytes;

					nNewSamplesInBytes *= m_buffer.GetBlockAlign();
					nOldSamplesInBytes *= m_buffer.GetBlockAlign();

				}
			}

			m_lockFFT.lock();
			if (bufferSizeInBytes != m_nFFT * m_buffer.GetBlockAlign()) {
				m_afSpectrumBuffer = null;
				m_afFFT2Pitch = null;
				bufferSizeInBytes = m_nFFT * m_buffer.GetBlockAlign();
				abFFTBufferByte = new byte[bufferSizeInBytes];
				Arrays.fill(abFFTBufferByte, (byte) 0x0);
				// FFTBufferByte = ByteBuffer.allocate(bufferSizeInBytes);

				switch (m_buffer.GetBlockAlign()) {
				case 1:

					asFFTBufferShort = null;
					// FFTBufferInt = null;
					break;

				case 2:
					asFFTBufferShort = new short[m_nFFT];
					// FFTBufferInt = null;
					break;

				case 4:
					asFFTBufferShort = null;
					// FFTBufferInt = FFTBufferByte.asIntBuffer();
					break;
				}
				m_afSpectrumBuffer = new float[m_nFFT / 2 + 1];
				m_afFFT2Pitch = new float[m_nFFT / 2 + 1];

				GenFFT2Pitch(m_nFFT, m_nSourceSampleRate, m_afFFT2Pitch);

				nNewSamplesInBytes = (int) ((float) m_nFFT * m_fOverlapRatio);
				nOldSamplesInBytes = m_nFFT - nNewSamplesInBytes;

				nNewSamplesInBytes *= m_buffer.GetBlockAlign();
				nOldSamplesInBytes *= m_buffer.GetBlockAlign();

				fCurrentSettingTime = (m_nFFT * m_fOverlapRatio * 1000.f)
						/ m_nSourceSampleRate;

				// GenFrqRemap(m_afSpectrumBuffer.length,aiFreqRemap,false);
			}

			m_fBufferUsage = m_buffer.GetBufferNewPersentage();

			if (m_buffer.GetNewDataSize() < nNewSamplesInBytes) {
				m_lockFFT.unlock();
				continue;
			}
			if (nOldSamplesInBytes > 0)
				System.arraycopy(abFFTBufferByte, nNewSamplesInBytes,
						abFFTBufferByte, 0, nOldSamplesInBytes);

			// if (m_fBufferUsage>99)
			// m_buffer.GetBufferBack(abFFTBufferByte,
			// nOldSamplesInBytes,nNewSamplesInBytes);
			// else
			m_buffer.GetBufferFront(abFFTBufferByte, nOldSamplesInBytes,
					nNewSamplesInBytes);

			if (asFFTBufferShort != null) {
				ShortBuffer tShortBuf = ByteBuffer.wrap(abFFTBufferByte)
				// .order(ByteOrder.LITTLE_ENDIAN)
						.asShortBuffer();
				// int[] intData = new int[intBuf.remaining()];
				tShortBuf.get(asFFTBufferShort);

				m_FFT.forward(asFFTBufferShort, m_afSpectrumBuffer);

				m_lockDraw.lock();
				// m_fFrqMax = m_FFT.updateBitmap(m_fFrqMax, m_afSpectrumBuffer,
				// aiFreqRemap, m_bitmap);
				m_FFT.updateBitmapPitch(m_nBitmapPitchS, m_nBitmapPitchE,
						m_fdBMax, m_fdBMin, m_afSpectrumBuffer, m_afFFT2Pitch,
						m_bitmap);
				m_lockDraw.unlock();

			}
			m_lockFFT.unlock();

			this.postInvalidate();

			ffttime = System.currentTimeMillis() - ffttime;
			if (m_fFFTTimeFFT != 0.0)
				m_fFFTTimeFFT = ((float) ffttime) * 0.1f + m_fFFTTimeFFT * 0.9f;
			else
				m_fFFTTimeFFT = (float) ffttime;

			if (m_lFFTTimeFFTUpdateCurTime != 0) {
				ffttime = System.currentTimeMillis()
						- m_lFFTTimeFFTUpdateCurTime;
				if (m_fFFTTimeFFTUpdate != 0.0)
					m_fFFTTimeFFTUpdate = ((float) ffttime) * 0.1f
							+ m_fFFTTimeFFTUpdate * 0.9f;
				else
					m_fFFTTimeFFTUpdate = (float) ffttime;
			}

			m_lFFTTimeFFTUpdateCurTime = System.currentTimeMillis();

		}
	}

	private void GenFFT2Pitch(int nFFT, int nSamplePerSec, float[] afFFT2Pitch) {
		int nHalfFFT = nFFT / 2 + 1;
		float fFreqGap = ((float) nSamplePerSec) / nFFT;

		afFFT2Pitch[0] = Freq2Pitch(0.0001f);
		for (int i = 1; i < nHalfFFT; i++)
			afFFT2Pitch[i] = Freq2Pitch(i * fFreqGap);

	}

	private void GenFrqRemap(int nIn, int[] remap, boolean bLinear) {
		if (nIn <= remap.length) {
			for (int i = 0; i < remap.length; i++)
				remap[i] = 1;
			return;
		} else if (bLinear) {
			int nInCountDown = nIn;
			int nCurHeight = nIn / remap.length;
			for (int i = 0; i < remap.length - 1; i++) {
				nInCountDown -= nCurHeight;
				remap[i] = nCurHeight;
			}
			remap[remap.length - 1] = nInCountDown;
			return;
		} else {
			int nInCountDown = nIn;
			int nCurHeight;
			float fMaxHeight = (nIn * 2) / remap.length;
			for (int i = 0; i < remap.length; i++) {
				nCurHeight = Math.max(
						(int) Math.ceil(fMaxHeight * i / remap.length), 1);
				nInCountDown -= nCurHeight;
				remap[i] = nCurHeight;
			}
			int debugCount = 0;
			for (int i = 0; i < remap.length; i++)
				debugCount += remap[i];
			Log.d("SpertrumView", "1. " + debugCount + " / " + nIn + " - "
					+ remap.length + " nInCountDown " + nInCountDown);
			if (nInCountDown != 0) {
				int nIncrease = (nInCountDown > 0) ? 1 : -1;
				int nIdx = remap.length - 1;
				nInCountDown = Math.abs(nInCountDown);
				while (nInCountDown > 0) {
					if (remap[nIdx] > 0) {
						nInCountDown--;
						remap[nIdx] += nIncrease;
					}
					nIdx--;
					if (nIdx < 0)
						nIdx = remap.length - 1;
				}
			}
			debugCount = 0;
			for (int i = 0; i < remap.length; i++)
				debugCount += remap[i];
			Log.d("SpertrumView", "2. " + debugCount + " / " + nIn + " - "
					+ remap.length);
			return;
		}
	}

	private void UpdateBitmap(float[] spectrum,
			int[] remap/* [bitmap.height] */, int[] bitmapBuffer) {

		for (int s = 0; s < spectrum.length; s++) {
			if (m_fFrqMax < spectrum[s])
				m_fFrqMax = spectrum[s];
		}

		float scaleColorSum;
		int scaleCount, pixelColor;
		scaleCount = 0;
		scaleColorSum = 0;

		for (int ifft = 0, y = 0, c = 0; ifft < spectrum.length
				&& y < m_bitmap.getHeight(); ifft++, c++) {
			scaleCount++;
			scaleColorSum += spectrum[ifft];

			if (c >= remap[y] - 1) {
				c = -1;
				// m_bitmap.getPixels(bitmapBuffer,0,m_bitmap.getWidth(),1,m_bitmap.getHeight()-1-y,m_bitmap.getWidth()-1,1);
				// m_bitmap.setPixels(bitmapBuffer,0,m_bitmap.getWidth(),0,m_bitmap.getHeight()-1-y,m_bitmap.getWidth()-1,1);

				// pImgData = (unsigned char*)m_pFrqImg->imageData +
				// ((m_pFrqImg->height-1-y)*m_pFrqImg->width+updateXPos)*m_pFrqImg->nChannels;
				// v = scaleColorSum/scaleCount;
				pixelColor = (int) (Math.min((scaleColorSum / scaleCount)
						/ (m_fFrqMax), 1) * 254);
				// pixelColor = (pixelColor<<24) | (pixelColor<<16) |
				// (pixelColor<<8) | 0xFF;
				// pixelColor = (0xFF<<24) | (pixelColor<<16) | (pixelColor<<8)
				// | 0xFF;
				pixelColor = (0xFF << 24) | (pixelColor << 16)
						| (pixelColor << 8) | pixelColor;
				m_bitmap.setPixel(m_bitmap.getWidth() - 1, m_bitmap.getHeight()
						- 1 - y, pixelColor);

				// v=(MIN((v-vMin)/(m_fFrqMax*0.4-vMin),1))*254.0;
				// pImgData[0] = pImgData[1] = pImgData[2] = v;
				y++;
				scaleCount = 0;
				scaleColorSum = 0;
			}
		}

		// for (int y=0;y<m_bitmap.getHeight();y++)
		// {
		//
		// }

	}

	public void StopFFT() {
		if (m_FFTThread != null) {
			m_isFFTLooping = false;
			while (m_FFTThread.getState() == Thread.State.RUNNABLE) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException ex) {
					System.out.println(ex.toString());
				}
			}
			m_FFTThread = null;
		}
	}

};
