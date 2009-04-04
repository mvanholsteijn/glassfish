/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
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
 */

package com.sun.enterprise.connectors.work.context;

import com.sun.enterprise.security.auth.login.DistinguishedPrincipalCredential;
import com.sun.enterprise.security.SecurityContext;
import org.glassfish.security.common.Group;
import org.glassfish.security.common.PrincipalImpl;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.message.callback.CallerPrincipalCallback;
import javax.security.auth.message.callback.GroupPrincipalCallback;
import javax.security.auth.message.callback.PasswordValidationCallback;
import javax.security.auth.Subject;
import java.io.IOException;
import java.util.*;
import java.security.Principal;

/**
 * Connector callback handler to intercept the callbacks provided by the work instance
 * in order to map the security credentials between container and EIS domain
 *
 * @author Jagadish Ramu
 * @since GlassFish v3
 */
//TODO V3 need contract based handlers for individual callbacks ?
public class ConnectorCallbackHandler implements CallbackHandler {

    public static final List<String> supportedCallbacks = new ArrayList<String>();

    static {
        supportedCallbacks.add(GroupPrincipalCallback.class.getName());
        supportedCallbacks.add(CallerPrincipalCallback.class.getName());
    }

    private CallbackHandler handler;
    private boolean needMapping;
    private Map securityMap;
    private Subject executionSubject;

    public ConnectorCallbackHandler(Subject executionSubject, CallbackHandler handler, Map securityMap) {
        this.handler = handler;
        if (securityMap != null) {
            needMapping = true;
            debug("translation required");
        } else {
            debug("no translation required");
        }
        this.executionSubject = executionSubject;
        this.securityMap = securityMap;
    }

    public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {

        Callback[] mappedCallbacks = callbacks;
        if (callbacks != null) {
            List<Callback> asCallbacks = new ArrayList<Callback>();

            boolean hasCallerPrincipalCallback = hasCallerPrincipalCallback(callbacks);

            if (needMapping) {
                for (Callback callback : callbacks) {
                    boolean callbackSupported = false;
                    for (String supportedCallback : supportedCallbacks) {
                        try {
                            //TODO V3 what if there is a callback impl that implements multiple callbacks ?
                            if (Class.forName(supportedCallback).isAssignableFrom(callback.getClass())) {
                                callbackSupported = true;
                                asCallbacks.add(handleSupportedCallback(callback));
                            }
                        } catch (ClassNotFoundException cnfe) {
                            cnfe.printStackTrace();
                        }
                    }
                    if (!callbackSupported) {
                        //TODO log ?
                        String message = "Unsupported callback: " + callback.getClass().getName() +
                                " during credential mapping";
                        debug(message);
                        throw new UnsupportedCallbackException(callback);
                    }
                }

                mappedCallbacks = new Callback[asCallbacks.size()];
                for (int i = 0; i < asCallbacks.size(); i++) {
                    mappedCallbacks[i] = asCallbacks.get(i);
                }
            }
            //TODO V3 what happens to multiple callbacks ?
            handler.handle(mappedCallbacks);

            processResults(mappedCallbacks, hasCallerPrincipalCallback);
        }
    }

    private boolean hasCallerPrincipalCallback(Callback[] callbacks) {
        if (callbacks != null) {
            for (Callback c : callbacks) {
                if (c instanceof CallerPrincipalCallback) {
                    return true;
                }
            }
        }
        return false;
    }

