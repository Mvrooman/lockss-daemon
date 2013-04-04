/*
 * $Id: SqlReindexingTask.java,v 1.3 2013/01/06 06:36:32 tlipkis Exp $
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

import static org.lockss.metadata.SqlMetadataManager.*;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Iterator;
import org.lockss.db.SqlDbManager;
import org.lockss.extractor.ArticleMetadataExtractor;
import org.lockss.extractor.MetadataTarget;
import org.lockss.metadata.ArticleMetadataBuffer.ArticleMetadataInfo;
import org.lockss.metadata.SqlMetadataManager.ReindexingStatus;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.PluginManager;
import org.lockss.scheduler.SchedulableTask;
import org.lockss.scheduler.Schedule;
import org.lockss.scheduler.TaskCallback;
import org.lockss.util.Logger;
import org.lockss.util.TimeBase;

/**
 * Implements a reindexing task that extracts metadata from all the articles in
 * the specified AU.
 */
public class SqlReindexingTask extends ReindexingTask {

	// The status of the task: successful if true.
	protected volatile ReindexingStatus status = ReindexingStatus.Running;

	// ThreadMXBean times.
	private volatile long startCpuTime = 0;
	private volatile long startUserTime = 0;

	private volatile long endCpuTime = 0;
	private volatile long endUserTime = 0;

	private static ThreadMXBean tmxb = ManagementFactory.getThreadMXBean();
	private final SqlMetadataManager sqlMetadataManager;
	static {
		log.debug3("current thread CPU time supported? "
				+ tmxb.isCurrentThreadCpuTimeSupported());

		if (tmxb.isCurrentThreadCpuTimeSupported()) {
			tmxb.setThreadCpuTimeEnabled(true);
		}
	}

	/**
	 * Constructor.
	 * 
	 * @param theAu
	 *            An ArchivalUnit with the AU for the task.
	 * @param theAe
	 *            An ArticleMetadataExtractor with the article metadata
	 *            extractor to be used.
	 */
	public SqlReindexingTask(ArchivalUnit theAu, ArticleMetadataExtractor theAe) {
		// NOTE: estimated window time interval duration not currently used.
		super(theAu, theAe);
		this.sqlMetadataManager = (SqlMetadataManager)this.mdManager;
		callback = new ReindexingEventHandler();
	}

	/**
	 * The handler for reindexing lifecycle events.
	 */
	private class ReindexingEventHandler implements TaskCallback {
		private final Logger log = Logger
				.getLogger(ReindexingEventHandler.class);

		/**
		 * Handles an event.
		 * 
		 * @param task
		 *            A SchedulableTask with the task that has changed state.
		 * @param type
		 *            A Schedule.EventType indicating the type of event.
		 * @throws Exception 
		 */
		@Override
		public void taskEvent(SchedulableTask task, Schedule.EventType type) throws Exception {
			long threadCpuTime = 0;
			long threadUserTime = 0;
			long currentClockTime = TimeBase.nowMs();

			if (tmxb.isCurrentThreadCpuTimeSupported()) {
				threadCpuTime = tmxb.getCurrentThreadCpuTime();
				threadUserTime = tmxb.getCurrentThreadUserTime();
			}

			// TODO: handle task Success vs. failure?
			if (type == Schedule.EventType.START) {
				// Handle the start event.
				handleStartEvent(threadCpuTime, threadUserTime,
						currentClockTime);
			} else if (type == Schedule.EventType.FINISH) {
				// Handle the finish event.
				handleFinishEvent(task, threadCpuTime, threadUserTime,
						currentClockTime);
			} else {
				log.error("Received unknown reindexing lifecycle event type '"
						+ type + "' for AU '" + auName + "' - Ignored.");
			}
		}

