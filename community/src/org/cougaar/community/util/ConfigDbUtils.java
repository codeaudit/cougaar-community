/*
 * <copyright>
 *  Copyright 2001-2002 Mobile Intelligence Corp.
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
package org.cougaar.community.util;

import java.util.*;
import java.sql.*;
import java.io.*;

import org.cougaar.util.Parameters;
import org.cougaar.util.DBProperties;
import org.cougaar.util.DBConnectionPool;
import org.cougaar.util.log.Logger;
import org.cougaar.util.ConfigFinder;
import org.cougaar.tools.csmart.core.db.DBUtils;
import org.cougaar.tools.csmart.util.XMLUtils;
import org.cougaar.tools.csmart.ui.viewer.CSMART;

import org.w3c.dom.*;

/**
 * Utility methods for importing/exporting community definitions in
 * Configuration database.
 */
public class ConfigDbUtils {

  private static final String selectQFILE = "community.q";
  private static final String csmartQFILE = "CSMART.q";
  private static Logger log;

  static {
    log = CSMART.createLogger("org.cougaar.community.util.ConfigDbUtils");
  }

 /**
  * Get assemblyID of community associated with specified experiment
  * @param experimentName  Name of Experiment
  * @return                Assembly id of community associated with
  *                        named experiment
  */
 public static String getAssemblyID(String experimentName) {
    Map substitutions = new HashMap();
    String result = null;
    substitutions.put(":experimentName", experimentName);
    try{
      Connection conn = DBUtils.getConnection(selectQFILE);
      Statement st = conn.createStatement();
      String query = DBUtils.getQuery("queryAssemblyID", substitutions, selectQFILE);
      ResultSet rs = st.executeQuery(query);
      while(rs.next())
        result = rs.getString(1);
      rs.close();
      st.close();
      conn.close();
    }catch(Exception e){log.error(e.getMessage(), e.fillInStackTrace());}
    return result;
  }

  /**
   * Takes a File handle on the XML file to import, the Assembly ID to which
   * this data should be merged. Duplicate rows are ignored.
   * The method use .q file to directly update the database.
   * @param xmlFile    Name of xml file containing community descriptions
   * @param assemblyID Assembly ID of community descriptions to export
   * @return           True if operation was successful
   **/
  public static boolean importCommunityXML(File xmlFile, String assemblyID) {
    XMLUtils xml = new XMLUtils();
    Document doc;
    try{
      doc = xml.loadXMLFile(xmlFile);
    }catch(Exception e)
    {
      log.error("Error when parse xml file ");
      return false;
    }
    Hashtable calist = new Hashtable(); //table saves communities and their attributes
    Hashtable celist = new Hashtable(); //table saves communities and their entities
    NodeList communities = doc.getElementsByTagName("Community");
    for(int i=0; i<communities.getLength(); i++)
    {
      Node community = communities.item(i);
      String communityName = community.getAttributes().getNamedItem("Name").getNodeValue();
        List attrs = new ArrayList();
        Hashtable entities = new Hashtable();
      NodeList children = community.getChildNodes();
      for(int j=0; j<children.getLength(); j++)
      {
        Node child = children.item(j);
        if(child.getLocalName() == null)
          continue;
        if(child.getLocalName().equals("Attribute")) //attribute of community
        {
          Attribute attr = new Attribute(child.getAttributes().getNamedItem("ID").getNodeValue(),
            child.getAttributes().getNamedItem("Value").getNodeValue());
          attrs.add(attr);
        }
        if(child.getLocalName().equals("Entity"))
        {
          String entityName = child.getAttributes().getNamedItem("Name").getNodeValue();
          List eattrs = new ArrayList();
          NodeList entitychildren = child.getChildNodes();
          for(int m=0; m<entitychildren.getLength(); m++)
          {
            Node entitychild = entitychildren.item(m);
            if(entitychild.getLocalName() == null)
              continue;
            if(entitychild.getLocalName().equals("Attribute"))
            {
              Attribute attr = new Attribute(entitychild.getAttributes().getNamedItem("ID").getNodeValue(),
                entitychild.getAttributes().getNamedItem("Value").getNodeValue());
              eattrs.add(attr);
            }
          }
          entities.put(entityName, eattrs);
        }
      }
      if(attrs.size() > 0)  calist.put(communityName, attrs);
      if(entities.size() > 0) celist.put(communityName, entities);
    }
    boolean flag1 = importCommunityAttribute(calist, assemblyID);
    boolean flag2 = importCommunityEntity(celist, assemblyID);
    return flag1&&flag2;
  }

