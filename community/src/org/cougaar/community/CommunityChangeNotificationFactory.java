/*
 * <copyright>
 *  Copyright 2002 Mobile Intelligence Corporation
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

import org.cougaar.core.domain.Factory;
import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.service.UIDServer;


/**
 * Community factory implementation.
 **/

public class CommunityChangeNotificationFactory
implements Factory 
{

  private final UIDServer myUIDServer;

  public CommunityChangeNotificationFactory(UIDServer myUIDServer) {
    this.myUIDServer = myUIDServer;
  }

  /**
   * newCommunityChangeNotification - returns a new CommunityChangeNotification
   *
   * @param Name of community
   */
  public CommunityChangeNotification newCommunityChangeNotification(MessageAddress source,
                                                                    String communityName,
                                                                    int type,
                                                                    String whatChanged) {
    CommunityChangeNotification ccn = new CommunityChangeNotificationImpl(source,
                                                                          communityName,
                                                                          myUIDServer.nextUID(),
                                                                          type,
                                                                          whatChanged);
    return ccn;
  }
}
