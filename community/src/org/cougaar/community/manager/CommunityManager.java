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
package org.cougaar.community.manager;

import org.cougaar.core.relay.Relay;

import org.cougaar.community.*;

import org.cougaar.core.service.community.CommunityChangeEvent;
import org.cougaar.core.service.community.CommunityResponse;

import org.cougaar.core.service.community.*;

import org.cougaar.core.component.ServiceBroker;

import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.mts.SimpleMessageAddress;

import org.cougaar.core.service.AgentIdentificationService;
import org.cougaar.core.service.BlackboardService;
import org.cougaar.core.service.AlarmService;
import org.cougaar.core.service.LoggingService;
import org.cougaar.core.service.SchedulerService;
import org.cougaar.core.service.UIDService;

import org.cougaar.core.service.wp.AddressEntry;
import org.cougaar.core.service.wp.WhitePagesService;
import org.cougaar.core.service.wp.Callback;
import org.cougaar.core.service.wp.Response;

import org.cougaar.core.blackboard.BlackboardClient;
import org.cougaar.core.blackboard.BlackboardClientComponent;
import org.cougaar.core.blackboard.IncrementalSubscription;
import org.cougaar.core.component.BindingSite;

import org.cougaar.core.component.ServiceAvailableListener;
import org.cougaar.core.component.ServiceAvailableEvent;

import org.cougaar.core.agent.service.alarm.Alarm;

import org.cougaar.util.UnaryPredicate;

import java.net.URI;
import java.net.URISyntaxException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.NamingException;
import javax.naming.NamingEnumeration;

/**
 * Manager for one or more communities.  Handles requests to join and leave a
 * community.  Disseminates community descriptors to community members
 * and other interested agents.
 */
public class CommunityManager extends BlackboardClientComponent {

  public static final String SEND_INTERVAL_PROPERTY = "org.cougaar.community.updateInterval";
  // Defines how long CommunityDescriptor updates should be aggregated before
  // sending to interested agents.
  private static long SEND_INTERVAL = 60 * 1000;

  // Defines TTL for community manager binding cache entry
  public static final String WPS_CACHE_EXPIRATION_PROPERTY = "org.cougaar.community.wps.cache.expiration";
  private static long CACHE_TTL = 5 * 60 * 1000;

  public static final String WPS_RETRY_INTERVAL_PROPERTY = "org.cougaar.community.wps.retry.interval";
  private static long WPS_RETRY_DELAY = 15 * 1000;

  // Defines frequency of White Pages read to verify that this agent is still
  // manager for community
  public static final String MANAGER_CHECK_INTERVAL_PROPERTY = "org.cougaar.community.manager.check.interval";
  private static long TIMER_INTERVAL = 60 * 1000;

  private LoggingService logger;
  private ServiceBroker serviceBroker;

  // Timers
  private WakeAlarm verifyMgrAlarm;

  private Set managedCommunities = Collections.synchronizedSet(new HashSet());
  private Map communities = Collections.synchronizedMap(new HashMap());

  // Queued WPS lookup requests to find community manager binding
  private Map managerLookupQueue = Collections.synchronizedMap(new HashMap());

  private int wpsAvailctr = -1; // Force delay to wait for wps to initialize
  private boolean wpsAvailable = false;

  private CommunityDistributer distributer;

