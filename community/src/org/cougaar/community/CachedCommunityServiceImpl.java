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
import javax.naming.*;
import javax.naming.directory.*;

import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.service.community.*;

import org.cougaar.core.naming.Filter;
import org.cougaar.core.naming.SearchStringParser;

import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.service.BlackboardService;
import org.cougaar.core.service.SchedulerService;
import org.cougaar.core.service.AlarmService;

import org.cougaar.core.blackboard.IncrementalSubscription;
import org.cougaar.util.UnaryPredicate;

import org.cougaar.core.component.ServiceRevokedListener;
import org.cougaar.core.component.ServiceRevokedEvent;

import org.cougaar.util.log.Logger;
import org.cougaar.util.log.Logging;

/**
 * Extends CommunityServiceImpl to provide local caching of frequently accessed
 * community information in NameServer.
 */

public class CachedCommunityServiceImpl extends CommunityServiceImpl {

  // Caching mode
  static private boolean LAZY  = true;

  // Code for gathering some caching statistics

  static int calls   = 0;  // Calls to cache enabled methods
  static int updates = 0;  // Cache updates triggered
  /*
  static {
    Thread cacheStats = new Thread("CacheStatsThread") {
      public void run() {
        while (true) {
          try { Thread.sleep(60000); } catch (Exception ex) {}
          System.out.println("Cache Stats: mode=" + ((LAZY) ? "LAZY" : "EAGER") +
            " calls=" + calls + " updates=" + updates);
        }
      }
    };
    cacheStats.start();
  }
  */


  // Class defining Community entry in cache
  class Community {
    String name;
    Attributes attrs;
    Map entities;
    boolean valid;  // are contents of cache valid

    private Community(String name, Attributes attrs) {
      this.name = name;
      this.attrs = attrs;
      this.entities = new HashMap();
      this.valid = false;
    }
  }

  class Entity {
    String name;
    Object obj;
    Attributes attrs;

    private Entity(String name, Object obj, Attributes attrs) {
      this.name = name;
      this.obj = obj;
      this.attrs = attrs;
    }
  }
  private Map cache = new HashMap();
  Thread initThread = null;

  protected CachedCommunityServiceImpl(ServiceBroker sb, MessageAddress addr) {
    super(sb, addr);
    String cacheMode = System.getProperty("org.cougaar.community.caching.mode");
    LAZY = (cacheMode == null || !cacheMode.equalsIgnoreCase("EAGER"));
    if (log.isDebugEnabled())
      log.debug("CacheMode is " + ((LAZY) ? "LAZY" : "EAGER"));
    initBlackboardSubscriber();
  }
    /**
   * Initialize a Blackboard subscriber to receive community change
   * notifications that are relayed from remote agents.  These notifications
   * are used to trigger a cache update.
   */
  private void initBlackboardSubscriber() {
    initThread = new Thread("CommunityService-BBSubscriberInit") {
      public void run() {
        // Wait for required services to become available
        while (getBlackboardService(serviceBroker) == null ||
               !serviceBroker.hasService(SchedulerService.class) ||
               !serviceBroker.hasService(AlarmService.class)) {
          try { Thread.sleep(500); } catch(Exception ex) {}
          if (log.isDebugEnabled())
            log.debug("initBlackboardSubscriber: waiting for services ...");
        }
        SchedulerService ss = (SchedulerService)serviceBroker.getService(this,
          SchedulerService.class, new ServiceRevokedListener() {
          public void serviceRevoked(ServiceRevokedEvent re) {}
        });
        AlarmService as = (AlarmService)serviceBroker.getService(this,
          AlarmService.class, new ServiceRevokedListener() {
          public void serviceRevoked(ServiceRevokedEvent re) {}
        });
        setBlackboardService(getBlackboardService(serviceBroker));
        setSchedulerService(ss);
        setAlarmService(as);
        initialize();
        load();
        CachedCommunityServiceImpl.this.start();
      }
    };
    initThread.start();
  }

  /**
   * Subscribe to CommunityChangeNotifications to receive cache updates.
   */
  public void setupSubscriptions() {
    changeNotifications =
      (IncrementalSubscription)getBlackboardService()
      .subscribe(changeNotificationPredicate);
  }

