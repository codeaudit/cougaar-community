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
import org.cougaar.community.requests.SearchCommunity;

import org.cougaar.core.component.ServiceRevokedListener;
import org.cougaar.core.component.ServiceRevokedEvent;

import org.cougaar.community.RelayAdapter;

import org.cougaar.core.service.community.CommunityResponse;
import org.cougaar.core.service.community.CommunityChangeEvent;

import org.cougaar.community.CommunityCache;

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

import org.cougaar.core.service.BlackboardService;
import org.cougaar.core.blackboard.IncrementalSubscription;

import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.mts.SimpleMessageAddress;

import org.cougaar.core.component.ServiceBroker;

import org.cougaar.core.service.BlackboardService;
import org.cougaar.core.service.LoggingService;
import org.cougaar.core.service.UIDService;
import org.cougaar.core.service.wp.WhitePagesService;
import org.cougaar.core.service.wp.AddressEntry;
import org.cougaar.core.service.wp.Application;
import org.cougaar.core.service.SchedulerService;
import org.cougaar.core.service.AlarmService;

import org.cougaar.core.blackboard.BlackboardClient;

import org.cougaar.util.UnaryPredicate;

import org.cougaar.core.plugin.ComponentPlugin;

import EDU.oswego.cs.dl.util.concurrent.Semaphore;

/** A CommunityService is an API which may be supplied by a
 * ServiceProvider registered in a ServiceBroker that provides
 * access to community management capabilities.
 **/
