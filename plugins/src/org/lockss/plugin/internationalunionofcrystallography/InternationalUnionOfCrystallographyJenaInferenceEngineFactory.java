package org.lockss.plugin.internationalunionofcrystallography;

import java.io.IOException;

import org.lockss.daemon.PluginException;
import org.lockss.db.DbManager;
import org.lockss.extractor.JenaInferenceEngine;
import org.lockss.extractor.JenaInferenceEngineFactory;
import org.lockss.extractor.JenaMetadataExtractor;
import org.lockss.plugin.ArchivalUnit;

public class InternationalUnionOfCrystallographyJenaInferenceEngineFactory implements JenaInferenceEngineFactory {

	@Override
	public JenaInferenceEngine createJenaInferenceEngine()
			throws PluginException {
		return new InternationalUnionOfCrystallographyJenaInferenceEngine();
	}

}
