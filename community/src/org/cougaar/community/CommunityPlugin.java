/*
 * <copyright>
 *  Copyright 1997-2001 Mobile Intelligence Corp
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

import org.cougaar.core.agent.ClusterIdentifier;
import org.cougaar.core.blackboard.IncrementalSubscription;
import org.cougaar.core.plugin.SimplePlugin;
import org.cougaar.core.service.LoggingService;

import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.component.ServiceRevokedListener;
import org.cougaar.core.component.ServiceRevokedEvent;

import org.cougaar.util.UnaryPredicate;

import org.cougaar.community.*;
import org.cougaar.community.util.*;
import org.cougaar.core.service.community.*;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;


/**
 * This Plugin establishes initial community assignments for parent agent
 * and subscribes to CommunityRequests.
 */
public class CommunityPlugin extends SimplePlugin {

  //private static Logger log;
  private LoggingService log;

  // The CommunityManagementService for creating communities,
  // getting community rosters, etc.
  private CommunityService communityService = null;

  private ClusterIdentifier myAgent;

  /**
   * @param obj Arguments
   */
  public void setParameter(Object obj) {
    //None currently used
  }


  /**
   * Initialize community assignments and subscribe to CommunityRequests and
   * CommunityChangeNotifications.
   */
  protected void setupSubscriptions() {

    log = (LoggingService) getBindingSite().getServiceBroker().
        getService(this, LoggingService.class, null);

    myAgent = getClusterIdentifier();

    communityService = getCommunityService();

    // Subscribe to new CommunityRequests
    requests =
      (IncrementalSubscription)getBlackboardService()
	.subscribe(communityRequestPredicate);

    // Subscribe to CommunityChangeNotifications
    /*
    changeNotifications =
      (IncrementalSubscription)getBlackboardService()
	.subscribe(changeNotificationPredicate);

    // Process any existing requests
    processCommunityRequests(getBlackboardService().
      query(communityRequestPredicate));
    */
  }

  /**
  * Determine identity of parent agent and respond to CommunityRequests by
  * other plugins.
  */
  protected void execute () {

    // Process CommunityRequests
    processCommunityRequests(requests.getAddedCollection());

    // Update rosters when CommunityChangeNotifications are received
    /*
    Enumeration enum  = changeNotifications.getAddedList();
    while (enum.hasMoreElements()) {
      CommunityChangeNotification ccn =
        (CommunityChangeNotification)enum.nextElement();
      String communityName = ccn.getCommunityName();
      log.debug("Added CCN: agent=" + myAgent + " source=" + ccn.getSource());
      updateRosters(communityName);
    }
    enum  = changeNotifications.getChangedList();
    while (enum.hasMoreElements()) {
      CommunityChangeNotification ccn =
        (CommunityChangeNotification)enum.nextElement();
      String communityName = ccn.getCommunityName();
      log.debug("Changed CCN: agent=" + myAgent + " source=" + ccn.getSource());
      updateRosters(communityName);
    }
    */

  }

  private void processCommunityRequests(Collection collection) {
    Iterator it = collection.iterator();
    while (it.hasNext()) {
      CommunityRequestImpl cr = (CommunityRequestImpl)it.next();
      if (cr.getCommunityResponse() == null &&  // Haven't responded to this yet
          cr.getVerb() != null) {               //   and has a Verb
        String verb = cr.getVerb();
        if (verb.startsWith("GET_ROSTER")) {
          getRoster(cr);
        } else if (verb.equals("LIST_PARENT_COMMUNITIES")) {
          listParentCommunities(cr);
        } else if (verb.equals("JOIN_COMMUNITY")) {
          join(cr);
        } else if (verb.equals("LEAVE_COMMUNITY")) {
          leave(cr);
        } else if (verb.equals("FIND_AGENTS_WITH_ROLE")) {
          findAgents(cr);
        } else if (verb.equals("GET_ROLES")) {
          getRoles(cr);
        } else if (verb.equals("LIST_ALL_COMMUNITIES")) {
          listCommunities(cr);
        } else if (verb.equals("FIND_COMMUNITIES")) {
          searchForCommunities(cr);
        } else if (verb.equals("FIND_ENTITIES")) {
          searchForEntities(cr);
        } else {
          unknownVerb(cr);
        }
      }
    }
  }

