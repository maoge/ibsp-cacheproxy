package ibsp.cache.access.util;

public class IntCounter {
	
	private /*volatile*/ int counter;
	
	public IntCounter() {
		this.counter = 0;
	}
	
	public IntCounter(int counter) {
		this.counter = counter;
	}
	
	public int incrementAndGet() {
		return ++counter;
	}
	
	public void increment() {
		++counter;
	}
	
	public void add(int delta) {
		counter += delta;
	}
	
	public int get() {
		return counter;
	}
	
	public void set(int val) {
		this.counter = val;
	}
	
}
