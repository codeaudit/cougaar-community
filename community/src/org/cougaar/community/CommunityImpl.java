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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.naming.directory.Attributes;

import org.cougaar.core.service.community.Agent;
import org.cougaar.core.service.community.Community;
import org.cougaar.core.service.community.Entity;

/**
 * A community entity.
 */
public class CommunityImpl extends EntityImpl
    implements Community, java.io.Serializable {

  private Map entities = Collections.synchronizedMap(new HashMap());

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
  public Collection getEntities() {
    synchronized (entities) {
      if (entities.isEmpty()) {
        return new ArrayList();
      } else {
        return new ArrayList(entities.values());
      }
    }
  }

  public void setEntities(Collection newEntities) {
    synchronized (entities) {
      entities = Collections.synchronizedMap(new HashMap());
    }
    for (Iterator it = newEntities.iterator(); it.hasNext(); ) {
      addEntity((Entity)it.next());
    }
  }

  /**
   * Returns the named Entity or null if it doesn't exist.
   * @return  Entity referenced by name
   */
  public Entity getEntity(String name) {
    return (Entity)entities.get(name);
  }

  /**
   * Returns true if community contains entity.
   * @param  Name of requested entity
   * @return true if community contains entity
   */
  public boolean hasEntity(String name) {
    return entities.containsKey(name);
  }

  /**
   * Adds an Entity to the community.
   * @param entity  Entity to add to community
   */
  public void addEntity(Entity entity) {
    if (entity != null) {
      synchronized (entities) {
        entities.put(entity.getName(), entity);
      }
    }
  }

  /**
   * Removes an Entity from the community.
   * @param entity  Name of entity to remove from community
   */
  public void removeEntity(String name) {
    synchronized (entities) {
      entities.remove(name);
    }
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
      for (Iterator it = getEntities().iterator(); it.hasNext(); ) {
        Entity entity = (Entity)it.next();
        if (entity != null && f.match(entity.getAttributes())) {
          if ((qualifier == ALL_ENTITIES) ||
              (qualifier == AGENTS_ONLY && entity instanceof Agent) ||
              (qualifier == COMMUNITIES_ONLY && entity instanceof Community)) {
            matches.add(entity);
          }
        }
      }
    } catch (Exception ex) {
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
    for (Iterator it = getEntities().iterator(); it.hasNext(); ) {
      sb.append(((Entity)it.next()).toXml(indent + "  "));
    }
    sb.append(indent + "</Community>\n");
    return sb.toString();
  }

  private void writeObject(ObjectOutputStream stream) throws IOException {
    stream.writeObject(this.getName());
    stream.writeObject(this.getAttributes());
    stream.writeObject(getEntities());
  }

  private void readObject(ObjectInputStream stream) throws ClassNotFoundException, IOException {
    entities = Collections.synchronizedMap(new HashMap());
    setName((String)stream.readObject());
    setAttributes((Attributes)stream.readObject());
    setEntities((Collection)stream.readObject());
  }
}