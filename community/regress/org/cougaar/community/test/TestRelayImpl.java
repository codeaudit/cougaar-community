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

package org.cougaar.community.test;

import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.util.UID;
import org.cougaar.core.relay.Relay;

import org.cougaar.util.log.Logger;
import org.cougaar.util.log.Logging;

import java.util.*;

/**
 * Implementation of TestRelay interface.
 **/
public class TestRelayImpl implements TestRelay, java.io.Serializable {

  private MessageAddress source;
  private String message;
  private Set responders;
  private UID uid;
  private String communityName;

  /**
   * Constructor.
   */
  public TestRelayImpl(MessageAddress source,
                       String         message,
                       UID            uid) {
    this(source, message, null, uid);
  }

  /**
   * Constructor.
   */
  public TestRelayImpl(MessageAddress source,
                       String         message,
                       String         communityName,
                       UID            uid) {
    this.source = source;
    this.message = message;
    this.uid = uid;
    this.communityName = communityName;
    this.responders = new TreeSet();
  }

  public void setMessage(String msg) {
    this.message = msg;
  }

  public String getMessage() {
    return message;
  }

  public String getCommunityName() {
    return communityName;
  }

  public void setResponse(Set responders) {
    this.responders = responders;
  }

  //
  // Relay.Target Interface methods
  //
  public Object getResponse() {
    return responders;
  }

  public MessageAddress getSource() {
    return source;
  }

  public int updateContent(Object content, Relay.Token token) {
    try {
      TestRelay tr = (TestRelay)content;
      message = tr.getMessage();
    } catch (Exception ex) {
      System.out.println(ex + ", " + content.getClass().getName());
      ex.printStackTrace();
    }
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
}
