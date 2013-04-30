package org.lockss.plugin.internationalunionofcrystallography;

import org.lockss.daemon.PluginException;
import org.lockss.extractor.JenaMetadataExtractor;
import org.lockss.extractor.JenaMetadataExtractorFactory;

public class InternationalUnionOfCrystallographyJenaMetadataExtractorFactory
		implements JenaMetadataExtractorFactory {

	@Override
	public JenaMetadataExtractor createJenaMetadataExtractor()
			throws PluginException {

		return new InternationalUnionOfCrystallographyJenaMetadataExtractor();
	}

}