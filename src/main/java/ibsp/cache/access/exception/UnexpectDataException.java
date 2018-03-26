package ibsp.cache.access.exception;

public class UnexpectDataException extends RouteException {
	private static final long serialVersionUID = 1L;

	public UnexpectDataException(String message) {
		super(message, ROUTERRERINFO.e8);
	}

	public UnexpectDataException(String message, Exception e) {
		super(message, e);
		setErrorCode(ROUTERRERINFO.e8);
	}

}
