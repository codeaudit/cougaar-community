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

import org.cougaar.community.init.CommunityInitializerService;
import org.cougaar.community.init.CommunityConfig;
import org.cougaar.community.init.EntityConfig;

import java.util.Collection;
import java.util.Set;
import java.util.Iterator;
import java.util.HashSet;

import org.cougaar.core.component.ServiceBroker;

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
public class SimpleCommunityAccessManager extends CommunityAccessManager {

  // The following fields are used for the default authorization policy
  private Set knownEntities; // List of predefined agents/communities in society

  public SimpleCommunityAccessManager(ServiceBroker sb) {
    super(sb);
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
                                                int operation,
                                                String target) {
    // Simply verify that requester was included in predefined
    // community configuration defined in communities.xml
    return getKnownEntities().contains(requester);
  }

  /**
   * Get entity names from communities.xml file on config path.
   * @return Set of predefined agent/community names
   */
  protected Set getKnownEntities() {
    if (knownEntities == null) {
      knownEntities = new HashSet();
      CommunityInitializerService cis = (CommunityInitializerService)
          serviceBroker.getService(this, CommunityInitializerService.class, null);
      try {
        Collection communityConfigs = cis.getCommunityDescriptions(null);
        for (Iterator it = communityConfigs.iterator(); it.hasNext(); ) {
          CommunityConfig cc = (CommunityConfig)it.next();
          knownEntities.add(cc.getName());
          for (Iterator it1 = cc.getEntities().iterator(); it1.hasNext(); ) {
            EntityConfig ec = (EntityConfig)it1.next();
            knownEntities.add(ec.getName());
          }
        }
      } catch (Exception e) {
        if (logger.isWarnEnabled()) {
          logger.warn("Unable to obtain community information for agent " +
                      agentName);
        }
      } finally {
        serviceBroker.releaseService(this, CommunityInitializerService.class,
                                     cis);
      }
    }
    return knownEntities;
  }
}
