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

import org.cougaar.core.blackboard.IncrementalSubscription;
import org.cougaar.planning.plugin.legacy.SimplePlugin;
import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.service.LoggingService;
import org.cougaar.core.service.BlackboardService;
import org.cougaar.core.service.DomainService;
import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.mts.MessageAddress;
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

  private static int ctr = 0;

  private static String communityName = "MiniTestConfig-COMM";
  private LoggingService log;
  private CommunityService cs;
  private BlackboardService bbs = null;
  private MessageAddress myAgent = null;
  private TestRelayFactory testRelayFactory;

  private TestRelay myTestRelay;

  // Map of agents within node that have received the Relay
  private static Map responders = new HashMap();

  // Adds an agent to nodes responder Map
  public static synchronized int addResponder(String name) {
    if (!responders.containsKey(name)) {
      responders.put(name, new Integer(++ctr));
    }
    return ((Integer)responders.get(name)).intValue();
  }


  public void setParameter(Object obj) {
    List args = (List)obj;
    if (args.size() > 0)
      communityName = (String)args.get(0);
  }

  protected void setupSubscriptions() {

    log =  (LoggingService) getBindingSite().getServiceBroker().
      getService(this, LoggingService.class, null);

    DomainService domainService =
      (DomainService) getBindingSite().getServiceBroker().getService(this, DomainService.class, null);

    testRelayFactory = ((TestRelayFactory) domainService.getFactory("test"));

    cs = getCommunityService();

    bbs = getBlackboardService();

    myAgent = getMessageAddress();

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

    // The community manager is responsible for publishing the TestRelay
    if (communityManager != null && communityManager.equals(myAgent.toString())) {
      myTestRelay =
        testRelayFactory.newTestRelay("TestRelay published: agent=" +
          myAgent.toString() + " to=" + communityName, myAgent);
      myTestRelay.addTarget(AttributeBasedAddress.getAttributeBasedAddress(communityName, "Role", "Member"));
      log.info("TestRelay published");
      publishAdd(myTestRelay);
    }
  }

  public void execute() {
    Enumeration enum  = testRelaySub.getAddedList();
    while (enum.hasMoreElements()) {
      TestRelay tr = (TestRelay)enum.nextElement();
      log.info("  " + addResponder(myAgent.toString()) + " - " + myAgent);
    }
    enum  = testRelaySub.getChangedList();
    while (enum.hasMoreElements()) {
      TestRelay tr = (TestRelay)enum.nextElement();
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

}
