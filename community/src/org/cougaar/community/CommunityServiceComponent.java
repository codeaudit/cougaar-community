/*
 * <copyright>
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
import javax.naming.directory.Attributes;

import org.cougaar.community.CommunityServiceProvider;

import org.cougaar.core.service.community.CommunityService;

import org.cougaar.core.component.ComponentSupport;
import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.component.ServiceRevokedListener;
import org.cougaar.core.component.ServiceRevokedEvent;

import org.cougaar.core.agent.AgentChildBinder;
import org.cougaar.core.agent.ClusterIdentifier;

import org.cougaar.core.node.InitializerService;
import org.cougaar.core.node.CommunityConfig;
import org.cougaar.core.node.CommunityConfigUtils;

import org.cougaar.core.service.LoggingService;

/**
 * Agent-level component that loads the CommunityService provider and adds
 * initial community relationships for agent to Name Server.
 */

public class CommunityServiceComponent extends ComponentSupport {

  private String initXmlFile = null;
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
    log = (LoggingService)sb.getService(this, LoggingService.class, null);
    if (log.isDebugEnabled())
      log.debug ("Loading CommunityServiceComponent");
    ClusterIdentifier agentId = (ClusterIdentifier)
      ((AgentChildBinder) getBindingSite()).getAgentIdentifier();
    initXmlFile = System.getProperty("org.cougaar.community.configfile");
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
    InitializerService is = (InitializerService) sb.getService(this, InitializerService.class, null);

    try {
      //initXmlFile only used by FileInitializerServiceProvider
      communityConfigs = is.getCommunityDescriptions(agentId.toString(), initXmlFile);  
      //FIXME -only gets info for this particular agent,
      //      -need to add functionality to load info for communities that are members of communities.
    }
    catch (Exception e) {
      System.err.println("\nUnable to obtain community information for agent "+agentId.toString());
      e.printStackTrace();
    } finally {
      sb.releaseService(this, InitializerService.class, is);
    }
    initializeCommunityRelationships(cs, agentId, communityConfigs);  

    super.load();
  }

  /**
   * Creates a CommunityService instance and adds to agent ServiceBroker.
   * @param agentId
   * @return
   */
  private CommunityService loadCommunityService(ClusterIdentifier agentId) {
    ServiceBroker sb = ((AgentChildBinder)getBindingSite()).getServiceBroker();
    CommunityServiceProvider csp = new CommunityServiceProvider(sb, agentId, useCache);   
    sb.addService(CommunityService.class, csp);
    return (CommunityService)sb.getService(this, CommunityService.class,
      new ServiceRevokedListener() {
        public void serviceRevoked(ServiceRevokedEvent re) {}
    });
  }

  /**
   * Adds initial community relationships for this agent to Name Server.
   * Community relationships is obtained from the InitializerService which
   * in turn obtains the information from either the Configuration database or
   * an XML file based on a system parameter. 
   * @param cs       Reference to CommunityService
   * @param agentID  Agent identifier
   * @param communityConfigs  CommunityConfig objects
   */

  private void initializeCommunityRelationships(CommunityService cs,
                                                ClusterIdentifier agentId, Collection communityConfigs) {
    if (log.isDebugEnabled()) {
      log.debug("Setup initial community assignments: agent=" + agentId);
    }
    try {
      for (Iterator it = communityConfigs.iterator(); it.hasNext();) {
        CommunityConfig cc = (CommunityConfig)it.next();
        if (!cs.communityExists(cc.getName())) {
          if (log.isDebugEnabled())
            log.debug("Agent " + agentId + ": creating community " + cc.getName());
          cs.createCommunity(cc.getName(), cc.getAttributes());
        }
        Attributes myAttributes = cc.getEntity(agentId.toString()).getAttributes();
        if (log.isDebugEnabled()) {
          log.debug("Adding Agent " + agentId + " to community " + cc.getName());
        }
        cs.addToCommunity(cc.getName(), agentId, agentId.toString(), myAttributes);
      }
    } catch (Exception ex) {
      log.error("Exception when initializing communities, " + ex, ex);
    }
  }

  /**
   * Adds initial community relationships for this agent to Name Server.
   * Community relationships may be obtained from Configuration database or
   * an XML file.  The Configuration database is used by default.  If loading
   * from an XML file is required the parameter "file=XXX" must be supplied
   * to this component.
   * @param cs       Reference to CommunityService
   * @param agentID  Agent identifier
   */
  private void initializeCommunityRelationships(CommunityService cs,
      ClusterIdentifier agentId) {
    if (log.isDebugEnabled()) {
      log.debug("Setup initial community assignments: agent=" + agentId);
    }
    try {
      // Get initial community config data
      Collection communityConfigs = null;
      if (initXmlFile == null) {
        if (log.isDebugEnabled())
          log.debug("Loading community config data from Configuration database");
        communityConfigs =
          CommunityConfigUtils.getCommunityConfigsFromDB(agentId.toString());
      } else {
        if (log.isDebugEnabled())
          log.debug("Loading community config data from file '" + initXmlFile + "'");
        communityConfigs =
          CommunityConfigUtils.getCommunityConfigsFromFile(initXmlFile, agentId.toString());
      }
      for (Iterator it = communityConfigs.iterator(); it.hasNext();) {
        CommunityConfig cc = (CommunityConfig)it.next();
        if (!cs.communityExists(cc.getName())) {
          if (log.isDebugEnabled())
            log.debug("Agent " + agentId + ": creating community " + cc.getName());
          cs.createCommunity(cc.getName(), cc.getAttributes());
        }
        Attributes myAttributes = cc.getEntity(agentId.toString()).getAttributes();
        if (log.isDebugEnabled()) {
          log.debug("Adding Agent " + agentId + " to community " + cc.getName());
        }
        cs.addToCommunity(cc.getName(), agentId, agentId.toString(), myAttributes);
      }
    } catch (Exception ex) {
      log.error("Exception when initializing communities, " + ex, ex);
    }
  }

}
