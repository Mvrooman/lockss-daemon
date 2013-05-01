package org.lockss.plugin.internationalunionofcrystallography;

import org.lockss.daemon.PluginException;
import org.lockss.extractor.JenaInferenceEngine;
import org.lockss.extractor.JenaInferenceEngineFactory;

public class InternationalUnionOfCrystallographyJenaInferenceEngineFactory implements JenaInferenceEngineFactory {

	@Override
	public JenaInferenceEngine createJenaInferenceEngine()
			throws PluginException {
		return new InternationalUnionOfCrystallographyJenaInferenceEngine();
	}

}
