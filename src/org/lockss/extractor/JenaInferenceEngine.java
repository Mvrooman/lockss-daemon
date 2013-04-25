package org.lockss.extractor;

import java.io.IOException;

import org.lockss.daemon.PluginException;
import org.lockss.db.DbManager;
import org.lockss.plugin.ArchivalUnit;

public interface JenaInferenceEngine {
	
	/**
	 * Generate Jena inference data about this AU.
	 * 
	 * @param au
	 * @param dbManager
	 * @throws IOException
	 * @throws PluginException
	 */
	public void extract(ArchivalUnit au, DbManager dbManager)
			throws IOException, PluginException;
}
