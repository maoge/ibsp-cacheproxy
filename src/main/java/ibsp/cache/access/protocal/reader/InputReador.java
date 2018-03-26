package ibsp.cache.access.protocal.reader;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

import ibsp.cache.access.exception.UnexpectDataException;
import ibsp.cache.access.pool.mempool.ByteArray;
import ibsp.cache.access.util.IntCounter;

public interface InputReador {

	public byte readByte() throws BufferOverflowException, IOException;
	
	public void readWithLength(ByteArray bout, int len, IntCounter haveRead) throws BufferOverflowException, IOException;
	
	public void readWithLength(ByteBuffer bout, int len, IntCounter haveRead) throws BufferOverflowException, IOException;
	
	public int  readIntCrLf() throws UnexpectDataException, IOException;
	
	public long readLongCrLf() throws UnexpectDataException, IOException;
	
	public void recycle();
	
}
