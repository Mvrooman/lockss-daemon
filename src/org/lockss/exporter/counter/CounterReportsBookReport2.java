/*
 * $Id: CounterReportsBookReport2.java,v 1.5 2013/01/14 21:58:18 fergaloy-sf Exp $
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
 * The COUNTER Book Report 2.
 */
package org.lockss.exporter.counter;

import static org.lockss.db.SqlDbManager.*;
import static org.lockss.exporter.counter.CounterReportsManager.*;
import static org.lockss.metadata.MetadataManager.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import org.lockss.app.LockssDaemon;
import org.lockss.db.SqlDbManager;
import org.lockss.util.Logger;
import org.lockss.util.StringUtil;

public class CounterReportsBookReport2 extends CounterReportsBookReport {
  private static final Logger log = Logger
      .getLogger(CounterReportsBookReport2.class);

  // Query to get the books to be included in the report.
  private static final String SQL_QUERY_REPORT_BOOKS_SELECT = "select "
      + "distinct a." + PUBLICATION_SEQ_COLUMN
      + ", n." + NAME_COLUMN
      + ", p." + PUBLICATION_ID_COLUMN
      + ", pu." + PUBLISHER_NAME_COLUMN
      + ", pl." + PLATFORM_COLUMN
      + ", d." + DOI_COLUMN
      + ", i1." + ISBN_COLUMN + " as " + P_ISBN_TYPE
      + ", i2." + ISBN_COLUMN + " as " + E_ISBN_TYPE
      + " from " + COUNTER_BOOK_TYPE_AGGREGATES_TABLE + " a"
      + "," + PUBLICATION_TABLE + " p"
      + "," + PUBLISHER_TABLE + " pu"
      + "," + MD_ITEM_TABLE + " m2"
      + "," + AU_MD_TABLE + " am"
      + "," + AU_TABLE + " au"
      + "," + PLUGIN_TABLE + " pl"
      + "," + MD_ITEM_TABLE + " m1"
      + " left outer join " + ISBN_TABLE + " i1"
      + " on m1." + MD_ITEM_SEQ_COLUMN + " = i1." + MD_ITEM_SEQ_COLUMN
      + " and i1." + ISBN_TYPE_COLUMN + " = '" + P_ISBN_TYPE + "'"
      + " left outer join " + ISBN_TABLE + " i2"
      + " on m1." + MD_ITEM_SEQ_COLUMN + " = i2." + MD_ITEM_SEQ_COLUMN
      + " and i2." + ISBN_TYPE_COLUMN + " = '" + E_ISBN_TYPE + "'"
      + " left outer join " + DOI_TABLE + " d"
      + " on m1." + MD_ITEM_SEQ_COLUMN + " = d." + MD_ITEM_SEQ_COLUMN
      + " left outer join " + MD_ITEM_NAME_TABLE + " n"
      + " on m1." + MD_ITEM_SEQ_COLUMN + " = n." + MD_ITEM_SEQ_COLUMN
      + " where "
      + "a." + IS_PUBLISHER_INVOLVED_COLUMN + " = false"
      + " and a." + SECTION_REQUESTS_COLUMN + " > 0"
      + " and ((a." + REQUEST_MONTH_COLUMN + " >= ?"
      + " and a." + REQUEST_YEAR_COLUMN + " = ?)"
      + " or a." + REQUEST_YEAR_COLUMN + " > ?)"
      + " and ((a." + REQUEST_MONTH_COLUMN + " <= ?"
      + " and a." + REQUEST_YEAR_COLUMN + " = ?)"
      + " or a." + REQUEST_YEAR_COLUMN + " < ?)"
      + " and a." + PUBLICATION_SEQ_COLUMN + " = p." + PUBLICATION_SEQ_COLUMN
      + " and p." + PUBLISHER_SEQ_COLUMN + " = pu." + PUBLISHER_SEQ_COLUMN
      + " and pu." + PUBLISHER_NAME_COLUMN + " != '" + ALL_PUBLISHERS_NAME + "'"
      + " and p." + MD_ITEM_SEQ_COLUMN + " = m1." + MD_ITEM_SEQ_COLUMN
      + " and n." + NAME_COLUMN + " != '" + ALL_BOOKS_NAME + "'"
      + " and m1." + MD_ITEM_SEQ_COLUMN + " = m2." + PARENT_SEQ_COLUMN
      + " and m2." + AU_MD_SEQ_COLUMN + " = am." + AU_MD_SEQ_COLUMN
      + " and am." + AU_SEQ_COLUMN + " = au." + AU_SEQ_COLUMN
      + " and au." + PLUGIN_SEQ_COLUMN + " = pl." + PLUGIN_SEQ_COLUMN
      + " order by n." + NAME_COLUMN + " asc";

