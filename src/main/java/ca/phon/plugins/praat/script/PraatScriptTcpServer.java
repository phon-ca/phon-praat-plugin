/*
 * phon-textgrid-plugin
 * Copyright (C) 2015, Gregory Hedlund <ghedlund@mun.ca>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package ca.phon.plugins.praat.script;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import ca.phon.util.PrefHelper;
import ca.phon.worker.PhonTask;
import ca.phon.worker.PhonWorker;

/**
 * 
 */
public class PraatScriptTcpServer {

	public final static String TIMEOUT = PraatScriptTcpServer.class.getName() + ".timeout";
	
	/*
	 * Default timeout is 5 seconds 
	 */
	private final static Integer DEFAULT_TIMEOUT = 0;
	
	private final static Logger LOGGER = Logger
			.getLogger(PraatScriptTcpServer.class.getName());
	
	private ServerSocket serverSock;
	
	private final AtomicReference<PraatScriptTcpHandler> handlerRef = new AtomicReference<PraatScriptTcpHandler>();
	
	public PraatScriptTcpServer() {
		super();
		// create sock, allocating the port
		try {
			serverSock = new ServerSocket(0);
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}
	
	public Integer getTimeout() {
		return PrefHelper.getInt(TIMEOUT, DEFAULT_TIMEOUT);
	}
	
	public Integer getPort() {
		return serverSock.getLocalPort();
	}
	
	public PraatScriptTcpHandler getHandler() {
		return handlerRef.get();
	}
	
	public void setHandler(PraatScriptTcpHandler handler) {
		handlerRef.getAndSet(handler);
	}
	
	/**
	 * Starts server on default background thread.
	 * 
	 * 
	 */
	public void startServer() {
		final PhonWorker worker = PhonWorker.createWorker();
		startServer(worker);
		worker.start();
	}
	
	/**
	 * Start server on given PhonWorker thread.
	 * 
	 * @param th
	 */
	public void startServer(PhonWorker th) {
		th.invokeLater(server);
	}
	
	private final PhonTask server = new PhonTask() {
		
		@Override
		public void performTask() {
			super.setStatus(TaskStatus.RUNNING);
			// start listening on socket, with timeout
			try {
				LOGGER.log(Level.INFO, "Setting timeout to " + getTimeout());
				serverSock.setSoTimeout(getTimeout());
				LOGGER.log(Level.INFO, "Setting up Praat listener on port:" + serverSock.getLocalPort());
				final Socket sock = serverSock.accept();
				final BufferedReader reader = new BufferedReader(
						new InputStreamReader(sock.getInputStream(), "UTF-8"));
				final StringBuilder sb = new StringBuilder();
				String line = null;
				while((line = reader.readLine()) != null) {
					sb.append(line + "\n");
				}
				reader.close();
				sock.close();
				
				// call action when finished
				final PraatScriptTcpHandler handler = getHandler();
				if(handler != null) {
					handler.praatScriptFinished(sb.toString());
				}
				
				setStatus(TaskStatus.FINISHED);
			} catch (IOException e) {
				super.err = e;
				setStatus(TaskStatus.ERROR);
				LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
			} finally {
				if(!serverSock.isClosed()) {
					try {
						serverSock.close();
					} catch (IOException e) {
						LOGGER.log(Level.SEVERE, e.getLocalizedMessage(),
								e);
					}
				}
			}
		}
		
	};

	public void stop() {
		try {
			serverSock.close();
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
		server.shutdown();
	}

}
