package org.lockss.plugin.internationalunionofcrystallography;

import java.io.IOException;

import org.lockss.daemon.PluginException;
import org.lockss.extractor.JenaMetadataExtractor;
import org.lockss.extractor.MetadataTarget;
import org.lockss.plugin.ArticleFiles;
import org.lockss.util.Logger;

public class InternationalUnionOfCrystallographyJenaMetadataExtractor implements
		JenaMetadataExtractor {
	static Logger log = Logger
			.getLogger(InternationalUnionOfCrystallographyJenaMetadataExtractor.class
					.getName());

	@Override
	public void extract() throws IOException, PluginException {
		log.info("EXTRACTION STARTED!");
	}

}
