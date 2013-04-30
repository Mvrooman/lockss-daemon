/*
 * $Id: TestSqlDbManager.java,v 1.2 2012/12/07 07:27:05 fergaloy-sf Exp $
 */

/*

 Copyright (c) 2012 Board of Trustees of Leland Stanford Jr. University,
 all rights reserved.

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
 STANFORD UNIVERSITY BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 Except as contained in this notice, the name of Stanford University shall not
 be used in advertising or otherwise to promote the sale, use or other dealings
 in this Software without prior written authorization from Stanford University.

 */

/**
 * Test class for org.lockss.db.SqlDbManager.
 * 
 * @author Fernando Garcia-Loygorri
 * @version 1.0
 */
package org.lockss.db;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import org.lockss.config.ConfigManager;
import org.lockss.repository.LockssRepositoryImpl;
import org.lockss.test.ConfigurationUtil;
import org.lockss.test.LockssTestCase;
import org.lockss.test.MockLockssDaemon;
import org.lockss.util.Logger;

public class TestSqlDbManager extends LockssTestCase {
  private static String TABLE_CREATE_SQL =
      "create table testtable (id bigint NOT NULL, name varchar(512))";

  private MockLockssDaemon theDaemon;
  private String tempDirPath;
  private SqlDbManager sqlDbManager;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    // Get the temporary directory used during the test.
    tempDirPath = getTempDir().getAbsolutePath();

    // Set the database log.
    System.setProperty("derby.stream.error.file",
		       new File(tempDirPath, "derby.log").getAbsolutePath());

