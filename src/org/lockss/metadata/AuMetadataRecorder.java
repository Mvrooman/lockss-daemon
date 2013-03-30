package org.lockss.metadata;

import static org.lockss.db.SqlDbManager.MAX_AUTHOR_COLUMN;
import static org.lockss.db.SqlDbManager.MAX_COVERAGE_COLUMN;
import static org.lockss.db.SqlDbManager.MAX_DATE_COLUMN;
import static org.lockss.db.SqlDbManager.MAX_DOI_COLUMN;
import static org.lockss.db.SqlDbManager.MAX_END_PAGE_COLUMN;
import static org.lockss.db.SqlDbManager.MAX_ISBN_COLUMN;
import static org.lockss.db.SqlDbManager.MAX_ISSN_COLUMN;
import static org.lockss.db.SqlDbManager.MAX_ISSUE_COLUMN;
import static org.lockss.db.SqlDbManager.MAX_ITEM_NO_COLUMN;
import static org.lockss.db.SqlDbManager.MAX_KEYWORD_COLUMN;
import static org.lockss.db.SqlDbManager.MAX_NAME_COLUMN;
import static org.lockss.db.SqlDbManager.MAX_PUBLICATION_ID_COLUMN;
import static org.lockss.db.SqlDbManager.MAX_START_PAGE_COLUMN;
import static org.lockss.db.SqlDbManager.MAX_URL_COLUMN;
import static org.lockss.db.SqlDbManager.MAX_VOLUME_COLUMN;

import org.lockss.db.DbManager;
import org.lockss.metadata.ArticleMetadataBuffer.ArticleMetadataInfo;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.Plugin;
import org.lockss.plugin.PluginManager;
import org.lockss.util.Logger;
import org.lockss.util.StringUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Writes to the database metadata related to an archival unit.
 */
public abstract class AuMetadataRecorder {

	// AU-related properties independent of the database.
	protected final Plugin plugin;
	protected final String platform;
	protected final int pluginVersion;
	protected final String auId;
	protected final String auKey;

	protected static Logger log = Logger.getLogger(AuMetadataRecorder.class);

	protected ReindexingTask task;
	
	// The archival unit.
	protected final ArchivalUnit au;
	
