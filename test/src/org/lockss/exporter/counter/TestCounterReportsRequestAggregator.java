/*
 * $Id: TestCounterReportsRequestAggregator.java,v 1.6 2013/01/14 21:58:18 fergaloy-sf Exp $
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
 * Test class for org.lockss.exporter.counter.CounterReportsRequestAggregator.
 * 
 * @author Fernando Garcia-Loygorri
 * @version 1.0
 */
package org.lockss.exporter.counter;

import static org.lockss.db.SqlDbManager.*;
import static org.lockss.metadata.SqlMetadataManager.PRIMARY_NAME_TYPE;
import static org.lockss.plugin.ArticleFiles.*;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.Properties;
import org.lockss.config.ConfigManager;
import org.lockss.daemon.Cron;
import org.lockss.db.SqlDbManager;
import org.lockss.exporter.counter.CounterReportsManager;
import org.lockss.exporter.counter.CounterReportsRequestAggregator;
import org.lockss.metadata.SqlMetadataManager;
import org.lockss.repository.LockssRepositoryImpl;
import org.lockss.test.ConfigurationUtil;
import org.lockss.test.LockssTestCase;
import org.lockss.test.MockLockssDaemon;
import org.lockss.util.TimeBase;

public class TestCounterReportsRequestAggregator extends LockssTestCase {
  private static final String FULL_URL = "http://example.com/full.url";
  private static final String SECTION_URL = "http://example.com/section.url";
  private static final String HTML_URL = "http://example.com/html.url";
  private static final String PDF_URL = "http://example.com/pdf.url";

  // Query to count all the request rows.
  private static final String SQL_QUERY_REQUEST_COUNT = "select count(*) from "
      + COUNTER_REQUEST_TABLE;

  // Query to count all the rows of book type aggregated totals.
  private static final String SQL_QUERY_BOOK_TYPE_AGGREGATED_TOTAL_COUNT =
      "select count(*) from " + COUNTER_BOOK_TYPE_AGGREGATES_TABLE;

  // Query to count all the rows of journal type aggregated totals.
  private static final String SQL_QUERY_JOURNAL_TYPE_AGGREGATED_TOTAL_COUNT =
      "select count(*) from " + COUNTER_JOURNAL_TYPE_AGGREGATES_TABLE;

  // Query to count monthly rows of book type aggregated totals.
  private static final String SQL_QUERY_BOOK_TYPE_AGGREGATED_MONTH_COUNT =
      "select count(*) "
      + "from " + COUNTER_BOOK_TYPE_AGGREGATES_TABLE
      + " where " + REQUEST_YEAR_COLUMN + " = ? "
      + "and " + REQUEST_MONTH_COLUMN + " = ? "
      + "and " + IS_PUBLISHER_INVOLVED_COLUMN + " = ?";

  // Query to count monthly rows of journal type aggregated totals.
  private static final String SQL_QUERY_JOURNAL_TYPE_AGGREGATED_MONTH_COUNT =
      "select count(*) "
      + "from " + COUNTER_JOURNAL_TYPE_AGGREGATES_TABLE
      + " where " + REQUEST_YEAR_COLUMN + " = ? "
      + "and " + REQUEST_MONTH_COLUMN + " = ? "
      + "and " + IS_PUBLISHER_INVOLVED_COLUMN + " = ?";

  // Query to get aggregated type book request counts for a month.
  private static final String SQL_QUERY_TYPE_AGGREGATED_MONTH_BOOK_SELECT =
      "select "
      + FULL_REQUESTS_COLUMN + ","
      + SECTION_REQUESTS_COLUMN
      + " from " + COUNTER_BOOK_TYPE_AGGREGATES_TABLE
      + " where " + PUBLICATION_SEQ_COLUMN + " = ? "
      + "and " + REQUEST_YEAR_COLUMN + " = ? "
      + "and " + REQUEST_MONTH_COLUMN + " = ? "
      + "and " + IS_PUBLISHER_INVOLVED_COLUMN + " = ?";

