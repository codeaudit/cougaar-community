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

import org.cougaar.core.service.community.Agent;
import org.cougaar.core.service.community.CommunityChangeEvent;
import org.cougaar.core.service.community.CommunityChangeListener;
import org.cougaar.core.service.community.Community;
import org.cougaar.core.service.community.CommunityResponse;
import org.cougaar.core.service.community.CommunityResponseListener;
import org.cougaar.core.service.community.CommunityService;
import org.cougaar.core.service.community.Entity;

import org.cougaar.core.util.UID;

import org.cougaar.community.CommunityDescriptor;
import org.cougaar.community.RelayAdapter;
import org.cougaar.community.manager.CommunityManagerRequestImpl;
import org.cougaar.community.requests.*;
import org.cougaar.community.manager.CommunityManager;
import org.cougaar.community.manager.CommunityManagerRequest;

import java.util.*;
import java.net.URI;

import org.cougaar.core.relay.Relay;
import org.cougaar.core.agent.service.alarm.Alarm;
import org.cougaar.core.blackboard.IncrementalSubscription;
import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.plugin.ComponentPlugin;

import org.cougaar.core.service.AlarmService;
import org.cougaar.core.service.BlackboardService;
import org.cougaar.core.service.LoggingService;
import org.cougaar.core.service.UIDService;

import org.cougaar.core.mts.MessageAddress;

import org.cougaar.multicast.AttributeBasedAddress;

import org.cougaar.community.init.*;

import javax.naming.*;
import javax.naming.directory.*;

import org.cougaar.util.UnaryPredicate;

