/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 *
 */
/*
 * Copyright 2009 Richard Zschech.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atmosphere.gwt.client;

import org.atmosphere.gwt.client.extra.LoadRegister.BeforeUnloadEvent;
import org.atmosphere.gwt.client.extra.LoadRegister.UnloadEvent;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.atmosphere.gwt.client.impl.CometTransport;

import com.google.gwt.core.client.Duration;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.rpc.AsyncCallback;
import org.atmosphere.gwt.client.impl.WebSocketCometTransport;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.atmosphere.gwt.client.extra.LoadRegister;

/**
 * This class is the Comet client. It will connect to the given url and notify the given {@link CometListener} of comet
 * events. To receive GWT serialized objects supply a {@link CometSerializer} method to parse the messages.
 * 
 * The sequence of events are as follows: The application calls {@link CometClient#start()}.
 * {@link CometListener#onConnected(int)} gets called when the connection is established.
 * {@link CometListener#onMessage(List)} gets called when messages are received from the server.
 * {@link CometListener#onDisconnected()} gets called when the connection is disconnected this includes connection
 * refreshes. {@link CometListener#onError(Throwable, boolean)} gets called if there is an error with the connection.
 * 
 * The Comet client will attempt to maintain to connection when disconnections occur until the application calls
 * {@link CometClient#stop()}.
 * 
 * The server sends heart beat messages to ensure the connection is maintained and that disconnections can be detected
 * in all cases.
 * 
 * @author Richard Zschech
 */
public class AtmosphereClient {
	
	private enum RefreshState {
		CONNECTING, PRIMARY_DISCONNECTED, REFRESH_CONNECTED
	}
	
	private String url;
	private final AtmosphereGWTSerializer serializer;
	private final AtmosphereListener listener;
	private CometClientTransportWrapper primaryTransport;
	private CometClientTransportWrapper refreshTransport;
    private HandlerRegistration unloadHandlerReg;
	
	private boolean running;
	private RefreshState refreshState;
	private List<Object> refreshQueue;
	
	private static final Object REFRESH = new Object();
	private static final Object DISCONNECT = new Object();
	
	private int connectionCount;
	
	private int connectionTimeout = 10000;
	private int reconnectionTimeout = 1000;
    
    private Logger logger = Logger.getLogger(getClass().getName());
    
