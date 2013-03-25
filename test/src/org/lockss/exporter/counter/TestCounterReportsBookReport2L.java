/*
 * $Id: TestCounterReportsBookReport2L.java,v 1.5 2013/01/09 04:05:12 fergaloy-sf Exp $
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
 * Test class for org.lockss.exporter.counter.CounterReportsBookReport2L.
 * 
 * @author Fernando Garcia-Loygorri
 * @version 1.0
 */
package org.lockss.exporter.counter;

import static org.lockss.db.SqlDbManager.*;
import static org.lockss.metadata.SqlMetadataManager.PRIMARY_NAME_TYPE;
import static org.lockss.plugin.ArticleFiles.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.Properties;
import org.lockss.config.ConfigManager;
import org.lockss.daemon.Cron;
import org.lockss.db.SqlDbManager;
import org.lockss.exporter.counter.CounterReportsBookReport2L;
import org.lockss.exporter.counter.CounterReportsManager;
import org.lockss.metadata.SqlMetadataManager;
import org.lockss.repository.LockssRepositoryImpl;
import org.lockss.test.ConfigurationUtil;
import org.lockss.test.LockssTestCase;
import org.lockss.test.MockLockssDaemon;
import org.lockss.util.IOUtil;
import org.lockss.util.TimeBase;

public class TestCounterReportsBookReport2L extends LockssTestCase {
  private static final String FULL_URL = "http://example.com/full.url";
  private static final String SECTION_URL = "http://example.com/section.url";

  private MockLockssDaemon theDaemon;
  private SqlDbManager sqlDbManager;
  private SqlMetadataManager sqlMetadataManager;
  private CounterReportsManager counterReportsManager;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    String tempDirPath = getTempDir().getAbsolutePath();

    // Set the database log.
    System.setProperty("derby.stream.error.file", new File(tempDirPath,
	"derby.log").getAbsolutePath());

    Properties props = new Properties();
    props.setProperty(LockssRepositoryImpl.PARAM_CACHE_LOCATION, tempDirPath);
    props.setProperty(ConfigManager.PARAM_PLATFORM_DISK_SPACE_LIST,
	      tempDirPath);
    props.setProperty(CounterReportsManager.PARAM_COUNTER_ENABLED, "true");
    props.setProperty(CounterReportsManager.PARAM_REPORT_BASEDIR_PATH,
	tempDirPath);
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
    runTestValidation();
    runTestEmptyReport();

    initializeFullBookMetadata();
    initializeSectionBookMetadata();

    counterReportsManager.persistRequest(FULL_URL, false);
    counterReportsManager.persistRequest(FULL_URL, true);
    counterReportsManager.persistRequest(SECTION_URL, false);
    counterReportsManager.persistRequest(SECTION_URL, true);

    CounterReportsRequestAggregator aggregator =
	new CounterReportsRequestAggregator(theDaemon);
    aggregator.getCronTask().execute();

