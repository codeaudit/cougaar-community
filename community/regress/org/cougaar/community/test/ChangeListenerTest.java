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

import junit.framework.*;

import javax.naming.directory.BasicAttribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.ModificationItem;

import org.cougaar.core.service.community.CommunityService;
import org.cougaar.core.service.community.Community;
import org.cougaar.core.service.community.Agent;
import org.cougaar.core.service.community.CommunityChangeListener;
import org.cougaar.core.service.community.CommunityChangeEvent;
import org.cougaar.core.service.community.CommunityResponse;
import org.cougaar.core.service.community.CommunityResponseListener;

import org.cougaar.community.CommunityImpl;
import org.cougaar.community.AgentImpl;
import org.cougaar.community.CommunityUtils;
import org.cougaar.community.util.Semaphore;

import org.apache.log4j.PropertyConfigurator;

/**
 * Test Change notification operations.
 *
 */
public class ChangeListenerTest extends TestCase {

  protected static final String AGENT = "Test_Agent";
  protected static final String COMMUNITY = "Test_Community";
  protected CommunityService commSvc;
  protected CommunityManagerTestImpl commMgr;
  protected CommunityResponse commResp; // Callback response
  protected CommunityChangeEvent commChangeEvent;

  protected String loggingProps[][] = {
      {"log4j.category.org.cougaar.community","INFO"},
      //{"log4j.category.org.cougaar.community.CommunityCache","DEBUG"},
      {"log4j.category.org.cougaar.community.test","INFO"}
  };

  public ChangeListenerTest(String name) {
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
    ((CommunityServiceTestImpl)commSvc).getCache().clear();
  }

  public static Test suite() {
    return new TestSuite(ChangeListenerTest.class);
    //Use following to run specific tests only
    //TestSuite suite= new TestSuite();
    //suite.addTest(new ChangeListenerTest("testAddCommunity"));
    //return suite;
  }

  /**
   * Test ADD_COMMUNITY change event.
   */
  public void testAddCommunity() {
    final Semaphore s = new Semaphore(-1);
    commSvc.addListener(new CommunityChangeListener() {
      public String getCommunityName() { return COMMUNITY; }
      public void communityChanged(CommunityChangeEvent cce) {
        //System.out.println(cce);
        if (cce.getType() == cce.ADD_COMMUNITY &&
            cce.getCommunityName().equals(COMMUNITY)) {
          commChangeEvent = cce;
          commSvc.removeListener(this);
          s.release();
        }
      }
    });
    CommunityResponseListener crl = new CommunityResponseListener() {
      public void getResponse(CommunityResponse resp) {
        commResp = resp;
        s.release();
      }
    };
    try {
      commSvc.joinCommunity(COMMUNITY, AGENT, commSvc.AGENT, null, true, null, crl);
      s.attempt(5000);
    } catch (Exception ex) {
      ex.printStackTrace();
      fail();
    }
    Community communityFromResponse = (Community)commResp.getContent();
    //System.out.println(communityFromResponse.toXml());
    Community communityFromEvent = commChangeEvent.getCommunity();
    assertTrue(commResp.getStatus() == CommunityResponse.SUCCESS &&
               communityFromResponse != null &&
               communityFromEvent != null &&
               commChangeEvent.getType() == commChangeEvent.ADD_COMMUNITY);
  }

  /**
   * Test ADD_ENTITY change event.
   */
  public void testAddEntity() {
    final Semaphore s = new Semaphore(-1);
    commSvc.addListener(new CommunityChangeListener() {
      public String getCommunityName() { return COMMUNITY; }
      public void communityChanged(CommunityChangeEvent cce) {
        if (cce.getType() == cce.ADD_ENTITY &&
            cce.getCommunityName().equals(COMMUNITY) &&
            cce.getWhatChanged().equals(AGENT)) {
          commChangeEvent = cce;
          commSvc.removeListener(this);
          s.release();
        }
      }
    });
    CommunityResponseListener crl = new CommunityResponseListener() {
      public void getResponse(CommunityResponse resp) {
        commResp = resp;
        s.release();
      }
    };
    try {
      commSvc.joinCommunity(COMMUNITY, AGENT, commSvc.AGENT, null, true, null, crl);
      s.attempt(5000);
    } catch (Exception ex) {
      ex.printStackTrace();
      fail();
    }
    Community communityFromResponse = (Community)commResp.getContent();
    Community communityFromEvent = commChangeEvent.getCommunity();
    assertTrue(commResp.getStatus() == CommunityResponse.SUCCESS &&
               communityFromResponse != null &&
               communityFromEvent != null &&
               commChangeEvent.getType() == commChangeEvent.ADD_ENTITY &&
               AGENT.equals(commChangeEvent.getWhatChanged()));
  }

