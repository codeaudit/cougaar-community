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

import java.util.Properties;
import javax.naming.directory.BasicAttributes;

import junit.framework.*;

import org.cougaar.core.service.community.Agent;
import org.cougaar.core.service.community.Community;
import org.cougaar.core.service.community.CommunityChangeEvent;
import org.cougaar.core.service.community.CommunityChangeListener;
import org.cougaar.core.service.community.CommunityResponse;
import org.cougaar.core.service.community.CommunityResponseListener;

import org.cougaar.community.CommunityImpl;
import org.cougaar.community.AgentImpl;
import org.cougaar.community.CommunityMemberships;
import org.cougaar.community.CommunityMembershipsListener;
import org.cougaar.community.CommunityUtils;
import org.cougaar.community.util.Semaphore;

import org.apache.log4j.PropertyConfigurator;

/**
 * Test automatic re-join capability when community memberships are unexpectedly
 * lost.
 *
 */
public class MembershipWatcherTest extends TestCase {

  protected static final long TIMEOUT = 5000;
  protected static final String AGENT = "Test_Agent";
  protected static final String COMMUNITY = "Test_Community";
  protected static final String SUBCOMMUNITY = "Test_SubCommunity";
  protected CommunityServiceTestImpl commSvc;
  protected CommunityManagerTestImpl commMgr;
  protected CommunityResponse commResp; // Callback response
  protected CommunityChangeEvent commChangeEvent;

  protected String loggingProps[][] = {
      {"log4j.category.org.cougaar.community","INFO"},
      {"log4j.category.org.cougaar.community.MembershipWatcher","WARN"},
      {"log4j.category.org.cougaar.community.test","INFO"}
  };

  public MembershipWatcherTest(String name) {
    super(name);
    //PropertyConfigurator.configure("configs/loggingConfig.conf");
    PropertyConfigurator.configure(getLoggingProps());
  }

  protected Properties getLoggingProps() {
    Properties props = new Properties();
    for (int i = 0; i < loggingProps.length; i++) {
      props.put(loggingProps[i][0], loggingProps[i][1]);
    }
    return props;
  }

  protected void setUp() {
    commSvc = new CommunityServiceTestImpl(AGENT);
    commMgr = CommunityManagerTestImpl.getInstance();
    commMgr.reset();
    commResp = null; // Clear response before each test
    commChangeEvent = null;
    commSvc.getCache().clear();
  }

  public static Test suite() {
    return new TestSuite(MembershipWatcherTest.class);
    //Use following to run specific tests only
    //TestSuite suite= new TestSuite();
    //suite.addTest(new MembershipWatcherTest("testJoinCommunity"));
    //return suite;

  }

  /**
   * Test ability of MembershipWatcher to detect the addition of a new
   * agent to a community.
   */
  public void testJoinCommunity() {
    final Semaphore s = new Semaphore(-1);
    CommunityMemberships myCommunities = commSvc.getCommunityMemberships();
    myCommunities.addListener(new CommunityMembershipsListener() {
      public void membershipsChanged() {
        s.release();
      }
    });
    CommunityResponseListener crl = new CommunityResponseListener() {
      public void getResponse(CommunityResponse resp) {
        commResp = resp;
        s.release();
      }
    };
    //test one agent joins the community
    try {
      commSvc.joinCommunity(COMMUNITY, AGENT, commSvc.AGENT, null, true, null, crl);
      s.attempt(TIMEOUT);
    } catch (Exception ex) {
      ex.printStackTrace();
      fail();
    }
    Community community = (Community)commResp.getContent();
    assertTrue(commResp.getStatus() == CommunityResponse.SUCCESS &&
               community != null &&
               community.hasEntity(AGENT) &&
               myCommunities.contains(COMMUNITY, AGENT));
  }

