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

package org.cougaar.community;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;

import org.cougaar.community.init.CommunityConfig;
import org.cougaar.community.init.CommunityInitializerService;
import org.cougaar.community.init.EntityConfig;
import org.cougaar.community.manager.CommunityManager;
import org.cougaar.community.manager.CommunityManagerRequest;
import org.cougaar.community.manager.CommunityManagerRequestImpl;
import org.cougaar.community.requests.AddChangeListener;
import org.cougaar.community.requests.CommunityRequest;
import org.cougaar.community.requests.CreateCommunity;
import org.cougaar.community.requests.GetCommunity;
import org.cougaar.community.requests.JoinCommunity;
import org.cougaar.community.requests.LeaveCommunity;
import org.cougaar.community.requests.ListParentCommunities;
import org.cougaar.community.requests.ModifyAttributes;
import org.cougaar.community.requests.ReleaseCommunity;
import org.cougaar.community.requests.RemoveChangeListener;
import org.cougaar.community.requests.SearchCommunity;
import org.cougaar.core.agent.service.alarm.Alarm;
import org.cougaar.core.blackboard.IncrementalSubscription;
import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.plugin.ComponentPlugin;
import org.cougaar.core.service.LoggingService;
import org.cougaar.core.service.UIDService;
import org.cougaar.core.service.community.Community;
import org.cougaar.core.service.community.CommunityChangeEvent;
import org.cougaar.core.service.community.CommunityChangeListener;
import org.cougaar.core.service.community.CommunityResponse;
import org.cougaar.core.service.community.CommunityResponseListener;
import org.cougaar.core.service.community.CommunityService;
import org.cougaar.core.service.community.Entity;
import org.cougaar.core.util.UID;
import org.cougaar.util.UnaryPredicate;

/**
 * Provides community services for a Cougaar society.  Communities provide a way
 * of segmenting societies into distinct groups of entities organized for
 * specific purposes.  A typical use of communities is to define a group of
 * agents that will be used as a destination address for a Blackboard Relay.
 * In addition to providing a way to define simple groups of agents,
 * communities and their associated entities may also be associated with
 * attributes that can be used to select a specific community member or
 * subgroup.   For instance, given the community defined below, an Attribute
 * Based Address (ABA) could be defined to send a Relay to all members in
 * CommunityA using the "Role=Member" criteria.  Alternately, the ABA could be
 * addressed to those members having the "Role=Role-A" attribute in which case
 * only Agent-2 and Agent-3 would receive the Relay.
 */
public class CommunityPlugin extends ComponentPlugin {

  // Default timeout for a CommunityManagerRequest sent to a remote community
  // manager
  public static final String RELAY_TIMEOUT_PROPERTY = "org.cougaar.community.request.timeout";
  public static long RELAY_TIMEOUT = 3 * 60 * 1000;

  // Defines how often the CommunityRequestQueue is checked for pending requests
  public static final String CMR_TIMER_INTERVAL_PROPERTY = "org.cougaar.community.request.interval";
  private static long CMR_TIMER_INTERVAL = 30 * 1000;

  // Defines duration between updates from community manager before a cache
  // refresh is requested.
  public static final String CACHE_EXPIRATION_PROPERTY = "org.cougaar.community.cache.expiration";
  public static long CACHE_EXPIRATION = 10 * 60 * 1000;

  // Services used
  protected LoggingService logger;
  protected UIDService uidService;

  // Provides community manager capabilities to plugin
  private CommunityManager communityManager;

  // Queue of CommunityManagerRequests for community manager
  private CommunityManagerRequestQueue cmrQueue = new CommunityManagerRequestQueue();

  // List of communities that this agent is a member of
  private CommunityMemberships myCommunities;

  // Local cache of Community objects
  private CommunityCache cache;

  // Requests that require additional processing after the response is
  // obtained from community manager
  private Map communityDescriptorRequests = new HashMap();

  private Set myUIDs = new HashSet();

  // Timer
  private WakeAlarm wakeAlarm;


  protected CommunityService communityService;

  /**
   * Get required services and create subscriptions for CommunityRequests,
   * CommunityDescriptors, and CommunityManagerRequests.
   */
  protected void setupSubscriptions() {
    logger =
      (LoggingService)getBindingSite().getServiceBroker().getService(this, LoggingService.class, null);
    logger = org.cougaar.core.logging.LoggingServiceWithPrefix.add(logger, agentId + ": ");

    uidService =
      (UIDService) getBindingSite().getServiceBroker().getService(this, UIDService.class, null);

    communityService =
      (CommunityService) getBindingSite().getServiceBroker().getService(this, CommunityService.class, null);

    // Set configurable parameters from system properties
    setProperties();

    // Subscribe to CommunityManagerRequests
    communityManagerRequestSub =
      (IncrementalSubscription)blackboard.subscribe(communityManagerRequestPredicate);

    // Subscribe to CommunityRelays
    communityRelaySub =
      (IncrementalSubscription)blackboard.subscribe(communityRelayPredicate);

    // Subscribe to CommunityDescriptors
    communityDescriptorSub =
      (IncrementalSubscription)blackboard.subscribe(communityDescriptorPredicate);

    // Subscribe to CommunityRequests
    communityRequestSub =
      (IncrementalSubscription)blackboard.subscribe(communityRequestPredicate);

    // Initialize cache
    //cache = new CommunityCache(getServiceBroker(), agentId.toString() + ".pi");
    cache = CommunityCache.getCache(getServiceBroker());
    cache.addListener(new CommunityChangeListener() {
      public void communityChanged(CommunityChangeEvent cce) {
        if (blackboard != null) {
          applyUpdate(cce);
        }
      }
      public String getCommunityName() { return "ALL_COMMUNITIES"; }
    });

    communityManager = new CommunityManager(getBindingSite());

    if (blackboard.didRehydrate()) {
      Collection cms = blackboard.query(new UnaryPredicate() {
        public boolean execute (Object o) {
          return (o instanceof CommunityMemberships);
        }
      });
      if (cms.isEmpty()) {
        myCommunities = new CommunityMemberships();
        blackboard.publishAdd(myCommunities);
      } else {
        myCommunities = (CommunityMemberships)cms.iterator().next();
      }
      // Rebuild cache from persisted Community objects
      Collection communities = blackboard.query(new UnaryPredicate() {
        public boolean execute(Object o) {
          return (o instanceof Community);
        }
      });
      for (Iterator it = communities.iterator(); it.hasNext();) {
        Community community = (Community)it.next();
        cache.communityChanged(new CommunityChangeEvent(community,
                                                        CommunityChangeEvent.ADD_COMMUNITY,
                                                        community.getName()));
      }
      logger.debug("didRehydrate=true" +
                   " membershipsFound=" + cms.size() +
                   " communitiesFound=" + communities.size());
    } else {
      logger.debug("didRehydrate=false");
      myCommunities = new CommunityMemberships();
      blackboard.publishAdd(myCommunities);
      Collection startupCommunities = findMyStartupCommunities();
      if (logger.isDetailEnabled()) {
        //logger.detail("startupCommunities=" + startupCommunities);
      }
      joinStartupCommunities(startupCommunities);
    }

    // Start timer to periodically check CommunityManagerRequestQueue
    wakeAlarm = new WakeAlarm(System.currentTimeMillis() + CMR_TIMER_INTERVAL);
    alarmService.addRealTimeAlarm(wakeAlarm);

  }

  /**
   * Set configurable parameters from system properties
   */
  private void setProperties() {
    try {
      RELAY_TIMEOUT =
          Long.parseLong(System.getProperty(RELAY_TIMEOUT_PROPERTY,
                                            Long.toString(RELAY_TIMEOUT)));
      CMR_TIMER_INTERVAL =
          Long.parseLong(System.getProperty(CMR_TIMER_INTERVAL_PROPERTY,
                                            Long.toString(CMR_TIMER_INTERVAL)));
      CACHE_EXPIRATION =
          Long.parseLong(System.getProperty(CACHE_EXPIRATION_PROPERTY,
                                            Long.toString(CACHE_EXPIRATION)));
    }
    catch (Exception ex) {
      logger.warn("Error setting parameter from system property", ex);
    }
  }

