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

import org.cougaar.community.requests.CommunityRequest;
import org.cougaar.community.requests.CreateCommunity;
import org.cougaar.community.requests.AddChangeListener;
import org.cougaar.community.requests.GetCommunity;
import org.cougaar.community.requests.JoinCommunity;
import org.cougaar.community.requests.LeaveCommunity;
import org.cougaar.community.requests.ModifyAttributes;
import org.cougaar.community.requests.ReleaseCommunity;
import org.cougaar.community.requests.SearchCommunity;
import org.cougaar.community.requests.ListParentCommunities;

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

import javax.naming.directory.Attributes;
import javax.naming.directory.Attribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.ModificationItem;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;

import org.cougaar.util.log.Logger;
import org.cougaar.core.util.UID;

import org.cougaar.core.service.community.CommunityService;
import org.cougaar.core.service.community.Agent;
import org.cougaar.core.service.community.Community;
import org.cougaar.core.service.community.Entity;
import org.cougaar.core.service.community.CommunityChangeEvent;
import org.cougaar.core.service.community.CommunityChangeListener;
import org.cougaar.core.service.community.CommunityResponseListener;
import org.cougaar.core.service.community.CommunityResponse;
import org.cougaar.core.service.community.CommunityRoster;

import org.cougaar.core.agent.service.alarm.Alarm;

import org.cougaar.core.service.BlackboardService;
import org.cougaar.core.blackboard.IncrementalSubscription;
import org.cougaar.core.blackboard.BlackboardClientComponent;

import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.mts.SimpleMessageAddress;

import org.cougaar.core.component.BindingSite;
import org.cougaar.core.component.ServiceBroker;

import org.cougaar.core.service.AgentIdentificationService;
import org.cougaar.core.service.BlackboardService;
import org.cougaar.core.service.LoggingService;
import org.cougaar.core.service.UIDService;
import org.cougaar.core.service.wp.WhitePagesService;
import org.cougaar.core.service.SchedulerService;
import org.cougaar.core.service.AlarmService;

import org.cougaar.core.component.ServiceAvailableListener;
import org.cougaar.core.component.ServiceAvailableEvent;

import org.cougaar.util.UnaryPredicate;

import EDU.oswego.cs.dl.util.concurrent.Semaphore;

/** A CommunityService is an API which may be supplied by a
 * ServiceProvider registered in a ServiceBroker that provides
 * access to community management capabilities.
 **/
