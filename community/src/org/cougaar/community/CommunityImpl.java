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

import org.cougaar.core.relay.Relay;
import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.util.UniqueObject;
import org.cougaar.core.util.UID;

import org.cougaar.core.service.community.Agent;
import org.cougaar.core.service.community.Entity;
import org.cougaar.core.service.community.Community;

import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

import javax.naming.directory.Attributes;

import org.cougaar.util.log.*;

import org.cougaar.core.naming.Filter;
import org.cougaar.core.naming.SearchStringParser;

/**
 * A community entity.
 */
public class CommunityImpl extends EntityImpl
    implements Community, java.io.Serializable {

  private Map entities = new HashMap();

  /**
   * Constructor
   */
  public CommunityImpl(String name) {
    super(name);
  }

  /**
   * Constructor
   */
  public CommunityImpl(String name, Attributes attrs) {
    super(name, attrs);
  }

  /**
   * Returns a collection containing all entities associated with this
   * community.
   * @return  Collection of Entity objects
   */
  public synchronized Collection getEntities() {
    return new ArrayList(entities.values());
  }

  public synchronized void setEntities(Collection newEntities) {
    entities = new HashMap();
    for (Iterator it = newEntities.iterator(); it.hasNext();) {
      Entity entity = (Entity)it.next();
      entities.put(entity.getName(), entity);
    }
  }

  /**
   * Returns the named Entity or null if it doesn't exist.
   * @return  Entity referenced by name
   */
  public synchronized Entity getEntity(String name) {
    return (Entity)entities.get(name);
  }

  /**
   * Returns true if community contains entity.
   * @param  Name of requested entity
   * @return true if community contains entity
   */
  public synchronized boolean hasEntity(String name) {
    return entities.containsKey(name);
  }

  /**
   * Adds an Entity to the community.
   * @param entity  Entity to add to community
   */
  public synchronized void addEntity(Entity entity) {
    entities.put(entity.getName(), entity);
  }

  /**
   * Removes an Entity from the community.
   * @param entity  Name of entity to remove from community
   */
  public synchronized void removeEntity(String name) {
    entities.remove(name);
  }

  /**
   * Performs search of community and returns collection of matching Entity
   * objects.
   * @param filter    JNDI style search filter
   * @param qualifier Search qualifier (e.g., AGENTS_ONLY, COMMUNITIES_ONLY, or
   *                  ALL_ENTITIES)
   * @return Set of Entity objects satisfying search filter
   */
  public Set search(String filter,
                    int qualifier) {
    Set matches = new HashSet();
    SearchStringParser parser = new SearchStringParser();
    try {
      Filter f = parser.parse(filter);
      synchronized (this) {
        for (Iterator it = entities.values().iterator(); it.hasNext(); ) {
          Entity entity = (Entity) it.next();
          if (f.match(entity.getAttributes())) {
            if ( (qualifier == ALL_ENTITIES) ||
                (qualifier == AGENTS_ONLY && entity instanceof Agent) ||
                (qualifier == COMMUNITIES_ONLY && entity instanceof Community)) {
              matches.add(entity);
            }
          }
        }
      }
    }
    catch (Exception ex) {
      System.out.println("Exception in Community search, filter=" + filter);
      ex.printStackTrace();
    }
    return matches;
  }

  // Converts a collection of entities to a compact string representation of names
  private String entityNames(Collection members) {
    StringBuffer sb = new StringBuffer("[");
    for (Iterator it = members.iterator(); it.hasNext(); ) {
      sb.append(it.next().toString() + (it.hasNext() ? "," : ""));
    }
    return (sb.append("]").toString());
  }

  public String qualifierToString(int qualifier) {
    switch (qualifier) {
      case AGENTS_ONLY: return "AGENTS_ONLY";
      case COMMUNITIES_ONLY: return "COMMUNITIES_ONLY";
      case ALL_ENTITIES: return "ALL_ENTITIES";
    }
    return "INVALID_VALUE";
  }

  /**
   * Returns an XML representation of community.
   */
  public String toXml() {
    return toXml("");
  }

  /**
   * Returns an XML representation of community.
   * @param indent Blank string used to pad beginning of entry to control
   *               indentation formatting
   */
  public String toXml(String indent) {
    StringBuffer sb = new StringBuffer(indent + "<Community name=\"" + getName() +
                                       "\" >\n");
    Attributes attrs = getAttributes();
    if (attrs != null && attrs.size() > 0)
      sb.append(attrsToString(getAttributes(), indent + "  "));
    synchronized (this) {
      for (Iterator it = entities.values().iterator(); it.hasNext(); ) {
        sb.append( ( (Entity) it.next()).toXml(indent + "  "));
      }
    }
    sb.append(indent + "</Community>\n");
    return sb.toString();
  }

  private void writeObject(ObjectOutputStream stream) throws IOException {
    //stream.defaultWriteObject();
    stream.writeObject(this.getName());
    stream.writeObject(this.getAttributes());
    synchronized (this) {
      stream.writeObject(this.entities);
    }
  }

  private void readObject(ObjectInputStream stream) throws ClassNotFoundException, IOException {
    //stream.defaultReadObject();
    setName((String)stream.readObject());
    setAttributes((Attributes)stream.readObject());
    entities = (HashMap)stream.readObject();
  }
}