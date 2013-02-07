/*

Copyright (c) 2000-2010 Board of Trustees of Leland Stanford Jr. University,
all rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
STANFORD UNIVERSITY BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

Except as contained in this notice, the name of Stanford University shall not
be used in advertising or otherwise to promote the sale, use or other dealings
in this Software without prior written authorization from Stanford University.

*/

package org.lockss.plugin.taylorandfrancis;

import java.io.*;
import java.util.List;
import java.util.Set;
import java.util.Iterator;

import org.apache.commons.collections.MultiMap;
import org.apache.commons.collections.map.MultiValueMap;
import org.apache.commons.lang.StringEscapeUtils;

import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.extractor.*;
import org.lockss.plugin.*;
import org.w3c.rdf.vocabulary.dublin_core_19990702.DC;


public class TaylorAndFrancisHtmlMetadataExtractorFactory implements FileMetadataExtractorFactory {
  static Logger log = Logger.getLogger("TaylorAndFrancisHtmlMetadataExtractorFactory");

  public FileMetadataExtractor createFileMetadataExtractor(MetadataTarget target,
							   String contentType)
      throws PluginException {
    return new TaylorAndFrancisHtmlMetadataExtractor();
  }
  
  public static class TaylorAndFrancisHtmlMetadataExtractor 
    implements FileMetadataExtractor {

    // Map Taylor & Francis DublinCore HTML meta tag names to cooked metadata fields
    private static MultiMap tagMap = new MultiValueMap();
    static {
      tagMap.put("dc.Date", MetadataField.FIELD_DATE);
      tagMap.put("dc.Date", MetadataField.DC_FIELD_DATE);
      tagMap.put("dc.Title", MetadataField.FIELD_ARTICLE_TITLE);
      tagMap.put("dc.Title", MetadataField.DC_FIELD_TITLE);
      tagMap.put("dc.Creator", MetadataField.FIELD_AUTHOR);
      tagMap.put("dc.Creator", MetadataField.DC_FIELD_CREATOR);
      tagMap.put("dc.Identifier", MetadataField.DC_FIELD_IDENTIFIER);
      tagMap.put("dc.Subject", MetadataField.DC_FIELD_SUBJECT);
      tagMap.put("dc.Description", MetadataField.DC_FIELD_DESCRIPTION);
      tagMap.put("dc.Publisher", MetadataField.DC_FIELD_PUBLISHER);
      tagMap.put("dc.Publisher", MetadataField.FIELD_PUBLISHER);
      tagMap.put("dc.Type", MetadataField.DC_FIELD_TYPE);
      tagMap.put("dc.Format", MetadataField.DC_FIELD_FORMAT);
      tagMap.put("dc.Source", MetadataField.DC_FIELD_SOURCE);
      tagMap.put("dc.Language", MetadataField.DC_FIELD_LANGUAGE);
      tagMap.put("dc.Coverage", MetadataField.DC_FIELD_COVERAGE);
      tagMap.put("keywords", new MetadataField(MetadataField.FIELD_KEYWORDS, MetadataField.splitAt(";")));
    }

    @Override
    public void extract(MetadataTarget target, CachedUrl cu, Emitter emitter)
        throws IOException {
      ArticleMetadata am = 
        new SimpleHtmlMetaTagMetadataExtractor().extract(target, cu);
      am.cook(tagMap);
      
      // Strip the extra whitespace found in the HTML around and within the "dc.Creator" and "dc.Publisher" fields
      TrimWhitespace(am, MetadataField.DC_FIELD_CREATOR);
      TrimWhitespace(am, MetadataField.FIELD_AUTHOR);
      TrimWhitespace(am, MetadataField.DC_FIELD_PUBLISHER);
      TrimWhitespace(am, MetadataField.FIELD_PUBLISHER);
      
      // Parse the dc.Identifier fields with scheme values "publisher-id", "doi", and "coden".
      List<String> cookedIdentifierList = am.getList(MetadataField.DC_FIELD_IDENTIFIER);
      List<String> rawIdentifierList = am.getRawList("dc.Identifier");
      
      String journalTitle = "";
      String volume = "";
      String issue = "";
      String spage = "";
      String epage = "";
      String doi = "";
      
      for (int j = 0; j < cookedIdentifierList.size(); j++) {
    	  
    	  // If our dc.Identifier field has a comma in it, its content is a comma-delimited list of
    	  // the journal title, volume, issue, and page range associated with the article.
    	  // The journal title itself may contain commas, so the list is parsed backwards and all content
    	  // before ", Vol." is assumed to be part of the journal title.
    	  if (cookedIdentifierList.get(j).contains(", ")) {
    		  String content = cookedIdentifierList.get(j);
    		  String[] biblioInfo = content.split(", ");
    		  
    		  for (int k = biblioInfo.length-1; k >= 0; k--) {
    			  // get the page range
                  if (biblioInfo[k].startsWith("pp. ")) {
    				  // page range separated by hyphen
    				  if (biblioInfo[k].contains("-")) {
    					  spage = biblioInfo[k].substring("pp. ".length(), biblioInfo[k].indexOf("-"));
    					  epage = biblioInfo[k].substring(biblioInfo[k].indexOf('-'), biblioInfo[k].length());
    				  }
                      // page range separated by en-dash
    				  else if (biblioInfo[k].contains("–")) {
                          spage = biblioInfo[k].substring("pp. ".length(), biblioInfo[k].indexOf("–"));
                          epage = biblioInfo[k].substring(biblioInfo[k].indexOf("–"), biblioInfo[k].length());
    				  }
    				  // page range is single page
    				  else {
    					  spage = biblioInfo[k].substring("pp. ".length(), biblioInfo[k].length());
    				  }
    			  }
    			  // get the issue number
    			  else if (biblioInfo[k].startsWith("No. ")) {
    				 issue = biblioInfo[k].substring("No. ".length(), biblioInfo[k].length());
    			  }
    			  // get the volume number
    			  else if (biblioInfo[k].startsWith("Vol. ")) {
     				 volume = biblioInfo[k].substring("Vol. ".length(), biblioInfo[k].length());
    			  }
    			  // by this point, we've come backwards in our comma-delimited list and reached
    			  // the journal title.
    			  else if (!volume.isEmpty()) {
    				  journalTitle = biblioInfo[k].concat(journalTitle);
    				  
    				  // If we're not at the beginning of the comma-separated list
    				  // (i.e. the journal title itself contains commas),
    				  // reinsert the comma that we lost in content.split(", ").
    				  if (k != 0) journalTitle = ", ".concat(journalTitle);
    			  }
    		  }
    		  
    		  // org.apache.commons.lang.StringEscapeUtils contains a method for unescaping HTML codes
    		  // (like &amp;) that may appear in the journal title
    		  journalTitle = StringEscapeUtils.unescapeHtml(journalTitle);
    		  
    		  am.put(MetadataField.FIELD_JOURNAL_TITLE, journalTitle);
    		  am.put(MetadataField.FIELD_VOLUME, volume);
              am.put(MetadataField.FIELD_ISSUE, issue);
              am.put(MetadataField.FIELD_START_PAGE, spage);
    	  }
    	  else if (MetadataUtil.isDOI(cookedIdentifierList.get(j))) {
    		  doi = cookedIdentifierList.get(j);
    		  am.put(MetadataField.FIELD_DOI, doi);
    	  }
    	  
      }
      emitter.emitMetadata(cu, am);
    }
    
    private void TrimWhitespace(ArticleMetadata am, MetadataField md) {
    	List<String> list = am.getList(md);
    	for (int i = 0; i < list.size(); i++) {
      	  String curEntry = list.get(i);
          curEntry = curEntry.trim();
      	  curEntry = curEntry.replace("   ", " ");
      	  list.set(i, curEntry);
    	}
    }
  }
}