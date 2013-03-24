/*
 * $Id: SqlMetadataManager.java,v 1.11 2013/01/16 08:07:28 tlipkis Exp $
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
package org.lockss.metadata;

import org.lockss.config.Configuration;
import org.lockss.config.Configuration.Differences;
import org.lockss.daemon.LockssRunnable;
import org.lockss.daemon.status.StatusService;
import org.lockss.db.SqlDbManager;
import org.lockss.extractor.ArticleMetadataExtractor;
import org.lockss.extractor.BaseArticleMetadataExtractor;
import org.lockss.extractor.MetadataTarget;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.AuUtil;
import org.lockss.plugin.Plugin;
import org.lockss.plugin.Plugin.Feature;
import org.lockss.plugin.PluginManager;
import org.lockss.scheduler.Schedule;
import org.lockss.util.PatternIntMap;
import org.lockss.util.StringUtil;
import org.lockss.util.TimeBase;

import java.sql.*;
import java.util.*;

import static java.sql.Types.BIGINT;
import static org.lockss.db.SqlDbManager.*;

/**
 * This class implements a metadata manager that is responsible for managing an
 * index of metadata from AUs.
 * 
 * @author Philip Gust
 * @version 1.0
 */
public class SqlMetadataManager extends MetadataManager {

  /** prefix for config properties */
  public static final String PREFIX = Configuration.PREFIX + "sqlMetadataManager.";

  /**
   * Determines whether MedataExtractor specified by plugin should be used if it
   * is available. If <code>false</code>, a MetaDataExtractor is created that
   * returns data from the TDB rather than from the content metadata. This is
   * faster than extracting metadata form content, but less complete. Use only
   * when minimal article info is required.
   */
  public static final String PARAM_USE_METADATA_EXTRACTOR = PREFIX
      + "use_metadata_extractor";

  /**
   * Default value of SqlMetadataManager use_metadata_extractor configuration
   * parameter; <code>true</code> to use specified MetadataExtractor.
   */
  public static final boolean DEFAULT_USE_METADATA_EXTRACTOR = true;

  /**
   * Determines whether indexing should be enabled. If indexing is not enabled,
   * AUs are queued for indexing, but the AUs are not reindexed until the
   * process is re-enabled. This parameter can be changed at runtime.
   */
  public static final String PARAM_INDEXING_ENABLED = PREFIX
      + "indexing_enabled";

  /**
   * Default value of SqlMetadataManager indexing enabled configuration parameter;
   * <code>false</code> to disable, <code>true</code> to enable.
   */
  public static final boolean DEFAULT_INDEXING_ENABLED = false;

  /**
   * The maximum number of concurrent reindexing tasks. This property can be
   * changed at runtime
   */
  public static final String PARAM_MAX_REINDEXING_TASKS = PREFIX
      + "maxReindexingTasks";

  /** Default maximum concurrent reindexing tasks */
  public static final int DEFAULT_MAX_REINDEXING_TASKS = 1;

  /** Disable allowing crawl to interrupt reindexing tasks */
  public static final String PARAM_DISABLE_CRAWL_RESCHEDULE_TASK = PREFIX
      + "disableCrawlRescheduleTask";

  /** Default disable allowing crawl to interrupt reindexing tasks */
  public static final boolean DEFAULT_DISABLE_CRAWL_RESCHEDULE_TASK = false;

  /**
   * The maximum number reindexing task history. This property can be changed at
   * runtime
   */
  public static final String PARAM_HISTORY_MAX = PREFIX + "historySize";

  /** Default maximum reindexing tasks history */
  public static final int DEFAULT_HISTORY_MAX = 200;

  /** Name of metadata status table */
  public static final String METADATA_STATUS_TABLE_NAME =
      "metadata_status_table";

  /** Map of AUID regexp to priority.  If set, AUs are assigned the
   * corresponding priority of the first regexp that their AUID matches.
   * Priority must be an integer; priorities <= -10000 mean "do not index
   * matching AUs", priorities <= -20000 mean "abort running indexes of
   * matching AUs". (Priorities are not yet implemented - only "do not
   * index" and "abort" are supported.)  */
  static final String PARAM_INDEX_PRIORITY_AUID_MAP =
    PREFIX + "indexPriorityAuidMap";
  static final List<String> DEFAULT_INDEX_PRIORITY_AUID_MAP = null;

  static final int MIN_INDEX_PRIORITY = -10000;
  static final int ABORT_INDEX_PRIORITY = -20000;

  /** Maximum number of AUs to be re-indexed to batch before writing them to the
   * database. */
  public static final String PARAM_MAX_PENDING_TO_REINDEX_AU_BATCH_SIZE =
    PREFIX + "maxPendingToReindexAuBatchSize";
  public static final int DEFAULT_MAX_PENDING_TO_REINDEX_AU_BATCH_SIZE = 1000;

  private static final int UNKNOWN_VERSION = -1;

  /**
   * The initial value of the metadata extraction time for an AU whose metadata
   * has not been extracted yet.
   */
  public static final long NEVER_EXTRACTED_EXTRACTION_TIME = 0L;

  /**
   * The standard type of a name that is primary.
   */
  public static final String PRIMARY_NAME_TYPE = "primary";

  /**
   * The standard type of a name that is not primary.
   */
  public static final String NOT_PRIMARY_NAME_TYPE = "not_primary";

  /**
   * The standard types of ISBNs and ISSNs.
   */
  public static final String E_ISBN_TYPE = "e_isbn";
  public static final String E_ISSN_TYPE = "e_issn";
  public static final String L_ISSN_TYPE = "l_issn";
  public static final String P_ISBN_TYPE = "p_isbn";
  public static final String P_ISSN_TYPE = "p_issn";

  // Query to find a metadata item type by its name.
  private static final String FIND_MD_ITEM_TYPE_QUERY = "select "
      + MD_ITEM_TYPE_SEQ_COLUMN
      + " from " + MD_ITEM_TYPE_TABLE
      + " where " + TYPE_NAME_COLUMN + " = ?";

  // Query to find a publisher by its name.
  private static final String FIND_PUBLISHER_QUERY = "select "
      + PUBLISHER_SEQ_COLUMN
      + " from " + PUBLISHER_TABLE
      + " where " + PUBLISHER_NAME_COLUMN + " = ?";

  // Query to add a publisher.
  private static final String INSERT_PUBLISHER_QUERY = "insert into "
      + PUBLISHER_TABLE
      + "(" + PUBLISHER_SEQ_COLUMN
      + "," + PUBLISHER_NAME_COLUMN
      + ") values (default,?)";

  // Query to find a plugin by its identifier.
  private static final String FIND_PLUGIN_QUERY = "select "
      + PLUGIN_SEQ_COLUMN
      + " from " + PLUGIN_TABLE
      + " where " + PLUGIN_ID_COLUMN + " = ?";

  // Query to add a plugin.
  private static final String INSERT_PLUGIN_QUERY = "insert into "
      + PLUGIN_TABLE
      + "(" + PLUGIN_SEQ_COLUMN
      + "," + PLUGIN_ID_COLUMN
      + "," + PLATFORM_COLUMN
      + ") values (default,?,?)";

  // Query to find an Archival Unit by its plugin and key.
  private static final String FIND_AU_QUERY = "select "
      + AU_SEQ_COLUMN
      + " from " + AU_TABLE
      + " where " + PLUGIN_SEQ_COLUMN + " = ?"
      + " and " + AU_KEY_COLUMN + " = ?";

  // Query to add an Archival Unit.
  private static final String INSERT_AU_QUERY = "insert into "
      + AU_TABLE
      + "(" + AU_SEQ_COLUMN
      + "," + PLUGIN_SEQ_COLUMN
      + "," + AU_KEY_COLUMN
      + ") values (default,?,?)";

  // Query to find the metadata entry of an Archival Unit.
  private static final String FIND_AU_MD_QUERY = "select "
      + AU_MD_SEQ_COLUMN
      + " from " + AU_MD_TABLE
      + " where " + AU_SEQ_COLUMN + " = ?";

  // Query to add an Archival Unit metadata entry.
  private static final String INSERT_AU_MD_QUERY = "insert into "
      + AU_MD_TABLE
      + "(" + AU_MD_SEQ_COLUMN
      + "," + AU_SEQ_COLUMN
      + "," + MD_VERSION_COLUMN
      + "," + EXTRACT_TIME_COLUMN
      + ") values (default,?,?,?)";

  // Query to add a metadata item.
  private static final String INSERT_MD_ITEM_QUERY = "insert into "
      + MD_ITEM_TABLE
      + "(" + MD_ITEM_SEQ_COLUMN
      + "," + PARENT_SEQ_COLUMN
      + "," + MD_ITEM_TYPE_SEQ_COLUMN
      + "," + AU_MD_SEQ_COLUMN
      + "," + DATE_COLUMN
      + "," + COVERAGE_COLUMN
      + ") values (default,?,?,?,?,?)";

  // Query to add a publication.
  private static final String INSERT_PUBLICATION_QUERY = "insert into "
      + PUBLICATION_TABLE
      + "(" + PUBLICATION_SEQ_COLUMN
      + "," + MD_ITEM_SEQ_COLUMN
      + "," + PUBLISHER_SEQ_COLUMN
      + "," + PUBLICATION_ID_COLUMN
      + ") values (default,?,?,?)";

  // Query to add a metadata item name.
  private static final String INSERT_MD_ITEM_NAME_QUERY = "insert into "
      + MD_ITEM_NAME_TABLE
      + "(" + MD_ITEM_SEQ_COLUMN
      + "," + NAME_COLUMN
      + "," + NAME_TYPE_COLUMN
      + ") values (?,?,?)";
	
  // Query to add an ISSN.
  private static final String INSERT_ISSN_QUERY = "insert into "
      + ISSN_TABLE
      + "(" + MD_ITEM_SEQ_COLUMN
      + "," + ISSN_COLUMN
      + "," + ISSN_TYPE_COLUMN
      + ") values (?,?,?)";
	
  // Query to add an ISBN.
  private static final String INSERT_ISBN_QUERY = "insert into "
      + ISBN_TABLE
      + "(" + MD_ITEM_SEQ_COLUMN
      + "," + ISBN_COLUMN
      + "," + ISBN_TYPE_COLUMN
      + ") values (?,?,?)";

  // Query to find a publication by its ISSNs.
  private static final String FIND_PUBLICATION_BY_ISSNS_QUERY = "select"
      + " p." + PUBLICATION_SEQ_COLUMN
      + " from " + PUBLICATION_TABLE + " p,"
      + ISSN_TABLE + " i,"
      + MD_ITEM_TABLE + " m,"
      + MD_ITEM_TYPE_TABLE + " t"
      + " where p." + PUBLISHER_SEQ_COLUMN + " = ?"
      + " and m." + AU_MD_SEQ_COLUMN + " is null"
      + " and p." + MD_ITEM_SEQ_COLUMN + " = i." + MD_ITEM_SEQ_COLUMN
      + " and (i." + ISSN_COLUMN + " = ?"
      + " or i." + ISSN_COLUMN + " = ?)"
      + " and p." + MD_ITEM_SEQ_COLUMN + " = m." + MD_ITEM_SEQ_COLUMN
      + " and m." + MD_ITEM_TYPE_SEQ_COLUMN + " = t." + MD_ITEM_TYPE_SEQ_COLUMN
      + " and t." + TYPE_NAME_COLUMN + " = ?";

  // Query to find a publication by its ISBNs.
  private static final String FIND_PUBLICATION_BY_ISBNS_QUERY = "select"
      + " p." + PUBLICATION_SEQ_COLUMN
      + " from " + PUBLICATION_TABLE + " p,"
      + ISBN_TABLE + " i,"
      + MD_ITEM_TABLE + " m,"
      + MD_ITEM_TYPE_TABLE + " t"
      + " where p." + PUBLISHER_SEQ_COLUMN + " = ?"
      + " and m." + AU_MD_SEQ_COLUMN + " is null"
      + " and p." + MD_ITEM_SEQ_COLUMN + " = i." + MD_ITEM_SEQ_COLUMN
      + " and (i." + ISBN_COLUMN + " = ?"
      + " or i." + ISBN_COLUMN + " = ?)"
      + " and p." + MD_ITEM_SEQ_COLUMN + " = m." + MD_ITEM_SEQ_COLUMN
      + " and m." + MD_ITEM_TYPE_SEQ_COLUMN + " = t." + MD_ITEM_TYPE_SEQ_COLUMN
      + " and t." + TYPE_NAME_COLUMN + " = ?";

  // Query to find a publication by its name.
  private static final String FIND_PUBLICATION_BY_NAME_QUERY =
      "select p." + PUBLICATION_SEQ_COLUMN
      + " from " + PUBLICATION_TABLE + " p,"
      + MD_ITEM_TABLE + " m,"
      + MD_ITEM_NAME_TABLE + " n,"
      + MD_ITEM_TYPE_TABLE + " t"
      + " where p." + PUBLISHER_SEQ_COLUMN + " = ?"
      + " and m." + AU_MD_SEQ_COLUMN + " is null"
      + " and p." + MD_ITEM_SEQ_COLUMN + " = m." + MD_ITEM_SEQ_COLUMN
      + " and p." + MD_ITEM_SEQ_COLUMN + " = n." + MD_ITEM_SEQ_COLUMN
      + " and n." + NAME_COLUMN + " = ?"
      + " and p." + MD_ITEM_SEQ_COLUMN + " = m." + MD_ITEM_SEQ_COLUMN
      + " and m." + MD_ITEM_TYPE_SEQ_COLUMN + " = t." + MD_ITEM_TYPE_SEQ_COLUMN
      + " and t." + TYPE_NAME_COLUMN + " = ?";

  // Query to find the metadata item of a publication.
  private static final String FIND_PUBLICATION_METADATA_ITEM_QUERY = "select "
      + MD_ITEM_SEQ_COLUMN
      + " from " + PUBLICATION_TABLE
      + " where " + PUBLICATION_SEQ_COLUMN + " = ?";

  // Query to find the secondary names of a metadata item.
  private static final String FIND_MD_ITEM_NAME_QUERY = "select "
      + NAME_COLUMN
      + "," + NAME_TYPE_COLUMN
      + " from " + MD_ITEM_NAME_TABLE
      + " where " + MD_ITEM_SEQ_COLUMN + " = ?";

  // Query to find the ISSNs of a metadata item.
  private static final String FIND_MD_ITEM_ISSN_QUERY = "select "
      + ISSN_COLUMN
      + "," + ISSN_TYPE_COLUMN
      + " from " + ISSN_TABLE
      + " where " + MD_ITEM_SEQ_COLUMN + " = ?";

  // Query to find the ISBNs of a metadata item.
  private static final String FIND_MD_ITEM_ISBN_QUERY = "select "
      + ISBN_COLUMN
      + "," + ISBN_TYPE_COLUMN
      + " from " + ISBN_TABLE
      + " where " + MD_ITEM_SEQ_COLUMN + " = ?";

  // Query to update the extraction time of the metadata of an Archival Unit.
  private static final String UPDATE_AU_MD_EXTRACT_TIME_QUERY = "update "
      + AU_MD_TABLE
      + " set " + EXTRACT_TIME_COLUMN + " = ?"
      + " where " + AU_MD_SEQ_COLUMN + " = ?";

  // Query to count bibliographic items.
  private static final String COUNT_BIB_ITEM_QUERY = "select count(*) from "
      + BIB_ITEM_TABLE;

  // Query to delete metadata items by Archival Unit key and plugin identifier.
  private static final String DELETE_MD_ITEM_QUERY = "delete from "
      + MD_ITEM_TABLE
      + " where "
      + AU_MD_SEQ_COLUMN + " in ("
      + "select m." + AU_MD_SEQ_COLUMN
      + " from " + AU_MD_TABLE + " m,"
      + AU_TABLE + " a,"
      + PLUGIN_TABLE + " p"
      + " where m." + AU_SEQ_COLUMN + " = " + "a." + AU_SEQ_COLUMN
      + " and a." + PLUGIN_SEQ_COLUMN + " = " + "p." + PLUGIN_SEQ_COLUMN
      + " and p." + PLUGIN_ID_COLUMN + " = ?"
      + " and a." + AU_KEY_COLUMN + " = ?)";

  // Query to delete an AU by Archival Unit key and plugin identifier.
  private static final String DELETE_AU_QUERY = "delete from " + AU_TABLE
      + " where "
      + PLUGIN_SEQ_COLUMN + " in ("
      + "select a." + PLUGIN_SEQ_COLUMN
      + " from " + AU_TABLE + " a,"
      + PLUGIN_TABLE + " p"
      + " where a." + PLUGIN_SEQ_COLUMN + " = " + "p." + PLUGIN_SEQ_COLUMN
      + " and p." + PLUGIN_ID_COLUMN + " = ?)"
      + " and " + AU_KEY_COLUMN + " = ?";

