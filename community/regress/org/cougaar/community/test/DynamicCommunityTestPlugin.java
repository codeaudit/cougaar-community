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
package org.cougaar.community.test;

import java.util.*;

import org.cougaar.core.blackboard.IncrementalSubscription;
import org.cougaar.core.plugin.ComponentPlugin;

import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.service.UIDService;
import org.cougaar.core.service.LoggingService;
import org.cougaar.core.service.community.CommunityService;
import org.cougaar.core.service.community.CommunityResponse;
import org.cougaar.core.service.community.CommunityResponseListener;
import org.cougaar.core.service.community.CommunityChangeListener;
import org.cougaar.core.service.community.CommunityChangeEvent;
import org.cougaar.core.service.community.Community;

import org.cougaar.community.examples.TestRelay;
import org.cougaar.community.examples.TestRelayImpl;

import org.cougaar.community.RelayAdapter;
import org.cougaar.community.requests.*;

import org.cougaar.core.mts.MessageAddress;
import org.cougaar.multicast.AttributeBasedAddress;

import org.cougaar.util.UnaryPredicate;

import javax.naming.*;
import javax.naming.directory.*;

import EDU.oswego.cs.dl.util.concurrent.Semaphore;

public class DynamicCommunityTestPlugin extends ComponentPlugin {

  protected MessageAddress agentId;  // ID of this agent

  private LoggingService logger;
  private UIDService uidService;
  private CommunityService communityService;

  private String destinationCommunity = null;
  private HashMap responders = new HashMap();
  public static final long MIN_DELAY = 25000L;
  private boolean result = false;

  protected void setupSubscriptions() {
    agentId = getAgentIdentifier();
    logger = (LoggingService) getBindingSite().getServiceBroker().getService(this, LoggingService.class, null);
    logger = org.cougaar.core.logging.LoggingServiceWithPrefix.add(logger, agentId + ": ");
    uidService = (UIDService) getBindingSite().getServiceBroker().getService(this, UIDService.class, null);
    communityService = (CommunityService) getBindingSite().getServiceBroker().getService(this, CommunityService.class, null);
    if(communityService == null)
      logger.error("No community service?!");

    // Subscribe to TestRelays
    testRelaySub = (IncrementalSubscription)blackboard.subscribe(testRelayPredicate);

    final Attributes attrs1 = new BasicAttributes();
    attrs1.put("EntityType", "Community");
    attrs1.put("Manager", "Agent20");
    final Attributes attrs2 = new BasicAttributes();
    attrs2.put("EntityType", "Community");
    attrs2.put("Role", "Member");
    //attrs2.put("Manager", "Agent23");

    Semaphore s = new Semaphore(0);


    /**
     * Use CommunityService:
     * Create community CommunityA, contains a nested community CommunityB. CommunityA
     * contains Agent20, Agent21 and Agent22. CommunityB contains Agent23 and Agent24.
     */
    //if(agentId.getAddress().equals("TestNode"))
      //System.out.println("========Use CommunityService========");
    if(agentId.getAddress().equals("Agent20")) {
      communityService.createCommunity("CommunityA", attrs1);
      communityService.setEntityAttributes("CommunityA", "Agent20", getAgentAttributes("Agent20"));
    }

    if(agentId.getAddress().equals("Agent21")) {
      communityService.addToCommunity("CommunityA", MessageAddress.getMessageAddress("Agent21"),
        "Agent21", getAgentAttributes("Agent21"));
    }
    if(agentId.getAddress().equals("Agent22")){
      communityService.addToCommunity("CommunityA", MessageAddress.getMessageAddress("Agent22"),
        "Agent22", getAgentAttributes("Agent22"));
    }
    if(agentId.getAddress().equals("Agent23")) {
      communityService.createCommunity("CommunityB", attrs2);
      communityService.setEntityAttributes("CommunityB", "Agent23", getAgentAttributes("Agent23"));
      communityService.addToCommunity("CommunityA", MessageAddress.getMessageAddress("CommunityB"),
        "CommunityB", attrs2);
    }
    if(agentId.getAddress().equals("Agent24")) {
      communityService.addToCommunity("CommunityB", MessageAddress.getMessageAddress("Agent24"),
        "Agent24", getAgentAttributes("Agent24"));
    }
    runTests(1);

    try{s.attempt(30000);}catch(InterruptedException e){}

    /**
     * Publish requests to blackboard to create CommunityC and CommunityD.
     * CommunityC contains Agent20, Agent21, Agent22 and CommunityD.
     * CommunityD conatins Agent23 and Agent24.
     */
    //if(agentId.getAddress().equals("TestNode"))
      //System.out.println("=========Use blackboard to publish===========");
    if(agentId.getAddress().equals("Agent20")) {
      JoinCommunity jc = new JoinCommunity("CommunityC", agentId.toString(), CommunityService.AGENT,
        getAgentAttributes("Agent20"), true, attrs1, uidService.nextUID());
      publishCommunityRequest(jc);
    }

    if(agentId.getAddress().equals("Agent21")) {
        JoinCommunity jc = new JoinCommunity("CommunityC", "Agent21", CommunityService.AGENT,
          getAgentAttributes("Agent21"), uidService.nextUID());
        publishCommunityRequest(jc);
    }

    if(agentId.getAddress().equals("Agent22")) {
        JoinCommunity jc = new JoinCommunity("CommunityC", "Agent22", CommunityService.AGENT,
          getAgentAttributes("Agent22"), uidService.nextUID());
        publishCommunityRequest(jc);
    }

    if(agentId.getAddress().equals("Agent23")) {
      JoinCommunity jc = new JoinCommunity("CommunityD", agentId.toString(), CommunityService.AGENT,
        getAgentAttributes("Agent23"), true, attrs2, uidService.nextUID());
      publishCommunityRequest(jc);
      jc = new JoinCommunity("CommunityC", "CommunityD", CommunityService.COMMUNITY,
        attrs2, uidService.nextUID());
      publishCommunityRequest(jc);
    }

    if(agentId.getAddress().equals("Agent24")) {
      JoinCommunity jc = new JoinCommunity("CommunityD", "Agent24", CommunityService.AGENT,
          getAgentAttributes("Agent24"), uidService.nextUID());
      publishCommunityRequest(jc);
    }
    //try{s.attempt(5000);}catch(InterruptedException e){}
    runTests(2);
  }

