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

import javax.servlet.*;
import javax.servlet.http.*;
import java.util.*;
import javax.naming.*;
import javax.naming.directory.*;
import java.io.*;

import org.cougaar.core.servlet.*;
import org.cougaar.util.UnaryPredicate;
import org.cougaar.core.servlet.BaseServletComponent;
import org.cougaar.core.servlet.ServletService;
import org.cougaar.core.service.community.CommunityService;

import org.apache.log4j.Category;

import org.cougaar.core.service.community.CommunityRoster;
import org.cougaar.core.util.PropertyNameValue;
import org.cougaar.core.service.community.CommunityMember;
import org.cougaar.core.agent.ClusterIdentifier;

public class CommunityTestServlet extends BaseServletComponent
{
   private static Category log = Category.getInstance(CommunityTestServlet.class);
   private CommunityService cs;

  /**
   * Hard-coded servlet path.
   */
  protected String getPath() {
    return "/communitytest";
  }

  /**
   * Create the servlet.
   */
  protected Servlet createServlet() {
    int counter = 0;
    /*
    cs = null;
    while (!serviceBroker.hasService(CommunityService.class)) {
      //if (++counter == 60) log.info("Waiting for CommunityService ... ");
      try { Thread.sleep(500); } catch (Exception ex) {}
    }
    cs = (CommunityService)serviceBroker.getService(this, CommunityService.class, null);
    if (cs == null) {
      throw new RuntimeException("no community service?!");
    }
    */

    return new MyServlet();
  }

  /**
   * Release the serlvet.
   */
  public void unload() {
    super.unload();
    // release the community service
    if (cs != null) {
      serviceBroker.releaseService(
        this, ServletService.class, servletService);
      cs = null;
    }
  }

  private class MyServlet extends HttpServlet {
    public void doGet( HttpServletRequest req, HttpServletResponse res) throws IOException {
       res.setContentType("text/html");
      PrintWriter out = res.getWriter();
      out.print(
          "<html><body>\n"+
          "<h2>Community Test</h2><br>\n");
      out.print("<p>community service: " + cs + ".</p>");
      out.print("<p>community size: " + cs.listAllCommunities().size() + "</p>");
      Collection list = cs.search("MiniTestConfig", "(Name=*)");
      out.print("<p>search result: <ul>");
      for(Iterator it = list.iterator(); it.hasNext();)
        out.print("<li>" + (String)it.next());
      out.print("</ul></p>");
      out.print("</body></html>");
    }

