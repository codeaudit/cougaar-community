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

import org.cougaar.core.service.community.Community;
import org.cougaar.core.service.community.CommunityChangeEvent;

import org.cougaar.community.CommunityDescriptor;
import org.cougaar.community.RelayAdapter;

import org.cougaar.core.component.BindingSite;
import org.cougaar.core.service.AlarmService;
import org.cougaar.core.service.AgentIdentificationService;
import org.cougaar.core.service.BlackboardService;
import org.cougaar.core.service.EventService;
import org.cougaar.core.service.LoggingService;
import org.cougaar.core.service.SchedulerService;
import org.cougaar.core.service.UIDService;
import org.cougaar.core.blackboard.BlackboardClientComponent;
import org.cougaar.core.component.ServiceBroker;

import org.cougaar.core.mts.MessageAddress;

import org.cougaar.core.agent.service.alarm.Alarm;

import org.cougaar.core.service.wp.AddressEntry;
import org.cougaar.core.service.wp.WhitePagesService;
import org.cougaar.core.service.wp.Callback;
import org.cougaar.core.service.wp.Response;

import java.net.URI;

import java.util.*;

/**
 * Helper class used to distribute new/updated CommunityDescriptor objects to
 * interested agents.
 */
public class CommunityDistributer extends BlackboardClientComponent {

  private long updateInterval;
  private long cacheExpiration;
  private boolean nodesOnly = true;

  private WhitePagesService whitePagesService;
  private ServiceBroker serviceBroker;
  private UIDService uidService;
  private LoggingService logger;
  private WakeAlarm wakeAlarm;

  // Map of DescriptorEntry objects.  Allows multiple communities to be
  // managed.
  private Map descriptors = Collections.synchronizedMap(new HashMap());
  class DescriptorEntry {
    RelayAdapter ra;
    CommunityDescriptor cd;
    Set agentTargets = Collections.synchronizedSet(new HashSet());
    Set nodeTargets = Collections.synchronizedSet(new HashSet());
    long lastSent = 0;
    boolean didChange = true;
    int changeType;
    String whatChanged;
    boolean doRemove = false;
  }

  /**
   * Constructor.
   * @param bs  BindingSite from CommunityManager.
   * @param updateInterval  Defines maximum rate that updates are sent
   * @param cacheExpiration Recipients cache expiration, defines minimum
   *                        update frequency
   * @param nodesOnly       True if CommunityDescriptors are only sent to node
   *                        agents
   */
  public CommunityDistributer(BindingSite bs,
                              long updateInterval,
                              long cacheExpiration,
                              boolean nodesOnly) {
    setBindingSite(bs);
    this.cacheExpiration = cacheExpiration;
    this.updateInterval = updateInterval;
    this.nodesOnly = nodesOnly;
    serviceBroker = getServiceBroker();
    setAgentIdentificationService(
        (AgentIdentificationService) serviceBroker.getService(this,
        AgentIdentificationService.class, null));
    logger = (LoggingService) serviceBroker.getService(this, LoggingService.class, null);
    logger = org.cougaar.core.logging.LoggingServiceWithPrefix.add(logger,
        agentId + ": ");
    initialize();
    load();
    start();
  }

  /**
   * Loads required services.
   */
  public void load() {
    setSchedulerService(
      (SchedulerService)getServiceBroker().getService(this, SchedulerService.class, null));
    setAlarmService((AlarmService)serviceBroker.getService(this, AlarmService.class, null));
    setBlackboardService(
      (BlackboardService)getServiceBroker().getService(this, BlackboardService.class, null));
    whitePagesService =
      (WhitePagesService)serviceBroker.getService(this, WhitePagesService.class, null);
    uidService =
      (UIDService)serviceBroker.getService(this, UIDService.class, null);
    super.load();
  }

  public void setupSubscriptions() { }

  public void execute() {
    if ((wakeAlarm != null) &&
        ((wakeAlarm.hasExpired()))) {
      publishDescriptors();
      wakeAlarm = new WakeAlarm(now() + updateInterval);
      alarmService.addRealTimeAlarm(wakeAlarm);
    }
  }

