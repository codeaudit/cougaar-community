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
    Collection communityConfigs = null;
    ServiceBroker sb = getBindingSite().getServiceBroker();
    AgentIdentificationService ais = (AgentIdentificationService)
      sb.getService(this, AgentIdentificationService.class, null);
    MessageAddress agentId = ais.getMessageAddress();
    sb.releaseService(this, AgentIdentificationService.class, ais);
    log = (LoggingService)sb.getService(this, LoggingService.class, null);
    if (log == null)
      log = LoggingService.NULL;
    if (log.isDebugEnabled())
      log.debug ("Loading CommunityServiceComponent");
    String initXmlFile = System.getProperty("org.cougaar.community.configfile");
    if (initXmlFile != null )
      if (log.isDebugEnabled())
        log.debug("initXmlFile is:" +initXmlFile);
    String value = System.getProperty("org.cougaar.community.caching");
    if (value != null ) {
      if (value.equalsIgnoreCase("off")) {
        if (log.isDebugEnabled())
          log.debug("System property for caching is off.\n");
        useCache = false;
      }
    }
    CommunityService cs = loadCommunityService(agentId);
    CommunityInitializerService cis = (CommunityInitializerService)
      sb.getService(this, CommunityInitializerService.class, null);
    
    if (cis != null) {
      try {
	//initXmlFile only used by file-based community config
	communityConfigs = cis.getCommunityDescriptions(agentId.toString());
      }
      catch (Exception e) {
	log.error("Unable to obtain community information for agent "+agentId.toString(), e);
      } finally {
	sb.releaseService(this, CommunityInitializerService.class, cis);
      }
      // FIXME we just released the community-init-service!
      initializeCommunityRelationships(cs, cis, agentId, communityConfigs); //recursive
    } else {
      if (log.isWarnEnabled())
	log.warn("Could not get a CommunityInitializerService. No communities initalized!");
    }

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

  /**
   * Adds initial community relationships for this agent to Name Server.
   * Community relationships are obtained from the CommunityInitializerService which
   * in turn obtains the information from either the Configuration database or
   * an XML file based on a system parameter. This method is recursive in tha this
   * agent, if it creates a community, takes responsibility for determining if the
   * community that it created is a member of other communities and will add that
   * information to the NameServer as well.
   * @param cs       Reference to CommunityService
   * @param cis       Reference to CommunityInitializerService
   * @param agentID  Agent identifier
   * @param communityConfigs  CommunityConfig objects
   */
  private void initializeCommunityRelationships(CommunityService cs, CommunityInitializerService cis,
                                                Object entId, Collection communityConfigs) {
    Object entityId;
    if (entId instanceof MessageAddress)
      entityId = (MessageAddress) entId;
    else
      entityId = entId.toString();

    if (log.isDebugEnabled()) {
      log.debug("Setup initial community assignments: agent=" + entityId);
    }
    String communityName = null;
    try {
      for (Iterator it = communityConfigs.iterator(); it.hasNext();) {
        CommunityConfig cc = (CommunityConfig)it.next();
        communityName = cc.getName();
        if (!cs.communityExists(communityName)) {
          if (log.isDebugEnabled())
            log.debug("Agent " + entityId + ": creating community " + communityName);
          cs.createCommunity(communityName, cc.getAttributes());
          //this agent just created this community so now have responsibility to check if this
          // community is a member of any other communities
          Collection communityWithCommMember = cis.getCommunityDescriptions(communityName);
          if (!communityWithCommMember.isEmpty()) {
            initializeCommunityRelationships(cs, cis, communityName, communityWithCommMember);
          }
        }
        Attributes myAttributes = cc.getEntity(entityId.toString()).getAttributes();
        if (log.isDebugEnabled()) {
          log.debug("Adding Entity " + entityId + " to community " + communityName);
        }
        // EntityId is either a MessageAddress or a String Object
        cs.addToCommunity(communityName, entityId, entityId.toString(), myAttributes);
      }
    } catch (Exception ex) {
      log.error("Exception when initializing communities, " + ex, ex);
    }
  }

}