  /**
   * Takes the name of the XML File to create and write. Expect this "name" to include
   * full path information. It will usually be someplace under CIP/results.
   * Also takes the assemblyID in the database from which to retrieve community information.
   * @param xmlFileFullName  Path to xml file to create
   * @param assemblyID Assembly ID of community descriptions to export
   * @return           True if operation was successful
   **/
  public static boolean dumpCommunityXML(String xmlFileFullName, String assemblyID)
  {
    File file = new File(xmlFileFullName);
    try{
      if(file.exists())
      {
        file.delete();
        file.createNewFile();
      }
    }catch(IOException e)
    {
      log.error("try to overwrite the file " + xmlFileFullName);
      return false;
    }
    RandomAccessFile rfile = null;
    try{
      rfile = new RandomAccessFile(file, "rw");
    }catch(IOException e)
    {
      log.error("Invalid file " + xmlFileFullName);
      return false;
    }
    Connection conn = null;
    try{
      conn = DBUtils.getConnection(csmartQFILE);
    }catch(Exception e)
    {
      log.error("try to get connection to database.", e.fillInStackTrace());
      return false;
    }
    Map substitutions = new HashMap();
    String assemblyMatch = DBUtils.getListMatch(assemblyID);
    if (assemblyMatch != null)
      substitutions.put(":assembly_match:", assemblyMatch);
    Hashtable community_attrs = getDataFromCommunityTable(assemblyID, conn, substitutions);
    if(community_attrs.size() == 0)
      return false;
    Hashtable community_entities = getDataFromEntityTable(assemblyID, conn, substitutions);
    try{
      conn.close();
    }catch(SQLException e)
    {log.error("try to close connection to database", e.fillInStackTrace());
      return false;
    }
    return writeXmlFile(rfile, community_attrs, community_entities);
  }

  /**
   * Save all community information from table community_attribute with specified assemblyID in a hashtable.
   * @param assemblyID search community for this assemblyID
   * @param conn connection to the database
   * @param substitutions the map contains arguments need to fill in the query
   * @return a collection saves all data from table community_attribute
   */
  private static Hashtable getDataFromCommunityTable(String assemblyID, Connection conn, Map substitutions)
  {
    Hashtable communities = new Hashtable();
    try{
      Statement st = conn.createStatement();
      List communityNames = getAllCommunities(st, substitutions);
      if(communityNames.size() == 0)
        return new Hashtable();

      for(int i=0; i<communityNames.size(); i++)
      {
        List attrs = new ArrayList(); //save all attributes of one community
        String name = (String)communityNames.get(i);
        substitutions.put(":community_id", name);
        String query = DBUtils.getQuery("queryCommunityInfo", substitutions, csmartQFILE);
        ResultSet rs = st.executeQuery(query);
        while(rs.next())
          attrs.add(new Attribute(rs.getString(2), rs.getString(3)));
        rs.close();
        communities.put(name, attrs);
      }
      st.close();
    }catch(Exception e)
    {
      log.error("try to get data from table community_attribute.");
      return new Hashtable();
    }
    return communities;
  }

  /**
   * Save all entity information with given assemblyID from table community_entity_attribute
   * in a hashtable.
   * @param assemblyID search for community entities with this assemblyID
   * @param conn connection to the database
   * @param substitutions the map contains arguments need to fill in the query
   * @return a collection saves all data from table community_entity_attribute
   */
  private static Hashtable getDataFromEntityTable(String assemblyID, Connection conn, Map substitutions)
  {
    Hashtable communities = new Hashtable();
    try{
      Statement st = conn.createStatement();
      List communityNames = getAllCommunities(st, substitutions);

      for(int i=0; i<communityNames.size(); i++)
      {
        Hashtable entities = new Hashtable(); //save all entities
        String name = (String)communityNames.get(i);
        substitutions.put(":community_id", name);
        List entityNames = getAllEntities(name, st, substitutions); //all entity names

        for(int j=0; j<entityNames.size(); j++)
        {
          String entityName = (String)entityNames.get(j);
          substitutions.put(":entity_id", entityName);
          List attrs = new ArrayList(); //save attritures of this entity
          String query = DBUtils.getQuery("queryEntityInfo", substitutions, csmartQFILE);
          ResultSet rs = st.executeQuery(query);
          while(rs.next())
            attrs.add(new Attribute(rs.getString(2), rs.getString(3)));
          rs.close();
          entities.put(entityName, attrs);
        }
        communities.put(name, entities);
      }
      st.close();
    }catch(Exception e){log.error("try to get data from table community_entity_attribute.", e.fillInStackTrace());}
    return communities;
  }