  // Query to get aggregated type journal request counts for a month.
  private static final String SQL_QUERY_TYPE_AGGREGATED_MONTH_JOURNAL_SELECT =
      "select "
      + HTML_REQUESTS_COLUMN + ","
      + PDF_REQUESTS_COLUMN + ","
      + TOTAL_REQUESTS_COLUMN
      + " from " + COUNTER_JOURNAL_TYPE_AGGREGATES_TABLE
      + " where " + PUBLICATION_SEQ_COLUMN + " = ? "
      + "and " + REQUEST_YEAR_COLUMN + " = ? "
      + "and " + REQUEST_MONTH_COLUMN + " = ? "
      + "and " + IS_PUBLISHER_INVOLVED_COLUMN + " = ?";

  // Query to count all the rows of publication year aggregated totals.
  private static final String SQL_QUERY_JOURNAL_PUBYEAR_AGGREGATED_TOTAL_COUNT =
      "select count(*) from " + COUNTER_JOURNAL_PUBYEAR_AGGREGATE_TABLE;

  // Query to get aggregated publication year journal request counts for a
  // month.
  private static final String SQL_QUERY_JOURNAL_PUBYEAR_AGGREGATED_MONTH_SELECT =
      "select "
      + PUBLICATION_YEAR_COLUMN + ","
      + REQUESTS_COLUMN
      + " from " + COUNTER_JOURNAL_PUBYEAR_AGGREGATE_TABLE
      + " where " + PUBLICATION_SEQ_COLUMN + " = ? "
      + "and " + REQUEST_YEAR_COLUMN + " = ? "
      + "and " + REQUEST_MONTH_COLUMN + " = ? "
      + "and " + IS_PUBLISHER_INVOLVED_COLUMN + " = ?";

  private MockLockssDaemon theDaemon;
  private SqlDbManager sqlDbManager;
  private SqlMetadataManager sqlMetadataManager;
  private CounterReportsManager counterReportsManager;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    String tempDirPath = getTempDir().getAbsolutePath();

    // Set the database log.
    System.setProperty("derby.stream.error.file",
		       new File(tempDirPath, "derby.log").getAbsolutePath());

    Properties props = new Properties();
    props.setProperty(LockssRepositoryImpl.PARAM_CACHE_LOCATION, tempDirPath);
    props
	.setProperty(ConfigManager.PARAM_PLATFORM_DISK_SPACE_LIST, tempDirPath);
    props.setProperty(CounterReportsManager.PARAM_COUNTER_ENABLED, "true");
    props.setProperty(CounterReportsManager.PARAM_REPORT_BASEDIR_PATH,
		      tempDirPath);
    props
	.setProperty(CounterReportsRequestAggregator
	             .PARAM_COUNTER_REQUEST_AGGREGATION_TASK_FREQUENCY,
		     "hourly");
    ConfigurationUtil.setCurrentConfigFromProps(props);

    theDaemon = getMockLockssDaemon();
    theDaemon.setDaemonInited(true);

    sqlDbManager = new SqlDbManager();
    theDaemon.setDbManager(sqlDbManager);
    sqlDbManager.initService(theDaemon);
    sqlDbManager.startService();

    sqlMetadataManager = new SqlMetadataManager();
    theDaemon.setMetadataManager(sqlMetadataManager);
    sqlMetadataManager.initService(theDaemon);
    sqlMetadataManager.startService();

    Cron cron = new Cron();
    theDaemon.setCron(cron);
    cron.initService(theDaemon);
    cron.startService();