  // Query to get the book request counts to be included in the report.
  private static final String SQL_QUERY_REPORT_REQUESTS_SELECT = "select "
      + "a." + PUBLICATION_SEQ_COLUMN
      + ", n." + NAME_COLUMN
      + ", a." + REQUEST_YEAR_COLUMN
      + ", a." + REQUEST_MONTH_COLUMN
      + ", a." + SECTION_REQUESTS_COLUMN
      + " from " + COUNTER_BOOK_TYPE_AGGREGATES_TABLE + " a"
      + "," + PUBLICATION_TABLE + " p"
      + "," + PUBLISHER_TABLE + " pu"
      + "," + MD_ITEM_TABLE + " m1"
      + " left outer join " + MD_ITEM_NAME_TABLE + " n"
      + " on m1." + MD_ITEM_SEQ_COLUMN + " = n." + MD_ITEM_SEQ_COLUMN
      + " where "
      + "a." + IS_PUBLISHER_INVOLVED_COLUMN + " = false"
      + " and a." + SECTION_REQUESTS_COLUMN + " > 0"
      + " and ((a." + REQUEST_MONTH_COLUMN + " >= ?"
      + " and a." + REQUEST_YEAR_COLUMN + " = ?)"
      + " or a." + REQUEST_YEAR_COLUMN + " > ?)"
      + " and ((a." + REQUEST_MONTH_COLUMN + " <= ?"
      + " and a." + REQUEST_YEAR_COLUMN + " = ?)"
      + " or a." + REQUEST_YEAR_COLUMN + " < ?)"
      + " and a." + PUBLICATION_SEQ_COLUMN + " = p." + PUBLICATION_SEQ_COLUMN
      + " and p." + PUBLISHER_SEQ_COLUMN + " = pu." + PUBLISHER_SEQ_COLUMN
      + " and pu." + PUBLISHER_NAME_COLUMN + " != '" + ALL_PUBLISHERS_NAME + "'"
      + " and p." + MD_ITEM_SEQ_COLUMN + " = m1." + MD_ITEM_SEQ_COLUMN
      + " and n." + NAME_COLUMN + " != '" + ALL_BOOKS_NAME + "'"
      + " order by n." + NAME_COLUMN
      + ", a." + REQUEST_YEAR_COLUMN
      + ", a." + REQUEST_MONTH_COLUMN + " asc";

  // The count of months included in the report.
  protected int monthCount = 0;

  /**
   * Constructor for the default report period.
   * 
   * @param daemon
   *          A LockssDaemon with the LOCKSS daemon.
   */
  public CounterReportsBookReport2(LockssDaemon daemon) {
    super(daemon);

    // Count the months included in the report.
    monthCount = getMonthIndex(endMonth, endYear);
  }

  /**
   * Constructor for a custom report period.
   * 
   * @param daemon
   *          A LockssDaemon with the LOCKSS daemon.
   * @param startMonth
   *          An int with the month of the beginning of the time period covered
   *          by the report.
   * @param startYear
   *          An int with the year of the beginning of the time period covered
   *          by the report.
   * @param endMonth
   *          An int with the year of the end of the time period covered by the
   *          report.
   * @param endYear
   *          An int with the year of the end of the time period covered by the
   *          report.
   * @throws IllegalArgumentException
   *           if the period specified is not valid.
   */
  public CounterReportsBookReport2(LockssDaemon daemon, int startMonth,
      int startYear, int endMonth, int endYear)
	  throws IllegalArgumentException {
    super(daemon, startMonth, startYear, endMonth, endYear);

    // Count the months included in the report.
    monthCount = getMonthIndex(endMonth, endYear);

    if (monthCount > CounterReportsRequestAggregator
	.MAX_NUMBER_OF_AGGREGATE_MONTHS) {
      throw new IllegalArgumentException("The report period cannot exceed "
	  + CounterReportsRequestAggregator.MAX_NUMBER_OF_AGGREGATE_MONTHS
	  + " months.");
    }
  }