  /**
   * Constructor
   * @param bs       BindingSite
   */
  public CommunityManager(BindingSite bs) {
    setBindingSite(bs);
    serviceBroker = getServiceBroker();
    setAgentIdentificationService(
        (AgentIdentificationService)serviceBroker.getService(this,
                                                  AgentIdentificationService.class, null));
    logger = (LoggingService)serviceBroker.getService(this, LoggingService.class, null);
    logger = org.cougaar.core.logging.LoggingServiceWithPrefix.add(logger,
        agentId + ": ");
    setSchedulerService(
        (SchedulerService)serviceBroker.getService(this, SchedulerService.class, null));
    setAlarmService((AlarmService)serviceBroker.getService(this, AlarmService.class, null));
    setBlackboardService(
        (BlackboardService)serviceBroker.getService(CommunityManager.this,
                                         BlackboardService.class, null));
    distributer = new CommunityDistributer(bs, SEND_INTERVAL, CommunityPlugin.CACHE_EXPIRATION, true);
    initialize();
    load();
    start();
    try {
      SEND_INTERVAL =
          Long.parseLong(System.getProperty(SEND_INTERVAL_PROPERTY, Long.toString(SEND_INTERVAL)));
      CACHE_TTL =
          Long.parseLong(System.getProperty(WPS_CACHE_EXPIRATION_PROPERTY, Long.toString(CACHE_TTL)));
      WPS_RETRY_DELAY =
          Long.parseLong(System.getProperty(WPS_RETRY_INTERVAL_PROPERTY, Long.toString(WPS_RETRY_DELAY)));
      TIMER_INTERVAL =
          Long.parseLong(System.getProperty(MANAGER_CHECK_INTERVAL_PROPERTY, Long.toString(TIMER_INTERVAL)));
    } catch (Exception ex) {
      logger.warn("Exception setting parameter from system property", ex);
    }
  }

  public void setupSubscriptions() {

    // Subscribe to CommunityManagerRequests
    communityManagerRequestSub =
      (IncrementalSubscription)blackboard.subscribe(communityManagerRequestPredicate);

    // Re-publish any CommunityDescriptor Relays found on BB
    //   If any are found we've rehydrated in which case the CommunityDescriptor
    //   may be out of date.  Clients are responsible for checking the
    //   accuracy of contents and resubmitting requests to correct.
    for (Iterator it = blackboard.query(communityDescriptorPredicate).iterator();
         it.hasNext(); ) {
      RelayAdapter ra = (RelayAdapter) it.next();
      CommunityDescriptor cd = (CommunityDescriptor) ra.getContent();
      logger.info("Found CommunityDescriptor Relay: community=" + cd.getName());
      communities.put(cd.getName(), cd.getCommunity());
      distributer.add(ra);
      assertCommunityManagerRole(cd.getName());
    }
    verifyMgrAlarm = new WakeAlarm(now() + TIMER_INTERVAL);
    alarmService.addRealTimeAlarm(verifyMgrAlarm);
  }

  public void execute() {

    // Process any WPS lookup requests that need to be retried
    if (!managerLookupQueue.isEmpty()) {
      long now = now();
      LookupQueueEntry queueEntries[] = getQueueEntries();
      for (int i = 0; i < queueEntries.length; i++) {
        if (queueEntries[i].nextRetry < now) {
          managerLookupQueue.remove(queueEntries[i].communityName);
          findManager(queueEntries[i].communityName, queueEntries[i].callback);
        }
      }
    }

    // On verifyMgrAlarm expiration check WPS binding to verify that
    // manager roles for this agent
    if ((verifyMgrAlarm != null) &&
        ((verifyMgrAlarm.hasExpired()))) {
      verifyManagerRole();
    }

    // Get CommunityManagerRequests sent by remote agents
    Collection communityManagerRequests = communityManagerRequestSub.
        getAddedCollection();
    for (Iterator it = communityManagerRequests.iterator(); it.hasNext(); ) {
      processRequest((CommunityManagerRequest)it.next());
    }
    communityManagerRequests = communityManagerRequestSub.
        getChangedCollection();
    for (Iterator it = communityManagerRequests.iterator(); it.hasNext(); ) {
      CommunityManagerRequest cmr = (CommunityManagerRequest)it.next();
      if (agentId.equals(cmr.getSource())) {
        if (cmr.getResponse() == null) {
          processRequest(cmr);
        }
      }
    }
  }

  private Collection priorCommunities() {
    Collection priorCommunities = new ArrayList();
    List l = new ArrayList();
    synchronized (communities) {
      l.addAll(communities.keySet());
    }
    for (Iterator it = l.iterator(); it.hasNext();) {
      String communityName = (String)it.next();
      if (!managedCommunities.contains(communityName)) {
        priorCommunities.add(communityName);
      }
    }
    return priorCommunities;
  }

