/*
 * $Id: AuMetadataRecorder.java,v 1.4 2013/01/14 21:58:19 fergaloy-sf Exp $
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

import static org.lockss.db.SqlDbManager.*;
import static org.lockss.metadata.MetadataManager.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.lockss.db.SqlDbManager;
import org.lockss.metadata.ArticleMetadataBuffer.ArticleMetadataInfo;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.Plugin;
import org.lockss.plugin.PluginManager;
import org.lockss.util.Logger;
import org.lockss.util.StringUtil;

/**
 * Writes to the database metadata related to an archival unit.
 */
public class AuMetadataRecorder {
  private static Logger log = Logger.getLogger(AuMetadataRecorder.class);

  private static final String ACCESS_URL_FEATURE = "Access";

  // Query to update the version of an Archival Unit metadata.
  private static final String UPDATE_AU_MD_QUERY = "update "
      + AU_MD_TABLE
      + " set " + MD_VERSION_COLUMN + " = ?"
      + " where " + AU_MD_SEQ_COLUMN + " = ?";

  // Query to find the name of the type of a metadata item.
  private static final String GET_MD_ITEM_TYPE_NAME_QUERY = "select "
      + "t." + TYPE_NAME_COLUMN
      + " from " + MD_ITEM_TYPE_TABLE + " t"
      + "," + MD_ITEM_TABLE + " m"
      + " where m." + MD_ITEM_SEQ_COLUMN + " = ?"
      + " and m." + MD_ITEM_TYPE_SEQ_COLUMN + " = t." + MD_ITEM_TYPE_SEQ_COLUMN;

  // Query to find a metadata item by its type, Archival Unit and access URL.
  private static final String FIND_MD_ITEM_QUERY = "select "
      + "m." + MD_ITEM_SEQ_COLUMN
      + " from " + MD_ITEM_TABLE + " m"
      + "," + URL_TABLE + " u"
      + " where m." + MD_ITEM_TYPE_SEQ_COLUMN + " = ?"
      + " and m." + AU_MD_SEQ_COLUMN + " = ?"
      + " and m." + MD_ITEM_SEQ_COLUMN + " = u." + MD_ITEM_SEQ_COLUMN
      + " and u." + FEATURE_COLUMN + " = '" + ACCESS_URL_FEATURE + "'"
      + " and u." + URL_COLUMN + " = ?";

  // Query to find the featured URLs of a metadata item.
  private static final String FIND_MD_ITEM_FEATURED_URL_QUERY = "select "
      + FEATURE_COLUMN + "," + URL_COLUMN
      + " from " + URL_TABLE
      + " where " + MD_ITEM_SEQ_COLUMN + " = ?";

  // Query to add a metadata item author.
  private static final String INSERT_AUTHOR_QUERY = "insert into "
      + AUTHOR_TABLE
      + "(" + MD_ITEM_SEQ_COLUMN
      + "," + AUTHOR_NAME_COLUMN
      + "," + AUTHOR_IDX_COLUMN
      + ") values (?,?,"
      + "(select coalesce(max(" + AUTHOR_IDX_COLUMN + "), 0) + 1"
      + " from " + AUTHOR_TABLE
      + " where " + MD_ITEM_SEQ_COLUMN + " = ?))";

  // Query to find the authors of a metadata item.
  private static final String FIND_MD_ITEM_AUTHOR_QUERY = "select "
      + AUTHOR_NAME_COLUMN
      + " from " + AUTHOR_TABLE
      + " where " + MD_ITEM_SEQ_COLUMN + " = ?";

  // Query to add a metadata item keyword.
  private static final String INSERT_KEYWORD_QUERY = "insert into "
      + KEYWORD_TABLE
      + "(" + MD_ITEM_SEQ_COLUMN
      + "," + KEYWORD_COLUMN
      + ") values (?,?)";

  // Query to find the keywords of a metadata item.
  private static final String FIND_MD_ITEM_KEYWORD_QUERY = "select "
      + KEYWORD_COLUMN
      + " from " + KEYWORD_TABLE
      + " where " + MD_ITEM_SEQ_COLUMN + " = ?";

  // Query to find the DOIs of a metadata item.
  private static final String FIND_MD_ITEM_DOI_QUERY = "select "
      + DOI_COLUMN
      + " from " + DOI_TABLE
      + " where " + MD_ITEM_SEQ_COLUMN + " = ?";

  // Query to add a bibliographic item.
  private static final String INSERT_BIB_ITEM_QUERY = "insert into "
      + BIB_ITEM_TABLE
      + "(" + MD_ITEM_SEQ_COLUMN
      + "," + VOLUME_COLUMN
      + "," + ISSUE_COLUMN
      + "," + START_PAGE_COLUMN
      + "," + END_PAGE_COLUMN
      + "," + ITEM_NO_COLUMN
      + ") values (?,?,?,?,?,?)";

  // Query to update a bibliographic item.
  private static final String UPDATE_BIB_ITEM_QUERY = "update "
      + BIB_ITEM_TABLE
      + " set " + VOLUME_COLUMN + " = ?"
      + "," + ISSUE_COLUMN + " = ?"
      + "," + START_PAGE_COLUMN + " = ?"
      + "," + END_PAGE_COLUMN + " = ?"
      + "," + ITEM_NO_COLUMN + " = ?"
      + " where " + MD_ITEM_SEQ_COLUMN + " = ?";

  // The calling task.
  private final ReindexingTask task;

  // The metadata manager.
  private final MetadataManager mdManager;

  // The database manager.
  private final SqlDbManager sqlDbManager;

  // The archival unit.
  private final ArchivalUnit au;

  // AU-related properties independent of the database.
  private final Plugin plugin;
  private final String platform;
  private final int pluginVersion;
  private final String auId;
  private final String auKey;

  // Database identifiers related to the AU. 
  private Long publisherSeq = null;
  private Long pluginSeq = null;
  private Long auSeq = null;
  private Long auMdSeq = null;