  /**
   * Initializes the rows to be included in the report with the title data.
   * 
   * @param conn
   *          A Connection with a connection to the database.
   * @return a List<Row> with the initialized rows to be included in the report.
   * @throws SQLException
   */
  protected void initializeReportRows(Connection conn) throws SQLException {
    final String DEBUG_HEADER = "initializeReportRows(): ";
    log.debug2(DEBUG_HEADER + "Starting...");
    Long titleId = 0L;

    // The first row is a placeholder for the totals for all books.
    CounterReportsBook book =
	new CounterReportsBook(TOTAL_LABEL, null, null, null, null, null, null);
    List<Row> rows = new ArrayList<Row>();
    rows.add(new Row(titleId, book));

    PreparedStatement statement = null;
    ResultSet resultSet = null;
    String sql = getReportBooksSqlQuery();
    log.debug2(DEBUG_HEADER + "SQL = '" + sql + "'.");

    try {
      // Get the books to be included in the report.
      statement = daemon.getDbManager().prepareStatement(conn, sql);

      short index = 1;

      statement.setInt(index++, startMonth);
      statement.setInt(index++, startYear);
      statement.setInt(index++, startYear);
      statement.setInt(index++, endMonth);
      statement.setInt(index++, endYear);
      statement.setInt(index++, endYear);

      resultSet = daemon.getDbManager().executeQuery(statement);

      // Loop through all the books to be included in the report.
      while (resultSet.next()) {
	// Check whether this book is the same as the previous one.
	if (resultSet.getLong(PUBLICATION_SEQ_COLUMN) == titleId) {
	  // Yes: This means that the publication has multiple values for some
	  // attributes. Ignore the copy.
	  log.debug2(DEBUG_HEADER + "Skipping repeated titleId = " + titleId
	             + ".");
	  continue;
	}

	// Get the identifier for the book.
	titleId = resultSet.getLong(PUBLICATION_SEQ_COLUMN);
	log.debug2(DEBUG_HEADER + "titleId = " + titleId + ".");

	// Get the book properties.
	book =
	    new CounterReportsBook(resultSet.getString(NAME_COLUMN),
	                           resultSet.getString(PUBLISHER_NAME_COLUMN),
	                           resultSet.getString(PLATFORM_COLUMN),
	                           resultSet.getString(DOI_COLUMN),
	                           resultSet.getString(PUBLICATION_ID_COLUMN),
	                           formatIsbn(resultSet
	                                      .getString(P_ISBN_TYPE)),
	                           formatIsbn(resultSet
	                                      .getString(E_ISBN_TYPE)));
	log.debug2(DEBUG_HEADER + "Book = [" + book + "].");

	// Add the row to the results.
	rows.add(new Row(titleId, book));
      }
    } catch (SQLException sqle) {
      log.error("Cannot retrieve the books to be included in a report", sqle);
      log.error("StartMonth = " + startMonth + ", StartYear = " + startYear
	  + ", EndMonth = " + endMonth + ", EndYear = " + endYear);
      log.error("SQL = '" + sql + "'.");
      throw sqle;
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      SqlDbManager.safeCloseStatement(statement);
    }

    log.debug2(DEBUG_HEADER + "rows.size() = " + rows.size() + ".");
    setRows(rows);
    log.debug2(DEBUG_HEADER + "Done.");
  }

  /**
   * Provides the SQL query used to find all the books to be included in the
   * report.
   * 
   * @return a String with the SQL query used to find all the books to be
   *         included in the report.
   */
  protected String getReportBooksSqlQuery() {
    return SQL_QUERY_REPORT_BOOKS_SELECT;
  }

