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

import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.lang.Thread;
import javax.net.ServerSocketFactory;

public class HTTPMetricsServer implements Runnable {
	private HentaiAtHomeClient client;
	private ServerSocket listener = null;
	private Thread myThread = null;
	private int currentConnId = 0;
	private boolean allowNormalConnections = false, isRestarting = false, isTerminated = false;

	public HTTPMetricsServer(HentaiAtHomeClient client) {
		this.client = client;
	}

	public boolean startConnectionListener(int port, InetAddress address) {
		try {
			Out.info("Starting up the internal metrics exporter Server...");

			// Set defaults
			if (port == 0) port = 9950; // One of the free ports in the promethues port allocation list(14.02.2022 17:00 UTC)
			if (address == null) address = InetAddress.getByName("127.0.0.1");

			listener = ServerSocketFactory
					.getDefault()
					.createServerSocket(port, -1, address);
			
			myThread = new Thread(this);
			myThread.start();

			Out.info("Internal metrics exporter Server was successfully started, and is listening on port " + port);

			return true;
		}
		catch(Exception e) {
			allowNormalConnections();

			e.printStackTrace();
			Out.info("");
			Out.info("************************************************************************************************************************************");
			Out.info("Could not start the internal metrics exporter HTTP server.");
			Out.info("This is most likely caused by something else running on port " + port + ", which H@H is trying to use.");
			Out.info("In order to fix this, either shut down whatever else is using the port, or assign a different metrics port to H@H.");

			if(port < 1024) {
				Out.info("It could also be caused by trying to use port " + port + " on a system that disallows non-root users from binding to low ports.");
				Out.info("For information on how to work around this, read this post: https://forums.e-hentai.org/index.php?showtopic=232693");
			}

			Out.info("************************************************************************************************************************************");
			Out.info("");
		}

		return false;
	}

	public void stopConnectionListener(boolean restart) {
		isRestarting = restart;
		
		if(listener != null) {
			try {
				listener.close();	// will cause listener.accept() to throw an exception, terminating the accept thread
			} catch(Exception e) {}

			listener = null;
		}
	}

	public void allowNormalConnections() {
		allowNormalConnections = true;
	}

	public void run() {
		try {
			while(true) {
				Socket socket = (Socket) listener.accept();
				boolean forceClose = false;
				InetAddress addr = socket.getInetAddress();
				String hostAddress = addr.getHostAddress().toLowerCase();

				if(!allowNormalConnections) {
					Out.warning("Rejecting connection request from " + hostAddress + " during startup.");
					forceClose = true;
				}

				if(forceClose) {
					try {
						socket.close();
					} catch(Exception e) {}
				} else {
					// all is well. keep truckin'
					new HTTPMetricsSession(socket, getNewConnId()).handleSession();
				}
			}
		}
		catch(java.io.IOException e) {
			if(!isRestarting && !client.isShuttingDown()) {
				Out.error("ServerSocket terminated unexpectedly!");
				HentaiAtHomeClient.dieWithError(e);
			}
			else {
				Out.info("ServerSocket was closed and will no longer accept new connections.");
			}

			listener = null;
		}
		
		isTerminated = true;
	}
	
	public boolean isThreadTerminated() {
		return isTerminated;
	}

	private synchronized int getNewConnId() {
		return ++currentConnId;
	}
}
