/*
 * <copyright>
 *  Copyright 1997-2001 Mobile Intelligence Corp
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

import org.cougaar.core.service.community.*;
import org.cougaar.community.*;

import java.io.FileInputStream;
import java.util.*;
import javax.naming.*;
import javax.naming.directory.*;

import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.plugin.ComponentPlugin;
import org.cougaar.core.blackboard.IncrementalSubscription;
import org.cougaar.util.UnaryPredicate;
import org.cougaar.core.service.*;
import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.component.ServiceRevokedListener;
import org.cougaar.core.component.ServiceRevokedEvent;
import org.cougaar.core.util.PropertyNameValue;
import org.cougaar.core.util.UID;

import org.cougaar.util.ConfigFinder;
import org.cougaar.util.log.Logger;
import org.cougaar.util.log.LoggerFactory;

import org.cougaar.glm.ldm.asset.Organization;

/**
 * This Plugin is used to demonstrate/test community operations such as
 * JOIN and LEAVE.
 */
public class JoinTestPlugin extends ComponentPlugin {

  private Logger log = lf.createLogger(this.getClass().getName());

  // The domainService acts as a provider of domain factory services
  private DomainService domainService = null;

  /**
   * Used by the binding utility through reflection to set my DomainService
   */
  public void setDomainService(DomainService aDomainService) {
    domainService = aDomainService;
  }

  /**
   * Used by the binding utility through reflection to get my DomainService
   */
  public DomainService getDomainService() {
    return domainService;
  }

  private String communityToJoin = "MiniTestConfig";

  /**
   * Overrides name of community to join.
   * @param obj CommunityName
   */
  public void setParameter(Object obj) {
    List args = (List)obj;
    if (args.size() == 1)
      communityToJoin = (String)args.get(0);
  }

  /**
   * Join community during setup
   */
  protected void setupSubscriptions() {
    // Subscribe to CommunityRequests to receive responses
    requests = (IncrementalSubscription)getBlackboardService()
	.subscribe(communityRequestPredicate);

    // Wait for community to initialize itself.
    try { Thread.sleep(30000); } catch (Exception ex) {}

   // Join MiniTestConfig community
    log.info("Joining community " + communityToJoin);
    CommunityRequest req = new CommunityRequestImpl();
    req.setVerb("JOIN_COMMUNITY");
    req.setTargetCommunityName(communityToJoin);
    getBlackboardService().publishAdd(req);

  }

  /**
  * Receive response to Join request, wait awhile, and then send request
  * to Leave community.
  */
  protected void execute () {

    Enumeration crs = requests.getChangedList();
    while (crs.hasMoreElements()) {
      CommunityRequest req = (CommunityRequest)crs.nextElement();
      if (req.getCommunityResponse() != null && req.getVerb() != null) {
        CommunityResponse resp = req.getCommunityResponse();
        String verb = req.getVerb();
        String communityName = req.getTargetCommunityName();
        if (verb.equals("JOIN_COMMUNITY")) {
          log.debug("Join " + communityName + ": " + resp);
          getBlackboardService().publishRemove(req);

          try { Thread.sleep(30000); } catch (Exception ex) {}
          log.info("Leaving community " + communityName);
          req = new CommunityRequestImpl();
          req.setVerb("LEAVE_COMMUNITY");
          req.setTargetCommunityName(communityName);
          getBlackboardService().publishAdd(req);

        } else if (verb.equals("LEAVE")) {
          log.debug("Leave " + req.getTargetCommunityName() + ": " + resp);
          getBlackboardService().publishRemove(req);
        }
      }
    }

  }


  private IncrementalSubscription requests;
  private UnaryPredicate communityRequestPredicate = new UnaryPredicate() {
    public boolean execute (Object o) {
      return (o instanceof CommunityRequest);
  }};


  // Logging Methods

  private static LoggerFactory lf;
  static {
    lf = LoggerFactory.getInstance();

    Properties defaults = new Properties();
    defaults.setProperty("log4j.rootCategory", "INFO, A1");
    defaults.setProperty("log4j.appender.A1",
                         "org.apache.log4j.ConsoleAppender");
    defaults.setProperty("log4j.appender.A1.Target", "System.out");
    defaults.setProperty("log4j.appender.A1.layout",
                         "org.apache.log4j.PatternLayout");
    defaults.setProperty("log4j.appender.A1.layout.ConversionPattern",
                         "%d{ABSOLUTE} %-5p [ %t] - %m%n");

    Properties props = new Properties(defaults);
    // Get the debug file.
    ConfigFinder cf = ConfigFinder.getInstance();
    try {
      props.load(new FileInputStream(cf.locateFile("debug.properties")));
    } catch(Exception e) {
      System.err.println("Could not read debug properties file, using defaults");
    }

    lf.configure(props);
  }

}
