/*
 * <copyright>
 *  Copyright 2001-2003 Mobile Intelligence Corp
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

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

import javax.naming.directory.ModificationItem;

import org.cougaar.util.log.LoggerFactory;

import org.cougaar.core.service.community.CommunityResponse;
import org.cougaar.core.service.community.CommunityResponseListener;
import org.cougaar.core.service.community.CommunityService;
import org.cougaar.core.service.community.Community;
import org.cougaar.core.service.community.Entity;
import org.cougaar.core.service.community.FindCommunityCallback;

import org.cougaar.community.AbstractCommunityService;
import org.cougaar.community.CommunityMemberships;
import org.cougaar.community.MembershipWatcher;
import org.cougaar.community.manager.CommunityManager;
import org.cougaar.community.CommunityUpdateListener;
import org.cougaar.community.CommunityResponseImpl;

/**
 * Implementation of CommunityService for testing outside of a running
 *  cougaar society.
 **/
public class CommunityServiceTestImpl extends AbstractCommunityService
  implements CommunityService, java.io.Serializable {

  /**
   * Constructor.
   */
  public CommunityServiceTestImpl(String agentName) {
    super.agentName = agentName;
    log = LoggerFactory.getInstance().createLogger(CommunityServiceTestImpl.class);
    communityUpdateListener = new MyCommunityUpdateListener();
    if (cache == null) cache = new CommunityCacheTestImpl();
    communityManager =
        CommunityManagerTestImpl.getInstance(agentName, cache, null);
    myCommunities = new CommunityMemberships();
    membershipWatcher = new MembershipWatcher(agentName,
                                              this,
                                              myCommunities);
  }

  protected CommunityCacheTestImpl getCache() {
    return (CommunityCacheTestImpl)cache;
  }

  protected CommunityMemberships getCommunityMemberships() {
    return myCommunities;
  }

  protected MembershipWatcher getMembershipWatcher() {
    return membershipWatcher;
  }

  protected void queueCommunityRequest(String                    communityName,
                                       int                       requestType,
                                       Entity                    entity,
                                       ModificationItem[]        attrMods,
                                       CommunityResponseListener crl,
                                       long                      delay) {
    log.debug(agentName+": queueCommunityRequest: " +
              " community=" + communityName +
              " type=" + requestType +
              " entity=" + entity +
              " attrMods=" + attrMods);
    CommunityResponse resp =
        communityManager.processRequest(agentName, communityName, requestType, entity, attrMods);
    handleResponse(resp, Collections.singleton(crl));
  }

  protected void sendResponse(CommunityResponse resp, Set listeners) {
    for (Iterator it = listeners.iterator(); it.hasNext();) {
      CommunityResponseListener crl = (CommunityResponseListener)it.next();
      if (crl != null) {
        crl.getResponse(resp);
      }
    }
  }

  protected CommunityManager getCommunityManager() {
    return communityManager;
  }

  protected String getAgentName() {
    return agentName;
  }

  /**
   * Lists all communities in White pages.
   * @return  Collection of community names
   */
  public Collection listAllCommunities() {
    return cache.listAll();
  }

  public void listAllCommunities(CommunityResponseListener crl) {
    crl.getResponse(new CommunityResponseImpl(CommunityResponse.SUCCESS,
                                              listAllCommunities()));
  }

  public void findCommunity(String                communityName,
                            FindCommunityCallback findMgrCB,
                            long                  timeout) {
    if (communityManager != null) {
      communityManager.findManager(communityName, findMgrCB);
    } else {
      findMgrCB.execute(null);
    }
  }

  class MyCommunityUpdateListener implements CommunityUpdateListener {

  public void updateCommunity(Community community) {
    log.debug("updateCommunity: community=" + community);
    cache.update(community);
  }

  public void removeCommunity(Community community) {
    cache.remove(community.getName());
  }

}


}
