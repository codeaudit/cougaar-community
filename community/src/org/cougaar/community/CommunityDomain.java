/*
 * <copyright>
 *  Copyright 2001-2002 Mobile Intelligence Corp.
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

package org.cougaar.community;

import java.util.*;
import javax.naming.directory.Attributes;

import org.cougaar.core.service.community.CommunityService;
import org.cougaar.community.*;
import org.cougaar.community.util.*;

import org.cougaar.core.blackboard.LogPlan;
import org.cougaar.core.blackboard.XPlanServesBlackboard;

import org.cougaar.core.domain.DomainAdapter;
import org.cougaar.core.domain.DomainBindingSite;

import org.cougaar.core.service.LoggingService;

import org.cougaar.core.mts.MessageAddress;

import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.component.ServiceProvider;
import org.cougaar.core.component.ServiceRevokedListener;
import org.cougaar.core.component.ServiceRevokedEvent;

/**
 * Community domain implementation.
 *
 * @see org.cougaar.core.domain.DomainAdapter
 **/

public class CommunityDomain extends DomainAdapter {
  private static final String COMMUNITY = "community".intern();

  private LoggingService log;
  private MessageAddress myAgent = null;

  /**
   * getDomainName - returns the Domain name. Used as domain identifier in
   * DomainService.getFactory(String domainName
   *
   * @return String domain name
   */
  public String getDomainName() {
    return COMMUNITY;
  }

  public CommunityDomain() {
    super();
  }

  public void initialize() {
    super.initialize();
  }

  public void load() {
    super.load();
    log =
      (LoggingService) getBindingSite().getServiceBroker().
        getService(this, LoggingService.class, null);
  }


  protected void loadFactory() {
    DomainBindingSite bindingSite = (DomainBindingSite) getBindingSite();

    if (bindingSite == null) {
      throw new RuntimeException("Binding site for the domain has not be set.\n" +
                                 "Unable to initialize domain Factory without a binding site.");
    }

    setFactory(new CommunityChangeNotificationFactory(bindingSite.getClusterServesLogicProvider().getLDM()));
  }

  protected void loadXPlan() {
    DomainBindingSite bindingSite = (DomainBindingSite) getBindingSite();

    if (bindingSite == null) {
      throw new RuntimeException("Binding site for the domain has not be set.\n" +
                             "Unable to initialize domain XPlan without a binding site.");
    }

    Collection xPlans = bindingSite.getXPlans();
    LogPlan logPlan = null;

    for (Iterator iterator = xPlans.iterator(); iterator.hasNext();) {
      XPlanServesBlackboard  xPlan = (XPlanServesBlackboard) iterator.next();
      if (xPlan instanceof LogPlan) {
        logPlan = (LogPlan) logPlan;
        break;
      }
    }

    if (logPlan == null) {
      logPlan = new LogPlan();
    }

    setXPlan(logPlan);
  }

  protected void loadLPs() {
  }

}
