/*
 * <copyright>
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

import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.util.UID;

/**
 * Implementation of CommunityChangeNotification interface.
 **/
public class CommunityChangeNotificationImpl
  extends CommunityChangeNotificationAdapter {

  private String communityName = null;
  private String myToString = null;


  /**
   * Constructor.
   * @param communityName  Name of changed community
   */
    public CommunityChangeNotificationImpl(String communityName,
                                           MessageAddress source,
                                           UID uid) {
    super();
    setCommunityName(communityName);
    setSource(source);
    super.setUID(uid);
  }


  /**
   *
   * @param uid
   */
  public void setUID(UID uid) {
    throw new RuntimeException("Invalid attempt  to set UID");
  }

  /**
   * Defines the name of the changed community.
   *
   * @param communityName Name of changed community
   */
  public void setCommunityName(String communityName) {
    this.communityName = communityName;
  }


  /**
   * Gets the name of the changed community.
   *
   * @return Name of changed community
   */
  public String getCommunityName() {
    return this.communityName;
  }

  protected boolean contentChanged(CommunityChangeNotificationAdapter newCCN) {
    CommunityChangeNotificationImpl ccn =
      (CommunityChangeNotificationImpl) newCCN;

   return (super.contentChanged(ccn) ||
          !getCommunityName().equals(ccn.getCommunityName()));
  }

  /**
   * Returns a string representation of the HealthReport
   *
   * @return String - a string representation of the HealthReport.
   **/
  public String toString() {
    if (myToString == null) {
      myToString = "CommunityChangeNotification: " +
        "CommunityName=" + getCommunityName();
      myToString = myToString.intern();
    }

    return myToString;
  }
}