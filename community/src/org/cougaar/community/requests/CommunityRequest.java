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

import org.cougaar.core.service.community.CommunityResponse;
import org.cougaar.core.util.UniqueObject;
import org.cougaar.core.util.UID;

/**
 * Base class for community requests (i.e., Join, Leave, GetDescriptor, etc.).
 */
public class CommunityRequest implements java.io.Serializable, UniqueObject {

  public static final long NEVER = -1;
  public static final long DEFAULT_TIMEOUT = NEVER;

  private String communityName;
  private String requestType;
  private CommunityResponse resp;
  private long timeout = DEFAULT_TIMEOUT;
  private UID uid;

  /**
   * Default constructor.
   */
  public CommunityRequest() {
  }

  public CommunityRequest(String                    communityName,
                          UID                       uid,
                          long                      timeout) {
    this.communityName = communityName;
    this.timeout = timeout;
    this.uid = uid;
    String classname = this.getClass().getName();
    int lastSeparator = classname.lastIndexOf(".");
    requestType = (lastSeparator == -1)
                  ? classname
                  : classname.substring(lastSeparator+1,classname.length());
  }

  public CommunityRequest(String                    communityName,
                          UID                       uid) {
    this(communityName, uid, DEFAULT_TIMEOUT);
  }

  public String getCommunityName() {
    return communityName;
  }

  public String getRequestType() {
    return requestType;
  }

  public long getTimeout() {
    return timeout;
  }

  public void setResponse(CommunityResponse resp) {
    this.resp = resp;
  }

  public CommunityResponse getResponse() {
    return resp;
  }

  public String toString() {
    return "request=" + getRequestType() +
           " community=" + getCommunityName() +
           " timeout=" + getTimeout() +
           " uid=" + getUID();
  }

  /**
   * Returns true if CommunityRequests have same request type
   * and target community.
   * @param o
   * @return
   */
  public boolean equals(Object o) {
    if (!(o instanceof CommunityRequest)) return false;
    CommunityRequest cr = (CommunityRequest)o;
    if (!requestType.equals(cr.getRequestType())) return false;
    if (communityName == null) {
      return cr.getCommunityName() == null;
    } else {
      return communityName.equals(cr.getCommunityName());
    }
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