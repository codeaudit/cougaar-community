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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.naming.directory.ModificationItem;

import org.cougaar.community.manager.CommunityManager;
import org.cougaar.community.manager.DefaultCommunityManagerImpl;
import org.cougaar.community.manager.Request;
import org.cougaar.community.manager.RequestImpl;

import org.cougaar.core.blackboard.IncrementalSubscription;
import org.cougaar.core.component.BindingSite;
import org.cougaar.core.component.ServiceAvailableEvent;
import org.cougaar.core.component.ServiceAvailableListener;
import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.service.AgentIdentificationService;
import org.cougaar.core.service.LoggingService;
import org.cougaar.core.service.UIDService;
import org.cougaar.core.service.ThreadService;
import org.cougaar.core.agent.service.alarm.Alarm;

import org.cougaar.core.service.community.CommunityResponse;
import org.cougaar.core.service.community.CommunityResponseListener;
import org.cougaar.core.service.community.CommunityService;
import org.cougaar.core.service.community.Community;
import org.cougaar.core.service.community.Entity;
import org.cougaar.core.service.community.FindCommunityCallback;

import org.cougaar.community.CommunityResponseImpl;

import org.cougaar.core.service.wp.AddressEntry;
import org.cougaar.core.service.wp.Callback;
import org.cougaar.core.service.wp.Response;
import org.cougaar.core.service.wp.WhitePagesService;

import org.cougaar.core.util.UID;
import org.cougaar.util.UnaryPredicate;

/**
 * Default implementation of CommunityService that uses Blackboard Relays
 * for remote communication.  This includes sending requests to a remote
 * community manager and community discovery.
 **/
