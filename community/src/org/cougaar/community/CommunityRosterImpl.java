/*
 * <copyright>
 *  Copyright 1997-2001 Mobile Intelligence Corp
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

import java.util.*;

import org.cougaar.core.service.community.*;
import org.cougaar.core.agent.ClusterIdentifier;

/**
 * Implementation of a CommunityRoster.
 */
public class CommunityRosterImpl
  implements CommunityRoster, java.io.Serializable {

  private String name;
  private Collection members = new Vector();
  private boolean exists = false;

  /**
   * Constructor that creates an empty roster.
   */
  public CommunityRosterImpl(String communityName) {
    this.name = communityName;
    this.exists = false;
  }

  /**
   * Constructor that takes a community name and Collection containing
   * CommunityMember objects.
   */
  public CommunityRosterImpl(String communityName, Collection members,
      boolean exists) {
    this.name = communityName;
    this.members = members;
    this.exists = exists;
  }

  /**
   * The getCommunityName method returns the name of the community that
   * this roster represents.
   * @return Returns the community name
   **/
  public String getCommunityName() {
    return this.name;
  }


  /**
   * This method identifies whether the community associated with this roster
   * currently exists.
   * @return True if the community exists
   **/
  public boolean communityExists() {
    return this.exists;
  }


  /**
   * This method identifies whether the community associated with this roster
   * currently exists.
   * @param exists True if the community exists
   **/
  public void setCommunityExists(boolean exists) {
    this.exists = exists;
  }


  /**
   * Returns an Collection of CommunityMember objects representing the currenty
   * community membership.
   * @return Array of CommunityMember objects
   */
  public Collection getMembers() {
    return members;
  }


  /**
   * Adds a member to community roster.
   */
  public void addMember(CommunityMember member) {
    members.add(member);
  }

  /**
   * Returns a Collection of ClusterIdentifiers identifying the agents that are
   * currently community members.
   * @return Collection of Agent ClusterIdentifiers
   */
  public Collection getMemberAgents() {
    Collection agents = new Vector();
    for (Iterator it = members.iterator(); it.hasNext();) {
      CommunityMember member = (CommunityMember)it.next();
      if (member.isAgent())
        agents.add(member.getAgentId());
    }
    return agents;
  }


  /**
   * Returns a Collection of community names identifying the communities that are
   * currently a member.
   * @return Collection of community names
   */
  public Collection getMemberCommunities() {
    Vector communityNames = new Vector();
    for (Iterator it = members.iterator(); it.hasNext();) {
      CommunityMember member = (CommunityMember)it.next();
      if (!member.isAgent())
        communityNames.add(member.getName());
    }
    return communityNames;
  }

  public String toString() {
    StringBuffer sb = new StringBuffer();
    if (exists) {
      sb.append("Roster for community '" + name + "':\n");
      for (Iterator it = members.iterator(); it.hasNext();) {
        CommunityMember member = (CommunityMember)it.next();
        sb.append("  name=" + member.getName() + ", type=" +
          (member.isAgent() ? "agent" : "community") +
          "\n");
      }
    } else {
      sb.append("Community '" + name + "' does not exist");
    }
    return sb.toString();
  }

}