  /**
   * Adds the request counts to the rows to be included in the report.
   * 
   * @param conn
   *          A Connection with a connection to the database.
   * @throws SQLException
   * @throws CounterReportsException
   */
  protected void addReportRequestCounts(Connection conn) throws SQLException,
      CounterReportsException {
    final String DEBUG_HEADER = "addReportRequestCounts(): ";
    log.debug2(DEBUG_HEADER + "Starting...");

    // Verify that the placeholder row for the totals for all books exists.
    Iterator<Row> rowIterator = getRows().iterator();
    if (!rowIterator.hasNext()) {
      throw new CounterReportsException(BaseCounterReport
                                        .ERROR_ALL_BOOKS_MISSING);
    }

    // Get the row for the request totals for all books.
    Row allBooksRow = rowIterator.next();

    // Verify that it is the row for the request totals for all books.
    if (!TOTAL_LABEL.equals(allBooksRow.getTitle().getName())) {
      throw new CounterReportsException(BaseCounterReport
                                        .ERROR_ALL_BOOKS_NOT_FIRST);
    }

    // Initialize its request counts.
    List<ItemCounts> allBooksRowMonthRequestCounts =
	initializeRowRequestCounts(allBooksRow);

    // Do nothing more if there are no requests for this report.
    if (!rowIterator.hasNext()) {
      return;
    }

    String[] itemKeys = getItemColumnKeys();
    String[] totalKeys = getTotalColumnKeys();

    // The current row to be populated with request counts.
    Row currentRow = rowIterator.next();

    // Initialize its request counts.
    List<ItemCounts> currentRowMonthRequestCounts =
	initializeRowRequestCounts(currentRow);

    int month;
    int year;
    Long titleId;
    ItemCounts counts = null;
    PreparedStatement statement = null;
    ResultSet resultSet = null;
    String sql = getReportRequestsSqlQuery();
    log.debug2(DEBUG_HEADER + "SQL = '" + sql + "'.");

    try {
      // Get the request counts for all the rows to be included in the report.
      statement = daemon.getDbManager().prepareStatement(conn, sql);

      short index = 1;

      statement.setInt(index++, startMonth);
      statement.setInt(index++, startYear);
      statement.setInt(index++, startYear);
      statement.setInt(index++, endMonth);
      statement.setInt(index++, endYear);
      statement.setInt(index++, endYear);

      resultSet = daemon.getDbManager().executeQuery(statement);

      // Loop through all the request counts to be included in the report.
      while (resultSet.next()) {
	// Get the identifier for this set of request counts.
	titleId = resultSet.getLong(PUBLICATION_SEQ_COLUMN);
	log.debug2(DEBUG_HEADER + "titleId = " + titleId + ".");

	// Check whether this set of request counts is for a row different than
	// the current one.
	if (currentRow.getTitleId() != titleId) {
	  // Yes: This means that all the items for the current row have been
	  // processed. Verify that there are more rows in the report.
	  if (!rowIterator.hasNext()) {
	    throw new CounterReportsException(BaseCounterReport
	                                      .ERROR_UNEXPECTED_IDENTIFIER);
	  }

	  // Make the next row the current one.
	  currentRow = rowIterator.next();

	  // Check whether this row is not in sync with this item.
	  if (currentRow.getTitleId() != titleId) {
	    throw new CounterReportsException(BaseCounterReport
	                                      .ERROR_WRONG_SORTING);
	  }

	  // Initialize the period request counts of the current row.
	  currentRowMonthRequestCounts = initializeRowRequestCounts(currentRow);
	}

	// Get the month for this set of request counts.
	month = resultSet.getShort(REQUEST_MONTH_COLUMN);
	year = resultSet.getShort(REQUEST_YEAR_COLUMN);
	log.debug2(DEBUG_HEADER + "Month = " + month + ", Year = " + year);

	// Retrieve and save the request counts for this row during this month.
	counts = new ItemCounts();
	for (int i = 0; i < itemKeys.length; i++) {
	  counts.put(itemKeys[i], resultSet.getInt(itemKeys[i]));
	}
	for (int i = 0; i < totalKeys.length; i++) {
	  counts.put(totalKeys[i], resultSet.getInt(totalKeys[i]));
	}

	// Populate the request counts for the book during the appropriate
	// month.
	currentRowMonthRequestCounts.set(getMonthIndex(month, year), counts);

	// Update the request counts for the book during the report period.
	accumulateRequestCounts(totalKeys, counts,
	    currentRowMonthRequestCounts.get(0));

	// Update the request counts for all the books during the appropriate
	// month.
	accumulateRequestCounts(itemKeys, counts,
	    allBooksRowMonthRequestCounts.get(getMonthIndex(month, year)));

	// Update the request counts for all the books during the report period.
	accumulateRequestCounts(totalKeys, counts,
	    allBooksRowMonthRequestCounts.get(0));
      }
    } catch (SQLException sqle) {
      log.error("Cannot retrieve the book requests to be included in a report",
	  sqle);
      log.error("StartMonth = " + startMonth + ", StartYear = " + startYear
	  + ", EndMonth = " + endMonth + ", EndYear = " + endYear);
      log.error("SQL = '" + sql + "'.");
      throw sqle;
    } catch (CounterReportsException cre) {
      log.error("Error processing book requests", cre);
      log.error("StartMonth = " + startMonth + ", StartYear = " + startYear
	  + ", EndMonth = " + endMonth + ", EndYear = " + endYear);
      log.error("SQL = '" + sql + "'.");
      throw cre;
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      SqlDbManager.safeCloseStatement(statement);
    }

    log.debug2(DEBUG_HEADER + "Done.");
  }