  /**
   * Get CommunityChangeNotifications and update community cache.
   */
  public void execute() {
    Enumeration enum  = changeNotifications.getAddedList();
    while (enum.hasMoreElements()) {
      CommunityChangeNotification ccn =
        (CommunityChangeNotification)enum.nextElement();
      String communityName = ccn.getCommunityName();
      if (log.isDebugEnabled()) {
        log.debug("Received added CommunityChangeNotification: community=" +
          communityName + " source=" + ccn.getSource());
      }
      fireListeners(new CommunityChangeEvent(communityName,
                                             ccn.getType(),
                                             ccn.whatChanged()));
      synchronized (this) {
        if (LAZY) {
          setIsValid(communityName, false);
        } else { // EAGER
          if (contains(communityName)) {
            update(communityName);
          }
        }
      }
    }

    enum  = changeNotifications.getChangedList();
    while (enum.hasMoreElements()) {
      CommunityChangeNotification ccn =
        (CommunityChangeNotification)enum.nextElement();
      String communityName = ccn.getCommunityName();
      if (log.isDebugEnabled()) {
        log.debug("Received changed CommunityChangeNotification: community=" +
          communityName + " source=" + ccn.getSource());
      }
      fireListeners(new CommunityChangeEvent(communityName,
                                             ccn.getType(),
                                             ccn.whatChanged()));
      synchronized (this) {
        if (LAZY) {
          setIsValid(communityName, false);
        } else { // EAGER
          if (contains(communityName)) {
            update(communityName);
          }
        }
      }
    }
  }

  private synchronized void setIsValid(String communityName, boolean isValid) {
    Community community = (Community)cache.get(communityName);
    if (community != null) community.valid = isValid;
  }

  private synchronized boolean isValid(String communityName) {
    Community community = (Community)cache.get(communityName);
    return (community != null && community.valid);
  }

  /**
   * Tests for existence of community in cache.
   * @param communityName
   * @return True if community is part of cache
   */
  private boolean contains(String communityName) {
    return cache.containsKey(communityName);
  }

  /**
   * Adds community to cache.  Added community replaces any previous entry
   * with same name.
   * @param communityName
   * @param attrs  Community Attributes
   * @param entitiesToRetain the entities that should be retained in
   * the community
   */
  private void addCommunity(String    communityName,
                            Attributes attrs,
                            Collection entitiesToRetain) {
    Community ce = (Community) cache.get(communityName);
    if (ce == null) {
      if (log.isDebugEnabled())
        log.debug("Adding community: community=" + communityName);
      cache.put(communityName, new Community(communityName, attrs));
      addListener(agentId, communityName);
    } else {
      if (log.isDebugEnabled())
        log.debug("Updating community: community=" + communityName);
      ce.attrs = attrs;
      ce.entities.keySet().retainAll(entitiesToRetain);
    }
  }

  /**
   * Removes named community from cache.
   * @param communityName
   */
  private void removeCommunity(String communityName) {
    cache.remove(communityName);
  }

  /**
   * Associates an entity and its attributes with a community.
   * @param communityName
   * @param entityName
   * @param attrs Entity Attributes
   */
  private void addEntity(String communityName, String entityName,
    Object obj, Attributes attrs) {
    if (log.isDebugEnabled())
      log.debug("Adding entity to community: community=" + communityName
               + " entity=" + entityName
               + " attrs=" + attrs);
    Community ce = (Community)cache.get(communityName);
    if (ce != null) {
      ce.entities.put(entityName, new Entity(entityName, obj, attrs));
    }
  }

  /**
   * Removes an entity from community in cache.
   * @param communityName
   * @param entityName
   */
  private void removeEntity(String communityName, String entityName) {
    Community ce = (Community)cache.get(communityName);
    if (ce != null) {
      ce.entities.remove(entityName);
    }
  }