import org.cougaar.util.log.*;

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
  public static final long RELAY_TIMEOUT = 1 * 60 * 1000;

  // Defines how often the CommunityRequestQueue is checked for pending requests
  private static final long CMR_TIMER_INTERVAL = 10 * 1000;

  // Defines duration between updates from community manager before a cache
  // refresh is requested.
  public static final long CACHE_EXPIRATION = 10 * 60 * 1000;

  // Services used
  protected LoggingService logger;
  protected UIDService uidService;

  // Provides community manager capabilities to plugin
  private CommunityManager communityManager;

  // Queue of CommunityManagerRequests waiting to be sent to community manager
  private List communityManagerRequestQueue = new Vector();

  // List of communities that this agent is a member of
  private CommunityMemberships myCommunities;

  // Local cache of Community objects
  private CommunityCache cache;

  // CommunityManagerRequests that have been sent to manager but haven't been
  // responded to yet
  private Map pendingRequests = new HashMap();

  // Requests that require additional processing after the response is
  // obtained from community manager
  private Map communityDescriptorRequests = new HashMap();

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
    cache = new CommunityCache(agentId.toString() + ".pi");

    communityManager = new CommunityManager(agentId, blackboard,
                                            getBindingSite().getServiceBroker());

    logger.debug("didRehydrate=" + blackboard.didRehydrate() +
                 " cacheExpirationTimeout=" + CACHE_EXPIRATION);
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
    } else {
      myCommunities = new CommunityMemberships();
      blackboard.publishAdd(myCommunities);
      Collection startupCommunities = findMyStartupCommunities();
      //if (logger.isDebugEnabled()) {
      //  logger.debug("startupCommunities=" + startupCommunities);
      //}
      joinStartupCommunities(startupCommunities);
    }

    // Start timer to periodically check CommunityManagerRequestQueue
    getAlarmService().addRealTimeAlarm(new RequestTimer(CMR_TIMER_INTERVAL));

  }

  /**
   * Handle CommunityRequests, CommunityDescriptors, and CommunityManagerRequests.
   */
  public void execute() {

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

    // An added CommunityManagerRequest is forwarded to the local
    // CommunityManager instance for processing.
    Collection communityManagerRequests = communityManagerRequestSub.getAddedCollection();
    for (Iterator it = communityManagerRequests.iterator(); it.hasNext();) {
      processCommunityManagerRequest((CommunityManagerRequest)it.next());
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
    communityManagerRequests = communityManagerRequestSub.getChangedCollection();
    for (Iterator it = communityManagerRequests.iterator(); it.hasNext();) {
      CommunityManagerRequest cmr = (CommunityManagerRequest)it.next();
      if (cmr.getSource().equals(agentId)) {  // is this agent also the source?
        if (cmr.getResponse() != null) {      // if so, this must be a response
          processCommunityManagerResponse(cmr);  // to a CMR
        } else {
          processCommunityManagerRequest(cmr); // this shouldn't happen
        }
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
        if (content instanceof CommunityManagerRequest) {
          processCommunityManagerRequest((CommunityManagerRequest)content);
        } else if(content instanceof CommunityDescriptor){
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
        cmr.setResponse((CommunityResponse)ra.getResponse());
        processCommunityManagerResponse(cmr);
      }
    }
    communityRelays = communityRelaySub.getRemovedCollection();
    for (Iterator it = communityRelays.iterator(); it.hasNext();) {
      RelayAdapter ra = (RelayAdapter)it.next();
      if (ra.getTargets().contains(agentId)) {
        Object content = ra.getContent();
        if (content instanceof CommunityManagerRequest) {
          //processRemovedCommunityManagerRequest((CommunityManagerRequest)content);
        } else if(content instanceof CommunityDescriptor){
          processRemovedCommunityDescriptor((CommunityDescriptor)content);
        }
      }
    }

    // Get community requests published by local components.  Either process the
    // request using Community info in local cache or queue request
    // for appropriate community manager.
   Collection communityRequests = communityRequestSub.getAddedCollection();
   for (Iterator it = communityRequests.iterator(); it.hasNext();) {
      CommunityRequest cr = (CommunityRequest)it.next();
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
          blackboard.publishChange(gcd);
        } else { // not found locally, queue request for community manager
          if (gcd.getCommunityName() != null) {
            CommunityManagerRequest cmr =
              new CommunityManagerRequestImpl(agentId,
                                              gcd.getCommunityName(),
                                              CommunityManagerRequest.GET_COMMUNITY_DESCRIPTOR,
                                              null,
                                              null,
                                              uidService.nextUID());
            queueCommunityManagerRequest(new CmrQueueEntry(cmr,
                                                           gcd,
                                                           gcd.getTimeout()));
          }
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
            blackboard.publishChange(cc);
          } else { // see if community exists
            MessageAddress cmAddr = communityManager.findManager(cc.getCommunityName());
            if (cmAddr == null) {  // Community doesn't exist, create it
              community = new CommunityImpl(cc.getCommunityName());
              community.setAttributes(cc.getAttributes());
              communityManager.addCommunity(community);
            } else {  // community exists but we don't have a local copy
              CommunityResponseListener crl = new CommunityResponseListener() {
                public void getResponse(CommunityResponse resp) {
                  cc.setResponse(resp);
                  blackboard.publishChange(cc);
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
        queueCommunityManagerRequest(new CmrQueueEntry(cmr,
                                                       ma,
                                                       ma.getTimeout()));
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
        if (cmAddr == null) {  // Community doesn't exist, create
          CommunityImpl community = new CommunityImpl(jc.getCommunityName());
          community.setAttributes(jc.getCommunityAttributes());
          community.addEntity(entity);
          communityManager.addCommunity(community);
        }
        CommunityManagerRequest cmr =
            new CommunityManagerRequestImpl(agentId,
                                            jc.getCommunityName(),
                                            CommunityManagerRequest.JOIN,
                                            entity,
                                            null,
                                            uidService.nextUID());
        queueCommunityManagerRequest(new CmrQueueEntry(cmr,
                                                       jc,
                                                       jc.getTimeout()));
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
        queueCommunityManagerRequest(new CmrQueueEntry(cmr,
                                                       lc,
                                                       lc.getTimeout()));
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
          blackboard.publishChange(sr);
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
            blackboard.publishChange(sr);
          }
          else { // community not found locally, get copy from manager
            CommunityResponseListener crl = new CommunityResponseListener() {
              public void getResponse(CommunityResponse resp) {
                final Community comm = (Community) resp.getContent();
                final Set entities = cache.search(sr.getCommunityName(),
                    sr.getFilter(),
                    sr.getQualifier(),
                    sr.isRecursiveSearch());
                CommunityResponse srResp =
                    new CommunityResponseImpl(CommunityResponse.SUCCESS,
                                              entities);
                sr.setResponse(srResp);
                blackboard.publishChange(sr);
              }
            };
            getCommunityWithPostProcessing(sr.getCommunityName(), crl);
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
        cache.addListener(acl.getChangeListener());
        CommunityResponse aclResp =
          new CommunityResponseImpl(CommunityResponse.SUCCESS,
                                    cache.get(acl.getCommunityName()));
        acl.setResponse(aclResp);
        blackboard.publishChange(acl);
        ////////////////////////////////////////////////////
        // Remove community change listener
        ////////////////////////////////////////////////////
      } else if (cr instanceof RemoveChangeListener) {
        RemoveChangeListener rcl = (RemoveChangeListener)cr;
        cache.removeListener(rcl.getChangeListener());
        CommunityResponse rclResp =
          new CommunityResponseImpl(CommunityResponse.SUCCESS,
                                    cache.get(rcl.getCommunityName()));
        rcl.setResponse(rclResp);
        blackboard.publishChange(rcl);
      } else {
        logger.warn("Received unknown CommunityRequest - " + cr);
      }
    }
  }

  protected void processCommunityManagerRequest(CommunityManagerRequest cmr) {
    if (logger.isDebugEnabled()) {
      logger.debug("Received CommunityManagerRequest:" +
                   " source=" + cmr.getSource() +
                   " request=" + cmr.getRequestTypeAsString() +
                   " community=" + cmr.getCommunityName() +
                   " id=" + cmr.getUID());
    }
    communityManager.processRequest(cmr);
  }

  protected void processCommunityManagerResponse(CommunityManagerRequest cmr) {
    CommunityResponse resp = (CommunityResponse) cmr.getResponse();
    if (resp != null && resp.getStatus() != CommunityResponse.UNDEFINED) {
      if (logger.isDebugEnabled()) {
        String entityNames = "";
        if (resp.getContent()instanceof Community)
          entityNames = entityNames( ( (Community) resp.getContent()).
                                    getEntities());
        logger.debug("Received CommunityManagerRequest response:" +
                     " source=" + cmr.getSource() +
                     " request=" + cmr.getRequestTypeAsString() +
                     " community=" + cmr.getCommunityName() +
                     " entities=" + entityNames +
                     " response.status=" + resp.getStatusAsString() +
                     " id=" + cmr.getUID());
      }
      updateMemberships(cmr);
      // Notify requester and remove Source Relay
      CmrQueueEntry cqe = (CmrQueueEntry) pendingRequests.remove(cmr.getUID());
      if (cqe != null && cqe.cr != null) {
        cqe.cr.setResponse( (CommunityResponse) cmr.getResponse());
        blackboard.publishChange(cqe.cr);
      }
      deleteSourceRelay(cmr.getUID());
    } else {
      logger.warn("Invalid CommunityResponse: response:" +
                   " source=" + cmr.getSource() +
                   " request=" + cmr.getRequestTypeAsString() +
                   " community=" + cmr.getCommunityName() +
                   " response=" + resp +
                   " id=" + cmr.getUID());
    }
  }

  private void deleteSourceRelay(UID uid) {
    Collection relays = blackboard.query(communityManagerRequestRelayPredicate);
    for (Iterator it = relays.iterator(); it.hasNext();) {
      RelayAdapter ra = (RelayAdapter)it.next();
      if (ra.getUID().equals(uid)) {
        blackboard.publishRemove(ra);
        //logger.debug("Removing source relay: uid=" + uid);
        return;
      }
    }
    logger.info("Unable to remove source relay, UID not found: uid=" + uid);
  }

  protected void processNewCommunityDescriptor(CommunityDescriptor cd) {
    Community community = cd.getCommunity();
    if (logger.isDebugEnabled()) {
      logger.debug("Received added CommunityDescriptor" +
        " source=" + cd.getSource() +
        " target=" + agentId +
        " community=" + community.getName() +
        " entities=" + entityNames(community.getEntities()));
    }
    blackboard.publishAdd(community);
    getCommunityDescriptorsForDescendents(community);
    cache.communityChanged(new CommunityChangeEvent(community,
                                                    CommunityChangeEvent.ADD_COMMUNITY,
                                                    community.getName()));
    if (communityDescriptorRequests.containsKey(community.getName())) {
      List gcListeners = (List)communityDescriptorRequests.remove(community.getName());
      for (Iterator it1 = gcListeners.iterator(); it1.hasNext();) {
        GetCommunityEntry gce = (GetCommunityEntry)it1.next();
        if (gce.crl != null) gce.crl.getResponse((CommunityResponse)cd.getResponse());
        blackboard.publishRemove(gce.cr);
      }
    }
    validateCommunityDescriptor(cd);
  }

  protected void processChangedCommunityDescriptor(CommunityDescriptor cd) {
    Community community = cd.getCommunity();
    if (logger.isDebugEnabled()) {
      logger.debug("Received changed CommunityDescriptor" +
        " source=" + cd.getSource() +
        " target=" + agentId +
        " community=" + community.getName() +
        " type=" + CommunityChangeEvent.getChangeTypeAsString(cd.getChangeType()) +
        " whatChanged=" + cd.getWhatChanged() +
        " entities=" + entityNames(community.getEntities()));
    }
    blackboard.publishChange(community);
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
      List gcListeners = (List)communityDescriptorRequests.remove(community.getName());
      for (Iterator it1 = gcListeners.iterator(); it1.hasNext();) {
        GetCommunityEntry gce = (GetCommunityEntry)it1.next();
        if (gce.crl != null) gce.crl.getResponse((CommunityResponse)cd.getResponse());
        blackboard.publishRemove(gce.cr);
      }
    }
    validateCommunityDescriptor(cd);
  }

  protected void processRemovedCommunityDescriptor(CommunityDescriptor cd) {
    Community community = cd.getCommunity();
    if (logger.isDebugEnabled()) {
      logger.debug("Received removed CommunityDescriptor" +
                   " target=" + agentId +
                   " community=" + community.getName());
    }
    blackboard.publishRemove(community);
    // Remove community from cache
    cache.communityChanged(new CommunityChangeEvent(community,
                                                    CommunityChangeEvent.REMOVE_COMMUNITY,
                                                    community.getName()));
    // Re-submit join requests for entities we added
    Collection entitiesWeAdded = myCommunities.getEntities(cd.getName());
    for (Iterator it = entitiesWeAdded.iterator(); it.hasNext(); ) {
      Entity entityWeAdded = (Entity) it.next();
      // Check for pending join request
      if (!isCmrQueued(community.getName(), entityWeAdded.getName(),
                       CommunityManagerRequest.JOIN)) {
        CommunityManagerRequest cmr =
            new CommunityManagerRequestImpl(agentId,
                                            community.getName(),
                                            CommunityManagerRequest.JOIN,
                                            entityWeAdded,
                                            null,
                                            uidService.nextUID());
        queueCommunityManagerRequest(new CmrQueueEntry(cmr, null));
      }
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
      e.printStackTrace();
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

  /**
   * Checks an added/changed CommunityDescriptor sent from CommunityManager to
   * verify membership.  If a mismatch is detected its likely the
   * CommunityManager has restarted and has an out of date master.  In this case
   * resend an appropriate JOIN or LEAVE request to correct.
   * @param cd
   */
  private void validateCommunityDescriptor(CommunityDescriptor cd) {
    Community community = cd.getCommunity();
    // Verify that community contains all the Entities that we've added
    Collection entitiesWeAdded = myCommunities.getEntities(cd.getName());
    for (Iterator it = entitiesWeAdded.iterator(); it.hasNext(); ) {
      Entity entityWeAdded = (Entity) it.next();
      Entity entityFromCommunity = community.getEntity(entityWeAdded.getName());
      if (entityFromCommunity == null ||
          !entityWeAdded.getAttributes().equals(entityFromCommunity.
                                                getAttributes())) {
        // Check for pending leave request, if no leave request has been issued
        // submit a join request
        if (!isCmrQueued(community.getName(), entityWeAdded.getName(), CommunityManagerRequest.LEAVE)) {
          CommunityManagerRequest cmr =
              new CommunityManagerRequestImpl(agentId,
                                              community.getName(),
                                              CommunityManagerRequest.JOIN,
                                              entityWeAdded,
                                              null,
                                              uidService.nextUID());
          queueCommunityManagerRequest(new CmrQueueEntry(cmr, null));
          if (logger.isInfoEnabled()) {
            logger.info(
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
      if (cd.getCommunity().hasEntity(agentId.toString())) {
        if (!isCmrQueued(community.getName(), agentId.toString(),
                         CommunityManagerRequest.JOIN)) {
          CommunityManagerRequest cmr =
              new CommunityManagerRequestImpl(agentId,
                                              community.getName(),
                                              CommunityManagerRequest.LEAVE,
                                              new EntityImpl(agentId.toString()),
                                              null,
                                              uidService.nextUID());
          queueCommunityManagerRequest(new CmrQueueEntry(cmr, null));
          if (logger.isInfoEnabled()) {
            logger.info(
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
   * Update map containing my current community memberships.  This map is used
   * to verify added/changed CommunityDescriptors received be Community Manager.
   * @param CommunityManagerRequestescrip
   */
  private void updateMemberships(CommunityManagerRequest cmr) {
    Entity entity = cmr.getEntity();
    if (entity != null &&
        cmr.getResponse() != null &&
        ((CommunityResponse)cmr.getResponse()).getStatus() == CommunityResponse.SUCCESS) {
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
    if (entity != null) {
      Attributes attrs = entity.getAttributes();
      if (attrs != null) {
        Attribute attr = attrs.get("Role");
        return (attr != null && attr.contains("Member"));
      }
    }
    return false;
  }

  /**
   * Adds a request to community manager request queue.
   * @param cqe
   */
  private void queueCommunityManagerRequest(CmrQueueEntry cqe) {
    synchronized(communityManagerRequestQueue) {
      int toMultiplier = (++cqe.attempts > 10 ? 10 : cqe.attempts);
      long newTimeout = (new Date()).getTime() + (toMultiplier * RELAY_TIMEOUT);
      if (cqe.ttl < 0 || newTimeout < cqe.ttl) {
        cqe.timeout = newTimeout;
      } else {
        cqe.timeout = cqe.ttl;
      }
      communityManagerRequestQueue.add(cqe);
    }
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
      boolean cmrPending = isCmrQueued(nestedCommunityName, null,
                                       CommunityManagerRequest.GET_COMMUNITY_DESCRIPTOR);
      if (!inCache && !cmrPending) {
        if (logger.isDebugEnabled())
          logger.debug("GetCommunityDescriptor:" +
                         " community=" + nestedCommunityName +
                         " parent=" + community.getName());
        CommunityManagerRequest cmr =
          new CommunityManagerRequestImpl(agentId,
                                          nestedCommunityName,
                                          CommunityManagerRequest.GET_COMMUNITY_DESCRIPTOR,
                                          null,
                                          null,
                                          uidService.nextUID());
        queueCommunityManagerRequest(new CmrQueueEntry(cmr, null));
      }
    }
  }

  // Check CMR queue and pending request queue for a CMR curently being processed
  private boolean isCmrQueued(String communityName, String entityName, int type) {
    boolean cmrQueued = false;
    // Check for CMRs that have been queued but not yet sent to community manager
    for (Iterator it = communityManagerRequestQueue.iterator(); it.hasNext();) {
      CmrQueueEntry cqe = (CmrQueueEntry)it.next();
      CommunityManagerRequest cmr = (CommunityManagerRequest)cqe.request;
      Entity entity = cmr.getEntity();
      if (cmr.getCommunityName().equals(communityName) &&
          cmr.getRequestType() == type &&
          (entityName == null || (entity != null && entityName.equals(entity.getName())))) {
        cmrQueued = true;
        break;
      }
    }
    if (cmrQueued) return true;
    // Check for CMRs that have been sent to community manager but not yet responded tp
    Collection requests = pendingRequests.values();
    for (Iterator it1 = requests.iterator(); it1.hasNext(); ) {
      CmrQueueEntry cqe = (CmrQueueEntry)it1.next();
      RelayAdapter ra = (RelayAdapter)cqe.request;
      CommunityManagerRequest cmr = (CommunityManagerRequest)ra.getContent();
      Entity entity = cmr.getEntity();
      if (cmr.getCommunityName().equals(communityName) &&
          cmr.getRequestType() == type &&
          (entityName == null || (entity != null && entityName.equals(entity.getName())))) {
        cmrQueued = true;
        break;
      }
    }
    return cmrQueued;
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
      if (o instanceof RelayAdapter &&
          ((RelayAdapter)o).getContent() instanceof CommunityManagerRequest) {
        RelayAdapter ra = (RelayAdapter)o;
        CommunityManagerRequest cmr = (CommunityManagerRequest)ra.getContent();
        return true;
      } else {
        return false;
      }
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
    for (Iterator it = myStartupCommunities.iterator(); it.hasNext();) {
      CommunityConfig cc = (CommunityConfig)it.next();
      String communityName = cc.getName();
      EntityConfig ec = cc.getEntity(agentId.toString());
      MessageAddress cmAddr = communityManager.findManager(communityName);
      Set designatedManagers = getDesignatedManagers(cc);
      logger.debug("joinStartupCommunity:" +
                   " agent=" + agentId.toString() +
                   " community=" + communityName +
                   " designatedManagers=" + designatedManagers +
                   " canBeManager=" + canBeManager(ec));
      if (designatedManagers.contains(ec.getName()) ||
          (cmAddr == null && designatedManagers.isEmpty() && canBeManager(ec))) {
        // Create community
        communityManager.addCommunity(new CommunityImpl(communityName,
                                                        cc.getAttributes()));
        // Add any nested communities
        for (Iterator it1 = cc.getEntities().iterator(); it1.hasNext();) {
          EntityConfig ec1 = (EntityConfig)it1.next();
          Attributes attrs = ec1.getAttributes();
          if (attrs != null) {
            Attribute attr = attrs.get("EntityType");
            if (attr != null) {
              if (attr.contains("Community")) {
                Community nestedCommunity = new CommunityImpl(ec1.getName(), attrs);
                CommunityManagerRequest cmr =
                    new CommunityManagerRequestImpl(agentId,
                                                    communityName,
                                                    CommunityManagerRequest.JOIN,
                                                    nestedCommunity,
                                                    null,
                                                    uidService.nextUID());
                queueCommunityManagerRequest(new CmrQueueEntry(cmr, null));
              }
            }
          }

        }
      }
      // Submit join request to add self to community
      CommunityManagerRequest cmr =
          new CommunityManagerRequestImpl(agentId,
                                          communityName,
                                          CommunityManagerRequest.JOIN,
                                          new AgentImpl(ec.getName(),
          ec.getAttributes()),
                                          null,
                                          uidService.nextUID());
      queueCommunityManagerRequest(new CmrQueueEntry(cmr, null));
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
        if (attr != null && attr.size() > 0) { // is a manager specified?
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
    if (attrs == null) return false;  // no attributes, can't be a member or manager
    Attribute attr = attrs.get("Role");
    if (attr == null || !attr.contains("Member")) return false;
    // Agent is member, check value of "CanBeManager" attribute
      attr = attrs.get("CanBeManager");
    if (attr == null) {
      return true;  // default to true if attr not specified
    } else {
      return (!attr.contains("No") && !attr.contains("False"));
    }
  }

  /**
   * Generates compact represntation of community names
   */
  private String communityNames(Collection communities) {
    StringBuffer sb = new StringBuffer("[");
    for (Iterator it = communities.iterator(); it.hasNext();) {
      CommunityConfig cc = (CommunityConfig)it.next();
      sb.append(cc.getName() + (it.hasNext() ? "," : ""));
    }
    return(sb.append("]").toString());
  }

  // Converts a collection of Entities to a compact string representation of names
  private String entityNames(Collection entities) {
    StringBuffer sb = new StringBuffer("[");
    for (Iterator it = entities.iterator(); it.hasNext();) {
      Entity entity = (Entity)it.next();
      sb.append(entity.getName() + (it.hasNext() ? "," : ""));
    }
    return(sb.append("]").toString());
  }

  /**
   * Periodically check communityManagerRequestQueue and send queued messages as
   * community managers are found
   */
  private class RequestTimer implements Alarm {
    private long expirationTime = -1;
    private boolean expired = false;

    /**
     * Create an Alarm to go off in the milliseconds specified,.
     **/
    public RequestTimer (long delay) {
      expirationTime = delay + System.currentTimeMillis();
    }

    /**
     * Called  when clock-time >= getExpirationTime().
     **/
    public void expire () {
      if (!expired) {
        List requestsToPublish = new ArrayList();
        List responsesToPublish = new ArrayList();
        try {
          long now = (new Date()).getTime();
          // Check for pending requests that have timed out
          for (Iterator it = pendingRequests.values().iterator(); it.hasNext();) {
            CmrQueueEntry cqe = (CmrQueueEntry)it.next();
            RelayAdapter ra = (RelayAdapter)cqe.request;
            CommunityManagerRequest cmr = (CommunityManagerRequest)ra.getContent();
            if (cqe.timeout < now) { // Request timeout
              boolean retry = cqe.ttl < 0 || cqe.timeout < cqe.ttl;
              if (logger.isDebugEnabled()) {
                logger.debug("CommunityManagerRequest timeout:" +
                             " source=" + cmr.getSource() +
                             " request=" + cmr.getRequestTypeAsString() +
                             " community=" + cmr.getCommunityName() +
                             " cmrUid=" + cmr.getUID() +
                             " crUid=" + (cqe.cr != null ? cqe.cr.getUID().toString() : "nullCR") +
                             " targets=" + RelayAdapter.targetsToString(ra) +
                             " timeout=" +
                             (cqe.ttl < 0 ? cqe.ttl : cqe.ttl - cqe.createTime) +
                             " sentToMgr=true" +
                             " retrying=" + retry);
              }
              // remove request from BB and pending request queue
              blackboard.openTransaction();
              blackboard.publishRemove(ra);
              blackboard.closeTransaction();
              it.remove();
              if (retry) {
                cqe.request = ra.getContent();
                queueCommunityManagerRequest(cqe);
              } else {
                // Notify requester of timeout
                CommunityResponse resp = new CommunityResponseImpl(CommunityResponse.
                                                                   TIMEOUT, null);
                cqe.cr.setResponse(resp);
                responsesToPublish.add(cqe.cr);
              }
            }
          }
          synchronized (communityManagerRequestQueue) {
            for (Iterator it = communityManagerRequestQueue.iterator(); it.hasNext();) {
              CmrQueueEntry cqe = (CmrQueueEntry)it.next();
              CommunityManagerRequest cmr = (CommunityManagerRequest)cqe.request;
              String communityName = cmr.getCommunityName();
              MessageAddress cmAddr = communityManager.findManager(communityName);
              if (cmAddr != null) {  // found a community manager
                it.remove();
                RelayAdapter relay =
                  new RelayAdapter(cmr.getSource(), cmr, cmr.getUID());
                relay.addTarget(cmAddr);
                cqe.attempts = 0;
                cqe.request = relay;
                pendingRequests.put(cmr.getUID(), cqe);
                requestsToPublish.add(relay);
              } else {  // community (community manager) not found
                if (cqe.timeout < now) { // Request timeout
                  boolean retry = cqe.ttl < 0 || cqe.timeout < cqe.ttl;
                  if (logger.isDebugEnabled()) {
                    logger.debug("CommunityManagerRequest timeout:" +
                                 " source=" + cmr.getSource() +
                                 " request=" + cmr.getRequestTypeAsString() +
                                 " community=" + cmr.getCommunityName() +
                                 " cmrUid=" + cmr.getUID() +
                                 " crUid=" + (cqe.cr != null ? cqe.cr.getUID().toString() : "nullCR") +
                                 " timeout=" +
                                 (cqe.ttl < 0 ? cqe.ttl :
                                  cqe.ttl - cqe.createTime) +
                                 " sentToMgr=false" +
                                 " retrying=" + retry);
                  }
                  if (retry) {
                    // update retry ctr and timeout
                    int toMultiplier = (++cqe.attempts > 10 ? 10 : cqe.attempts);
                    long newTimeout = (new Date()).getTime() + (toMultiplier * RELAY_TIMEOUT);
                    cqe.timeout =  (cqe.ttl < 0 || newTimeout < cqe.ttl) ? newTimeout : cqe.ttl;
                  } else {
                    // remove from request queue
                    it.remove();
                    // Notify requester of timeout
                    CommunityResponse resp = new CommunityResponseImpl(CommunityResponse.
                                                                       TIMEOUT, null);
                    cqe.cr.setResponse(resp);
                    responsesToPublish.add(cqe.cr);
                  }
                }
              }
            }
          }
          // Request an update for cache entries that haven't been modified within
          // expiration period
          Collection expiredCacheEntries = cache.getExpired(CACHE_EXPIRATION);
          if (!expiredCacheEntries.isEmpty()) {
            for (Iterator it = expiredCacheEntries.iterator(); it.hasNext(); ) {
              String communityName = (String) it.next();
              if (!isCmrQueued(communityName, null,
                               CommunityManagerRequest.GET_COMMUNITY_DESCRIPTOR)) {
                if (logger.isDebugEnabled()) {
                  logger.debug("Expired cache entry:" +
                               " community=" + communityName +
                               " updateRequested=true");
                }
                CommunityManagerRequest cmr =
                    new CommunityManagerRequestImpl(agentId,
                    communityName,
                    CommunityManagerRequest.GET_COMMUNITY_DESCRIPTOR,
                    null,
                    null,
                    uidService.nextUID());
                queueCommunityManagerRequest(new CmrQueueEntry(cmr, null));
              }
            }
          }
          // Publish updated requests and responses outside of iterator loop
          // otherwise deadlock is created with "isCMRQueued" method
          blackboard.openTransaction();
          for (Iterator it = requestsToPublish.iterator(); it.hasNext();) {
            RelayAdapter relay = (RelayAdapter)it.next();
            CommunityManagerRequest cmr = (CommunityManagerRequest)relay.getContent();
            blackboard.publishAdd(relay);
            if (logger.isDebugEnabled()) {
              logger.debug("Sending CommunityManagerRequest:"+
               " source=" + cmr.getSource() +
               " request=" + cmr.getRequestTypeAsString() +
               " community=" + cmr.getCommunityName() +
               " entity=" + cmr.getEntity() +
               " uid=" + cmr.getUID() +
               " targets=" + RelayAdapter.targetsToString(relay));
            }
          }
          for (Iterator it = responsesToPublish.iterator(); it.hasNext();) {
            CommunityRequest cr = (CommunityRequest)it.next();
            blackboard.publishChange(cr);
            if (logger.isDebugEnabled()) {
              logger.debug("Updating CommunityRequest:"+
                           " source=" + agentId +
                           " request=" + cr.getRequestType() +
                           " community=" + cr.getCommunityName() +
                           " uid=" + cr.getUID() +
                           " response=" + cr.getResponse().getStatusAsString());
            }
          }
          blackboard.closeTransaction();
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          expired = true;
          getAlarmService().addRealTimeAlarm(new RequestTimer(CMR_TIMER_INTERVAL));
        }
      }
    }

    /** @return absolute time (in milliseconds) that the Alarm should
     * go off.
     **/
    public long getExpirationTime () { return expirationTime; }

    /** @return true IFF the alarm has expired or was canceled. **/
    public boolean hasExpired () { return expired; }

    /** can be called by a client to cancel the alarm.  May or may not remove
     * the alarm from the queue, but should prevent expire from doing anything.
     * @return false IF the the alarm has already expired or was already canceled.
     **/
    public synchronized boolean cancel () {
      if (!expired)
        return expired = true;
      return false;
    }
  }

  /**
   * Contains information associated with a CommunityManagerRequest that is
   * either waiting to be sent to remote manager or has been sent but hasn't
   * been responded to yet.
   */
  class CmrQueueEntry {
    Object request;       // CMR or RelayAdapter
    CommunityRequest cr;  // origina CommunityRequest from client
    long createTime;
    long timeout = 0;
    int attempts = 0;     // Number of times send has been attempted
    long ttl = -1;
    CmrQueueEntry(CommunityManagerRequest   req,
                  CommunityRequest          commReq) {
      this(req, commReq, -1);
    }
    CmrQueueEntry(CommunityManagerRequest   req,
                  CommunityRequest          commReq,
                  long                      timeout) {
      this.request = req;
      this.cr = commReq;
      this.createTime = (new Date()).getTime();
      if (timeout >= 0) ttl = createTime + timeout;
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