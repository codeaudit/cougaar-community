/*
 * <copyright>
 *  Copyright 1997-2001 Mobile Intelligence Corp
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
package org.cougaar.community.test;

import java.util.*;
import java.net.URL;
import java.net.URLConnection;
import java.net.HttpURLConnection;
import java.io.*;
import javax.naming.directory.Attributes;

import org.apache.log4j.Category;

import org.cougaar.core.service.community.CommunityMember;
import org.cougaar.core.agent.ClusterIdentifier;

public class ServletConnection
{
   private static Category cat = Category.getInstance(ServletConnection.class);
   private static String host = "localhost";
   private static String port = "8800";

   protected static Vector getAgentFromServlet()
   {
    Vector clusterNames = new Vector();
    try{
     //generate a plain text, one agent name each line.
     URL url1 = new URL("http://" + host + ":" + port + "/agents?all&text");
     URLConnection connection = url1.openConnection();
     ((HttpURLConnection)connection).setRequestMethod("PUT");
     connection.setDoInput(true);
     connection.setDoOutput(true);
     InputStream is = connection.getInputStream();
     BufferedReader in = new BufferedReader(new InputStreamReader(is));
     String name = in.readLine();
     boolean flag = false;
     while(name != null)
     {
        if(name.indexOf("</ol>") != -1)
          break;
        if(flag)
        {
          name = name.substring(0, name.indexOf("</a>"));
          String cluster = name.substring(name.lastIndexOf(">")+1, name.length());
          clusterNames.add(cluster);
        }
        if(name.indexOf("<ol>") != -1)
          flag = true;
       name = in.readLine();
     }
    }catch(Exception o){o.printStackTrace();}
    return clusterNames;
  }

  protected static ObjectInputStream linkToTestServlet(Vector vs)
  {
    try{
     int i=0;
     InputStream is = null;
     while(is == null)
     {
       String agent = (String)getAgentFromServlet().get(i);
       try{
         URL url = new URL("http://" + host + ":" + port + "/$" + agent + "/communitytest");
         URLConnection connection = url.openConnection();
         //force the connection use doPut for data
         ((HttpURLConnection)connection).setRequestMethod("PUT");
         connection.setDoInput(true);
         connection.setDoOutput(true);
         OutputStream os = connection.getOutputStream();
         ObjectOutputStream oos = new ObjectOutputStream(os);
         oos.writeObject(vs);
         oos.close();
         is = connection.getInputStream();
       }catch(Exception e){is = null; i++;}
     }
       ObjectInputStream p = new ObjectInputStream(is);
       return p;
    }catch(Exception e)
    { cat.error("in getting data from servlet:", e.fillInStackTrace()); }
    return null;
  }

  protected static Vector getCommunities()
  {
    Vector vs = new Vector();
    vs.add("listAllCommunities");
    return getCollectionFromServlet(vs);
  }

  protected static Vector getListenersOfCommunity(String community)
  {
    Vector vs = new Vector();
    vs.add("getListeners");
    vs.add(community);
    return getCollectionFromServlet(vs);
  }

  protected static Vector getCommunityRoles(String community)
  {
    Vector vs = new Vector();
    vs.add("listCommunityRoles");
    vs.add(community);
    return getCollectionFromServlet(vs);
  }

  protected static Vector searchByRole(String community, String role)
  {
    Vector vs = new Vector();
    vs.add("searchByRole");
    vs.add(community);
    vs.add(role);
    return getCollectionFromServlet(vs);
  }

  protected static Vector search(String filter)
  {
    Vector vs = new Vector();
    vs.add("searchByCommunity");
    vs.add(filter);
    return getCollectionFromServlet(vs);
  }

  protected static Vector search(String community, String filter)
  {
    Vector vs = new Vector();
    vs.add("searchByEntity");
    vs.add(community);
    vs.add(filter);
    return getCollectionFromServlet(vs);
  }

 /* protected static Vector agentSearchByRole(String communityName, String roleName)
  {
    Vector vs = new Vector();
    vs.add("agentSearchByRole");
    vs.add(communityName);
    vs.add(roleName);
    return getCollectionFromServlet(vs);
  }

  protected static Vector agentSearch(String community, String filter)
  {
    Vector vs = new Vector();
    vs.add("agentSearch");
    vs.add(community);
    vs.add(filter);
    return getCollectionFromServlet(vs);
  }*/

  protected static Vector listParentCommunities(String member)
  {
    Vector vs = new Vector();
    vs.add("listParentCommunities");
    vs.add(member);
    return getCollectionFromServlet(vs);
  }

  protected static Vector listParentCommunities(String member, String filter)
  {
    Vector vs = new Vector();
    vs.add("listParentCommunities");
    vs.add(member);
    vs.add(filter);
    return getCollectionFromServlet(vs);
  }

  //list all agent members of given community
 /* protected static Vector listAgentMembers(String community)
  {
     Vector vs = new Vector();
     vs.add("getAgentMember");
     vs.add(community);
     return getVectorFromServlet(vs);
  }

  //list all community members of given community
  protected static Vector listCommunityMembers(String community)
  {
     Vector vs = new Vector();
     vs.add("getCommunityMember");
     vs.add(community);
     return getVectorFromServlet(vs);
  }*/

  //list all members of given community
  protected static Vector getMembersOfCommunity(String community)
  {
    Vector vs = new Vector();
    vs.add("getMembers");
    vs.add(community);
    return getCollectionFromServlet(vs);
  }

  protected static Attributes getCommunityAttributes(String communityName)
  {
    Vector vs = new Vector();
    vs.add("getCommunityAttributes");
    vs.add(communityName);
    return getAttributesFromServlet(vs);
  }

  protected static Attributes getEntityAttributes(String community, String entity)
  {
    Vector vs = new Vector();
    vs.add("getEntityAttributes");
    vs.add(community);
    vs.add(entity);
    return getAttributesFromServlet(vs);
  }

  protected static Vector getEntityRoles(String community, String entity)
  {
    Vector vs = new Vector();
    vs.add("getEntityRoles");
    vs.add(community);
    vs.add(entity);
    return getCollectionFromServlet(vs);
  }

  protected static boolean actionToAgent(String op, String what, String community, String agent)
  {
    Vector vs = new Vector();
    if(what.equals("Listener"))
    {
      if(op.equals("Add"))
        vs.add("addListener");
      else
        vs.add("removeListener");
      vs.add(agent);
      vs.add(community);
    }
    else if(op.equals("Remove"))
    {
      //if(what.equals("Member"))
        //vs.add("removeMember");
      //else
        vs.add("removeEntity");
      vs.add(community);
      vs.add(agent);
    }
    return getBooleanFromServlet(vs);
  }

  protected static boolean addMember(String parent, String member, int type, String[] roles)
  {
    Vector vs = new Vector();
    vs.add("addMember");
    vs.add(parent);
    vs.add(member);
    vs.add(Integer.toString(type));
    vs.add(roles);
    return getBooleanFromServlet(vs);
  }

  protected static boolean addRemoveRole(String command, String community, String entity, String role)
  {
    Vector vs = new Vector();
    if(command.equalsIgnoreCase("add"))
      vs.add("addRole");
    else
      vs.add("removeRole");
    vs.add(community);
    vs.add(entity);
    vs.add(role);
    return getBooleanFromServlet(vs);
  }

  protected static boolean createCommunity(String communityName, Attributes pairs)
  {
    Vector vs = new Vector();
    vs.add("createCommunity");
    vs.add(communityName);
    vs.add(pairs);
    return getBooleanFromServlet(vs);
  }

  protected static boolean setCommunityAttributes(String communityName, Attributes pairs)
  {
    Vector vs = new Vector();
    vs.add("setCommunityAttributes");
    vs.add(communityName);
    vs.add(pairs);
    return getBooleanFromServlet(vs);
  }

  protected static boolean setEntityAttributes(String community, String entity, Attributes attrs)
  {
    Vector vs = new Vector();
    vs.add("setEntityAttributes");
    vs.add(community);
    vs.add(entity);
    vs.add(attrs);
    return getBooleanFromServlet(vs);
  }

  protected static boolean addEntity(String community, String entityName, Attributes attrs)
  {
    Vector vs = new Vector();
    vs.add("addEntity");
    vs.add(community);
    vs.add(entityName);
    vs.add(attrs);
    return getBooleanFromServlet(vs);
  }

  /*protected static boolean addAgent(String community, String agent, Attributes attrs)
  {
    Vector vs = new Vector();
    vs.add("addAgent");
    vs.add(community);
    vs.add(agent);
    vs.add(attrs);
    return getBooleanFromServlet(vs);
  }*/

  protected static Vector getEntitiesOfCommunity(String communityName)
  {
    Vector vs = new Vector();
    vs.add("getEntitiesOfCommunity");
    vs.add(communityName);
    return getCollectionFromServlet(vs);
  }

  private static boolean getBooleanFromServlet(Vector vs)
  {
    Boolean value = null;
    ObjectInputStream in = linkToTestServlet(vs);
    try{
      try{
        while(true)
        { value = (Boolean)in.readObject(); }
      }catch(EOFException e){}
     }catch(IOException e){cat.error("try to get value from servlet: " + e, e.fillInStackTrace());}
     catch(Exception e){cat.error("try to get value from servlet: " + e, e.fillInStackTrace());}
    return value.booleanValue();
  }

  private static Vector getCollectionFromServlet(Vector vs)
  {
    Collection list = null;
    ObjectInputStream in = linkToTestServlet(vs);
    try{
      try{
        while(true)
        { list = (Collection)in.readObject(); }
      }catch(EOFException e){}
     }catch(IOException e){cat.error("try to "  + (String)vs.get(0) + " from servlet: " + e, e.fillInStackTrace());}
     catch(Exception e){cat.error("try to " + (String)vs.get(0) + " from servlet: " + e, e.fillInStackTrace());}
     Vector cs = new Vector();
     for(Iterator it=list.iterator(); it.hasNext();)
     {
       Object o = it.next();
       if(o instanceof ClusterIdentifier)
         cs.add(((ClusterIdentifier)o).getAddress());
       else
         cs.add((String)o);
     }
     return cs;
  }

  private static Attributes getAttributesFromServlet(Vector vs)
  {
    Attributes attrs = null;
    ObjectInputStream in = linkToTestServlet(vs);
    try{
      try{
        while(true)
        { attrs = (Attributes)in.readObject(); }
      }catch(EOFException e){}
    }catch(IOException e){cat.error("try to " + (String)vs.get(0) + " from servlet: " + e, e.fillInStackTrace());}
    catch(Exception e){cat.error("try to " + (String)vs.get(0) + " from servlet: " + e, e.fillInStackTrace());}
    return attrs;
  }

  private static Vector getVectorFromServlet(Vector vs)
  {
    Vector list = new Vector();
    ObjectInputStream in = linkToTestServlet(vs);
    try{
      try{
        while(true)
        { list = (Vector)in.readObject(); }
      }catch(EOFException e){}
     }catch(IOException e){cat.error("try to " + (String)vs.get(0) + " from servlet: " + e, e.fillInStackTrace());}
     catch(Exception e){cat.error("try to " + (String)vs.get(0) + " from servlet: " + e, e.fillInStackTrace());}
     return list;
  }
}