    private void processResults(Callback[] mappedCallbacks, boolean hasCallerPrincipalCallback) {
        if (mappedCallbacks != null) {
            Subject s = new Subject();

            // Handle Single Principal as the caller identity
            if (!hasCallerPrincipalCallback) {
                Set<Principal> principals = executionSubject.getPrincipals();
                if (principals != null && principals.size() == 1) {
                    //process if there is only one principal
                    for (Principal p : principals) {
                        Principal mappedPrincipal = null;
                        if (needMapping) {
                            mappedPrincipal = getMappedPrincipal(p, null);
                        } else {
                            mappedPrincipal = p;
                        }

                        if (mappedPrincipal != null) {
                            s.getPrincipals().add(mappedPrincipal);
                        }
                    }
                    s.getPublicCredentials().addAll(executionSubject.getPublicCredentials());
                    s.getPrivateCredentials().addAll(executionSubject.getPrivateCredentials());
                }
            }

            //TODO V3 what happens for Public/Private Credentials of Mapped case (Case II) 
            for (Callback callback : mappedCallbacks) {
                if (callback instanceof CallerPrincipalCallback) {
                    CallerPrincipalCallback cpc = (CallerPrincipalCallback) callback;
                    s.getPrincipals().addAll(cpc.getSubject().getPrincipals());
                    s.getPublicCredentials().addAll(cpc.getSubject().getPublicCredentials());
                    s.getPrivateCredentials().addAll(cpc.getSubject().getPrivateCredentials());
                } else if (callback instanceof GroupPrincipalCallback) {
                    GroupPrincipalCallback gpc = (GroupPrincipalCallback) callback;
                    s.getPrincipals().addAll(gpc.getSubject().getPrincipals());
                    s.getPublicCredentials().addAll(gpc.getSubject().getPublicCredentials());
                    s.getPrivateCredentials().addAll(gpc.getSubject().getPrivateCredentials());
                } else if (callback instanceof PasswordValidationCallback) {
                    PasswordValidationCallback pvc = (PasswordValidationCallback) callback;
                    s.getPrincipals().addAll(pvc.getSubject().getPrincipals());
                    s.getPublicCredentials().addAll(pvc.getSubject().getPublicCredentials());
                    s.getPrivateCredentials().addAll(pvc.getSubject().getPrivateCredentials());
                } else {
                    //log warning, can't happen
                }
            }
            SecurityContext.setCurrent(new SecurityContext(s));
        } else {
            //log warning, can't happen
        }
    }

    private Callback handleSupportedCallback(Callback callback) throws UnsupportedCallbackException {
        /* TODO V3 need to merge the principals/maps after calling all the callbacks and then
           TODO V3 set the security context ? */
        if (callback instanceof CallerPrincipalCallback) {
            return handleCallerPrincipalCallbackWithMapping((CallerPrincipalCallback) callback);
        } else if (callback instanceof GroupPrincipalCallback) {
            return handleGroupPrincipalCallbackWithMapping((GroupPrincipalCallback) callback);
        } else {
            throw new UnsupportedCallbackException(callback);
        }
    }

    private Callback handleGroupPrincipalCallbackWithMapping(GroupPrincipalCallback gpc) {

        String[] groups = gpc.getGroups();
        List<String> asGroupNames = new ArrayList<String>();

        for (String groupName : groups) {
            Group mappedGroup = (Group) securityMap.get(new Group(groupName));
            if (mappedGroup != null) {
                debug("got mapped group as [" + groupName + "] for eis-group [" + mappedGroup.getName() + "]");
                asGroupNames.add(mappedGroup.getName());
            }
        }

        String[] asGroupsString = new String[asGroupNames.size()];
        for (int i = 0; i < asGroupNames.size(); i++) {
            asGroupsString[i] = asGroupNames.get(i);
        }
        return new GroupPrincipalCallback(gpc.getSubject(), asGroupsString);

        //SecurityContext.setCurrent(new SecurityContext(gpc.getSubject()));
    }

    public Callback handleCallerPrincipalCallbackWithMapping(CallerPrincipalCallback cpc) {

        CallerPrincipalCallback asCPC;

        Principal eisPrincipal = cpc.getPrincipal();
        String eisName = cpc.getName();

        Principal asPrincipal = getMappedPrincipal(eisPrincipal, eisName);

        asCPC = new CallerPrincipalCallback(cpc.getSubject(), asPrincipal);

        return asCPC;
/*
        Set<Principal> principals = cpc.getSubject().getPrincipals();
        for (Principal p : principals) {
            Principal mappedPrincipal = (Principal) securityMap.get(p);
            if (mappedPrincipal != null) {
                DistinguishedPrincipalCredential dpc = new DistinguishedPrincipalCredential(mappedPrincipal);
                cpc.getSubject().getPublicCredentials().add(dpc);
            }
        }
        SecurityContext.setCurrent(new SecurityContext(cpc.getSubject()));
*/
    }

    private Principal getMappedPrincipal(Principal eisPrincipal, String eisName) {
        Principal asPrincipal = null;
        if (eisPrincipal != null) {
            asPrincipal = (PrincipalImpl) securityMap.get(eisPrincipal);
            debug("got mapped principal as [" + asPrincipal + "] for eis-group [" + eisPrincipal.getName() + "]");
        } else if (eisName != null) {
            asPrincipal = ((PrincipalImpl) securityMap.get(new PrincipalImpl(eisName)));
            debug("got mapped principal as [" + asPrincipal + "] for eis-group [" + eisName + "]");
        }
        return asPrincipal;
    }

    public void debug(String message) {
        System.out.println("JSR-322 [Connector Container] [ConnectorCallbackHandler]: " + message);
    }
}
