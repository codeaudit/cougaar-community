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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.relay.Relay;
import org.cougaar.core.util.UID;
import org.cougaar.core.util.UniqueObject;

/**
 * Implementation of CommunityChangeNotification.
 * Declared abstract because it does not include the ability to convey content.
 * Extenders are responsible for defining content semantics.
 **/
abstract public class CommunityChangeNotificationAdapter
  implements CommunityChangeNotification, Cloneable {

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
    //if (mySource != null) {
    //  RuntimeException rt = new RuntimeException("Attempt to call setSource() more than once.");
    //  throw rt;
    //}
    mySource = source;
  }

  /**
   * Get all the addresses of the target agents to which this Relay
   * should be sent.
   **/
  public Set getTargets() {
    Set targets = (myTargetSet == null) ? Collections.EMPTY_SET
                                        : Collections.unmodifiableSet(myTargetSet);
    //System.out.println("targets=" + targets);
    return targets;
  }

  /**
   * Set the addresses of the target agents to which this Relay
   * should be sent.
   **/
  public void setTargets(Set targets) {
    myTargetSet = targets;
  }

  /**
   * Get an object representing the value of this Relay suitable
   * for transmission. This implementation uses itself to represent
   * its Content.
   **/
  public Object getContent() {
    return this;
  }

  protected boolean contentChanged(CommunityChangeNotificationAdapter newCCN) {
    return false;
  }

  private static final class SimpleRelayFactory
    implements TargetFactory, java.io.Serializable {

    public static final SimpleRelayFactory INSTANCE = new SimpleRelayFactory();

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
      CommunityChangeNotificationAdapter target = null;

      if (content instanceof CommunityChangeNotificationAdapter) {
        CommunityChangeNotificationAdapter ccn =
          (CommunityChangeNotificationAdapter) content;

        if (ccn.myTargetSet != null) {
          // intra-vm case so must clone
          try {
            target =
              (CommunityChangeNotificationAdapter) ((CommunityChangeNotificationAdapter) content).clone();

            // Relay.Target's should not have targets. Causes infinite loops
            if (target != null) {
              target.clearTargets();
            }

          } catch (CloneNotSupportedException cnse) {
            throw new IllegalArgumentException("content argument: " + content + " does not support clone.");
          }
        } else {
          target = ccn;
        }

      } else {
        throw new IllegalArgumentException("content argument must extend CommunityChangeNotificationAdapter.");
      }

      // Use arguments to customize the target.
      if (!uid.equals(target.getUID())) {
        throw new IllegalArgumentException("uid argument does not match source's UID.");
      }
      target.setSource(source);

      return target;
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
    return (contentChanged((CommunityChangeNotificationAdapter) content) ?
            Relay.CONTENT_CHANGE : Relay.NO_CHANGE);
  }

  protected Object clone() throws CloneNotSupportedException {
    CommunityChangeNotificationAdapter clone;

    clone = (CommunityChangeNotificationAdapter) super.clone();

    // Make sure we have a distinct target hash set
    clone.clearTargets();
    if (getTargets().size() > 0) {
      clone.addAllTargets(getTargets());
    }
    return clone;
  }
}
