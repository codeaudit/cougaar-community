/*
 * <copyright>
 *  Copyright 2001-2002 Mobile Intelligence Corp.
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

import org.cougaar.core.relay.Relay;
import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.util.UniqueObject;
import org.cougaar.core.util.UID;
import java.util.Set;

/**
 * Message used to notify agents of state change in a community of interest.
 **/
public interface CommunityChangeNotification
  extends Relay.Source, Relay.Target, UniqueObject {


  /**
   * Defines the community that has changed.
   *
   * @param communityName Name of changed community
   */
  public void setCommunityName(String communityName);


  /**
   * Gets the name of changed community.
   *
   * @return Name of changed community.
   */
  public String getCommunityName();


  /**
   * Add a target message address.
   * @param target the address of the target agent.
   **/
  public void addTarget(MessageAddress target);

  /**
   * Remove a target message address.
   * @param target the address of the target agent to be removed.
   **/
  public void removeTarget(MessageAddress target);

  /**
   * Sets destination targets.
   * @param targets  Destination addresses
   */
  public void setTargets(Set targets);

  /**
   * Returns code indicating type of changed element.  Refer to
   * org.cougaar.core.service.CommunityChangeEvent for values.
   */
  public int getType();

  /**
   * Returns name of community or entity that changed.
   */
  public String whatChanged();

}