  /**
   * Constructor.
   * 
   * @param task A ReindexingTaskwith the calling task.
   * @param mdManager A MetadataManager with the metadata manager.
   * @param au An ArchivalUnit with the archival unit.
   */
  public AuMetadataRecorder(ReindexingTask task, MetadataManager mdManager,
      ArchivalUnit au) {
    this.task = task;
    this.mdManager = mdManager;
    sqlDbManager = mdManager.getDbManager();
    this.au = au;

    plugin = au.getPlugin();
    platform = plugin.getPublishingPlatform();
    pluginVersion = mdManager.getPluginMetadataVersionNumber(plugin);
    auId = au.getAuId();
    auKey = PluginManager.auKeyFromAuId(auId);
  }

  /**
   * Writes to the database metadata related to an archival unit.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param mditr
   *          An Iterator<ArticleMetadataInfo> with the metadata.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  public void recordMetadata(Connection conn,
      Iterator<ArticleMetadataInfo> mditr) throws SQLException {
    final String DEBUG_HEADER = "recordMetadata(): ";

    // Loop through the metadata for each article.
    while (mditr.hasNext()) {
      // Normalize all the metadata fields.
      ArticleMetadataInfo normalizedMdInfo = normalizeMetadata(mditr.next());

      // Store the metadata fields in the database.
      storeMetadata(conn, normalizedMdInfo);

      // Count the processed article.
      task.incrementUpdatedArticleCount();
      log.debug3(DEBUG_HEADER + "updatedArticleCount = "
	  + task.getUpdatedArticleCount());
    }

    if (auMdSeq != null) {
      // Update the AU last extraction timestamp.
      mdManager.updateAuLastExtractionTime(conn, auMdSeq);
    } else {
      log.warning("auMdSeq is null for auid = '" + au.getAuId() + "'.");
    }
  }

  /**
   * Normalizes metadata info fields.
   * 
   * @param mdinfo
   *          the ArticleMetadataInfo
   * @return an ArticleMetadataInfo with the normalized properties.
   */
  private ArticleMetadataInfo normalizeMetadata(ArticleMetadataInfo mdinfo) {
    final String DEBUG_HEADER = "normalizeMetadata(): ";
    if (mdinfo.accessUrl != null) {
      if (mdinfo.accessUrl.length() > MAX_URL_COLUMN) {
	log.warning("accessUrl too long '" + mdinfo.accessUrl
	    + "' for title: '" + mdinfo.journalTitle + "' publisher: "
	    + mdinfo.publisher + "'");
	mdinfo.accessUrl =
	    SqlDbManager.truncateVarchar(mdinfo.accessUrl, MAX_URL_COLUMN);
      }
    }

    if (mdinfo.isbn != null) {
      String isbn = mdinfo.isbn.replaceAll("-", "");
      log.debug3(DEBUG_HEADER + "isbn = '" + isbn + "'.");

      if (isbn.length() > MAX_ISBN_COLUMN) {
	log.warning("isbn too long '" + mdinfo.isbn + "' for title: '"
	    + mdinfo.journalTitle + "' publisher: " + mdinfo.publisher + "'");
	mdinfo.isbn = SqlDbManager.truncateVarchar(isbn, MAX_ISBN_COLUMN);
      } else {
	mdinfo.isbn = isbn;
      }
    }

    if (mdinfo.eisbn != null) {
      String isbn = mdinfo.eisbn.replaceAll("-", "");
      log.debug3(DEBUG_HEADER + "isbn = '" + isbn + "'.");

      if (isbn.length() > MAX_ISBN_COLUMN) {
	log.warning("eisbn too long '" + mdinfo.eisbn + "' for title: '"
	    + mdinfo.journalTitle + "' publisher: " + mdinfo.publisher + "'");
	mdinfo.eisbn = SqlDbManager.truncateVarchar(isbn, MAX_ISBN_COLUMN);
      } else {
	mdinfo.eisbn = isbn;
      }
    }

    if (mdinfo.issn != null) {
      String issn = mdinfo.issn.replaceAll("-", "");
      log.debug3(DEBUG_HEADER + "issn = '" + issn + "'.");

      if (issn.length() > MAX_ISSN_COLUMN) {
	log.warning("issn too long '" + mdinfo.issn + "' for title: '"
	    + mdinfo.journalTitle + "' publisher: " + mdinfo.publisher + "'");
	mdinfo.issn = SqlDbManager.truncateVarchar(issn, MAX_ISSN_COLUMN);
      } else {
	mdinfo.issn = issn;
      }
    }

    if (mdinfo.eissn != null) {
      String issn = mdinfo.eissn.replaceAll("-", "");
      log.debug3(DEBUG_HEADER + "issn = '" + issn + "'.");

      if (issn.length() > MAX_ISSN_COLUMN) {
	log.warning("issn too long '" + mdinfo.eissn + "' for title: '"
	    + mdinfo.journalTitle + "' publisher: " + mdinfo.publisher + "'");
	mdinfo.eissn = SqlDbManager.truncateVarchar(issn, MAX_ISSN_COLUMN);
      } else {
	mdinfo.eissn = issn;
      }
    }

    if (mdinfo.doi != null) {
      String doi = mdinfo.doi;
      if (StringUtil.startsWithIgnoreCase(doi, "doi:")) {
	doi = doi.substring("doi:".length());
	log.debug3("doi = '" + doi + "'.");
      }

      if (doi.length() > MAX_DOI_COLUMN) {
	log.warning("doi too long '" + mdinfo.doi + "' for title: '"
	    + mdinfo.journalTitle + "' publisher: " + mdinfo.publisher + "'");
	mdinfo.doi = SqlDbManager.truncateVarchar(doi, MAX_DOI_COLUMN);
      } else {
	mdinfo.doi = doi;
      }
    }

    if (mdinfo.pubDate != null) {
      if (mdinfo.pubDate.length() > MAX_DATE_COLUMN) {
	log.warning("pubDate too long '" + mdinfo.pubDate + "' for title: '"
	    + mdinfo.journalTitle + "' publisher: " + mdinfo.publisher + "'");
	mdinfo.pubDate =
	    SqlDbManager.truncateVarchar(mdinfo.pubDate, MAX_DATE_COLUMN);
      }
    }

    if (mdinfo.volume != null) {
      if (mdinfo.volume.length() > MAX_VOLUME_COLUMN) {
	log.warning("volume too long '" + mdinfo.pubDate + "' for title: '"
	    + mdinfo.journalTitle + "' publisher: " + mdinfo.publisher + "'");
	mdinfo.volume =
	    SqlDbManager.truncateVarchar(mdinfo.volume, MAX_VOLUME_COLUMN);
      }
    }

    if (mdinfo.issue != null) {
      if (mdinfo.issue.length() > MAX_ISSUE_COLUMN) {
	log.warning("issue too long '" + mdinfo.issue + "' for title: '"
	    + mdinfo.journalTitle + "' publisher: " + mdinfo.publisher + "'");
	mdinfo.issue =
	    SqlDbManager.truncateVarchar(mdinfo.issue, MAX_ISSUE_COLUMN);
      }
    }

    if (mdinfo.startPage != null) {
      if (mdinfo.startPage.length() > MAX_START_PAGE_COLUMN) {
	log.warning("startPage too long '" + mdinfo.startPage
	    + "' for title: '" + mdinfo.journalTitle + "' publisher: "
	    + mdinfo.publisher + "'");
	mdinfo.startPage =
	    SqlDbManager.truncateVarchar(mdinfo.startPage, MAX_START_PAGE_COLUMN);
      }
    }

    if (mdinfo.articleTitle != null) {
      if (mdinfo.articleTitle.length() > MAX_NAME_COLUMN) {
	log.warning("article title too long '" + mdinfo.articleTitle
	    + "' for title: '" + mdinfo.journalTitle + "' publisher: "
	    + mdinfo.publisher + "'");
	mdinfo.articleTitle =
	    SqlDbManager.truncateVarchar(mdinfo.articleTitle, MAX_NAME_COLUMN);
      }
    }

    if (mdinfo.publisher != null) {
      if (mdinfo.publisher.length() > MAX_NAME_COLUMN) {
	log.warning("publisher too long '" + mdinfo.publisher
	    + "' for title: '" + mdinfo.journalTitle + "'");
	mdinfo.publisher =
	    SqlDbManager.truncateVarchar(mdinfo.publisher, MAX_NAME_COLUMN);
      }
    }

    if (mdinfo.journalTitle != null) {
      if (mdinfo.journalTitle.length() > MAX_NAME_COLUMN) {
	log.warning("journal title too long '" + mdinfo.journalTitle
	    + "' for publisher: " + mdinfo.publisher + "'");
	mdinfo.journalTitle =
	    SqlDbManager.truncateVarchar(mdinfo.journalTitle, MAX_NAME_COLUMN);
      }
    }

    if (mdinfo.authorSet != null) {
      Set<String> authors = new HashSet<String>();
      for (String author : mdinfo.authorSet) {
	if (author.length() > MAX_AUTHOR_COLUMN) {
	  log.warning("author too long '" + author + "' for title: '"
	      + mdinfo.journalTitle + "' publisher: " + mdinfo.publisher + "'");
	  authors.add(SqlDbManager.truncateVarchar(author, MAX_AUTHOR_COLUMN));
	} else {
	  authors.add(author);
	}
      }
      mdinfo.authorSet = authors;
    }

    if (mdinfo.keywordSet != null) {
      Set<String> keywords = new HashSet<String>();
      for (String keyword : mdinfo.keywordSet) {
	if (keyword.length() > MAX_KEYWORD_COLUMN) {
	  log.warning("keyword too long '" + keyword + "' for title: '"
	      + mdinfo.journalTitle + "' publisher: " + mdinfo.publisher + "'");
	  keywords.add(SqlDbManager.truncateVarchar(keyword, MAX_KEYWORD_COLUMN));
	} else {
	  keywords.add(keyword);
	}
      }
      mdinfo.keywordSet = keywords;
    }

    if (mdinfo.featuredUrlMap != null) {
      Map<String, String> featuredUrls = new HashMap<String, String>();
      for (String key : mdinfo.featuredUrlMap.keySet()) {
	if (mdinfo.featuredUrlMap.get(key).length() > MAX_URL_COLUMN) {
	  log.warning("URL too long '" + mdinfo.featuredUrlMap.get(key)
	      + "' for title: '" + mdinfo.journalTitle + "' publisher: "
	      + mdinfo.publisher + "'");
	  featuredUrls.put(key,
	                   SqlDbManager.truncateVarchar(mdinfo.featuredUrlMap.
	                                             get(key), MAX_URL_COLUMN));
	} else {
	  featuredUrls.put(key, mdinfo.featuredUrlMap.get(key));
	}
      }
      mdinfo.featuredUrlMap = featuredUrls;
    }

    if (mdinfo.endPage != null) {
      if (mdinfo.endPage.length() > MAX_END_PAGE_COLUMN) {
	log.warning("endPage too long '" + mdinfo.endPage + "' for title: '"
	    + mdinfo.journalTitle + "' publisher: " + mdinfo.publisher + "'");
	mdinfo.endPage =
	    SqlDbManager.truncateVarchar(mdinfo.endPage, MAX_END_PAGE_COLUMN);
      }
    }

    if (mdinfo.coverage != null) {
      if (mdinfo.coverage.length() > MAX_COVERAGE_COLUMN) {
	log.warning("coverage too long '" + mdinfo.coverage + "' for title: '"
	    + mdinfo.journalTitle + "' publisher: " + mdinfo.publisher + "'");
	mdinfo.coverage =
	    SqlDbManager.truncateVarchar(mdinfo.coverage, MAX_COVERAGE_COLUMN);
      }
    } else {
	mdinfo.coverage = "fulltext";
    }

    if (mdinfo.itemNumber != null) {
      if (mdinfo.itemNumber.length() > MAX_ITEM_NO_COLUMN) {
	log.warning("itemNumber too long '" + mdinfo.itemNumber
	    + "' for title: '" + mdinfo.journalTitle + "' publisher: "
	    + mdinfo.publisher + "'");
	mdinfo.itemNumber =
	    SqlDbManager.truncateVarchar(mdinfo.itemNumber, MAX_ITEM_NO_COLUMN);
      }
    }

    if (mdinfo.proprietaryIdentifier != null) {
      if (mdinfo.proprietaryIdentifier.length() > MAX_PUBLICATION_ID_COLUMN) {
	log.warning("proprietaryIdentifier too long '"
	    + mdinfo.proprietaryIdentifier + "' for title: '"
	    + mdinfo.journalTitle + "' publisher: " + mdinfo.publisher + "'");
	mdinfo.proprietaryIdentifier =
	    SqlDbManager.truncateVarchar(mdinfo.proprietaryIdentifier,
				      MAX_PUBLICATION_ID_COLUMN);
      }
    }

    return mdinfo;
  }

