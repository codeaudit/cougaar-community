package org.cougaar.community.util;

import javax.servlet.*;
import javax.servlet.http.*;
import java.util.*;
import java.io.*;

import org.cougaar.core.servlet.BaseServletComponent;
import org.cougaar.core.service.ServletService;
import org.cougaar.core.service.LoggingService;
import org.cougaar.core.service.BlackboardService;
import org.cougaar.core.service.AgentIdentificationService;
import org.cougaar.core.servlet.ServletUtil;
import org.cougaar.core.blackboard.IncrementalSubscription;
import org.cougaar.core.blackboard.BlackboardClient;
import org.cougaar.util.ConfigFinder;
import org.cougaar.util.UnaryPredicate;

import org.cougaar.community.CommunityImpl;
import org.cougaar.core.service.community.CommunityResponseListener;
import org.cougaar.core.service.community.CommunityResponse;
import org.cougaar.core.service.community.CommunityService;
import org.cougaar.core.service.community.Community;
import org.cougaar.core.service.community.Entity;

import javax.xml.transform.*;
import javax.xml.transform.stream.*;

import EDU.oswego.cs.dl.util.concurrent.Semaphore;

/**
 * An optional servlet for viewing information of communities.
 * Load into any agent:
 *   plugin = org.cougaar.community.util.CommunityViewerServlet
 */
public class CommunityViewerServlet extends BaseServletComponent implements BlackboardClient{

  private CommunityService cs;
  private static LoggingService log;
  private BlackboardService blackboard;
  private PrintWriter out;
  private String agentId;

  /**
   * Hard-coded servlet path.
   */
  protected String getPath() {
    return "/communityViewer";
  }

  public void setCommunityService(CommunityService cs){
    this.cs = cs;
  }

  public void setBlackboardService(BlackboardService bb){
    this.blackboard = bb;
  }

  /**
   * Create the servlet.
   */
  protected Servlet createServlet() {
    log =  (LoggingService) serviceBroker.getService(this, LoggingService.class, null);
    blackboard = (BlackboardService)serviceBroker.getService(this, BlackboardService.class, null);
    AgentIdentificationService ais = (AgentIdentificationService)serviceBroker.getService(
        this, AgentIdentificationService.class, null);
    if (ais != null) {
      this.agentId = ais.getMessageAddress().toString();
      serviceBroker.releaseService(this, AgentIdentificationService.class, ais);
    }
    return new MyServlet();
  }

  private class MyServlet extends HttpServlet {
    public void doGet(
        HttpServletRequest req,
        HttpServletResponse res) throws IOException {
        out = res.getWriter();
        parseParams(req);
    }
  }

  private String format = "", currentXML = "", communityShown;
  private String command = "", target = "";
  private void parseParams(HttpServletRequest request) throws IOException
    {
      format = "html";
      command = "";
      target = "";
      // create a URL parameter visitor
      ServletUtil.ParamVisitor vis =
        new ServletUtil.ParamVisitor() {
          public void setParam(String name, String value) {
            if(name.equalsIgnoreCase("format"))
              format = value;
            if(name.equalsIgnoreCase("community")) {
              command = "showCommunity";
              target = value;
              communityShown = value;
            }
            if(name.equals("attributes")) {
              command = "showAttributes";
              target = value;
            }
          }
        };
      // visit the URL parameters
      ServletUtil.parseParams(vis, request);
      if(command.equals(""))
        showFrontPage();
      else {
        displayParams(command, target);
      }
    }

  //The first page when user call this servlet will show all communities who are
  //direct parents of calling agent.
  private void showFrontPage() {
    List comms = new ArrayList();
    Collection communityDescriptors = null;
    try{
      blackboard.openTransaction();
      communityDescriptors = blackboard.query(communityPredicate);
    }finally{blackboard.closeTransactionDontReset();}
    for(Iterator it = communityDescriptors.iterator(); it.hasNext();) {
      Community community = (Community)it.next();
      if(community.hasEntity(agentId))
        if(!comms.contains(community.getName()))
          comms.add(community.getName());
    }
    out.print("<html><title>communityViewer</title>\n");
    out.print("<body>\n<ol>");
    for(int i=0; i<comms.size(); i++){
      out.print("<li><a href=./communityViewer?community=" + (String)comms.get(i) + ">" + (String)comms.get(i) + "</a>\n");
    }
    out.print("</body>\n</html>\n");
  }

