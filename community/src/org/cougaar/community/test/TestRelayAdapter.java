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
package org.cougaar.community.test;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.w3c.dom.Element;
import org.w3c.dom.Document;

import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.relay.Relay;
import org.cougaar.core.util.UID;
import org.cougaar.core.util.UniqueObject;
import org.cougaar.core.util.XMLize;
import org.cougaar.core.util.XMLizable;

/**
 **/
abstract public class TestRelayAdapter
  implements TestRelay, XMLizable {

  private transient Set myTargetSet = null;

  private UID myUID = null;
  private MessageAddress mySource = null;

  /**
   * Add a target message address.
   * @param target the address of the target agent.
   **/
  public void addTarget(MessageAddress target) {
    if (myTargetSet == null) {
      myTargetSet = new HashSet();
    }
    myTargetSet.add(target);
  }

  /**
   * Add a collection of target message addresses.
   * @param targets Collection of target agent addresses.
   **/
  public void addAllTargets(Collection targets) {
    if (myTargetSet == null) {
      myTargetSet = new HashSet();
    }

    for (Iterator iterator = targets.iterator(); iterator.hasNext();) {
      Object target = iterator.next();
      if (target instanceof MessageAddress) {
        addTarget((MessageAddress) target);
      } else {
        throw new IllegalArgumentException("Invalid target class: " + target.getClass() +
                                           " all targets must extend MessageAddress.");
      }
    }
  }

  /**
   * Remove a target message address.
   * @param target the address of the target agent to be removed.
   **/
  public void removeTarget(MessageAddress target) {
    if (myTargetSet != null) {
      myTargetSet.remove(target);
    }
  }

  public void clearTargets() {
    myTargetSet = null;
  }

  // UniqueObject interface
  /** @return the UID of a UniqueObject.  If the object was created
   * correctly (e.g. via a Factory), will be non-null.
   **/
  public UID getUID() {
    return myUID;
  }

  /** set the UID of a UniqueObject.  This should only be done by
   * an LDM factory.  Will throw a RuntimeException if
   * the UID was already set.
   **/
  public void setUID(UID uid) {
    if (myUID != null) {
      RuntimeException rt = new RuntimeException("Attempt to call setUID() more than once.");
      throw rt;
    }

    myUID = uid;
  }

  // Relay.Source interface

  /**
   * @return MessageAddress of the source
   */
  public MessageAddress getSource() {
    return mySource;
  }

  /** set the MessageAddress of the source.  This should only be done by
   * an LDM factory.  Will throw a RuntimeException if
   * the source was already set.
   **/
  public void setSource(MessageAddress source) {
    if (mySource != null) {
      RuntimeException rt = new RuntimeException("Attempt to call setSource() more than once.");
      throw rt;
    }

    mySource = source;
  }

  /**
   * Get all the addresses of the target agents to which this Relay
   * should be sent.
   **/
  public Set getTargets() {
    if (myTargetSet == null) {
      return Collections.EMPTY_SET;
    } else {
      return Collections.unmodifiableSet(myTargetSet);
    }
  }

  /**
   * Get an object representing the value of this Relay suitable
   * for transmission. This implementation uses itself to represent
   * its Content.
   **/
  public Object getContent() {
    return this;
  }

  protected boolean contentChanged(TestRelayAdapter newTr) {
    return false;
  }

  private static final class SimpleRelayFactory
  implements TargetFactory, java.io.Serializable {

    public static final SimpleRelayFactory INSTANCE =
      new SimpleRelayFactory();

    private SimpleRelayFactory() {}

    /**
    * Convert the given content and related information into a Target
    * that will be published on the target's blackboard.
    **/
    public Relay.Target create(
        UID uid,
        MessageAddress source,
        Object content,
        Token token) {
      TestRelay tr = (TestRelay)content;
      return new TestRelayImpl(tr.getMessage(), source);
      //(
      //    uid, source, null, content, null);
    }

    private Object readResolve() {
      return INSTANCE;
    }
  };

  /**
  * Get a factory for creating the target.
  */
  public TargetFactory getTargetFactory() {
    return SimpleRelayFactory.INSTANCE;
  }

  /**
   * Set the response that was sent from a target. For LP use only.
   * This implemenation does nothing because responses are not needed
   * or used.
   **/
  public int updateResponse(MessageAddress target, Object response) {
    // No response expected
    return Relay.NO_CHANGE;
  }

  /**
   * Get the current Response for this target. Null indicates that
   * this target has no response.
   */
  public Object getResponse() {
    return null;
  }

  /**
   * Update the target with the new content.
   * @return true if the update changed the Relay, in which
   *    case the infrastructure should "publishChange" this
   */
  public int updateContent(Object content, Token token) {
    return Relay.NO_CHANGE;
  }

  // XMLizable interface
  /** getXML - add the Alert to the document as an XML Element and return the
   *
   * BOZO - not currently handling XML
   *
   * @param doc Document to which XML Element will be added
   * @return Element
   **/
  public Element getXML(Document doc) {
    return XMLize.getPlanObjectXML(this, doc);
  }

}
