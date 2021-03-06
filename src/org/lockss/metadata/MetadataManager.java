package org.lockss.metadata;


import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import org.lockss.app.BaseLockssDaemonManager;
import org.lockss.app.ConfigurableManager;
import org.lockss.db.DbManager;
import org.lockss.metadata.ArticleMetadataBuffer.ArticleMetadataInfo;
import org.lockss.metadata.ReindexingTask.ReindexingStatus;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.Plugin;
import org.lockss.plugin.Plugin.Feature;
import org.lockss.util.Logger;
import org.lockss.util.StringUtil;

public abstract class MetadataManager extends BaseLockssDaemonManager implements ConfigurableManager {

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

	protected static Logger log = Logger.getLogger(MetadataManager.class);
	
	  // The number of failed reindexing operations.
	public static long failedReindexingCount = 0;
	  // The number of articles currently in the metadata database.
	public static long metadataArticleCount = 0;
	  // The number of successful reindexing operations.
	public long successfulReindexingCount = 0;

	/**
	 * Provides a list of AuIds that require reindexing sorted by priority.
	 *
	 * @param maxAuIds An int with the maximum number of AuIds to return.
	 * @return a List<String> with the list of AuIds that require reindexing
	 *         sorted by priority.
	 */
	abstract List<String> getPrioritizedAuIdsToReindex(int maxAuIds) throws Exception;

	/**
	 * Provides the identifier of an Archival Unit if existing or after creating
	 * it otherwise.
	 *
	 * @param pluginSeq A Long with the identifier of the plugin.
	 * @param auKey     A String with the Archival Unit key.
	 * @return a Long with the identifier of the Archival Unit.
	 * @throws Exception if any problem occurred accessing the database.
	 */
	abstract Long findOrCreateAu(Long pluginSeq, String auKey) throws Exception;

	/**
	 * Restarts the Metadata Managaer service by terminating any running
	 * reindexing tasks and then resetting its database before calling
	 * {@link #startServie()}
	 * .
	 * <p/>
	 * This method is only used for testing.
	 */
	abstract void restartService();

	/**
	 * Provides the number of active reindexing tasks.
	 *
	 * @return a long with the number of active reindexing tasks.
	 */
	abstract long getActiveReindexingCount();

	/**
	 * Provides the number of successful reindexing operations.
	 *
	 * @return a long with the number of successful reindexing operations.
	 */
	abstract long getSuccessfulReindexingCount();

	/**
	 * Provides the number of unsuccesful reindexing operations.
	 *
	 * @return a long the number of unsuccessful reindexing operations.
	 */
	abstract long getFailedReindexingCount();

	/**
	 * Provides the list of reindexing tasks.
	 *
	 * @return a List<SqlReindexingTask> with the reindexing tasks.
	 */
	abstract List<SqlReindexingTask> getReindexingTasks();

	/**
	 * Provides the number of distinct articles in the metadata database.
	 *
	 * @return a long with the number of distinct articles in the metadata
	 *         database.
	 */
	abstract long getArticleCount();

	// The number of AUs pending to be reindexed.

	/**
	 * Provides the number of AUs pending to be reindexed.
	 *
	 * @return a long with the number of AUs pending to be reindexed.
	 */
	abstract long getPendingAusCount();

	/**
	 * Provides the indexing enabled state of this manager.
	 *
	 * @return a boolean with the indexing enabled state of this manager.
	 */
	abstract boolean isIndexingEnabled();

	/**
	 * Provides the identifier of a plugin if existing or after creating it
	 * otherwise.
	 *
	 * @param pluginId A String with the plugin identifier.
	 * @param platform A String with the publishing platform.
	 * @return a Long with the identifier of the plugin.
	 * @throws Exception if any problem occurred accessing the database.
	 */
	abstract Long findOrCreatePlugin(String pluginId, String platform) throws Exception;

