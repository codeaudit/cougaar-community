/*
 * <copyright>
 *  Copyright 1997-2003 Mobile Intelligence Corp
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
package org.cougaar.community.examples;

import java.util.*;
import javax.naming.directory.*;

import org.cougaar.core.blackboard.IncrementalSubscription;
import org.cougaar.core.plugin.ComponentPlugin;

import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.service.UIDService;
import org.cougaar.core.service.LoggingService;
import org.cougaar.core.agent.service.alarm.Alarm;

import org.cougaar.community.RelayAdapter;

import org.cougaar.core.mts.MessageAddress;
import org.cougaar.multicast.AttributeBasedAddress;

import org.cougaar.util.UnaryPredicate;

/**
 * This plugin provides a simple example using an ABA to send a message to
 * members of a community.
 */
public class SampleABAPlugin extends ComponentPlugin {

  // Name of community that will receive ABA.  This parameter is specified as
  // a plugin argument for the agent that is responsible for sending the ABA.
  // For instance, in an .ini file the following entry is included in the
  // sending agents [Plugins] section:
  // plugin = org.cougaar.community.test.ABATestPlugin(MiniTestConfig)
  // Receiving agents will not specify a plugin argument.  They will receive
  // ABA based on their community membership defined in the communities.xml
  // file.
  protected String destinationCommunity = null;

  protected MessageAddress agentId;  // ID of this agent

  private LoggingService logger;

  private Set priorResponse;

  // Receives community name from plugin argument
  public void setParameter(Object obj) {
    List args = (List)obj;
    if (args != null && args.size() > 0)
      destinationCommunity = (String)args.get(0);
  }

  protected void setupSubscriptions() {

    agentId = getAgentIdentifier();
    logger = (LoggingService) getBindingSite().getServiceBroker().getService(this, LoggingService.class, null);
    logger = org.cougaar.core.logging.LoggingServiceWithPrefix.add(logger, agentId + ": ");

    // Subscribe to TestRelays
    testRelaySub = (IncrementalSubscription)blackboard.subscribe(testRelayPredicate);

    // If this agent is responsible for sending the test message
    if (isSender()) {
      // Subscribe to TestRelay responses
      testRelayRespSub = (IncrementalSubscription)blackboard.subscribe(testRelayRespPredicate);
      sendRelay();
    }
  }

  public void execute() {

    // Get test relays
    for (Iterator it = testRelaySub.getAddedCollection().iterator(); it.hasNext();) {
      TestRelay tr = (TestRelay)it.next();
      logger.info("Added TestRelay received:" +
                  " sender=" + tr.getSource() +
                  " message=" + tr.getMessage());
      // Send response back to sender
      Set responders = (Set)tr.getResponse();
      responders.add(agentId.toString());
      tr.setResponse(responders);
    }

    for (Iterator it = testRelaySub.getChangedCollection().iterator(); it.hasNext();) {
      TestRelay tr = (TestRelay)it.next();
      logger.info("Changed TestRelay received:" +
                  " sender=" + tr.getSource() +
                  " message=" + tr.getMessage());
      // Send response back to sender
      Set responders = (Set)tr.getResponse();
      responders.add(agentId.toString());
      tr.setResponse(responders);
    }

    // Get responses from recipients
    if (isSender()) {
      for (Iterator it = testRelayRespSub.getChangedCollection().iterator(); it.hasNext();) {
        RelayAdapter ra = (RelayAdapter)it.next();
        TestRelay tr = (TestRelay)ra.getContent();
        Set responders = (Set)tr.getResponse();
        if (priorResponse == null ||
            !priorResponse.containsAll(responders) ||
            !responders.containsAll(priorResponse)) {
          logger.info("TestRelay response(s) received: responders=" + responders);
          // Copy responders to priorResponse to check for changes next time
          priorResponse = new HashSet();
          for (Iterator it1 = responders.iterator(); it1.hasNext();)
            priorResponse.add(it1.next());
        }
      }
    }

  }

  // Returns true if this agent is responsible for publishing TestRelay and
  // collecting responses.
  private boolean isSender() { return destinationCommunity != null; }

  private void sendRelay() {
    // Compose TestRelay
    UIDService uidService =
        (UIDService) getBindingSite().getServiceBroker().getService(this,
        UIDService.class,
        null);
    TestRelay content = new TestRelayImpl(agentId, now(), uidService.nextUID());
    RelayAdapter myTestRelay = new RelayAdapter(content.getSource(),
                                                content,
                                                content.getUID());

    // Address ABA to all member agents in destination community
    AttributeBasedAddress target =
        AttributeBasedAddress.getAttributeBasedAddress(destinationCommunity,
        "Role",
        "Member");
    myTestRelay.addTarget(target);

    // Publish relay
    blackboard.publishAdd(myTestRelay);
    logger.info("TestRelay published:" +
                " message=" + content.getMessage() +
                " target=" + target);

    getAlarmService().addRealTimeAlarm(new UpdateTimer(myTestRelay, 60000));
  }

  private String now() {
    return (new Date()).toString();
  }

  // Select TestRelay objects
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

/**
 * Relay update timer
 */
private class UpdateTimer implements Alarm {
  private RelayAdapter ra;
  private long expTime = -1;
  private long delay;
  private boolean expired = false;

  public UpdateTimer(RelayAdapter ra,
                     long delay) {
    this.ra = ra;
    this.delay = delay;
    expTime = delay + System.currentTimeMillis();
  }

  /**
   * Called  when clock-time >= getExpirationTime().
   **/
  public void expire () {
    if (!expired) {
      try {
        TestRelay tr = (TestRelay)ra.getContent();
        tr.setMessage(now());
        tr.updateContent(tr, null);
        logger.info("TestRelay updated:" +
                    " message=" + tr.getMessage());
        blackboard.openTransaction();
        blackboard.publishChange(ra);
        blackboard.closeTransaction();
        getAlarmService().addRealTimeAlarm(new UpdateTimer(ra, delay));
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
