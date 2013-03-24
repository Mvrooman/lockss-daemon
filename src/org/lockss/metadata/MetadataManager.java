package org.lockss.metadata;


import java.util.List;

import org.lockss.app.BaseLockssDaemonManager;
import org.lockss.app.ConfigurableManager;
import org.lockss.util.Logger;

public abstract class MetadataManager extends BaseLockssDaemonManager implements ConfigurableManager {

	protected static Logger log = Logger.getLogger(MetadataManager.class);

	/**
	 * Provides a list of AuIds that require reindexing sorted by priority.
	 * @param maxAuIds
	 *          An int with the maximum number of AuIds to return.
	 * @return a List<String> with the list of AuIds that require reindexing
	 *         sorted by priority.
	 */
	abstract List<String> getPrioritizedAuIdsToReindex(int maxAuIds) throws Exception;

	/**
	 * Provides the identifier of an Archival Unit if existing or after creating
	 * it otherwise.
	 * 
	 * @param pluginSeq
	 *          A Long with the identifier of the plugin.
	 * @param auKey
	 *          A String with the Archival Unit key.
	 * @return a Long with the identifier of the Archival Unit.
	 * @throws Exception
	 *           if any problem occurred accessing the database.
	 */
	abstract Long findOrCreateAu(Long pluginSeq, String auKey) throws Exception;

	/**
	 * Restarts the Metadata Managaer service by terminating any running
	 * reindexing tasks and then resetting its database before calling
	 * {@link #startServie()}
	 * .
	 * <p>
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
	 * @return a List<ReindexingTask> with the reindexing tasks.
	 */
	abstract List<ReindexingTask> getReindexingTasks();

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
	 * @param pluginId
	 *            A String with the plugin identifier.
	 * @param platform
	 *            A String with the publishing platform.
	 * @return a Long with the identifier of the plugin.
	 * @throws Exception
	 *             if any problem occurred accessing the database.
	 */
	abstract Long findOrCreatePlugin(String pluginId, String platform) throws Exception;

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
	 * @throws Exception
	 *             if any problem occurred accessing the database.
	 */
	abstract Long addAuMd(Long auSeq, int version, long extractTime)
			throws Exception;
	



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
}