    runTestDefaultPeriodReport();
    runTestCustomPeriodReport();
  }

  /**
   * Tests the validation of the constructor parameters.
   * 
   * @throws Exception
   */
  public void runTestValidation() throws Exception {

    try {
      new CounterReportsBookReport2L(theDaemon, 0, 2011, 7, 2012);
      fail("Invalid start month - Must be between 1 and 12");
    } catch (IllegalArgumentException iae) {
    }

    try {
      new CounterReportsBookReport2L(theDaemon, 13, 2011, 7, 2012);
      fail("Invalid start month - Must be between 1 and 12");
    } catch (IllegalArgumentException iae) {
    }

    try {
      new CounterReportsBookReport2L(theDaemon, 1, 2011, 0, 2012);
      fail("Invalid end month - Must be between 1 and 12");
    } catch (IllegalArgumentException iae) {
    }

    try {
      new CounterReportsBookReport2L(theDaemon, 1, 2011, 13, 2012);
      fail("Invalid end month - Must be between 1 and 12");
    } catch (IllegalArgumentException iae) {
    }

    try {
      new CounterReportsBookReport2L(theDaemon, 1, 2012, 12, 2011);
      fail("Invalid report period - End must not precede start");
    } catch (IllegalArgumentException iae) {
    }

    boolean validArgument = false;

    try {
      new CounterReportsBookReport2L(theDaemon, 1, 2012, 1, 2012);
      validArgument = true;
    } catch (IllegalArgumentException iae) {
    }

    assertEquals(true, validArgument);
  }

  /**
   * Tests an empty report.
   * 
   * @throws Exception
   */
  public void runTestEmptyReport() throws Exception {
    CounterReportsBookReport2L report =
	new CounterReportsBookReport2L(theDaemon);

    report.logReport();
    report.saveCsvReport();
    File reportFile =
	new File(counterReportsManager.getOutputDir(),
	    report.getReportFileName("csv"));
    assertEquals(true, reportFile.exists());

    report.populateReportHeaderEntries();

    BufferedReader reader = new BufferedReader(new FileReader(reportFile));
    String line = reader.readLine();

    for (int i = 0; i < 8; i++) {
      line = reader.readLine();
    }

    assertEquals(
	"\"Total for all books\",,,,,,,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0",
	line);
    assertNull(reader.readLine());

    IOUtil.safeClose(reader);
    reportFile.delete();
    assertEquals(false, reportFile.exists());

    report.saveTsvReport();
    reportFile =
	new File(counterReportsManager.getOutputDir(),
	    report.getReportFileName("txt"));
    assertEquals(true, reportFile.exists());

    reader = new BufferedReader(new FileReader(reportFile));
    line = reader.readLine();

    for (int i = 0; i < 8; i++) {
      line = reader.readLine();
    }

    assertEquals(
	"Total for all books\t\t\t\t\t\t\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0",
	line);
    assertNull(reader.readLine());

    IOUtil.safeClose(reader);
    reportFile.delete();
    assertEquals(false, reportFile.exists());
  }

  /**
   * Creates a full book for which to aggregate requests.
   * 
   * @return a Long with the identifier of the created book.
   * @throws SQLException
   */
  private Long initializeFullBookMetadata() throws SQLException {
    Long mdItemSeq = null;
    Connection conn = null;

    try {
      conn = sqlDbManager.getConnection();

      // Add the publisher.
      Long publisherSeq =
	  sqlMetadataManager.findOrCreatePublisher("publisher");

      // Add the publication.
      Long publicationSeq =
	  sqlMetadataManager.findOrCreatePublication(null, null,
						  "9876543210987",
						  "9876543210123", publisherSeq,
						  "The Full Book", "2010-01-01",
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

      sqlMetadataManager.addMdItemDoi(parentSeq, "10.1000/182");

      Long mdItemTypeSeq =
	  sqlMetadataManager.findMetadataItemType(MD_ITEM_TYPE_BOOK);

      mdItemSeq = sqlMetadataManager.addMdItem(parentSeq, mdItemTypeSeq,
                                            auMdSeq, "2010-01-01", null);

	  sqlMetadataManager.addMdItemName(mdItemSeq, "The Full Book",
					PRIMARY_NAME_TYPE);

      sqlMetadataManager.addMdItemUrl(mdItemSeq, ROLE_FULL_TEXT_HTML,
                                   FULL_URL);
    } finally {
      conn.commit();
      SqlDbManager.safeCloseConnection(conn);
    }
    
    return mdItemSeq;
  }

  /**
   * Creates a book section for which to aggregate requests.
   * 
   * @return a Long with the identifier of the created book.
   * @throws SQLException
   */
  private Long initializeSectionBookMetadata() throws SQLException {
    Long mdItemSeq = null;
    Connection conn = null;

    try {
      conn = sqlDbManager.getConnection();

      // Add the publisher.
      Long publisherSeq =
	  sqlMetadataManager.findOrCreatePublisher("publisher");

      // Add the publication.
      Long publicationSeq =
	  sqlMetadataManager.findOrCreatePublication(null, null,
						  "9876543210234",
						  "9876543210345", publisherSeq,
						  "The Book In Sections",
						  "2010-02-02", null, null);

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

      mdItemSeq = sqlMetadataManager.addMdItem(parentSeq, mdItemTypeSeq,
                                            auMdSeq, "2010-02-02", null);

	  sqlMetadataManager.addMdItemName(mdItemSeq, "Chapter Name",
					PRIMARY_NAME_TYPE);

      sqlMetadataManager.addMdItemUrl(mdItemSeq, ROLE_FULL_TEXT_PDF,
                                   SECTION_URL);
    } finally {
      conn.commit();
      SqlDbManager.safeCloseConnection(conn);
    }
    
    return mdItemSeq;
  }

  /**
   * Tests a report for the default period.
   * 
   * @throws Exception
   */
  public void runTestDefaultPeriodReport() throws Exception {
    CounterReportsBookReport2L report =
	new CounterReportsBookReport2L(theDaemon);

    report.logReport();
    report.saveCsvReport();
    File reportFile =
	new File(counterReportsManager.getOutputDir(),
	    report.getReportFileName("csv"));
    assertEquals(true, reportFile.exists());

    report.populateReportHeaderEntries();

    BufferedReader reader = new BufferedReader(new FileReader(reportFile));
    String line = reader.readLine();

    for (int i = 0; i < 8; i++) {
      line = reader.readLine();
    }

    assertEquals(
	"\"Total for all books\",,,,,,,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0",
	line);
    assertNull(reader.readLine());

    IOUtil.safeClose(reader);
    reportFile.delete();
    assertEquals(false, reportFile.exists());

    report.saveTsvReport();
    reportFile =
	new File(counterReportsManager.getOutputDir(),
	    report.getReportFileName("txt"));
    assertEquals(true, reportFile.exists());

    reader = new BufferedReader(new FileReader(reportFile));
    line = reader.readLine();

    for (int i = 0; i < 8; i++) {
      line = reader.readLine();
    }

    assertEquals(
	"Total for all books\t\t\t\t\t\t\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0",
	line);
    assertNull(reader.readLine());

    IOUtil.safeClose(reader);
    reportFile.delete();
    assertEquals(false, reportFile.exists());
  }

  /**
   * Tests a report for a custom period.
   * 
   * @throws Exception
   */
  public void runTestCustomPeriodReport() throws Exception {
    Calendar calendar = Calendar.getInstance();
    calendar.setTime(TimeBase.nowDate());
    int endYear = calendar.get(Calendar.YEAR);
    int endMonth = calendar.get(Calendar.MONTH) + 1;
    calendar.add(Calendar.MONTH, -4);
    int startYear = calendar.get(Calendar.YEAR);
    int startMonth = calendar.get(Calendar.MONTH) + 1;

    CounterReportsBookReport2L report =
	new CounterReportsBookReport2L(theDaemon, startMonth, startYear,
	    endMonth, endYear);

    report.logReport();
    report.saveCsvReport();
    File reportFile =
	new File(counterReportsManager.getOutputDir(),
	    report.getReportFileName("csv"));
    assertEquals(true, reportFile.exists());

    report.populateReportHeaderEntries();

    BufferedReader reader = new BufferedReader(new FileReader(reportFile));
    String line = reader.readLine();

    for (int i = 0; i < 8; i++) {
      line = reader.readLine();
    }

    assertEquals("\"Total for all books\",,,,,,,2,0,0,0,0,2", line);
    line = reader.readLine();
    assertEquals("\"The Book In Sections\",publisher,secPlatform,,,987-654321-0234,987-654321-0345,2,0,0,0,0,2",
	line);
    assertNull(reader.readLine());

    IOUtil.safeClose(reader);
    reportFile.delete();
    assertEquals(false, reportFile.exists());

    report.saveTsvReport();
    reportFile =
	new File(counterReportsManager.getOutputDir(),
	    report.getReportFileName("txt"));
    assertEquals(true, reportFile.exists());

    reader = new BufferedReader(new FileReader(reportFile));
    line = reader.readLine();

    for (int i = 0; i < 8; i++) {
      line = reader.readLine();
    }

    assertEquals("Total for all books\t\t\t\t\t\t\t2\t0\t0\t0\t0\t2", line);
    line = reader.readLine();
    assertEquals(
	"The Book In Sections\tpublisher\tsecPlatform\t\t\t987-654321-0234\t987-654321-0345\t2\t0\t0\t0\t0\t2",
	line);
    assertNull(reader.readLine());

    IOUtil.safeClose(reader);
    reportFile.delete();
    assertEquals(false, reportFile.exists());
  }
}
