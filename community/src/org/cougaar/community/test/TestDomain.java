/*
 * <copyright>
 *  Copyright 2001-2002 Mobile Intelligence Corp.
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

package org.cougaar.community.test;

import java.util.*;
import javax.naming.directory.Attributes;

import org.cougaar.core.service.community.CommunityService;
import org.cougaar.community.*;
import org.cougaar.community.util.*;

import org.cougaar.core.domain.Factory;
import org.cougaar.core.domain.XPlan;

import org.cougaar.planning.ldm.LDMServesPlugin;
import org.cougaar.planning.service.LDMService;
import org.cougaar.core.domain.DomainAdapter;

import org.cougaar.core.service.LoggingService;
import org.cougaar.core.service.UIDServer;

import org.cougaar.core.mts.MessageAddress;

import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.component.ServiceProvider;
import org.cougaar.core.component.ServiceRevokedListener;
import org.cougaar.core.component.ServiceRevokedEvent;

/**
 **/

public class TestDomain extends DomainAdapter {
  private static final String TEST = "test";

  private LDMService ldmService;

  /**
   * getDomainName - returns the Domain name. Used as domain identifier in
   * DomainService.getFactory(String domainName
   *
   * @return String domain name
   */
  public String getDomainName() {
    return TEST;
  }

  public TestDomain() {
    super();
  }

  public void setLDMService(LDMService ldmService) {
    this.ldmService = ldmService;
  }

  public void unload() {
    ServiceBroker sb = getBindingSite().getServiceBroker();
    if (ldmService != null) {
      sb.releaseService(
          this, LDMService.class, ldmService);
      ldmService = null;
    }
    super.unload();
  }

  protected void loadFactory() {
    LDMServesPlugin ldm = ldmService.getLDM();
    UIDServer myUIDServer = ldm.getUIDServer();
    Factory f = new TestRelayFactory(myUIDServer);
    setFactory(f);
  }

  protected void loadXPlan() {
    // no test-specific plan
  }

  protected void loadLPs() {
  }

}
