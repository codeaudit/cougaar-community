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

import java.util.Set;
import javax.naming.directory.ModificationItem;

import org.cougaar.core.mts.MessageAddress;

import org.cougaar.core.service.community.CommunityResponse;
import org.cougaar.core.service.community.CommunityResponseListener;
import org.cougaar.community.CommunityServiceConstants;
import org.cougaar.core.service.community.Entity;
import org.cougaar.core.util.UniqueObject;

/**
 * Blackboard Relay used by CommunityService to send request to remote
 * Community Manager.  This interface should only be used by Community
 * Service implementations, it is not intended for use by clients.
 **/
public interface Request extends UniqueObject, CommunityServiceConstants {

  public String getCommunityName();

  public void setRequestType(int reqType);
  public int getRequestType();
  public String getRequestTypeAsString(int type);
  public MessageAddress getSource();

  public void addCommunityResponseListener(CommunityResponseListener crl);
  public Set getCommunityResponseListeners();

  public void setEntity(Entity entity);
  public Entity getEntity();

  public void setAttributeModifications(ModificationItem[] mods);
  public ModificationItem[] getAttributeModifications();

  public void setResponse(CommunityResponse resp);
  public Object getResponse();

}
