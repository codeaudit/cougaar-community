/*
 * <copyright>
 *  
 *  Copyright 2001-2004 Mobile Intelligence Corp
 *  under sponsorship of the Defense Advanced Research Projects
 *  Agency (DARPA).
 * 
 *  You can redistribute this software and/or modify it under the
 *  terms of the Cougaar Open Source License as published on the
 *  Cougaar Open Source Website (www.cougaar.org).
 * 
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 *  A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 *  OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 *  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 *  LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 *  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 *  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 *  OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *  
 * </copyright>
 */
package org.cougaar.community.test;

import java.util.Set;

import org.cougaar.util.log.LoggerFactory;

import org.cougaar.community.CommunityCache;
import org.cougaar.community.CommunityImpl;
import org.cougaar.community.CommunityUtils;
import org.cougaar.core.service.community.FindCommunityCallback;
import org.cougaar.community.CommunityUpdateListener;

import org.cougaar.community.manager.AbstractCommunityManager;

import org.cougaar.core.service.community.Community;

/**
 * Manager for one or more communities.  Handles requests to join and leave a
 * community.  Disseminates community descriptors to community members
 * and other interested agents.  This implementation is used in support
 * of stand-alone unit testing, it does not support distributed operation
 * and will not perform correctly in a live Cougaar society.
 */
public class CommunityManagerTestImpl extends AbstractCommunityManager {

  protected CommunityUpdateListener updateListener;
  static private CommunityManagerTestImpl instance;
  static protected CommunityCache cache;

  /**
   * Construct CommunityManager component capable of communicating with remote
   * agents via Blackboard Relays.
   * @param bs       BindingSite
   */
  private CommunityManagerTestImpl(String agentName,
                                   CommunityCache cache,
                                   CommunityUpdateListener cul) {
    logger = LoggerFactory.getInstance().createLogger(
        CommunityManagerTestImpl.class);
    super.agentName = agentName;
    this.cache = cache;
    this.updateListener = cul;
  }

  /**
   * Tests whether this agent is the manager for the specified community.
   * @param communityName String
   * @return boolean
   */
  protected boolean isManager(String communityName) {
    return communities.containsKey(communityName);
  }

  /**
   * Add agents to distribution list for community updates.
   * @param communityName
   * @param targetName String
   */
  protected void addTargets(String communityName, Set targets) {
    // Intentionally empty
  }

  /**
   * Remove agents from distribution list for community updates.
   * @param communityName
   * @param targetName String
   */
  protected void removeTargets(String communityName, Set targets) {
    // Intentionally empty
  }

  /**
   * Send updated Community info to agents on distribution.
   * @param String communityName
   */
  protected void distributeUpdates(String communityName) {
    if (updateListener != null) {
      CommunityImpl community = (CommunityImpl)communities.get(communityName);
      community.setLastUpdate(System.currentTimeMillis());
      updateListener.updateCommunity((CommunityImpl)community.clone());
    }
  }

  /**
   * Get name of community manager.
   * @param communityName String
   * @param fmcb FindManagerCallback
   * @return String
   */
  public void findManager(String communityName,
                                 FindCommunityCallback fccb) {
    fccb.execute(isManager(communityName) ? agentName : null);
  }
  /**
   * Asserts community manager role.
   * @param communityName Community to manage
   */
  protected void assertCommunityManagerRole(String communityName) {
    // Intentionally empty
  }

  protected static CommunityManagerTestImpl getInstance(String agentName,
                                                        CommunityCache cache,
                                                        CommunityUpdateListener cul) {
    if (instance == null) {
      instance = new CommunityManagerTestImpl(agentName, cache, cul);
    }
    return instance;
  }

  protected static CommunityManagerTestImpl getInstance() {
    return instance;
  }

  protected void reset() {
    communities.clear();
  }

  /**
   * Sets community state for testing.
   */
  protected void addCommunity(Community community) {
    cache.update(community);
    communities.put(community.getName(), community);
    //distributeUpdates(community.getName());
  }
  protected void removeCommunity(String communityName) {
    cache.remove(communityName);
    communities.remove(communityName);
    //distributeUpdates(community.getName());
  }

}
