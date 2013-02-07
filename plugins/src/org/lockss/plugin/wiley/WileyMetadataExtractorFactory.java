/*
 * $Id:
 */

/*

 Copyright (c) 2000-2012 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.wiley;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.collections.map.MultiValueMap;

import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.extractor.*;
import org.lockss.extractor.XmlDomMetadataExtractor.NodeValue;
import org.lockss.extractor.XmlDomMetadataExtractor.TextValue;
import org.lockss.extractor.XmlDomMetadataExtractor.XPathValue;
import org.lockss.plugin.*;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;


/**
 * This class implements a FileMetadataExtractorFactory for Wiley content
 * Files used to write this class constructed from Wiley FTP archive:
 * http://clockss-ingest.lockss.org/sourcefiles/wiley-dev/2011/A/AEN49.1.zip
 */
  public class WileyMetadataExtractorFactory
    implements FileMetadataExtractorFactory {
    static Logger log = 
      Logger.getLogger("WileyMetadataExtractorFactory");

  public FileMetadataExtractor createFileMetadataExtractor(
      MetadataTarget target, String contentType) throws PluginException {
    return new WileyMetadataExtractor();
  }

/**
 * This class implements a FileMetadataExtractor for Wiley content.
 */
  public static class WileyMetadataExtractor 
    implements FileMetadataExtractor {
	    
    /** NodeValue for creating value of subfields from author tag */
    static private final XPathValue AUTHOR_VALUE = new NodeValue() {
      @Override
      public String getValue(Node node) {
        if (node == null) {
          return null;
        }

        // Wiley stores author names in two tags: givenNames and familyName
        // Middle initials are stored as suffixes of givenNames with no period (e.g. "Todd F")
        NodeList nameNodes = node.getChildNodes();
        String givenName = null, familyName = null;
        for (int k = 0; k < nameNodes.getLength(); k++) {
          Node nameNode = nameNodes.item(k);
          if (nameNode.getNodeName().equals("givenNames")) {
            givenName = nameNode.getTextContent();
          } else if (nameNode.getNodeName().equals("familyName")) {
            familyName = nameNode.getTextContent();
          }
        }
        return familyName + ", " + givenName;
      }
    };

    // matches issue number ranges of integers separated by a unicode dash
    static final Pattern issuePat = Pattern.compile("(^[0-9]+)[^0-9]+([0-9]+)$");
    static private final XPathValue ISSUE_VALUE = new TextValue() {
      @Override
      public String getValue(String s) {
        return StringUtil.isNullString(s) 
            ? null : issuePat.matcher(s).replaceFirst("$1-$2");
      }
    };
    
    /** Map of raw xpath key to node value function */
    
    static private final Map<String,XPathValue> nodeMap = 
        new HashMap<String,XPathValue>();
    static {
      // Journal article schema
      nodeMap.put("/component/header/contentMeta/titleGroup/title", XmlDomMetadataExtractor.TEXT_VALUE);
      nodeMap.put("/component/header/publicationMeta[@level='product']/titleGroup/title", XmlDomMetadataExtractor.TEXT_VALUE);
      nodeMap.put("/component/header/publicationMeta[@level='product']/issn[@type='print']", XmlDomMetadataExtractor.TEXT_VALUE);
      nodeMap.put("/component/header/publicationMeta[@level='product']/issn[@type='electronic']", XmlDomMetadataExtractor.TEXT_VALUE);
      nodeMap.put("/component/header/publicationMeta[@level='part']/numberingGroup/numbering[@type='journalVolume']", XmlDomMetadataExtractor.TEXT_VALUE);
      nodeMap.put("/component/header/publicationMeta[@level='part']/numberingGroup/numbering[@type='journalIssue']", ISSUE_VALUE);
      nodeMap.put("/component/header/publicationMeta[@level='part']/coverDate/@startDate", XmlDomMetadataExtractor.TEXT_VALUE);
      nodeMap.put("/component/header/publicationMeta[@level='unit']/numberingGroup/numbering[@type='pageFirst']", XmlDomMetadataExtractor.TEXT_VALUE);
      nodeMap.put("/component/header/publicationMeta[@level='unit']/numberingGroup/numbering[@type='pageLast']", XmlDomMetadataExtractor.TEXT_VALUE);
      nodeMap.put("/component/header/publicationMeta[@level='unit']/doi", XmlDomMetadataExtractor.TEXT_VALUE);
      nodeMap.put("/component/header/contentMeta/keywordGroup/keyword", XmlDomMetadataExtractor.TEXT_VALUE);
      nodeMap.put("/component/header/publicationMeta[@level='product']/publisherInfo/publisherName", XmlDomMetadataExtractor.TEXT_VALUE);
      nodeMap.put("/component/header/contentMeta/creators/creator[@creatorRole='author']/personName", AUTHOR_VALUE);
    }

    /** Map of raw xpath key to cooked MetadataField */
    
    static private final MultiValueMap xpathMap = new MultiValueMap();
    static {
      // Journal article schema
      xpathMap.put("/component/header/contentMeta/titleGroup/title", MetadataField.FIELD_ARTICLE_TITLE);
      xpathMap.put("/component/header/publicationMeta[@level='product']/titleGroup/title", MetadataField.FIELD_JOURNAL_TITLE);
      xpathMap.put("/component/header/publicationMeta[@level='product']/issn[@type='print']", MetadataField.FIELD_ISSN);
      xpathMap.put("/component/header/publicationMeta[@level='product']/issn[@type='electronic']", MetadataField.FIELD_EISSN);
      xpathMap.put("/component/header/publicationMeta[@level='part']/numberingGroup/numbering[@type='journalVolume']", MetadataField.FIELD_VOLUME);
      xpathMap.put("/component/header/publicationMeta[@level='part']/numberingGroup/numbering[@type='journalIssue']", MetadataField.FIELD_ISSUE);
      xpathMap.put("/component/header/publicationMeta[@level='part']/coverDate/@startDate", MetadataField.FIELD_DATE);
      xpathMap.put("/component/header/publicationMeta[@level='unit']/numberingGroup/numbering[@type='pageFirst']", MetadataField.FIELD_START_PAGE);
      xpathMap.put("/component/header/publicationMeta[@level='unit']/numberingGroup/numbering[@type='pageLast']", MetadataField.FIELD_END_PAGE);
      xpathMap.put("/component/header/publicationMeta[@level='unit']/doi", MetadataField.FIELD_DOI);
      xpathMap.put("/component/header/contentMeta/keywordGroup/keyword", MetadataField.FIELD_KEYWORDS);
      xpathMap.put("/component/header/publicationMeta[@level='product']/publisherInfo/publisherName", MetadataField.FIELD_PUBLISHER);
      xpathMap.put("/component/header/contentMeta/creators/creator[@creatorRole='author']/personName", MetadataField.FIELD_AUTHOR);
    }

    /**
     * Use XmlMetadataExtractor to extract raw metadata, map
     * to cooked fields, then extract extra tags by reading the file.
     * 
     * @param target the MetadataTarget
     * @param cu the CachedUrl from which to read input
     * @param emitter the emitter to output the resulting ArticleMetadata
     */
    @Override
    public void extract(MetadataTarget target, CachedUrl cu, Emitter emitter)
        throws IOException, PluginException {
      log.debug3("Attempting to extract metadata from cu: "+cu);
      try {
        String xmlUrl = cu.getUrl().replaceFirst("\\.pdf$", ".wml.xml");
        CachedUrl xmlCu = cu.getArchivalUnit().makeCachedUrl(xmlUrl);
        ArticleMetadata am;
        try {
          am = new XmlDomMetadataExtractor(nodeMap).extract(target, xmlCu);
        } finally {
          AuUtil.safeRelease(xmlCu);
        }
        am.cook(xpathMap);
        emitter.emitMetadata(cu,  am);
      } catch (XPathExpressionException ex) {
        PluginException ex2 = new PluginException("Error parsing XPaths");
        ex2.initCause(ex);
        throw ex2;
      }
    }
  }
}