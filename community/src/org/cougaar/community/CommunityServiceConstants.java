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
package org.cougaar.community;

/**
 * Constants used by Community Service.
 */
public interface CommunityServiceConstants {

  // Community Manager request types
  public static final int UNDEFINED                    = -1;
  public static final int JOIN                         = 0;
  public static final int LEAVE                        = 1;
  public static final int MODIFY_ATTRIBUTES            = 2;
  public static final int GET_COMMUNITY_DESCRIPTOR     = 3;
  public static final int LIST                         = 4;

  // Defines how long CommunityDescriptor updates should be aggregated before
  // sending to interested agents.
  public static final String UPDATE_INTERVAL_PROPERTY =
      "org.cougaar.community.update.interval";
  public static long DEFAULT_UPDATE_INTERVAL = 30 * 1000;

  // Defines frequency of White Pages read to verify that an agent is still
  // manager for community
  public static final String VERIFY_MGR_INTERVAL_PROPERTY =
      "org.cougaar.community.manager.check.interval";
  public static long DEFAULT_VERIFY_MGR_INTERVAL = 1 * 60 * 1000;

  // Period that a client caches its community descriptors
  public static final String CACHE_EXPIRATION_PROPERTY =
      "org.cougaar.community.cache.expiration";
  public static long DEFAULT_CACHE_EXPIRATION = 20 * 60 * 1000;

  // Classname of CommunityAccessManager to use for request authorization
  public static final String COMMUNITY_ACCESS_MANAGER_PROPERTY =
      "org.cougaar.community.access.manager.classname";
  public static String DEFAULT_COMMUNITY_ACCESS_MANAGER_CLASSNAME =
      "org.cougaar.community.manager.CommunityAccessManager";

  // Defines how often an agent will check parent communities to verify
  // correct state
  public static final String VERIFY_MEMBERSHIPS_INTERVAL_PROPERTY =
      "org.cougaar.community.verify.memberships.interval";
  public static long DEFAULT_VERIFY_MEMBERSHIPS_INTERVAL = 1 * 60 * 1000;


}
