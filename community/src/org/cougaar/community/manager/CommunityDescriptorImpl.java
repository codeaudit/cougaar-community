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

import org.cougaar.core.service.community.Community;
import org.cougaar.core.service.community.CommunityChangeEvent;
import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.util.UID;
import org.cougaar.core.relay.Relay;

import org.cougaar.core.persist.NotPersistable;

import org.cougaar.community.CommunityDescriptor;
import org.cougaar.community.CommunityImpl;

import java.util.*;

/**
 * Implementation of CommunityDescriptor interface.
 **/
public class CommunityDescriptorImpl
  implements CommunityDescriptor, java.io.Serializable, NotPersistable {

  private MessageAddress source;
  private Community community;
  private UID uid;
  private int changeType;
  private String whatChanged;


  /**
   * Constructor.
   * @param communityName  Name of  community
   */
  public CommunityDescriptorImpl(MessageAddress source,
                                 Community community,
                                 UID uid) {
    this.source = source;
    this.community = community;
    this.uid = uid;
    this.changeType = CommunityChangeEvent.ADD_COMMUNITY;
    this.whatChanged = community.getName();
  }

  /**
   * Gets the community.
   *
   * @return Community
   */
  public Community getCommunity() {
    return community;
  }

  public String getName() {
    return community.getName();
  }

  public void setChangeType(int changeType) {
    this.changeType = changeType;
  }
  public int getChangeType() {
    return changeType;
  }

  public void setWhatChanged(String name) {
    this.whatChanged = name;
  }
  public String getWhatChanged() {
    return whatChanged;
  }

  //
  // Relay.Target Interface methods
  //
  public Object getResponse() {
    return null;
  }

  public MessageAddress getSource() {
    return source;
  }

  public int updateContent(Object content, Relay.Token token) {
    CommunityDescriptor cd = (CommunityDescriptorImpl)content;
    Community updatedCommunity = cd.getCommunity();
    setWhatChanged(cd.getWhatChanged());
    setChangeType(cd.getChangeType());
    this.community.setAttributes(updatedCommunity.getAttributes());
    ((CommunityImpl)this.community).setEntities(new ArrayList(updatedCommunity.getEntities()));
    return Relay.CONTENT_CHANGE;
  }

  public String toXML() {
    return community.toXml();
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

  /**
   * Returns a string representation
   *
   * @return String - a string representation
   **/
  public String toString() {
    return "CommunityDescriptor: community=" + community.getName();
  }
}