		/**
		 * Handles a starting event.
		 * 
		 * @param threadCpuTime
		 *            A long with the thread CPU time.
		 * @param threadUserTime
		 *            A long with the thread user time.
		 * @param currentClockTime
		 *            A long with the current clock time.
		 */
		private void handleStartEvent(long threadCpuTime, long threadUserTime,
				long currentClockTime) {
			final String DEBUG_HEADER = "handleStartEvent(): ";
			log.debug3(DEBUG_HEADER + "Starting to reindex AU: " + auName);

			// Remember the times at startup.
			startCpuTime = threadCpuTime;
			startUserTime = threadUserTime;
			startClockTime = currentClockTime;

			if (log.isDebug2()) {
				log.debug2(DEBUG_HEADER + "Reindexing task start for AU: "
						+ au.getName() + " startCpuTime: " + startCpuTime
						/ 1.0e9 + ", startUserTime: " + startUserTime / 1.0e9
						+ ", startClockTime: " + startClockTime / 1.0e3);
			}

			long lastExtractionTime = NEVER_EXTRACTED_EXTRACTION_TIME;
			Connection conn = null;
			String message = null;
			boolean needsIncrementalExtraction = false;

			try {
				// Get a connection to the database.
				message = "Cannot obtain a database connection";
				conn = sqlDbManager.getConnection();

				// Get the AU database identifier, if any.
				message = "Cannot find the AU identifier for AU = " + auId
						+ " in the database";
				Long auSeq = findAuSeq(conn);
				log.debug2(DEBUG_HEADER + "auSeq = " + auSeq);

				// Determine whether this is a new, never indexed, AU;
				isNewAu = auSeq == null;
				log.debug2(DEBUG_HEADER + "isNewAu = " + isNewAu);

				// Check whether this same AU has been indexed before.
				if (!isNewAu) {
					// Yes: Determine whether an incremental extraction is
					// needed.
					message = "Cannot determine whther the metadata for AU = "
							+ auSeq + " was extracted with an obsolete plugin";

					needsIncrementalExtraction = !sqlMetadataManager
							.isAuMetadataForObsoletePlugin(conn, au);
					log.debug2(DEBUG_HEADER + "needsIncrementalExtraction = "
							+ needsIncrementalExtraction);

					// Check whether an incremental extraction is needed.
					if (needsIncrementalExtraction) {
						// Yes: Get the last extraction time for the AU.
						message = "Cannot find the last extraction time for AU = "
								+ auSeq + " in the database";

						lastExtractionTime = sqlMetadataManager.getAuExtractionTime(
								conn, auSeq);
						log.debug2(DEBUG_HEADER + "lastExtractionTime = "
								+ lastExtractionTime);
					}
				}
			} catch (SQLException sqle) {
				log.error(message + ": " + sqle);
			} finally {
				SqlDbManager.safeRollbackAndClose(conn);
			}
			// Indicate that only new metadata after the last extraction is to
			// be
			// included.
			MetadataTarget target = MetadataTarget.OpenURL();

			// Check whether an incremental extraction is needed.
			if (needsIncrementalExtraction) {
				target.setIncludeFilesChangedAfter(lastExtractionTime);
			}

			// The article iterator won't be null because only AUs with article
			// iterators are queued for processing.
			articleIterator = au.getArticleIterator(target);

			if (log.isDebug2()) {
				long articleIteratorInitTime = TimeBase.nowMs()
						- startClockTime;
				log.debug2(DEBUG_HEADER + "Starting reindexing task for au: "
						+ au.getName() + " has articles? "
						+ articleIterator.hasNext()
						+ " initializing iterator took "
						+ articleIteratorInitTime + "ms");
			}

			try {
				articleMetadataInfoBuffer = new ArticleMetadataBuffer();
				sqlMetadataManager.notifyStartReindexingAu(au);
			} catch (IOException ioe) {
				log.error("Failed to set up pending AU '" + au.getName()
						+ "' for re-indexing", ioe);
				setFinished();
				if (status == ReindexingStatus.Running) {
					status = ReindexingStatus.Rescheduled;
				}
			}
		}