  /**
   * Return copy of managerLookupQueue.
   * @return  Array of queue contents
   */
  private LookupQueueEntry[] getQueueEntries() {
    synchronized (managerLookupQueue) {
      return (LookupQueueEntry[]) managerLookupQueue.values().toArray(new LookupQueueEntry[0]);
    }
  }

  /**
   * Processes CommunityManagerRequests.
   * @param cmr CommunityManagerRequest
   */
  private void processRequest(CommunityManagerRequest cmr) {
    if (logger.isDebugEnabled()) {
      logger.debug("CommunityManagerRequest:" + cmr);
    }
    String communityName = cmr.getCommunityName();
    if (isManager(communityName)) {
      evaluateRequest(cmr);
    } else {
      if (logger.isDebugEnabled()) {
          logger.debug("Not community manager:" +
                       " community=" + cmr.getCommunityName() +
                       " source=" + cmr.getSource() +
                       " request=" + cmr.getRequestTypeAsString() +
                       " manager=" + findManager(communityName));
      }
      cmr.setResponse(new CommunityResponseImpl(CommunityResponse.TIMEOUT,
                                                cmr.getCommunityName()));
      // The response is going local agent in which case there won't
      // be a Relay.Source.  Set the source in the Relay.Target to null
      // to inhibit RelayLP attemting to find/update Relay.Source.
      if (agentId.equals(cmr.getSource())) {
        cmr.setSource(null);
      }
      if (logger.isDebugEnabled()) {
        logger.debug("PublishChange CommunityManagerRequest (response):" +
                     " target=" + cmr.getSource() +
                     " request=" + cmr.getRequestTypeAsString() +
                     " id=" + cmr.getUID() +
                     " response=" + cmr.getResponse());
      }
      blackboard.publishChange(cmr);
    }
  }

  // Ensure that all items required to manage community have been created
  private boolean isManager(String communityName) {
    return (managedCommunities.contains(communityName) &&
            communities.containsKey(communityName) &&
            distributer.contains(communityName));
  }

