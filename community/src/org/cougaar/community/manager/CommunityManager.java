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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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


  private long timerInterval = 30000;

  private MessageAddress agentId;
  private String communityName;
  private LoggingService logger;
  private ServiceBroker serviceBroker;
  private BlackboardService bbs;
  private UIDService uidService;

  // Map containing CommunityDescriptors associated with all managed communities.
  private Map myManagingCommunities = new HashMap();

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
      as.addRealTimeAlarm(new ManagerCheckTimer(timerInterval));
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      if (as != null) {
        serviceBroker.releaseService(this, AlarmService.class, as);
      }
    }
  }

  /**
   * Processes CommunityManagerRequests.
   * @param cr CommunityManagerRequest
   */
  public void processRequest(CommunityManagerRequest cmr) {
    if (logger.isDebugEnabled())
      logger.debug("CommunityManagerRequest." + cmr.getRequestTypeAsString() +
        " community=" + cmr.getCommunityName() +
        " source=" + cmr.getSource());
    String communityName = cmr.getCommunityName();
    MessageAddress requester = cmr.getSource();
    RelayAdapter ra =
      (RelayAdapter)myManagingCommunities.get(communityName);
    if (ra != null) {
      CommunityDescriptorImpl cd = (CommunityDescriptorImpl)ra.getContent();
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
            }
            else {
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
        logger.debug("PublishChange CommunityDescriptor:" +
                     " targets=" + RelayAdapter.targetsToString(ra) +
                     " community=" + cd.getCommunity().getName() +
                     " entities=" + entityNames(cd.getCommunity().getEntities()) +
                     " id=" + cd.getUID());
      }
      bbs.publishChange(cmr);
      bbs.publishChange(ra);
    } else {
      logger.error("RelayAdapter is null");
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
    MessageAddress communityManager = null;
    if (communityName != null) {
      WhitePagesService wps =
        (WhitePagesService) serviceBroker.getService(this, WhitePagesService.class, null);
      try {
        AddressEntry ae[] = wps.get(communityName);
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
   * Adds a community to be managed by this community manager.
   * @param community Community to manage
   */
  public void addCommunity(org.cougaar.core.service.community.Community community) {
    if (logger.isDebugEnabled()) {
      logger.debug("addCommunity - name=" + community.getName());
    }
    String communityName = community.getName();
    if (myManagingCommunities.containsKey(communityName)) {
      logger.error("Invalid request to create multiple CommunityManagers " +
        "for community " + communityName);
    } else {
      WhitePagesService wps =
        (WhitePagesService)serviceBroker.getService(this,
                                                    WhitePagesService.class,
                                                    null);
      try {
        AddressEntry communityAE =
          new AddressEntry(communityName,
                           Application.getApplication("community"),
                           new java.net.URI(URN_PREFIX + ":" +
                                            NAMESPACE_IDENTIFIER + ":" +
                                            agentId),
                           Cert.NULL,
                           Long.MAX_VALUE);
        MessageAddress communityManager = findManager(communityName);
        if (communityManager != null) {
          //logger.error("Invalid request to create multiple CommunityManagers " +
          //  "for community " + communityName);
          wps.rebind(communityAE);
        } else {
          wps.bind(communityAE);
        }
        communityManager = agentId;
        CommunityDescriptor cd = new CommunityDescriptorImpl(agentId, community, uidService.nextUID());
        RelayAdapter ra = new RelayAdapter(cd.getSource(), cd, cd.getUID());
        //RelayAdapter ra = new RelayAdapter(cd.getSource(), cd, uidService.nextUID());
        myManagingCommunities.put(communityName, ra);
        logger.debug("Managing community " + communityName);
        ra.addTarget(agentId);
        logger.debug("PublishAdd CommunityDescriptor:" +
                     " targets=" + RelayAdapter.targetsToString(ra) +
                     " community=" + cd.getCommunity().getName() +
                     " entities=" + entityNames(cd.getCommunity().getEntities()) +
                     " cdUid=" + cd.getUID() +
                     " raUid=" + ra.getUID());
        bbs.publishAdd(ra);
      } catch (Exception ex) {
       ex.printStackTrace();
      } finally {
        serviceBroker.releaseService(this, WhitePagesService.class, wps);
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

  /**
   * Periodically check the WPS to verity that this agent is still identifitied
   * as the community manager for all communities it thinks it's managing.  If
   * not it removes the community from its list of managed communities and
   * removes the associated CommunityDescriptor relays.
   */
  private class ManagerCheckTimer implements Alarm {
    private long expirationTime = -1;
    private boolean expired = false;
    private AlarmService as = null;
    private WhitePagesService wps = null;

    /**
     * Create an Alarm to go off in the milliseconds specified,.
     **/
    public ManagerCheckTimer (long delay) {
      expirationTime = delay + System.currentTimeMillis();
    }

    /**
     * Called  when clock-time >= getExpirationTime().
     **/
    public void expire () {
      if (!expired) {
        List requestsToPublish = new ArrayList();
        try {
          wps = (WhitePagesService)serviceBroker.getService(this,
                                                            WhitePagesService.class,
                                                            null);
          for (Iterator it = myManagingCommunities.keySet().iterator(); it.hasNext();) {
            String communityName = (String)it.next();
            AddressEntry ae[] = wps.get(communityName);
            if (ae.length > 0) {
              MessageAddress communityManager = extractAgentIdFromURI(ae[0].getAddress());
              if (!communityManager.equals(agentId)) {
                logger.info("No longer community manager:" +
                            " community=" + communityName +
                            " newManager=" + communityManager);
                RelayAdapter ra =
                  (RelayAdapter)myManagingCommunities.get(communityName);
                bbs.openTransaction();
                bbs.publishRemove(ra);
                bbs.closeTransaction();
                myManagingCommunities.remove(communityName);
              }
            }
          }
          as = (AlarmService)serviceBroker.getService(this,
                                                      AlarmService.class,
                                                      null);
          as.addRealTimeAlarm(new ManagerCheckTimer(timerInterval));
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
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
}