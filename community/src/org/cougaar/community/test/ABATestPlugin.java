/*
 * <copyright>
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
import org.cougaar.core.plugin.SimplePlugin;
import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.service.LoggingService;
import org.cougaar.core.service.BlackboardService;
import org.cougaar.core.service.DomainService;
import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.agent.ClusterIdentifier;
import org.cougaar.multicast.AttributeBasedAddress;

import org.cougaar.util.UnaryPredicate;

import org.cougaar.core.service.AlarmService;
import org.cougaar.core.agent.service.alarm.Alarm;

import org.cougaar.core.service.community.*;
import javax.naming.directory.*;

/**
 * This plugin tests the use of ABAs and Relays.
 */
public class ABATestPlugin extends SimplePlugin {

  private static Map responders = new HashMap();
  private static int ctr = 0;

  private static String communityName = "MiniTestConfig-COMM";
  private LoggingService log;
  private CommunityService cs;
  private BlackboardService bbs = null;
  private ClusterIdentifier myAgent = null;
  private TestRelayFactory testRelayFactory;

  private TestRelay myTestRelay;

  private String sender;

  public static synchronized int addResponder(String name) {
    if (!responders.containsKey(name)) {
      responders.put(name, new Integer(++ctr));
    }
    return ((Integer)responders.get(name)).intValue();
  }

  public static synchronized void clearResponders() {
    responders = new HashMap();
  }

  public static synchronized int getResponderCount() {
    return responders.size();
  }

  public void setParameter(Object obj) {
    List args = (List)obj;
    if (args.size() > 0)
      communityName = (String)args.get(0);
  }

  protected void setupSubscriptions() {

    log =  (LoggingService) getBindingSite().getServiceBroker().
      getService(this, LoggingService.class, null);

    //log.info("communityName=" + communityName + " sender=" + sender);

    DomainService domainService =
      (DomainService) getBindingSite().getServiceBroker().getService(this, DomainService.class, null);

    testRelayFactory = ((TestRelayFactory) domainService.getFactory("test"));

    cs = getCommunityService();

    bbs = getBlackboardService();

    myAgent = getClusterIdentifier();

    // Find name of community manager
    String communityManager = null;
    if (communityName != null && communityName.trim().length() > 0) {
      Attributes attrs = cs.getCommunityAttributes(communityName);
      Attribute attr = attrs.get("CommunityManager");
      try {
        if (attr != null) communityManager = (String)attr.get();
      } catch (javax.naming.NamingException ne) {
        log.error("Exception getting CommunityManager attribute, " + ne);
      }
    }

    // Subscribe to TestRelays
    testRelaySub =
      (IncrementalSubscription)getBlackboardService()
	      .subscribe(testRelayPredicate);

    if (communityManager != null && communityManager.equals(myAgent.toString())) {

      myTestRelay =
        testRelayFactory.newTestRelay("TestRelay published: agent=" +
          myAgent.toString() + " to=" + communityName, myAgent);
      myTestRelay.addTarget(new AttributeBasedAddress(communityName, "Role", "Member"));
      //log.info("TestRelay published: type=added source=" + myAgent +
      //  " community=" + communityName + " Role=Member");
      clearResponders();
      log.info("TestRelay published");
      publishAdd(myTestRelay);

      //sendRelay(true);
      //getAlarmService().addRealTimeAlarm(new WakeupAlarm(120000));
      //getAlarmService().addRealTimeAlarm(new WakeupAlarm(20000));
    }
  }

