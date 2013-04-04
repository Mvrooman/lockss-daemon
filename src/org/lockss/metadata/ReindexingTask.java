package org.lockss.metadata;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import org.lockss.app.LockssDaemon;
import org.lockss.config.TdbAu;
import org.lockss.daemon.PluginException;
import org.lockss.db.SqlDbManager;
import org.lockss.extractor.ArticleMetadata;
import org.lockss.extractor.ArticleMetadataExtractor;
import org.lockss.extractor.MetadataField;
import org.lockss.extractor.MetadataTarget;
import org.lockss.extractor.ArticleMetadataExtractor.Emitter;
import org.lockss.extractor.MetadataException.ValidationException;
import org.lockss.metadata.ArticleMetadataBuffer.ArticleMetadataInfo;
import org.lockss.metadata.SqlMetadataManager.ReindexingStatus;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.ArticleFiles;
import org.lockss.plugin.AuUtil;
import org.lockss.scheduler.Schedule;
import org.lockss.scheduler.StepTask;
import org.lockss.util.Constants;
import org.lockss.util.Logger;
import org.lockss.util.TimeBase;
import org.lockss.util.TimeInterval;

public abstract class ReindexingTask extends StepTask {

	// The status of the task: successful if true.
	protected volatile ReindexingStatus status = ReindexingStatus.Running;

	protected static Logger log = Logger.getLogger(ReindexingTask.class);

	// The default number of steps for this task.
	protected static final int default_steps = 10;

	// The article iterator for this task.
	protected Iterator<ArticleFiles> articleIterator = null;

	protected int extractedCount = 0;

	// The archival unit for this task.
	protected final ArchivalUnit au;

	// The article metadata extractor for this task.
	protected final ArticleMetadataExtractor ae;

	// Archival unit properties.
	protected final String auName;
	protected final String auId;

	// The database manager.
	protected final SqlDbManager sqlDbManager;

	// The metadata manager.
	protected MetadataManager mdManager;

	protected final Emitter emitter;

	protected boolean auNoSubstance;

	// The number of articles indexed by this task.
	protected volatile long indexedArticleCount = 0;

	// The number of articles updated by this task.
	protected volatile long updatedArticleCount = 0;

	// An indication of whether the AU being indexed is new to the index.
	protected volatile boolean isNewAu = true;
	protected volatile long startClockTime = 0;
	protected volatile long startUpdateClockTime = 0;
	protected volatile long endClockTime = 0;

	// The hashes of the text strings of the log messages already emitted for
	// this
	// task's AU. Used to prevent duplicate messages from being logged.
	// The hashes of the text strings of the log messages already emitted for
	// this
	// task's AU. Used to prevent duplicate messages from being logged.
	protected final HashSet<Integer> auLogTable = new HashSet<Integer>();

	protected ArticleMetadataBuffer articleMetadataInfoBuffer = null;

	public ReindexingTask(ArchivalUnit theAu, ArticleMetadataExtractor theAe) {
		// NOTE: estimated window time interval duration not currently used.
		super(new TimeInterval(TimeBase.nowMs(), TimeBase.nowMs()
				+ Constants.HOUR), 0, // estimatedDuration.
				null, // TaskCallback.
				null); // Object cookie.

		this.au = theAu;
		this.ae = theAe;
		this.auName = au.getName();
		this.auId = au.getAuId();
		this.auNoSubstance = AuUtil.getAuState(au).hasNoSubstance();
		sqlDbManager = (SqlDbManager) LockssDaemon.getLockssDaemon().getDbManager();
		mdManager = LockssDaemon.getLockssDaemon().getMetadataManager();

		// The accumulator of article metadata.
		emitter = new ReindexingEmitter();

		// Set the task event handler callback after construction to ensure that
		// the
		// instance is initialized.

	}

	/**
	 * Cancels the current task without rescheduling it.
	 */
	@Override
	public void cancel() {
		if (!isFinished() && (status == ReindexingStatus.Running)) {
			status = ReindexingStatus.Failed;
			super.cancel();
			setFinished();
		}
	}

