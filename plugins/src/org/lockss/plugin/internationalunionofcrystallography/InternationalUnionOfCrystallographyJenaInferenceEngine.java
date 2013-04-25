package org.lockss.plugin.internationalunionofcrystallography;

import java.io.IOException;

import org.apache.jena.atlas.logging.Log;
import org.lockss.daemon.PluginException;
import org.lockss.db.DbManager;
import org.lockss.extractor.JenaInferenceEngine;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.util.Logger;

public class InternationalUnionOfCrystallographyJenaInferenceEngine implements
		JenaInferenceEngine {
	
	static Logger log = Logger
			.getLogger(InternationalUnionOfCrystallographyJenaInferenceEngine.class
					.getName());
	@Override
	public void extract(ArchivalUnit au, DbManager dbManager)
			throws IOException, PluginException {
		log.info("--- INFERENCE STARTED ---");

	}

}
