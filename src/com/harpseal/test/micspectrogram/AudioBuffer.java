package com.harpseal.test.micspectrogram;

import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
import java.nio.ByteBuffer;
import android.util.Log;

public class AudioBuffer {
	private ReentrantLock m_lock = new ReentrantLock();
	private Condition m_setDataAlignCondition = m_lock.newCondition();
	
	private int m_nSourceBlockAlign = 2;
	private int m_nSourceChannels = 1;
	private int m_nSourceSampleRate = 44100;
	private int m_nSourceBitsPreSample = 16;
	
	private byte [] m_pBuffer;
	private int m_nBufferSize;
	private int m_nBuffer;
	private int m_iBuffer;
	private int m_nBufferNewData;
	
	private static final int m_nBufferExtraCapacity = 8;
	
	public AudioBuffer(int defaultOutputMaxSizeInByte)
	{
		m_nSourceBlockAlign = 2;
		m_pBuffer = null;
		
		m_nBufferSize = 0;
		m_nBuffer = 0;
		m_iBuffer = 0;
		m_nBufferNewData = 0;		
		
		EnsureCapacity(defaultOutputMaxSizeInByte);

	}
	
	public void PushBuffer(byte[] pData,int nBytes)
	{
		m_lock.lock();
		EnsureCapacity(nBytes);
		if (m_iBuffer+nBytes>m_nBufferSize)
		{
			System.arraycopy(pData, 0, m_pBuffer, m_iBuffer, m_nBufferSize-m_iBuffer);
			System.arraycopy(pData, m_nBufferSize-m_iBuffer, m_pBuffer, 0, nBytes - (m_nBufferSize-m_iBuffer));
			//memcpy(m_pBuffer.pByte+m_iBuffer,pData,sizeof(BYTE)*(m_nBufferSize-m_iBuffer));
			//memcpy(m_pBuffer.pByte,pData+(m_nBufferSize-m_iBuffer),sizeof(BYTE)*(nBytes - (m_nBufferSize-m_iBuffer)));
			m_nBuffer = m_nBufferSize;
			m_iBuffer = nBytes - (m_nBufferSize-m_iBuffer);
		}
		else
		{
			System.arraycopy(pData, 0, m_pBuffer, m_iBuffer, nBytes);
			//memcpy(m_pBuffer.pByte+m_iBuffer,pData,sizeof(BYTE)*(nBytes));
			if (m_nBuffer<m_nBufferSize)
				m_nBuffer+=nBytes;
			m_iBuffer+=nBytes;
			if (m_iBuffer>=m_nBufferSize)
				m_iBuffer-=m_nBufferSize;
		}
		m_nBufferNewData += nBytes;
		if (m_nBufferNewData > m_nBufferSize)
			m_nBufferNewData = m_nBufferSize;
		m_lock.unlock();
	}
	
	public int GetBufferFront(byte [] pData,int nOffset,int nBytes) 
	{
		int nCopyedSize,iStartIdx;
		m_lock.lock();

		EnsureCapacity(nBytes);

		nCopyedSize = Math.min(nBytes,m_nBuffer);
		//if (m_nBuffer<m_nBufferSize)
		//	iStartIdx=0;
		//else
		//	iStartIdx = m_iBuffer;

		iStartIdx = m_iBuffer-Math.max(m_nBufferNewData,nCopyedSize);
		if (iStartIdx<0)
			iStartIdx+=m_nBufferSize;
		GetBuffer(iStartIdx,nCopyedSize,pData,nOffset,nBytes);
		m_lock.unlock();

		if (m_nBufferNewData<nCopyedSize)
			m_nBufferNewData=0;
		else
			m_nBufferNewData-=nCopyedSize;


		return nCopyedSize;
	}
	
	public int GetBufferBack(byte [] pData,int nOffset,int nBytes) 
	{
		int nCopyedSize,iStartIdx;
		m_lock.lock();

		EnsureCapacity(nBytes);

		nCopyedSize = Math.min(nBytes,m_nBuffer);
		iStartIdx = m_iBuffer-nCopyedSize;
		if (iStartIdx<0)
			iStartIdx+=m_nBufferSize;
		GetBuffer(iStartIdx,nCopyedSize,pData,nOffset,nBytes);

		m_lock.unlock();

		m_nBufferNewData=0;
		return nCopyedSize;
	}
	