  /**
   * Stores in the database metadata for the Archival Unit.
   * 
   * @param conn
   *          A Connection with the connection to the database
   * @param mdinfo
   *          An ArticleMetadataInfo providing the metadata.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private void storeMetadata(Connection conn, ArticleMetadataInfo mdinfo)
      throws SQLException {
    final String DEBUG_HEADER = "storeMetadata(): ";
    if (log.isDebug3()) {
      log.debug3(DEBUG_HEADER + "Starting: auId = " + auId);
      log.debug3(DEBUG_HEADER + "auKey = " + auKey);
      log.debug3(DEBUG_HEADER + "auMdSeq = " + auMdSeq);
      log.debug3(DEBUG_HEADER + "mdinfo.articleTitle = " + mdinfo.articleTitle);
    }

    // Check whether the publisher has not been located in the database.
    if (publisherSeq == null) {
      // Yes: Get the publisher received in the metadata.
      String publisher = mdinfo.publisher;
      log.debug3(DEBUG_HEADER + "publisher = " + publisher);

      // Find the publisher or create it.
      publisherSeq = mdManager.findOrCreatePublisher(conn, publisher);
      log.debug3(DEBUG_HEADER + "publisherSeq = " + publisherSeq);
    }

    // Get any ISBN values received in the metadata.
    String pIsbn = mdinfo.isbn;
    log.debug3(DEBUG_HEADER + "pIsbn = " + pIsbn);

    String eIsbn = mdinfo.eisbn;
    log.debug3(DEBUG_HEADER + "eIsbn = " + eIsbn);

    // Get any ISSN values received in the metadata.
    String pIssn = mdinfo.issn;
    log.debug3(DEBUG_HEADER + "pIssn = " + pIssn);

    String eIssn = mdinfo.eissn;
    log.debug3(DEBUG_HEADER + "eIssn = " + eIssn);

    // Get the proprietary identifier received in the metadata.
    String proprietaryId = mdinfo.proprietaryIdentifier;
    log.debug3(DEBUG_HEADER + "proprietaryId = " + proprietaryId);

    // Get the publication date received in the metadata.
    String date = mdinfo.pubDate;
    log.debug3(DEBUG_HEADER + "date = " + date);

    // Get the volume received in the metadata.
    String volume = mdinfo.volume;
    log.debug3(DEBUG_HEADER + "volume = " + volume);

    // Get the publication to which this metadata belongs.
    Long publicationSeq =
	mdManager.findOrCreatePublication(conn, pIssn, eIssn, pIsbn, eIsbn,
					  publisherSeq, mdinfo.journalTitle,
					  date, proprietaryId, volume);
    log.debug3(DEBUG_HEADER + "publicationSeq = " + publicationSeq);

    // Skip it if the publication could not be found or created.
    if (publicationSeq == null) {
      log.debug3(DEBUG_HEADER + "Done: publicationSeq is null.");
      return;
    }

    // Check whether the plugin has not been located in the database.
    if (pluginSeq == null) {
      // Yes: Get its identifier.
      String pluginId = PluginManager.pluginIdFromAuId(auId);
      log.debug3(DEBUG_HEADER + "pluginId = " + pluginId);

      // Find the plugin or create it.
      pluginSeq = mdManager.findOrCreatePlugin(conn, pluginId, platform);
      log.debug3(DEBUG_HEADER + "pluginSeq = " + pluginSeq);

      // Skip it if the plugin could not be found or created.
      if (pluginSeq == null) {
        log.debug3(DEBUG_HEADER + "Done: pluginSeq is null.");
        return;
      }
    }

    // Check whether the Archival Unit has not been located in the database.
    if (auSeq == null) {
      // Yes: Find it or create it.
      auSeq = mdManager.findOrCreateAu(conn, pluginSeq, auKey);
      log.debug3(DEBUG_HEADER + "auSeq = " + auSeq);

      // Skip it if the Archival Unit could not be found or created.
      if (auSeq == null) {
        log.debug3(DEBUG_HEADER + "Done: auSeq is null.");
        return;
      }
    }

    // Check whether the Archival Unit metadata has not been located in the
    // database.
    if (auMdSeq == null) {
      // Yes: Find the Archival Unit metadata in the database.
      auMdSeq = mdManager.findAuMd(conn, auSeq);
      log.debug3(DEBUG_HEADER + "new auMdSeq = " + auMdSeq);
    }

    boolean newAu = false;

    // Check whether it is a new Archival Unit metadata.
    if (auMdSeq == null) {
      // Yes: Add to the database the new Archival Unit metadata.
      auMdSeq = mdManager.addAuMd(conn, auSeq, pluginVersion,
                                  NEVER_EXTRACTED_EXTRACTION_TIME);
      log.debug3(DEBUG_HEADER + "new auSeq = " + auMdSeq);

      // Skip it if the new Archival Unit metadata could not be created.
      if (auMdSeq == null) {
	log.debug3(DEBUG_HEADER + "Done: auMdSeq is null.");
	return;
      }

      newAu = true;
    } else {
      // No: Update the Archival Unit metadata ancillary data.
      updateAuMd(conn, auMdSeq, pluginVersion);
      log.debug3(DEBUG_HEADER + "updated AU.");
    }

    // Update or create the metadata item.
    updateOrCreateMdItem(conn, newAu, publicationSeq, mdinfo);

    log.debug3(DEBUG_HEADER + "Done.");
  }

  /**
   * Updates the metadata version an Archival Unit in the database.
   * 
   * @param conn
   *          A Connection with the connection to the database
   * @param auMdSeq
   *          A Long with the identifier of the archival unit metadata.
   * @param version
   *          A String with the archival unit metadata version.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private void updateAuMd(Connection conn, Long auMdSeq, int version)
      throws SQLException {
    final String DEBUG_HEADER = "updateAuMd(): ";
    PreparedStatement updateAu =
	sqlDbManager.prepareStatement(conn, UPDATE_AU_MD_QUERY);

    try {
      updateAu.setShort(1, (short) version);
      updateAu.setLong(2, auMdSeq);
      int count = sqlDbManager.executeUpdate(updateAu);

      if (log.isDebug3()) {
	log.debug3(DEBUG_HEADER + "count = " + count);
	log.debug3(DEBUG_HEADER + "Updated auMdSeq = " + auMdSeq);
      }
    } finally {
      updateAu.close();
    }
  }

  /**
   * Updates a metadata item if it exists in the database, otherwise it creates
   * it.
   * 
   * @param conn
   *          A Connection with the connection to the database
   * @param newAu
   *          A boolean with the indication of whether this is a new AU.
   * @param publicationSeq
   *          A Long with the identifier of the publication.
   * @param mdinfo
   *          An ArticleMetadataInfo providing the metadata.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private void updateOrCreateMdItem(Connection conn, boolean newAu,
      Long publicationSeq, ArticleMetadataInfo mdinfo) throws SQLException {
    final String DEBUG_HEADER = "updateOrCreateMdItem(): ";

    // Get the publication date received in the metadata.
    String date = mdinfo.pubDate;
    log.debug3(DEBUG_HEADER + "date = " + date);

    // Get the issue received in the metadata.
    String issue = mdinfo.issue;
    log.debug3(DEBUG_HEADER + "issue = " + issue);

    // Get the start page received in the metadata.
    String startPage = mdinfo.startPage;
    log.debug3(DEBUG_HEADER + "startPage = " + startPage);

    // Get the end page received in the metadata.
    String endPage = mdinfo.endPage;
    log.debug3(DEBUG_HEADER + "endPage = " + endPage);

    // Get the item number received in the metadata.
    String itemNo = mdinfo.itemNumber;
    log.debug3(DEBUG_HEADER + "itemNo = " + itemNo);

    // Get the item title received in the metadata.
    String itemTitle = mdinfo.articleTitle;
    log.debug3(DEBUG_HEADER + "itemTitle = " + itemTitle);

    // Get the coverage received in the metadata.
    String coverage = mdinfo.coverage;
    log.debug3(DEBUG_HEADER + "coverage = " + coverage);

    // Get the DOI received in the metadata.
    String doi = mdinfo.doi;
    log.debug3(DEBUG_HEADER + "doi = " + doi);

    // Get the featured URLs received in the metadata.
    Map<String, String> featuredUrlMap = mdinfo.featuredUrlMap;

    if (log.isDebug3()) {
      for (String feature : featuredUrlMap.keySet()) {
	log.debug3(DEBUG_HEADER + "feature = " + feature + ", URL = "
	    + featuredUrlMap.get(feature));
      }
    }

    // Get the access URL received in the metadata.
    String accessUrl = mdinfo.accessUrl;
    log.debug3(DEBUG_HEADER + "accessUrl = " + accessUrl);

    // Get the identifier of the parent, which is the publication metadata
    // item.
    Long parentSeq =
	mdManager.findPublicationMetadataItem(conn, publicationSeq);
    log.debug3(DEBUG_HEADER + "parentSeq = " + parentSeq);

    // Get the type of the parent.
    String parentMdItemType = getMdItemTypeName(conn, parentSeq);
    log.debug3(DEBUG_HEADER + "parentMdItemType = " + parentMdItemType);

    // Determine what type of a metadata item it is.
    String mdItemType = null;
    if (MD_ITEM_TYPE_BOOK.equals(parentMdItemType)) {
      if (StringUtil.isNullString(startPage)
	  && StringUtil.isNullString(endPage)
	  && StringUtil.isNullString(itemNo)) {
	mdItemType = MD_ITEM_TYPE_BOOK;
      } else {
	mdItemType = MD_ITEM_TYPE_BOOK_CHAPTER;
      }
    } else if (MD_ITEM_TYPE_JOURNAL.equals(parentMdItemType)) {
      mdItemType = MD_ITEM_TYPE_JOURNAL_ARTICLE;
    } else {
      // Skip it if the parent type is not a book or journal.
      log.error(DEBUG_HEADER + "Unknown parentMdItemType = "
	  + parentMdItemType);
      return;
    }

    log.debug3(DEBUG_HEADER + "mdItemType = " + mdItemType);

    // Find the metadata item type record sequence.
    Long mdItemTypeSeq = mdManager.findMetadataItemType(conn, mdItemType);
    log.debug3(DEBUG_HEADER + "mdItemTypeSeq = " + mdItemTypeSeq);

    Long mdItemSeq = null;
    boolean newMdItem = false;

    // Check whether it is a metadata item for a new Archival Unit.
    if (newAu) {
      // Yes: Create the new metadata item in the database.
      mdItemSeq =
	  mdManager.addMdItem(conn, parentSeq, mdItemTypeSeq, auMdSeq, date,
			      coverage);
      log.debug3(DEBUG_HEADER + "new mdItemSeq = " + mdItemSeq);

	  mdManager.addMdItemName(conn, mdItemSeq, itemTitle, PRIMARY_NAME_TYPE);

      newMdItem = true;
    } else {
      // No: Find the metadata item in the database.
      mdItemSeq = findMdItem(conn, mdItemTypeSeq, auMdSeq, accessUrl);
      log.debug3(DEBUG_HEADER + "mdItemSeq = " + mdItemSeq);

      // Check whether it is a new metadata item.
      if (mdItemSeq == null) {
	// Yes: Create it.
	mdItemSeq =
	    mdManager.addMdItem(conn, parentSeq, mdItemTypeSeq, auMdSeq, date,
				coverage);
	log.debug3(DEBUG_HEADER + "new mdItemSeq = " + mdItemSeq);

	  mdManager.addMdItemName(conn, mdItemSeq, itemTitle, PRIMARY_NAME_TYPE);

	newMdItem = true;
      }
    }

    log.debug3(DEBUG_HEADER + "newMdItem = " + newMdItem);

    String volume = null;

    // Check  whether this is a journal article.
    if (MD_ITEM_TYPE_JOURNAL_ARTICLE.equals(mdItemType)) {
      // Yes: Get the volume received in the metadata.
      volume = mdinfo.volume;
      log.debug3(DEBUG_HEADER + "volume = " + volume);
    }

    // Add the bibliographic data.
    int bibCount =
	updateOrCreateBibItem(conn, mdItemSeq, volume, issue, startPage,
			      endPage, itemNo);
    log.debug3(DEBUG_HEADER + "bibCount = " + bibCount);

    // Get the authors received in the metadata.
    Set<String> authors = mdinfo.authorSet;
    log.debug3(DEBUG_HEADER + "authors = " + authors);

    // Get the keywords received in the metadata.
    Set<String> keywords = mdinfo.keywordSet;
    log.debug3(DEBUG_HEADER + "keywords = " + keywords);

    // Check whether it is a new metadata item.
    if (newMdItem) {
      // Yes: Add the item URLs.
      addMdItemUrls(conn, mdItemSeq, accessUrl, featuredUrlMap);
      log.debug3(DEBUG_HEADER + "added AUItem URL.");

      // Add the item authors.
      addMdItemAuthors(conn, mdItemSeq, authors);
      log.debug3(DEBUG_HEADER + "added AUItem authors.");

      // Add the item keywords.
      addMdItemKeywords(conn, mdItemSeq, keywords);
      log.debug3(DEBUG_HEADER + "added AUItem keywords.");

      // Add the item DOI.
      mdManager.addMdItemDoi(conn, mdItemSeq, doi);
      log.debug3(DEBUG_HEADER + "added AUItem DOI.");
    } else {
      // No: Since the record exists, only add the properties that are new.
      // Add the item new URLs.
      addNewMdItemUrls(conn, mdItemSeq, accessUrl, featuredUrlMap);
      log.debug3(DEBUG_HEADER + "added AUItem URL.");

      // Add the item new authors.
      addNewMdItemAuthors(conn, mdItemSeq, authors);
      log.debug3(DEBUG_HEADER + "updated AUItem authors.");

      // Add the item new keywords.
      addNewMdItemKeywords(conn, mdItemSeq, keywords);
      log.debug3(DEBUG_HEADER + "updated AUItem keywords.");

      // Update the item DOI.
      updateMdItemDoi(conn, mdItemSeq, doi);
      log.debug3(DEBUG_HEADER + "updated AUItem DOI.");
    }

    log.debug3(DEBUG_HEADER + "Done.");
  }

  /**
   * Provides the name of the type of a metadata item.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param mdItemSeq
   *          A Long with the identifier of the metadata item.
   * @return a String with the name of the type of the metadata item.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private String getMdItemTypeName(Connection conn, Long mdItemSeq)
      throws SQLException {
    final String DEBUG_HEADER = "getMdItemTypeName(): ";
    String typeName = null;
    PreparedStatement getMdItemTypeName =
	sqlDbManager.prepareStatement(conn, GET_MD_ITEM_TYPE_NAME_QUERY);
    ResultSet resultSet = null;

    try {
      getMdItemTypeName.setLong(1, mdItemSeq);
      resultSet = sqlDbManager.executeQuery(getMdItemTypeName);

      if (resultSet.next()) {
	typeName = resultSet.getString(TYPE_NAME_COLUMN);
	log.debug3(DEBUG_HEADER + "typeName = " + typeName);
      }
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      getMdItemTypeName.close();
    }

    return typeName;
  }

  /**
   * Provides the identifier of a metadata item.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param mdItemTypeSeq
   *          A Long with the identifier of the metadata item type.
   * @param auMdSeq
   *          A Long with the identifier of the archival unit metadata.
   * @param accessUrl
   *          A String with the access URL of the metadata item.
   * @return a Long with the identifier of the metadata item.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private Long findMdItem(Connection conn, Long mdItemTypeSeq, Long auMdSeq,
      String accessUrl) throws SQLException {
    final String DEBUG_HEADER = "findMdItem(): ";
    Long mdItemSeq = null;
    ResultSet resultSet = null;

    PreparedStatement findMdItem =
	sqlDbManager.prepareStatement(conn, FIND_MD_ITEM_QUERY);

    try {
      findMdItem.setLong(1, mdItemTypeSeq);
      findMdItem.setLong(2, auMdSeq);
      findMdItem.setString(3, accessUrl);

      resultSet = sqlDbManager.executeQuery(findMdItem);
      if (resultSet.next()) {
	mdItemSeq = resultSet.getLong(MD_ITEM_SEQ_COLUMN);
      }
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      SqlDbManager.safeCloseStatement(findMdItem);
    }

    log.debug3(DEBUG_HEADER + "mdItemSeq = " + mdItemSeq);
    return mdItemSeq;
  }

  /**
   * Updates a bibliographic item if existing or creates it otherwise.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param mdItemSeq
   *          A Long with the metadata item identifier.
   * @param volume
   *          A String with the bibliographic volume.
   * @param issue
   *          A String with the bibliographic issue.
   * @param startPage
   *          A String with the bibliographic starting page.
   * @param endPage
   *          A String with the bibliographic ending page.
   * @param itemNo
   *          A String with the bibliographic item number.
   * @return an int with the number of database rows updated or inserted.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private int updateOrCreateBibItem(Connection conn, Long mdItemSeq,
      String volume, String issue, String startPage, String endPage,
      String itemNo) throws SQLException {
    final String DEBUG_HEADER = "updateOrCreateBiBItem(): ";
    int updatedCount =
	updateBibItem(conn, mdItemSeq, volume, issue, startPage, endPage,
		      itemNo);
    log.debug3(DEBUG_HEADER + "updatedCount = " + updatedCount);

    if (updatedCount > 0) {
      return updatedCount;
    }

    int addedCount =
	addBibItem(conn, mdItemSeq, volume, issue, startPage, endPage, itemNo);
    log.debug3(DEBUG_HEADER + "addedCount = " + addedCount);

    return addedCount;
  }

  /**
   * Adds to the database the URLs of a metadata item.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param mdItemSeq
   *          A Long with the metadata item identifier.
   * @param accessUrl
   *          A String with the access URL to be added.
   * @param featuredUrlMap
   *          A Map<String, String> with the URL/feature pairs to be added.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private void addMdItemUrls(Connection conn, Long mdItemSeq, String accessUrl,
      Map<String, String> featuredUrlMap) throws SQLException {
    final String DEBUG_HEADER = "addMdItemUrls(): ";

    if (!StringUtil.isNullString(accessUrl)) {
      // Add the access URL.
      mdManager.addMdItemUrl(conn, mdItemSeq, ACCESS_URL_FEATURE, accessUrl);
      log.debug3(DEBUG_HEADER + "Added feature = " + ACCESS_URL_FEATURE
	  + ", URL = " + accessUrl);
    }

    // Loop through all the featured URLs.
    for (String feature : featuredUrlMap.keySet()) {
      // Add the featured URL.
      mdManager.addMdItemUrl(conn, mdItemSeq, feature,
			     featuredUrlMap.get(feature));
      log.debug3(DEBUG_HEADER + "Added feature = " + feature + ", URL = "
	  + featuredUrlMap.get(feature));
    }
  }

  /**
   * Adds to the database the authors of a metadata item.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param mdItemSeq
   *          A Long with the metadata item identifier.
   * @param authors
   *          A Set<String> with the authors of the metadata item.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private void addMdItemAuthors(Connection conn, Long mdItemSeq,
      Set<String> authors) throws SQLException {
    final String DEBUG_HEADER = "addMdItemAuthors(): ";

    if (authors == null || authors.size() == 0) {
      return;
    }

    PreparedStatement insertMdItemAuthor =
	sqlDbManager.prepareStatement(conn, INSERT_AUTHOR_QUERY);

    try {
      for (String author : authors) {
	insertMdItemAuthor.setLong(1, mdItemSeq);
	insertMdItemAuthor.setString(2, author);
	insertMdItemAuthor.setLong(3, mdItemSeq);
	int count = sqlDbManager.executeUpdate(insertMdItemAuthor);

	if (log.isDebug3()) {
	  log.debug3(DEBUG_HEADER + "count = " + count);
	  log.debug3(DEBUG_HEADER + "Added author = " + author);
	}
      }
    } finally {
      SqlDbManager.safeCloseStatement(insertMdItemAuthor);
    }
  }

  /**
   * Adds to the database the keywords of a metadata item.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param mdItemSeq
   *          A Long with the metadata item identifier.
   * @param keywords
   *          A Set<String> with the keywords of the metadata item.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private void addMdItemKeywords(Connection conn, Long mdItemSeq,
      Set<String> keywords) throws SQLException {
    final String DEBUG_HEADER = "addMdItemKeywords(): ";

    if (keywords == null || keywords.size() == 0) {
      return;
    }

    PreparedStatement insertMdItemKeyword =
	sqlDbManager.prepareStatement(conn, INSERT_KEYWORD_QUERY);

    try {
      for (String keyword : keywords) {
	insertMdItemKeyword.setLong(1, mdItemSeq);
	insertMdItemKeyword.setString(2, keyword);
	int count = sqlDbManager.executeUpdate(insertMdItemKeyword);

	if (log.isDebug3()) {
	  log.debug3(DEBUG_HEADER + "count = " + count);
	  log.debug3(DEBUG_HEADER + "Added keyword = " + keyword);
	}
      }
    } finally {
      SqlDbManager.safeCloseStatement(insertMdItemKeyword);
    }
  }

  /**
   * Adds to the database the URLs of a metadata item, if they are new.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param mdItemSeq
   *          A Long with the metadata item identifier.
   * @param accessUrl
   *          A String with the access URL to be added.
   * @param featuredUrlMap
   *          A Map<String, String> with the URL/feature pairs to be added.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private void addNewMdItemUrls(Connection conn, Long mdItemSeq,
      String accessUrl, Map<String, String> featuredUrlMap)
	  throws SQLException {
    final String DEBUG_HEADER = "addNewMdItemUrls(): ";

    if (StringUtil.isNullString(accessUrl) && featuredUrlMap.size() == 0) {
      return;
    }

    // Initialize the collection of URLs to be added.
    Map<String, String> newUrls = new HashMap<String, String>(featuredUrlMap);
    newUrls.put(ACCESS_URL_FEATURE, accessUrl);

    PreparedStatement findMdItemFeaturedUrl =
	sqlDbManager.prepareStatement(conn, FIND_MD_ITEM_FEATURED_URL_QUERY);

    ResultSet resultSet = null;
    String feature;
    String url;

    try {
      // Get the existing URLs.
      findMdItemFeaturedUrl.setLong(1, mdItemSeq);
      resultSet = sqlDbManager.executeQuery(findMdItemFeaturedUrl);

      // Loop through all the URLs already linked to the metadata item.
      while (resultSet.next()) {
	feature = resultSet.getString(FEATURE_COLUMN);
	url = resultSet.getString(URL_COLUMN);
	log.debug3(DEBUG_HEADER + "Found feature = " + feature + ", URL = "
	    + url);

	// Remove it from the collection to be added if it exists already.
	if (newUrls.containsKey(feature) && newUrls.get(feature).equals(url)) {
	  log.debug3(DEBUG_HEADER + "Feature = " + feature + ", URL = " + url
	      + " already exists: Not adding it.");

	  newUrls.remove(feature);
	}
      }

    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      findMdItemFeaturedUrl.close();
    }

    // Add the URLs that are new.
    addMdItemUrls(conn, mdItemSeq, null, newUrls);
  }

  /**
   * Adds to the database the authors of a metadata item, if they are new.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param mdItemSeq
   *          A Long with the metadata item identifier.
   * @param authors
   *          A Set<String> with the authors of the metadata item.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private void addNewMdItemAuthors(Connection conn, Long mdItemSeq,
      Set<String> authors) throws SQLException {
    if (authors == null || authors.size() == 0) {
      return;
    }

    // Initialize the collection of authors to be added.
    Set<String> newAuthors = new HashSet<String>(authors);

    PreparedStatement findMdItemAuthor =
	sqlDbManager.prepareStatement(conn, FIND_MD_ITEM_AUTHOR_QUERY);

    ResultSet resultSet = null;

    try {
      // Get the existing authors.
      findMdItemAuthor.setLong(1, mdItemSeq);
      resultSet = sqlDbManager.executeQuery(findMdItemAuthor);

      List<String> oldAuthors = new ArrayList<String>();

      // Add them to the list of authors already linked to the metadata
      // item.
      while (resultSet.next()) {
	oldAuthors.add(resultSet.getString(AUTHOR_NAME_COLUMN));
      }

      // Remove them from the collection to be added.
      newAuthors.removeAll(oldAuthors);
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      findMdItemAuthor.close();
    }

    // Add the authors that are new.
    addMdItemAuthors(conn, mdItemSeq, newAuthors);
  }

  /**
   * Adds to the database the keywords of a metadata item.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param mdItemSeq
   *          A Long with the metadata item identifier.
   * @param keywords
   *          A Set<String> with the keywords of the metadata item.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private void addNewMdItemKeywords(Connection conn, Long mdItemSeq,
      Set<String> keywords) throws SQLException {
    if (keywords == null || keywords.size() == 0) {
      return;
    }

    // Initialize the collection of keywords to be added.
    Set<String> newKeywords = new HashSet<String>(keywords);

    PreparedStatement findMdItemKeyword =
	sqlDbManager.prepareStatement(conn, FIND_MD_ITEM_KEYWORD_QUERY);

    ResultSet resultSet = null;

    try {
      // Get the existing keywords.
      findMdItemKeyword.setLong(1, mdItemSeq);
      resultSet = sqlDbManager.executeQuery(findMdItemKeyword);

      List<String> oldKeywords = new ArrayList<String>();

      // Add them to the list of keywords already linked to the metadata
      // item.
      while (resultSet.next()) {
	oldKeywords.add(resultSet.getString(KEYWORD_COLUMN));
      }

      // Remove them from the collection to be added.
      newKeywords.removeAll(oldKeywords);
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      findMdItemKeyword.close();
    }

    // Add the keywords that are new.
    addMdItemKeywords(conn, mdItemSeq, newKeywords);
  }

  /**
   * Updates the DOI of a metadata item in the database.
   * 
   * @param conn
   *          A Connection with the connection to the database
   * @param mdItemSeq
   *          A Long with the metadata item identifier.
   * @param doi
   *          A String with the metadata item DOI.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private void updateMdItemDoi(Connection conn, Long mdItemSeq, String doi)
      throws SQLException {
    if (StringUtil.isNullString(doi)) {
      return;
    }

    PreparedStatement findMdItemDoi =
	sqlDbManager.prepareStatement(conn, FIND_MD_ITEM_DOI_QUERY);

    ResultSet resultSet = null;

    try {
      findMdItemDoi.setLong(1, mdItemSeq);
      resultSet = sqlDbManager.executeQuery(findMdItemDoi);

      if (!resultSet.next()) {
	mdManager.addMdItemDoi(conn, mdItemSeq, doi);
      }
    } finally {
      SqlDbManager.safeCloseResultSet(resultSet);
      findMdItemDoi.close();
    }
  }

  /**
   * Updates a bibliographic item.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param mdItemSeq
   *          A Long with the metadata item identifier.
   * @param volume
   *          A String with the bibliographic volume.
   * @param issue
   *          A String with the bibliographic issue.
   * @param startPage
   *          A String with the bibliographic starting page.
   * @param endPage
   *          A String with the bibliographic ending page.
   * @param itemNo
   *          A String with the bibliographic item number.
   * @return an int with the number of database rows updated.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private int updateBibItem(Connection conn, Long mdItemSeq, String volume,
      String issue, String startPage, String endPage, String itemNo)
      throws SQLException {
    final String DEBUG_HEADER = "updateBibItem(): ";
    int updatedCount = 0;
    PreparedStatement updateBibItem =
	sqlDbManager.prepareStatement(conn, UPDATE_BIB_ITEM_QUERY);

    try {
      updateBibItem.setString(1, volume);
      updateBibItem.setString(2, issue);
      updateBibItem.setString(3, startPage);
      updateBibItem.setString(4, endPage);
      updateBibItem.setString(5, itemNo);
      updateBibItem.setLong(6, mdItemSeq);
      updatedCount = sqlDbManager.executeUpdate(updateBibItem);
    } finally {
      SqlDbManager.safeCloseStatement(updateBibItem);
    }

    log.debug3(DEBUG_HEADER + "updatedCount = " + updatedCount);
    return updatedCount;
  }

  /**
   * Adds to the database a bibliographic item.
   * 
   * @param conn
   *          A Connection with the database connection to be used.
   * @param mdItemSeq
   *          A Long with the metadata item identifier.
   * @param volume
   *          A String with the bibliographic volume.
   * @param issue
   *          A String with the bibliographic issue.
   * @param startPage
   *          A String with the bibliographic starting page.
   * @param endPage
   *          A String with the bibliographic ending page.
   * @param itemNo
   *          A String with the bibliographic item number.
   * @return an int with the number of database rows inserted.
   * @throws SQLException
   *           if any problem occurred accessing the database.
   */
  private int addBibItem(Connection conn, Long mdItemSeq, String volume,
      String issue, String startPage, String endPage, String itemNo)
      throws SQLException {
    final String DEBUG_HEADER = "addBibItem(): ";
    int addedCount = 0;
    PreparedStatement insertBibItem =
	sqlDbManager.prepareStatement(conn, INSERT_BIB_ITEM_QUERY);

    try {
      insertBibItem.setLong(1, mdItemSeq);
      insertBibItem.setString(2, volume);
      insertBibItem.setString(3, issue);
      insertBibItem.setString(4, startPage);
      insertBibItem.setString(5, endPage);
      insertBibItem.setString(6, itemNo);
      addedCount = sqlDbManager.executeUpdate(insertBibItem);
    } finally {
      SqlDbManager.safeCloseStatement(insertBibItem);
    }

    log.debug3(DEBUG_HEADER + "addedCount = " + addedCount);
    return addedCount;
  }
}