  /**
   * Handle CommunityRequests, CommunityDescriptors, and CommunityManagerRequests.
   */
  public void execute() {

    // Publish all queued changes
    publishQueuedChanges();

    // Periodically check request queue and validate community memberships
    if ((wakeAlarm != null) &&
        ((wakeAlarm.hasExpired()))) {
      checkForBBOrphans();
      checkRequestQueues();
      validateCommunityMemberships();
      validateCompletedRequests();
    }

    // Gets CommunityDescriptors from community managers.  A CommunityDescriptor
    // is basically a wrapper around a Community instance that defines the
    // entities and attributes of a community.
    Collection communityDescriptors = communityDescriptorSub.getAddedCollection();
    for (Iterator it = communityDescriptors.iterator(); it.hasNext();) {
      processNewCommunityDescriptor((CommunityDescriptor)it.next());
    }
    communityDescriptors = communityDescriptorSub.getChangedCollection();
    for (Iterator it = communityDescriptors.iterator(); it.hasNext();) {
      processChangedCommunityDescriptor((CommunityDescriptor)it.next());
    }
    communityDescriptors = communityDescriptorSub.getRemovedCollection();
    for (Iterator it = communityDescriptors.iterator(); it.hasNext();) {
      processRemovedCommunityDescriptor((CommunityDescriptor)it.next());
    }

    // Changed CommunityManagerRequests are only significant if this agent
    // is also identified as the source.  This means that this agent is both the
    // requester and the community manager handling the request which
    // indicates that the changed CMR contains a response to a pending request.
    // In the typical case where the requester and community manager are
    // different agents the response to a CMR is received as a change to a
    // Relay.Source (RelayAdapter) which is handled later by the
    // CommunityRelayPredicate.  This additional logic is needed because an
    // agent cannot be both the source and target of a Relay.
    Collection communityManagerRequests = communityManagerRequestSub.getChangedCollection();
    for (Iterator it = communityManagerRequests.iterator(); it.hasNext(); ) {
      CommunityManagerRequest cmr = (CommunityManagerRequest) it.next();
      //logger.debug("CommunityResponse: CommunityManagerRequest=" + cmr);
      if (cmr.getResponse() != null) { // if so, this must be a response
        processCommunityManagerResponse(cmr, (CommunityResponse) cmr.getResponse());
        blackboard.publishRemove(cmr);
      }
    }

    // See if community relay contains this agent as a target.  If so, this
    // means that the same agent is both the sender of the relay and
    // an intended recipient.  Since the Relay.Target won't be extracted
    // and republished by the infrastructure we need to capture this when it's
    // initially published and process the content.  Since we use Relays for
    // both CommunityManagerRequests and CommunityDescriptors we need to
    // check the content type.
    Collection communityRelays = communityRelaySub.getAddedCollection();
    for (Iterator it = communityRelays.iterator(); it.hasNext();) {
      RelayAdapter ra = (RelayAdapter)it.next();
      if (ra.getTargets().contains(agentId)) {
        Object content = ra.getContent();
        if(content instanceof CommunityDescriptor){
          processNewCommunityDescriptor((CommunityDescriptor)content);
        }
      }
    }
    // A changed Relay means that either a CommunityDescriptor has changed or
    // a response has been received to a CommunityManagerRequest.
    communityRelays = communityRelaySub.getChangedCollection();
    for (Iterator it = communityRelays.iterator(); it.hasNext();) {
      RelayAdapter ra = (RelayAdapter)it.next();
      if (ra.getContent() instanceof CommunityDescriptor &&
          ra.getTargets().contains(agentId)) {
        processChangedCommunityDescriptor((CommunityDescriptor)ra.getContent());
      } else if (ra.getContent() instanceof CommunityManagerRequest) {
        // CommunityManagerRequest response
        CommunityManagerRequest cmr = (CommunityManagerRequest)ra.getContent();
        if (ra.getResponse() != null) {
          processCommunityManagerResponse(cmr, (CommunityResponse) ra.getResponse());
          blackboard.publishRemove(ra);
        }
      }
    }

    communityRelays = communityRelaySub.getRemovedCollection();
    for (Iterator it = communityRelays.iterator(); it.hasNext();) {
      RelayAdapter ra = (RelayAdapter)it.next();
      if (ra.getTargets().contains(agentId)) {
        Object content = ra.getContent();
        if (content instanceof CommunityManagerRequest) {
        } else if(content instanceof CommunityDescriptor){
          processRemovedCommunityDescriptor((CommunityDescriptor)content);
        }
      }
    }

    // Get community requests published by local components.
    Collection communityRequests = communityRequestSub.getAddedCollection();
    for (Iterator it = communityRequests.iterator(); it.hasNext(); ) {
      CommunityRequest cr = (CommunityRequest) it.next();
      processCommunityRequest(cr);
    }
  }

  /**
   * Check blackboard for orphaned requests/responses.  Occassionally subscriptions
   * don't get triggered when BB objects are added/changed resulting in orphaned
   * objects.  The problem is believed to be caused by some sort of BBClientComponent
   * interaction between the CommunityPlugin and CommunityServiceImpl.  This
   * problem will need to be addressed in Cougaar-11.  In the meantime the
   * solution is to periodically check the BB for these orphaned objects.
   */
  private void checkForBBOrphans() {
    // CommunityManagerRequest responses
    for (Iterator it = blackboard.query(communityManagerRequestPredicate).iterator(); it.hasNext(); ) {
      CommunityManagerRequest cmr = (CommunityManagerRequest) it.next();
      if (cmr.getResponse() != null) { // if so, this must be a response
        processCommunityManagerResponse(cmr, (CommunityResponse) cmr.getResponse());
        blackboard.publishRemove(cmr);
      }
    }
    // CommunityDescriptor updates
    for (Iterator it = blackboard.query(communityDescriptorPredicate).iterator(); it.hasNext();) {
      processChangedCommunityDescriptor((CommunityDescriptor)it.next());
    }
    // CommunityRequests
    for (Iterator it = blackboard.query(communityRequestPredicate).iterator(); it.hasNext(); ) {
      CommunityRequest cr = (CommunityRequest) it.next();
      if (cr.getResponse() == null && isNewRequest(cr)) {
        processCommunityRequest(cr);
      }
    }
    for (Iterator it = blackboard.query(communityRelayPredicate).iterator(); it.hasNext();) {
      RelayAdapter ra = (RelayAdapter)it.next();
      if (ra.getContent() instanceof CommunityDescriptor &&
          ra.getTargets().contains(agentId)) {
        processChangedCommunityDescriptor((CommunityDescriptor)ra.getContent());
      } else if (ra.getContent() instanceof CommunityManagerRequest) {
        // CommunityManagerRequest response
        CommunityManagerRequest cmr = (CommunityManagerRequest)ra.getContent();
        if (ra.getResponse() != null) {
          processCommunityManagerResponse(cmr, (CommunityResponse) ra.getResponse());
          blackboard.publishRemove(ra);
        }
      }
    }
  }

  /* Determines if a CommunityRequest on blackboard is new.  Used in conjunction
   with BB "query" method to mimic "getAddedCollection" capability. */
  private boolean isNewRequest(CommunityRequest cr) {
    CommunityManagerRequestQueue.Entry[] queueEntries =
        cmrQueue.get(CommunityManagerRequestQueue.ALL);
    for (int i = 0; i < queueEntries.length; i++) {
      if (queueEntries[i].cr != null && queueEntries[i].cr.getUID().equals(cr.getUID())) {
        return false;
      }
    }
    return true;
  }

