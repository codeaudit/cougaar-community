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
import java.util.Collection;
import java.util.Iterator;

import junit.framework.*;

import javax.naming.directory.Attributes;
import javax.naming.directory.Attribute;
import javax.naming.directory.BasicAttributes;

import EDU.oswego.cs.dl.util.concurrent.Semaphore;

import org.cougaar.core.service.community.Entity;
import org.cougaar.core.service.community.CommunityService;
import org.cougaar.core.service.community.Community;
import org.cougaar.core.service.community.CommunityResponse;
import org.cougaar.core.service.community.CommunityResponseListener;

import org.cougaar.community.CommunityImpl;
import org.cougaar.community.AgentImpl;

import org.apache.log4j.PropertyConfigurator;

/**
 * Test operations involving nested communities.
 *
 */
public class NestedCommunityTest extends TestCase {

  protected static final String AGENT = "Test_Agent";
  protected static final String COMMUNITY = "Test_Community";
  protected static final String NESTED_COMMUNITY = "Nested_Community";
  protected CommunityService commSvc;
  protected CommunityManagerTestImpl commMgr;
  protected CommunityResponse topCommResp;
  protected CommunityResponse nestedCommResp;
  protected Collection searchResults;

  protected String loggingProps[][] = {
      {"log4j.category.org.cougaar.community","INFO"},
      {"log4j.category.org.cougaar.community.test","INFO"}
  };

  public NestedCommunityTest(String name) {
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
    topCommResp = null; // Clear response before each test
    nestedCommResp = null;
    ((CommunityServiceTestImpl)commSvc).getCache().clear();
    searchResults = null;
  }

  public static Test suite() {
    return new TestSuite(NestedCommunityTest.class);
    //Use following to run specific tests only
    //TestSuite suite= new TestSuite();
    //suite.addTest(new NestedCommunityTest("testLeaveCommunity"));
    //return suite;
  }

  /**
   * Test add of a nested community to a parent community.
   */
  public void testAddCommunity() {
    // Setup test state
    Community top = new CommunityImpl(COMMUNITY);
    commMgr.addCommunity(top);

    Attributes attrs = new BasicAttributes();
    attrs.put("CommunityManager", AGENT);
    Community nested = new CommunityImpl(NESTED_COMMUNITY, attrs);
    nested.addEntity(new AgentImpl(AGENT));
    commMgr.addCommunity(nested);

    Community topComm = null;
    Community nestedComm = null;
    final Semaphore s = new Semaphore(0);
    CommunityResponseListener crl = new CommunityResponseListener() {
      public void getResponse(CommunityResponse resp) {
        topCommResp = resp;
        s.release();
      }
    };
    try {
      commSvc.joinCommunity(COMMUNITY, NESTED_COMMUNITY, commSvc.COMMUNITY, null, true, null, crl);
      s.attempt(5000);
      nestedComm = commSvc.getCommunity(NESTED_COMMUNITY, null);
    } catch (Exception ex) {
      ex.printStackTrace();
      fail();
    }
    //System.out.println(CommunityCache.getCache());
    topComm = (topCommResp != null)
                                  ? (Community)topCommResp.getContent()
                                  : null;
    Attribute parentAttr = (nestedComm != null)
                                  ? nestedComm.getAttributes().get("Parent")
                                  : null;
    assertTrue(topCommResp.getStatus() == CommunityResponse.SUCCESS &&
               topComm != null && nestedComm != null &&
               topComm.hasEntity(NESTED_COMMUNITY) &&
               parentAttr != null &&
               parentAttr.contains(COMMUNITY));
  }

  /**
   * Test removal of a nested community from its parent community.
   */
  public void testLeaveCommunity() {
    // Setup test state
    Community top = new CommunityImpl(COMMUNITY);
    top.addEntity(new CommunityImpl(NESTED_COMMUNITY));
    commMgr.addCommunity(top);

    Attributes attrs = new BasicAttributes();
    attrs.put("CommunityManager", AGENT);
    attrs.put("Parent", COMMUNITY);
    Community nested = new CommunityImpl(NESTED_COMMUNITY, attrs);
    nested.addEntity(new AgentImpl(AGENT));
    commMgr.addCommunity(nested);

    Community topComm = null;
    Community nestedComm = null;
    final Semaphore s = new Semaphore(0);
    CommunityResponseListener crl = new CommunityResponseListener() {
      public void getResponse(CommunityResponse resp) {
        topCommResp = resp;
        s.release();
      }
    };
    try {
      commSvc.leaveCommunity(COMMUNITY, NESTED_COMMUNITY, crl);
      s.attempt(5000);
      nestedComm = commSvc.getCommunity(NESTED_COMMUNITY, null);
    } catch (Exception ex) {
      ex.printStackTrace();
      fail();
    }
    //System.out.println(CommunityCache.getCache());
    topComm = (topCommResp != null)
                                  ? (Community)topCommResp.getContent()
                                  : null;
    Attribute parentAttr = (nestedComm != null)
                                  ? nestedComm.getAttributes().get("Parent")
                                  : null;
    assertTrue(topCommResp.getStatus() == CommunityResponse.SUCCESS &&
               topComm != null && nestedComm != null &&
               !topComm.hasEntity(NESTED_COMMUNITY) &&
               (parentAttr == null || !parentAttr.contains(COMMUNITY)));
  }