  // Query to add a metadata item URL.
  private static final String INSERT_URL_QUERY = "insert into "
      + URL_TABLE
      + "(" + MD_ITEM_SEQ_COLUMN
      + "," + FEATURE_COLUMN
      + "," + URL_COLUMN
      + ") values (?,?,?)";

  // Query to add a metadata item DOI.
  private static final String INSERT_DOI_QUERY = "insert into "
      + DOI_TABLE
      + "(" + MD_ITEM_SEQ_COLUMN
      + "," + DOI_COLUMN
      + ") values (?,?)";

  // Query to count enabled pending AUs.
  private static final String COUNT_ENABLED_PENDING_AUS_QUERY = "select "
      + "count(*) from " + PENDING_AU_TABLE
      + " where " + PRIORITY_COLUMN + " > " + MIN_INDEX_PRIORITY;
  
  // Query to find enabled pending AUs sorted by priority.
  private static final String FIND_PRIORITIZED_PENDING_AUS_QUERY = "select "
      + PLUGIN_ID_COLUMN
      + "," + AU_KEY_COLUMN
      + "," + PRIORITY_COLUMN
      + " from " + PENDING_AU_TABLE
      + " where " + PRIORITY_COLUMN + " > " + MIN_INDEX_PRIORITY
      + " order by " + PRIORITY_COLUMN;
  
  // Query to find a pending AU by its key and plugin identifier.
  private static final String FIND_PENDING_AU_QUERY = "select "
      + PLUGIN_ID_COLUMN
      + "," + AU_KEY_COLUMN
      + " from " + PENDING_AU_TABLE
      + " where " + PLUGIN_ID_COLUMN + " = ?"
      + " and " + AU_KEY_COLUMN + " = ?"
      ;

  // Query to add an enabled pending AU at the bottom of the current priority
  // list.
  private static final String INSERT_ENABLED_PENDING_AU_QUERY = "insert into "
      + PENDING_AU_TABLE
      + "(" + PLUGIN_ID_COLUMN
      + "," + AU_KEY_COLUMN
      + "," + PRIORITY_COLUMN
      + ") values (?,?,"
      + "(select coalesce(max(" + PRIORITY_COLUMN + "), 0) + 1"
      + " from " + PENDING_AU_TABLE
      + " where " + PRIORITY_COLUMN + " > " + MIN_INDEX_PRIORITY + "))";

  // Query to add a disabled pending AU.
  private static final String INSERT_DISABLED_PENDING_AU_QUERY = "insert into "
      + PENDING_AU_TABLE
      + "(" + PLUGIN_ID_COLUMN
      + "," + AU_KEY_COLUMN
      + "," + PRIORITY_COLUMN
      + ") values (?,?," + MIN_INDEX_PRIORITY + ")";

  // Query to delete a pending AU by its key and plugin identifier.
  private static final String DELETE_PENDING_AU_QUERY = "delete from "
      + PENDING_AU_TABLE
      + " where " + PLUGIN_ID_COLUMN + " = ?"
      + " and " + AU_KEY_COLUMN + " = ?";

  // Query to delete a disabled pending AU by its key and plugin identifier.
  private static final String DELETE_DISABLED_PENDING_AU_QUERY = "delete from "
      + PENDING_AU_TABLE
      + " where " + PLUGIN_ID_COLUMN + " = ?"
      + " and " + AU_KEY_COLUMN + " = ?"
      + " and " + PRIORITY_COLUMN + " <= " + MIN_INDEX_PRIORITY;

  // Query to get the version of the metadata of an AU as is recorded in the
  // database.
  private static final String FIND_AU_METADATA_VERSION_QUERY = "select m."
      + MD_VERSION_COLUMN
      + " from " + AU_MD_TABLE + " m,"
      + AU_TABLE + " a,"
      + PLUGIN_TABLE + " p"
      + " where m." + AU_SEQ_COLUMN + " = " + " a." + AU_SEQ_COLUMN
      + " and a." + PLUGIN_SEQ_COLUMN + " = " + " p." + PLUGIN_SEQ_COLUMN
      + " and p." + PLUGIN_ID_COLUMN + " = ?"
      + " and a." + AU_KEY_COLUMN + " = ?";

  // Query to find the extraction time of an Archival Unit.
  private static final String FIND_AU_MD_EXTRACT_TIME_BY_AUSEQ_QUERY = "select "
      + EXTRACT_TIME_COLUMN
      + " from " + AU_MD_TABLE
      + " where " + AU_SEQ_COLUMN + " = ?";

  // Query to find the extraction time of an Archival Unit.
  private static final String FIND_AU_MD_EXTRACT_TIME_BY_AU_QUERY = "select m."
      + EXTRACT_TIME_COLUMN
      + " from " + AU_MD_TABLE + " m,"
      + AU_TABLE + " a,"
      + PLUGIN_TABLE + " p"
      + " where m." + AU_SEQ_COLUMN + " = " + " a." + AU_SEQ_COLUMN
      + " and a." + PLUGIN_SEQ_COLUMN + " = " + " p." + PLUGIN_SEQ_COLUMN
      + " and p." + PLUGIN_ID_COLUMN + " = ?"
      + " and a." + AU_KEY_COLUMN + " = ?";

  /**
   * Map of running reindexing tasks keyed by their AuIds
   */
  final Map<String, ReindexingTask> activeReindexingTasks =
      new HashMap<String, ReindexingTask>();

  /**
   * List of reindexing tasks in order from most recent (0) to least recent.
   */
  final List<ReindexingTask> reindexingTaskHistory =
      new ArrayList<ReindexingTask>();

  /**
   * Metadata manager indexing enabled flag.  Initial value should always
   * be false, independent of DEFAULT_INDEXING_ENABLED, so
   * setIndexingEnabled() sees a transition.
   */
  boolean reindexingEnabled = false;

  /**
   * XXX temporary one-time startup
   */
  boolean everEnabled = false;

  /**
   * Metadata manager use metadata extractor flag. Note: set this to false only
   * where specific metadata from the metadata extractor are not needed.
   */
  boolean useMetadataExtractor = DEFAULT_USE_METADATA_EXTRACTOR;

  /** Maximum number of reindexing tasks */
  int maxReindexingTasks = DEFAULT_MAX_REINDEXING_TASKS;

  /** Disable crawl completion rescheduling a running task for same AU */
  boolean disableCrawlRescheduleTask = DEFAULT_DISABLE_CRAWL_RESCHEDULE_TASK;

  // The number of articles currently in the metadata database.
  private long metadataArticleCount = 0;

  // The number of AUs pending to be reindexed.
  private long pendingAusCount = 0;

  // The number of successful reindexing operations.
  private long successfulReindexingCount = 0;

  // The number of failed reindexing operations.
  private long failedReindexingCount = 0;

  private int maxReindexingTaskHistory = DEFAULT_HISTORY_MAX;

  // The plugin manager.
  private PluginManager pluginMgr = null;

  // The database manager.
  private SqlDbManager sqlDbManager = null;

  private PatternIntMap indexPriorityAuidMap;

  private int maxPendingAuBatchSize =
      DEFAULT_MAX_PENDING_TO_REINDEX_AU_BATCH_SIZE;

  private int pendingAuBatchCurrentSize = 0;

  private Connection pendingAusBatchConnection = null;
  private PreparedStatement insertPendingAuBatchStatement = null;

  /** enumeration status for reindexing tasks */
  public enum ReindexingStatus {
    Running, // if the reindexing task is running
    Success, // if the reindexing task was successful
    Failed, // if the reindexing task failed
    Rescheduled // if the reindexing task was rescheduled
  };

  /**
   * Starts the SqlMetadataManager service.
   */
  @Override
  public void startService() {
    final String DEBUG_HEADER = "startService(): ";
    log.debug(DEBUG_HEADER + "Starting SqlMetadataManager");

    pluginMgr = getDaemon().getPluginManager();
    sqlDbManager = getDaemon().getDbManager();

    // Get a connection to the database.
    Connection conn;

    try {
      conn = sqlDbManager.getConnection();
    } catch (SQLException ex) {
      log.error("Cannot connect to database", ex);
      return;
    }

    // Initialize the counts of articles and pending AUs from database.
    try {
      pendingAusCount = getEnabledPendingAusCount(conn);
      metadataArticleCount = getArticleCount(conn);
    } catch (SQLException ex) {
      log.error("Cannot get pending AUs and article counts", ex);
    }

    SqlDbManager.safeRollbackAndClose(conn);

    StatusService statusServ = getDaemon().getStatusService();
    statusServ.registerStatusAccessor(METADATA_STATUS_TABLE_NAME,
        new MetadataManagerStatusAccessor(this));
    statusServ.registerOverviewAccessor(METADATA_STATUS_TABLE_NAME,
        new MetadataManagerStatusAccessor.IndexingOverview(this));

    resetConfig();
    log.debug(DEBUG_HEADER + "SqlMetadataManager service successfully started");
  }

  /** Start the starter thread, which waits for AUs to be started,
   * registers AuEvent handler and performs initial scan of AUs
   */
  void startStarter() {
    MetadataStarter starter = new MetadataStarter(sqlDbManager, this, pluginMgr);
    new Thread(starter).start();
  }

  /**
   * Provides the number of enabled pending AUs.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @return a long with the number of enabled pending AUs.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private long getEnabledPendingAusCount(Connection conn) throws SQLException {
    final String DEBUG_HEADER = "getEnabledPendingAusCount(): ";
    long rowCount = -1;

    PreparedStatement stmt =
	sqlDbManager.prepareStatement(conn, COUNT_ENABLED_PENDING_AUS_QUERY);
    ResultSet resultSet = null;

    try {
      resultSet = sqlDbManager.executeQuery(stmt);
      resultSet.next();
      rowCount = resultSet.getLong(1);
    } catch (SQLException sqle) {
      log.error("Cannot get the count of enabled pending AUs", sqle);
      log.error("SQL = '" + COUNT_ENABLED_PENDING_AUS_QUERY + "'.");
      throw sqle;
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      SqlDbManager.safeCloseStatement(stmt);
    }

    log.debug3(DEBUG_HEADER + "rowCount = " + rowCount);
    return rowCount;
  }
  
//  /** TODO -- CLEAN UP  to see if we need it....
//   * Provides the number of enabled pending AUs.
//   * 
//   * @return a long with the number of enabled pending AUs.
//   * @throws SQLException
//   *           if any problem occurred accessing the database.
//   */
//  @Override
//  public long getEnabledPendingAusCount() throws SQLException {
//	  Connection conn = sqlDbManager.getConnection();
//	  return getEnabledPendingAusCount(conn);
//	  
//  }
  /**
   * Provides the number of articles in the metadata database.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @return a long with the number of articles in the metadata database.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private long getArticleCount(Connection conn) throws SQLException {
    final String DEBUG_HEADER = "getArticleCount(): ";
    long rowCount = -1;

    PreparedStatement stmt =
	sqlDbManager.prepareStatement(conn, COUNT_BIB_ITEM_QUERY);
    ResultSet resultSet = null;
    try {
      resultSet = sqlDbManager.executeQuery(stmt);
      resultSet.next();
      rowCount = resultSet.getLong(1);
    } catch (SQLException sqle) {
      log.error("Cannot get the count of articles", sqle);
      log.error("SQL = '" + COUNT_BIB_ITEM_QUERY + "'.");
      throw sqle;
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      SqlDbManager.safeCloseStatement(stmt);
    }

    log.debug3(DEBUG_HEADER + "rowCount = " + rowCount);
    return rowCount;
  }

  /**
   * Handles new configuration.
   * 
   * @param config
   *          the new configuration
   * @param prevConfig
   *          the previous configuration
   * @changedKeys the configuration keys that changed
   */
  @Override
  public void setConfig(Configuration config, Configuration prevConfig,
      Differences changedKeys) {
    if (changedKeys.contains(PREFIX)) {
      useMetadataExtractor =
	  config.getBoolean(PARAM_USE_METADATA_EXTRACTOR,
			    DEFAULT_USE_METADATA_EXTRACTOR);
      maxReindexingTasks =
	  Math.max(0, config.getInt(PARAM_MAX_REINDEXING_TASKS,
				    DEFAULT_MAX_REINDEXING_TASKS));
      disableCrawlRescheduleTask =
	  config.getBoolean(PARAM_DISABLE_CRAWL_RESCHEDULE_TASK,
			    DEFAULT_DISABLE_CRAWL_RESCHEDULE_TASK);
      if (isDaemonInited()) {
	boolean doEnable =
	  config.getBoolean(PARAM_INDEXING_ENABLED, DEFAULT_INDEXING_ENABLED);
	setIndexingEnabled(doEnable);
      }

      if (changedKeys.contains(PARAM_HISTORY_MAX)) {
	int histSize = config.getInt(PARAM_HISTORY_MAX, DEFAULT_HISTORY_MAX);
	setMaxHistory(histSize);
      }

      if (changedKeys.contains(PARAM_INDEX_PRIORITY_AUID_MAP)) {
	installIndexPriorityAuidMap((List<String>) (config
	    .getList(PARAM_INDEX_PRIORITY_AUID_MAP,
		     DEFAULT_INDEX_PRIORITY_AUID_MAP)));

	if (isInited()) {
	  processAbortPriorities();
	  // process queued AUs in case any are newly eligible
	  startReindexing();
	}
      }

      if (changedKeys.contains(PARAM_MAX_PENDING_TO_REINDEX_AU_BATCH_SIZE)) {
	maxPendingAuBatchSize =
	    config.getInt(PARAM_MAX_PENDING_TO_REINDEX_AU_BATCH_SIZE,
			  DEFAULT_MAX_PENDING_TO_REINDEX_AU_BATCH_SIZE);
      }
    }
  }

  /**
   * Sets the indexing enabled state of this manager.
   * 
   * @param enable
   *          A boolean with the new enabled state of this manager.
   */
  void setIndexingEnabled(boolean enable) {
    final String DEBUG_HEADER = "setIndexingEnabled(): ";
    log.debug(DEBUG_HEADER + "enabled: " + enable);

    // Start or stop reindexing if initialized.
    if (sqlDbManager != null) {
      if (!reindexingEnabled && enable) {
	if (everEnabled) {
        // Start reindexing
	  startReindexing();
	} else {
	  // start first-time startup thread (XXX needs to be periodic?)
	  startStarter();
	  everEnabled = true;
	}
	reindexingEnabled = enable;
      } else if (reindexingEnabled && !enable) {
        // Stop any pending reindexing operations
        stopReindexing();
      }
      reindexingEnabled = enable;
    }
  }

  /**
   * Sets the maximum reindexing task history list size.
   * 
   * @param maxSize
   *          An int with the maximum reindexing task history list size.
   */
  private void setMaxHistory(int maxSize) {
    maxReindexingTaskHistory = maxSize;

    synchronized (reindexingTaskHistory) {
      while (reindexingTaskHistory.size() > maxReindexingTaskHistory) {
        reindexingTaskHistory.remove(maxReindexingTaskHistory);
      }
    }
  }

  /**
   * Sets up the index priority map.
   * 
   * @param patternPairs A List<String> with the patterns.
   */
  private void installIndexPriorityAuidMap(List<String> patternPairs) {
    if (patternPairs == null) {
      log.debug("Installing empty index priority map");
      indexPriorityAuidMap = PatternIntMap.EMPTY;
    } else {
      try {
	indexPriorityAuidMap = new PatternIntMap(patternPairs);
	log.debug("Installing index priority map: " + indexPriorityAuidMap);
      } catch (IllegalArgumentException e) {
	log.error("Illegal index priority map, ignoring", e);
	log.error("Index priority map unchanged: " + indexPriorityAuidMap);
      }
    }
  }

  /**
   * For all the entries in indexPriorityAuidMap whose priority is less than
   * ABORT_INDEX_PRIORITY, collect those that are new (not in
   * prevIndexPriorityAuidMap), then abort all indexes matching any of those
   * patterns.
   */
  private void processAbortPriorities() {
    List<ArchivalUnit> abortAus = new ArrayList<ArchivalUnit>();

    synchronized (activeReindexingTasks) {
      for (ReindexingTask task : activeReindexingTasks.values()) {
	ArchivalUnit au = task.getAu();

	if (indexPriorityAuidMap.getMatch(au.getAuId()) <
	    ABORT_INDEX_PRIORITY) {
	  abortAus.add(au);
	}
      }
    }

    for (ArchivalUnit au : abortAus) {
      log.info("Aborting indexing: " + au);
      cancelAuTask(au.getAuId());
    }
  }

