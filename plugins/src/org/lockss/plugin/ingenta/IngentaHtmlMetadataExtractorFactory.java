/*
 * $Id: IngentaHtmlMetadataExtractorFactory.java,v 1.2 2012/04/17 20:18:02 akanshab01 Exp $
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

package org.lockss.plugin.ingenta;

import java.io.*;
import org.apache.commons.collections.MultiMap;
import org.apache.commons.collections.map.MultiValueMap;
import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.extractor.*;
import org.lockss.extractor.MetadataField.Extractor;
import org.lockss.plugin.*;

public class IngentaHtmlMetadataExtractorFactory implements
    FileMetadataExtractorFactory {
  static Logger log = Logger.getLogger("IngentaHtmlMetadataExtractorFactory");

  public FileMetadataExtractor createFileMetadataExtractor(
      MetadataTarget target, String contentType) throws PluginException {
    return new IngentaHtmlMetadataExtractor();
  }

  public static class IngentaHtmlMetadataExtractor 
    extends SimpleHtmlMetaTagMetadataExtractor {

    private static MultiMap tagMap = new MultiValueMap();
    static {
   
      String splitMetaPattern = "(.*)[,](.*)[,](.*)[,]([^-]+)[-]([^-()]+)"; 
      //   <meta name="DC.creator" content="Karni, Nirit"/>
      //  <meta name="DC.creator" content="Reiter, Shunit"/>
      //  <meta name="DC.creator" content="Bryen, Diane Nelson"/>  
      tagMap.put("DC.creator", MetadataField.DC_FIELD_CREATOR);
      // <meta name="DC.type" scheme="DCMIType" content="Text"/>
      tagMap.put("Dc.type",MetadataField.DC_FIELD_TYPE);
      tagMap.put("DC.Date.issued", MetadataField.FIELD_DATE);
      // <meta name="DC.title" content="Israeli Arab Teachers&#039; Attitudes on 
      //Inclusion of Students with Disabilities"/>
      tagMap.put("DC.title", MetadataField.DC_FIELD_TITLE);
      //<meta name="DCTERMS.issued" content="July 2011"/>
      tagMap.put("DC.Issued", MetadataField.DC_FIELD_ISSUED);
      //  <meta name="DC.identifier" scheme="URI"
      //content="info:doi/10.1179/096979511798967106"/>
      tagMap.put("DC.identifier",new MetadataField(
          MetadataField.DC_FIELD_IDENTIFIER, MetadataField.
          splitAt("info:doi/")));
      // <meta name="DCTERMS.isPartOf" scheme="URI" content="urn:ISSN:0969-7950"/>
     tagMap.put("DCTERMS.isPartOf",new MetadataField(
          MetadataField.DC_FIELD_IDENTIFIER_ISSNM, MetadataField.
          splitAt("urn:ISSN:")));
      tagMap.put("DCTERMS.bibliographicCitation",new MetadataField(
          MetadataField.FIELD_ARTICLE_TITLE,
          MetadataField.groupExtractor(splitMetaPattern, 1)));
      //<meta name="DCTERMS.bibliographicCitation" content="The British Journal of Development
      // Disabilities, 57, 113, 123-132(10)"/>
     tagMap.put("DCTERMS.bibliographicCitation",new MetadataField(
         MetadataField.DC_FIELD_CITATION_VOLUME, 
         MetadataField.groupExtractor(splitMetaPattern, 2)));
     tagMap.put("DCTERMS.bibliographicCitation",new MetadataField(
         MetadataField.DC_FIELD_CITATION_ISSUE,
         MetadataField.groupExtractor(splitMetaPattern, 3)));
     tagMap.put("DCTERMS.bibliographicCitation",new MetadataField(
         MetadataField.DC_FIELD_CITATION_SPAGE, 
         MetadataField.groupExtractor(splitMetaPattern, 4)));
     tagMap.put("DCTERMS.bibliographicCitation",new MetadataField(
         MetadataField.DC_FIELD_CITATION_EPAGE, 
         MetadataField.groupExtractor(splitMetaPattern, 5))); 
  
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
 