  private void evaluateRequest(CommunityManagerRequest cmr) {
    MessageAddress requester = cmr.getSource();
    Community community = (Community)communities.get(cmr.getCommunityName());
    boolean result = true;
    switch (cmr.getRequestType()) {
      case CommunityManagerRequest.JOIN:
        Entity entity = cmr.getEntity();
        if (entity != null) {
          String entitiesBeforeAdd = "";
          if (logger.isDetailEnabled()) {
            entitiesBeforeAdd = entityNames(community.getEntities());
          }
          community.addEntity(entity);
          if (logger.isDetailEnabled()) {
            logger.debug("Add entity: " +
                         " community=" + community.getName() +
                         " entity=" + entity.getName() +
                         " before=" + entitiesBeforeAdd +
                         " after=" +
                         entityNames(community.getEntities()));
          }
          if (entity instanceof Community) {
            // Add manager for nested community to Relay targets
            logger.debug("Adding nested community:" +
                        " parent=" + cmr.getCommunityName() +
                        " child=" + entity.getName());
            addNestedCommunity(cmr.getCommunityName(), entity.getName());
          }
          distributer.update(cmr.getCommunityName(), CommunityChangeEvent.ADD_ENTITY, entity.getName());
          distributer.addTargets(cmr.getCommunityName(), Collections.singleton(cmr.getSource()));
        } else {
          result = false;
        }
        break;
      case CommunityManagerRequest.LEAVE:
        if (community.hasEntity(requester.toString()) &&
            cmr.getEntity() != null &&
            community.hasEntity(cmr.getEntity().getName())) {
          String entitiesBeforeRemove = "";
          if (logger.isDetailEnabled()) {
            entitiesBeforeRemove = entityNames(community.getEntities());
          }
          community.removeEntity(cmr.getEntity().getName());
          if (logger.isDetailEnabled()) {
            logger.debug("Removing entity: " +
                         " community=" + community.getName() +
                         " entity=" + cmr.getEntity().getName() +
                         " before=" + entitiesBeforeRemove +
                         " after=" +
                         entityNames(community.getEntities()));
          }
          distributer.update(cmr.getCommunityName(), CommunityChangeEvent.REMOVE_ENTITY, cmr.getEntity().getName());
        } else {
          result = false;
        }
        break;
      case CommunityManagerRequest.GET_COMMUNITY_DESCRIPTOR:
        distributer.addTargets(cmr.getCommunityName(), Collections.singleton(cmr.getSource()));
        break;
      case CommunityManagerRequest.RELEASE_COMMUNITY_DESCRIPTOR:
        // Remove destination from relay targets if not a community member
        if (!community.getEntities().contains(cmr.getSource())) {
          distributer.removeTargets(cmr.getCommunityName(), Collections.singleton(cmr.getSource()));
        }
        break;
      case CommunityManagerRequest.MODIFY_ATTRIBUTES:
        if (cmr.getEntity() == null ||
            community.getName().equals(cmr.getEntity().getName())) {
          // modify community attributes
          Attributes attrs = community.getAttributes();
          if (logger.isDebugEnabled()) {
            logger.debug("Modifying community attributes: " +
                         " community=" + community.getName() +
                         " before=" + attrsToString(attrs));
          }
          applyAttrMods(attrs, cmr.getAttributeModifications());
          if (logger.isDebugEnabled()) {
            logger.debug("Modifying community attributes: " +
                         " community=" + community.getName() +
                         " after=" + attrsToString(attrs));
          }
          distributer.update(cmr.getCommunityName(), CommunityChangeEvent.
                           COMMUNITY_ATTRIBUTES_CHANGED, community.getName());
        } else {
          // modify attributes of a community entity
          entity = community.getEntity(cmr.getEntity().getName());
          if (entity != null) {
            Attributes attrs = entity.getAttributes();
            if (logger.isDebugEnabled()) {
              logger.debug("Modifying entity attributes: " +
                           " community=" + community.getName() +
                           " entity=" + cmr.getEntity().getName() +
                           " before=" + attrsToString(attrs));
            }
            applyAttrMods(attrs, cmr.getAttributeModifications());
            if (logger.isDebugEnabled()) {
              logger.debug("Modifying entity attributes: " +
                           " community=" + community.getName() +
                           " entity=" + cmr.getEntity().getName() +
                           " after=" + attrsToString(attrs));
            }
            distributer.update(cmr.getCommunityName(), CommunityChangeEvent.
                             ENTITY_ATTRIBUTES_CHANGED, entity.getName());
          }
        }
        break;
    }
    cmr.setResponse(new CommunityResponseImpl(result
                                                ? CommunityResponse.SUCCESS
                                                : CommunityResponse.FAIL,
                                              distributer.get(cmr.getCommunityName())));
    // The response is going local agent in which case there won't
    // be a Relay.Source.  Set the source in the Relay.Target to null
    // to inhibit RelayLP attemting to find/update Relay.Source.
    if (agentId.equals(cmr.getSource())) {
      cmr.setSource(null);
    }
    if (logger.isDebugEnabled()) {
      logger.debug("PublishChange CommunityManagerRequest (response):" +
                   " target=" + cmr.getSource() +
                   " request=" + cmr.getRequestTypeAsString() +
                   " id=" + cmr.getUID() +
                   " response=" + cmr.getResponse());
    }
    blackboard.publishChange(cmr);
  }

  /**
   * Apply attribute modifications.
   */
  private void applyAttrMods(Attributes attrs, ModificationItem[] mods) {
    for (int i = 0; i < mods.length; i++) {
      switch (mods[i].getModificationOp()) {
        case DirContext.ADD_ATTRIBUTE:
          attrs.put(mods[i].getAttribute());
          break;
        case DirContext.REPLACE_ATTRIBUTE:
          attrs.remove(mods[i].getAttribute().getID());
          attrs.put(mods[i].getAttribute());
          break;
        case DirContext.REMOVE_ATTRIBUTE:
          attrs.remove(mods[i].getAttribute().getID());
          break;
      }
    }
  }