	/**
	 * Extracts the metadata from the next group of articles.
	 * 
	 * @param n
	 *            An int with the amount of work to do.
	 * @todo: figure out what the amount of work means
	 */
	@Override
	public int step(int n) {
		final String DEBUG_HEADER = "step(): ";
		int steps = (n <= 0) ? default_steps : n;
		log.debug3(DEBUG_HEADER + "step: " + steps + ", has articles: "
				+ articleIterator.hasNext());

		while (!isFinished() && (extractedCount <= steps)
				&& articleIterator.hasNext()) {
			log.debug3(DEBUG_HEADER + "Getting the next ArticleFiles...");
			ArticleFiles af = articleIterator.next();
			try {
				ae.extract(MetadataTarget.OpenURL(), af, emitter);
			} catch (IOException ex) {
				log.error(
						"Failed to index metadata for full text URL: "
								+ af.getFullTextUrl(), ex);
				setFinished();
				if (status == ReindexingStatus.Running) {
					status = ReindexingStatus.Rescheduled;
					indexedArticleCount = 0;
				}
			} catch (PluginException ex) {
				log.error(
						"Failed to index metadata for full text URL: "
								+ af.getFullTextUrl(), ex);
				setFinished();
				if (status == ReindexingStatus.Running) {
					status = ReindexingStatus.Failed;
					indexedArticleCount = 0;
				}
			} catch (RuntimeException ex) {
				log.error(" Caught unexpected Throwable for full text URL: "
						+ af.getFullTextUrl(), ex);
				setFinished();
				if (status == ReindexingStatus.Running) {
					status = ReindexingStatus.Failed;
					indexedArticleCount = 0;
				}
			}
		}

		log.debug3(DEBUG_HEADER + "isFinished() = " + isFinished());
		if (!isFinished()) {
			// finished if all articles handled
			if (!articleIterator.hasNext()) {
				setFinished();
				log.debug3(DEBUG_HEADER + "isFinished() = " + isFinished());
			}
		}

		log.debug3(DEBUG_HEADER + "extractedCount = " + extractedCount);
		return extractedCount;
	}

	/**
	 * Cancels and marks the current task for rescheduling.
	 */
	void reschedule() {
		if (!isFinished() && (status == ReindexingStatus.Running)) {
			status = ReindexingStatus.Rescheduled;
			super.cancel();
			setFinished();
		}
	}

	/**
	 * Returns the task AU.
	 * 
	 * @return an ArchivalUnit with the AU of this task.
	 */
	ArchivalUnit getAu() {
		return au;
	}

	/**
	 * Returns the name of the task AU.
	 * 
	 * @return a String with the name of the task AU.
	 */
	String getAuName() {
		return auName;
	}

	/**
	 * Returns the auid of the task AU.
	 * 
	 * @return a String with the auid of the task AU.
	 */
	String getAuId() {
		return auId;
	}

	/**
	 * Returns the substance state of the task AU.
	 * 
	 * @return <code>true</code> if AU has no substance, <code>false</code>
	 *         otherwise.
	 */
	boolean hasNoAuSubstance() {
		return auNoSubstance;
	}

	/**
	 * Returns an indication of whether the AU has not yet been indexed.
	 * 
	 * @return <code>true</code> if the AU ihas not yet been indexed,
	 *         <code>false</code> otherwise.
	 */
	boolean isNewAu() {
		return isNewAu;
	}

	/**
	 * Provides the start time for indexing.
	 * 
	 * @return a long with the start time in miliseconds since epoch (0 if not
	 *         started).
	 */
	long getStartTime() {
		return startClockTime;
	}

	/**
	 * Provides the update start time.
	 * 
	 * @return a long with the update start time in miliseconds since epoch (0
	 *         if not started).
	 */
	long getStartUpdateTime() {
		return startUpdateClockTime;
	}

	/**
	 * Provides the end time for indexing.
	 * 
	 * @return a long with the end time in miliseconds since epoch (0 if not
	 *         finished).
	 */
	long getEndTime() {
		return endClockTime;
	}

	/**
	 * Returns the reindexing status of this task.
	 * 
	 * @return a ReindexingStatus with the reindexing status.
	 */
	ReindexingStatus getReindexingStatus() {
		return status;
	}

	/**
	 * Returns the number of articles extracted by this task.
	 * 
	 * @return a long with the number of articles extracted by this task.
	 */
	long getIndexedArticleCount() {
		return indexedArticleCount;
	}

	/**
	 * Returns the number of articles updated by this task.
	 * 
	 * @return a long with the number of articles updated by this task.
	 */
	long getUpdatedArticleCount() {
		return updatedArticleCount;
	}