public class CommunityServiceImpl extends BlackboardClientComponent
  implements CommunityService, java.io.Serializable {

  private static long TIMER_INTERVAL = 10 * 1000;

  private LoggingService log;
  private UIDService uidService;
  private CommunityCache cache;
  private final List todo = new ArrayList(5);
  private final Map myRequests = Collections.synchronizedMap(new HashMap());

  private static Map instances = Collections.synchronizedMap(new HashMap());

  // Timer
  private WakeAlarm wakeAlarm;

  /**
   * Returns CommunityService instance.
   */
  public static CommunityService getInstance(BindingSite    bs,
                                             MessageAddress addr) {
    CommunityService cs = (CommunityService)instances.get(addr);
    if (cs == null) {
      cs = new CommunityServiceImpl(bs, addr);
      instances.put(addr, cs);
    }
    return cs;
  }

  /**
   * Constructor.
   * @param sb       Reference to agent ServiceBroker
   * @param addr     Address of parent agent
   */
  protected CommunityServiceImpl(BindingSite bs, MessageAddress addr) {
    setBindingSite(bs);
    ServiceBroker sb = getServiceBroker();
    setAgentIdentificationService(
        (AgentIdentificationService)sb.getService(this,
                                                  AgentIdentificationService.class, null));
    log = (LoggingService)sb.getService(this, LoggingService.class, null);
    log = org.cougaar.core.logging.LoggingServiceWithPrefix.add(log,
        agentId + ": ");
    initUidService();
    if (sb.hasService(org.cougaar.core.service.BlackboardService.class)) {
      init();
    } else {
      sb.addServiceListener(new ServiceAvailableListener() {
        public void serviceAvailable(ServiceAvailableEvent sae) {
          if (sae.getService().equals(BlackboardService.class)) {
            init();
          }
        }
      });
    }
    cache = CommunityCache.getCache(sb);
  }

  Object blackboardLock = new Object();
  /**
   * Set essential services and invoke GenericStateModel methods.
   */
  private void init() {
    ServiceBroker sb = getServiceBroker();
    setSchedulerService(
        (SchedulerService)sb.getService(this, SchedulerService.class, null));
    setAlarmService((AlarmService)sb.getService(this, AlarmService.class, null));
    synchronized (blackboardLock) {
      setBlackboardService(
          (BlackboardService)sb.getService(CommunityServiceImpl.this,
                                           BlackboardService.class, null));
    }
    initialize();
    load();
    start();
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

  protected AlarmService getAlarmService() {
    if (alarmService == null) {
      alarmService = (AlarmService) getServiceBroker().getService(this,
          AlarmService.class, null);
    }
    return alarmService;
  }

  /**
   * Request to create a community.  If the specified community does not
   * exist it will be created and the caller will become the community
   * manager.  It the community already exists a Community reference is obtained
   * from its community manager and returned.
   * @param communityName    Name of community to create
   * @param attrs            Attributes to associate with new community
   * @param crl              Listener to receive response
   */
  public void createCommunity(String                    communityName,
                              Attributes                attrs,
                              CommunityResponseListener crl) {
    queueCommunityRequest(new CreateCommunity(communityName,
                                              attrs,
                                              getUID()), crl);
  }

  /**
   * Request to get a Community instance from local cache.  If community is
   * found in cache a reference is returned by method call.  If the community
   * is not found in the cache a null value is returned and the Community
   * reference is requested from the community manager.  After the Community
   * instance has been obtained from the community manager the supplied
   * CommunityResponseListener callback is invoked to notify the requester.
   * Note that the supplied callback is not invoked if a non-null value is
   * returned.
   * @param communityName  Community of interest
   * @param crl            Listener to receive response after remote fetch
   * @return               Community instance if found in cache or null if not
   *                       found
   */
  public Community getCommunity(String                    communityName,
                                CommunityResponseListener crl) {
    if (log.isDebugEnabled()) {
      log.debug("getCommunity:" +
                " name=" + communityName +
                " inCache=" + cache.contains(communityName) +
                " community=" + (cache.contains(communityName)
                                 ? cache.get(communityName).getEntities().size() + " entities"
                                 : null));
    }
    if (cache.contains(communityName)) {
      return cache.get(communityName);
    } else {
      queueCommunityRequest(new GetCommunity(communityName, getUID(), -1),
                              crl);
      return null;
    }
  }

  /**
   * Request to modify an Entity's attributes.
   * @param communityName    Name of community
   * @param entityName       Name of affected Entity or null if modifying
   *                         community attributes
   * @param mods             Attribute modifications
   * @param crl              Listener to receive response
   */
  public void modifyAttributes(String                    communityName,
                               String                    entityName,
                               ModificationItem[]        mods,
                               CommunityResponseListener crl) {
    queueCommunityRequest(new ModifyAttributes(communityName,
                                                 entityName,
                                                 mods,
                                                 getUID()), crl);
  }

  /**
   * Request to join a named community.  If the specified community does not
   * exist it may be created in which case the caller becomes the community
   * manager.  It the community doesn't exist and the caller has set the
   * "createIfNotFound flag to false the join request will be queued until the
   * community is found.
   * @param communityName    Community to join
   * @param entityName       New member name
   * @param entityType       Type of member entity to create (AGENT or COMMUNITY)
   * @param entityAttrs      Attributes for new member
   * @param createIfNotFound Create community if it doesn't exist, otherwise
   *                         wait
   * @param communityAttrs   Attributes for created community (used if
   *                         createIfNotFound set to true, otherwise ignored)
   * @param crl              Listener to receive response
   */
  public void joinCommunity(String                    communityName,
                            String                    entityName,
                            int                       entityType,
                            Attributes                entityAttrs,
                            boolean                   createIfNotFound,
                            Attributes                newCommunityAttrs,
                            CommunityResponseListener crl) {
    queueCommunityRequest(new JoinCommunity(communityName,
                                              entityName,
                                              entityType,
                                              entityAttrs,
                                              createIfNotFound,
                                              newCommunityAttrs,
                                              getUID()), crl);
  }

  /**
   * Request to leave named community.
   * @param communityName  Community to leave
   * @param entityName     Entity to remove from community
   * @param crl            Listener to receive response
   */
  public void leaveCommunity(String                    communityName,
                             String                    entityName,
                             CommunityResponseListener crl) {
    queueCommunityRequest(new LeaveCommunity(communityName,
                                               entityName,
                                               getUID()), crl);
  }

  /**
   * Initiates a community search operation.  The results of the search are
   * immediately returned as part of the method call if the search can be
   * resolved using locally cached data.  However, if the search requires
   * interaction with a remote community manager a null value is returned by
   * the method call and the search results are returned via the
   * CommunityResponseListener callback after the remote operation has been
   * completed.  In the case where the search can be satisified using local
   * data (i.e., the method returns a non-null value) the
   * CommunityResponseListener is not invoked.
   * @param communityName   Name of community to search
   * @param searchFilter    JNDI compliant search filter
   * @param recursiveSearch True for recursive search into nested communities
   *                        [false = search top community only]
   * @param resultQualifier Type of entities to return in result [ALL_ENTITIES,
   *                        AGENTS_ONLY, or COMMUNITIES_ONLY]
   * @param crl             Callback object to receive search results
   * @return                Collection of Entity objects matching search
   *                        criteria if available in local cache.  A null value
   *                        is returned if cache doesn't contained named
   *                        community.
   */
  public Collection searchCommunity(String              communityName,
                              String                    searchFilter,
                              boolean                   recursiveSearch,
                              int                       resultQualifier,
                              CommunityResponseListener crl) {
    Collection results = null;
    if (communityName == null) {
      // Search all parent communities
      results = cache.search(searchFilter);
      if (results == null) {
        // Cache didn't contain any parent communities, submit request
        // with callback
        queueCommunityRequest(new SearchCommunity(null,
                                                    searchFilter,
                                                    recursiveSearch,
                                                    resultQualifier,
                                                    getUID()), crl);
      }
    } else {
      if (cache.contains(communityName)) {
        results = cache.search(communityName,
                            searchFilter,
                            resultQualifier,
                            recursiveSearch);
      } else {
        queueCommunityRequest(new SearchCommunity(communityName,
                                                    searchFilter,
                                                    recursiveSearch,
                                                    resultQualifier,
                                                    getUID()), crl);
      }
    }
    if (log.isDebugEnabled()) {
      boolean inCache = cache.contains(communityName);
      log.debug("searchCommunity:" +
                " community=" + communityName +
                " filter=" + searchFilter +
                " recursive=" + recursiveSearch +
                " qualifier=" + resultQualifier +
                " inCache=" + inCache +
                " results=" + (results != null
                                 ? Integer.toString(results.size())
                                 : null));
    }
    return results;
  }

  /**
   * Returns an array of community names of all communities of which caller is
   * a member.
   * @param allLevels Set to false if the list should contain only those
   *                  communities in which the caller is explicitly
   *                  referenced.  If true the list will also include those
   *                  communities in which the caller is implicitly a member
   *                  as a result of community nesting.
   * @return          Array of community names
   */
  public String[] getParentCommunities(boolean allLevels) {
    return (String[])cache.getAncestorNames(agentId.toString(), allLevels).toArray(new String[0]);
  }

  /**
   * Requests a collection of community names identifying the communities that
   * contain the specified member.  If the member name is null the immediate
   * parent communities for calling agent are returned.  If member is
   * the name of a nested community the names of all immediate parent communities
   * is returned.  The results are returned directly if the member name is
   * null or if a copy of the specified community is available in local cache.
   * Otherwise, the results will be returned in the CommunityResponseListener
   * callback in which case the method returns a null value.
   * @param name   Member name (either null or name of a nested community)
   * @param crl    Listner to receive results if remote lookup is required
   * @return A collection of community names if operation can be resolved using
   *         data from local cache, otherwise null
   */
  public Collection listParentCommunities(String                    member,
                                          CommunityResponseListener crl) {
    Collection results = null;
    // if member == null, return parent names for this agent
    if (member == null || member.equals(agentId.toString())) {
      results = cache.getAncestorNames(agentId.toString(), false);
    } else {  // get parent names for specified community
      if (cache.contains(member)) {
        results = new HashSet();
        Attributes attrs = cache.get(member).getAttributes();
        if (attrs != null) {
          Attribute parentAttr = attrs.get("Parent");
          if (parentAttr != null) {
            try {
              for (NamingEnumeration enum = parentAttr.getAll(); enum.hasMore();) {
                results.add((String)enum.next());
              }
            } catch (NamingException ne) {
              log.error("Error parsing attributes for " + member, ne);
            }
          }
        }
      } else {
        queueCommunityRequest(new ListParentCommunities(member,
                              getUID()), crl);
      }
    }
    if (log.isDebugEnabled()) {
      log.debug("listParentCommunities:" +
                " entity=" + member +
                " inCache=" + (results != null) +
                " results=" + (results != null
                                 ? Integer.toString(results.size())
                                 : null));
    }
    return results;
  }

  /**
   * Add listener for CommunityChangeEvents.
   * @param l  Listener
   */
  public void addListener(CommunityChangeListener l) {
    if (log.isDebugEnabled())
      log.debug("Adding CommunityChangeListener:" +
               " community=" + l.getCommunityName());
    cache.addListener(l);
  }

  /**
   * Check for availability of essential services
   * @return True if needed services available
   */
  private boolean servicesReady() {
    return blackboard != null && uidService != null;
  }

  /**
   * Remove listener for CommunityChangeEvents.
   * @param l  Listener
   */
  public void removeListener(CommunityChangeListener l) {
    if (l != null) {
      String communityName = l.getCommunityName();
      cache.removeListener(l);
      // Remove community descriptors from cache if no more listeners
      // and if agent is not a member
      if (cache.contains(communityName) &&
          cache.getListeners(communityName).isEmpty()) {
        Community community = cache.get(l.getCommunityName());
        if (!community.hasEntity(agentId.toString())) {
          queueCommunityRequest(new ReleaseCommunity(communityName,
              getUID(),
              -1), null);
          Collection ancestors = cache.getNestedCommunityNames(community);
          for (Iterator it = ancestors.iterator(); it.hasNext(); ) {
            String nestedCommunityName = (String) it.next();
            if (cache.getListeners(nestedCommunityName).isEmpty()) {
              Community nestedCommunity = cache.get(nestedCommunityName);
              if (!nestedCommunity.hasEntity(agentId.toString())) {
                queueCommunityRequest(new ReleaseCommunity(nestedCommunityName,
                    getUID(),
                    -1), null);
              }
            }
          }
        }
      }
    }
  }

  /**
   * Queue Community Service Request.
   * @param pr
   */
  protected void fireLater(PendingRequest pr) {
    synchronized (todo) {
      todo.add(pr);
    }
    if (servicesReady()) {
      blackboard.signalClientActivity();
    } else {
      AlarmService as = getAlarmService();
      if (as != null) {
        // Start timer to check service availability later
        wakeAlarm = new WakeAlarm(System.currentTimeMillis() + TIMER_INTERVAL);
        as.addRealTimeAlarm(wakeAlarm);
      }
    }
  }

  /**
   * Process queued Community Service Reauests.
   */
  private void fireAll() {
    int n;
    List l;
    synchronized (todo) {
      n = todo.size();
      if (n <= 0 || !servicesReady()) {
        return;
      }
      l = new ArrayList(todo);
      todo.clear();
    }
    for (int i = 0; i < n; i++) {
      PendingRequest pr = (PendingRequest) l.get(i);
      if (pr.request.getUID() == null) {
        pr.request.setUID(getUID());
      }
      myRequests.put(pr.request.getUID(), pr);
      blackboard.publishAdd(pr.request);
      log.debug("publishAdd: " + pr.request);
    }
  }

  protected void queueCommunityRequest(CommunityRequest          cr,
                                       CommunityResponseListener crl) {
    log.debug("queueCommunityRequest: " + cr);
    fireLater(new PendingRequest(cr, crl));
  }


  /////////////////////////////////////////////////////////////////////////////
  // D E P R E C A T E D    M E T H O D s
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Creates a new community.
   * @param communityName Name of community
   * @param attributes    Community attributes
   * @return              True if operation was successful
   */
  public boolean createCommunity(String communityName, Attributes attributes) {
    log.debug("createCommunity:" +
              " community=" + communityName);
    final Status status = new Status(false);
    final CommunityHolder ch = new CommunityHolder();
    final Semaphore s = new Semaphore(0);
    createCommunity(communityName, attributes, new CommunityResponseListener(){
      public void getResponse(CommunityResponse resp){
        status.setValue(resp != null && resp.getStatus() == CommunityResponse.SUCCESS);
        s.release();
      }
    });
    try {
      s.acquire();
    } catch (InterruptedException ie) {
      log.error("Error in createCommunity:", ie);
    }
    return status.getValue();
  }


  /**
   * Checks for the existence of a community in the White pages.
   * @param communityName Name of community to look for
   * @return              True if community was found
   */
  public boolean communityExists(String communityName) {
    if (cache.contains(communityName)) {
      return true;
    } else {
      final Status status = new Status(false);
      final Semaphore s = new Semaphore(0);
      queueCommunityRequest(new GetCommunity(communityName, getUID(), 0),
                              new CommunityResponseListener() {
        public void getResponse(CommunityResponse resp) {
          status.setValue(resp != null &&
                          resp.getStatus() == CommunityResponse.SUCCESS);
          s.release();
        }
      });
      try {
        s.acquire();
      }
      catch (Exception ex) {}
      return status.getValue();
    }
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
          0,       // no timeout
          -1);     // no recursion limit
    } catch (Exception e){
      log.error("Error in listAllCommunities: " + e);
    }
    return commNames;
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
      String s = (String) iter.next();
      if (s == null || s.length() <= 5) {
        // never
      } else if (s.charAt(0) == '.') {
        // hierarchical community name
        recursiveFindCommunities(toCol, wps, s, timeout, (limit-1));
      } else {
        // trim the ".comm" suffix
        String commName = s.substring(0, s.length()-5);
        toCol.add(commName);
      }
    }
  }

  /**
   * Returns attributes associated with community.
   * @param communityName Name of community
   * @return              Communities attributes
   */
  public Attributes getCommunityAttributes(String communityName) {
    if (cache.contains(communityName)) {
      return cache.get(communityName).getAttributes();
    } else {
      final CommunityHolder ch = new CommunityHolder();
      final Semaphore s = new Semaphore(0);
      queueCommunityRequest(new GetCommunity(communityName, getUID(), -1),
                              new CommunityResponseListener() {
        public void getResponse(CommunityResponse resp) {
          ch.setCommunity( (Community) resp.getContent());
          s.release();
        }
      });
      try {
        s.acquire();
        if (ch.getCommunity() != null) {
          return ch.getCommunity().getAttributes();
        }
        else {
          log.warn("Error in getCommunityAttributes: " +
                   " community=" + communityName +
                   " communityExists=" + (ch.getCommunity() != null));
        }
      }
      catch (InterruptedException ie) {
        log.error("Error in getCommunityAttributes:", ie);
      }
      return new BasicAttributes();
    }
  }


  /**
   * Sets the attributes associated with a community.
   * @param communityName Name of community
   * @param attributes    Communities attributes
   * @return              True if operation was successful
   */
  public boolean setCommunityAttributes(String communityName,
                                        Attributes attributes) {

    final Status status = new Status(false);
    final Semaphore s = new Semaphore(0);
    final CommunityHolder ch = new CommunityHolder();
    CommunityResponseListener crl = new CommunityResponseListener(){
      public void getResponse(CommunityResponse resp) {
        status.setValue(resp != null && resp.getStatus() == CommunityResponse.SUCCESS);
        ch.setCommunity((Community)resp.getContent());
        s.release();
      }
    };
    queueCommunityRequest(new GetCommunity(communityName, getUID(), -1), crl);
    try {
      s.acquire();
      if (ch.getCommunity() != null) {
        ModificationItem[] items =
          getAttributeModificationItems(ch.getCommunity().getAttributes(), attributes);
        modifyAttributes(communityName, null, items, crl);
      } else {
        log.warn("Error in setCommunityAttributes: " +
                 " community=" + communityName +
                 " communityExists=" + (ch.getCommunity() != null));
        status.setValue(false);
      }
      s.acquire();
    } catch (Exception e) {
      log.error("Error in setCommunityAttributes: " + e);
    }
    return status.getValue();
  }

  private ModificationItem[] getAttributeModificationItems(Attributes oldAttrs, Attributes newAttrs) {
    List modsList = new ArrayList();
    for(NamingEnumeration enums = oldAttrs.getAll(); enums.hasMoreElements();) {
      Attribute oldAttr = (Attribute) enums.nextElement();
      Attribute newAttr = (Attribute) newAttrs.get(oldAttr.getID());
      if (newAttr == null || !newAttr.equals(oldAttr)) {
        modsList.add(new ModificationItem(DirContext.REMOVE_ATTRIBUTE, oldAttr));
      }
    }
    for(NamingEnumeration enums = newAttrs.getAll(); enums.hasMoreElements();) {
      Attribute newAttr = (Attribute) enums.nextElement();
      Attribute oldAttr = (Attribute) oldAttrs.get(newAttr.getID());
      if (oldAttr != null || !newAttr.equals(oldAttr)) {
        modsList.add(new ModificationItem(DirContext.ADD_ATTRIBUTE, newAttr));
      }
    }
    return (ModificationItem[])modsList.toArray(new ModificationItem[0]);
  }


  /**
   * Modifies the attributes associated with a community.
   * @param communityName Name of community
   * @param mods          Attribute modifications to be performed
   * @return              True if operation was successful
   */
  public boolean modifyCommunityAttributes(String communityName, ModificationItem[] mods) {
    final Status status = new Status(false);
    final Semaphore s = new Semaphore(0);
    CommunityResponseListener cl = new CommunityResponseListener(){
      public void getResponse(CommunityResponse resp) {
        status.setValue(resp != null && resp.getStatus() == CommunityResponse.SUCCESS);
        s.release();
      }
    };
    modifyAttributes(communityName, null, mods, cl);
    try {
      s.acquire();
    } catch (Exception e) {
      log.error("Error in modifyCommunityAttributes: " + e);
    }
    return status.getValue();
  }

  /**
   * Adds an entity to a community.
   * @param communityName        Community name
   * @param entity               Entity to add
   * @param attributes           Attributes to associate with entity
   * @return                     True if operation was successful
   */
  public boolean addToCommunity(String communityName, Object entity,
                         String entityName, Attributes attributes) {
    if(communityName == null || entity == null || entityName == null)
      return false;
    final Status status = new Status(false);
    final Semaphore s = new Semaphore(0);
    CommunityResponseListener cl = new CommunityResponseListener(){
      public void getResponse(CommunityResponse resp){
        log.debug("addToCommunity: " +
                  " resp=" + resp);
        status.setValue(resp != null &&
                        resp.getStatus() == CommunityResponse.SUCCESS);
        s.release();
      }
    };
    // Try to determine if entity is an agent or nested community
    int entityType = AGENT;
    if (attributes != null) {
      Attribute attr = attributes.get("EntityType");
      if (attr != null && attr.contains("Community")) {
        entityType = COMMUNITY;
      }
    }
    joinCommunity(communityName, entityName, entityType, attributes, false, null, cl);
    try {
      s.acquire();
    } catch(InterruptedException e) {
      log.error("Error in addToCommunity: " + e);
    }
    return status.getValue();
  }


  /**
   * Removes an entity from a community.
   * @param communityName  Community name
   * @param entityName     Name of entity to remove
   * @return               True if operation was successful
   */
  public boolean removeFromCommunity(String communityName, String entityName) {
    final Status status = new Status(false);
    final Semaphore s = new Semaphore(0);
    Entity entity = new EntityImpl(entityName);
    leaveCommunity(communityName, entity.getName(), new CommunityResponseListener(){
      public void getResponse(CommunityResponse resp){
        status.setValue(resp.getStatus() == CommunityResponse.SUCCESS);
        s.release();
      }
    });
    try {
      s.acquire();
    } catch(InterruptedException e) {
      log.error("Error in addToCommunity: " + e);
    }
    return status.getValue();
  }


  /**
   * Returns a collection of entity names associated with the specified
   * community.
   * @param communityName  Entities parent community
   * @return               Collection of entity names
   */
  public Collection listEntities(String communityName) {
    final CommunityHolder ch = new CommunityHolder();
    final Semaphore s = new Semaphore(0);
    queueCommunityRequest(new GetCommunity(communityName, getUID(), -1),
                            new CommunityResponseListener() {
      public void getResponse(CommunityResponse resp){
        ch.setCommunity((Community)resp.getContent());
        s.release();
      }
    });
    try {
      s.acquire();
      if (ch.getCommunity() != null) {
        return ch.getCommunity().getEntities();
      } else {
        log.warn("Error in listEntities: " +
                 " community=" + communityName +
                 " communityExists=" + (ch.getCommunity() != null));
      }
    }catch(InterruptedException e){log.error("Error in listEntities:" + e);}
    return ch.getCommunity().getEntities();
  }


  /**
   * Returns attributes associated with specified community entity.
   * @param communityName  Entities parent community
   * @param entityName     Name of community entity
   * @return               Attributes associated with entity
   */
  public Attributes getEntityAttributes(String communityName,
                                        String entityName) {
    log.debug("getEntityAttributes");
    if (cache.contains(communityName)) {
      Community community = cache.get(communityName);
      if (community.hasEntity(entityName)) {
        return community.getEntity(entityName).getAttributes();
      } else {
        return new BasicAttributes();
      }
    } else {
      final CommunityHolder ch = new CommunityHolder();
      final Semaphore s = new Semaphore(0);
      queueCommunityRequest(new GetCommunity(communityName, getUID(), -1),
                              new CommunityResponseListener() {
        public void getResponse(CommunityResponse resp) {
          ch.setCommunity( (Community) resp.getContent());
          s.release();
        }
      });
      try {
        s.acquire();
        if (ch.getCommunity() != null &&
            ch.getCommunity().hasEntity(entityName)) {
          return ch.getCommunity().getEntity(entityName).getAttributes();
        }
        else {
          log.warn("Error in getEntityAttributes: " +
                   " community=" + communityName +
                   " entity=" + entityName +
                   " communityExists=" + (ch.getCommunity() != null) +
                   " entityExists=" + (ch.getCommunity().hasEntity(entityName)));
        }
      }
      catch (Exception ex) {
        log.debug(ex.getMessage(), ex);
      }
      return new BasicAttributes();
    }
  }

  /**
   * Sets the attributes associated with specified community entity.
   * @param communityName  Entities parent community
   * @param entityName     Name of community entity
   * @param attributes     Attributes to associate with entity
   * @return               True if operation was successful
   */
  public boolean setEntityAttributes(String communityName, String entityName,
                                     Attributes attributes) {
    final CommunityHolder ch = new CommunityHolder();
    final Semaphore s = new Semaphore(0);
    final Status status = new Status(false);
    CommunityResponseListener crl = new CommunityResponseListener(){
      public void getResponse(CommunityResponse resp){
        status.setValue(resp.getStatus() == CommunityResponse.SUCCESS);
        ch.setCommunity((Community)resp.getContent());
        s.release();
      }
    };
    queueCommunityRequest(new GetCommunity(communityName, getUID(), -1), crl);
    try{
      s.acquire();
      if (ch.getCommunity() != null &&
          ch.getCommunity().hasEntity(entityName)) {
          ModificationItem[] items =
          getAttributeModificationItems(ch.getCommunity().getEntity(entityName).
                                        getAttributes(), attributes);
          modifyAttributes(communityName, entityName, items, crl);
          s.acquire();
      } else {
        log.warn("Error in setEntityAttributes: " +
                 " community=" + communityName +
                 " entity=" + entityName +
                 " communityExists=" + (ch.getCommunity() != null) +
                 " entityExists=" + (ch.getCommunity().hasEntity(entityName)));
       }
    }catch(Exception e) {
      log.error("Error in setEntityAttributes: " + e);
    }
    return status.getValue();
  }


  /**
   * Modifies the attributes associated with specified community entity.
   * @param communityName  Entities parent community
   * @param entityName     Name of community entity
   * @param mods           Attribute modifications to be performed
   * @return               True if operation was successful
   */
  public boolean modifyEntityAttributes(String communityName, String entityName,
                                 ModificationItem[] mods) {
    log.debug("modifyEntityAttributes");
    final Semaphore s = new Semaphore(0);
    final Status status = new Status(false);
    CommunityResponseListener cl = new CommunityResponseListener(){
      public void getResponse(CommunityResponse resp) {
        status.setValue(resp != null && resp.getStatus() == CommunityResponse.SUCCESS);
         s.release();
      }
    };
    modifyAttributes(communityName, entityName, mods, cl);
    try {
      s.acquire();
    } catch(InterruptedException e) {
      log.error("Error in modifyEntityAttributes:" + e);
    }
    return status.getValue();
  }

  /**
   * Performs attribute based search of community context.  This search looks
   * for communities with attributes that satisfy criteria specified by filter.
   * Entities within communities are not searched.  This is a general
   * purpose search operation using a JNDI search filter.  Refer to JNDI
   * documentation for filter syntax.
   * @param filter        JNDI search filter
   * @return              Collection of community names that satisfy filter
   */
  public Collection search(String filter) {
    log.debug("search: filter=" + filter);
    Collection entities = cache.search(filter);
    Collection names = new ArrayList();
    for (Iterator it = entities.iterator(); it.hasNext();) {
      names.add(((Entity)it.next()).getName());
    }
    return names;
  }


  /**
   * Performs attribute based search of community entities.  This is a general
   * purpose search operation using a JNDI search filter.
   * @param communityName Name of community to search
   * @param filter        JNDI search filter
   * @return              Collection of MessageAddresses
   */
  public Collection search(final String communityName, String filter) {
    return search(communityName, filter, false);
  }

  public Collection search(final String communityName, final String filter, boolean block) {
    if (log.isDebugEnabled()) {
      boolean inCache = cache.contains(communityName);
      log.debug("search" +
                " community=" + communityName +
                " filter=" + filter +
                " blocking=" + block +
                " inCache=" + inCache);
      if (inCache) {
        log.detail(cache.get(communityName).toXml());
      }
    }
    Collection matches = Collections.EMPTY_SET;
    final Semaphore s = new Semaphore((block) ? 0 : 1);
    if (cache.contains(communityName)) {
      Collection searchResults = cache.search(communityName,
                                              filter,
                                              Community.AGENTS_ONLY,
                                              true);
      matches = new HashSet();
      for (Iterator it = searchResults.iterator(); it.hasNext();) {
        Entity e = (Entity)it.next();
        if (e instanceof Agent) {
          matches.add(SimpleMessageAddress.getSimpleMessageAddress(e.getName()));
        }
      }
    } else {
      CommunityResponseListener crl = new CommunityResponseListener() {
        public void getResponse(CommunityResponse resp) {
          s.release();
        }
      };
      queueCommunityRequest(new GetCommunity(communityName, getUID(), -1), crl);
      try { s.acquire(); } catch (Exception ex) {}
    }
    if (log.isDebugEnabled()) {
      log.debug("search:" +
               " community=" + communityName +
               " filter=" + filter +
               " matches=" + matches.size());
    }
    return matches;
  }

  private String entityNames(Collection entities) {
    StringBuffer sb = new StringBuffer("[");
    for (Iterator it = entities.iterator(); it.hasNext();) {
      Entity entity = (Entity)it.next();
      sb.append(entity.getName());
      if (it.hasNext()) sb.append(",");
    }
    sb.append("]");
    return sb.toString();
  }

  protected Object lookup(String communityName, String entityName) {
    return null;
  }


  /**
   * Requests the roster for the named community.
   * @param communityName Name of community
   * @return              Community roster (or null if agent is not authorized
   *                      access)
   */
  public CommunityRoster getRoster(String communityName) {
    log.debug("getRoster: community=" + communityName);
    if (cache.contains(communityName)) {
      return new CommunityRosterImpl(cache.get(communityName));
    } else {
      final CommunityHolder ch = new CommunityHolder();
      final Semaphore s = new Semaphore(0);
      queueCommunityRequest(new GetCommunity(communityName, getUID(), 0),
                              new CommunityResponseListener() {
        public void getResponse(CommunityResponse resp) {
          ch.setCommunity( (Community) resp.getContent());
          s.release();
        }
      });
      try {
        s.acquire();
      }
      catch (Exception ex) {}
      if (ch.getCommunity() == null) {
        return new CommunityRosterImpl(communityName, new Vector(), false);
      }
      else {
        return new CommunityRosterImpl(ch.getCommunity());
      }
    }
  }

  /**
   * Requests a collection of community names identifying the communities that
   * contain the specified member.
   * @param name   Member name
   * @return A collection of community names
   */
  public Collection listParentCommunities(String member) {
    return cache.getAncestorNames(member, false);
  }

  /**
   * Requests a collection of community names identifying the communities that
   * contain the specified member and satisfy a given set of attributes.
   * @param name   Member name
   * @param filter Search filter defining community attributes
   * @return A collection of community names
   */
  public Collection listParentCommunities(String member, String filter) {
    List matches = new ArrayList();
    Collection parentNames = listParentCommunities(member);
    Set communitiesMatchingFilter = cache.search(filter);
    if (communitiesMatchingFilter != null) {
      for (Iterator it = communitiesMatchingFilter.iterator(); it.hasNext(); ) {
        Community community = (Community) it.next();
        if (parentNames.contains(community.getName())) {
          matches.add(community.getName());
        }
      }
    }
    return matches;
  }

  /**
   * Adds a listener to list of addresses that are notified of changes to
   * specified community.
   * @param addr          Listeners MessageAddress
   * @param communityName Community of interest
   * @return              True if operation was successful
   * @deprecated          Subscribe to changes in Community objects associated
   *                      with community of interest
   */
  public boolean addListener(MessageAddress addr, String communityName) {
    return false;
  }


  /**
   * Removes a listener from list of addresses that are notified of changes to
   * specified community.
   * @param addr          Listeners MessageAddress
   * @param communityName Community of interest
   * @return              True if operation was successful
   * @deprecated          Subscribe to changes in Community objects associated
   *                      with community of interest
   */
  public boolean removeListener(MessageAddress addr, String communityName) {
    return false;
  }


  /**
   * Returns a collection of MessageAddresss associated with the agents
   * that are have the attribute "ChangeListener".
   * specified community.
   * @param communityName Community of interest
   * @return              MessageAddresses of listener agents
   * @deprecated
   */
  public Collection getListeners(String communityName) {
    return Collections.EMPTY_SET;
  }


  /**
   * Finds all community entities associated with a given role.  This method
   * is equivalent to using the search method with the filter
   * "(Role=RoleName)".
   * @param communityName Name of community to query
   * @param roleName      Name of role provided
   * @return              Collection of entity objects
   */
  public Collection searchByRole(String communityName, String roleName) {
    return search(communityName, "(Role=" + roleName + ")");
  }


  /**
   * Returns a collection of all roles supported by the specified community
   * entity.
   * @param communityName  Parent community
   * @param entityName     Name of community entity
   * @return               Collection of role names
   */
  public Collection getEntityRoles(String communityName, String entityName) {
    final CommunityHolder ch = new CommunityHolder();
    final Semaphore s = new Semaphore(0);
    queueCommunityRequest(new GetCommunity(communityName, getUID(), -1),
                            new CommunityResponseListener() {
      public void getResponse(CommunityResponse resp){
        ch.setCommunity((Community)resp.getContent());
        s.release();
      }
    });
    try{
      s.acquire();
      if (ch.getCommunity() != null &&
          ch.getCommunity().hasEntity(entityName)) {
        Entity entity = ch.getCommunity().getEntity(entityName);
        Attributes attrs = entity.getAttributes();
        Attribute attr = attrs.get("Role");
        Collection roles = new ArrayList();
        for(NamingEnumeration enum = attr.getAll(); enum.hasMoreElements();) {
          roles.add((String)enum.nextElement());
        }
        if (log.isDebugEnabled()) {
          log.debug("getCommunityRoles:" +
                    " community=" + communityName +
                    " attrs=" + attrsToString(attrs) +
                    " roles=" + roles);
        }
        return roles;
      } else {
        log.warn("Error in getEntityRoles: " +
                 " community=" + communityName +
                 " entity=" + entityName +
                 " communityExists=" + (ch.getCommunity() != null) +
                 " entityExists=" + (ch.getCommunity().hasEntity(entityName)));
       }
    } catch(Exception e) {
      log.error("Error in getEntityRoles:" + e);
    }
    return new ArrayList();
  }

  public void setupSubscriptions() {
    communityRequestSub =
        (IncrementalSubscription)blackboard.subscribe(communityRequestPredicate);
  }

  /**
   * Get all pending community requests that match the specified
   * request.
   * @param cr Rquest to match
   * @return Collection of PendingRequest objects
   */
  private Collection getMatchingRequests(CommunityRequest cr) {
    Collection matches = new ArrayList();
    synchronized (myRequests) {
      for (Iterator it = myRequests.values().iterator(); it.hasNext();) {
        PendingRequest pr = (PendingRequest)it.next();
        if (cr.equals(pr.request)) matches.add(pr);
      }
    }
    return matches;
  }

  public void execute() {

    // get queued CommunityRequests and publish for processing by CommunityPlugin
    fireAll();

    // Check for completed requests (requests with a response)
    //    Notify listeners and remove request from BB
    Collection communityRequests = communityRequestSub.getChangedCollection();
    for (Iterator it = communityRequests.iterator(); it.hasNext(); ) {
      CommunityRequest cr = (CommunityRequest) it.next();
      if (cr.getResponse() != null) {
        // Find all pending requests satisfied by this response
        for (Iterator it1 = getMatchingRequests(cr).iterator(); it1.hasNext();) {
          PendingRequest pr = (PendingRequest)it1.next();
          // Notify listeners
          if (pr.listener != null) {
            pr.listener.getResponse(cr.getResponse());
            log.debug("Fire CommunityResponseListener callback:" +
                      " request=" + pr.request.getRequestType() +
                      " community=" + pr.request.getCommunityName() +
                      " uid=" + pr.request.getUID());
          }
          // Remove request from BB and pending request list
          if (log.isDebugEnabled()) {
            log.debug("Removing CommunityRequest:" +
                      " request=" + pr.request.getRequestType() +
                      " uid=" + pr.request.getUID());
          }
          myRequests.remove(pr.request.getUID());
          if (!(pr.request instanceof SearchCommunity)) {
            blackboard.publishRemove(pr.request);
          }
        }
      }
    }
  }

  /**
   * Returns a list of all external roles supported by the specified community.
   * @param communityName Community name
   * @return              Collection of role names
   */
  public Collection getCommunityRoles(String communityName) {
    final CommunityHolder ch = new CommunityHolder();
    final Semaphore s = new Semaphore(0);
    queueCommunityRequest(new GetCommunity(communityName, getUID(), -1),
                            new CommunityResponseListener() {
      public void getResponse(CommunityResponse resp){
        ch.setCommunity((Community)resp.getContent());
        s.release();
      }
    });
    try {
      s.acquire();
      if (ch.getCommunity() != null) {
        Attributes attrs = ch.getCommunity().getAttributes();
        Attribute attr = attrs.get("Role");
        if(attr == null) return new ArrayList();
        Collection roles = new ArrayList();
        if (attr.size() == 1)
          roles.add( (String) attr.get());
        else {
          for (NamingEnumeration enum = attr.getAll(); enum.hasMoreElements(); ) {
            roles.add( (String) enum.nextElement());
          }
        }
        if (log.isDebugEnabled()) {
          log.debug("getCommunityRoles:" +
                    " community=" + communityName +
                    " attrs=" + attrsToString(attrs) +
                    " roles=" + roles);
        }
        return roles;
      }
      else {
        log.warn("Error in getCommunityRoles: " +
                 " community=" + communityName +
                 " communityExists=" + (ch.getCommunity() != null));
      }
    } catch(Exception e) {
      log.error("Error in getCommunityRoles:" + e);
    }
    return Collections.EMPTY_SET;
  }


  /**
   * Associates a new role with specified community entity.
   * @param communityName  Parent community
   * @param entityName     Name of community entity
   * @param roleName       Name of role to associate with entity
   * @return               True if operation was successful
   */
  public boolean addRole(String communityName, String entityName,
                         String roleName) {
    final CommunityHolder ch = new CommunityHolder();
    final Semaphore s = new Semaphore(0);
    final Status status = new Status(false);
    CommunityResponseListener cl = new CommunityResponseListener() {
      public void getResponse(CommunityResponse resp) {
        status.setValue(resp.getStatus() == CommunityResponse.SUCCESS);
        ch.setCommunity( (Community) resp.getContent());
        s.release();
      }
    };
    queueCommunityRequest(new GetCommunity(communityName, getUID(), -1), cl);
    try {
      s.acquire();
      if (ch.getCommunity() != null &&
          ch.getCommunity().hasEntity(entityName)) {
        Entity entity = ch.getCommunity().getEntity(entityName);
        Attributes attrs = entity.getAttributes();
        Attribute attr = attrs.get("Role");
        ModificationItem mi[];
        if (attr == null)
          mi = new ModificationItem[] {
              new ModificationItem(DirContext.ADD_ATTRIBUTE,
                                   new BasicAttribute("Role", roleName))};
        else {
          mi = new ModificationItem[2];
          mi[0] = new ModificationItem(DirContext.REMOVE_ATTRIBUTE, attr);
          attr.add(roleName);
          mi[1] = new ModificationItem(DirContext.ADD_ATTRIBUTE, attr);
        }
        modifyAttributes(communityName, entityName, mi, cl);
      }
      else {
        log.warn("Error in addRole: " +
                 " community=" + communityName +
                 " entity=" + entityName +
                 " communityExists=" + (ch.getCommunity() != null) +
                 " entityExists=" + (ch.getCommunity().hasEntity(entityName)));
        status.setValue(false);
      }
    }
    catch (Exception e) {
      log.error("Error in addRole: " + e);
    }
    return status.getValue();
  }


  /**
   * Removes a Role from attributes of specified community entity.
   * @param communityName  Parent community
   * @param entityName     Name of community entity
   * @param roleName       Name of role to associate with entity
   * @return               True if operation was successful
   */
  public boolean removeRole(String communityName, String entityName,
                            String roleName) {
    final CommunityHolder ch = new CommunityHolder();
    final Semaphore s = new Semaphore(0);
    final Status status = new Status(false);
    CommunityResponseListener cl = new CommunityResponseListener(){
      public void getResponse(CommunityResponse resp){
        status.setValue(resp.getStatus() == CommunityResponse.SUCCESS);
        ch.setCommunity((Community)resp.getContent());
        s.release();
      }
    };
    queueCommunityRequest(new GetCommunity(communityName, getUID(), -1), cl);
    try {
      s.acquire();
      if (ch.getCommunity() != null &&
          ch.getCommunity().hasEntity(entityName)) {
        Entity entity = ch.getCommunity().getEntity(entityName);
        Attributes attrs = entity.getAttributes();
        Attribute attr = attrs.get("Role");
        ModificationItem[] mi;
        if (!attr.contains(roleName)) {
          return true;
        }
        else {
          mi = new ModificationItem[2];
          mi[0] = new ModificationItem(DirContext.REMOVE_ATTRIBUTE, attr);
          attr.remove(roleName);
          mi[1] = new ModificationItem(DirContext.ADD_ATTRIBUTE, attr);
        }
        modifyAttributes(communityName, entityName, mi, cl);
      }
      else {
        log.warn("Error in removeRole: " +
                 " community=" + communityName +
                 " entity=" + entityName +
                 " communityExists=" + (ch.getCommunity() != null) +
                 " entityExists=" + (ch.getCommunity().hasEntity(entityName)));
        status.setValue(false);
      }
    }
    catch (Exception e) {
      log.error("Error in removeRole: " + e);
    }
    return status.getValue();
  }

  /**
   * Get Unique identifier.
   */
  private UID getUID() {
    return uidService != null ? uidService.nextUID() : null;
  }


  /**
   * Notifies interested agents that a change has occurred in community.
   */
  protected void notifyListeners(final String communityName, final int type,
      final String whatChanged) {
  }


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
   * Creates a string representation of an Attribute set.
   */
  protected String attrsToString(Attributes attrs) {
    StringBuffer sb = new StringBuffer("[");
    try {
      for (NamingEnumeration enum = attrs.getAll(); enum.hasMore();) {
        Attribute attr = (Attribute)enum.next();
        sb.append(attr.getID() + "=(");
        for (NamingEnumeration enum1 = attr.getAll(); enum1.hasMore();) {
          sb.append((String)enum1.next());
          if (enum1.hasMore())
            sb.append(",");
          else
            sb.append(")");
        }
        if (enum.hasMore()) sb.append(",");
      }
      sb.append("]");
    } catch (NamingException ne) {}
    return sb.toString();
  }

  /**
   * Container for community request/listener pair.
   */
  class PendingRequest {
    CommunityRequest request;
    CommunityResponseListener listener;
    PendingRequest (CommunityRequest req, CommunityResponseListener crl) {
      this.request = req;
      this.listener = crl;
    }
  }

  class Status {
    boolean value = false;
    Status (boolean v) { value = v; }
    void setValue(boolean v) { value = v; }
    boolean getValue() { return value; }
  }

  class CommunityHolder {
    Community community;
    CommunityHolder () {}
    void setCommunity(Community c) { community = c; }
    Community getCommunity() { return community; }
  }

  // Timer for periodically checking blackboard availability.
  // Blackboard activity is signaled once the blackboard service is available
  // to check for queued requests
  private class WakeAlarm implements Alarm {
    private long expiresAt;
    private boolean expired = false;
    public WakeAlarm (long expirationTime) { expiresAt = expirationTime;
    log.info("wakeAlarm");}
    public long getExpirationTime() { return expiresAt; }
    public synchronized void expire() {
      if (!expired) {
        expired = true;
        if (servicesReady()) {
          blackboard.signalClientActivity();
        } else {  // Not ready yet, wait for awhile
          wakeAlarm = new WakeAlarm(System.currentTimeMillis() + TIMER_INTERVAL);
          alarmService.addRealTimeAlarm(wakeAlarm);
        }
      }
    }
    public boolean hasExpired() { return expired; }
    public synchronized boolean cancel() {
      boolean was = expired;
      expired = true;
      return was;
    }
  }

}