  /**
   * Use white pages to find manager for named community.  If community
   * isn't found in WP a null value is returned and the request is sent
   * to WPS with callback to update local cache.
   * @param communityName
   * @return MessageAddress to agent registered as community manager
   */
  public MessageAddress findManager(final String communityName) {
    MessageAddress mgrAddr = null;
    if (communityName == null) return null;
    CacheEntry ce = (CacheEntry)communityManagerCache.get(communityName);
    if (ce != null && ce.expiration > now()) {
      mgrAddr = ce.communityManager;
    }
    if (logger.isDetailEnabled()) {
      logger.detail("findManager: community=" + communityName +
                    " manager=" + mgrAddr +
                    " inCache=" + (ce != null && ce.expiration > now()) +
                    " pendingQuery=" + pendingWpsQueries.contains(communityName));
    }
    if (!pendingWpsQueries.contains(communityName)) {
      Callback cb = new Callback() {
        public void execute(Response resp) {
          boolean isAvailable = resp.isAvailable();
          boolean isSuccess = resp.isSuccess();
          AddressEntry entry = null;
          String agentName = null;
          if (isAvailable && isSuccess) {
            entry = ((Response.Get)resp).getAddressEntry();
            wpsAvailable = (++wpsAvailctr > 0);
          }
          if (entry != null) {
            URI uri = entry.getURI();
            agentName = uri.getPath().substring(1);
          }
          if (logger.isDetailEnabled()) {
            logger.detail("findManager callback:" +
                          " community=" + communityName +
                          " resp.isAvailable=" + isAvailable +
                          " resp.isSuccess=" + isSuccess +
                          " entry=" + entry +
                          " agentName=" + agentName +
                          " wpsAvailCtr=" + wpsAvailctr);
          }
          if (isAvailable && isSuccess && agentName != null) {
            MessageAddress addr = MessageAddress.getMessageAddress(agentName);
            communityManagerCache.put(communityName,
                                      new CacheEntry(addr, now() + CACHE_TTL));
            pendingWpsQueries.remove(communityName);
          } else {
            // Not found, retry later
            managerLookupQueue.put(communityName, new LookupQueueEntry(communityName, this,
                now() + WPS_RETRY_DELAY));
            blackboard.signalClientActivity();
          }
        }
      };
      pendingWpsQueries.add(communityName);
      return findManager(communityName, cb);
    } else {
      blackboard.signalClientActivity();
    }
    return mgrAddr;
  }


  /**
   * Use white pages to find manager for named community.  If mapping is found
   * in local cache the managers address is returned immediately.  If not, a
   * null is returned and a request is submitted to WPS with user supplied
   * callback.
   * @param communityName
   * @param cb  User specified callback to be invoked by WPS
   * @return MessageAddress to agent registered as community manager
   */
  public MessageAddress findManager(String communityName, Callback cb) {
    MessageAddress ret = null;
    logger.detail("findManager: community=" + communityName);
    if (communityName != null) {
      CacheEntry ce = (CacheEntry)communityManagerCache.get(communityName);
      if (ce != null && ce.expiration > now()) {
        ret = ce.communityManager;
      }
      WhitePagesService wps = (WhitePagesService)
          serviceBroker.getService(this, WhitePagesService.class, null);
      try {
        if (logger.isDetailEnabled()) {
          logger.detail("wps.get: community=" + communityName);
        }
        wps.get(communityName + ".comm", "community", cb);
      } catch (Exception ex) {
        logger.error(ex.getMessage());
      } finally {
        serviceBroker.releaseService(this, WhitePagesService.class, wps);
      }
    }
    logger.detail("findManager: community=" + communityName + " manager=" + ret);
    return ret;
  }