  /**
   * Ensures that as many reindexing tasks as possible are running if the
   * manager is enabled.
   * 
   * @return an int with the number of reindexing tasks started.
   */
  private int startReindexing() {
    final String DEBUG_HEADER = "startReindexing(): ";

    Connection conn = null;
    int count = 0;
    try {
      conn = sqlDbManager.getConnection();
      count = startReindexing(conn);
      conn.commit();
    } catch (SQLException sqle) {
      log.error("Cannot start reindexing", sqle);
    } finally {
      SqlDbManager.safeRollbackAndClose(conn);
    }

    log.debug3(DEBUG_HEADER + "count = " + count);
    return count;
  }

  /**
   * Stops any pending reindexing operations.
   */
  private void stopReindexing() {
    final String DEBUG_HEADER = "stopReindexing(): ";
    log.debug(DEBUG_HEADER + "Number of reindexing tasks being stopped: "
        + activeReindexingTasks.size());

    // Quit any running reindexing tasks.
    synchronized (activeReindexingTasks) {
      for (ReindexingTask task : activeReindexingTasks.values()) {
        task.cancel();
      }

      activeReindexingTasks.clear();
    }
  }

  /**
   * Cancels the reindexing task for the specified AU.
   * 
   * @param auId
   *          A String with the AU identifier.
   * @return a boolean with <code>true</code> if task was canceled,
   *         <code>false</code> otherwise.
   */
  private boolean cancelAuTask(String auId) {
    final String DEBUG_HEADER = "cancelAuTask(): ";
    ReindexingTask task = activeReindexingTasks.get(auId);

    if (task != null) {
      // task cancellation will remove task and schedule next one
      log.debug2(DEBUG_HEADER + "Canceling pending reindexing task for auId "
	  + auId);
      task.cancel();
      return true;
    }

    return false;
  }

  /**
   * Ensures that as many re-indexing tasks as possible are running if the
   * manager is enabled.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @return an int with the number of reindexing tasks started.
   */
  int startReindexing(Connection conn) {
    final String DEBUG_HEADER = "startReindexing(): ";

    if (!getDaemon().isDaemonInited()) {
      log.debug(DEBUG_HEADER + "Daemon not initialized: No reindexing tasks.");
      return 0;
    }

    // Don't run reindexing tasks run if reindexing is disabled.
    if (!reindexingEnabled) {
      log.debug(DEBUG_HEADER
	  + "Metadata manager reindexing is disabled: No reindexing tasks.");
      return 0;
    }

    int reindexedTaskCount = 0;

    synchronized (activeReindexingTasks) {
      // Try to add more concurrent reindexing tasks as long as the maximum
      // number of them is not reached.
      while (activeReindexingTasks.size() < maxReindexingTasks) {
        // Get the list of pending AUs to reindex.
	List<String> auIds =
	    getPrioritizedAuIdsToReindex(conn, maxReindexingTasks
		- activeReindexingTasks.size());

	// Nothing more to do if there are no pending AUs to reindex.
        if (auIds.isEmpty()) {
          break;
        }

        // Loop through all the pending AUs. 
        for (String auId : auIds) {
          // Get the next pending AU.
          ArchivalUnit au = pluginMgr.getAuFromId(auId);

          // Check whether it does not exist.
          if (au == null) {
	    // Yes: Cancel any running tasks associated with the AU and delete
	    // the AU metadata.
            try {
              int count = deleteAu(conn, auId);
              notifyDeletedAu(auId, count);
            } catch (SQLException sqle) {
	      log.error("Error removing AU for auId " + auId
		  + " from the table of pending AUs", sqle);
            }
          } else {
            // No: Get the metadata extractor.
            ArticleMetadataExtractor ae = getMetadataExtractor(au);

            // Check whether it does not exist.
            if (ae == null) {
	      // Yes: It shouldn't happen because it was checked before adding
	      // the AU to the pending AUs list.
	      log.debug(DEBUG_HEADER + "Not running reindexing task for AU '"
		  + au.getName() + "' because it nas no metadata extractor");

	      // Remove it from the table of pending AUs.
              try {
                removeFromPendingAus(conn, au.getAuId());
              } catch (SQLException sqle) {
                log.error("Error removing AU " + au.getName()
                          + " from the table of pending AUs", sqle);
                break;
              }
            } else {
              // No: Schedule the pending AU.
	      log.debug3(DEBUG_HEADER + "Creating the reindexing task for AU: "
		  + au.getName());
              ReindexingTask task = new ReindexingTask(au, ae);
              activeReindexingTasks.put(au.getAuId(), task);

              // Add the reindexing task to the history; limit history list
              // size.
              addToHistory(task);

	      log.debug(DEBUG_HEADER + "Running the reindexing task for AU: "
		  + au.getName());
              runReindexingTask(task);
              reindexedTaskCount++;
            }
          }
        }
      }
    }

    log.debug(DEBUG_HEADER + "Started " + reindexedTaskCount
              + " AU reindexing tasks");
    return reindexedTaskCount;
  }

  /**
   * Provides a list of AuIds that require reindexing sorted by priority.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param maxAuIds
   *          An int with the maximum number of AuIds to return.
   * @return a List<String> with the list of AuIds that require reindexing
   *         sorted by priority.
   */
  
  private List<String> getPrioritizedAuIdsToReindex(Connection conn,
                                                   int maxAuIds) {
    final String DEBUG_HEADER = "getPrioritizedAuIdsToReindex(): ";
    ArrayList<String> auIds = new ArrayList<String>();

    if (pluginMgr != null) {
      PreparedStatement selectPendingAus = null;
      ResultSet results = null;

      try {
	selectPendingAus = sqlDbManager.prepareStatement(conn,
	    FIND_PRIORITIZED_PENDING_AUS_QUERY);
        results = sqlDbManager.executeQuery(selectPendingAus);

        while ((auIds.size() < maxAuIds) && results.next()) {
          String pluginId = results.getString(PLUGIN_ID_COLUMN);
          log.debug2(DEBUG_HEADER + "pluginId = " + pluginId);
          String auKey = results.getString(AU_KEY_COLUMN);
          log.debug2(DEBUG_HEADER + "auKey = " + auKey);
          log.debug2(DEBUG_HEADER + "priority = "
              + results.getString(PRIORITY_COLUMN));
          String auId = PluginManager.generateAuId(pluginId, auKey);

	  if (isEligibleForReindexing(auId)) {
	    if (!activeReindexingTasks.containsKey(auId)) {
	      auIds.add(auId);
	      log.debug2(DEBUG_HEADER + "Added auId = " + auId
		  + " to reindex list");
	    }
	  }
	}
      } catch (SQLException sqle) {
	log.error("Cannot identify the enabled pending AUs", sqle);
	log.error("SQL = '" + FIND_PRIORITIZED_PENDING_AUS_QUERY + "'.");
      } finally {
        SqlDbManager.safeCloseResultSet(results);
        SqlDbManager.safeCloseStatement(selectPendingAus);
      }
    }

    auIds.trimToSize();
    return auIds;
  }
  
  /**
   * Provides a list of AuIds that require reindexing sorted by priority.
   * 
   * @param maxAuIds
   *          An int with the maximum number of AuIds to return.
   * @return a List<String> with the list of AuIds that require reindexing
   *         sorted by priority.
   */
  @Override
  public List<String> getPrioritizedAuIdsToReindex(int maxAuIds) throws SQLException
  {
	  Connection conn = sqlDbManager.getConnection();
	  return getPrioritizedAuIdsToReindex(conn, maxAuIds);
	  
  }

  /**
   * Cancels any running tasks associated with an AU and deletes the AU
   * metadata.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param auId
   *          A String with the AU identifier.
   * @return an int with the number of articles deleted.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private int deleteAu(Connection conn, String auId) throws SQLException {
    final String DEBUG_HEADER = "deleteAu(): ";
    log.debug3(DEBUG_HEADER + "auid = " + auId);
    cancelAuTask(auId);

    // Remove from the history list
    removeFromHistory(auId);

    // Remove the metadata for this AU.
    int articleCount = removeAuMetadataItems(conn, auId);
    log.debug3(DEBUG_HEADER + "articleCount = " + articleCount);

    removeAu(conn, auId);

    // Remove pending reindexing operations for this AU.
    removeFromPendingAus(conn, auId);

    notifyDeletedAu(auId, articleCount);

    return articleCount;
  }

  /**
   * Notify listeners that an AU has been removed.
   * 
   * @param auId the AuId of the AU that was removed
   * @param articleCount the number of articles deleted
   */
  protected void notifyDeletedAu(String auId, int articleCount) {
  }

  /**
   * Provides the ArticleMetadataExtractor for the specified AU.
   * 
   * @param au
   *          An ArchivalUnit with the AU.
   * @return an ArticleMetadataExtractor with the article metadata extractor.
   */
  private ArticleMetadataExtractor getMetadataExtractor(ArchivalUnit au) {
    ArticleMetadataExtractor ae = null;

    if (useMetadataExtractor) {
      Plugin plugin = au.getPlugin();
      ae = plugin.getArticleMetadataExtractor(MetadataTarget.OpenURL(), au);
    }

    if (ae == null) {
      ae = new BaseArticleMetadataExtractor(null);
    }

    return ae;
  }

  /**
   * Removes an AU from the pending Aus table.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param auId
   *          A String with the AU identifier.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  void removeFromPendingAus(Connection conn, String auId) throws SQLException {
    PreparedStatement deletePendingAu =
	sqlDbManager.prepareStatement(conn, DELETE_PENDING_AU_QUERY);

    try {
      String pluginId = PluginManager.pluginIdFromAuId(auId);
      String auKey = PluginManager.auKeyFromAuId(auId);
  
      deletePendingAu.setString(1, pluginId);
      deletePendingAu.setString(2, auKey);
      sqlDbManager.executeUpdate(deletePendingAu);
    } catch (SQLException sqle) {
      log.error("Cannot remove AU from pending table", sqle);
      log.error("auId = '" + auId + "'.");
      log.error("SQL = '" + DELETE_PENDING_AU_QUERY + "'.");
      throw sqle;
    } finally {
      SqlDbManager.safeCloseStatement(deletePendingAu);
    }

    pendingAusCount = getEnabledPendingAusCount(conn);
  }

  /**
   * Adds a task to the history.
   * 
   * @param task
   *          A ReindexingTask with the task.
   */
  private void addToHistory(ReindexingTask task) {
    synchronized (reindexingTaskHistory) {
      reindexingTaskHistory.add(0, task);
      setMaxHistory(maxReindexingTaskHistory);
    }
  }

  /**
   * Runs the specified reindexing task.
   * <p>
   * Temporary implementation runs as a LockssRunnable in a thread rather than
   * using the SchedService.
   * 
   * @param task A ReindexingTask with the reindexing task.
   */
  private void runReindexingTask(final ReindexingTask task) {
    /*
     * Temporarily running task in its own thread rather than using SchedService
     * 
     * @todo Update SchedService to handle this case
     */
    LockssRunnable runnable =
	new LockssRunnable(AuUtil.getThreadNameFor("Reindexing",
	                                           task.getAu())) {
	  public void lockssRun() {
	    task.handleEvent(Schedule.EventType.START);

	    while (!task.isFinished()) {
	      task.step(Integer.MAX_VALUE);
	    }

	    task.handleEvent(Schedule.EventType.FINISH);
	  }
	};

    Thread runThread = new Thread(runnable);
    runThread.start();
  }

  /**
   * Removes from history tasks for a specified AU.
   * 
   * @param auId
   *          A String with the AU identifier.
   * @return an int with the number of items removed.
   */
  private int removeFromHistory(String auId) {
    int count = 0;

    synchronized (reindexingTaskHistory) {
      // Remove tasks with this auid from task history list.
      for (Iterator<ReindexingTask> itr = reindexingTaskHistory.iterator();
	  itr.hasNext();) {
        ReindexingTask task = itr.next();

        if (auId.equals(task.getAu().getAuId())) {
          itr.remove();
          count++;
        }
      }
    }

    return count;
  }

  /**
   * Removes all metadata items for an AU.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param auId
   *          A String with the AU identifier.
   * @return an int with the number of metadata items deleted.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  int removeAuMetadataItems(Connection conn, String auId) throws SQLException {
    final String DEBUG_HEADER = "removeAuMetadataItems(): ";
    log.debug3(DEBUG_HEADER + "auid = " + auId);
    log.debug3(DEBUG_HEADER + "SQL = '" + DELETE_MD_ITEM_QUERY + "'.");
    int count = -1;
    PreparedStatement deletePendingAu =
	sqlDbManager.prepareStatement(conn, DELETE_MD_ITEM_QUERY);

    try {
      String pluginId = PluginManager.pluginIdFromAuId(auId);
      log.debug3(DEBUG_HEADER + "pluginId = " + pluginId);
      String auKey = PluginManager.auKeyFromAuId(auId);
      log.debug3(DEBUG_HEADER + "auKey = " + auKey);

      deletePendingAu.setString(1, pluginId);
      deletePendingAu.setString(2, auKey);
      count = sqlDbManager.executeUpdate(deletePendingAu);
    } catch (SQLException sqle) {
      log.error("Cannot delete AU metadata items", sqle);
      log.error("auid = " + auId);
      log.error("SQL = '" + DELETE_MD_ITEM_QUERY + "'.");
      throw sqle;
    } finally {
      SqlDbManager.safeCloseStatement(deletePendingAu);
    }

    log.debug3(DEBUG_HEADER + "count = " + count);
    return count;
  }

  /**
   * Removes an AU.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param auId
   *          A String with the AU identifier.
   * @return an int with the number of rows deleted.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  int removeAu(Connection conn, String auId) throws SQLException {
    final String DEBUG_HEADER = "removeAu(): ";
    log.debug3(DEBUG_HEADER + "auid = " + auId);
    log.debug3(DEBUG_HEADER + "SQL = '" + DELETE_AU_QUERY + "'.");
    int count = -1;
    PreparedStatement deleteAu =
	sqlDbManager.prepareStatement(conn, DELETE_AU_QUERY);

    try {
      String pluginId = PluginManager.pluginIdFromAuId(auId);
      log.debug3(DEBUG_HEADER + "pluginId = " + pluginId);
      String auKey = PluginManager.auKeyFromAuId(auId);
      log.debug3(DEBUG_HEADER + "auKey = " + auKey);

      deleteAu.setString(1, pluginId);
      deleteAu.setString(2, auKey);
      count = sqlDbManager.executeUpdate(deleteAu);
    } catch (SQLException sqle) {
      log.error("Cannot delete AU", sqle);
      log.error("auid = " + auId);
      log.error("SQL = '" + DELETE_AU_QUERY + "'.");
      throw sqle;
    } finally {
      SqlDbManager.safeCloseStatement(deleteAu);
    }

    log.debug3(DEBUG_HEADER + "count = " + count);
    return count;
  }

  /**
   * Restarts the Metadata Managaer service by terminating any running
   * reindexing tasks and then resetting its database before calling
   * {@link #startServie()}
   * .
   * <p>
   * This method is only used for testing.
   */
  @Override
  public void restartService() {
    stopReindexing();

    // Start the service
    startService();
  }
  

  /**
   * Provides an indication of whether an Archival Unit is eligible for
   * reindexing.
   * 
   * @param au
   *          An ArchivalUnit with the Archival Unit involved.
   * @return a boolean with <code>true</code> if the Archival Unit is eligible
   *         for reindexing, <code>false</code> otherwise.
   */
  boolean isEligibleForReindexing(ArchivalUnit au) {
    return isEligibleForReindexing(au.getAuId());
  }

  /**
   * Provides an indication of whether an Archival Unit is eligible for
   * reindexing.
   * 
   * @param auId
   *          A String with the Archival Unit identifier.
   * @return a boolean with <code>true</code> if the Archival Unit is eligible
   *         for reindexing, <code>false</code> otherwise.
   */
  private boolean isEligibleForReindexing(String auId) {
    return indexPriorityAuidMap == null
      || indexPriorityAuidMap.getMatch(auId, 0) > MIN_INDEX_PRIORITY;
  }

  /**
   * Provides the number of active reindexing tasks.
   * 
   * @return a long with the number of active reindexing tasks.
   */
  @Override
  public long getActiveReindexingCount() {
    return activeReindexingTasks.size();
  }

  /**
   * Provides the number of succesful reindexing operations.
   * 
   * @return a long with the number of successful reindexing operations.
   */
  @Override
  public long getSuccessfulReindexingCount() {
    return this.successfulReindexingCount;
  }

  /**
   * Provides the number of unsuccesful reindexing operations.
   * 
   * @return a long the number of unsuccessful reindexing operations.
   */
  @Override
  public long getFailedReindexingCount() {
    return this.failedReindexingCount;
  }

  /**
   * Provides the list of reindexing tasks.
   * 
   * @return a List<ReindexingTask> with the reindexing tasks.
   */
  @Override
  public List<ReindexingTask> getReindexingTasks() {
    return new ArrayList<ReindexingTask>(reindexingTaskHistory);
  }

