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
package org.cougaar.community.test;

import java.util.*;

import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.blackboard.IncrementalSubscription;
import org.cougaar.planning.plugin.legacy.SimplePlugin;
import org.cougaar.core.service.LoggingService;

import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.component.ServiceRevokedListener;
import org.cougaar.core.component.ServiceRevokedEvent;

import org.cougaar.util.UnaryPredicate;

import org.cougaar.community.*;
import org.cougaar.community.util.*;
import org.cougaar.core.service.community.*;

import javax.naming.*;
import javax.naming.directory.*;


/**
 * Plugin for testing community operations.
 */
public class CommunityTestPlugin extends SimplePlugin {

  //private static Logger log;
  private LoggingService log;

  private MessageAddress myAgent = null;

  // Configurable test parameters
  private String testCommunityName = "TestCommunity";
  private String testAgentName = "TestAgent";
  private String testRole = "TestRole";

  /**
   * Overrides default values for configurable test parameters
   */
  public void setParameter(Object obj) {
    List args = (List)obj;
    switch (args.size()) {
      case 3:
        testRole = (String)args.get(2);
      case 2:
        testAgentName = (String)args.get(1);
      case 1:
        testCommunityName = (String)args.get(0);
    }
  }

  private int testNum = 0;

  /**
   * Subscribe to roster for community of interest.
   */
  protected void setupSubscriptions() {

    log =
      (LoggingService) getBindingSite().getServiceBroker().
        getService(this, LoggingService.class, null);

    myAgent = getMessageAddress();


    //////////////////////////////////////////////////////////////////
    //
    // Test direct interaction with CommunityService interface
    //
    //////////////////////////////////////////////////////////////////

    System.out.println("Testing CommunityService Interface");
    System.out.println("----------------------------------");
    CommunityService cs = getCommunityService();

    // Test: communityExists - No
    boolean result = cs.communityExists(testCommunityName);
    System.out.println("Method: communityExists (it doesn't)             " +
      (result ? "fail" : "pass"));

    // Test: getRoster - No community
    CommunityRoster roster = cs.getRoster(testCommunityName);
    result = (!roster.communityExists());
    System.out.println("Method: getRoster (community doesn't exist)      " +
      (result ? "pass" : "fail"));

    // Test: createCommunity
    Attributes attrs = new BasicAttributes();
    attrs.put(new BasicAttribute("Type", "Domain"));
    result = cs.createCommunity(testCommunityName, attrs);
    System.out.println("Method: createCommunity (valid name)             " +
      (result ? "pass" : "fail"));

    // Test: createCommunity - null name
    attrs = new BasicAttributes();
    attrs.put(new BasicAttribute("Type", "Domain"));
    result = cs.createCommunity(null, attrs);
    System.out.println("Method: createCommunity (null name)              " +
      (result ? "fail" : "pass"));

    // Test: getRoster - 1 member
    roster = cs.getRoster(testCommunityName);
    result = (roster.communityExists() &&
              roster.getMemberAgents().size() == 1);
    System.out.println("Method: getRoster (1 member)                     " +
      (result ? "pass" : "fail"));
    if (result == false) log.info(roster.toString() +
                                  " exists=" + roster.communityExists() +
                                  " numAgents=" + roster.getMemberAgents().size());

    // Test: communityExists - Yes
    result = cs.communityExists(testCommunityName);
    System.out.println("Method: communityExists (it exists)              " +
      (result ? "pass" : "fail"));

    // Test: listAllCommunities
    Collection communityList = cs.listAllCommunities();
    result = (communityList.size() == 1 && communityList.contains(testCommunityName));
    System.out.println("Method: listAllCommunities                       " +
      (result ? "pass" : "fail"));
    if (result == false) {
      if (communityList == null) {
        log.error("CommunityList is null");
      } else if (communityList.isEmpty()) {
        log.error("CommunityList is empty");
      } else {
        log.error("CommunityList has " + communityList.size() +
          " entries, expected 1");
        for (Iterator it = communityList.iterator(); it.hasNext();) {
          log.error("  " + (String)it.next());
        }
      }
    }

    // Test: setCommunityAttributes
    attrs = cs.getCommunityAttributes(testCommunityName);
    // Should already have 1 attribute defined (Domain)
    Attribute attr = new BasicAttribute("TestAttribute");
    attr.add("TestValue");
    attr.add("TestValue1");
    attrs.put(attr);
    result = cs.setCommunityAttributes(testCommunityName, attrs);
    System.out.println("Method: setCommunityAttributes                   " +
      (result ? "pass" : "fail"));


    // Test: getCommunityAttributes
    attrs = cs.getCommunityAttributes(testCommunityName);
    result = (attrs.size() == 2 &&
              (attrs.get("TestAttribute").contains("TestValue")) &&
              (attrs.get("TestAttribute").contains("TestValue1"))
             );
    System.out.println("Method: getCommunityAttributes                   " +
      (result ? "pass" : "fail"));
    if (result == false) {
      if (attrs == null) {
        log.error("Attributes is null");
      } else if (attrs.size() == 0) {
        log.error("Attributes are empty");
      } else {
        log.error("Community has " + attrs.size() +
          " attributes, expected 3");
        try {
          for (NamingEnumeration enum = attrs.getAll(); enum.hasMore();) {
            log.error("  " + (Attribute)enum.next());
          }
        } catch (NamingException ne) {}
      }
    }

    // Test: addToCommunity - add entity to a valid community
    attrs = new BasicAttributes();
    attrs.put("Name", testAgentName);
    attrs.put("Type", "Agent");
    attrs.put("Role", "Member");
    //log.info("Before=" + entityNames(cs.listEntities(testCommunityName)));
    result = cs.addToCommunity(testCommunityName,
      MessageAddress.getMessageAddress(testAgentName), testAgentName, attrs);
    //log.info("After=" + entityNames(cs.listEntities(testCommunityName)));
    System.out.println("Method: addToCommunity (to valid community)      " +
      (result ? "pass" : "fail"));

    // Test: removeFromCommunity - removes an entity from a valid community
    result = cs.removeFromCommunity(testCommunityName, testAgentName);
    System.out.println("Method: removeFromCommunity (valid community)    " +
      (result ? "pass" : "fail"));

    // Test: setEntityAttributes
    // First, add an entity to community
    attrs = new BasicAttributes();
    attrs.put("Name", testAgentName);
    attrs.put("Type", "Agent");
    attrs.put("Role", "Member");
    cs.addToCommunity(testCommunityName, MessageAddress.getMessageAddress(testAgentName),
      testAgentName, attrs);
    attrs = new BasicAttributes();
    attrs.put("TestAttribute", "TestValue");
    result = cs.setEntityAttributes(testCommunityName, testAgentName, attrs);
    // The 3 attributes created for addToCommunity should have been overwritten
    // by this single attribute
    System.out.println("Method: setEntityAttributes (valid entity)       " +
      (result ? "pass" : "fail"));

    // Test: getEntityAttributes
    attrs = cs.getEntityAttributes(testCommunityName, testAgentName);
    result = (attrs.size() == 1 &&
              (attrs.get("TestAttribute").contains("TestValue")));
    System.out.println("Method: getEntityAttributes (valid entity)       " +
      (result ? "pass" : "fail"));
    if (result == false) {
      if (attrs == null) {
        log.error("Attributes is null");
      } else if (attrs.size() == 0) {
        log.error("Atributes are empty");
      } else {
        log.error("Entity has " + attrs.size() +
          " attributes, expected 1");
        try {
          for (NamingEnumeration enum = attrs.getAll(); enum.hasMore();) {
            attr = (Attribute)enum.next();
            StringBuffer sb = new StringBuffer(attr.getID() + "=");
            for (NamingEnumeration enum1 = attr.getAll(); enum1.hasMore();) {
              sb.append((String)enum1.next());
              if (enum1.hasMore()) sb.append(",");
            }
            log.error("  " + sb.toString());

          }
        } catch (NamingException ne) {}
      }
    }

    // Test: modifyEntityAttributes
    attrs = new BasicAttributes();
    attrs.put("Name", testAgentName);
    attrs.put("Type", "Agent");
    attrs.put("Role", "Member");
    cs.addToCommunity(testCommunityName, MessageAddress.getMessageAddress(testAgentName),
      testAgentName, attrs);
    ModificationItem mods[] = new ModificationItem[1];
    mods[0] = new ModificationItem(DirContext.ADD_ATTRIBUTE,
              new BasicAttribute("TestAttribute", "TestValue"));
    result = cs.modifyEntityAttributes(testCommunityName, testAgentName, mods);
    System.out.println("Method: modifyEntityAttributes (valid entity)    " +
      (result ? "pass" : "fail"));

    // Test: getEntityAttributes
    attrs = cs.getEntityAttributes(testCommunityName, testAgentName);
    result = (attrs.size() == 4 &&
              (attrs.get("TestAttribute").contains("TestValue")));
    System.out.println("Method: getEntityAttributes (valid entity)       " +
      (result ? "pass" : "fail"));
    if (result == false) {
      if (attrs == null) {
        log.error("Attributes is null");
      } else if (attrs.size() == 0) {
        log.error("Atributes are empty");
      } else {
        log.error("Entity has " + attrs.size() +
          " attributes, expected 4");
        try {
          for (NamingEnumeration enum = attrs.getAll(); enum.hasMore();) {
            attr = (Attribute)enum.next();
            StringBuffer sb = new StringBuffer(attr.getID() + "=");
            for (NamingEnumeration enum1 = attr.getAll(); enum1.hasMore();) {
              sb.append((String)enum1.next());
              if (enum1.hasMore()) sb.append(",");
            }
            log.error("  " + sb.toString());

          }
        } catch (NamingException ne) {}
      }
    }

    // Test: search (for community)
    /*
    Collection communities = cs.search("(TestAttribute=TestValue)");
    result = (communities != null &&
              communities.size() == 1 &&
              communities.contains(testCommunityName));
    System.out.println("Method: search for community                     " +
      (result ? "pass" : "fail"));
    */

    // Test: search (for entity)
    Collection entities = cs.search(testCommunityName, "(TestAttribute=TestValue)", true);
    result = (entities != null &&
              entities.size() == 1 &&
              entities.contains(MessageAddress.getMessageAddress(testAgentName)));
    System.out.println("Method: search for entity                        " +
      (result ? "pass" : "fail"));

    // Test: addMember - to a valid community
    attrs = new BasicAttributes();
    attrs.put("Name", testAgentName);
    attrs.put("Type", "Agent");
    attrs.put("Role", "Member");
    result = cs.addToCommunity(testCommunityName, MessageAddress.getMessageAddress(testAgentName),
      testAgentName, attrs);
    System.out.println("Method: addToCommunity (to valid community)      " +
      (result ? "pass" : "fail"));

    // Test: addMember - add agent multiple times
    result = cs.addToCommunity(testCommunityName, MessageAddress.getMessageAddress(testAgentName),
      testAgentName, attrs);
    System.out.println("Method: addToCommunity (agent already added)     " +
      (result ? "pass" : "fail"));

    // Test: addMember - null community name
    result = cs.addToCommunity(null, MessageAddress.getMessageAddress(testAgentName),
      testAgentName, attrs);
    System.out.println("Method: addToCommunity (null community name)     " +
      (result ? "fail" : "pass"));

    // Test: addMember - null attributes
    result = cs.addToCommunity(testCommunityName, MessageAddress.getMessageAddress(testAgentName),
      testAgentName, null);
    System.out.println("Method: addToCommunity (null attributes)         " +
      (result ? "pass" : "fail"));
    cs.removeFromCommunity(testCommunityName, testAgentName);

    // Test: getRoster - 1 member
    attrs = new BasicAttributes();
    attrs.put("Name", testAgentName);
    attrs.put("Type", "Agent");
    attrs.put("Role", "Member");
    result = cs.addToCommunity(testCommunityName, MessageAddress.getMessageAddress(testAgentName),
      testAgentName, attrs);
    roster = cs.getRoster(testCommunityName);
    result = (roster.communityExists() &&
              roster.getMemberAgents().size() == 2 &&
              roster.getCommunityName().equals(testCommunityName) &&
              roster.getMemberAgents().contains(testAgentName));
    System.out.println("Method: getRoster (community has 2 members)      " +
      (result ? "pass" : "fail"));

    // Test: listParentCommunities
    communityList = cs.listParentCommunities(testAgentName);
    result = (communityList.size() == 1 && communityList.contains(testCommunityName));
    System.out.println("Method: listParentCommunities (valid agent)      " +
      (result ? "pass" : "fail"));
    if (result == false) {
      for (Iterator it = communityList.iterator(); it.hasNext();) {
        log.error((String)it.next());
      }
    }

    // Test: listParentCommunities - bogus agent
    communityList = cs.listParentCommunities("BogusAgent");
    result = (communityList.size() == 0);
    System.out.println("Method: listParentCommunities (invalid agent)    " +
      (result ? "pass" : "fail"));

    /*
    // Test: addListener
    result = cs.addListener(myAgent, testCommunityName);
    System.out.println("Method: addListener                              " +
      (result ? "pass" : "fail"));

    // Test: getListeners
    Collection listeners = cs.getListeners(testCommunityName);
    result = (listeners.size() == 1 && listeners.contains(myAgent));
    System.out.println("Method: getListeners                             " +
      (result ? "pass" : "fail"));

    // Test: removeListener
    result = cs.removeListener(myAgent, testCommunityName);
    System.out.println("Method: removeListener                           " +
      (result ? "pass" : "fail"));
    */

    // Test: removeMember - member exists
    result = cs.removeFromCommunity(testCommunityName, testAgentName);
    System.out.println("Method: removeFromCommunity (entity exists)      " +
      (result ? "pass" : "fail"));

    // Test: removeMember - member exists
    result = cs.removeFromCommunity(testCommunityName, testAgentName);
    System.out.println("Method: removeFromCommunity(entity doesn't exist)" +
      (result ? "pass" : "fail"));

    // Test: getCommunityRoles
    attrs = new BasicAttributes();
    attrs.put("Name", testAgentName);
    attrs.put("Type", "Agent");
    attrs.put("Role", "Member");
    attrs.put("ExternalRole", "TestExternalRole");
    MessageAddress testAgentCid = MessageAddress.getMessageAddress(testAgentName);
    cs.addToCommunity(testCommunityName, testAgentCid,
      testAgentName, attrs);
    mods[0] = new ModificationItem(DirContext.ADD_ATTRIBUTE,
              new BasicAttribute("Role", "Member"));
    cs.modifyCommunityAttributes(testCommunityName, mods);
    Collection roles = cs.getCommunityRoles(testCommunityName);
    result = (roles != null &&
              roles.size() == 1 &&
              roles.contains("Member"));
    System.out.println("Method: getCommunityRoles                        " +
      (result ? "pass" : "fail"));

    // Test: getEntityRoles
    roles = cs.getEntityRoles(testCommunityName, testAgentName);
    result = (roles != null &&
              roles.size() == 1 &&
              roles.contains("Member"));
    if (result == false) {
      System.out.println("Roles:");
      for (Iterator it = roles.iterator(); it.hasNext();) {
        System.out.println("Role=" + (String)it.next());
      }
    }
    System.out.println("Method: getEntityRoles                           " +
      (result ? "pass" : "fail"));

    // Test: searchByRole
    entities = cs.searchByRole(testCommunityName, "Member");
    result = (entities != null &&
              entities.size() == 1 &&
              entities.contains(testAgentCid));
    System.out.println("Method: searchByRole                             " +
      (result ? "pass" : "fail"));

    // Test: addRole
    result = cs.addRole(testCommunityName, testAgentName, "NewRole");
    if (result == true) {
      roles = cs.getEntityRoles(testCommunityName, testAgentName);
      result = (roles != null &&
                roles.size() == 2 &&
                roles.contains("NewRole"));
    }
    if (result == false) {
      System.out.println("Roles after add");
      for (Iterator it = roles.iterator(); it.hasNext();) {
        System.out.println("Role=" + (String)it.next());
      }
    }
    System.out.println("Method: addRole                                  " +
      (result ? "pass" : "fail"));

    // Test: removeRole
    result = cs.removeRole(testCommunityName, testAgentName, "NewRole");
    if (result == true) {
      roles = cs.getEntityRoles(testCommunityName, testAgentName);
      result = (roles != null &&
                roles.size() == 1 &&
                !roles.contains("NewRole"));
    }
    System.out.println("Method: removeRole                               " +
      (result ? "pass" : "fail"));


    // Test: multi-valued attribute search
    cs.removeFromCommunity(testCommunityName, testAgentName);
    attrs = new BasicAttributes();
    attrs.put("Name", "Agent1");
    attrs.put("Type", "Agent");
    Attribute roleAttr = new BasicAttribute("Role");
    roleAttr.add("Member");
    roleAttr.add("TransportProvider");
    roleAttr.add("AmmoSupplyProvider");
    attrs.put(roleAttr);
    MessageAddress agent1Cid = MessageAddress.getMessageAddress("Agent1");
    cs.addToCommunity(testCommunityName, agent1Cid, "Agent1", attrs);
    attrs = new BasicAttributes();
    attrs.put("Name", "Agent2");
    attrs.put("Type", "Agent");
    roleAttr = new BasicAttribute("Role");
    roleAttr.add("Member");
    roleAttr.add("AmmoSupplyProvider");
    attrs.put(roleAttr);
    MessageAddress agent2Cid = MessageAddress.getMessageAddress("Agent2");
    cs.addToCommunity(testCommunityName, agent2Cid, "Agent2", attrs);
    Collection roles1 = cs.getEntityRoles(testCommunityName, "Agent1");
    Collection roles2 = cs.getEntityRoles(testCommunityName, "Agent2");
    result = ((roles1 != null &&
              roles1.size() == 3 &&
              roles1.contains("Member") &&
              roles1.contains("TransportProvider") &&
              roles1.contains("AmmoSupplyProvider")) &&
             (roles2 != null &&
              roles2.size() == 2 &&
              roles2.contains("Member") &&
              roles2.contains("AmmoSupplyProvider")));
    if (result == true) {
      Collection entities1 = cs.search(testCommunityName, "(Role=Member)", true);
      Collection entities2 = cs.search(testCommunityName, "(Role=TransportProvider)", true);
      Collection entities3 = cs.search(testCommunityName, "(Role=AmmoSupplyProvider)", true);
      result = ((entities1 != null &&
                entities1.size() == 2 &&
                entities1.contains(agent1Cid) &&
                entities1.contains(agent2Cid)) &&
                (entities2 != null &&
                entities2.size() == 1 &&
                entities2.contains(agent1Cid)) &&
                (entities3 != null &&
                entities3.size() == 2 &&
                entities3.contains(agent1Cid) &&
                entities3.contains(agent2Cid)));
    }
    System.out.println("Method: search (multi-valued attribute)          " +
      (result ? "pass" : "fail"));
    cs.removeFromCommunity(testCommunityName, "Agent1");
    cs.removeFromCommunity(testCommunityName, "Agent2");

    System.exit(0);

    //////////////////////////////////////////////////////////////////
    //
    // Test Community Request/Response protocol using CommunityPlugin
    //
    //////////////////////////////////////////////////////////////////

    // Subscribe to CommunityRequests to get roster updates
    /*
    requests = (IncrementalSubscription)getBlackboardService()
  .subscribe(communityRequestPredicate);

    attrs.put("Name", testAgentName);
    attrs.put("Type", "Agent");
    roleAttr = new BasicAttribute("Role");
    roleAttr.add("Member");
    roleAttr.add("TestRole");
    cs.addToCommunity(testCommunityName, MessageAddress.getMessageAddress(testAgentName),
      testAgentName, attrs);

    System.out.println();
    System.out.println("Testing Community Blackboard Publish/Subscribe Interface");
    System.out.println("--------------------------------------------------------");
    testNum = 1;
    CommunityRequest cr = new CommunityRequestImpl();
    cr.setVerb("GET_ROSTER");
    cr.setTargetCommunityName(testCommunityName);
    getBlackboardService().publishAdd(cr);
    */
  }

