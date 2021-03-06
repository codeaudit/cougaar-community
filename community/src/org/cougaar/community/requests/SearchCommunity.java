/*
 * <copyright>
 *  
 *  Copyright 1997-2004 Mobile Intelligence Corp
 *  under sponsorship of the Defense Advanced Research Projects
 *  Agency (DARPA).
 * 
 *  You can redistribute this software and/or modify it under the
 *  terms of the Cougaar Open Source License as published on the
 *  Cougaar Open Source Website (www.cougaar.org).
 * 
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 *  A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 *  OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 *  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 *  LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 *  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 *  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 *  OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *  
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