  /**
   * Provides the number of distinct articles in the metadata database.
   * 
   * @return a long with the number of distinct articles in the metadata
   *         database.
   */
  @Override
  public long getArticleCount() {
    return metadataArticleCount;
  }

  // The number of AUs pending to be reindexed.
  /**
   * Provides the number of AUs pending to be reindexed.
   * 
   * @return a long with the number of AUs pending to be reindexed.
   */
  @Override
  public long getPendingAusCount() {
    return pendingAusCount;
  }

  /**
   * Provides the indexing enabled state of this manager.
   * 
   * @return a boolean with the indexing enabled state of this manager.
   */
  @Override
  public boolean isIndexingEnabled() {
    return reindexingEnabled;
  }

  /**
   * Provides the identifier of a plugin if existing or after creating it
   * otherwise.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param pluginId
   *          A String with the plugin identifier.
   * @param platform
   *          A String with the publishing platform.
   * @return a Long with the identifier of the plugin.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  public Long findOrCreatePlugin(Connection conn, String pluginId,
      String platform) throws SQLException {
    final String DEBUG_HEADER = "findOrCreatePlugin(): ";
    log.debug3(DEBUG_HEADER + "pluginId = " + pluginId);
    Long pluginSeq = findPlugin(conn, pluginId);
    log.debug3(DEBUG_HEADER + "pluginSeq = " + pluginSeq);

    // Check whether it is a new plugin.
    if (pluginSeq == null) {
      // Yes: Add to the database the new plugin.
      pluginSeq = addPlugin(conn, pluginId, platform);
      log.debug3(DEBUG_HEADER + "new pluginSeq = " + pluginSeq);
    }

    return pluginSeq;
  }

  /**
   * Provides the identifier of a plugin if existing or after creating it
   * otherwise.
   * @param pluginId
   *          A String with the plugin identifier.
   * @param platform
   *          A String with the publishing platform.
   * @return a Long with the identifier of the plugin.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */  
	@Override
	public Long findOrCreatePlugin(String pluginId, String platform)
			throws SQLException {
		Connection conn = sqlDbManager.getConnection();
		return findOrCreatePlugin(conn, pluginId, platform);

	}

  /**
   * Provides the identifier of a plugin.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param pluginId
   *          A String with the plugin identifier.
   * @return a Long with the identifier of the plugin.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  Long findPlugin(Connection conn, String pluginId) throws SQLException {
    final String DEBUG_HEADER = "findPlugin(): ";
    log.debug3(DEBUG_HEADER + "pluginId = " + pluginId);
    Long pluginSeq = null;
    ResultSet resultSet = null;

    PreparedStatement findPlugin =
	sqlDbManager.prepareStatement(conn, FIND_PLUGIN_QUERY);

    try {
      findPlugin.setString(1, pluginId);

      resultSet = sqlDbManager.executeQuery(findPlugin);
      if (resultSet.next()) {
	pluginSeq = resultSet.getLong(PLUGIN_SEQ_COLUMN);
      }
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      SqlDbManager.safeCloseStatement(findPlugin);
    }

    log.debug3(DEBUG_HEADER + "pluginSeq = " + pluginSeq);
    return pluginSeq;
  }

  /**
   * Adds a plugin to the database.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param pluginId
   *          A String with the plugin identifier.
   * @param platform
   *          A String with the publishing platform.
   * @return a Long with the identifier of the plugin just added.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private Long addPlugin(Connection conn, String pluginId,
      String platform) throws SQLException {
    final String DEBUG_HEADER = "addPlugin(): ";
    Long pluginSeq = null;
    ResultSet resultSet = null;
    PreparedStatement insertPlugin = sqlDbManager.prepareStatement(conn,
	INSERT_PLUGIN_QUERY, Statement.RETURN_GENERATED_KEYS);

    try {
      // skip auto-increment key field #0
      insertPlugin.setString(1, pluginId);
      insertPlugin.setString(2, platform);
      sqlDbManager.executeUpdate(insertPlugin);
      resultSet = insertPlugin.getGeneratedKeys();

      if (!resultSet.next()) {
	log.error("Unable to create plugin table row.");
	return null;
      }

      pluginSeq = resultSet.getLong(1);
      log.debug3(DEBUG_HEADER + "Added pluginSeq = " + pluginSeq);
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      SqlDbManager.safeCloseStatement(insertPlugin);
    }

    return pluginSeq;
  }

  /**
   * Provides the identifier of an Archival Unit if existing or after creating
   * it otherwise.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param pluginSeq
   *          A Long with the identifier of the plugin.
   * @param auKey
   *          A String with the Archival Unit key.
   * @return a Long with the identifier of the Archival Unit.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  public Long findOrCreateAu(Connection conn, Long pluginSeq, String auKey)
      throws SQLException {
    final String DEBUG_HEADER = "findOrCreateAu(): ";
    Long auSeq = findAu(conn, pluginSeq, auKey);
    log.debug3(DEBUG_HEADER + "auSeq = " + auSeq);

    // Check whether it is a new AU.
    if (auSeq == null) {
      // Yes: Add to the database the new AU.
      auSeq = addAu(conn, pluginSeq, auKey);
      log.debug3(DEBUG_HEADER + "new auSeq = " + auSeq);
    }

    return auSeq;
  }
  
  /**
   * Provides the identifier of an Archival Unit if existing or after creating
   * it otherwise.
   * 
   * @param pluginSeq
   *          A Long with the identifier of the plugin.
   * @param auKey
   *          A String with the Archival Unit key.
   * @return a Long with the identifier of the Archival Unit.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  @Override
  public Long findOrCreateAu(Long pluginSeq, String auKey) throws SQLException {
	  Connection conn = sqlDbManager.getConnection();
	  return findOrCreateAu(conn, pluginSeq, auKey);
  }

  /**
   * Provides the identifier of an Archival Unit.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param pluginSeq
   *          A Long with the identifier of the plugin.
   * @param auKey
   *          A String with the Archival Unit key.
   * @return a Long with the identifier of the Archival Unit.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  Long findAu(Connection conn, Long pluginSeq, String auKey)
      throws SQLException {
    final String DEBUG_HEADER = "findAu(): ";
    PreparedStatement findAu = sqlDbManager.prepareStatement(conn, FIND_AU_QUERY);
    ResultSet resultSet = null;
    Long auSeq = null;

    try {
      findAu.setLong(1, pluginSeq);
      findAu.setString(2, auKey);
      resultSet = sqlDbManager.executeQuery(findAu);
      if (resultSet.next()) {
	auSeq = resultSet.getLong(AU_SEQ_COLUMN);
	log.debug3(DEBUG_HEADER + "Found auSeq = " + auSeq);
      }
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      SqlDbManager.safeCloseStatement(findAu);
    }

    return auSeq;
  }

  /**
   * Adds an Archival Unit to the database.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param pluginSeq
   *          A Long with the identifier of the plugin.
   * @param auKey
   *          A String with the Archival Unit key.
   * @return a Long with the identifier of the Archival Unit just added.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private Long addAu(Connection conn, Long pluginSeq, String auKey)
      throws SQLException {
    final String DEBUG_HEADER = "addAu(): ";
    PreparedStatement insertAu = sqlDbManager.prepareStatement(conn,
	INSERT_AU_QUERY, Statement.RETURN_GENERATED_KEYS);

    ResultSet resultSet = null;
    Long auSeq = null;

    try {
      // skip auto-increment key field #0
      insertAu.setLong(1, pluginSeq);
      insertAu.setString(2, auKey);
      sqlDbManager.executeUpdate(insertAu);
      resultSet = insertAu.getGeneratedKeys();

      if (!resultSet.next()) {
	log.error("Unable to create AU table row for AU key " + auKey);
	return null;
      }

      auSeq = resultSet.getLong(1);
      log.debug3(DEBUG_HEADER + "Added auSeq = " + auSeq);
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      SqlDbManager.safeCloseStatement(insertAu);
    }

    return auSeq;
  }

  /**
   * Provides the identifier of an Archival Unit metadata.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param auSeq
   *          A Long with the identifier of the Archival Unit.
   * @return a Long with the identifier of the Archival Unit metadata.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  Long findAuMd(Connection conn, Long auSeq)
      throws SQLException {
    final String DEBUG_HEADER = "findAuMd(): ";
    Long auMdSeq = null;
    ResultSet resultSet = null;

    PreparedStatement findAuMd = sqlDbManager.prepareStatement(conn,
	FIND_AU_MD_QUERY);

    try {
      findAuMd.setLong(1, auSeq);

      resultSet = sqlDbManager.executeQuery(findAuMd);
      if (resultSet.next()) {
	auMdSeq = resultSet.getLong(AU_MD_SEQ_COLUMN);
      }
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      SqlDbManager.safeCloseStatement(findAuMd);
    }

    log.debug3(DEBUG_HEADER + "auMdSeq = " + auMdSeq);
    return auMdSeq;
  }

  /**
   * Adds an Archival Unit metadata to the database.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param auSeq
   *          A Long with the identifier of the Archival Unit.
   * @param version
   *          An int with the metadata version.
   * @param extractTime
   *          A long with the extraction time of the metadata.
   * @return a Long with the identifier of the Archival Unit metadata just
   *         added.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  
  public Long addAuMd(Connection conn, Long auSeq, int version,
      long extractTime) throws SQLException {
    final String DEBUG_HEADER = "addAuMd(): ";
    PreparedStatement insertAuMd = sqlDbManager.prepareStatement(conn,
	INSERT_AU_MD_QUERY, Statement.RETURN_GENERATED_KEYS);

    ResultSet resultSet = null;
    Long auMdSeq = null;

    try {
      // skip auto-increment key field #0
      insertAuMd.setLong(1, auSeq);
      insertAuMd.setShort(2, (short) version);
      insertAuMd.setLong(3, extractTime);
      sqlDbManager.executeUpdate(insertAuMd);
      resultSet = insertAuMd.getGeneratedKeys();

      if (!resultSet.next()) {
	log.error("Unable to create AU_MD table row for auSeq " + auSeq);
	return null;
      }

      auMdSeq = resultSet.getLong(1);
      log.debug3(DEBUG_HEADER + "Added auMdSeq = " + auMdSeq);
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      SqlDbManager.safeCloseStatement(insertAuMd);
    }

    return auMdSeq;
  }

  
	/**
	 * Adds an Archival Unit metadata to the database.
	 * 
	 * @param auSeq
	 *            A Long with the identifier of the Archival Unit.
	 * @param version
	 *            An int with the metadata version.
	 * @param extractTime
	 *            A long with the extraction time of the metadata.
	 * @return a Long with the identifier of the Archival Unit metadata just
	 *         added.
	 * @throws SQLException
	 *             if any problem occurred accessing the database.
	 */
	@Override
	public Long addAuMd(Long auSeq, int version, long extractTime)
			throws SQLException {

		Connection conn = sqlDbManager.getConnection();
		return addAuMd(auSeq, version, extractTime);
  }
  
  /**
   * Updates the timestamp of the last extraction of an Archival Unit metadata.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param auMdSeq
   *          A Long with the identifier of the Archival Unit metadata.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  void updateAuLastExtractionTime(Connection conn, Long auMdSeq)
      throws SQLException {
    PreparedStatement updateAuLastExtractionTime =
	sqlDbManager.prepareStatement(conn, UPDATE_AU_MD_EXTRACT_TIME_QUERY);

    try {
      updateAuLastExtractionTime.setLong(1, TimeBase.nowMs());
      updateAuLastExtractionTime.setLong(2, auMdSeq);
      sqlDbManager.executeUpdate(updateAuLastExtractionTime);
    } catch (SQLException sqle) {
      log.error("Cannot update the AU extraction time", sqle);
      log.error("auMdSeq = '" + auMdSeq + "'.");
      log.error("SQL = '" + UPDATE_AU_MD_EXTRACT_TIME_QUERY + "'.");
      throw sqle;
    } finally {
      SqlDbManager.safeCloseStatement(updateAuLastExtractionTime);
    }

    pendingAusCount = getEnabledPendingAusCount(conn);
  }

  /**
   * Provides the identifier of a publisher if existing or after creating it
   * otherwise.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param publisher
   *          A String with the publisher name.
   * @return a Long with the identifier of the publisher.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  public Long findOrCreatePublisher(Connection conn, String publisher)
      throws SQLException {
    final String DEBUG_HEADER = "findOrCreatePublisher(): ";
    Long publisherSeq = findPublisher(conn, publisher);
    log.debug3(DEBUG_HEADER + "publisherSeq = " + publisherSeq);

    // Check whether it is a new publisher.
    if (publisherSeq == null) {
      // Yes: Add to the database the new publisher.
      publisherSeq = addPublisher(conn, publisher);
      log.debug3(DEBUG_HEADER + "new publisherSeq = " + publisherSeq);
    }

    return publisherSeq;
  }
  
	/**
	 * Provides the identifier of a publisher if existing or after creating it
	 * otherwise.
	 * 
	 * @param conn
	 *            A Connection with the database connection to be used.
	 * @param publisher
	 *            A String with the publisher name.
	 * @return a Long with the identifier of the publisher.
	 * @throws SQLException
	 *             if any problem occurred accessing the database.
	 */
	@Override
	public Long findOrCreatePublisher(String publisher) throws SQLException {

		Connection conn = sqlDbManager.getConnection();
		return findOrCreatePublisher(conn, publisher);

	}

  /**
   * Provides the identifier of a publisher.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param publisher
   *          A String with the publisher name.
   * @return a Long with the identifier of the publisher.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private Long findPublisher(Connection conn, String publisher)
      throws SQLException {
    final String DEBUG_HEADER = "findPublisher(): ";
    Long publisherSeq = null;
    ResultSet resultSet = null;

    PreparedStatement findPublisher =
	sqlDbManager.prepareStatement(conn, FIND_PUBLISHER_QUERY);

    try {
      findPublisher.setString(1, publisher);

      resultSet = sqlDbManager.executeQuery(findPublisher);
      if (resultSet.next()) {
	publisherSeq = resultSet.getLong(PUBLISHER_SEQ_COLUMN);
      }
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      SqlDbManager.safeCloseStatement(findPublisher);
    }

    log.debug3(DEBUG_HEADER + "publisherSeq = " + publisherSeq);
    return publisherSeq;
  }

  /**
   * Adds a publisher to the database.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param publisher
   *          A String with the publisher name.
   * @return a Long with the identifier of the publisher just added.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private Long addPublisher(Connection conn, String publisher)
      throws SQLException {
    final String DEBUG_HEADER = "addPublisher(): ";
    Long publisherSeq = null;
    ResultSet resultSet = null;
    PreparedStatement insertPublisher = sqlDbManager.prepareStatement(conn,
	INSERT_PUBLISHER_QUERY, Statement.RETURN_GENERATED_KEYS);

    try {
      // skip auto-increment key field #0
      insertPublisher.setString(1, publisher);
      sqlDbManager.executeUpdate(insertPublisher);
      resultSet = insertPublisher.getGeneratedKeys();

      if (!resultSet.next()) {
	log.error("Unable to create publisher table row.");
	return null;
      }

      publisherSeq = resultSet.getLong(1);
      log.debug3(DEBUG_HEADER + "Added publisherSeq = " + publisherSeq);
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      SqlDbManager.safeCloseStatement(insertPublisher);
    }

    return publisherSeq;
  }

  /**
   * Provides the identifier of a publication if existing or after creating it
   * otherwise.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param pIssn
   *          A String with the print ISSN of the publication.
   * @param eIssn
   *          A String with the electronic ISSN of the publication.
   * @param pIsbn
   *          A String with the print ISBN of the publication.
   * @param eIsbn
   *          A String with the electronic ISBN of the publication.
   * @param publisherSeq
   *          A Long with the publisher identifier.
   * @param name
   *          A String with the name of the publication.
   * @param date
   *          A String with the publication date of the publication.
   * @param proprietaryId
   *          A String with the proprietary identifier of the publication.
   * @param volume
   *          A String with the bibliographic volume.
   * @return a Long with the identifier of the publication.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  public Long findOrCreatePublication(Connection conn, String pIssn,
      String eIssn, String pIsbn, String eIsbn, Long publisherSeq, String name,
      String date, String proprietaryId, String volume) throws SQLException {
    final String DEBUG_HEADER = "findOrCreatePublication(): ";
    Long publicationSeq = null;

    // Get the title name.
    String title = null;
    log.debug3(DEBUG_HEADER + "name = " + name);

    if (!StringUtil.isNullString(name)) {
      title = name.substring(0, Math.min(name.length(), MAX_NAME_COLUMN));
    }
    log.debug3(DEBUG_HEADER + "title = " + title);

    // Check whether it is a book series.
    if (isBookSeries(pIssn, eIssn, pIsbn, eIsbn, volume)) {
      // Yes: Find or create the book series.
      log.debug3(DEBUG_HEADER + "is book series.");
      publicationSeq =
	  findOrCreateBookInBookSeries(conn, pIssn, eIssn, pIsbn, eIsbn,
	                                publisherSeq, name, date, proprietaryId,
	                                volume);
      // No: Check whether it is a book.
    } else if (isBook(pIsbn, eIsbn)) {
      // Yes: Find or create the book.
      log.debug3(DEBUG_HEADER + "is book.");
      publicationSeq =
	  findOrCreateBook(conn, pIsbn, eIsbn, publisherSeq, title, null, date,
	                   proprietaryId);
    } else {
      // No, it is a journal article: Find or create the journal.
      log.debug3(DEBUG_HEADER + "is journal.");
      publicationSeq =
	  findOrCreateJournal(conn, pIssn, eIssn, publisherSeq, title, date,
	                      proprietaryId);
    }

    return publicationSeq;
  }

  /**
   * Provides an indication of whether a metadata set corresponds to a book
   * series.
   * 
   * @param pIssn
   *          A String with the print ISSN in the metadata.
   * @param eIssn
   *          A String with the electronic ISSN in the metadata.
   * @param pIsbn
   *          A String with the print ISBN in the metadata.
   * @param eIsbn
   *          A String with the electronic ISBN in the metadata.
   * @param volume
   *          A String with the volume in the metadata.
   * @return <code>true</code> if the metadata set corresponds to a book series,
   *         <code>false</code> otherwise.
   */
  private boolean isBookSeries(String pIssn, String eIssn, String pIsbn,
      String eIsbn, String volume) {
    final String DEBUG_HEADER = "isBook(): ";

    // If the metadata contains both ISBN and ISSN values, it is a book that is
    // part of a book series.
    boolean isBookSeries =
	isBook(pIsbn, eIsbn)
	    && (!StringUtil.isNullString(pIssn) || !StringUtil
		.isNullString(eIssn));
    log.debug3(DEBUG_HEADER + "isBookSeries = " + isBookSeries);

    // Handle book series with no ISSNs.
    if (!isBookSeries && isBook(pIsbn, eIsbn)) {
      isBookSeries = !StringUtil.isNullString(volume);
    }

    return isBookSeries;
  }

