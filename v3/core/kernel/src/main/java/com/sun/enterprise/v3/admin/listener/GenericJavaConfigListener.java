/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2011 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
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

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.enterprise.v3.admin.listener;

import com.sun.enterprise.config.serverbeans.Cluster;
import com.sun.enterprise.config.serverbeans.Config;
import com.sun.enterprise.config.serverbeans.Domain;
import com.sun.enterprise.config.serverbeans.JavaConfig;
import com.sun.enterprise.config.serverbeans.Server;
import com.sun.enterprise.config.serverbeans.Profiler;
import com.sun.enterprise.config.serverbeans.SystemProperty;
import com.sun.logging.LogDomains;
import org.jvnet.hk2.config.types.Property;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.api.Startup;
import org.glassfish.api.admin.ServerEnvironment;

import org.jvnet.hk2.annotations.*;
import org.jvnet.hk2.component.PostConstruct;
import org.jvnet.hk2.config.Changed;
import org.jvnet.hk2.config.Changed.TYPE;
import org.jvnet.hk2.config.ConfigBeanProxy;
import org.jvnet.hk2.config.ConfigListener;
import org.jvnet.hk2.config.ConfigSupport;
import org.jvnet.hk2.config.NotProcessed;
import org.jvnet.hk2.config.ObservableBean;
import org.jvnet.hk2.config.UnprocessedChangeEvents;

/**
 *  Listens for the changes to the configuration of JVM and Java system
 *  properties (including the Java VM options).  Most of the effort involves the jvm-options
 *  list, but restart is also required for any changes to the java-config.
 *  <p>
 *  This class is implemented so that the server restart is NOT required if a deployer wants to deploy 
 *  an application and the application depends on a particular Java system property
 *  (-D) to be specified. As of now, the deployer specifies the system property
 *  and deploys the application and the application should find it when it does
 *  System.getProperty("property-name"). Here is the complete algorithm:
 * 
 *  <ol>
 *    <li> If any of the attributes of the java-config element (JavaConfig) change,
 *         this listener flags it as server-restart-required kind of change.
 *    </li>
 *    <li> If a system property is being defined and it is NOT one that starts with
 *         "-Djava." or "-Djavax.", it will be immediately set in the System using
 *         System.setProperty() call. A server restart won't be needed.
 *    </li>
 *    <li> If any other JVM option is defined that does not start with "-D" (excluding
 *         the cases covered above), it is deemed to be a JVM option resulting
 *         in server-restart-required flag set.
 *    </li>
 *    <li> If a System Property (with above distinctions) is removed, System.clearProperty()
 *         is called and server-restart-required flag is set accordingly.
 *    </li>
 *  </ol>
 * Change in the value of a particular system property level is not handled explicitly.
 * User interfaces should take a note of it. e.g. CLI does not make -Dfoo=bar and -Dfoo=bar1
 * as same properties being set to two different values since it is hard to distinguish it
 * in general case. Users should delete -Dfoo=bar and add -Dfoo=bar1explicitly in this case.
 * @author &#2325;&#2375;&#2342;&#2366;&#2352 (km@dev.java.net)
 * @since GlassFish V3
 * @see com.sun.enterprise.config.serverbeans.JavaConfig
 */

@Service
public final class GenericJavaConfigListener implements PostConstruct, ConfigListener, Startup {
    /* The following objects are injected so that this
     * ConfigListener receives config events related to those objects.
     */   
    @Inject
    private Domain domain; //note: this should be current, and does contain the already modified values!
    
    @Inject(name=ServerEnvironment.DEFAULT_INSTANCE_NAME, optional=true)
    Cluster cluster;
    
    @Inject(name=ServerEnvironment.DEFAULT_INSTANCE_NAME)
    Config config; // this is the server's Config
    
    @Inject(name=ServerEnvironment.DEFAULT_INSTANCE_NAME)
    Server server;
    
    // The JavaConfig cannot be injected because it might not be the right 
    // one that gets injected.  The JavaConfig is obtained from the Config
    // in postConstruct below.
    private JavaConfig jc;

    
    volatile List<String> oldProps;
    /* Implementation note: See 6028*/
    
    volatile Map<String,String>  oldAttrs;
    
    static final Logger logger = LogDomains.getLogger(GenericJavaConfigListener.class, LogDomains.ADMIN_LOGGER);
    
    @Override
    public void postConstruct() {
        jc = config.getJavaConfig();
        if (jc != null) {
            // register to listen for config events on the JavaConfig
            ((ObservableBean)ConfigSupport.getImpl(jc)).addListener(this);            
        }
        if (jc != null && jc.getJvmOptions() != null) {
            oldProps = new ArrayList<String>(jc.getJvmOptions()); //defensive copy
            oldAttrs = collectAttrs(jc);
        }
    }

