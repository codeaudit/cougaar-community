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
import org.cougaar.core.service.LoggingService;
import org.cougaar.core.service.BlackboardService;
import org.cougaar.core.service.DomainService;
import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.agent.ClusterIdentifier;
import org.cougaar.multicast.AttributeBasedAddress;

import org.cougaar.util.UnaryPredicate;

/**
 * This plugin tests the use of ABAs and Relays.
 */
public class ABATestPlugin extends SimplePlugin {

  private static final String communityName = "MiniTestConfig-COMM";
  private LoggingService log;
  private BlackboardService bbs = null;
  private ClusterIdentifier myAgent = null;
  private TestRelayFactory testRelayFactory;

  protected void setupSubscriptions() {

    log =  (LoggingService) getBindingSite().getServiceBroker().
      getService(this, LoggingService.class, null);

    DomainService domainService =
      (DomainService) getBindingSite().getServiceBroker().getService(this, DomainService.class, null);

    testRelayFactory = ((TestRelayFactory) domainService.getFactory("test"));

    bbs = getBlackboardService();

    myAgent = getClusterIdentifier();

    TestRelay tr =
      testRelayFactory.newTestRelay("TestRelay sent: agent=" +
        myAgent.toString() + " community=" + communityName, myAgent);
    tr.addTarget(new AttributeBasedAddress(communityName, "Role", "Member"));
    log.info("Sending TestRelay: source=" + myAgent + " destination=" + communityName);
    publishAdd(tr);
  }

  public void execute() {
  }

}