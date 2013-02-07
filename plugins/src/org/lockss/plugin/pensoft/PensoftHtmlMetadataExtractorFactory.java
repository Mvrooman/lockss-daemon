/*
 * $Id: 
 */

/*

 Copyright (c) 2000-2009 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.pensoft;

import java.io.*;
import org.apache.commons.collections.MultiMap;
import org.apache.commons.collections.map.MultiValueMap;
import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.extractor.*;
import org.lockss.extractor.MetadataField.Extractor;
import org.lockss.plugin.*;

/*
 * Metadata on abstract page:
 * http://www.pensoft.net/journals/jhr/article/1548/abstract/review-of-the-asian-wood-boring-genus-euxiphydria-hymenoptera-symphyta-xiphydriidae-
<meta name="Allow-search" content="yes">
<meta name="Audience" content="all">
<meta name="Rating" content="all">
<meta name="Voluntary content rating" content="all">
<meta name="resource-type" content="document">
<meta name="revisit-after" content="1 day">
<meta name="distribution" content="global">
<meta name="robots" content="index, follow">
<meta name="keywords" content="woodborers; Palearctic; Oriental; Hyperxiphia">
<meta name="description" content="Five species of Euxiphydria are recognized, E. leucopoda Takeuchi, 1938, from Japan, E. potanini (Jakovlev, 1891) from Japan, Russia, Korea, and China, E. pseud">
<meta name="title" content="Review of the Asian wood-boring genus Euxiphydria (Hymenoptera, Symphyta, Xiphydriidae)"/><meta name="citation_pdf_url" content="http://www.pensoft.net/inc/journals/download.php?fileTable=J_GALLEYS&fileId=3070"/><meta name="citation_xml_url" content="http://www.pensoft.net/inc/journals/download.php?fileTable=J_GALLEYS&fileId=3069"/><meta name="citation_fulltext_html_url" content="http://www.pensoft.net/journals/jhr/article/1548/review-of-the-asian-wood-boring-genus-euxiphydria-hymenoptera-symphyta-xiphydriidae-"/>
<meta name="citation_abstract_html_url" content="http://www.pensoft.net/journals/jhr/article/1548/abstract/review-of-the-asian-wood-boring-genus-euxiphydria-hymenoptera-symphyta-xiphydriidae-"/>
<meta name="dc.title" content="Review of the Asian wood-boring genus Euxiphydria (Hymenoptera, Symphyta, Xiphydriidae)" />
<meta name="dc.creator" content="David Smith" />
<meta name="dc.contributor" content="David Smith" />
<meta name="dc.creator" content="Akihiko Shinohara" />
<meta name="dc.contributor" content="Akihiko Shinohara" />
<meta name="dc.type" content="Research Article" />
<meta name="dc.source" content="Journal of Hymenoptera Research 2011 23: 1" />
<meta name="dc.date" content="2011-10-21" />
<meta name="dc.identifier" content="10.3897/jhr.23.1548" />
<meta name="dc.publisher" content="Pensoft Publishers" />
<meta name="dc.rights" content="http://creativecommons.org/licenses/by/3.0/" />
<meta name="dc.format" content="text/html" />
<meta name="dc.language" content="en" />

<meta name="prism.publicationName" content="Journal of Hymenoptera Research" />
<meta name="prism.issn" content="1314-2607" />
<meta name="prism.publicationDate" content="2011-10-21" /> 
<meta name="prism.volume" content="23" />

<meta name="prism.doi" content="10.3897/jhr.23.1548" />
<meta name="prism.section" content="Research Article" />
<meta name="prism.startingPage" content="1" />
<meta name="prism.endingPage" content="22" />
<meta name="prism.copyright" content="2011 David Smith, Akihiko Shinohara" />
<meta name="prism.rightsAgent" content="Journal of Hymenoptera Research@pensoft.net" />

<meta name="eprints.title" content="Review of the Asian wood-boring genus Euxiphydria (Hymenoptera, Symphyta, Xiphydriidae)" />
<meta name="eprints.creators_name" content="Smith, David " /> <meta name="eprints.creators_name" content="Shinohara, Akihiko " /> 
<meta name="eprints.type" content="Research Article" />
<meta name="eprints.datestamp" content="2011-10-21" />
<meta name="eprints.ispublished" content="pub" />
<meta name="eprints.date" content="2011" />
<meta name="eprints.date_type" content="published" />
<meta name="eprints.publication" content="Pensoft Publishers" />
<meta name="eprints.volume" content="23" />
<meta name="eprints.pagerange" content="1-22" />

<meta name="citation_journal_title" content="Journal of Hymenoptera Research" />
<meta name="citation_publisher" content="Pensoft Publishers" />
<meta name="citation_author" content="David Smith" /> <meta name="citation_author" content="Akihiko Shinohara" /> 
<meta name="citation_title" content="Review of the Asian wood-boring genus Euxiphydria (Hymenoptera, Symphyta, Xiphydriidae)" />
<meta name="citation_volume" content="23" />

<meta name="citation_firstpage" content="1" />
<meta name="citation_lastpage" content="22" />
<meta name="citation_doi" content="10.3897/jhr.23.1548" />
<meta name="citation_issn" content="1314-2607" />
<meta name="citation_date" content="2011/10/21" />

 */
