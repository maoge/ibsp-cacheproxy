package ibsp.cache.access.exception;

public class NotEnoughException extends RouteException {
	private static final long serialVersionUID = 1L;

	public NotEnoughException(String message) {
		super(message, ROUTERRERINFO.e22);
	}

	public NotEnoughException(String message, Exception e) {
		super(message, e);
		setErrorCode(ROUTERRERINFO.e22);
	}

}