  /**
  * Print community roster when changes are received.
  */
  protected void execute () {

    /*
     boolean doNextTest = false;

    // Evaluate test results
    Enumeration crs = requests.getChangedList();
    while (crs.hasMoreElements()) {
      boolean result = false;
      CommunityRequest cr = (CommunityRequest)crs.nextElement();
      if (testNum == 1) {
        if (cr.getVerb() != null &&
            cr.getVerb().equals("GET_ROSTER") &&
            cr.getTargetCommunityName().equals(testCommunityName)) {
          CommunityResponse resp = cr.getCommunityResponse();
          if (resp.getStatus() == CommunityResponse.SUCCESS) {
            //CommunityRoster roster = (CommunityRoster)resp.getContent();
            // FIXME: convert rest.getCommunity() to CommunityRoster
            CommunityRoster roster = null;
            result = (roster.getMembers().size() == 1);
          }
        }
        System.out.println("Verb: GET_ROSTER               " + (result ? "pass" : "fail"));
        ++testNum; doNextTest = true;
      } else if (testNum == 2) {
        if (cr.getVerb() != null &&
            cr.getVerb().equals("LIST_PARENT_COMMUNITIES")) {
          CommunityResponse resp = cr.getCommunityResponse();
          if (resp.getStatus() == CommunityResponse.SUCCESS) {
            Collection myCommunities = (Collection)resp.getContent();
            result = (myCommunities.size()== 1);
          }
        }
        System.out.println("Verb: LIST_PARENT_COMMUNITIES  " + (result ? "pass" : "fail"));
        ++testNum; doNextTest = true;
      } else if (testNum == 3) {
        if (cr.getVerb() != null &&
            cr.getVerb().equals("FIND_AGENTS_WITH_ROLE")) {
          CommunityResponse resp = cr.getCommunityResponse();
          if (resp.getStatus() == CommunityResponse.SUCCESS) {
            Collection agentsWithRole = (Collection)resp.getContent();
            result = (agentsWithRole != null && agentsWithRole.size()== 1);
          }
        }
        System.out.println("Verb: FIND_AGENTS_WITH_ROLE    " + (result ? "pass" : "fail"));
        ++testNum; doNextTest = true;
      } else if (testNum == 4) {
        if (cr.getVerb() != null &&
            cr.getVerb().equals("GET_ROLES")) {
          CommunityResponse resp = cr.getCommunityResponse();
          if (resp.getStatus() == CommunityResponse.SUCCESS) {
            Collection roles = (Collection)resp.getContent();
            result = (roles != null && roles.size()== 2);
          }
        }
        System.out.println("Verb: GET_ROLES                " + (result ? "pass" : "fail"));
        ++testNum; doNextTest = true;
      } else if (testNum == 5) {
        Collection communityNames = null;
        if (cr.getVerb() != null &&
            cr.getVerb().equals("LIST_ALL_COMMUNITIES")) {
          CommunityResponse resp = cr.getCommunityResponse();
          if (resp.getStatus() == CommunityResponse.SUCCESS) {
            communityNames = (Collection)resp.getContent();
            result = (communityNames != null && communityNames.size()== 1);
          }
        }
        System.out.println("Verb: LIST_ALL_COMMUNITIES     " + (result ? "pass" : "fail"));
        if (result == false) {
          if (communityNames == null) {
            log.error("CommunityList is null");
          } else if (communityNames.isEmpty()) {
            log.error("CommunityList is empty");
          } else {
            log.error("CommunityList has " + communityNames.size() +
              " entries, expected 1");
            for (Iterator it = communityNames.iterator(); it.hasNext();) {
              log.error("  " + (String)it.next());
            }
          }
        }
        ++testNum; doNextTest = true;
      }
    }

    // Start next test
    if (doNextTest) {
      // Start new test
      if (testNum == 2) {
        CommunityRequest cr = new CommunityRequestImpl();
        cr.setVerb("LIST_PARENT_COMMUNITIES");
        cr.setAgentName(testAgentName);
        getBlackboardService().publishAdd(cr);
      } else if (testNum == 3) {
        CommunityRequest cr = new CommunityRequestImpl();
        cr.setVerb("FIND_AGENTS_WITH_ROLE");
        cr.setTargetCommunityName(testCommunityName);
        cr.setRole("Member");
        getBlackboardService().publishAdd(cr);
      } else if (testNum == 4) {
        CommunityRequest cr = new CommunityRequestImpl();
        cr.setVerb("GET_ROLES");
        cr.setTargetCommunityName(testCommunityName);
        cr.setAgentName(testAgentName);
        getBlackboardService().publishAdd(cr);
      } else if (testNum == 5) {
        CommunityRequest cr = new CommunityRequestImpl();
        cr.setVerb("LIST_ALL_COMMUNITIES");
        getBlackboardService().publishAdd(cr);
      } else {
        System.exit(0);
      }
    }
    */

  }