public class CommunityServiceImpl extends ComponentPlugin
  implements CommunityService, java.io.Serializable {

  protected LoggingService log;

  private CommunityCache cache;

  //private List listeners = new ArrayList();

  private BlackboardService blackboardService = null;
  private UIDService uidService = null;

  private MessageAddress agentId;
  private ServiceBroker serviceBroker;

  /**
   * @deprecated use getInstance(ServiceBroker sb, MessageAddress addr).
   */
  public static CommunityService getInstance(ServiceBroker sb,
    MessageAddress addr, boolean useCache) {
      return getInstance(sb, addr);
  }

  /**
   * Returns CommunityService instance.
   */
  public static CommunityService getInstance(ServiceBroker sb,
      MessageAddress addr) {
      return new CommunityServiceImpl(sb, addr);
  }

  /**
   * Constructor.
   * @param sb       Reference to agent ServiceBroker
   * @param addr     Address of parent agent
   */
  protected CommunityServiceImpl(ServiceBroker sb, MessageAddress addr) {
    serviceBroker = sb;
    agentId = addr;
    this.log = getLoggingService();
    this.log = org.cougaar.core.logging.LoggingServiceWithPrefix.add(log, agentId + ": ");
    cache = new CommunityCache(agentId.toString() + ".cs");
    initBlackboardSubscriber();
  }


  /**
   * Request to create a community.  If the specified community does not
   * exist it will be created and the caller will become the community
   * manager.  It the community already exists a descriptor is obtained
   * from its community manager and returned.
   * @param communityName    Name of community to create
   * @param attrs            Attributes to associate with new community
   * @param crl              Listener to receive response
   */
  public void createCommunity(String                    communityName,
                              Attributes                attrs,
                              CommunityResponseListener crl) {
    publishCommunityRequest(new CreateCommunity(communityName,
                                                attrs,
                                                getUID()), crl);
  }

  /**
   * Request to add named community to local cache and register for update
   * notifications.
   * @param communityName  Community of interest, if null listener will receive
   *                       notification of changes in all communities
   * @param timeout        Time (in milliseconds) to wait for operation to
   *                       complete before returning response (-1 = wait forever)
   * @param crl            Listener to receive response
   */
  public void getCommunity(String                    communityName,
                           long                      timeout,
                           CommunityResponseListener crl) {
    if (cache.contains(communityName)) {
      crl.getResponse(new CommunityResponseImpl(CommunityResponse.SUCCESS,
                                                cache.get(communityName)));
    } else {
      publishCommunityRequest(new GetCommunity(communityName, getUID(), timeout),
                              crl);
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
    publishCommunityRequest(new ModifyAttributes(communityName,
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
    publishCommunityRequest(new JoinCommunity(communityName,
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
    publishCommunityRequest(new LeaveCommunity(communityName,
                                               entityName,
                                               getUID()), crl);
  }

  /**
   * Initiates a community search operation. The results are provided via a
   * call back to a specified CommunitySearchListener.
   * @param communityName   Name of community to search
   * @param searchFilter    JNDI compliant search filter
   * @param recursiveSearch True for recursive search into nested communities
   *                        [false = search top community only]
   * @param resultQualifier Type of entities to return in result [ALL_ENTITIES,
   *                        AGENTS_ONLY, or COMMUNITIES_ONLY]
   * @param crl             Callback object to receive search results
   */
  public void searchCommunity(String                    communityName,
                              String                    searchFilter,
                              boolean                   recursiveSearch,
                              int                       resultQualifier,
                              CommunityResponseListener crl) {
    publishCommunityRequest(new SearchCommunity(communityName,
                                                searchFilter,
                                                recursiveSearch,
                                                resultQualifier,
                                                getUID()), crl);
  }

  /**
   * Returns a list of all communities of which caller is a member.
   * @param allLevels Set to false if the list should contain only those
   *                  communities in which the caller is explicitly
   *                  referenced.  If true the list will also include those
   *                  communities in which the caller is implicitly a member
   *                  as a result of community nesting.
   * @param crl       Callback object to receive search results
   */
  public void getParentCommunities(boolean                   allLevels,
                                   CommunityResponseListener crl) {
    final Set ancestors = cache.getAncestorNames(agentId.toString(), allLevels);
    crl.getResponse(new CommunityResponseImpl(CommunityResponse.SUCCESS, ancestors));
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
   * Remove listener for CommunityChangeEvents.
   * @param l  Listener
   */
  public void removeListener(CommunityChangeListener l) {
    cache.removeListener(l);
  }

  private Map myRequests = new HashMap();
  protected void publishCommunityRequest(final CommunityRequest    cr,
                                         CommunityResponseListener crl) {
    Thread communityRequestThread = new Thread("CommunityRequestThread") {
      public void run() {
        try {
          if (log.isDebugEnabled()) {
            log.debug("Publishing CommunityRequest, type=" + cr.getRequestType() +
                      " community=" + cr.getCommunityName());
          }
          BlackboardService bbs = getBlackboardService(serviceBroker);
          bbs.openTransaction();
          bbs.publishAdd(cr);
          bbs.closeTransaction();
          if (log.isDebugEnabled()) {
            log.debug("Done Publishing CommunityRequest, type=" +
                      cr.getRequestType() +
                      " community=" + cr.getCommunityName());
          }
        } catch (Exception ex) {
          log.error("Exception in publishCommunityRequest", ex);
        }
      }
    };
    myRequests.put(cr.getUID(), crl);
    log.debug("Adding request to myRequests:" +
              " request=" + cr.getRequestType() +
              " uid=" + cr.getUID());
    communityRequestThread.start();
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
    final Status status = new Status(false);
    final Semaphore s = new Semaphore(0);
    getCommunity(communityName, 0, new CommunityResponseListener() {
      public void getResponse(CommunityResponse resp) {
        status.setValue(resp != null &&
                        resp.getStatus() == CommunityResponse.SUCCESS);
        s.release();
      }
    });
    try { s.acquire(); } catch (Exception ex) {}
    return status.getValue();
  }


  /**
   * Lists all communities in White pages.
   * @return  Collection of community names
   */
  public Collection listAllCommunities() {
    Collection communityNames = new Vector();
    try{
      WhitePagesService wps = (WhitePagesService)serviceBroker.getService(this, WhitePagesService.class, null);
      AddressEntry[] entrys = wps.get(".");
      for(int i=0; i<entrys.length; i++) {
        if(entrys[i].getApplication().toString().equals("community")){
          communityNames.add(entrys[i].getName());
        }
      }
    }catch(Exception e){log.error("Error in listAllCommunities: " + e);}
    return communityNames;
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
      getCommunity(communityName, -1, new CommunityResponseListener() {
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
    getCommunity(communityName, -1, crl);
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

  private ModificationItem[] getAttributeModificationItems(Attributes olds, Attributes news) {
    ModificationItem[] items = new ModificationItem[olds.size() + news.size()];
    int count = 0;
    for(NamingEnumeration enums = olds.getAll(); enums.hasMoreElements();)
    {
      items[count++] = new ModificationItem(DirContext.REMOVE_ATTRIBUTE, (Attribute)enums.nextElement());
    }
    for(NamingEnumeration enums = news.getAll(); enums.hasMoreElements();) {
      items[count++] = new ModificationItem(DirContext.ADD_ATTRIBUTE, (Attribute)enums.nextElement());
    }
    return items;
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
    getCommunity(communityName, -1, new CommunityResponseListener(){
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
      getCommunity(communityName, -1, new CommunityResponseListener() {
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
    getCommunity(communityName, -1, crl);
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
    final Semaphore s = new Semaphore(0);
    final ResultsHolder rh = new ResultsHolder();
    searchCommunity(null, filter, true, Community.ALL_ENTITIES, new CommunityResponseListener() {
      public void getResponse(CommunityResponse resp){
        if (resp != null) {
          Collection entities = (Collection) resp.getContent();
          Collection names = new ArrayList();
          for (Iterator it = entities.iterator(); it.hasNext();) {
            names.add(((Entity)it.next()).getName());
          }
          rh.setResults(names);
        }
        s.release();
      }
    });
    try {
      s.acquire();
    } catch(InterruptedException e) {
      log.error("Error in search: " + e);
    }
    return rh.getResults();
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
      getCommunity(communityName, -1, crl);
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
      getCommunity(communityName, 0, new CommunityResponseListener() {
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
    //if(!member.equals(agentId)) return new Vector();
    final ResultsHolder rh = new ResultsHolder();
    final Semaphore s = new Semaphore(0);
    getParentCommunities(true, new CommunityResponseListener(){
      public void getResponse(CommunityResponse resp){
        rh.setResults((Collection)resp.getContent());
        s.release();
      }
    });
    try{s.acquire();}catch(Exception e){log.error("Error in listParentCommunities: " + e);}
    return rh.getResults();

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
    getCommunity(communityName, -1, new CommunityResponseListener(){
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


  /**
   * Returns a list of all external roles supported by the specified community.
   * @param communityName Community name
   * @return              Collection of role names
   */
  public Collection getCommunityRoles(String communityName) {
    final CommunityHolder ch = new CommunityHolder();
    final Semaphore s = new Semaphore(0);
    getCommunity(communityName, -1, new CommunityResponseListener(){
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
    getCommunity(communityName, -1, cl);
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
    getCommunity(communityName, -1, cl);
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

  private BlackboardClient blackboardClient;
  /**
   * Gets reference to Blackboard service.
   */
  protected BlackboardService getBlackboardService(ServiceBroker sb) {
    if (blackboardService == null) {
      while (!sb.hasService(org.cougaar.core.service.BlackboardService.class)) {
        try { Thread.sleep(500); } catch (Exception ex) {}
        log.debug("Waiting for BlackboardService");
      }
      if (blackboardClient == null) {
        blackboardClient = new MyBlackboardClient();
      }
      blackboardService = (BlackboardService)sb.getService(blackboardClient,
        BlackboardService.class, null);
    }
    return blackboardService;
  }

  private UID getUID() {
    return getUIDService(serviceBroker).nextUID();
  }

  /**
   * Gets reference to UID service.
   */
  protected UIDService getUIDService(ServiceBroker sb) {
    if (uidService == null) {
      while (!sb.hasService(org.cougaar.core.service.UIDService.class)) {
        try { Thread.sleep(500); } catch (Exception ex) {}
        log.debug("Waiting for UIDService");
      }
      uidService = (UIDService)sb.getService(this, UIDService.class, null);
    }
    return uidService;
  }


  /**
   * Notifies interested agents that a change has occurred in community.
   */
  protected void notifyListeners(final String communityName, final int type,
      final String whatChanged) {
  }


  /**
   * Initialize a Blackboard subscriber to receive changed Community Requests.
   */
  private void initBlackboardSubscriber() {
    Thread initThread = new Thread("CommunityService-BBSubscriberInit") {
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
        CommunityServiceImpl.this.start();
      }
    };
    initThread.start();
  }

  public void setupSubscriptions() {
    // Subscribe to CommunityRequests
    getBlackboardService().subscribe(communityRequestPredicate);
    AddChangeListener acl =
      new AddChangeListener("ALL_COMMUNITIES", getUID(), cache);
    myRequests.put(acl.getUID(), null);
    getBlackboardService(serviceBroker).publishAdd(acl);
  }
  public void execute() {
    BlackboardService bbs = getBlackboardService(serviceBroker);
    Collection communityRequests = bbs.query(communityRequestPredicate);
    for (Iterator it = communityRequests.iterator(); it.hasNext();) {
      CommunityRequest cr = (CommunityRequest)it.next();
      //log.debug("CommunityRequestPredicate cr=" + cr + " response=" + cr.getResponse());
      if (myRequests.containsKey(cr.getUID())) {
        if (cr.getResponse() != null) {
          // Remove request
          if (log.isDebugEnabled()){
            log.debug("Removing CommunityRequest:" +
                      " request=" + cr.getRequestType() +
                      " uid=" + cr.getUID() +
                      " removingRequest=" + bbs.isTransactionOpen());
          }
          CommunityResponseListener crl =
            (CommunityResponseListener)myRequests.remove(cr.getUID());
          if (crl != null) {
            crl.getResponse(cr.getResponse());
          }
          if (bbs.isTransactionOpen()) {
            bbs.publishRemove(cr);
          }
        }
      }
    }
  }

  /**
   * Predicate for Community Requests that are published locally.  These
   * requests are used to create CommunityManagerRequest relays that are
   * forwarded to the appropriate community manager.
   */
  private UnaryPredicate communityRequestPredicate = new UnaryPredicate() {
    public boolean execute (Object o) {
      return (o instanceof CommunityRequest);
  }};


  /**
   * Gets reference to LoggingService.
   */
  private LoggingService getLoggingService() {
    return (LoggingService)serviceBroker.getService(
        this, LoggingService.class, null);
  }

  class MyBlackboardClient implements BlackboardClient {

    public long currentTimeMillis() {
      return System.currentTimeMillis();
    }

    public String getBlackboardClientName() {
      return "CommunityService";
    }

    public boolean triggerEvent(Object event) {
      return false;
    }

  }

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

  class ResultsHolder {
    Collection results = Collections.EMPTY_SET;
    ResultsHolder () {}
    void setResults(Collection c) { results = c; }
    Collection getResults() { return results; }
  }

}
