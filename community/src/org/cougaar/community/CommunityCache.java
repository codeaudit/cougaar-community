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

import org.cougaar.core.service.community.Community;
import org.cougaar.core.service.community.Entity;
import org.cougaar.core.service.community.CommunityChangeEvent;
import org.cougaar.core.service.community.CommunityChangeListener;

import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.component.ServiceRevokedListener;
import org.cougaar.core.component.ServiceRevokedEvent;

import org.cougaar.core.service.ThreadService;
import org.cougaar.core.thread.Schedulable;

import org.cougaar.util.log.*;

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

/**
 * Maintains a local cache of Community objects.
 */
public class CommunityCache
  implements CommunityChangeListener {

  private static CommunityCache cache;

  private Logger logger = LoggerFactory.getInstance().createLogger(CommunityCache.class);
  private ServiceBroker serviceBroker;
  private Map communities = new HashMap();
  private Map listenerMap = new HashMap();
  private String cacheId;

  public static CommunityCache getCache(ServiceBroker serviceBroker) {
    if (cache == null) {
      cache = new CommunityCache(serviceBroker);
    }
    return cache;
  }

  private CommunityCache(ServiceBroker serviceBroker, String cacheId) {
    this.serviceBroker = serviceBroker;
    this.cacheId = cacheId;
  }

  private CommunityCache(ServiceBroker serviceBroker) {
    this(serviceBroker, "");
  }

  protected synchronized void add(Community community) {
    if (logger.isDebugEnabled()) {
      logger.debug(cacheId+": add:" +
                   " community=" + community.getName() +
                   " entities=" + entityNames(community.getEntities()));
    }
    communities.put(community.getName(), new CacheEntry(new Date(), community));
  }

  protected synchronized Community get(String name) {
    CacheEntry ce = (CacheEntry)communities.get(name);
    return (ce != null ? ce.community : null);
  }

  protected synchronized Date getTimeStamp(String name) {
    CacheEntry ce = (CacheEntry)communities.get(name);
    return ce.timeStamp;
  }

  protected synchronized Collection getExpired(long expirationPeriod) {
    long now = (new Date()).getTime();
    Collection expiredEntries = new Vector();
    for (Iterator it = communities.values().iterator(); it.hasNext();) {
      CacheEntry ce = (CacheEntry)it.next();
      if ((ce.timeStamp.getTime() + expirationPeriod) < now) {
        expiredEntries.add(ce.community.getName());
      }
    }
    return expiredEntries;
  }

  protected synchronized Community remove(String communityName) {
    if (logger.isDebugEnabled()) {
      logger.debug(cacheId+": remove:" +
                   " community=" + communityName);
    }
    CacheEntry ce = (CacheEntry)communities.remove(communityName);
    return (ce == null ? null : ce.community);
  }

  protected boolean contains(String name) {
    return communities.containsKey(name);
  }

  public synchronized Set listAll() {
    return new HashSet(communities.keySet());
  }

   /**
   * Searches community map for all ancestors of specified entity.
   * @param entityName
   * @param recursive If true all ancestors are retrieved, if false only immediate
   *                  parents
   * @return List of communities having specified community as a descendent
   */
  public List getAncestorNames(String entityName, boolean recursive) {
    List ancestors = new ArrayList();
    findAncestors(entityName, ancestors, recursive);
    return ancestors;
  }

  /**
   * Recursive search of community map for all ancestors of a specified entity.
   * @param communityName
   * @param ancestors
   */
  private synchronized void findAncestors(String entityName, List ancestors, boolean recursive) {
    Collection allCommunities = communities.values();
    for (Iterator it = allCommunities.iterator(); it.hasNext();) {
      CacheEntry ce = (CacheEntry)it.next();
      Community community = ce.community;
      if (community.hasEntity(entityName)) {
        String parent = community.getName();
        ancestors.add(parent);
        if (recursive) findAncestors(parent, ancestors, recursive);
      }
    }
  }

  /**
   * Determines if a local copy exists for all nested communities from a
   * specified root community.
   */
  private boolean allDescendentsFound(Community community) {
    Collection nestedCommunities =
        community.search("(Role=Member)", Community.COMMUNITIES_ONLY);
    for (Iterator it = nestedCommunities.iterator(); it.hasNext();) {
      Community nestedCommunity = (Community)it.next();
      if (!communities.containsKey(nestedCommunity.getName()) ||
          !allDescendentsFound(nestedCommunity)) return false;
    }
    return true;
  }

  /**
   * Get names of nested communities that are members of specfied community.
   */
  protected Collection getNestedCommunityNames(Community community) {
    Collection nestedCommunityNames = new Vector();
    Collection nestedCommunities =
        community.search("(Role=Member)", Community.COMMUNITIES_ONLY);
    for (Iterator it = nestedCommunities.iterator(); it.hasNext();) {
      Community nestedCommunity = (Community)it.next();
      nestedCommunityNames.add(nestedCommunity.getName());
    }
    return nestedCommunityNames;
  }

  /**
   * Searches all communities in cache for a community matching search filter
   */
  protected synchronized Set search(String filter) {
    if (communities.isEmpty())
      return null;
    Set matches = new HashSet();
    try {
      Filter f = new SearchStringParser().parse(filter);
      for (Iterator it = communities.values().iterator(); it.hasNext(); ) {
        CacheEntry ce = (CacheEntry)it.next();
        Community community = ce.community;
        if (f.match(community.getAttributes()))
          matches.add(community);
      }
    }
    catch (Exception ex) {
      System.out.println("Exception in search, filter=" + filter);
      ex.printStackTrace();
    }
    if (logger.isDebugEnabled())
      logger.debug(cacheId+": search: matches=" + entityNames(matches));
    return matches;
  }

  /**
   * Searches community for all entities matching search filter
   */
  protected Set search(String  communityName,
                       String  filter,
                       int     qualifier,
                       boolean recursive) {
    Community community = get(communityName);
    if (logger.isDebugEnabled()) {
      logger.debug(cacheId+": search:" +
                   " community=" + communityName +
                   " filter=" + filter +
                   " qualifier=" + community.qualifierToString(qualifier) +
                   " recursive=" + recursive);
    }
    if (community == null) return Collections.EMPTY_SET;
    if (recursive) {
      Set matches = new HashSet();
      recursiveSearch(community, filter, qualifier, matches);
      return matches;
    } else {  // Recursive search
      return community.search(filter, qualifier);
    }
  }

  private void recursiveSearch(Community community,
                               String    filter,
                               int       qualifier,
                               Set       matches) {
    if (community != null) {
      Collection entities = community.search(filter, qualifier);
      /*
      logger.debug(cacheId+": recursiveSearch:" +
                   " community=" + community.getName() +
                   " filter=" + filter +
                   " qualifier=" + qualifier +
                   " matches=" + entityNames(entities));
      */
      matches.addAll(entities);
      Collection nestedCommunities = community.search("(Role=Member)",
                                                      Community.COMMUNITIES_ONLY);
      /*
      logger.debug(cacheId+": recursiveSearch:" +
                   " community=" + community.getName() +
                   " nestedCommunities=" + entityNames(nestedCommunities));
      */
      for (Iterator it = nestedCommunities.iterator(); it.hasNext();) {
        String nestedCommunityName = ((Community)it.next()).getName();
        Community nestedCommunity = get(nestedCommunityName);
        if (nestedCommunity != null) {
          recursiveSearch(nestedCommunity, filter, qualifier, matches);
        }
      }
    }
  }

  // Converts a collection of entities to a compact string representation of names
  private String entityNames(Collection members) {
    StringBuffer sb = new StringBuffer("[");
    for (Iterator it = members.iterator(); it.hasNext();) {
      sb.append(it.next().toString() + (it.hasNext() ? "," : ""));
    }
    return(sb.append("]").toString());
  }


  /**
   * Invoke callback on each CommunityListener associated with named
   * community and its ancestors.  Provide community reference in callback
   * argument.
   * @param community Changed community
   */
  protected void notifyListeners(CommunityChangeEvent cce) {
    Set listenerSet = new HashSet();
    Set affectedCommunities = new HashSet();
    affectedCommunities.add(cce.getCommunityName());
    affectedCommunities.addAll(getAncestorNames(cce.getCommunityName(), true));
    for (Iterator it = affectedCommunities.iterator(); it.hasNext();) {
      Set listeners = getListeners((String)it.next());
      for (Iterator it1 = listeners.iterator(); it1.hasNext();) {
        listenerSet.add((CommunityChangeListener)it1.next());
      }
    }
    Set listeners = getListeners("ALL_COMMUNITIES");
    for (Iterator it = listeners.iterator(); it.hasNext();) {
      listenerSet.add((CommunityChangeListener)it.next());
    }
    if (logger.isDebugEnabled()) {
      logger.debug(cacheId+": notifyListeners:" +
                   " community=" + cce.getCommunityName() +
                   " numListeners=" + listenerSet.size());
    }
    fireCommunityChangeEvent(listenerSet,  cce);
  }

  protected void fireCommunityChangeEvent(CommunityChangeListener l,
                                          CommunityChangeEvent cce) {
    Set listeners = new HashSet();
    listeners.add(l);
    fireCommunityChangeEvent(listeners, cce);
  }

  protected void fireCommunityChangeEvent(final Set listeners,
                                          final CommunityChangeEvent cce) {
    ThreadService ts =
      (ThreadService)serviceBroker.getService(this, ThreadService.class, null);
    Schedulable notifierThread = ts.getThread(this, new  Runnable() {
      public void run() {
        for (Iterator it = listeners.iterator(); it.hasNext(); ) {
          ( (CommunityChangeListener) it.next()).communityChanged(cce);
        }
      }
    }, "CommunityNotificationThread");
    serviceBroker.releaseService(this, ThreadService.class, ts);
    notifierThread.start();
  }

  /**
   * Add listener to be notified when a change occurs to community.
   * @param l  Listener to be notified
   */
  protected void addListener(CommunityChangeListener l) {
    if (l != null) addListener(l.getCommunityName(), l);
  }

  protected synchronized void addListener(String communityName, CommunityChangeListener l) {
    if (l != null) {
      String cname = (communityName != null ? communityName : "ALL_COMMUNITIES");
      if (logger.isDebugEnabled()) {
        logger.debug(cacheId+": addListeners:" +
                     " community=" + cname);
      }
      synchronized (listenerMap) {
        Set listeners = (Set)listenerMap.get(cname);
        if (listeners == null) {
          listeners = new HashSet();
          listenerMap.put(cname, listeners);
        }
        listeners.add(l);
        // If listener is interested in communities which are already in cache
        // send an initial event
        if (cname.equals("ALL_COMMUNITIES")) {
          for (Iterator it = communities.values().iterator(); it.hasNext();) {
            CacheEntry ce = (CacheEntry)it.next();
            Community community = ce.community;
            fireCommunityChangeEvent(l, new CommunityChangeEvent(community,
                                                                 CommunityChangeEvent.ADD_COMMUNITY,
                                                                 community.getName()));
          }
        } else if (contains(cname)) {
          fireCommunityChangeEvent(l, new CommunityChangeEvent(get(cname),
                                                               CommunityChangeEvent.ADD_COMMUNITY,
                                                               cname));
        }
      }
    }
  }

  /**
   * Removes listener from change notification list.
   * @param l  Listener to be removed
   */
  protected void removeListener(CommunityChangeListener l) {
    logger.debug("removeListener: community=" + l.getCommunityName());
    synchronized (listenerMap) {
      Set listeners = (Set)listenerMap.get(l.getCommunityName());
      if (listeners == null) {
        listeners = new HashSet();
        listenerMap.put(l.getCommunityName(), listeners);
      }
      listeners.remove(l);
    }
  }

  /**
   * Gets listeners.
   * @return Set of CommunityListeners.
   */
  protected Set getListeners(String communityName) {
    synchronized (listenerMap) {
      Set listeners = (Set)listenerMap.get(communityName);
      if (listeners == null) {
        listeners = new HashSet();
        listenerMap.put(communityName, listeners);
      }
      return listeners;
    }
  }

  // CommunityChangeListener methods
  public String getCommunityName() { return "ALL_COMMUNITIES"; }

  public synchronized void communityChanged(CommunityChangeEvent cce) {
    Community affectedCommunity = cce.getCommunity();
    switch(cce.getType()) {
      case CommunityChangeEvent.ADD_COMMUNITY:
        add(affectedCommunity);
        break;
      case CommunityChangeEvent.REMOVE_COMMUNITY:
        remove(affectedCommunity.getName());
        break;
      default:
        CacheEntry ce = (CacheEntry)communities.get(affectedCommunity.getName());
        if (ce != null) {
          ce.timeStamp = new Date();
          ((CommunityImpl)ce.community).setAttributes(affectedCommunity.getAttributes());
          ((CommunityImpl)ce.community).setEntities(affectedCommunity.getEntities());
        } else {
          logger.warn("Update requested on non-existent community: community=" +
                      affectedCommunity.getName());
        }
        break;
    }
    if (cce.getType() >= 0) {
      if (logger.isDebugEnabled()) {
        logger.debug(cacheId+": " + cce.toString());
      }
      notifyListeners(cce);
    }
  }

  class CacheEntry {
    private Date timeStamp;
    private Community community;
    CacheEntry(Date timeStamp, Community community) {
      this.timeStamp = timeStamp;
      this.community = community;
    }
  }

}
