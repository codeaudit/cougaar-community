/*
 * <copyright>
 *  
 *  Copyright 2003-2004 BBNT Solutions, LLC
 *  under sponsorship of the Defense Advanced Research Projects
 *  Agency (DARPA).
 * 
 *  You can redistribute this software and/or modify it under the
 *  terms of the Cougaar Open Source License as published on the
 *  Cougaar Open Source Website (www.cougaar.org).
 * 
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 *  A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 *  OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 *  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 *  LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 *  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 *  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 *  OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *  
 * </copyright>
 */
package org.cougaar.community.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.StringTokenizer;

import org.cougaar.util.DBConnectionPool;
import org.cougaar.util.DBProperties;
import org.cougaar.util.Parameters;

public class CsvFileLoader
{
  private static final String QUERY_FILE = "DBInitializer.q";
  private static Connection conn;
  private static final String extension = ".csv";

  static
  {
    try{
      DBProperties dbp = DBProperties.readQueryFile(QUERY_FILE);
      String dbStyle = dbp.getDBType();
      insureDriverClass(dbStyle);
      String database = dbp.getProperty("database");
      String username = dbp.getProperty("username");
      String password = dbp.getProperty("password");
      try{
        conn = DBConnectionPool.getConnection(database, username, password);
      }catch(SQLException e){e.printStackTrace();}
    }catch(IOException e){e.printStackTrace();}
    catch(Exception e){e.printStackTrace();}
  }

  /*
   * Set up available JDBC driver
   */
  private static void insureDriverClass(String dbtype) throws SQLException, ClassNotFoundException {
    String driverParam = "driver." + dbtype;
    String driverClass = Parameters.findParameter(driverParam);
    if (driverClass == null) {
      // this is likely a "cougaar.rc" problem.
      // Parameters should be modified to help generate this exception:
      throw new SQLException("Unable to find driver class for \""+
                             driverParam+"\" -- check your \"cougaar.rc\"");
    }
    Class.forName(driverClass);
  }

  public CsvFileLoader(String fileName)
  {
    String tableName;
    if(!fileName.endsWith(extension))
    {
      fileName += extension;
      tableName = fileName;
    }
    else
      tableName = fileName.substring(0, fileName.lastIndexOf(extension));

    File file = org.cougaar.util.ConfigFinder.getInstance().locateFile(fileName);

    Statement st = null;
    try{
      st = conn.createStatement();
      ResultSet rs = st.executeQuery("select * from " + tableName);
      st.executeUpdate("drop table " + tableName);
    }catch(SQLException e){}

    if(file != null)
    {
      try{
        RandomAccessFile rfile = new RandomAccessFile(file, "r");

        String createTable = "create table " + tableName + "(";
        String str = rfile.readLine();
        //first line is column names
        StringTokenizer tokens = new StringTokenizer(str, ",");
        while(tokens.hasMoreTokens())
        {
          createTable += tokens.nextToken() + " varchar(100), ";
        }
        createTable = createTable.substring(0, createTable.length()-2);
        createTable += ")";
        st.executeUpdate(createTable);

        str = rfile.readLine();
        //insert data
        while(str != null)
        {
          String insertQuery = "insert into " + tableName + " values(";
          StringTokenizer token = new StringTokenizer(str, ",");
          while(token.hasMoreTokens())
          {
            insertQuery += "'" + token.nextToken() + "', ";
          }
          insertQuery = insertQuery.substring(0, insertQuery.length()-2);
          insertQuery += ")";
          st.executeUpdate(insertQuery);
          str = rfile.readLine();
        }
      }catch(FileNotFoundException e){e.printStackTrace();}
      catch(IOException e){e.printStackTrace();}
      catch(SQLException e){e.printStackTrace();}
      catch(Exception e){e.printStackTrace();}
    }
  }

  public static void main(String[] args)
  {
    for(int i=0; i<args.length; i++)
    {System.out.println("Loading " + args[i]);
      new CsvFileLoader(args[i]);
    }
    //try{
   /* ConfigFinder config = ConfigFinder.getInstance();
    config.setVerbose(true);
    File file = config.locateFile("community_attribute.csv");
    System.out.println(file.getAbsolutePath());*/
    //}catch(IOException e){e.printStackTrace();}
  }

}
