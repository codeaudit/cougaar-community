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

import org.cougaar.core.service.community.Entity;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttributes;

/**
 * Defines entities that are associated with a community.
 */
public class EntityImpl implements Entity, java.io.Serializable {

  // Instance variables
  private String name;
  private Attributes attrs = new BasicAttributes();

  /**
   * Constructor.
   */
  public EntityImpl(String name) {
    this.name = name;
  }

  /**
   * Constructor.
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

  /**
   * Instances are considered equal if they have the same name.
   */
  public boolean equals(Object o) {
    return (o instanceof Entity && name.equals(((Entity)o).getName()));
  }

  /**
   * Instances are considered equal if they have the same name.
   */
  public int hashCode() {
    return (name != null ? name.hashCode() : "".hashCode());
  }

  /**
   * Returns name of entity.
   */
  public String toString() {
    return getName();
  }

  /**
   * Returns an XML representation of entity.
   */
  public String toXml() {
    return toXml("");
  }

  /**
   * Returns an XML representation of Entity.
   * @param indent Blank string used to pad beginning of entry to control
   *               indentation formatting
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
   */
  protected String attrsToString(Attributes attrs) {
    return attrsToString(attrs, "");
  }

  /**
   * Creates a string representation of an Attribute set.
   */
  protected String attrsToString(Attributes attrs, String indent) {
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