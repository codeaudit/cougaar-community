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

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.table.*;
import javax.swing.event.*;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.Attribute;
import javax.naming.directory.BasicAttribute;
import javax.naming.NamingException;
import java.util.*;
import java.util.List;

public class AttributesTable extends JTable
{
  private static DefaultTableModel model;

  protected void buildTable()
  {
    model = new DefaultTableModel();
    model.setColumnIdentifiers(new String[]{"name", "value"});
    model.addRow(new String[]{"", ""});
    setModel(model);
    getColumnModel().getColumn(0).setPreferredWidth(2);
    final JPopupMenu popup = new JPopupMenu(); //the popup menu for this table.
    JMenuItem add = new JMenuItem("add"); //item "add": add a new line in this table
    add.addActionListener(new ActionListener(){
       public void actionPerformed(ActionEvent e)
       { model.addRow(new String[]{"", ""});}
     });
     JMenuItem delete = new JMenuItem("delete"); //item "delete": delete current line of this table
     delete.addActionListener(new ActionListener(){
       public void actionPerformed(ActionEvent e)
       {
         int row = getSelectedRow();
         clearSelection();
         model.removeRow(row);
         if(getRowCount() == 0)
           model.addRow(new String[]{"", ""});
       }
     });
     popup.add(add);
     popup.add(delete);
     addMouseListener(new MouseAdapter(){
       public void mousePressed(MouseEvent me)
       {
         int row = rowAtPoint(me.getPoint());
         int col = columnAtPoint(me.getPoint());
         if (me.isPopupTrigger()  && getSelectedRowCount() > 0) //right click shows pop-up menu
         {
            popup.show(me.getComponent(),me.getX(), getRowHeight()*row);
         }
       }
    });
  }

  protected void insertData(Attributes attributes)
  {
    try{
      for(NamingEnumeration enums = attributes.getAll(); enums.hasMore();)
      {
        try{
          Attribute attr = (Attribute)enums.next();
          String id = attr.getID();
          if(attr.size() > 1)
          {
            for(NamingEnumeration subattrs = attr.getAll(); subattrs.hasMore();)
            {
              String subattr = (String)subattrs.next();
              model.addRow(new String[]{id, subattr});
            }
          }
          else
          {
            String value = (String)attr.get();
            if(id != null && value != null && !value.equals(""))
              model.addRow(new String[]{id, value});
          }
        }catch(NoSuchElementException e){} //in case the attribute doesn't have a value
      }
    }catch(NamingException e){e.printStackTrace();}
  }

  /**
   * Get data in table and save them in attributes. Since some attributes may contain
   * multiple values, we save all values in a hashtable at first.
   * @return the attributes
   */
  protected Attributes getData()
  {
    Attributes attrs = new BasicAttributes();
    Hashtable tempTable = new Hashtable(); //record all attributes
    for(int i=0; i<getRowCount(); i++) //put all values into a hashtable
    {
      if(getValueAt(i, 0) != null && getValueAt(i, 1) != null)
      {
        String name = (String)getValueAt(i, 0);
        String value = (String)getValueAt(i, 1);
        if(tempTable.containsKey(name)) //the attribute has multiple_value.
        {
          List list = (List)tempTable.get(name);
          list.add(value);
        }
        else
        {
          List list = new ArrayList();
          list.add(value);
          tempTable.put(name, list);
        }
      }
    }
    for(Enumeration enums = tempTable.keys(); enums.hasMoreElements();) //put all values in hashtable into attributes
    {
      String name = (String)enums.nextElement();
      List list = (List)tempTable.get(name);
      if(list.size() == 1)
        attrs.put(name, (String)list.get(0));
      else
      {
        Attribute attr = new BasicAttribute(name);
        for(int i=0; i<list.size(); i++)
          attr.add((String)list.get(i));
        attrs.put(attr);
      }
    }
    return attrs;
  }

  protected void removeData()
  {
    int size = getRowCount();
    clearSelection();
    for(int i=0; i<size; i++)
    {
      try{
        getCellEditor().cancelCellEditing();
      }catch(NullPointerException o){}
      model.removeRow(0);
    }
  }

  protected void addEmptyRow()
  {
    model.addRow(new String[]{"", ""});
  }
}
