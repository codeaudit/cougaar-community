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

import org.cougaar.community.CommunityDescriptor;
import org.cougaar.community.CommunityImpl;
import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.persist.NotPersistable;
import org.cougaar.core.relay.Relay;
import org.cougaar.core.service.community.Community;
import org.cougaar.core.util.UID;

/**
 * Implementation of CommunityDescriptor interface.
 **/
public class CommunityDescriptorImpl
  implements CommunityDescriptor, java.io.Serializable, NotPersistable {

  protected MessageAddress source;
  protected Community community;
  protected UID uid;

  /**
   * Constructor.
   * @param source MessageAddress of sender
   * @param community Associated Community
   * @param uid Unique identifier
   */
  public CommunityDescriptorImpl(MessageAddress source,
                                 Community community,
                                 UID uid) {
    this.source = source;
    this.community = community;
    this.uid = uid;
  }

  /**
   * Gets the community.
   * @return Community
   */
  public Community getCommunity() {
    return community;
  }

  public String getName() {
    return community.getName();
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
    community = (Community)((CommunityImpl)cd.getCommunity()).clone();
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
   * @return String - a string representation
   **/
  public String toString() {
    return "CommunityDescriptor: community=" + community.getName();
  }
}