  /**
   * Write community information fetched from the database into a xml file.
   * @param rfile the full path xml file to write
   * @param community_attrs a hashtable contains all communities and attributes.
   * @param community_entities a hashtable contains all communities and their entities.
   * @return a boolean value indicates if the export is succeed.
   */
  private static boolean writeXmlFile(RandomAccessFile rfile, Hashtable community_attrs, Hashtable community_entities)
  {
    try{
      rfile.write(version.getBytes());
      rfile.write(dtd.getBytes());
      rfile.write("<Communities>\n".getBytes());

      List communities = sortKeys(community_attrs);
      for(int i=0; i<communities.size(); i++)
      {
        String communityID = (String)communities.get(i);
        rfile.write(new String("  <Community Name=\"" + communityID + "\" >\n").getBytes());
        if(community_attrs.containsKey(communityID))
        {
          List attrs = (List)community_attrs.get(communityID);
          if(!writeAttributes(rfile, attrs, "    "))
            return false;
        }
        if(community_entities.containsKey(communityID))
        {
          Hashtable entities = (Hashtable)community_entities.get(communityID);
          List entityNames = sortKeys(entities);
          for(int j=0; j<entityNames.size(); j++)
          {
             String entityID = (String)entityNames.get(j);
             List entity_attrs = (List)entities.get(entityID);
             rfile.write(new String("    <Entity Name=\"" + entityID + "\" >\n").getBytes());
             if(!writeAttributes(rfile, entity_attrs, "      "))
               return false;
             rfile.write("    </Entity>\n".getBytes());
          }
        }
        rfile.write("  </Community>\n".getBytes());
      }

      rfile.write("</Communities>\n".getBytes());
      return true;
    }catch(IOException e)
    {log.error("try to dump to xml file.", e.fillInStackTrace());
     return false;
    }
  }

  /**
   * Write attributes of one community or entity into a file.
   * @param file  the export xml file
   * @param attrs a list of attributes
   * @param space
   * @return a boolean value indicates if the processing is succeed
   */
  private static boolean writeAttributes(RandomAccessFile file, List attrs, String space)
  {
    try{
      for(int i=0; i<attrs.size(); i++) //write attributes of the community
        {
          Attribute attr = (Attribute)attrs.get(i);
          file.write(new String(space + "<Attribute ID=\"" + attr.name + "\" Value=\"" +
            attr.value + "\" />\n").getBytes());
        }
    }catch(IOException e)
    {log.error("try to dump to xml file.", e.fillInStackTrace());
      return false;
    }
    return true;
  }

  /**
   * Sort keys of one hashtable. This method is called when writing data into a xml file,
   * both the community names and entity names are sorted.
   * @param a the hashtable
   * @return a sorted list contains all keys of hashtable.
   */
  private static List sortKeys(Hashtable a)
  {
    List keys = new ArrayList();
    for(Enumeration enums = a.keys(); enums.hasMoreElements();)
      keys.add((String)enums.nextElement());
    Collections.sort(keys);
    return keys;
  }

  /**
   * Get names of all communities of specified assemblyIDs.
   * @param st statement generated by connection to the database
   * @param substitutions the map contains arguments need to fill in the query
   * @return a list saves names of all fetched communities.
   */
  private static List getAllCommunities(Statement st, Map substitutions)
  {
    List communityNames = new ArrayList(); //save all community names
    try{
      String query = DBUtils.getQuery("queryMyCommunities", substitutions, csmartQFILE);
      ResultSet rs = st.executeQuery(query);
      while(rs.next())
        communityNames.add(rs.getString(1));
      rs.close();
    }catch(Exception e)
    {
      log.error("try to get all communities from table community_attribute");
      return new ArrayList();
    }
    if(communityNames.size() == 0)
    {
      log.error("Invalid assemblyID");
      return new ArrayList();
    }
    return communityNames;
  }

  /**
   * Get names of all entities of given assemblyID and communityID.
   * @param communityName the community need be searched to fetch entities
   * @param st statement generated by the conneciton to database
   * @param substitutions the map contains arguments need to fill in the query
   * @return a list of names of all entities of given community and assemblyID
   */
  private static List getAllEntities(String communityName, Statement st, Map substitutions)
  {
    List entityNames = new ArrayList(); //all entity names
    try{
      String query = DBUtils.getQuery("queryEntities", substitutions, csmartQFILE);
      ResultSet rs = st.executeQuery(query);
      while(rs.next()) //get all entities of this community
        entityNames.add(rs.getString(1));
      rs.close();
    }catch(Exception e)//{log.error("try to get all entities of community: " + communityName, e.fillInStackTrace());}
    {e.printStackTrace();}
    return entityNames;
  }

