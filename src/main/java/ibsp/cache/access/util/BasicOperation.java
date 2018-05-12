package ibsp.cache.access.util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSONObject;

import ibsp.cache.access.configure.MetadataConfigProxyService;

public class BasicOperation {

	private static Logger logger = LoggerFactory.getLogger(BasicOperation.class);

	public static int getLocalIP(SVarObject sVarIP) {
		int ret = CONSTS.REVOKE_NOK;

		String rootUrl = MetadataConfigProxyService.getInstance().getMetasvrUrl();
		String reqUrl = String.format("http://%s/%s/%s", rootUrl, CONSTS.META_SERVICE, CONSTS.FUN_URL_TEST);

		SVarObject sVarInvoke = new SVarObject();
		boolean retInvoke = HttpUtils.getData(reqUrl, sVarInvoke);
		if (retInvoke) {
			JSONObject jsonObj = JSONObject.parseObject(sVarInvoke.getVal());
			ret = jsonObj.getIntValue(CONSTS.JSON_HEADER_RET_CODE);

			if (ret != CONSTS.REVOKE_OK) {
				String retInfo = jsonObj.getString(CONSTS.JSON_HEADER_RET_INFO);
				logger.error("getLocalIP error:{}", retInfo);
			} else {
				String ip = jsonObj.getString(CONSTS.JSON_HEADER_REMOTE_IP);
				sVarIP.setVal(ip);
			}
		} else {
			logger.error("http request:{} error.", reqUrl);
			MetadataConfigProxyService.getInstance().putBrokenUrl(rootUrl);
		}

		return ret;
	}

	@SuppressWarnings("resource")
	public static int getUsablePort(String ip, IVarObject iVarPort) {
		int ret = CONSTS.REVOKE_NOK;

		int basePort = CONSTS.BASE_PORT;
		int maxPort = basePort + CONSTS.BATCH_FIND_CNT;
		for (int port = basePort; port < maxPort; port++) {
			try {
				ServerSocket sock = new ServerSocket();
				InetSocketAddress addr = new InetSocketAddress(ip, port);
				sock.bind(addr);

				if (sock.isBound()) {
					sock.close();
					iVarPort.setVal(port);

					ret = CONSTS.REVOKE_OK;
					break;
				}

			} catch (IOException e) {
				continue;
			}
		}

		return ret;
	}

	public static int putClientStatisticInfo(String context, String lsnrAddr) {
		int ret = CONSTS.REVOKE_NOK;

		String rootUrl = MetadataConfigProxyService.getInstance().getMetasvrUrl();
		
		String reqUrl = String.format("%s/%s/%s", rootUrl, CONSTS.META_SERVICE, CONSTS.FUN_PUT_STATISTIC_INFO);
		String params = String.format("%s=%s&%s=%s&%s=%s", CONSTS.PARAM_CLIENT_TYPE, CONSTS.TYPE_CACHE_PROXY,
				CONSTS.PARAM_LSNR_ADDR, lsnrAddr,
				CONSTS.PARAM_CLIENT_INFO, context);

		SVarObject sVarInvoke = new SVarObject();
		boolean retInvoke = HttpUtils.postData(reqUrl, params, sVarInvoke);
		if (retInvoke) {
			JSONObject jsonObj = JSONObject.parseObject(sVarInvoke.getVal());
			ret = jsonObj.getIntValue(CONSTS.JSON_HEADER_RET_CODE);
			if (ret != CONSTS.REVOKE_OK) {
				String errInfo = jsonObj.getString(CONSTS.JSON_HEADER_RET_INFO);
				logger.error("Error sending cache client statistic info...", errInfo);
			}
		} else {
			logger.error("http request:{} error.", reqUrl);
			MetadataConfigProxyService.getInstance().putBrokenUrl(rootUrl);
		}

		return ret;
	}

}
