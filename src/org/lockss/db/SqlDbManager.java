/*
 * $Id: SqlDbManager.java,v 1.13 2013/01/14 21:58:19 fergaloy-sf Exp $
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
 * Database manager.
 * 
 * @author Fernando Garcia-Loygorri
 */
package org.lockss.db;

import java.io.File;
import java.net.InetAddress;
import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransientException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.derby.drda.NetworkServerControl;
import org.apache.derby.jdbc.ClientDataSource;
import org.lockss.app.*;
import org.lockss.config.*;
import org.lockss.util.*;

public class SqlDbManager extends BaseLockssDaemonManager
  implements ConfigurableManager, DbManager {

  private static final Logger log = Logger.getLogger(SqlDbManager.class);

  // Prefix for the database manager configuration entries.
  private static final String PREFIX = Configuration.PREFIX + "sqlDbManager.";

  // Prefix for the datasource configuration entries.
  private static final String DATASOURCE_ROOT = PREFIX + "datasource";

  /**
   * Name of the database datasource class. Changes require daemon restart.
   */
  public static final String PARAM_DATASOURCE_CLASSNAME = DATASOURCE_ROOT
      + ".className";
  public static final String DEFAULT_DATASOURCE_CLASSNAME =
      "org.apache.derby.jdbc.EmbeddedDataSource";

  /**
   * Name of the database create. Changes require daemon restart.
   */
  public static final String PARAM_DATASOURCE_CREATEDATABASE = DATASOURCE_ROOT
      + ".createDatabase";
  public static final String DEFAULT_DATASOURCE_CREATEDATABASE = "create";

  /**
   * Name of the database with the relative path to the DB directory. Changes
   * require daemon restart.
   */
  public static final String PARAM_DATASOURCE_DATABASENAME = DATASOURCE_ROOT
      + ".databaseName";
  public static final String DEFAULT_DATASOURCE_DATABASENAME = "db/SqlDbManager";

  /**
   * Port number of the database. Changes require daemon restart.
   */
  public static final String PARAM_DATASOURCE_PORTNUMBER = DATASOURCE_ROOT
      + ".portNumber";
  public static final String DEFAULT_DATASOURCE_PORTNUMBER = "1527";

  /**
   * Name of the server. Changes require daemon restart.
   */
  public static final String PARAM_DATASOURCE_SERVERNAME = DATASOURCE_ROOT
      + ".serverName";
  public static final String DEFAULT_DATASOURCE_SERVERNAME = "localhost";

  /**
   * Name of the database user. Changes require daemon restart.
   */
  public static final String PARAM_DATASOURCE_USER = DATASOURCE_ROOT + ".user";
  public static final String DEFAULT_DATASOURCE_USER = "LOCKSS";

  /**
   * Maximum number of retries for transient SQL exceptions.
   */
  public static final String PARAM_MAX_RETRY_COUNT = PREFIX + "maxRetryCount";
  public static final int DEFAULT_MAX_RETRY_COUNT = 10;

  /**
   * Delay  between retries for transient SQL exceptions.
   */
  public static final String PARAM_RETRY_DELAY = PREFIX + "retryDelay";
  public static final long DEFAULT_RETRY_DELAY = 3 * Constants.SECOND;

  /**
   * The indicator to be inserted in the database at the end of truncated text
   * values.
   */
  public static final String TRUNCATION_INDICATOR = "\u0019";

  //
  // Database table names.
  //
  /** Name of the obsolete DOI table. */
  public static final String OBSOLETE_TITLE_TABLE = "TITLE";

  /** Name of the obsolete feature table. */
  public static final String OBSOLETE_FEATURE_TABLE = "FEATURE";

  /** Name of the obsolete pending AUs table. */
  public static final String OBSOLETE_PENDINGAUS_TABLE = "PENDINGAUS";

  /** Name of the obsolete metadata table. */
  public static final String OBSOLETE_METADATA_TABLE = "METADATA";

  /** Name of the plugin table. */
  public static final String PLUGIN_TABLE = "plugin";

  /** Name of the archival unit table. */
  public static final String AU_TABLE = "au";

  /** Name of the archival unit metadata table. */
  public static final String AU_MD_TABLE = "au_md";

  /** Name of the metadata item type table. */
  public static final String MD_ITEM_TYPE_TABLE = "md_item_type";

  /** Name of the metadata item table. */
  public static final String MD_ITEM_TABLE = "md_item";

  /** Name of the metadata item name table. */
  public static final String MD_ITEM_NAME_TABLE = "md_item_name";

  /** Name of the metadata key table. */
  public static final String MD_KEY_TABLE = "md_key";

  /** Name of the metadata table. */
  public static final String MD_TABLE = "md";

  /** Name of the bibliographic item table. */
  public static final String BIB_ITEM_TABLE = "bib_item";

  /** Name of the URL table. */
  public static final String URL_TABLE = "url";

  /** Name of the author table. */
  public static final String AUTHOR_TABLE = "author";

  /** Name of the keyword table. */
  public static final String KEYWORD_TABLE = "keyword";

  /** Name of the DOI table. */
  public static final String DOI_TABLE = "doi";

  /** Name of the ISSN table. */
  public static final String ISSN_TABLE = "issn";

  /** Name of the ISBN table. */
  public static final String ISBN_TABLE = "isbn";

  /** Name of the publisher table. */
  public static final String PUBLISHER_TABLE = "publisher";

  /** Name of the publication table. */
  public static final String PUBLICATION_TABLE = "publication";

  /** Name of the pending AUs table. */
  public static final String PENDING_AU_TABLE = "pending_au";

  /** Name of the version table. */
  public static final String VERSION_TABLE = "version";

  /** Name of the obsolete COUNTER publication year aggregate table. */
  public static final String OBSOLETE_PUBYEAR_AGGREGATES_TABLE =
      "counter_pubyear_aggregates";

  /** Name of the obsolete COUNTER title table. */
  public static final String OBSOLETE_TITLES_TABLE = "counter_titles";

  /** Name of the obsolete COUNTER type aggregates table. */
  public static final String OBSOLETE_TYPE_AGGREGATES_TABLE =
      "counter_type_aggregates";

  /** Name of the obsolete COUNTER request table. */
  public static final String OBSOLETE_REQUESTS_TABLE = "counter_requests";

  /** Name of the COUNTER request table. */
  public static final String COUNTER_REQUEST_TABLE = "counter_request";

  /** Name of the COUNTER books type aggregates table. */
  public static final String COUNTER_BOOK_TYPE_AGGREGATES_TABLE =
      "counter_book_type_aggregates";

  /** Name of the COUNTER books type aggregates table. */
  public static final String COUNTER_JOURNAL_TYPE_AGGREGATES_TABLE =
      "counter_journal_type_aggregates";

  /** Name of the obsolete COUNTER journal publication year aggregate table. */
  public static final String COUNTER_JOURNAL_PUBYEAR_AGGREGATE_TABLE =
      "counter_journal_pubyear_aggregate";

  //
  // Database table column names.
  //
  /** Metadata identifier column. */
  public static final String MD_ID_COLUMN = "md_id";

  /** Article title column. */
  public static final String ARTICLE_TITLE_COLUMN = "article_title";

  /** Author column. */
  public static final String AUTHOR_COLUMN = "author";

  /** Access URL column. */
  public static final String ACCESS_URL_COLUMN = "access_url";

  /** Title column. */
  public static final String TITLE_COLUMN = "title";

  /** LOCKSS identifier column. */
  public static final String LOCKSS_ID_COLUMN = "lockss_id";
  public static final String TITLE_NAME_COLUMN = "title_name";

  /** Publisher name column. */
  public static final String PUBLISHER_NAME_COLUMN = "publisher_name";
  public static final String PLATFORM_NAME_COLUMN = "platform_name";

  /** DOI column. */
  public static final String DOI_COLUMN = "doi";
  public static final String PROPRIETARY_ID_COLUMN = "proprietary_id";
  public static final String PRINT_ISSN_COLUMN = "print_issn";
  public static final String ONLINE_ISSN_COLUMN = "online_issn";

  /** ISBN column. */
  public static final String ISBN_COLUMN = "isbn";
  public static final String BOOK_ISSN_COLUMN = "book_issn";

  /** Publication year column. */
  public static final String PUBLICATION_YEAR_COLUMN = "publication_year";
  public static final String IS_BOOK_COLUMN = "is_book";
  public static final String IS_SECTION_COLUMN = "is_section";
  public static final String IS_HTML_COLUMN = "is_html";
  public static final String IS_PDF_COLUMN = "is_pdf";

  /** Publisher involvement indicator column. */
  public static final String IS_PUBLISHER_INVOLVED_COLUMN =
      "is_publisher_involved";

  /** In-aggregation indicator column. */
  public static final String IN_AGGREGATION_COLUMN = "in_aggregation";

  /** Request day column. */
  public static final String REQUEST_DAY_COLUMN = "request_day";

  /** Request month column. */
  public static final String REQUEST_MONTH_COLUMN = "request_month";

  /** Request year column. */
  public static final String REQUEST_YEAR_COLUMN = "request_year";
  public static final String TOTAL_JOURNAL_REQUESTS_COLUMN =
      "total_journal_requests";
  public static final String HTML_JOURNAL_REQUESTS_COLUMN =
      "html_journal_requests";
  public static final String PDF_JOURNAL_REQUESTS_COLUMN =
      "pdf_journal_requests";
  public static final String FULL_BOOK_REQUESTS_COLUMN =
      "full_book_requests";
  public static final String SECTION_BOOK_REQUESTS_COLUMN =
      "section_book_requests";
  public static final String REQUEST_COUNT_COLUMN = "request_count";

  /** Title sequential identifier column. */
  public static final String PLUGIN_SEQ_COLUMN = "plugin_seq";

  /** Name of plugin_id column. */
  public static final String PLUGIN_ID_COLUMN = "plugin_id";

  /** Name of the plugin platform column. */
  public static final String PLATFORM_COLUMN = "platform";

  /** Archival unit sequential identifier column. */
  public static final String AU_SEQ_COLUMN = "au_seq";

  /** Name of au_key column. */
  public static final String AU_KEY_COLUMN = "au_key";

  /** Archival unit metadata sequential identifier column. */
  public static final String AU_MD_SEQ_COLUMN = "au_md_seq";

  /** Name of the metadata version column. */
  public static final String MD_VERSION_COLUMN = "md_version";

  /** Metadata extraction time column. */
  public static final String EXTRACT_TIME_COLUMN = "extract_time";

  /** Metadata item type identifier column. */
  public static final String MD_ITEM_TYPE_SEQ_COLUMN = "md_item_type_seq";

  /** Type name column. */
  public static final String TYPE_NAME_COLUMN = "type_name";

  /** Metadata item identifier column. */
  public static final String MD_ITEM_SEQ_COLUMN = "md_item_seq";

  /** Parent identifier column. */
  public static final String PARENT_SEQ_COLUMN = "parent_seq";

  /** Date column. */
  public static final String DATE_COLUMN = "date";

  /** Name of the coverage column. */
  public static final String COVERAGE_COLUMN = "coverage";

  /** Name column. */
  public static final String NAME_COLUMN = "name";

  /** Name type column. */
  public static final String NAME_TYPE_COLUMN = "name_type";

  /** Metadata key identifier column. */
  public static final String MD_KEY_SEQ_COLUMN = "md_key_seq";

  /** Key name column. */
  public static final String KEY_NAME_COLUMN = "key_name";

  /** Metadata value column. */
  public static final String MD_VALUE_COLUMN = "md_value";

  /** Volume column. */
  public static final String VOLUME_COLUMN = "volume";

  /** Issue column. */
  public static final String ISSUE_COLUMN = "issue";

  /** Start page column. */
  public static final String START_PAGE_COLUMN = "start_page";

  /** End page column. */
  public static final String END_PAGE_COLUMN = "end_page";
  
  /** Item number column. */
  public static final String ITEM_NO_COLUMN = "item_no";

  /** Feature column (e.g. "fulltext", "abstract", "toc") */
  public static final String FEATURE_COLUMN = "feature";

  /** URL column. */
  public static final String URL_COLUMN = "url";

  /** Author column. */
  public static final String AUTHOR_NAME_COLUMN = "author_name";

  /** Author index column. */
  public static final String AUTHOR_IDX_COLUMN = "author_idx";

  /** Keyword column. */
  public static final String KEYWORD_COLUMN = "keyword";

  /** ISSN column. */
  public static final String ISSN_COLUMN = "issn";

  /** ISSN type column. */
  public static final String ISSN_TYPE_COLUMN = "issn_type";

  /** ISBN type column. */
  public static final String ISBN_TYPE_COLUMN = "isbn_type";

  /** Publisher identifier column. */
  public static final String PUBLISHER_SEQ_COLUMN = "publisher_seq";

  /** Publication identifier column. */
  public static final String PUBLICATION_SEQ_COLUMN = "publication_seq";

  /** Publication publisher identifier column. */
  public static final String PUBLICATION_ID_COLUMN = "publication_id";

  /** Priority column. */
  public static final String PRIORITY_COLUMN = "priority";

  /** System column. */
  public static final String SYSTEM_COLUMN = "system";

  /** Version column. */
  public static final String VERSION_COLUMN = "version";

  /** Total requests column. */
  public static final String TOTAL_REQUESTS_COLUMN = "total_requests";

  /** HTML requests column. */
  public static final String HTML_REQUESTS_COLUMN = "html_requests";

  /** PDF requests column. */
  public static final String PDF_REQUESTS_COLUMN = "pdf_requests";

  /** Full requests column. */
  public static final String FULL_REQUESTS_COLUMN = "full_requests";

  /** Section requests column. */
  public static final String SECTION_REQUESTS_COLUMN = "section_requests";

  /** Requests column. */
  public static final String REQUESTS_COLUMN = "requests";

  //
  // Maximum lengths of variable text length database columns.
  //
  /** Length of the article title column. */
  public static final int MAX_ARTICLE_TITLE_COLUMN = 512;

  /** Length of the author column. */
  public static final int OBSOLETE_MAX_AUTHOR_COLUMN = 512;

  /**
   * Length of the plugin ID column. This column will be used as a horizontal
   * partitioning column in the future, so its length must be compatible for
   * that purpose for the database product used.
   */
  public static final int OBSOLETE_MAX_PLUGIN_ID_COLUMN = 128;

  /** Length of the title column. */
  public static final int MAX_TITLE_COLUMN = 512;

  /**
   * Length of the plugin ID column. This column will be used as a horizontal
   * partitioning column in the future, so its length must be compatible for
   * that purpose for the database product used.
   */
  public static final int MAX_PLUGIN_ID_COLUMN = 256;

  /** Length of the publishing platform column. */
  public static final int MAX_PLATFORM_COLUMN = 64;

  /** Length of the AU key column. */
  public static final int MAX_AU_KEY_COLUMN = 512;

  /** Length of the coverage column. */
  public static final int MAX_COVERAGE_COLUMN = 16;

  /** Length of the type name column. */
  public static final int MAX_TYPE_NAME_COLUMN = 32;

  /** Length of the date column. */
  public static final int MAX_DATE_COLUMN = 16;

  /** Length of the name column. */
  public static final int MAX_NAME_COLUMN = 512;

  /** Length of the name type column. */
  public static final int MAX_NAME_TYPE_COLUMN = 16;

  /** Length of the key name column. */
  public static final int MAX_KEY_NAME_COLUMN = 128;

  /** Length of the metadata value column. */
  public static final int MAX_MD_VALUE_COLUMN = 128;

  /** Length of the volume column. */
  public static final int MAX_VOLUME_COLUMN = 16;

  /** Length of the issue column. */
  public static final int MAX_ISSUE_COLUMN = 16;

  /** Length of the start page column. */
  public static final int MAX_START_PAGE_COLUMN = 16;

  /** Length of the end page column. */
  public static final int MAX_END_PAGE_COLUMN = 16;

  /** Length of the item number column. */
  public static final int MAX_ITEM_NO_COLUMN = 16;

  /** Length of feature column. */
  public static final int MAX_FEATURE_COLUMN = 32;

  /** Length of the URL column. */
  public static final int MAX_URL_COLUMN = 4096;

  /** Length of the author column. */
  public static final int MAX_AUTHOR_COLUMN = 128;

  /** Length of the keyword column. */
  public static final int MAX_KEYWORD_COLUMN = 64;

  /** Length of the DOI column. */
  public static final int MAX_DOI_COLUMN = 256;

  /** Length of the ISSN column. */
  public static final int MAX_ISSN_COLUMN = 8;

  /** Length of the ISSN type column. */
  public static final int MAX_ISSN_TYPE_COLUMN = 16;

  /** Length of the ISBN column. */
  public static final int MAX_ISBN_COLUMN = 13;

  /** Length of the ISBN type column. */
  public static final int MAX_ISBN_TYPE_COLUMN = 16;

  /** Length of the publication proprietary identifier column. */
  public static final int MAX_PUBLICATION_ID_COLUMN = 32;

  /** Length of the system column. */
  public static final int MAX_SYSTEM_COLUMN = 16;

  //
  //Types of metadata items.
  //
  public static final String MD_ITEM_TYPE_BOOK = "book";
  public static final String MD_ITEM_TYPE_BOOK_CHAPTER = "book_chapter";
  public static final String MD_ITEM_TYPE_BOOK_SERIES = "book_series";
  public static final String MD_ITEM_TYPE_JOURNAL = "journal";
  public static final String MD_ITEM_TYPE_JOURNAL_ARTICLE = "journal_article";

  // Query to create the table for recording bibliobraphic metadata for an
  // article.
  private static final String OBSOLETE_CREATE_METADATA_TABLE_QUERY = "create "
      + "table " + OBSOLETE_METADATA_TABLE + " ("
      + MD_ID_COLUMN + " bigint PRIMARY KEY GENERATED ALWAYS AS IDENTITY,"
      + DATE_COLUMN + " varchar(" + MAX_DATE_COLUMN + "),"
      + VOLUME_COLUMN + " varchar(" + MAX_VOLUME_COLUMN + "),"
      + ISSUE_COLUMN + " varchar(" + MAX_ISSUE_COLUMN + "),"
      + START_PAGE_COLUMN + " varchar(" + MAX_START_PAGE_COLUMN + "),"
      + ARTICLE_TITLE_COLUMN + " varchar(" + MAX_ARTICLE_TITLE_COLUMN + "),"
      // author column is a semicolon-separated list
      + AUTHOR_COLUMN + " varchar(" + OBSOLETE_MAX_AUTHOR_COLUMN + "),"
      + PLUGIN_ID_COLUMN + " varchar(" + OBSOLETE_MAX_PLUGIN_ID_COLUMN
      + ") NOT NULL,"
      // partition by
      + AU_KEY_COLUMN + " varchar(" + MAX_AU_KEY_COLUMN + ") NOT NULL,"
      + ACCESS_URL_COLUMN + " varchar(" + MAX_URL_COLUMN + ") NOT NULL)";

  // Query to create the table for recording title journal/book title of an
  // article.
  private static final String OBSOLETE_CREATE_TITLE_TABLE_QUERY = "create "
      + "table " + OBSOLETE_TITLE_TABLE + " ("
      + TITLE_COLUMN + " varchar(" + MAX_TITLE_COLUMN + ") NOT NULL,"
      + MD_ID_COLUMN + " bigint NOT NULL references " + OBSOLETE_METADATA_TABLE
      + " (" + MD_ID_COLUMN + ") on delete cascade)";

  // Query to create the table for recording pending AUs to index.
  private static final String OBSOLETE_CREATE_PENDINGAUS_TABLE_QUERY = "create "
      + "table " + OBSOLETE_PENDINGAUS_TABLE + " ("
      + PLUGIN_ID_COLUMN + " varchar(" + OBSOLETE_MAX_PLUGIN_ID_COLUMN
      + ") NOT NULL,"
      + AU_KEY_COLUMN + " varchar(" + MAX_AU_KEY_COLUMN + ") NOT NULL)";

  // Query to create the table for recording a feature URL for an article.
  private static final String OBSOLETE_CREATE_FEATURE_TABLE_QUERY = "create "
      + "table " + OBSOLETE_FEATURE_TABLE + " ("
      + FEATURE_COLUMN + " VARCHAR(" + MAX_FEATURE_COLUMN + ") NOT NULL,"
      + ACCESS_URL_COLUMN + " VARCHAR(" + MAX_URL_COLUMN + ") NOT NULL," 
      + MD_ID_COLUMN + " BIGINT NOT NULL REFERENCES " + OBSOLETE_METADATA_TABLE
      + " (" + MD_ID_COLUMN + ") on delete cascade)";
  
  // Query to create the table for recording a DOI for an article.
  private static final String OBSOLETE_CREATE_DOI_TABLE_QUERY = "create table "
      + DOI_TABLE + " (" 
      + DOI_COLUMN + " VARCHAR(" + MAX_DOI_COLUMN + ") NOT NULL,"
      + MD_ID_COLUMN + " BIGINT NOT NULL REFERENCES " + OBSOLETE_METADATA_TABLE
      + " (" + MD_ID_COLUMN + ") on delete cascade)";
      
  // Query to create the table for recording an ISBN for an article.
  private static final String OBSOLETE_CREATE_ISBN_TABLE_QUERY = "create table "
      + ISBN_TABLE + " (" 
      + ISBN_COLUMN + " VARCHAR(" + MAX_ISBN_COLUMN + ") NOT NULL,"
      + MD_ID_COLUMN + " BIGINT NOT NULL REFERENCES " + OBSOLETE_METADATA_TABLE
      + " (" + MD_ID_COLUMN + ") on delete cascade)";

  // Query to create the table for recording an ISSN for an article.
  private static final String OBSOLETE_CREATE_ISSN_TABLE_QUERY = "create table "
      + ISSN_TABLE + " (" 
      + ISSN_COLUMN + " VARCHAR(" + MAX_ISSN_COLUMN + ") NOT NULL,"
      + MD_ID_COLUMN + " BIGINT NOT NULL REFERENCES " + OBSOLETE_METADATA_TABLE
      + " (" + MD_ID_COLUMN + ") on delete cascade)";

  // Query to create the table for recording title data used for COUNTER
  // reports.
  private static final String OBSOLETE_CREATE_TITLES_TABLE_QUERY = "create "
      + "table " + OBSOLETE_TITLES_TABLE + " ("
      + LOCKSS_ID_COLUMN + " bigint NOT NULL PRIMARY KEY,"
      + TITLE_NAME_COLUMN + " varchar(512) NOT NULL,"
      + PUBLISHER_NAME_COLUMN + " varchar(512),"
      + PLATFORM_NAME_COLUMN + " varchar(512),"
      + DOI_COLUMN + " varchar(256),"
      + PROPRIETARY_ID_COLUMN + " varchar(256),"
      + IS_BOOK_COLUMN + " boolean NOT NULL,"
      + PRINT_ISSN_COLUMN + " varchar(9),"
      + ONLINE_ISSN_COLUMN + " varchar(9),"
      + ISBN_COLUMN + " varchar(15),"
      + BOOK_ISSN_COLUMN + " varchar(9))";

  // Query to create the table for recording requests used for COUNTER reports.
  private static final String OBSOLETE_CREATE_REQUESTS_TABLE_QUERY = "create "
      + "table " + OBSOLETE_REQUESTS_TABLE + " ("
      + LOCKSS_ID_COLUMN
      + " bigint NOT NULL CONSTRAINT FK_LOCKSS_ID_REQUESTS REFERENCES "
      + OBSOLETE_TITLES_TABLE + ","
      + PUBLICATION_YEAR_COLUMN + " smallint,"
      + IS_SECTION_COLUMN + " boolean,"
      + IS_HTML_COLUMN + " boolean,"
      + IS_PDF_COLUMN + " boolean,"
      + IS_PUBLISHER_INVOLVED_COLUMN + " boolean NOT NULL,"
      + REQUEST_YEAR_COLUMN + " smallint NOT NULL,"
      + REQUEST_MONTH_COLUMN + " smallint NOT NULL,"
      + REQUEST_DAY_COLUMN + " smallint NOT NULL,"
      + IN_AGGREGATION_COLUMN + " boolean)";

  // Query to create the table for recording type aggregates (PDF vs. HTML, Full
  // vs. Section, etc.) used for COUNTER reports.
  private static final String OBSOLETE_CREATE_TYPE_AGGREGATES_TABLE_QUERY
  = "create table " + OBSOLETE_TYPE_AGGREGATES_TABLE + " ("
      + LOCKSS_ID_COLUMN
      + " bigint NOT NULL CONSTRAINT FK_LOCKSS_ID_TYPE_AGGREGATES REFERENCES "
      + OBSOLETE_TITLES_TABLE + ","
      + IS_PUBLISHER_INVOLVED_COLUMN + " boolean NOT NULL,"
      + REQUEST_YEAR_COLUMN + " smallint NOT NULL,"
      + REQUEST_MONTH_COLUMN + " smallint NOT NULL,"
      + TOTAL_JOURNAL_REQUESTS_COLUMN + " integer,"
      + HTML_JOURNAL_REQUESTS_COLUMN + " integer,"
      + PDF_JOURNAL_REQUESTS_COLUMN + " integer,"
      + FULL_BOOK_REQUESTS_COLUMN + " integer,"
      + SECTION_BOOK_REQUESTS_COLUMN + " integer)";

  // Query to create the table for recording publication year aggregates used
  // for COUNTER reports.
  private static final String OBSOLETE_CREATE_PUBYEAR_AGGREGATES_TABLE_QUERY
  = "create table " + OBSOLETE_PUBYEAR_AGGREGATES_TABLE + " ("
      + LOCKSS_ID_COLUMN
      + " bigint NOT NULL CONSTRAINT FK_LOCKSS_ID_PUBYEAR_AGGREGATES REFERENCES "
      + OBSOLETE_TITLES_TABLE + ","
      + IS_PUBLISHER_INVOLVED_COLUMN + " boolean NOT NULL,"
      + REQUEST_YEAR_COLUMN + " smallint NOT NULL,"
      + REQUEST_MONTH_COLUMN + " smallint NOT NULL,"
      + PUBLICATION_YEAR_COLUMN + " smallint NOT NULL,"
      + REQUEST_COUNT_COLUMN + " integer NOT NULL)";

  // Query to create the table for recording plugins.
  private static final String CREATE_PLUGIN_TABLE_QUERY = "create table "
      + PLUGIN_TABLE + " ("
      + PLUGIN_SEQ_COLUMN + " bigint primary key generated always as identity,"
      + PLUGIN_ID_COLUMN + " varchar(" + MAX_PLUGIN_ID_COLUMN + ") not null,"
      + PLATFORM_COLUMN + " varchar(" + MAX_PLATFORM_COLUMN + ")"
      + ")";

  // Query to create the table for recording archival units.
  private static final String CREATE_AU_TABLE_QUERY = "create table "
      + AU_TABLE + " ("
      + AU_SEQ_COLUMN + " bigint primary key generated always as identity,"
      + PLUGIN_SEQ_COLUMN + " bigint not null references " + PLUGIN_TABLE
      + " (" + PLUGIN_SEQ_COLUMN + ") on delete cascade,"
      + AU_KEY_COLUMN + " varchar(" + MAX_AU_KEY_COLUMN + ") not null)";

  // Query to create the table for recording archival units metadata.
  private static final String CREATE_AU_MD_TABLE_QUERY = "create table "
      + AU_MD_TABLE + " ("
      + AU_MD_SEQ_COLUMN + " bigint primary key generated always as identity,"
      + AU_SEQ_COLUMN + " bigint not null references " + AU_TABLE
      + " (" + AU_SEQ_COLUMN + ") on delete cascade,"
      + MD_VERSION_COLUMN + " smallint not null,"
      + EXTRACT_TIME_COLUMN + " bigint not null"
      + ")";

  // Query to create the table for recording metadata item types.
  private static final String CREATE_MD_ITEM_TYPE_TABLE_QUERY = "create table "
      + MD_ITEM_TYPE_TABLE + " ("
      + MD_ITEM_TYPE_SEQ_COLUMN
      + " bigint primary key generated always as identity,"
      + TYPE_NAME_COLUMN + " varchar(" + MAX_TYPE_NAME_COLUMN + ") not null"
      + ")";

  // Query to create the table for recording metadata items.
  private static final String CREATE_MD_ITEM_TABLE_QUERY = "create table "
      + MD_ITEM_TABLE + " ("
      + MD_ITEM_SEQ_COLUMN + " bigint primary key generated always as identity,"
      + PARENT_SEQ_COLUMN + " bigint references " + MD_ITEM_TABLE
      + " (" + MD_ITEM_SEQ_COLUMN + ") on delete cascade,"
      + MD_ITEM_TYPE_SEQ_COLUMN + " bigint not null references "
      + MD_ITEM_TYPE_TABLE + " (" + MD_ITEM_TYPE_SEQ_COLUMN + ")"
      + " on delete cascade,"
      + AU_MD_SEQ_COLUMN + " bigint references " + AU_MD_TABLE
      + " (" + AU_MD_SEQ_COLUMN + ") on delete cascade,"
      + DATE_COLUMN + " varchar(" + MAX_DATE_COLUMN + "),"
      + COVERAGE_COLUMN + " varchar(" + MAX_COVERAGE_COLUMN + ")"
      + ")";

  // Query to create the table for recording metadata items names.
  private static final String CREATE_MD_ITEM_NAME_TABLE_QUERY = "create table "
      + MD_ITEM_NAME_TABLE + " ("
      + MD_ITEM_SEQ_COLUMN + " bigint not null references " + MD_ITEM_TABLE
      + " (" + MD_ITEM_SEQ_COLUMN + ") on delete cascade,"
      + NAME_COLUMN + " varchar(" + MAX_NAME_COLUMN + ") not null,"
      + NAME_TYPE_COLUMN + " varchar(" + MAX_NAME_TYPE_COLUMN  + ") not null"
      + ")";

  // Query to create the table for recording metadata keys.
  private static final String CREATE_MD_KEY_TABLE_QUERY = "create table "
      + MD_KEY_TABLE + " ("
      + MD_KEY_SEQ_COLUMN + " bigint primary key generated always as identity,"
      + KEY_NAME_COLUMN + " varchar(" + MAX_NAME_COLUMN + ") not null"
      + ")";

  // Query to create the table for recording metadata items generic key/value
  // pairs.
  private static final String CREATE_MD_TABLE_QUERY = "create table "
      + MD_TABLE + " ("
      + MD_ITEM_SEQ_COLUMN + " bigint not null references " + MD_ITEM_TABLE
      + " (" + MD_ITEM_SEQ_COLUMN + ") on delete cascade,"
      + MD_KEY_SEQ_COLUMN + " bigint not null references " + MD_KEY_TABLE
      + " (" + MD_KEY_SEQ_COLUMN + ") on delete cascade,"
      + MD_VALUE_COLUMN + " varchar(" + MAX_MD_VALUE_COLUMN + ") not null"
      + ")";

  // Query to create the table for recording bibliographic items.
  private static final String CREATE_BIB_ITEM_TABLE_QUERY = "create table "
      + BIB_ITEM_TABLE + " ("
      + MD_ITEM_SEQ_COLUMN + " bigint not null references " + MD_ITEM_TABLE
      + " (" + MD_ITEM_SEQ_COLUMN + ") on delete cascade,"
      + VOLUME_COLUMN + " varchar(" + MAX_VOLUME_COLUMN + "),"
      + ISSUE_COLUMN + " varchar(" + MAX_ISSUE_COLUMN + "),"
      + START_PAGE_COLUMN + " varchar(" + MAX_START_PAGE_COLUMN + "),"
      + END_PAGE_COLUMN + " varchar(" + MAX_END_PAGE_COLUMN + "),"
      + ITEM_NO_COLUMN + " varchar(" + MAX_ITEM_NO_COLUMN + ")"
      + ")";

  // Query to create the table for recording metadata items URLs.
  private static final String CREATE_URL_TABLE_QUERY = "create table "
      + URL_TABLE + " ("
      + MD_ITEM_SEQ_COLUMN + " bigint not null references " + MD_ITEM_TABLE
      + " (" + MD_ITEM_SEQ_COLUMN + ") on delete cascade,"
      + FEATURE_COLUMN + " varchar(" + MAX_FEATURE_COLUMN + ") not null,"
      + URL_COLUMN + " varchar(" + MAX_URL_COLUMN + ") not null"
      + ")";

  // Query to create the table for recording metadata items authors.
  private static final String CREATE_AUTHOR_TABLE_QUERY = "create table "
      + AUTHOR_TABLE + " ("
      + MD_ITEM_SEQ_COLUMN + " bigint not null references " + MD_ITEM_TABLE
      + " (" + MD_ITEM_SEQ_COLUMN + ") on delete cascade,"
      + AUTHOR_NAME_COLUMN + " varchar(" + MAX_AUTHOR_COLUMN + ") not null,"
      + AUTHOR_IDX_COLUMN + " smallint not null"
      + ")";

  // Query to create the table for recording metadata items keywords.
  private static final String CREATE_KEYWORD_TABLE_QUERY = "create table "
      + KEYWORD_TABLE + " ("
      + MD_ITEM_SEQ_COLUMN + " bigint not null references " + MD_ITEM_TABLE
      + " (" + MD_ITEM_SEQ_COLUMN + ") on delete cascade,"
      + KEYWORD_COLUMN + " varchar(" + MAX_KEYWORD_COLUMN + ") not null"
      + ")";

  // Query to create the table for recording metadata items DOIs.
  private static final String CREATE_DOI_TABLE_QUERY = "create table "
      + DOI_TABLE + " ("
      + MD_ITEM_SEQ_COLUMN + " bigint not null references " + MD_ITEM_TABLE
      + " (" + MD_ITEM_SEQ_COLUMN + ") on delete cascade,"
      + DOI_COLUMN + " varchar(" + MAX_DOI_COLUMN + ") not null"
      + ")";

  // Query to create the table for recording metadata items ISSNs.
  private static final String CREATE_ISSN_TABLE_QUERY = "create table "
      + ISSN_TABLE + " ("
      + MD_ITEM_SEQ_COLUMN + " bigint not null references " + MD_ITEM_TABLE
      + " (" + MD_ITEM_SEQ_COLUMN + ") on delete cascade,"
      + ISSN_COLUMN + " varchar(" + MAX_ISSN_COLUMN + ") not null,"
      + ISSN_TYPE_COLUMN + " varchar(" + MAX_ISSN_TYPE_COLUMN + ") not null"
      + ")";

  // Query to create the table for recording metadata items ISBNs.
  private static final String CREATE_ISBN_TABLE_QUERY = "create table "
      + ISBN_TABLE + " ("
      + MD_ITEM_SEQ_COLUMN + " bigint not null references " + MD_ITEM_TABLE
      + " (" + MD_ITEM_SEQ_COLUMN + ") on delete cascade,"
      + ISBN_COLUMN + " varchar(" + MAX_ISBN_COLUMN + ") not null,"
      + ISBN_TYPE_COLUMN + " varchar(" + MAX_ISBN_TYPE_COLUMN + ") not null"
      + ")";

  // Query to create the table for recording publishers.
  private static final String CREATE_PUBLISHER_TABLE_QUERY = "create table "
      + PUBLISHER_TABLE + " ("
      + PUBLISHER_SEQ_COLUMN
      + " bigint primary key generated always as identity,"
      + PUBLISHER_NAME_COLUMN + " varchar(" + MAX_NAME_COLUMN + ") not null"
      + ")";

  // Query to create the table for recording publications.
  private static final String CREATE_PUBLICATION_TABLE_QUERY = "create table "
      + PUBLICATION_TABLE + " ("
      + PUBLICATION_SEQ_COLUMN
      + " bigint primary key generated always as identity,"
      + MD_ITEM_SEQ_COLUMN + " bigint not null references " + MD_ITEM_TABLE
      + " (" + MD_ITEM_SEQ_COLUMN + ") on delete cascade,"
      + PUBLISHER_SEQ_COLUMN + " bigint not null references " + PUBLISHER_TABLE
      + " (" + PUBLISHER_SEQ_COLUMN + ") on delete cascade,"
      + PUBLICATION_ID_COLUMN + " varchar(" + MAX_PUBLICATION_ID_COLUMN + ")"
      + ")";

  // Query to create the table for recording pending AUs to index.
  private static final String CREATE_PENDING_AU_TABLE_QUERY = "create table "
      + PENDING_AU_TABLE + " ("
      + PLUGIN_ID_COLUMN + " varchar(" + MAX_PLUGIN_ID_COLUMN + ") not null,"
      + AU_KEY_COLUMN + " varchar(" + MAX_AU_KEY_COLUMN + ") not null,"
      + PRIORITY_COLUMN + " bigint not null)";

  // Query to create the table for recording versions.
  private static final String CREATE_VERSION_TABLE_QUERY = "create table "
      + VERSION_TABLE + " ("
      + SYSTEM_COLUMN + " varchar(" + MAX_SYSTEM_COLUMN + ") not null,"
      + VERSION_COLUMN + " smallint not null"
      + ")";

  // Query to create the table for recording requests used for COUNTER reports.
  private static final String REQUEST_TABLE_CREATE_QUERY = "create table "
      + COUNTER_REQUEST_TABLE + " ("
      + URL_COLUMN + " varchar(" + MAX_URL_COLUMN + ") NOT NULL, "
      + IS_PUBLISHER_INVOLVED_COLUMN + " boolean NOT NULL,"
      + REQUEST_YEAR_COLUMN + " smallint NOT NULL,"
      + REQUEST_MONTH_COLUMN + " smallint NOT NULL,"
      + REQUEST_DAY_COLUMN + " smallint NOT NULL,"
      + IN_AGGREGATION_COLUMN + " boolean)";

  // Query to create the table for recording book type aggregates (Full vs.
  // Section) used for COUNTER reports.
  private static final String BOOK_TYPE_AGGREGATES_TABLE_CREATE_QUERY =
      "create table " + COUNTER_BOOK_TYPE_AGGREGATES_TABLE + " ("
      + PUBLICATION_SEQ_COLUMN + " bigint NOT NULL"
      + " CONSTRAINT FK_PUBLICATION_SEQ_BOOK_TYPE_AGGREGATES"
      + " REFERENCES " + PUBLICATION_TABLE + ","
      + IS_PUBLISHER_INVOLVED_COLUMN + " boolean NOT NULL,"
      + REQUEST_YEAR_COLUMN + " smallint NOT NULL,"
      + REQUEST_MONTH_COLUMN + " smallint NOT NULL,"
      + FULL_REQUESTS_COLUMN + " integer,"
      + SECTION_REQUESTS_COLUMN + " integer)";

  // Query to create the table for recording journal type aggregates (PDF vs.
  // HTML) used for COUNTER reports.
  private static final String JOURNAL_TYPE_AGGREGATES_TABLE_CREATE_QUERY =
      "create table " + COUNTER_JOURNAL_TYPE_AGGREGATES_TABLE + " ("
      + PUBLICATION_SEQ_COLUMN + " bigint NOT NULL"
      + " CONSTRAINT FK_PUBLICATION_SEQ_JOURNAL_TYPE_AGGREGATES"
      + " REFERENCES " + PUBLICATION_TABLE + ","
      + IS_PUBLISHER_INVOLVED_COLUMN + " boolean NOT NULL,"
      + REQUEST_YEAR_COLUMN + " smallint NOT NULL,"
      + REQUEST_MONTH_COLUMN + " smallint NOT NULL,"
      + TOTAL_REQUESTS_COLUMN + " integer,"
      + HTML_REQUESTS_COLUMN + " integer,"
      + PDF_REQUESTS_COLUMN + " integer)";

  // Query to create the table for recording journal publication year aggregates
  // used for COUNTER reports.
  private static final String JOURNAL_PUBYEAR_AGGREGATE_TABLE_CREATE_QUERY =
      "create table " + COUNTER_JOURNAL_PUBYEAR_AGGREGATE_TABLE + " ("
      + PUBLICATION_SEQ_COLUMN + " bigint NOT NULL"
      + " CONSTRAINT FK_PUBLICATION_SEQ_JOURNAL_PUBYEAR_AGGREGATE"
      + " REFERENCES " + PUBLICATION_TABLE + ","
      + IS_PUBLISHER_INVOLVED_COLUMN + " boolean NOT NULL,"
      + REQUEST_YEAR_COLUMN + " smallint NOT NULL,"
      + REQUEST_MONTH_COLUMN + " smallint NOT NULL,"
      + PUBLICATION_YEAR_COLUMN + " smallint NOT NULL,"
      + REQUESTS_COLUMN + " integer NOT NULL)";

  // The SQL code used to create the necessary version 1 database tables.
  @SuppressWarnings("serial")
  private static final Map<String, String> VERSION_1_TABLE_CREATE_QUERIES =
      new LinkedHashMap<String, String>() {
	{
	  put(OBSOLETE_METADATA_TABLE, OBSOLETE_CREATE_METADATA_TABLE_QUERY);
	  put(OBSOLETE_TITLE_TABLE, OBSOLETE_CREATE_TITLE_TABLE_QUERY);
	  put(OBSOLETE_PENDINGAUS_TABLE, OBSOLETE_CREATE_PENDINGAUS_TABLE_QUERY);
	  put(OBSOLETE_FEATURE_TABLE, OBSOLETE_CREATE_FEATURE_TABLE_QUERY);
	  put(DOI_TABLE, OBSOLETE_CREATE_DOI_TABLE_QUERY);
	  put(ISBN_TABLE, OBSOLETE_CREATE_ISBN_TABLE_QUERY);
	  put(ISSN_TABLE, OBSOLETE_CREATE_ISSN_TABLE_QUERY);
	  put(OBSOLETE_TITLES_TABLE, OBSOLETE_CREATE_TITLES_TABLE_QUERY);
	  put(OBSOLETE_REQUESTS_TABLE, OBSOLETE_CREATE_REQUESTS_TABLE_QUERY);
	  put(OBSOLETE_PUBYEAR_AGGREGATES_TABLE,
	      OBSOLETE_CREATE_PUBYEAR_AGGREGATES_TABLE_QUERY);
	  put(OBSOLETE_TYPE_AGGREGATES_TABLE,
	      OBSOLETE_CREATE_TYPE_AGGREGATES_TABLE_QUERY);
	}
      };

  // The SQL code used to remove the obsolete version 1 database tables.
  @SuppressWarnings("serial")
  private static final Map<String, String> VERSION_1_TABLE_DROP_QUERIES =
      new LinkedHashMap<String, String>() {
	{
	  put(OBSOLETE_TYPE_AGGREGATES_TABLE,
	      dropTableQuery(OBSOLETE_TYPE_AGGREGATES_TABLE));
	  put(OBSOLETE_PUBYEAR_AGGREGATES_TABLE,
	      dropTableQuery(OBSOLETE_PUBYEAR_AGGREGATES_TABLE));
	  put(OBSOLETE_REQUESTS_TABLE, dropTableQuery(OBSOLETE_REQUESTS_TABLE));
	  put(OBSOLETE_TITLES_TABLE, dropTableQuery(OBSOLETE_TITLES_TABLE));
	  put(ISSN_TABLE, dropTableQuery(ISSN_TABLE));
	  put(ISBN_TABLE, dropTableQuery(ISBN_TABLE));
	  put(DOI_TABLE, dropTableQuery(DOI_TABLE));
	  put(OBSOLETE_FEATURE_TABLE, dropTableQuery(OBSOLETE_FEATURE_TABLE));
	  put(OBSOLETE_PENDINGAUS_TABLE,
	      dropTableQuery(OBSOLETE_PENDINGAUS_TABLE));
	  put(OBSOLETE_TITLE_TABLE, dropTableQuery(OBSOLETE_TITLE_TABLE));
	  put(OBSOLETE_METADATA_TABLE, dropTableQuery(OBSOLETE_METADATA_TABLE));
	}
      };

  // The SQL code used to create the necessary version2 database tables.
  @SuppressWarnings("serial")
  private static final Map<String, String> VERSION_2_TABLE_CREATE_QUERIES =
      new LinkedHashMap<String, String>() {
    	{
    	  put(PLUGIN_TABLE, CREATE_PLUGIN_TABLE_QUERY);
    	  put(AU_TABLE, CREATE_AU_TABLE_QUERY);
    	  put(AU_MD_TABLE, CREATE_AU_MD_TABLE_QUERY);
    	  put(MD_ITEM_TYPE_TABLE, CREATE_MD_ITEM_TYPE_TABLE_QUERY);
    	  put(MD_ITEM_TABLE, CREATE_MD_ITEM_TABLE_QUERY);
    	  put(MD_ITEM_NAME_TABLE, CREATE_MD_ITEM_NAME_TABLE_QUERY);
    	  put(MD_KEY_TABLE, CREATE_MD_KEY_TABLE_QUERY);
    	  put(MD_TABLE, CREATE_MD_TABLE_QUERY);
    	  put(BIB_ITEM_TABLE, CREATE_BIB_ITEM_TABLE_QUERY);
    	  put(URL_TABLE, CREATE_URL_TABLE_QUERY);
    	  put(AUTHOR_TABLE, CREATE_AUTHOR_TABLE_QUERY);
    	  put(KEYWORD_TABLE, CREATE_KEYWORD_TABLE_QUERY);
    	  put(DOI_TABLE, CREATE_DOI_TABLE_QUERY);
    	  put(ISSN_TABLE, CREATE_ISSN_TABLE_QUERY);
    	  put(ISBN_TABLE, CREATE_ISBN_TABLE_QUERY);
    	  put(PUBLISHER_TABLE, CREATE_PUBLISHER_TABLE_QUERY);
    	  put(PUBLICATION_TABLE, CREATE_PUBLICATION_TABLE_QUERY);
    	  put(PENDING_AU_TABLE, CREATE_PENDING_AU_TABLE_QUERY);
    	  put(VERSION_TABLE, CREATE_VERSION_TABLE_QUERY);
    	  put(COUNTER_REQUEST_TABLE, REQUEST_TABLE_CREATE_QUERY);
    	  put(COUNTER_JOURNAL_PUBYEAR_AGGREGATE_TABLE,
    	      JOURNAL_PUBYEAR_AGGREGATE_TABLE_CREATE_QUERY);
    	  put(COUNTER_BOOK_TYPE_AGGREGATES_TABLE,
    	      BOOK_TYPE_AGGREGATES_TABLE_CREATE_QUERY);
    	  put(COUNTER_JOURNAL_TYPE_AGGREGATES_TABLE,
    	      JOURNAL_TYPE_AGGREGATES_TABLE_CREATE_QUERY);
    	}
          };

  // SQL statements that create the necessary version 1 functions.
  private static final String[] VERSION_1_FUNCTION_CREATE_QUERIES =
      new String[] {
    "create function contentSizeFromUrl(url varchar(4096)) "
    	+ "returns bigint language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getContentSizeFromArticleUrl' "
    	+ "parameter style java no sql",

        "create function contentTypeFromUrl(url varchar(4096)) "
    	+ "returns varchar(512) language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getContentTypeFromArticleUrl' "
    	+ "parameter style java no sql",

        "create function eisbnFromAuId(pluginId varchar(128), "
    	+ "auKey varchar(512)) returns varchar(8) language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getEisbnFromAuId' "
    	+ "parameter style java no sql",

        "create function eisbnFromUrl(url varchar(4096)) "
    	+ "returns varchar(13) language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getEisbnFromArticleUrl' "
    	+ "parameter style java no sql",

        "create function eissnFromAuId(pluginId varchar(128), "
    	+ "auKey varchar(512)) returns varchar(8) language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getEissnFromAuId' "
    	+ "parameter style java no sql",

        "create function eissnFromUrl(url varchar(4096)) "
    	+ "returns varchar(8) language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getEissnFromArticleUrl' "
    	+ "parameter style java no sql",

        "create function endVolumeFromAuId(pluginId varchar(128), "
    	+ "auKey varchar(512)) returns varchar(16) language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getEndVolumeFromAuId' "
    	+ "parameter style java no sql",

        "create function endVolumeFromUrl(url varchar(4096)) "
    	+ "returns varchar(16) language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getEndVolumeFromArticleUrl' "
    	+ "parameter style java no sql",

        "create function endYearFromAuId(pluginId varchar(128), "
    	+ "auKey varchar(512)) returns varchar(16) language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getEndYearFromAuId' "
    	+ "parameter style java no sql",

        "create function endYearFromUrl(url varchar(4096)) "
    	+ "returns varchar(16) language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getEndYearFromArticleUrl' "
    	+ "parameter style java no sql",

        "create function generateAuId(pluginId varchar(128), "
    	+ "auKey varchar(512)) returns varchar(640) language java external name "
    	+ "'org.lockss.plugin.PluginManager.generateAuId' "
    	+ "parameter style java no sql",

        "create function formatIsbn(isbn varchar(17)) "
    	+ "returns varchar(17) language java external name "
    	+ "'org.lockss.util.MetadataUtil.formatIsbn' "
    	+ "parameter style java no sql",

        "create function formatIssn(issn varchar(9)) "
    	+ "returns varchar(9) language java external name "
    	+ "'org.lockss.util.MetadataUtil.formatIssn' "
    	+ "parameter style java no sql",

        "create function ingestDateFromAuId(pluginId varchar(128), "
    	+ "auKey varchar(512)) returns varchar(16) language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getingestDateFromAuId' "
    	+ "parameter style java no sql",

        "create function ingestDateFromUrl(url varchar(4096)) "
    	+ "returns varchar(16) language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getIngestDateFromArticleUrl' "
    	+ "parameter style java no sql",

        "create function ingestYearFromAuId(pluginId varchar(128), "
    	+ "auKey varchar(512)) returns varchar(4) language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getIngestYearFromAuId' "
    	+ "parameter style java no sql",

        "create function ingestYearFromUrl(url varchar(4096)) "
    	+ "returns varchar(4) language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getIngestYearFromArticleUrl' "
    	+ "parameter style java no sql",

        "create function isbnFromAuId(pluginId varchar(128), "
    	+ "auKey varchar(512)) returns varchar(8) language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getPrintIsbnFromAuId' "
    	+ "parameter style java no sql",

        "create function isbnFromUrl(url varchar(4096)) "
    	+ "returns varchar(13) language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getIsbnFromArticleUrl' "
    	+ "parameter style java no sql",

        "create function issnFromAuId(pluginId varchar(128), "
    	+ "auKey varchar(512)) returns varchar(8) language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getPrintIssnFromAuId' "
    	+ "parameter style java no sql",

        "create function issnFromUrl(url varchar(4096)) "
    	+ "returns varchar(8) language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getIssnFromArticleUrl' "
    	+ "parameter style java no sql",

        "create function issnlFromAuId(pluginId varchar(128), "
    	+ "auKey varchar(512)) returns varchar(8) language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getIssnLFromAuId' "
    	+ "parameter style java no sql",

        "create function issnlFromUrl(url varchar(4096)) "
    	+ "returns varchar(8) language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getIssnLFromArticleUrl' "
    	+ "parameter style java no sql",

        "create function printIsbnFromAuId(pluginId varchar(128), "
    	+ "auKey varchar(512)) returns varchar(8) language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getPrintIsbnFromAuId' "
    	+ "parameter style java no sql",

        "create function printIsbnFromUrl(url varchar(4096)) "
    	+ "returns varchar(13) language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getPrintIsbnFromArticleUrl' "
    	+ "parameter style java no sql",

        "create function printIssnFromAuId(pluginId varchar(128), "
    	+ "auKey varchar(512)) returns varchar(8) language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getPrintIssnFromAuId' "
    	+ "parameter style java no sql",

        "create function printIssnFromUrl(url varchar(4096)) "
    	+ "returns varchar(8) language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getPrintIssnFromArticleUrl' "
    	+ "parameter style java no sql",

        "create function publisherFromUrl(url varchar(4096)) "
    	+ "returns varchar(256) language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getPublisherFromArticleUrl' "
    	+ "parameter style java no sql",

        "create function publisherFromAuId(pluginId varchar(128), "
    	+ "auKey varchar(512)) returns varchar(256)  language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getPublisherFromAuId' "
    	+ "parameter style java no sql",

        "create function startVolumeFromAuId(pluginId varchar(128), "
    	+ "auKey varchar(512)) returns varchar(16) language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getStartVolumeFromAuId' "
    	+ "parameter style java no sql",

        "create function startVolumeFromUrl(url varchar(4096)) "
    	+ "returns varchar(16) language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getStartVolumeFromArticleUrl' "
    	+ "parameter style java no sql",

        "create function startYearFromAuId(pluginId varchar(128), "
    	+ "auKey varchar(512)) returns varchar(16) language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getStartYearFromAuId' "
    	+ "parameter style java no sql",

        "create function startYearFromUrl(url varchar(4096)) "
    	+ "returns varchar(16) language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getStartYearFromArticleUrl' "
    	+ "parameter style java no sql",

        "create function titleFromAuId(pluginId varchar(128), "
    	+ "auKey varchar(512)) returns varchar(256) language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getTitleFromAuId' "
    	+ "parameter style java no sql",

        "create function titleFromIssn(issn varchar(9)) "
    	+ "returns varchar(512) language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getTitleFromIssn' "
    	+ "parameter style java no sql",

        "create function titleFromUrl(url varchar(4096)) "
    	+ "returns varchar(512) language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getTitleFromArticleUrl' "
    	+ "parameter style java no sql",

        "create function volumeTitleFromAuId(pluginId varchar(128), "
    	+ "auKey varchar(512)) returns varchar(256) language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getVolumeTitleFromAuId' "
    	+ "parameter style java no sql",

        "create function volumeTitleFromIsbn(issn varchar(18)) "
    	+ "returns varchar(512) language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getVolumeTitleFromIsbn' "
    	+ "parameter style java no sql",

        "create function volumeTitleFromUrl(url varchar(4096)) "
    	+ "returns varchar(512) language java external name "
    	+ "'org.lockss.util.SqlStoredProcedures.getVolumeTitleFromArticleUrl' "
    	+ "parameter style java no sql",

        "create function yearFromDate(date varchar(16)) returns varchar(4) "
    	+ "language java external name "
    	+ "'org.lockss.util.MetadataUtil.getYearFromDate' "
    	+ "parameter style java no sql", };

  // SQL statements that drop the obsolete version 1 functions.
  @SuppressWarnings("serial")
  private static final Map<String, String> VERSION_1_FUNCTION_DROP_QUERIES =
      new LinkedHashMap<String, String>() {
	{
	  put("contentSizeFromUrl", dropFunctionQuery("contentSizeFromUrl"));
	  put("contentTypeFromUrl", dropFunctionQuery("contentTypeFromUrl"));
	  put("eisbnFromAuId", dropFunctionQuery("eisbnFromAuId"));
	  put("eisbnFromUrl", dropFunctionQuery("eisbnFromUrl"));
	  put("eissnFromAuId", dropFunctionQuery("eissnFromAuId"));
	  put("eissnFromUrl", dropFunctionQuery("eissnFromUrl"));
	  put("endVolumeFromAuId", dropFunctionQuery("endVolumeFromAuId"));
	  put("endVolumeFromUrl", dropFunctionQuery("endVolumeFromUrl"));
	  put("endYearFromAuId", dropFunctionQuery("endYearFromAuId"));
	  put("endYearFromUrl", dropFunctionQuery("endYearFromUrl"));
	  put("formatIsbn", dropFunctionQuery("formatIsbn"));
	  put("formatIssn", dropFunctionQuery("formatIssn"));
	  put("generateAuId", dropFunctionQuery("generateAuId"));
	  put("ingestDateFromAuId", dropFunctionQuery("ingestDateFromAuId"));
	  put("ingestDateFromUrl", dropFunctionQuery("ingestDateFromUrl"));
	  put("ingestYearFromAuId", dropFunctionQuery("ingestYearFromAuId"));
	  put("ingestYearFromUrl", dropFunctionQuery("ingestYearFromUrl"));
	  put("isbnFromAuId", dropFunctionQuery("isbnFromAuId"));
	  put("isbnFromUrl", dropFunctionQuery("isbnFromUrl"));
	  put("issnFromAuId", dropFunctionQuery("issnFromAuId"));
	  put("issnFromUrl", dropFunctionQuery("issnFromUrl"));
	  put("issnlFromAuId", dropFunctionQuery("issnlFromAuId"));
	  put("issnlFromUrl", dropFunctionQuery("issnlFromUrl"));
	  put("printIsbnFromAuId", dropFunctionQuery("printIsbnFromAuId"));
	  put("printIsbnFromUrl", dropFunctionQuery("printIsbnFromUrl"));
	  put("printIssnFromAuId", dropFunctionQuery("printIssnFromAuId"));
	  put("printIssnFromUrl", dropFunctionQuery("printIssnFromUrl"));
	  put("publisherFromAuId", dropFunctionQuery("publisherFromAuId"));
	  put("publisherFromUrl", dropFunctionQuery("publisherFromUrl"));
	  put("startVolumeFromAuId", dropFunctionQuery("startVolumeFromAuId"));
	  put("startVolumeFromUrl", dropFunctionQuery("startVolumeFromUrl"));
	  put("startYearFromAuId", dropFunctionQuery("startYearFromAuId"));
	  put("startYearFromUrl", dropFunctionQuery("startYearFromUrl"));
	  put("titleFromAuId", dropFunctionQuery("titleFromAuId"));
	  put("titleFromIssn", dropFunctionQuery("titleFromIssn"));
	  put("titleFromUrl", dropFunctionQuery("titleFromUrl"));
	  put("volumeTitleFromAuId", dropFunctionQuery("volumeTitleFromAuId"));
	  put("volumeTitleFromIsbn", dropFunctionQuery("volumeTitleFromIsbn"));
	  put("volumeTitleFromUrl", dropFunctionQuery("volumeTitleFromUrl"));
	  put("yearFromDate", dropFunctionQuery("yearFromDate"));
	}
      };

  // SQL statements that create the necessary version 2 functions.
  private static final String[] VERSION_2_FUNCTION_CREATE_QUERIES =
      new String[] {
    "create function contentSizeFromUrl(url varchar(4096)) "
	+ "returns bigint language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getContentSizeFromArticleUrl' "
	+ "parameter style java no sql",

    "create function contentTypeFromUrl(url varchar(4096)) "
	+ "returns varchar(512) language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getContentTypeFromArticleUrl' "
	+ "parameter style java no sql",

    "create function eisbnFromAuId(pluginId varchar(128), "
	+ "auKey varchar(512)) returns varchar(8) language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getEisbnFromAuId' "
	+ "parameter style java no sql",

    "create function eisbnFromUrl(url varchar(4096)) "
	+ "returns varchar(13) language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getEisbnFromArticleUrl' "
	+ "parameter style java no sql",

    "create function eissnFromAuId(pluginId varchar(128), "
	+ "auKey varchar(512)) returns varchar(8) language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getEissnFromAuId' "
	+ "parameter style java no sql",

    "create function eissnFromUrl(url varchar(4096)) "
	+ "returns varchar(8) language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getEissnFromArticleUrl' "
	+ "parameter style java no sql",

    "create function endVolumeFromAuId(pluginId varchar(128), "
	+ "auKey varchar(512)) returns varchar(16) language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getEndVolumeFromAuId' "
	+ "parameter style java no sql",

    "create function endVolumeFromUrl(url varchar(4096)) "
	+ "returns varchar(16) language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getEndVolumeFromArticleUrl' "
	+ "parameter style java no sql",

    "create function endYearFromAuId(pluginId varchar(128), "
	+ "auKey varchar(512)) returns varchar(16) language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getEndYearFromAuId' "
	+ "parameter style java no sql",

    "create function endYearFromUrl(url varchar(4096)) "
	+ "returns varchar(16) language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getEndYearFromArticleUrl' "
	+ "parameter style java no sql",

    "create function generateAuId(pluginId varchar(128), "
	+ "auKey varchar(512)) returns varchar(640) language java external name "
	+ "'org.lockss.plugin.PluginManager.generateAuId' "
	+ "parameter style java no sql",

    "create function formatIsbn(isbn varchar(17)) "
	+ "returns varchar(17) language java external name "
	+ "'org.lockss.util.MetadataUtil.formatIsbn' "
	+ "parameter style java no sql",

    "create function formatIssn(issn varchar(9)) "
	+ "returns varchar(9) language java external name "
	+ "'org.lockss.util.MetadataUtil.formatIssn' "
	+ "parameter style java no sql",

    "create function ingestDateFromAuId(pluginId varchar(128), "
	+ "auKey varchar(512)) returns varchar(16) language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getingestDateFromAuId' "
	+ "parameter style java no sql",

    "create function ingestDateFromUrl(url varchar(4096)) "
	+ "returns varchar(16) language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getIngestDateFromArticleUrl' "
	+ "parameter style java no sql",

    "create function ingestYearFromAuId(pluginId varchar(128), "
	+ "auKey varchar(512)) returns varchar(4) language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getIngestYearFromAuId' "
	+ "parameter style java no sql",

    "create function ingestYearFromUrl(url varchar(4096)) "
	+ "returns varchar(4) language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getIngestYearFromArticleUrl' "
	+ "parameter style java no sql",

    "create function isbnFromAuId(pluginId varchar(128), "
	+ "auKey varchar(512)) returns varchar(8) language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getPrintIsbnFromAuId' "
	+ "parameter style java no sql",

    "create function isbnFromUrl(url varchar(4096)) "
	+ "returns varchar(13) language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getIsbnFromArticleUrl' "
	+ "parameter style java no sql",

    "create function issnFromAuId(pluginId varchar(128), "
	+ "auKey varchar(512)) returns varchar(8) language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getPrintIssnFromAuId' "
	+ "parameter style java no sql",

    "create function issnFromUrl(url varchar(4096)) "
	+ "returns varchar(8) language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getIssnFromArticleUrl' "
	+ "parameter style java no sql",

    "create function issnlFromAuId(pluginId varchar(128), "
	+ "auKey varchar(512)) returns varchar(8) language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getIssnLFromAuId' "
	+ "parameter style java no sql",

    "create function issnlFromUrl(url varchar(4096)) "
	+ "returns varchar(8) language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getIssnLFromArticleUrl' "
	+ "parameter style java no sql",

    "create function printIsbnFromAuId(pluginId varchar(128), "
	+ "auKey varchar(512)) returns varchar(8) language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getPrintIsbnFromAuId' "
	+ "parameter style java no sql",

    "create function printIsbnFromUrl(url varchar(4096)) "
	+ "returns varchar(13) language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getPrintIsbnFromArticleUrl' "
	+ "parameter style java no sql",

    "create function printIssnFromAuId(pluginId varchar(128), "
	+ "auKey varchar(512)) returns varchar(8) language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getPrintIssnFromAuId' "
	+ "parameter style java no sql",

    "create function printIssnFromUrl(url varchar(4096)) "
	+ "returns varchar(8) language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getPrintIssnFromArticleUrl' "
	+ "parameter style java no sql",

    "create function publisherFromUrl(url varchar(4096)) "
	+ "returns varchar(256) language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getPublisherFromArticleUrl' "
	+ "parameter style java no sql",

    "create function publisherFromAuId(pluginId varchar(128), "
	+ "auKey varchar(512)) returns varchar(256)  language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getPublisherFromAuId' "
	+ "parameter style java no sql",

    "create function startVolumeFromAuId(pluginId varchar(128), "
	+ "auKey varchar(512)) returns varchar(16) language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getStartVolumeFromAuId' "
	+ "parameter style java no sql",

    "create function startVolumeFromUrl(url varchar(4096)) "
	+ "returns varchar(16) language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getStartVolumeFromArticleUrl' "
	+ "parameter style java no sql",

    "create function startYearFromAuId(pluginId varchar(128), "
	+ "auKey varchar(512)) returns varchar(16) language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getStartYearFromAuId' "
	+ "parameter style java no sql",

    "create function startYearFromUrl(url varchar(4096)) "
	+ "returns varchar(16) language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getStartYearFromArticleUrl' "
	+ "parameter style java no sql",

    "create function titleFromAuId(pluginId varchar(128), "
	+ "auKey varchar(512)) returns varchar(256) language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getTitleFromAuId' "
	+ "parameter style java no sql",

    "create function titleFromIssn(issn varchar(9)) "
	+ "returns varchar(512) language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getTitleFromIssn' "
	+ "parameter style java no sql",

    "create function titleFromUrl(url varchar(4096)) "
	+ "returns varchar(512) language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getTitleFromArticleUrl' "
	+ "parameter style java no sql",

    "create function volumeTitleFromAuId(pluginId varchar(128), "
	+ "auKey varchar(512)) returns varchar(256) language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getVolumeTitleFromAuId' "
	+ "parameter style java no sql",

    "create function volumeTitleFromIsbn(issn varchar(18)) "
	+ "returns varchar(512) language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getVolumeTitleFromIsbn' "
	+ "parameter style java no sql",

    "create function volumeTitleFromUrl(url varchar(4096)) "
	+ "returns varchar(512) language java external name "
	+ "'org.lockss.util.SqlStoredProcedures.getVolumeTitleFromArticleUrl' "
	+ "parameter style java no sql",

    "create function yearFromDate(date varchar(16)) returns varchar(4) "
	+ "language java external name "
	+ "'org.lockss.util.MetadataUtil.getYearFromDate' "
	+ "parameter style java no sql", };

  // Database metadata keys.
  private static final String COLUMN_NAME = "COLUMN_NAME";
  private static final String COLUMN_SIZE = "COLUMN_SIZE";
  private static final String FUNCTION_NAME = "FUNCTION_NAME";
  private static final String TABLE_NAME = "TABLE_NAME";
  private static final String TYPE_NAME = "TYPE_NAME";

  private static final String DATABASE_VERSION_TABLE_SYSTEM = "database";

  // Query to get the database version.
  private static final String GET_DATABASE_VERSION_QUERY = "select "
      + VERSION_COLUMN
      + " from " + VERSION_TABLE
      + " where " + SYSTEM_COLUMN + " = '" + DATABASE_VERSION_TABLE_SYSTEM
      + "'";

  // Query to insert a type of metadata item.
  private static final String INSERT_MD_ITEM_TYPE_QUERY = "insert into "
      + MD_ITEM_TYPE_TABLE
      + "(" + MD_ITEM_TYPE_SEQ_COLUMN
      + "," + TYPE_NAME_COLUMN
      + ") values (default,?)";

  // Query to insert the database version.
  private static final String INSERT_DB_VERSION_QUERY = "insert into "
      + VERSION_TABLE
      + "(" + SYSTEM_COLUMN
      + "," + VERSION_COLUMN
      + ") values (?,?)";

  // Query to update the database version.
  private static final String UPDATE_DB_VERSION_QUERY = "update "
      + VERSION_TABLE
      + " set " + VERSION_COLUMN + " = ?"
      + " where " + SYSTEM_COLUMN + " = ?";

  // The database data source.
  private DataSource dataSource = null;

  // The data source configuration.
  private Configuration dataSourceConfig = null;

  // The data source user.
  private String datasourceUser = null;

  // The network server control.
  private NetworkServerControl networkServerControl = null;
  
  private SqlOpenUrlResolverDbManager sqlOpenUrlResolverDbManager = null;

  // An indication of whether this object is ready to be used.
  private boolean ready = false;

  // The version of the database to be targeted by this daemon.
  //
  // After this service has started successfully, this is the version of the
  // database that will be in place, as long as the database version prior to
  // starting the service was not higher already.
  private int targetDatabaseVersion = 2;

  // The maximum number of retries to be attempted when encountering transient
  // SQL exceptions.
  private int maxRetryCount = DEFAULT_MAX_RETRY_COUNT;

  // The interval to wait between consecutive retries when encountering
  // transient SQL exceptions.
  private long retryDelay = DEFAULT_RETRY_DELAY;
  
  

  /**
   * Starts the SqlDbManager service.
   */
  @Override
  public void startService() {
    final String DEBUG_HEADER = "startService(): ";
    log.debug2(DEBUG_HEADER + "dataSource != null = " + (dataSource != null));

    // Do nothing more if it is already initialized.
    ready = ready && dataSource != null;
    if (ready) {
      return;
    }

    // Do nothing more if the database infrastructure cannot be setup.
    if (!setUpInfrastructure()) {
      return;
    }

    Connection conn = null;

    try {
      conn = getConnectionBeforeReady();

      // Find the current database version.
      int existingDatabaseVersion = 0;

      if (tableExistsBeforeReady(conn, VERSION_TABLE)) {
	existingDatabaseVersion = getDatabaseVersionBeforeReady(conn);
      } else if (tableExistsBeforeReady(conn, OBSOLETE_METADATA_TABLE)){
	existingDatabaseVersion = 1;
      }

      log.debug2("existingDatabaseVersion = " + existingDatabaseVersion);
      log.debug2("targetDatabaseVersion = " + targetDatabaseVersion);

      // Check whether the database needs to be updated.
      if (targetDatabaseVersion > existingDatabaseVersion) {
	// Yes: Check whether the database update was successful.
	if (updateDatabase(conn, existingDatabaseVersion,
	                   targetDatabaseVersion)) {
	  // Yes.
	  log.info("Database has been updated to version "
	      + targetDatabaseVersion);
	} else {
	  // No.
	  return;
	}
	// No: Check whether the database is already up-to-date.
      } else if (targetDatabaseVersion == existingDatabaseVersion) {
	// Yes: Nothing more to do.
	log.info("Database is up-to-date (version " + targetDatabaseVersion
	         + ")");
      } else {
	// No: The existing database is newer than what this version of the
	// daemon expects: Disable the use of the database.
	log.error("Existing database is version " + existingDatabaseVersion
	    + ", which is higher than the target database version "
	    + targetDatabaseVersion + " for this daemon.");

	return;
      }

      ready = true;
    } catch (BatchUpdateException bue) {
      log.error("Error initializing manager", bue);
      return;
    } catch (SQLException sqle) {
      log.error("Error initializing manager", sqle);
      return;
    } finally {
      if (ready) {
	try {
	  conn.commit();
	  SqlDbManager.safeCloseConnection(conn);
	} catch (SQLException sqle) {
	  log.error("Exception caught committing the connection", sqle);
	  SqlDbManager.safeRollbackAndClose(conn);
	  ready = false;
	}
      } else {
	SqlDbManager.safeRollbackAndClose(conn);
      }
    }
    
    sqlOpenUrlResolverDbManager = new SqlOpenUrlResolverDbManager(this);
    
    log.debug2(DEBUG_HEADER + "SqlDbManager ready? = " + ready);
  }

  @Override
  public void setConfig(Configuration config, Configuration prevConfig,
			Configuration.Differences changedKeys) {
    if (changedKeys.contains(PREFIX)) {
	maxRetryCount = config.getInt(PARAM_MAX_RETRY_COUNT,
				      DEFAULT_MAX_RETRY_COUNT);

	retryDelay = config.getTimeInterval(PARAM_RETRY_DELAY,
					    DEFAULT_RETRY_DELAY);
    }
  }

  /**
   * Sets up the database infrastructure.
   */
  private boolean setUpInfrastructure() {
    final String DEBUG_HEADER = "setUpInfrastructure(): ";

    boolean setUp = false;

    // Get the datasource configuration.
    dataSourceConfig = getDataSourceConfig();

    // Create the datasource.
    try {
      dataSource = createDataSource();
    } catch (Exception e) {
      log.error("Cannot create the datasource - SqlDbManager not ready", e);
      return setUp;
    }

    // Check whether the datasource properties have been successfully
    // initialized.
    if (initializeDataSourceProperties()) {
      // Yes: Check whether the Derby NetworkServerControl for client
      // connections needs to be started.
      if (dataSource instanceof ClientDataSource) {
	// Yes: Start it.
	try {
	  setUp = startNetworkServerControl();
	  log.debug2(DEBUG_HEADER + "SqlDbManager set up? = " + setUp);
	} catch (Exception e) {
	  log.error("Cannot enable remote access to Derby database - "
	      + "SqlDbManager not ready", e);
	  dataSource = null;
	  return setUp;
	}
      } else {
	setUp = true;
	log.debug2(DEBUG_HEADER + "SqlDbManager set up? = " + setUp);
      }
    } else {
      dataSource = null;
      log.error("Could not initialize the datasource - SqlDbManager not ready.");
    }

    return setUp;
  }

  /**
   * Provides a database connection using the datasource, retrying the operation
   * in case of transient failures. To be used during
   * initialization.
   * <p />
   * Autocommit is disabled to allow the client code to manage transactions.
   * 
   * @return a Connection with the database connection to be used.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private Connection getConnectionBeforeReady() throws SQLException {
    return getConnectionBeforeReady(maxRetryCount, retryDelay);
  }

  /**
   * Provides a database connection using the datasource, retrying the operation
   * in case of transient failures. To be used during
   * initialization.
   * <p />
   * Autocommit is disabled to allow the client code to manage transactions.
   * 
   * @param maxRetryCount
   *          An int with the maximum number of retries to be attempted.
   * @param retryDelay
   *          A long with the number of milliseconds to wait between
   *          consecutive retries.
   * @return a Connection with the database connection to be used.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private Connection getConnectionBeforeReady(int maxRetryCount,
      long retryDelay) throws SQLException {
    final String DEBUG_HEADER = "getConnectionBeforeReady(): ";

    boolean success = false;
    int retryCount = 0;
    Connection conn = null;

    // Keep trying until success.
    while (!success) {
      try {
	if (dataSource instanceof javax.sql.ConnectionPoolDataSource) {
	  conn = ((javax.sql.ConnectionPoolDataSource) dataSource)
	      .getPooledConnection().getConnection();
	} else {
	  conn = dataSource.getConnection();
    	}

	conn.setAutoCommit(false);
	success = true;
      } catch (SQLTransientException sqltre) {
	// A SQLTransientException is caught: Count the next retry.
	retryCount++;

	// Check whether the next retry would go beyond the specified maximum
	// number of retries.
	if (retryCount > maxRetryCount) {
	  // Yes: Report the failure.
	  log.error("Transient exception caught", sqltre);
	  log.error("Maximum retry count of " + maxRetryCount + " reached.");
	  throw sqltre;
	} else {
	  // No: Wait for the specified amount of time before attempting the
	  // next retry.
	  log.debug(DEBUG_HEADER + "Exception caught", sqltre);
	  log.debug(DEBUG_HEADER + "Waiting "
	      + StringUtil.timeIntervalToString(retryDelay)
	      + " before retry number " + retryCount + "...");
	  try {
	    Deadline.in(retryDelay).sleep();
	  } catch (InterruptedException ie) {
	    // Continue with the next retry.
	  }
	}
      }
    }

    return conn;
  }

  /**
   * Provides an indication of whether a table exists to be used during
   * initialization.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param tableName
   *          A String with name of the table to be checked.
   * @return <code>true</code> if the named table exists, <code>false</code>
   *         otherwise.
   * @throws BatchUpdateException
   *           if a batch update exception occurred.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private boolean tableExistsBeforeReady(Connection conn, String tableName)
      throws SQLException {
    if (conn == null) {
      throw new NullPointerException("Null connection.");
    }

    ResultSet resultSet = null;

    try {
      // Get the database schema table data.
      resultSet =
	  conn.getMetaData().getTables(null, datasourceUser, null, null);

      // Loop through each table.
      while (resultSet.next()) {
	if (tableName.toUpperCase().equals(resultSet.getString(TABLE_NAME))) {
	  // Found the table: No need to check further.
	  return true;
	}
      }
    } finally {
      safeCloseResultSet(resultSet);
    }

    // The table does not exist.
    return false;
  }

  /**
   * Get the database version to be used during initialization.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @return an int with the database version.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private int getDatabaseVersionBeforeReady(Connection conn)
      throws SQLException {
    final String DEBUG_HEADER = "getDatabaseVersionBeforeReady(): ";
    int version = 1;
    PreparedStatement stmt =
	prepareStatementBeforeReady(conn, GET_DATABASE_VERSION_QUERY);
    ResultSet resultSet = null;

    try {
      resultSet = executeQueryBeforeReady(stmt);
      if (resultSet.next()) {
	version = resultSet.getShort(VERSION_COLUMN);
      }
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      SqlDbManager.safeCloseStatement(stmt);
    }

    log.debug2(DEBUG_HEADER + "version = " + version);
    return version;
  }

  /**
   * Updates the database to the target version.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param existingDatabaseVersion
   *          An int with the existing database version.
   * @param finalDatabaseVersion
   *          An int with the version of the database to which the database is
   *          to be updated.
   * @return <code>true</code> if the database was successfully updated,
   *         <code>false</code> otherwise.
   * @throws BatchUpdateException
   *           if a batch update exception occurred.
   * @throws SQLException
   *           if any other problem occurred accessing the database.
   */
  private boolean updateDatabase(Connection conn, int existingDatabaseVersion,
      int finalDatabaseVersion) throws BatchUpdateException, SQLException {
    final String DEBUG_HEADER = "updateDatabase(): ";
    boolean success = true;

    // Loop through all the versions to be updated to reach the targeted
    // version.
    for (int from = existingDatabaseVersion; from < finalDatabaseVersion;
	from++) {
      log.debug2(DEBUG_HEADER + "Updating from version " + from + "...");

      // Perform the appropriate update for this version.
      if (from == 0) {
	setUpDatabaseVersion1(conn);
      } else if (from == 1) {
	updateDatabaseFrom1To2(conn);
      } else {
	log.error("Non-existent method to update the database from version "
	    + from + ".");
	success = false;
	break;
      }
      log.debug2(DEBUG_HEADER + "Done updating from version " + from + ".");
    }

    if (success && finalDatabaseVersion > 1) {
      // Record the current database version in the database.
      recordDbVersion(conn, finalDatabaseVersion);
      log.debug2(DEBUG_HEADER + "Done updating the database.");
    }

    return success;
  }

  /**
   * Updates the database from version 1 to version 2.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @throws BatchUpdateException
   *           if a batch update exception occurred.
   * @throws SQLException
   *           if any other problem occurred accessing the database.
   */
  private void updateDatabaseFrom1To2(Connection conn)
      throws BatchUpdateException, SQLException {
    // Remove obsolete database tables.
    removeVersion1ObsoleteTablesIfPresent(conn);

    // Create the necessary tables if they do not exist.
    createVersion2TablesIfMissing(conn);
	
    // Initialize necessary data in new tables.
    addMetadataItemType(conn, MD_ITEM_TYPE_BOOK_SERIES);
    addMetadataItemType(conn, MD_ITEM_TYPE_BOOK);
    addMetadataItemType(conn, MD_ITEM_TYPE_BOOK_CHAPTER);
    addMetadataItemType(conn, MD_ITEM_TYPE_JOURNAL);
    addMetadataItemType(conn, MD_ITEM_TYPE_JOURNAL_ARTICLE);

    // Remove old functions.
    removeVersion1FunctionsIfPresent(conn);

    // Create new functions.
    createVersion2FunctionsIfMissing(conn);
  }

  /**
   * Removes all the obsolete version 1 database tables if they exist.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @throws BatchUpdateException
   *           if a batch update exception occurred.
   * @throws SQLException
   *           if any other problem occurred accessing the database.
   */
  private void removeVersion1ObsoleteTablesIfPresent(Connection conn)
      throws BatchUpdateException, SQLException {
    final String DEBUG_HEADER = "removeVersion1ObsoleteTablesIfPresent(): ";

      // Loop through all the table names.
    for (String tableName : VERSION_1_TABLE_DROP_QUERIES.keySet()) {
      log.debug2(DEBUG_HEADER + "Checking table = " + tableName);

      // Remove the table if it does exist.
      removeTableIfPresent(conn, tableName,
                           VERSION_1_TABLE_DROP_QUERIES.get(tableName));
    }
  }

  /**
   * Creates all the necessary version 2 database tables if they do not exist.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @throws BatchUpdateException
   *           if a batch update exception occurred.
   * @throws SQLException
   *           if any other problem occurred accessing the database.
   */
  private void createVersion2TablesIfMissing(Connection conn)
      throws BatchUpdateException, SQLException {
    final String DEBUG_HEADER = "createVersion2TablesIfMissing(): ";

    // Loop through all the table names.
    for (String tableName : VERSION_2_TABLE_CREATE_QUERIES.keySet()) {
      log.debug2(DEBUG_HEADER + "Checking table = " + tableName);

      // Create the table if it does not exist.
      createTableIfMissingBeforeReady(conn, tableName,
                                      VERSION_2_TABLE_CREATE_QUERIES
                                      	.get(tableName));
    }
  }

  /**
   * Adds a metadata item type to the database.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param typeName
   *          A String with the name of the type to be added.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private void addMetadataItemType(Connection conn, String typeName)
      throws SQLException {
    final String DEBUG_HEADER = "addMetadataItemType(): ";

    if (StringUtil.isNullString(typeName)) {
      return;
    }

    PreparedStatement insertMetadataItemType =
	prepareStatementBeforeReady(conn, INSERT_MD_ITEM_TYPE_QUERY);

    try {
      insertMetadataItemType.setString(1, typeName);
      int count = executeUpdateBeforeReady(insertMetadataItemType);

      if (log.isDebug3()) {
	log.debug2(DEBUG_HEADER + "count = " + count);
	log.debug2(DEBUG_HEADER + "Added metadata item type = " + typeName);
      }
    } finally {
      SqlDbManager.safeCloseStatement(insertMetadataItemType);
    }
  }

  /**
   * Removes all the obsolete version 1 database functions if they exist.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @throws BatchUpdateException
   *           if a batch update exception occurred.
   * @throws SQLException
   *           if any other problem occurred accessing the database.
   */
  private void removeVersion1FunctionsIfPresent(Connection conn)
      throws BatchUpdateException, SQLException {
    final String DEBUG_HEADER = "removeVersion1FunctionsIfPresent(): ";

    // Loop through all the function names.
    for (String functionName : VERSION_1_FUNCTION_DROP_QUERIES.keySet()) {
      log.debug2(DEBUG_HEADER + "Checking function = " + functionName);

      // Remove the function if it does exist.
      removeFunctionIfPresentBeforeReady(conn, functionName,
					 VERSION_1_FUNCTION_DROP_QUERIES
					     .get(functionName));
    }
  }

  /**
   * Creates all the necessary version 2 database functions if they do not
   * exist.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @throws BatchUpdateException
   *           if a batch update exception occurred.
   * @throws SQLException
   *           if any other problem occurred accessing the database.
   */
  private void createVersion2FunctionsIfMissing(Connection conn)
      throws BatchUpdateException, SQLException {

    // Create the functions.
    executeBatchBeforeReady(conn, VERSION_2_FUNCTION_CREATE_QUERIES);
  }

  /**
   * Deletes a database table if it does exist.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param tableName
   *          A String with the name of the table to delete, if present.
   * @param tableDropSql
   *          A String with the SQL code used to drop the table, if present.
   * @return <code>true</code> if the table did exist and it was removed,
   *         <code>false</code> otherwise.
   * @throws BatchUpdateException
   *           if a batch update exception occurred.
   * @throws SQLException
   *           if any other problem occurred accessing the database.
   */
  private boolean removeTableIfPresent(Connection conn, String tableName,
      String tableDropSql) throws BatchUpdateException, SQLException {
    final String DEBUG_HEADER = "removeTableIfPresent(): ";

    if (conn == null) {
      throw new NullPointerException("Null connection.");
    }

    // Check whether the table needs to be removed.
    if (tableExistsBeforeReady(conn, tableName)) {
      // Yes: Delete it.
      log.debug2(DEBUG_HEADER + "Dropping table '" + tableName + "'...");
      log.debug2(DEBUG_HEADER + "tableDropSql = '" + tableDropSql + "'.");

      executeBatchBeforeReady(conn, new String[] { tableDropSql });
      return true;
    } else {
      // No.
      log.debug2(DEBUG_HEADER + "Table '" + tableName
	  + "' does not exist - Not dropping it.");
      return false;
    }
  }

  /**
   * Creates a database table if it does not exist to be used during
   * initialization.
   * 
   * @param conn
   *          A connection with the database connection to be used.
   * @param tableName
   *          A String with the name of the table to create, if missing.
   * @param tableCreateSql
   *          A String with the SQL code used to create the table, if missing.
   * @return <code>true</code> if the table did not exist and it was created,
   *         <code>false</code> otherwise.
   * @throws BatchUpdateException
   *           if a batch update exception occurred.
   * @throws SQLException
   *           if any other problem occurred accessing the database.
   */
  // TODO: If the table exists, verify that it matches the table to be created.
  private boolean createTableIfMissingBeforeReady(Connection conn,
      String tableName, String tableCreateSql) throws BatchUpdateException,
      SQLException {
    final String DEBUG_HEADER = "createTableIfMissingBeforeReady(): ";

    if (conn == null) {
      throw new NullPointerException("Null connection.");
    }

    // Check whether the table needs to be created.
    if (!tableExistsBeforeReady(conn, tableName)) {
      // Yes: Create it.
      log.debug2(DEBUG_HEADER + "Creating table '" + tableName + "'...");
      log.debug2(DEBUG_HEADER + "tableCreateSql = '" + tableCreateSql + "'.");

      executeBatchBeforeReady(conn, new String[] { tableCreateSql });
      return true;
    } else {
      // No.
      log.debug2(DEBUG_HEADER + "Table '" + tableName
	  + "' exists - Not creating it.");
      return false;
    }
  }

  /**
   * Deletes a database function if it does exist to be used during
   * initialization.
   * 
   * @param conn
   *          A connection with the database connection to be used.
   * @param functionName
   *          A String with the name of the function to delete, if present.
   * @param functionDropSql
   *          A String with the SQL code used to drop the function, if present.
   * @return <code>true</code> if the function did exist and it was removed,
   *         <code>false</code> otherwise.
   * @throws BatchUpdateException
   *           if a batch update exception occurred.
   * @throws SQLException
   *           if any other problem occurred accessing the database.
   */
  private boolean removeFunctionIfPresentBeforeReady(Connection conn,
      String functionName, String functionDropSql)
	  throws BatchUpdateException, SQLException {
    final String DEBUG_HEADER = "removeFunctionIfPresentBeforeReady(): ";

    if (conn == null) {
      throw new NullPointerException("Null connection.");
    }

    // Check whether the function needs to be removed.
    if (functionExistsBeforeReady(conn, functionName)) {
      // Yes: Delete it.
      log.debug2(DEBUG_HEADER + "Dropping function '" + functionName + "'...");
      log.debug2(DEBUG_HEADER + "functionDropSql = '" + functionDropSql + "'.");

      executeBatchBeforeReady(conn, new String[] { functionDropSql });
      return true;
    } else {
      // No.
      log.debug2(DEBUG_HEADER + "Function '" + functionName
	  + "' does not exist - Not dropping it.");
      return false;
    }
  }

  /**
   * Executes a batch of statements to be used during initialization.
   * 
   * @param conn
   *          A connection with the database connection to be used.
   * @param stmts
   *          A String[] with the statements to be executed.
   * @throws BatchUpdateException
   *           if a batch update exception occurred.
   * @throws SQLException
   *           if any other problem occurred accessing the database.
   */
  private void executeBatchBeforeReady(Connection conn, String[] stmts)
      throws BatchUpdateException, SQLException {
    if (conn == null) {
      throw new NullPointerException("Null connection.");
    }

    Statement statement = null;

    try {
      statement = conn.createStatement();
      for (String stmt : stmts) {
	statement.addBatch(stmt);
      }
      statement.executeBatch();
    } finally {
      safeCloseStatement(statement);
    }
  }

  /**
   * Provides an indication of whether a function exists to be used during
   * initialization.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param functionName
   *          A String with name of the function to be checked.
   * @return <code>true</code> if the named function exists, <code>false</code>
   *         otherwise.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private boolean functionExistsBeforeReady(Connection conn,
      String functionName) throws SQLException {
    if (conn == null) {
      throw new NullPointerException("Null connection.");
    }

    ResultSet resultSet = null;

    try {
      // Get the database schema function data.
      resultSet =
	  conn.getMetaData().getFunctions(null, datasourceUser, null);

      // Loop through each function.
      while (resultSet.next()) {
	if (functionName.toUpperCase().equals(resultSet
	                                      .getString(FUNCTION_NAME))) {
	  // Found the function: No need to check further.
	  return true;
	}
      }
    } finally {
      safeCloseResultSet(resultSet);
    }

    // The function does not exist.
    return false;
  }

  /**
   * Records in the database the database version.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param version
   *          An int with version to be recorded.
   * @return an int with the number of database rows recorded.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private int recordDbVersion(Connection conn, int version)
      throws SQLException {
    final String DEBUG_HEADER = "recordDbVersion(): ";

    // Try to update the version.
    int updatedCount = updateDbVersion(conn, version);
    log.debug3(DEBUG_HEADER + "updatedCount = " + updatedCount);

    // Check whether the update was successful.
    if (updatedCount > 0) {
      // Yes: Done.
      return updatedCount;
    }

    // No: Add the version.
    int addedCount = addDbVersion(conn, version);
    log.debug3(DEBUG_HEADER + "addedCount = " + addedCount);

    return addedCount;
  }

  /**
   * Updates in the database the database version.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param version
   *          An int with version to be updated.
   * @return an int with the number of database rows updated.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private int updateDbVersion(Connection conn, int version)
      throws SQLException {
    final String DEBUG_HEADER = "updateDbVersion(): ";
    int updatedCount = 0;
    PreparedStatement updateVersion =
	prepareStatementBeforeReady(conn, UPDATE_DB_VERSION_QUERY);

    try {
      updateVersion.setShort(1, (short)version);
      updateVersion.setString(2, DATABASE_VERSION_TABLE_SYSTEM);
      updatedCount = executeUpdateBeforeReady(updateVersion);
    } finally {
      SqlDbManager.safeCloseStatement(updateVersion);
    }

    log.debug3(DEBUG_HEADER + "updatedCount = " + updatedCount);
    return updatedCount;
  }

  /**
   * Adds to the database the database version.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param version
   *          An int with version to be updated.
   * @return an int with the number of database rows added.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private int addDbVersion(Connection conn, int version) throws SQLException {
    final String DEBUG_HEADER = "addDbVersion(): ";
    int addedCount = 0;
    PreparedStatement insertVersion =
	prepareStatementBeforeReady(conn, INSERT_DB_VERSION_QUERY);

    try {
      insertVersion.setString(1, DATABASE_VERSION_TABLE_SYSTEM);
      insertVersion.setShort(2, (short)version);
      addedCount = executeUpdateBeforeReady(insertVersion);
    } finally {
      SqlDbManager.safeCloseStatement(insertVersion);
    }

    log.debug3(DEBUG_HEADER + "addedCount = " + addedCount);
    return addedCount;
  }

  /**
   * Provides an indication of whether this object is ready to be used.
   * 
   * @return <code>true</code> if this object is ready to be used,
   *         <code>false</code> otherwise.
   */
  public boolean isReady() {
    return ready;
  }

  /**
   * Provides a database connection using the datasource, retrying the operation
   * in case of transient failures.
   * <p />
   * Autocommit is disabled to allow the client code to manage transactions.
   * 
   * @return a Connection with the database connection to be used.
   * @throws SQLException
   *           if this object is not ready or any problem occurred accessing the
   *           database.
   */
  public Connection getConnection() throws SQLException {
    return getConnection(maxRetryCount, retryDelay);
  }

  /**
   * Provides a database connection using the datasource, retrying the operation
   * in case of transient failures.
   * <p />
   * Autocommit is disabled to allow the client code to manage transactions.
   * 
   * @param maxRetryCount
   *          An int with the maximum number of retries to be attempted.
   * @param retryDelay
   *          A long with the number of milliseconds to wait between
   *          consecutive retries.
   * @return a Connection with the database connection to be used.
   * @throws SQLException
   *           if this object is not ready or any problem occurred accessing the
   *           database.
   */
  public Connection getConnection(int maxRetryCount, long retryDelay)
      throws SQLException {
    if (!ready) {
      throw new SQLException("SqlDbManager has not been initialized.");
    }

    return getConnectionBeforeReady(maxRetryCount, retryDelay);
  }

  /**
   * Commits a connection or rolls it back if it's not possible.
   * 
   * @param conn
   *          A connection with the database connection to be committed.
   * @param logger
   *          A Logger used to report errors.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  public static void commitOrRollback(Connection conn, Logger logger)
      throws SQLException {
    try {
      conn.commit();
    } catch (SQLException sqle) {
      logger.error("Exception caught committing the connection", sqle);
      safeRollbackAndClose(conn);
      throw sqle;
    }
  }

  /**
   * Creates a database table if it does not exist.
   * 
   * @param conn
   *          A connection with the database connection to be used.
   * @param tableName
   *          A String with the name of the table to create, if missing.
   * @param tableCreateSql
   *          A String with the SQL code used to create the table, if missing.
   * @return <code>true</code> if the table did not exist and it was created,
   *         <code>false</code> otherwise.
   * @throws BatchUpdateException
   *           if a batch update exception occurred.
   * @throws SQLException
   *           if this object is not ready or any other problem occurred
   *           accessing the database.
   */
  public boolean createTableIfMissing(Connection conn, String tableName,
      String tableCreateSql) throws BatchUpdateException, SQLException {
    if (!ready) {
      throw new SQLException("SqlDbManager has not been initialized.");
    }

    return createTableIfMissingBeforeReady(conn, tableName, tableCreateSql);
  }

  /**
   * Executes a batch of statements.
   * 
   * @param conn
   *          A connection with the database connection to be used.
   * @param stmts
   *          A String[] with the statements to be executed.
   * @throws BatchUpdateException
   *           if a batch update exception occurred.
   * @throws SQLException
   *           if this object is not ready or any other problem occurred
   *           accessing the database.
   */
  public void executeBatch(Connection conn, String[] stmts)
      throws BatchUpdateException, SQLException {
    if (!ready) {
      throw new SQLException("SqlDbManager has not been initialized.");
    }

    executeBatchBeforeReady(conn, stmts);
  }

  /**
   * Provides the database version.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @return an int with the database version.
   * @throws SQLException
   *           if this object is not ready or any problem occurred accessing the
   *           database.
   */
  public int getDatabaseVersion(Connection conn) throws SQLException {
    if (!ready) {
      throw new SQLException("SqlDbManager has not been initialized.");
    }

    return getDatabaseVersion(conn);
  }

  /**
   * Writes the named table schema to the log. For debugging purposes only.
   * 
   * @param conn
   *          A connection with the database connection to be used.
   * @param tableName
   *          A String with name of the table to log.
   * @throws SQLException
   *           if this object is not ready or any problem occurred accessing the
   *           database.
   */
  public void logTableSchema(Connection conn, String tableName)
      throws SQLException {
    if (!ready) {
      throw new SQLException("SqlDbManager has not been initialized.");
    }

    if (conn == null) {
      throw new NullPointerException("Null connection.");
    }

    if (!tableExists(conn, tableName)) {
      log.debug("Table '" + tableName + "' does not exist.");
      return;
    }

    // Do nothing more if the current log level is not appropriate.
    if (!log.isDebug()) {
      return;
    }

    String columnName = null;
    String padding = "                               ";
    ResultSet resultSet = null;

    try {
      // Get the table column data.
      resultSet =
	  conn.getMetaData().getColumns(null, datasourceUser,
	      tableName.toUpperCase(), null);

      log.debug("Table Name : " + tableName);
      log.debug("Column" + padding.substring(0, 32 - "Column".length())
	  + "\tsize\tDataType");

      // Loop through each column.
      while (resultSet.next()) {
	// Output the column data.
	StringBuilder sb = new StringBuilder();
	columnName = resultSet.getString(COLUMN_NAME);
	sb.append(columnName);
	sb.append(padding.substring(0, 32 - columnName.length()));
	sb.append("\t");
	sb.append(resultSet.getString(COLUMN_SIZE));
	sb.append(" \t");
	sb.append(resultSet.getString(TYPE_NAME));
	log.debug(sb.toString());
      }
    } finally {
      safeCloseResultSet(resultSet);
    }
  }

  /**
   * Closes a result set without throwing exceptions.
   * 
   * @param resultSet
   *          A ResultSet with the database result set to be closed.
   */
  public static void safeCloseResultSet(ResultSet resultSet) {
    if (resultSet != null) {
      try {
	resultSet.close();
      } catch (SQLException sqle) {
	log.error("Cannot close result set", sqle);
      }
    }
  }

  /**
   * Closes a statement without throwing exceptions.
   * 
   * @param statement
   *          A Statement with the database statement to be closed.
   */
  public static void safeCloseStatement(Statement statement) {
    if (statement != null) {
      try {
	statement.close();
      } catch (SQLException sqle) {
	log.error("Cannot close statement", sqle);
      }
    }
  }

  /**
   * Closes a connection without throwing exceptions.
   * 
   * @param conn
   *          A Connection with the database connection to be closed.
   */
  public static void safeCloseConnection(Connection conn) {
    try {
      if ((conn != null) && !conn.isClosed()) {
	conn.close();
      }
    } catch (SQLException sqle) {
      log.error("Cannot close connection", sqle);
    }
  }

  /**
   * Rolls back and closes a connection without throwing exceptions.
   * 
   * @param conn
   *          A Connection with the database connection to be rolled back and
   *          closed.
   */
  public static void safeRollbackAndClose(Connection conn) {
    // Roll back the connection.
    try {
      if ((conn != null) && !conn.isClosed()) {
	conn.rollback();
      }
    } catch (SQLException sqle) {
      log.error("Cannot roll back the connection", sqle);
    }
    // Close it.
    safeCloseConnection(conn);
  }

  /**
   * Provides an indication of whether a table exists.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param tableName
   *          A String with name of the table to be checked.
   * @return <code>true</code> if the named table exists, <code>false</code>
   *         otherwise.
   * @throws SQLException
   *           if this object is not ready or any problem occurred accessing the
   *           database.
   */
  public boolean tableExists(Connection conn, String tableName)
      throws SQLException {
    if (!ready) {
      throw new SQLException("SqlDbManager has not been initialized.");
    }

    return tableExistsBeforeReady(conn, tableName);
  }

  /**
   * Stops the SqlDbManager service.
   */
  @Override
  public void stopService() {
    final String DEBUG_HEADER = "stopService(): ";

    try {
      if (networkServerControl != null) {
	networkServerControl.shutdown();
      }

      // Create the datasource for shutdown.
      dataSource = createDataSource();
    } catch (Exception e) {
      log.error("Cannot create the datasource to shutdown the database", e);
      return;
    }

    dataSourceConfig.remove("createDatabase");
    dataSourceConfig.put("shutdownDatabase", "shutdown");

    // Check whether the datasource properties have been successfully
    // initialized.
    if (initializeDataSourceProperties()) {
      try {
	getConnection();
      } catch (SQLException sqle) {
	// This is expected.
	log.debug2(DEBUG_HEADER + "Expected exception caught: " + sqle);
      }
      log.debug(DEBUG_HEADER + "Database shutdown.");
    } else {
      log.error("Failed database shutdown.");
    }

    ready = false;
    dataSource = null;
  }

  /**
   * Provides the datasource configuration.
   * 
   * @return a Configuration with the datasource configuration parameters.
   */
  private Configuration getDataSourceConfig() {
    final String DEBUG_HEADER = "getDataSourceConfig(): ";

    // Get the current configuration.
    Configuration currentConfig = ConfigManager.getCurrentConfig();

    // Create the datasource configuration.
    Configuration dsConfig = ConfigManager.newConfiguration();

    // Populate it from the current configuration datasource tree.
    dsConfig.copyFrom(currentConfig.getConfigTree(DATASOURCE_ROOT));

    // Save the default class name, if not configured.
    dsConfig.put("className", currentConfig.get(
	PARAM_DATASOURCE_CLASSNAME, DEFAULT_DATASOURCE_CLASSNAME));

    // Save the creation directive, if not configured.
    dsConfig.put("createDatabase", currentConfig.get(
	PARAM_DATASOURCE_CREATEDATABASE, DEFAULT_DATASOURCE_CREATEDATABASE));

    // Check whether the configured datasource database name does not exist.
    if (dsConfig.get("databaseName") == null) {
      // Yes: Get the data source root directory.
      File datasourceDir =
	  ConfigManager.getConfigManager()
	      .findConfiguredDataDir(PARAM_DATASOURCE_DATABASENAME,
				     DEFAULT_DATASOURCE_DATABASENAME, false);

      // Save the database name.
      dsConfig.put("databaseName", FileUtil
	  .getCanonicalOrAbsolutePath(datasourceDir));
      log.debug2(DEBUG_HEADER + "datasourceDatabaseName = '"
	  + dsConfig.get("databaseName") + "'.");
    }

    // Save the port number, if not configured.
    dsConfig.put("portNumber", currentConfig.get(
	PARAM_DATASOURCE_PORTNUMBER, DEFAULT_DATASOURCE_PORTNUMBER));

    // Save the server name, if not configured.
    dsConfig.put("serverName", currentConfig.get(
	PARAM_DATASOURCE_SERVERNAME, DEFAULT_DATASOURCE_SERVERNAME));

    // Save the user name, if not configured.
    dsConfig.put("user",
	currentConfig.get(PARAM_DATASOURCE_USER, DEFAULT_DATASOURCE_USER));
    datasourceUser = dsConfig.get("user");

    return dsConfig;
  }

  /**
   * Creates a datasource using the specified configuration.
   * 
   * @return <code>true</code> if created, <code>false</code> otherwise.
   * @throws Exception
   *           if the datasource could not be created.
   */
  private DataSource createDataSource() throws Exception {
    // Get the datasource class name.
    String dataSourceClassName = dataSourceConfig.get("className");
    Class<?> dataSourceClass;

    // Locate the datasource class.
    try {
      dataSourceClass = Class.forName(dataSourceClassName);
    } catch (Throwable t) {
      throw new Exception("Cannot locate datasource class '"
	  + dataSourceClassName + "'", t);
    }

    // Create the datasource.
    try {
      return ((DataSource) dataSourceClass.newInstance());
    } catch (ClassCastException cce) {
      throw new Exception("Class '" + dataSourceClassName
	  + "' is not a DataSource.", cce);
    } catch (Throwable t) {
      throw new Exception("Cannot create instance of datasource class '"
	  + dataSourceClassName + "'", t);
    }
  }

  /**
   * Initializes the properties of the datasource using the specified
   * configuration.
   * 
   * @return <code>true</code> if successfully initialized, <code>false</code>
   *         otherwise.
   */
  private boolean initializeDataSourceProperties() {
    final String DEBUG_HEADER = "initializeDataSourceProperties(): ";

    String dataSourceClassName = dataSourceConfig.get("className");
    log.debug2(DEBUG_HEADER + "dataSourceClassName = '" + dataSourceClassName
	+ "'.");
    boolean errors = false;
    String value = null;

    // Loop through all the configured datasource properties.
    for (String key : dataSourceConfig.keySet()) {
      log.debug2(DEBUG_HEADER + "key = '" + key + "'.");

      // Skip over the class name, as it is not really part of the datasource
      // definition.
      if (!"className".equals(key)) {
	// Get the property value.
	value = dataSourceConfig.get(key);
	log.debug2(DEBUG_HEADER + "value = '" + value + "'.");

	// Set the property value in the datasource.
	try {
	  BeanUtils.setProperty(dataSource, key, value);
	} catch (Throwable t) {
	  errors = true;
	  log.error("Cannot set value '" + value + "' for property '" + key
	      + "' for instance of datasource class '" + dataSourceClassName
	      + "' - Instance of datasource class not initialized", t);
	}
      }
    }

    return !errors;
  }

  /**
   * Starts the Derby NetworkServerControl and waits for it to be ready.
   * 
   * @return <code>true</code> if the Derby NetworkServerControl is started and
   *         ready, <code>false</code> otherwise.
   * @throws Exception
   *           if the network server control could not be started.
   */
  private boolean startNetworkServerControl() throws Exception {
    final String DEBUG_HEADER = "startNetworkServerControl(): ";

    ClientDataSource cds = (ClientDataSource) dataSource;
    String serverName = cds.getServerName();
    log.debug2(DEBUG_HEADER + "serverName = '" + serverName + "'.");
    int serverPort = cds.getPortNumber();
    log.debug2(DEBUG_HEADER + "serverPort = " + serverPort + ".");

    // Start the network server control.
    InetAddress inetAddr = InetAddress.getByName(serverName);
    networkServerControl =
	new NetworkServerControl(inetAddr, serverPort);
    networkServerControl.start(null);

    // Wait for the network server control to be ready.
    for (int i = 0; i < 40; i++) { // At most 20 seconds.
      try {
	networkServerControl.ping();
	log.debug(DEBUG_HEADER + "Remote access to Derby database enabled");
	return true;
      } catch (Exception e) {
	// Control is not ready: wait and try again.
	try {
	  Deadline.in(500).sleep(); // About 1/2 second.
	} catch (InterruptedException ie) {
	  break;
	}
      }
    }

    log.error("Cannot enable remote access to Derby database");
    return false;
  }

  /**
   * Provides the query used to drop a function.
   * 
   * @param functionName
   *          A string with the name of the function to be dropped.
   * @return a String with the query used to drop the function.
   */
  private static String dropFunctionQuery(String functionName) {
    return "drop function " + functionName;
  }

  /**
   * Provides the query used to drop a table.
   * 
   * @param tableName
   *          A string with the name of the table to be dropped.
   * @return a String with the query used to drop the table.
   */
  private static String dropTableQuery(String tableName) {
    return "drop table " + tableName;
  }

  /**
   * Sets the version of the database that is the upgrade target of this daemon.
   * 
   * @param version
   *          An int with the target version of the database.
   */
  void setTargetDatabaseVersion(int version) {
    targetDatabaseVersion = version;
  }

  /**
   * Sets up the database for a given version.
   * 
   * @param finalVersion
   *          An int with the version of the database to be set up.
   * @return <code>true</code> if the database was successfully set up,
   *         <code>false</code> otherwise.
   */
  boolean setUpDatabase(int finalVersion) {
    final String DEBUG_HEADER = "setUpDatabase(): ";
    log.debug2(DEBUG_HEADER + "finalVersion = " + finalVersion);

    // Do nothing to set up a non-existent database.
    if (finalVersion < 1) {
      return true;
    }

    // Do nothing more if the database infrastructure cannot be setup.
    if (!setUpInfrastructure()) {
      return false;
    }

    boolean success = false;
    Connection conn = null;

    try {
      conn = getConnectionBeforeReady();

      // Check whether the version 1 set up was successful.
      if (setUpDatabaseVersion1(conn)) {
	// Yes: Update the database to the final version.
	success = updateDatabase(conn, 1, finalVersion);
      }

      log.debug2(DEBUG_HEADER + "Database update Success? = " + success);
    } catch (BatchUpdateException bue) {
      log.error("Error updating database", bue);
      return success;
    } catch (SQLException sqle) {
      log.error("Error updating database", sqle);
      return success;
    } finally {
      if (success) {
	try {
	  conn.commit();
	  SqlDbManager.safeCloseConnection(conn);
	} catch (SQLException sqle) {
	  log.error("Exception caught committing the connection", sqle);
	  SqlDbManager.safeRollbackAndClose(conn);
	  success = false;
	}
      } else {
	SqlDbManager.safeRollbackAndClose(conn);
      }
    }

    return success;
  }

  /**
   * Sets up the database to version 1.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @return <code>true</code> if the database was successfully set up,
   *         <code>false</code> otherwise.
   * @throws BatchUpdateException
   *           if a batch update exception occurred.
   * @throws SQLException
   *           if any other problem occurred accessing the database.
   */
  private boolean setUpDatabaseVersion1(Connection conn)
      throws BatchUpdateException, SQLException {
    // Create the necessary tables if they do not exist.
    createVersion1TablesIfMissing(conn);

    // Create new functions.
    createVersion1FunctionsIfMissing(conn);
    
    return true;
  }

  /**
   * Creates all the necessary version 1 database tables if they do not exist.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @throws BatchUpdateException
   *           if a batch update exception occurred.
   * @throws SQLException
   *           if any other problem occurred accessing the database.
   */
  private void createVersion1TablesIfMissing(Connection conn)
      throws BatchUpdateException, SQLException {
    final String DEBUG_HEADER = "createVersion1TablesIfMissing(): ";

    // Loop through all the table names.
    for (String tableName : VERSION_1_TABLE_CREATE_QUERIES.keySet()) {
      log.debug2(DEBUG_HEADER + "Checking table = " + tableName);

      // Create the table if it does not exist.
      createTableIfMissingBeforeReady(conn, tableName,
                                      VERSION_1_TABLE_CREATE_QUERIES
                                      	.get(tableName));
    }
  }

  /**
   * Creates all the necessary version 1 database functions if they do not
   * exist.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @throws BatchUpdateException
   *           if a batch update exception occurred.
   * @throws SQLException
   *           if any other problem occurred accessing the database.
   */
  private void createVersion1FunctionsIfMissing(Connection conn)
      throws BatchUpdateException, SQLException {

    // Create the functions.
    executeBatchBeforeReady(conn, VERSION_1_FUNCTION_CREATE_QUERIES);
  }

  /**
   * Provides a version of a text truncated to a maximum length, if necessary,
   * including an indication of the truncation.
   * 
   * @param original
   *          A String with the original text to be truncated, if necessary.
   * @param maxLength
   *          An int with the maximum length of the truncated text to be
   *          provided.
   * @return a String with the original text if it is not longer than the
   *         maximum length allowed or the truncated text including an
   *         indication of the truncation.
   */
  public static String truncateVarchar(String original, int maxLength) {
    if (original.length() <= maxLength) {
      return original;
    }

    return original.substring(0, maxLength - TRUNCATION_INDICATOR.length())
	+ TRUNCATION_INDICATOR;
  }

  /**
   * Provides an indication of whether a text has been truncated.
   * 
   * @param text
   *          A String with the text to be evaluated for truncation.
   * @return <code>true</code> if the text has been truncated,
   *         <code>false</code> otherwise.
   */
  public static boolean isTruncatedVarchar(String text) {
    return text.endsWith(TRUNCATION_INDICATOR);
  }

  /**
   * Executes a querying prepared statement, retrying the execution in case of
   * transient failures.
   * 
   * @param statement
   *          A PreparedStatement with the querying prepared statement to be
   *          executed.
   * @return a ResultSet with the results of the query.
   */
  public ResultSet executeQuery(PreparedStatement statement)
      throws SQLException {
    return executeQuery(statement, maxRetryCount, retryDelay);
  }

  /**
   * Executes a querying prepared statement, retrying the execution in case of
   * transient failures.
   * 
   * @param statement
   *          A PreparedStatement with the querying prepared statement to be
   *          executed.
   * @param maxRetryCount
   *          An int with the maximum number of retries to be attempted.
   * @param retryDelay
   *          A long with the number of milliseconds to wait between
   *          consecutive retries.
   * @return a ResultSet with the results of the query.
   */
  public ResultSet executeQuery(PreparedStatement statement, int maxRetryCount,
      long retryDelay) throws SQLException {
    if (!ready) {
      throw new SQLException("SqlDbManager has not been initialized.");
    }

    return executeQueryBeforeReady(statement, maxRetryCount, retryDelay);
  }

  /**
   * Executes a querying prepared statement, retrying the execution in case of
   * transient failures.
   * 
   * @param statement
   *          A PreparedStatement with the querying prepared statement to be
   *          executed.
   * @return a ResultSet with the results of the query.
   */
  private ResultSet executeQueryBeforeReady(PreparedStatement statement)
      throws SQLException {
    return executeQueryBeforeReady(statement, maxRetryCount, retryDelay);
  }

  /**
   * Executes a querying prepared statement, retrying the execution in case of
   * transient failures.
   * 
   * @param statement
   *          A PreparedStatement with the querying prepared statement to be
   *          executed.
   * @param maxRetryCount
   *          An int with the maximum number of retries to be attempted.
   * @param retryDelay
   *          A long with the number of milliseconds to wait between
   *          consecutive retries.
   * @return a ResultSet with the results of the query.
   */
  private ResultSet executeQueryBeforeReady(PreparedStatement statement,
      int maxRetryCount, long retryDelay) throws SQLException {
    final String DEBUG_HEADER = "executeQueryBeforeReady(): ";

    boolean success = false;
    int retryCount = 0;
    ResultSet results = null;

    // Keep trying until success.
    while (!success) {
      try {
	results = statement.executeQuery();
	success = true;
      } catch (SQLTransientException sqltre) {
	// A SQLTransientException is caught: Count the next retry.
	retryCount++;

	// Check whether the next retry would go beyond the specified maximum
	// number of retries.
	if (retryCount > maxRetryCount) {
	  // Yes: Report the failure.
	  log.error("Transient exception caught", sqltre);
	  log.error("Maximum retry count of " + maxRetryCount + " reached.");
	  throw sqltre;
	} else {
	  // No: Wait for the specified amount of time before attempting the
	  // next retry.
	  log.debug(DEBUG_HEADER + "Exception caught", sqltre);
	  log.debug(DEBUG_HEADER + "Waiting "
		    + StringUtil.timeIntervalToString(retryDelay)
		    + " before retry number " + retryCount + "...");

	  try {
	    Deadline.in(retryDelay).sleep();
	  } catch (InterruptedException ie) {
	    // Continue with the next retry.
	  }
	}
      }
    }

    return results;
  }

  /**
   * Executes an updating prepared statement, retrying the execution in case of
   * transient failures.
   * 
   * @param statement
   *          A PreparedStatement with the updating prepared statement to be
   *          executed.
   * @return an int with the number of database rows updated.
   */
  public int executeUpdate(PreparedStatement statement) throws SQLException {
    return executeUpdate(statement, maxRetryCount, retryDelay);
  }

  /**
   * Executes an updating prepared statement, retrying the execution in case of
   * transient failures.
   * 
   * @param statement
   *          A PreparedStatement with the updating prepared statement to be
   *          executed.
   * @param maxRetryCount
   *          An int with the maximum number of retries to be attempted.
   * @param retryDelay
   *          A long with the number of milliseconds to wait between
   *          consecutive retries.
   * @return an int with the number of database rows updated.
   */
  public int executeUpdate(PreparedStatement statement, int maxRetryCount,
      long retryDelay) throws SQLException {
    if (!ready) {
      throw new SQLException("SqlDbManager has not been initialized.");
    }

    return executeUpdateBeforeReady(statement, maxRetryCount, retryDelay);
  }

  /**
   * Executes an updating prepared statement, retrying the execution in case of
   * transient failures.
   * 
   * @param statement
   *          A PreparedStatement with the updating prepared statement to be
   *          executed.
   * @return an int with the number of database rows updated.
   */
  private int executeUpdateBeforeReady(PreparedStatement statement)
      throws SQLException {
    return executeUpdateBeforeReady(statement, maxRetryCount, retryDelay);
  }

  /**
   * Executes an updating prepared statement, retrying the execution in case of
   * transient failures.
   * 
   * @param statement
   *          A PreparedStatement with the updating prepared statement to be
   *          executed.
   * @param maxRetryCount
   *          An int with the maximum number of retries to be attempted.
   * @param retryDelay
   *          A long with the number of milliseconds to wait between
   *          consecutive retries.
   * @return an int with the number of database rows updated.
   */
  private int executeUpdateBeforeReady(PreparedStatement statement,
      int maxRetryCount, long retryDelay) throws SQLException {
    final String DEBUG_HEADER = "executeUpdateBeforeReady(): ";

    boolean success = false;
    int retryCount = 0;
    int updatedCount = 0;

    // Keep trying until success.
    while (!success) {
      try {
	updatedCount = statement.executeUpdate();
	success = true;
      } catch (SQLTransientException sqltre) {
	// A SQLTransientException is caught: Count the next retry.
	retryCount++;

	// Check whether the next retry would go beyond the specified maximum
	// number of retries.
	if (retryCount > maxRetryCount) {
	  // Yes: Report the failure.
	  log.error("Transient exception caught", sqltre);
	  log.error("Maximum retry count of " + maxRetryCount + " reached.");
	  throw sqltre;
	} else {
	  // No: Wait for the specified amount of time before attempting the
	  // next retry.
	  log.debug(DEBUG_HEADER + "Exception caught", sqltre);
	  log.debug(DEBUG_HEADER + "Waiting "
		    + StringUtil.timeIntervalToString(retryDelay)
		    + " before retry number " + retryCount + "...");
	  try {
	    Deadline.in(retryDelay).sleep();
	  } catch (InterruptedException ie) {
	    // Continue with the next retry.
	  }
	}
      }
    }

    return updatedCount;
  }

  /**
   * Prepares a statement, retrying the preparation in case of transient
   * failures.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param sql
   *          A String with the prepared statement SQL query.
   * @return a PreparedStatement with the prepared statement.
   */
  public PreparedStatement prepareStatement(Connection conn, String sql)
      throws SQLException {
    return prepareStatement(conn, sql, maxRetryCount, retryDelay);
  }

  /**
   * Prepares a statement, retrying the preparation in case of transient
   * failures.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param sql
   *          A String with the prepared statement SQL query.
   * @param maxRetryCount
   *          An int with the maximum number of retries to be attempted.
   * @param retryDelay
   *          A long with the number of milliseconds to wait between consecutive
   *          retries.
   * @return a PreparedStatement with the prepared statement.
   */
  public PreparedStatement prepareStatement(Connection conn, String sql,
      int maxRetryCount, long retryDelay) throws SQLException {
    if (!ready) {
      throw new SQLException("SqlDbManager has not been initialized.");
    }

    return prepareStatementBeforeReady(conn, sql, maxRetryCount, retryDelay);
  }

  /**
   * Prepares a statement, retrying the preparation in case of transient
   * failures.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param sql
   *          A String with the prepared statement SQL query.
   * @return a PreparedStatement with the prepared statement.
   */
  private PreparedStatement prepareStatementBeforeReady(Connection conn,
      String sql) throws SQLException {
    return prepareStatementBeforeReady(conn, sql, maxRetryCount, retryDelay);
  }

  /**
   * Prepares a statement, retrying the preparation in case of transient
   * failures.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param sql
   *          A String with the prepared statement SQL query.
   * @param maxRetryCount
   *          An int with the maximum number of retries to be attempted.
   * @param retryDelay
   *          A long with the number of milliseconds to wait between consecutive
   *          retries.
   * @return a PreparedStatement with the prepared statement.
   */
  private PreparedStatement prepareStatementBeforeReady(Connection conn,
      String sql, int maxRetryCount, long retryDelay) throws SQLException {
    final String DEBUG_HEADER = "prepareStatementBeforeReady(): ";

    boolean success = false;
    int retryCount = 0;
    PreparedStatement statement = null;

    // Keep trying until success.
    while (!success) {
      try {
	statement = conn.prepareStatement(sql);
	success = true;
      } catch (SQLTransientException sqltre) {
	// A SQLTransientException is caught: Count the next retry.
	retryCount++;

	// Check whether the next retry would go beyond the specified maximum
	// number of retries.
	if (retryCount > maxRetryCount) {
	  // Yes: Report the failure.
	  log.error("Transient exception caught", sqltre);
	  log.error("Maximum retry count of " + maxRetryCount + " reached.");
	  throw sqltre;
	} else {
	  // No: Wait for the specified amount of time before attempting the
	  // next retry.
	  log.debug(DEBUG_HEADER + "Exception caught", sqltre);
	  log.debug(DEBUG_HEADER + "Waiting "
	      + StringUtil.timeIntervalToString(retryDelay)
	      + " before retry number " + retryCount + "...");
	  try {
	    Deadline.in(retryDelay).sleep();
	  } catch (InterruptedException ie) {
	    // Continue with the next retry.
	  }
	}
      }
    }

    return statement;
  }

  /**
   * Prepares a statement, retrying the preparation in case of transient
   * failures.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param sql
   *          A String with the prepared statement SQL query.
   * @param returnGeneratedKeys
   *          An int indicating that generated keys should not be made available
   *          for retrieval.
   * @return a PreparedStatement with the prepared statement.
   */
  public PreparedStatement prepareStatement(Connection conn, String sql,
      int returnGeneratedKeys) throws SQLException {
    return prepareStatement(conn, sql, returnGeneratedKeys, maxRetryCount,
	retryDelay);
  }

  /**
   * Prepares a statement, retrying the preparation in case of transient
   * failures.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param sql
   *          A String with the prepared statement SQL query.
   * @param returnGeneratedKeys
   *          An int indicating that generated keys should not be made available
   *          for retrieval.
   * @param maxRetryCount
   *          An int with the maximum number of retries to be attempted.
   * @param retryDelay
   *          A long with the number of milliseconds to wait between consecutive
   *          retries.
   * @return a PreparedStatement with the prepared statement.
   */
  public PreparedStatement prepareStatement(Connection conn, String sql,
      int returnGeneratedKeys, int maxRetryCount, long retryDelay)
      throws SQLException {
    if (!ready) {
      throw new SQLException("SqlDbManager has not been initialized.");
    }

    return prepareStatementBeforeReady(conn, sql, returnGeneratedKeys,
	maxRetryCount, retryDelay);
  }

  /**
   * Prepares a statement, retrying the preparation in case of transient
   * failures.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param sql
   *          A String with the prepared statement SQL query.
   * @param returnGeneratedKeys
   *          An int indicating that generated keys should not be made available
   *          for retrieval.
   * @return a PreparedStatement with the prepared statement.
   */
  private PreparedStatement prepareStatementBeforeReady(Connection conn,
      String sql, int returnGeneratedKeys) throws SQLException {
    return prepareStatementBeforeReady(conn, sql, returnGeneratedKeys,
	maxRetryCount, retryDelay);
  }

  /**
   * Prepares a statement, retrying the preparation in case of transient
   * failures.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param sql
   *          A String with the prepared statement SQL query.
   * @param returnGeneratedKeys
   *          An int indicating that generated keys should not be made available
   *          for retrieval.
   * @param maxRetryCount
   *          An int with the maximum number of retries to be attempted.
   * @param retryDelay
   *          A long with the number of milliseconds to wait between consecutive
   *          retries.
   * @return a PreparedStatement with the prepared statement.
   */
  private PreparedStatement prepareStatementBeforeReady(Connection conn,
      String sql, int returnGeneratedKeys, int maxRetryCount, long retryDelay)
      throws SQLException {
    final String DEBUG_HEADER = "prepareStatementBeforeReady(): ";

    boolean success = false;
    int retryCount = 0;
    PreparedStatement statement = null;

    // Keep trying until success.
    while (!success) {
      try {
	statement = conn.prepareStatement(sql, returnGeneratedKeys);
	success = true;
      } catch (SQLTransientException sqltre) {
	// A SQLTransientException is caught: Count the next retry.
	retryCount++;

	// Check whether the next retry would go beyond the specified maximum
	// number of retries.
	if (retryCount > maxRetryCount) {
	  // Yes: Report the failure.
	  log.error("Transient exception caught", sqltre);
	  log.error("Maximum retry count of " + maxRetryCount + " reached.");
	  throw sqltre;
	} else {
	  // No: Wait for the specified amount of time before attempting the
	  // next retry.
	  log.debug(DEBUG_HEADER + "Exception caught", sqltre);
	  log.debug(DEBUG_HEADER + "Waiting "
	      + StringUtil.timeIntervalToString(retryDelay)
	      + " before retry number " + retryCount + "...");
	  try {
	    Deadline.in(retryDelay).sleep();
	  } catch (InterruptedException ie) {
	    // Continue with the next retry.
	  }
	}
      }
    }

    return statement;
  }
}
