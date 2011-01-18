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
package org.atmosphere.gwt.client.impl;

import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import org.atmosphere.gwt.client.AtmosphereClient;
import org.atmosphere.gwt.client.AtmosphereListener;
import org.atmosphere.gwt.client.SerialMode;

import com.google.gwt.core.client.Duration;
import com.google.gwt.core.client.GWT;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.rpc.SerializationException;
import com.google.gwt.user.client.rpc.StatusCodeException;
import org.atmosphere.gwt.client.AtmosphereGWTSerializer;
import java.io.Serializable;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.atmosphere.gwt.shared.Constants;

/**
 * This is the base class for the comet implementations
 * 
 * @author Richard Zschech
 */
public abstract class BaseCometTransport implements CometTransport {
	
	protected AtmosphereClient client;
	protected AtmosphereListener listener;
    protected ServerTransport serverTransport;
    protected int connectionId;
    protected final Logger logger = Logger.getLogger(getClass().getName());

    @Override
    public void disconnect() {
        listener.onBeforeDisconnected();
        getServerTransport().disconnect();
    }

    @Override
    public void post(Serializable message, AsyncCallback<Void> callback) {
        getServerTransport().post(message, callback);
    }

    @Override
    public void post(List<Serializable> messages, AsyncCallback<Void> callback) {
        getServerTransport().post(messages, callback);
    }

    @Override
    public void broadcast(Serializable message) {
        getServerTransport().broadcast(message);
    }

    @Override
    public void broadcast(List<Serializable> messages) {
        getServerTransport().broadcast(messages);
    }
    
    @Override
	public void initiate(AtmosphereClient client, AtmosphereListener listener) {
		this.client = client;
		this.listener = listener;
	}
    
    protected ServerTransport getServerTransport() {
        if (serverTransport == null) {
            serverTransport = new RPCServerTransport();
        }
        return serverTransport;
    }
    
    protected class RPCServerTransport extends ServerTransportProtocol {

        @Override
        void send(String message, final AsyncCallback<Void> callback) {
            RequestBuilder request = new RequestBuilder(RequestBuilder.POST, serviceUrl());
            try {
                request.sendRequest(message, new RequestCallback() {
                    @Override
                    public void onResponseReceived(Request request, Response response) {
                        // when a connection is abruptly closed (for instance when a user presses F5
                        // the statuscode seems to be 0, the call could have arrived at the server though
                        if (response.getStatusCode() != Response.SC_OK
                            && response.getStatusCode() != 0) {
                            logger.log(Level.SEVERE, "Failed to send server message: [" + response.getStatusText() + "," + response.getStatusCode() + "]");
                            callback.onFailure(new StatusCodeException(response.getStatusCode(), response.getStatusText()));
                        } else {
                            callback.onSuccess(null);
                        }
                    }

                    @Override
                    public void onError(Request request, Throwable exception) {
                        callback.onFailure(exception);
                    }
                });
            } catch (RequestException ex) {
                callback.onFailure(ex);
            }
        }

        @Override
        public String serialize(Serializable message) throws SerializationException {
            return client.getSerializer().serialize(message);
        }

        protected String serviceUrl() {
            int i = client.getUrl().indexOf('?');
            String serviceUrl = (i > 0 ? client.getUrl().substring(0, i) : client.getUrl())
                    + "?servertransport=rpcprotocol&connectionID=" + connectionId;
            return serviceUrl;
        }

    }
    
    protected Serializable parse(String message) throws SerializationException {
        if (message == null || message.isEmpty()) {
            return null;
        }
        AtmosphereGWTSerializer serializer = client.getSerializer();
        if (serializer == null) {
            throw new SerializationException("Can not deserialize message with no serializer: " + message);
        }
        else {
            return serializer.parse(message);
        }
    }
	
	
	public String getUrl(int connectionCount) {
		String url = client.getUrl();
		if (client.getSerializer() != null && client.getSerializer().getMode() == SerialMode.DE_RPC) {
			url += (url.contains("?") ? "&" : "?") + Constants.MODULE_BASE_PARAMETER + '=' + GWT.getModuleBaseURL() + '&' + Constants.STRONG_NAME_PARAMETER + '=' + GWT.getPermutationStrongName();
		}
        String className = getClass().getName();
        className = className.substring(className.lastIndexOf('.')+1);
        String transport = className.substring(0, className.indexOf("CometTransport"));
		return url + (url.contains("?") ? "&" : "?") 
                + "t=" + Integer.toString((int) (Duration.currentTimeMillis() % Integer.MAX_VALUE), Character.MAX_RADIX).toUpperCase() 
                + "&c=" + connectionCount
                + "&tr=" + transport;
	}
    
}