  /**
   * Finds entities in named community that satisfy critera specified by
   * search filter.  Returns collection of String type that identify
   * names of matching entities.
   * @param communityName
   * @param filter         JNDI search filter
   * @return               Collection of entity names
   */
  public synchronized Collection search(String communityName, String filter) {
    if (log.isDebugEnabled())
      log.debug("search: community=" + communityName + " filter=" + filter);
    ++calls;
    if (LAZY && !isValid(communityName) || !contains(communityName)) {
      update(communityName);
    }
    Collection entities = new Vector();
    Community ce = (Community)cache.get(communityName);
    try {
      if (ce != null) {
        SearchStringParser parser = new SearchStringParser();
        Filter f = parser.parse(filter);
        for (Iterator it = ce.entities.entrySet().iterator(); it.hasNext();) {
          Map.Entry me = (Map.Entry)it.next();
          Entity entity = (Entity)me.getValue();
          Attributes attrs = entity.attrs;
          if (f.match(attrs)) entities.add(entity.obj);
        }
      }
    } catch (Exception ex) {
      log.error("Exception in CommunityCache.search, " + ex);
      ex.printStackTrace();
    }
    if (log.isDebugEnabled()) {
      StringBuffer sb = new StringBuffer();
      sb.append("search: found ")
        .append(entities.size())
        .append(" entities, filter=")
        .append(filter)
        .append(", entities=")
        .append(entities);
      log.debug(sb.toString());
    }
    return entities;
  }

  /**
   * Updates cache with current community data in Name Server.
   * @param communityName
   */
  public void update(String communityName) {
    if (log.isDebugEnabled())
      log.debug("Updating cache, agent=" + agentId + " community=" + communityName);
    try {
      ++updates;
      if (communityExists(communityName)) {
        Attributes attrs = super.getCommunityAttributes(communityName);
        Collection entities = listEntities(communityName);
        if (!contains(communityName)) {
          addCommunity(communityName, attrs, entities);
        }
        //System.out.println("Community " + communityName + " has " +
        //  entities.size() + " entities");
        for (Iterator it = entities.iterator(); it.hasNext();) {
          String entityName = (String)it.next();
          attrs = super.getEntityAttributes(communityName, entityName);
          if (log.isDebugEnabled())
            log.debug("update: entity="+ entityName +
                      " attributes=" + attrsToString(attrs));
          Object obj = super.lookup(communityName, entityName);
          addEntity(communityName, entityName, obj, attrs);
        }
        setIsValid(communityName, true);
      } else {
        log.error("Community '" + communityName + "' not found in Name Server");
      }
    } catch (Exception ex) {
      System.err.println("Exception updating CommunityCache, " + ex);
    }
  }

  /**
   * Returns attributes associated with community.
   * @param communityName Name of community
   * @return              Communities attributes
   */
  public synchronized Attributes getCommunityAttributes(String communityName) {
    if (log.isDebugEnabled())
      log.debug("getCommunityAttributes: community=" + communityName);
    ++calls;
    if (LAZY && !isValid(communityName) || !contains(communityName)) {
      update(communityName);
    }
    Community ce = (Community)cache.get(communityName);
    return ce.attrs;
  }


  /**
   * Returns attributes associated with specified community entity.
   * @param communityName  Entities parent community
   * @param entityName     Name of community entity
   * @return               Attributes associated with entity
   */
  public Attributes getEntityAttributes(String communityName,
                                        String entityName) {
    if (log.isDebugEnabled())
      log.debug("getEntityAttributes: entity=" + entityName +
        " community=" + communityName);
    ++calls;
    if (LAZY && !isValid(communityName) || !contains(communityName)) {
      update(communityName);
    }
    Community ce = (Community)cache.get(communityName);
    if (ce.entities.containsKey(entityName)) {
      return ((Entity)ce.entities.get(entityName)).attrs;
    } else {
      if (log.isDebugEnabled())
        log.debug("Community " + communityName + " does not contain entity " + entityName);
      return new BasicAttributes();
    }
  }

  protected void notifyListeners(final String communityName, int type, final String whatChanged) {
    if (log.isDebugEnabled())
      log.debug("notifyListeners:");
    Collection listeners = getListeners(communityName);
      fireListeners(new CommunityChangeEvent(communityName,
                                             type,
                                             whatChanged));
      if (LAZY) {
        setIsValid(communityName, false);
      } else { // EAGER cache mode, update cache now
        if (contains(communityName)) update(communityName);
      }
    super.notifyListeners(communityName, type, whatChanged);
  }


  private IncrementalSubscription changeNotifications;
  private UnaryPredicate changeNotificationPredicate = new UnaryPredicate() {
    public boolean execute (Object o) {
      return (o instanceof CommunityChangeNotification);
  }};


}
