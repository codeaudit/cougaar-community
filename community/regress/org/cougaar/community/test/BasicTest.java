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

import junit.framework.*;

import org.cougaar.core.service.community.CommunityService;
import org.cougaar.core.service.community.Community;
import org.cougaar.core.service.community.CommunityResponse;
import org.cougaar.core.service.community.CommunityResponseListener;

import org.cougaar.community.CommunityImpl;
import org.cougaar.community.AgentImpl;
import org.cougaar.community.util.Semaphore;

import javax.naming.directory.BasicAttributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.DirContext;
import javax.naming.NamingException;

import java.util.*;

/**
 * Test basic CommunityService operations.
 *
 */
public class BasicTest extends TestBase {

  protected static final long TIMEOUT = 5000;
  protected static final String AGENT = "Test_Agent";
  protected static final String COMMUNITY = "Test_Community";
  protected static final String SUBCOMMUNITY = "Test_SubCommunity";
  protected CommunityService commSvc;
  protected CommunityManagerTestImpl commMgr;
  protected CommunityResponse commResp; // Callback response

  protected static final String loggingProps[][] = {
      {"log4j.category.org.cougaar.community","INFO"},
      {"log4j.category.org.cougaar.community.test","INFO"}
  };

  public BasicTest(String name) {
    super(name, loggingProps);
  }

  protected void setUp() {
    commSvc = new CommunityServiceTestImpl(AGENT);
    commMgr = CommunityManagerTestImpl.getInstance();
    commMgr.reset();
    commResp = null; // Clear response before each test
    ((CommunityServiceTestImpl)commSvc).getCache().clear();
  }

  public static Test suite() {
    return new TestSuite(BasicTest.class);
    /* Use following to run specific tests only
    TestSuite suite= new TestSuite();
    suite.addTest(new BasicTests("testGetParentCommunities"));
    return suite;*/
  }

  public static void main(String[] args) {
    junit.textui.TestRunner.run(suite());
  }