    @Override
    public Lifecycle getLifecycle() {
        return Startup.Lifecycle.SERVER;
    }
        
    /**
        Get attributes as a Map so that we can do an easy compare of old vs new and
        also emit a useful change message.
        <p>
        This list must contain all attributes that are relevant to restart-required.
     */
    private static Map<String,String> collectAttrs(final JavaConfig jc)
    {
        final Map<String,String> values = new HashMap<String,String>();
        values.put( "JavaHome", jc.getJavaHome() );
        values.put( "DebugEnabled", jc.getDebugEnabled() );
        values.put( "DebugOptions", jc.getDebugOptions() );
        values.put( "RmicOptions", jc.getRmicOptions() );
        values.put( "JavacOptions", jc.getJavacOptions() );
        values.put( "ClasspathPrefix", jc.getClasspathPrefix() );
        values.put( "ClasspathSuffix", jc.getClasspathSuffix() );
        values.put( "ServerClasspath", jc.getServerClasspath() );
        values.put( "SystemClasspath", jc.getSystemClasspath() );
        values.put( "NativeLibraryPathPrefix", jc.getNativeLibraryPathPrefix() );
        values.put( "NativeLibraryPathSuffix", jc.getNativeLibraryPathSuffix() );
        values.put( "BytecodePreprocessors", jc.getBytecodePreprocessors() );
        values.put( "EnvClasspathIgnored", jc.getEnvClasspathIgnored() );
        
        return values;
    }
    
    /* force serial behavior; don't allow more than one thread to make a mess here */
    @Override
    public synchronized UnprocessedChangeEvents changed(PropertyChangeEvent[] events) {
        final UnprocessedChangeEvents unp = ConfigSupport.sortAndDispatch(events, new Changed() {
            @Override
            public <T extends ConfigBeanProxy> NotProcessed changed(TYPE type, Class<T> tc, T t) {
                NotProcessed result = null;
                
                if (tc == Profiler.class) {
                    result = new NotProcessed("Creation or changes to a profiler require restart");
                }
                else if (tc == Property.class && t.getParent().getClass() == JavaConfig.class) {
                    result = new NotProcessed("Addition of properties to JavaConfig requires restart");
                }
                // check tc first because it is faster than instanceof
                else if (tc == JavaConfig.class && t instanceof JavaConfig) {
                    final JavaConfig njc = (JavaConfig) t; 
                    logFine(type, njc);
                    
                    // we must *always* check the jvm options, no way to know except by comparing,
                    // plus we should send an appropriate message back for each removed/added item
                    final List<String> curProps = new ArrayList<String>( njc.getJvmOptions() );
                    final boolean jvmOptionsWereChanged = ! oldProps.equals(curProps);
                    final List<String> reasons = handle(oldProps, curProps);
                    oldProps = curProps;
                    
                    // something in the JavaConfig itself changed
                    // to do this well, we ought to keep a list of attributes, so we can make a good message
                    // saying exactly which attribute what changed
                    final Map<String,String> curAttrs = collectAttrs(njc);
                    reasons.addAll( handleAttrs( oldAttrs, curAttrs ) );
                    oldAttrs = curAttrs;
                    
                    result = reasons.isEmpty() ? null : new NotProcessed( GenericJavaConfigListener.toString(reasons) );
                }
                // check tc first because it is faster than instanceof
                else if (tc == SystemProperty.class && t instanceof SystemProperty) {
                    // check to see if this system property is referenced by any of the options
                    final SystemProperty sp = (SystemProperty) t;
                    String pname = sp.getName();
                    if (referencesProperty(pname, oldProps) ||
                        referencesProperty(pname, oldAttrs.values())) {
                        result = new NotProcessed("The system-property, " + pname + ", that is referenced by the Java configuration, was modified");
                    }
                }
                else {
                    // ignore other changes that are reported
                }

                return result;
            }
        }
        , logger);
         return unp;
    }
    
    private void logFine(TYPE ct, JavaConfig njc) {
        final Level level = Level.FINE;
        if (logger.isLoggable(level)) {
            logger.log(level, "<java-config> changed");
            int os = oldProps.size(), ns = njc.getJvmOptions().size();
            if (os > ns) {
                logger.log(level, "a system property or a JVM option was removed (old size = {0}), new size: ({1}), restart is required, based on the property", new Object[]{os, ns});
            } else if(os < ns) {
                logger.log(level, "a system property or a JVM option was added, (old size = {0}), new size: ({1}), restart is required, based on the property", new Object[]{os, ns});
            } else {
                logger.log(level, "an attribute was changed, restart required");
            }
        }
    }
    