  /**
   * Provides the identifier of a book that belongs to a book series if existing
   * or after creating it otherwise.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param pIssn
   *          A String with the print ISSN of the book series.
   * @param eIssn
   *          A String with the electronic ISSN of the book series.
   * @param pIsbn
   *          A String with the print ISBN of the book series.
   * @param eIsbn
   *          A String with the electronic ISBN of the book series.
   * @param publisherSeq
   *          A Long with the publisher identifier.
   * @param title
   *          A String with the name of the book series.
   * @param date
   *          A String with the publication date of the book series.
   * @param proprietaryId
   *          A String with the proprietary identifier of the book series.
   * @param volume
   *          A String with the bibliographic volume.
   * @return a Long with the identifier of the book.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private Long findOrCreateBookInBookSeries(Connection conn, String pIssn,
      String eIssn, String pIsbn, String eIsbn, Long publisherSeq, String title,
      String date, String proprietaryId, String volume) throws SQLException {
    final String DEBUG_HEADER = "findOrCreateBookInBookSeries(): ";
    Long bookSeq = null;
    Long bookSeriesSeq = null;
    Long mdItemSeq = null;

    // Construct a title for the book in the series.
    String bookTitle = title + " Volume " + volume;
    log.debug3(DEBUG_HEADER + "bookTitle = " + bookTitle);

    // Find the book series.
    bookSeriesSeq =
	findPublication(conn, title, publisherSeq, pIssn, eIssn, pIsbn, eIsbn,
			MD_ITEM_TYPE_BOOK_SERIES);
    log.debug3(DEBUG_HEADER + "bookSeriesSeq = " + bookSeriesSeq);

    // Check whether it is a new book series.
    if (bookSeriesSeq == null) {
      // Yes: Add to the database the new book series.
      bookSeriesSeq =
	  addPublication(conn, null, MD_ITEM_TYPE_BOOK_SERIES, date, title,
	                 proprietaryId, publisherSeq);
      log.debug3(DEBUG_HEADER + "new bookSeriesSeq = " + bookSeriesSeq);

      // Skip it if the new book series could not be added.
      if (bookSeriesSeq == null) {
	log.error("Title for new book series '" + title
	    + "' could not be created.");
	return bookSeq;
      }

      // Get the book series metadata item identifier.
      mdItemSeq = findPublicationMetadataItem(conn, bookSeriesSeq);
      log.debug3(DEBUG_HEADER + "mdItemSeq = " + mdItemSeq);

      // Add to the database the new book series ISSN values.
      addMdItemIssns(conn, mdItemSeq, pIssn, eIssn);
      log.debug3(DEBUG_HEADER + "added title ISSNs.");

      // Add to the database the new book.
      bookSeq =
	  addPublication(conn, bookSeriesSeq, MD_ITEM_TYPE_BOOK, date,
	                 bookTitle, proprietaryId, publisherSeq);
      log.debug3(DEBUG_HEADER + "new bookSeq = " + bookSeq);

      // Skip it if the new book could not be added.
      if (bookSeq == null) {
	log.error("Title for new book '" + bookTitle
	    + "' could not be created.");
	return bookSeq;
      }

      // Get the book metadata item identifier.
      mdItemSeq = findPublicationMetadataItem(conn, bookSeq);
      log.debug3(DEBUG_HEADER + "mdItemSeq = " + mdItemSeq);

      // Add to the database the new book ISBN values.
      addMdItemIsbns(conn, mdItemSeq, pIsbn, eIsbn);
      log.debug3(DEBUG_HEADER + "added title ISBNs.");
    } else {
      // No: Get the book series metadata item identifier.
      mdItemSeq = findPublicationMetadataItem(conn, bookSeriesSeq);
      log.debug3(DEBUG_HEADER + "mdItemSeq = " + mdItemSeq);

      // Add to the database the book series name in the metadata as an
      // alternate, if new.
      addNewMdItemName(conn, mdItemSeq, title);
      log.debug3(DEBUG_HEADER + "added new title name.");

      // Add to the database the ISSN values in the metadata, if new.
      addNewMdItemIssns(conn, mdItemSeq, pIssn, eIssn);
      log.debug3(DEBUG_HEADER + "added new title ISSNs.");

      // Find or create the book.
      bookSeq =
	  findOrCreateBook(conn, pIsbn, eIsbn, publisherSeq, bookTitle,
			   bookSeriesSeq, date, proprietaryId);
    }

    return bookSeq;
  }

  /**
   * Provides an indication of whether a metadata set corresponds to a book.
   * 
   * @param pIsbn
   *          A String with the print ISBN in the metadata.
   * @param eIsbn
   *          A String with the electronic ISBN in the metadata.
   * @return <code>true</code> if the metadata set corresponds to a book,
   *         <code>false</code> otherwise.
   */
  private boolean isBook(String pIsbn, String eIsbn) {
    final String DEBUG_HEADER = "isBook(): ";

    // If there are ISBN values in the metadata, it is a book or a book series.
    boolean isBook =
	!StringUtil.isNullString(pIsbn) || !StringUtil.isNullString(eIsbn);
    log.debug3(DEBUG_HEADER + "isBook = " + isBook);

    return isBook;
  }

