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
import org.cougaar.core.service.NamingService;
import org.cougaar.core.util.PropertyNameValue;

import org.cougaar.core.service.community.CommunityMember;

import org.cougaar.core.mts.MessageAddress;
import org.cougaar.lib.web.arch.root.GlobalEntry;
import org.cougaar.core.mts.MTImpl;
import org.cougaar.core.node.NodeIdentifier;

/**
 * The servlet fetch naming service from blackboard and get all information of
 * yellow pages.
 */
public class YellowPagesServlet extends BaseServletComponent
{
  private NamingService ns;
  private String indexName = "Communities";

  /**
   * Hard-coded servlet path.
   */
  protected String getPath() {
    return "/yellowpages";
  }

  /**
   * Create the servlet.
   */
  protected Servlet createServlet() {
    // get the logging service
    ns = (NamingService)
      serviceBroker.getService(
          this,
          NamingService.class,
          null);
    if (ns == null) {
      throw new RuntimeException("no naming service?!");
    }

    return new MyServlet();
  }

  /**
   * Release the serlvet.
   */
  public void unload() {
    super.unload();
    // release the naming service
    if (ns != null) {
      serviceBroker.releaseService(
        this, ServletService.class, servletService);
      ns = null;
    }
  }

  private class MyServlet extends HttpServlet {
    public void doGet(
        HttpServletRequest req,
        HttpServletResponse res) throws IOException {
      /*res.setContentType("text/html");
      PrintWriter out = res.getWriter();
      out.print(
          "<html><body>\n"+
          "<h2>Yellow Page Information</h2><br>\n");*/
      Hashtable totalList = new Hashtable();
      ServletOutputStream outs = res.getOutputStream();
      ObjectOutputStream oout = new ObjectOutputStream(outs);
      try{
        InitialDirContext idc = ns.getRootContext();
        NamingEnumeration nes = idc.listBindings("");
        //out.print("<ul>");
        while(nes.hasMore())
        {
          NameClassPair ncp = (NameClassPair)nes.nextElement();
          //out.print("<ul>\n");
          String ncpN = ncp.getName();
          String ncpC = ncp.getClassName();

          if(ncpN.equalsIgnoreCase("Topology"))
          {
            Hashtable list = buildTopologyTable(idc);
            totalList.put("Topology", list);
          }

          else if(ncpN.equalsIgnoreCase("Webservers"))
          {
            Hashtable list = buildWebserverTable(idc);
            totalList.put("Webservers", list);
          }

          else if(!ncpN.equalsIgnoreCase(indexName))
          {
            DirContext dc = (DirContext)idc.lookup(ncp.getName());
            List sublist = new ArrayList();
            NamingEnumeration enum = dc.list("");
            while(enum.hasMore())
            {
              NameClassPair nvpair = (NameClassPair)enum.next();
              sublist.add(nvpair);
            }
            totalList.put(ncp.getName(), sublist);
          }
          //out.print("</ul>\n");

          else
          {
            Hashtable communities = buildCommunitiesTable(idc, indexName);
            totalList.put("Communities", communities);
          }
        }
        oout.writeObject(totalList);
        //out.print("</ul>\n");
      }catch(NamingException e){e.printStackTrace();}

      //out.print("</body></html>");
    }

    private Attributes getAttributes(DirContext context, String name)
    {
      Attributes attrs = null;
      try{
        attrs = context.getAttributes(name);
      }catch(NamingException e){e.printStackTrace();}
      return attrs;
    }

