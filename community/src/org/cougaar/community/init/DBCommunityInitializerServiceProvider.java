/*
 * <copyright>
 *  Copyright 1997-2001 BBNT Solutions, LLC
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

import java.io.IOException;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.Connection;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;
import java.util.Collection;
import java.util.Collections;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import org.cougaar.util.DBProperties;
import org.cougaar.util.DBConnectionPool;
import org.cougaar.util.Parameters;
import org.cougaar.util.log.Logger;
import org.cougaar.util.log.Logging;
import org.cougaar.core.component.*;
import org.cougaar.core.node.*;
import org.cougaar.core.mts.MessageAddress;
import org.cougaar.planning.plugin.asset.AssetDataReader;
import org.cougaar.planning.plugin.asset.AssetDataDBReader;


/**
 * Community config from database.
 **/
class DBCommunityInitializerServiceProvider implements ServiceProvider {

  private final DBInitializerService dbInit;
  private final Logger logger;

  public DBCommunityInitializerServiceProvider(DBInitializerService dbInit) {
    this.dbInit = dbInit;
    this.logger = Logging.getLogger(getClass());
  }

  public Object getService(ServiceBroker sb, Object requestor, Class serviceClass) {
    if (serviceClass != CommunityInitializerService.class) {
      throw new IllegalArgumentException(
          getClass() + " does not furnish " + serviceClass);
    }
    return new CommunityInitializerServiceImpl();
  }

  public void releaseService(ServiceBroker sb, Object requestor,
      Class serviceClass, Object service)
  {
  }

  private class CommunityInitializerServiceImpl implements CommunityInitializerService {

    public Collection getCommunityDescriptions(
        String entityName,
        String empty) // bogus param?
    {
      Collection ret = new Vector();
      Map substitutions = null;
      String query1 = null;
      String query2 = null;
      try {
        substitutions = dbInit.createSubstitutions();
        Connection conn = null;
        try {
          query1 = dbInit.getQuery("queryCommunityEntityAttributes", substitutions);
          query2 = dbInit.getQuery("queryCommunityAttributes", substitutions);
          conn = dbInit.getConnection();
          ret = getParentCommunities(conn, entityName, query1, query2);
        } finally {
          // Must close the connection when done
          if (conn != null) {
            conn.close();
          }
        }
      } catch (Exception ex) {
        if (logger.isErrorEnabled()) {
          logger.error(
              "Exception in getCommunityDescriptions from DB ("+
              "entityName=\""+entityName+
              "\", subs=\""+substitutions+
              "\", query1=\""+query1+
              "\", query2=\""+query2+
              "\")",
              ex);
        }
      }
      return ret;
    }
  }

  //
  // Community configuration utilities:
  //
  // These are static for now, but could be promoted to non-static
  // methods...
  //

  private static Attributes getCommunityAttributes(
      Connection conn, String communityName, String query2)
    throws SQLException {
      Statement s = conn.createStatement();
      ResultSet rs = s.executeQuery(query2);
      //ResultSet rs = s.executeQuery("select * from community_attribute");
      javax.naming.directory.Attributes attrs = new BasicAttributes();
      while(rs.next()) {
        if (rs.getString(1).equals(communityName)) {
          String attrId = rs.getString(2);
          String attrValue = rs.getString(3);
          Attribute attr = attrs.get(attrId);
          if (attr == null) {
            attr = new BasicAttribute(attrId);
            attrs.put(attr);
          }
          if (!attr.contains(attrValue)) attr.add(attrValue);
        }
      }

      // Close the result set and the statement
      try {
        rs.close();
      } catch (SQLException e) {}
      try {
        s.close();
      } catch (SQLException e) {}

      return attrs;
    }

  private static void addEntityAttribute(
      Map configMap, String communityName, String entityName,
      String attrId, String attrValue) {
    CommunityConfig cc = (CommunityConfig)configMap.get(communityName);
    EntityConfig entity = cc.getEntity(entityName);
    if (entity == null) {
      entity = new EntityConfig(entityName);
      cc.addEntity(entity);
    }
    entity.addAttribute(attrId, attrValue);
  }

  private static Collection getParentCommunities(
      Connection conn, String entityName, String query1, String query2)
    throws SQLException {

      Statement s = conn.createStatement();
      ResultSet rs = s.executeQuery(query1);
      //ResultSet rs = s.executeQuery("select * from community_entity_attribute");
      Map configMap = new HashMap();

      while(rs.next()) {
        if (rs.getString(2).equals(entityName)) {
          String communityName = rs.getString(1);
          if (!configMap.containsKey(communityName)) {
            CommunityConfig cc = new CommunityConfig(communityName);
            cc.setAttributes(getCommunityAttributes(conn, communityName, query2));
            configMap.put(communityName, cc);
          }
          addEntityAttribute(configMap, communityName, entityName, rs.getString(3), rs.getString(4));
        }
      }

      // Close the result set and the statement
      try {
        rs.close();
      } catch (SQLException e) {}
      try {
        s.close();
      } catch (SQLException e) {}

      return configMap.values();
    }

  /**
   * For testing.
   */
  public static void main(String args[]) throws Exception {
    String trialId = System.getProperty("org.cougaar.experiment.id");
    if (trialId == null) {
      System.err.println(
          "Must specify a trial id (using \"-Dorg.cougaar.experiment.id\")");
      return;
    }
    String nodeName = System.getProperty("org.cougaar.node.name");
    String entityName = (args.length < 1 ? "OSC" : args[0]);
    System.out.println(
        "<!-- load trial=\""+trialId+
        "\" node=\""+nodeName+
        "\" entity=\""+entityName+"\" -->");

    // load the db-init-service
    Object requestor = new Object();
    final ServiceBroker sb = new ServiceBrokerSupport();
    if (nodeName != null) {
      MessageAddress nodeId =
        MessageAddress.getMessageAddress(nodeName);
      NodeIdentificationServiceProvider nodeIdSP =
        new NodeIdentificationServiceProvider(nodeId);
      sb.addService(NodeIdentificationService.class, nodeIdSP);
    }
    BindingSite bs = new BindingSite(){
      public ServiceBroker getServiceBroker() { return sb; }
      public void requestStop() { }
    };
    DBInitializerServiceComponent dbisc = 
      new DBInitializerServiceComponent();
    BindingUtility.activate(dbisc, bs, sb);
    DBInitializerService dbInit = (DBInitializerService)
      sb.getService(requestor, DBInitializerService.class, null);

    // load my db community-init-service
    DBCommunityInitializerServiceProvider commInitSP = 
      new DBCommunityInitializerServiceProvider(dbInit);
    sb.addService(CommunityInitializerService.class, commInitSP);

    // get the service
    CommunityInitializerService cis = (CommunityInitializerService)
      sb.getService(requestor, CommunityInitializerService.class, null);

    // print the communities
    Collection configs = cis.getCommunityDescriptions(entityName, "");
    System.out.println("<Communities>");
    for (Iterator it = configs.iterator(); it.hasNext();) {
      System.out.println(((CommunityConfig)it.next()).toString());
    }
    System.out.println("</Communities>");
  }
}