  /**
   * Import communities and their attributes into table community_attribute for specified assemblyID.
   * @param communities a hashtable contains communities and their attributes fetched from xml file
   * @param assemblyID save these communities for this assemblyID
   * @return a boolean value indicates if the processing is succeed
   */
  private static boolean importCommunityAttribute(Hashtable communities, String assemblyID)
  {
    Map substitutions = new HashMap();
    try{
      Connection conn = DBUtils.getConnection(selectQFILE);
      Statement st = conn.createStatement();
      for(Enumeration enums = communities.keys(); enums.hasMoreElements();)
      {
        String name = (String)enums.nextElement();
        List attrs = (List)communities.get(name);
        for(int i=0; i<attrs.size(); i++)
        {
          Attribute pair = (Attribute)attrs.get(i);
          substitutions.put(":assembly_id:", assemblyID);
          substitutions.put(":community_id", name);
          substitutions.put("CommunityType", pair.name);
          substitutions.put(":community_type", pair.value);
          String query = DBUtils.getQuery("queryCommunityInfo", substitutions, selectQFILE);
          ResultSet rs = st.executeQuery(query);
          if(rs.next())
            if(rs.getString(1).equals(assemblyID)) //it's a duplicate row, ignore it
            {
              rs.close();
              continue;
            }
          rs.close();
          query = DBUtils.getQuery("queryInsertCommunityInfo", substitutions, csmartQFILE);
          st.executeUpdate(query);
        }
      }
      st.close();
      conn.close();
    }catch(Exception e){
      log.error("try to import community attributes.", e.fillInStackTrace());
      return false;
    }
    return true;
  }

  /**
   * Import entities and their attributes into table community_entity_attribute for specified assemblyID.
   * @param communities a hashtable saves data of communities and entities from xml file.
   * @param assemblyID save these entities with this assemblyID
   * @return a boolean value indicates if the processing is succeed
   */
  private static boolean importCommunityEntity(Hashtable communities, String assemblyID)
  {
    Map substitutions = new HashMap();
    try{
      Connection conn = DBUtils.getConnection(selectQFILE);
      Statement st = conn.createStatement();
      for(Enumeration enums = communities.keys(); enums.hasMoreElements();)
      {
        String name = (String)enums.nextElement();
        Hashtable entities = (Hashtable)communities.get(name);
        for(Enumeration enum = entities.keys(); enum.hasMoreElements();)
        {
          String ename = (String)enum.nextElement();
          List attrs = (List)entities.get(ename);
          for(int i=0; i<attrs.size(); i++)
          {
            Attribute pair = (Attribute)attrs.get(i);
            substitutions.put(":assembly_id:", assemblyID);
            substitutions.put(":community_id", name);
            substitutions.put(":entity_id", ename);
            substitutions.put(":attribute_id", pair.name);
            substitutions.put(":attribute_value", pair.value);
            String query = DBUtils.getQuery("queryEntityInfo", substitutions, selectQFILE);
            ResultSet rs = st.executeQuery(query);
            if(rs.next())
              if(rs.getString(1).equals(assemblyID)) //it's a duplicate row, ignore it
              {
                rs.close();
                continue;
              }
            rs.close();
            query = DBUtils.getQuery("queryInsertEntityInfo", substitutions, csmartQFILE);
            st.executeUpdate(query);
          }
        }
      }
      st.close();
      conn.close();
    }catch(Exception e){
      log.error("try to import community entities.", e.fillInStackTrace());
      return false;
    }
    return true;
  }

  /**
   * Get assemblyID from the argument of command line. If the argument is assembly=xxx,
   * then assemblyID is xxx. if argument is experiment=xxx, then need to fetch the
   * assemblyID of this experiment.
   * @param args auguments fetched from the command line.
   * @return the assemblyID.
   */
  private static String getAssemblyIDFromArgument(String args)
  {
    String assemblyID = "";
    if(args.substring(0, args.indexOf("=")).equals("assembly"))
        assemblyID = args.substring(args.indexOf("=")+1, args.length());
    else if(args.substring(0, args.indexOf("=")).equals("experiment"))
        assemblyID = getAssemblyID(args.substring(args.indexOf("=")+1, args.length()));
    else
    {
        log.error("Invalid arguments. Type 'help' to get help.");
        System.exit(0);
    }
    return assemblyID;
  }

