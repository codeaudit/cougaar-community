/*
 * <copyright>
 *  Copyright 2003 BBNT Solutions, LLC
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
package org.cougaar.community;

import java.util.*;
import javax.naming.directory.*;

import org.cougaar.community.CommunityServiceProvider;

import org.cougaar.core.service.community.CommunityService;

import org.cougaar.core.component.ComponentSupport;
import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.component.ServiceRevokedListener;
import org.cougaar.core.component.ServiceRevokedEvent;

import org.cougaar.core.mts.MessageAddress;

import org.cougaar.community.init.CommunityInitializerService;
import org.cougaar.community.init.CommunityConfig;

import org.cougaar.core.service.AgentIdentificationService;
import org.cougaar.core.service.LoggingService;

/**
 * Agent-level component that loads the CommunityService provider and adds
 * initial community relationships for agent to Name Server.
 */

public class CommunityServiceComponent extends ComponentSupport {

  private LoggingService log = null;
  private boolean useCache = true;

  public CommunityServiceComponent() {
    super();
  }

  /**
   * Initializes CommunityService and adds initial community
   * relationships for this agent to Name Server.
   */
  public void load() {
    ServiceBroker sb = getBindingSite().getServiceBroker();
    AgentIdentificationService ais = (AgentIdentificationService)
      sb.getService(this, AgentIdentificationService.class, null);
    MessageAddress agentId = ais.getMessageAddress();
    sb.releaseService(this, AgentIdentificationService.class, ais);
    CommunityService cs = loadCommunityService(agentId);
    super.load();
  }

  /**
   * Creates a CommunityService instance and adds to agent ServiceBroker.
   * @param agentId
   * @return
   */
  private CommunityService loadCommunityService(MessageAddress agentId) {
    ServiceBroker sb = getBindingSite().getServiceBroker();
    CommunityServiceProvider csp = new CommunityServiceProvider(sb, agentId, useCache);
    sb.addService(CommunityService.class, csp);
    return (CommunityService)sb.getService(this, CommunityService.class,
      new ServiceRevokedListener() {
        public void serviceRevoked(ServiceRevokedEvent re) {}
    });
  }

}