  private static Hashtable table = new Hashtable();
  private void displayParams(String command, String value){
    try{
      Community community = null;
      final Semaphore s = new Semaphore(0);
      cs.getCommunity(communityShown, -1, new CommunityResponseListener(){
        public void getResponse(CommunityResponse resp){
          communityChangeNotification((Community)resp.getContent());
          s.release();
        }
      });
      try{
        s.acquire();
      }catch(InterruptedException e){}
      community = (Community)table.get(communityShown);
      currentXML = community.toXml();
      if(format.equals("xml"))
        out.print(convertSignals(currentXML));
      else {
        if(command.equals("showCommunity"))
          out.print(getHTMLFromXML(currentXML, communityViewer));
        else {
          String xml = "";
          if(value.equals(communityShown)) {
            //make sure this element do have attributes
            int temp1 = currentXML.indexOf("<Attributes>");
            int temp2 = currentXML.indexOf("<", currentXML.indexOf("Community"));
            if(temp1 == temp2){
              xml = currentXML.substring(0, currentXML.indexOf("</Attributes>"));
              xml += "</Attributes></Community>";
            }
            else {
              xml = "<Community Name=\"" + communityShown + "\"></Community>";
            }
          }else {
            String temp = "name=\"" + value + "\"";
            int index = currentXML.indexOf(temp);
            int firstIndex = currentXML.substring(0, index).lastIndexOf("<");
            int temp1 = currentXML.indexOf("<Attributes", firstIndex);
            int temp2 = currentXML.indexOf("<", firstIndex+1);
            if(temp1 == temp2){
              index = currentXML.indexOf("</Attributes>", firstIndex);
              int lastIndex = currentXML.indexOf(">", index+13);
              xml = currentXML.substring(firstIndex, lastIndex+1);
            }else {
              index = currentXML.indexOf("</", firstIndex);
              int lastIndex = currentXML.indexOf(">", index);
              xml = currentXML.substring(firstIndex, lastIndex+1);
            }
          }
          out.print(getHTMLFromXML(xml, attributesViewer));
        }
      }
    }catch(Exception e){e.printStackTrace();}
  }

  public static void communityChangeNotification(Community community){
    if(table.containsKey(community.getName())){
      table.remove(community.getName());
      table.put(community.getName(), community);
    }else {
      table.put(community.getName(), community);
    }
  }

  /**
   * To show raw xml in a html page, convert several specific signals.
   * @param xml the given xml string
   * @return converted xml
   */
  private String convertSignals(String xml)
  {
    String tmp1 = xml.replaceAll("<", "&lt;");
    String tmp2 = tmp1.replaceAll(">", "&gt;");
    String tmp3 = tmp2.replaceAll("\n", "<br>");
    String tmp4 = tmp3.replaceAll(" ", "&nbsp;");
    return tmp4;
  }

  /**
   * Using xsl file to transform a xml file into html file.
   * @param xml given xml string
   * @param xsl name of xsl file
   * @return the html string
   */
  private String getHTMLFromXML(String xml, String xsl)
  {
    String html = "";
    try{
      TransformerFactory tFactory = TransformerFactory.newInstance();
      //File xslf = ConfigFinder.getInstance().locateFile(xsl);
      //xsl = "/configs/common/" + xsl;
      //InputStream in = CommunityViewerServlet.class.getResourceAsStream(xsl);
      //Transformer transformer = tFactory.newTransformer(new StreamSource(in));
      Transformer transformer = tFactory.newTransformer(new StreamSource(new StringReader(xsl)));
      StringWriter writer = new StringWriter();
      transformer.transform(new StreamSource(new StringReader(xml)), new StreamResult(writer));
      html = writer.toString();
    }catch(Exception e){log.error(e.getMessage());}
    return html;
  }

  /**
   * Selects CommunityDescriptors that are sent by remote community manager
   * agent.
   */
  private UnaryPredicate communityPredicate = new UnaryPredicate() {
    public boolean execute (Object o) {
      return (o instanceof Community);
  }};

  // BlackboardClient method:
  public String getBlackboardClientName() {
		return toString();
  }

  // unused BlackboardClient method:
  public long currentTimeMillis() {
    return new Date().getTime();
  }

  // unused BlackboardClient method:
  public boolean triggerEvent(Object event) {
    return false;
  }


  public static void main(String[] args){
    String xml = "<Community name=\"MiniTestConfig\"><Attributes><Attribute id=\"CommunityType\">" +
      "<Value>Test</Value><Value>Domain</Value></Attribute></Attributes></Community>";
      //"<Community name=\"NestedCommunity\"></Community><Agent name=\"3-69-ARBN\"></Agent>" +
      //"<Agent name=\"3ID\"></Agent></Community>";
    try{
      TransformerFactory tFactory = TransformerFactory.newInstance();
      //File xslf = new File("examples/configs/common/attributesViewer.xsl");
      Transformer transformer = tFactory.newTransformer(new StreamSource(new StringReader(attributesViewer)));
      StringWriter writer = new StringWriter();
      transformer.transform(new StreamSource(new StringReader(xml)), new StreamResult(writer));
      String html = writer.toString();
      System.out.println(html);
    }catch(Exception e){e.printStackTrace();}
  }

