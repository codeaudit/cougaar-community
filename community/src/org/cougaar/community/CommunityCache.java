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

import org.cougaar.core.naming.Filter;
import org.cougaar.core.naming.SearchStringParser;

import org.cougaar.util.log.*;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

/**
 * Maintains a local cache of Community objects.
 */
public class CommunityCache
  implements CommunityChangeListener {

  private Logger logger = LoggerFactory.getInstance().createLogger(CommunityCache.class);

  private Map communities = new HashMap();
  private Map listenerMap = new HashMap();
  private String cacheId;

  public CommunityCache(String cacheId) {
    this.cacheId = cacheId;
  }

  public CommunityCache() {
    this("");
  }

  protected void add(Community community) {
    if (logger.isDebugEnabled()) {
      logger.debug(cacheId+": add:" +
                   " community=" + community.getName() +
                   " entities=" + entityNames(community.getEntities()));
    }
    communities.put(community.getName(), community);
  }

  protected Community get(String name) {
    return (Community)communities.get(name);
  }

  protected Community remove(String communityName) {
    if (logger.isDebugEnabled()) {
      logger.debug(cacheId+": remove:" +
                   " community=" + communityName);
    }
    return (Community)communities.remove(communityName);
  }

  protected boolean contains(String name) {
    return communities.containsKey(name);
  }

   /**
   * Searches community map for all ancestors of specified entity.
   * @param entityName
   * @param recursive If true all ancestors are retrieved, if false only immediate
   *                  parents
   * @return Set of communities having specified community as a descendent
   */
  public Set getAncestorNames(String entityName, boolean recursive) {
    Set ancestors = new HashSet();
    findAncestors(entityName, ancestors, recursive);
    return ancestors;
  }

  /**
   * Recursive search of community map for all ancestors of a specified entity.
   * @param communityName
   * @param ancestors
   */
  private void findAncestors(String entityName, Set ancestors, boolean recursive) {
    Collection allCommunities = communities.values();
    for (Iterator it = allCommunities.iterator(); it.hasNext();) {
      Community community = (Community)it.next();
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
  protected Set search(String filter) {
    if (logger.isDebugEnabled()) {
      logger.debug(cacheId + ": search:" +
                   " filter=" + filter);
    }
    if (communities.isEmpty())
      return Collections.EMPTY_SET;
    Set matches = new HashSet();
    try {
      Filter f = new SearchStringParser().parse(filter);
      for (Iterator it = communities.values().iterator(); it.hasNext(); ) {
        Community community = (Community) it.next();
        if (f.match(community.getAttributes()))
          matches.add(community);
      }
    }
    catch (Exception ex) {
      System.out.println("Exception in search, filter=" + filter);
      ex.printStackTrace();
    }
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
    for (Iterator it = listenerSet.iterator(); it.hasNext();) {
      ((CommunityChangeListener)it.next()).communityChanged(cce);
    }
  }


  /**
   * Add listener to be notified when a change occurs to community.
   * @param l  Listener to be notified
   */
  protected void addListener(CommunityChangeListener l) {
    addListener(l.getCommunityName(), l);
  }

  protected void addListener(String communityName, CommunityChangeListener l) {
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
            Community community = (Community)it.next();
            l.communityChanged(new CommunityChangeEvent(community,
                                                        CommunityChangeEvent.ADD_COMMUNITY,
                                                        community.getName()));
          }
        } else if (contains(cname)) {
          l.communityChanged(new CommunityChangeEvent(get(cname),
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

  public void communityChanged(CommunityChangeEvent cce) {
    Community affectedCommunity = cce.getCommunity();
    if (logger.isDebugEnabled()) {
      logger.debug(cacheId+": " + cce.toString());
    }
    switch(cce.getType()) {
      case CommunityChangeEvent.ADD_COMMUNITY:
        add(affectedCommunity);
        break;
      case CommunityChangeEvent.REMOVE_COMMUNITY:
        remove(affectedCommunity.getName());
        break;
    }
    notifyListeners(cce);
  }


}