  /**
   * Test ability of MembershipWatcher to detect when an agent leaves a
   * community.
   */
  public void testLeaveCommunity() {
    final Semaphore s = new Semaphore( -1);

    Community comm = new CommunityImpl(COMMUNITY);
    Agent agent = new AgentImpl(AGENT);
    comm.addEntity(agent);
    commMgr.addCommunity(comm);

    CommunityMemberships myCommunities = commSvc.getCommunityMemberships();
    myCommunities.add(COMMUNITY, agent);
    myCommunities.addListener(new CommunityMembershipsListener() {
      public void membershipsChanged() {
        s.release();
      }
    });
    CommunityResponseListener crl = new CommunityResponseListener() {
      public void getResponse(CommunityResponse resp) {
        commResp = resp;
        s.release();
      }
    };

    try {
      commSvc.leaveCommunity(COMMUNITY, AGENT, crl);
      s.attempt(TIMEOUT);
    } catch (Exception ex) {
      ex.printStackTrace();
      fail();
    }
    Community community = (Community)commResp.getContent();
    assertTrue(commResp.getStatus() == CommunityResponse.SUCCESS &&
               community != null &&
               !community.hasEntity(AGENT) &&
               myCommunities.listCommunities().isEmpty());
  }

  /**
   * Test ability of MembershipWatcher to detect an inconsistency with
   * memberships and community state.  An automatic re-join of community
   * is initiated.
   */
  public void testAutoRejoin() {
    final Semaphore s = new Semaphore(-1);

    Community comm = new CommunityImpl(COMMUNITY);
    commMgr.addCommunity(comm);

    CommunityMemberships myCommunities = commSvc.getCommunityMemberships();
    myCommunities.add(COMMUNITY, new AgentImpl(AGENT));
    myCommunities.addListener(new CommunityMembershipsListener() {
      public void membershipsChanged() {
        s.release();
      }
    });

    commSvc.addListener(new CommunityChangeListener() {
      public String getCommunityName() { return COMMUNITY; }
      public void communityChanged(CommunityChangeEvent cce) {
        if (cce.getType() == cce.ADD_ENTITY &&
            cce.getCommunityName().equals(COMMUNITY) &&
            AGENT.equals(cce.getWhatChanged())) {
          commChangeEvent = cce;
          commSvc.removeListener(this);
          s.release();
        }
      }
    });

    try {
      commSvc.getMembershipWatcher().validate();
      s.attempt(TIMEOUT);
    } catch (Exception ex) {
      ex.printStackTrace();
      fail();
    }
    Community community = commChangeEvent != null
                            ? commChangeEvent.getCommunity()
                            : null;
    assertTrue(community != null &&
               community.hasEntity(AGENT) &&
               myCommunities.contains(COMMUNITY, AGENT));
  }

  /**
   * Test ability of MembershipWatcher to detect an inconsistency with
   * memberships and community state.  An automatic re-join of a nested community
   * is initiated.
   */
  public void testAutoRejoinNestedCommunity() {
    final Semaphore s = new Semaphore(-1);

    Community comm = new CommunityImpl(COMMUNITY);
    commMgr.addCommunity(comm);

    Community nestedComm =
        new CommunityImpl(SUBCOMMUNITY,
        new BasicAttributes("CommunityManager", AGENT));
    nestedComm.addEntity(new AgentImpl(AGENT));
    commMgr.addCommunity(nestedComm);

    CommunityMemberships myCommunities = commSvc.getCommunityMemberships();
    myCommunities.add(SUBCOMMUNITY, new AgentImpl(AGENT));
    myCommunities.add(COMMUNITY, new CommunityImpl(SUBCOMMUNITY));

    myCommunities.addListener(new CommunityMembershipsListener() {
      public void membershipsChanged() {
        s.release();
      }
    });

    commSvc.addListener(new CommunityChangeListener() {
      public String getCommunityName() { return COMMUNITY; }
      public void communityChanged(CommunityChangeEvent cce) {
        if (cce.getType() == cce.ADD_ENTITY &&
            cce.getCommunityName().equals(COMMUNITY) &&
            SUBCOMMUNITY.equals(cce.getWhatChanged())) {
          commChangeEvent = cce;
          commSvc.removeListener(this);
          s.release();
        }
      }
    });

    try {
      commSvc.getMembershipWatcher().validate();
      s.attempt(TIMEOUT);
    } catch (Exception ex) {
      ex.printStackTrace();
      fail();
    }
    //System.out.println(((CommunityServiceTestImpl)commSvc).getCache());
    Community community = commChangeEvent != null
                            ? commChangeEvent.getCommunity()
                            : null;
    Community nestedCommunity = commSvc.getCommunity(SUBCOMMUNITY, null);
    assertTrue(community != null &&
               community.hasEntity(SUBCOMMUNITY) &&
               nestedCommunity != null &&
               CommunityUtils.hasAttribute(nestedCommunity.getAttributes(), "Parent", COMMUNITY));
  }

}
