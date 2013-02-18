/*
 * $Id: ReindexingTask.java,v 1.3 2013/01/06 06:36:32 tlipkis Exp $
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

import static org.lockss.metadata.MetadataManager.*;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import org.lockss.app.LockssDaemon;
import org.lockss.config.TdbAu;
import org.lockss.daemon.PluginException;
import org.lockss.db.DbManager;
import org.lockss.extractor.ArticleMetadata;
import org.lockss.extractor.ArticleMetadataExtractor;
import org.lockss.extractor.MetadataField;
import org.lockss.extractor.MetadataTarget;
import org.lockss.extractor.ArticleMetadataExtractor.Emitter;
import org.lockss.extractor.MetadataException.ValidationException;
import org.lockss.metadata.ArticleMetadataBuffer.ArticleMetadataInfo;
import org.lockss.metadata.MetadataManager.ReindexingStatus;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.ArticleFiles;
import org.lockss.plugin.AuUtil;
import org.lockss.plugin.PluginManager;
import org.lockss.scheduler.SchedulableTask;
import org.lockss.scheduler.Schedule;
import org.lockss.scheduler.StepTask;
import org.lockss.scheduler.TaskCallback;
import org.lockss.util.Constants;
import org.lockss.util.Logger;
import org.lockss.util.TimeBase;
import org.lockss.util.TimeInterval;

/**
 * Implements a reindexing task that extracts metadata from all the articles in
 * the specified AU.
 */
public class ReindexingTask extends StepTask {
  private static Logger log = Logger.getLogger(ReindexingTask.class);

  // The default number of steps for this task.
  private static final int default_steps = 10;

  // The archival unit for this task.
  private final ArchivalUnit au;

  // The article metadata extractor for this task.
  private final ArticleMetadataExtractor ae;

  // The article iterator for this task.
  private Iterator<ArticleFiles> articleIterator = null;

  // The hashes of the text strings of the log messages already emitted for this
  // task's AU. Used to prevent duplicate messages from being logged.
  private final HashSet<Integer> auLogTable = new HashSet<Integer>();

  // An indication of whether the AU being indexed is new to the index.
  private volatile boolean isNewAu = true;

  // The status of the task: successful if true.
  private volatile ReindexingStatus status = ReindexingStatus.Running;

  // The number of articles indexed by this task.
  private volatile long indexedArticleCount = 0;

  // The number of articles updated by this task.
  private volatile long updatedArticleCount = 0;

  // ThreadMXBean times.
  private volatile long startCpuTime = 0;
  private volatile long startUserTime = 0;
  private volatile long startClockTime = 0;

  private volatile long startUpdateClockTime = 0;

  private volatile long endCpuTime = 0;
  private volatile long endUserTime = 0;
  private volatile long endClockTime = 0;

  // Archival unit properties.
  private final String auName;
  private final String auId;
  private final boolean auNoSubstance;

  private ArticleMetadataBuffer articleMetadataInfoBuffer = null;

  // The database manager.
  private final DbManager dbManager;

  // The metadata manager.
  private final MetadataManager mdManager;
  private final MetadataManager mongoMdManager; //MONGOSVC

  private final Emitter emitter;
  private int extractedCount = 0;

