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

package org.cougaar.community.test;

import junit.framework.*;

import EDU.oswego.cs.dl.util.concurrent.Semaphore;

import org.cougaar.core.service.community.CommunityService;
import org.cougaar.core.service.community.Community;
import org.cougaar.core.service.community.CommunityResponse;
import org.cougaar.core.service.community.CommunityResponseListener;

import org.apache.log4j.PropertyConfigurator;

import java.util.*;

/**
 * Test CommunityService operations.
 *
 */
public class BookkeepingTest extends TestCase {

  protected static final long TIMEOUT = 5000;
  protected static final String AGENT = "Test_Agent";
  protected static final String COMMUNITY = "Test_Community";

  protected static final int NUM_AGENTS = 20;
  protected CommunityService commSvc[];
  protected CommunityManagerTestImpl commMgr;
  protected Set joinSet;

  protected String loggingProps[][] = {
      {"log4j.category.org.cougaar.community","INFO"},
      //{"log4j.category.org.cougaar.community.CommunityCache","DEBUG"},
      {"log4j.category.org.cougaar.community.test","INFO"}
  };

  public BookkeepingTest(String name) {
    super(name);
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
    commSvc = new CommunityService[NUM_AGENTS];
    for (int i = 0; i < NUM_AGENTS; i++) {
      commSvc[i] = new CommunityServiceTestImpl(AGENT + i);
      ((CommunityServiceTestImpl)commSvc[i]).getCache().clear();
    }
    commMgr = CommunityManagerTestImpl.getInstance();
    commMgr.reset();
    joinSet = Collections.synchronizedSet(new HashSet());
  }

  public static Test suite() {
    return new TestSuite(BookkeepingTest.class);
    /* Use following to run specific tests only
    TestSuite suite= new TestSuite();
    suite.addTest(new BasicTests("testGetParentCommunities"));
    return suite;*/
  }

  public static void main(String[] args) {
    junit.textui.TestRunner.run(suite());
  }

  protected void join(final int agentNum) {
    final Semaphore s = new Semaphore(0);
    CommunityResponseListener crl = new CommunityResponseListener() {
      public void getResponse(CommunityResponse resp) {
        Community community = (Community)resp.getContent();
        if (resp.getStatus() == CommunityResponse.SUCCESS) {
          joinSet.add(AGENT+agentNum);
          //System.out.println("agent=" + AGENT+agentNum + " community=" + community.toXml());
          assertTrue(community != null &&
                     community.hasEntity(AGENT+agentNum));
        } else {
          fail();
        }
        s.release();
      }
    };
    //test one agent joins the community
    try {
      commSvc[agentNum].joinCommunity(COMMUNITY, AGENT+agentNum, CommunityService.AGENT, null, true, null, crl);
      s.attempt(TIMEOUT);
    } catch (Exception ex) {
      ex.printStackTrace();
      fail();
    }
  }

  protected void leave(final int agentNum) {
    final Semaphore s = new Semaphore(0);
    CommunityResponseListener crl = new CommunityResponseListener() {
      public void getResponse(CommunityResponse resp) {
        if (resp.getStatus() == CommunityResponse.SUCCESS) {
          joinSet.remove(AGENT+agentNum);
        }
        s.release();
      }
    };
    //test one agent joins the community
    try {
      commSvc[agentNum].leaveCommunity(COMMUNITY, AGENT+agentNum, crl);
      s.attempt(TIMEOUT);
    } catch (Exception ex) {
      ex.printStackTrace();
      fail();
    }
  }

  /**
   * Perform multiple join/leaves and ensure community state is accurately
   * reflected.
   */
  public void test1() {
    for (int i = 0; i < NUM_AGENTS; i++) {
      join(i);
      Community community = commSvc[i].getCommunity(COMMUNITY, null);
      assertTrue(community != null &&
                 community.getEntities().size() == i+1 &&
                 community.hasEntity(AGENT+i));
      Collection parents =
          commSvc[i].listParentCommunities(AGENT+i, (CommunityResponseListener)null);
      assertTrue(parents != null &&
                 parents.size() == 1 &&
                 parents.contains(COMMUNITY));
    }
    assertTrue(joinSet.size() == NUM_AGENTS);
    for (int i = 0; i < NUM_AGENTS; i++) {
      leave(i);
      Community community = commSvc[i].getCommunity(COMMUNITY, null);
      assertTrue(community != null &&
                 community.getEntities().size() == NUM_AGENTS - i - 1 &&
                 !community.hasEntity(AGENT+i));
      Collection parents =
          commSvc[i].listParentCommunities(AGENT+i, (CommunityResponseListener)null);
      assertTrue(parents != null &&
                 parents.size() == 0);
    }
    assertTrue(joinSet.size() == 0);
  }

}
