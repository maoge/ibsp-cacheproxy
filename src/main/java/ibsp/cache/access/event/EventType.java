package ibsp.cache.access.event;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public enum EventType {

	e63(50063, true, "redis automatic ha switch");       // redis故障主从切换

	private final int value;
	private final boolean alarm;
	private final String info;

	private static final Map<Integer, EventType> map = new HashMap<Integer, EventType>();

	static {
		for (EventType s : EnumSet.allOf(EventType.class)) {
			map.put(s.value, s);
		}
	}

	private EventType(int i, boolean b, String s) {
		value = i;
		alarm = b;
		info = s;
	}

	public static EventType get(int code) {
		return map.get(code);
	}

	public int getValue() {
		// 得到枚举值代表的字符串。
		return value;
	}

	public boolean isAarm() {
		return alarm;
	}

	public String getInfo() {
		// 得到枚举值代表的字符串。
		return info;
	}

	public boolean equals(EventType e) {
		return this.value == e.value;
	}

}