	/**
	 * Adds an Archival Unit metadata to the database.
	 *
	 * @param auSeq       A Long with the identifier of the Archival Unit.
	 * @param version     An int with the metadata version.
	 * @param extractTime A long with the extraction time of the metadata.
	 * @return a Long with the identifier of the Archival Unit metadata just
	 *         added.
	 * @throws Exception if any problem occurred accessing the database.
	 */
	abstract Long addAuMd(Long auSeq, int version, long extractTime)
			throws Exception;

	/**
	 * Provides the identifier of a publisher if existing or after creating it
	 * otherwise.
	 *
	 * @param publisher A String with the publisher name.
	 * @return a Long with the identifier of the publisher.
	 * @throws Exception if any problem occurred accessing the database.
	 */
	public abstract Long findOrCreatePublisher(String publisher) throws Exception;

	/**
	 * Provides the identifier of a publication if existing or after creating it
	 * otherwise.
	 *
	 * @param pIssn         A String with the print ISSN of the publication.
	 * @param eIssn         A String with the electronic ISSN of the publication.
	 * @param pIsbn         A String with the print ISBN of the publication.
	 * @param eIsbn         A String with the electronic ISBN of the publication.
	 * @param publisherSeq  A Long with the publisher identifier.
	 * @param name          A String with the name of the publication.
	 * @param date          A String with the publication date of the publication.
	 * @param proprietaryId A String with the proprietary identifier of the publication.
	 * @param volume        A String with the bibliographic volume.
	 * @return a Long with the identifier of the publication.
	 * @throws Exception if any problem occurred accessing the database.
	 */
	public abstract Long findOrCreatePublication(String pIssn, String eIssn,
			String pIsbn, String eIsbn, Long publisherSeq, String name,
			String date, String proprietaryId, String volume) throws Exception;


	/**
	 * Provides the identifier of the metadata item of a publication.
	 *
	 * @param publicationSeq A Long with the identifier of the publication.
	 * @return a Long with the identifier of the metadata item of the publication.
	 * @throws Exception if any problem occurred accessing the database.
	 */
	abstract Long findPublicationMetadataItem(Long publicationSeq) throws Exception;

	/**
	 * Provides the identifier of a metadata item type by its name.
	 *
	 * @param typeName A String with the name of the metadata item type.
	 * @return a Long with the identifier of the metadata item type.
	 * @throws Exception if any problem occurred accessing the database.
	 */
	abstract Long findMetadataItemType(String typeName) throws Exception;

	/**
	 * Adds a metadata item to the database.
	 *
	 * @param parentSeq     A Long with the metadata item parent identifier.
	 * @param auMdSeq       A Long with the identifier of the Archival Unit metadata.
	 * @param mdItemTypeSeq A Long with the identifier of the type of metadata item.
	 * @param date          A String with the publication date of the metadata item.
	 * @param coverage      A String with the metadata item coverage.
	 * @return a Long with the identifier of the metadata item just added.
	 * @throws Exception if any problem occurred accessing the database.
	 */
	abstract Long addMdItem(Long parentSeq,
			Long mdItemTypeSeq, Long auMdSeq, String date,
			String coverage) throws Exception;

	/**
	 * Adds a metadata item name to the database.
	 *
	 * @param mdItemSeq A Long with the metadata item identifier.
	 * @param name      A String with the name of the metadata item.
	 * @param type      A String with the type of name of the metadata item.
	 * @throws Exception if any problem occurred accessing the database.
	 */
	abstract void addMdItemName(Long mdItemSeq, String name,
			String type) throws Exception;

	/**
	 * Adds to the database a metadata item URL.
	 *
	 * @param mdItemSeq A Long with the metadata item identifier.
	 * @param feature   A String with the feature of the metadata item URL.
	 * @param url       A String with the metadata item URL.
	 * @throws Exception if any problem occurred accessing the database.
	 */
	abstract void addMdItemUrl(Long mdItemSeq, String feature,
			String url) throws Exception;

