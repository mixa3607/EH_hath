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

import java.time.Instant;
import java.util.regex.Pattern;

public class HTTPMetricsResponse {
	private static final Pattern absoluteUriPattern = Pattern.compile("^http://[^/]+/", Pattern.CASE_INSENSITIVE);

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

	private String getMetricsString() {
		String nowEpochMilliseconds = String.valueOf(Instant.now().getEpochSecond() * 1000);
		StringBuilder builder = new StringBuilder();
		builder.append("# These are metrics of the Hentai@Home client.").append("\n");
		builder.append("# These metrics are written in the prometheus exposition format").append("\n");
		builder.append("# to be easily read by human and computer.").append("\n");
		builder.append("\n");

		builder.append("# HELP hath_client_status Client Status").append("\n");
		builder.append("# TYPE hath_client_status untyped").append("\n");
		builder.append("hath_client_status{status=\"running\"} ").append(String.valueOf(Stats.isClientRunning()? 1 : 0)).append(" ").append(nowEpochMilliseconds).append("\n");
		builder.append("hath_client_status{status=\"suspended\"} ").append(String.valueOf(Stats.isClientSuspended()? 1 : 0)).append(" ").append(nowEpochMilliseconds).append("\n");
		builder.append("\n");

		builder.append("# HELP hath_client_uptime_seconds Client uptime in seconds").append("\n");
		builder.append("# TYPE hath_client_uptime_seconds untyped").append("\n");
		builder.append("hath_client_uptime_seconds ").append(String.valueOf(Stats.getUptimeDouble())).append(" ").append(nowEpochMilliseconds).append("\n");
		builder.append("\n");

		builder.append("# HELP hath_transfer_tx_files_count Amount of files sent since last restart").append("\n");
		builder.append("# TYPE hath_transfer_tx_files_count untyped").append("\n");
		builder.append("hath_transfer_tx_files_count ").append(String.valueOf(Stats.getFilesSent())).append(" ").append(nowEpochMilliseconds).append("\n");
		builder.append("\n");

		builder.append("# HELP hath_transfer_rx_files_count Amount of files received since last restart").append("\n");
		builder.append("# TYPE hath_transfer_rx_files_count untyped").append("\n");
		builder.append("hath_transfer_rx_files_count ").append(String.valueOf(Stats.getFilesRcvd())).append(" ").append(nowEpochMilliseconds).append("\n");
		builder.append("\n");

		builder.append("# HELP hath_transfer_tx_bytes Bytes sent since last restart").append("\n");
		builder.append("# TYPE hath_transfer_tx_bytes untyped").append("\n");
		builder.append("hath_transfer_tx_bytes ").append(String.valueOf(Stats.getBytesSent())).append(" ").append(nowEpochMilliseconds).append("\n");
		builder.append("\n");

		builder.append("# HELP hath_transfer_rx_bytes Bytes received since last restart").append("\n");
		builder.append("# TYPE hath_transfer_rx_bytes untyped").append("\n");
		builder.append("hath_transfer_rx_bytes ").append(String.valueOf(Stats.getBytesRcvd())).append(" ").append(nowEpochMilliseconds).append("\n");
		builder.append("\n");

		builder.append("# HELP hath_cache_size_limit_bytes Reserved maximal cache size").append("\n");
		builder.append("# TYPE hath_cache_size_limit_bytes untyped").append("\n");
		builder.append("hath_cache_size_limit_bytes ").append(String.valueOf(Settings.getDiskLimitBytes())).append(" ").append(nowEpochMilliseconds).append("\n");
		builder.append("\n");

		builder.append("# HELP hath_cache_size_bytes").append("\n");
		builder.append("# TYPE hath_cache_size_bytes untyped").append("\n");
		builder.append("hath_cache_size_used_bytes ").append(String.valueOf(Stats.getCacheSize())).append(" ").append(nowEpochMilliseconds).append("\n");
		builder.append("\n");

		builder.append("# HELP hath_cache_size_free_bytes Free space reserved for H@H cache").append("\n");
		builder.append("# TYPE hath_cache_size_free_bytes untyped").append("\n");
		builder.append("hath_cache_size_free_bytes ").append(String.valueOf(Stats.getCacheFree())).append(" ").append(nowEpochMilliseconds).append("\n");
		builder.append("\n");

		builder.append("# HELP hath_cache_size_used_ratio How much of the reserverd cache is already in use").append("\n");
		builder.append("# TYPE hath_cache_size_used_ratio untyped").append("\n");
		builder.append("hath_cache_size_used_ratio ").append(String.valueOf(Stats.getCacheFill())).append(" ").append(nowEpochMilliseconds).append("\n");
		builder.append("\n");

		builder.append("# HELP hath_cache_file_count Count of files currently in client cache").append("\n");
		builder.append("# TYPE hath_cache_file_count untyped").append("\n");
		builder.append("hath_cache_file_count ").append(String.valueOf(Stats.getCacheCount())).append(" ").append(nowEpochMilliseconds).append("\n");
		builder.append("\n");

		builder.append("# HELP hath_connections_open_count Currently open connections to the client").append("\n");
		builder.append("# TYPE hath_connections_open_count gauge").append("\n");
		builder.append("hath_connections_open_count ").append(String.valueOf(Stats.getOpenConnections())).append(" ").append(nowEpochMilliseconds).append("\n");
		builder.append("\n");

		builder.append("# HELP hath_last_server_contact_epoch Epoch timestamp of last server contact").append("\n");
		builder.append("# TYPE hath_last_server_contact_epoch untyped").append("\n");
		builder.append("hath_last_server_contact_epoch ").append(String.valueOf(Stats.getLastServerContact())).append(" ").append(nowEpochMilliseconds).append("\n");
		
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