	/**
	 * Increments by one the number of articles updated by this task.
	 */
	void incrementUpdatedArticleCount() {
		this.updatedArticleCount++;
	}

	/**
	 * Temporary
	 * 
	 * @param evt
	 */
	protected void handleEvent(Schedule.EventType evt) {
		try {
			callback.taskEvent(this, evt);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Issues a warning for this re-indexing task.
	 * 
	 * @param s
	 *            A String with the warning message.
	 */
	void taskWarning(String s) {
		int hashcode = s.hashCode();
		if (auLogTable.add(hashcode)) {
			log.warning(s);
		}
	}

	/**
	 * Accumulator of article metadata.
	 */
	private class ReindexingEmitter implements Emitter {
		private final Logger log = Logger.getLogger(ReindexingEmitter.class);

		@Override
		public void emitMetadata(ArticleFiles af, ArticleMetadata md) {
			final String DEBUG_HEADER = "emitMetadata(): ";

			if (log.isDebug3()) {
				log.debug3(DEBUG_HEADER + "\n" + md.ppString(2));
			}

			Map<String, String> roles = new HashMap<String, String>();

			for (String key : af.getRoleMap().keySet()) {
				String value = af.getRoleUrl(key);
				log.debug3(DEBUG_HEADER + "af.getRoleMap().key = " + key
						+ ", af.getRoleUrl(key) = " + value);
				roles.put(key, value);
			}

			if (log.isDebug3()) {
				log.debug3(DEBUG_HEADER + "field access url: "
						+ md.get(MetadataField.FIELD_ACCESS_URL));
			}

			if (md.get(MetadataField.FIELD_ACCESS_URL) == null) {
				// temporary -- use full text url if not set
				// (should be set by metadata extractor)
				md.put(MetadataField.FIELD_ACCESS_URL, af.getFullTextUrl());
			}

			md.putRaw(MetadataField.FIELD_FEATURED_URL_MAP.getKey(), roles);

			try {
				validateDataAgainstTdb(new ArticleMetadataInfo(md), au);
				articleMetadataInfoBuffer.add(md);
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}

			extractedCount++;
			indexedArticleCount++;
		}

		/**
		 * Validate data against TDB information.
		 * 
		 * @param mdinfo
		 *            the ArticleMetadataInfo
		 * @param au
		 *            An ArchivalUnit with the archival unit.
		 * @throws ValidationException
		 *             if field is invalid
		 */
		private void validateDataAgainstTdb(ArticleMetadataInfo mdinfo,
				ArchivalUnit au) {
			HashSet<String> isbns = new HashSet<String>();
			if (mdinfo.isbn != null) {
				isbns.add(mdinfo.isbn);
			}

			if (mdinfo.eisbn != null) {
				isbns.add(mdinfo.eisbn);
			}

			HashSet<String> issns = new HashSet<String>();
			if (mdinfo.issn != null) {
				issns.add(mdinfo.issn);
			}

			if (mdinfo.eissn != null) {
				issns.add(mdinfo.eissn);
			}

			TdbAu tdbau = au.getTdbAu();
			boolean isTitleInTdb = !au.isBulkContent();
			String tdbauName = (tdbau == null) ? null : tdbau.getName();
			String tdbauStartYear = (tdbau == null) ? au.getName() : tdbau
					.getStartYear();
			String tdbauYear = (tdbau == null) ? null : tdbau.getYear();
			String tdbauIsbn = null;
			String tdbauIssn = null;
			String tdbauEissn = null;
			String tdbauJournalTitle = null;

			// Check whether the TDB has title information.
			if (isTitleInTdb && (tdbau != null)) {
				// Yes: Get the title information from the TDB.
				tdbauIsbn = tdbau.getIsbn();
				tdbauIssn = tdbau.getPrintIssn();
				tdbauEissn = tdbau.getEissn();
				tdbauJournalTitle = tdbau.getJournalTitle();
			}

			if (tdbau != null) {
				// Validate journal title against the TDB journal title.
				if (tdbauJournalTitle != null) {
					if (!tdbauJournalTitle.equals(mdinfo.journalTitle)) {
						if (mdinfo.journalTitle == null) {
							taskWarning("tdb title  is " + tdbauJournalTitle
									+ " for " + tdbauName
									+ " -- metadata title is missing");
						} else {
							taskWarning("tdb title "
									+ tdbauJournalTitle
									+ " for "
									+ tdbauName
									+ " -- does not match metadata journal title "
									+ mdinfo.journalTitle);
						}
					}
				}

				// Validate ISBN against the TDB ISBN.
				if (tdbauIsbn != null) {
					if (!tdbauIsbn.equals(mdinfo.isbn)) {
						isbns.add(tdbauIsbn);
						if (mdinfo.isbn == null) {
							taskWarning("using tdb isbn " + tdbauIsbn + " for "
									+ tdbauName + " -- metadata isbn missing");
						} else {
							taskWarning("also using tdb isbn " + tdbauIsbn
									+ " for " + tdbauName
									+ " -- different than metadata isbn: "
									+ mdinfo.isbn);
						}
					} else if (mdinfo.isbn != null) {
						taskWarning("tdb isbn missing for " + tdbauName
								+ " -- should be: " + mdinfo.isbn);
					}
				} else if (mdinfo.isbn != null) {
					if (isTitleInTdb) {
						taskWarning("tdb isbn missing for " + tdbauName
								+ " -- should be: " + mdinfo.isbn);
					}
				}

				// validate ISSN against the TDB ISSN.
				if (tdbauIssn != null) {
					if (tdbauIssn.equals(mdinfo.eissn) && (mdinfo.issn == null)) {
						taskWarning("tdb print issn " + tdbauIssn + " for "
								+ tdbauName
								+ " -- reported by metadata as eissn");
					} else if (!tdbauIssn.equals(mdinfo.issn)) {
						// add both ISSNs so it can be found either way
						issns.add(tdbauIssn);
						if (mdinfo.issn == null) {
							taskWarning("using tdb print issn " + tdbauIssn
									+ " for " + tdbauName
									+ " -- metadata print issn is missing");
						} else {
							taskWarning("also using tdb print issn "
									+ tdbauIssn
									+ " for "
									+ tdbauName
									+ " -- different than metadata print issn: "
									+ mdinfo.issn);
						}
					}
				} else if (mdinfo.issn != null) {
					if (mdinfo.issn.equals(tdbauEissn)) {
						taskWarning("tdb eissn " + tdbauEissn + " for "
								+ tdbauName
								+ " -- reported by metadata as print issn");
					} else if (isTitleInTdb) {
						taskWarning("tdb issn missing for " + tdbauName
								+ " -- should be: " + mdinfo.issn);
					}
				}

				// Validate EISSN against the TDB EISSN.
				if (tdbauEissn != null) {
					if (tdbauEissn.equals(mdinfo.issn)
							&& (mdinfo.eissn == null)) {
						taskWarning("tdb eissn " + tdbauEissn + " for "
								+ tdbauName
								+ " -- reported by metadata as print issn");
					} else if (!tdbauEissn.equals(mdinfo.eissn)) {
						// Add both ISSNs so that they can be found either way.
						issns.add(tdbauEissn);
						if (mdinfo.eissn == null) {
							taskWarning("using tdb eissn " + tdbauEissn
									+ " for " + tdbauName
									+ " -- metadata eissn is missing");
						} else {
							taskWarning("also using tdb eissn " + tdbauEissn
									+ " for " + tdbauName
									+ " -- different than metadata eissn: "
									+ mdinfo.eissn);
						}
					}
				} else if (mdinfo.eissn != null) {
					if (mdinfo.eissn.equals(tdbauIssn)) {
						taskWarning("tdb print issn " + tdbauIssn + " for "
								+ tdbauName
								+ " -- reported by metadata as print eissn");
					} else if (isTitleInTdb) {
						taskWarning("tdb eissn missing for " + tdbauName
								+ " -- should be: " + mdinfo.eissn);
					}
				}

				// Validate publication date against the TDB year.
				String pubYear = mdinfo.pubYear;
				if (pubYear != null) {
					if (!tdbau.includesYear(mdinfo.pubYear)) {
						if (tdbauYear != null) {
							taskWarning("tdb year " + tdbauYear + " for "
									+ tdbauName
									+ " -- does not match metadata year "
									+ pubYear);
						} else {
							taskWarning("tdb year missing for " + tdbauName
									+ " -- should include year " + pubYear);
						}
					}
				} else {
					pubYear = tdbauStartYear;
					if (mdinfo.pubYear != null) {
						taskWarning("using tdb start year " + mdinfo.pubYear
								+ " for " + tdbauName
								+ " -- metadata year is missing");
					}
				}
			}
		}
	}

}
