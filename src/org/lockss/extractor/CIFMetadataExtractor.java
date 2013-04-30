package org.lockss.extractor;

import org.lockss.plugin.CachedUrl;
import org.lockss.plugin.base.BaseCachedUrl;
import org.lockss.util.IOUtil;
import org.lockss.util.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CIFMetadataExtractor  {

	static Logger log = Logger.getLogger("CIFMetadataExtractor");

    public Map<String, String> getAdditionalMetadata(CachedUrl cu)
            throws IOException {
        if (cu == null) {
            throw new IllegalArgumentException(
                    "extract() called with null CachedUrl");
        }
        log.info("Starting CIFMetadataExtractor for " + cu.getUrl());
        Map<String, String> map = new HashMap<String, String>();
        if (((BaseCachedUrl) cu).getNodeVersion().hasContent()) {     //Check to see if the content actually exists
            BufferedReader bReader = new BufferedReader(cu.openForReading());

            for (String line = bReader.readLine(); line != null; line = bReader
                    .readLine()) {

                if (line.length() > 0 && line.startsWith("_")) {
                    log.debug3("reading line " + line);
                    String regex = "([^\\s]+)\\s+(.+)";
                    Pattern p = Pattern.compile(regex);
                    Matcher m = p.matcher(line);
                    if (m.find()) {
                        log.debug3("Found netadata for:" + m.group(1) + " with value:" + m.group(2));
                        map.put(m.group(1), m.group(2));
                    }
                }
            }
            IOUtil.safeClose(bReader);
        }
        return map;
    }
}