  /**
   * Provides the count of items in a row, excluding any total item.
   * 
   * @return an int with the count of items in a row, excluding any total item.
   */
  protected int getNonTotalItemCount() {
    return monthCount;
  }

  /**
   * Provides an indication of whether the report includes a total column.
   * 
   * @return a boolean with the indication of whether the report includes a
   *         total column.
   */
  protected boolean hasTotalColumn() {
    return true;
  }

  /**
   * Provides the SQL query used to find all the requests to be included in the
   * report.
   * 
   * @return a String with the SQL query used to find all the requests to be
   *         included in the report.
   */
  protected String getReportRequestsSqlQuery() {
    return SQL_QUERY_REPORT_REQUESTS_SELECT;
  }

  /**
   * Provides the text line used as the table header.
   * 
   * @param separator
   *          A String with the separator to be used between items in report
   *          lines.
   * @return a String with the text line used as the table header.
   */
  protected String getTableHeaderTextLine(String separator) {
    // Place the title data headers.
    String[] tableHeader =
	new String[] { "Book", "Publisher", "Platform", "Book DOI",
	    "Proprietary Identifier", "ISBN", "ISSN",
    	    "Reporting Period Total" };

    StringBuilder sb =
	new StringBuilder(StringUtil.separatedString(tableHeader, separator));

    // Place the request month headers.
    Calendar calendar = Calendar.getInstance();
    int month = startMonth;
    int year = startYear;

    for (int i = 0; i < monthCount; i++) {
      calendar.set(year, month - 1, 1);

      sb.append(separator).append(monthFormat.format(calendar.getTime()));
      month++;

      if (month > 12) {
	month = 1;
	year++;
      }
    }

    return sb.toString();
  }

  /**
   * Provides the name of the report to be used in the report file name.
   * 
   * @return a String with the name of the report to be used in the report file
   *         name.
   */
  protected String getFileReportName() {
    return "COUNTER_Book_2";
  }

  /**
   * Provides the header items in the report.
   * 
   * @return a String[] with the report header items.
   */
  @Override
  protected void populateReportHeaderEntries() {
    header.reportName = "Book Report 2 (R4)";
    header.reportDescription =
	"Number of Successful Section Requests by Month and Title";
    header.sectionTitle = "Section Type:";
    header.periodTitle = "Period covered by Report:";
    header.runDateTitle = "Date run:";
  }

  /**
   * Provides the keys used to populate the total columns.
   * 
   * @return a String[] with the keys used to populate the total columns.
   */
  protected String[] getTotalColumnKeys() {
    return new String[] { SECTION_REQUESTS_COLUMN };
  }

  /**
   * Provides the keys used to populate the item columns.
   * 
   * @return a String[] with the keys used to populate the item columns.
   */
  protected String[] getItemColumnKeys() {
    return new String[] { SECTION_REQUESTS_COLUMN };
  }
}
