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

import org.cougaar.community.examples.TestRelay;
import org.cougaar.community.examples.TestRelayImpl;

import org.cougaar.core.blackboard.IncrementalSubscription;
import org.cougaar.core.plugin.ComponentPlugin;

import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.service.UIDService;
import org.cougaar.core.service.LoggingService;

import org.cougaar.community.RelayAdapter;

import org.cougaar.core.mts.MessageAddress;
import org.cougaar.multicast.AttributeBasedAddress;

import org.cougaar.util.UnaryPredicate;

public class TestPlugin extends ComponentPlugin {

  private List destinationCommunity = new Vector();

  protected MessageAddress agentId;  // ID of this agent

  private LoggingService logger;
  private UIDService uidService;

  private HashMap responders = new HashMap();

  public static final long MIN_DELAY = 30000L;

  /**
   * Join community during setup
   */
  protected void setupSubscriptions() {
    agentId = getAgentIdentifier();
    logger = (LoggingService) getBindingSite().getServiceBroker().getService(this, LoggingService.class, null);
    logger = org.cougaar.core.logging.LoggingServiceWithPrefix.add(logger, agentId + ": ");
    uidService = (UIDService) getBindingSite().getServiceBroker().getService(this, UIDService.class, null);

    // Subscribe to TestRelays
    testRelaySub = (IncrementalSubscription)blackboard.subscribe(testRelayPredicate);

    if(agentId.getAddress().equals("TestNode")) {
      /**
       * Test1: sender: not community member (TestNode)
       *        Community manager: managing 1 community (Agent10)
       *        Destination: flat community (CommunityD)
       */
      destinationCommunity.add("CommunityD");;
      sendRelay(message1);
      Set answer1 = new HashSet();
      answer1.add(agents[9]);
      answer1.add(agents[10]);
      answer1.add(agents[11]);
      Timer timer1 = new Timer(1, answer1, message1, 0);
      timer1.start();

      /**
       * Test2: Sender: not community member (TestNode)
       *        Community manager: managing 1 community (Agent04)
       *        Destination: multi-level community (CommunityB, contains CommunityD)
       *        Qualifier: 1 level (Agent04, Agent05, Agent06)
       */
      destinationCommunity.clear();
      destinationCommunity.add("CommunityB");
      sendRelay(message2);
      Set answer2 = new HashSet();
      answer2.add(agents[3]);
      answer2.add(agents[4]);
      answer2.add(agents[5]);
      Timer timer2 = new Timer(2, answer2, message2, 0.1);
      timer2.start();
      /**
       * Test3: Sender: not community member (TestNode)
       *        Community manager: managing 1 community (Agent04)
       *        Destination: multi-level community (CommunityB, contains CommunityD)
       *        Qualifier: all levels (Agent04, Agent05, Agent06, Agent10, Agent11, Agent12)
       */

      Set answer3 = new HashSet();
      for(int i=3; i<6; i++)
        answer3.add(agents[i]);
      for(int i=9; i<12; i++)
        answer3.add(agents[i]);
      Timer timer3 = new Timer(3, answer3, message2, 0.1);
      timer3.start();

      /**
       * Test13: Sender: not community member (TestNode)
       *         Community manager: managing multiple communities, multiple in destination (Agent01)
       *         Destination: multi-level community (CommunityA, CommunityE)
       *         Qualifier: 1 level (Agent01, Agent02, Agent03, Agent13, Agent14, Agent15)
       */
      destinationCommunity.clear();
      destinationCommunity.add("CommunityA");
      destinationCommunity.add("CommunityE");
      sendRelay(message9);
      Set answer4 = new HashSet();
      for(int i=0; i<3; i++)
        answer4.add(agents[i]);
      for(int i=12; i<15; i++)
        answer4.add(agents[i]);
      Timer timer4 = new Timer(13, answer4, message9, 0);
      timer4.start();
      /**
       * Test14: Sender: not community member (TestNode)
       *         Community manager: managing multiple communities, multiple in destination (Agent01)
       *         Destination: multi-level community (CommunityA, CommunityE)
       *         Qualifier: all levels (all agents in array agents[])
       */

      Set answer5 = new HashSet();
      for(int i=0; i<15; i++)
        answer5.add(agents[i]);
      Timer timer5 = new Timer(14, answer5, message9, 0);
      timer5.start();
    }

    if(agentId.getAddress().equals("Agent11")) {
      /**
       * Test4: Sender: community member, not manager (Agent11)
       *        Community manager: managing 1 community (Agent10)
       *        Destination: flat community (CommunityD)
       */
      destinationCommunity.add("CommunityD");
      sendRelay(message3);
      Set answer = new HashSet();
      answer.add(agents[9]);
      answer.add(agents[11]);
      Timer timer = new Timer(4, answer, message3, 0);
      timer.start();
    }

    if(agentId.getAddress().equals("Agent05")) {
      /**
       * Test5: Sender: community member, not manager (Agent05)
       *        Community manager: managing 1 community (Agent04)
       *        Destination: multi-level community (CommunityB, contains CommunityD)
       *        Qualifier: 1 level (Agent04, Agent06)
       */
      destinationCommunity.add("CommunityB");
      sendRelay(message4);
      Set answer1 = new HashSet();
      answer1.add(agents[3]);
      answer1.add(agents[5]);
      Timer timer1 = new Timer(5, answer1, message4, 0);
      timer1.start();
      /**
       * Test6: Sender: community member, not manager (Agent05)
       *        Community manager: managing 1 community (Agent04)
       *        Destination: multi-level community (CommunityB, contains CommunityD)
       *        Qualifier: all levels (Agent04, Agent06, Agent10, Agent11, Agent12)
       */

      Set answer2 = new HashSet();
      answer2.add(agents[3]);
      answer2.add(agents[5]);
      answer2.add(agents[9]);
      answer2.add(agents[10]);
      answer2.add(agents[11]);
      Timer timer2 = new Timer(6, answer2, message4, 0);
      timer2.start();
    }

    if(agentId.getAddress().equals("Agent07")) {
      /**
       * Test7: Sender: community manager (Agent07)
       *        Community manager: managing 1 community (Agent07)
       *        Destination: flat community (CommunityC)
       */
      destinationCommunity.add("CommunityC");
      sendRelay(message5);
      Set answer = new HashSet();
      answer.add(agents[7]);
      answer.add(agents[8]);
      Timer timer = new Timer(7, answer, message5, 0);
      timer.start();
    }

    if(agentId.getAddress().equals("Agent04")) {
      /**
       * Test8: Sender: community manager (Agent04)
       *        Community manager: managing 1 community (Agent04)
       *        Destination: multi-level community (CommunityB, contains CommunityD)
       *        Qualifier: 1 level (Agent05, Agent06)
       */
      destinationCommunity.add("CommunityB");
      sendRelay(message6);
      Set answer1 = new HashSet();
      answer1.add(agents[4]);
      answer1.add(agents[5]);
      Timer timer1 = new Timer(8, answer1, message6, 0);
      timer1.start();
      /**
       * Test9: Sender: community manager (Agent04)
       *        Community manager: managing 1 community (Agent04)
       *        Destination: multi-level community (CommunityB, contains CommunityD)
       *        Qualifier: all levels (Agent05, Agent06, Agent10, Agent11, Agent12)
       */
      Set answer2 = new HashSet();
      answer2.add(agents[4]);
      answer2.add(agents[5]);
      answer2.add(agents[9]);
      answer2.add(agents[10]);
      answer2.add(agents[11]);
      Timer timer2 = new Timer(9, answer2, message6, 0);
      timer2.start();
    }

    if(agentId.getAddress().equals("Agent08")) {
      /**
       * Test10: Sender: not community member (Agent08)
       *         Community manager: managing multiple communities (Agent01, manages CommunityA, CommunityE)
       *         Destination: flat community (CommunityE)
       */
      destinationCommunity.add("CommunityE");
      sendRelay(message7);
      Set answer1 = new HashSet();
      answer1.add(agents[0]);
      answer1.add(agents[12]);
      answer1.add(agents[13]);
      answer1.add(agents[14]);
      Timer timer1 = new Timer(10, answer1, message7, 0);
      timer1.start();
    }


    if(agentId.getAddress().equals("Agent13")) {
      /**
       * Test11: Sender: not community member (Agent13)
       *         Community manager: managing multiple communities, only 1 in destination
       *           (Agent01, manages CommunityA, CommunityE)
       *         Destination: multi-level community (CommunityA)
       *         Qualifier: 1 level (Agent01, Agent02, Agent03)
       */
      destinationCommunity.add("CommunityA");
      sendRelay(message8);
      Set answer2 = new HashSet();
      answer2.add(agents[0]);
      answer2.add(agents[1]);
      answer2.add(agents[2]);
      Timer timer2 = new Timer(11, answer2, message8, 0);
      timer2.start();
      /**
       * Test12: Sender: not community member (Agent13)
       *         Community manager: managing multiple communities, only 1 in destination
       *           (Agent01, manages CommunityA, CommunityE)
       *         Destination: multi-level community (CommunityA)
       *         Qualifier: 1 level (Agent01 to Agent12)
       */
      Set answer3 = new HashSet();
      for(int i=0; i<12; i++)
        answer3.add(agents[i]);
      Timer timer3 = new Timer(12, answer3, message8, 0);
      timer3.start();

      /**
       * Test15: Sender: community member, not manager (Agent13)
       *         Community manager: managing multiple communities, only 1 in destination
       *           (Agent01, manages CommunityA, CommunityE)
       *         Destination: flat community (CommunityE)
       */
      destinationCommunity.clear();
      destinationCommunity.add("CommunityE");
      sendRelay(message10);
      Set answer1  = new HashSet();
      answer1.add(agents[0]);
      answer1.add(agents[13]);
      answer1.add(agents[14]);
      Timer timer1 = new Timer(15, answer1, message10, 0.2);
      timer1.start();
    }

    if(agentId.getAddress().equals("Agent02")) {
      /**
       * Test16: Sender: community member, not manager (Agent02)
       *         Community manager: managing multiple communities, only 1 in destination
       *           (Agent01, manages CommunityA, CommunityE)
       *         Destination: multi-level community (CommunityA, contains CommunityB, CommunityC and CommunityD)
       *         Qualifier: 1 level (Agent01, Agent03)
       */
      destinationCommunity.add("CommunityA");
      sendRelay(message11);
      Set answer1 = new HashSet();
      answer1.add(agents[0]);
      answer1.add(agents[2]);
      Timer timer1 = new Timer(16, answer1, message11, 0.2);
      timer1.start();
      /**
       * Test17: Sender: community member, not manager (Agent02)
       *         Community manager: managing multiple communities, only 1 in destination
       *           (Agent01, manages CommunityA, CommunityE)
       *         Destination: multi-level community (CommunityA, contains CommunityB, CommunityC and CommunityD)
       *         Qualifier: all levels (Agent01 to Agent12 except Agent02)
       */
      Set answer2 = new HashSet();
      for(int i=0; i<12 && i != 1; i++)
        answer2.add(agents[i]);
      Timer timer2 = new Timer(17, answer2, message11, 0);
      timer2.start();

      /**
       * Test18: Sender: community member, not manager (Agent02)
       *         Community manager: managing multiple communities, multiple in destination
       *           (Agent01, manages CommunityA, CommunityE)
       *         Destination: multi-level community (CommunityA, CommunityE)
       *         Qualifier: 1 level (Agent01, Agent03, Agent13, Agent14, Agent15)
       */
      destinationCommunity.clear();
      destinationCommunity.add("CommunityA");
      destinationCommunity.add("CommunityE");
      sendRelay(message12);
      Set answer3 = new HashSet();
      answer3.add(agents[0]);
      answer3.add(agents[2]);
      for(int i=12; i<15; i++)
        answer3.add(agents[i]);
      Timer timer3 = new Timer(18, answer3, message12, 0.1);
      timer3.start();
      /**
       * Test19: Sender: community member, not manager (Agent02)
       *         Community manager: managing multiple communities, multiple in destination
       *           (Agent01, manages CommunityA, CommunityE)
       *         Destination: multi-level community (CommunityA, CommunityE)
       *         Qualifier: all levels (Agent01 to Agent15 except Agent02)
       */
      Set answer4 = new HashSet();
      for(int i=0; i<15 && i != 1; i++)
        answer4.add(agents[i]);
      Timer timer4 = new Timer(19, answer4, message12, 0);
      timer4.start();
    }

    if(agentId.getAddress().equals("Agent01")) {
      /**
       * Test20: Sender: community manager (Agent01)
       *         Community manager: managing multiple communities, only 1 in destination
       *           (Agent01, manages CommunityA, CommunityE)
       *         Destination: flat community (CommunityE)
       */
      destinationCommunity.add("CommunityE");
      sendRelay(message13);
      Set answer1 = new HashSet();
      for(int i=12; i<15; i++)
        answer1.add(agents[i]);
      Timer timer1 = new Timer(20, answer1, message13, 0);
      timer1.start();

      /**
       * Test21: Sender: community manager (Agent01)
       *         Community manager: managing multiple communities, only 1 in destination
       *           (Agent01, manages CommunityA, CommunityE)
       *         Destination: multi-level community (CommunityA, contains CommunityB, CommunityC and CommunityD)
       *         Qualifier: 1 level (Agent02, Agent03)
       */
      destinationCommunity.clear();
      destinationCommunity.add("CommunityA");
      sendRelay(message14);
      Set answer2 = new HashSet();
      answer2.add(agents[1]);
      answer2.add(agents[2]);
      Timer timer2 = new Timer(21, answer2, message14, 0.1);
      timer2.start();
      /**
       * Test22: Sender: community manager (Agent01)
       *         Community manager: managing multiple communities, only 1 in destination
       *           (Agent01, manages CommunityA, CommunityE)
       *         Destination: multi-level community (CommunityA, contains CommunityB, CommunityC and CommunityD)
       *         Qualifier: all levels (Agent02 to Agent12)
       */
      Set answer3 = new HashSet();
      for(int i=1; i<12; i++)
        answer3.add(agents[i]);
      Timer timer3 = new Timer(22, answer3, message14, 0.1);
      timer3.start();

      /**
       * Test23: Sender: community manager (Agent01)
       *         Community manager: managing multiple communities, multiple in destination
       *           (Agent01, manages CommunityA, CommunityE)
       *         Destination: multi-level community (CommunityA, CommunityE)
       *         Qualifier: 1 level (Agent02, Agent03, Agent13, Agent14, Agent15)
       */
      destinationCommunity.clear();
      destinationCommunity.add("CommunityA");
      destinationCommunity.add("CommunityE");
      sendRelay(message15);
      Set answer4 = new HashSet();
      answer4.add(agents[1]);
      answer4.add(agents[2]);
      for(int i=12; i<15; i++)
        answer4.add(agents[i]);
      Timer timer4 = new Timer(23, answer4, message15, 0.1);
      timer4.start();
      /**
       * Test24: Sender: community manager (Agent01)
       *         Community manager: managing multiple communities, multiple in destination
       *           (Agent01, manages CommunityA, CommunityE)
       *         Destination: multi-level community (CommunityA, CommunityE)
       *         Qualifier: all levels (Agent02 to Agent15)
       */
      Set answer5 = new HashSet();
      for(int i=1; i<15; i++)
        answer5.add(agents[i]);
      Timer timer5 = new Timer(24, answer5, message15, 0.1);
      timer5.start();
    }
  }

