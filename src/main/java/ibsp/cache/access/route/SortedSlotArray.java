package ibsp.cache.access.route;

import java.util.ArrayList;

public class SortedSlotArray {
	
	private ArrayList<Slot> slotArr;  // 按递增方式排序
	private volatile int size = 0;
	
	private static int DEFAULT_SIZE = 16;
	
	public SortedSlotArray() {
		slotArr = new ArrayList<Slot>(DEFAULT_SIZE);
	}
	
	public int locate(Slot slot) {
		if (slotArr == null)
			return -1;
		
		int idx = 0;
		for (; idx < size; idx++) {
			if (slot.compareTo(slotArr.get(idx)) < 0)
				break;
		}
		
		return idx;
	}
	
	public void addSlot(Slot slot) {
		int idx = locate(slot);
		if (idx != -1) {
			slotArr.add(idx, slot);
			size++;
		}
	}
	
	public boolean removeSlot(Slot slot) {
		boolean ret = slotArr.remove(slot);
		if (ret)
			size--;
		
		return ret;
	}
	
	public void clear() {
		size = 0;
		slotArr.clear();
	}
	
	/**
	 * 精确地查找到与开始和结束槽号相符的槽段
	 */
	public Slot searchAccurately(int start, int end) {
		for (Slot slot : slotArr) {
			if (slot.getStartSlot()==start && slot.getEndSlot()==end) {
				return slot;
			}
		}
		return null;
	}
	
	public boolean checkConflict(int start, int end) {
		for (Slot slot : slotArr) {
			if ((start>slot.getStartSlot() && start<slot.getEndSlot()) ||
					(end>slot.getStartSlot() && end<slot.getEndSlot()) ||
					(start<slot.getStartSlot() && end>slot.getEndSlot())) {
				return true;
			}
		}
		return false;
	}
	
	public Slot binarySearch(int slot) {
		int start = 0, end = size;
		int mid;
		Slot tmp = null;
		int cmp;
		boolean find = false;
		
		while (start < end) {
			mid = (start + end) / 2;
			tmp = slotArr.get(mid);
			cmp = tmp.compare(slot);
			if (cmp != 0) {
				if (mid == start || mid == end)
					break;
				
				if (cmp < 0)
					end = mid;
				else
					start = mid;
			} else {
				find = true;
				break;
			}
		}
		
		return find ? tmp : null;
	}
	
	public Slot binarySearch(Slot slot) {
		int start = 0, end = size;
		int mid;
		Slot tmp = null;
		int cmp;
		boolean find = false;
		
		while (start < end) {
			mid = (start + end) / 2;
			
			tmp = slotArr.get(mid);
			cmp = slot.compareTo(tmp);
			if (cmp != 0) {
				if (mid == start || mid == end)
					break;
				
				if (cmp < 0)
					end = mid;
				else
					start = mid;
			} else {
				find = true;
				break;
			}
		}
		
		return find ? tmp : null;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("size=" + size);
		sb.append(", slotArr:[");
		for (int i = 0; i < size; i++) {
			Slot slot = slotArr.get(i);
			sb.append("{").append(slot.toString()).append("}");
		}
		sb.append("]");
		
		return sb.toString();
	}

	public static void main(String[] args) {
		
		SortedSlotArray slotArr = new SortedSlotArray();
		Slot slot1 = new Slot(0, 4095);
		Slot slot4 = new Slot(12288, 16383);
		Slot slot3 = new Slot(8092, 12287);
		Slot slot2 = new Slot(4096, 8091);
		
		slotArr.addSlot(slot1);
		slotArr.addSlot(slot4);
		slotArr.addSlot(slot3);
		slotArr.addSlot(slot2);
		
		System.out.println(slotArr.toString());
		
		Slot slot = slotArr.binarySearch(16384);
		System.out.println(slot);
		
		//Slot slot = slotArr.binarySearch(new Slot(4096, 5000));
		//System.out.println(slot);
		
		/*long start = System.currentTimeMillis();
		long total = 100000000L;
		
		java.util.Random ran = new java.util.Random();
		int slotNum;
		int maxSlot = 0x3FFF;
		Slot slot;
		
		for (int i=0; i < total; i++) {
			slotNum = ran.nextInt() & maxSlot;
			slot = slotArr.binarySearch(slotNum);
			if (slot == null) {
				System.out.println("not fund, slotNum:" + slotNum);
			}
		}
		
		long end = System.currentTimeMillis();
		long diff = end - start;
		long tps = (total * 1000) / diff;
		
		System.out.println("diff:" + diff + ", tps:" + tps);*/
	}
	
}
