/*
 * $Id: CounterReportsBookReport2L.java,v 1.3 2013/01/09 04:05:12 fergaloy-sf Exp $
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
 * The COUNTER Book Report 2L.
 */
package org.lockss.exporter.counter;

import static org.lockss.db.DbManager.*;
import static org.lockss.exporter.counter.CounterReportsManager.*;
import static org.lockss.metadata.MetadataManager.*;

import org.lockss.app.LockssDaemon;

public class CounterReportsBookReport2L extends CounterReportsBookReport2 {
  // Query to get the books to be included in the report.
  // This the same query used for Book Report 2, except for the fact that in
  // this one the publisher involvement is not considered as a filtering
  // criteria.
  private static final String SQL_QUERY_REPORT_BOOKS_2L_SELECT = "select "
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
      + "a." + SECTION_REQUESTS_COLUMN + " > 0"
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
  // This the same query used for Book Report 2, except for the fact that in
  // this one the publisher involvement is not considered as a filtering
  // criteria.
  private static final String SQL_QUERY_REPORT_REQUESTS_2L_SELECT = "select "
      + "a." + PUBLICATION_SEQ_COLUMN
      + ", n." + NAME_COLUMN
      + ", a." + REQUEST_YEAR_COLUMN
      + ", a." + REQUEST_MONTH_COLUMN
      + ", sum(a." + SECTION_REQUESTS_COLUMN + ") as "
      + SECTION_REQUESTS_COLUMN
      + " from " + COUNTER_BOOK_TYPE_AGGREGATES_TABLE + " a"
      + "," + PUBLICATION_TABLE + " p"
      + "," + PUBLISHER_TABLE + " pu"
      + "," + MD_ITEM_TABLE + " m1"
      + " left outer join " + MD_ITEM_NAME_TABLE + " n"
      + " on m1." + MD_ITEM_SEQ_COLUMN + " = n." + MD_ITEM_SEQ_COLUMN
      + " where "
      + "a." + SECTION_REQUESTS_COLUMN + " > 0"
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
      + " group by n." + NAME_COLUMN
      + ", a." + PUBLICATION_SEQ_COLUMN
      + ", a." + REQUEST_YEAR_COLUMN
      + ", a." + REQUEST_MONTH_COLUMN
      + " order by n." + NAME_COLUMN
      + ", a." + REQUEST_YEAR_COLUMN
      + ", a." + REQUEST_MONTH_COLUMN + " asc";

  /**
   * Constructor for the default report period.
   * 
   * @param daemon
   *          A LockssDaemon with the LOCKSS daemon.
   */
  public CounterReportsBookReport2L(LockssDaemon daemon) {
    super(daemon);
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
  public CounterReportsBookReport2L(LockssDaemon daemon, int startMonth,
      int startYear, int endMonth, int endYear)
	  throws IllegalArgumentException {
    super(daemon, startMonth, startYear, endMonth, endYear);
  }

  /**
   * Provides the SQL query used to find all the books to be included in the
   * report.
   * 
   * @return a String with the SQL query used to find all the books to be
   *         included in the report.
   */
  protected String getReportBooksSqlQuery() {
    return SQL_QUERY_REPORT_BOOKS_2L_SELECT;
  }

  /**
   * Provides the SQL query used to find all the requests to be included in the
   * report.
   * 
   * @return a String with the SQL query used to find all the requests to be
   *         included in the report.
   */
  protected String getReportRequestsSqlQuery() {
    return SQL_QUERY_REPORT_REQUESTS_2L_SELECT;
  }

  /**
   * Provides the name of the report to be used in the report file name.
   * 
   * @return a String with the name of the report to be used in the report file
   *         name.
   */
  protected String getFileReportName() {
    return "COUNTER_Book_2L";
  }

  /**
   * Provides the header items in the report.
   * 
   * @return a String[] with the report header items.
   */
  @Override
  protected void populateReportHeaderEntries() {
    header.reportName = "Book Report 2L (R4)";
    header.reportDescription =
	"Number of Successful Section Requests by Month and Title";
    header.sectionTitle = "Section Type:";
    header.periodTitle = "Period covered by Report:";
    header.runDateTitle = "Date run:";
  }
}