    counterReportsManager = new CounterReportsManager();
    theDaemon.setCounterReportsManager(counterReportsManager);
    counterReportsManager.initService(theDaemon);
    counterReportsManager.startService();
  }

  /**
   * Runs all the tests.
   * <br />
   * This avoids unnecessary set up and tear down of the database.
   * 
   * @throws Exception
   */
  public void testAll() throws Exception {
    runTestEmptyAggregations();
    runTestMonthBookAggregation();
    runTestMonthJournalAggregation();
    runTestNextTime();
  }

  /**
   * Tests the aggregation of an empty system.
   * 
   * @throws Exception
   */
  public void runTestEmptyAggregations() throws Exception {
    CounterReportsRequestAggregator aggregator =
	new CounterReportsRequestAggregator(theDaemon);
    aggregator.getCronTask().execute();

    checkBookTypeAggregatedRowCount(0);
    checkJournalTypeAggregatedRowCount(0);
    checkJournalPublicationYearAggregatedRowCount(0);
  }

  /**
   * Checks the expected count of rows in the book aggregation by type table.
   * 
   * @param expected
   *          An int with the expected number of rows in the table.
   * @throws SQLException
   */
  private void checkBookTypeAggregatedRowCount(int expected)
      throws SQLException {
    Connection conn = null;
    PreparedStatement statement = null;
    ResultSet resultSet = null;
    int count = -1;
    String sql = SQL_QUERY_BOOK_TYPE_AGGREGATED_TOTAL_COUNT;

    try {
      conn = sqlDbManager.getConnection();

      statement = sqlDbManager.prepareStatement(conn, sql);
      resultSet = sqlDbManager.executeQuery(statement);

      if (resultSet.next()) {
	count = resultSet.getInt(1);
      }
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      SqlDbManager.safeCloseStatement(statement);
    }

    assertEquals(expected, count);
  }

  /**
   * Checks the expected count of rows in the journal aggregation by type table.
   * 
   * @param expected
   *          An int with the expected number of rows in the table.
   * @throws SQLException
   */
  private void checkJournalTypeAggregatedRowCount(int expected)
      throws SQLException {
    Connection conn = null;
    PreparedStatement statement = null;
    ResultSet resultSet = null;
    int count = -1;
    String sql = SQL_QUERY_JOURNAL_TYPE_AGGREGATED_TOTAL_COUNT;

    try {
      conn = sqlDbManager.getConnection();

      statement = sqlDbManager.prepareStatement(conn, sql);
      resultSet = sqlDbManager.executeQuery(statement);

      if (resultSet.next()) {
	count = resultSet.getInt(1);
      }
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      SqlDbManager.safeCloseStatement(statement);
    }

    assertEquals(expected, count);
  }

  /**
   * Checks the expected count of rows in the aggregation by publication year
   * table.
   * 
   * @param expected
   *          An int with the expected number of rows in the table.
   * @throws SQLException
   */
  private void checkJournalPublicationYearAggregatedRowCount(int expected)
      throws SQLException {
    Connection conn = null;
    PreparedStatement statement = null;
    ResultSet resultSet = null;
    int count = -1;
    String sql = SQL_QUERY_JOURNAL_PUBYEAR_AGGREGATED_TOTAL_COUNT;

    try {
      conn = sqlDbManager.getConnection();

      statement = sqlDbManager.prepareStatement(conn, sql);
      resultSet = sqlDbManager.executeQuery(statement);

      if (resultSet.next()) {
	count = resultSet.getInt(1);
      }
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      SqlDbManager.safeCloseStatement(statement);
    }

    assertEquals(expected, count);
  }

  /**
   * Tests the monthly aggregation of book requests.
   * 
   * @throws Exception
   */
  public void runTestMonthBookAggregation() throws Exception {
    Long fullPublicationSeq = initializeFullBookMetadata();
    Long sectionPublicationSeq = initializeSectionBookMetadata();

    counterReportsManager.persistRequest(FULL_URL, false);
    counterReportsManager.persistRequest(FULL_URL, true);
    counterReportsManager.persistRequest(SECTION_URL, false);
    counterReportsManager.persistRequest(SECTION_URL, true);

    checkRequestRowCount(4);

    CounterReportsRequestAggregator aggregator =
	new CounterReportsRequestAggregator(theDaemon);
    aggregator.getCronTask().execute();

    checkBookTypeAggregatedRowCount(6);

    Calendar cal = Calendar.getInstance();
    cal.setTime(TimeBase.nowDate());

    int requestMonth = cal.get(Calendar.MONTH) + 1;
    int requestYear = cal.get(Calendar.YEAR);

    checkBookTypeAggregatedRowCount(requestYear, requestMonth, Boolean.TRUE, 3);
    checkBookTypeAggregatedRowCount(requestYear, requestMonth, Boolean.FALSE,
                                    3);
    checkBookMonthlyTypeRequests(fullPublicationSeq, requestYear, requestMonth,
                                 Boolean.TRUE, 1, 0);
    checkBookMonthlyTypeRequests(sectionPublicationSeq, requestYear,
                                 requestMonth, Boolean.TRUE, 0, 1);
    checkBookMonthlyTypeRequests(fullPublicationSeq, requestYear, requestMonth,
                                 Boolean.FALSE, 1, 0);
    checkBookMonthlyTypeRequests(sectionPublicationSeq, requestYear,
                                 requestMonth, Boolean.FALSE, 0, 1);

    checkRequestRowCount(0);
  }

  /**
   * Creates a full book for which to aggregate requests.
   * 
   * @return a Long with the identifier of the created book.
   * @throws SQLException
   */
  private Long initializeFullBookMetadata() throws SQLException {
    Long publicationSeq = null;
    Connection conn = null;

    try {
      conn = sqlDbManager.getConnection();

      // Add the publisher.
      Long publisherSeq =
	  sqlMetadataManager.findOrCreatePublisher("publisher");

      // Add the publication.
      publicationSeq =
	  sqlMetadataManager.findOrCreatePublication(null, null, "FULLPISBN",
						  "FULLEISBN", publisherSeq,
						  "Full Name", "2010-01-01",
						  null, null);

      // Add the plugin.
      Long pluginSeq =
	  sqlMetadataManager.findOrCreatePlugin("fullPluginId",
	      "fullPlatform");

      // Add the AU.
      Long auSeq =
	  sqlMetadataManager.findOrCreateAu(pluginSeq, "fullAuKey");

      // Add the AU metadata.
      Long auMdSeq = sqlMetadataManager.addAuMd(auSeq, 1, 0L);

      Long parentSeq =
	  sqlMetadataManager.findPublicationMetadataItem(publicationSeq);

      Long mdItemTypeSeq =
	  sqlMetadataManager.findMetadataItemType(MD_ITEM_TYPE_BOOK);

      Long mdItemSeq =
	  sqlMetadataManager.addMdItem(conn, parentSeq, mdItemTypeSeq, auMdSeq,
	                            "2010-01-01", null);

	  sqlMetadataManager.addMdItemName(conn, mdItemSeq, "Full Name",
					PRIMARY_NAME_TYPE);

      sqlMetadataManager.addMdItemUrl(conn, mdItemSeq, ROLE_FULL_TEXT_HTML,
                                   FULL_URL);
    } finally {
      conn.commit();
      SqlDbManager.safeCloseConnection(conn);
    }
    
    return publicationSeq;
  }

  /**
   * Creates a book section for which to aggregate requests.
   * 
   * @return a Long with the identifier of the created book.
   * @throws SQLException
   */
  private Long initializeSectionBookMetadata() throws SQLException {
    Long publicationSeq = null;
    Connection conn = null;

    try {
      conn = sqlDbManager.getConnection();

      // Add the publisher.
      Long publisherSeq =
	  sqlMetadataManager.findOrCreatePublisher("publisher");

      // Add the publication.
      publicationSeq =
	  sqlMetadataManager.findOrCreatePublication(null, null,
	                                          "SECTIONPISBN",
	                                          "SECTIONEISBN", publisherSeq,
						  "Section Name", "2010-01-01",
						  null, null);

      // Add the plugin.
      Long pluginSeq =
	  sqlMetadataManager.findOrCreatePlugin("secPluginId",
	      "secPlatform");

      // Add the AU.
      Long auSeq =
	  sqlMetadataManager.findOrCreateAu(pluginSeq, "secAuKey");

      // Add the AU metadata.
      Long auMdSeq = sqlMetadataManager.addAuMd(auSeq, 1, 0L);

      Long parentSeq =
	  sqlMetadataManager.findPublicationMetadataItem(publicationSeq);

      Long mdItemTypeSeq =
	  sqlMetadataManager.findMetadataItemType(MD_ITEM_TYPE_BOOK_CHAPTER);

      Long mdItemSeq =
	  sqlMetadataManager.addMdItem(conn, parentSeq, mdItemTypeSeq, auMdSeq,
	                            "2010-01-01", null);

	  sqlMetadataManager.addMdItemName(conn, mdItemSeq, "Chapter Name",
					PRIMARY_NAME_TYPE);

      sqlMetadataManager.addMdItemUrl(conn, mdItemSeq, ROLE_FULL_TEXT_PDF,
                                   SECTION_URL);
    } finally {
      conn.commit();
      SqlDbManager.safeCloseConnection(conn);
    }
    
    return publicationSeq;
  }

  /**
   * Checks the expected count of rows in the requests table.
   * 
   * @param expected
   *          An int with the expected number of rows in the table.
   * @throws SQLException
   */
  private void checkRequestRowCount(int expected) throws SQLException {
    Connection conn = null;
    PreparedStatement statement = null;
    ResultSet resultSet = null;
    int count = -1;
    String sql = SQL_QUERY_REQUEST_COUNT;

    try {
      conn = sqlDbManager.getConnection();

      statement = sqlDbManager.prepareStatement(conn, sql);
      resultSet = sqlDbManager.executeQuery(statement);

      if (resultSet.next()) {
	count = resultSet.getInt(1);
      }
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      SqlDbManager.safeCloseStatement(statement);
    }

    assertEquals(expected, count);
  }

  /**
   * Checks the expected count of monthly rows in the book aggregation by type
   * table.
   * 
   * @param requestYear
   *          An int with the year of the month to check.
   * @param requestMonth
   *          An int with the month to check.
   * @param isPublisherInvolved
   *          A Boolean with the indication of whether a publisher is involved
   *          in the requests to check.
   * @param expected
   *          An int with the expected number of monthly rows in the table.
   * @throws SQLException
   */
  private void checkBookTypeAggregatedRowCount(int requestYear,
      int requestMonth, Boolean isPublisherInvolved, int expected)
	  throws SQLException {
    Connection conn = null;
    PreparedStatement statement = null;
    ResultSet resultSet = null;
    int count = -1;
    String sql = SQL_QUERY_BOOK_TYPE_AGGREGATED_MONTH_COUNT;

    try {
      conn = sqlDbManager.getConnection();

      statement = sqlDbManager.prepareStatement(conn, sql);
      statement.setInt(1, requestYear);
      statement.setInt(2, requestMonth);
      statement.setBoolean(3, isPublisherInvolved);
      resultSet = sqlDbManager.executeQuery(statement);

      if (resultSet.next()) {
	count = resultSet.getInt(1);
      }
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      SqlDbManager.safeCloseStatement(statement);
    }

    assertEquals(expected, count);
  }

  /**
   * Checks the expected type counts of monthly requests for a book.
   * 
   * @param apublicationSeq
   *          A Long with the identifier of the book.
   * @param requestYear
   *          An int with the year of the month to check.
   * @param requestMonth
   *          An int with the month to check.
   * @param isPublisherInvolved
   *          A Boolean with the indication of whether a publisher is involved
   *          in the requests to check.
   * @param expectedFull
   *          An int with the expected number of full requests.
   * @param expectedSection
   *          An int with the expected number of section requests.
   * @throws SQLException
   */
  private void checkBookMonthlyTypeRequests(Long publicationSeq,
      int requestYear, int requestMonth, Boolean isPublisherInvolved,
      int expectedFull, int expectedSection) throws SQLException {
    Connection conn = null;
    PreparedStatement statement = null;
    ResultSet resultSet = null;
    int fullCount = -1;
    int sectionCount = -1;
    String sql = SQL_QUERY_TYPE_AGGREGATED_MONTH_BOOK_SELECT;

    try {
      conn = sqlDbManager.getConnection();

      statement = sqlDbManager.prepareStatement(conn, sql);
      statement.setLong(1, publicationSeq);
      statement.setInt(2, requestYear);
      statement.setInt(3, requestMonth);
      statement.setBoolean(4, isPublisherInvolved);
      resultSet = sqlDbManager.executeQuery(statement);

      if (resultSet.next()) {
	fullCount = resultSet.getInt(FULL_REQUESTS_COLUMN);
	sectionCount = resultSet.getInt(SECTION_REQUESTS_COLUMN);
      }
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      SqlDbManager.safeCloseStatement(statement);
    }

    assertEquals(expectedFull, fullCount);
    assertEquals(expectedSection, sectionCount);
  }

  /**
   * Tests the monthly aggregation of journal requests.
   * 
   * @throws Exception
   */
  public void runTestMonthJournalAggregation() throws Exception {
    Long publicationSeq = initializeJournalMetadata();

    counterReportsManager.persistRequest(HTML_URL, false);
    counterReportsManager.persistRequest(HTML_URL, true);
    counterReportsManager.persistRequest(PDF_URL, false);
    counterReportsManager.persistRequest(PDF_URL, true);

    checkRequestRowCount(4);

    CounterReportsRequestAggregator aggregator =
	new CounterReportsRequestAggregator(theDaemon);
    aggregator.getCronTask().execute();

    checkJournalTypeAggregatedRowCount(4);

    Calendar cal = Calendar.getInstance();
    cal.setTime(TimeBase.nowDate());

    int requestMonth = cal.get(Calendar.MONTH) + 1;
    int requestYear = cal.get(Calendar.YEAR);

    checkJournalTypeAggregatedRowCount(requestYear, requestMonth, Boolean.TRUE,
                                       2);
    checkJournalTypeAggregatedRowCount(requestYear, requestMonth, Boolean.FALSE,
                                       2);
    checkJournalMonthlyTypeRequests(publicationSeq, requestYear, requestMonth,
                                    Boolean.TRUE, 1, 1, 2);
    checkJournalMonthlyTypeRequests(publicationSeq, requestYear, requestMonth,
                                    Boolean.FALSE, 1, 1, 2);
    checkJournalPublicationYearAggregatedRowCount(2);
    checkJournalMonthlyPublicationYearRequests(publicationSeq, requestYear,
                                               requestMonth, Boolean.TRUE,
                                               "2009", 2);
    checkJournalMonthlyPublicationYearRequests(publicationSeq, requestYear,
                                               requestMonth, Boolean.FALSE,
                                               "2009", 2);
    checkRequestRowCount(0);
  }

  /**
   * Creates a journal for which to aggregate requests.
   * 
   * @return a Long with the identifier of the created journal.
   * @throws SQLException
   */
  private Long initializeJournalMetadata() throws SQLException {
    Long publicationSeq = null;
    Connection conn = null;

    try {
      conn = sqlDbManager.getConnection();

      // Add the publisher.
      Long publisherSeq =
	  sqlMetadataManager.findOrCreatePublisher("publisher");

      // Add the publication.
      publicationSeq =
	  sqlMetadataManager.findOrCreatePublication("PISSN", "EISSN",
						  null, null, publisherSeq,
						  "JOURNAL", "2009-01-01", null,
						  null);

      // Add the plugin.
      Long pluginSeq =
	  sqlMetadataManager.findOrCreatePlugin("pluginId", "platform");

      // Add the AU.
      Long auSeq = sqlMetadataManager.findOrCreateAu(pluginSeq, "auKey");

      // Add the AU metadata.
      Long auMdSeq = sqlMetadataManager.addAuMd(auSeq, 1, 0L);

      Long parentSeq =
	  sqlMetadataManager.findPublicationMetadataItem(publicationSeq);

      Long mdItemTypeSeq = sqlMetadataManager
	  .findMetadataItemType(MD_ITEM_TYPE_JOURNAL_ARTICLE);

      Long mdItemSeq = sqlMetadataManager.addMdItem(conn, parentSeq, mdItemTypeSeq,
                                            auMdSeq, "2009-01-01", null);

	  sqlMetadataManager.addMdItemName(conn, mdItemSeq, "html", PRIMARY_NAME_TYPE);

      sqlMetadataManager.addMdItemUrl(conn, mdItemSeq, ROLE_FULL_TEXT_HTML,
                                   HTML_URL);

      mdItemSeq = sqlMetadataManager.addMdItem(conn, parentSeq, mdItemTypeSeq,
                                            auMdSeq, "2009-01-01", null);

	  sqlMetadataManager.addMdItemName(conn, mdItemSeq, "pdf", PRIMARY_NAME_TYPE);

      sqlMetadataManager.addMdItemUrl(conn, mdItemSeq, ROLE_FULL_TEXT_PDF,
                                   PDF_URL);
    } finally {
      conn.commit();
      SqlDbManager.safeCloseConnection(conn);
    }
    
    return publicationSeq;
  }

  /**
   * Checks the expected count of monthly rows in the journal aggregation by
   * type table.
   * 
   * @param requestYear
   *          An int with the year of the month to check.
   * @param requestMonth
   *          An int with the month to check.
   * @param isPublisherInvolved
   *          A Boolean with the indication of whether a publisher is involved
   *          in the requests to check.
   * @param expected
   *          An int with the expected number of monthly rows in the table.
   * @throws SQLException
   */
  private void checkJournalTypeAggregatedRowCount(int requestYear,
      int requestMonth, Boolean isPublisherInvolved, int expected)
	  throws SQLException {
    Connection conn = null;
    PreparedStatement statement = null;
    ResultSet resultSet = null;
    int count = -1;
    String sql = SQL_QUERY_JOURNAL_TYPE_AGGREGATED_MONTH_COUNT;

    try {
      conn = sqlDbManager.getConnection();

      statement = sqlDbManager.prepareStatement(conn, sql);
      statement.setInt(1, requestYear);
      statement.setInt(2, requestMonth);
      statement.setBoolean(3, isPublisherInvolved);
      resultSet = sqlDbManager.executeQuery(statement);

      if (resultSet.next()) {
	count = resultSet.getInt(1);
      }
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      SqlDbManager.safeCloseStatement(statement);
    }

    assertEquals(expected, count);
  }

  /**
   * Checks the expected type counts of monthly requests for journals.
   * 
   * @param publicationSeq
   *          An Long with the identifier of the journal.
   * @param requestYear
   *          An int with the year of the month to check.
   * @param requestMonth
   *          An int with the month to check.
   * @param isPublisherInvolved
   *          A Boolean with the indication of whether a publisher is involved
   *          in the requests to check.
   * @param expectedHtml
   *          An int with the expected number of HTML requests.
   * @param expectedPdf
   *          An int with the expected number of PDF requests.
   * @param expectedTotal
   *          An int with the expected number of total requests.
   * @throws SQLException
   */
  private void checkJournalMonthlyTypeRequests(Long publicationSeq,
      int requestYear, int requestMonth, Boolean isPublisherInvolved,
      int expectedHtml, int expectedPdf, int expectedTotal)
	  throws SQLException {
    Connection conn = null;
    PreparedStatement statement = null;
    ResultSet resultSet = null;
    int htmlCount = -1;
    int pdfCount = -1;
    int totalCount = -1;
    String sql = SQL_QUERY_TYPE_AGGREGATED_MONTH_JOURNAL_SELECT;

    try {
      conn = sqlDbManager.getConnection();

      statement = sqlDbManager.prepareStatement(conn, sql);
      statement.setLong(1, publicationSeq);
      statement.setInt(2, requestYear);
      statement.setInt(3, requestMonth);
      statement.setBoolean(4, isPublisherInvolved);
      resultSet = sqlDbManager.executeQuery(statement);

      if (resultSet.next()) {
	htmlCount = resultSet.getInt(HTML_REQUESTS_COLUMN);
	pdfCount = resultSet.getInt(PDF_REQUESTS_COLUMN);
	totalCount = resultSet.getInt(TOTAL_REQUESTS_COLUMN);
      }
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      SqlDbManager.safeCloseStatement(statement);
    }

    assertEquals(expectedHtml, htmlCount);
    assertEquals(expectedPdf, pdfCount);
    assertEquals(expectedTotal, totalCount);
  }

  /**
   * Checks the expected publication year count of monthly requests for a
   * journal.
   * 
   * @param publicationSeq
   *          An Long with the identifier of the journal.
   * @param requestYear
   *          An int with the year of the month to check.
   * @param requestMonth
   *          An int with the month to check.
   * @param isPublisherInvolved
   *          A Boolean with the indication of whether a publisher is involved
   *          in the requests to check.
   * @param expectedPublicationYear
   *          A String with the expected publication year.
   * @param expectedCount
   *          An int with the expected count of requests.
   * @throws SQLException
   */
  private void checkJournalMonthlyPublicationYearRequests(Long publicationSeq,
      int requestYear, int requestMonth, Boolean isPublisherInvolved,
      String expectedPublicationYear, int expectedCount) throws SQLException {
    Connection conn = null;
    PreparedStatement statement = null;
    ResultSet resultSet = null;
    String pubYear = null;
    int count = -1;
    String sql = SQL_QUERY_JOURNAL_PUBYEAR_AGGREGATED_MONTH_SELECT;

    try {
      conn = sqlDbManager.getConnection();

      statement = sqlDbManager.prepareStatement(conn, sql);
      statement.setLong(1, publicationSeq);
      statement.setInt(2, requestYear);
      statement.setInt(3, requestMonth);
      statement.setBoolean(4, isPublisherInvolved);
      resultSet = sqlDbManager.executeQuery(statement);

      if (resultSet.next()) {
	pubYear = resultSet.getString(PUBLICATION_YEAR_COLUMN);
	count = resultSet.getInt(REQUESTS_COLUMN);
      }
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      SqlDbManager.safeCloseStatement(statement);
    }

    assertEquals(expectedPublicationYear, pubYear);
    assertEquals(expectedCount, count);
  }

  /**
   * Tests the next scheduled execution time.
   * 
   * @throws Exception
   */
  public void runTestNextTime() throws Exception {
    CounterReportsRequestAggregator aggregator =
	new CounterReportsRequestAggregator(theDaemon);

    long now = TimeBase.nowMs();
    Calendar cal1 = Calendar.getInstance();
    cal1.setTimeInMillis(now);
    cal1.add(Calendar.HOUR, 1);

    long next = aggregator.getCronTask().nextTime(now);
    Calendar cal2 = Calendar.getInstance();
    cal2.setTimeInMillis(next);

    assertEquals(cal1.get(Calendar.YEAR), cal2.get(Calendar.YEAR));
    assertEquals(cal1.get(Calendar.MONTH), cal2.get(Calendar.MONTH));
    assertEquals(cal1.get(Calendar.DAY_OF_MONTH),
		 cal2.get(Calendar.DAY_OF_MONTH));
    assertEquals(cal1.get(Calendar.HOUR_OF_DAY),
                 cal2.get(Calendar.HOUR_OF_DAY));
    assertEquals(0, cal2.get(Calendar.MINUTE));
    assertEquals(0, cal2.get(Calendar.SECOND));
  }
}
