/*
 * <copyright>
 *  Copyright 2001-2003 Mobile Intelligence Corp
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
package org.cougaar.community.manager;

import org.cougaar.community.CommunityProtectionService;
import org.cougaar.community.CommunityServiceConstants;

import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.service.AgentIdentificationService;
import org.cougaar.core.service.LoggingService;
import org.cougaar.core.mts.MessageAddress;

/**
 * Performs access control for community manager.  All authorization requests
 * are delegated to the CommunityProtectionService if available.  If the
 * CommunityProtectionService is not available the requests are delegated to
 * the "authorizeUsingDefaultPolicy" method.  The base implementation of this
 * method approves all requests.  Alternate implementations should exend this
 * class and override the authorizeUsingDefaultPolicy method.  The use of an
 * alternate implementation is specified by defining the new class in the
 * "org.cougaar.community.access.manager.classname" system property.
 */
public class CommunityAccessManager
    implements CommunityProtectionService, CommunityServiceConstants {

  protected ServiceBroker serviceBroker;
  protected LoggingService logger;
  protected String agentName;

  public CommunityAccessManager(ServiceBroker sb) {
    this.serviceBroker = sb;
    agentName = getAgentName();
    logger =
      (LoggingService)serviceBroker.getService(this, LoggingService.class, null);
    logger = org.cougaar.core.logging.LoggingServiceWithPrefix.add(logger, agentName + ": ");
  }

  /**
   * Authorize request to read or modify community state.
   * @param communityName String  Name of affected community
   * @param requester String      Name of requesting agent
   * @param operation int         Requested operation (refer to
   *                         org.cougaar.core.service.CommunityServiceConstants
   *                              for valid op codes)
   * @param target String         Name of affected community member or null if
   *                              target is community
   * @return boolean              Return true if request is authorized by
   *                              current policy
   */
  public final boolean authorize(String communityName,
                                 String requester,
                                 int    operation,
                                 String target) {
    boolean isAuthorized = false;
    CommunityProtectionService cps =
        (CommunityProtectionService)serviceBroker.getService(this,
                                                             CommunityProtectionService.class,
                                                             null);
    if (cps != null) {
      isAuthorized = cps.authorize(communityName, requester, operation, target);
      serviceBroker.releaseService(this, CommunityProtectionService.class, cps);
    } else {
      isAuthorized =
        authorizeUsingDefaultPolicy(communityName, requester, operation, target);
    }
    return isAuthorized;
  }

  /**
   * Authorization method that is used if the CommunityProtectionService is
   * not available.
   * @param communityName String  Name of affected community
   * @param requester String      Name of requesting agent
   * @param operation int         Requested operation (refer to
   *                         org.cougaar.core.service.CommunityServiceConstants
   *                              for valid op codes)
   * @param target String         Name of affected community member or null if
   *                              target is community
   * @return boolean              Return true if request is authorized by
   *                              current policy
   */
  protected boolean authorizeUsingDefaultPolicy(String communityName,
                                                String requester,
                                                int    operation,
                                                String target) {
    return true;
  }

  protected String getAgentName() {
    AgentIdentificationService ais =
        (AgentIdentificationService)serviceBroker.getService(this,
        AgentIdentificationService.class, null);
    MessageAddress addr = ais.getMessageAddress();
    serviceBroker.releaseService(this, AgentIdentificationService.class, ais);
    return addr.toString();
  }

}
