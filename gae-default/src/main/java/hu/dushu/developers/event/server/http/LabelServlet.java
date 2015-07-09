package hu.dushu.developers.event.server.http;

import com.google.api.client.http.*;
import com.google.gdata.util.ServiceException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import hu.dushu.developers.event.server.ActiveEvent;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.Date;

/**
 * Created by renfeng on 6/22/15.
 */
@Singleton
public class LabelServlet extends HttpServlet {

	private static final Logger logger = LoggerFactory
			.getLogger(LabelServlet.class);

	private final HttpTransport transport;
	private final ActiveEvent activeEvent;

	@Inject
	public LabelServlet(HttpTransport transport, ActiveEvent activeEvent) {
		this.transport = transport;
		this.activeEvent = activeEvent;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		try {
			activeEvent.refresh(new Date());
		} catch (ServiceException e) {
			throw new ServletException(e);
		}

		HttpRequestFactory factory = transport.createRequestFactory();

		/*
		 * Google%20I%2FO%2015%20Extended%20-%20Auto%20number%20Name%20GDG%20Suzhou.label
		 */
		String label = activeEvent.getLabel();
		GenericUrl url = new GenericUrl(
				"https://googledrive.com/host/0B8bvxFOa9pJlfkVwbVlnWDF3TzJxdzJJZDMySzAwQzhyVmozMHRYSVBaX1NCMHpYd25jYnc/"
						+ URLEncoder.encode(label,"UTF-8"));
		HttpRequest request = factory.buildGetRequest(url);

		HttpResponse response = request.execute();
		if (response.getStatusCode() == 200) {
//			resp.addHeader("Content-Type", "text/javascript");
			IOUtils.copy(response.getContent(), resp.getOutputStream());
		} else {
			resp.setStatus(response.getStatusCode());
		}
	}
}