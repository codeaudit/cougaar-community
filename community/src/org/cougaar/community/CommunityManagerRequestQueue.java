package org.cougaar.community;

import org.cougaar.community.manager.CommunityManagerRequest;
import org.cougaar.community.requests.*;
import org.cougaar.core.util.UID;
import java.util.*;

/**
 * Manages local CommunityManagerRequests waiting to be sent or dispositioned by
 * a community manager.
 */
public class CommunityManagerRequestQueue {

  public static final int WAITING    = 0;
  public static final int SENT       = 1;
  public static final int COMPLETED  = 2;
  public static final int ALL        = 3;

  // Queue Entry
  static class Entry {
    CommunityManagerRequest cmr;
    RelayAdapter ra;
    CommunityRequest cr;  // original CommunityRequest from client
    int status = WAITING;
    long createTime;
    long timeout = 0;
    int attempts = 0;     // Number of times send has been attempted
    long ttl = -1;
    boolean listenerNotified = false;
    Entry(CommunityManagerRequest   cmr,
          CommunityRequest          cr,
          long                      timeout) {
      this.cmr = cmr;
      this.cr = cr;
      this.createTime = (new Date()).getTime();
      if (timeout >= 0)
        ttl = createTime + timeout;
    }
    public String toString() {
      return "CMRQueue.Entry: uid=" + cmr.getUID() +
             " type=" + cmr.getRequestTypeAsString() +
             " community=" + cmr.getCommunityName() +
             " entity=" + cmr.getEntity() +
             " status=" + statusAsString(this);
    }
  }

  // List of Entry objects
  private List cmrQueue = Collections.synchronizedList(new ArrayList());

  public CommunityManagerRequestQueue() {
  }

  /**
   * Adds a CommunityManagerRequest to queue if an equivalent one does not
   * already exist.
   * @param cmr  Request to add
   * @param cr   Original CommunityRequest from which CommunityManagerRequest
   *             was derived
   * @param timeout  Defines how long request should remain on queue in the
   *                 absence of a manager.
   * @return         Queue entry
   */
  public Entry add(CommunityManagerRequest cmr,
                   CommunityRequest cr,
                   long timeout) {
    Entry queueEntry = get(cmr);
    if (queueEntry == null) {
    queueEntry = new Entry(cmr, cr, timeout);
      cmrQueue.add(queueEntry);
    }
    return queueEntry;
  }

  /**
   *  Returns queue size.
   * @return Number of items in queue
   */
  public int size() {
    return cmrQueue.size();
  }

  /**
   * Removes entry from queue.
   * @param cmr  Removed Entry
   */
  public void remove(CommunityManagerRequest cmr) {
    Entry queueEntry = get(cmr);
    if (queueEntry != null) cmrQueue.remove(queueEntry);
  }

  /**
   * Remove entry from queue using its UID.
   * @param uid  UID associated with CommunityManagerRequest to be removed
   * @return  Removed Entry
   */
  public Entry remove(UID uid) {
    Entry queueEntry = get(uid);
    if (queueEntry != null) cmrQueue.remove(queueEntry);
    return queueEntry;
  }

  public Entry get(UID uid) {
    Entry e = null;
    synchronized (cmrQueue) {
      for (Iterator it = cmrQueue.iterator(); it.hasNext();) {
        Entry queueEntry = (Entry)it.next();
        if (queueEntry.cmr.getUID().equals(uid)) {
          e = queueEntry;
          break;
        }
      }
    }
    return e;
  }

  /**
   * Get Entry from queue.
   * @param cmr  CommunityManagerRequest associated with queue Entry
   * @return     Entry from queue
   */
  protected Entry get(CommunityManagerRequest cmr) {
    synchronized (cmrQueue) {
      for (Iterator it = cmrQueue.iterator(); it.hasNext();) {
        Entry queueEntry = (Entry)it.next();
        if (cmr.equals(queueEntry.cmr)) return queueEntry;
      }
      return null;
    }
  }

  /**
   * Returns true if queue contains an entry with a CommunityManagerRequest
   * equivalent to the one specified.
   * @param cmr  CommunityManagerRequest
   * @return   True if CommunityManagerRequest found on queue
   */
  public boolean contains(CommunityManagerRequest cmr) {
    return get(cmr) != null;
  }

  public boolean contains(String communityName, int requestType) {
    synchronized (cmrQueue) {
      for (Iterator it = cmrQueue.iterator(); it.hasNext();) {
        CommunityManagerRequest cmr = ((Entry)it.next()).cmr;
        if (cmr.getCommunityName().equals(communityName) &&
            cmr.getRequestType() == requestType) return true;
      }
    }
    return false;
  }

  /**
   * Get Entries on queue based on the specified type qualifier
   * (ALL, WAITING, SENT).
   * @param type  Selection qualifier
   * @return  All queue Entries matching type qualifier
   */
  public Entry[] get(int type) {
    List entries = new ArrayList();
    synchronized (cmrQueue) {
      for (Iterator it = cmrQueue.iterator(); it.hasNext();) {
        Entry queueEntry = (Entry)it.next();
        if (type == ALL || queueEntry.status == type)
          entries.add(queueEntry);
      }
      return (Entry[])entries.toArray(new Entry[0]);
    }
  }

  public static String statusAsString(Entry e) {
    switch (e.status) {
      case WAITING: return "WAITING";
      case SENT: return "SENT";
      case COMPLETED: return "COMPLETED";
      default: return "UNDEFINED";
    }
  }

  public String toString() {
    StringBuffer sb = new StringBuffer("[");
    synchronized (cmrQueue) {
      for (Iterator it = cmrQueue.iterator(); it.hasNext();) {
        Entry queueEntry = (Entry)it.next();
        sb.append("(" + queueEntry.cmr +
                  " targets=" + RelayAdapter.targetsToString(queueEntry.ra) +
                  " status=" + statusAsString(queueEntry) + ")");
        if (it.hasNext()) sb.append(",");
      }
      sb.append("]");
    }
    return sb.toString();
  }

}