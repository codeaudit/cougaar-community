/*
 * <copyright>
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

import javax.naming.*;
import javax.naming.directory.*;

import org.cougaar.util.log.Logger;

import org.cougaar.core.service.community.*;
import org.cougaar.core.service.BlackboardService;

import org.cougaar.core.mts.MessageAddress;
//import org.cougaar.core.agent.ClusterIdentifier;

import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.component.ServiceRevokedListener;
import org.cougaar.core.component.ServiceRevokedEvent;

import org.cougaar.core.service.DomainService;
import org.cougaar.core.service.NamingService;
import org.cougaar.core.service.LoggingService;
import org.cougaar.core.blackboard.BlackboardClient;
import org.cougaar.multicast.AttributeBasedAddress;

import org.cougaar.core.plugin.ComponentPlugin;

/** A CommunityService is an API which may be supplied by a
 * ServiceProvider registered in a ServiceBroker that provides
 * access to community management capabilities.
 **/
public class CommunityServiceImpl extends ComponentPlugin
  implements CommunityService, java.io.Serializable {

  protected static Logger log;
  protected MessageAddress agentId = null;
  protected ServiceBroker serviceBroker = null;
  protected DirContext communitiesContext = null;

  private BlackboardClient blackboardClient = null;
  private SearchControls defaultSearchControls = new SearchControls();
  private BlackboardService blackboardService = null;


  public static CommunityService getInstance(ServiceBroker sb,
    MessageAddress addr, boolean useCache) {
    if (useCache) {
      return new CachedCommunityServiceImpl(sb, addr);
    } else {
      return new CommunityServiceImpl(sb, addr);
    }
  }

  /**
   * Constructor.
   * @param sb       Reference to agent ServiceBroker
   * @param addr     Address of parent agent
   * @param useCache Cache flag
   */
  protected CommunityServiceImpl(ServiceBroker sb, MessageAddress addr) {
    this.agentId = addr;
    this.serviceBroker = sb;
    this.log = getLoggingService();
    this.communitiesContext = getCommunitiesContext();
    defaultSearchControls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
    defaultSearchControls.setReturningObjFlag(false);
  }

  /**
   * Gets the Community Context from the Yellow Pages root.
   */
  private DirContext getCommunitiesContext() {
    DirContext communitiesContext =
      getContext(CommunityService.COMMUNITIES_CONTEXT_NAME);
    if (communitiesContext == null)
      communitiesContext =
        createContext(CommunityService.COMMUNITIES_CONTEXT_NAME);
    return communitiesContext;
  }


  /**
   * Gets a context from the Yellow Pages root.
   */
  private DirContext getContext(String contextName) {
    try {
      return (DirContext)getNamingService().getRootContext().lookup(contextName);
    } catch (Exception ex) {
      //log.error("Exception getting Context " + contextName + ", " + ex);
      return null;
    }
  }

  private DirContext createContext(String contextName) {
    if (log.isDebugEnabled())
      log.debug("Creating '" + contextName + "' context in Yellow Pages");
    DirContext context = null;
    try {
      // Check for existence of context
      context =
        (DirContext)getNamingService().getRootContext().lookup(contextName);
    } catch (NamingException ne) {
      // Context doesn't exist, try to crate it
      try {
        InitialDirContext root = getNamingService().getRootContext();
        context = root.createSubcontext(contextName, null);
      } catch (Exception ex) {
        log.error("Exception creating Context " + contextName + ", " + ex);
      }
    }
    return context;
  }


  /**
   * Creates a new community in Name Server.
   * @param communityName Name of community
   * @param attributes    Community attributes
   * @return              True if operation was successful
   */
  public boolean createCommunity(String communityName, Attributes attributes) {
    boolean success = false;
    if (communityName != null && !communityExists(communityName)) {
      try {
        // Ensure that the community at least has a "Name" attribute defined
        Attributes attrs = attributes;
        if (attrs == null) {
          attrs = new BasicAttributes();
          attrs.put(new BasicAttribute("Name", communityName));
        } else {
          if (attrs.get("Name") == null) {
            attrs.put(new BasicAttribute("Name", communityName));
          }
        }
        DirContext dc =
          communitiesContext.createSubcontext(communityName, attrs);
        if (log.isDebugEnabled())
            log.debug("Community '" + communityName + "' added to Name Server");
        success = true;
      } catch (NameAlreadyBoundException nabe) {
        // Ignore these exeptions, on occassion another agent will create
        // the context after we've tested for it existence but before we
        // create it
        //log.error("Exception adding community '" + communityName +
        //"' to Name Server, " + ne, ne);
      } catch (NamingException ne) {
        log.error("Exception adding community '" + communityName +
        "' to Name Server, " + ne, ne);
      }
    }
    return success;
  }


  /**
   * Checks for the existence of a community in Name Server.
   * @param communityName Name of community to look for
   * @return              True if community was found
   */
  public boolean communityExists(String communityName) {
    boolean exists = false;
    if (communityName != null) {
      try {
        exists = (communitiesContext.lookup(communityName) != null);
      } catch (NamingException ne) {}
    }
    return exists;
  }


  /**
   * Lists all communities in Name Server.
   * @return  Collection of community names
   */
  public Collection listAllCommunities() {
    Collection communityNames = new Vector();
    try {
      NamingEnumeration enum = communitiesContext.list("");
      while (enum.hasMore()) {
        NameClassPair ncPair = (NameClassPair)enum.next();
        communityNames.add(ncPair.getName());
      }
    } catch (NamingException ex) {
      log.error("Exception getting Community Context, " + ex, ex);
    }
    return communityNames;
  }


  /**
   * Returns attributes associated with community.
   * @param communityName Name of community
   * @return              Communities attributes
   */
  public Attributes getCommunityAttributes(String communityName) {
    Attributes attrs = new BasicAttributes();
    try {
      attrs = communitiesContext.getAttributes(communityName);
    } catch (NamingException ne) {
      log.error("Exception getting attributes for community " +
        communityName + ", " + ne);
    }
    return attrs;
  }


  /**
   * Modifies the attributes associated with a community.
   * @param communityName Name of community
   * @param attributes    Communities attributes
   * @return              True if operation was successful
   */
  public boolean setCommunityAttributes(String communityName,
                                        Attributes attributes) {
    try {
      communitiesContext.modifyAttributes(communityName,
        DirContext.REPLACE_ATTRIBUTE, attributes);
      notifyListeners(communityName, "Set community attributes for " + communityName);
      return true;
    } catch (NamingException ne) {
      log.error("Exception setting attributes for community " +
        communityName + ", " + ne);
    }
    return false;
  }



  /**
   * Adds an entity to a community.
   * @param communityName        Community name
   * @param entity               Entity to add
   * @param attributes           Attributes to associate with entity
   * @return                     True if operation was successful
   */
  public boolean addToCommunity(String communityName, Object entity,
                         String entityName, Attributes attributes) {
    if (log.isDebugEnabled())
      log.debug("Add entity " + entityName + " to community " + communityName);
    try {
      Attributes attrs = attributes;
      if (attrs == null) {
        attrs = new BasicAttributes("Name", entityName);
      } else {
        Attribute attr = attrs.get("Name");
        if (attr == null || attr.size() == 0) {
        attrs.put(new BasicAttribute("Name", entityName));
        }
      }
      DirContext community =
        (DirContext)communitiesContext.lookup(communityName);
      community.rebind(entityName, entity, attrs);
      notifyListeners(communityName, "Add " + entityName + " to community " + communityName);
      return true;
    } catch (Exception ex) {
      log.error("Exception adding entity '" + entityName + "' to community '" +
        communityName + "', " + ex);
    }
    return false;
  }


  /**
   * Removes an entity from a community.
   * @param communityName  Community name
   * @param entityName     Name of entity to remove
   * @return               True if operation was successful
   */
  public boolean removeFromCommunity(String communityName, String entityName) {
    try {
      DirContext community = (DirContext)communitiesContext.lookup(communityName);
      community.unbind(entityName);
      notifyListeners(communityName, "Remove " + entityName + " from community "
        + communityName);
      return true;
    } catch (Exception ex) {
      log.error("Exception removing entity '" + entityName +
        "' from community '" + communityName + "', " + ex);
    }
    return false;
  }


  /**
   * Returns a collection of entity names associated with the specified
   * community.
   * @param communityName  Entities parent community
   * @return               Collection of entity names
   */
  public Collection listEntities(String communityName) {
    Collection entityNames = new Vector();
    try {
      NamingEnumeration enum = communitiesContext.list(communityName);
      while (enum.hasMoreElements()) {
        NameClassPair ncp = (NameClassPair)enum.next();
        entityNames.add(ncp.getName());
      }
    } catch (Exception ex) {
      log.error("Exception getting entity list for community '" +
        communityName + "', " + ex);
    }
    return entityNames;
  }


  /**
   * Returns attributes associated with specified community entity.
   * @param communityName  Entities parent community
   * @param entityName     Name of community entity
   * @return               Attributes associated with entity
   */
  public Attributes getEntityAttributes(String communityName,
                                        String entityName) {
    Attributes attrs = new BasicAttributes();
    try {
      DirContext community = (DirContext)communitiesContext.lookup(communityName);
      attrs = community.getAttributes(entityName);
    } catch (Exception ex) {
      log.error("Exception getting attributes for entity '" +
        entityName + "' in community '" + communityName + "', " + ex);
    }
    return attrs;
  }

  /**
   * Merges two Attribute objects into one.  Use in conjunction with
   * DirContext.modifyAttributes to preserve multi-valued attributes.
   * @param a1 Attribute set 1
   * @param a2 Attribute set 2
   * @return   Merged attributes
   */
  private Attributes mergeAttributes(Attributes a1, Attributes a2) {
    Attributes newAttrs = new BasicAttributes();
    try {
      for (NamingEnumeration a1Enum = a1.getAll(); a1Enum.hasMore();) {
        Attribute attr1 = (Attribute)a1Enum.next();
        Attribute attr2 = a2.get(attr1.getID());
        if (attr2 == null) { // Attribute is unique to a1
          newAttrs.put(attr1);
        } else { // Both a1 and a2 contain attribute, merge values
          Attribute newAttr = new BasicAttribute(attr1.getID());
          // Add all values from attr1
          for (NamingEnumeration valEnum = attr1.getAll(); valEnum.hasMore();) {
            newAttr.add(valEnum.nextElement());
          }
          // Add unique values from attr2
          for (NamingEnumeration valEnum = attr2.getAll(); valEnum.hasMore();) {
            Object val = valEnum.nextElement();
            if (!newAttr.contains(val)) newAttr.add(val);
          }
          newAttrs.put(newAttr);
        }
      }
      for (NamingEnumeration a2Enum = a2.getAll(); a2Enum.hasMore();) {
        Attribute attr2 = (Attribute)a2Enum.next();
        Attribute attr1 = a1.get(attr2.getID());
        if (attr1 == null) { // Attribute is unique to a2
          newAttrs.put(attr2);
        }
      }
    } catch (Exception ex) {
      log.error("Exception merging attributes, " + ex);
    }
    return newAttrs;
  }

  /**
   * Modifies the attributes associated with specified community entity.
   * @param communityName  Entities parent community
   * @param entityName     Name of community entity
   * @param attributes     Attributes to associate with entity
   * @return               True if operation was successful
   */
  public boolean setEntityAttributes(String communityName, String entityName,
                                     Attributes attributes) {
    try {
      DirContext community =
        (DirContext)communitiesContext.lookup(communityName);
      Attributes oldAttrs = getEntityAttributes(communityName, entityName);
      Attributes mergedAttributes = mergeAttributes(oldAttrs, attributes);
      community.modifyAttributes(entityName,
        DirContext.REPLACE_ATTRIBUTE, mergedAttributes);
      String attrStr = attrsToString(mergedAttributes);
      if (log.isDebugEnabled()) {
        log.debug("setEntityAttributes: entity=" + entityName +
          " community=" + communityName +
          " oldAttrs=(" + attrsToString(oldAttrs) + ")" +
          " newAttrs=(" + attrsToString(attributes) + ")" +
          " mergedAttributes=(" + attrStr + ")");
      }
      notifyListeners(communityName, "Set entity attributes for " + entityName +
        " (" + attrStr + ")");
      return true;
    } catch (Exception ex) {
      log.error("Exception setting attributes for entity '" +
        entityName + "' in community '" + communityName + "', " + ex);
    }
    return false;
  }


  /**
   * Performs attribute based search of community context.  This search looks
   * for communities with attributes that satisfy criteria specified by filter.
   * Entities within communities are not searched.  This is a general
   * purpose search operation using a JNDI search filter.  Refer to JNDI
   * documentation for filter syntax.
   * @param filter        JNDI search filter
   * @return              Collection of community names that satisfy filter
   */
  public Collection search(String filter) {
    Collection communityNames = new Vector();
    try {
      NamingEnumeration enum = communitiesContext.search("",
                                   filter, defaultSearchControls);
      while (enum.hasMore()) {
        SearchResult sr = (SearchResult)enum.next();
        communityNames.add(sr.getName());
      }
    } catch (Exception ex) {
      log.error("Exception searching communities context, filter='" + filter +
        "', " + ex);
    }
    return communityNames;
  }


  /**
   * Performs attribute based search of community entities.  This is a general
   * purpose search operation using a JNDI search filter.
   * @param communityName Name of community to search
   * @param filter        JNDI search filter
   * @return              Collection of entities
   */
  public Collection search(String communityName, String filter) {
    if (log.isDebugEnabled())
      log.debug("search: community=" + communityName + " filter=" + filter);
    Collection entities = new Vector();
    try {
      DirContext community =
        (DirContext)communitiesContext.lookup(communityName);
      SearchControls sc = new SearchControls();
      sc.setSearchScope(SearchControls.ONELEVEL_SCOPE);
      sc.setReturningObjFlag(true);
      NamingEnumeration enum = community.search("",
                                   filter, sc);
      log.debug ("search result: matched=" + enum.hasMoreElements());
      while (enum.hasMore()) {
        SearchResult sr = (SearchResult)enum.next();
        entities.add(sr.getObject());
        log.debug ("search result: class=" + sr.getObject().getClass().getName() + " value=" + sr.getObject().toString());
      }
    } catch (Exception ex) {
      log.error("Exception searching community '" + communityName +
        "', filter='" + filter + "', " + ex);
    }
    return entities;
  }


  protected Object lookup(String communityName, String entityName) {
    try {
      DirContext community =
        (DirContext)communitiesContext.lookup(communityName);
      return community.lookup(entityName);
    } catch (Exception ex) {
      log.error("Exception performing lookup on community'" + communityName +
        "', entityName='" + entityName + "', " + ex);
    }
    return null;
  }


  /**
   * Requests the roster for the named community.
   * @param communityName Name of community
   * @return              Community roster (or null if agent is not authorized
   *                      access)
   */
  public CommunityRoster getRoster(String communityName) {
    CommunityRosterImpl roster = new CommunityRosterImpl(communityName);
    if (communityExists(communityName)) {
      roster.setCommunityExists(true);
      try {
        SearchControls sc = new SearchControls();
        sc.setSearchScope(SearchControls.ONELEVEL_SCOPE);
        sc.setReturningAttributes(new String[]{"Name","EntityType"});
        sc.setReturningObjFlag(true);
        NamingEnumeration enum = communitiesContext.search(communityName,
                                   "(Role=Member)", sc);
        while (enum.hasMore()) {
          SearchResult sr = (SearchResult)enum.next();
          Attribute attr = sr.getAttributes().get("Name");
          String name = "";
          if (attr != null)
            name = (String)attr.get();
          attr = sr.getAttributes().get("EntityType");
          int type = CommunityMember.AGENT;
          if (attr != null && attr.contains("Community"))
              type = CommunityMember.COMMUNITY;
          roster.addMember(new CommunityMember(name, type));
        }
      } catch (Exception ex) {
        log.error("Exception getting roster for community " + communityName +
          ", " + ex);
      }
    } else {
      roster.setCommunityExists(false);
    }
    return roster;
  }

  /**
   * Requests a collection of community names identifying the communities that
   * contain the specified member.
   * @param name   Member name
   * @return A collection of community names
   */
  public Collection listParentCommunities(String member) {
    Collection communitiesWithMember = new Vector();
    try {
      SearchControls treeSearch = new SearchControls();
      treeSearch.setSearchScope(SearchControls.SUBTREE_SCOPE);
      treeSearch.setReturningObjFlag(false);
      NamingEnumeration enum = communitiesContext.search("",
                          "(&(Role=Member)(Name=" + member + "))",
                          treeSearch);
      while (enum.hasMore()) {
        SearchResult sr = (SearchResult)enum.next();
        String name = sr.getName();
        int i = name.indexOf("/");
        String communityName = (i > 0) ? name.substring(0, i) : name;
        communitiesWithMember.add(communityName);
      }
    } catch (Exception ex) {
        log.error("Exception searching for parent communities, member=" +
         member + ", " + ex);
    }
    return communitiesWithMember;
  }

  /**
   * Requests a collection of community names identifying the communities that
   * contain the specified member and satisfy a given set of attributes.
   * @param name   Member name
   * @param filter Search filter defining community attributes
   * @return A collection of community names
   */
  public Collection listParentCommunities(String member, String filter) {
      Collection communitiesWithMember = new Vector();
    try {
      // Check to see if a filter was specified, if not do a regular
      // parent community search
      if (filter == null || filter.trim().length() == 0) {
        return listParentCommunities(member);
      }
      // Find communities that satisfy search filter
      NamingEnumeration enum = communitiesContext.search("",
                          filter, defaultSearchControls);
      while (enum.hasMore()) {
        // Now, find communities that have specified member
        SearchResult sr = (SearchResult)enum.next();
        String communityName = sr.getName();
        DirContext community =
          (DirContext)communitiesContext.lookup(communityName);
        NamingEnumeration enum1 = community.search("",
          "(&(Role=Member)(Name=" + member + "))", defaultSearchControls);
        if (enum1.hasMore()) {
          communitiesWithMember.add(sr.getName());
        }
      }
    } catch (Exception ex) {
        log.error("Exception searching for parent communities, member=" +
         member + ", filter=" + filter + ", " + ex);
    }
    return communitiesWithMember;
  }


  /**
   * Checks for existence of specified entity within named community.
   * @param communityName
   * @param entityName
   * @return True if entity exists
   */
  private boolean entityExists(String communityName, String entityName) {
    try {
      DirContext community =
        (DirContext)communitiesContext.lookup(communityName);
      return (community.lookup(entityName) != null);
    } catch (Exception ex) {
      return false;
    }
  }

  /**
   * Adds a listener to list of addresses that are notified of changes to
   * specified community.
   * @param addr          Listeners MessageAddress
   * @param communityName Community of interest
   * @return              True if operation was successful
   */
  public boolean addListener(MessageAddress addr, String communityName) {
    if (log.isDebugEnabled())
      log.debug("addListener: listener=" + addr.toString() +
        " community=" + communityName);
    if (addr != null && communityName != null) {
      try {
        DirContext community =
          (DirContext)communitiesContext.lookup(communityName);
        if (!entityExists(communityName, addr.toString())) {
          // Entity doesn't exist, create it
          Attributes attrs = new BasicAttributes();
          attrs.put(new BasicAttribute("Name", addr.toString()));
          attrs.put(new BasicAttribute("Role", "ChangeListener"));
          if (!addToCommunity(communityName, addr, addr.toString(), attrs)) {
            return false;  // Couldn't add entity to community
          }
        }
        return addRole(communityName, addr.toString(), "ChangeListener");
      } catch (Exception ex) {
        log.error("Exception adding listener '" + addr +
          "' to community '" + communityName + "', " + ex);
      }
    }
    return false;
  }


  /**
   * Removes a listener from list of addresses that are notified of changes to
   * specified community.
   * @param addr          Listeners MessageAddress
   * @param communityName Community of interest
   * @return              True if operation was successful
   */
  public boolean removeListener(MessageAddress addr, String communityName) {
    if (addr != null && communityName != null) {
      try {
        DirContext community =
          (DirContext)communitiesContext.lookup(communityName);
        if (entityExists(communityName, addr.toString())) { // Entity exists
          removeRole(communityName, addr.toString(), "ChangeListener");
          // If that was the only attribute associated with agent remove
          // it from community
          Attributes attrs = community.getAttributes(addr.toString());
          if (attrs.size() == 0)
            return removeFromCommunity(communityName, addr.toString());
          return true;
        }
      } catch (Exception ex) {
        log.error("Exception removing listener '" + addr +
          "' from community '" + communityName + "', " + ex);
      }
    }
    return false;
  }


  /**
   * Returns a collection of ClusterIdentifiers associated with the agents
   * that are have the attribute "ChangeListener".
   * specified community.
   * @param communityName Community of interest
   * @return              MessageAddresses of listener agents
   */
  public Collection getListeners(String communityName) {
    Collection listeners = new Vector();
    try {
      DirContext community =
        (DirContext)communitiesContext.lookup(communityName);
      SearchControls sc = new SearchControls();
      sc.setSearchScope(SearchControls.ONELEVEL_SCOPE);
      sc.setReturningObjFlag(true);
      NamingEnumeration enum = community.search("", "(Role=ChangeListener)", sc);
      while (enum.hasMore()) {
        SearchResult sr = (SearchResult)enum.next();
        if (sr.getObject() instanceof MessageAddress) {
          listeners.add(sr.getObject());
        }
      }
    } catch (Exception ex) {
        log.error("Exception getting changeListeners for community '" +
         communityName + "', " + ex);
    }
    return listeners;
  }


  /**
   * Finds all community entities associated with a given role.  This method
   * is equivalent to using the search method with the filter
   * "(Role=RoleName)".
   * @param communityName Name of community to query
   * @param roleName      Name of role provided
   * @return              Collection of entity objects
   */
  public Collection searchByRole(String communityName, String roleName) {
    return search(communityName, "(Role=" + roleName + ")");
  }


  /**
   * Returns a collection of all roles supported by the specified community
   * entity.
   * @param communityName  Parent community
   * @param entityName     Name of community entity
   * @return               Collection of role names
   */
  public Collection getEntityRoles(String communityName, String entityName) {
    Collection roles = new Vector();
    try {
      DirContext community =
        (DirContext)communitiesContext.lookup(communityName);
      NamingEnumeration enum = community.search("",
                          "(&(Role=*)(Name=" + entityName + "))",
                          defaultSearchControls);
      while (enum.hasMore()) {
        SearchResult sr = (SearchResult)enum.next();
        Attribute attr = sr.getAttributes().get("Role");
        NamingEnumeration valuesEnum = attr.getAll();
        while (valuesEnum.hasMore()) {
          roles.add((String)valuesEnum.next());
        }
      }
    } catch (Exception ex) {
        log.error("Exception getting Roles for entity '" +
         entityName + "' in community '" + communityName + "', " + ex);
    }
    return roles;
  }


  /**
   * Returns a list of all external roles supported by the specified community.
   * @param communityName Community name
   * @return              Collection of role names
   */
  public Collection getCommunityRoles(String communityName) {
    Collection externalRoles = new Vector();
    try {
      DirContext community =
        (DirContext)communitiesContext.lookup(communityName);
      NamingEnumeration enum = community.search("",
                          "(&(Role=Member)(ExternalRole=*))",
                          defaultSearchControls);
      while (enum.hasMore()) {
        SearchResult sr = (SearchResult)enum.next();
        Attribute attr = sr.getAttributes().get("ExternalRole");
        NamingEnumeration valuesEnum = attr.getAll();
        while (valuesEnum.hasMore()) {
          externalRoles.add((String)valuesEnum.next());
        }
      }
    } catch (Exception ex) {
        log.error("Exception getting externalRoles for community '" +
         communityName + "', " + ex);
    }
    return externalRoles;
  }


  /**
   * Associates a new role with specified community entity.
   * @param communityName  Parent community
   * @param entityName     Name of community entity
   * @param roleName       Name of role to associate with entity
   * @return               True if operation was successful
   */
  public boolean addRole(String communityName, String entityName,
                         String roleName) {
    if (log.isDebugEnabled())
      log.debug("addRole: entity=" + entityName +
        " community=" + communityName + " role=" + roleName);
    try {
      DirContext community =
      (DirContext)communitiesContext.lookup(communityName);
      Attributes attrs = community.getAttributes(entityName);
      Attribute roleAttr = attrs.get("Role");
      if (roleAttr == null) {
        roleAttr = new BasicAttribute("Role", roleName);
        attrs.put(roleAttr);
      } else {
        if (!roleAttr.contains(roleName)) {
          roleAttr.add(roleName);
        }
      }
      community.modifyAttributes(entityName,
        DirContext.REPLACE_ATTRIBUTE, attrs);
      return true;
    } catch (Exception ex) {
      log.error("Exception adding Role attribute for entity '" +
        entityName + "' in community '" + communityName + "', " + ex);
    }
    return false;
  }


  /**
   * Removes a Role from attributes of specified community entity.
   * @param communityName  Parent community
   * @param entityName     Name of community entity
   * @param roleName       Name of role to associate with entity
   * @return               True if operation was successful
   */
  public boolean removeRole(String communityName, String entityName,
                            String roleName) {
    try {
      DirContext community =
        (DirContext)communitiesContext.lookup(communityName);
      Attributes attrs = community.getAttributes(entityName);
      Attribute roleAttr = attrs.get("Role");
      Attribute newRoleAttr = new BasicAttribute("Role");
      if (roleAttr != null && roleAttr.contains(roleName)) {
        roleAttr.remove(roleName);
        if (roleAttr.size() == 0) attrs.remove("Role");
      }
      community.modifyAttributes(entityName,
        DirContext.REPLACE_ATTRIBUTE, attrs);
      return true;
    } catch (Exception ex) {
      log.error("Exception removing Role attribute for entity '" +
        entityName + "' in community '" + communityName + "', " + ex);
    }
    return false;
  }


  /**
   * Gets reference to NamingService.
   */
  protected NamingService getNamingService() {
    NamingService ns = null;
    if (serviceBroker.hasService(org.cougaar.core.service.NamingService.class)) {
      ns = (NamingService)serviceBroker.getService(this,
      org.cougaar.core.service.NamingService.class, null);
    }
    return ns;
  }


  /**
   * Gets reference to Blackboard service.
   */
  protected BlackboardService getBlackboardService(ServiceBroker sb) {
    if (blackboardService == null) {
      while (!sb.hasService(org.cougaar.core.service.BlackboardService.class)) {
        try { Thread.sleep(500); } catch (Exception ex) {}
        log.debug("Waiting for BlackboardService");
      }
      if (blackboardClient == null) {
        blackboardClient = new MyBlackboardClient();
      }
      blackboardService = (BlackboardService)sb.getService(blackboardClient,
        BlackboardService.class, null);
    }
    return blackboardService;
  }


  /**
   * Gets reference to Domain service.
   */
  protected DomainService getDomainService(ServiceBroker sb) {
    while (!sb.hasService(org.cougaar.core.service.DomainService.class)) {
      try { Thread.sleep(500); } catch (Exception ex) {}
    }
    return (DomainService)sb.getService(this, DomainService.class, null);
  }


  /**
   * Notifies interested agents that a change has occurred in community.
   */
  protected void notifyListeners(final String communityName, final String message) {
    if (communityExists(communityName)) {
      final Collection listeners = getListeners(communityName);
      //log.debug("NotifyListeners: listeners=" + listeners.size() + " agent=" +
      //  agentId + " community=" + communityName + " message='" + message + "'");
      if (listeners.size() > 0) {
        Thread notifyThread = new Thread("CommunityChangeNotificationThread") {
          public void run() {
            try {
              DomainService domainService = getDomainService(serviceBroker);
              CommunityChangeNotificationFactory ccnFactory = null;
              while (ccnFactory == null) {
                ccnFactory =
                  ((CommunityChangeNotificationFactory)domainService.getFactory("community"));
                if (ccnFactory == null) Thread.sleep(500);
              }
              BlackboardService bbs = getBlackboardService(serviceBroker);
              CommunityChangeNotification ccn =
                ccnFactory.newCommunityChangeNotification(communityName, agentId);

			        /*
			        log.debug("NotifyListeners: agent=" + agentId + " community=" +
			  	      communityName + " attributeID=Role attributeValue=ChangeListener");
              ccn.addTarget(new AttributeBasedAddress(communityName,
                                                "Role",
                                                "ChangeListener"));
			        */

			        // Uses explicit addresses for listeners rather than ABA
              for (Iterator it = listeners.iterator(); it.hasNext();) {
                MessageAddress listener = (MessageAddress)it.next();
			  	      log.debug("NotifyListeners: message='" + message + "' agent=" +
                  agentId + " community=" + communityName + " listener=" + listener);
                ccn.addTarget(listener);
              }

              bbs.openTransaction();
              bbs.publishAdd(ccn);
              bbs.closeTransaction();
            } catch (Exception ex) {
              log.error("Exception in NotifyListeners, " + ex, ex);
            }
          }
        };
        notifyThread.start();
      }
    }
  }


  /**
   * Gets reference to LoggingService.
   */
  private LoggingService getLoggingService() {
    return (LoggingService)serviceBroker.getService(
        this, LoggingService.class, null);
  }

  /**
   * Prints a list of the current services.
   */
  private void listServices() {
    System.out.println("Current Services:");
    for (Iterator it = serviceBroker.getCurrentServiceClasses(); it.hasNext();) {
      System.out.println("  " + ((Class)it.next()).getName());
    }
  }

  /**
   * Creates a string representation of an Attribute set.
   */
  private String attrsToString(Attributes attrs) {
    StringBuffer sb = new StringBuffer();
    try {
      for (NamingEnumeration enum = attrs.getAll(); enum.hasMore();) {
        Attribute attr = (Attribute)enum.next();
        sb.append(attr.getID() + "=[");
        for (NamingEnumeration enum1 = attr.getAll(); enum1.hasMore();) {
          sb.append((String)enum1.next());
          if (enum1.hasMore())
            sb.append(",");
          else
            sb.append("]");
        }
        if (enum.hasMore()) sb.append(" ");
      }
    } catch (NamingException ne) {}
    return sb.toString();
  }

  class MyBlackboardClient implements BlackboardClient {

    public long currentTimeMillis() {
      return System.currentTimeMillis();
    }

    public String getBlackboardClientName() {
      return "CommunityService";
    }

    public boolean triggerEvent(Object event) {
      return false;
    }

  }

  public void setupSubscriptions() {}
  public void execute() {}

}