  private void processCommunityRequest (CommunityRequest cr) {
    if (logger.isDebugEnabled()){
      logger.debug("Received CommunityRequest: " + cr);
    }
    ////////////////////////////////////////////////////
    // Get Community descriptor from community manager
    ////////////////////////////////////////////////////
    if (cr instanceof GetCommunity) {
      GetCommunity gcd = (GetCommunity)cr;
      // Check BB for local copy of CommunityDescriptor
      Community community = cache.get(gcd.getCommunityName());
      if (community != null) {  // found it locally, notify requestor
        CommunityResponse gcdResp =
          new CommunityResponseImpl(CommunityResponse.SUCCESS, community);
        gcd.setResponse(gcdResp);
        publishResponse(gcd);
      } else { // not found locally, queue request for community manager
        if (gcd.getCommunityName() != null) {
          CommunityManagerRequest cmr =
              new CommunityManagerRequestImpl(agentId,
                                              gcd.getCommunityName(),
                                              CommunityManagerRequest.
                                              GET_COMMUNITY_DESCRIPTOR,
                                              null,
                                              null,
                                              uidService.nextUID());
          queueCommunityManagerRequest(cmr, gcd, gcd.getTimeout());
        }
      }
      ////////////////////////////////////////////////////
      // Remove Community descriptor from cache
      ////////////////////////////////////////////////////
    } else if (cr instanceof ReleaseCommunity) {
      ReleaseCommunity rc = (ReleaseCommunity) cr;
      if (cache.contains(rc.getCommunityName()) &&
          !cache.get(rc.getCommunityName()).hasEntity(agentId.toString())) {
        CommunityManagerRequest cmr =
            new CommunityManagerRequestImpl(agentId,
                                            rc.getCommunityName(),
                                            CommunityManagerRequest.
                                            RELEASE_COMMUNITY_DESCRIPTOR,
                                            null,
                                            null,
                                            uidService.nextUID());
        queueCommunityManagerRequest(cmr, rc, rc.getTimeout());
      }
      ////////////////////////////////////////////////////
      // Create Community
      ////////////////////////////////////////////////////
    } else if (cr instanceof CreateCommunity) {
        final CreateCommunity cc = (CreateCommunity)cr;
        // Check BB for local copy of CommunityDescriptor
        Community community = cache.get(cc.getCommunityName());
        if (community != null) {  // found it locally, notify requestor
          CommunityResponse ccResp =
            new CommunityResponseImpl(CommunityResponse.SUCCESS, community);
          cc.setResponse(ccResp);
          publishResponse(cc);
        } else { // see if community exists
          MessageAddress cmAddr = communityManager.findManager(cc.getCommunityName());
          if (cmAddr == null) {  // Community doesn't exist, create it
            cmAddr = createCommunity(cc.getCommunityName(),
                                     cc.getAttributes());
            CommunityResponse ccResp =
              new CommunityResponseImpl(cmAddr != null
                                          ? CommunityResponse.SUCCESS
                                          : CommunityResponse.FAIL,
                                        community);
            cc.setResponse(ccResp);
            publishResponse(cc);
          } else {  // community exists but we don't have a local copy
            CommunityResponseListener crl = new CommunityResponseListener() {
              public void getResponse(CommunityResponse resp) {
                cc.setResponse(resp);
                publishResponse(cc);
              }
            };
            getCommunityWithPostProcessing(cc.getCommunityName(), crl);
          }
        }
      ////////////////////////////////////////////////////
      // Modify Community or Entity attributes
      ////////////////////////////////////////////////////
    } else if (cr instanceof ModifyAttributes) {
      ModifyAttributes ma = (ModifyAttributes)cr;
      Entity entity = new EntityImpl((ma.getEntityName() == null
                                      ? ma.getCommunityName()  // modify community attributes
                                      : ma.getEntityName()),   // modify entity attributes
                                     null);
      CommunityManagerRequest cmr =
         new CommunityManagerRequestImpl(agentId,
                                         ma.getCommunityName(),
                                         CommunityManagerRequest.MODIFY_ATTRIBUTES,
                                         entity,
                                         ma.getModifications(),
                                         uidService.nextUID());
      queueCommunityManagerRequest(cmr, ma, ma.getTimeout());
      ////////////////////////////////////////////////////
      // Join community
      ////////////////////////////////////////////////////
    } else if (cr instanceof JoinCommunity) {
      JoinCommunity jc = (JoinCommunity)cr;
      Entity entity = null;
      if (jc.getEntityType() == CommunityService.COMMUNITY) {
        entity = new CommunityImpl(jc.getEntityName(), jc.getEntityAttributes());
      } else {
        entity = new AgentImpl(jc.getEntityName(), jc.getEntityAttributes());
      }
      MessageAddress cmAddr = communityManager.findManager(jc.getCommunityName());
      if (communityManager.isWpsAvailable() && cmAddr == null &&
          jc.createIfNotFound()) {  // Community doesn't exist, create
        cmAddr = createCommunity(jc.getCommunityName(),
                                 jc.getCommunityAttributes());
      }
      CommunityManagerRequest cmr =
          new CommunityManagerRequestImpl(agentId,
                                          jc.getCommunityName(),
                                          CommunityManagerRequest.JOIN,
                                          entity,
                                          null,
                                          uidService.nextUID());
      updateMemberships(cmr);
      queueCommunityManagerRequest(cmr, jc, jc.getTimeout());
      ////////////////////////////////////////////////////
      // Leave community
      ////////////////////////////////////////////////////
    } else if (cr instanceof LeaveCommunity) {
      LeaveCommunity lc = (LeaveCommunity)cr;
      CommunityManagerRequest cmr =
        new CommunityManagerRequestImpl(agentId,
                                        lc.getCommunityName(),
                                        CommunityManagerRequest.LEAVE,
                                        new EntityImpl(lc.getEntityName()),
                                        null,
                                        uidService.nextUID());
      updateMemberships(cmr);
      queueCommunityManagerRequest(cmr, lc, lc.getTimeout());
      ////////////////////////////////////////////////////
      // Search community
      ////////////////////////////////////////////////////
    } else if (cr instanceof SearchCommunity) {
      final SearchCommunity sr = (SearchCommunity)cr;
      if (sr.getCommunityName() == null) { // search for a community
        Set matchingCommunities = cache.search(sr.getFilter());
        CommunityResponse srResp =
            new CommunityResponseImpl(CommunityResponse.SUCCESS, matchingCommunities);
        sr.setResponse(srResp);
        publishResponse(sr);
      } else { // search for entities in named community
        final Community community = (Community)cache.get(sr.getCommunityName());
        if (community != null) { // community to search found in cache
          Set entities = cache.search(sr.getCommunityName(),
                                      sr.getFilter(),
                                      sr.getQualifier(),
                                      sr.isRecursiveSearch());
          CommunityResponse srResp =
              new CommunityResponseImpl(CommunityResponse.SUCCESS, entities);
          sr.setResponse(srResp);
          publishResponse(sr);
        }
        else { // community not found locally, get copy from manager
          CommunityResponseListener crl = new CommunityResponseListener() {
            public void getResponse(CommunityResponse resp) {
              if (resp != null) {
                final Community comm = (Community)resp.getContent();
                final Set entities = cache.search(sr.getCommunityName(),
                                                  sr.getFilter(),
                                                  sr.getQualifier(),
                                                  sr.isRecursiveSearch());
                CommunityResponse srResp =
                    new CommunityResponseImpl(CommunityResponse.SUCCESS,
                                              entities);
                sr.setResponse(srResp);
                publishResponse(sr);
              }
            }
          };
          getCommunityWithPostProcessing(sr.getCommunityName(), crl);
        }
      }
      ////////////////////////////////////////////////////
      // List Parent Communities
      ////////////////////////////////////////////////////
    } else if (cr instanceof ListParentCommunities) {
      final ListParentCommunities lpc = (ListParentCommunities)cr;
      if (lpc.getCommunityName() == null) { // find agent parents
        List parents = cache.getAncestorNames(agentId.toString(), false);
        CommunityResponse resp =
            new CommunityResponseImpl(CommunityResponse.SUCCESS, parents);
        lpc.setResponse(resp);
        publishResponse(lpc);
      } else {
        if (cache.contains(lpc.getCommunityName())) {
          List parents = getAttrValues(cache.get(lpc.getCommunityName()).getAttributes(),
                                       "Parent");
          CommunityResponse resp =
              new CommunityResponseImpl(CommunityResponse.SUCCESS, parents);
          lpc.setResponse(resp);
          publishResponse(lpc);
        } else { // community not found locally, get copy from manager
          CommunityResponseListener crl = new CommunityResponseListener() {
            public void getResponse(CommunityResponse resp) {
              final Community comm = (Community) resp.getContent();
              List parents = getAttrValues(comm.getAttributes(), "Parent");
              resp =
                  new CommunityResponseImpl(CommunityResponse.SUCCESS,
                                            parents);
              lpc.setResponse(resp);
              publishResponse(lpc);
            }
          };
          getCommunityWithPostProcessing(lpc.getCommunityName(), crl);
        }
      }
      ////////////////////////////////////////////////////
      // Add community change listener
      ////////////////////////////////////////////////////
      // A CommunityChangeListener is used by the the
      // org.cougaar.core.service.community.CommunityService to trigger a
      // refresh on its local cache which in turn propagates a
      // CommunityChangeEvent to the Blackboard object indicating that ABA
      // resolutions associated with the specified community are no longer
      // valid.  Any component could register as a change listener alhough
      // the preferred method of community change notification would be to
      // subscribe to changes in the associated Community object.
    } else if (cr instanceof AddChangeListener) {
      AddChangeListener acl = (AddChangeListener)cr;
      communityService.addListener(acl.getChangeListener());
      CommunityResponse aclResp =
        new CommunityResponseImpl(CommunityResponse.SUCCESS,
                                  cache.get(acl.getCommunityName()));
      acl.setResponse(aclResp);
      publishResponse(acl);
      ////////////////////////////////////////////////////
      // Remove community change listener
      ////////////////////////////////////////////////////
    } else if (cr instanceof RemoveChangeListener) {
      RemoveChangeListener rcl = (RemoveChangeListener)cr;
      communityService.removeListener(rcl.getChangeListener());
      CommunityResponse rclResp =
        new CommunityResponseImpl(CommunityResponse.SUCCESS,
                                  cache.get(rcl.getCommunityName()));
      rcl.setResponse(rclResp);
      publishResponse(rcl);
    } else {
      logger.warn("Received unknown CommunityRequest - " + cr);
    }
  }

  private MessageAddress createCommunity(String communityName, Attributes attrs) {
    logger.debug("createCommunity: community=" + communityName);
    CommunityImpl community = new CommunityImpl(communityName);
    community.setAttributes(attrs);
    return communityManager.manageCommunity(community);
  }

  protected void updateSearchRequests(String communityName) {
    for (Iterator it = blackboard.query(communityRequestPredicate).iterator(); it.hasNext();) {
      CommunityRequest cr = (CommunityRequest)it.next();
      if (cr instanceof SearchCommunity) {
        SearchCommunity sc = (SearchCommunity)cr;
        if (cr.getCommunityName() == null) {
          sc.setResponse(new CommunityResponseImpl(CommunityResponse.SUCCESS,
                                                   cache.search(sc.getFilter())));
          publishResponse(sc);
      } else if (cr.getCommunityName().equals(communityName)) {
        Community community = (Community)cache.get(communityName);
        Set entities = cache.search(sc.getCommunityName(),
                                    sc.getFilter(),
                                    sc.getQualifier(),
                                    sc.isRecursiveSearch());
          CommunityResponse scResp =
              new CommunityResponseImpl(CommunityResponse.SUCCESS, entities);
          sc.setResponse(scResp);
          publishResponse(sc);
        }
      }
    }
  }