    private AsyncCallback<Void> postCallback = new AsyncCallback<Void>() {
        @Override
        public void onFailure(Throwable caught) {
            logger.log(Level.SEVERE, "Failed to post message", caught);
        }
        @Override
        public void onSuccess(Void result) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "post succeeded");
            }
        }
    };
	
	public AtmosphereClient(String url, AtmosphereListener listener) {
		this(url, null, listener);
	}
	
	public AtmosphereClient(String url, AtmosphereGWTSerializer serializer, AtmosphereListener listener) {
		this.url = url;
		this.serializer = serializer;
		this.listener = listener;
        
		primaryTransport = new CometClientTransportWrapper();
        
	}
	
	public String getUrl() {
		return url;
	}

    public void setUrl(String url) {
        this.url = url;
    }
    
	public AtmosphereGWTSerializer getSerializer() {
		return serializer;
	}
    
	public AtmosphereListener getListener() {
		return listener;
	}
	
	public void setConnectionTimeout(int connectionTimeout) {
		this.connectionTimeout = connectionTimeout;
	}
	
	public int getConnectionTimeout() {
		return connectionTimeout;
	}
	
	public void setReconnectionTimeout(int reconnectionTimout) {
		this.reconnectionTimeout = reconnectionTimout;
	}
	
	public int getReconnectionTimeout() {
		return reconnectionTimeout;
	}
	
	public boolean isRunning() {
		return running;
	}

    public int getConnectionID() {
        return primaryTransport.connectionID;
    }
	
    // push message back to the server on this connection
    public void post(Serializable message) {
        primaryTransport.post(message, postCallback);
    }
    
    // push message back to the server on this connection
    public void post(Serializable message, AsyncCallback<Void> callback) {
        primaryTransport.post(message, callback);
    }
    
    // push message back to the server on this connection
    public void post(List<Serializable> messages) {
        primaryTransport.post(messages, postCallback);
    }
    
    // push message back to the server on this connection
    public void post(List<Serializable> messages, AsyncCallback<Void> callback) {
        primaryTransport.post(messages, callback);
    }
    
    // push message back to the server on this connection
    public void broadcast(Serializable message) {
        primaryTransport.broadcast(message);
    }
    
    // push message back to the server on this connection
    public void broadcast(List<Serializable> messages) {
        primaryTransport.broadcast(messages);
    }
    
    
	public void start() {
		if (!running) {
			running = true;
            
            if (unloadHandlerReg != null) {
                unloadHandlerReg.removeHandler();
            }
            
            UnloadHandler handler = new UnloadHandler();
            final HandlerRegistration reg1 = LoadRegister.addBeforeUnloadHandler(handler);
            final HandlerRegistration reg2 = LoadRegister.addUnloadHandler(handler);
            unloadHandlerReg = new HandlerRegistration() {
                @Override
                public void removeHandler() {
                    reg1.removeHandler();
                    reg2.removeHandler();
                }
            };
            
			doConnect();
		}
	}

    private class UnloadHandler implements LoadRegister.BeforeUnloadHandler, LoadRegister.UnloadHandler {
        @Override
        public void onBeforeUnload(BeforeUnloadEvent event) {
            doUnload();
        }
        @Override
        public void onUnload(UnloadEvent event) {
            doUnload();
        }
        private void doUnload() {
            if (!done) {
                done = true;
                logger.log(Level.FINE, "Stopping comet client because of page unload");
                stop();
            }
        }
        private boolean done = false;
    }
	
	public void stop() {
		if (running) {
			running = false;
            if (unloadHandlerReg != null) {
                unloadHandlerReg.removeHandler();
                unloadHandlerReg = null;
            }
			doDisconnect();
		}
	}
	
	private void doConnect() {
		primaryTransport.connect();
	}
	
	private void doDisconnect() {
		refreshState = null;
		primaryTransport.disconnect();
		if (refreshTransport != null) {
			refreshTransport.disconnect();
		}
	}
	
	private void doOnConnected(int heartbeat, int connectionID, CometClientTransportWrapper transport) {
		if (refreshState != null) {
			if (transport == refreshTransport) {
				if (refreshState == RefreshState.PRIMARY_DISCONNECTED) {
					doneRefresh();
				}
				else if (refreshState == RefreshState.CONNECTING) {
					primaryTransport.disconnect();
                    doneRefresh();
				}
				else {
					throw new IllegalStateException("Unexpected refresh state");
				}
			}
			else {
				throw new IllegalStateException("Unexpected connection from primairyTransport");
			}
		}
		else {
			listener.onConnected(heartbeat, connectionID);
		}
	}
	
    
	private void doOnBeforeDisconnected(CometClientTransportWrapper transport) {
        if (refreshState == null && transport == primaryTransport) {
            listener.onBeforeDisconnected();
        }
    }
    
	private void doOnDisconnected(CometClientTransportWrapper transport) {
		if (refreshState != null) {
			if (transport == primaryTransport) {
				if (refreshState != RefreshState.CONNECTING) {
					throw new IllegalStateException("Unexpected refreshState");
				}
                refreshState = RefreshState.PRIMARY_DISCONNECTED;
                GWT.log("CometClient: primary disconnected before refresh transport was connected");
			}
			else {
				// the refresh transport has disconnected
                failedRefresh();
			}
		}
		else {
			listener.onDisconnected();
			
			if (running) {
				doConnect();
			}
		}
	}

    private void failedRefresh() {
        refreshState = null;
        GWT.log("CometClient: Failed refesh");
        // dispatch remaining messages;
		if (refreshQueue != null) {
			for (Object object : refreshQueue) {
				if (object == REFRESH || object == DISCONNECT) {
				}
				else {
					doOnMessage((List<? extends Serializable>) object, primaryTransport);
				}
			}
			refreshQueue.clear();
		}
        doDisconnect();
        doConnect();
    }
	
	@SuppressWarnings("unchecked")
	private void doneRefresh() {
		refreshState = null;
		CometClientTransportWrapper temp = primaryTransport;
		primaryTransport = refreshTransport;
		refreshTransport = temp;

		if (refreshQueue != null) {
            if (refreshQueue.size() > 0) {
                GWT.log("CometClient: pushing queued messages");
            }
			for (Object object : refreshQueue) {
				if (object == REFRESH) {
					doOnRefresh(primaryTransport);
				}
				else if (object == DISCONNECT) {
					doOnDisconnected(primaryTransport);
				}
				else {
					doOnMessage((List<? extends Serializable>) object, primaryTransport);
				}
			}
			refreshQueue.clear();
		}
	}
	
	private void doOnHeartbeat(CometClientTransportWrapper transport) {
		if (transport == primaryTransport) {
			listener.onHeartbeat();
		}
	}
	
	private void doOnRefresh(CometClientTransportWrapper transport) {
		if (refreshState == null && transport == primaryTransport) {
			refreshState = RefreshState.CONNECTING;
			
			if (refreshTransport == null) {
				refreshTransport = new CometClientTransportWrapper();
			}
			refreshTransport.connect();
			
			listener.onRefresh();
		}
		else if (transport == refreshTransport) {
			refreshEnqueue(REFRESH);
		}
		else {
			throw new IllegalStateException("Unexpected refresh from primaryTransport");
		}
	}
	
	private void refreshEnqueue(Object message) {
		if (refreshQueue == null) {
			refreshQueue = new ArrayList<Object>();
		}
		refreshQueue.add(message);
	}
	
	private void doOnError(Throwable exception, boolean connected, CometClientTransportWrapper transport) {
		if (connected) {
			doDisconnect();
		}
		
		listener.onError(exception, connected);
		
		if (running) {
			primaryTransport.reconnectionTimer.schedule(reconnectionTimeout);
		}
	}
	
	private void doOnMessage(List<? extends Serializable> messages, CometClientTransportWrapper transport) {
		if (transport == primaryTransport) {
			listener.onMessage(messages);
		}
		else if (RefreshState.PRIMARY_DISCONNECTED.equals(refreshState)) {
			refreshEnqueue(messages);
		}
	}
	
	private class CometClientTransportWrapper implements AtmosphereListener {
		
		private CometTransport transport;

		private final Timer connectionTimer = createConnectionTimer();
		private final Timer reconnectionTimer = createReconnectionTimer();
		private final Timer heartbeatTimer = createHeartbeatTimer();
		
		private boolean webSocketSuccessful = false;
		private int heartbeatTimeout;
		private double lastReceivedTime;
        private int connectionID;
		
		public CometClientTransportWrapper() {
            // Websocket support not enabled yet
//            if (WebSocketTransport.hasWebSocketSupport()) {
//                transport = new WebSocketTransport();
//            } else {
                transport = GWT.create(CometTransport.class);
                GWT.log("Created transport: " + transport.getClass().getName()); 
//            }
			transport.initiate(AtmosphereClient.this, this);
		}

        public void post(Serializable message, AsyncCallback<Void> callback) {
            transport.post(message, callback);
        }
        public void post(List<Serializable> messages, AsyncCallback<Void> callback) {
            transport.post(messages, callback);
        }
        public void broadcast(Serializable message) {
            transport.broadcast(message);
        }
        public void broadcast(List<Serializable> messages) {
            transport.broadcast(messages);
        }
        public int getConnectionID() {
            return connectionID;
        }
		
		public void connect() {
			connectionTimer.schedule(connectionTimeout);
			transport.connect(++connectionCount);
		}

		public void disconnect() {
			cancelTimers();
			transport.disconnect();
		}
		
		@Override
		public void onConnected(int heartbeat, int connectionID) {
			heartbeatTimeout = heartbeat + connectionTimeout;
			lastReceivedTime = Duration.currentTimeMillis();
            this.connectionID = connectionID;
            if (transport instanceof WebSocketCometTransport) {
                webSocketSuccessful = true;
            }
			
			cancelTimers();
			heartbeatTimer.schedule(heartbeatTimeout);
			
			doOnConnected(heartbeat, connectionID, this);
		}
		
        @Override
        public void onBeforeDisconnected() {
            doOnBeforeDisconnected(this);
        }
		
		@Override
		public void onDisconnected() {
			cancelTimers();
            if (transport instanceof WebSocketCometTransport && webSocketSuccessful == false) {
                // server doesn't support WebSocket's degrade the connection ...
                transport = GWT.create(CometTransport.class);
                transport.initiate(AtmosphereClient.this, this);
            }
			doOnDisconnected(this);
		}
		
		@Override
		public void onError(Throwable exception, boolean connected) {
			cancelTimers();
			doOnError(exception, connected, this);
		}
		
		@Override
		public void onHeartbeat() {
			lastReceivedTime = Duration.currentTimeMillis();
			doOnHeartbeat(this);
		}
		
		@Override
		public void onRefresh() {
			lastReceivedTime = Duration.currentTimeMillis();
			doOnRefresh(this);
		}
		
		@Override
		public void onMessage(List<? extends Serializable> messages) {
			lastReceivedTime = Duration.currentTimeMillis();
			doOnMessage(messages, this);
		}
		
		private void cancelTimers() {
			connectionTimer.cancel();
			reconnectionTimer.cancel();
			heartbeatTimer.cancel();
		}
		
		private Timer createConnectionTimer() {
			return new Timer() {
				@Override
				public void run() {
					doDisconnect();
					doOnError(new TimeoutException(url, connectionTimeout), false, CometClientTransportWrapper.this);
				}
			};
		}
		
		private Timer createHeartbeatTimer() {
			return new Timer() {
				@Override
				public void run() {
					double currentTimeMillis = Duration.currentTimeMillis();
					double difference = currentTimeMillis - lastReceivedTime;
					if (difference >= heartbeatTimeout) {
						doDisconnect();
						doOnError(new AtmosphereClientException("Heartbeat failed"), false, CometClientTransportWrapper.this);
					}
					else {
						// we have received a message since the timer was
						// schedule so reschedule it.
						schedule(heartbeatTimeout - (int) difference);
					}
				}
			};
		}
		
		private Timer createReconnectionTimer() {
			return new Timer() {
				@Override
				public void run() {
					if (running) {
						doConnect();
					}
				}
			};
		}
		
	}
	
	// TODO precompile all regexps
	public native static JsArrayString split(String string, String separator) /*-{
		return string.split(separator);
	}-*/;
	
	/*
	 * @Override public void start() { if (!closing) { Window.addWindowCloseListener(windowListener); super.start(); } }
	 * 
	 * @Override public void stop() { super.stop(); Window.removeWindowCloseListener(windowListener); }
	 * 
	 * private WindowCloseListener windowListener = new WindowCloseListener() {
	 * 
	 * @Override public void onWindowClosed() { closing = true; }
	 * 
	 * @Override public String onWindowClosing() { return null; } };
	 */
}
