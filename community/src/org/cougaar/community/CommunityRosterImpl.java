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
package org.cougaar.community;

import java.util.*;

import org.cougaar.core.service.community.*;
import org.cougaar.core.mts.MessageAddress;

import javax.naming.*;
import javax.naming.directory.*;

/**
 * Implementation of a CommunityRoster.
 */
public class CommunityRosterImpl
  implements CommunityRoster, java.io.Serializable {

  private Community community = null;
  private boolean exists = true;

  /**
   * Constructor that creates an empty roster.
   */
  public CommunityRosterImpl(String communityName) {
    this.community = new CommunityImpl(communityName);
  }

  /**
   * Constructor that takes a community name and Collection containing
   * CommunityMember objects.
   */
  public CommunityRosterImpl(String communityName, Collection members,
      boolean exists) {
    this.community = new CommunityImpl(communityName);
    for (Iterator it = members.iterator(); it.hasNext();) {
      CommunityMember cm = (CommunityMember)it.next();
      addMember(cm);
    }
    this.exists = exists;
  }

  public CommunityRosterImpl(Community community) {
    this.community = community;
  }

  /**
   * The getCommunityName method returns the name of the community that
   * this roster represents.
   * @return Returns the community name
   **/
  public String getCommunityName() {
    return this.community.getName();
  }


  /**
   * This method identifies whether the community associated with this roster
   * currently exists.
   * @return True if the community exists
   **/
  public boolean communityExists() {
    return exists;
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
    Collection members = new ArrayList();
    for (Iterator it = community.getEntities().iterator(); it.hasNext();) {
      members.add(entityToCommunityMember((Entity)it.next()));
    }
    return members;
  }

  private CommunityMember entityToCommunityMember(Entity entity) {
    int memberType = CommunityMember.AGENT;
    if (entity instanceof Community) memberType = CommunityMember.COMMUNITY;
    CommunityMember cm = new CommunityMember(entity.getName(), memberType);
    Attribute roles = entity.getAttributes().get("Role");
    try {
      NamingEnumeration enum = roles.getAll();
      while (enum.hasMore()) {
        cm.addRoleName( (String) enum.next());
      }
    } catch (NamingException ne) {}
    return cm;
  }

  /**
   * Adds a member to community roster.
   */
  public void addMember(CommunityMember member) {
    Entity entity = null;
    if (member.isAgent()) {
      entity = new AgentImpl(member.getName());
    } else {
      entity = new CommunityImpl(member.getName());
    }
    Attribute roles = new BasicAttribute("Role");
    for (Iterator it = member.getRoles().iterator(); it.hasNext();) {
      roles.add(it.next());
    }
    Attributes attrs = new BasicAttributes();
    attrs.put(roles);
    entity.setAttributes(attrs);
    community.addEntity(entity);
  }

  /**
   * Returns a Collection of MessageAddresss identifying the agents that are
   * currently community members.
   * @return Collection of Agent MessageAddresss
   */
  public Collection getMemberAgents() {
    if(community != null) {
      Collection agents = new Vector();
      for (Iterator it = community.getEntities().iterator(); it.hasNext();) {
        Entity entity = (Entity)it.next();
        if(!(entity instanceof Community))
          agents.add(entity.getName());
      }
      return agents;
    }
    else
     return new ArrayList();
  }


  /**
   * Returns a Collection of community names identifying the communities that are
   * currently a member.
   * @return Collection of community names
   */
  public Collection getMemberCommunities() {
    if(community != null) {
      Collection communitys = new Vector();
      for (Iterator it = community.getEntities().iterator(); it.hasNext();) {
        Map.Entry entry = (Map.Entry)it.next();
        Entity entity = (Entity)entry.getValue();
        if(entity instanceof Community)
          communitys.add(entity.getName());
      }
      return communitys;
    }
    else
     return new ArrayList();
  }

  public String toString() {
    StringBuffer sb = new StringBuffer();
    if (exists) {
      sb.append("Roster for community '" + community.getName() + "':\n");
      for (Iterator it = community.getEntities().iterator(); it.hasNext();) {
        Entity entity = (Entity)it.next();
        sb.append("  name=" + entity.getName() + ", type=" +
          (entity instanceof Agent ? "agent" : "community") +
          "\n");
      }
    } else {
      sb.append("Community '" + community.getName() + "' does not exist");
    }
    return sb.toString();
  }

}