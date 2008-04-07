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
package org.glassfish.admin.amx.loader;

import java.io.File;

import javax.management.ObjectName;
import javax.management.MBeanServer;
import javax.management.MBeanServerInvocationHandler;

import com.sun.appserv.management.base.Util;

import org.glassfish.admin.amx.util.Issues;

import org.glassfish.admin.amx.logging.AMXServerLogger;
import org.glassfish.admin.amx.logging.AMXMBeanRootLogger;

/**
	Utilities to help boot up AMX.
 */
public final class BootUtil
{
	private final String	mAppserverDomainName;
	private final File      mInstanceRoot;

	private final String	mAMX_JMXDomain;
	
	private static BootUtil	INSTANCE	= null;
	
	private boolean         mAMXReady;
	
	private final boolean   mOfflineAMX;
	 
		private
	BootUtil( final boolean   offline )
	{
		AMXServerLogger.getInstance();
		AMXMBeanRootLogger.getInstance();
		
        mInstanceRoot        =  new File( System.getProperty( "com.sun.aas.instanceRoot" ) );
        mAppserverDomainName = mInstanceRoot.getName();

		mAMX_JMXDomain		    = "amx";
		mAMXReady   = false;
		
		mOfflineAMX = offline;
	}
    
		public File
	getInstanceRoot()
	{
        return mInstanceRoot;
    }
    
    /**
       The name of the appserver domain eg "domain1".
     */ 
		public String
	getAppserverDomainName()
	{
        return mAppserverDomainName;
    }
	
    
    /**
        Return the name of the server in which this code is running.
     */
        public String
    getServerName()
    {
        //return  AdminService.getAdminService().getAdminContext().getServerName();
        Issues.getAMXIssues().notDone( "BootUtil.getServerName()" );
        
        return "server";
    }
    
		public static synchronized void
	init( final boolean offline )
	{
		INSTANCE	= new BootUtil( offline );
	}

        public boolean
    getOffline()
    {
        return mOfflineAMX;
    }

		public static synchronized BootUtil
	getInstance()
	{
	    if ( INSTANCE == null )
	    {   
	        throw new IllegalArgumentException( "must call init() first" );
	    }

		return( INSTANCE );
	}
	
		public boolean
	getAMXReady()
	{
	    return mAMXReady;
	}
	
		public void
	setAMXReady( final boolean  ready)
	{
	    if ( mAMXReady && ! ready )
	    {
	        throw new IllegalArgumentException();
	    }
	    
	    mAMXReady   = ready;
	}

		private ObjectName
	getObjectName( String props )
	{
		final String	domain	= getAMXSupportJMXDomain();
		
		return( Util.newObjectName( domain, props ) );
	}
	
	    public String
	getAMXSupportJMXDomain()
	{
	    return getAMXJMXDomainName() + "-support";
	}
	
		public String
	getAMXJMXDomainName()
	{
		return( mAMX_JMXDomain );
	}
}