    theDaemon = getMockLockssDaemon();
    theDaemon.setDaemonInited(true);
  }

  /**
   * Tests table creation with the minimal configuration.
   * 
   * @throws Exception
   */
  public void testCreateTable1() throws Exception {
    Properties props = new Properties();
    props.setProperty(LockssRepositoryImpl.PARAM_CACHE_LOCATION, tempDirPath);
    props
	.setProperty(ConfigManager.PARAM_PLATFORM_DISK_SPACE_LIST, tempDirPath);
    ConfigurationUtil.setCurrentConfigFromProps(props);

    createTable();
  }

  /**
   * Tests table creation with an absolute database name path.
   * 
   * @throws Exception
   */
  public void testCreateTable2() throws Exception {
    Properties props = new Properties();
    props.setProperty(LockssRepositoryImpl.PARAM_CACHE_LOCATION, tempDirPath);
    props
	.setProperty(ConfigManager.PARAM_PLATFORM_DISK_SPACE_LIST, tempDirPath);
    props.setProperty(SqlDbManager.PARAM_DATASOURCE_DATABASENAME,
		      new File(tempDirPath, "db/TestSqlDbManager")
			  .getCanonicalPath());
    ConfigurationUtil.setCurrentConfigFromProps(props);

    createTable();
  }

  /**
   * Tests table creation with the client datasource.
   * 
   * @throws Exception
   */
  public void testCreateTable3() throws Exception {
    Properties props = new Properties();
    props.setProperty(LockssRepositoryImpl.PARAM_CACHE_LOCATION, tempDirPath);
    props
	.setProperty(ConfigManager.PARAM_PLATFORM_DISK_SPACE_LIST, tempDirPath);
    props.setProperty(SqlDbManager.PARAM_DATASOURCE_CLASSNAME,
		      "org.apache.derby.jdbc.ClientDataSource");
    ConfigurationUtil.setCurrentConfigFromProps(props);

    createTable();
  }

  /**
   * Tests a misconfigured datasource.
   * 
   * @throws Exception
   */
  public void testNotReady() throws Exception {
    Properties props = new Properties();
    props.setProperty(LockssRepositoryImpl.PARAM_CACHE_LOCATION, tempDirPath);
    props
	.setProperty(ConfigManager.PARAM_PLATFORM_DISK_SPACE_LIST, tempDirPath);
    props.setProperty(SqlDbManager.PARAM_DATASOURCE_CLASSNAME, "java.lang.String");
    ConfigurationUtil.setCurrentConfigFromProps(props);

    startService();
    assertEquals(false, sqlDbManager.isReady());

    Connection conn = null;
    try {
      conn = sqlDbManager.getConnection();
    } catch (SQLException sqle) {
    }
    assertNull(conn);

  }

  /**
   * Tests the safe roll back.
   * 
   * @throws Exception
   */
  public void testRollback() throws Exception {
    Properties props = new Properties();
    props.setProperty(LockssRepositoryImpl.PARAM_CACHE_LOCATION, tempDirPath);
    props
	.setProperty(ConfigManager.PARAM_PLATFORM_DISK_SPACE_LIST, tempDirPath);
    ConfigurationUtil.setCurrentConfigFromProps(props);

    startService();
    Connection conn = sqlDbManager.getConnection();
    assertNotNull(conn);
    assertFalse(sqlDbManager.tableExists(conn, "testtable"));
    assertTrue(sqlDbManager.createTableIfMissing(conn, "testtable",
					      TABLE_CREATE_SQL));
    assertTrue(sqlDbManager.tableExists(conn, "testtable"));

    SqlDbManager.safeRollbackAndClose(conn);
    conn = sqlDbManager.getConnection();
    assertNotNull(conn);
    assertFalse(sqlDbManager.tableExists(conn, "testtable"));
  }

  /**
   * Tests the commit or rollback method.
   * 
   * @throws Exception
   */
  public void testCommitOrRollback() throws Exception {
    Properties props = new Properties();
    props.setProperty(LockssRepositoryImpl.PARAM_CACHE_LOCATION, tempDirPath);
    props
	.setProperty(ConfigManager.PARAM_PLATFORM_DISK_SPACE_LIST, tempDirPath);
    ConfigurationUtil.setCurrentConfigFromProps(props);

    startService();
    Connection conn = sqlDbManager.getConnection();
    Logger logger = Logger.getLogger("testCommitOrRollback");
    SqlDbManager.commitOrRollback(conn, logger);
    SqlDbManager.safeCloseConnection(conn);

    conn = null;
    try {
      SqlDbManager.commitOrRollback(conn, logger);
    } catch (NullPointerException sqle) {
    }
  }

  @Override
  public void tearDown() throws Exception {
    sqlDbManager.stopService();
    theDaemon.stopDaemon();
    super.tearDown();
  }

  /**
   * Creates a table and verifies that it exists.
   * 
   * @throws Exception
   */
  protected void createTable() throws Exception {
    startService();
    assertEquals(true, sqlDbManager.isReady());

    Connection conn = sqlDbManager.getConnection();
    assertNotNull(conn);
    assertFalse(sqlDbManager.tableExists(conn, "testtable"));
    assertTrue(sqlDbManager.createTableIfMissing(conn, "testtable",
					      TABLE_CREATE_SQL));
    assertTrue(sqlDbManager.tableExists(conn, "testtable"));
    sqlDbManager.logTableSchema(conn, "testtable");
    assertFalse(sqlDbManager.createTableIfMissing(conn, "testtable",
					       TABLE_CREATE_SQL));
  }

  /**
   * Creates and starts the SqlDbManager.
   * 
   * @throws Exception
   */
  protected void startService() throws Exception {
    sqlDbManager = new SqlDbManager();
    theDaemon.setDbManager(sqlDbManager);
    sqlDbManager.initService(theDaemon);
    sqlDbManager.startService();
  }

  /**
   * Tests an empty database before updating.
   * 
   * @throws Exception
   */
  public void testEmptyDbSetup() throws Exception {
    Properties props = new Properties();
    props.setProperty(LockssRepositoryImpl.PARAM_CACHE_LOCATION, tempDirPath);
    props
	.setProperty(ConfigManager.PARAM_PLATFORM_DISK_SPACE_LIST, tempDirPath);
    ConfigurationUtil.setCurrentConfigFromProps(props);

    sqlDbManager = new SqlDbManager();
    theDaemon.setDbManager(sqlDbManager);
    sqlDbManager.initService(theDaemon);
    assertTrue(sqlDbManager.setUpDatabase(0));
    sqlDbManager.setTargetDatabaseVersion(0);
    sqlDbManager.startService();
    assertTrue(sqlDbManager.isReady());

    Connection conn = sqlDbManager.getConnection();
    assertNotNull(conn);
    assertFalse(sqlDbManager.tableExists(conn, SqlDbManager.OBSOLETE_METADATA_TABLE));
  }

  /**
   * Tests version 1 set up.
   * 
   * @throws Exception
   */
  public void testV1Setup() throws Exception {
    Properties props = new Properties();
    props.setProperty(LockssRepositoryImpl.PARAM_CACHE_LOCATION, tempDirPath);
    props
	.setProperty(ConfigManager.PARAM_PLATFORM_DISK_SPACE_LIST, tempDirPath);
    ConfigurationUtil.setCurrentConfigFromProps(props);

    sqlDbManager = new SqlDbManager();
    theDaemon.setDbManager(sqlDbManager);
    sqlDbManager.initService(theDaemon);
    assertTrue(sqlDbManager.setUpDatabase(1));
    sqlDbManager.setTargetDatabaseVersion(1);
    sqlDbManager.startService();
    assertTrue(sqlDbManager.isReady());

    Connection conn = sqlDbManager.getConnection();
    assertNotNull(conn);
    assertTrue(sqlDbManager.tableExists(conn, SqlDbManager.OBSOLETE_METADATA_TABLE));
  }

  /**
   * Tests an attempt to update the database to a lower version.
   * 
   * @throws Exception
   */
  public void testV1ToV0Update() throws Exception {
    Properties props = new Properties();
    props.setProperty(LockssRepositoryImpl.PARAM_CACHE_LOCATION, tempDirPath);
    props
	.setProperty(ConfigManager.PARAM_PLATFORM_DISK_SPACE_LIST, tempDirPath);
    ConfigurationUtil.setCurrentConfigFromProps(props);

    sqlDbManager = new SqlDbManager();
    theDaemon.setDbManager(sqlDbManager);
    sqlDbManager.initService(theDaemon);
    assertTrue(sqlDbManager.setUpDatabase(1));
    sqlDbManager.setTargetDatabaseVersion(0);
    sqlDbManager.startService();
    assertFalse(sqlDbManager.isReady());
  }

  /**
   * Tests the update of the database from version 0 to version 1.
   * 
   * @throws Exception
   */
  public void testV0ToV1Update() throws Exception {
    Properties props = new Properties();
    props.setProperty(LockssRepositoryImpl.PARAM_CACHE_LOCATION, tempDirPath);
    props
	.setProperty(ConfigManager.PARAM_PLATFORM_DISK_SPACE_LIST, tempDirPath);
    ConfigurationUtil.setCurrentConfigFromProps(props);

    sqlDbManager = new SqlDbManager();
    theDaemon.setDbManager(sqlDbManager);
    sqlDbManager.initService(theDaemon);
    assertTrue(sqlDbManager.setUpDatabase(0));
    sqlDbManager.setTargetDatabaseVersion(1);
    sqlDbManager.startService();
    assertTrue(sqlDbManager.isReady());

    Connection conn = sqlDbManager.getConnection();
    assertNotNull(conn);
    assertTrue(sqlDbManager.tableExists(conn, SqlDbManager.OBSOLETE_METADATA_TABLE));
  }

  /**
   * Tests the update of the database from version 0 to version 2.
   * 
   * @throws Exception
   */
  public void testV0ToV2Update() throws Exception {
    Properties props = new Properties();
    props.setProperty(LockssRepositoryImpl.PARAM_CACHE_LOCATION, tempDirPath);
    props
	.setProperty(ConfigManager.PARAM_PLATFORM_DISK_SPACE_LIST, tempDirPath);
    ConfigurationUtil.setCurrentConfigFromProps(props);

    sqlDbManager = new SqlDbManager();
    theDaemon.setDbManager(sqlDbManager);
    sqlDbManager.initService(theDaemon);
    assertTrue(sqlDbManager.setUpDatabase(0));
    sqlDbManager.setTargetDatabaseVersion(2);
    sqlDbManager.startService();
    assertTrue(sqlDbManager.isReady());

    Connection conn = sqlDbManager.getConnection();
    assertNotNull(conn);
    assertFalse(sqlDbManager.tableExists(conn, SqlDbManager.OBSOLETE_METADATA_TABLE));
    assertTrue(sqlDbManager.tableExists(conn, SqlDbManager.VERSION_TABLE));
  }

  /**
   * Tests the update of the database from version 1 to version 2.
   * 
   * @throws Exception
   */
  public void testV1ToV2Update() throws Exception {
    Properties props = new Properties();
    props.setProperty(LockssRepositoryImpl.PARAM_CACHE_LOCATION, tempDirPath);
    props
	.setProperty(ConfigManager.PARAM_PLATFORM_DISK_SPACE_LIST, tempDirPath);
    ConfigurationUtil.setCurrentConfigFromProps(props);

    sqlDbManager = new SqlDbManager();
    theDaemon.setDbManager(sqlDbManager);
    sqlDbManager.initService(theDaemon);
    assertTrue(sqlDbManager.setUpDatabase(1));
    sqlDbManager.setTargetDatabaseVersion(2);
    sqlDbManager.startService();
    assertTrue(sqlDbManager.isReady());

    Connection conn = sqlDbManager.getConnection();
    assertNotNull(conn);
    assertFalse(sqlDbManager.tableExists(conn, SqlDbManager.OBSOLETE_METADATA_TABLE));
    assertTrue(sqlDbManager.tableExists(conn, SqlDbManager.VERSION_TABLE));
  }

  /**
   * Tests text truncation.
   * 
   * @throws Exception
   */
  public void testTruncation() throws Exception {
    Properties props = new Properties();
    props.setProperty(LockssRepositoryImpl.PARAM_CACHE_LOCATION, tempDirPath);
    props
	.setProperty(ConfigManager.PARAM_PLATFORM_DISK_SPACE_LIST, tempDirPath);
    ConfigurationUtil.setCurrentConfigFromProps(props);

    startService();

    String original = "Total characters = 21";

    String truncated = SqlDbManager.truncateVarchar(original, 30);
    assertTrue(original.equals(truncated));
    assertFalse(SqlDbManager.isTruncatedVarchar(truncated));

    truncated = SqlDbManager.truncateVarchar(original, original.length());
    assertTrue(original.equals(truncated));
    assertFalse(SqlDbManager.isTruncatedVarchar(truncated));

    truncated = SqlDbManager.truncateVarchar(original, original.length() - 3);
    assertFalse(original.equals(truncated));
    assertTrue(SqlDbManager.isTruncatedVarchar(truncated));
    assertTrue(truncated.length() == original.length() - 3);
  }
}
