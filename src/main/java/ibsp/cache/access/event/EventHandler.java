package ibsp.cache.access.event;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.ffcs.nio.core.buffer.IoBuffer;
import com.ffcs.nio.core.core.impl.HandlerAdapter;

import ibsp.cache.access.util.CONSTS;

import com.ffcs.nio.core.core.Session;

public class EventHandler extends HandlerAdapter {
	
	private static Logger logger = LoggerFactory.getLogger(EventHandler.class);
	private static CharsetDecoder decoder = Charset.forName("UTF-8").newDecoder();

	public EventHandler() {
		
	}
	
	@Override
	public void onMessageSent(Session session, Object msg)
    {
		
    }

	@Override
	public void onMessageReceived(Session session, Object msg) {
		if (msg == null)
			return;
		
		try {
			IoBuffer ioBuff = (IoBuffer) msg;
			
			int buffLen = ioBuff.limit();
			boolean headMatch = true;
			
			byte[] buffArr = ioBuff.array();
			if (buffLen > CONSTS.FIX_HEAD_LEN) {
				for (int i = 0; i < CONSTS.FIX_PREHEAD_LEN; i++) {
					if (buffArr[i] != CONSTS.PRE_HEAD[i]) {
						headMatch = false;
						break;
					}
				}
			} else {
				return;
			}
			
			if (!headMatch) {
				return;
			}
			
			int bodyLen = 0;
			bodyLen |= buffArr[CONSTS.FIX_PREHEAD_LEN] & 0xff;
			bodyLen |= (buffArr[CONSTS.FIX_PREHEAD_LEN+1] & 0xff) << 8;
			bodyLen |= (buffArr[CONSTS.FIX_PREHEAD_LEN+2] & 0xff) << 16;
			bodyLen |= (buffArr[CONSTS.FIX_PREHEAD_LEN+3] & 0xff) << 24;
			
			if (buffLen < CONSTS.FIX_HEAD_LEN + bodyLen)
				return;
			
			JSONObject event = this.parseJson(buffArr, CONSTS.FIX_HEAD_LEN, bodyLen);
			if (event == null)
				return;
			
			logger.info("rev EventMsg:{}", event.toJSONString());
			EventController.getInstance().pushEventMsg(event);
		} catch(Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	private JSONObject parseJson(byte[] bs, int offset, int len) {
		JSONObject result = null;
		try {
			result = (JSONObject) JSONObject.parse(bs, offset, len, decoder, JSON.DEFAULT_PARSER_FEATURE);
		} catch(JSONException e) {
			logger.error("json:{} parse error", bs.toString());
			logger.error(e.getMessage(), e);
		}
		
		return result;
	}
}