  /**
   * Gets reference to CommunityService.
   */
  private CommunityService getCommunityService() {
    int counter = 0;
    CommunityService cs = null;
    ServiceBroker sb = getBindingSite().getServiceBroker();
    while (!sb.hasService(CommunityService.class)) {
      // Print a message after waiting for 30 seconds
      if (++counter == 60) log.info("Waiting for CommunityService ... ");
      try { Thread.sleep(500); } catch (Exception ex) {}
    }
    return (CommunityService)sb.getService(this, CommunityService.class,
      new ServiceRevokedListener() {
        public void serviceRevoked(ServiceRevokedEvent re) {}
    });
  }

  /**
   * Gets reference to LoggingService.
   */
  private LoggingService getLoggingService() {
    ServiceBroker sb = getBindingSite().getServiceBroker();
    return (LoggingService)sb.getService(this, LoggingService.class,
      new ServiceRevokedListener() {
        public void serviceRevoked(ServiceRevokedEvent re) {}
    });
  }

  private String entityNames(Collection entities) {
    StringBuffer sb = new StringBuffer("[");
    for (Iterator it = entities.iterator(); it.hasNext();) {
      Entity entity = (Entity)it.next();
      sb.append(entity.getName());
      if (it.hasNext()) sb.append(",");
    }
    sb.append("]");
    return sb.toString();
  }

  /**
   * Prints a list of the current services.
   */
  private void listServices() {
    System.out.println("Current Services:");
    for (Iterator it = getBindingSite().getServiceBroker().getCurrentServiceClasses(); it.hasNext();) {
      System.out.println("  " + ((Class)it.next()).getName());
    }
  }


}