  /**
   * Gets the roster for a named community.
   */
  private void getRoster(final CommunityRequestImpl req) {
    CommunityRoster roster = communityService.getRoster(req.getTargetCommunityName());
    int respCode = roster.communityExists() ? CommunityResponse.SUCCESS
                                            : CommunityResponse.FAIL;
    CommunityResponse resp = new CommunityResponseImpl(respCode,
      CommunityResponseImpl.resultCodeToString(respCode), roster);
    req.setCommunityResponse(resp);
    getBlackboardService().publishChange(req);

    if (req.getVerb().endsWith("WITH_UPDATES")) {
      //communityService.addListener(myAgent, req.getTargetCommunityName());
      communityService.addListener(new CommunityChangeListener(){
        public void communityChanged(CommunityChangeEvent cce) {
          if (cce.getCommunityName().equals(req.getTargetCommunityName()) &&
              (cce.getType() == cce.ADD_ENTITY ||
               cce.getType() == cce.REMOVE_ENTITY)) {
            updateRosters(req.getTargetCommunityName());
          }
        }
        public String getCommunityName() {
          return req.getTargetCommunityName();
        }
      });
    }
  }

  /**
   * Adds an agent to a community
   */
  private void join(CommunityRequestImpl req) {
    Attributes attrs = new BasicAttributes();
    attrs.put("Name", myAgent.toString());
    attrs.put("EntityType", "Agent");
    attrs.put("Role", "Member");
    boolean result = communityService.addToCommunity(req.getTargetCommunityName(),
      myAgent, myAgent.toString(), attrs);
    int respCode = result ? CommunityResponse.SUCCESS : CommunityResponse.FAIL;
    CommunityResponse resp = new CommunityResponseImpl(respCode,
      CommunityResponseImpl.resultCodeToString(respCode), null);
    req.setCommunityResponse(resp);
    getBlackboardService().publishChange(req);
  }

  /**
   * Adds an agent to a community
   */
  private void leave(CommunityRequestImpl req) {
    boolean result = communityService.removeFromCommunity(req.getTargetCommunityName(),
      myAgent.toString());
    int respCode = result ? CommunityResponse.SUCCESS : CommunityResponse.FAIL;
    CommunityResponse resp = new CommunityResponseImpl(respCode,
      CommunityResponseImpl.resultCodeToString(respCode), null);
    req.setCommunityResponse(resp);
    getBlackboardService().publishChange(req);
  }

  /**
   * Creates a list identifying all communities of which a specifed agent is
   * a member
   */
  private void listParentCommunities(CommunityRequestImpl req) {
    Collection communityNames =
      communityService.listParentCommunities(req.getAgentName(), req.getFilter());
    int respCode = CommunityResponse.SUCCESS;
    String respMsg = CommunityResponseImpl.resultCodeToString(respCode);
    CommunityResponse resp =
      new CommunityResponseImpl(CommunityResponse.SUCCESS, respMsg, communityNames);
    req.setCommunityResponse(resp);
    getBlackboardService().publishChange(req);
  }

  /**
   * Locates all agents in a specified community that provide a specified role.
   */
  private void findAgents(CommunityRequestImpl req) {
    Collection names =
      communityService.searchByRole(req.getTargetCommunityName(), req.getRole());
    Collection agentIds = new Vector();
    for (Iterator it = names.iterator(); it.hasNext();)
      agentIds.add(it.next());
    int respCode = CommunityResponse.SUCCESS;
    String respMsg = CommunityResponseImpl.resultCodeToString(respCode);
    CommunityResponse resp =
      new CommunityResponseImpl(respCode, respMsg, agentIds);
    req.setCommunityResponse(resp);
    getBlackboardService().publishChange(req);
  }

