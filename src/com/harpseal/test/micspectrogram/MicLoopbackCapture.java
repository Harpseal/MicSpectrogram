package com.harpseal.test.micspectrogram;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import android.util.Log;



public class MicLoopbackCapture {
	private static final int RECORDER_SAMPLERATE = 44100;
	private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;//AudioFormat.CHANNEL_IN_STEREO;
	private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
	
	AudioBuffer m_buffer;
	
	private AudioRecord m_recorder = null;
	private int m_bufferSizeInBytes = 0;
	private Thread m_recordingThread = null;
	private boolean m_isRecording = false;
	
	public MicLoopbackCapture(AudioBuffer buffer)
	{
		m_buffer = buffer;
		m_bufferSizeInBytes = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE,RECORDER_CHANNELS,RECORDER_AUDIO_ENCODING);
		if (RECORDER_CHANNELS == AudioFormat.CHANNEL_IN_MONO)
			m_buffer.SetSourceAudioInfo(2,1,RECORDER_SAMPLERATE,16);//AudioFormat.ENCODING_PCM_16BIT;
		else
			m_buffer.SetSourceAudioInfo(4,2,RECORDER_SAMPLERATE,16);
		m_buffer.SignalDataAlignCondition();
	}
	
	
	public void StartRecording(){
		m_recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
						RECORDER_SAMPLERATE, RECORDER_CHANNELS,RECORDER_AUDIO_ENCODING, m_bufferSizeInBytes);
		
		if (m_recorder.getAudioFormat()== AudioFormat.ENCODING_PCM_16BIT)
			Log.i("MicLoopbackCapture","PCM_16BIT");
		else if (m_recorder.getAudioFormat()== AudioFormat.ENCODING_PCM_8BIT)
			Log.i("MicLoopbackCapture","PCM_8BIT");
		else
			Log.i("MicLoopbackCapture","PCM_ERROR");
		
		if (m_recorder. getAudioSource ()== MediaRecorder.AudioSource.MIC)
			Log.i("MicLoopbackCapture","SOURCE_MIC");
		else
			Log.i("MicLoopbackCapture","SOURCE_ERROR");

		if (m_recorder. getChannelConfiguration ()== AudioFormat.CHANNEL_IN_STEREO)
			Log.i("MicLoopbackCapture","CHANNEL_IN_STEREO");
		else if (m_recorder. getChannelConfiguration ()== AudioFormat.CHANNEL_IN_MONO)
			Log.i("MicLoopbackCapture","CHANNEL_IN_MONO");
		else
			Log.i("MicLoopbackCapture","CHANNEL_IN_ERROR");
		
		Log.i("MicLoopbackCapture","ChannelCount: " + m_recorder. getChannelCount());
		Log.i("MicLoopbackCapture","SampleRate: " + m_recorder.  getSampleRate ());
		Log.i("MicLoopbackCapture","BufferSizeInBytes: " + m_bufferSizeInBytes);
		
		m_recorder.startRecording();
		
		m_isRecording = true;
		
		m_recordingThread = new Thread(new Runnable() {
			
			@Override
			public void run() {
				WriteAudioDataToBuffer();
			}
		},"AudioRecorder Thread");
		
		m_recordingThread.start();
	}
	
	private void WriteAudioDataToBuffer(){
		byte data[] = new byte[m_bufferSizeInBytes];
		int read = 0;
		byte tbyte;
		
		while(m_isRecording){
			read = m_recorder.read(data, 0, m_bufferSizeInBytes);
			
			if(AudioRecord.ERROR_INVALID_OPERATION != read){
				if (read == 0)
				{
		            try {
		                Thread.sleep(10);

		            } catch (InterruptedException e) {
		                // TODO Auto-generated catch block
		                e.printStackTrace();
		            }
				}
				else
				{
					for (int i=0;i<read;i+=2)
					{
						tbyte = data[i];
						data[i] = data[i+1];
						data[i+1] = tbyte;
					}
					m_buffer.PushBuffer(data,read);
				}
			}
		}

	}
	
	public void StopRecording(){
		if(null != m_recorder){
			m_isRecording = false;
			
			m_recorder.stop();
			m_recorder.release();
			
			m_recorder = null;
			m_recordingThread = null;
		}
	}
}
