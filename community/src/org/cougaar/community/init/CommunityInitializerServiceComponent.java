/*
 * <copyright>
 *  Copyright 1997-2001 BBNT Solutions, LLC
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
import org.cougaar.core.component.ServiceProvider;
import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.node.DBInitializerService;
import org.cougaar.core.node.NodeControlService;
import org.cougaar.util.GenericStateModelAdapter;

/** 
 * A component which creates and advertises the appropriate 
 * CommunityInitializerService ServiceProvider.
 * <p>
 * @see FileCommunityInitializerServiceProvider
 * @see DBCommunityInitializerServiceProvider
 **/
public final class CommunityInitializerServiceComponent
extends GenericStateModelAdapter
implements Component 
{
  private ServiceBroker sb;

  private DBInitializerService dbInit;
  private ServiceProvider theSP;

  public void setBindingSite(BindingSite bs) {
    // this.sb = bs.getServiceBroker();
  }

  public void setNodeControlService(NodeControlService ncs) {
    this.sb = ncs.getRootServiceBroker();
  }

  public void setDBInitializerService(DBInitializerService dbInit) {
    this.dbInit = dbInit;
  }

  public void load() {
    super.load();
    theSP = chooseSP();
    sb.addService(CommunityInitializerService.class, theSP);
  }

  public void unload() {
    sb.revokeService(CommunityInitializerService.class, theSP);
    super.unload();
  }

  private ServiceProvider chooseSP() {
    try {
      ServiceProvider sp;
      if (dbInit == null) {
        sp = new FileCommunityInitializerServiceProvider();
      } else {
        sp = new DBCommunityInitializerServiceProvider(dbInit);
      }
      return sp;
    } catch (Exception e) {
      throw new RuntimeException("Exception while creating "+getClass().getName(), e);
    }
  }
}
