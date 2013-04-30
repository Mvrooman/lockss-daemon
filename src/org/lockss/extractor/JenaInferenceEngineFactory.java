package org.lockss.extractor;

import java.io.IOException;

import org.lockss.daemon.PluginException;
import org.lockss.db.DbManager;
import org.lockss.plugin.ArchivalUnit;

public interface JenaInferenceEngineFactory {
	
	/**
	 * Create a JenaInferenceEngine
	 */
	public JenaInferenceEngine createJenaInferenceEngine()
			throws PluginException;

}