  /**
   * Clear all records with specified assemblyID from table community_attribute and community_entity_attribute.
   * This method is used when command line has argument db=replace.
   * @param assemblyID
   */
  private static void clearRecordsInCommunity(String assemblyID)
  {
    Map substitutions = new HashMap();
    String assemblyMatch = DBUtils.getListMatch(assemblyID);
    if (assemblyMatch != null)
      substitutions.put(":assembly_match:", assemblyMatch);
    try{
      Connection conn = DBUtils.getConnection(selectQFILE);
      Statement st = conn.createStatement();
      substitutions.put(":assembly_id:", assemblyID);
      List communities = new ArrayList();
      String query = DBUtils.getQuery("queryMyCommunities", substitutions, csmartQFILE);
      ResultSet rs = st.executeQuery(query);
      while(rs.next())
        communities.add(rs.getString(1));
      rs.close();
      for(int i=0; i<communities.size(); i++)
      {
        String name = (String)communities.get(i);
        substitutions.put(":community_id", name);
        query = DBUtils.getQuery("queryDeleteCommunityInfo", substitutions, csmartQFILE);
        st.executeUpdate(query);
        List entities = getAllEntities(name, st, substitutions);
        for(int j=0; j<entities.size(); j++)
        {
          substitutions.put(":entity_id", (String)entities.get(j));
          query = DBUtils.getQuery("queryDeleteEntityInfo", substitutions, csmartQFILE);
          st.executeUpdate(query);
        }
      }
      st.close();
      conn.close();
    }catch(Exception e){log.error("try to clear records in community tables. ", e.fillInStackTrace());}
  }

  public static void main(String[] args)
  {
    if(args.length == 0)
    {
      System.out.println("No parameters. Type '-h' to get help.");
      System.exit(0);
    }
    else if(args[0].equals("-h"))
    {
      System.out.println(
        "  help                                display this help and exit\n" +
        "  export file=XmlFileName assembly=assemblyID         create an xml file from community associated with assemblyID\n" +
        "  export file=XmlFileName experiment=experimentName   create an xml file from community associated with experiment\n" +
        "  import file=XmlFileName assembly=assemblyID db='merge'or'replace'\n" +
        "      insert the community defined in the xml file into database with given assemblyID\n" +
        "  import file=XmlFileName experiment=experimentName db='merge'or'replace'\n" +
        "      insert the community defined in the xml file into database with given assemblyID\n");
      System.exit(0);
    }
    else if(args[0].equals("import"))
    {
      if(args.length != 4)
      {
        log.error("Invalid arguments. Type 'help' to get help.");
        System.exit(0);
      }
      File xmlFile = ConfigFinder.getInstance().locateFile(args[1].substring(args[1].indexOf("=")+1, args[1].length()));
      String assemblyID = getAssemblyIDFromArgument(args[2]);
      if(args[3].substring(args[3].indexOf("=")+1, args[3].length()).equals("replace"))
        clearRecordsInCommunity(assemblyID);
      importCommunityXML(xmlFile, assemblyID);
    }
    else if(args[0].equals("export"))
    {
      if(args.length != 3)
      {
        log.error("Invalid arguments. Type 'help' to get help.");
        System.exit(0);
      }
      dumpCommunityXML(args[1].substring(args[1].indexOf("=")+1, args[1].length()), getAssemblyIDFromArgument(args[2]));
    }
    else
    {
      System.out.println("Invalid operation. Type 'help' for help.");
      System.exit(0);
    }
  }

  private static class Attribute
  {
    public String name;
    public String value;
    public Attribute(String name, String value)
    {
      this.name = name;
      this.value = value;
    }
  }

  private static final String dtd = "<!DOCTYPE Communities [\n" +
               "<!ELEMENT Communities (Community+)>\n" +
               "<!ELEMENT Community (Attribute+, Entity*)>\n" +
               "<!ATTLIST Community Name CDATA #REQUIRED>\n\n" +
               "<!ELEMENT Entity (Attribute*)>\n" +
               "<!ATTLIST Entity Name CDATA #REQUIRED>\n\n" +
               "<!ELEMENT Attribute EMPTY>\n" +
               "<!ATTLIST Attribute ID CDATA #REQUIRED>\n" +
               "<!ATTLIST Attribute Value CDATA #REQUIRED>\n" +
               "]>\n\n";
  private static final String version = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
}