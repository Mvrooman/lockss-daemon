package org.lockss.extractor;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.collections.MultiMap;
import org.apache.commons.collections.map.MultiValueMap;
import org.apache.commons.lang.StringEscapeUtils;
import org.lockss.daemon.PluginException;
import org.lockss.plugin.CachedUrl;
import org.lockss.util.IOUtil;
import org.lockss.util.Logger;
import org.lockss.util.StringUtil;

public class CIFMetadataExtractor extends SimpleFileMetadataExtractor {

	static Logger log = Logger.getLogger("CIFMetadataExtractor");
	private MultiMap cifTagMap = new MultiValueMap();

	public ArticleMetadata extract(MetadataTarget target, CachedUrl cu)
			throws IOException {
		if (cu == null) {
			throw new IllegalArgumentException(
					"extract() called with null CachedUrl");
		}

		ArticleMetadata metadata = new ArticleMetadata();
		BufferedReader bReader = new BufferedReader(cu.openForReading());
		Map<String, String> map = new HashMap<String, String>();
		for (String line = bReader.readLine(); line != null; line = bReader
				.readLine()) {
			
			if (line.length() > 0 && line.startsWith("_")) {

				String regex = "([^\\s]+)\\s+(.+)";
				Pattern p = Pattern.compile(regex);
				Matcher m = p.matcher(line);
				if (m.find())
				{
					map.put(m.group(1), m.group(2));
					cifTagMap.put(m.group(1), new MetadataField(m.group(1)));
				
				}
			}
		}
		
		metadata.putRaw(MetadataField.FIELD_ADDITIONAL_METADATA.getKey(), map);
		IOUtil.safeClose(bReader);
		return metadata;
	}
	 

	public void cook(ArticleMetadata articleMetadata) {
		
		articleMetadata.cook(cifTagMap);
		
	}


}
