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

import org.cougaar.core.service.BlackboardService;
import org.cougaar.core.service.AlarmService;
import org.cougaar.core.service.LoggingService;
import org.cougaar.core.service.UIDService;

import org.cougaar.core.service.wp.AddressEntry;
import org.cougaar.core.service.wp.Application;
import org.cougaar.core.service.wp.Cert;
import org.cougaar.core.service.wp.WhitePagesService;

import org.cougaar.core.agent.service.alarm.Alarm;

import org.cougaar.util.UnaryPredicate;

import java.net.URI;
import java.net.URISyntaxException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
public class CommunityManager {

  private static final String URN_PREFIX = "urn";
  private static final String NAMESPACE_IDENTIFIER = "agent";
  private static final String URN_PREFIX_AND_NID = URN_PREFIX + ":" +
                                                   NAMESPACE_IDENTIFIER + ":";

  // Timeout for calls to WhitePagesService
  private static final long WPS_TIMEOUT = 60000;

  // Defines frequency of White Pages read to verify that this agent is still
  // manager for community
  private static final long TIMER_INTERVAL = 60000;

  // Defines how long CommunityDescriptor updates should be aggregated before
  // sending to interested agents.
  private static final long SEND_INTERVAL  = 10000;

  private MessageAddress agentId;
  private String communityName;
  private LoggingService logger;
  private ServiceBroker serviceBroker;
  private BlackboardService bbs;
  private UIDService uidService;

  // Map of timestamps identifying last time that a CommunityDescriptor was
  // sent to interested agents.
  private Map communityTimestamps = new HashMap();

  private Set communityDescriptorsToSend = new HashSet();

  /**
   * Constructor
   * @param agentId  Id of host agent
   * @param bbs      Agents blackboard service
   * @param sb       Agents service broker
   */
  public CommunityManager(MessageAddress agentId,
                          BlackboardService bbs,
                          ServiceBroker sb) {
    this.agentId = agentId;
    this.bbs = bbs;
    this.serviceBroker = sb;
    logger =
      (LoggingService)serviceBroker.getService(this, LoggingService.class, null);
    logger = org.cougaar.core.logging.LoggingServiceWithPrefix.add(logger, agentId + ": ");
    uidService = (UIDService)serviceBroker.getService(this, UIDService.class, null);
    AlarmService as = null;
    try {
      as = (AlarmService)serviceBroker.getService(this,
                                                  AlarmService.class,
                                                  null);
      as.addRealTimeAlarm(new ManagerCheckTimer(TIMER_INTERVAL));
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      if (as != null) {
        serviceBroker.releaseService(this, AlarmService.class, as);
      }
    }
    // Re-publish any CommunityDescriptor Relays found on BB
    //   If any are found we've rehydrated in which case the CommunityDescriptor
    //   may be out of date.  Clients are responsible for checking the
    //   accuracy of contents and resubmitting requests to correct.
    for (Iterator it = bbs.query(communityDescriptorPredicate).iterator(); it.hasNext();) {
      RelayAdapter ra = (RelayAdapter)it.next();
      CommunityDescriptor cd = (CommunityDescriptor)ra.getContent();
      assertCommunityManagerRole(cd.getName());
      updateCommunityDescriptor(cd.getName());
    }
  }