  /**
   * Test flat search.
   */
  public void testFlatSearch() {
    // Setup test state
    Community top = new CommunityImpl(COMMUNITY);
    top.addEntity(new CommunityImpl(NESTED_COMMUNITY));
    commMgr.addCommunity(top);

    Attributes attrs = new BasicAttributes();
    attrs.put("CommunityManager", AGENT);
    attrs.put("Parent", COMMUNITY);
    Community nested = new CommunityImpl(NESTED_COMMUNITY, attrs);
    nested.addEntity(new AgentImpl("Agent1", new BasicAttributes("Attr1", "Val1")));
    nested.addEntity(new AgentImpl("Agent2", new BasicAttributes("Attr2", "Val2")));
    nested.addEntity(new AgentImpl("Agent3", new BasicAttributes("Attr3", "Val3")));
    commMgr.addCommunity(nested);

    final Semaphore s = new Semaphore(0);
    try {
      searchResults =
          commSvc.searchCommunity(COMMUNITY, "(Attr2=Val2)",
                                  false, // Flat search
                                  Community.ALL_ENTITIES,
                                  new CommunityResponseListener() {
        public void getResponse(CommunityResponse resp) {
          if (resp.getStatus() == CommunityResponse.SUCCESS) {
            searchResults = (Collection)resp.getContent();
          }
          s.release();
        }
      });
      if (searchResults != null) {
        s.release();
      }
      s.attempt(5000);
    } catch (Exception ex) {
      ex.printStackTrace();
      fail();
    }
    assertTrue(searchResults != null &&
               searchResults.size() == 0);
  }

  /**
   * Test nested search.
   */
  public void testNestedSearch() {
    // Setup test state
    Community top = new CommunityImpl(COMMUNITY);
    top.addEntity(new CommunityImpl(NESTED_COMMUNITY));
    commMgr.addCommunity(top);

    Attributes attrs = new BasicAttributes();
    attrs.put("CommunityManager", AGENT);
    attrs.put("Parent", COMMUNITY);
    Community nested = new CommunityImpl(NESTED_COMMUNITY, attrs);
    nested.addEntity(new AgentImpl("Agent1", new BasicAttributes("Attr1", "Val1")));
    nested.addEntity(new AgentImpl("Agent2", new BasicAttributes("Attr2", "Val2")));
    nested.addEntity(new AgentImpl("Agent3", new BasicAttributes("Attr3", "Val3")));
    commMgr.addCommunity(nested);

    final Semaphore s = new Semaphore(0);
    try {
      searchResults =
          commSvc.searchCommunity(COMMUNITY, "(Attr2=Val2)",
                                  true,  // Nested search
                                  Community.ALL_ENTITIES,
                                  new CommunityResponseListener() {
        public void getResponse(CommunityResponse resp) {
          if (resp.getStatus() == CommunityResponse.SUCCESS) {
            searchResults = (Collection)resp.getContent();
          }
          s.release();
        }
      });
      if (searchResults != null) {
        s.release();
      }
      s.attempt(5000);
    } catch (Exception ex) {
      ex.printStackTrace();
      fail();
    }
    assertTrue(searchResults != null &&
               searchResults.size() == 1 &&
               resultsContains(searchResults, "Agent2"));
  }

  /**
   * Test listParentCommunities of one nested community
   */
  public void testListParentCommunities() {
    // Setup test state
    Community top = new CommunityImpl(COMMUNITY);
    top.addEntity(new CommunityImpl(NESTED_COMMUNITY));
    commMgr.addCommunity(top);

    Attributes attrs = new BasicAttributes();
    attrs.put("CommunityManager", AGENT);
    attrs.put("Parent", COMMUNITY);
    Community nested = new CommunityImpl(NESTED_COMMUNITY, attrs);
    nested.addEntity(new AgentImpl(AGENT));
    commMgr.addCommunity(nested);

    final Semaphore s = new Semaphore(0);
    //Get parents of the nested community, result should be 1 community.
    try {
      searchResults =
          commSvc.listParentCommunities(NESTED_COMMUNITY,
                                  new CommunityResponseListener() {
        public void getResponse(CommunityResponse resp) {
          if (resp.getStatus() == CommunityResponse.SUCCESS) {
            searchResults = (Collection)resp.getContent();
          }
          s.release();
        }
      });
      if (searchResults != null) {
        s.release();
      }
      s.attempt(5000);
    } catch (Exception ex) {
      ex.printStackTrace();
      fail();
    }
    assertTrue(searchResults != null &&
               searchResults.size() == 1 &&
               searchResults.contains(COMMUNITY));

    //Get parents of entity in nested community, result should be 1 community.
    try {
      searchResults =
          commSvc.listParentCommunities(AGENT,
                                  new CommunityResponseListener() {
        public void getResponse(CommunityResponse resp) {
          if (resp.getStatus() == CommunityResponse.SUCCESS) {
            searchResults = (Collection)resp.getContent();
          }
          s.release();
        }
      });
      if (searchResults != null) {
        s.release();
      }
      s.attempt(5000);
    } catch (Exception ex) {
      ex.printStackTrace();
      fail();
    }
    assertTrue(searchResults != null &&
               searchResults.size() == 1 &&
               searchResults.contains(NESTED_COMMUNITY));

  }

  private boolean resultsContains(Collection results, String entityName) {
    if (results != null) {
      for (Iterator it = results.iterator(); it.hasNext();) {
        Entity entity = (Entity)it.next();
        if (entity.getName().equals(entityName)) return true;
      }
    }
    return false;
  }
}