  /**
   * Simple test of Join and Create community operation.  A community is
   * created and the requesting agent is added as a member.
   */
  public void testJoinCommunity() {
    final Semaphore s = new Semaphore(0);
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
               community.hasEntity(AGENT));
  }

  /**
   * Simple test of leave community operation.
   */
  public void testLeaveCommunity() {
    // Set test community to initial state using special backdoor in
    //    CommunityManagerTestImpl
    Community comm = new CommunityImpl(COMMUNITY);
    comm.addEntity(new AgentImpl(AGENT));
    commMgr.addCommunity(comm);

    final Semaphore s = new Semaphore(0);
    CommunityResponseListener crl = new CommunityResponseListener() {
      public void getResponse(CommunityResponse resp) {
        commResp = resp;
        s.release();
      }
    };
    //test remove one agent entity
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
               !comm.hasEntity(AGENT));
  }

  /**
   * Simple test of create community operation.
   */
  public void testCreateCommunity() {
    BasicAttributes attrs = new BasicAttributes();
    attrs.put("Name", COMMUNITY);
    attrs.put("CommunityType", "Robustness");

    final Semaphore s = new Semaphore(0);
    CommunityResponseListener crl = new CommunityResponseListener() {
      public void getResponse(CommunityResponse resp) {
        commResp = resp;
        s.release();
      }
    };
    try {
      commSvc.createCommunity(COMMUNITY, attrs, crl);
      s.attempt(TIMEOUT);
    } catch (Exception ex) {
      ex.printStackTrace();
      fail();
    }
    Community community = (Community)commResp.getContent();
    assertTrue(commResp.getStatus() == CommunityResponse.SUCCESS &&
               community != null &&
               community.getName().equals(COMMUNITY) &&
               community.getAttributes().size() == 3);
  }

  /**
   * Simple test of get community operation.
   */
  public void testGetCommunity() {
    Community comm = new CommunityImpl(COMMUNITY);
    comm.addEntity(new AgentImpl(AGENT));
    commMgr.addCommunity(comm);

    final Semaphore s = new Semaphore(0);
    CommunityResponseListener crl = new CommunityResponseListener() {
      public void getResponse(CommunityResponse resp) {
        commResp = resp;
        s.release();
      }
    };
    Community community = null;
    try {
      community = commSvc.getCommunity(COMMUNITY, crl);
      if (community != null) {
        s.release();
      }
      s.attempt(TIMEOUT);
    } catch (Exception ex) {
      ex.printStackTrace();
      fail();
    }
    if (commResp != null) {
      community = (Community)commResp.getContent();
    }
    assertTrue(community != null &&
               community.getName().equals(COMMUNITY) &&
               community.getEntities().size() == 1); //the entity should be "Test_Agent".
  }

  /**
   * Simple test of modify entity attributes operation.
   */
  public void testModifyAttributes() {
    BasicAttributes attrs = new BasicAttributes();
    BasicAttribute attr = new BasicAttribute("EntityType", "Agent");
    attrs.put(attr);
    ModificationItem[] mods = new ModificationItem[1];

    CommunityImpl comm = new CommunityImpl(COMMUNITY);
    AgentImpl agent = new AgentImpl(AGENT);
    agent.setAttributes(attrs);
    comm.addEntity(agent);
    attrs.remove("EntityType");
    attrs.put(new BasicAttribute("EntityType", "Community"));
    CommunityImpl subcomm = new CommunityImpl(SUBCOMMUNITY);
    subcomm.setAttributes(attrs);
    comm.addEntity(subcomm);
    commMgr.addCommunity(comm);

    //test add attribute to an agent entity
    final Semaphore s1 = new Semaphore(0);
    mods[0] = new ModificationItem(DirContext.ADD_ATTRIBUTE,
                                   new BasicAttribute("Role", "Member"));
    try {
      commSvc.modifyAttributes(COMMUNITY, AGENT, mods, new CommunityResponseListener() {
        public void getResponse(CommunityResponse resp) {
          commResp = resp;
          s1.release();
        }
      });
      s1.attempt(TIMEOUT);
    } catch (Exception ex) {
      ex.printStackTrace();
      fail();
    }
    Community community = (Community)commResp.getContent();
    assertTrue(commResp.getStatus() == CommunityResponse.SUCCESS &&
               community != null &&
               community.getEntity(AGENT).getAttributes().size() == 2);

    //test modify attribute of an agent entity
    final Semaphore s2 = new Semaphore(0);
    mods[0] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
                                   new BasicAttribute("Role", "Manager"));
    try {
      commSvc.modifyAttributes(COMMUNITY, AGENT, mods, new CommunityResponseListener() {
        public void getResponse(CommunityResponse resp) {
          commResp = resp;
          s2.release();
        }
      });
      s2.attempt(TIMEOUT);
    } catch (Exception ex) {
      ex.printStackTrace();
      fail();
    }
    community = (Community)commResp.getContent();
    String value = "";
    try {
      value = (String)community.getEntity(AGENT).getAttributes().get("Role").
          get();
    } catch (NamingException e) {
      e.printStackTrace();
      fail();
    }
    assertTrue(commResp.getStatus() == CommunityResponse.SUCCESS &&
               community != null &&
               value.equals("Manager"));

    //test remove attribute from an agent entity
    final Semaphore s3 = new Semaphore(0);
    mods[0] = new ModificationItem(DirContext.REMOVE_ATTRIBUTE,
                                   new BasicAttribute("Role", "Manager"));
    try {
      commSvc.modifyAttributes(COMMUNITY, AGENT, mods, new CommunityResponseListener() {
        public void getResponse(CommunityResponse resp) {
          commResp = resp;
          s3.release();
        }
      });
      s3.attempt(TIMEOUT);
    } catch (Exception ex) {
      ex.printStackTrace();
      fail();
    }
    community = (Community)commResp.getContent();
    assertTrue(commResp.getStatus() == CommunityResponse.SUCCESS &&
               community != null &&
               community.getEntity(AGENT).getAttributes().size() == 1);

    //test add attribute to a community entity
    final Semaphore s4 = new Semaphore(0);
    mods[0] = new ModificationItem(DirContext.ADD_ATTRIBUTE,
                                   new BasicAttribute("Manager", "Agent1"));
    try {
      commSvc.modifyAttributes(COMMUNITY, SUBCOMMUNITY, mods, new CommunityResponseListener() {
        public void getResponse(CommunityResponse resp) {
          commResp = resp;
          s4.release();
        }
      });
      s4.attempt(TIMEOUT);
    } catch (Exception ex) {
      ex.printStackTrace();
      fail();
    }
    community = (Community)commResp.getContent();
    //System.out.println(community.toXml());
    assertTrue(commResp.getStatus() == CommunityResponse.SUCCESS &&
               community != null &&
               community.getEntity(SUBCOMMUNITY).getAttributes().size() == 2);

    //test modify attribute of a community entity
    final Semaphore s5 = new Semaphore(0);
    mods[0] = new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
                                   new BasicAttribute("Manager", "Agent2"));
    try {
      commSvc.modifyAttributes(COMMUNITY, SUBCOMMUNITY, mods, new CommunityResponseListener() {
        public void getResponse(CommunityResponse resp) {
          commResp = resp;
          s5.release();
        }
      });
      s5.attempt(TIMEOUT);
    } catch (Exception ex) {
      ex.printStackTrace();
      fail();
    }
    community = (Community)commResp.getContent();
    value = "";
    try {
      value = (String)community.getEntity(SUBCOMMUNITY).getAttributes().get("Manager").
          get();
    } catch (NamingException e) {
      e.printStackTrace();
      fail();
    }
    assertTrue(commResp.getStatus() == CommunityResponse.SUCCESS &&
               community != null &&
               value.equals("Agent2"));

    //test remove attribute from a community entity
    final Semaphore s6 = new Semaphore(0);
    mods[0] = new ModificationItem(DirContext.REMOVE_ATTRIBUTE,
                                   new BasicAttribute("Manager", "Role2"));
    try {
      commSvc.modifyAttributes(COMMUNITY, SUBCOMMUNITY, mods, new CommunityResponseListener() {
        public void getResponse(CommunityResponse resp) {
          commResp = resp;
          s6.release();
        }
      });
      s6.attempt(TIMEOUT);
    } catch (Exception ex) {
      ex.printStackTrace();
      fail();
    }
    community = (Community)commResp.getContent();
    assertTrue(commResp.getStatus() == CommunityResponse.SUCCESS &&
               community != null &&
               community.getEntity(SUBCOMMUNITY).getAttributes().size() == 1);

  }

  /**
   * Test of list parent communities operation.
   */
  public void testListParentCommunities() {
    CommunityImpl comm = new CommunityImpl(COMMUNITY);
    comm.addEntity(new AgentImpl(AGENT));
    commMgr.addCommunity(comm);

    //now agent Test_Agent only has one parent: Test_Community
    Collection parents = null;
    try {
      parents = commSvc.listParentCommunities(AGENT, (CommunityResponseListener)null);
    } catch (Exception ex) {
      ex.printStackTrace();
      fail();
    }
    assertTrue(parents.size() == 1 &&
               parents.contains(COMMUNITY));

    //add one more parent: MyTestCommunity, agent Test_agent should have two parents.
    final Semaphore s1 = new Semaphore(0);
    try{
      commSvc.joinCommunity("MyTestCommunity", AGENT, CommunityService.AGENT,
                            null, true, null, new CommunityResponseListener() {
      public void getResponse(CommunityResponse resp) {
        s1.release();
      }
    });
      s1.acquire();
    }catch(Exception ex) {
      ex.printStackTrace();
      fail();
    }
    try {
      parents = commSvc.listParentCommunities(AGENT, (CommunityResponseListener)null);
    } catch (Exception ex) {
      ex.printStackTrace();
      fail();
    }
    assertTrue(parents.size() == 2 &&
               parents.contains(COMMUNITY) &&
               parents.contains("MyTestCommunity"));

    //remove one parent.
    final Semaphore s2 = new Semaphore(0);
    try {
      commSvc.leaveCommunity("MyTestCommunity", AGENT,
                             new CommunityResponseListener() {
        public void getResponse(CommunityResponse resp) {
          s2.release();
        }
      });
      s2.acquire();
    }catch(Exception ex) {
      ex.printStackTrace();
      fail();
    }
    parents = commSvc.listParentCommunities(AGENT, (CommunityResponseListener)null);
    //System.out.println("parents=" + parents);
    assertTrue(parents.size() == 1 &&
               parents.contains(COMMUNITY));

  }

  public void testSearchCommunity() {
    CommunityImpl comm = new CommunityImpl(COMMUNITY);
    BasicAttributes attrs = new BasicAttributes();
    attrs.put("Role", "Member");
    attrs.put("TestAttribute", "test");
    comm.addEntity(new AgentImpl(AGENT, attrs));
    commMgr.addCommunity(comm);

    Collection results = null;
    try {
      results =
          commSvc.searchCommunity(COMMUNITY, "(Role=Member)", true, Community.ALL_ENTITIES, null);
    } catch(Exception ex) {
      ex.printStackTrace();
      fail();
    }
    assertTrue(results != null && results.size() == 1);
  }

  //test those deprecated methods
  public void testGetParentCommunities() {
    CommunityImpl comm = new CommunityImpl(COMMUNITY);
    comm.addEntity(new CommunityImpl(SUBCOMMUNITY));
    commMgr.addCommunity(comm);

    CommunityImpl subcomm = new CommunityImpl(SUBCOMMUNITY);
    subcomm.addEntity(new AgentImpl(AGENT));
    commMgr.addCommunity(subcomm);

    String[] parents = commSvc.getParentCommunities(false);
    assertTrue(parents.length == 1 && parents[0].equals(SUBCOMMUNITY));
    parents = commSvc.getParentCommunities(true);
    assertTrue(parents.length == 2);
  }

  public void testListParentCommunitiesI() {
    CommunityImpl comm = new CommunityImpl(COMMUNITY);
    comm.addEntity(new CommunityImpl(SUBCOMMUNITY));
    commMgr.addCommunity(comm);

    CommunityImpl subcomm = new CommunityImpl(SUBCOMMUNITY);
    subcomm.addEntity(new AgentImpl(AGENT));
    commMgr.addCommunity(subcomm);

    Collection parents = commSvc.listParentCommunities(AGENT);
    assertTrue(parents.size() == 1 && parents.contains(SUBCOMMUNITY));

    parents = commSvc.listParentCommunities(SUBCOMMUNITY);
    assertTrue(parents.size() == 1 && parents.contains(COMMUNITY));
  }

  public void testListParentCommunitiesII() {
    CommunityImpl comm = new CommunityImpl(COMMUNITY);
    BasicAttributes attrs = new BasicAttributes();
    attrs.put(new BasicAttribute("CommunityType", "Robustness"));
    comm.setAttributes(attrs);
    comm.addEntity(new AgentImpl(AGENT));
    commMgr.addCommunity(comm);

    Collection parents = commSvc.listParentCommunities(AGENT, "(CommunityType=Robustness)");
    assertTrue(parents.size() == 1 && parents.contains(COMMUNITY));
    parents = commSvc.listParentCommunities(AGENT, "(CommunityType=Security)");
    assertTrue(parents.size() == 0);
  }

  public void testListAllCommunities() {
    CommunityImpl comm = new CommunityImpl(COMMUNITY);
    comm.addEntity(new CommunityImpl(SUBCOMMUNITY));
    commMgr.addCommunity(comm);

    CommunityImpl subcomm = new CommunityImpl(SUBCOMMUNITY);
    subcomm.addEntity(new AgentImpl(AGENT));
    commMgr.addCommunity(subcomm);

    Collection comms = commSvc.listAllCommunities();
    //System.out.println("allCommunities=" + comms);
    assertTrue(comms.size() == 2 && comms.contains(COMMUNITY) && comms.contains(SUBCOMMUNITY));
  }

  public void testCloneCommunity() {
    BasicAttributes attrs = new BasicAttributes();
    attrs.put(new BasicAttribute("id", "val"));
    CommunityImpl comm = new CommunityImpl(COMMUNITY, attrs);
    comm.addEntity(new AgentImpl(AGENT));
    comm.addEntity(new CommunityImpl(SUBCOMMUNITY));

    Community clone = (Community)comm.clone();

    try {
      assertTrue(clone.getName().equals(COMMUNITY) &&
                 clone.getName() != comm.getName() &&
                 clone.getAttributes().get("id").contains("val") &&
                 clone.getAttributes() != comm.getAttributes() &&
                 clone.getAttributes().get("id") != comm.getAttributes().get("id") &&
                 clone.getAttributes().get("id").get() != comm.getAttributes().get("id").get() &&
                 clone.getEntities().size() == 2 &&
                 clone.getEntities() != comm.getEntities() &&
                 clone.getEntity(AGENT) != comm.getEntity(AGENT) &&
                 clone.getEntity(AGENT).equals(comm.getEntity(AGENT)) &&
                 clone.getEntity(SUBCOMMUNITY) != comm.getEntity(SUBCOMMUNITY) &&
                 clone.getEntity(SUBCOMMUNITY).equals(comm.getEntity(SUBCOMMUNITY))
                );
    } catch (Exception ex) {
      fail();
    }
  }

}
