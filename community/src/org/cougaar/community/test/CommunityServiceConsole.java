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
package org.cougaar.community.test;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import java.util.*;
import java.util.List;
import javax.naming.directory.*;

import org.apache.log4j.Category;
import org.apache.log4j.Priority;
import org.apache.log4j.BasicConfigurator;

import org.cougaar.core.util.PropertyNameValue;
import org.cougaar.core.service.community.CommunityMember;

/**
 * <p>Title: CommunityServiceConsole</p>
 * <p>Description: This is a console to test all functions in CommunityService.</p>
 */
public class CommunityServiceConsole extends JFrame
{
   private static Category cat = Category.getInstance(CommunityServiceConsole.class);
   private JPanel controlPanel;
   private JComboBox communities; //shows all communities
   private JComboBox agents; //shows community_relative agents
   private JComboBox roles; //roles of community entity
   private JTextField message, filter; //shows message
   private JCheckBox[] sroles; //entity roles
   private YellowPagesViewer viewP;
   private AttributesTable table;

   public CommunityServiceConsole()
   {
     super("Community Test");
     this.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        System.exit(0); }
     });
     setSize(600, 600);
     relocateFrame(this);

     this.getContentPane().setLayout(new BorderLayout());

     viewP = new YellowPagesViewer();
     controlPanel = new JPanel(new BorderLayout());
     controlPanel.add(buildControlPanel(), BorderLayout.CENTER);
     JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, controlPanel, viewP);
     sp.setDividerLocation(this.getWidth()*4/9);
     sp.setContinuousLayout(true);
     getContentPane().add(sp, BorderLayout.CENTER);
     message = new JTextField();
     message.setBackground(Color.white);
     getContentPane().add(message, BorderLayout.SOUTH);

     setVisible(true);
   }

   /**
    * Control panel let user select a function and show the relative interface.
    * @return
    */
   private JPanel buildControlPanel()
   {
     JPanel controlP = new JPanel(new GridLayout(14, 1));
     controlP.setBackground(Color.white);
     controlP.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

     controlP.add(new MyEmptyPanel());

     ButtonGroup group1 = new ButtonGroup();

     //add or remove entity from community
     JRadioButton addEntity = new JRadioButton("add");
     addEntity.addActionListener(new ActionListener(){
       public void actionPerformed(ActionEvent e)
       {
         clearControlPanel();
         controlPanel.add(communityEntityPanel("add", "entity"), BorderLayout.CENTER);
       }
     });
     JRadioButton removeEntity = new JRadioButton("remove");
     removeEntity.addActionListener(new ActionListener(){
       public void actionPerformed(ActionEvent e)
       {
         clearControlPanel();
         controlPanel.add(buildCommunityAgentPanel("Remove", "Entity"), BorderLayout.CENTER);
       }
     });
     controlP.add(createRadioButtonPanel(addEntity, removeEntity, group1, "entity"));

     //add or remove listener
     JRadioButton addListener = new JRadioButton("add"); //add listener
     addListener.addActionListener(new ActionListener(){
       public void actionPerformed(ActionEvent e)
       {
         clearControlPanel();
         controlPanel.add(addListener(), BorderLayout.CENTER);
       }
     });
     JRadioButton removeListener = new JRadioButton("remove"); //remove listener
     removeListener.addActionListener(new ActionListener(){
       public void actionPerformed(ActionEvent e)
       {
         clearControlPanel();
         controlPanel.add(buildCommunityAgentPanel("Remove", "Listener"), BorderLayout.CENTER);
       }
     });
     JPanel listener = createRadioButtonPanel(addListener, removeListener, group1, "listener");
     controlP.add(listener);

     //add or remove a member from selected community
     /*JRadioButton addMember = new JRadioButton("add"); //add member
     addMember.addActionListener(new ActionListener(){
       public void actionPerformed(ActionEvent e)
       {
         clearControlPanel();
         controlPanel.add(buildAddMemberPanel(), BorderLayout.CENTER);
       }
     });
     JRadioButton removeMember = new JRadioButton("remove"); //remove member
     removeMember.addActionListener(new ActionListener(){
       public void actionPerformed(ActionEvent e)
       {
         clearControlPanel();
         controlPanel.add(buildCommunityAgentPanel("Remove", "Member"), BorderLayout.CENTER);
       }
     });
     controlP.add(createRadioButtonPanel(addMember, removeMember, group1, "member"));*/

     //add or remove a role of given entity in specified community
     JRadioButton addsa = new JRadioButton("add"); //add spokesagent
     addsa.addActionListener(new ActionListener(){
       public void actionPerformed(ActionEvent e)
       {
         clearControlPanel();
         controlPanel.add(addRemoveRole("add"), BorderLayout.CENTER);
       }
     });
     JRadioButton removesa = new JRadioButton("remove"); //remove spokesagent
     removesa.addActionListener(new ActionListener(){
       public void actionPerformed(ActionEvent e)
       {
         clearControlPanel();
         controlPanel.add(addRemoveRole("remove"), BorderLayout.CENTER);
       }
     });
     controlP.add(createRadioButtonPanel(addsa, removesa, group1, "role"));

     //create a new community
     JButton createB = new JButton("create community"); //create community
     createB.addActionListener(new ActionListener(){
       public void actionPerformed(ActionEvent e)
       {
         clearControlPanel();
         controlPanel.add(buildCreateCommunityPanel(), BorderLayout.CENTER);
       }
     });
     controlP.add(createCommandPanel(createB));

     /*JButton addAgent = new JButton("add agent to community"); //create community
     addAgent.addActionListener(new ActionListener(){
       public void actionPerformed(ActionEvent e)
       {
         clearControlPanel();
         controlPanel.add(communityEntityPanel("add", "agent"), BorderLayout.CENTER);
       }
     });
     controlP.add(createCommandPanel(addAgent));*/

     //reset attributes of one community
     JButton setAttributesB = new JButton("set community attributes");
     setAttributesB.addActionListener(new ActionListener(){
       public void actionPerformed(ActionEvent e)
       {
         clearControlPanel();
         controlPanel.add(setCommunityAttributesPanel(), BorderLayout.CENTER);
       }
     });
     controlP.add(createCommandPanel(setAttributesB));

     //reset attributes of one entity in given community
     JButton setEntitysB = new JButton("set entity attributes");
     setEntitysB.addActionListener(new ActionListener(){
       public void actionPerformed(ActionEvent e)
       {
         clearControlPanel();
         controlPanel.add(communityEntityPanel("set", "entity"), BorderLayout.CENTER);
       }
     });
     controlP.add(createCommandPanel(setEntitysB));

     //list parent communities of given member
     JButton listB = new JButton("list parent communities"); //list parent communities
     listB.addActionListener(new ActionListener(){
       public void actionPerformed(ActionEvent e)
       {
         clearControlPanel();
         controlPanel.add(buildListParentCommunitiesPanel("parent"), BorderLayout.CENTER);
       }
     });
     controlP.add(createCommandPanel(listB));

     //list all listeners of given community
     JButton listListenerB = new JButton("list listeners by community");
     listListenerB.addActionListener(new ActionListener(){
       public void actionPerformed(ActionEvent e)
       {
         clearControlPanel();
         controlPanel.add(buildListParentCommunitiesPanel("listeners"), BorderLayout.CENTER);
       }
     });
     controlP.add(createCommandPanel(listListenerB));

     //search all communities who contain attributes match given filter
     JButton searchByCommunity = new JButton("search by community attributes");
     searchByCommunity.addActionListener(new ActionListener(){
       public void actionPerformed(ActionEvent e)
       {
         clearControlPanel();
         controlPanel.add(searchPanel("community"), BorderLayout.CENTER);
       }
     });
     controlP.add(createCommandPanel(searchByCommunity));

     /*JButton searchAgentByCommunity = new JButton("search agents by community attributes");
     searchAgentByCommunity.addActionListener(new ActionListener(){
       public void actionPerformed(ActionEvent e)
       {
         clearControlPanel();
         controlPanel.add(searchPanel("agent"), BorderLayout.CENTER);
       }
     });
     controlP.add(createCommandPanel(searchAgentByCommunity));*/

     //search all entities in given community who contain attributes match given filter
     JButton searchByEntity = new JButton("search by entity attributes");
     searchByEntity.addActionListener(new ActionListener(){
       public void actionPerformed(ActionEvent e)
       {
         clearControlPanel();
         controlPanel.add(searchPanel("entity"), BorderLayout.CENTER);
       }
     });
     controlP.add(createCommandPanel(searchByEntity));

     //search all entities in given community who contain given role.
     JButton searchByRole = new JButton("search by role");
     searchByRole.addActionListener(new ActionListener(){
       public void actionPerformed(ActionEvent e)
       {
         clearControlPanel();
         controlPanel.add(searchPanel("role"), BorderLayout.CENTER);
       }
     });
     controlP.add(createCommandPanel(searchByRole));

     /*JButton searchAgentByRole = new JButton("search agents by role");
     searchAgentByRole.addActionListener(new ActionListener(){
       public void actionPerformed(ActionEvent e)
       {
         clearControlPanel();
         controlPanel.add(searchPanel("agent role"), BorderLayout.CENTER);
       }
     });
     controlP.add(createCommandPanel(searchAgentByRole));*/

     controlP.add(new MyEmptyPanel());
     return controlP;
   }

   /**
    * remove all contents in control panel.
    */
   private void clearControlPanel()
   {
     setMessage("");
     if(controlPanel != null)
     {
       controlPanel.removeAll();
       controlPanel.repaint();
       controlPanel.revalidate();
     }
   }

   private JTextField newCommunityName; //the community be created
   //The panel to create community. User should fill in the new community name
   //and all it's attributes.
   private JPanel buildCreateCommunityPanel()
   {
     JPanel p = new JPanel(new BorderLayout());
     p.setBackground(Color.white);
     p.add(buildButtonPanel("create", "community"), BorderLayout.SOUTH);
     JPanel main = new JPanel(new GridBagLayout());
     main.setBorder(BorderFactory.createEmptyBorder(15, 10, 20, 10));
     JLabel label = createTitleLabel("   Create Community");
     main.setBackground(Color.white);
     table = new AttributesTable();
     table.buildTable();
     JScrollPane sp = new JScrollPane(table);

    newCommunityName = new JTextField();
    Insets insets = new Insets(5, 5, 5, 5);
    addComponent(main, label, 1, 0, 3, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
    addComponent(main, new JLabel("Community Name:"), 0, 5, 2, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
    addComponent(main, newCommunityName, 1, 7, 4, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
    addComponent(main, new JLabel("Attributes:"), 0, 9, 2, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
    addComponent(main, sp, 1, 11, 4, 10, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 1, 1);
     p.add(main, BorderLayout.CENTER);
     return p;
   }

   /**
    * Create a panel to let user reset community attributes. This panel includes
    * a combo box of all communities in the blackboard and a table shows the
    * attributes of selected community.
    * @return the panel
    */
   private JPanel setCommunityAttributesPanel()
   {
     JPanel p = new JPanel(new BorderLayout());
     p.setBackground(Color.white);
     p.add(buildButtonPanel("set", "community attributes"), BorderLayout.SOUTH);
     JPanel main = new JPanel(new GridBagLayout());
     main.setBorder(BorderFactory.createEmptyBorder(15, 10, 20, 10));
     JLabel label = createTitleLabel("Set Community Attributes");
     main.setBackground(Color.white);

     table = new AttributesTable();
     table.buildTable();
     communities = new JComboBox(ServletConnection.getCommunities()); //list all communities
     communities.setSelectedIndex(0);
     communities.addItemListener(new ItemListener(){
       public void itemStateChanged(ItemEvent e)
       {
         if(e.getStateChange() == ItemEvent.SELECTED)
         {
           table.removeData();
           table.insertData(ServletConnection.getCommunityAttributes((String)communities.getSelectedItem()));
         }
       }
     });
     table.removeData();
     table.insertData(ServletConnection.getCommunityAttributes((String)communities.getItemAt(0)));
     JScrollPane sp = new JScrollPane(table);

     Insets insets = new Insets(5, 5, 5, 5);
     addComponent(main, label, 1, 0, 3, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     addComponent(main, new JLabel("Community Name:"), 0, 5, 2, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     addComponent(main, communities, 1, 7, 4, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     addComponent(main, new JLabel("Attributes:"), 0, 9, 2, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     addComponent(main, sp, 1, 11, 4, 10, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 1, 1);
     p.add(main, BorderLayout.CENTER);
     return p;
   }

   private static JTextField newEntityName;
   /**
    * Create a panel to let user add entity or reset entity attributes.
    * It includes a combo box of names of all communities, a table to show attributes
    * of entity and a textfield to let user fill in name of new entity.
    * @param command If command is "add" and what is "entity", this is for adding
    *        and entity to selected community. If command is "set"
    *        and what is "entity", this is for resetting entity attributes.
    * @param what same as above.
    * @return the panel
    */
   private JPanel communityEntityPanel(final String command, final String what)
   {
     JPanel p = new JPanel(new BorderLayout());
     p.setBackground(Color.white);
     p.add(buildButtonPanel(command, what), BorderLayout.SOUTH);
     JPanel main = new JPanel(new GridBagLayout());
     main.setBorder(BorderFactory.createEmptyBorder(15, 10, 20, 10));
     JLabel label;
     if(command.equals("add") && what.equalsIgnoreCase("entity"))
       label = createTitleLabel("      Add Entity");
     //else if(command.equalsIgnoreCase("add") && what.equalsIgnoreCase("agent"))
       //label = createTitleLabel("      Add Agent");
     else
       label = createTitleLabel("  Set Entity Attributes");
     main.setBackground(Color.white);

     communities = new JComboBox(ServletConnection.getCommunities()); //list all communities
     communities.setSelectedIndex(0);
     table = new AttributesTable();
     table.buildTable();
     if(command.equals("add"))
     {
       newEntityName = new JTextField();
     }
     else
     {
       agents = new JComboBox(ServletConnection.getEntitiesOfCommunity((String)communities.getItemAt(0)));
       agents.setSelectedIndex(0);
       communities.addItemListener(new ItemListener(){
         public void itemStateChanged(ItemEvent e)
         {
           if(e.getStateChange() == ItemEvent.SELECTED)
           {
             agents.removeAllItems();
             Vector vs = ServletConnection.getEntitiesOfCommunity((String)communities.getSelectedItem());
             for(int i=0; i<vs.size(); i++)
               agents.addItem((String)vs.get(i));
             agents.setSelectedIndex(0);
             table.removeData();
             table.insertData(ServletConnection.getEntityAttributes((String)communities.getSelectedItem(), (String)agents.getSelectedItem()));
           }
         }
       });
       agents.addItemListener(new ItemListener(){
         public void itemStateChanged(ItemEvent e)
         {
           if(e.getStateChange() == ItemEvent.SELECTED)
           {
             String agent = (String)agents.getSelectedItem();
             table.removeData();
             table.insertData(ServletConnection.getEntityAttributes((String)communities.getSelectedItem(), agent));
           }
         }
       });
       String agent = (String)agents.getSelectedItem();
       table.removeData();
       table.insertData(ServletConnection.getEntityAttributes((String)communities.getSelectedItem(), agent));
     }
     JScrollPane sp = new JScrollPane(table);

     Insets insets = new Insets(5, 5, 5, 5);
     addComponent(main, label, 1, 0, 3, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     addComponent(main, new JLabel("Community Name:"), 0, 5, 2, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     addComponent(main, communities, 1, 7, 4, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     String str = "Entity Name:";
    /* if(what.equalsIgnoreCase("agent"))
       str = "Agent Address:";
     else
       str = "Entity Name:";*/
     addComponent(main, new JLabel(str), 0, 9, 2, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     if(command.equals("add"))
       addComponent(main, newEntityName, 1, 11, 4, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     else
       addComponent(main, agents, 1, 11, 4, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     addComponent(main, new JLabel("Attributes"), 0, 13, 2, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     addComponent(main, sp, 1, 15, 4, 10, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 1, 1);
     p.add(main, BorderLayout.CENTER);
     return p;
   }

   /**
    * Create a panel let user add or remove a role of an entity. The panel includes
    * a combobox of names of all communities, a combobox of names of all entities
    * of selected community and a combobox of roles of selected entity.
    * @param command If the command is "add", this is for adding a role to selected entity,
    *        if the command is "remove", this is for removing a role from entity.
    * @return the panel
    */
   private JPanel addRemoveRole(final String command)
   {
     JPanel p = new JPanel(new BorderLayout());
     p.setBackground(Color.white);
     p.add(buildButtonPanel(command, "role"), BorderLayout.SOUTH);
     JPanel main = new JPanel(new GridBagLayout());
     main.setBorder(BorderFactory.createEmptyBorder(15, 10, 20, 10));
     JLabel label;
     if(command.equals("add"))
       label = createTitleLabel("  Add Role To Entity");
     else
       label = createTitleLabel(" Remove Role From Entity");
     main.setBackground(Color.white);

     communities = new JComboBox(ServletConnection.getCommunities()); //list all communities
     communities.setSelectedIndex(0);
     agents = new JComboBox(ServletConnection.getEntitiesOfCommunity((String)communities.getItemAt(0)));
     agents.setSelectedIndex(0);
     if(command.equalsIgnoreCase("add"))
       roles = new JComboBox(getAvaliableRoles((String)communities.getItemAt(0), (String)agents.getItemAt(0)));
     else
       roles = new JComboBox(ServletConnection.getEntityRoles((String)communities.getItemAt(0), (String)agents.getItemAt(0)));
     Dimension d = roles.getPreferredSize();
     roles.setPreferredSize(new Dimension(200, d.height));
     roles.setMaximumSize(new Dimension(200, d.height));
     communities.addItemListener(new ItemListener(){
       public void itemStateChanged(ItemEvent e)
       {
         if(e.getStateChange() == ItemEvent.SELECTED)
         {
             agents.removeAllItems();
             Vector vs = ServletConnection.getEntitiesOfCommunity((String)communities.getSelectedItem());
             for(int i=0; i<vs.size(); i++)
               agents.addItem((String)vs.get(i));
             agents.setSelectedIndex(0);
             roles.removeAllItems();
             Vector vecs;
             if(command.equalsIgnoreCase("remove"))
               vecs = ServletConnection.getEntityRoles((String)communities.getSelectedItem(), (String)agents.getSelectedItem());
             else
               vecs = getAvaliableRoles((String)communities.getSelectedItem(), (String)agents.getSelectedItem());
             for(int i=0; i<vecs.size(); i++)
               roles.addItem((String)vecs.get(i));
         }
       }
     });
     agents.addItemListener(new ItemListener(){
         public void itemStateChanged(ItemEvent e)
         {
           if(e.getStateChange() == ItemEvent.SELECTED)
           {
             roles.removeAllItems();
             Vector vecs;
             if(command.equalsIgnoreCase("remove"))
               vecs = ServletConnection.getEntityRoles((String)communities.getSelectedItem(), (String)agents.getSelectedItem());
             else
               vecs = getAvaliableRoles((String)communities.getSelectedItem(), (String)agents.getSelectedItem());
             for(int i=0; i<vecs.size(); i++)
               roles.addItem((String)vecs.get(i));
           }
         }
      });

     Insets insets = new Insets(5, 5, 5, 5);
     addComponent(main, label, 1, 0, 4, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     addComponent(main, new JLabel("Community Name:"), 0, 5, 2, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     addComponent(main, communities, 1, 6, 4, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     addComponent(main, new JLabel("Entity Name:"), 0, 7, 2, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     addComponent(main, agents, 1, 8, 4, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     addComponent(main, new JLabel("Role:"), 0, 9, 2, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     addComponent(main, roles, 1, 11, 4, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     addComponent(main, new MyEmptyPanel(), 0, 12, 4, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 1, 1);
     p.add(main, BorderLayout.CENTER);
     return p;
   }

   /**
    * Create a panel to list all parent communities of user selected entity or list
    * listeners of selected community. If list parent communities, the
    * panel includes a combobox of all entities in the blackboard and a list shows
    * all parent communities of selected entity. If this is for list listeners,
    * the panel includes a combobox of names of all communities and a list shows
    * listeners of selected community.
    * @param command if the command is "listener", this is for listing listeners of community.
    *   If the command is "parent", this is for listing parent communities.
    * @return the panel
    */
   private JPanel buildListParentCommunitiesPanel(final String command)
   {
     JPanel p = new JPanel(new BorderLayout());
     p.setBackground(Color.white);
     p.add(buildButtonPanel("find", command), BorderLayout.SOUTH);
     JPanel main = new JPanel(new GridBagLayout());
     main.setBorder(BorderFactory.createEmptyBorder(15, 10, 20, 10));
     String title;
     if(command.equalsIgnoreCase("parent"))
       title = "List Parent Communities";
     else
     {
       title = "  List Listeners of\n      Community";
     }
     JLabel label = createTitleLabel(title);
     main.setBackground(Color.white);

     final JComboBox members;
     final DefaultListModel model = new DefaultListModel();
     JList list = new JList(model); //list all parent communities
     JScrollPane sp = new JScrollPane(list);
     if(command.equalsIgnoreCase("parent"))
     {
       members = new JComboBox(getAllMembers()); //list all members
       filter = new JTextField(); //to fill in serach condition
       filter.addActionListener(new ActionListener(){
          public void actionPerformed(ActionEvent e)
          {
            String member = (String)members.getSelectedItem();
            member = member.substring(member.indexOf(" ")+1, member.length());
            Vector vs = ServletConnection.listParentCommunities(member, getFilter(filter));
            model.removeAllElements();
            for(int i=0; i<vs.size(); i++)
              model.addElement((String)vs.get(i));
          }
       });
     }
     else
       members = new JComboBox(ServletConnection.getCommunities()); //list all communities
     members.setSelectedIndex(0);
     members.addItemListener(new ItemListener(){
       public void itemStateChanged(ItemEvent e)
       {
         if(e.getStateChange() == ItemEvent.SELECTED)
         {
            model.removeAllElements();
            Vector vs;
            if(command.equalsIgnoreCase("parent"))
            {
              String member = (String)members.getSelectedItem();
              member = member.substring(member.indexOf(" ")+1, member.length());
              if(filter.getText().equals(""))
                vs = ServletConnection.listParentCommunities(member);
              else
                vs = ServletConnection.listParentCommunities(member, getFilter(filter));
            }
            else
              vs = ServletConnection.getListenersOfCommunity((String)members.getSelectedItem());
            for(int i=0; i<vs.size(); i++)
              model.addElement((String)vs.get(i));
         }
       }
     });

     Vector vs;
     if(command.equalsIgnoreCase("parent"))
       vs = ServletConnection.listParentCommunities((String)members.getSelectedItem());
     else
       vs = ServletConnection.getListenersOfCommunity((String)members.getSelectedItem());
     for(int i=0; i<vs.size(); i++)
        model.addElement((String)vs.get(i));

    String s1, s2;
    if(command.equalsIgnoreCase("parent"))
    { s1 = "Member Name:"; s2 = "Parent Communities:"; }
    else
    { s1 = "Community Name:"; s2 = "Listeners:"; }
    Insets insets = new Insets(5, 5, 5, 5);
    addComponent(main, label, 1, 0, 3, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
    addComponent(main, new JLabel(s1), 0, 5, 2, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
    addComponent(main, members, 1, 7, 4, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
    if(command.equalsIgnoreCase("parent"))
    {
      addComponent(main, new JLabel("Search Filter: (optional)"), 0, 9, 2, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
      addComponent(main, filter, 1, 11, 4, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
    }
    addComponent(main, new JLabel(s2), 0, 13, 2, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
    addComponent(main, sp, 1, 15, 4, 10, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 1, 1);
     p.add(main, BorderLayout.CENTER);
     return p;
   }

   /**
    * This panel is used to remove entity or listener from selected community. The
    * panel includes a combobox shows names of all communties and a combobox shows
    * entities or listeners of selected community.
    * @param command is a single string "Remove" is this case. If what is "entity",
    *        this panel is for removing entity from selected community, if what is
    *        "listener", this is for removing listener from selected community.
    * @param what same as above.
    * @return the panel
    */
   private JPanel buildCommunityAgentPanel(final String command, final String what)
   {
     JPanel p = new JPanel(new BorderLayout());
     p.setBackground(Color.white);
     p.add(buildButtonPanel(command, what), BorderLayout.SOUTH);
     JPanel main = new JPanel(new GridBagLayout());
     main.setBorder(BorderFactory.createEmptyBorder(15, 10, 20, 10));
     JLabel label = createTitleLabel("    " + command + " " + what);
     main.setBackground(Color.white);

     communities = new JComboBox(ServletConnection.getCommunities()); //list all communities
     communities.setSelectedIndex(0);
     agents = new JComboBox(getRelativeAgentVector(command, what, (String)communities.getSelectedItem()));
     communities.addItemListener(new ItemListener(){
       public void itemStateChanged(ItemEvent e)
       {
         if(e.getStateChange() == ItemEvent.SELECTED)
         {
           agents.removeAllItems();
           Vector vs;
           vs = getRelativeAgentVector(command, what, (String)communities.getSelectedItem());
           for(int i=0; i<vs.size(); i++)
             agents.addItem((String)vs.get(i));
         }
       }
     });

     Insets insets = new Insets(5, 5, 5, 5);
     addComponent(main, label, 1, 0, 3, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 1, 1);
     String s1 = "";
     String s2 = "";
     if(what.equals("Listener"))
     {s1 = "Community Name:"; s2 = "Listener of Agent:";}
     //else if(what.equals("Member"))
     //{s1 = "Parent Name:"; s2 = "Member Name:";}
     else if(what.equals("Entity"))
     {s1 = "Community Name:"; s2 = "Entity Name:";}
     addComponent(main, new JLabel(s1), 0, 3, 2, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     addComponent(main, communities, 1, 4, 4, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     addComponent(main, new JLabel(s2), 0, 5, 2, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     addComponent(main, agents, 1, 6, 4, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     p.add(main, BorderLayout.NORTH);
     return p;
   }

   /**
    * Create a panel to add listener to selected community. This panel includes a
    * combobox of all communities and a combobox of all listeners of selected community.
    * @return the panel
    */
   private JPanel addListener()
   {
     JPanel p = new JPanel(new BorderLayout());
     p.setBackground(Color.white);
     p.add(buildButtonPanel("Add", "Listener"), BorderLayout.SOUTH);
     JPanel main = new JPanel(new GridBagLayout());
     main.setBorder(BorderFactory.createEmptyBorder(15, 10, 20, 10));
     JLabel label = createTitleLabel("     Add Listener");
     main.setBackground(Color.white);
     //p.add(label, BorderLayout.NORTH);

     communities = new JComboBox(ServletConnection.getCommunities()); //list all communities
     communities.setSelectedIndex(0);
     newEntityName = new JTextField();
     Insets insets = new Insets(5, 5, 5, 5);
     addComponent(main, label, 1, 0, 3, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     addComponent(main, new JLabel("Community Name:"), 0, 3, 2, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     addComponent(main, communities, 1, 4, 4, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     addComponent(main, new JLabel("Listener Name:"), 0, 5, 2, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     addComponent(main, newEntityName, 1, 6, 4, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     addComponent(main, new MyEmptyPanel(), 0, 7, 4, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 1, 1);
     p.add(main, BorderLayout.CENTER);
     return p;
   }

   private String memberType = "";
   //panel to add member to a selected community
   private JPanel buildAddMemberPanel()
   {
     JPanel p = new JPanel(new BorderLayout());
     p.setBackground(Color.white);
     p.add(buildButtonPanel("Add", "Member"), BorderLayout.SOUTH);
     JPanel main = new JPanel(new GridBagLayout());
     main.setBorder(BorderFactory.createEmptyBorder(15, 10, 20, 10));
     JLabel label = createTitleLabel("     Add Member");
     main.setBackground(Color.white);

     communities = new JComboBox(ServletConnection.getCommunities()); //list all communities
     communities.setSelectedIndex(0);
     newEntityName = new JTextField();
     JPanel mtPanel = new JPanel();
     mtPanel.setLayout(new GridLayout(memberTypes.length, 1));
     //mtPanel.setBackground(Color.white);
     mtPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
     ButtonGroup mTypes = new ButtonGroup();
     for(int i=0; i<memberTypes.length; i++)
     {
       JRadioButton but = new JRadioButton(" " + memberTypes[i]);
       but.addActionListener(new ActionListener(){
         public void actionPerformed(ActionEvent e)
         {
           memberType = e.getActionCommand().trim();
         }
       });
       mtPanel.add(but);
       mTypes.add(but);
     }

     JPanel panel = new JPanel();
     panel.setLayout(new GridLayout(agentRoles.length, 1));
     panel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
     sroles = new JCheckBox[agentRoles.length]; //all agent roles
     for(int i=0; i<agentRoles.length; i++)
     {
       JCheckBox box = new JCheckBox(" " + agentRoles[i]);
       panel.add(box);
       sroles[i] = box;
     }
     JScrollPane checkboxP = new JScrollPane();
     checkboxP.getViewport().add(panel);

     Insets insets = new Insets(5, 5, 5, 5);
     addComponent(main, label, 0, 0, 2, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     addComponent(main, new JLabel("Parent Name:"), 0, 3, 2, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     addComponent(main, communities, 1, 4, 4, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     addComponent(main, new JLabel("Member Name:"), 0, 5, 2, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     addComponent(main, newEntityName, 1, 6, 4, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     addComponent(main, new JLabel("Member Type:"), 0, 7, 2, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     addComponent(main, mtPanel, 1, 8, 4, 3, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 1, 0);
     addComponent(main, new JLabel("Member Roles:"), 0, 11, 2, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     addComponent(main, checkboxP, 1, 12, 4, 2, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 1, 1);
     p.add(main, BorderLayout.CENTER);
     return p;
   }

   private DefaultListModel searchResultsModel;
   /**
    * Create a panel to list all communities matching the input filter, list
    * all entities in selected community who match input filter or list all entities
    * in selected community who contains seleted role.
    * @param command If command is "community", it is for attribute_based searching of community. If
    *        command is "entity", it is for attribute_based searching of entities. If command is "role",
    *        it is for searching entities who contains given role.
    * @return
    */
   private JPanel searchPanel(final String command)
   {
     JPanel p = new JPanel(new BorderLayout());
     p.setBackground(Color.white);
     p.add(buildButtonPanel("search", command), BorderLayout.SOUTH);
     JPanel main = new JPanel(new GridBagLayout());
     main.setBorder(BorderFactory.createEmptyBorder(15, 10, 20, 10));
     String title;
     if(command.equalsIgnoreCase("community"))
       title = "      Search By\n  Community Attributes";
     else if(command.equalsIgnoreCase("entity"))
       title = "      Search By\n    Entity Attributes";
     else //if(command.equalsIgnoreCase("role"))
       title = "   Search By Role";
     /*else if(command.equalsIgnoreCase("agent"))
       title = "    Search Agents By\n  Community Attributes";
     else
       title = "  Search Agents By Role";*/
     JLabel label = createTitleLabel(title);
     main.setBackground(Color.white);
     p.add(label, BorderLayout.NORTH);

     searchResultsModel = new DefaultListModel();
     JList list = new JList(searchResultsModel); //list search results
     JScrollPane sp = new JScrollPane(list);

     if(!command.equalsIgnoreCase("role"))
       filter = new JTextField();
     if(!command.equalsIgnoreCase("community"))
     {
       communities = new JComboBox(ServletConnection.getCommunities());
       communities.setSelectedIndex(0);
       if(command.equalsIgnoreCase("role"))// || command.equalsIgnoreCase("agent role"))
       {
         roles = new JComboBox(agentRoles);
         communities.addItemListener(new ItemListener(){
           public void itemStateChanged(ItemEvent e)
           {
             if(e.getStateChange() == ItemEvent.SELECTED)
             {
               searchResultsModel.removeAllElements();
               Vector vecs = null;
               //if(command.equalsIgnoreCase("role"))
                 vecs = ServletConnection.searchByRole((String)communities.getSelectedItem(), (String)roles.getSelectedItem());
               //else
                 //vecs = ServletConnection.agentSearchByRole((String)communities.getSelectedItem(), (String)roles.getSelectedItem());
               for(int i=0; i<vecs.size(); i++)
                 searchResultsModel.addElement((String)vecs.get(i));

             }
           }
         });
         roles.addItemListener(new ItemListener(){
           public void itemStateChanged(ItemEvent e)
           {
             if(e.getStateChange() == ItemEvent.SELECTED)
             {
               searchResultsModel.removeAllElements();
               Vector vecs = null;
               //if(command.equalsIgnoreCase("role"))
                 vecs = ServletConnection.searchByRole((String)communities.getSelectedItem(), (String)roles.getSelectedItem());
               //else
                 //vecs = ServletConnection.agentSearchByRole((String)communities.getSelectedItem(), (String)roles.getSelectedItem());
               for(int i=0; i<vecs.size(); i++)
                 searchResultsModel.addElement((String)vecs.get(i));
             }
           }
         });
         roles.setSelectedIndex(0);
       }
     }

     if(command.equalsIgnoreCase("role"))
     {
       Vector vs = ServletConnection.searchByRole((String)communities.getSelectedItem(), (String)roles.getSelectedItem());
       for(int i=0; i<vs.size(); i++)
         searchResultsModel.addElement((String)vs.get(i));
     }
    /* else if(command.equalsIgnoreCase("agent role"))
     {
       Vector vs = ServletConnection.agentSearchByRole((String)communities.getSelectedItem(), (String)roles.getSelectedItem());
       for(int i=0; i<vs.size(); i++)
         searchResultsModel.addElement((String)vs.get(i));
     }*/

     Insets insets = new Insets(5, 5, 5, 5);
     addComponent(main, label, 1, 0, 3, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     if(!command.equalsIgnoreCase("community"))
     {
       addComponent(main, new JLabel("Community Name:"), 0, 5, 2, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
       addComponent(main, communities, 1, 7, 4, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     }
     if(!command.equalsIgnoreCase("role"))// && !command.equalsIgnoreCase("agent role"))
     {
       addComponent(main, new JLabel("Search Filter:"), 0, 9, 2, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
       addComponent(main, filter, 1, 11, 4, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     }
     else
     {
       addComponent(main, new JLabel("Search Role:"), 0, 9, 2, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
       addComponent(main, roles, 1, 11, 4, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     }
     addComponent(main, new JLabel("Search Results"), 0, 13, 2, 1, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 0, 0);
     addComponent(main, sp, 1, 15, 4, 10, GridBagConstraints.CENTER, GridBagConstraints.BOTH, 0, 0, insets, 1, 1);
     p.add(main, BorderLayout.CENTER);
     return p;
   }


   private Vector getRelativeAgentVector(String command, String what, String community)
   {
     Vector vs = null;
     if(command.equals("Remove") && what.equals("Listener"))
        vs = ServletConnection.getListenersOfCommunity(community);
     //else if(command.equals("Remove") && what.equals("Member"))
       //vs = getMembersOfCommunity(community);
     else if(command.equalsIgnoreCase("remove") && what.equalsIgnoreCase("entity"))
       vs = ServletConnection.getEntitiesOfCommunity(community);
     return vs;
   }

   //this panel includes three buttons: clear, done and back.
   private JPanel buildButtonPanel(final String operation, final String name)
   {
     JPanel p = new JPanel();
     p.setBackground(Color.white);
     JButton clear = new JButton("clear"); //only used in create community. empty the text field and the attributes table
     clear.setEnabled(false);
     if(operation.equals("create") && name.equals("community") ||
       (operation.equalsIgnoreCase("add") && !name.equalsIgnoreCase("role")))
       clear.setEnabled(true);
     clear.addActionListener(new ActionListener(){
       public void actionPerformed(ActionEvent e)
       {
         if(name.equalsIgnoreCase("community"))
         {
           newCommunityName.setText("");
           table.removeData();
           table.addEmptyRow();
         }
         else
         {
           newEntityName.setText("");
           if(name.equalsIgnoreCase("entity"))
           {
             table.removeData();
             table.addEmptyRow();
           }
           else if(name.equalsIgnoreCase("member"))
             for(int i=0; i<sroles.length; i++)
               sroles[i].setSelected(false);
         }
       }
     });

     JButton done = new JButton("done"); //do operations to community
     if(operation.equalsIgnoreCase("Add") || operation.equalsIgnoreCase("Remove")
       || operation.equals("create") || operation.equals("set") || operation.equalsIgnoreCase("search"))
       done.setEnabled(true);
     else
       done.setEnabled(false);
     done.addActionListener(new ActionListener(){
       public void actionPerformed(ActionEvent e)
       {
         boolean value = false;
         if(name.equals("Listener") && operation.equals("Remove"))  //add or remove listener
         {
             value = ServletConnection.actionToAgent(operation, name, (String)communities.getSelectedItem(), (String)agents.getSelectedItem());
         }
         else if(name.equals("Listener") && operation.equals("Add"))
           value = ServletConnection.actionToAgent("Add", "Listener", (String)communities.getSelectedItem(), newEntityName.getText().trim());
         else if(name.equalsIgnoreCase("role"))
         {
           value = ServletConnection.addRemoveRole(operation, (String)communities.getSelectedItem(),
               (String)agents.getSelectedItem(), (String)roles.getSelectedItem());
         }
         else if(operation.equals("Remove")) //remove spokesagent or member
         {
           String agent = (String)agents.getSelectedItem();
           agent = agent.substring(agent.indexOf(" ") + 1, agent.length());
           value = ServletConnection.actionToAgent(operation, name, (String)communities.getSelectedItem(), agent);
         }
       /*  else if(operation.equals("Add") && name.equals("Member"))
         {
           String communityName = (String)communities.getSelectedItem();
           String memberName = newEntityName.getText().trim();
           int type;
           if(memberType.equalsIgnoreCase("agent"))
             type = CommunityMember.AGENT;
           else
             type = CommunityMember.COMMUNITY;
           Vector vs = new Vector();
           for(int i=0; i<sroles.length; i++)
             if(sroles[i].isSelected())
               vs.add(sroles[i].getText().trim());
           String[] roles = new String[vs.size()];
           for(int i=0; i<vs.size(); i++)
             roles[i] = (String)vs.get(i);
           value = ServletConnection.addMember(communityName, memberName, type, roles);
         }*/

         if(operation.equals("create") && name.equals("community"))
         {
           if(!newCommunityName.getText().equals("") || !newCommunityName.getText().equals(null))
           {
             Attributes attrs = table.getData();
             value = ServletConnection.createCommunity(newCommunityName.getText(), attrs);
           }
         }
         else if(operation.equals("set") && name.equals("community attributes"))
         {
           String communityName = (String)communities.getSelectedItem();
           Attributes attrs = table.getData();
           value = ServletConnection.setCommunityAttributes(communityName, attrs);
         }
         else if(operation.equals("set") && name.equals("entity"))
         {
           String communityName = (String)communities.getSelectedItem();
           String entityName = (String)agents.getSelectedItem();
           Attributes attrs = table.getData();
           value = ServletConnection.setEntityAttributes(communityName, entityName, attrs);
         }
         else if(operation.equals("add") && name.equals("entity"))// || name.equalsIgnoreCase("agent")))
         {
           String communityName = (String)communities.getSelectedItem();
           String entityName = newEntityName.getText().trim();
           Attributes attrs = table.getData();
           //if(name.equals("entity"))
             value = ServletConnection.addEntity(communityName, entityName, attrs);
          // else
             //value = ServletConnection.addAgent(communityName, entityName, attrs);
         }
         else if(operation.equalsIgnoreCase("search"))
         {
           Vector vs = null;
           String filterStr = getFilter(filter);
           if(name.equalsIgnoreCase("community"))
             vs = ServletConnection.search(filterStr);
           else if(name.equalsIgnoreCase("entity"))
             vs = ServletConnection.search((String)communities.getSelectedItem(), filterStr);
           //else if(name.equalsIgnoreCase("agent"))
             //vs = ServletConnection.agentSearch((String)communities.getSelectedItem(), filterStr);
           searchResultsModel.removeAllElements();
           if(vs.size() == 0)
           {
             setMessage("No result at all.");
             return;
           }
           for(int i=0; i<vs.size(); i++)
             searchResultsModel.addElement((String)vs.get(i));
           value = true;
         }

         if(value)
           setMessage(operation + " " + name.toLowerCase() + " succeed");
         else
           setMessage(operation + " " + name.toLowerCase() + " failed");

         //refresh yellow page
         viewP.refresh();
       }
     });

     JButton back = new JButton("back"); //back to function page
     back.addActionListener(new ActionListener(){
       public void actionPerformed(ActionEvent e)
       {
         clearControlPanel();
         controlPanel.add(buildControlPanel(), BorderLayout.CENTER);
       }
     });
     p.add(clear);
     p.add(done);
     p.add(back);
     return p;
   }

  /* private Vector getMembersOfCommunity(String community)
   {
     Vector agents = ServletConnection.listAgentMembers(community);
     Vector communities = ServletConnection.listCommunityMembers(community);
     Vector vs = new Vector();
     for(int i=0; i<agents.size(); i++)
       vs.add("agent " + (String)agents.get(i));
     for(int i=0; i<communities.size(); i++)
       vs.add("community " + (String)communities.get(i));
     return vs;
   }*/

  /**
   * Get roles who are not in given entity. This method is used when user try to
   * add new role to an entity.
   * @param community which community is the entity in?
   * @param entity the entity we are searching
   * @return a list of all roles who are not in this entity
   */
  private Vector getAvaliableRoles(String community, String entity)
  {
    Vector results = new Vector();
    Vector currentRoles = ServletConnection.getEntityRoles(community, entity);
    for(int i=0; i<agentRoles.length; i++)
    {
      if(!currentRoles.contains(agentRoles[i]))
        results.add(agentRoles[i]);
    }
    return results;
  }

  /**
   * Get all entities in current blackboard. This method is used when user try
   * to list parent of an entity.
   * @return a list of names of all entities.
   */
  private Vector getAllMembers()
  {
    Vector results = new Vector();
    Vector allCommunities = ServletConnection.getCommunities();
    for(int i=0; i<allCommunities.size(); i++)
    {
      Vector members = ServletConnection.getMembersOfCommunity((String)allCommunities.get(i));
      for(int j=0; j<members.size(); j++)
        if(!results.contains((String)members.get(j)))
          results.add((String)members.get(j));
    }
    return results;
  }

  /**
   * Put the frame in the middle of display screen.
   * @param f the frame
   */
  private static void relocateFrame(JFrame f)
  {
    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    f.setLocation(screenSize.width / 2 - f.getSize().width / 2,
		screenSize.height / 2 - f.getSize().height / 2);
  }

  private void addComponent( Container container, Component component, int gridx,
                              int gridy, int gridwidth, int gridheight, int anchor,
                              int fill, int ipadx, int ipady, Insets insets,
                              double weightx, double weighty)
   {
      GridBagConstraints gbc = new GridBagConstraints();
      gbc.gridx = gridx;
      gbc.gridy = gridy;
      gbc.gridheight = gridheight;
      gbc.gridwidth = gridwidth;
      gbc.fill = fill;
      gbc.anchor = anchor;
      gbc.ipadx = ipadx;
      gbc.ipady = ipady;
      gbc.insets = insets;
      gbc.weightx = weightx;
      gbc.weighty = weighty;
      container.add(component, gbc);
   }

   /**
    * Show message in the text field at the bottom of the main window.
    * @param text message
    */
   private void setMessage(String text)
   {
     message.setEditable(true);
     message.setText(text);
     message.setEditable(false);
   }

   /**
    * Create a panel with two radio buttons.
    * @param rb1 first radio button
    * @param rb2 second radio button
    * @param group button group of the two buttons
    * @param what label follows the two radio buttons
    * @return the panel
    */
   private JPanel createRadioButtonPanel(JRadioButton rb1, JRadioButton rb2, ButtonGroup group, String what)
   {
     JPanel panel = new JPanel(new GridLayout(1, 4));
     panel.setBorder(BorderFactory.createEmptyBorder(5, 1, 5, 1));
     panel.setBackground(Color.white);
     rb1.setBackground(Color.white);
     rb2.setBackground(Color.white);
     group.add(rb1);
     group.add(rb2);
     JPanel extra1 = new JPanel(new BorderLayout());
     extra1.setBackground(Color.white);
     extra1.add(new JLabel("  "), BorderLayout.WEST);
     extra1.add(rb1, BorderLayout.EAST);
     panel.add(extra1);
     panel.add(rb2);
     panel.add(new JLabel(what));
     return panel;
   }

   /**
    * Create a panel contains the command button.
    * @param button the command button
    * @return the panel
    */
   private JPanel createCommandPanel(JButton button)
   {
     JPanel panel = new JPanel(new BorderLayout());
     panel.setBackground(Color.white);
     panel.add(button, BorderLayout.CENTER);
     panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
     return panel;
   }

   /**
    * Create a multi line label with input string.
    * @param title string shows in the label
    * @return the label
    */
   private JLabel createTitleLabel(String title)
   {
     JLabel label = new JLabel(title);
     label.setFont(new Font("DialogInput", Font.BOLD, 16));
     label.setUI( new MultiLineLabelUI() );
     label.setForeground(new Color(101, 0, 50));
     return label;
   }

   /**
    * Fetch user input search filter. Add brackets to the string if it doesn't have.
    * @param filterField the text field
    * @return the filter string
    */
   private String getFilter(JTextField filterField)
   {
     if(filterField.getText().equals(""))
       return "";
     else
     {
       String filter = filterField.getText().trim();
       if(filter.startsWith("(") && filter.endsWith(")"))
         return filter;
       else
         return "(" + filter + ")";
     }
   }

  public static void main(String[] args)
  {
    BasicConfigurator.configure();
    cat.setPriority(Priority.INFO);
    CommunityServiceConsole console = new CommunityServiceConsole();
  }

  private class MyEmptyPanel extends JPanel
  {
    public MyEmptyPanel()
    {
      this.setLayout(new BorderLayout());
      this.setBackground(Color.white);
      this.add(new JLabel("   "), BorderLayout.CENTER);
    }
  }

  private static String[] agentRoles = new String[]{ "AirTransportationProvider", "AmmoSupplyProvider",
    "AmmunitionProvider", "CONUSGroundTransportationProvider", "ConstructionProvider",
    "FuelSupplyProvider", "FuelTransportProvider", "MaterielTransportProvider",
    "OrganicAirTransportationProvider", "PackagedPOLSupplyProvider", "SeaTransportationProvider",
    "ShipPackingTransportationProvider", "SparePartsProvider", "StrategicTransportationProvider",
    "SubsistenceSupplyProvider", "TheaterStrategicTransportationProvider", "TransportProvider"
  };
  //private static String[] agentRoles = new String[]{"Member", "ChangeListener"};
  private static String[] memberTypes = new String[]{"Agent", "Community"};
}
