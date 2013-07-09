package com.harpseal.test.micspectrogram;

import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
import java.nio.ByteBuffer;
import android.util.Log;

public class AudioBuffer_old_Backup {
	private ReentrantLock m_lock = new ReentrantLock();
	private Condition m_setDataAlignCondition = m_lock.newCondition();
	
	private ByteBuffer m_buffer;
	private static final int m_iBufferExtraCapacity = 4;
	private int m_nBlockAlign;
	
	public AudioBuffer_old_Backup(int defaultOutputMaxSize)
	{
		m_buffer = ByteBuffer.allocate(defaultOutputMaxSize);
		m_buffer.clear();
		m_nBlockAlign = 1;
	}
	
	public void EnsureCapacity(int capacityRequirementInBytes)
	{
		m_lock.lock();
		if (capacityRequirementInBytes*m_iBufferExtraCapacity>m_buffer.capacity())
		{
			ByteBuffer bufferNew;
			bufferNew = ByteBuffer.allocate(capacityRequirementInBytes*m_iBufferExtraCapacity);
			bufferNew.put(m_buffer);
			m_buffer = bufferNew;
		}	
		m_lock.unlock();
	}

	public void PushBuffer(byte[] pData,int nData)
	{
		if (nData<=0)
			nData = pData.length;
		m_lock.lock();
		try{
			if (nData*m_iBufferExtraCapacity>m_buffer.capacity())
			{
				ByteBuffer bufferNew;
				bufferNew = ByteBuffer.allocate(nData*m_iBufferExtraCapacity);
				bufferNew.put(m_buffer);
				m_buffer = bufferNew;
			}

			m_buffer.flip();
			int freeSpace = m_buffer.capacity()-m_buffer.remaining();
			if (nData>freeSpace)
			{
				int newlimit = m_buffer.limit()+nData-freeSpace;
				newlimit %= m_buffer.capacity();
				m_buffer.limit(newlimit);
			}
			m_buffer.flip();
			m_buffer.put(pData,0,nData);
			
		}catch(java.nio.BufferOverflowException e){
			Log.e("IO","java.nio.BufferOverflowException :" + e.toString());
		}
		m_lock.unlock();
		
	}
	
	public int GetBuffer(byte [] pData,int nData) 
	{
		int nGet = 0;
		
		m_lock.lock();
		try{
			nGet = java.lang.Math.min(pData.length, m_buffer.remaining());
			nGet = java.lang.Math.min(nGet,nData);
			m_buffer.flip();
			m_buffer.get(pData,0,nGet);
			m_buffer.flip();
		}catch (java.nio.BufferUnderflowException eb){
			Log.e("IO","java.nio.BufferUnderflowException");
		}
		m_lock.unlock();
		
		return nGet;
	}
	
	public int GetNewDataSize()
	{
		int res = 0;
		m_lock.lock();
		res = m_buffer.remaining();
		m_lock.unlock();
		return res;
	}
	
	
	public void SetBlockAlign(int nBlockAlign)
	{
		m_nBlockAlign = nBlockAlign;
	}
	
	public int GetBlockAlign()
	{
		return m_nBlockAlign;
	}
	
	public void SignalDataAlignCondition()
	{
		m_lock.lock();
		try{
			m_setDataAlignCondition.signal();
		}catch (java.lang.Exception e)
		{
			Log.i("Thread","someone interrupted the SetDataAlignCondition");
		} finally {
			m_lock.unlock();
	    }	
	}
	
	public void WaitSetDataAlignCondition()
	{
		m_lock.lock();
		try{
			m_setDataAlignCondition.await();
		}catch (java.lang.InterruptedException e)
		{
			Log.i("Thread","someone interrupted the WaitSetDataAlignCondition");
		} finally {
			m_lock.unlock();
	    }
	}
	
	public String toString(){
		int positionOriginal,limitOriginal;
		String resStr = "";
		
		m_lock.lock();
		
		positionOriginal = m_buffer.position();
		limitOriginal = m_buffer.limit();
		
		byte[] bytearray = new byte[m_buffer.remaining()];
		
		m_buffer.get(bytearray);
	
		m_buffer.position(positionOriginal);
		m_buffer.limit(limitOriginal);
		
		m_lock.unlock();
		
		for (int i=0;i<bytearray.length;i++)
			resStr += bytearray[i] + ", ";
		
		return resStr; 
		
	} 
};