  // Community manager bindings are cached for use by findManager method.
  class CacheEntry {
    MessageAddress communityManager;
    long           expiration;
    CacheEntry(MessageAddress cm, long exp) {
      communityManager = cm;
      expiration = exp;
    }
  }
  Map communityManagerCache = Collections.synchronizedMap(new HashMap());
  List pendingWpsQueries = Collections.synchronizedList(new ArrayList());

  public boolean isWpsAvailable() {
    return wpsAvailable;
  }

  /**
   * Return current time as a long.
   * @return  Current time
   */
  private long now() {
    return System.currentTimeMillis();
  }

  /** create a wp entry for white pages binding */
  private AddressEntry createManagerEntry(
      String communityName) throws Exception {
    URI uri = URI.create("agent:///"+agentId);
    AddressEntry entry =
      AddressEntry.getAddressEntry(communityName+".comm", "community", uri);
    return entry;
  }

  /**
   * Asserts community manager role by binding address to community name in
   * White Pages
   * @param communityName Community to manage
   */
  public void assertCommunityManagerRole(String communityName) {
    assertCommunityManagerRole(communityName, false);
  }

  /**
   * Asserts community manager role by binding address to community name in
   * White Pages
   * @param communityName Community to manage
   * @param override      If true any existing binding will be removed
   *                      and replaced with new
   */
  public void assertCommunityManagerRole(String communityName, boolean override) {
    if (communities.containsKey(communityName) &&
        !isManager(communityName)) {
      if (logger.isDetailEnabled()) {
        logger.detail("assertCommunityManagerRole: agent=" + agentId.toString() +
                      " community=" + communityName);
      }
      final WhitePagesService wps =
          (WhitePagesService) serviceBroker.getService(this, WhitePagesService.class, null);
      if (wps != null) {
        try {
          bindCommunityManager(wps, communityName, override);
        }
        catch (Throwable ex) {
          logger.warn("Unable to (re)bind agent as community manager:" +
                      " error=" + ex +
                      " agent=" + agentId +
                      " community=" + communityName);
        }
        finally {
          serviceBroker.releaseService(this, WhitePagesService.class, wps);
        }
      }
    }
  }

  private void bindCommunityManager(final WhitePagesService wps,
                                    final String communityName,
                                    final boolean override) throws Exception {
    final AddressEntry communityAE = createManagerEntry(communityName);
    Callback cb = new Callback() {
      public void execute(Response resp) {
        Response.Bind bindResp = (Response.Bind) resp;
        if (resp.isAvailable()) {
          if (logger.isDetailEnabled())
            logger.detail("bind: " +
                         " success=" + resp.isSuccess() +
                         " didBind=" + bindResp.didBind());
          if (resp.isSuccess()) {
            if (bindResp.didBind()) {
              distributer.add((Community)communities.get(communityName), Collections.singleton(agentId));
              communityManagerCache.put(communityName,
                                        new CacheEntry(agentId, now() + CACHE_TTL));
              managedCommunities.add(communityName);
              logger.debug("Managing community " + communityName);
            } else {
              if (logger.isDetailEnabled())
                logger.detail(
                  "Unable to bind agent as community manager:" +
                  " agent=" + agentId +
                  " community=" + communityName +
                  " entry=" + communityAE +
                  " attemptingRebind=" + override);
              if (override) {
                rebindCommunityManager(wps, communityAE, communityName);
              }
            }
            resp.removeCallback(this);
          }
        }
      }
    };
    wps.bind(communityAE, cb);
  }

  private void rebindCommunityManager(WhitePagesService wps,
                                      AddressEntry ae,
                                      final String communityName) {
    Callback cb = new Callback() {
      public void execute(Response resp) {
        Response.Bind bindResp = (Response.Bind) resp;
        if (resp.isAvailable()) {
          if (logger.isDetailEnabled())
            logger.detail("rebind: " +
                        " success=" + resp.isSuccess() +
                        " didBind=" + bindResp.didBind());
          if (resp.isSuccess() && bindResp.didBind()) {
            managedCommunities.add(communityName);
            communityManagerCache.put(communityName,
                                      new CacheEntry(agentId, now() + CACHE_TTL));
            logger.debug("Managing community (rebind)" + communityName);
          } else {
            if (logger.isDetailEnabled())
              logger.detail("Unable to rebind agent as community manager:" +
                           " agent=" + agentId +
                           " community=" + communityName);
          }
          resp.removeCallback(this);
        }
      }
    };
    wps.rebind(ae, cb);
  }