  private static ThreadMXBean tmxb = ManagementFactory.getThreadMXBean();

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
   *          An ArchivalUnit with the AU for the task.
   * @param theAe
   *          An ArticleMetadataExtractor with the article metadata extractor to
   *          be used.
   */
  public ReindexingTask(ArchivalUnit theAu, ArticleMetadataExtractor theAe) {
    // NOTE: estimated window time interval duration not currently used.
    super(
	  new TimeInterval(TimeBase.nowMs(), TimeBase.nowMs() + Constants.HOUR),
	  0, // estimatedDuration.
	  null, // TaskCallback.
	  null); // Object cookie.

    this.au = theAu;
    this.ae = theAe;
    this.auName = au.getName();
    this.auId = au.getAuId();
    this.auNoSubstance = AuUtil.getAuState(au).hasNoSubstance();
    dbManager = LockssDaemon.getLockssDaemon().getDbManager();
    mdManager = LockssDaemon.getLockssDaemon().getMetadataManager();
    mongoMdManager = LockssDaemon.getLockssDaemon().getMongoMetadataManager(); //MONGOSVC

    // The accumulator of article metadata.
    emitter = new ReindexingEmitter();

    // Set the task event handler callback after construction to ensure that the
    // instance is initialized.
    callback = new ReindexingEventHandler();
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
   *          An int with the amount of work to do.
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
	log.error("Failed to index metadata for full text URL: "
		      + af.getFullTextUrl(), ex);
	setFinished();
	if (status == ReindexingStatus.Running) {
	  status = ReindexingStatus.Rescheduled;
	  indexedArticleCount = 0;
	}
      } catch (PluginException ex) {
	log.error("Failed to index metadata for full text URL: "
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
   * <code>false</code> otherwise.
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
   * @return a long with the update start time in miliseconds since epoch (0 if
   *         not started).
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
    callback.taskEvent(this, evt);
  }

  /**
   * Issues a warning for this re-indexing task.
   * 
   * @param s
   *          A String with the warning message.
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
        log.debug3(DEBUG_HEADER+"\n"+md.ppString(2));
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
     *          the ArticleMetadataInfo
     * @param au
     *          An ArchivalUnit with the archival unit.
     * @throws ValidationException
     *           if field is invalid
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
      String tdbauStartYear =
	  (tdbau == null) ? au.getName() : tdbau.getStartYear();
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
	      taskWarning("tdb title  is " + tdbauJournalTitle + " for "
		  + tdbauName + " -- metadata title is missing");
	    } else {
	      taskWarning("tdb title " + tdbauJournalTitle + " for "
		  + tdbauName + " -- does not match metadata journal title "
		  + mdinfo.journalTitle);
	    }
	  }
	}

	// Validate ISBN against the TDB ISBN.
	if (tdbauIsbn != null) {
	  if (!tdbauIsbn.equals(mdinfo.isbn)) {
	    isbns.add(tdbauIsbn);
	    if (mdinfo.isbn == null) {
	      taskWarning("using tdb isbn " + tdbauIsbn + " for " + tdbauName
		  + " -- metadata isbn missing");
	    } else {
	      taskWarning("also using tdb isbn " + tdbauIsbn + " for "
		  + tdbauName + " -- different than metadata isbn: "
		  + mdinfo.isbn);
	    }
	  } else if (mdinfo.isbn != null) {
	    taskWarning("tdb isbn missing for " + tdbauName + " -- should be: "
		+ mdinfo.isbn);
	  }
	} else if (mdinfo.isbn != null) {
	  if (isTitleInTdb) {
	    taskWarning("tdb isbn missing for " + tdbauName + " -- should be: "
		+ mdinfo.isbn);
	  }
	}

	// validate ISSN against the TDB ISSN.
	if (tdbauIssn != null) {
	  if (tdbauIssn.equals(mdinfo.eissn) && (mdinfo.issn == null)) {
	    taskWarning("tdb print issn " + tdbauIssn + " for " + tdbauName
		+ " -- reported by metadata as eissn");
	  } else if (!tdbauIssn.equals(mdinfo.issn)) {
	    // add both ISSNs so it can be found either way
	    issns.add(tdbauIssn);
	    if (mdinfo.issn == null) {
	      taskWarning("using tdb print issn " + tdbauIssn + " for "
		  + tdbauName + " -- metadata print issn is missing");
	    } else {
	      taskWarning("also using tdb print issn " + tdbauIssn + " for "
		  + tdbauName + " -- different than metadata print issn: "
		  + mdinfo.issn);
	    }
	  }
	} else if (mdinfo.issn != null) {
	  if (mdinfo.issn.equals(tdbauEissn)) {
	    taskWarning("tdb eissn " + tdbauEissn + " for " + tdbauName
		+ " -- reported by metadata as print issn");
	  } else if (isTitleInTdb) {
	    taskWarning("tdb issn missing for " + tdbauName + " -- should be: "
		+ mdinfo.issn);
	  }
	}

	// Validate EISSN against the TDB EISSN.
	if (tdbauEissn != null) {
	  if (tdbauEissn.equals(mdinfo.issn) && (mdinfo.eissn == null)) {
	    taskWarning("tdb eissn " + tdbauEissn + " for " + tdbauName
		+ " -- reported by metadata as print issn");
	  } else if (!tdbauEissn.equals(mdinfo.eissn)) {
	    // Add both ISSNs so that they can be found either way.
	    issns.add(tdbauEissn);
	    if (mdinfo.eissn == null) {
	      taskWarning("using tdb eissn " + tdbauEissn + " for " + tdbauName
		  + " -- metadata eissn is missing");
	    } else {
	      taskWarning("also using tdb eissn " + tdbauEissn + " for "
		  + tdbauName + " -- different than metadata eissn: "
		  + mdinfo.eissn);
	    }
	  }
	} else if (mdinfo.eissn != null) {
	  if (mdinfo.eissn.equals(tdbauIssn)) {
	    taskWarning("tdb print issn " + tdbauIssn + " for " + tdbauName
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
	      taskWarning("tdb year " + tdbauYear + " for " + tdbauName
		  + " -- does not match metadata year " + pubYear);
	    } else {
	      taskWarning("tdb year missing for " + tdbauName
		  + " -- should include year " + pubYear);
	    }
	  }
	} else {
	  pubYear = tdbauStartYear;
	  if (mdinfo.pubYear != null) {
	    taskWarning("using tdb start year " + mdinfo.pubYear + " for "
		+ tdbauName + " -- metadata year is missing");
	  }
	}
      }
    }
  }

  /**
   * The handler for reindexing lifecycle events.
   */
  private class ReindexingEventHandler implements TaskCallback {
    private final Logger log = Logger.getLogger(ReindexingEventHandler.class);

    /**
     * Handles an event.
     * 
     * @param task
     *          A SchedulableTask with the task that has changed state.
     * @param type
     *          A Schedule.EventType indicating the type of event.
     */
    @Override
    public void taskEvent(SchedulableTask task, Schedule.EventType type) {
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
	handleStartEvent(threadCpuTime, threadUserTime, currentClockTime);
      } else if (type == Schedule.EventType.FINISH) {
	// Handle the finish event.
	handleFinishEvent(task, threadCpuTime, threadUserTime,
	                  currentClockTime);
      } else {
	log.error("Received unknown reindexing lifecycle event type '" + type
	    + "' for AU '" + auName + "' - Ignored.");
      }
    }

    /**
     * Handles a starting event.
     * 
     * @param threadCpuTime
     *          A long with the thread CPU time.
     * @param threadUserTime
     *          A long with the thread user time.
     * @param currentClockTime
     *          A long with the current clock time.
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
	    + au.getName() + " startCpuTime: " + startCpuTime / 1.0e9
	    + ", startUserTime: " + startUserTime / 1.0e9
	    + ", startClockTime: " + startClockTime / 1.0e3);
      }

      long lastExtractionTime = NEVER_EXTRACTED_EXTRACTION_TIME;
      Connection conn = null;
      String message = null;
      boolean needsIncrementalExtraction = false;

      try {
	// Get a connection to the database.
	message = "Cannot obtain a database connection";
	conn = dbManager.getConnection();

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
	  // Yes: Determine whether an incremental extraction is needed.
	  message = "Cannot determine whther the metadata for AU = " + auSeq
	      + " was extracted with an obsolete plugin";

	  needsIncrementalExtraction =
	      !mdManager.isAuMetadataForObsoletePlugin(conn, au);
	  log.debug2(DEBUG_HEADER + "needsIncrementalExtraction = "
	      + needsIncrementalExtraction);

	  // Check whether an incremental extraction is needed.
	  if (needsIncrementalExtraction) {
	    // Yes: Get the last extraction time for the AU.
	    message =
		"Cannot find the last extraction time for AU = " + auSeq
		    + " in the database";

	    lastExtractionTime = mdManager.getAuExtractionTime(conn, auSeq);
	    log.debug2(DEBUG_HEADER + "lastExtractionTime = "
		+ lastExtractionTime);
	  }
	}
      } catch (SQLException sqle) {
	log.error(message + ": " + sqle);
      } finally {
	DbManager.safeRollbackAndClose(conn);
      }
      // Indicate that only new metadata after the last extraction is to be
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
	long articleIteratorInitTime = TimeBase.nowMs() - startClockTime;
	log.debug2(DEBUG_HEADER + "Starting reindexing task for au: "
	    + au.getName() + " has articles? " + articleIterator.hasNext()
	    + " initializing iterator took " + articleIteratorInitTime + "ms");
      }

      try {
	articleMetadataInfoBuffer = new ArticleMetadataBuffer();
	mdManager.notifyStartReindexingAu(au);
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
     *          A SchedulableTask with the task that has finished.
     * @param threadCpuTime
     *          A long with the thread CPU time.
     * @param threadUserTime
     *          A long with the thread user time.
     * @param currentClockTime
     *          A long with the current clock time.
     */
    private void handleFinishEvent(SchedulableTask task, long threadCpuTime,
	long threadUserTime, long currentClockTime) {
      final String DEBUG_HEADER = "handleFinishEvent(): ";
      log.debug3(DEBUG_HEADER + "Finishing reindexing (" + status + ") for AU: "
	  + auName);

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
	    conn = dbManager.getConnection();

	    // Check whether the plugin version used to obtain the metadata
	    // stored in the database is older than the current plugin version.
	    if (mdManager.isAuMetadataForObsoletePlugin(conn, au)) {
	      // Yes: Remove old AU metadata before adding new.
	      removedArticleCount = mdManager.removeAuMetadataItems(conn, auId);
	      log.debug3(DEBUG_HEADER + "removedArticleCount = "
		  + removedArticleCount);
	    }

	    Iterator<ArticleMetadataInfo> mditr =
		articleMetadataInfoBuffer.iterator();

	    // Check whether there is any metadata to record.
	    if (mditr.hasNext()) {

	      // Yes: Write the AU metadata to the database.
//	      new AuMetadataRecorder((ReindexingTask) task, mdManager, au)
//		  .recordMetadata(conn, mditr);


          //record in parallel  MONGOSVC
          new MongoAuMetadataRecorder((ReindexingTask) task, mongoMdManager, au)
          .recordMetadata(conn, mditr);

	    }

	    // Remove the AU just re-indexed from the list of AUs pending to be
	    // re-indexed.
	    mdManager.removeFromPendingAus(conn, auId);

	    // Complete the database transaction.
	    conn.commit();

	    // Update the successful re-indexing count.
	    mdManager.incrementSuccessfulReindexingCount();

	    // Update the total article count.
	    mdManager.addToMetadataArticleCount(updatedArticleCount
		- removedArticleCount);

	    break;
	  } catch (SQLException sqle) {
	    log.warning("Error updating metadata at FINISH for " + status
		+ " -- rescheduling", sqle);
	    status = ReindexingStatus.Rescheduled;
	  } finally {
	    DbManager.safeRollbackAndClose(conn);
	  }

	  // Fall through if SQL exception occurred during update.
	case Failed:
	case Rescheduled:

	  mdManager.incrementFailedReindexingCount();

	  // Reindexing not successful, so try again later.
	  // if status indicates the operation should be rescheduled
	  log.debug2(DEBUG_HEADER + "Reindexing task (" + status
	      + ") did not finish for au " + au.getName());

	  try {
	    // Get a connection to the database.
	    conn = dbManager.getConnection();

	    // Attempt to move failed AU to end of pending list.
	    mdManager.removeFromPendingAus(conn, au.getAuId());

	    if (status == ReindexingStatus.Rescheduled) {
	      log.debug2(DEBUG_HEADER + "Rescheduling reindexing task au "
		  + au.getName());
	      mdManager.addToPendingAus(conn, Collections.singleton(au));
	    }

	    // Complete the database transaction.
	    conn.commit();
	  } catch (SQLException sqle) {
	    log.warning("Error updating pending queue at FINISH" + " for "
		+ status, sqle);
	  } finally {
	    DbManager.safeRollbackAndClose(conn);
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
	    + endUserTime / 1.0e9 + ") Clock time: " + elapsedClockTime / 1.0e3
	    + " (" + endClockTime / 1.0e3 + ")");
      }

