/*
 * <copyright>
 *  Copyright 1997-2003 BBNT Solutions, LLC
 *  under sponsorship of the Defense Advanced Research Projects Agency (DARPA).
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the Cougaar Open Source License as published by
 *  DARPA on the Cougaar Open Source Website (www.cougaar.org).
 * 
 *  THE COUGAAR SOFTWARE AND ANY DERIVATIVE SUPPLIED BY LICENSOR IS
 *  PROVIDED 'AS IS' WITHOUT WARRANTIES OF ANY KIND, WHETHER EXPRESS OR
 *  IMPLIED, INCLUDING (BUT NOT LIMITED TO) ALL IMPLIED WARRANTIES OF
 *  MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE, AND WITHOUT
 *  ANY WARRANTIES AS TO NON-INFRINGEMENT.  IN NO EVENT SHALL COPYRIGHT
 *  HOLDER BE LIABLE FOR ANY DIRECT, SPECIAL, INDIRECT OR CONSEQUENTIAL
 *  DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE OF DATA OR PROFITS,
 *  TORTIOUS CONDUCT, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
 *  PERFORMANCE OF THE COUGAAR SOFTWARE.
 * </copyright>
 */

package org.cougaar.community.init;

import org.cougaar.core.component.BindingSite;
import org.cougaar.core.component.Component;
import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.component.ServiceProvider;
import org.cougaar.core.node.DBInitializerService;
import org.cougaar.core.node.NodeControlService;
import org.cougaar.core.service.LoggingService;
import org.cougaar.util.GenericStateModelAdapter;

/** 
 * A component which creates and advertises the appropriate 
 * CommunityInitializerService ServiceProvider.
 * It can initialize from the CSMART database, using the <code>DBInitializerService</code>,
 * or from XML files, depending on where components were intialized from.
 * <p>
 * @see FileCommunityInitializerServiceProvider
 * @see DBCommunityInitializerServiceProvider
 **/
public final class CommunityInitializerServiceComponent
extends GenericStateModelAdapter
implements Component 
{
  private static final String INITIALIZER_PROP = 
    "org.cougaar.core.node.InitializationComponent";

  private ServiceBroker sb;

  private DBInitializerService dbInit;
  private ServiceProvider theSP;
  private LoggingService log;

  public void setBindingSite(BindingSite bs) {
    // this.sb = bs.getServiceBroker();
  }

  public void setNodeControlService(NodeControlService ncs) {
    if (ncs == null) {
      // Revocation
    } else {
      this.sb = ncs.getRootServiceBroker();
    }
  }

  /*
    // DBInitializerService isn't available in the node agent
  public void setDBInitializerService(DBInitializerService dbInit) {
    this.dbInit = dbInit;
  }
  */

  public void load() {
    super.load();

    log = (LoggingService)
      sb.getService(this, LoggingService.class, null);
    if (log == null) {
      log = LoggingService.NULL;
    }

    dbInit = (DBInitializerService) sb.getService(this, DBInitializerService.class, null);

    // Do not provide this service if there is already one there.
    // This allows someone to provide their own component to provide
    // the community initializer service in their configuration
    if (sb.hasService(CommunityInitializerService.class)) {
      // already have CommunityInitializer service!
      //
      // leave the existing service in place
      if (log.isInfoEnabled()) {
        log.info(
            "Not loading the default community initializer service");
      }
      if (log != LoggingService.NULL) {
        sb.releaseService(this, LoggingService.class, log);
        log = null;
      }
      return;
    }

    theSP = chooseSP();
    if (theSP != null)
      sb.addService(CommunityInitializerService.class, theSP);

    if (log != LoggingService.NULL) {
      sb.releaseService(this, LoggingService.class, log);
      log = null;
    }
  }

  public void unload() {
    if (theSP != null) {
      sb.revokeService(CommunityInitializerService.class, theSP);
      theSP = null;
    }
    super.unload();
  }

  private ServiceProvider chooseSP() {
    try {
      ServiceProvider sp;
      String prop = System.getProperty(INITIALIZER_PROP);
      if (prop != null && prop.indexOf("DB") != -1 && dbInit != null) {
        sp = new DBCommunityInitializerServiceProvider(dbInit);
	if (log.isInfoEnabled())
	  log.info("Using CSMART DB CommunityInitializer");
      } else {
	// Note that these files are XML
        sp = new FileCommunityInitializerServiceProvider();
	if (log.isInfoEnabled())
	  log.info("Using File (XML) CommunityInitializer");
      }
      return sp;
    } catch (Exception e) {
      log.error("Exception creating CommunityInitializerService", e);
      return null;
    }
  }
}