  private static final String attributesViewer =
    "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"1.0\">\n" +
    "<xsl:output method=\"html\" indent=\"yes\"/>\n" +
    "<xsl:template match=\"/\">\n" +
    "<xsl:variable name=\"community\">" +
    "<xsl:value-of select=\"//Community/@name\" /> " +
    "</xsl:variable>" +
    "<xsl:variable name=\"agent\">" +
    "<xsl:value-of select=\"//Agent/@name\" />" +
    "</xsl:variable>" +
    "<xsl:variable name=\"communityLength\">" +
    "<xsl:value-of select=\"string-length($community)\" />" +
    "</xsl:variable>" +
    "<xsl:variable name=\"agentLength\">" +
    "<xsl:value-of select=\"string-length($agent)\" />" +
    "</xsl:variable>" +
    "<html><head><title><xsl:choose>" +
    "<xsl:when test=\"$communityLength > 0\">" +
    "<xsl:text>Community </xsl:text>" +
    "<xsl:value-of select=\"$community\" />" +
    "</xsl:when><xsl:otherwise>" +
    "<xsl:text>Agent </xsl:text><xsl:value-of select=\"$agent\" />" +
    "</xsl:otherwise></xsl:choose></title></head>" +
    "<body><center><br /><H1><xsl:choose>" +
    "<xsl:when test=\"$communityLength > 0\">" +
    "<xsl:text>Community </xsl:text><xsl:value-of select=\"$community\" />" +
    "</xsl:when><xsl:otherwise><xsl:text>Agent </xsl:text>" +
    "<xsl:value-of select=\"$agent\" /></xsl:otherwise></xsl:choose></H1><br />" +
    "<table border=\"1\" cellpadding=\"10\" cellspacing=\"0\">" +
    "<th><xsl:text>Attribute Id</xsl:text></th>" +
    "<th><xsl:text>Attribute Value</xsl:text></th>" +
    "<xsl:apply-templates select=\"//Attributes\" />" +
    "</table></center></body></html></xsl:template>" +
    "<xsl:template match=\"Attributes\">" +
    "<xsl:for-each select=\"//Attribute\">" +
    "<xsl:variable name=\"count\"><xsl:value-of select=\"count(.//Value)\" /></xsl:variable>" +
    "<xsl:variable name=\"id\"><xsl:value-of select=\"@id\" /></xsl:variable>" +
    "<xsl:choose><xsl:when test=\"$count=1\">" +
    "<tr><td><xsl:value-of select=\"$id\" /></td>" +
    "<td><xsl:value-of select=\".//Value\" /></td></tr></xsl:when>" +
    "<xsl:otherwise><xsl:for-each select=\".//Value\"><xsl:choose>" +
    "<xsl:when test=\"position() = 1\">" +
    "<tr><td><xsl:value-of select=\"$id\" /></td><td><xsl:value-of select=\".\" /></td>" +
    "</tr></xsl:when><xsl:otherwise>" +
    "<tr><td /><td><xsl:value-of select=\".\" /></td></tr>" +
    "</xsl:otherwise></xsl:choose></xsl:for-each></xsl:otherwise></xsl:choose></xsl:for-each>" +
    "</xsl:template></xsl:stylesheet>";

   private static final String communityViewer =
    "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"1.0\">" +
    "<xsl:output method=\"html\" indent=\"yes\"/>" +
    "<xsl:template match=\"/\"><xsl:variable name=\"community\">" +
    "<xsl:value-of select=\"//Community/@name\" /></xsl:variable>" +
    "<html><head><title><xsl:text>Community </xsl:text><xsl:value-of select=\"$community\" />" +
    "</title></head><body><br /><H1>" +
    "<xsl:element name=\"a\"><xsl:attribute name=\"href\">" +
    "<xsl:text>./communityViewer?attributes=</xsl:text>" +
    "<xsl:value-of select=\"$community\" /></xsl:attribute>" +
    "<xsl:text>Community </xsl:text><xsl:value-of select=\"$community\" />" +
    "</xsl:element></H1><br /><ul>" +
    "<xsl:for-each select=\"Community/Community\">" +
    "<li><xsl:element name=\"a\"><xsl:attribute name=\"href\">" +
    "<xsl:text>./communityViewer?attributes=</xsl:text>" +
    "<xsl:value-of select=\"@name\" /></xsl:attribute>" +
    "<xsl:text>Community </xsl:text><xsl:value-of select=\"@name\" />" +
    "</xsl:element></li></xsl:for-each>" +
    "<xsl:for-each select=\"//Agent\"><li>" +
    "<xsl:element name=\"a\"><xsl:attribute name=\"href\">" +
    "<xsl:text>./communityViewer?attributes=</xsl:text>" +
    "<xsl:value-of select=\"@name\" /></xsl:attribute>" +
    "<xsl:value-of select=\"@name\" /></xsl:element></li>" +
    "</xsl:for-each></ul></body></html></xsl:template></xsl:stylesheet>";

}