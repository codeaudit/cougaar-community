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

import org.cougaar.core.service.community.Agent;

import javax.naming.directory.Attributes;

/**
 * An agent entity.
 */
public class AgentImpl extends EntityImpl implements Agent, java.io.Serializable {

  /**
   * Constructor
   */
  public AgentImpl(String name) {
    super(name);
  }

  /**
   * Constructor
   */
  public AgentImpl(String name, Attributes attrs) {
    super(name, attrs);
  }

  /**
   * Returns an XML representation of agent.
   */
  public String toXml() {
    return toXml("");
  }

  /**
   * Returns an XML representation of agent.
   * @param indent Blank string used to pad beginning of entry to control
   *               indentation formatting
   */
  public String toXml(String indent) {
    StringBuffer sb = new StringBuffer(indent + "<Agent name=\"" + getName() + "\" >\n");
    Attributes attrs = getAttributes();
    if (attrs != null && attrs.size() > 0)
      sb.append(attrsToString(getAttributes(), indent + "  "));
    sb.append(indent + "</Agent>\n");
    return sb.toString();
  }

}