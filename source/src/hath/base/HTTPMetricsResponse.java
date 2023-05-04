/*

Copyright 2008-2020 E-Hentai.org
https://forums.e-hentai.org/
ehentai@gmail.com

This file is part of Hentai@Home.

Hentai@Home is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Hentai@Home is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Hentai@Home.  If not, see <http://www.gnu.org/licenses/>.

*/

package hath.base;

import java.util.Map;
import java.util.regex.Pattern;

public class HTTPMetricsResponse {
	private static final Pattern absoluteUriPattern = Pattern.compile("^http://[^/]+/", Pattern.CASE_INSENSITIVE);

	public static final String LF = "\n";

	private HTTPMetricsSession session;

	private boolean requestHeadOnly;
	private int responseStatusCode;

	private HTTPResponseProcessor hpc;

	public HTTPMetricsResponse(HTTPMetricsSession session) {
		this.session = session;
		requestHeadOnly = false;
		responseStatusCode = 500;	// if nothing alters this, there's a bug somewhere
	}
	
	public void parseRequest(String request) {
		if(request == null) {
			Out.debug(session + " Client did not send a request.");
			responseStatusCode = 400;
			return;		
		}
	
		String[] requestParts = request.trim().split(" ", 3);

		if(requestParts.length != 3) {
			Out.debug(session + " Invalid HTTP request form.");
			responseStatusCode = 400;
			return;
		}
		
		if( !(requestParts[0].equalsIgnoreCase("GET") || requestParts[0].equalsIgnoreCase("HEAD")) || !requestParts[2].startsWith("HTTP/") ) {
			Out.debug(session + " HTTP request is not GET or HEAD.");
			responseStatusCode = 405;
			return;
		}

		// The request URI may be an absolute path or an absolute URI for GET/HEAD requests (see section 5.1.2 of RFC2616)
		requestParts[1] = absoluteUriPattern.matcher(requestParts[1]).replaceFirst("/");
		
		if (!requestParts[1].startsWith("/")) {
			Out.debug(session + " HTTP request contained invalid URI");
			responseStatusCode = 400;
			return;
		}

		String[] urlparts = requestParts[1].split("/");

		if (urlparts.length == 0) {
			// Redirect from / to /metrics
			hpc = new HTTPResponseProcessorText("");
			hpc.addHeaderField("Location", "/metrics");
			responseStatusCode = 301; // Moved Permanently
			return;
		}

		// I don't know any url like /metrics/foo of /metrics/bar/baz
		if(urlparts.length > 2) {
			Out.debug(session + " The requested metrics URL not supported.");
			responseStatusCode = 404;
			return;
		}
		
		requestHeadOnly = requestParts[0].equalsIgnoreCase("HEAD");


		

		if(urlparts[1].equals("metrics")) {
			hpc = new HTTPResponseProcessorText(getMetricsString());
			responseStatusCode = 200;
			return;
		}

		Out.debug(session + " Invalid request path " + urlparts[1]);
		responseStatusCode = 404;
		return;
	}

	public HTTPResponseProcessor getHTTPResponseProcessor() {
		if(hpc == null) {
			hpc = new HTTPResponseProcessorText("An error has occurred. (" + responseStatusCode + ")");
			
			if(responseStatusCode == 405) {
				hpc.addHeaderField("Allow", "GET,HEAD");
			}
		}

		return hpc;
	}

	private <T extends Number> void appendMetric(StringBuilder sb, String help, String type, String name, T value, Map<String, String> labels) {
		// help
		sb.append("# HELP ").append(name).append(" ").append(help).append(LF);
		sb.append("# TYPE ").append(name).append(" ").append(type).append(LF);

		// name
		sb.append(name);

		//labels
		if (labels != null && labels.size() > 0) {
			sb.append("{");
			boolean first = true;
			for (Map.Entry<String, String> entry : labels.entrySet()) {
				if (!first)
					sb.append(",");
				first = false;

				sb.append(entry.getKey() + "=\"" + entry.getValue() + "\"");
			}
			sb.append("}");
		}
		sb.append(" ");

		//value
		sb.append(value).append(LF);
	}

	private String getMetricsString() {
		StringBuilder builder = new StringBuilder();
		Map<String, String> defaultLabels = Map.ofEntries(
				Map.entry("user", Settings.getMetricsUserId()),
				Map.entry("name", Settings.getMetricsClientName()),
				Map.entry("host", Settings.getClientHost())
		);

		builder.append("# These are metrics of the Hentai@Home client.").append(LF);
		builder.append("# These metrics are written in the prometheus exposition format").append(LF);
		builder.append("# to be easily read by human and computer.").append(LF);
		builder.append("\n");

		appendMetric(builder,
				"Client Status", "gauge",
				"eh_hath_client_status",
				Stats.isClientSuspended() ? 10 : Stats.isClientRunning() ? 20 : 0, defaultLabels);

		appendMetric(builder,
				"Client uptime in seconds", "counter",
				"eh_hath_client_uptime_seconds",
				Stats.getUptimeDouble(), defaultLabels);

		appendMetric(builder,
				"Amount of files sent since last restart", "counter",
				"eh_hath_transfer_tx_files_count",
				Stats.getFilesSent(), defaultLabels);

		appendMetric(builder,
				"Amount of files received since last restart", "counter",
				"eh_hath_transfer_rx_files_count",
				Stats.getFilesRcvd(), defaultLabels);

		appendMetric(builder,
				"Bytes sent since last restart", "counter",
				"eh_hath_transfer_tx_bytes",
				Stats.getBytesSent(), defaultLabels);

		appendMetric(builder,
				"Bytes received since last restart", "counter",
				"eh_hath_transfer_rx_bytes",
				Stats.getBytesRcvd(), defaultLabels);

		appendMetric(builder,
				"Reserved maximal cache size", "gauge",
				"eh_hath_cache_size_limit_bytes",
				Settings.getDiskLimitBytes(), defaultLabels);

		appendMetric(builder,
				"Cache size", "gauge",
				"eh_hath_cache_size_bytes",
				Stats.getCacheSize(), defaultLabels);

		appendMetric(builder,
				"Free space reserved for H@H cache", "gauge",
				"eh_hath_cache_size_free_bytes",
				Stats.getCacheFree(), defaultLabels);

		appendMetric(builder,
				"How much of the reserved cache is already in use", "gauge",
				"eh_hath_cache_size_used_ratio",
				Stats.getCacheFill(), defaultLabels);

		appendMetric(builder,
				"Count of files currently in client cache", "counter",
				"eh_hath_cache_file_count",
				Stats.getCacheCount(), defaultLabels);

		appendMetric(builder,
				"Currently open connections to the client", "gauge",
				"eh_hath_connections_open_count",
				Stats.getOpenConnections(), defaultLabels);

		appendMetric(builder,
				"Max connections", "gauge",
				"eh_hath_connections_max_count",
				Settings.getMaxConnections(), defaultLabels);

		appendMetric(builder,
				"Epoch timestamp of last server contact", "counter",
				"eh_hath_last_server_contact_epoch",
				Stats.getLastServerContact(), defaultLabels);

		return builder.toString();
	}
	
	public void requestCompleted() {
		hpc.requestCompleted();
	}

	// accessors

	public int getResponseStatusCode() {
		return responseStatusCode;
	}

	public boolean isRequestHeadOnly() {
		return requestHeadOnly;
	}
}
