package ibsp.cache.access.pool.mempool;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;

public class BufferProxy {
	
	protected final ByteBuffer buffer;
	
	public BufferProxy(int capacity) {
		this(capacity, true);
	}

	public BufferProxy(int capacity, boolean isDirect) {
		super();
		if (isDirect)
			buffer = ByteBuffer.allocateDirect(capacity);
		else
			buffer = ByteBuffer.allocate(capacity);
	}

	public ByteBuffer getBuffer() {
		return buffer;
	}

	public final int capacity() {
		return buffer.capacity();
	}

	public final int position() {
		return buffer.position();
	}

	public final Buffer position(int newPosition) {
		return buffer.position(newPosition);
	}

	public final int limit() {
		return buffer.limit();
	}

	public final Buffer limit(int newLimit) {
		return buffer.limit(newLimit);
	}

	public final Buffer mark() {
		return buffer.mark();
	}

	public final Buffer reset() {
		return buffer.reset();
	}

	public final Buffer clear() {
		return buffer.clear();
	}

	public final Buffer flip() {
		return buffer.flip();
	}

	public final Buffer rewind() {
		return buffer.rewind();
	}

	public final int remaining() {
		return buffer.remaining();
	}

	public final boolean hasRemaining() {
		return buffer.hasRemaining();
	}

	public ByteBuffer slice() {
		return buffer.slice();
	}

	public boolean isReadOnly() {
		return buffer.isReadOnly();
	}

	public ByteBuffer duplicate() {
		return buffer.duplicate();
	}

	public ByteBuffer asReadOnlyBuffer() {
		return buffer.asReadOnlyBuffer();
	}

	public byte get() {
		return buffer.get();
	}

	public ByteBuffer put(byte b) {
		return buffer.put(b);
	}

	public byte get(int index) {
		return buffer.get(index);
	}

	public ByteBuffer put(int index, byte b) {
		return buffer.put(index, b);
	}

	public ByteBuffer get(byte[] dst, int offset, int length) {
		return buffer.get(dst, offset, length);
	}

	public ByteBuffer get(byte[] dst) {
		return buffer.get(dst);
	}

	public ByteBuffer put(ByteBuffer src) {
		return buffer.put(src);
	}

	public ByteBuffer put(byte[] src, int offset, int length) {
		return buffer.put(src, offset, length);
	}

	public final ByteBuffer put(byte[] src) {
		return buffer.put(src);
	}

	public final boolean hasArray() {
		return buffer.hasArray();
	}

	public final byte[] array() {
		return buffer.array();
	}

	public final int arrayOffset() {
		return buffer.arrayOffset();
	}

	public ByteBuffer compact() {
		return buffer.compact();
	}

	public boolean isDirect() {
		return buffer.isDirect();
	}


	public int compareTo(ByteBuffer that) {
		return buffer.compareTo(that);
	}

	public final ByteOrder order() {
		return buffer.order();
	}

	public final ByteBuffer order(ByteOrder bo) {
		return buffer.order(bo);
	}

	public char getChar() {
		return buffer.getChar();
	}
	
	public ByteBuffer putChar(char value) {
		return buffer.putChar(value);
	}

	public char getChar(int index) {
		return buffer.getChar(index);
	}

	public ByteBuffer putChar(int index, char value) {
		return buffer.putChar(index, value);
	}

	public CharBuffer asCharBuffer() {
		return buffer.asCharBuffer();
	}

	public short getShort() {
		return buffer.getShort();
	}

	public ByteBuffer putShort(short value) {
		return buffer.putShort(value);
	}

	public short getShort(int index) {
		return buffer.getShort(index);
	}

	public ByteBuffer putShort(int index, short value) {
		return buffer.putShort(index, value);
	}

	public ShortBuffer asShortBuffer() {
		return buffer.asShortBuffer();
	}

	public int getInt() {
		return buffer.getInt();
	}

	public ByteBuffer putInt(int value) {
		return buffer.putInt(value);
	}

	public int getInt(int index) {
		return buffer.getInt(index);
	}

	public ByteBuffer putInt(int index, int value) {
		return buffer.putInt(index, value);
	}

	public IntBuffer asIntBuffer() {
		return buffer.asIntBuffer();
	}

	public long getLong() {
		return buffer.getLong();
	}

	public ByteBuffer putLong(long value) {
		return buffer.putLong(value);
	}

	public long getLong(int index) {
		return buffer.getLong(index);
	}

	public ByteBuffer putLong(int index, long value) {
		return buffer.putLong(index, value);
	}

	public LongBuffer asLongBuffer() {
		return buffer.asLongBuffer();
	}

	public float getFloat() {
		return buffer.getFloat();
	}

	public ByteBuffer putFloat(float value) {
		return buffer.putFloat(value);
	}

	public float getFloat(int index) {
		return buffer.getFloat(index);
	}

	public ByteBuffer putFloat(int index, float value) {
		return buffer.putFloat(index, value);
	}

	public FloatBuffer asFloatBuffer() {
		return buffer.asFloatBuffer();
	}

	public double getDouble() {
		return buffer.getDouble();
	}

	public ByteBuffer putDouble(double value) {
		return buffer.putDouble(value);
	}

	public double getDouble(int index) {
		return buffer.getDouble(index);
	}

	public ByteBuffer putDouble(int index, double value) {
		return buffer.putDouble(index, value);
	}

	public DoubleBuffer asDoubleBuffer() {
		return buffer.asDoubleBuffer();
	}
	
	
	
}
