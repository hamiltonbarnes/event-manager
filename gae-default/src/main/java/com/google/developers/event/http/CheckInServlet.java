package com.google.developers.event.http;

import com.google.api.client.http.*;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonGenerator;
import com.google.developers.api.CellFeedProcessor;
import com.google.developers.api.SpreadsheetManager;
import com.google.developers.event.ActiveEvent;
import com.google.gdata.util.ServiceException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by renfeng on 6/17/15.
 */
@Singleton
public class CheckInServlet extends HttpServlet implements Path {

	private static final Logger logger = LoggerFactory
			.getLogger(CheckInServlet.class);

	private static final String QR_CODE_COLUMN = "QR code";

	private final HttpTransport transport;
	private final JsonFactory jsonFactory;
	private final SpreadsheetManager spreadsheetManager;

	@Inject
	public CheckInServlet(HttpTransport transport, JsonFactory jsonFactory,
						  SpreadsheetManager spreadsheetManager) {
		this.transport = transport;
		this.jsonFactory = jsonFactory;
		this.spreadsheetManager = spreadsheetManager;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		req.getRequestDispatcher("/check-in/index.html").forward(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		final String uuid = req.getParameter("uuid");
		final ThreadLocal<String> emailThreadLocal = new ThreadLocal<>();
		final ThreadLocal<String> nameThreadLocal = new ThreadLocal<>();
		final ThreadLocal<String> numberThreadLocal = new ThreadLocal<>();
		final ThreadLocal<String> errorThreadLocal = new ThreadLocal<>();

		/*
		 * retrieve the urls of register and check-in for the latest event
		 */

		/*
		 * retrieve event id from http header, referer
		 * e.g.
		 * https://plus.google.com/events/c2vl1u3p3pbglde0gqhs7snv098
		 * https://developers.google.com/events/6031536915218432/
		 * https://hub.gdgx.io/events/6031536915218432
		 */
		String referer = req.getHeader("Referer");
//		Pattern gplusEventPattern = Pattern.compile("https://plus.google.com/events/" +
//				"[^/]+");
//		Pattern devsiteEventPattern = Pattern.compile("https://developers.google.com/events/" +
//				"[^/]+/");
//		Pattern gdgxHubEventPattern = Pattern.compile("https://hub.gdgx.io/events/" +
//				"([^/]+)");
		String requestURL = req.getRequestURL().toString();
		String urlBase = requestURL.substring(0, requestURL.indexOf(req.getRequestURI())) + CHECK_IN_URL;
		if (!referer.startsWith(urlBase) || referer.equals(urlBase)) {
			//req.getRequestDispatcher("/images/gdg-suzhou-museum-transparent.png").forward(req, resp);
			resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
			return;
		}

		String gplusEventUrl = "https://plus.google.com/events/" + referer.substring(urlBase.length());

		ActiveEvent activeEvent;
		try {
			activeEvent = ActiveEvent.get(gplusEventUrl, spreadsheetManager);
		} catch (ServiceException e) {
			throw new ServletException(e);
		}

		/*
		 * check if the id is registered
		 */
		{
			String registerURL = activeEvent.getRegisterResponsesURL();
			if (registerURL == null) {
				throw new ServletException(
						"Missing URL to the register form responses of event, " + activeEvent.getEvent());
			}

			final String registerEmailColumn = activeEvent.getRegisterEmailColumn();
			if (registerEmailColumn == null) {
				throw new ServletException(
						"Missing emailAddress column mapping for the register form responses of event, " +
								activeEvent.getEvent());
			}

			final String registerNameColumn = activeEvent.getRegisterNameColumn();
			if (registerNameColumn == null) {
				throw new ServletException(
						"Missing nickname column mapping for the register form responses of event, " +
								activeEvent.getEvent());
			}

			CellFeedProcessor cellFeedProcessor = new CellFeedProcessor(spreadsheetManager) {

				private int number = 1;

				@Override
				protected boolean processDataRow(Map<String, String> valueMap, URL cellFeedURL)
						throws IOException, ServiceException {
					String qrCode = valueMap.get(QR_CODE_COLUMN);
					if (qrCode != null && qrCode.equals(uuid)) {
						emailThreadLocal.set(valueMap.get(registerEmailColumn));
						nameThreadLocal.set(valueMap.get(registerNameColumn));
						numberThreadLocal.set(String.format("%03d", number));
						return false;
					}

					number++;

					return true;
				}
			};
			try {
				cellFeedProcessor.process(
						spreadsheetManager.getWorksheet(registerURL),
						registerEmailColumn, QR_CODE_COLUMN, registerNameColumn);
			} catch (ServiceException e) {
				throw new ServletException(e);
			}
		}

		final String email = emailThreadLocal.get();
		if (email != null) {
			/*
			 * check if the id is available for check in
			 */
			String checkInResponsesURL = activeEvent.getCheckInResponsesURL();
			if (checkInResponsesURL == null) {
				throw new ServletException(
						"Missing URL to the check-in form responses of event, " + activeEvent.getEvent());
			}

			final String checkInEmailColumn = activeEvent.getCheckInEmailColumn();
			if (checkInEmailColumn == null) {
				throw new ServletException(
						"Missing emailAddress column mapping for the check-in form responses of event, " +
								activeEvent.getEvent());
			}

			String checkInTimestampColumn = activeEvent.getCheckInTimestampColumn();
			if (checkInTimestampColumn == null) {
				throw new ServletException(
						"Missing timestamp column mapping for the check-in form responses of event, " +
								activeEvent.getEvent());
			}

			/*
			 * the check-in number is determined by the order of registration
			 */
//			numberThreadLocal.set("001");
			CellFeedProcessor cellFeedProcessor = new CellFeedProcessor(spreadsheetManager) {

//				private int number = 1;

				@Override
				protected boolean processDataRow(Map<String, String> valueMap, URL cellFeedURL)
						throws IOException, ServiceException {
					if (email.equals(valueMap.get(checkInEmailColumn))) {
						errorThreadLocal.set("Already checked in.");
						return false;
					}

//					number++;
//					numberThreadLocal.set(String.format("%03d", number));

					return true;
				}
			};
			try {
				/*
				 * timestamp column here was to ensure the number won't duplicate when the email is removed to
                 * allow check-in again. it's not a problem any more since
                 * the check-in number is determined by the order of registration
				 */
				cellFeedProcessor.process(
						spreadsheetManager.getWorksheet(checkInResponsesURL),
						checkInEmailColumn, checkInTimestampColumn);
			} catch (ServiceException e) {
				throw new ServletException(e);
			}
		} else {
			errorThreadLocal.set("Invalid QR code");
		}

		String error = errorThreadLocal.get();
		if (error == null) {
			String clientIp = req.getRemoteAddr();

			/*
			 * http post to formResponse with email entry parameter
			 */
			HttpRequestFactory factory = transport.createRequestFactory();

			GenericUrl url = new GenericUrl(activeEvent.getCheckInFormURL());

			Map<String, Object> params = new HashMap<>();
			params.put(activeEvent.getCheckInEmailEntry(), email);
			params.put(activeEvent.getCheckInClientIp(), clientIp);

			HttpContent content = new UrlEncodedContent(params);
			HttpRequest request = factory.buildPostRequest(url, content);

			HttpResponse response = request.execute();

			int statusCode = response.getStatusCode();
			if (statusCode != 200) {
				resp.setStatus(statusCode);
				return;
			}
		}

		/*
		 * headers must be set before body
		 *
		 * java - Response encoding of Google App Engine (can not change response encoding) - Stack Overflow
		 * http://stackoverflow.com/a/9447068/333033
		 */
		resp.setContentType("text/javascript; charset=utf-8");

		/*
		 * return nick name for label printer
		 */
		String name = nameThreadLocal.get();
		String number = numberThreadLocal.get();

		JsonGenerator generator = jsonFactory.createJsonGenerator(resp.getWriter());
		generator.writeStartObject();
		generator.writeFieldName("number");
		generator.writeString(number);
		generator.writeFieldName("name");
		generator.writeString(name);
		generator.writeFieldName("error");
		generator.writeString(error);
		generator.writeEndObject();
		generator.flush();
	}
}