    public void doPut(HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException
    {
      int counter = 0;
      cs = null;
      while (!serviceBroker.hasService(CommunityService.class)) {
        if (++counter == 60) log.info("Waiting for CommunityService ... ");
        try { Thread.sleep(500); } catch (Exception ex) {}
      }
      cs = (CommunityService)serviceBroker.getService(this, CommunityService.class, null);

      ServletInputStream in = request.getInputStream();
      ObjectInputStream ois = new ObjectInputStream(in);
      ServletOutputStream outs = response.getOutputStream();
      ObjectOutputStream oout = new ObjectOutputStream(outs);
      Vector command = null;
      try
      {
        command = (Vector)ois.readObject();
      }catch(java.lang.ClassNotFoundException e){e.printStackTrace();}
      String op = (String)command.get(0);

      if(op.equals("getCommunityAttributes") || op.equals("getEntityAttributes"))
      {
        Attributes attrs = null;
        if(op.equals("getCommunityAttributes"))
          attrs = cs.getCommunityAttributes((String)command.get(1));
        else
          attrs = cs.getEntityAttributes((String)command.get(1), (String)command.get(2));
        try{
          oout.writeObject(attrs);
        }catch(java.util.NoSuchElementException e){log.error("try to "+ op + " in servlet: " + e, e.fillInStackTrace());}
        catch(java.lang.NullPointerException e){log.error("try to " + op + " in servlet: " + e, e.fillInStackTrace());}
      }

      else if(op.equals("listAllCommunities") || op.equals("listParentCommunities")
        || op.equals("getEntityRoles") || op.equals("listCommunityRoles") || op.equals("searchByRole")
        || op.equals("searchByCommunity") || op.equals("searchByEntity") || op.equals("agentSearchByRole")
        || op.equals("agentSearch"))
      {
        Collection list = null;
        if(op.equals("listAllCommunities"))
          list = cs.listAllCommunities();
        else if(op.equals("listParentCommunities"))
        {
          if(command.size() == 2)
            list = cs.listParentCommunities((String)command.get(1));
          else
            list = cs.listParentCommunities((String)command.get(1), (String)command.get(2));
        }
        else if(op.equals("getEntityRoles"))
          list = cs.getEntityRoles((String)command.get(1), (String)command.get(2));
        else if(op.equals("listCommunityRoles"))
          list = cs.getCommunityRoles((String)command.get(1));
        else if(op.equals("searchByRole"))
          list = cs.searchByRole((String)command.get(1), (String)command.get(2));
        else if(op.equals("searchByCommunity"))
          list = cs.search((String)command.get(1));
        else if(op.equals("searchByEntity"))
          list = cs.search((String)command.get(1), (String)command.get(2));
        //else if(op.equals("agentSearchByRole"))
        //  list = cs.agentSearchByRole((String)command.get(1), (String)command.get(2));
        //else if(op.equals("agentSearch"))
        //  list = cs.agentSearch((String)command.get(1), (String)command.get(2));
        try{
          oout.writeObject(list);
        }catch(java.util.NoSuchElementException e){log.error("try to "+ op + " in servlet: " + e, e.fillInStackTrace());}
        catch(java.lang.NullPointerException e){log.error("try to " + op + " in servlet: " + e, e.fillInStackTrace());}
      }

      else if(op.equals("getListeners"))
      {
        Collection list = cs.getListeners((String)command.get(1));
        Collection results = new Vector();
        for(Iterator it = list.iterator(); it.hasNext();)
        {
          ClusterIdentifier agent = (ClusterIdentifier)it.next();
          results.add(agent.getAddress());
        }
        try{
          oout.writeObject(results);
        }catch(java.util.NoSuchElementException e){log.error("try to "+ op + " in servlet: " + e, e.fillInStackTrace());}
        catch(java.lang.NullPointerException e){log.error("try to " + op + " in servlet: " + e, e.fillInStackTrace());}
      }

      else if(op.equals("setCommunityAttributes") || op.equals("setEntityAttributes") || op.equals("addEntity")
        || op.equals("addAgent"))
      {
        boolean value = false;
        if(op.equals("setCommunityAttributes"))
          value = cs.setCommunityAttributes((String)command.get(1), (Attributes)command.get(2));
        else if(op.equals("setEntityAttributes"))
          value = cs.setEntityAttributes((String)command.get(1), (String)command.get(2), (Attributes)command.get(3));
        else if(op.equals("addEntity"))
          value = cs.addToCommunity((String)command.get(1), new ClusterIdentifier((String)command.get(2)), (String)command.get(2), (Attributes)command.get(3));
        //else if(op.equals("addAgent"))
        //  value = cs.addToCommunity((String)command.get(1), new ClusterIdentifier((String)command.get(2)), (Attributes)command.get(3));
        try{
          oout.writeObject(Boolean.valueOf(value));
        }catch(java.util.NoSuchElementException e){log.error("try to " + op + " in servlet: " + e, e.fillInStackTrace());}
        catch(java.lang.NullPointerException e){log.error("try to " + op + " in servlet: " + e, e.fillInStackTrace());}
      }

    /*  else if(op.equals("getAgentMember"))
      {
        CommunityRoster cr = cs.getRoster((String)command.get(1));
        String[] agents = cr.getMemberAgents();
        Vector vs = new Vector();
        for(int i=0; i<agents.length; i++)
          vs.add(agents[i]);
        try{
          oout.writeObject(vs);
        }catch(java.util.NoSuchElementException e){log.error("try to write agent member in servlet: " + e, e.fillInStackTrace());}
        catch(java.lang.NullPointerException e){log.error("try to write agent member in servlet: " + e, e.fillInStackTrace());}
      }
      else if(op.equals("getCommunityMember"))
      {
        CommunityRoster cr = cs.getRoster((String)command.get(1));
        String[] communities = cr.getMemberCommunities();
        Vector vs = new Vector();
        for(int i=0; i<communities.length; i++)
          vs.add(communities[i]);
        try{
          oout.writeObject(vs);
        }catch(java.util.NoSuchElementException e){log.error("try to write community member in servlet: " + e, e.fillInStackTrace());}
        catch(java.lang.NullPointerException e){log.error("try to write community member in servlet: " + e, e.fillInStackTrace());}
      }*/
      else if(op.equals("getMembers"))
      {
        Collection list = cs.searchByRole((String)command.get(1), "Member");
        try{
          oout.writeObject(list);
        }catch(java.util.NoSuchElementException e){log.error("try to "+ op + " in servlet: " + e, e.fillInStackTrace());}
        catch(java.lang.NullPointerException e){log.error("try to " + op + " in servlet: " + e, e.fillInStackTrace());}
      }
      else if(op.equals("addListener") || op.equals("removeListener") || op.equals("removeEntity")
        || op.equals("addRole") || op.equals("removeRole"))
      {
        boolean value = false;
        System.out.println(op);
        if(op.equals("addListener"))
          value = cs.addListener(new ClusterIdentifier((String)command.get(1)), (String)command.get(2));
        else if(op.equals("removeListener"))
          value = cs.removeListener(new ClusterIdentifier((String)command.get(1)), (String)command.get(2));
        //else if(op.equals("addMember"))
          //value = cs.addMember((String)command.get(1), (String)command.get(2), Integer.parseInt((String)command.get(3)), (String[])command.get(4));
        //else if(op.equals("removeMember"))
          //value = cs.removeMember((String)command.get(1), (String)command.get(2));
        else if(op.equals("removeEntity"))
          value = cs.removeFromCommunity((String)command.get(1), (String)command.get(2));
        else if(op.equals("addRole"))
          value = cs.addRole((String)command.get(1), (String)command.get(2), (String)command.get(3));
        else if(op.equals("removeRole"))
          value = cs.removeRole((String)command.get(1), (String)command.get(2), (String)command.get(3));
        try{
          oout.writeObject(Boolean.valueOf(value));
        }catch(java.util.NoSuchElementException e){log.error("try to " + op + " in servlet: " + e, e.fillInStackTrace());}
        catch(java.lang.NullPointerException e){log.error("try to " + op + " in servlet: " + e, e.fillInStackTrace());}
      }

      else if(op.equals("createCommunity"))
      {
        Attributes pairs = (Attributes)command.get(2);
        boolean value = cs.createCommunity((String)command.get(1), pairs);
        try{
          oout.writeObject(Boolean.valueOf(value));
        }catch(java.util.NoSuchElementException e){log.error("try to create community in servlet: " + e, e.fillInStackTrace());}
        catch(java.lang.NullPointerException e){log.error("try to create community in servlet: " + e, e.fillInStackTrace());}
      }

      else if(op.equals("getEntitiesOfCommunity"))
      {
        Collection list = cs.search((String)command.get(1), "(Name=*)");
        try{
          oout.writeObject(list);
        }catch(java.util.NoSuchElementException e){log.error("try to "+ op + " in servlet: " + e, e.fillInStackTrace());}
        catch(java.lang.NullPointerException e){log.error("try to " + op + " in servlet: " + e, e.fillInStackTrace());}
      }
    }
  }
}
