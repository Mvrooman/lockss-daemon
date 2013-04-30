package org.lockss.metadata;

import java.io.IOException;

import org.lockss.daemon.PluginException;
import org.lockss.db.DbManager;
import org.lockss.extractor.JenaInferenceEngine;
import org.lockss.extractor.JenaMetadataExtractor;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.Plugin;

public class JenaMetadataRecorder {
	
	private ArchivalUnit au = null;
	private Plugin plugin = null;
	private DbManager dbManager = null;
	
	public JenaMetadataRecorder(ArchivalUnit au, DbManager dbManager) {
		this.au = au;
		this.plugin = au.getPlugin();
		this.dbManager = dbManager;
	}
	
	public void recordMetadata() {
		JenaMetadataExtractor jme = plugin.getJenaMetadataExtractor();
		
		try {
			jme.extract(au, dbManager);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (PluginException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void inferMetadata() {
		JenaInferenceEngine jie = plugin.getJenaInferenceEngine();
		
		try {
			jie.extract(au, dbManager);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (PluginException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}	
	}

}