    private Hashtable buildCommunitiesTable(InitialDirContext idc, String index)
    {
      Hashtable list = new Hashtable();
      try{
        DirContext communitiesContext = (DirContext)idc.lookup(index);
        NamingEnumeration enum = communitiesContext.list("");
        while (enum.hasMore()) {
             NameClassPair ncPair = (NameClassPair)enum.next();
             List contents = new ArrayList();
             contents.add(getAttributes(communitiesContext, ncPair.getName())); //attributes of this community
             Hashtable entities = new Hashtable(); //records all entities of this community
             DirContext entityContext = (DirContext)communitiesContext.lookup(ncPair.getName());
             NamingEnumeration entityEnums = entityContext.list("");
             while(entityEnums.hasMore())
             {
               NameClassPair ncp = (NameClassPair)entityEnums.next();
               entities.put(ncp.getName(), getAttributes(entityContext, ncp.getName()));
             }
             contents.add(entities);
             list.put(ncPair.getName(), contents);
             /*Community o = (Community)communitiesContext.lookup(ncPair.getName());
             String cname = o.getName();
             Hashtable cContent = new Hashtable();
             PropertyNameValue[] atts = o.getAttributes();
             String[][] attributes = new String[atts.length][2];
             for(int i=0; i<atts.length; i++)
             {
               attributes[i][0] = atts[i].name;
               attributes[i][1] = (String)atts[i].value;
             }
             cContent.put("attribute", attributes);

             cContent.put("role", o.getRoles());

             cContent.put("listener", o.getListeners());

             Collection cms = o.getMembers();
             List communities = new ArrayList();
             Hashtable agents = new Hashtable();
             for(Iterator it = cms.iterator(); it.hasNext();)
             {
                CommunityMember member = (CommunityMember)it.next();
                String memberName = member.getName();
                if(member.isAgent())
                {
                   Collection agentRoles = member.getRoles();
                   agents.put(memberName, agentRoles);
                }
                else
                  communities.add(memberName);
             }
             cContent.put("member--agent", agents);
             cContent.put("member--community", communities);

             Hashtable sas = new Hashtable();
             CommunitySpokesagent[] css = o.getSpokesagents();
             for(int i=0; i<css.length; i++)
             {
               CommunitySpokesagent cs = css[i];
               String agent = cs.getIdentifier();
               Collection roles = cs.getRoleNames();
               sas.put(agent, roles);
             }
             cContent.put("spokesagent", sas);
             list.put(cname, cContent);*/
            }
      }catch(NamingException e){e.printStackTrace();}
      return list;
    }

    private Hashtable buildTopologyTable(InitialDirContext idc)
    {
      Hashtable table = new Hashtable();
      try{
        DirContext dc = (DirContext)idc.lookup("Topology");
        NamingEnumeration enum = dc.list("");
        while(enum.hasMore())
        {
          NameClassPair ncPair = (NameClassPair)enum.next();
          String name = ncPair.getName();
          /*MessageAddress o = (MessageAddress)dc.lookup(name);
          table.put(name, o.getAddress());*/
          if(ncPair.getClassName().equals("org.cougaar.core.node.NodeIdentifier"))
          {
            //NodeIdentifier o = (NodeIdentifier)dc.lookup(name);
            table.put(name + " Node", ncPair.getClassName());
          }
          else
            table.put(name, ncPair.getClassName());
        }
      }catch(NamingException e){e.printStackTrace();}
      return table;
    }

    private Hashtable buildWebserverTable(InitialDirContext idc)
    {
      Hashtable table = new Hashtable();
      try{
        DirContext dc = (DirContext)idc.lookup("WEBSERVERS");
        NamingEnumeration enum = dc.list("");
        while(enum.hasMore())
        {
          NameClassPair ncPair = (NameClassPair)enum.next();
          String name = ncPair.getName();
          GlobalEntry o = (GlobalEntry)dc.lookup(name);
          table.put(name, o);
        }
      }catch(NamingException e){e.printStackTrace();}
      return table;
    }

    private Hashtable buildAgentsTable(InitialDirContext idc)
    {
      Hashtable table = new Hashtable();
      try{
        DirContext dc = (DirContext)idc.lookup("Agents");
        NamingEnumeration enum = dc.list("");
        while(enum.hasMore())
        {
          NameClassPair ncPair = (NameClassPair)enum.next();
          String name = ncPair.getName();
          MTImpl o = (MTImpl)dc.lookup(name);
          try{
          table.put(name, o.getClientHost());
          }catch(java.rmi.server.ServerNotActiveException e){e.printStackTrace();}
        }
      }catch(NamingException e){e.printStackTrace();}
      return table;
    }
  }
}