  private void runTests(int testRound) {
    if(agentId.getAddress().equals("TestNode")) {
        /**
         * Test1: Sender: not community member (TestNode)
         *        Destination: flat community (CommunityB)
         *        Responders: Agent04, Agent05
         */
        if(testRound == 1)
          destinationCommunity = "CommunityB";
        else
          destinationCommunity = "CommunityD";
        String message = getMessage(message1, testRound);
        sendRelay(message);
        Set answer1 = new HashSet();
        answer1.add(agents[3]);
        answer1.add(agents[4]);
        Timer timer1 = new Timer(1, answer1, message);
        timer1.start();

        /**
         * Test2: Sender: not community member (TestNode)
         *        Destination: nested community (CommunityA)
         *        Responders: Agent01 to Agent05
         */
        if(testRound == 1)
          destinationCommunity = "CommunityA";
        else
          destinationCommunity = "CommunityC";
        message = getMessage(message2, testRound);
        sendRelay(message);
        Set answer2 = new HashSet();
        for(int i=0; i<agents.length; i++)
          answer2.add(agents[i]);
        Timer timer2 = new Timer(2, answer2, message);
        timer2.start();
      }

      else if(agentId.getAddress().equals("Agent24")) {
        /**
         * Test3: Sender: community member (Agent24)
         *        Destination: flat community (CommunityB)
         *        Responders: Agent23
         */
        if(testRound == 1)
          destinationCommunity = "CommunityB";
        else
          destinationCommunity = "CommunityD";
        String message = getMessage(message3, testRound);
        sendRelay(message);
        Set answer = new HashSet();
        answer.add(agents[3]);
        Timer timer = new Timer(3, answer, message);
        timer.start();
      }

      else if(agentId.getAddress().equals("Agent20")) {
        /**
         * Test4: Sender: community member (Agent20)
         *        Destination: nested community (CommunityA)
         *        Responders: Agent21 to Agent24
         */
        if(testRound == 1)
          destinationCommunity = "CommunityA";
        else
          destinationCommunity = "CommunityC";
        String message = getMessage(message4, testRound);
        sendRelay(message);
        Set answer = new HashSet();
        for(int i=1; i<agents.length; i++)
          answer.add(agents[i]);
        Timer timer = new Timer(4, answer, message);
        timer.start();
      }
  }

  private String getMessage(String message, int testRound) {
    return "Round " + Integer.toString(testRound) + ": " + message;
  }

