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

import org.cougaar.util.ConfigFinder;
import org.cougaar.util.log.Logger;
import org.cougaar.util.log.LoggerFactory;

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

import org.cougaar.glm.ldm.asset.Organization;

/**
 * This Plugin tests community change notifications on membership lists.
 */
public class RosterWatcherPlugin extends ComponentPlugin {

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

  private String communityOfInterest = "MiniTestConfig";

  /**
   * Overrides name of community to watch.
   * @param obj CommunityName
   */
  public void setParameter(Object obj) {
    List args = (List)obj;
    if (args.size() == 1)
      communityOfInterest = (String)args.get(0);
  }

  /**
   * Subscribe to roster for community of interest.
   */
  protected void setupSubscriptions() {
    log = getLoggingService();

    // Subscribe to CommunityRequests to get roster updates
    requests = (IncrementalSubscription)getBlackboardService()
	.subscribe(communityRequestPredicate);

    // Wait for community to initialize itself.
    try { Thread.sleep(5000); } catch (Exception ex) {}

    // Publish request
    log.debug("Getting roster for community " + communityOfInterest);
    CommunityRequest cr = new CommunityRequestImpl();
    cr.setVerb("GET_ROSTER_WITH_UPDATES");
    cr.setTargetCommunityName(communityOfInterest);
    getBlackboardService().publishAdd(cr);

  }

  /**
  * Print community roster deltas when changes are received.
  */
  protected void execute () {

    Enumeration crs = requests.getChangedList();
    while (crs.hasMoreElements()) {
      CommunityRequest cr = (CommunityRequest)crs.nextElement();
      if (cr.getVerb() != null &&
          cr.getVerb().equals("GET_ROSTER_WITH_UPDATES") &&
          cr.getTargetCommunityName().equals(communityOfInterest)) {
        CommunityResponse resp = cr.getCommunityResponse();
        CommunityRoster roster = (CommunityRoster)resp.getResponseObject();
        processChanges(roster);
      }
    }

  }

  // Membership list used for detecting additions/deletions in roster
  private List members = null;

  /**
   * Evaluate roster for additions/deletions
   * @param roster
   */
  private void processChanges(CommunityRoster roster) {
    // If first time, copy members from roster to local list and print roster
    if (members == null) {
      log.info(roster.toString());
      members = new Vector();
      Collection cmList = roster.getMembers();
      for (Iterator it = cmList.iterator(); it.hasNext();)
        members.add((CommunityMember)it.next());
    } else {
      Collection cmList = roster.getMembers();
      //log.debug(roster);
      //for (int i = 0; i < cm.length; i++)
      //  log.debug(cm[i].getName());

      // Look for additions
      for (Iterator it = cmList.iterator(); it.hasNext();) {
        CommunityMember cm = (CommunityMember)it.next();
        if (!members.contains(cm)) {
          members.add(cm);
          log.info(cm.getName() + " added to community " +
            communityOfInterest);
        }
      }
      // Look for deletions
      for (Iterator it = members.iterator(); it.hasNext();) {
        CommunityMember member = (CommunityMember)it.next();
        boolean found = false;
        for (Iterator it1 = cmList.iterator(); it1.hasNext();) {
          if (member.equals((CommunityMember)it1.next())) {
            found = true;
            break;
          }
        }
        if (!found) {
          log.info(member.getName() + " removed from community " +
            communityOfInterest);
          it.remove();
        }
      }
    }
  }

  /**
   * Gets reference to LoggingService.
   */
  private LoggingService getLoggingService() {
    ServiceBroker sb = getServiceBroker();
    return (LoggingService)sb.getService(this, LoggingService.class,
      new ServiceRevokedListener() {
        public void serviceRevoked(ServiceRevokedEvent re) {}
    });
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
