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

import java.io.Serializable;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttributes;

import org.cougaar.core.service.community.Entity;

/**
 * Defines entities that are associated with a community.
 */
public class EntityImpl implements Entity, Serializable, Cloneable {

  // Instance variables
  protected String name;
  protected Attributes attrs = new BasicAttributes();

  /**
   * Constructor.
   * @param name Name of new Entity
   */
  public EntityImpl(String name) {
    this.name = name;
  }

  /**
   * Constructor.
   * @param name Name of new Entity
   * @param attrs Initial attributes
   */
  public EntityImpl(String name, Attributes attrs) {
    this.name = name;
    this.attrs = attrs;
  }

  /**
   * Set entity name.
   * @param name  Entity name
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Get entity name.
   * @return Entity name
   */
  public String getName() {
    return this.name;
  }

  /**
   * Set entity attributes.
   * @param attrs Entity attributes
   */
  public void setAttributes(Attributes attrs) {
    this.attrs = attrs;
  }

  /**
   * Get entity attributes.
   * @return Entity attributes
   */
  public Attributes getAttributes() {
    return this.attrs;
  }

  public boolean equals(Object o) {
    return (o instanceof Entity && name.equals(((Entity)o).getName()) &&
            attrs.equals(((Entity)o).getAttributes()));
  }

  public int hashCode() {
    return (name != null ? name.hashCode() : "".hashCode());
  }

  /**
   * Returns name of entity.
   * @return entity name
   */
  public String toString() {
    return getName();
  }

  public Object clone() {
    EntityImpl o = null;
    try {
      o = (EntityImpl)super.clone();
    } catch (CloneNotSupportedException e) {
      e.printStackTrace();
    }
    o.name = new String(name);
    if (attrs == null) {
      o.attrs = null;
    } else {
      o.attrs = CommunityUtils.cloneAttributes(attrs);
    }
    return o;
  }

  /**
   * Returns an XML representation of entity.
   * @return XML representation of entity
   */
  public String toXml() {
    return toXml("");
  }

  /**
   * Returns an XML representation of Entity.
   * @param indent Blank string used to pad beginning of entry to control
   *               indentation formatting
   * @return XML representation of entity
   */
  public String toXml(String indent) {
    StringBuffer sb = new StringBuffer(indent + "<Entity name=\"" + name + "\" >\n");
    if (attrs != null && attrs.size() > 0)
      sb.append(attrsToString(attrs, indent + "  "));
    sb.append(indent + "</Entity>\n");
    return sb.toString();
  }

  /**
   * Creates a string representation of an Attribute set.
   * @param attrs Attributes
   * @return String representation of attributes
   */
  public static String attrsToString(Attributes attrs) {
    return attrsToString(attrs, "");
  }

  /**
   * Creates a string representation of an Attribute set.
   * @param attrs Attributes
   * @param indent Indentation for pretty printing
   * @return String representation of attributes
   */
  public static String attrsToString(Attributes attrs, String indent) {
    StringBuffer sb = new StringBuffer(indent + "<Attributes>\n");
    try {
      for (NamingEnumeration enum = attrs.getAll(); enum.hasMore();) {
        Attribute attr = (Attribute)enum.next();
        sb.append(indent + "  <Attribute id=\"" + attr.getID() + "\" >\n");
        for (NamingEnumeration enum1 = attr.getAll(); enum1.hasMore();) {
          sb.append(indent + "    <Value>" + enum1.next() + "</Value>\n");
        }
        sb.append(indent + "  </Attribute>\n");
      }
    } catch (NamingException ne) {}
    sb.append(indent + "</Attributes>\n");
    return sb.toString();
  }

  /**
   * Creates a string representation of an Attribute set.
   * @return String representation of attributes
   */
  public String attrsToString() {
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

}
