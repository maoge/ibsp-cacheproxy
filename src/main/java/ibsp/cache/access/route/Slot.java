package ibsp.cache.access.route;

import ibsp.cache.access.util.IDGenerator;

public class Slot implements Comparable<Object> {
	
	private long   id;
	
	// startSlot, endSlot不能重叠, 否则compareTo会不正常
	private int    startSlot = -1;
	private int    endSlot   = -1;	
	private HaNode haNode    = null;
	
	public Slot(int startSlot, int endSlot, HaNode haNode) {
		this.id        = IDGenerator.getGeneralIDGenerator().nextID();
		this.startSlot = startSlot;
		this.endSlot   = endSlot;
		this.haNode    = haNode;
	}
	
	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}
	
	public int getStartSlot() {
		return startSlot;
	}

	public void setStartSlot(int startSlot) {
		this.startSlot = startSlot;
	}

	public int getEndSlot() {
		return endSlot;
	}

	public void setEndSlot(int endSlot) {
		this.endSlot = endSlot;
	}
	
	public HaNode getHaNode() {
		return haNode;
	}

	public void setHaNode(HaNode haNode) {
		this.haNode = haNode;
	}
	
	public boolean isInMargin(int slot) {
		return slot>= startSlot && slot <= endSlot;
	}
	
	public int compare(int slot) {
		if (startSlot > slot)
			return -1;
		
		if (endSlot < slot)
			return 1;
		
		return 0;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + endSlot;
		result = prime * result + startSlot;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Slot other = (Slot) obj;
		if (endSlot != other.endSlot)
			return false;
		if (startSlot != other.startSlot)
			return false;
		return true;
	}

	@Override
	public int compareTo(Object o) {
		if (this.equals(o))
			return 0;
		
		Slot other = (Slot) o;
		if (startSlot < other.startSlot)
			return -1;
		if (endSlot > other.endSlot)
			return 1;
		
		return 0;
	}

	@Override
	public String toString() {
		return "Slot [id=" + id + ", startSlot=" + startSlot + ", endSlot="
				+ endSlot + "]";
	}
	
}
