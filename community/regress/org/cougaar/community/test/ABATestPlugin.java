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
package org.cougaar.community.test;

import java.util.*;
import javax.naming.directory.*;

import org.cougaar.core.blackboard.IncrementalSubscription;
import org.cougaar.core.plugin.ComponentPlugin;
import org.cougaar.core.component.ServiceBroker;

import org.cougaar.core.service.LoggingService;
import org.cougaar.core.service.UIDService;
import org.cougaar.core.agent.service.alarm.Alarm;

import org.cougaar.core.relay.*;
import org.cougaar.core.util.UID;

import org.cougaar.community.RelayAdapter;

import org.cougaar.community.examples.TestRelay;
import org.cougaar.community.examples.TestRelayImpl;

import org.cougaar.core.service.community.Community;
import org.cougaar.core.service.community.Entity;
import org.cougaar.core.service.community.CommunityService;
import org.cougaar.core.service.community.CommunityService;
import org.cougaar.core.service.community.CommunityChangeListener;
import org.cougaar.core.service.community.CommunityChangeEvent;
import org.cougaar.core.service.community.CommunityResponse;
import org.cougaar.core.service.community.CommunityResponseListener;

import org.cougaar.core.mts.MessageAddress;
import org.cougaar.multicast.AttributeBasedAddress;

import org.cougaar.util.UnaryPredicate;

/**
 * This plugin tests the use of ABAs and Relays.
 */
public class ABATestPlugin extends ComponentPlugin {

  protected List    destinations = new Vector();
  protected String  argStr;

  public static final long RELAY_TIMEOUT = 30000;

  public static final long MIN_DELAY = 5000L;
  public static final long MAX_DELAY = 10000L;

  // Operations
  private static final int LEAVE = 0;
  private static final int JOIN  = 1;

  protected MessageAddress agentId;  // ID of this agent

  protected Map relayMap = new HashMap();

  // CommunityService
  private CommunityService cs;  // Community Service
  private LoggingService logger;
  private UIDService uidService;

  public void setParameter(Object obj) {
    List args = (List)obj;
    StringBuffer miscArgs = new StringBuffer();
    for (Iterator it = args.iterator(); it.hasNext();) {
      String param = (String)it.next();
      StringTokenizer st = new StringTokenizer(param, "=");
      if (st.countTokens() == 2) {
        String key = st.nextToken();
        String value = st.nextToken();
        if (key.equalsIgnoreCase("community")) {
          destinations.add(value);
        } else {
          miscArgs.append(param);
        }
      }
    }
    argStr = miscArgs.toString();
  }

  private boolean isAbaGenerator() { return destinations.size() > 0; }

  protected void setupSubscriptions() {

    agentId = getAgentIdentifier();
    logger = (LoggingService) getBindingSite().getServiceBroker().getService(this, LoggingService.class, null);
    logger = org.cougaar.core.logging.LoggingServiceWithPrefix.add(logger, agentId + ": ");

    //logger.debug("");
    uidService =
      (UIDService) getBindingSite().getServiceBroker().getService(this, UIDService.class, null);

    cs = (CommunityService) getBindingSite().getServiceBroker().getService(this, CommunityService.class, null);

    // Subscribe to TestRelays
    testRelaySub = (IncrementalSubscription)blackboard.subscribe(testRelayPredicate);

    // If this agent is responsible for sending the test message
    if (isAbaGenerator()) {
      // Subscribe to TestRelay responses
      testRelayRespSub = (IncrementalSubscription)blackboard.subscribe(testRelayRespPredicate);
      for (Iterator it = destinations.iterator(); it.hasNext();) {
        final String communityName = (String)it.next();
        // Add a listener to receive change events and log membership changes
        cs.addListener(new CommunityChangeListener() {
          public void communityChanged(CommunityChangeEvent cce) {
            Collection entities = cs.search(communityName, "(Role=Member)");
            Community community = cce.getCommunity();
            if (membershipChanged(entities)) {
              logger.info("CommunityChangeEvent:" +
                           " community=" + cce.getCommunityName() +
                           " entities=" + entityNames(entities));
            } else {
              logger.debug("CommunityChangeEvent: no change in membership");
            }
          }
          public String getCommunityName() { return communityName; }
        });

        // Compose and publish TestRelay
        TestRelay content = new TestRelayImpl(agentId, argStr, communityName,
                                              uidService.nextUID());
        RelayAdapter myTestRelay = new RelayAdapter(content.getSource(),
                                                        content,
                                                        content.getUID());
        AttributeBasedAddress target =
          AttributeBasedAddress.getAttributeBasedAddress(communityName,
                                                         "Role",
                                                         "Member");
        myTestRelay.addTarget(target);
        relayMap.put(myTestRelay.getUID(), communityName);
        blackboard.publishAdd(myTestRelay);
        logger.info("TestRelay published: target=" + target);
        //getAlarmService().addRealTimeAlarm(new UpdateTimer(myTestRelay, communityName, 60000));
      }
    }
  }

