/*
 * <copyright>
 *  Copyright 2003 BBNT Solutions, LLC
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

import org.cougaar.core.service.community.*;
import org.cougaar.core.blackboard.Publishable;


/**
  * The CommunityRequest provides a mechanism for components
  * to interact with the community management infrastructure.  A
  * CommunityRequest is a request by an agent to the community
  * management infrastructure to perform a specific task (such
  * as joining a community) on its behalf.
  **/

public class CommunityRequestImpl implements
  CommunityRequest, java.io.Serializable {

  private String verb = null;
  private String agentName = null;
  private String roleName = null;
  private String filter = null;
  private String sourceCommunityName = null;
  private String targetCommunityName = null;
  private CommunityResponse communityResponse = null;

  public CommunityRequestImpl() {
  }

  /**
   * The getVerb method returns the verb of the CommunityRequest.
   * For example, in the CommunityRequest "join 1ad_community...", the
   * Verb is the object represented by "join".
   * @return Returns the verb string for this CommunityRequest.
   **/

  public String getVerb() {
    return this.verb;
  }


  /**
   * The getAgentName method identifies the name of the agent
   * that is the target of this CommunityRequest.
   * @return Name of the agent that is target of this CommunityRequest
   **/

  public String getAgentName() {
    return this.agentName;
  }


  /**
   * The getTargetCommunityName method identifies the name of community
   * that is the target of the CommunityRequest.
   * For example, in the CommunityRequest "join 1ad_community...", the
   * target community is the object represented by "1ad_community".
   * @return Name of community that is target of this CommunityDirective
   **/

  public String getTargetCommunityName() {
    return this.targetCommunityName;
  }


  /**
   * The getSourceCommunityName method identifies the name of the community
   * of which the target agent is currently a member.
   * For example, in the CommunityRequest "reassign agent1 from communityA
   * to communityB", the source community is the object represented by
   * "communityA" and the target community is the object represented by
   * "communityB".
   * @return Name of community of which the target agent is currently a member
   **/

  public String getSourceCommunityName() {
    return this.sourceCommunityName;
  }


  /**
   * The getRoleName method retrieves the name of a role provided by an agent.
   * @return Name of role provided by agent.
   **/

  public String getRole() {
    return this.roleName;
  }


  /**
   * Returns the response object associated with this CommunityRequest.
   * @return CommunityResponse object associated with this request.
   */
  public CommunityResponse getCommunityResponse() {
    return this.communityResponse;
  }


  /**
   * The setVerb method sets the verb of the CommunityRequest.
   **/

  public void setVerb(String verb) {
    this.verb = verb;
  }


  /**
   * The setAgentName method identifies the name of the agent
   * that is the target of this CommunityRequest.
   * @param Name of the agent that is target of this CommunityRequest
   **/

  public void setAgentName(String agentName) {
    this.agentName = agentName;
  }


  /**
   * The setTargetCommunityName method identifies the name of community
   * that is the target of the CommunityRequest.
   * For example, in the CommunityRequest "join 1ad_community...", the
   * target community is the object represented by "1ad_community".
   * @return Name of community that is target of this CommunityDirective
   **/

  public void setTargetCommunityName(String communityName) {
    this.targetCommunityName = communityName;
  }


  /**
   * The setSourceCommunityName method identifies the name of the community
   * of which the target agent is currently a member.
   * For example, in the CommunityRequest "reassign agent1 from communityA
   * to communityB", the source community is the object represented by
   * "communityA" and the target community is the object represented by
   * "communityB".
   * @return Name of community of which the target agent is currently a member
   **/

  public void setSourceCommunityName(String communityName) {
    this.sourceCommunityName = communityName;
  }


  /**
   * The setRoleName method identifies the name of a role provided by an agent.
   * @param roleName  Name of role provided by agent
   **/

  public void setRole(String roleName) {
    this.roleName = roleName;
  }


  /**
   * Defines a JNDI search filter that is used in a search.
   * @param filter  JNDI search filter
   **/

  public void setFilter(String filter) {
    this.filter = filter;
  }


  public String getFilter() {
    return this.filter;
  }


  /**
   * Sets CommunityResponse object.
   **/

  protected void setCommunityResponse(CommunityResponse resp) {
    this.communityResponse = resp;
  }


  public boolean isPersistable() { return true; }
}