  /**
   * Verifies that requested attribute modifications have been applied.
   * @param attrs Attributes to be modified
   * @param mods  Requested modifications
   */
  private boolean attributeModsApplied(Attributes attrs, ModificationItem[] mods) {
    boolean result = true;
    if (attrs != null && mods != null) {
      for (int i = 0; i < mods.length && result == true; i++) {
        switch (mods[i].getModificationOp()) {
          case DirContext.ADD_ATTRIBUTE:
            Attribute tmpAttr = attrs.get(mods[i].getAttribute().getID());
            if (tmpAttr == null || !tmpAttr.equals(mods[i].getAttribute())) {
              result = false;
            }
            break;
          case DirContext.REPLACE_ATTRIBUTE:
            tmpAttr = attrs.get(mods[i].getAttribute().getID());
            if (tmpAttr == null || !tmpAttr.equals(mods[i].getAttribute())) {
              result = false;
            }
            break;
          case DirContext.REMOVE_ATTRIBUTE:
            if (attrs.get(mods[i].getAttribute().getID()) != null) {
              result = false;
            }
            break;
        }
      }
    }
    if (logger.isDetailEnabled()) {
      logger.detail("attributeModsApplied:" +
                   " attrs=" + attrsToString(attrs) +
                   " mods=" + modsToString(mods) +
                   " modsApplied=" + result);
    }
    return result;
  }

  /**
   * Generates stringified version of ModificationItem array.
   * @param mods attribute modifications
   */
  private String modsToString(ModificationItem[] mods) {
    if (mods == null) {
      return null;
    } else {
      StringBuffer sb = new StringBuffer("[");
      for (int i = 0; i < mods.length; i++) {
        sb.append(mods[i].toString());
        if (i < mods.length - 1)
          sb.append(",");
      }
      return sb.toString();
    }
  }

  /**
   * Creates a string representation of an Attribute set.
   */
  private String attrsToString(Attributes attrs) {
    if (attrs == null) {
      return null;
    } else {
      StringBuffer sb = new StringBuffer("[");
      try {
        for (NamingEnumeration enum = attrs.getAll(); enum.hasMore(); ) {
          Attribute attr = (Attribute) enum.next();
          sb.append(attr.getID() + "=(");
          for (NamingEnumeration enum1 = attr.getAll(); enum1.hasMore(); ) {
            sb.append( (String) enum1.next());
            if (enum1.hasMore())
              sb.append(",");
            else
              sb.append(")");
          }
          if (enum.hasMore())
            sb.append(",");
        }
        sb.append("]");
      }
      catch (NamingException ne) {}
      return sb.toString();
    }
  }

  /**
   * Compares a Community object with pending CommunityManagerRequests to
   * to determine if any requests have been completed.  A request is considered
   * complete if: 1) the Community object reflects the result of the request,
   * AND 2) a CommunityManagerResponse has been received.  When both
   * conditions are met the request is removed.
   */
  protected void validateCompletedRequests() {
    long now = System.currentTimeMillis();
    CommunityManagerRequestQueue.Entry[] queueEntries =
        cmrQueue.get(CommunityManagerRequestQueue.COMPLETED);
    for (int i = 0; i < queueEntries.length; i++) {
      CommunityManagerRequestQueue.Entry cqe = queueEntries[i];
      CommunityManagerRequest cmr = cqe.cmr;
      Community community = cache.get(cmr.getCommunityName());
      if (community == null) { // shouldn't occur
        // Resubmit request
        reQueueCommunityManagerRequest(cqe.cmr, cqe.cr, cqe.timeout);
      } else {
        logger.detail("validateCompletedRequest:" + cmr + " status=COMPLETED");
        if (cqe.timeout < now) { // timeout
          boolean retry = cqe.ttl < 0 || cqe.timeout < cqe.ttl;
          if (logger.isDebugEnabled()) {
            logger.debug("CommunityManagerRequest timeout:" + cmr +
                         " status=COMPLETED" +
                         " retrying=" + retry);
          }
          if (retry) {
            reQueueCommunityManagerRequest(cqe.cmr,
                                         cqe.cr,
                                         cqe.cr != null
                                         ? cqe.cr.getTimeout()
                                         : CommunityRequest.DEFAULT_TIMEOUT);
          } else {
            removeCommunityManagerRequest(cqe.cmr.getUID());
          }
        } else {
          CommunityResponse resp = (cqe.ra != null
                                    ? (CommunityResponse)cqe.ra.getResponse()
                                    : (CommunityResponse)cqe.cmr.getResponse());
          if (cmr.getCommunityName().equals(community.getName()) &&
              resp != null) {
            switch (cmr.getRequestType()) {
              case CommunityManagerRequest.GET_COMMUNITY_DESCRIPTOR:
                removeCommunityManagerRequest(cqe.cmr.getUID());
                break;
              case CommunityManagerRequest.MODIFY_ATTRIBUTES:
                Attributes attrs = null;
                if (cmr.getEntity() == null ||
                    cmr.getCommunityName().equals(cmr.getEntity().getName())) {
                  attrs = community.getAttributes();
                } else {
                  if (community.hasEntity(cmr.getEntity().getName())) {
                    attrs = community.getEntity(cmr.getEntity().getName()).
                        getAttributes();
                  }
                }
                if (attributeModsApplied(attrs, cmr.getAttributeModifications())) {
                  removeCommunityManagerRequest(cqe.cmr.getUID());
                }
                break;
              case CommunityManagerRequest.JOIN:
                if (isMember(community, cmr.getEntity().getName())) {
                  removeCommunityManagerRequest(cqe.cmr.getUID());
                }
                break;
              case CommunityManagerRequest.LEAVE:
                if (!isMember(community, cmr.getEntity().getName())) {
                  removeCommunityManagerRequest(cqe.cmr.getUID());
                }
                break;
              case CommunityManagerRequest.RELEASE_COMMUNITY_DESCRIPTOR:
                removeCommunityManagerRequest(cqe.cmr.getUID());
                break;
            }
          }
        }
      }
    }
  }

  protected void processCommunityManagerResponse(CommunityManagerRequest cmr, CommunityResponse resp) {
    CommunityManagerRequestQueue.Entry cqe = cmrQueue.get(cmr.getUID());
    if (resp != null && resp.getStatus() != CommunityResponse.UNDEFINED) {
      if (logger.isDebugEnabled()) {
        String entityNames = "";
        if (resp.getContent() != null) {
          if (resp.getContent()instanceof CommunityDescriptor) {
            CommunityDescriptor cd = (CommunityDescriptor)resp.getContent();
            entityNames = entityNames(cd.getCommunity().getEntities());
          }
        }
        logger.debug("Received CommunityManagerRequest response:" +
                     " source=" + cmr.getSource() +
                     " request=" + cmr.getRequestTypeAsString() +
                     " community=" + cmr.getCommunityName() +
                     //" entities=" + entityNames +
                     " response.status=" +
                     (resp != null ? resp.getStatusAsString() : null) +
                     " id=" + cmr.getUID() +
                     (cqe == null ? " cqe=null" : " cqe.cr=" + cqe.cr));
      }
      switch (resp.getStatus()) {
        case CommunityResponse.SUCCESS:
          updateCommunityDescriptor( (CommunityDescriptor) resp.getContent());
          if (cqe != null) {
            final String communityName = cmr.getCommunityName();
            cqe.status = CommunityManagerRequestQueue.COMPLETED;
            cqe.timeout = System.currentTimeMillis() + 60000;
            if (cqe.cr != null) {
              if (myUIDs.contains(cqe.cr.getUID())) {
                myUIDs.remove(cmr.getUID());
                blackboard.publishRemove(cqe.cr);
              }
              else {
                cqe.cr.setResponse(new CommunityResponseImpl(resp.SUCCESS,
                    ( (CommunityDescriptor) resp.getContent()).getCommunity()));
                publishResponse(cqe.cr);
                cqe.listenerNotified = true;
              }
            }
          }
          break;
        case CommunityResponse.TIMEOUT:
          if (cqe != null) {
            reQueueCommunityManagerRequest(cqe.cmr, cqe.cr, cqe.timeout);
          }
          break;
        case CommunityResponse.FAIL:
          removeCommunityManagerRequest(cmr.getUID());
          if (cqe != null && cqe.cr != null) {
            cqe.cr.setResponse(new CommunityResponseImpl(resp.FAIL, null));
            publishResponse(cqe.cr);
          }
          break;
      }
    }
  }

  protected void processNewCommunityDescriptor(CommunityDescriptor cd) {
    if (logger.isDebugEnabled()) {
      logger.debug("Received new CommunityDescriptor" +
                   " source=" + cd.getSource() +
                   " target=" + agentId +
                   " community=" + cd.getName() +
                   " type=" + (cd.getChangeType() < 0
                               ? "NO_CHANGE"
                               : CommunityChangeEvent.getChangeTypeAsString(cd.getChangeType())) +
                   " what=" + cd.getWhatChanged());
    }
    updateCommunityDescriptor(cd);
  }