  /**
   * Publishes pending CommunityDescriptor Relays to local blackboard.
   */
  private void publishDescriptors() {
    long now = now();
    List l;
    synchronized (descriptors) {
      l = new ArrayList(descriptors.values());
    }
    for (Iterator it = l.iterator(); it.hasNext();) {
      DescriptorEntry de = (DescriptorEntry) it.next();
      //logger.debug("publishDescriptors:" +
      //             " community=" + de.cd.getName() +
      //             " ra=" + de.ra +
      //             " doRemove=" + de.doRemove +
      //             " didChange=" + de.didChange);
      if (de.ra == null) { // publish new descriptor
        if (!de.nodeTargets.isEmpty()) {
          de.ra = new RelayAdapter(de.cd.getSource(), de.cd, de.cd.getUID());
          updateTargets(de.ra, nodesOnly ? de.nodeTargets : de.agentTargets);
          de.didChange = false;
          de.lastSent = now;
          blackboard.publishAdd(de.ra);
          if (logger.isDebugEnabled()) {
            logger.debug("publishAdd: " + de.ra);
          }
        }
      } else {
        if ( (de.didChange && now > de.lastSent + updateInterval) ||
            (now > de.lastSent + cacheExpiration)) {
          // publish changed descriptor
          updateTargets(de.ra, nodesOnly ? de.nodeTargets : de.agentTargets);
          de.didChange = false;
          ( (CommunityDescriptorImpl) de.cd).setChangeType(de.changeType);
          ( (CommunityDescriptorImpl) de.cd).setWhatChanged(de.whatChanged);
          de.lastSent = now();
          blackboard.publishChange(de.ra);
          if (logger.isDebugEnabled()) {
            logger.debug("publishChange: " + de.ra);
          }
        } else {
          if (de.doRemove) { // remove descriptor
            blackboard.publishRemove(de.ra);
            descriptors.remove(de.cd.getName());
            if (logger.isDebugEnabled()) {
              logger.debug("publishRemove: " + de.ra);
            }
          }
        }
      }
    }
  }

  /**
   * Enable automatic update of CommunityDescriptors for named community.
   * @param community  Community to update
   * @param agents     Initial set of targets
   */
  protected void add(Community community, Set agents) {
    String communityName = community.getName();
    DescriptorEntry de = (DescriptorEntry)descriptors.get(communityName);
    if (de == null) {
      de = new DescriptorEntry();
      de.cd = new CommunityDescriptorImpl(agentId, community, uidService.nextUID());
      descriptors.put(communityName, de);
      addTargets(communityName, agents);
    }
    if (wakeAlarm == null) {
      wakeAlarm = new WakeAlarm(now() + updateInterval);
      alarmService.addRealTimeAlarm(wakeAlarm);
    }
  }

  /**
   * Enable automatic update of CommunityDescriptors for named community.
   * @param ra  RelayAdapter associated with previously created CommunityDescriptor
   */
  protected void add(RelayAdapter ra) {
    CommunityDescriptor cd = (CommunityDescriptor)ra.getContent();
    String communityName = cd.getName();
    DescriptorEntry de = (DescriptorEntry)descriptors.get(communityName);
    if (de == null) {
      de = new DescriptorEntry();
      de.cd = cd;
      de.ra = ra;
      descriptors.put(communityName, de);
      addTargets(communityName, ra.getTargets());
    }
    if (wakeAlarm == null) {
      wakeAlarm = new WakeAlarm(now() + updateInterval);
      alarmService.addRealTimeAlarm(wakeAlarm);
    }
  }

  protected boolean contains(String communityName) {
    return descriptors.containsKey(communityName);
  }

  protected Set getTargets(String communityName) {
    DescriptorEntry de = (DescriptorEntry)descriptors.get(communityName);
    if (de != null && de.ra != null) {
      return de.ra.getTargets();
    } else {
      return Collections.EMPTY_SET;
    }
  }

