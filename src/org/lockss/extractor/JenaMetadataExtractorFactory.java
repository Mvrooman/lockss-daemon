package org.lockss.extractor;

import org.lockss.daemon.*;

public interface JenaMetadataExtractorFactory {

	/**
	 * Create a ArticleMetadataExtractor
	 * 
	 * @param target the purpose for which metadata is being extracted
	 */
	public JenaMetadataExtractor createJenaMetadataExtractor()
			throws PluginException;
}
