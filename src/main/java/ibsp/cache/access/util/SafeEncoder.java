package ibsp.cache.access.util;

import java.io.UnsupportedEncodingException;

import org.apache.log4j.Logger;

public class SafeEncoder {
	
	public static final Logger logger = Logger.getLogger(SafeEncoder.class);

	public static byte[] encode(final String str) {
		if (str == null)
			return null;
		
		try {
			return str.getBytes(CONSTS.CHARSET);
		} catch (UnsupportedEncodingException e) {
			logger.error(e.getStackTrace(), e);
		}
		
		return null;
	}
	
}
