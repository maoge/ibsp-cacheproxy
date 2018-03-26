package ibsp.cache.access.protocal.reader;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import ibsp.cache.access.Config;
import ibsp.cache.access.exception.UnexpectDataException;
import ibsp.cache.access.pool.mempool.ByteArray;
import ibsp.cache.access.request.RedisRequest;
import ibsp.cache.access.util.IntCounter;


public class NioRedisInputReador implements InputReador {
	
	protected byte buf[];
	protected volatile int count, limit;
	protected ByteBuffer bytebuff;
	
	protected RedisRequest current;
	
	protected final SocketChannel reador;
	
	public NioRedisInputReador(SocketChannel socketChannel) {
		this.reador = socketChannel;
	}

	void initBuff() {
		if (bytebuff == null) {
			synchronized (this) {
				if (bytebuff == null) {
					bytebuff = ByteBuffer.allocate(Config.getConfig().getRedis_input_reador_buffsize());
					this.buf = bytebuff.array();
					this.limit = bytebuff.position();
					this.count = 0;
				}
			}
		}
	}

	public byte readByte() throws BufferOverflowException, IOException {
		ensureFill();
		return buf[count++];
	}

	public void readWithLength(ByteArray bout, int len, IntCounter haveRead) throws BufferOverflowException, IOException {
		ensureFill();
			
		while (haveRead.get() < len) {
			if (count == limit) {
				ensureFill();
			}
			
			int needRead = len - haveRead.get();
			int buffRemain = limit - count;
			if (buffRemain >= needRead) {
				System.arraycopy(buf, count, bout.getBytes(), haveRead.get(), needRead);
				count += needRead;
				haveRead.add(needRead);
				break;
			} else {
				System.arraycopy(buf, count, bout.getBytes(), haveRead.get(), buffRemain);
				count += buffRemain;
				haveRead.add(buffRemain);
			}
		}
	}
	
	public void readWithLength(ByteBuffer bout, int len, IntCounter haveRead) throws BufferOverflowException, IOException {
		ensureFill();
			
		while (haveRead.get() < len) {
			if (count == limit) {
				ensureFill();
			}
			
			int needRead = len - haveRead.get();
			int buffRemain = limit - count;
			if (buffRemain >= needRead) {
				bout.put(buf, count, needRead);
				count += needRead;
				haveRead.add(needRead);
				break;
			} else {
				bout.put(buf, count, buffRemain);
				count += buffRemain;
				haveRead.add(buffRemain);
			}
		}
	}
	
	public int readIntCrLf() throws UnexpectDataException, IOException {
		return (int) readLongCrLf();
	}

	public long readLongCrLf() throws UnexpectDataException, IOException {
		final byte[] buf = this.buf;

		ensureFill();

		final boolean isNeg = buf[count] == '-';
		if (isNeg) {
			++count;
		}

		long value = 0;
		while (true) {
			ensureFill();

			final int b = buf[count++];
			if (b == '\r') {
				ensureFill();

				if (buf[count++] != '\n') {
					throw new UnexpectDataException("Unexpected character!");
				}

				break;
			} else {
				value = value * 10 + b - '0';
			}
		}

		return (isNeg ? -value : value);
	}

	/**
	 * This methods assumes there are required bytes to be read. If we cannot read anymore bytes an
	 * exception is thrown to quickly ascertain that the stream was smaller than expected.
	 * @throws IOException 
	 */
	protected final void ensureFill() throws BufferOverflowException, IOException {
		initBuff();
		if (count >= limit) {
			if (limit != bytebuff.position()) {
				throw new IOException("bug of NioRedisInputStream.");
			}
			
			try {
				bytebuff.clear();
				count = 0;
				limit = nioRead();
				
				if (limit == -1) {
					throw new IOException("Unexpected end of stream.");
				}
//				if (limit == 0 ) {
//					System.out.println("ensureFill BufferOverflowException ......");
//					throw new BufferOverflowException(); 
//				}
			} catch (IOException e) {
				throw e;
			}
		}
	}
	
	protected int nioRead() throws IOException {
		return reador.read(bytebuff);
	}

	public void recycle() {
		
	}
	
}