	public int GetNewDataSize()
	{
		int res = 0;
		m_lock.lock();
		res = m_nBufferNewData;
		m_lock.unlock();
		return res;
	}
	
	public void SetSourceAudioInfo(int nBlockAlign,int nChannels,int nSampleRate,int nBitPreSample)
	{
		m_lock.lock();
		m_nSourceBlockAlign = nBlockAlign;
		m_nSourceChannels = nChannels;
		m_nSourceSampleRate = nSampleRate;
		m_nSourceBitsPreSample = nBitPreSample;	
		m_lock.unlock();
	}
	

	public void SetBlockAlign(int nBlockAlign)
	{
		m_nSourceBlockAlign = nBlockAlign;
	}
	
	public int GetBlockAlign()
	{
		return m_nSourceBlockAlign;
	}
	
	public int GetChannels()
	{
		return m_nSourceChannels;
	}
	
	public int GetSampleRate()
	{
		return m_nSourceSampleRate;
	}
	
	public int GetBitsPreSample()
	{
		return m_nSourceBitsPreSample;
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
		int iNewData=m_iBuffer-m_nBufferNewData;
		if (iNewData<0)
			iNewData += m_nBufferSize;
		String resStr = "<" + m_nBufferNewData + ">:";
	
		for (int i=0;i<m_nBufferSize;i++)
		{
			if (i==m_iBuffer && i==m_nBuffer)
				resStr += "{"+m_pBuffer[i]+"} ";
			else if (i==m_iBuffer)
				resStr += "["+m_pBuffer[i]+"] ";
			else if (i==iNewData)
				resStr += "|"+m_pBuffer[i]+"| ";
			else
				resStr += " "+m_pBuffer[i]+"  ";
		}
		return resStr; 
		
	} 
	public float GetBufferNewPersentage()
	{
		int nNewData;
		m_lock.lock();
		nNewData = m_nBufferNewData;
		m_lock.unlock();
		return (float)nNewData/(float)m_nBuffer*100.f;
		
	}
	
	
	private void EnsureCapacity(int capacityRequirementInBytes)
	{
	    if (capacityRequirementInBytes*m_nBufferExtraCapacity > m_nBufferSize) 
	    {
			byte [] pBufferNew = new byte[capacityRequirementInBytes*m_nBufferExtraCapacity];
			Arrays.fill(pBufferNew, (byte)0);
//System.arraycopy
			if (m_pBuffer!=null)
			{
				if (m_nBuffer>0)
				{
					if (m_nBuffer<m_nBufferSize)
					{
						System.arraycopy(m_pBuffer, 0, pBufferNew, 0, m_nBuffer);
					}
					else
					{
						System.arraycopy(m_pBuffer, m_iBuffer, pBufferNew, 0, m_nBuffer-m_iBuffer);
						System.arraycopy(m_pBuffer, 0, pBufferNew, m_nBuffer-m_iBuffer, m_iBuffer);
						m_iBuffer = m_nBuffer;
						
						//memcpy((BYTE*)pNewBuffer,m_pBuffer.pByte+m_iBuffer,sizeof(BYTE)*(m_nBuffer-m_iBuffer));
						//memcpy((BYTE*)pNewBuffer+(m_nBuffer-m_iBuffer),m_pBuffer.pByte,sizeof(BYTE)*(m_iBuffer));

					}
				}
			}
			m_pBuffer = pBufferNew;
			m_nBufferSize = capacityRequirementInBytes*m_nBufferExtraCapacity;
	    } 
	}
	
