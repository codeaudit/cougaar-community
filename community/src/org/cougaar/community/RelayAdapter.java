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

package org.cougaar.community;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.relay.Relay;
import org.cougaar.core.relay.Relay.TargetFactory;
import org.cougaar.core.util.UID;

/**
 */

public class RelayAdapter implements Relay.Source {
  protected Set interestedAgents = Collections.synchronizedSet(new HashSet());
  protected Set myTargetSet = new HashSet();
  MessageAddress source;
  Object content;
  UID myUID;
  Object resp;

  public RelayAdapter(MessageAddress source,
               Object content,
               UID uid) {
    this.source = source;
    this.content = content;
    this.myUID = uid;
  }
  public Object getContent() {
    return content;
  }
  /**
   * Get a factory for creating the target.
   */
  public TargetFactory getTargetFactory() {
    return null;
  }

  public Object getResponse() {
    return resp;
  }

  /**
   * Set the response that was sent from a target.
   **/
  public int updateResponse(MessageAddress target, Object response) {
    this.resp = response;
    return Relay.RESPONSE_CHANGE;
  }

  /**
   * Get all the addresses of the target agents to which this Relay
   * should be sent.
   **/
  public Set getTargets() {
    if (myTargetSet == null) {
      return Collections.EMPTY_SET;
    } else {
      return Collections.unmodifiableSet(myTargetSet);
    }
  }
  /**
   * Add a target destination.
   **/
  public void addTarget(MessageAddress target) {
    if (myTargetSet != null) {
      myTargetSet.add(target);
    }
  }

  public Set getInterestedAgents() {
    return interestedAgents;
  }

 public UID getUID() {
    return myUID;
  }

  /** set the UID of a UniqueObject.  This should only be done by
   * an LDM factory.  Will throw a RuntimeException if
   * the UID was already set.
   **/

  public void setUID(UID uid) {
    if (myUID != null) {
      RuntimeException rt = new RuntimeException("Attempt to call setUID() more than once.");
      throw rt;
    }
    myUID = uid;
  }

  public static String targetsToString(Relay.Source rs) {
    StringBuffer sb = new StringBuffer("[");
    if (rs != null) {
      for (Iterator it = rs.getTargets().iterator(); it.hasNext(); ) {
        MessageAddress addr = (MessageAddress)it.next();
        if (addr != null) {
          sb.append(addr.toString());
          if (it.hasNext())
            sb.append(",");
        }
      }
    }
    sb.append("]");
    return sb.toString();
  }

  public String toString() {
    return "RelayAdapter:" +
        " uid=" + myUID +
        " source=" + source +
        " content=" + content +
        " targets=" + targetsToString(this);
  }

}