    private List<String>
    handleAttrs( final Map<String,String> old, final Map<String,String> cur) {
        if ( old.size() != cur.size() ) {
            throw new IllegalArgumentException();
        }
        
        // find all the differences and generate helpful messages
        final List<String> reasons = new ArrayList<String>();
        for( final Map.Entry<String,String> e : old.entrySet() ) {
            final String oldValue = e.getValue();
            final String curValue = cur.get(e.getKey());
            
            final boolean changed = (oldValue == null && curValue != null) ||
                                    (oldValue != null && curValue == null) ||
                                    (oldValue != null && ! oldValue.equals(curValue));
            if ( changed ) {
                reasons.add( "JavaConfig attribute '" + e.getKey() + "' was changed from '" + oldValue + "' to '" + curValue + "'");
            }
        }
        return reasons;
    }


    
    private List<String> handle(List<String> old, List<String> cur) {
        NotProcessed np = null;
        
        final Set<String> added = new HashSet<String>(cur);
        added.removeAll(old);
        
        final Set<String> removed = new HashSet<String>(old);
        removed.removeAll(cur);
        
        return getNotProcessed(removed, added);
    }
    //using C-style ;)
    private static final String SYS_PROP_REGEX = "=";
    
    //TODO need to handle system property substitution here
    private String[] nvp(final String s) {
        final String[] nv = s.split(SYS_PROP_REGEX);
        final String name  = nv[0];
        String value = s.substring(name.length());
        if ( value.startsWith("=") ) {
            value = value.substring(1);
        }
        
        return new String[] { name, value };
    }
    
    static final String DPREFIX = "-D";
    
    private static String stripPrefix(final String s)
    {
        return s.startsWith(DPREFIX) ? s.substring(DPREFIX.length()) : s;
    }
    
    private List<String> getNotProcessed(
        final Set<String> removals,
        final Set<String> additions)
    {
        //look at the list, clear and/or add system properties 
        // otherwise they require server restart
        
        final List<String> reasons = new ArrayList<String>();
        for( final String removed : removals) {
            final String[] nv = nvp(removed);
            final String name  = nv[0];
            
            if (possiblyDynamicallyReconfigurable(removed)) {
                System.clearProperty(stripPrefix(name));
            }
            else {
                // detect a removal/addition which is really a change
                String newItem = null;
                for( final String added : additions ) {
                    if ( name.equals( nvp(added)[0] ) ) {
                        newItem = added;
                        additions.remove(added);
                        break;
                    }
                }
                String msg = null;
                if ( newItem != null ) {
                    msg = "Change from '" + removed + "' to '" + newItem + "' cannot take effect without server restart";
                }
                else {
                    msg = "Removal of: " + removed + " cannot take effect without server restart";
                }
                reasons.add(msg);
            }
        }
        
        // process any remaining additions
        for( final String added : additions) {
            final String[] nv = nvp(added);
            final String   name  = nv[0];
            final String   newValue = nv[1];
            
            if (possiblyDynamicallyReconfigurable(added)) {
                System.setProperty( stripPrefix(name), newValue );
            }
            else {
                reasons.add( "Addition of: '" + added + "' cannot take effect without server restart" );
            }
        }
        
        return reasons;
    }
    
    private static String toString( final List<String> items ) {
        final StringBuffer buf = new StringBuffer();
        final String delim = ", ";
        for( final String s : items ) {
            if ( buf.length() != 0 ) {
                buf.append(delim);
            }
            buf.append(s);
        }
        
        return buf.toString();
    }

    
    /** Determines with some confidence level if a particular String denotes
     *  a system property that can be set in the current JVM's (i.e. the JVM where
     *  this method's code runs) System. Anything that does not start with
     *  "-D" is not dynamically settable. However, anything that starts with "-Djava."
     *  or "-Djavax." is not dynamically settable.
     */
    private boolean possiblyDynamicallyReconfigurable(String s) {
        if (s.startsWith(DPREFIX) && !s.startsWith("-Djava.")
            && !s.startsWith("-Djavax.")) 
            return true;
        return false;
    }
  
    /*
     * Deterines whether the given property name, pname, is references by any
     * of the values in the values list. A reference is of the form ${pname}.
     * Returns true if the pname is referenced.
     */
    static private boolean referencesProperty(String pname, Collection<String> values) {
        String ref = "${" + pname + "}";
        for (String v : values) {
            if ((v != null) && (v.contains(ref))) 
                return true;
        }
        return false;
    }
}