  protected void processChangedCommunityDescriptor(CommunityDescriptor cd) {
    if (logger.isDebugEnabled()) {
      logger.debug("Received changed CommunityDescriptor" +
                   " source=" + cd.getSource() +
                   " target=" + agentId +
                   " community=" + cd.getName() +
                   " type=" + (cd.getChangeType() < 0
                               ? "NO_CHANGE"
                               : CommunityChangeEvent.getChangeTypeAsString(cd.getChangeType())) +
                   " what=" + cd.getWhatChanged());
    }
    updateCommunityDescriptor(cd);
  }

  private void updateCommunityDescriptor(CommunityDescriptor cd) {
    checkParentAttributes();
    Community community = cd.getCommunity();
    getCommunityDescriptorsForDescendents(community);
    if (cache.contains(community.getName())) {
        cache.communityChanged(new CommunityChangeEvent(community,
           cd.getChangeType(),
           cd.getWhatChanged()));
    } else {
        cache.communityChanged(new CommunityChangeEvent(community,
            CommunityChangeEvent.ADD_COMMUNITY,
            community.getName()));
    }
    if (communityDescriptorRequests.containsKey(community.getName())) {
      List gcListeners = (List) communityDescriptorRequests.remove(community.
          getName());
      for (Iterator it1 = gcListeners.iterator(); it1.hasNext(); ) {
        GetCommunityEntry gce = (GetCommunityEntry) it1.next();
        if (gce.crl != null)
          gce.crl.getResponse( (CommunityResponse) cd.getResponse());
        blackboard.publishRemove(gce.cr);
      }
    }
  }

  private void applyUpdate(CommunityChangeEvent cce) {
    //logger.debug("applyUpdate");
    Community community = cce.getCommunity();
    blackboard.openTransaction();
    validateCompletedRequests();
    updateSearchRequests(community.getName());
    validateCommunityContents(community);
    blackboard.closeTransaction();
  }

  /**
   * Remove Community from cache and blackboard.  The associated
   * CommunityDescriptor was likely removed as a result of a change in the
   * manager agent.  We need to resubmit any prior requests to new manager
   * to ensure community state is correct.
   * @param cd
   */
  protected void processRemovedCommunityDescriptor(CommunityDescriptor cd) {
    Community community = cd.getCommunity();
    if (logger.isDebugEnabled()) {
      logger.debug("Received removed CommunityDescriptor" +
                   " target=" + agentId +
                   " community=" + community.getName() +
                   " myCommunities=" + entityNames(myCommunities.getEntities(community.getName())));
    }
    // Remove community from cache and blackboard
    if (cache.contains(community.getName())) {
      //blackboard.publishRemove(cache.get(community.getName()));
      cache.communityChanged(new CommunityChangeEvent(community,
                                                      CommunityChangeEvent.REMOVE_COMMUNITY,
                                                      community.getName()));
    }
    // Re-submit request for community descriptor
    queueCommunityManagerRequest(
        new CommunityManagerRequestImpl(agentId,
                                        community.getName(),
                                        CommunityManagerRequest.GET_COMMUNITY_DESCRIPTOR,
                                        null,
                                        null,
                                        uidService.nextUID()), null, -1);

    // Re-submit join requests for entities we added
    Collection entitiesWeAdded = myCommunities.getEntities(cd.getName());
    for (Iterator it = entitiesWeAdded.iterator(); it.hasNext(); ) {
      Entity entityWeAdded = (Entity)it.next();
      // Check for pending join request
      queueCommunityManagerRequest(
          new CommunityManagerRequestImpl(agentId,
                                          community.getName(),
                                          CommunityManagerRequest.JOIN,
                                          entityWeAdded,
                                          null,
                                          uidService.nextUID()), null, -1);
    }
  }

  /**
   * Request a community descriptor from community manager and add a listener
   * to be called when the response is retrieved.
   */
  protected void getCommunityWithPostProcessing(String communityName, CommunityResponseListener crl) {
    GetCommunity gcd = new GetCommunity(communityName,
                                        uidService.nextUID(),
                                        -1);
    List gcdListeners = null;
    if (communityDescriptorRequests.containsKey(gcd.getCommunityName())) {
      gcdListeners = (List)communityDescriptorRequests.get(communityName);
    }
    else {
      gcdListeners = new Vector();
      communityDescriptorRequests.put(communityName, gcdListeners);
    }
    gcdListeners.add(new GetCommunityEntry(gcd, crl));
    blackboard.publishAdd(gcd);
  }

  /**
   * Builds a collection of CommunityConfig objects for all parent communities.
   * The community config information is retrieved from the
   * CommunityInitializerService.  The source of the community config data will
   * either be an XML file (named "communities.xml" in config path) if running
   * from .ini file based configuration or a database if running from CSMART.
   * @return Collection of CommunityConfig objects defining parent communities
   */
  private Collection findMyStartupCommunities() {
    Collection startupCommunities = new Vector();
    Collection communityConfigs = null;
    ServiceBroker sb = getBindingSite().getServiceBroker();
    CommunityInitializerService cis = (CommunityInitializerService)
      sb.getService(this, CommunityInitializerService.class, null);
    try {
      communityConfigs = cis.getCommunityDescriptions(agentId.toString());
    } catch (Exception e) {
      logger.warn("Unable to obtain community information for agent "+ agentId.toString());
      //e.printStackTrace();
    } finally {
      sb.releaseService(this, CommunityInitializerService.class, cis);
    }
    for (Iterator it = communityConfigs.iterator(); it.hasNext();) {
      CommunityConfig cc = (CommunityConfig)it.next();
      EntityConfig ec = cc.getEntity(agentId.toString());
      Attributes attrs = ec.getAttributes();
      Attribute roles = attrs.get("Role");
      if (roles != null && roles.contains("Member")) {
        startupCommunities.add(cc);
      }
    }
    return startupCommunities;
  }

  private UnaryPredicate communityPredicate = new UnaryPredicate() {
    public boolean execute (Object o) {
      return (o instanceof Community);
  }};

  /*private Community[] getCommunitiesFromBB(final String communityName) {
    Collection communitiesOnBB = blackboard.query(new UnaryPredicate() {
        public boolean execute (Object o) {
          if (o instanceof Community) {
            Community community = (Community)o;
            return (community.getName().equals(communityName));
          }
          return false;
        }
      });
    return (Community[])communitiesOnBB.toArray(new Community[0]);
  }*/

  /**
   * Checks cache to verify that all parent communities are found and accurately
   * reflect this agents memberships.
   */
  private void validateCommunityMemberships() {
    for (Iterator it = myCommunities.listCommunities().iterator(); it.hasNext(); ) {
      String communityName = (String)it.next();
      Community community = cache.get(communityName);
      if (community != null) {
        /*
        Community[] communitiesFromBB = getCommunitiesFromBB(communityName);
        if (communitiesFromBB.length != 1 || communitiesFromBB[0] != community) {
          logger.debug("Blackboard and cache inconsistent:" +
                       " communitiesOnBB=" + communitiesFromBB.length);
          // Remove community from cache and BB
          cache.communityChanged(new CommunityChangeEvent(community,
              CommunityChangeEvent.REMOVE_COMMUNITY,
              community.getName()));
          for (int i = 0; i < communitiesFromBB.length; i++) {
            blackboard.publishRemove(communitiesFromBB[i]);
          }
          // Resubmit join request
          rejoinCommunity(communityName, community.getEntity(agentId.toString()));
        } else { */
          validateCommunityContents(community);
        //}
      } else { // Community not found
        for (Iterator it1 = myCommunities.getEntities(communityName).iterator();
             it1.hasNext(); ) {
          Entity entityWeAdded = (Entity)it1.next();
          rejoinCommunity(communityName, entityWeAdded);
        }
      }
    }
  }

  private void rejoinCommunity(String communityName, Entity entity) {
    CommunityManagerRequest cmr =
        new CommunityManagerRequestImpl(agentId,
                                        communityName,
                                        CommunityManagerRequest.JOIN,
                                        entity,
                                        null,
                                        uidService.nextUID());
    if (!cmrQueue.contains(cmr)) {
      queueCommunityManagerRequest(cmr, null, -1);
      if (logger.isDebugEnabled()) {
        logger.debug(
            "Parent community inconsistent or not found, submitting join request to rectify:" +
            " source=" + agentId +
            " community=" + communityName +
            " entity=" + (entity != null ? entity.getName() : ""));
      }
    }
  }