	/**
	 * Adds to the database a metadata item DOI.
	 *
	 * @param mdItemSeq
	 *          A Long with the metadata item identifier.
	 * @param doi
	 *          A String with the DOI of the metadata item.
	 * @throws Exception
	 *           if any problem occurred accessing the database.
	 */
	abstract void addMdItemDoi(Long mdItemSeq, String doi)
			throws Exception;

	/**
	 * Adds an AU to the list of AUs to be reindexed.
	 * Does incremental reindexing if possible.
	 *
	 * @param au
	 *          An ArchivalUnit with the AU to be reindexed.
	 * @return <code>true</code> if au was added for reindexing
	 */
	public abstract boolean addAuToReindex(ArchivalUnit au);

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
	public abstract boolean addAuToReindex(ArchivalUnit au, boolean inBatch);

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
	public abstract boolean addAuToReindex(
			ArchivalUnit au, boolean inBatch, boolean fullReindex);

	/**
	 * Disables the indexing of an AU.
	 *
	 * @param au
	 *          An ArchivalUnit with the AU for which indexing is to be disabled.
	 * @return <code>true</code> if au was added for reindexing,
	 *         <code>false</code> otherwise.
	 */
	public abstract boolean disableAuIndexing(ArchivalUnit au);


	/**
	 * Something
	 * @return
	 */
	public abstract DbManager getDbManager();

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

	/**
	 * Provides the identifier of an Archival Unit metadata.
	 * 
	 * @param conn
	 *          A Connection with the database connection to be used.
	 * @param auSeq
	 *          A Long with the identifier of the Archival Unit.
	 * @return a Long with the identifier of the Archival Unit metadata.
	 * @throws Exception
	 *           if any problem occurred accessing the database.
	 */
	abstract Long findAuMd(Long auSeq) throws Exception;

	/**
	 * Updates the timestamp of the last extraction of an Archival Unit metadata.
	 * 
	 * @param auMdSeq
	 *          A Long with the identifier of the Archival Unit metadata.
	 * @throws Exception
	 *           if any problem occurred accessing the database.
	 */
	public abstract void updateAuLastExtractionTime(Long auMdSeq) throws Exception;

	//	  /**
	//	   * Provides the number of enabled pending AUs.
	//	   *
	//	   * @return a long with the number of enabled pending AUs.
	//	   * @throws Exception
	//	   *           if any problem occurred accessing the database.
	//	   */
	//
	//TODO look back at a later point.
	//abstract long getEnabledPendingAusCount() throws Exception;
	
	
	  
	/**
	 * Provides an indication of whether a metadata set corresponds to a book.
	 * 
	 * @param pIsbn
	 *            A String with the print ISBN in the metadata.
	 * @param eIsbn
	 *            A String with the electronic ISBN in the metadata.
	 * @return <code>true</code> if the metadata set corresponds to a book,
	 *         <code>false</code> otherwise.
	 */
	protected boolean isBook(String pIsbn, String eIsbn) {
		final String DEBUG_HEADER = "isBook(): ";

		// If there are ISBN values in the metadata, it is a book or a book
		// series.
		boolean isBook = !StringUtil.isNullString(pIsbn)
				|| !StringUtil.isNullString(eIsbn);
		log.debug3(DEBUG_HEADER + "isBook = " + isBook);

		return isBook;
	}
	
	
	  /**
	   * Notifies listeners that an AU is being reindexed.
	   * 
	   * @param au
	   *          An ArchivalUnit with the Archival Unit.
	   */
	  protected void notifyStartReindexingAu(ArchivalUnit au) {
	  }
	  
	  protected void incrementFailedReindexingCount() {
		    this.failedReindexingCount++;
		  } 
	  
	  protected synchronized void addToMetadataArticleCount(long count) {
		    this.metadataArticleCount += count;
		  }
	  
	  protected void incrementSuccessfulReindexingCount() {
		    this.successfulReindexingCount++;
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


 
}