      // Release collected metadata info once finished.
      articleMetadataInfoBuffer.close();
      articleMetadataInfoBuffer = null;

      synchronized (mdManager.activeReindexingTasks) {
	mdManager.activeReindexingTasks.remove(au.getAuId());
	mdManager.notifyFinishReindexingAu(au, status);

	try {
	  // Get a connection to the database.
	  conn = dbManager.getConnection();

	  // Schedule another task if available.
	  mdManager.startReindexing(conn);

	  // Complete the database transaction.
	  conn.commit();
	} catch (SQLException sqle) {
	  log.error("Cannot restart indexing", sqle);
	} finally {
	  DbManager.safeRollbackAndClose(conn);
	}
      }
    }

    /**
     * Provides the identifier of the AU for this task.
     * 
     * @param conn
     *          A Connection with the database connection to be used.
     * @return a Long with the identifier of the AU for this task, if any.
     * @throws SQLException
     *           if any problem occurred accessing the database.
     */
    private Long findAuSeq(Connection conn) throws SQLException {
      final String DEBUG_HEADER = "findAuSeq(): ";

      Long auSeq = null;

      // Find the plugin.
      Long pluginSeq =
	  mdManager.findPlugin(conn, PluginManager.pluginIdFromAuId(auId));

      // Check whether the plugin exists.
      if (pluginSeq != null) {
	// Yes: Get the database identifier of the AU.
	String auKey = PluginManager.auKeyFromAuId(auId);

	auSeq = mdManager.findAu(conn, pluginSeq, auKey);
	log.debug2(DEBUG_HEADER + "auSeq = " + auSeq);
      }

      return auSeq;
    }
  }
}