  /**
  */
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
        if(tr.getMessage().equals(message9) || tr.getMessage().equals(message12)
           || tr.getMessage().equals(message15)){
          if(!responders.containsKey(tr.getMessage()))
            responders.put(tr.getMessage(), (Set)tr.getResponse());
          else {
            Set temp1 = (Set)tr.getResponse();
            Set temp2 = (Set)responders.get(tr.getMessage());
            for(Iterator iter = temp1.iterator(); iter.hasNext();) {
              String obj = (String)iter.next();
              if(!temp2.contains(obj))
                temp2.add(obj);
            }
          }
        }
        else
          responders.put(tr.getMessage(), (Set)tr.getResponse());
        logger.info("TestRelay response(s) received: responders=" + tr.getResponse());
      }
    }

  }

  private void sendRelay(String message) {

    // If this agent is responsible for sending the test message
    if (isSender()) {
      // Subscribe to TestRelay responses
      testRelayRespSub = (IncrementalSubscription)blackboard.subscribe(testRelayRespPredicate);

      for(Iterator it = destinationCommunity.iterator(); it.hasNext();)
      {
        String communityName = (String)it.next();
        TestRelay content = new TestRelayImpl(agentId, message, uidService.nextUID());
        RelayAdapter myTestRelay = new RelayAdapter(content.getSource(),
                                                  content,
                                                  content.getUID());

        // Address ABA to all member agents in destination community
        AttributeBasedAddress target =
          AttributeBasedAddress.getAttributeBasedAddress(communityName,
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

  }

  // Returns true if this agent is responsible for publishing TestRelay and
  // collecting responses.
  private boolean isSender() {
    return destinationCommunity.size() > 0;
    //return senders.contains(agentId.getAddress());
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

  private class Timer extends Thread {
    private Set answer;
    String message;
    int testNum;
    double speedFactor;
    public Timer(int testNum, Set answer, String message, double speedFactor) {
      this.answer = answer;
      this.message = message;
      this.testNum = testNum;
      this.speedFactor = speedFactor;
    }
    public void run(){
        try{
          boolean result = false;
          long interval = MIN_DELAY + testNum * 2000;
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

  private final String message1 = "s: non-member, cm: 1, d: flat";
  private final String message2 = "s: non-member, cm: 1, d: multi";
  private final String message3 = "s: member, cm: 1, d: flat";
  private final String message4 = "s: member, cm: 1, d: multi";
  private final String message5 = "s: manager, cm: 1, d: flat";
  private final String message6 = "s: manager, cm: 1, d: multi";
  private final String message7 = "s: non-member, cm: m, d: flat";
  private final String message8 = "s: non-member, cm: m, d: multi";
  private final String message9 = "s: non-member, cm: m, d: multi-multi";
  private final String message10 = "s: member, cm: m, d: flat";
  private final String message11 = "s: member, cm: m, d: multi";
  private final String message12 = "s: member, cm: m, d: multi-multi";
  private final String message13 = "s: manager, cm: m, d: flat";
  private final String message14 = "s: manager, cm: m, d: multi";
  private final String message15 = "s: manager, cm: m, d: multi-multi";
  private final String[] agents = new String[]{"Agent01", "Agent02", "Agent03", "Agent04",
    "Agent05", "Agent06", "Agent07", "Agent08", "Agent09", "Agent10", "Agent11", "Agent12",
    "Agent13", "Agent14", "Agent15"};
}