  /**
   * Processes CommunityManagerRequests.
   * @param cr CommunityManagerRequest
   */
  public void processRequest(CommunityManagerRequest cmr) {
    if (logger.isDebugEnabled())
      logger.debug("CommunityManagerRequest:" + cmr.getRequestTypeAsString() +
        " community=" + cmr.getCommunityName() +
        " source=" + cmr.getSource());
    String communityName = cmr.getCommunityName();
    RelayAdapter ra = getManagedCommunityRelayAdapter(communityName);
    if (ra == null) {
      if (agentId.equals(findManager(communityName))) {
        // This agent has likely been killed/restarted before CommunityDescriptor was
        // persisted.
        // A new CommunityDescriptor relay needs to be created
        logger.debug("Re-creating CommunityDescriptor:" +
                  " community=" + communityName +
                  " reason=restart prior to persist");
        CommunityDescriptor cd = new CommunityDescriptorImpl(agentId,
            new CommunityImpl(communityName),
            uidService.nextUID());
        ra = new RelayAdapter(cd.getSource(), cd, cd.getUID());
        ra.addTarget(agentId);
        logger.debug("PublishAdd CommunityDescriptor:" +
                     " targets=" + RelayAdapter.targetsToString(ra) +
                     " community=" + cd.getCommunity().getName() +
                     " entities=" + entityNames(cd.getCommunity().getEntities()) +
                     " cdUid=" + cd.getUID() +
                     " raUid=" + ra.getUID());
        processRequest(ra, cmr);
        bbs.publishAdd(ra);
        updateTimestamp(communityName);
      } else {
        logger.error("RelayAdapter is null:" +
                     " community=" + cmr.getCommunityName() +
                     " source=" + cmr.getSource() +
                     " request=" + cmr.getRequestTypeAsString());
      }
    } else {
      processRequest(ra, cmr);
      // Update clients CommunityDescriptor
      updateCommunityDescriptor(communityName);
    }
  }

  private void processRequest (RelayAdapter ra, CommunityManagerRequest cmr) {
    MessageAddress requester = cmr.getSource();
    CommunityDescriptorImpl cd = (CommunityDescriptorImpl) ra.getContent();
    switch (cmr.getRequestType()) {
      case CommunityManagerRequest.JOIN:
        if (cmr.getEntity() != null) {
          //boolean requesterIsJoiner = (cmr.getEntity().getName().equals(requester.toString()));
          //boolean joinerInCommunity = cd.getCommunity().hasEntity(cmr.getEntity().getName());
          //boolean requesterInCommunity = cd.getCommunity().hasEntity(requester.toString());
          //if (requesterIsJoiner || (requesterInCommunity && !joinerInCommunity)) {
          String entitiesBeforeAdd = "";
          if (logger.isDebugEnabled()) {
            entitiesBeforeAdd = entityNames(cd.getCommunity().getEntities());
          }
          cd.getCommunity().addEntity(cmr.getEntity());
          if (logger.isDebugEnabled()) {
            logger.debug("Add entity: " +
                         " community=" + cd.getName() +
                         " entity=" + cmr.getEntity().getName() +
                         " before=" + entitiesBeforeAdd +
                         " after=" +
                         entityNames(cd.getCommunity().getEntities()));
          }
          cd.setChangeType(CommunityChangeEvent.REMOVE_ENTITY);
          cd.setChangeType(CommunityChangeEvent.ADD_ENTITY);
          cd.setWhatChanged(cmr.getEntity().getName());
          ra.addTarget(cmr.getSource());
          //}
        }
        break;
      case CommunityManagerRequest.LEAVE:
        if (cd.getCommunity().hasEntity(requester.toString()) &&
            cmr.getEntity() != null &&
            cd.getCommunity().hasEntity(cmr.getEntity().getName())) {
          String entitiesBeforeRemove = "";
          if (logger.isDebugEnabled()) {
            entitiesBeforeRemove = entityNames(cd.getCommunity().getEntities());
          }
          cd.getCommunity().removeEntity(cmr.getEntity().getName());
          if (logger.isDebugEnabled()) {
            logger.debug("Removing entity: " +
                         " community=" + cd.getName() +
                         " entity=" + cmr.getEntity().getName() +
                         " before=" + entitiesBeforeRemove +
                         " after=" +
                         entityNames(cd.getCommunity().getEntities()));
          }
          cd.setChangeType(CommunityChangeEvent.REMOVE_ENTITY);
          cd.setWhatChanged(cmr.getEntity().getName());
        }
        break;
      case CommunityManagerRequest.GET_COMMUNITY_DESCRIPTOR:
        ra.addTarget(cmr.getSource());
        break;
      case CommunityManagerRequest.MODIFY_ATTRIBUTES:
        if (cd.getCommunity().hasEntity(requester.toString())) {
          if (cd.getCommunity().getName().equals(cmr.getEntity().getName())) {
            // modify community attributes
            Attributes attrs = cd.getCommunity().getAttributes();
            if (logger.isDebugEnabled()) {
              logger.debug("Modifying community attributes: " +
                           " community=" + cd.getName() +
                           " before=" + attrsToString(attrs));
            }
            applyAttrMods(attrs, cmr.getAttributeModifications());
            if (logger.isDebugEnabled()) {
              logger.debug("Modifying community attributes: " +
                           " community=" + cd.getName() +
                           " after=" + attrsToString(attrs));
            }
            cd.setChangeType(CommunityChangeEvent.
                             COMMUNITY_ATTRIBUTES_CHANGED);
            cd.setWhatChanged(cd.getCommunity().getName());
          } else {
            // modify attributes of a community entity
            Entity entity = cd.getCommunity().getEntity(cmr.getEntity().
                getName());
            if (entity != null) {
              Attributes attrs = entity.getAttributes();
              if (logger.isDebugEnabled()) {
                logger.debug("Modifying entity attributes: " +
                             " community=" + cd.getName() +
                             " entity=" + cmr.getEntity().getName() +
                             " before=" + attrsToString(attrs));
              }
              applyAttrMods(attrs, cmr.getAttributeModifications());
              if (logger.isDebugEnabled()) {
                logger.debug("Modifying entity attributes: " +
                             " community=" + cd.getName() +
                             " entity=" + cmr.getEntity().getName() +
                             " after=" + attrsToString(attrs));
              }
              cd.setChangeType(CommunityChangeEvent.ENTITY_ATTRIBUTES_CHANGED);
              cd.setWhatChanged(entity.getName());
            }
          }
        }
        break;
    }
    cmr.setResponse(new CommunityResponseImpl(CommunityResponse.SUCCESS,
                                        cd.getCommunity()));
    if (logger.isDebugEnabled()) {
      logger.debug("Publishing response to CommunityManagerRequest" +
                   " target=" + cmr.getSource() +
                   " request=" + cmr.getRequestTypeAsString() +
                   " id=" + cmr.getUID() +
                   " response=" + cmr.getResponse());
    }
    bbs.publishChange(cmr);
  }

