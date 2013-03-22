/*
 * $Id: MetadataStarter.java,v 1.4 2013/01/16 08:07:40 tlipkis Exp $
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
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import org.lockss.app.LockssDaemon;
import org.lockss.daemon.LockssRunnable;
import org.lockss.db.SqlDbManager;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.AuEvent;
import org.lockss.plugin.AuEventHandler;
import org.lockss.plugin.AuUtil;
import org.lockss.plugin.PluginManager;
import org.lockss.util.CollectionUtil;
import org.lockss.util.Logger;

/**
 * Starts the metadata extraction process.
 */
public class MetadataStarter extends LockssRunnable {
  private static Logger log = Logger.getLogger(MetadataStarter.class);

  private final SqlDbManager sqlDbManager;
  private final SqlMetadataManager mdManager;
  private final PluginManager pluginManager;

  /**
   * Constructor.
   * 
   * @param sqlDbManager
   *          A SqlDbManager with the database manager.
   * @param mdManager
   *          A SqlMetadataManager with the metadata manager.
   * @param pluginManager
   *          A PluginManager with the plugin manager.
   */
  public MetadataStarter(SqlDbManager sqlDbManager, SqlMetadataManager mdManager,
      PluginManager pluginManager) {
    super("MetadataStarter");

    this.sqlDbManager = sqlDbManager;
    this.mdManager = mdManager;
    this.pluginManager = pluginManager;
  }

  /**
   * Entry point to start the metadata extraction process.
   */
  public void lockssRun() {
    final String DEBUG_HEADER = "lockssRun(): ";
    log.debug(DEBUG_HEADER + "Starting...");
    LockssDaemon daemon = LockssDaemon.getLockssDaemon();

    // Wait until the AUs have been started.
    if (!daemon.areAusStarted()) {
      log.debug(DEBUG_HEADER + "Waiting for aus to start");

      while (!daemon.areAusStarted()) {
	try {
	  daemon.waitUntilAusStarted();
	} catch (InterruptedException ex) {
	}
      }
    }

    // Get a connection to the database.
    Connection conn;

    try {
      conn = sqlDbManager.getConnection();
    } catch (SQLException sqle) {
      log.error("Cannot connect to database -- extraction not started", sqle);
      return;
    }

    // Register the event handler to receive archival unit content change
    // notifications and to be able to re-index the database content associated
    // with the archival unit.
    pluginManager.registerAuEventHandler(new ArchivalUnitEventHandler());

    log.debug2(DEBUG_HEADER + "Examining AUs");

    List<ArchivalUnit> toBeIndexed = new ArrayList<ArchivalUnit>();

    // Loop through all the AUs to see which need to be on the pending queue.
    for (ArchivalUnit au : pluginManager.getAllAus()) {
      if (log.isDebug3()) log.debug3(DEBUG_HEADER + "AU = " + au.getName());

      // Check whether the AU has not been crawled.
      if (!AuUtil.hasCrawled(au)) {
	// Yes: Do not index it.
	continue;
      } else {
	// No: Check whether the plugin's md extractor version is newer
	// than the version of the metadata already in the database or
	// whether the AU metadata hasn't been extracted since the last
	// successful crawl.
	try {
	  if (mdManager.isAuMetadataForObsoletePlugin(conn, au)
	      || mdManager.isAuCrawledAndNotExtracted(conn, au)) {
	    // Yes: index it.
	    toBeIndexed.add(au);
	  }
	} catch (SQLException sqle) {
	  log.error("Cannot get AU metadata version: " + sqle);
	}
      }
    }
    log.debug2(DEBUG_HEADER + "Done examining AUs");

    // Add the AUs to be indexed to the table of pending AUs, if not already
    // there.
    try {
      mdManager.addToPendingAus(conn,
				CollectionUtil.randomPermutation(toBeIndexed));
      conn.commit();
      log.debug2(DEBUG_HEADER + "Queue updated");
    } catch (SQLException sqle) {
      log.error("Cannot add to pending AUs table \"" + PENDING_AU_TABLE + "\"",
		sqle);
      SqlDbManager.safeRollbackAndClose(conn);
      return;
    }

    // Start the reindexing process.
    try {
      mdManager.startReindexing(conn);
      conn.commit();
    } catch (SQLException sqle) {
      log.error("Cannot start reindexing AUs", sqle);
    } finally {
      SqlDbManager.safeRollbackAndClose(conn);
    }
  }

  /**
   * Event handler to receive archival unit content change notifications and to
   * be able to re-index the database content associated with the archival unit.
   */
  private class ArchivalUnitEventHandler extends AuEventHandler.Base {

    /** Called after the AU is created. */
    @Override
    public void auCreated(AuEvent event, ArchivalUnit au) {
      final String DEBUG_HEADER = "auCreated(): ";
      switch (event.getType()) {
	case StartupCreate:
	  log.debug2(DEBUG_HEADER + "StartupCreate for au: " + au);

	  // Since this handler is installed after daemon startup, this case
	  // only occurs rarely, as when an AU is added to au.txt, which is then
	  // rescanned by the daemon. If this restores an existing AU that has
	  // already been crawled, we schedule it to be added to the metadata
	  // database now. Otherwise it will be added through auContentChanged()
	  // once the crawl has been completed.
	  if (AuUtil.hasCrawled(au)) {
	    mdManager.addAuToReindex(au, event.isInBatch());
	  }

	  break;
	case Create:
	  log.debug2(DEBUG_HEADER + "Create for au: " + au);

	  // This case occurs when the user has added an AU through the GUI. If
	  // this restores an existing AU that has already crawled, we schedule
	  // it to be added to the metadata database now. Otherwise it will be
	  // added through auContentChanged() once the crawl has been completed.
	  if (AuUtil.hasCrawled(au)) {
	    mdManager.addAuToReindex(au, event.isInBatch());
	  }

	  break;
	case RestartCreate:
	  log.debug2(DEBUG_HEADER + "RestartCreate for au: " + au);

	  // A new version of the plugin has been loaded. Refresh the metadata
	  // only if the feature version of the metadata extractor increased.
	  if (mdManager.isAuMetadataForObsoletePlugin(au)) {
	    mdManager.addAuToReindex(au, event.isInBatch());
	  }

	  break;
      }
    }

    /** Called for AU deleted events. */
    @Override
    public void auDeleted(AuEvent event, ArchivalUnit au) {
      final String DEBUG_HEADER = "auDeleted(): ";
      switch (event.getType()) {
	case Delete:
	  log.debug2(DEBUG_HEADER + "Delete for au: " + au);

	  // This case occurs when the AU is being deleted, so delete its
	  // metadata.
	  mdManager.deleteAuAndReindex(au);
	  break;
	case RestartDelete:
	  // This case occurs when the plugin is about to restart. There is
	  // nothing to do in this case but wait for the plugin to be
	  // reactivated and see whether anything needs to be done.
	  break;
      }
    }

    /** Called for AU changed events */
    @Override
    public void auContentChanged(AuEvent event, ArchivalUnit au,
	ChangeInfo info) {
      switch (event.getType()) {
	case ContentChanged:
	  // This case occurs after a change to the AU's content after a crawl.
	  // This code assumes that a new crawl will simply add new metadata and
	  // not change existing metadata. Otherwise,
	  // deleteOrRestartAu(au, true) should be called.
	  if (info.isComplete()) {
	    mdManager.addAuToReindex(au, event.isInBatch());
	  }
      }
    }
  }
}
