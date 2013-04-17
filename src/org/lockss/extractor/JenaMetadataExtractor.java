package org.lockss.extractor;

import java.io.*;

import org.lockss.daemon.*;
import org.lockss.plugin.*;

public interface JenaMetadataExtractor {
	
	/**
	 * Emit zero or more ArticleMetadata containing metadata extracted from
	 * files comprising article (feature)
	 * 
	 * @param target the purpose for which metadata is being extracted
	 * @param af describes the files making up the article
	 * @param emitter
	 */
	public void extract()
			throws IOException, PluginException;

	/** Functor to emit ArticleMetadata object(s) created by extractor */
	// TODO: Needed?
	public interface Emitter {
		public void emitMetadata(ArticleFiles af, ArticleMetadata metadata);
	}

}
