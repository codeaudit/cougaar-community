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

import org.cougaar.core.util.UID;

/**
 * Request to search a community for Entities satisfying specified
 * criteria.
 */
public class SearchCommunity
    extends CommunityRequest implements java.io.Serializable {

  private String filter;
  private boolean recursive;
  private int qualifier;

  public SearchCommunity(String                    communityName,
                         String                    searchFilter,
                         boolean                   recursive,
                         int                       resultQualifier,
                         UID                       uid) {
    super(communityName, uid);
    this.filter = searchFilter;
    this.recursive = recursive;
    this.qualifier = resultQualifier;
  }

  public SearchCommunity(String                    communityName,
                         String                    searchFilter,
                         boolean                   recursive,
                         UID                       uid,
                         int                       resultQualifier) {
    this(communityName, searchFilter, recursive, resultQualifier, uid);
  }

  public boolean isRecursiveSearch() {
    return recursive;
  }

  public String getFilter() {
    return filter;
  }

  public int getQualifier() {
    return qualifier;
  }

}