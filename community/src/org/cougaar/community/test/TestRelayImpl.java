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
package org.cougaar.community.test;

import org.cougaar.core.mts.MessageAddress;

/**
 * Implementation of CommunityChangeNotification interface.
 **/
public class TestRelayImpl
  extends TestRelayAdapter {

  private String message = null;
  private String myToString = null;


  /**
   */
    public TestRelayImpl(String message, MessageAddress source) {
    super();
    setMessage(message);
    setSource(source);
  }


  /**
   */
  public void setMessage(String message) {
    this.message = message;
  }


  /**
   */
  public String getMessage() {
    return this.message;
  }

  protected boolean contentChanged(TestRelayAdapter newTr) {
    TestRelayImpl tr = (TestRelayImpl) newTr;

    return (super.contentChanged(tr) ||
            !getMessage().equals(tr.getMessage()));
  }

  /**
   * Returns a string representation of the HealthReport
   *
   * @return String - a string representation of the HealthReport.
   **/
  public String toString() {
    if (myToString == null) {
      myToString = "TestRelay: " +
        "Message=" + getMessage();
      myToString = myToString.intern();
    }

    return myToString;
  }
}