	private void GetBuffer(int iStartIdx,int nCopyedSize,byte [] pData,int nOffset,int nBytes)
	{
		assert(nCopyedSize<=nBytes);
		if (iStartIdx+nCopyedSize>m_nBufferSize)
		{
			System.arraycopy( m_pBuffer, iStartIdx, pData, nOffset, m_nBufferSize-iStartIdx);
			System.arraycopy( m_pBuffer, 0, pData, nOffset + m_nBufferSize-iStartIdx, nCopyedSize - (m_nBufferSize-iStartIdx));
			
			//memcpy(pData,m_pBuffer.pByte+iStartIdx,sizeof(BYTE)*(m_nBufferSize-iStartIdx));
			//memcpy(pData+(m_nBufferSize-iStartIdx),m_pBuffer.pByte,sizeof(BYTE)*(nCopyedSize - (m_nBufferSize-iStartIdx)));
		}
		else
		{
			System.arraycopy( m_pBuffer, iStartIdx, pData, nOffset, nCopyedSize);
			//memcpy(pData,m_pBuffer.pByte+iStartIdx,sizeof(BYTE)*(nCopyedSize));
		}
	}

};

/*
AudioBuffer::AudioBuffer(int defaultOutputMaxSize)
{
	m_hDataMutex = CreateMutex(NULL,false,NULL);
	m_pBuffer.pi64 = NULL;
	m_nBufferSize = 0;
	m_nBuffer = 0;
    m_iBuffer = 0;
	m_nBufferNewData = 0;
	m_nBufferExtraCapacity = 4;
	pWaveInfo = NULL;
	EnsureCapacity(defaultOutputMaxSize);
}

AudioBuffer::AudioBuffer()
{
	m_hDataMutex = CreateMutex(NULL,false,NULL);
	m_pBuffer.pi64 = NULL;
	m_nBufferSize = 0;
	m_nBuffer = 0;
    m_iBuffer = 0;
	m_nBufferNewData = 0;
	m_nBufferExtraCapacity = 4;
	pWaveInfo = NULL;
}

AudioBuffer::~AudioBuffer()
{
	CloseHandle(m_hDataMutex);
	if (m_pBuffer.pi64)
		delete [] m_pBuffer.pi64;
	if (pWaveInfo)
		delete pWaveInfo;
}


void AudioBuffer::EnsureCapacity(int capacityRequirementInBytes)
{
	int nNewSizeInInt64,nNewSizeInByte;
	nNewSizeInInt64 = (capacityRequirementInBytes/sizeof(INT64) + 1)*m_nBufferExtraCapacity;
	nNewSizeInByte = nNewSizeInInt64*sizeof(INT64);

    if (nNewSizeInByte > m_nBufferSize) 
    {
		INT64* pNewBuffer;
		pNewBuffer = new INT64[nNewSizeInInt64];

#ifdef _DEBUG
		memset(pNewBuffer,0,sizeof(INT64)*nNewSizeInInt64);
#endif

		if (m_pBuffer.pi64)
		{
			if (m_nBuffer>0)
			{
				if (m_nBuffer<m_nBufferSize)
				{
					assert(m_iBuffer == m_nBuffer);
					memcpy(pNewBuffer,m_pBuffer.pByte,sizeof(BYTE)*m_nBuffer);
				}
				else
				{
					memcpy((BYTE*)pNewBuffer,m_pBuffer.pByte+m_iBuffer,sizeof(BYTE)*(m_nBuffer-m_iBuffer));
					memcpy((BYTE*)pNewBuffer+(m_nBuffer-m_iBuffer),m_pBuffer.pByte,sizeof(BYTE)*(m_iBuffer));
					m_iBuffer = m_nBuffer;
				}
			}
			delete [] m_pBuffer.pi64;
		}
		m_pBuffer.pi64 = pNewBuffer;
		m_nBufferSize = nNewSizeInByte;
    } 
}

void AudioBuffer::PushBuffer(const BYTE *pData,int nBytes)
{
	WaitForSingleObject(m_hDataMutex, INFINITE);
	EnsureCapacity(nBytes);
	if (m_iBuffer+nBytes>m_nBufferSize)
	{
		memcpy(m_pBuffer.pByte+m_iBuffer,pData,sizeof(BYTE)*(m_nBufferSize-m_iBuffer));
		memcpy(m_pBuffer.pByte,pData+(m_nBufferSize-m_iBuffer),sizeof(BYTE)*(nBytes - (m_nBufferSize-m_iBuffer)));
		m_nBuffer = m_nBufferSize;
		m_iBuffer = nBytes - (m_nBufferSize-m_iBuffer);
	}
	else
	{
		memcpy(m_pBuffer.pByte+m_iBuffer,pData,sizeof(BYTE)*(nBytes));
		if (m_nBuffer<m_nBufferSize)
			m_nBuffer+=nBytes;
		m_iBuffer+=nBytes;
		if (m_iBuffer>=m_nBufferSize)
			m_iBuffer-=m_nBufferSize;
	}
	m_nBufferNewData += nBytes;
	if (m_nBufferNewData > m_nBufferSize)
		m_nBufferNewData = m_nBufferSize;
	ReleaseMutex(m_hDataMutex);
}

void AudioBuffer::GetBuffer(int iStartIdx,int nCopyedSize,BYTE *pData,int nBytes)
{
	assert(nCopyedSize<=nBytes);
	if (iStartIdx+nCopyedSize>m_nBufferSize)
	{
		memcpy(pData,m_pBuffer.pByte+iStartIdx,sizeof(BYTE)*(m_nBufferSize-iStartIdx));
		memcpy(pData+(m_nBufferSize-iStartIdx),m_pBuffer.pByte,sizeof(BYTE)*(nCopyedSize - (m_nBufferSize-iStartIdx)));
	}
	else
	{
		memcpy(pData,m_pBuffer.pByte+iStartIdx,sizeof(BYTE)*(nCopyedSize));
	}

}

int AudioBuffer::GetBufferFront(BYTE *pData,int nBytes)
{
	int nCopyedSize,iStartIdx;
	WaitForSingleObject(m_hDataMutex, INFINITE);

	EnsureCapacity(nBytes);

	nCopyedSize = min(nBytes,m_nBuffer);
	//if (m_nBuffer<m_nBufferSize)
	//	iStartIdx=0;
	//else
	//	iStartIdx = m_iBuffer;

	iStartIdx = m_iBuffer-max(m_nBufferNewData,nCopyedSize);
	if (iStartIdx<0)
		iStartIdx+=m_nBufferSize;
	GetBuffer(iStartIdx,nCopyedSize,pData,nBytes);
	ReleaseMutex(m_hDataMutex);

	if (m_nBufferNewData<nCopyedSize)
		m_nBufferNewData=0;
	else
		m_nBufferNewData-=nCopyedSize;


	return nCopyedSize;
}

int AudioBuffer::GetBufferBack(BYTE *pData,int nBytes)
{
	int nCopyedSize,iStartIdx;
	WaitForSingleObject(m_hDataMutex, INFINITE);

	EnsureCapacity(nBytes);

	nCopyedSize = min(nBytes,m_nBuffer);
	iStartIdx = m_iBuffer-nCopyedSize;
	if (iStartIdx<0)
		iStartIdx+=m_nBufferSize;
	GetBuffer(iStartIdx,nCopyedSize,pData,nBytes);

	ReleaseMutex(m_hDataMutex);

	m_nBufferNewData=0;
	return nCopyedSize;
}

int AudioBuffer::GetNewDataSize()
{
	int res;
	WaitForSingleObject(m_hDataMutex, INFINITE);
	res = m_nBufferNewData;
	ReleaseMutex(m_hDataMutex);
	return res;
}

void AudioBuffer::SetWaveInfo(LPCWAVEFORMATEX pInfo)
{
	if (pWaveInfo==NULL)
		pWaveInfo = new WAVEFORMATEX;
	memcpy(pWaveInfo,pInfo,sizeof(WAVEFORMATEX));
}

LPCWAVEFORMATEX AudioBuffer::GetWaveInfo()
{
	return pWaveInfo;
}

void AudioBuffer::PrintBuffer()
{
	printf("B ");
	for (int i=0;i<m_nBufferSize;i++)
	{
		if (i==m_iBuffer && i==m_nBuffer)
			printf("{%2d} ",((unsigned char*)m_pBuffer.pByte)[i]);
		else if (i==m_iBuffer)
			printf("[%2d] ",((unsigned char*)m_pBuffer.pByte)[i]);
		else
			printf(" %2d  ",((unsigned char*)m_pBuffer.pByte)[i]);
	}
	printf("\nNew Data: %d\n",m_nBufferNewData);
		
}
 */