  private void updateCommunityDescriptor(String communityName) {
    if(communityDescriptorsToSend.add(communityName)) {
      AlarmService as = (AlarmService) serviceBroker.getService(this,
          AlarmService.class,
          null);
      as.addRealTimeAlarm(new CommunityDescriptorSendTimer(communityName, SEND_INTERVAL));
      serviceBroker.releaseService(this, AlarmService.class, as);
    }
  }

  /**
   * Apply attribute modifications.
   */
  private void applyAttrMods(Attributes attrs, ModificationItem[] mods) {
    for (int i = 0; i < mods.length; i++) {
      switch(mods[i].getModificationOp()) {
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
   * isn't found in WP a null value is returned.
   * @param communityName
   * @return MessageAddress to agent registered as community manager
   */
  public MessageAddress findManager(String communityName) {
    //logger.debug("findManager: community=" + communityName);
    MessageAddress communityManager = null;
    if (communityName != null) {
      WhitePagesService wps =
        (WhitePagesService) serviceBroker.getService(this, WhitePagesService.class, null);
      try {
        AddressEntry ae[] = wps.get(communityName, WPS_TIMEOUT);
        if (ae.length > 0) {
          communityManager = extractAgentIdFromURI(ae[0].getAddress());
        }
      } catch (Exception ex) {
        ex.printStackTrace();
      } finally {
        serviceBroker.releaseService(this, WhitePagesService.class, wps);
      }
    }
    return communityManager;
  }

  /**
   * Asserts community manager role by binding address to community name in
   * White Pages
   * @param communityName Community to manage
   */
  public void assertCommunityManagerRole(String communityName) {
    logger.debug("assertCommunityManagerRole: agent=" + agentId.toString() + " community=" + communityName);
    WhitePagesService wps =
        (WhitePagesService) serviceBroker.getService(this, WhitePagesService.class, null);
    URI cmUri = null;
    try {
      cmUri = new java.net.URI(URN_PREFIX + ":" +
                               NAMESPACE_IDENTIFIER + ":" +
                               agentId);
      AddressEntry communityAE =
          new AddressEntry(communityName,
                           Application.getApplication("community"),
                           cmUri,
                           Cert.NULL,
                           Long.MAX_VALUE);
      MessageAddress communityManager = findManager(communityName);

      // Bind this agent as manager for community
      if (communityManager != null) {
        //logger.error("Invalid request to create multiple CommunityManagers " +
        //  "for community " + communityName);
        wps.rebind(communityAE, WPS_TIMEOUT);
      } else {
        try {
          wps.bind(communityAE, WPS_TIMEOUT);
        } catch (Throwable ex) { // probably due to another agent binding since our check
          logger.debug(
              "Unable to bind agent as community manager, attempting rebind:" +
              " error=" + ex.getMessage() +
              " agent=" + agentId +
              " community=" + communityName);
          wps.rebind(communityAE, WPS_TIMEOUT);
        }
      }
      logger.debug("Managing community " + communityName);
    } catch (URISyntaxException use) {
      logger.error("Invalid community manager URI: uri=" + cmUri);
    } catch (Throwable ex) {
      logger.error("Unable to (re)bind agent as community manager:" +
                   " error=" + ex.getMessage() +
                   " agent=" + agentId +
                   " community=" + communityName);
    } finally {
      serviceBroker.releaseService(this, WhitePagesService.class, wps);
    }
  }

  /**
   * Adds a community to be managed by this community manager.
   * @param community Community to manage
   */
  public void addCommunity(Community community) {
    if (logger.isDebugEnabled()) {
      logger.debug("addCommunity - name=" + community.getName());
    }
    String communityName = community.getName();
    if (communityName != null) {
      if (getManagedCommunityRelayAdapter(communityName) != null) {
        logger.error("Invalid request to create multiple CommunityManagers " +
                     "for community " + communityName);
      } else {
        assertCommunityManagerRole(communityName);
        CommunityDescriptor cd = new CommunityDescriptorImpl(agentId, community,
            uidService.nextUID());
        RelayAdapter ra = new RelayAdapter(cd.getSource(), cd, cd.getUID());
        ra.addTarget(agentId);
        logger.debug("PublishAdd CommunityDescriptor:" +
                     " targets=" + RelayAdapter.targetsToString(ra) +
                     " community=" + cd.getCommunity().getName() +
                     " entities=" + entityNames(cd.getCommunity().getEntities()) +
                     " cdUid=" + cd.getUID() +
                     " raUid=" + ra.getUID());
        bbs.publishAdd(ra);
        updateTimestamp(cd.getName());
      }
    }
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

  private final int urnPrefixLen = URN_PREFIX.length() +
                                          NAMESPACE_IDENTIFIER.length() +
                                          2;  // add 2 for colons
  private MessageAddress extractAgentIdFromURI(URI uri) {
    String urn = uri.toString();
    if (urn.startsWith(URN_PREFIX + ":" + NAMESPACE_IDENTIFIER + ":"))
      return SimpleMessageAddress.getSimpleMessageAddress(
        urn.substring(urnPrefixLen, urn.length()));
    else
      return null;
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

  private void updateTimestamp(String communityName) {
    communityTimestamps.put(communityName, new Date());
  }

  /**
   * Get RelayAdapter associated with a managed community.
   */
  private RelayAdapter getManagedCommunityRelayAdapter(String communityName) {
    for (Iterator it = bbs.query(communityDescriptorPredicate).iterator(); it.hasNext();) {
      RelayAdapter ra = (RelayAdapter)it.next();
      CommunityDescriptor cd = (CommunityDescriptor)ra.getContent();
      if (cd.getName().equals(communityName)) return ra;
    }
    return null;
  }

  private UnaryPredicate communityDescriptorPredicate = new UnaryPredicate() {
    public boolean execute (Object o) {
      return (o instanceof RelayAdapter &&
              ((RelayAdapter)o).getContent() instanceof CommunityDescriptor);
      }
   };

  /**
   * Periodically check the WPS to verity that this agent is still identified
   * as the community manager for all communities it thinks it's managing.  If
   * not it removes the associated CommunityDescriptor relays.
   */
  private class ManagerCheckTimer implements Alarm {
    private long expirationTime = -1;
    private boolean expired = false;
    private AlarmService as = null;
    private WhitePagesService wps = null;
    public ManagerCheckTimer (long delay) {
      expirationTime = delay + System.currentTimeMillis();
    }

    public void expire() {
      if (!expired) {
        try {
          wps = (WhitePagesService) serviceBroker.getService(this,
              WhitePagesService.class,
              null);
          bbs.openTransaction();
          for (Iterator it = bbs.query(communityDescriptorPredicate).iterator();
               it.hasNext(); ) {
            RelayAdapter ra = (RelayAdapter) it.next();
            String communityName = ( (CommunityDescriptor) ra.getContent()).
                getName();
            AddressEntry ae[] = wps.get(communityName, WPS_TIMEOUT);
            if (ae.length > 0) {
              MessageAddress communityManager = extractAgentIdFromURI(ae[0].
                  getAddress());
              if (!communityManager.equals(agentId)) {
                logger.debug("No longer community manager:" +
                             " community=" + communityName +
                             " newManager=" + communityManager);
                bbs.publishRemove(ra);
              }
            }
          }
          // Check timestamps for last transmission of CommunityDescriptor to
          // interested agents.  If the period is about to exceed the cache
          // expiration time set in the CommunityPlugin resend to refresh
          // client caches.
          long now = (new Date()).getTime();
          for (Iterator it = communityTimestamps.entrySet().iterator();
               it.hasNext(); ) {
            Map.Entry me = (Map.Entry) it.next();
            String communityName = (String) me.getKey();
            long timestamp = ( (Date) me.getValue()).getTime();
            long FUDGE = 1 * 60 * 1000; // Allowance for transmission delay
            // due to busy system, etc.
          }

          as = (AlarmService) serviceBroker.getService(this,
              AlarmService.class,
              null);
          as.addRealTimeAlarm(new ManagerCheckTimer(TIMER_INTERVAL));
        }
        catch (Exception e) {
          e.printStackTrace();
        }
        finally {
          bbs.closeTransaction();
          expired = true;
          if (as != null) {
            serviceBroker.releaseService(this, AlarmService.class, as);
          }
          if (wps != null) {
            serviceBroker.releaseService(this, WhitePagesService.class, wps);
          }
        }
      }
    }

    public long getExpirationTime () { return expirationTime; }
    public boolean hasExpired () { return expired; }
    public synchronized boolean cancel () {
      if (!expired)
        return expired = true;
      return false;
    }
  }

  /**
   * Timer used for batching the transmission of CommunityDescriptors.
   */
  private class CommunityDescriptorSendTimer implements Alarm {
    private String cname;
    private long expirationTime = -1;
    private boolean expired = false;
    public CommunityDescriptorSendTimer (String communityName, long delay) {
      expirationTime = delay + System.currentTimeMillis();
      this.cname = communityName;
    }

    public void expire() {
      if (!expired) {
        try {
          communityDescriptorsToSend.remove(cname);
          bbs.openTransaction();
          updateTimestamp(cname);
          RelayAdapter ra = getManagedCommunityRelayAdapter(cname);
          if (ra != null)
            bbs.publishChange(ra);
          if (ra != null && logger.isDebugEnabled()) {
            CommunityDescriptor cd = (CommunityDescriptor) ra.getContent();
            logger.debug("PublishChange CommunityDescriptor:" +
                         " targets=" + RelayAdapter.targetsToString(ra) +
                         " community=" + cd.getName() +
                         " entities=" +
                         entityNames(cd.getCommunity().getEntities()) +
                         " id=" + cd.getUID());
          }
        }
        catch (Exception e) {
          e.printStackTrace();
        }
        finally {
          bbs.closeTransaction();
          expired = true;
        }
      }
  }

  public long getExpirationTime () { return expirationTime; }
  public boolean hasExpired () { return expired; }
  public synchronized boolean cancel () {
    if (!expired)
      return expired = true;
    return false;
  }
}

}