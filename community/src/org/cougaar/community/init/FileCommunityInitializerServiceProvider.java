/*
 * <copyright>
 *  Copyright 1997-2003 BBNT Solutions, LLC
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

import java.io.File;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;
import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.component.ServiceProvider;
import org.cougaar.util.ConfigFinder;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

class FileCommunityInitializerServiceProvider implements ServiceProvider {

  private static final String DEFAULT_FILE = "communities.xml";

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
        String entityName)
    {
      String file = System.getProperty("org.cougaar.community.configfile", DEFAULT_FILE);
      Collection ret;
      try {
        ret = getCommunityConfigsFromFile(file, entityName);
      } catch (RuntimeException ex) {
        System.out.println("Exception in getCommunityDescriptions from File: "+file);
        ret = new Vector();
      }
      return ret;
    }
  }

  /**
   * Get Collection of all CommunityConfig objects from XML file.  Uses
   * standard Cougaar config finder to locate XML file.
   */
  private static Collection getCommunityConfigsFromFile(String xmlFileName) {
    File communityFile = ConfigFinder.getInstance().locateFile(xmlFileName);
    if (communityFile != null) {
      Collection communityConfigs = loadCommunitiesFromFile(communityFile.getAbsolutePath());
      if (communityConfigs.isEmpty()) {
        System.out.println("Couldn't get community config info, retrying ...");
        communityConfigs = loadCommunitiesFromFile(communityFile.getAbsolutePath());
      }
      return communityConfigs;
    }
    else {
      System.err.println("Error: Could not find file '" +xmlFileName + "' on config path");
    }
    return new Vector();
  }

  private static Collection loadCommunitiesFromFile(String fname) {
    try {
      XMLReader xr = new org.apache.xerces.parsers.SAXParser();
      SaxHandler myHandler = new SaxHandler();
      xr.setContentHandler(myHandler);
      InputSource is = new InputSource(new FileReader(fname));
      xr.parse(is);
      return myHandler.getCommunityConfigs();
    } catch (Exception ex) {
      System.out.println("Exception parsing Community XML definition, " + ex);
      //System.out.println(getCommunityDescriptorText(fname));
    }
    return new Vector();
  }

  private static String getCommunityDescriptorText(String fname) {
    StringBuffer sb = new StringBuffer();
    try {
      FileReader in = new FileReader(fname);
      LineNumberReader lnr = new LineNumberReader(in);
      String line = null;
      while ((line = lnr.readLine()) != null) {
        sb.append(line + "\n");
      }
      lnr.close();
      in.close();
    } catch (Exception ex) {
      System.out.println("Exception reading community text from file, " + ex);
    }
    return sb.toString();
  }

  /**
   * Get Collection of CommunityConfig objects for named entity.  Uses
   * standard Cougaar config finder to locate XML file.
   */
  private static Collection getCommunityConfigsFromFile(String xmlFileName, String entityName) {
    Collection allCommunities = getCommunityConfigsFromFile(xmlFileName);
    Collection communitiesWithEntity = new Vector();
    for (Iterator it = allCommunities.iterator(); it.hasNext();) {
      CommunityConfig cc = (CommunityConfig)it.next();
      if (cc.hasEntity(entityName)) {
        communitiesWithEntity.add(cc);
      }
    }
    return communitiesWithEntity;
  }

  /**
   * For testing.  Loads CommunityConfigs from XML File or database
   * and prints to screen.
   * @param args
   */
  public static void main(String args[]) throws Exception {
    String entityName = "OSC";
    System.out.print(
        "<!-- load entity=\""+entityName+" -->");
    //
    FileCommunityInitializerServiceProvider me =
      new FileCommunityInitializerServiceProvider();
    CommunityInitializerService cis = (CommunityInitializerService)
      me.getService(null, null, CommunityInitializerService.class);
    Collection configs = cis.getCommunityDescriptions(entityName);
    //
    System.out.println("<Communities>");
    for (Iterator it = configs.iterator(); it.hasNext();) {
      System.out.println(((CommunityConfig)it.next()).toString());
    }
    System.out.println("</Communities>");
  }

  /**
   * SAX Handler for parsing Community XML files
   */
  private static class SaxHandler extends DefaultHandler {

    public SaxHandler () {}

    private Map communityMap = null;

    private CommunityConfig community = null;
    private EntityConfig entity = null;

    public void startDocument() {
      communityMap = new HashMap();
    }

    public Collection getCommunityConfigs() {
      return communityMap.values();
    }

    public void startElement(String uri, String localname, String rawname,
        Attributes p3) {
      try {
        if (localname.equals("Community")){
          String name = null;
          for (int i = 0; i < p3.getLength(); i++) {
            if (p3.getLocalName(i).equals("Name")) {
              name = p3.getValue(i).trim();
            }
          }
          community = new CommunityConfig(name);
        } else if (localname.equals("Entity")) {
          String name = null;
          for (int i = 0; i < p3.getLength(); i++) {
            if (p3.getLocalName(i).equals("Name")) {
              name = p3.getValue(i).trim();
            }
          }
          entity = new EntityConfig(name);
        } else if (localname.equals("Attribute")) {
          String id = null;
          String value = null;
          for (int i = 0; i < p3.getLength(); i++) {
            if (p3.getLocalName(i).equals("ID")) {
              id = p3.getValue(i).trim();
            } else if (p3.getLocalName(i).equals("Value")) {
              value = p3.getValue(i).trim();
            }
          }
          if (id != null && value != null) {
            if (entity != null)
              entity.addAttribute(id, value);
            else
              community.addAttribute(id, value);
          }
        }
      } catch (Exception ex ){
        ex.printStackTrace();
      }
    }

    public void endElement(String uri, String localname, String qname) {
      try {
        if (localname.equals("Community")){
          communityMap.put(community.getName(), community);
          community = null;
        } else if (localname.equals("Entity")){
          community.addEntity(entity);
          entity = null;
        }
      } catch (Exception ex ){
        ex.printStackTrace();
      }
    }

  }
}
