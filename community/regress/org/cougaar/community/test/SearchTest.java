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
import javax.naming.directory.BasicAttribute;
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
 * Test community search operations.
 *
 */
public class SearchTest extends TestCase {

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

  public SearchTest(String name) {
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
    return new TestSuite(SearchTest.class);
    /* Use following to run specific tests only
    TestSuite suite= new TestSuite();
    suite.addTest(new SearchTests("testAddEntity"));
    return suite;
    */
  }

  /**
   * Basic search.
   */
  public void testBasicSearch() {
    // Setup test state
    Community comm = new CommunityImpl(COMMUNITY);
    comm.addEntity(new AgentImpl("Agent1", new BasicAttributes("Attr1", "Val1")));
    comm.addEntity(new AgentImpl("Agent2", new BasicAttributes("Attr2", "Val2")));
    comm.addEntity(new AgentImpl("Agent3", new BasicAttributes("Attr3", "Val3")));
    commMgr.addCommunity(comm);

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
               searchResults.size() == 1 &&
               resultsContains(searchResults, "Agent2"));
  }

  /**
   * Compound search.
   */
  public void testCompoundSearch() {
    // Setup test state
    Community comm = new CommunityImpl(COMMUNITY);
    comm.addEntity(new AgentImpl("Agent1", new BasicAttributes("Attr1", "Val1")));
    comm.addEntity(new AgentImpl("Agent2", new BasicAttributes("Attr2", "Val2")));

    Attributes agent3Attrs = new BasicAttributes();
    agent3Attrs.put("Attr3","Val3");
    agent3Attrs.put("Attr4","Val4");
    comm.addEntity(new AgentImpl("Agent3", agent3Attrs));
    Attributes agent4Attrs = new BasicAttributes();
    agent4Attrs.put("Attr3","Val3");
    agent4Attrs.put("Attr4", "Val4");
    agent4Attrs.put("Attr5","Val5");
    comm.addEntity(new AgentImpl("Agent4", agent4Attrs));
    commMgr.addCommunity(comm);

    final Semaphore s = new Semaphore(0);
    //search for entities have both attributes: Attr3=Val3 and Attr4=Val4, get two results
    try {
      searchResults =
          commSvc.searchCommunity(COMMUNITY, "(&(Attr3=Val3)(Attr4=Val4))",
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
               searchResults.size() == 2 &&
               resultsContains(searchResults, "Agent3") &&
               resultsContains(searchResults, "Agent4"));

    //search for entities have both attributes: Attr3=Val3 and Attr5=Val5, get one result.
    try {
      searchResults =
          commSvc.searchCommunity(COMMUNITY, "(&(Attr3=Val3)(Attr5=Val5))",
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
               searchResults.size() == 1 &&
               resultsContains(searchResults, "Agent4"));

  }

  /**
   * Alternative search
   */
  public void testAlternativeSearch() {
    // Setup test state
    Community comm = new CommunityImpl(COMMUNITY);
    comm.addEntity(new AgentImpl("Agent1", new BasicAttributes("Attr1", "Val1")));
    comm.addEntity(new AgentImpl("Agent2", new BasicAttributes("Attr2", "Val2")));

    Attributes agent3Attrs = new BasicAttributes();
    agent3Attrs.put("Attr3","Val3");
    agent3Attrs.put("Attr4","Val4");
    agent3Attrs.put("Attr5", "Val5");
    comm.addEntity(new AgentImpl("Agent3", agent3Attrs));
    Attributes agent4Attrs = new BasicAttributes();
    agent4Attrs.put("Attr5","Val5");
    agent4Attrs.put("Attr6","Val6");
    comm.addEntity(new AgentImpl("Agent4", agent4Attrs));
    commMgr.addCommunity(comm);

    final Semaphore s = new Semaphore(0);
    //search result only get one entity
    try {
      searchResults =
          commSvc.searchCommunity(COMMUNITY, "(|(Attr3=Val3)(Attr4=Val4))",
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
               searchResults.size() == 1 &&
               resultsContains(searchResults, "Agent3"));

    //search result contains two entities
    try {
      searchResults =
          commSvc.searchCommunity(COMMUNITY, "(|(Attr3=Val3)(Attr5=Val5))",
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
               searchResults.size() == 2 &&
               resultsContains(searchResults, "Agent3") &&
               resultsContains(searchResults, "Agent4"));
  }

  /**
   * Negative search
   */
  public void testNegationSearch() {
    // Setup test state
    Community comm = new CommunityImpl(COMMUNITY);
    comm.addEntity(new AgentImpl("Agent1", new BasicAttributes("Attr1", "Val1")));
    comm.addEntity(new AgentImpl("Agent2", new BasicAttributes("Attr2", "Val2")));

    Attributes agent3Attrs = new BasicAttributes();
    agent3Attrs.put("Attr3","Val3");
    agent3Attrs.put("Attr4","Val4");
    comm.addEntity(new AgentImpl("Agent3", agent3Attrs));
    commMgr.addCommunity(comm);

    final Semaphore s = new Semaphore(0);
    try {
      searchResults =
          commSvc.searchCommunity(COMMUNITY, "(!(Attr3=Val3))",
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
               searchResults.size() == 2 &&
               resultsContains(searchResults, "Agent1") &&
               resultsContains(searchResults, "Agent2"));

    try {
      searchResults =
          commSvc.searchCommunity(COMMUNITY, "(!(Attr3=Val4))",
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
               searchResults.size() == 3 &&
               resultsContains(searchResults, "Agent1") &&
               resultsContains(searchResults, "Agent2") &&
               resultsContains(searchResults, "Agent3"));

  }

  /**
   * Wildcard search
   */
  public void testWildcardSearch() {
    // Setup test state
    Community comm = new CommunityImpl(COMMUNITY);
    comm.addEntity(new AgentImpl("Agent1", new BasicAttributes("Attr1", "Val1")));
    comm.addEntity(new AgentImpl("Agent2", new BasicAttributes("Attr2", "Val2")));

    Attributes agent3Attrs = new BasicAttributes();
    agent3Attrs.put("Attr3","Val3");
    agent3Attrs.put("Attr4","Val4");
    comm.addEntity(new AgentImpl("Agent3", agent3Attrs));
    comm.addEntity(new AgentImpl("Agent4", new BasicAttributes("Attr3", "Val4")));
    commMgr.addCommunity(comm);

    final Semaphore s = new Semaphore(0);
    //serch for all entities who have the attribute Attr3.
    try {
      searchResults =
          commSvc.searchCommunity(COMMUNITY, "(Attr3=*)",
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
               searchResults.size() == 2 &&
               resultsContains(searchResults, "Agent3") &&
               resultsContains(searchResults, "Agent4"));

    //search for all entities who have attribute Attr3 and the value contains a 3.
    try {
      searchResults =
          commSvc.searchCommunity(COMMUNITY, "(Attr3=*3)",
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
               searchResults.size() == 1 &&
               resultsContains(searchResults, "Agent3"));

  }

  /**
   * Multi-valued attribute search.
   */
  public void testMultiValuedAttributeSearch() {
    // Setup test state
    Community comm = new CommunityImpl(COMMUNITY);
    comm.addEntity(new AgentImpl("Agent1", new BasicAttributes("Attr1", "Val1")));
    comm.addEntity(new AgentImpl("Agent2", new BasicAttributes("Attr2", "Val2")));

    Attribute attr = new BasicAttribute("Attr3");
    attr.add("Val3");
    attr.add("Val4");
    Attributes attrs = new BasicAttributes();
    attrs.put(attr);
    comm.addEntity(new AgentImpl("Agent3", attrs));
    commMgr.addCommunity(comm);

    final Semaphore s = new Semaphore(0);
    try {
      searchResults =
          commSvc.searchCommunity(COMMUNITY, "(Attr3=Val4)",
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
               searchResults.size() == 1 &&
               resultsContains(searchResults, "Agent3"));
  }

  public void testCombinationSearch() {
    // Setup test state
    Community comm = new CommunityImpl(COMMUNITY);
    comm.addEntity(new AgentImpl("Agent1", new BasicAttributes("Attr1", "Val1")));
    comm.addEntity(new AgentImpl("Agent2", new BasicAttributes("Attr2", "Val2")));

    Attributes agent3Attrs = new BasicAttributes();
    agent3Attrs.put("Attr3","Val3");
    agent3Attrs.put("Attr4","Val4");
    comm.addEntity(new AgentImpl("Agent3", agent3Attrs));
    Attribute attr = new BasicAttribute("Attr3");
    attr.add("Val3");
    attr.add("Val4");
    Attributes agent4Attrs = new BasicAttributes();
    agent4Attrs.put(attr);
    comm.addEntity(new AgentImpl("Agent4", agent4Attrs));
    commMgr.addCommunity(comm);

    final Semaphore s = new Semaphore(0);
    try {
      searchResults =
          commSvc.searchCommunity(COMMUNITY, "(|(Attr4=*)(&(Attr1=Val1)(!(Attr3=Val3))))",
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
               searchResults.size() == 2 &&
               resultsContains(searchResults, "Agent1") &&
               resultsContains(searchResults, "Agent3"));

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