  private List newCommunityDescriptors = new ArrayList();
  /**
   * Adds a community to be managed by this community manager.
   * @param community Community to manage
   * @return MessageAddress of manager indicating successful creation, or null
   *         on failure
   */
  public MessageAddress manageCommunity(Community community) {
    logger.debug("manageCommunity: community=" + community.getName());
    MessageAddress mgrAddr = null;
    String communityName = community.getName();
      communities.put(communityName, community);
      if (!isManager(communityName)) {
        assertCommunityManagerRole(communityName);
        mgrAddr = agentId;
        if (logger.isDebugEnabled()) {
          logger.debug("addCommunity:" +
                        " name=" + community.getName() +
                        " success=" + (mgrAddr != null));
        }
      } else {
        mgrAddr = agentId;
      }
    return mgrAddr;
  }

  /**
   * Adds the community manager for a nested community to target list.
   * @param parent  Parent communtiy name
   * @param child   Nested community name
   */
  protected void addNestedCommunity(final String parent, final String child) {
    if (isManager(parent)) {
      MessageAddress childAddr = findManager(child);
      if (childAddr != null) {
        if (!distributer.getTargets(parent).contains(childAddr)) {
          distributer.addTargets(parent, Collections.singleton(childAddr));
          logger.debug("addNestedCommunity:" +
                      " parent=" + parent +
                      " child=" + child +
                      " childManager=" + childAddr +
                      " source=fromCache");
        }
      } else {
        Callback cb = new Callback() {
          public void execute(Response resp) {
            boolean isAvailable = resp.isAvailable();
            boolean isSuccess = resp.isSuccess();
            AddressEntry entry = null;
            String agentName = null;
            if (isAvailable && isSuccess) {
              entry = ((Response.Get)resp).getAddressEntry();
            }
            if (entry != null) {
              URI uri = entry.getURI();
              agentName = uri.getPath().substring(1);
            }
            if (logger.isDetailEnabled()) {
              logger.detail("addNestedCommunity callback:" +
                            " community=" + child +
                            " resp.isAvailable=" + isAvailable +
                            " resp.isSuccess=" + isSuccess +
                            " entry=" + entry +
                            " agentName=" + agentName);
            }
            if (isAvailable && isSuccess && agentName != null) {
              MessageAddress addr = MessageAddress.getMessageAddress(agentName);
              communityManagerCache.put(child, new CacheEntry(addr, now() + CACHE_TTL));
              if (!distributer.getTargets(parent).contains(addr)) {
                distributer.addTargets(parent, Collections.singleton(addr));
                if (logger.isDetailEnabled()) {
                  logger.detail("addNestedCommunity:" +
                                " parent=" + parent +
                                " child=" + child +
                                " childManager=" + addr +
                                " source=fromCallback");
                }
              }
              resp.removeCallback(this);
            } else {
              // Not found, retry later
              managerLookupQueue.put(child, new LookupQueueEntry(child, this, now() + WPS_RETRY_DELAY));
            }
          }
        };
        findManager(child, cb);
      }
    }
  }

  // Converts a collection of Entities to a compact string representation of names
  private String entityNames(Collection entities) {
    StringBuffer sb = new StringBuffer("[");
    for (Iterator it = entities.iterator(); it.hasNext(); ) {
      Entity entity = (Entity) it.next();
      sb.append(entity.getName() + (it.hasNext() ? "," : ""));
    }
    return (sb.append("]").toString());
  }

