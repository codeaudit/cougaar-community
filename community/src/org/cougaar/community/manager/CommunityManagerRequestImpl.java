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

import org.cougaar.core.service.community.Entity;

import org.cougaar.core.service.community.CommunityResponse;

import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.util.UID;
import org.cougaar.core.relay.Relay;

import java.util.*;
import javax.naming.directory.ModificationItem;

/**
 * Implementation of CommunityManagerRequest interface.
 **/
public class CommunityManagerRequestImpl
  implements CommunityManagerRequest, java.io.Serializable {

  private MessageAddress source;
  private String communityName;
  private int requestType = UNDEFINED;
  private Entity entity;
  private UID uid;
  private CommunityResponse resp;
  private ModificationItem[] mods;

  /**
   * Constructor.
   */
  public CommunityManagerRequestImpl(MessageAddress     source,
                                     String             communityName,
                                     int                reqType,
                                     Entity             entity,
                                     ModificationItem[] attrMods,
                                     UID                uid) {
    this.source = source;
    this.communityName = communityName;
    this.requestType = reqType;
    this.entity = entity;
    this.mods = attrMods;
    this.uid = uid;
  }

  public String getCommunityName() {
    return communityName;
  }

  /**
   * Defines the type of request.
   *
   * @param reqType Request type
   */
  public void setRequestType(int reqType) {
    this.requestType = reqType;
  }


  public int getRequestType() {
    return this.requestType;
  }

  /**
   * Entity for requests requiring one, such as a JOIN and LEAVE.
   *
   * @param entity Affected entity
   */
  public void setEntity(Entity entity) {
    this.entity = entity;
  }


  public Entity getEntity() {
    return this.entity;
  }

  public void setResponse(CommunityResponse resp) {
    this.resp = resp;
  }

  public void setAttributeModifications(ModificationItem[] mods) {
    this.mods = mods;
  }

  public ModificationItem[] getAttributeModifications() {
    return mods;
  }

  //
  // Relay.Target Interface methods
  //
  public Object getResponse() {
    return resp;
  }

  public MessageAddress getSource() {
    return source;
  }

  public int updateContent(Object content, Relay.Token token) {
    CommunityManagerRequest cmr = (CommunityManagerRequest)content;
    this.communityName = cmr.getCommunityName();
    this.requestType = cmr.getRequestType();
    this.entity = cmr.getEntity();
    this.mods = cmr.getAttributeModifications();
    return Relay.CONTENT_CHANGE;
  }

  //
  // UniqueObject Interface methods
  //
  public void setUID(UID uid) {
    if (uid != null) {
      RuntimeException rt = new RuntimeException("Attempt to call setUID() more than once.");
      throw rt;
    }
    this.uid = uid;
  }
  public UID getUID() {
    return this.uid;
  }


  public String toXML() {
    StringBuffer sb = new StringBuffer();
    sb.append("<CommunityManagerRequest type=\"" + getRequestTypeAsString() + "\"" +
      " community=\"" + communityName + "\"" +
      " agent=\"" + getSource() + "\" />\n");
    return sb.toString();
  }

  public String getRequestTypeAsString() {
    switch (requestType) {
      case CommunityManagerRequest.UNDEFINED: return "UNDEFINED";
      case CommunityManagerRequest.JOIN: return "JOIN";
      case CommunityManagerRequest.LEAVE: return "LEAVE";
      case CommunityManagerRequest.GET_COMMUNITY_DESCRIPTOR: return "GET_COMMUNITY_DESCRIPTOR";
      case CommunityManagerRequest.MODIFY_ATTRIBUTES: return "MODIFY_ATTRIBUTES";
    }
    return "INVALID_VALUE";
  }

  /**
   * Returns a string representation of the request
   *
   * @return String - a string representation of the request.
   **/
  public String toString() {
    return "CommunityManagerRequest:" +
           " community=" + communityName +
           " entity=" + (entity == null ? "null" : entity.getName()) +
           " request=" + getRequestTypeAsString();
  }
}