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

package org.cougaar.community.requests;

import javax.naming.directory.ModificationItem;

import org.cougaar.core.util.UID;

/**
 * Request to modify the attributes of a Community or Entity.
 */
public class ModifyAttributes
    extends CommunityRequest implements java.io.Serializable {

  private String entityName;
  private ModificationItem mods[];

  /**
   * Modifies the attributes associated with an entity.  The modified attributes
   * are applied to the entity in the specified community unless the entity is
   * null in which case the attribute modifications are applied to the community.
   * @param communityName  Name of affected community
   * @param entityName     Name of entity, if null the attributes are applied
   *                       to the community
   * @param mods           Attribute modifications
   * @param uid            Unique identifier
   */
  public ModifyAttributes(String                    communityName,
                          String                    entityName,
                          ModificationItem[]        mods,
                          UID                       uid) {
    super(communityName, uid);
    this.entityName = entityName;
    this.mods = mods;
  }

  public String getEntityName() {
    return entityName;
  }

  public ModificationItem[] getModifications() {
    return mods;
  }

}