  /**
   * Provides the identifier of a book existing or after creating it otherwise.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param pIsbn
   *          A String with the print ISBN of the book.
   * @param eIsbn
   *          A String with the electronic ISBN of the book.
   * @param publisherSeq
   *          A Long with the publisher identifier.
   * @param title
   *          A String with the name of the book.
   * @param parentSeq
   *          A Long with the publication parent publication identifier.
   * @param date
   *          A String with the publication date of the book.
   * @param proprietaryId
   *          A String with the proprietary identifier of the book.
   * @param volume
   *          A String with the bibliographic volume.
   * @return a Long with the identifier of the book.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private Long findOrCreateBook(Connection conn, String pIsbn, String eIsbn,
      Long publisherSeq, String title, Long parentSeq, String date,
      String proprietaryId) throws SQLException {
    final String DEBUG_HEADER = "findOrCreateBook(): ";
    Long publicationSeq = null;
    Long mdItemSeq = null;

    // Find the book.
    publicationSeq =
	findPublication(conn, title, publisherSeq, null, null, pIsbn, eIsbn,
			MD_ITEM_TYPE_BOOK);
    log.debug3(DEBUG_HEADER + "publicationSeq = " + publicationSeq);

    // Check whether it is a new book.
    if (publicationSeq == null) {
      // Yes: Add to the database the new book.
      publicationSeq =
	  addPublication(conn, parentSeq, MD_ITEM_TYPE_BOOK, date, title,
			 proprietaryId, publisherSeq);
      log.debug3(DEBUG_HEADER + "new publicationSeq = " + publicationSeq);

      // Skip it if the new book could not be added.
      if (publicationSeq == null) {
	log.error("Publication for new book '" + title
	    + "' could not be created.");
	return publicationSeq;
      }

      // Get the book metadata item identifier.
      mdItemSeq = findPublicationMetadataItem(conn, publicationSeq);
      log.debug3(DEBUG_HEADER + "mdItemSeq = " + mdItemSeq);

      // Add to the database the new book ISBN values.
      addMdItemIsbns(conn, mdItemSeq, pIsbn, eIsbn);
      log.debug3(DEBUG_HEADER + "added title ISBNs.");
    } else {
      // No: Get the book metadata item identifier.
      mdItemSeq = findPublicationMetadataItem(conn, publicationSeq);
      log.debug3(DEBUG_HEADER + "mdItemSeq = " + mdItemSeq);

      // Add to the database the book name in the metadata as an alternate,
      // if new.
      addNewMdItemName(conn, mdItemSeq, title);
      log.debug3(DEBUG_HEADER + "added new title name.");

      // Add to the database the ISBN values in the metadata, if new.
      addNewMdItemIsbns(conn, mdItemSeq, pIsbn, eIsbn);
      log.debug3(DEBUG_HEADER + "added new title ISBNs.");
    }

    return publicationSeq;
  }

  /**
   * Provides the identifier of a journal if existing or after creating it
   * otherwise.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param pIssn
   *          A String with the print ISSN of the journal.
   * @param eIssn
   *          A String with the electronic ISSN of the journal.
   * @param publisherSeq
   *          A Long with the publisher identifier.
   * @param title
   *          A String with the name of the journal.
   * @param date
   *          A String with the publication date of the journal.
   * @param proprietaryId
   *          A String with the proprietary identifier of the journal.
   * @return a Long with the identifier of the journal.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private Long findOrCreateJournal(Connection conn, String pIssn,
      String eIssn, Long publisherSeq, String title, String date,
      String proprietaryId) throws SQLException {
    final String DEBUG_HEADER = "findOrCreateJournal(): ";
    Long publicationSeq = null;
    Long mdItemSeq = null;
    Long parentSeq = null;

    // Skip it if it no title name or ISSNs, as it will not be possible to
    // find the journal to which it belongs in the database.
    if (StringUtil.isNullString(title) && pIssn == null && eIssn == null) {
      log.error("Title for article cannot be created as it has no name or ISSN "
	  + "values.");
      return publicationSeq;
    }

    // Find the journal.
    publicationSeq =
	findPublication(conn, title, publisherSeq, pIssn, eIssn, null, null,
			MD_ITEM_TYPE_JOURNAL);
    log.debug3(DEBUG_HEADER + "publicationSeq = " + publicationSeq);

    // Check whether it is a new journal.
    if (publicationSeq == null) {
      // Yes: Add to the database the new journal.
      publicationSeq =
	  addPublication(conn, parentSeq, MD_ITEM_TYPE_JOURNAL, date, title,
			 proprietaryId, publisherSeq);
      log.debug3(DEBUG_HEADER + "new publicationSeq = " + publicationSeq);

      // Skip it if the new journal could not be added.
      if (publicationSeq == null) {
	log.error("Publication for new journal '" + title
	    + "' could not be created.");
	return publicationSeq;
      }

      // Get the journal metadata item identifier.
      mdItemSeq = findPublicationMetadataItem(conn, publicationSeq);
      log.debug3(DEBUG_HEADER + "mdItemSeq = " + mdItemSeq);

      // Add to the database the new journal ISSN values.
      addMdItemIssns(conn, mdItemSeq, pIssn, eIssn);
      log.debug3(DEBUG_HEADER + "added title ISSNs.");
    } else {
      // No: Get the journal metadata item identifier.
      mdItemSeq = findPublicationMetadataItem(conn, publicationSeq);
      log.debug3(DEBUG_HEADER + "mdItemSeq = " + mdItemSeq);

      // No: Add to the database the journal name in the metadata as an
      // alternate, if new.
      addNewMdItemName(conn, mdItemSeq, title);
      log.debug3(DEBUG_HEADER + "added new title name.");

      // Add to the database the ISSN values in the metadata, if new.
      addNewMdItemIssns(conn, mdItemSeq, pIssn, eIssn);
      log.debug3(DEBUG_HEADER + "added new title ISSNs.");
    }

    return publicationSeq;
  }

  /**
   * Provides the identifier of a publication by its title, publisher, ISSNs
   * and/or ISBNs.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param title
   *          A String with the title of the publication.
   * @param publisherSeq
   *          A Long with the publisher identifier.
   * @param pIssn
   *          A String with the print ISSN of the publication.
   * @param eIssn
   *          A String with the electronic ISSN of the publication.
   * @param pIsbn
   *          A String with the print ISBN of the publication.
   * @param eIsbn
   *          A String with the electronic ISBN of the publication.
   * @param mdItemType
   *          A String with the type of publication to be identified.
   * @return a Long with the identifier of the publication.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private Long findPublication(Connection conn, String title, Long publisherSeq,
      String pIssn, String eIssn, String pIsbn, String eIsbn, String mdItemType)
	  throws SQLException {
    final String DEBUG_HEADER = "findPublication(): ";
    Long publicationSeq = null;
    boolean hasIssns = pIssn != null || eIssn != null;
    log.debug3(DEBUG_HEADER + "hasIssns = " + hasIssns);
    boolean hasIsbns = pIsbn != null || eIsbn != null;
    log.debug3(DEBUG_HEADER + "hasIsbns = " + hasIsbns);
    boolean hasName = !StringUtil.isNullString(title);
    log.debug3(DEBUG_HEADER + "hasName = " + hasName);

    if (!hasIssns && !hasIsbns && !hasName) {
      log.debug3(DEBUG_HEADER + "Cannot find publication with no name, ISSNs"
	  + " or ISBNs.");
      return null;
    }

    if (hasIssns && hasIsbns && hasName) {
      publicationSeq =
	  findPublicationByIssnsOrIsbnsOrName(conn, title, publisherSeq, pIssn,
					      eIssn, pIsbn, eIsbn, mdItemType);
    } else if (hasIssns && hasName) {
      publicationSeq =
	  findPublicationByIssnsOrName(conn, title, publisherSeq, pIssn, eIssn,
				       mdItemType);
    } else if (hasIsbns && hasName) {
      publicationSeq =
	  findPublicationByIsbnsOrName(conn, title, publisherSeq, pIsbn, eIsbn,
				       mdItemType);
    } else if (hasIssns) {
      publicationSeq =
	  findPublicationByIssns(conn, publisherSeq, pIssn, eIssn, mdItemType);
    } else if (hasIsbns) {
      publicationSeq =
	  findPublicationByIsbns(conn, publisherSeq, pIsbn, eIsbn, mdItemType);
    } else if (hasName) {
      publicationSeq =
	  findPublicationByName(conn, title, publisherSeq, mdItemType);
    }

    return publicationSeq;
  }

  /**
   * Adds a publication to the database.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param parentSeq
   *          A Long with the publication parent publication identifier.
   * @param mdItemType
   *          A String with the type of publication.
   * @param date
   *          A String with the publication date of the publication.
   * @param title
   *          A String with the title of the publication.
   * @param proprietaryId
   *          A String with the proprietary identifier of the publication.
   * @param publisherSeq
   *          A Long with the publisher identifier.
   * @return a Long with the identifier of the publication just added.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private Long addPublication(Connection conn, Long parentSeq,
      String mdItemType, String date, String title, String proprietaryId,
      Long publisherSeq) throws SQLException {
    final String DEBUG_HEADER = "addPublication(): ";
    Long publicationSeq = null;

    Long mdItemTypeSeq = findMetadataItemType(conn, mdItemType);
    log.debug2(DEBUG_HEADER + "mdItemTypeSeq = " + mdItemTypeSeq);

    if (mdItemTypeSeq == null) {
	log.error("Unable to find the metadata item type " + mdItemType);
	return null;
    }

    Long mdItemSeq =
	addMdItem(conn, parentSeq, mdItemTypeSeq, null, date, null);
    log.debug2(DEBUG_HEADER + "mdItemSeq = " + mdItemSeq);

    if (mdItemSeq == null) {
	log.error("Unable to create metadata item table row.");
	return null;
    }

    addMdItemName(conn, mdItemSeq, title, PRIMARY_NAME_TYPE);

    ResultSet resultSet = null;

    PreparedStatement insertPublication = sqlDbManager.prepareStatement(conn,
	INSERT_PUBLICATION_QUERY, Statement.RETURN_GENERATED_KEYS);

    try {
      // skip auto-increment key field #0
      insertPublication.setLong(1, mdItemSeq);
      insertPublication.setLong(2, publisherSeq);
      insertPublication.setString(3, proprietaryId);
      sqlDbManager.executeUpdate(insertPublication);
      resultSet = insertPublication.getGeneratedKeys();

      if (!resultSet.next()) {
	log.error("Unable to create publication table row.");
	return null;
      }

      publicationSeq = resultSet.getLong(1);
      log.debug3(DEBUG_HEADER + "Added publicationSeq = " + publicationSeq);
    } catch (SQLException sqle) {
      log.error("Cannot insert publication", sqle);
      log.error("mdItemSeq = '" + mdItemSeq + "'.");
      log.error("SQL = '" + INSERT_PUBLICATION_QUERY + "'.");
      throw sqle;
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      SqlDbManager.safeCloseStatement(insertPublication);
    }

    return publicationSeq;
  }

  /**
   * Provides the identifier of the metadata item of a publication.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param publicationSeq
   *          A Long with the identifier of the publication.
   * @return a Long with the identifier of the metadata item of the publication.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  public Long findPublicationMetadataItem(Connection conn, Long publicationSeq)
      throws SQLException {
    final String DEBUG_HEADER = "findPublicationMetadataItem(): ";
    Long mdItemSeq = null;
    PreparedStatement findMdItem =
	sqlDbManager.prepareStatement(conn, FIND_PUBLICATION_METADATA_ITEM_QUERY);
    ResultSet resultSet = null;

    try {
      findMdItem.setLong(1, publicationSeq);

      resultSet = sqlDbManager.executeQuery(findMdItem);
      if (resultSet.next()) {
	mdItemSeq = resultSet.getLong(MD_ITEM_SEQ_COLUMN);
	log.debug3(DEBUG_HEADER + "mdItemSeq = " + mdItemSeq);
      }
    } catch (SQLException sqle) {
	log.error("Cannot find publication metadata item", sqle);
	log.error("publicationSeq = '" + publicationSeq + "'.");
	log.error("SQL = '" + FIND_PUBLICATION_METADATA_ITEM_QUERY + "'.");
	throw sqle;
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      SqlDbManager.safeCloseStatement(findMdItem);
    }

    return mdItemSeq;
  }

  /**
   * Adds to the database the ISSNs of a metadata item.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param mdItemSeq
   *          A Long with the metadata item identifier.
   * @param pIssn
   *          A String with the print ISSN of the metadata item.
   * @param eIssn
   *          A String with the electronic ISSN of the metadata item.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private void addMdItemIssns(Connection conn, Long mdItemSeq, String pIssn,
      String eIssn) throws SQLException {
    final String DEBUG_HEADER = "addMdItemIssns(): ";

    if (pIssn == null && eIssn == null) {
      return;
    }

    PreparedStatement insertIssn =
	sqlDbManager.prepareStatement(conn, INSERT_ISSN_QUERY);

    try {
      if (pIssn != null) {
	log.debug3(DEBUG_HEADER + "pIssn = " + pIssn);
	insertIssn.setLong(1, mdItemSeq);
	insertIssn.setString(2, pIssn);
	insertIssn.setString(3, P_ISSN_TYPE);
	int count = sqlDbManager.executeUpdate(insertIssn);

	if (log.isDebug3()) {
	  log.debug3(DEBUG_HEADER + "count = " + count);
	  log.debug3(DEBUG_HEADER + "Added PISSN = " + pIssn);
	}
      }

      if (eIssn != null) {
	log.debug3(DEBUG_HEADER + "eIssn = " + eIssn);
	insertIssn.setLong(1, mdItemSeq);
	insertIssn.setString(2, eIssn);
	insertIssn.setString(3, E_ISSN_TYPE);
	int count = sqlDbManager.executeUpdate(insertIssn);

	if (log.isDebug3()) {
	  log.debug3(DEBUG_HEADER + "count = " + count);
	  log.debug3(DEBUG_HEADER + "Added EISSN = " + eIssn);
	}
      }
    } finally {
      SqlDbManager.safeCloseStatement(insertIssn);
    }
  }

  /**
   * Adds to the database the ISBNs of a metadata item.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param mdItemSeq
   *          A Long with the metadata item identifier.
   * @param pIsbn
   *          A String with the print ISBN of the metadata item.
   * @param eIsbn
   *          A String with the electronic ISBN of the metadata item.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private void addMdItemIsbns(Connection conn, Long mdItemSeq, String pIsbn,
      String eIsbn) throws SQLException {
    final String DEBUG_HEADER = "addMdItemIsbns(): ";

    if (pIsbn == null && eIsbn == null) {
      return;
    }

    PreparedStatement insertIsbn =
	sqlDbManager.prepareStatement(conn, INSERT_ISBN_QUERY);

    try {
      if (pIsbn != null) {
	log.debug3(DEBUG_HEADER + "pIsbn = " + pIsbn);
	insertIsbn.setLong(1, mdItemSeq);
	insertIsbn.setString(2, pIsbn);
	insertIsbn.setString(3, P_ISBN_TYPE);
	int count = sqlDbManager.executeUpdate(insertIsbn);

	if (log.isDebug3()) {
	  log.debug3(DEBUG_HEADER + "count = " + count);
	  log.debug3(DEBUG_HEADER + "Added PISBN = " + pIsbn);
	}
      }

      if (eIsbn != null) {
	log.debug3(DEBUG_HEADER + "eIsbn = " + eIsbn);
	insertIsbn.setLong(1, mdItemSeq);
	insertIsbn.setString(2, eIsbn);
	insertIsbn.setString(3, E_ISBN_TYPE);
	int count = sqlDbManager.executeUpdate(insertIsbn);

	if (log.isDebug3()) {
	  log.debug3(DEBUG_HEADER + "count = " + count);
	  log.debug3(DEBUG_HEADER + "Added EISBN = " + eIsbn);
	}
      }
    } finally {
      SqlDbManager.safeCloseStatement(insertIsbn);
    }
  }

  /**
   * Adds to the database the name of a metadata item, if it does not exist
   * already.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param mdItemSeq
   *          A Long with the metadata item identifier.
   * @param mdItemName
   *          A String with the name to be added, if new.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private void addNewMdItemName(Connection conn, Long mdItemSeq,
      String mdItemName) throws SQLException {
    final String DEBUG_HEADER = "addNewMdItemName(): ";

    if (mdItemName == null) {
      return;
    }

    Map<String, String> titleNames = getMdItemNames(conn, mdItemSeq);

    for (String name : titleNames.keySet()) {
      if (name.equals(mdItemName)) {
	log.debug3(DEBUG_HEADER + "Title name = " + mdItemName
	    + " already exists.");
	return;
      }
    }

    addMdItemName(conn, mdItemSeq, mdItemName, NOT_PRIMARY_NAME_TYPE);
  }

  /**
   * Adds to the database the ISSNs of a metadata item, if they do not exist
   * already.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param mdItemSeq
   *          A Long with the metadata item identifier.
   * @param pIssn
   *          A String with the print ISSN of the metadata item.
   * @param eIssn
   *          A String with the electronic ISSN of the metadata item.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private void addNewMdItemIssns(Connection conn, Long mdItemSeq, String pIssn,
      String eIssn) throws SQLException {
    if (pIssn == null && eIssn == null) {
      return;
    }

    PreparedStatement findIssns =
	sqlDbManager.prepareStatement(conn, FIND_MD_ITEM_ISSN_QUERY);

    ResultSet resultSet = null;

    try {
      findIssns.setLong(1, mdItemSeq);
      resultSet = sqlDbManager.executeQuery(findIssns);

      while (resultSet.next()) {
	if (pIssn != null && pIssn.equals(resultSet.getString(ISSN_COLUMN))
	    && P_ISSN_TYPE.equals(resultSet.getString(ISSN_TYPE_COLUMN))) {
	  pIssn = null;
	}

	if (eIssn != null && eIssn.equals(resultSet.getString(ISSN_COLUMN))
	    && E_ISSN_TYPE.equals(resultSet.getString(ISSN_TYPE_COLUMN))) {
	  eIssn = null;
	}
      }
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      SqlDbManager.safeCloseStatement(findIssns);
    }

    addMdItemIssns(conn, mdItemSeq, pIssn, eIssn);
  }

  /**
   * Adds to the database the ISBNs of a metadata item, if they do not exist
   * already.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param mdItemSeq
   *          A Long with the metadata item identifier.
   * @param pIsbn
   *          A String with the print ISBN of the metadata item.
   * @param eIsbn
   *          A String with the electronic ISBN of the metadata item.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private void addNewMdItemIsbns(Connection conn, Long mdItemSeq, String pIsbn,
      String eIsbn) throws SQLException {
    if (pIsbn == null && eIsbn == null) {
      return;
    }

    PreparedStatement findIsbns =
	sqlDbManager.prepareStatement(conn, FIND_MD_ITEM_ISBN_QUERY);

    ResultSet resultSet = null;

    try {
      findIsbns.setLong(1, mdItemSeq);
      resultSet = sqlDbManager.executeQuery(findIsbns);

      while (resultSet.next()) {
	if (pIsbn != null && pIsbn.equals(resultSet.getString(ISBN_COLUMN))
	    && P_ISBN_TYPE.equals(resultSet.getString(ISBN_TYPE_COLUMN))) {
	  pIsbn = null;
	}

	if (eIsbn != null && eIsbn.equals(resultSet.getString(ISBN_COLUMN))
	    && E_ISBN_TYPE.equals(resultSet.getString(ISBN_TYPE_COLUMN))) {
	  eIsbn = null;
	}
      }
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      SqlDbManager.safeCloseStatement(findIsbns);
    }

    addMdItemIsbns(conn, mdItemSeq, pIsbn, eIsbn);
  }

  /**
   * Provides the identifier of a publication by its title, publisher, ISSNs and
   * ISBNs.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param title
   *          A String with the title of the publication.
   * @param publisherSeq
   *          A Long with the publisher identifier.
   * @param pIssn
   *          A String with the print ISSN of the publication.
   * @param eIssn
   *          A String with the electronic ISSN of the publication.
   * @param pIsbn
   *          A String with the print ISBN of the publication.
   * @param eIsbn
   *          A String with the electronic ISBN of the publication.
   * @param mdItemType
   *          A String with the type of publication to be identified.
   * @return a Long with the identifier of the publication.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private Long findPublicationByIssnsOrIsbnsOrName(Connection conn,
      String title, Long publisherSeq, String pIssn, String eIssn, String pIsbn,
      String eIsbn, String mdItemType) throws SQLException {
    Long publicationSeq =
	findPublicationByIssns(conn, publisherSeq, pIssn, eIssn, mdItemType);

    if (publicationSeq == null) {
      publicationSeq =
	  findPublicationByIsbnsOrName(conn, title, publisherSeq, pIsbn, eIsbn,
				       mdItemType);
    }

    return publicationSeq;
  }

  /**
   * Provides the identifier of a publication by its publisher and title or
   * ISSNs.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param title
   *          A String with the title of the publication.
   * @param publisherSeq
   *          A Long with the publisher identifier.
   * @param pIssn
   *          A String with the print ISSN of the publication.
   * @param eIssn
   *          A String with the electronic ISSN of the publication.
   * @param mdItemType
   *          A String with the type of publication to be identified.
   * @return a Long with the identifier of the publication.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private Long findPublicationByIssnsOrName(Connection conn, String title,
      Long publisherSeq, String pIssn, String eIssn, String mdItemType)
	  throws SQLException {
    Long publicationSeq =
	findPublicationByIssns(conn, publisherSeq, pIssn, eIssn, mdItemType);

    if (publicationSeq == null) {
      publicationSeq =
	  findPublicationByName(conn, title, publisherSeq, mdItemType);
    }

    return publicationSeq;
  }

  /**
   * Provides the identifier of a publication by its publisher and title or
   * ISBNs.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param title
   *          A String with the title of the publication.
   * @param publisherSeq
   *          A Long with the publisher identifier.
   * @param pIsbn
   *          A String with the print ISBN of the publication.
   * @param eIsbn
   *          A String with the electronic ISBN of the publication.
   * @param mdItemType
   *          A String with the type of publication to be identified.
   * @return a Long with the identifier of the publication.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private Long findPublicationByIsbnsOrName(Connection conn, String title,
      Long publisherSeq, String pIsbn, String eIsbn, String mdItemType)
      throws SQLException {
    Long publicationSeq =
	findPublicationByIsbns(conn, publisherSeq, pIsbn, eIsbn, mdItemType);

    if (publicationSeq == null) {
      publicationSeq =
	  findPublicationByName(conn, title, publisherSeq, mdItemType);
    }

    return publicationSeq;
  }

  /**
   * Provides the identifier of a publication by its publisher and ISSNs.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param publisherSeq
   *          A Long with the publisher identifier.
   * @param pIssn
   *          A String with the print ISSN of the publication.
   * @param eIssn
   *          A String with the electronic ISSN of the publication.
   * @param mdItemType
   *          A String with the type of publication to be identified.
   * @return a Long with the identifier of the publication.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private Long findPublicationByIssns(Connection conn, Long publisherSeq,
      String pIssn, String eIssn, String mdItemType) throws SQLException {
    final String DEBUG_HEADER = "findPublicationByIssns(): ";
    log.debug3(DEBUG_HEADER + "publisherSeq = " + publisherSeq);
    log.debug3(DEBUG_HEADER + "pIssn = " + pIssn);
    log.debug3(DEBUG_HEADER + "eIssn = " + eIssn);
    log.debug3(DEBUG_HEADER + "mdItemType = " + mdItemType);
    Long publicationSeq = null;
    ResultSet resultSet = null;
    log.debug3(DEBUG_HEADER + "SQL = '" + FIND_PUBLICATION_BY_ISSNS_QUERY
	+ "'.");
    PreparedStatement findPublicationByIssns =
	sqlDbManager.prepareStatement(conn, FIND_PUBLICATION_BY_ISSNS_QUERY);

    try {
      findPublicationByIssns.setLong(1, publisherSeq);
      findPublicationByIssns.setString(2, pIssn);
      findPublicationByIssns.setString(3, eIssn);
      findPublicationByIssns.setString(4, mdItemType);

      resultSet = sqlDbManager.executeQuery(findPublicationByIssns);
      if (resultSet.next()) {
	publicationSeq = resultSet.getLong(PUBLICATION_SEQ_COLUMN);
	log.debug3(DEBUG_HEADER + "publicationSeq = " + publicationSeq);
      }
    } catch (SQLException sqle) {
      log.error("Cannot find publication", sqle);
      log.error("publisherSeq = '" + publisherSeq + "'.");
      log.error("pIssn = " + pIssn);
      log.error("eIssn = " + eIssn);
      log.error("SQL = '" + FIND_PUBLICATION_BY_ISSNS_QUERY + "'.");
      throw sqle;
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      SqlDbManager.safeCloseStatement(findPublicationByIssns);
    }

    return publicationSeq;
  }

  /**
   * Provides the identifier of a publication by its publisher and ISBNs.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param publisherSeq
   *          A Long with the publisher identifier.
   * @param pIsbn
   *          A String with the print ISBN of the publication.
   * @param eIsbn
   *          A String with the electronic ISBN of the publication.
   * @param mdItemType
   *          A String with the type of publication to be identified.
   * @return a Long with the identifier of the publication.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private Long findPublicationByIsbns(Connection conn, Long publisherSeq,
      String pIsbn, String eIsbn, String mdItemType) throws SQLException {
    final String DEBUG_HEADER = "findPublicationByIsbns(): ";
    Long publicationSeq = null;
    ResultSet resultSet = null;
    log.debug3(DEBUG_HEADER + "SQL = '" + FIND_PUBLICATION_BY_ISBNS_QUERY
	+ "'.");
    PreparedStatement findPublicationByIsbns =
	sqlDbManager.prepareStatement(conn, FIND_PUBLICATION_BY_ISBNS_QUERY);

    try {
      findPublicationByIsbns.setLong(1, publisherSeq);
      findPublicationByIsbns.setString(2, pIsbn);
      findPublicationByIsbns.setString(3, eIsbn);
      findPublicationByIsbns.setString(4, mdItemType);

      resultSet = sqlDbManager.executeQuery(findPublicationByIsbns);
      if (resultSet.next()) {
	publicationSeq = resultSet.getLong(PUBLICATION_SEQ_COLUMN);
	log.debug3(DEBUG_HEADER + "publicationSeq = " + publicationSeq);
      }
    } catch (SQLException sqle) {
      log.error("Cannot find publication", sqle);
      log.error("publisherSeq = '" + publisherSeq + "'.");
      log.error("pIsbn = " + pIsbn);
      log.error("eIsbn = " + eIsbn);
      log.error("SQL = '" + FIND_PUBLICATION_BY_ISBNS_QUERY + "'.");
      throw sqle;
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      SqlDbManager.safeCloseStatement(findPublicationByIsbns);
    }

    return publicationSeq;
  }

  /**
   * Provides the identifier of a publication by its title and publisher.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param title
   *          A String with the title of the publication.
   * @param publisherSeq
   *          A Long with the publisher identifier.
   * @param mdItemType
   *          A String with the type of publication to be identified.
   * @return a Long with the identifier of the publication.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private Long findPublicationByName(Connection conn, String title,
      Long publisherSeq, String mdItemType) throws SQLException {
    final String DEBUG_HEADER = "findPublicationByName(): ";
    Long publicationSeq = null;
    ResultSet resultSet = null;
    log.debug3(DEBUG_HEADER + "SQL = '" + FIND_PUBLICATION_BY_NAME_QUERY
    		+ "'.");
    PreparedStatement findPublicationByName =
	sqlDbManager.prepareStatement(conn, FIND_PUBLICATION_BY_NAME_QUERY);

    try {
      findPublicationByName.setLong(1, publisherSeq);
      findPublicationByName.setString(2, title);
      findPublicationByName.setString(3, mdItemType);

      resultSet = sqlDbManager.executeQuery(findPublicationByName);
      if (resultSet.next()) {
	publicationSeq = resultSet.getLong(PUBLICATION_SEQ_COLUMN);
	log.debug3(DEBUG_HEADER + "publicationSeq = " + publicationSeq);
      }
    } catch (SQLException sqle) {
      log.error("Cannot find publication", sqle);
      log.error("publisherSeq = '" + publisherSeq + "'.");
      log.error("title = " + title);
      log.error("SQL = '" + FIND_PUBLICATION_BY_NAME_QUERY + "'.");
      throw sqle;
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      SqlDbManager.safeCloseStatement(findPublicationByName);
    }

    return publicationSeq;
  }

  /**
   * Provides the identifier of a metadata item type by its name.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param typeName
   *          A String with the name of the metadata item type.
   * @return a Long with the identifier of the metadata item type.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  public Long findMetadataItemType(Connection conn, String typeName)
      throws SQLException {
    final String DEBUG_HEADER = "findMetadataItemType(): ";
    Long mdItemTypeSeq = null;
    ResultSet resultSet = null;

    PreparedStatement findMdItemType =
	sqlDbManager.prepareStatement(conn, FIND_MD_ITEM_TYPE_QUERY);

    try {
      findMdItemType.setString(1, typeName);

      resultSet = sqlDbManager.executeQuery(findMdItemType);
      if (resultSet.next()) {
	mdItemTypeSeq = resultSet.getLong(MD_ITEM_TYPE_SEQ_COLUMN);
      }
    } catch (SQLException sqle) {
      log.error("Cannot find metadata item type", sqle);
      log.error("typeName = '" + typeName + "'.");
      log.error("SQL = '" + FIND_MD_ITEM_TYPE_QUERY + "'.");
      throw sqle;
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      SqlDbManager.safeCloseStatement(findMdItemType);
    }

    log.debug3(DEBUG_HEADER + "mdItemTypeSeq = " + mdItemTypeSeq);
    return mdItemTypeSeq;
  }

  /**
   * Adds a metadata item to the database.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param parentSeq
   *          A Long with the metadata item parent identifier.
   * @param auMdSeq
   *          A Long with the identifier of the Archival Unit metadata.
   * @param mdItemTypeSeq
   *          A Long with the identifier of the type of metadata item.
   * @param date
   *          A String with the publication date of the metadata item.
   * @param coverage
   *          A String with the metadata item coverage.
   * @return a Long with the identifier of the metadata item just added.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  public Long addMdItem(Connection conn, Long parentSeq,
      Long mdItemTypeSeq, Long auMdSeq, String date,
      String coverage) throws SQLException {
    final String DEBUG_HEADER = "addMdItem(): ";
    PreparedStatement insertMdItem = sqlDbManager.prepareStatement(conn,
	INSERT_MD_ITEM_QUERY, Statement.RETURN_GENERATED_KEYS);

    ResultSet resultSet = null;
    Long mdItemSeq = null;

    try {
      // skip auto-increment key field #0
      if (parentSeq != null) {
	insertMdItem.setLong(1, parentSeq);
      } else {
	insertMdItem.setNull(1, BIGINT);
      }
      insertMdItem.setLong(2, mdItemTypeSeq);
      if (auMdSeq != null) {
	insertMdItem.setLong(3, auMdSeq);
      } else {
	insertMdItem.setNull(3, BIGINT);
      }
      insertMdItem.setString(4, date);
      insertMdItem.setString(5, coverage);
      sqlDbManager.executeUpdate(insertMdItem);
      resultSet = insertMdItem.getGeneratedKeys();

      if (!resultSet.next()) {
    log.error("Unable to create metadata item table row.");
	return null;
      }

      mdItemSeq = resultSet.getLong(1);
      log.debug3(DEBUG_HEADER + "Added mdItemSeq = " + mdItemSeq);
    } catch (SQLException sqle) {
      log.error("Cannot insert metadata item", sqle);
      log.error("SQL = '" + INSERT_MD_ITEM_QUERY + "'.");
      throw sqle;
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      SqlDbManager.safeCloseStatement(insertMdItem);
    }

    return mdItemSeq;
  }

  /**
   * Provides the names of a metadata item.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param mdItemSeq
   *          A Long with the metadata item identifier.
   * @return a Map<String, String> with the names and name types of the metadata
   *         item.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private Map<String, String> getMdItemNames(Connection conn, Long mdItemSeq)
      throws SQLException {
    final String DEBUG_HEADER = "getMdItemNames(): ";
    Map<String, String> names = new HashMap<String, String>();
    PreparedStatement getNames =
	sqlDbManager.prepareStatement(conn, FIND_MD_ITEM_NAME_QUERY);
    ResultSet resultSet = null;

    try {
      getNames.setLong(1, mdItemSeq);
      resultSet = sqlDbManager.executeQuery(getNames);
      while (resultSet.next()) {
	names.put(resultSet.getString(NAME_COLUMN),
		  resultSet.getString(NAME_TYPE_COLUMN));
	log.debug3(DEBUG_HEADER + "Found metadata item name = '"
	    + resultSet.getString(NAME_COLUMN) + "' of type '"
	    + resultSet.getString(NAME_TYPE_COLUMN) + "'.");
      }
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      SqlDbManager.safeCloseStatement(getNames);
    }

    return names;
  }

  /**
   * Adds a metadata item name to the database.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param mdItemSeq
   *          A Long with the metadata item identifier.
   * @param name
   *          A String with the name of the metadata item.
   * @param type
   *          A String with the type of name of the metadata item.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  public void addMdItemName(Connection conn, Long mdItemSeq, String name,
      String type) throws SQLException {
    final String DEBUG_HEADER = "addMdItemName(): ";

    if (name == null || type == null) {
      return;
    }

    PreparedStatement insertMdItemName =
	sqlDbManager.prepareStatement(conn, INSERT_MD_ITEM_NAME_QUERY);

    try {
      insertMdItemName.setLong(1, mdItemSeq);
      insertMdItemName.setString(2, name);
      insertMdItemName.setString(3, type);
      int count = sqlDbManager.executeUpdate(insertMdItemName);

      if (log.isDebug3()) {
	log.debug3(DEBUG_HEADER + "count = " + count);
	log.debug3(DEBUG_HEADER + "Added metadata item name = " + name);
      }
    } finally {
      SqlDbManager.safeCloseStatement(insertMdItemName);
    }
  }

  /**
   * Adds to the database a metadata item URL.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param mdItemSeq
   *          A Long with the metadata item identifier.
   * @param feature
   *          A String with the feature of the metadata item URL.
   * @param url
   *          A String with the metadata item URL.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  public void addMdItemUrl(Connection conn, Long mdItemSeq, String feature,
      String url) throws SQLException {
    final String DEBUG_HEADER = "addMdItemUrl(): ";
    PreparedStatement insertMdItemUrl =
	sqlDbManager.prepareStatement(conn, INSERT_URL_QUERY);

    try {
      insertMdItemUrl.setLong(1, mdItemSeq);
      insertMdItemUrl.setString(2, feature);
      insertMdItemUrl.setString(3, url);
      int count = sqlDbManager.executeUpdate(insertMdItemUrl);

      if (log.isDebug3()) {
	log.debug3(DEBUG_HEADER + "count = " + count);
	log.debug3(DEBUG_HEADER + "Added URL = " + url);
      }
    } finally {
      SqlDbManager.safeCloseStatement(insertMdItemUrl);
    }
  }

  /**
   * Adds to the database a metadata item DOI.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param mdItemSeq
   *          A Long with the metadata item identifier.
   * @param doi
   *          A String with the DOI of the metadata item.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  public void addMdItemDoi(Connection conn, Long mdItemSeq, String doi)
      throws SQLException {
    final String DEBUG_HEADER = "addMdItemDoi(): ";

    if (StringUtil.isNullString(doi)) {
      return;
    }

    PreparedStatement insertMdItemDoi =
	sqlDbManager.prepareStatement(conn, INSERT_DOI_QUERY);

    try {
      insertMdItemDoi.setLong(1, mdItemSeq);
      insertMdItemDoi.setString(2, doi);
      int count = sqlDbManager.executeUpdate(insertMdItemDoi);

      if (log.isDebug3()) {
	log.debug3(DEBUG_HEADER + "count = " + count);
	log.debug3(DEBUG_HEADER + "Added DOI = " + doi);
      }
    } finally {
      SqlDbManager.safeCloseStatement(insertMdItemDoi);
    }
  }

  /**
   * Adds an AU to the list of AUs to be reindexed.
   * Does incremental reindexing if possible.
   * 
   * @param au
   *          An ArchivalUnit with the AU to be reindexed.
   * @return <code>true</code> if au was added for reindexing
   */
  public boolean addAuToReindex(ArchivalUnit au) {
    return addAuToReindex(au, false, false);
  }