  protected void execute () {
    // Get test relays
    for (Iterator it = testRelaySub.getAddedCollection().iterator(); it.hasNext();) {
      TestRelay tr = (TestRelay)it.next();
      logger.info("TestRelay received:" +
                  " sender=" + tr.getSource() +
                  " message=" + tr.getMessage());
      // Send response back to sender
      //tr.setResponse(agentId.toString());
      Set resps = (Set)tr.getResponse();
      resps.add(agentId.toString());
      tr.setResponse(resps);
      blackboard.publishChange(tr);
    }

    // Get responses from recipients
    if (isSender()) {
      for (Iterator it = testRelayRespSub.getChangedCollection().iterator(); it.hasNext();) {
        RelayAdapter ra = (RelayAdapter)it.next();
        TestRelay tr = (TestRelay)ra.getContent();
        responders.put(tr.getMessage(), (Set)tr.getResponse());
        logger.info("TestRelay response(s) received: responders=" + tr.getResponse());
      }
    }

  }

  protected void publishCommunityRequest(final org.cougaar.community.requests.CommunityRequest cr) {
    Thread communityRequestThread = new Thread("CommunityRequestThread") {
      public void run() {
        try {
          if (logger.isDebugEnabled()) {
            logger.debug("Publishing CommunityRequest, type=" + cr.getRequestType() +
                      " community=" + cr.getCommunityName());
          }
          blackboard.openTransaction();
          blackboard.publishAdd(cr);
          blackboard.closeTransaction();
          if (logger.isDebugEnabled()) {
            logger.debug("Done Publishing CommunityRequest, type=" +
                      cr.getRequestType() +
                      " community=" + cr.getCommunityName());
          }
        } catch (Exception ex) {
          logger.error("Exception in publishCommunityRequest", ex);
        }
      }
    };
    communityRequestThread.start();
  }

  private void sendRelay(String message) {
    // If this agent is responsible for sending the test message
    if (isSender()) {
      // Subscribe to TestRelay responses
      testRelayRespSub = (IncrementalSubscription)blackboard.subscribe(testRelayRespPredicate);

      communityService.addListener(new CommunityChangeListener() {
          public void communityChanged(CommunityChangeEvent cce) {
            Collection entities = communityService.search(destinationCommunity, "(Role=Member)");
            Community community = cce.getCommunity();
            if (membershipChanged(entities)) {
              logger.info("CommunityChangeEvent:" +
                           " community=" + cce.getCommunityName() +
                           " entities=" + entityNames(entities));
            } else {
              logger.debug("CommunityChangeEvent: no change in membership");
            }
          }
          public String getCommunityName() { return destinationCommunity; }
        });

      TestRelay content = new TestRelayImpl(agentId, message, uidService.nextUID());
      RelayAdapter myTestRelay = new RelayAdapter(content.getSource(),
                                                  content,
                                                  content.getUID());

      // Address ABA to all member agents in destination community
      AttributeBasedAddress target = AttributeBasedAddress.getAttributeBasedAddress(destinationCommunity,
                                                       "Role",
                                                       "Member");
      myTestRelay.addTarget(target);

      // Publish relay
      blackboard.publishAdd(myTestRelay);
      logger.info("TestRelay published:" +
                  " message=" + content.getMessage() +
                  " target=" + target);

    }
  }

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

  private boolean isSender() {
    return destinationCommunity != null;
  }

  private Attributes getAgentAttributes(String agentName) {
    Attributes attrs = new BasicAttributes();
    attrs.put("Name", agentName);
    attrs.put("Type", "Agent");
    attrs.put("Role", "Member");
    return attrs;
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

  private class MyResponseListener implements CommunityResponseListener {
    public void getResponse(CommunityResponse cr){
      result = true;
    }
  }


  private class Timer extends Thread {
    private Set answer;
    String message;
    int testNum;
    public Timer(int testNum, Set answer, String message) {
      this.answer = answer;
      this.message = message;
      this.testNum = testNum;
    }
    public void run(){
        try{
          boolean result = false;
          long interval = MIN_DELAY + testNum * 3000;
          if(message.indexOf("Round 1") != -1)
            interval += 15000;
          sleep(interval);
          Set results = (Set)responders.get(message);
          try{
            result = (results.equals(answer) || results.containsAll(answer));
          }catch(NullPointerException e){
            System.err.println("Test" + testNum + " get null results.");
            return;
          }
          System.out.println("Test" + testNum + "  " + message + " ----------- " + (result ? "pass" : "fail"));
          if(!result)
            System.out.println("     get: " + results + "  expected: " + answer);
        }
        catch(InterruptedException e){}
      }
  }


  private final static String[] agents = new String[]{"Agent20", "Agent21", "Agent22", "Agent23", "Agent24"};
  private final static String message1 = "s: non-member, d: flat";
  private final static String message2 = "s: non-member, d: multi";
  private final static String message3 = "s: member, d: flat";
  private final static String message4 = "s: member, d: multi";
}