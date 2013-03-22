package org.lockss.metadata;

import java.sql.Connection;
import java.util.List;

import org.lockss.app.BaseLockssDaemonManager;
import org.lockss.app.ConfigurableManager;
import org.lockss.util.Logger;

public abstract class MetadataManager extends BaseLockssDaemonManager implements ConfigurableManager {
	
	protected static Logger log = Logger.getLogger(MetadataManager.class);

	abstract List<String> getPrioritizedAuIdsToReindex(Connection conn, int maxAuIds);

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
//	abstract Long findOrCreateAu(Long pluginSeq, String auKey) throws Exception;

}