  public void execute() {

    Collection testRelays = testRelaySub.getAddedCollection();
    for (Iterator it = testRelays.iterator(); it.hasNext();) {
      TestRelay tr = (TestRelay)it.next();
      logger.debug("Added TestRelay received: " +
                   " source=" + tr.getSource() +
                   " community=" + tr.getCommunityName() +
                   " message=" + tr.getMessage());
      Set resp = (Set)tr.getResponse();
      resp.add(agentId.toString());
      if (randomJoinAndLeave(tr.getMessage())) {
        // After receiving the test relay leave the community and re-join a few seconds lager
        long waitTime = MIN_DELAY + (long)((MAX_DELAY-MIN_DELAY) * java.lang.Math.random());
        getAlarmService().addRealTimeAlarm(new OpTimer(agentId,
                                                       tr.getCommunityName(),
                                                       LEAVE,
                                                       waitTime));
      }
    }

    testRelays = testRelaySub.getChangedCollection();
    for (Iterator it = testRelays.iterator(); it.hasNext();) {
      TestRelay tr = (TestRelay)it.next();
      logger.debug("Changed TestRelay received: " +
                   " source=" + tr.getSource() +
                   " community=" + tr.getCommunityName() +
                   " message=" + tr.getMessage());
      Set resp = (Set)tr.getResponse();
      resp.add(agentId.toString());
      if (randomJoinAndLeave(tr.getMessage())) {
        // After receiving the test relay leave the community and re-join a few seconds lager
        long waitTime = MIN_DELAY + (long)((MAX_DELAY-MIN_DELAY) * java.lang.Math.random());
        getAlarmService().addRealTimeAlarm(new OpTimer(agentId,
                                                       tr.getCommunityName(),
                                                       LEAVE,
                                                       waitTime));
      }
    }

    if (isAbaGenerator()) {
      Collection testRelayResponses = testRelayRespSub.getChangedCollection();
      for (Iterator it = testRelayResponses.iterator(); it.hasNext();) {
        RelayAdapter ra = (RelayAdapter)it.next();
        TestRelay tr = (TestRelay)ra.getContent();
        String communityName = (String)relayMap.get(tr.getUID());
        Set responders = (Set)ra.getResponse();
        if (respondersChanged(communityName, responders)) {
          logger.info("TestRelay:" +
                      " community=" + tr.getCommunityName() +
                      " responders=" + responders.size() + " " + responders);
        }
      }
    }

  }

  private Map lastResponderMap = new HashMap();
  private boolean respondersChanged(String communityName, Set responders) {
    Set lastResponderSet = (Set)lastResponderMap.get(communityName);
    if (lastResponderSet == null) {
      lastResponderSet = new HashSet();
      lastResponderMap.put(communityName, lastResponderSet);
    }
    if (lastResponderSet.containsAll(responders) &&
        responders.containsAll(lastResponderSet)) {
      return false;
    } else {
      lastResponderSet = new HashSet();
      for (Iterator it = responders.iterator(); it.hasNext();)
        lastResponderSet.add(it.next());
      lastResponderMap.put(communityName, lastResponderSet);
      return true;
    }
  }

  private boolean randomJoinAndLeave(String argStr) {
    if (argStr != null) {
      StringTokenizer st = new StringTokenizer(argStr, "=");
      return (st.countTokens() == 2 &&
              st.nextToken().equalsIgnoreCase("JoinAndLeaveTest") &&
              st.nextToken().equalsIgnoreCase("True"));
    }
    return false;
  }

  // Select TestRelay objects that are published to BB
  private IncrementalSubscription testRelaySub;
  private UnaryPredicate testRelayPredicate = new UnaryPredicate() {
    public boolean execute (Object o) {
      return (o instanceof TestRelay);
  }};

