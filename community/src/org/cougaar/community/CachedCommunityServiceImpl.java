package org.cougaar.community;

import java.util.*;
import javax.naming.*;
import javax.naming.directory.*;

import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.agent.ClusterIdentifier;
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

/**
 * Extends CommunityServiceImpl to provide local caching of frequently accessed
 * community information in NameServer.
 */

public class CachedCommunityServiceImpl extends CommunityServiceImpl {

  // Class defining Community entry in cache
  class Community {
    String name;
    Attributes attrs;
    Map entities;

    private Community(String name, Attributes attrs) {
      this.name = name;
      this.attrs = attrs;
      this.entities = new HashMap();
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
    if (log.isDebugEnabled()) log.debug("setupSubscriptions()");
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
      if (log.isDebugEnabled())
        log.debug("Received added CommunityChangeNotification: community=" +
          communityName + " source=" + ccn.getSource());
      synchronized (this) {
        if (contains(communityName)) update(communityName);
      }
    }

    enum  = changeNotifications.getChangedList();
    while (enum.hasMoreElements()) {
      CommunityChangeNotification ccn =
        (CommunityChangeNotification)enum.nextElement();
      String communityName = ccn.getCommunityName();
      if (log.isDebugEnabled())
        log.debug("Received changed CommunityChangeNotification: community=" +
          communityName + " source=" + ccn.getSource());
      synchronized (this) {
        if (contains(communityName)) update(communityName);
      }
    }
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
  private void addCommunity(String communityName, Attributes attrs, Collection entitiesToRetain) {
    Community ce = (Community) cache.get(communityName);
    if (ce == null) {
      if (log.isDebugEnabled())
        log.debug("Adding community: community=" + communityName);
      addListener(agentId, communityName);
      cache.put(communityName, new Community(communityName, attrs));
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
    fireListeners(new CommunityChangeEvent(communityName,
                                           CommunityChangeEvent.REMOVE_COMMUNITY,
                                           communityName));
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
      fireListeners(new CommunityChangeEvent(communityName,
                                             CommunityChangeEvent.ADD_ENTITY,
                                             entityName));
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
      fireListeners(new CommunityChangeEvent(communityName,
                                             CommunityChangeEvent.REMOVE_ENTITY,
                                             entityName));
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
    if (!contains(communityName)) update(communityName);
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
      if (communityExists(communityName)) {
        Attributes attrs = super.getCommunityAttributes(communityName);
        Collection entities = listEntities(communityName);
        addCommunity(communityName, attrs, entities);
        //System.out.println("Community " + communityName + " has " +
        //  entities.size() + " entities");
        for (Iterator it = entities.iterator(); it.hasNext();) {
          String entityName = (String)it.next();
          attrs = super.getEntityAttributes(communityName, entityName);
          log.debug("update: entity="+ entityName + " attributes=" + attrsToString(attrs));
          Object obj = super.lookup(communityName, entityName);
          addEntity(communityName, entityName, obj, attrs);
        }
        fireListeners(new CommunityChangeEvent(communityName,
                                               CommunityChangeEvent.ADD_COMMUNITY,
                                               communityName));
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
    if (!contains(communityName)) update(communityName);
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
    if (!contains(communityName)) update(communityName);
    Community ce = (Community)cache.get(communityName);
    if (ce.entities.containsKey(entityName)) {
      return ((Entity)ce.entities.get(entityName)).attrs;
    } else {
      log.debug("Community " + communityName + " does not contain entity " + entityName);
      return new BasicAttributes();
    }
  }

  protected void notifyListeners(final String communityName, final String message) {
    Collection listeners = getListeners(communityName);
    if (listeners.contains(agentId)) update(communityName);
    super.notifyListeners(communityName, message);
  }

  private IncrementalSubscription changeNotifications;
  private UnaryPredicate changeNotificationPredicate = new UnaryPredicate() {
    public boolean execute (Object o) {
      return (o instanceof CommunityChangeNotification);
      /*
      if (o instanceof CommunityChangeNotification) {
        CommunityChangeNotification ccn = (CommunityChangeNotification)o;
        return (ccn.getTargets() == Collections.EMPTY_SET);
      }
      return false;
      */
  }};

}