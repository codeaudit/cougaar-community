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

package org.cougaar.community.requests;

import org.cougaar.core.service.community.CommunityService;
import org.cougaar.core.service.community.Entity;
import org.cougaar.core.util.UID;

import javax.naming.directory.Attributes;

/**
 * Request to join (and optionally create) a community.
 */
public class JoinCommunity
    extends CommunityRequest implements java.io.Serializable {

  private String entityName;
  private Attributes entityAttrs;
  private int entityType;
  private boolean createIfNotFound;
  private Attributes communityAttrs;

  public JoinCommunity(String                    communityName,
                       String                    entityName,
                       int                       entityType,
                       Attributes                entityAttrs,
                       UID                       uid) {
    this(communityName, entityName, entityType, entityAttrs, false, null, uid);
  }

  public JoinCommunity(String                    communityName,
                       String                    entityName,
                       int                       entityType,
                       Attributes                entityAttrs,
                       boolean                   createIfNotFound,
                       Attributes                communityAttrs,
                       UID                       uid) {
    super(communityName, uid);
    this.entityName = entityName;
    this.entityType = entityType;
    this.entityAttrs = entityAttrs;
    this.createIfNotFound = createIfNotFound;
    this.communityAttrs = communityAttrs;
  }

  public String getEntityName() {
    return entityName;
  }

  public int getEntityType() {
    return entityType;
  }

  public Attributes getEntityAttributes() {
    return entityAttrs;
  }

  public boolean createIfNotFound() {
    return createIfNotFound;
  }

  public Attributes getCommunityAttributes() {
    return communityAttrs;
  }

  private String entityTypeAsString(int type) {
    if (type == CommunityService.AGENT) return "AGENT";
    if (type == CommunityService.COMMUNITY) return "COMMUNITY";
    return "UNKNOWN_TYPE";
  }

  public String toString() {
    return "request=" + getRequestType() +
           " community=" + getCommunityName() +
           " entity=" + getEntityName() + "(" + entityTypeAsString(getEntityType()) + ")" +
           " createCommunityIfNotFound=" + createIfNotFound() +
           " timeout=" + getTimeout() +
           " uid=" + getUID();
  }

}