  /**
   * Adds an AU to the list of AUs to be reindexed.
   * Does incremental reindexing if possible.
   * 
   * @param au
   *          An ArchivalUnit with the AU to be reindexed.
   * @param inBatch
   *          A boolean indicating whether the reindexing of this AU should be
   *          performed as part of a batch.
   * @return <code>true</code> if au was added for reindexing
   */
  public boolean addAuToReindex(ArchivalUnit au, boolean inBatch) {
    return addAuToReindex(au, inBatch, false);
  }
  
  /**
   * Adds an AU to the list of AUs to be reindexed. Optionally causes
   * full reindexing by removing the AU from the database.
   * 
   * @param au
   *          An ArchivalUnit with the AU to be reindexed.
   * @param inBatch
   *          A boolean indicating whether the reindexing of this AU should be
   *          performed as part of a batch.
   * @param fullReindex
   *          Causes a full reindex by removing that AU from the database. 
   * @return <code>true</code> if au was added for reindexing
   */
  public boolean addAuToReindex(
      ArchivalUnit au, boolean inBatch, boolean fullReindex) {
    final String DEBUG_HEADER = "addAuToReindex(): ";

    synchronized (activeReindexingTasks) {

      try {
        // If disabled crawl completion rescheduling
        // a running task, have this function report false;
        if (disableCrawlRescheduleTask
            && activeReindexingTasks.containsKey(au.getAuId())) {
          log.debug2(DEBUG_HEADER + "Not adding AU to reindex: "
              + au.getName());
          return false;
        }

        log.debug2(DEBUG_HEADER + "Adding AU to reindex: " + au.getName());
        
        if (pendingAusBatchConnection == null) {
          pendingAusBatchConnection = sqlDbManager.getConnection();

          if (pendingAusBatchConnection == null) {
            log.error("Cannot connect to database"
                + " -- cannot add aus to pending aus");
            return false;
          }
        }

        // Remove it from the list if it was marked as disabled.
        removeDisabledFromPendingAus(pendingAusBatchConnection, au.getAuId());

        // If it's not possible to reschedule the current task, add the AU to
        // the pending list.
        if (!rescheduleAuTask(au.getAuId())) {
          addToPendingAus(pendingAusBatchConnection, Collections.singleton(au),
                          inBatch);
        }

        // force a full reindex by removing the AU metadata from the database
        if (fullReindex) {
          removeAu(pendingAusBatchConnection, au.getAuId());
        }
        
        startReindexing(pendingAusBatchConnection);
        pendingAusBatchConnection.commit();

        // once transaction has committed, resync AU count with database
        if (fullReindex) {
          metadataArticleCount = getArticleCount(pendingAusBatchConnection);
        }
        
        return true;

      } catch (SQLException ex) {
        log.error("Cannot add au to pending AUs: " + au.getName(), ex);
        return false;
      } finally {
	if (!inBatch) {
	  SqlDbManager.safeRollbackAndClose(pendingAusBatchConnection);
	  pendingAusBatchConnection = null;
	}
      }
    }
  }

  /**
   * Removes an AU with disabled indexing from the table of pending AUs.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param auId
   *          A String with the Archiva lUnit identifier.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private void removeDisabledFromPendingAus(Connection conn, String auId)
      throws SQLException {
    PreparedStatement deletePendingAu =
	sqlDbManager.prepareStatement(conn, DELETE_DISABLED_PENDING_AU_QUERY);

    try {
      String pluginId = PluginManager.pluginIdFromAuId(auId);
      String auKey = PluginManager.auKeyFromAuId(auId);
  
      deletePendingAu.setString(1, pluginId);
      deletePendingAu.setString(2, auKey);
      sqlDbManager.executeUpdate(deletePendingAu);
    } catch (SQLException sqle) {
      log.error("Cannot remove disabled AU from pending table", sqle);
      log.error("auId = '" + auId + "'.");
      log.error("SQL = '" + DELETE_DISABLED_PENDING_AU_QUERY + "'.");
      throw sqle;
    } finally {
      SqlDbManager.safeCloseStatement(deletePendingAu);
    }

    pendingAusCount = getEnabledPendingAusCount(conn);
  }

  /**
   * Reschedules a reindexing task for a specified AU.
   * 
   * @param auId
   *          A String with the Archiva lUnit identifier.
   * @return <code>true</code> if task was rescheduled, <code>false</code>
   *         otherwise.
   */
  private boolean rescheduleAuTask(String auId) {
    final String DEBUG_HEADER = "rescheduleAuTask(): ";
    ReindexingTask task = activeReindexingTasks.get(auId);

    if (task != null) {
      log.debug2(DEBUG_HEADER
	  + "Rescheduling pending reindexing task for auId " + auId);
      // Task rescheduling will remove the task, and cause it to be rescheduled.
      task.reschedule();
      return true;
    }

    return false;
  }

  /**
   * Disables the indexing of an AU.
   * 
   * @param au
   *          An ArchivalUnit with the AU for which indexing is to be disabled.
   * @return <code>true</code> if au was added for reindexing,
   *         <code>false</code> otherwise.
   */
  public boolean disableAuIndexing(ArchivalUnit au) {
    final String DEBUG_HEADER = "disableAuIndexing(): ";

    synchronized (activeReindexingTasks) {
      Connection conn = null;

      try {
        log.debug2(DEBUG_HEADER + "Disabing indexing for AU " + au.getName());
        conn = sqlDbManager.getConnection();

        if (conn == null) {
          log.error("Cannot connect to database"
              + " -- cannot disable indexing for AU");
          return false;
        }

        String auId = au.getAuId();
        log.debug2(DEBUG_HEADER + "auId " + auId);

        if (activeReindexingTasks.containsKey(auId)) {
          ReindexingTask task = activeReindexingTasks.get(auId);
          task.cancel();
          activeReindexingTasks.remove(task);
        }

        removeFromPendingAus(conn, auId);

        PreparedStatement insertDisabledPendingAu =
            sqlDbManager.prepareStatement(conn, INSERT_DISABLED_PENDING_AU_QUERY);

        String pluginId = PluginManager.pluginIdFromAuId(auId);
        String auKey = PluginManager.auKeyFromAuId(auId);

        insertDisabledPendingAu.setString(1, pluginId);
        insertDisabledPendingAu.setString(2, auKey);
        int count = sqlDbManager.executeUpdate(insertDisabledPendingAu);
  	log.debug2(DEBUG_HEADER + "count = " + count);
        conn.commit();

        return true;
      } catch (SQLException ex) {
        log.error("Cannot disable AU: " + au.getName(), ex);
        return false;
      } finally {
        SqlDbManager.safeRollbackAndClose(conn);
      }
    }
  }

