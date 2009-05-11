/**
* DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
* Copyright 2009 Sun Microsystems, Inc. All rights reserved.
* Generated code from the com.sun.enterprise.config.serverbeans.*
* config beans, based on  HK2 meta model for these beans
* see generator at org.admin.admin.rest.GeneratorResource
* date=Mon May 11 13:27:46 PDT 2009
* Very soon, this generated code will be replace by asm or even better...more dynamic logic.
* Ludovic Champenois ludo@dev.java.net
*
**/
package org.glassfish.admin.rest.resources;
import com.sun.enterprise.config.serverbeans.*;
import javax.ws.rs.*;
import org.glassfish.admin.rest.TemplateResource;
import com.sun.grizzly.config.dom.PortUnification;
public class PortUnificationResource extends TemplateResource<PortUnification> {

	@Path("protocol-finder/")
	public ListProtocolFinderResource getProtocolFinderResource() {
		ListProtocolFinderResource resource = resourceContext.getResource(ListProtocolFinderResource.class);
		resource.setEntity(getEntity().getProtocolFinder() );
		return resource;
	}
}