	/**
	 * Constructor.
	 *
	 * @param task A ReindexingTaskwith the calling task.
	 * @param mdManager A MetadataManager with the metadata manager.
	 * @param au An ArchivalUnit with the archival unit.
	 */
	public AuMetadataRecorder(ReindexingTask task, MetadataManager mdManager,
			ArchivalUnit au)
	{
		this.task = task;
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
	 * @param mditr An Iterator<ArticleMetadataInfo> with the metadata.
	 * @throws Exception if any problem occurred accessing the database.
	 */
	abstract void recordMetadata(Iterator<ArticleMetadataBuffer.ArticleMetadataInfo> mditr) throws Exception;

	/**
	 * Normalizes metadata info fields.
	 * 
	 * @param mdinfo
	 *          the ArticleMetadataInfo
	 * @return an ArticleMetadataInfo with the normalized properties.
	 */
	protected ArticleMetadataInfo normalizeMetadata(ArticleMetadataInfo mdinfo) {
		final String DEBUG_HEADER = "normalizeMetadata(): ";
		if (mdinfo.accessUrl != null) {
			if (mdinfo.accessUrl.length() > MAX_URL_COLUMN) {
				log.warning("accessUrl too long '" + mdinfo.accessUrl
						+ "' for title: '" + mdinfo.journalTitle + "' publisher: "
						+ mdinfo.publisher + "'");
				mdinfo.accessUrl =
						DbManager.truncateVarchar(mdinfo.accessUrl, MAX_URL_COLUMN);
			}
		}

		if (mdinfo.isbn != null) {
			String isbn = mdinfo.isbn.replaceAll("-", "");
			log.debug3(DEBUG_HEADER + "isbn = '" + isbn + "'.");

			if (isbn.length() > MAX_ISBN_COLUMN) {
				log.warning("isbn too long '" + mdinfo.isbn + "' for title: '"
						+ mdinfo.journalTitle + "' publisher: " + mdinfo.publisher + "'");
				mdinfo.isbn = DbManager.truncateVarchar(isbn, MAX_ISBN_COLUMN);
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
				mdinfo.eisbn = DbManager.truncateVarchar(isbn, MAX_ISBN_COLUMN);
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
				mdinfo.issn = DbManager.truncateVarchar(issn, MAX_ISSN_COLUMN);
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
				mdinfo.eissn = DbManager.truncateVarchar(issn, MAX_ISSN_COLUMN);
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
				mdinfo.doi = DbManager.truncateVarchar(doi, MAX_DOI_COLUMN);
			} else {
				mdinfo.doi = doi;
			}
		}

		if (mdinfo.pubDate != null) {
			if (mdinfo.pubDate.length() > MAX_DATE_COLUMN) {
				log.warning("pubDate too long '" + mdinfo.pubDate + "' for title: '"
						+ mdinfo.journalTitle + "' publisher: " + mdinfo.publisher + "'");
				mdinfo.pubDate =
						DbManager.truncateVarchar(mdinfo.pubDate, MAX_DATE_COLUMN);
			}
		}

		if (mdinfo.volume != null) {
			if (mdinfo.volume.length() > MAX_VOLUME_COLUMN) {
				log.warning("volume too long '" + mdinfo.pubDate + "' for title: '"
						+ mdinfo.journalTitle + "' publisher: " + mdinfo.publisher + "'");
				mdinfo.volume =
						DbManager.truncateVarchar(mdinfo.volume, MAX_VOLUME_COLUMN);
			}
		}

		if (mdinfo.issue != null) {
			if (mdinfo.issue.length() > MAX_ISSUE_COLUMN) {
				log.warning("issue too long '" + mdinfo.issue + "' for title: '"
						+ mdinfo.journalTitle + "' publisher: " + mdinfo.publisher + "'");
				mdinfo.issue =
						DbManager.truncateVarchar(mdinfo.issue, MAX_ISSUE_COLUMN);
			}
		}

		if (mdinfo.startPage != null) {
			if (mdinfo.startPage.length() > MAX_START_PAGE_COLUMN) {
				log.warning("startPage too long '" + mdinfo.startPage
						+ "' for title: '" + mdinfo.journalTitle + "' publisher: "
						+ mdinfo.publisher + "'");
				mdinfo.startPage =
						DbManager.truncateVarchar(mdinfo.startPage, MAX_START_PAGE_COLUMN);
			}
		}

		if (mdinfo.articleTitle != null) {
			if (mdinfo.articleTitle.length() > MAX_NAME_COLUMN) {
				log.warning("article title too long '" + mdinfo.articleTitle
						+ "' for title: '" + mdinfo.journalTitle + "' publisher: "
						+ mdinfo.publisher + "'");
				mdinfo.articleTitle =
						DbManager.truncateVarchar(mdinfo.articleTitle, MAX_NAME_COLUMN);
			}
		}

		if (mdinfo.publisher != null) {
			if (mdinfo.publisher.length() > MAX_NAME_COLUMN) {
				log.warning("publisher too long '" + mdinfo.publisher
						+ "' for title: '" + mdinfo.journalTitle + "'");
				mdinfo.publisher =
						DbManager.truncateVarchar(mdinfo.publisher, MAX_NAME_COLUMN);
			}
		}

		if (mdinfo.journalTitle != null) {
			if (mdinfo.journalTitle.length() > MAX_NAME_COLUMN) {
				log.warning("journal title too long '" + mdinfo.journalTitle
						+ "' for publisher: " + mdinfo.publisher + "'");
				mdinfo.journalTitle =
						DbManager.truncateVarchar(mdinfo.journalTitle, MAX_NAME_COLUMN);
			}
		}

		if (mdinfo.authorSet != null) {
			Set<String> authors = new HashSet<String>();
			for (String author : mdinfo.authorSet) {
				if (author.length() > MAX_AUTHOR_COLUMN) {
					log.warning("author too long '" + author + "' for title: '"
							+ mdinfo.journalTitle + "' publisher: " + mdinfo.publisher + "'");
					authors.add(DbManager.truncateVarchar(author, MAX_AUTHOR_COLUMN));
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
					keywords.add(DbManager.truncateVarchar(keyword, MAX_KEYWORD_COLUMN));
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
							DbManager.truncateVarchar(mdinfo.featuredUrlMap.
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
						DbManager.truncateVarchar(mdinfo.endPage, MAX_END_PAGE_COLUMN);
			}
		}

		if (mdinfo.coverage != null) {
			if (mdinfo.coverage.length() > MAX_COVERAGE_COLUMN) {
				log.warning("coverage too long '" + mdinfo.coverage + "' for title: '"
						+ mdinfo.journalTitle + "' publisher: " + mdinfo.publisher + "'");
				mdinfo.coverage =
						DbManager.truncateVarchar(mdinfo.coverage, MAX_COVERAGE_COLUMN);
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
						DbManager.truncateVarchar(mdinfo.itemNumber, MAX_ITEM_NO_COLUMN);
			}
		}

		if (mdinfo.proprietaryIdentifier != null) {
			if (mdinfo.proprietaryIdentifier.length() > MAX_PUBLICATION_ID_COLUMN) {
				log.warning("proprietaryIdentifier too long '"
						+ mdinfo.proprietaryIdentifier + "' for title: '"
						+ mdinfo.journalTitle + "' publisher: " + mdinfo.publisher + "'");
				mdinfo.proprietaryIdentifier =
						DbManager.truncateVarchar(mdinfo.proprietaryIdentifier,
								MAX_PUBLICATION_ID_COLUMN);
			}
		}

		return mdinfo;
	}


}