  /**
   * Adds AUs to the list of pending AUs to reindex.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param aus
   *          A Collection<ArchivalUnit> with the AUs to add.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  void addToPendingAus(Connection conn, Collection<ArchivalUnit> aus)
      throws SQLException {
    addToPendingAus(conn, aus, false);
  }

  /**
   * Adds AUs to the list of pending AUs to reindex.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param aus
   *          A Collection<ArchivalUnit> with the AUs to add.
   * @param inBatch
   *          A boolean indicating whether adding these AUs to the list of
   *          pending AUs to reindex should be performed as part of a batch.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  void addToPendingAus(Connection conn, Collection<ArchivalUnit> aus,
      boolean inBatch) throws SQLException {
    final String DEBUG_HEADER = "addToPendingAus(): ";
    PreparedStatement selectPendingAu =
	sqlDbManager.prepareStatement(conn, FIND_PENDING_AU_QUERY);

    if (insertPendingAuBatchStatement == null) {
      insertPendingAuBatchStatement =
	  sqlDbManager.prepareStatement(conn, INSERT_ENABLED_PENDING_AU_QUERY);
    }

    ResultSet results = null;
    log.debug2(DEBUG_HEADER + "maxPendingAuBatchSize = " + maxPendingAuBatchSize);
    log.debug2(DEBUG_HEADER + "Number of pending aus to add: " + aus.size());

    try {
      // Loop through all the AUs.
      for (ArchivalUnit au : aus) {
        // Only add for extraction iff it has article metadata.
        if (!hasArticleMetadata(au)) {
          log.debug3(DEBUG_HEADER + "Not adding au " + au.getName()
              + " to pending list because it has no metadata");
        } else {
          String auid = au.getAuId();
          String pluginId = PluginManager.pluginIdFromAuId(auid);
          String auKey = PluginManager.auKeyFromAuId(auid);

          // Find the AU in the table.
          selectPendingAu.setString(1, pluginId);
          selectPendingAu.setString(2, auKey);
          results = sqlDbManager.executeQuery(selectPendingAu);

          if (!results.next()) {
            // Only insert if entry does not exist.
	    log.debug3(DEBUG_HEADER + "Adding au " + au.getName()
		+ " to pending list");
            insertPendingAuBatchStatement.setString(1, pluginId);
            insertPendingAuBatchStatement.setString(2, auKey);
            insertPendingAuBatchStatement.addBatch();
            pendingAuBatchCurrentSize++;
	    log.debug3(DEBUG_HEADER + "pendingAuBatchCurrentSize = "
		+ pendingAuBatchCurrentSize);

	    // Check whether the maximum batch size has been reached.
	    if (pendingAuBatchCurrentSize >= maxPendingAuBatchSize) {
	      // Yes: Perform the insertion of all the AUs in the batch.
	      log.debug3(DEBUG_HEADER + "Executing batch...");
	      insertPendingAuBatchStatement.executeBatch();
	      pendingAuBatchCurrentSize = 0;
	      log.debug3(DEBUG_HEADER + "pendingAuBatchCurrentSize = "
		  + pendingAuBatchCurrentSize);
	    }
          } else {
            log.debug3(DEBUG_HEADER+ "Not adding au " + au.getName()
                       + " to pending list becuase it is already on the list");
          }

          SqlDbManager.safeCloseResultSet(results);
	}
      }

      // Check whether there are no more AUs to be batched and the batch is not
      // empty.
      if (!inBatch && pendingAuBatchCurrentSize > 0) {
	// Yes: Perform the insertion of all the AUs in the batch.
	log.debug3(DEBUG_HEADER + "Executing batch...");
	insertPendingAuBatchStatement.executeBatch();
	pendingAuBatchCurrentSize = 0;
	log.debug3(DEBUG_HEADER + "pendingAuBatchCurrentSize = "
	    + pendingAuBatchCurrentSize);
      }
    } finally {
      SqlDbManager.safeCloseResultSet(results);

      // Check whether there are no more AUs to be batched.
      if (!inBatch) {
	// Yes: Perform the insertion of all the AUs in the batch.
	SqlDbManager.safeCloseStatement(insertPendingAuBatchStatement);
	insertPendingAuBatchStatement = null;
      }

      SqlDbManager.safeCloseStatement(selectPendingAu);
    }

    pendingAusCount = getEnabledPendingAusCount(conn);
  }

  /**
   * Provides an indication of whether an AU has article metadata.
   * 
   * @param au
   *          An ArchivalUnit with the Archival Unit.
   * @return <code>true</code> if the AU has article metadata,
   *         <code>false</code> otherwise.
   */
  private boolean hasArticleMetadata(ArchivalUnit au) {
    if (au.getArticleIterator(MetadataTarget.OpenURL()) == null) {
      return false;
    }

    // It has article metadata if there is a metadata extractor.
    if (useMetadataExtractor) {
      Plugin p = au.getPlugin();

      if (p.getArticleMetadataExtractor(MetadataTarget.OpenURL(), au) != null) {
        return true;
      }
    }

    // Otherwise, it has metadata if it can be created from the TdbAu.
    return (au.getTdbAu() != null);
  }

  /**
   * Notifies listeners that an AU is being reindexed.
   * 
   * @param au
   *          An ArchivalUnit with the Archival Unit.
   */
  protected void notifyStartReindexingAu(ArchivalUnit au) {
  }

  /**
   * Notifies listeners that an AU is finished being reindexed.
   * 
   * @param au
   *          An ArchivalUnit with the Archival Unit.
   * @param status
   *          A ReindexingStatus with the status of the reindexing process.
   */
  protected void notifyFinishReindexingAu(ArchivalUnit au,
      ReindexingStatus status) {
  }

  /**
   * Deletes an AU and starts the next reindexing task.
   * 
   * @param au
   *          An ArchivalUnit with the Archival Unit.
   * @return <code>true</code> if the AU was deleted, <code>false</code>
   *         otherwise.
   */
  boolean deleteAuAndReindex(ArchivalUnit au) {
    final String DEBUG_HEADER = "deleteAuAndReindex(): ";

    synchronized (activeReindexingTasks) {
      Connection conn = null;

      try {
        log.debug2(DEBUG_HEADER + "Removing au to reindex: " + au.getName());
        // add pending AU
        conn = sqlDbManager.getConnection();

        if (conn == null) {
          log.error("Cannot connect to database"
              + " -- cannot add aus to pending aus");
          return false;
        }

        deleteAu(conn, au.getAuId());

        // Force reindexing to start next task.
        startReindexing(conn);
        conn.commit();

        return true;
      } catch (SQLException ex) {
        log.error("Cannot remove au to pending AUs: " + au.getName(), ex);
        return false;
      } finally {
        SqlDbManager.safeRollbackAndClose(conn);
      }
    }
  }

  /**
   * Provides the metadata version of a plugin.
   * 
   * @param plugin
   *          A Plugin with the plugin.
   * @return an int with the plugin metadata version.
   */
  int getPluginMetadataVersionNumber(Plugin plugin) {
    final String DEBUG_HEADER = "getPluginMetadataVersionNumber(): ";
  
    int version = 1;
    String pluginVersion = plugin.getFeatureVersion(Feature.Metadata);
    if (log.isDebug3()) {
      log.debug3(DEBUG_HEADER + "Metadata Featrure version: " +pluginVersion
		 + " for " + plugin.getPluginName());
    }
    if (StringUtil.isNullString(pluginVersion)) {
      log.debug2("Plugin version not found: Using " + version);
      return version;
    }

    String prefix = Feature.Metadata + "_";

    if (!pluginVersion.startsWith(prefix)) {
      log.error("Plugin version '" + pluginVersion + "' does not start with '"
	  + prefix + "': Using " + version);
      return version;
    }

    try {
      version = Integer.valueOf(pluginVersion.substring(prefix.length()));
    } catch (NumberFormatException nfe) {
      log.error("Plugin version '" + pluginVersion + "' does not end with a "
	  + "number after '" + prefix + "': Using " + version);
    }
    
    log.debug3(DEBUG_HEADER + "version = " + version);
    return version;
  }

  void incrementSuccessfulReindexingCount() {
    this.successfulReindexingCount++;
  }

  synchronized void addToMetadataArticleCount(long count) {
    this.metadataArticleCount += count;
  }

  void incrementFailedReindexingCount() {
    this.failedReindexingCount++;
  }

  /**
   * Provides an indication of whether the version of the metadata of an AU
   * stored in the database has been obtained with an obsolete version of the
   * plugin.
   * 
   * @param au
   *          An ArchivalUnit with the AU involved.
   * @return <code>true</code> if the metadata was obtained with a version of
   *         the plugin previous to the current version, <code>false</code>
   *         otherwise.
   */
  boolean isAuMetadataForObsoletePlugin(ArchivalUnit au) {
    final String DEBUG_HEADER = "isAuMetadataForObsoletePlugin(): ";

    // Get the plugin version of the stored AU metadata. 
    int auVersion = getAuMetadataVersion(au);
    log.debug(DEBUG_HEADER + "auVersion = " + auVersion);

    // Get the current version of the plugin. 
    int pVersion = getPluginMetadataVersionNumber(au.getPlugin());
    log.debug(DEBUG_HEADER + "pVersion = " + pVersion);

    return pVersion > auVersion;
  }

  /**
   * Provides the version of the metadata of an AU stored in the database.
   * 
   * @param au
   *          An ArchivalUnit with the AU involved.
   * @return an int with the version of the metadata of the AU stored in the
   *         database.
   */
  private int getAuMetadataVersion(ArchivalUnit au) {
    int version = UNKNOWN_VERSION;
    Connection conn = null;

    try {
      // Get a connection to the database.
      conn = sqlDbManager.getConnection();

      // Get the version.
      version = getAuMetadataVersion(conn, au);
    } catch (SQLException sqle) {
      log.error("Cannot get AU metadata version - Using " + version + ": "
	  + sqle);
    } finally {
      SqlDbManager.safeRollbackAndClose(conn);
    }

    return version;
  }

  /**
   * Provides the version of the metadata of an AU stored in the database.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param au
   *          An ArchivalUnit with the AU involved.
   * @return an int with the version of the metadata of the AU stored in the
   *         database.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private int getAuMetadataVersion(Connection conn, ArchivalUnit au)
      throws SQLException {
    final String DEBUG_HEADER = "getAuMetadataVersion(): ";
    int version = UNKNOWN_VERSION;
    PreparedStatement selectMetadataVersion = null;
    ResultSet resultSet = null;

    try {
      String auId = au.getAuId();
      String pluginId = PluginManager.pluginIdFromAuId(auId);
      log.debug2(DEBUG_HEADER + "pluginId() = " + pluginId);

      String auKey = PluginManager.auKeyFromAuId(auId);
      log.debug2(DEBUG_HEADER + "auKey = " + auKey);

      selectMetadataVersion =
	  sqlDbManager.prepareStatement(conn, FIND_AU_METADATA_VERSION_QUERY);
      selectMetadataVersion.setString(1, pluginId);
      selectMetadataVersion.setString(2, auKey);
      resultSet = sqlDbManager.executeQuery(selectMetadataVersion);

      if (resultSet.next()) {
	version = resultSet.getShort(MD_VERSION_COLUMN);
	log.debug2(DEBUG_HEADER + "version = " + version);
      }
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      SqlDbManager.safeCloseStatement(selectMetadataVersion);
    }

    return version;
  }

  /**
   * Provides an indication of whether the version of the metadata of an AU
   * stored in the database has been obtained with an obsolete version of the
   * plugin.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param au
   *          An ArchivalUnit with the AU involved.
   * @return <code>true</code> if the metadata was obtained with a version of
   *         the plugin previous to the current version, <code>false</code>
   *         otherwise.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  boolean isAuMetadataForObsoletePlugin(Connection conn, ArchivalUnit au)
      throws SQLException {
    final String DEBUG_HEADER = "isAuMetadataForObsoletePlugin(): ";

    // Get the plugin version of the stored AU metadata. 
    int auVersion = getAuMetadataVersion(conn, au);
    log.debug2(DEBUG_HEADER + "auVersion = " + auVersion);

    // Get the current version of the plugin. 
    int pVersion = getPluginMetadataVersionNumber(au.getPlugin());
    log.debug2(DEBUG_HEADER + "pVersion = " + pVersion);

    return pVersion > auVersion;
  }

  /**
   * Provides the extraction time of an Archival Unit metadata.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param auSeq
   *          A Long with the identifier of the Archival Unit.
   * @return a long with the extraction time of the Archival Unit metadata.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  long getAuExtractionTime(Connection conn, Long auSeq)
	throws SQLException {
    final String DEBUG_HEADER = "getAuExtractionTime(): ";
    long timestamp = NEVER_EXTRACTED_EXTRACTION_TIME;
    PreparedStatement selectLastExtractionTime = null;
    ResultSet resultSet = null;

    try {
      selectLastExtractionTime = sqlDbManager.prepareStatement(conn,
	  FIND_AU_MD_EXTRACT_TIME_BY_AUSEQ_QUERY);
      selectLastExtractionTime.setLong(1, auSeq);
      resultSet = sqlDbManager.executeQuery(selectLastExtractionTime);

      if (resultSet.next()) {
	timestamp = resultSet.getLong(EXTRACT_TIME_COLUMN);
	log.debug2(DEBUG_HEADER + "timestamp = " + timestamp);
      }
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      SqlDbManager.safeCloseStatement(selectLastExtractionTime);
    }

    return timestamp;
  }

  /**
   * Provides an indication of whether the metadata of an AU has not been saved
   * in the database after the last successful crawl of the AU.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param au
   *          An ArchivalUnit with the AU involved.
   * @return <code>true</code> if the metadata of the AU has not been saved in
   *         the database after the last successful crawl of the AU,
   *         <code>false</code> otherwise.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  boolean isAuCrawledAndNotExtracted(Connection conn, ArchivalUnit au)
      throws SQLException {
    final String DEBUG_HEADER = "isAuCrawledAndNotExtracted(): ";

    // Get the time of the last successful crawl of the AU. 
    long lastCrawlTime = AuUtil.getAuState(au).getLastCrawlTime();
    log.debug2(DEBUG_HEADER + "lastCrawlTime = " + lastCrawlTime);

    long lastExtractionTime = getAuExtractionTime(conn, au);
    log.debug2(DEBUG_HEADER + "lastExtractionTime = " + lastExtractionTime);

    return lastCrawlTime > lastExtractionTime;
  }

  /**
   * Provides the extraction time of an Archival Unit metadata.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param au
   *          An ArchivalUnit with the AU involved.
   * @return a long with the extraction time of the Archival Unit metadata.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private long getAuExtractionTime(Connection conn, ArchivalUnit au)
	throws SQLException {
    final String DEBUG_HEADER = "getAuExtractionTime(): ";
    long timestamp = NEVER_EXTRACTED_EXTRACTION_TIME;
    PreparedStatement selectLastExtractionTime = null;
    ResultSet resultSet = null;

    try {
      String auId = au.getAuId();
      String pluginId = PluginManager.pluginIdFromAuId(auId);
      log.debug2(DEBUG_HEADER + "pluginId() = " + pluginId);

      String auKey = PluginManager.auKeyFromAuId(auId);
      log.debug2(DEBUG_HEADER + "auKey = " + auKey);

      selectLastExtractionTime =
	  sqlDbManager.prepareStatement(conn, FIND_AU_MD_EXTRACT_TIME_BY_AU_QUERY);
      selectLastExtractionTime.setString(1, pluginId);
      selectLastExtractionTime.setString(2, auKey);
      resultSet = sqlDbManager.executeQuery(selectLastExtractionTime);

      if (resultSet.next()) {
	timestamp = resultSet.getLong(EXTRACT_TIME_COLUMN);
	log.debug2(DEBUG_HEADER + "timestamp = " + timestamp);
      }
    } finally {
	SqlDbManager.safeCloseResultSet(resultSet);
	SqlDbManager.safeCloseStatement(selectLastExtractionTime);
    }

    return timestamp;
  }

  /**
   * Utility method to provide the database manager.
   * 
   * @return a SqlDbManager with the database manager.
   */
  SqlDbManager getDbManager() {
    return sqlDbManager;
  }

  public void setDbManager(SqlDbManager sqlDbManager) {
	  this.sqlDbManager = sqlDbManager;
  }
}