  // Select responses to TestRelay
  private IncrementalSubscription testRelayRespSub;
  private UnaryPredicate testRelayRespPredicate = new UnaryPredicate() {
    public boolean execute (Object o) {
      return (o instanceof RelayAdapter &&
              ((RelayAdapter)o).getContent() instanceof TestRelay);
  }};

  // Converts a collection of entities to a compact string representation of names
  private String entityNames(Collection members) {
    StringBuffer sb = new StringBuffer("[");
    for (Iterator it = members.iterator(); it.hasNext();) {
      sb.append(it.next().toString() + (it.hasNext() ? "," : ""));
    }
    return(sb.append("]").toString());
  }

  private Set members = new HashSet();  // Set containing community member names
  // Determines if community membership has changed since last check
  //   if so, the members set is updated and the method returns "true"
  private boolean membershipChanged(Collection c) {
    Set tmpSet = new HashSet();
    for (Iterator it = c.iterator(); it.hasNext();) {
      tmpSet.add(it.next());
    }
    if (members.containsAll(tmpSet) && tmpSet.containsAll(members)) {
      return false;
    } else {
      members = tmpSet;
      return true;
    }
  }

  /**
   * Community operation timer
   */
  private class OpTimer implements Alarm {
    private MessageAddress agent;
    private long expTime = -1;
    private boolean expired = false;
    private String communityName;
    private int operation;
    private long delay;

    public OpTimer (MessageAddress id,
                    String name,
                    int oper,
                    long delay) {
      agent = id;
      operation = oper;
      communityName = name;
      this.delay = delay;
      expTime = delay + System.currentTimeMillis();
    }

    /**
     * Called  when clock-time >= getExpirationTime().
     **/
    public void expire () {
      if (!expired) {
        try {
          switch(operation) {
            case LEAVE:
              logger.info("Leaving community");
              cs.leaveCommunity(communityName,
                                agentId.toString(),
                                new CommunityResponseListener() {
                public void getResponse(CommunityResponse resp) {
                  if (resp.getStatus() == CommunityResponse.SUCCESS) {
                    // set timer for re-join
                    long waitTime = MIN_DELAY + (long)((MAX_DELAY-MIN_DELAY) * java.lang.Math.random());
                    getAlarmService().addRealTimeAlarm(new OpTimer(agentId,
                                                                   communityName,
                                                                   JOIN,
                                                                   waitTime));
                  }
                }
              });
              break;
            case JOIN:
              logger.info("Joining community");
              Attributes myAttrs = new BasicAttributes();
              myAttrs.put(new BasicAttribute("EntityType", "Agent"));
              myAttrs.put(new BasicAttribute("Role", "Member"));
              cs.joinCommunity(communityName,
                               agentId.toString(),
                               cs.AGENT,
                               myAttrs,
                               false,
                               null,
                               null);
              break;
          }
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          expired = true;
        }
      }
    }

    public long getExpirationTime () { return expTime; }
    public boolean hasExpired () { return expired; }
    public synchronized boolean cancel () {
      if (!expired)
        return expired = true;
      return false;
    }
  }

  /**
   * Relay update timer
   */
  private class UpdateTimer implements Alarm {
    private RelayAdapter ra;
    private long expTime = -1;
    private long delay;
    private boolean expired = false;
    private String communityName;

    public UpdateTimer(RelayAdapter ra,
                       String communityName,
                       long delay) {
      this.ra = ra;
      this.delay = delay;
      this.communityName = communityName;
      expTime = delay + System.currentTimeMillis();
    }

    /**
     * Called  when clock-time >= getExpirationTime().
     **/
    public void expire () {
      if (!expired) {
        try {
          Relay.Target rt = (Relay.Target)ra.getContent();
          rt.updateContent(new TestRelayImpl(agentId, (new Date()).toString(), communityName,
                                              uidService.nextUID()), null);
          blackboard.openTransaction();
          blackboard.publishChange(ra);
          blackboard.closeTransaction();
          getAlarmService().addRealTimeAlarm(new UpdateTimer(ra, communityName, delay));
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          expired = true;
        }
      }
    }

    public long getExpirationTime () { return expTime; }
    public boolean hasExpired () { return expired; }
    public synchronized boolean cancel () {
      if (!expired)
        return expired = true;
      return false;
    }
  }

}