  /**
   * Adds new targets to receive CommunityDescriptor updates.
   * @param communityName  Community
   * @param targets  New targets
   */
  protected void addTargets(String communityName, Set targets) {
    DescriptorEntry de = (DescriptorEntry)descriptors.get(communityName);
    if (de != null) {
      de.agentTargets.addAll(targets);
      Set agentsToAdd = new HashSet(targets);
      for (Iterator it = agentsToAdd.iterator(); it.hasNext(); ) {
        findNodeTargets((MessageAddress)it.next(), communityName);
      }
    }
  }

  /**
   * Removes targets to receive CommunityDescriptor updates.
   * @param communityName  Community
   * @param agentNames  Targets to remove
   */
  protected void removeTargets(String communityName, Set agentNames) {
    DescriptorEntry de = (DescriptorEntry)descriptors.get(communityName);
    if (de != null) {
      de.agentTargets.removeAll(agentNames);
    }
  }

  /**
   * Disables CommunityDescriptor updates for named community.  If a
   * CommunityDescriptor Relay was previously published it is rescinded via
   * a blackboard publishRemove.
   * @param communityName  Name of community
   */
  protected void remove(String communityName) {
    DescriptorEntry de = (DescriptorEntry)descriptors.get(communityName);
    if (de != null) {
      de.doRemove = true;
    }
  }

  /**
   * Update Relay target set.
   * @param ra      Relay to update
   * @param targets Targets
   */
  private void updateTargets(RelayAdapter ra, Set targets) {
    Set targetsToAdd = new HashSet();
    synchronized (targets) {
      targetsToAdd.addAll(targets);
    }
    for (Iterator it = targetsToAdd.iterator(); it.hasNext();) {
      MessageAddress target = (MessageAddress)it.next();
      if (!ra.getTargets().contains(target)) {
        ra.addTarget(target);
      }
    }
  }

  /**
   * Notify targets of a change in community state.
   * @param communityName  Name of changed community
   * @param type  Type of change
   * @param what  Entity affected by change
   */
  protected void update(String communityName, int type, String what) {
    logger.debug("update:" +
                 " community=" + communityName +
                 " type=" + CommunityChangeEvent.getChangeTypeAsString(type) +
                 " whatChanged=" + what);
    DescriptorEntry de = (DescriptorEntry)descriptors.get(communityName);
    if (de != null) {
      de.didChange = true;
      de.changeType = type;
      de.whatChanged = what;
      wakeAlarm.expire();
    }
  }

  /**
   * Get CommunityDescriptor associated with named community.
   * @param communityName
   * @return  CommunityDescriptor for community
   */
  protected CommunityDescriptor get(String communityName) {
    DescriptorEntry de = (DescriptorEntry)descriptors.get(communityName);
    return de != null ? de.cd : null;
  }

  /**
   * Find an agents node by looking in WhitePages.  Add node address to
   * Relay target set.
   */
  private void findNodeTargets(final MessageAddress agentName,
                               final String communityName) {
    Callback cb = new Callback() {
      public void execute(Response resp) {
        if (resp.isAvailable()) {
          if (resp.isSuccess()) {
            AddressEntry entry = ((Response.Get)resp).getAddressEntry();
            try {
              if (entry != null) {
                URI uri = entry.getURI();
                MessageAddress node = MessageAddress.getMessageAddress(uri.
                    getPath().substring(1));
                DescriptorEntry de = (DescriptorEntry) descriptors.get(
                    communityName);
                if (de != null) {
                  if (!de.nodeTargets.contains(node)) {
                    de.nodeTargets.add(node);
                    de.didChange = true;
                  }
                }
              } else {
                logger.debug("AddressEntry is null: agent=" + agentName);
              }
            } catch (Exception ex) {
              logger.error("Exception in addNodeToTargets:", ex);
            } finally {
              resp.removeCallback(this);
            }
          }
        }
      }
    };
    whitePagesService.get(agentName.toString(), "topology", cb);
  }

  private long now() {
    return System.currentTimeMillis();
  }

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

}