  /**
   * Gets a list of roles associated with a specific agent in a specific
   * community.
   */
  private void getRoles(CommunityRequestImpl req) {
    Collection roles =
      communityService.getEntityRoles(req.getTargetCommunityName(),
      req.getAgentName());
    int respCode = CommunityResponse.SUCCESS;
    String respMsg = CommunityResponseImpl.resultCodeToString(respCode);
    CommunityResponse resp =
      new CommunityResponseImpl(respCode, respMsg, roles);
    req.setCommunityResponse(resp);
    getBlackboardService().publishChange(req);
  }

  /**
   * Gets a list of all communities currently listed in YP.
   */
  private void listCommunities(CommunityRequestImpl req) {
    Collection communityNames =
      communityService.listAllCommunities();
    int respCode = CommunityResponse.SUCCESS;
    String respMsg = CommunityResponseImpl.resultCodeToString(respCode);
    CommunityResponse resp =
      new CommunityResponseImpl(respCode, respMsg, communityNames);
    req.setCommunityResponse(resp);
    getBlackboardService().publishChange(req);
  }

  /**
   * Search for communties that satisfy given criteria.
   */
  private void searchForCommunities(CommunityRequestImpl req) {
    Collection communityNames = communityService.search(req.getFilter());
    int respCode = CommunityResponse.SUCCESS;
    String respMsg = CommunityResponseImpl.resultCodeToString(respCode);
    CommunityResponse resp =
      new CommunityResponseImpl(respCode, respMsg, communityNames);
    req.setCommunityResponse(resp);
    getBlackboardService().publishChange(req);
  }


  /**
   * Search for entities that satisfy given criteria.
   */
  private void searchForEntities(CommunityRequestImpl req) {
    Collection entityNames =
      communityService.search(req.getTargetCommunityName(), req.getFilter());
    int respCode = CommunityResponse.SUCCESS;
    String respMsg = CommunityResponseImpl.resultCodeToString(respCode);
    CommunityResponse resp =
      new CommunityResponseImpl(respCode, respMsg, entityNames);
    req.setCommunityResponse(resp);
    getBlackboardService().publishChange(req);
  }


  /**
   * Process CommunityRequests with invalid verbs.
   */
  private void unknownVerb(CommunityRequestImpl req) {
    CommunityResponse resp = new CommunityResponseImpl(CommunityResponse.FAIL,
      "Unknown CommunityRequest verb", null);
    req.setCommunityResponse(resp);
    getBlackboardService().publishChange(req);
  }

  /**
   * Updates rosters.
   */
  private void updateRosters(String communityName) {
    getBlackboardService().openTransaction();
    Iterator it = getBlackboardService().query(communityRequestPredicate).iterator();
    getBlackboardService().closeTransaction();
    while (it.hasNext()) {
      CommunityRequestImpl req = (CommunityRequestImpl)it.next();
      if (req.getVerb() != null && req.getVerb().equals("GET_ROSTER_WITH_UPDATES")) {
        CommunityRoster roster = communityService.getRoster(req.getTargetCommunityName());
        CommunityResponseImpl resp = (CommunityResponseImpl)req.getCommunityResponse();
        resp.setResponseObject(roster);
        getBlackboardService().openTransaction();
        getBlackboardService().publishChange(req);
        getBlackboardService().closeTransaction();
      }
    }
  }


  /**
   * Gets reference to CommunityService.
   */
  private CommunityService getCommunityService() {
    ServiceBroker sb = getBindingSite().getServiceBroker();
    if (sb.hasService(CommunityService.class)) {
      return (CommunityService)sb.getService(this, CommunityService.class,
        new ServiceRevokedListener() {
          public void serviceRevoked(ServiceRevokedEvent re) {}
      });
    } else {
      log.error("CommunityService not available");
      return null;
    }
  }


  private IncrementalSubscription requests;
  private UnaryPredicate communityRequestPredicate = new UnaryPredicate() {
    public boolean execute (Object o) {
      return (o instanceof CommunityRequest);
  }};

  /*
  private IncrementalSubscription changeNotifications;
  private UnaryPredicate changeNotificationPredicate = new UnaryPredicate() {
    public boolean execute (Object o) {
      if (o instanceof CommunityChangeNotification) {
        CommunityChangeNotification ccn = (CommunityChangeNotification)o;
        return (ccn.getTargets() == Collections.EMPTY_SET);
      }
      return false;
  }};
  */

}