  private void sendRelay(boolean useABA) {
    TestRelay tr =
      testRelayFactory.newTestRelay("TestRelay published: agent=" +
      myAgent.toString() + " to=" + communityName, myAgent);
    if (useABA) {
      tr.addTarget(new AttributeBasedAddress(communityName, "Role", "Member"));
      log.info("TestRelay published: type=added source=" + myAgent +
         " community=" + communityName + " Role=Member");
    } else { //use explicit addressing
      String searchString = "(&(Role=Member)(EntityType=Agent))";
      Collection agents = cs.search(communityName, searchString);
      for (Iterator it = agents.iterator(); it.hasNext();) {
        tr.addTarget((ClusterIdentifier)it.next());
      }
      //log.info("TestRelay published: type=added source=" + myAgent +
      //  " targets=" + targetsToString(tr) + " Role=Member");
    }
    publishAdd(tr);
  }

  public void execute() {
    Enumeration enum  = testRelaySub.getAddedList();
    while (enum.hasMoreElements()) {
      TestRelay tr = (TestRelay)enum.nextElement();
      //log.info("TestRelay received: type=added dest=" + myAgent +
      //  " source=" + tr.getSource());
      log.info("  " + addResponder(myAgent.toString()) + " - " + myAgent);
    }
    enum  = testRelaySub.getChangedList();
    while (enum.hasMoreElements()) {
      TestRelay tr = (TestRelay)enum.nextElement();
      //log.info("TestRelay received: type=changed dest=" + myAgent +
      //  " source=" + tr.getSource());
      log.info("  " + addResponder(myAgent.toString()) + " - " + myAgent);
    }
  }

  /**
   * Gets reference to CommunityService.
   */
  private CommunityService getCommunityService() {
    CommunityService cs = null;
    ServiceBroker sb = getBindingSite().getServiceBroker();
    while (!sb.hasService(CommunityService.class)) {
      try { Thread.sleep(500); } catch (Exception ex) {}
    }
    return (CommunityService)sb.getService(this, CommunityService.class, null);
  }

  private IncrementalSubscription testRelaySub;
  private UnaryPredicate testRelayPredicate = new UnaryPredicate() {
    public boolean execute (Object o) {
      if (o instanceof TestRelay) {
        TestRelay tr = (TestRelay)o;
        return (tr.getTargets() == Collections.EMPTY_SET);
      }
      return false;
  }};

  private String targetsToString(TestRelay tr) {
    StringBuffer sb = new StringBuffer("[");
    for (Iterator it = tr.getTargets().iterator(); it.hasNext();) {
      sb.append(it.next().toString());
      if (it.hasNext()) sb.append(",");
    }
    sb.append("]");
    return sb.toString();
  }

  private class WakeupAlarm implements Alarm {
    private long expirationTime = -1;
    private boolean expired = false;

    /**
     * Create an Alarm to go off in the milliseconds specified,.
     **/
    public WakeupAlarm (long delay) {
      expirationTime = delay + System.currentTimeMillis();
    }

    /** @return absolute time (in milliseconds) that the Alarm should
     * go off.
     * This value must be implemented as a fixed value.
     **/
    public long getExpirationTime () {
      return expirationTime;
    }

    /**
     * Called by the cluster clock when clock-time >= getExpirationTime().
     **/
    public void expire () {
      if (!expired) {
        try {
          openTransaction();
          //log.info("TestRelay published: type=changed source=" + myAgent +
          //  " community=" + communityName + " Role=Member");
          //clearCtr();
          //log.info("TestRelay published");
          //publishChange(myTestRelay);
          //sendRelay(true);
          if (getResponderCount() == 50) {
            log.info("ABA Test PASSED");
            System.exit(0);
          } else {
            log.info("ABA Test FAILED (" + getResponderCount() + "/50 agents received ABA/Relay)");
            System.exit(-1);
          }
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
         expired = true;
         closeTransaction();
        }
      }
    }

    /** @return true IFF the alarm has expired or was canceled. **/
    public boolean hasExpired () {
      return expired;
    }

    /** can be called by a client to cancel the alarm.  May or may not remove
     * the alarm from the queue, but should prevent expire from doing anything.
     * @return false IF the the alarm has already expired or was already canceled.
     **/
    public synchronized boolean cancel () {
      if (!expired)
        return expired = true;
      return false;
    }
  }
}