  /**
   * Checks an added/changed CommunityDescriptor sent from CommunityManager to
   * verify membership.  If a mismatch is detected its likely the
   * CommunityManager has restarted and has an out of date master.  In this case
   * resend an appropriate JOIN or LEAVE request to correct.
   * @param cd
   */
  private void validateCommunityContents(Community community) {
    // Verify that community contains all the Entities that we've added
    Collection entitiesWeAdded = myCommunities.getEntities(community.getName());
    /*
         if (logger.isDebugEnabled()) {
      logger.debug("ValidateCommunityContent:" +
                   " community=" + community.getName() +
                   " entities=" + entityNames(entitiesWeAdded));
         }
     */
    for (Iterator it = entitiesWeAdded.iterator(); it.hasNext(); ) {
      Entity entityWeAdded = (Entity)it.next();
      Entity entityFromCommunity = community.getEntity(entityWeAdded.getName());
      boolean attrsEqual = entityFromCommunity == null ||
          entityWeAdded.getAttributes().equals(entityFromCommunity.
                                               getAttributes());
      if (entityFromCommunity == null || !attrsEqual) {
        // Check for pending leave request, if no leave request has been issued
        // submit a join request
        CommunityManagerRequest cmr =
            new CommunityManagerRequestImpl(agentId,
                                            community.getName(),
                                            CommunityManagerRequest.JOIN,
                                            entityWeAdded,
                                            null,
                                            uidService.nextUID());
        if (!cmrQueue.contains(cmr)) {
          queueCommunityManagerRequest(cmr, null, -1);
          if (logger.isDebugEnabled()) {
            logger.debug(
                "Detected inconsistent community, submitting join request to rectify:" +
                " source=" + cmr.getSource() +
                " community=" + cmr.getCommunityName() +
                " entity=" + entityWeAdded.getName());
          }
        }
      }
    }
    // Verify that community doesn't contain this agent if we're not a member
    if (!myCommunities.contains(community.getName(), agentId.toString())) {
      if (community.hasEntity(agentId.toString())) {
        CommunityManagerRequest cmr =
            new CommunityManagerRequestImpl(agentId,
                                            community.getName(),
                                            CommunityManagerRequest.LEAVE,
                                            new EntityImpl(agentId.toString()),
                                            null,
                                            uidService.nextUID());
        if (!cmrQueue.contains(cmr)) {
          queueCommunityManagerRequest(cmr, null, -1);
          if (logger.isDebugEnabled()) {
            logger.debug(
                "Detected inconsistent community, submitting leave request to rectify:" +
                " source=" + cmr.getSource() +
                " community=" + cmr.getCommunityName() +
                " entity=" + agentId.toString());
          }
        }
      }
    }
  }