		/**
		 * Handles a finishing event.
		 * 
		 * @param task
		 *            A SchedulableTask with the task that has finished.
		 * @param threadCpuTime
		 *            A long with the thread CPU time.
		 * @param threadUserTime
		 *            A long with the thread user time.
		 * @param currentClockTime
		 *            A long with the current clock time.
		 * @throws Exception 
		 */
		private void handleFinishEvent(SchedulableTask task,
				long threadCpuTime, long threadUserTime, long currentClockTime) throws Exception {
			final String DEBUG_HEADER = "handleFinishEvent(): ";
			log.debug3(DEBUG_HEADER + "Finishing reindexing (" + status
					+ ") for AU: " + auName);

			if (status == ReindexingStatus.Running) {
				status = ReindexingStatus.Success;
			}

			Connection conn = null;
			startUpdateClockTime = currentClockTime;

			switch (status) {
			case Success:

				try {
					long removedArticleCount = 0L;

					// Get a connection to the database.
					conn = sqlDbManager.getConnection();

					// Check whether the plugin version used to obtain the
					// metadata
					// stored in the database is older than the current plugin
					// version.
					if (sqlMetadataManager.isAuMetadataForObsoletePlugin(conn, au)) {
						// Yes: Remove old AU metadata before adding new.
						removedArticleCount = sqlMetadataManager.removeAuMetadataItems(
								conn, auId);
						log.debug3(DEBUG_HEADER + "removedArticleCount = "
								+ removedArticleCount);
					}

					Iterator<ArticleMetadataInfo> mditr = articleMetadataInfoBuffer
							.iterator();

					// Check whether there is any metadata to record.
					if (mditr.hasNext()) {

						// Yes: Write the AU metadata to the database.
						new SqlAuMetadataRecorder((SqlReindexingTask) task,
								sqlMetadataManager, au).recordMetadata(conn, mditr);

					}

					// Remove the AU just re-indexed from the list of AUs
					// pending to be
					// re-indexed.
					sqlMetadataManager.removeFromPendingAus(conn, auId);

					// Complete the database transaction.
					conn.commit();

					// Update the successful re-indexing count.
					sqlMetadataManager.incrementSuccessfulReindexingCount();

					// Update the total article count.
					sqlMetadataManager.addToMetadataArticleCount(updatedArticleCount
							- removedArticleCount);

					break;
				} catch (SQLException sqle) {
					log.warning("Error updating metadata at FINISH for "
							+ status + " -- rescheduling", sqle);
					status = ReindexingStatus.Rescheduled;
				} finally {
					SqlDbManager.safeRollbackAndClose(conn);
				}

				// Fall through if SQL exception occurred during update.
			case Failed:
			case Rescheduled:

				sqlMetadataManager.incrementFailedReindexingCount();

				// Reindexing not successful, so try again later.
				// if status indicates the operation should be rescheduled
				log.debug2(DEBUG_HEADER + "Reindexing task (" + status
						+ ") did not finish for au " + au.getName());

				try {
					// Get a connection to the database.
					conn = sqlDbManager.getConnection();

					// Attempt to move failed AU to end of pending list.
					sqlMetadataManager.removeFromPendingAus(conn, au.getAuId());

					if (status == ReindexingStatus.Rescheduled) {
						log.debug2(DEBUG_HEADER
								+ "Rescheduling reindexing task au "
								+ au.getName());
						sqlMetadataManager.addToPendingAus(conn,
								Collections.singleton(au));
					}

					// Complete the database transaction.
					conn.commit();
				} catch (SQLException sqle) {
					log.warning("Error updating pending queue at FINISH"
							+ " for " + status, sqle);
				} finally {
					SqlDbManager.safeRollbackAndClose(conn);
				}
			}

			articleIterator = null;
			endClockTime = TimeBase.nowMs();

			if (tmxb.isCurrentThreadCpuTimeSupported()) {
				endCpuTime = tmxb.getCurrentThreadCpuTime();
				endUserTime = tmxb.getCurrentThreadUserTime();
			}

			// Display timings.
			if (log.isDebug2()) {
				long elapsedCpuTime = threadCpuTime - startCpuTime;
				long elapsedUserTime = threadUserTime - startUserTime;
				long elapsedClockTime = currentClockTime - startClockTime;

				log.debug2(DEBUG_HEADER + "Reindexing task finished (" + status
						+ ") for au: " + au.getName() + " CPU time: "
						+ elapsedCpuTime / 1.0e9 + " (" + endCpuTime / 1.0e9
						+ "), UserTime: " + elapsedUserTime / 1.0e9 + " ("
						+ endUserTime / 1.0e9 + ") Clock time: "
						+ elapsedClockTime / 1.0e3 + " (" + endClockTime
						/ 1.0e3 + ")");
			}

			// Release collected metadata info once finished.
			articleMetadataInfoBuffer.close();
			articleMetadataInfoBuffer = null;

			synchronized (sqlMetadataManager.activeReindexingTasks) {
				sqlMetadataManager.activeReindexingTasks.remove(au.getAuId());
				sqlMetadataManager.notifyFinishReindexingAu(au, status);

				try {
					// Get a connection to the database.
					conn = sqlDbManager.getConnection();

					// Schedule another task if available.
					sqlMetadataManager.startReindexing(conn);

					// Complete the database transaction.
					conn.commit();
				} catch (SQLException sqle) {
					log.error("Cannot restart indexing", sqle);
				} finally {
					SqlDbManager.safeRollbackAndClose(conn);
				}
			}
		}

		/**
		 * Provides the identifier of the AU for this task.
		 * 
		 * @param conn
		 *            A Connection with the database connection to be used.
		 * @return a Long with the identifier of the AU for this task, if any.
		 * @throws SQLException
		 *             if any problem occurred accessing the database.
		 */
		private Long findAuSeq(Connection conn) throws SQLException {
			final String DEBUG_HEADER = "findAuSeq(): ";

			Long auSeq = null;

			// Find the plugin.
			Long pluginSeq = sqlMetadataManager.findPlugin(conn,
					PluginManager.pluginIdFromAuId(auId));

			// Check whether the plugin exists.
			if (pluginSeq != null) {
				// Yes: Get the database identifier of the AU.
				String auKey = PluginManager.auKeyFromAuId(auId);

				auSeq = sqlMetadataManager.findAu(conn, pluginSeq, auKey);
				log.debug2(DEBUG_HEADER + "auSeq = " + auSeq);
			}

			return auSeq;
		}
	}
}