public class PensoftHtmlMetadataExtractorFactory implements
    FileMetadataExtractorFactory {
  static Logger log = Logger.getLogger("PensoftMetadataExtractorFactory");

  public FileMetadataExtractor createFileMetadataExtractor(
      MetadataTarget target, String contentType) throws PluginException {
    return new PensoftHtmlMetadataExtractor();
  }

  public static class PensoftHtmlMetadataExtractor 
    extends SimpleHtmlMetaTagMetadataExtractor {

    // Map BePress-specific HTML meta tag names to cooked metadata fields
    private static MultiMap tagMap = new MultiValueMap();
    static {
       //general pattern for capturing start and end page number. 
      String pagenumpattern = "[pP\\. ]*([^-]+)(?:-(.+))?";
      String authorpattern = ".*\\p{L}";
      tagMap.put("citation_author", new MetadataField(MetadataField.FIELD_AUTHOR, MetadataField.splitAt(",")));
      tagMap.put("citation_title", MetadataField.FIELD_ARTICLE_TITLE);
      tagMap.put("citation_publisher", MetadataField.FIELD_PUBLISHER);
      tagMap.put("citation_journal_title", MetadataField.FIELD_JOURNAL_TITLE);
      tagMap.put("citation_volume", MetadataField.FIELD_VOLUME);
      tagMap.put("citation_issue", MetadataField.FIELD_ISSUE);
      tagMap.put("citation_issn", MetadataField.FIELD_EISSN);
      tagMap.put("citation_firstpage", MetadataField.FIELD_START_PAGE);
      tagMap.put("citation_lastpage", MetadataField.FIELD_END_PAGE);         
      tagMap.put("citation_date", MetadataField.FIELD_DATE);
      tagMap.put("citation_doi", MetadataField.FIELD_DOI);
      
      tagMap.put("dc.title", MetadataField.FIELD_JOURNAL_TITLE); 
      tagMap.put("dc.creator", MetadataField.DC_FIELD_CREATOR); 
      tagMap.put("dc.contributor", MetadataField.DC_FIELD_CONTRIBUTOR); 
      tagMap.put("dc.type", MetadataField.DC_FIELD_TYPE); 
      tagMap.put("dc.source", MetadataField.DC_FIELD_SOURCE); 
      tagMap.put("dc.date", MetadataField.DC_FIELD_DATE); 
      tagMap.put("dc.identifier", MetadataField.DC_FIELD_IDENTIFIER); 
      tagMap.put("dc.publisher", MetadataField.DC_FIELD_PUBLISHER); 
      tagMap.put("dc.rights", MetadataField.DC_FIELD_RIGHTS); 
      tagMap.put("dc.format", MetadataField.DC_FIELD_FORMAT); 
      tagMap.put("dc.language", MetadataField.DC_FIELD_LANGUAGE); 
    
      tagMap.put("keywords", MetadataField.FIELD_KEYWORDS); 

     }

    @Override
    public ArticleMetadata extract(MetadataTarget target, CachedUrl cu)
      throws IOException {
      ArticleMetadata am = super.extract(target, cu);
      am.cook(tagMap);
      return am;
    }
  }
}
 