  /**
   * Creates a string representation of an Attribute set.
   */
  protected String attrsToString(Attributes attrs) {
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

  private UnaryPredicate communityDescriptorPredicate = new UnaryPredicate() {
    public boolean execute(Object o) {
      return (o instanceof RelayAdapter &&
              ((RelayAdapter)o).getContent() instanceof CommunityDescriptor);
    }
  };

  /**
   * Check WPS binding to verify that the state of this community manager
   * is in sync with the WPS bindings.
   */
  private void verifyManagerRole() {
    Collection l = new ArrayList();
    synchronized (managedCommunities) {
      l.addAll(managedCommunities);
    }

    // See if WP binding lists this agent as manager for each name
    // in communityNames collection
    try {
      for (Iterator it = l.iterator(); it.hasNext(); ) {
        final String communityName = (String)it.next();
        Callback cb = new Callback() {
          public void execute(Response resp) {
            MessageAddress mgrAddr = null;
            if (resp.isAvailable() && resp.isSuccess()) {
              AddressEntry entry = ((Response.Get)resp).getAddressEntry();
              if (entry != null) {
                String agentName = entry.getURI().getPath().substring(1);
                mgrAddr = MessageAddress.getMessageAddress(agentName);
                communityManagerCache.put(communityName,
                                          new CacheEntry(mgrAddr, now() + CACHE_TTL));
              }
            }
            //logger.debug("verifyManagerRole: community=" + communityName +
            //             " mgrAddr=" + mgrAddr + " managedCommunities=" +
            //             managedCommunities + " priorCommunities=" + priorCommunities());
            if (isManager(communityName) && mgrAddr == null) {
              assertCommunityManagerRole(communityName); // reassert mgr role
            } else if (isManager(communityName) && !agentId.equals(mgrAddr)) {
              if (logger.isDebugEnabled()) {
                MessageAddress newMgr = findManager(communityName);
                logger.debug("No longer community manager:" +
                             " community=" + communityName +
                             " newManager=" + newMgr);
              }
              managedCommunities.remove(communityName);
              distributer.remove(communityName);
            } else if (!isManager(communityName) && agentId.equals(mgrAddr)) { // manager == this agent
              assertCommunityManagerRole(communityName);
            }
            blackboard.signalClientActivity();
            resp.removeCallback(this);
          }
        };
        WhitePagesService wps = (WhitePagesService)
            serviceBroker.getService(this, WhitePagesService.class, null);
        try {
           wps.get(communityName + ".comm", "community", cb);
        } catch (Exception ex) {
          logger.error(ex.getMessage());
        } finally {
          serviceBroker.releaseService(this, WhitePagesService.class, wps);
        }
      }
    } catch (Exception ex) {
      logger.error(ex.getMessage());
    }
    verifyMgrAlarm = new WakeAlarm(now() + TIMER_INTERVAL);
    alarmService.addRealTimeAlarm(verifyMgrAlarm);
  }

  // Timer for periodically checking identity of community manager.  Actual
  // processing is performed in execute() method.
  private class WakeAlarm implements Alarm {
    private long expiresAt;
    private boolean expired = false;
    public WakeAlarm (long expirationTime) {
      expiresAt = expirationTime;
    }
    public long getExpirationTime() {
      return expiresAt;
    }
    public synchronized void expire() {
      if (!expired) {
        expired = true;
        blackboard.signalClientActivity();
      }
    }
    public boolean hasExpired() {
      return expired;
    }
    public synchronized boolean cancel() {
      boolean was = expired;
      expired = true;
      return was;
    }
  }

  /**
   * Predicate used to select CommunityManagerRequests sent by remote
   * agents.
   */
  private IncrementalSubscription communityManagerRequestSub;
  private UnaryPredicate communityManagerRequestPredicate = new UnaryPredicate() {
    public boolean execute (Object o) {
      return (o instanceof CommunityManagerRequest);
  }};

  class LookupQueueEntry {
    String communityName;
    Callback callback;
    long nextRetry;
    LookupQueueEntry(String name, Callback cb, long when) {
      communityName = name;
      callback = cb;
      nextRetry = when;
    }
  }

  class Status {
    boolean value = false;
    Status (boolean v) { value = v; }
    void setValue(boolean v) { value = v; }
    boolean getValue() { return value; }
  }
}
