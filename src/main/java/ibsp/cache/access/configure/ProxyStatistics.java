package ibsp.cache.access.configure;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanConstructorInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.ReflectionException;
import javax.management.modelmbean.ModelMBeanAttributeInfo;
import javax.management.modelmbean.ModelMBeanOperationInfo;

import ibsp.cache.access.util.Metrics;

public class ProxyStatistics implements IStatisticsMBean, DynamicMBean, Serializable {
	private static final long serialVersionUID = -5819368300823149669L;
	private ICacheProxyService cacheProxyService = null;
    
    public ProxyStatistics() {
    }
    
	public ProxyStatistics(ICacheProxyService _cacheProxyService) {
		this.cacheProxyService = _cacheProxyService;
	}
	
	@Override
	public void stop() {
        System.out.println("proxy stop!");
	}

	@Override
	public void restart() {
        System.out.println("proxy restart!");
	}

	@Override
	public String execute(String command) {
        System.out.println("hello world " + command);
        return "hello world " + command;
	}
	
	@Override
	public Object getAttribute(String attribute) throws AttributeNotFoundException, MBeanException, ReflectionException {
        Class<? extends ProxyStatistics> cls = this.getClass();
        try {
            String methodName = getMethodNameByField(attribute);
            Method attributeGet = cls.getMethod("get" + methodName, new Class[0]);
            return attributeGet.invoke(this, new Object[0]);
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
		return null;
	}

	@Override
	public void setAttribute(Attribute attribute) throws AttributeNotFoundException, InvalidAttributeValueException, MBeanException, ReflectionException {
        Class<? extends ProxyStatistics> cls = this.getClass();
        try {
            String methodName = getMethodNameByField(attribute.getName());
            Method attributeSet = cls.getMethod("set" + methodName, new Class[] { attribute.getValue().getClass() });
            attributeSet.invoke(this, attribute.getValue());
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
	}

    private String getMethodNameByField(String attribute) {
    	String[] temp = attribute.split("_");
    	StringBuilder sb = new StringBuilder("");
    	for (int i=0; i<temp.length; i++) {
    		String word = temp[i];
    		sb.append(word.substring(0, 1).toUpperCase() + word.substring(1));
    	}
        return sb.toString();
    }
	
	@Override
	public AttributeList getAttributes(String[] attributes) {
		return null;
	}

	@Override
	public AttributeList setAttributes(AttributeList attributes) {
		return null;
	}

	@Override
	public Object invoke(String actionName, Object[] params, String[] signature) throws MBeanException, ReflectionException {
        if("execute".equalsIgnoreCase(actionName)) {
        	return execute((String)params[0]);
        } else if("stop".equalsIgnoreCase(actionName)) {
        	stop();
        } else if("restart".equalsIgnoreCase(actionName)) {
        	restart();
        } else if ("getAccessClientConns".equalsIgnoreCase(actionName)) {
        	return getAccessClientConns();
        } else if ("getAccessRedisConns".equalsIgnoreCase(actionName)) {
        	return getAccessRedisConns();
        } else if ("getAccessGroupList".equalsIgnoreCase(actionName)) {
        	return getAccessGroupList();
        } else if ("getAccessGroupTps".equalsIgnoreCase(actionName)) {
        	return getAccessGroupTps();
        } else if ("getAccessRequestTps".equalsIgnoreCase(actionName)) {
        	return getAccessRequestTps();
        } else if ("getAccessProcessMaxTime".equalsIgnoreCase(actionName)) {
        	return getAccessProcessMaxTime();
        } else if ("getAccessProcessAvTime".equalsIgnoreCase(actionName)) {
        	return getAccessProcessAvTime();
        } else if ("getAccessRequestExcepts".equalsIgnoreCase(actionName)) {
        	return getAccessRequestExcepts();
        } else if ("getAccessGroupOnetimeTps".equalsIgnoreCase(actionName)) {
        	return getAccessGroupOnetimeTps();
        }
        
		return null;
	}

	@Override
	public MBeanInfo getMBeanInfo() {
        try {
            Class<? extends ProxyStatistics> cls = this.getClass();
            
            ModelMBeanAttributeInfo attribute1 = new ModelMBeanAttributeInfo("access_client_conns", "",
            		cls.getMethod("getAccessClientConns", null), null);
            ModelMBeanAttributeInfo attribute2 = new ModelMBeanAttributeInfo("access_redis_conns", "",
            		cls.getMethod("getAccessRedisConns", null), null);
            ModelMBeanAttributeInfo attribute3 = new ModelMBeanAttributeInfo("access_group_list", "",
            		cls.getMethod("getAccessGroupList", null), null);
            ModelMBeanAttributeInfo attribute4 = new ModelMBeanAttributeInfo("access_request_tps", "",
            		cls.getMethod("getAccessRequestTps", null), null);
            ModelMBeanAttributeInfo attribute5 = new ModelMBeanAttributeInfo("access_process_max_time", "",
            		cls.getMethod("getAccessProcessMaxTime", null), null);
            ModelMBeanAttributeInfo attribute6 = new ModelMBeanAttributeInfo("access_process_avg_time", "",
            		cls.getMethod("getAccessProcessAvTime", null), null);
            ModelMBeanAttributeInfo attribute7 = new ModelMBeanAttributeInfo("access_request_excepts", "",
            		cls.getMethod("getAccessRequestExcepts", null), null);
            
            
            Method m = cls.getMethod("execute", new Class[] { String.class });
            ModelMBeanOperationInfo execute = new ModelMBeanOperationInfo("execute", m);
            MBeanConstructorInfo mBeanConstructorInfo = new MBeanConstructorInfo("Constructor for ServerMonitor", cls.getConstructor(new Class[0]));
            MBeanInfo info = new MBeanInfo(cls.getName(), 
            		"proxy mbean server", 
            		new MBeanAttributeInfo[] { attribute1, attribute2, attribute3, attribute4, attribute5, attribute6, attribute7 },
                    new MBeanConstructorInfo[] { mBeanConstructorInfo }, 
                    new MBeanOperationInfo[] { execute }, 
                    null);
            System.out.println(info.getClassName() + this.hashCode());
            return info;
        } catch (Exception e) {
            e.printStackTrace();
        }
		return null;
	}

	@Override
	public long getAccessClientConns() {
		return Metrics.access_client_conns;
	}

	@Override
	public long getAccessRedisConns() {
		return Metrics.access_redis_conns;
	}

	@Override
	public String[] getAccessGroupList() {
		return Metrics.access_group_list;
	}

	@Override
	public Map<String, Long> getAccessGroupTps() {
		return Metrics.access_group_tps;
	}

	@Override
	public long getAccessRequestTps() {
		return Metrics.access_request_tps;
	}

	@Override
	public double getAccessProcessMaxTime() {
		return Metrics.access_process_max_time;
	}

	@Override
	public double getAccessProcessAvTime() {
		return Metrics.access_process_av_time;
	}

	@Override
	public long getAccessRequestExcepts() {
		return Metrics.access_request_excepts;
	}

	@Override
	public Map<String, Long> getAccessGroupOnetimeTps() {
		return Metrics.access_group_onetime_tps;
	}

}
