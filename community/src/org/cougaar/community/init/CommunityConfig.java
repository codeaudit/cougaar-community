/*
 * <copyright>
 *  Copyright 1997-2001 Mobile Intelligence Corp
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

package org.cougaar.community.init;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;

/**
 * Defines an initial configuration for a community.
 */
public class CommunityConfig {

  private String name;
  private Attributes attributes = new BasicAttributes();
  private Map entities = new HashMap();

  public CommunityConfig(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public void addAttribute(String id, String value) {
    Attribute attr = attributes.get(id);
    if (attr == null) {
      attr = new BasicAttribute(id, value);
      attributes.put(attr);
    } else {
      if (!attr.contains(value)) attr.add(value);
    }
  }

  public void setAttributes(Attributes attrs) {
    this.attributes = attrs;
  }

  public Attributes getAttributes() {
    return attributes;
  }

  public void addEntity(EntityConfig entity) {
    entities.put(entity.getName(), entity);
  }

  public EntityConfig getEntity(String name) {
    return (EntityConfig)entities.get(name);
  }

  public Collection getEntities() {
    return entities.values();
  }

  public boolean hasEntity(String entityName) {
    return entities.containsKey(entityName);
  }

  /**
   * Creates a printable representation of Community data.
   */
  public String toString() {
    StringBuffer sb = new StringBuffer();
    try {
      sb.append("<Community Name=\"" + getName() + "\" >\n");
      Attributes attributes = getAttributes();
      if (attributes != null && attributes.size() > 0) {
        NamingEnumeration enum = attributes.getAll();
        while (enum.hasMore()) {
          Attribute attr =
            (Attribute)enum.next();
          String id = attr.getID();
          NamingEnumeration valuesEnum = attr.getAll();
          while (valuesEnum.hasMore()) {
            String value = (String)valuesEnum.next();
            sb.append("  <Attribute ID=\"" + id +
              " Value=\"" + value + "\" />\n");
          }
        }
      }
      Collection entities = getEntities();
      for (Iterator it2 = entities.iterator(); it2.hasNext();) {
        EntityConfig entity = (EntityConfig)it2.next();
        sb.append("  <Entity Name=\"" + entity.getName() + "\"");
        attributes = entity.getAttributes();
        if (attributes.size() > 0) {
          sb.append(" />\n");
        } else {
          sb.append(" >\n");
        }
        NamingEnumeration enum = attributes.getAll();
        while (enum.hasMore()) {
          Attribute attr =
            (Attribute)enum.next();
          String id = attr.getID();
          NamingEnumeration valuesEnum = attr.getAll();
          while (valuesEnum.hasMore()) {
            String value = (String)valuesEnum.next();
            sb.append("    <Attribute ID=\"" + id +
              " Value=\"" + value + "\" />\n");
          }
        }
        sb.append("  </Entity>\n");
      }
      sb.append("</Community>");
    } catch (NamingException ne) {}
    return sb.toString();
  }

}
