package org.lockss.extractor;

import java.io.*;

import org.lockss.daemon.*;
import org.lockss.db.DbManager;
import org.lockss.plugin.*;

public interface JenaMetadataExtractor {
	
	/**
	 * Generate Jena metadata for this AU
	 * 
	 * @param au
	 * @param dbManager
	 * @throws IOException
	 * @throws PluginException
	 */
	public void extract(ArchivalUnit au, DbManager dbManager)
			throws IOException, PluginException;

}