public class DefaultCommunityServiceImpl extends AbstractCommunityService
  implements CommunityService, java.io.Serializable, CommunityServiceConstants {

  protected BindingSite bindingSite;
  protected UIDService uidService;

  // This agent
  protected MessageAddress agentId;

  protected MyBlackboardClient myBlackboardClient;
  protected static Object cacheLock = new Object();
  protected CommunityRequestQueue requestQueue;

  protected long verifyMembershipsInterval = DEFAULT_VERIFY_MEMBERSHIPS_INTERVAL;

  /**
   * Constructor.
   * @param bs       Agents BindingSite
   */
  public DefaultCommunityServiceImpl(BindingSite bs) {
    this.bindingSite = bs;
    agentId = getAgentId();
    agentName = agentId.toString();
    log = (LoggingService)getServiceBroker().getService(this, LoggingService.class, null);
    initUidService();
    communityUpdateListener = new MyCommunityUpdateListener();
    myBlackboardClient = new MyBlackboardClient(bs);
    communityManager = getCommunityManager();
    synchronized (cacheLock) {
      if (cache == null) {
        ThreadService ts =
          (ThreadService)getServiceBroker().getService(this, ThreadService.class, null);
        cache = new CommunityCache(ts);
      }
    }
    requestQueue =  new CommunityRequestQueue(getServiceBroker(), this);
  }

  protected void getSystemProperties() {
    try {
      verifyMembershipsInterval =
          Long.parseLong(System.getProperty(VERIFY_MEMBERSHIPS_INTERVAL_PROPERTY,
                                            Long.toString(DEFAULT_VERIFY_MEMBERSHIPS_INTERVAL)));
    } catch (Exception ex) {
      if (log.isWarnEnabled()) {
        log.warn(agentName + ": Exception setting parameter from system property", ex);
      }
    }
  }

  protected MessageAddress getAgentId() {
    AgentIdentificationService ais =
        (AgentIdentificationService)getServiceBroker().getService(this,
        AgentIdentificationService.class, null);
    MessageAddress addr = ais.getMessageAddress();
    getServiceBroker().releaseService(this, AgentIdentificationService.class, ais);
    return addr;
  }

  protected ServiceBroker getServiceBroker() {
    return bindingSite.getServiceBroker();
  }

  /*
   * Get Unique identifier.
   */
  protected UID getUID() {
    return uidService != null ? uidService.nextUID() : null;
  }

  /**
   * Initialize UIDService using ServiceAvailableListener if service not
   * immediately available.
   */
  private void initUidService() {
    ServiceBroker sb = getServiceBroker();
    if (sb.hasService(org.cougaar.core.service.UIDService.class)) {
      uidService = (UIDService)sb.getService(this, UIDService.class, null);
    } else {
      sb.addServiceListener(new ServiceAvailableListener() {
        public void serviceAvailable(ServiceAvailableEvent sae) {
          if (sae.getService().equals(UIDService.class)) {
            uidService = (UIDService)getServiceBroker().getService(this, UIDService.class, null);
          }
        }
      });
    }
  }

  protected CommunityManager getCommunityManager() {
    return communityManager != null
        ? communityManager
        : new DefaultCommunityManagerImpl(bindingSite, this, communityUpdateListener);
  }

  /**
   * Send a request to manager of specified community.
   * @param communityName String
   * @param requestType int
   * @param entity Entity
   * @param attrMods ModificationItem[]
   * @param crl CommunityResponseListener
   * @param delay Defines how long to wait before processing request, a value
   *    of 0 or < 1 indicates that the request should be processed immediately
   */
  protected void queueCommunityRequest(final String communityName,
                                       final int requestType,
                                       final Entity entity,
                                       final ModificationItem[] attrMods,
                                       final CommunityResponseListener crl,
                                       final long delay) {
    if (log.isDebugEnabled()) {
      log.debug(agentName + ": queueCommunityRequest: " +
                " community=" + communityName +
                " type=" + requestType +
                " entity=" + entity +
                " attrMods=" + attrMods +
                " delay=" + delay);
    }
    if (delay > 0) {
      requestQueue.add(delay, communityName, requestType, entity, attrMods, crl);
    } else {
      sendCommunityRequest(communityName, requestType, entity, attrMods, crl);
    }
  }

  /**
   * Send request to manager.
   * @param communityName String
   * @param requestType int
   * @param entity Entity
   * @param attrMods ModificationItem[]
   * @param crl CommunityResponseListener
   */
  protected void sendCommunityRequest(final String communityName,
                                      final int requestType,
                                      final Entity entity,
                                      final ModificationItem[] attrMods,
                                      final CommunityResponseListener crl) {
    if (log.isDebugEnabled()) {
      log.debug(agentName + ": sendCommunityRequest: " +
                " community=" + communityName +
                " type=" + requestType +
                " entity=" + entity +
                " attrMods=" + attrMods);
    }
    FindCommunityCallback fmcb = new FindCommunityCallback() {
      public void execute(String managerName) {
        if (log.isDebugEnabled()) {
          log.debug(agentName + ": sendCommunityRequest: " +
                    " community=" + communityName +
                    " manager=" + managerName);
        }
        if (managerName != null) {
          if (managerName.equals(agentName)) { // is this agent manager?
            CommunityResponse resp =
                communityManager.processRequest(agentName,
                                                communityName,
                                                requestType,
                                                entity,
                                                attrMods);
            Set listeners = Collections.singleton(crl);
            handleResponse(resp, listeners);
          } else { // Send request to remote manager agent
            MessageAddress managerAddr =
                MessageAddress.getMessageAddress(managerName);
            Request req = new RequestImpl(agentId, // source
                                          managerAddr, // target
                                          communityName,
                                          requestType,
                                          entity,
                                          attrMods,
                                          getUID(),
                                          crl);
            myBlackboardClient.publish(req, BlackboardClient.ADD);
          }
        }
      }
    };
    findCommunity(communityName, fmcb, -1);
  }

  /**
   * Handle response to community request returned by manager.
   * @param req Request
   */
  protected void handleResponse(Request req) {
    handleResponse((CommunityResponse)req.getResponse(),
                   req.getCommunityResponseListeners());
  }

  protected void sendResponse(CommunityResponse resp, Set listeners) {
    myBlackboardClient.queueResponse(resp, listeners);
  }

  protected String getAgentName() {
    return agentId.toString();
  }

  /**
   * Lists all communities in White pages.
   * @return  Collection of community names
   */
  public Collection listAllCommunities() {
    List commNames = new ArrayList();
    try {
      WhitePagesService wps = (WhitePagesService)
          getServiceBroker().getService(this, WhitePagesService.class, null);
      recursiveFindCommunities(
          commNames,
          wps,
          ".comm", // all community entries end in ".comm"
          0, // no timeout
          -1); // no recursion limit
    } catch (Exception e) {
      if (log.isDebugEnabled()) {
        log.error(agentName + ": Error in listAllCommunities: " + e);
      }
    }
    return commNames;
  }

  public void listAllCommunities(CommunityResponseListener crl) {
    crl.getResponse(new CommunityResponseImpl(CommunityResponse.SUCCESS,
                                              listAllCommunities()));
  }

  private static final void recursiveFindCommunities(
      Collection toCol,
      WhitePagesService wps,
      String suffix,
      long timeout,
      int limit) throws Exception {
    if (limit == 0) {
      // max recursion depth
      return;
    }
    Set names = wps.list(suffix, timeout);
    for (Iterator iter = names.iterator(); iter.hasNext(); ) {
      String s = (String)iter.next();
      if (s == null || s.length() <= 5) {
        // never
      } else if (s.charAt(0) == '.') {
        // hierarchical community name
        recursiveFindCommunities(toCol, wps, s, timeout, (limit - 1));
      } else {
        // trim the ".comm" suffix
        String commName = s.substring(0, s.length() - 5);
        toCol.add(commName);
      }
    }
  }

  /**
   * Invokes callback when specified community is found.
   * @param communityName Name of community
   * @param fccb          Callback invoked after community is found or timeout
   *                      has lapsed
   * @param timeout       Length of time (in milliseconds) to wait for
   *                      community to be located.  A value of -1 disables
   *                      the timeout.
   */
  public void findCommunity(final String                communityName,
                            final FindCommunityCallback fccb,
                            final long                  timeout) {
    if (log.isDetailEnabled()) {
      log.detail(agentName + ": findCommunity:" +
               " community=" + communityName +
               " timeout=" + timeout);
    }
    long tryUntil = timeout >= 0 ? System.currentTimeMillis() + timeout : -1;
    myBlackboardClient.queueFindManagerRequest(communityName, fccb, 0, tryUntil);
  }

  public void findManager(final String communityName,
                          final FindCommunityCallback fccb,
                          final long tryUntil) {
    Callback cb = new Callback() {
      public void execute(Response resp) {
        String name = null;
        if (resp.isAvailable() && resp.isSuccess()) {
          AddressEntry entry = ((Response.Get)resp).getAddressEntry();
          if (entry != null) {
            name = entry.getURI().getPath().substring(1);
          }
        }
        if (log.isDetailEnabled()) {
          log.detail(agentName + ": findManager:" +
                     " community=" + communityName +
                     " manager=" + name);
        }
        if (name != null) {
          fccb.execute(name);
        } else { // retry?
          long now = System.currentTimeMillis();
          if (tryUntil < 0 || now < tryUntil) {
            myBlackboardClient.queueFindManagerRequest(communityName,
                fccb,
                5000, // 5 sec delay
                tryUntil);
          } else {
            fccb.execute(null); // Give up
          }
        }
      }
    };
    WhitePagesService wps = (WhitePagesService)
        getServiceBroker().getService(this, WhitePagesService.class, null);
    try {
      wps.get(communityName + ".comm", "community", cb);
    } catch (Exception ex) {
      if (log.isErrorEnabled()) {
        log.error(ex.getMessage());
      }
    } finally {
      getServiceBroker().releaseService(this, WhitePagesService.class, wps);
    }
  }

  protected long now() {
    return System.currentTimeMillis();
  }

  class MyCommunityUpdateListener implements CommunityUpdateListener {

    public void updateCommunity(Community community) {
      if (log.isDebugEnabled()) {
        log.debug(agentName+": updateCommunity:" +
                 " community=" + community +
                 " size=" + community.getEntities().size());
      }
      cache.update(community);
    }

    public void removeCommunity(Community community) {
      if (log.isDebugEnabled()) {
        log.debug(agentName+": remove: community=" + community);
      }
      cache.remove(community.getName());
      //myBlackboardClient.publish(community, BlackboardClient.REMOVE);
    }

  }

  class MyBlackboardClient extends BlackboardClient {

    List findManagerRequests = Collections.synchronizedList(new ArrayList());
    WakeAlarm findMgrTimer;
    WakeAlarm verifyMembershipsTimer;
    boolean myCommunitiesChanged;

    List responses = new ArrayList();

    public MyBlackboardClient(BindingSite bs) {
      super(bs);
    }

    protected long now() { return System.currentTimeMillis(); }

    protected void queueFindManagerRequest(String communityName,
                                            FindCommunityCallback fccb,
                                            long delay,
                                            long tryUntil) {
      findManagerRequests.add(new FindManagerRequest(now() + delay,
                                                     communityName,
                                                     fccb,
                                                     tryUntil));
      if (findMgrTimer == null) {
        findMgrTimer = new WakeAlarm(now() + TIMER_INTERVAL);
        alarmService.addRealTimeAlarm(findMgrTimer);
      }
    }

    protected void queueResponse(CommunityResponse resp,
                                 Set listeners) {
      responses.add(new ResponseHolder(resp, listeners));
      if (blackboard != null) blackboard.signalClientActivity();
    }

    public void setupSubscriptions() {

      if (blackboard.didRehydrate()) {
        // Look for a persisted CommunityMemberships instance
        // This is used to determine what communities this agent previously joined
        // in order to ensure that correct memberships are maintained after a restart.
        Collection cms = blackboard.query(new UnaryPredicate() {
          public boolean execute(Object o) {
            return (o instanceof CommunityMemberships);
          }
        });
        if (cms.isEmpty()) {
          myCommunities = new CommunityMemberships();
          blackboard.publishAdd(myCommunities);
        } else {
          myCommunities = (CommunityMemberships)cms.iterator().next();
        }

      } else { // Initial start
        myCommunities = new CommunityMemberships();
      }

      myCommunities.addListener(new CommunityMembershipsListener() {
        public void membershipsChanged() {
          myCommunitiesChanged = true;
          if (!myCommunities.listCommunities().isEmpty() && verifyMembershipsTimer == null) {
            verifyMembershipsTimer =
                new WakeAlarm(System.currentTimeMillis() + verifyMembershipsInterval);
            alarmService.addRealTimeAlarm(verifyMembershipsTimer);
          }
        }
      });

      // Add listener to maintain memberships if this agent restarts or if
      // Community Manager state gets out of sync due to manager change/restart
      membershipWatcher = new MembershipWatcher(agentName,
                                                DefaultCommunityServiceImpl.this,
                                                myCommunities);

      // Activate MembershipWatcher
      if (!myCommunities.listCommunities().isEmpty() && verifyMembershipsTimer == null) {
        verifyMembershipsTimer =
            new WakeAlarm(System.currentTimeMillis() + verifyMembershipsInterval);
        alarmService.addRealTimeAlarm(verifyMembershipsTimer);
      }

      // Subscribe to CommunityRequests
      communityRequestSub =
          (IncrementalSubscription)blackboard.subscribe(
          communityRequestPredicate);

      // Subscribe to CommunityDescriptors
      communityDescriptorSub =
          (IncrementalSubscription)blackboard.subscribe(
          communityDescriptorPredicate);
    }

    public void execute() {
      super.execute();

      sendCommunityResponses();

      // Resend queued FindManagerRequests
      if (findMgrTimer != null && findMgrTimer.hasExpired()) {
        performFindManagerRetries();
        if (!findManagerRequests.isEmpty()) {
          findMgrTimer = new WakeAlarm(now() + TIMER_INTERVAL);
          alarmService.addRealTimeAlarm(findMgrTimer);
        } else {
          findMgrTimer = null;
        }
      }

      // Verify agent memberships
      if (verifyMembershipsTimer != null && verifyMembershipsTimer.hasExpired()) {
        if (myCommunitiesChanged) {
          blackboard.publishChange(myCommunities);
          myCommunitiesChanged = false;
        }
        membershipWatcher.validate();
        if (!myCommunities.listCommunities().isEmpty()) {
          verifyMembershipsTimer = new WakeAlarm(now() + verifyMembershipsInterval);
          alarmService.addRealTimeAlarm(verifyMembershipsTimer);
        } else {
          verifyMembershipsTimer = null;
        }
      }

      // Process request response
      Collection communityRequests = communityRequestSub.getChangedCollection();
      for (Iterator it = communityRequests.iterator(); it.hasNext(); ) {
        Request req = (Request)it.next();
        if (agentId.equals(req.getSource())) {
          if (logger.isDetailEnabled()) {
            logger.detail(agentName + ": Request subscription: " + req);
          }
          blackboard.publishRemove(req);  // Remove completed request from BB
          handleResponse(req);
        }
      }

      // Receives CommunityDescriptors from community managers.  A CommunityDescriptor
      // is basically a wrapper around a Community instance that defines the
      // entities and attributes of a community.
      for (Iterator it = communityDescriptorSub.getAddedCollection().iterator();
           it.hasNext(); ) {
        CommunityDescriptor cd = (CommunityDescriptor)it.next();
        if (logger.isDebugEnabled()) {
          logger.debug(agentName+": received added CommunityDescriptor: " + cd +
                      " size=" + cd.getCommunity().getEntities().size());
        }
        communityUpdateListener.updateCommunity(cd.getCommunity());
      }
      for (Iterator it = communityDescriptorSub.getChangedCollection().iterator();
           it.hasNext(); ) {
        CommunityDescriptor cd = (CommunityDescriptor)it.next();
        if (logger.isDebugEnabled()) {
          logger.debug(agentName+": received changed CommunityDescriptor: " + cd +
                       " size=" + cd.getCommunity().getEntities().size());
        }
        communityUpdateListener.updateCommunity(cd.getCommunity());
      }
      for (Iterator it = communityDescriptorSub.getRemovedCollection().iterator();
           it.hasNext(); ) {
        CommunityDescriptor cd = (CommunityDescriptor)it.next();
        if (logger.isDebugEnabled()) {
          logger.debug(agentName+": received removed CommunityDescriptor: " + cd +
                       " size=" + cd.getCommunity().getEntities().size());
        }
        communityUpdateListener.removeCommunity(cd.getCommunity());
      }
    }

    private void sendCommunityResponses() {
       int n;
       List l;
       synchronized (responses) {
         n = responses.size();
         if (n <= 0 || blackboard == null) { return; }
         l = new ArrayList(responses);
         responses.clear();
       }
       for (int i = 0; i < n; i++) {
         ResponseHolder resp = (ResponseHolder)l.get(i);
         Set listeners = resp.getListeners();
         for (Iterator it = listeners.iterator(); it.hasNext();) {
           CommunityResponseListener crl = (CommunityResponseListener)it.next();
           if (crl != null) {
             crl.getResponse(resp.getResponse());
           }
         }
       }
     }

    private void performFindManagerRetries() {
      int n;
      List l;
      long now = now();
      synchronized (findManagerRequests) {
        n = findManagerRequests.size();
        if (n <= 0 || blackboard == null) { return; }
        l = new ArrayList(findManagerRequests);
        findManagerRequests.clear();
      }
      for (int i = 0; i < n; i++) {
        FindManagerRequest req = (FindManagerRequest)l.get(i);
        if (now >= req.getTime()) {
          findManager(req.getCommunityName(), req.getCallback(), req.tryUntil);
        } else {  // requeue
          findManagerRequests.add(req);
        }
      }
    }

    /**
     * Predicate used to select CommunityRequests.
     */
    private IncrementalSubscription communityRequestSub;
    private UnaryPredicate communityRequestPredicate = new UnaryPredicate() {
      public boolean execute(Object o) {
        return (o instanceof Request);
      }
    };

    /**
     * Selects CommunityDescriptors that are sent by remote community manager
     * agent.
     */
    private IncrementalSubscription communityDescriptorSub;
    private UnaryPredicate communityDescriptorPredicate = new UnaryPredicate() {
      public boolean execute(Object o) {
        return (o instanceof CommunityDescriptor);
      }
    };

    // Timer for periodically checking blackboard availability.
    // Blackboard activity is signaled once the blackboard service is available
    // to check for queued requests
    protected class WakeAlarm implements Alarm {
      private long expiresAt;
      private boolean expired = false;
      public WakeAlarm(long expirationTime) {expiresAt = expirationTime;}
      public long getExpirationTime() {return expiresAt;}
      public synchronized void expire() {
        if (!expired) {
          expired = true;
          blackboard.signalClientActivity();
        }
      }
      public boolean hasExpired() {return expired;}
      public synchronized boolean cancel() {
        boolean was = expired;
        expired = true;
        return was;
      }
    }
  }

  class FindManagerRequest {
    private long nextRetryTime;
    private String communityName;
    private FindCommunityCallback fmcb;
    private long tryUntil;
    FindManagerRequest(long time,
                       String cname,
                       FindCommunityCallback cb,
                       long tu) {
      nextRetryTime = time;
      communityName = cname;
      fmcb = cb;
      tryUntil = tu;
    }
    protected long getTime() { return nextRetryTime; }
    protected String getCommunityName() { return communityName; }
    protected FindCommunityCallback getCallback() { return fmcb; }
  }

  class ResponseHolder {
  private CommunityResponse resp;
  private Set listeners;
  ResponseHolder(CommunityResponse r, Set l) {
    resp = r;
    listeners = l;
  }
  protected CommunityResponse getResponse() { return resp; }
  protected Set getListeners() { return listeners; }
}

}
