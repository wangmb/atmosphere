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

package org.atmosphere.gwt.client.extra;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.GwtEvent.Type;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.event.shared.SimpleEventBus;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.Window.ClosingEvent;

/**
 *
 * @author p.havelaar
 */
public class LoadRegister {
    
    public static class BeforeUnloadEvent extends GwtEvent<BeforeUnloadHandler> {
        private static Type TYPE;
        public static Type<BeforeUnloadHandler> getType() {
            return TYPE != null ? TYPE : (TYPE = new Type());
        }
        @Override
        public Type<BeforeUnloadHandler> getAssociatedType() {
            return getType();
        }
        @Override
        protected void dispatch(BeforeUnloadHandler handler) {
            handler.onBeforeUnload(this);
        }
        protected BeforeUnloadEvent() {
        }
        
    }
    protected static final BeforeUnloadEvent beforeUnloadEvent = new BeforeUnloadEvent();
    
    public static class UnloadEvent extends GwtEvent<UnloadHandler> {
        private static Type TYPE;
        public static Type<UnloadHandler> getType() {
            return TYPE != null ? TYPE : (TYPE = new Type());
        }
        @Override
        public Type<UnloadHandler> getAssociatedType() {
            return getType();
        }
        @Override
        protected void dispatch(UnloadHandler handler) {
            handler.onUnload(this);
        }
        protected UnloadEvent() {
        }
    }
    protected static final UnloadEvent unloadEvent = new UnloadEvent();
    
    public interface BeforeUnloadHandler extends EventHandler {
        public void onBeforeUnload(BeforeUnloadEvent event);
    }
    public interface UnloadHandler extends EventHandler {
        public void onUnload(UnloadEvent event);
    }
    public static HandlerRegistration addBeforeUnloadHandler(BeforeUnloadHandler handler) {
        maybeInit();
        return eventBus.addHandler(BeforeUnloadEvent.getType(), handler);
    }
    
    public static HandlerRegistration addUnloadHandler(UnloadHandler handler) {
        maybeInit();
        return eventBus.addHandler(UnloadEvent.getType(), handler);
    }
    
    private static void maybeInit() {
        if (!initialized) {
            if (isFirefox()) {
                initWindowHandlers();
            } else {
                initRootHandlers(Document.get().getBody());
            }
            initialized = true;
        }
    }

    static String onBeforeUnload() {
        eventBus.fireEvent(beforeUnloadEvent);
        return null;
    }
    static void onUnload() {
        eventBus.fireEvent(unloadEvent);
    }

    private static boolean isFirefox() {
        String ua = userAgent();
        return ua.indexOf("gecko") != -1 && ua.indexOf("webkit") == -1;
    };

    private static native String userAgent() /*-{
        return $wnd.navigator.userAgent.toLowerCase();
    }-*/;

    private static native Element getWindow() /*-{
        return $wnd;
    }-*/;

    private static void initWindowHandlers() {
        Window.addWindowClosingHandler(new Window.ClosingHandler() {
            @Override
            public void onWindowClosing(ClosingEvent event) {
                onBeforeUnload();
            }
        });
        Window.addCloseHandler(new CloseHandler<Window>() {
            @Override
            public void onClose(CloseEvent<Window> event) {
                onUnload();
            }
        });
    }

    private static native void initRootHandlers(Element element) /*-{
        var ref = element;
        var oldBeforeUnload = ref.onbeforeunload;
        var oldUnload = ref.onunload;

        ref.onbeforeunload = function(evt) {
            var ret,oldRet;
            try {
                ret = $entry(@org.atmosphere.gwt.client.extra.LoadRegister::onBeforeUnload())();
            } finally {
                oldRet = oldBeforeUnload && oldBeforeUnload(evt);
            }
            // Avoid returning null as IE6 will coerce it into a string.
            // Ensure that "" gets returned properly.
            if (ret != null) {
              return ret;
            }
            if (oldRet != null) {
              return oldRet;
            }
            // returns undefined.
        };

        ref.onunload = $entry(function(evt) {
            try {
               @org.atmosphere.gwt.client.extra.LoadRegister::onUnload()();
            } finally {
               oldUnload && oldUnload(evt);
               ref.onbeforeunload = null;
               ref.onunload = null;
            }
        });
    }-*/;
    
    private static SimpleEventBus eventBus = new SimpleEventBus();
    private static boolean initialized = false;
}