  /**
   * Adds "Parent" attribute to a nested community.  Attribute is used to link
   * nested community to parent.
   * @param community
   */
  private void checkParentAttributes() {
    //logger.debug("checkParentAttributes");
    Set allCommunities = cache.listAll();
    for (Iterator it = allCommunities.iterator(); it.hasNext(); ) {
      Community community = cache.get((String)it.next());
      List parents = cache.getAncestorNames(community.getName(), false);
      if (!parents.isEmpty()) {
        Attribute parentAttr = community.getAttributes().get("Parent");
        if (parentAttr == null || !attrContainsAll(parentAttr, parents)) {
          ModificationItem mods[] = new ModificationItem[1];
          if (parentAttr == null) {
            parentAttr = new BasicAttribute("Parent");
            for (Iterator it1 = parents.iterator(); it1.hasNext(); ) {
              parentAttr.add(it1.next());
            }
            mods[0] = new ModificationItem(DirContext.ADD_ATTRIBUTE, parentAttr);
          } else {
            for (Iterator it1 = parents.iterator(); it1.hasNext(); ) {
              String parentName = (String) it1.next();
              if (!parentAttr.contains(parentName))
                parentAttr.add(parentName);
            }
            mods[0] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
                                           parentAttr);
          }
          logger.debug("addParentAttribute:" +
                       " community=" + community.getName() +
                       " parent(s)=" + parents);
          CommunityManagerRequest cmr =
              new CommunityManagerRequestImpl(agentId,
                                              community.getName(),
                                              CommunityManagerRequest.
                                              MODIFY_ATTRIBUTES,
                                              null,
                                              mods,
                                              uidService.nextUID());
          queueCommunityManagerRequest(cmr, null, -1);
        }
      }
    }
  }

  private boolean attrContainsAll(Attribute attr, List values) {
    boolean result = true;
    for (Iterator it = values.iterator(); it.hasNext();) {
      if (!attr.contains(it.next())) {
        result = false;
        break;
      }
    }
    if (logger.isDetailEnabled()) {
      StringBuffer sb = new StringBuffer("[");
      for (Iterator it = values.iterator(); it.hasNext();) {
        sb.append(it.next());
        if (it.hasNext()) sb.append(",");
      }
      sb.append("]");
      logger.detail("attrContainsAll: attr=" + attr + " values=" + sb + " result=" + result);
    }
    return result;
  }

  private List getAttrValues(Attributes attrs, String id) {
    List values = new ArrayList();
    if (attrs != null) {
      Attribute attr = attrs.get(id);
      if (attr != null) {
        try {
          for (NamingEnumeration enum = attr.getAll(); enum.hasMore();) {
            values.add(enum.next());
          }
        } catch (NamingException ne) {
          logger.error(ne.getMessage());
        }
      }
    }
    return values;
  }

  /**
   * Update map containing my current community memberships.  This map is used
   * to verify added/changed CommunityDescriptors received be Community Manager.
   * @param CommunityManagerRequestescrip
   */
  private void updateMemberships(CommunityManagerRequest cmr) {
    if (logger.isDetailEnabled()) {
      logger.detail("updateMemberships:" +
                   " community=" + cmr.getCommunityName() +
                   " entity=" + cmr.getEntity() +
                   " response=" + cmr.getResponse());
    }
    Entity entity = cmr.getEntity();
    if (entity != null) {
      String communityName = cmr.getCommunityName();
      switch(cmr.getRequestType()) {
        case CommunityManagerRequest.JOIN:
          if (!myCommunities.contains(communityName, entity.getName()))
            myCommunities.add(communityName, entity);
          break;
        case CommunityManagerRequest.LEAVE:
          if (myCommunities.contains(communityName, entity.getName()))
            myCommunities.remove(communityName, entity.getName());
          break;
      }
      blackboard.publishChange(myCommunities);
    }
  }

  /**
   * Determines if a entity is a member of specified community.
   * @param community
   * @param entityName
   * @return
   */
  private boolean isMember(Community community, String entityName) {
    Entity entity = community.getEntity(entityName);
    return (entity != null &&
            hasAttribute(entity.getAttributes(), "Role", "Member"));
  }

  /**
   * Adds a request to community manager request queue.
   * @param cmr CommunityManagerRequest
   * @param cr  CommunityRequest
   */
  private void queueCommunityManagerRequest(CommunityManagerRequest cmr,
                                            CommunityRequest cr,
                                            long timeout) {
    // Check for an existing equivalent request.  If found, remove prior
    // request if no listener waiting for response
    CommunityManagerRequestQueue.Entry existingRequest = cmrQueue.get(cmr);
    if (existingRequest != null && existingRequest.cr == null) {
      removeCommunityManagerRequest(existingRequest.cmr.getUID());
    }
    CommunityManagerRequestQueue.Entry cqe = cmrQueue.add(cmr, cr, timeout);
    if (logger.isDebugEnabled()) {
      logger.debug("queueCommunityManagerRequest: cmr=" + cmr + " cr=" + cr +
                   " duplicate=" + !cqe.cmr.getUID().equals(cmr.getUID()));
    }
    int toMultiplier = (++cqe.attempts > 10 ? 10 : cqe.attempts);
    long newTimeout = System.currentTimeMillis() + (toMultiplier * RELAY_TIMEOUT);
    if (cqe.ttl < 0 || newTimeout < cqe.ttl) {
      cqe.timeout = newTimeout;
    } else {
      cqe.timeout = cqe.ttl;
    }
  }

  /**
   * Re-queue CommunityManagerRequest.  Remove any prior requests from queue
   * and blackboard.  Resubmit request with new UID.
   */
  private void reQueueCommunityManagerRequest(CommunityManagerRequest cmr,
                                              CommunityRequest cr,
                                              long timeout) {
    removeCommunityManagerRequest(cmr.getUID());
    CommunityManagerRequest newCMR =
        new CommunityManagerRequestImpl(cmr.getSource(),
                                        cmr.getCommunityName(),
                                        cmr.getRequestType(),
                                        cmr.getEntity(),
                                        cmr.getAttributeModifications(),
                                        uidService.nextUID());
    queueCommunityManagerRequest(newCMR, cr, timeout);
  }

  /**
   * Checks for local community reference for nested communities.  If not found,
   * a request is queued for manager
   */
  private void getCommunityDescriptorsForDescendents(Community community) {
    Collection nestedCommunities = cache.getNestedCommunityNames(community);
    for (Iterator it = nestedCommunities.iterator(); it.hasNext(); ) {
      String nestedCommunityName = (String) it.next();
      boolean inCache = cache.contains(nestedCommunityName);
      boolean requestAlreadyQueued =
          cmrQueue.contains(nestedCommunityName,
                            CommunityManagerRequest.GET_COMMUNITY_DESCRIPTOR);
      if (!inCache && !requestAlreadyQueued) {
        if (logger.isDebugEnabled())
          logger.debug("GetCommunityDescriptor (for child):" +
                         " community=" + nestedCommunityName +
                         " parent=" + community.getName());
        CommunityManagerRequest cmr =
          new CommunityManagerRequestImpl(agentId,
                                          nestedCommunityName,
                                          CommunityManagerRequest.GET_COMMUNITY_DESCRIPTOR,
                                          null,
                                          null,
                                          uidService.nextUID());
        queueCommunityManagerRequest(cmr, null, -1);
      }
    }
  }

  /**
   * Selects CommunityDescriptors that are sent by remote community manager
   * agent.
   */
  private IncrementalSubscription communityDescriptorSub;
  private UnaryPredicate communityDescriptorPredicate = new UnaryPredicate() {
    public boolean execute (Object o) {
      return (o instanceof CommunityDescriptor);
  }};

  private IncrementalSubscription communityRelaySub;
  private UnaryPredicate communityRelayPredicate = new UnaryPredicate() {
    public boolean execute (Object o) {
      if (o instanceof RelayAdapter) {
        Object content = ((RelayAdapter)o).getContent();
        return (content instanceof CommunityDescriptor ||
                content instanceof CommunityManagerRequest);
      }
      return false;
   }};

  /**
   * Predicate used to select CommunityManagerRequests sent by remote
   * agents.
   */
  private IncrementalSubscription communityManagerRequestSub;
  private UnaryPredicate communityManagerRequestPredicate = new UnaryPredicate() {
    public boolean execute (Object o) {
      return (o instanceof CommunityManagerRequest);
  }};

  /**
   * Predicate used to select CommunityManagerRequest relays published locally.
   */
  private IncrementalSubscription communityManagerRequestRelaySub;
  private UnaryPredicate communityManagerRequestRelayPredicate = new UnaryPredicate() {
    public boolean execute (Object o) {
      return (o instanceof RelayAdapter &&
          ((RelayAdapter)o).getContent() instanceof CommunityManagerRequest);
  }};

  /**
   * Predicate for Community Requests that are published locally.  These
   * requests are used to create CommunityManagerRequest relays that are
   * forwarded to the appropriate community manager.
   */
  private IncrementalSubscription communityRequestSub;
  private UnaryPredicate communityRequestPredicate = new UnaryPredicate() {
    public boolean execute (Object o) {
      return (o instanceof CommunityRequest);
  }};

  /**
   * Join and/or create all parent communities.  If a community manager is found
   * a join request is sent.  If the community manager is not found this agent
   * will attempt to become the manager based on community attributes set in
   * community configuration.  The agent will assert the manager role if the
   * community attribute "Manager=" contains its name.  If this attribute is
   * not set the agent will also attempt to become the manager if the entity
   * attribute "CanBeManager=" is undefined or set to true.
   * @param myCommunities  List of CommunityConfig objects associated with
   *                       all parent communities
   */
  private void joinStartupCommunities(Collection myStartupCommunities) {
    for (Iterator it = myStartupCommunities.iterator(); it.hasNext(); ) {
      CommunityConfig cc = (CommunityConfig) it.next();
      String communityName = cc.getName();
      EntityConfig ec = cc.getEntity(agentId.toString());
      Set designatedManagers = getDesignatedManagers(cc);
      logger.debug("joinStartupCommunity :" +
                   " agent=" + agentId.toString() +
                   " community=" + communityName +
                   " designatedManagers=" + designatedManagers +
                   " canBeManager=" + canBeManager(ec));
      // Add any nested communities
      for (Iterator it1 = cc.getEntities().iterator(); it1.hasNext(); ) {
        EntityConfig ec1 = (EntityConfig) it1.next();
        Attributes attrs = ec1.getAttributes();
        if (attrs != null) {
          Attribute attr = attrs.get("EntityType");
          if (attr != null) {
            if (attr.contains("Community")) {
              Community nestedCommunity = new CommunityImpl(ec1.getName(), attrs);
              JoinCommunity jc = new JoinCommunity(communityName,
                                                   ec1.getName(),
                                                   CommunityService.COMMUNITY,
                                                   ec1.getAttributes(),
                                                   false,
                                                   null,
                                                   uidService.nextUID(),
                                                   -1);
              myUIDs.add(jc.getUID());
              blackboard.publishAdd(jc);
            }
          }
        }
      }
      // Submit join request to add self to community
      boolean createIfNotFound = (designatedManagers.contains(ec.getName()) ||
                                  (designatedManagers.isEmpty() &&
                                   canBeManager(ec)));
      JoinCommunity jc = new JoinCommunity(communityName,
                                           ec.getName(),
                                           CommunityService.AGENT,
                                           ec.getAttributes(),
                                           createIfNotFound,
                                           (createIfNotFound ? cc.getAttributes() : null),
                                           uidService.nextUID(),
                                           -1);
      myUIDs.add(jc.getUID());
      blackboard.publishAdd(jc);
    }
  }

  /**
   * Retrieves designated community manager(s) from community configuration.
   * @param cc Config data associated with parent community
   * @return Set of entity names
   */
  private Set getDesignatedManagers(CommunityConfig cc) {
    Set managers = new HashSet();
    try {
      Attributes attrs = cc.getAttributes();  // get community attributes
      if (attrs != null) {
        Attribute attr = attrs.get("CommunityManager");
        if (attr != null &&
            attr.size() > 0 &&
            ((String)attr.get()).trim().length() > 0) { // is a manager specified?
          for (NamingEnumeration ne = attr.getAll(); ne.hasMoreElements();) {
            managers.add(ne.next());
          }
        }
      }
    } catch (NamingException ne) {}
    return managers;
  }

  /**
   * Determines if specified agent can become a community manager.  A value
   * of true is returned if the agent is a member of community and the
   * attribute "CanBeManager=" is either undefined or is not equal to false.
   * @param ec Config data associated with agent
   * @return true if can be manager
   */
  private boolean canBeManager(EntityConfig ec) {
    Attributes attrs = ec.getAttributes();  // get agent attributes
    if (attrs == null)
      return false;  // no attributes, can't be a member or manager
    if (!hasAttribute(attrs, "Role", "Member") ||
        !hasAttribute(attrs, "EntityType", "Agent"))
      return false;
    Attribute attr = attrs.get("CanBeManager");
    if (attr == null) {
      return true;  // default to true if attr not specified
    } else {
      return (!attr.contains("No") && !attr.contains("False"));
    }
  }

  private boolean hasAttribute(Attributes attrs, String id, String value) {
    if (attrs != null) {
      Attribute attr = attrs.get(id);
      return (attr != null && attr.contains(value));
    }
    return false;
  }

  // Converts a collection of Entities to a compact string representation of names
  private String entityNames(Collection entities) {
    StringBuffer sb = new StringBuffer("[");
    for (Iterator it = entities.iterator(); it.hasNext();) {
      Entity entity = (Entity)it.next();
      if (entity != null) {
        sb.append(entity.getName() + (it.hasNext() ? "," : ""));
      }
    }
    return(sb.append("]").toString());
  }

  /**
   * Check request queues for CommunityManagerRequests waiting for community
   * manager to become available or for a response to a previously sent
   * request.
   */
  private void checkRequestQueues() {
    try {
      boolean wpsAvailable = communityManager.isWpsAvailable();
      if (cmrQueue.size() > 0 && logger.isDebugEnabled()) {
        logger.debug("cmrQueue(" + cmrQueue.size() + ")" + cmrQueue.toString() +
                     " wpsAvail=" + wpsAvailable);
      }
      // Check CommunityManagerRequests that have been sent but haven't
      // been responded to by manager.
      long now = System.currentTimeMillis();
      CommunityManagerRequestQueue.Entry[] queueEntries =
          cmrQueue.get(CommunityManagerRequestQueue.SENT);
      for (int i = 0; i < queueEntries.length; i++) {
        CommunityManagerRequestQueue.Entry cqe = queueEntries[i];
        String targets = null;
        if (cqe.ra != null) {
          targets = RelayAdapter.targetsToString(cqe.ra);
        } else {
          targets = agentId.toString();
        }
        // Verify that the agent to which the request was sent is the current
        // manager.  If not the request must be removed and resent to new
        // manager agent.
        MessageAddress cmAddr = communityManager.findManager(cqe.cmr.getCommunityName());
        if ((cqe.ra != null && agentId.equals(cmAddr)) ||                  // Manager changed to this agent
            (cqe.ra != null && !cqe.ra.getTargets().contains(cmAddr)) ||   // Remote manager changed
            (cqe.ra == null && !agentId.equals(cmAddr))) {                 // Manager changed from this agent
          // Manager has changed to this agent, remove request (relay) and resend
          logger.info("Resending CMR: manager=" + cmAddr +
                      " cmr=" + cqe.cmr + " " + cqe.ra);
          reQueueCommunityManagerRequest(cqe.cmr, cqe.cr, cqe.timeout);
        } else {
          // If the target address is correct, check for request timeout.
          if (cqe.timeout < now) { // Request timeout
            boolean retry = cqe.ttl < 0 || cqe.timeout < cqe.ttl;
            if (logger.isDebugEnabled()) {
              logger.debug("CommunityManagerRequest timeout:" +
                           " source=" + cqe.cmr.getSource() +
                           " request=" + cqe.cmr.getRequestTypeAsString() +
                           " community=" + cqe.cmr.getCommunityName() +
                           " cmrUid=" + cqe.cmr.getUID() +
                           " crUid=" + (cqe.cr != null
                                          ? cqe.cr.getUID().toString()
                                          : "nullCR") +
                           " targets=" + targets +
                           " timeout=" + (cqe.ttl < 0
                                            ? cqe.ttl
                                            :cqe.ttl - cqe.createTime) +
                           " sentToMgr=true" +
                           " retrying=" + retry);
            }
            if (retry) {
              reQueueCommunityManagerRequest(cqe.cmr, cqe.cr, cqe.timeout);
            } else {
              // remove request from BB and pending request queue
              removeCommunityManagerRequest(cqe.cmr.getUID());
              // Notify requester of timeout
              CommunityResponse resp = new CommunityResponseImpl(
                  CommunityResponse.TIMEOUT, null);
              cqe.cr.setResponse(resp);
              publishResponse(cqe.cr);
            }
          }
        }
      }

      // Check communityManagerRequestQueue for CMRs that are waiting for
      // a manager to become available.  If the manager is found in the WP
      // send the request.
      queueEntries = cmrQueue.get(CommunityManagerRequestQueue.WAITING);
      for (int i = 0; i < queueEntries.length; i++) {
        CommunityManagerRequestQueue.Entry cqe = queueEntries[i];
        String communityName = cqe.cmr.getCommunityName();
        MessageAddress cmAddr = communityManager.findManager(communityName);

        // If manager not found and request is JOIN with createIfNotFound
        // option, attempt to become manager
        if (wpsAvailable && cmAddr == null &&
            cqe.cmr.getRequestType() == CommunityManagerRequest.JOIN &&
            cqe.cr != null && ((JoinCommunity)cqe.cr).createIfNotFound()) {
          cmAddr = createCommunity(cqe.cmr.getCommunityName(),
                                   ((JoinCommunity)cqe.cr).
                                   getCommunityAttributes());
        }

        if (wpsAvailable && cmAddr != null) { // found a community manager
          Object req = null;
          if (cmAddr == agentId) {
            // This agent is manager, send regular request
            req = cqe.cmr;
          } else {
            // Send CMR to remote manager via Relay
            cqe.ra =
                new RelayAdapter(cqe.cmr.getSource(), cqe.cmr, cqe.cmr.getUID());
            cqe.ra.addTarget(cmAddr);
            cqe.attempts = 0;
            req = cqe.ra;
          }
          cqe.cmr.setSource(agentId);
          cqe.status = CommunityManagerRequestQueue.SENT;

          // Check for any existing requests on blackboard
          // There shouldn't be any (can cause a ClassCastException in
          // RelayLP).  Only check wheck when DEBUG is enabled
          if (logger.isDebugEnabled()) {
            Collection existingRequests = findExistingRequests(cqe.cmr.getUID());
            if (!existingRequests.isEmpty()) {
              logger.warn("Existing request found on blackboard: " + existingRequests);
            }
          }

          blackboard.publishAdd(req);
          if (logger.isDebugEnabled()) {
            logger.debug("PublishAdd CommunityManagerRequest:" + req);
          }
        } else { // community (community manager) not found
          if (cqe.timeout < now) { // Request timeout
            boolean retry = cqe.ttl < 0 || cqe.timeout < cqe.ttl;
            if (logger.isDebugEnabled()) {
              logger.debug("CommunityManagerRequest timeout:" +
                           " source=" + cqe.cmr.getSource() +
                           " request=" + cqe.cmr.getRequestTypeAsString() +
                           " community=" + cqe.cmr.getCommunityName() +
                           " cmrUid=" + cqe.cmr.getUID() +
                           " crUid=" +
                           (cqe.cr != null ? cqe.cr.getUID().toString() :
                            "nullCR") +
                           " timeout=" +
                           (cqe.ttl < 0 ? cqe.ttl :
                            cqe.ttl - cqe.createTime) +
                           " sentToMgr=false" +
                           " retrying=" + retry);
            }
            if (retry) {
              // update retry ctr and timeout
              int toMultiplier = (++cqe.attempts > 10 ? 10 : cqe.attempts);
              long newTimeout = System.currentTimeMillis() +
                  (toMultiplier * RELAY_TIMEOUT);
              cqe.timeout = (cqe.ttl < 0 || newTimeout < cqe.ttl) ?
                  newTimeout : cqe.ttl;
            } else {
              // remove from request queue
              removeCommunityManagerRequest(cqe.cmr.getUID());
              // Notify requester of timeout
              CommunityResponse resp = new CommunityResponseImpl(
                  CommunityResponse.TIMEOUT, null);
              cqe.cr.setResponse(resp);
              publishResponse(cqe.cr);
            }
          }
        }
      }

      // Request an update for cache entries that haven't been modified within
      // expiration period
      Collection expiredCacheEntries = cache.getExpired(CACHE_EXPIRATION);
      if (!expiredCacheEntries.isEmpty()) {
        for (Iterator it = expiredCacheEntries.iterator(); it.hasNext(); ) {
          String communityName = (String)it.next();
          if (logger.isDebugEnabled()) {
            logger.debug("Expired cache entry:" +
                          " community=" + communityName +
                          " updateRequested=true");
          }
          CommunityManagerRequest cmr =
              new CommunityManagerRequestImpl(agentId,
                                              communityName,
                                              CommunityManagerRequest.
                                              GET_COMMUNITY_DESCRIPTOR,
                                              null,
                                              null,
                                              uidService.nextUID());
          queueCommunityManagerRequest(cmr, null, -1);
        }
      }
    } catch (Exception ex) {
      logger.error(ex.getMessage(), ex);
    } finally {
      wakeAlarm = new WakeAlarm(System.currentTimeMillis() + CMR_TIMER_INTERVAL);
      alarmService.addRealTimeAlarm(wakeAlarm);
    }
  }

  /**
   * Find prior requests on blackboard with specified UID.
   * @param uid
   * @return
   */
  private Collection findExistingRequests(UID uid) {
    Collection found = new ArrayList();
    Collection requests = blackboard.query(communityManagerRequestPredicate);
    for (Iterator it = requests.iterator(); it.hasNext(); ) {
      CommunityManagerRequest cmr = (CommunityManagerRequest)it.next();
      if (uid.equals(cmr.getUID())) {
        found.add(cmr);
      }
    }
    requests = blackboard.query(communityManagerRequestRelayPredicate);
    for (Iterator it = requests.iterator(); it.hasNext(); ) {
      RelayAdapter ra = (RelayAdapter)it.next();
      CommunityManagerRequest cmr = (CommunityManagerRequest)ra.getContent();
      if (uid.equals(cmr.getUID())) {
        found.add(cmr);
      }
    }
    return found;
  }

  // Remove request artifacts
  private void removeCommunityManagerRequest(UID uid) {
    logger.debug("removeCommunityManagerRequest: uid=" + uid);
    CommunityManagerRequestQueue.Entry cqe = cmrQueue.remove(uid);
    for (Iterator it = findExistingRequests(uid).iterator(); it.hasNext();) {
      Object requestToRemove = it.next();
      CommunityResponse resp = null;
      if (requestToRemove instanceof CommunityManagerRequest) {
        resp = (CommunityResponse)((CommunityManagerRequest)requestToRemove).getResponse();
      } else if (requestToRemove instanceof RelayAdapter) {
        resp = (CommunityResponse)((RelayAdapter)requestToRemove).getResponse();
      }
      blackboard.publishRemove(requestToRemove);
      logger.debug("publishRemove:" + requestToRemove + " resp=" + resp);
    }
  }

  private void publishResponse(CommunityRequest cr) {
    queueChange(cr);
    if (logger.isDebugEnabled()) {
      logger.debug("Updating CommunityRequest:" +
                   " source=" + agentId +
                   " request=" + cr.getRequestType() +
                   " community=" + cr.getCommunityName() +
                   " uid=" + cr.getUID() +
                   " response=" +
                   cr.getResponse().getStatusAsString());
    }
  }

  private List changeQueue = new ArrayList();
  /**
   * Queue object for publishChange
   * @param pr
   */
  protected void queueChange(Object obj) {
    synchronized (changeQueue) {
      changeQueue.add(obj);
    }
    //blackboard.signalClientActivity();
  }

  /**
   * Process queued changes.
   */
  private void publishQueuedChanges() {
    int n;
    List l;
    synchronized (changeQueue) {
      n = changeQueue.size();
      if (n <= 0) {
        return;
      }
      l = new ArrayList(changeQueue);
      changeQueue.clear();
    }
    for (int i = 0; i < n; i++) {
      blackboard.publishChange(l.get(i));
    }
  }

  // Timer for periodically checking CommunityManagerRequest queue and
  // validating community membership.
  private class WakeAlarm implements Alarm {
    private long expiresAt;
    private boolean expired = false;
    public WakeAlarm (long expirationTime) { expiresAt = expirationTime; }
    public long getExpirationTime() { return expiresAt; }
    public synchronized void expire() {
      if (!expired) {
        expired = true;
        if (blackboard != null) blackboard.signalClientActivity();
      }
    }
    public boolean hasExpired() { return expired; }
    public synchronized boolean cancel() {
      boolean was = expired;
      expired = true;
      return was;
    }
  }

  /**
   * Contains information pertaining to a getCommunity request requiring
   * post processing that has been submitted but not yet completed
   */
  class GetCommunityEntry {
    CommunityRequest cr;
    CommunityResponseListener crl;
    GetCommunityEntry(CommunityRequest cr, CommunityResponseListener crl) {
      this.cr = cr;
      this.crl = crl;
    }
  }
}