  /**
   * Test REMOVE_ENTITY change event.
   */
  public void testRemoveEntity() {
    Community comm = new CommunityImpl(COMMUNITY);
    comm.addEntity(new AgentImpl(AGENT));
    commMgr.addCommunity(comm);

    final Semaphore s = new Semaphore(-1);
    commSvc.addListener(new CommunityChangeListener() {
      public String getCommunityName() { return COMMUNITY; }
      public void communityChanged(CommunityChangeEvent cce) {
        if (cce.getType() == cce.REMOVE_ENTITY &&
            cce.getCommunityName().equals(COMMUNITY)) {
          commChangeEvent = cce;
          commSvc.removeListener(this);
          s.release();
        }
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
      s.attempt(5000);
    } catch (Exception ex) {
      ex.printStackTrace();
      fail();
    }
    Community communityFromResponse = (Community)commResp.getContent();
    Community communityFromEvent = commChangeEvent.getCommunity();
    assertTrue(commResp.getStatus() == CommunityResponse.SUCCESS &&
               communityFromResponse != null &&
               communityFromEvent != null &&
               commChangeEvent.getType() == commChangeEvent.REMOVE_ENTITY &&
               AGENT.equals(commChangeEvent.getWhatChanged()));
  }

  /**
   * Test ENTITY_ATTRIBUTES_CHANGED change event.
   */
  public void testEntityAttributesChanged() {
    Community comm = new CommunityImpl(COMMUNITY);
    Attributes entityAttrs = new BasicAttributes();
    entityAttrs.put(new BasicAttribute("TestAttr1", "Val1"));
    Agent agent = new AgentImpl(AGENT, entityAttrs);
    comm.addEntity(agent);
    commMgr.addCommunity(comm);

    Attributes changes = new BasicAttributes();
    changes.put(new BasicAttribute("TestAttr1", "Val1"));
    changes.put(new BasicAttribute("TestAttr2", "Val2"));

    ModificationItem[] attrMods =
        CommunityUtils.getAttributeModificationItems(entityAttrs, changes);

    final Semaphore s = new Semaphore(-1);
    commSvc.addListener(new CommunityChangeListener() {
      public String getCommunityName() { return COMMUNITY; }
      public void communityChanged(CommunityChangeEvent cce) {
        if (cce.getType() == cce.ENTITY_ATTRIBUTES_CHANGED &&
            cce.getCommunityName().equals(COMMUNITY)) {
          commChangeEvent = cce;
          commSvc.removeListener(this);
          s.release();
        }
      }
    });
    CommunityResponseListener crl = new CommunityResponseListener() {
      public void getResponse(CommunityResponse resp) {
        commResp = resp;
        s.release();
      }
    };
    try {
      commSvc.modifyAttributes(COMMUNITY, AGENT, attrMods, crl);
      s.attempt(5000);
    } catch (Exception ex) {
      ex.printStackTrace();
      fail();
    }
    Community communityFromResponse = (Community)commResp.getContent();
    Community communityFromEvent = commChangeEvent.getCommunity();

    assertTrue(commResp.getStatus() == CommunityResponse.SUCCESS &&
               communityFromResponse != null &&
               communityFromEvent != null &&
               commChangeEvent.getType() == commChangeEvent.ENTITY_ATTRIBUTES_CHANGED &&
               AGENT.equals(commChangeEvent.getWhatChanged()));
 }

  /**
   * Test COMMUNITY_ATTRIBUTES_CHANGED change event.
   */
  public void testCommunityAttributesChanged() {
    Attributes commAttrs = new BasicAttributes();

    commAttrs.put(new BasicAttribute("TestAttr1", "Val1"));
    Community comm = new CommunityImpl(COMMUNITY, commAttrs);

    comm.addEntity(new AgentImpl(AGENT));
    commMgr.addCommunity(comm);

    Attributes changes = new BasicAttributes();
    changes.put(new BasicAttribute("TestAttr1", "Val1"));
    changes.put(new BasicAttribute("TestAttr2", "Val2"));

    ModificationItem[] attrMods =
        CommunityUtils.getAttributeModificationItems(commAttrs, changes);

    final Semaphore s = new Semaphore(-1);
    commSvc.addListener(new CommunityChangeListener() {
      public String getCommunityName() { return COMMUNITY; }
      public void communityChanged(CommunityChangeEvent cce) {
        if (cce.getType() == cce.COMMUNITY_ATTRIBUTES_CHANGED &&
            cce.getCommunityName().equals(COMMUNITY)) {
          commChangeEvent = cce;
          commSvc.removeListener(this);
          s.release();
        }
      }
    });
    CommunityResponseListener crl = new CommunityResponseListener() {
      public void getResponse(CommunityResponse resp) {
        commResp = resp;
        s.release();
      }
    };
    try {
      commSvc.modifyAttributes(COMMUNITY, null, attrMods, crl);
      s.attempt(5000);
    } catch (Exception ex) {
      ex.printStackTrace();
      fail();
    }
    Community communityFromResponse = (Community)commResp.getContent();
    Community communityFromEvent = commChangeEvent.getCommunity();

    assertTrue(commResp.getStatus() == CommunityResponse.SUCCESS &&
               communityFromResponse != null &&
               communityFromEvent != null &&
               commChangeEvent.getType() == commChangeEvent.COMMUNITY_ATTRIBUTES_CHANGED &&
               COMMUNITY.equals(commChangeEvent.getWhatChanged()));
  }


}
