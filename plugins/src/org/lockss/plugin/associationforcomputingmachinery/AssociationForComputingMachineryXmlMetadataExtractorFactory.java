/*
 * $Id: AssociationForComputingMachineryXmlMetadataExtractorFactory.java,v 1.10 2013/01/12 00:45:32 pgust Exp $
 */

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

package org.lockss.plugin.associationforcomputingmachinery;

import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.extractor.*;
import org.lockss.plugin.*;
import org.lockss.extractor.XmlDomMetadataExtractor.NodeValue;
import org.lockss.extractor.XmlDomMetadataExtractor.XPathValue;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.util.*;
import org.apache.commons.collections.map.*;
import javax.xml.xpath.XPathExpressionException;

/**
 * Files used to write this class constructed from ACM FTP archive:
 * ~/2011/10aug2011/NEW-MAG-QUEUE-V9I7-2001562/NEW-MAG-QUEUE-V9I7-2001562.xml
 *
 */
public class AssociationForComputingMachineryXmlMetadataExtractorFactory
implements FileMetadataExtractorFactory {
  static Logger log = Logger.getLogger("ACMXmlMetadataExtractorFactory");


  @Override
  public FileMetadataExtractor createFileMetadataExtractor(MetadataTarget target,
      String contentType)
          throws PluginException {
    return new ACMXmlMetadataExtractor();
  }

  public static class ACMXmlMetadataExtractor 
  implements FileMetadataExtractor {

    private static final int FILE_NAME_INDEX = 7;
    private static final int AUTHOR_INDEX = 6;

    private static String[] xpathArr = {
      "article_publication_date",
      "title",
      "page_from",
      "page_to",
      "doi_number",
      "language",
      "authors",
      "fulltext"
    };

    private static MetadataField[] articleMetadataFields = {
      MetadataField.FIELD_DATE,
      MetadataField.FIELD_ARTICLE_TITLE,
      MetadataField.FIELD_START_PAGE,
      MetadataField.FIELD_END_PAGE,
      MetadataField.FIELD_DOI,
      MetadataField.FIELD_LANGUAGE,
      MetadataField.FIELD_AUTHOR,
      MetadataField.DC_FIELD_IDENTIFIER,
    };

    private static String[] xpathValArr = new String[xpathArr.length];
    private static CachedUrl currCachedUrl;
    private static ArrayList<ArticleMetadata> articleMetadataList = new ArrayList<ArticleMetadata>();
    private static ArrayList<CachedUrl> cachedUrlList = new ArrayList<CachedUrl>();

    /** NodeValue for creating value of subfields from article_rec's parent tag */
    static private final NodeValue SECTION_VALUE = new NodeValue() {
      @Override
      public String getValue(Node node) {
        if (node == null) {
          return null;
        }

        NodeList articleNodes = node.getChildNodes(); ///periodical/section/?

        for (int m = 0; m < articleNodes.getLength(); m++) {
          Node articleNode = articleNodes.item(m); ///periodical/section/?

          if (articleNode.getNodeName().equals("article_rec")) {
            NodeList articleRecs = articleNode.getChildNodes(); ///periodical/section/article_rec/?

            for (int k = 0; k < articleRecs.getLength(); k++) {
              Node articleRecNode = articleRecs.item(k); ///periodical/section/article_rec/?

              for (int i = 0; i < xpathArr.length; ++i) {
                if (articleRecNode.getNodeName().equals(xpathArr[i])) {
                  if (i == AUTHOR_INDEX) {
                    NodeList authors = articleRecNode.getChildNodes(); ///periodical/section/article_rec/authors/?

                    for (int l = 0; l < authors.getLength(); ++l)
                    {
                      if (authors.item(l).getNodeName().equals("au")) {
                        NodeList authorFields = authors.item(l).getChildNodes(); ///periodical/section/article_rec/authors/au/?
                        String first_name = null, middle_name = null, last_name = null;

                        for (int j = 0; j < authorFields.getLength(); ++j) {
                          Node authorNode = authorFields.item(j);

                          if (authorNode.getNodeName().equals("first_name")) {
                            first_name = authorNode.getTextContent();
                          } else if (authorNode.getNodeName().equals("middle_name")) {
                            middle_name = authorNode.getTextContent();
                          } else if (authorNode.getNodeName().equals("last_name")) {
                            last_name = authorNode.getTextContent();
                          }
                        }

                        if (StringUtil.isNullString(xpathValArr[i]))
                          xpathValArr[i] = "";
                        else
                          xpathValArr[i] += "; ";
                        xpathValArr[i] += last_name + ", " + first_name + " " + (middle_name != null ? middle_name : "");
                      }
                    }
                  } else if (i == FILE_NAME_INDEX) {
                    NodeList fulltext = articleRecNode.getChildNodes(); ///periodical/section/article_rec/fulltext/?

                    for (int l = 0; l < fulltext.getLength(); ++l) {
                      if (fulltext.item(l).getNodeName().equals("file")) {
                        NodeList fileNames = fulltext.item(l).getChildNodes(); ///periodical/section/article_rec/fulltext/file/?

                        for (int j = 0; j < fileNames.getLength(); ++j) {
                          Node fileName = fileNames.item(j);

                          if (fileName.getNodeName().equals("fname")) {
                            xpathValArr[i] = fileName.getTextContent();
                          }
                        }
                      }
                    }
                  } else {
                    xpathValArr[i] = articleRecNode.getTextContent();
                  }

                  if (xpathValArr[i].contains("&amp;#"))
                    xpathValArr[i] = fixUnicodeIn(xpathValArr[i]);
                }  
              }
            }

            storeCurrentArticleRec();
            clear();
          }
        }

        // return the file name
        return xpathValArr[FILE_NAME_INDEX];
      }

      private String fixUnicodeIn(String str) {
        String output = "";
        Matcher unicode = Pattern.compile("(&amp;#)(\\d+)(;)").matcher(str);

        while(unicode.find()) {
          output = unicode.replaceFirst(""+(char)Integer.parseInt(unicode.group(2)));

          unicode.reset(output);
        }

        log.debug3("UNICODE -> "+output);
        return output;
      }
    };

    private static void clear()
    {
      for (int i = 0; i < xpathValArr.length; ++i) {
        xpathValArr[i] = "";
      }
    }

    /** Map of raw xpath key to node value function */
    static private final Map<String,XPathValue> nodeMap = 
        new HashMap<String,XPathValue>();
    static {
      // normal journal article schema
      nodeMap.put("/periodical/journal_rec/journal_name", XmlDomMetadataExtractor.TEXT_VALUE);
      nodeMap.put("/periodical/journal_rec/journal_code", XmlDomMetadataExtractor.TEXT_VALUE);
      nodeMap.put("/periodical/journal_rec/issn", XmlDomMetadataExtractor.TEXT_VALUE);
      nodeMap.put("/periodical/journal_rec/eissn", XmlDomMetadataExtractor.TEXT_VALUE);
      nodeMap.put("/periodical/journal_rec/publisher/publisher_name", XmlDomMetadataExtractor.TEXT_VALUE);
      nodeMap.put("/periodical/issue_rec/volume", XmlDomMetadataExtractor.TEXT_VALUE);
      nodeMap.put("/periodical/issue_rec/issue", XmlDomMetadataExtractor.TEXT_VALUE);
      nodeMap.put("/periodical/issue_rec/issue_date", XmlDomMetadataExtractor.TEXT_VALUE);

      // conference proceeding schema
      nodeMap.put("/proceeding/conference_rec/conference_date/start_date", XmlDomMetadataExtractor.TEXT_VALUE);
      nodeMap.put("/proceeding/proceeding_rec/proc_title", XmlDomMetadataExtractor.TEXT_VALUE);
      nodeMap.put("/proceeding/proceeding_rec/acronym", XmlDomMetadataExtractor.TEXT_VALUE);
      nodeMap.put("/proceeding/proceeding_rec/isbn", XmlDomMetadataExtractor.TEXT_VALUE);
      nodeMap.put("/proceeding/proceeding_rec/copyright_year", XmlDomMetadataExtractor.TEXT_VALUE);
      nodeMap.put("/proceeding/proceeding_rec/publisher/publisher_name", XmlDomMetadataExtractor.TEXT_VALUE);

      // individual article's metadata
      nodeMap.put("/periodical/section", SECTION_VALUE);
      nodeMap.put("/proceeding/section", SECTION_VALUE);
      nodeMap.put("/proceeding", SECTION_VALUE);
      nodeMap.put("/periodical", SECTION_VALUE);
    }

    /** Map of raw xpath key to cooked MetadataField */
    static private final MultiValueMap xpathMap = new MultiValueMap();
    static {
      // normal journal article schema
      xpathMap.put("/periodical/journal_rec/journal_name", MetadataField.FIELD_JOURNAL_TITLE);
      xpathMap.put("/periodical/journal_rec/journal_code", MetadataField.FIELD_PROPRIETARY_IDENTIFIER);
      xpathMap.put("/periodical/journal_rec/issn", MetadataField.FIELD_ISSN);
      xpathMap.put("/periodical/journal_rec/eissn", MetadataField.FIELD_EISSN);
      xpathMap.put("/periodical/journal_rec/publisher/publisher_name", MetadataField.FIELD_PUBLISHER);
      xpathMap.put("/periodical/issue_rec/volume", MetadataField.FIELD_VOLUME);
      xpathMap.put("/periodical/issue_rec/issue", MetadataField.FIELD_ISSUE);
      xpathMap.put("/periodical/issue_rec/issue_date", MetadataField.FIELD_DATE);

      // conference proceeding schema
      xpathMap.put("/proceeding/conference_rec/conference_date/start_date", MetadataField.FIELD_DATE);
      xpathMap.put("/proceeding/proceeding_rec/proc_title", MetadataField.FIELD_JOURNAL_TITLE);
      xpathMap.put("/proceeding/proceeding_rec/acronym", MetadataField.FIELD_PROPRIETARY_IDENTIFIER);
      xpathMap.put("/proceeding/proceeding_rec/isbn", MetadataField.FIELD_ISBN);
      xpathMap.put("/proceeding/proceeding_rec/copyright_year", MetadataField.DC_FIELD_RIGHTS);
      xpathMap.put("/proceeding/proceeding_rec/publisher/publisher_name", MetadataField.FIELD_PUBLISHER);
    }

    static private final MetadataField[] journalMetadataFields = {
      MetadataField.FIELD_JOURNAL_TITLE,
      MetadataField.FIELD_ISSN,
      MetadataField.FIELD_EISSN,
      MetadataField.FIELD_VOLUME,
      MetadataField.FIELD_ISSUE,
      MetadataField.FIELD_DATE,
      MetadataField.FIELD_ISBN,
      MetadataField.DC_FIELD_RIGHTS,
      MetadataField.FIELD_PUBLISHER,
      MetadataField.FIELD_PROPRIETARY_IDENTIFIER
    };

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
      CachedUrl metadataCu = new AssociationForComputingMachineryCachedUrl(cu.getArchivalUnit(),getMetadataFile(cu));

      if (metadataCu.getUrl().contains("TEST00")) //TODO: make this check better
        metadataCu = cu;

      if (metadataCu == null || !metadataCu.hasContent())
      {
        log.debug3("The metadata file does not exist in the au: "+metadataCu.getUrl());
        return;
      }

      currCachedUrl = cu;

      do_extract(target, metadataCu, emitter);
      // need to release created CU
      AuUtil.safeRelease(metadataCu);
    }

    /**
     * Use XmlMetadataExtractor to extract raw metadata, map
     * to cooked fields, then extract extra tags by reading the file.
     * 
     * @param target the MetadataTarget
     * @param in the Xml input stream to parse
     * @param emitter the emitter to output the resulting ArticleMetadata
     */
    public ArticleMetadata do_extract(MetadataTarget target, CachedUrl cu, Emitter emit)
        throws IOException, PluginException {
      try {
        ArticleMetadata am = 
            new XmlDomMetadataExtractor(nodeMap).extract(target, cu);
        am.cook(xpathMap);
        
        // If not present, add "ACM" publisher name (shouldn't happen!)
        am.putIfBetter(MetadataField.FIELD_PUBLISHER, "ACM");
        emitAllMetadata(am, emit);
        return am;
      } catch (XPathExpressionException ex) {
        PluginException ex2 = new PluginException("Error parsing XPaths");
        ex2.initCause(ex);
        throw ex2;
      }
    }

    /**
     * Emits metadata for all of the CachedUrl's stored in CachedUrlList using
     * articleMetadataList
     * @param journalMetadata - contains metadata which applies to every article
     * 	in the volume and which is copied into their individual ArticleMetadata
     * @param emit - an AcmEmitter to emit the metadata
     */
    private void emitAllMetadata(ArticleMetadata journalMetadata, Emitter emit) {	    	
      for (int i = 0; i < cachedUrlList.size(); ++i) {
        ArticleMetadata am = articleMetadataList.get(i);
        CachedUrl cu = cachedUrlList.get(i);

        for (int j = 0; j < journalMetadataFields.length; ++j) {
          if (journalMetadata.get(journalMetadataFields[j]) != null)
            am.put(journalMetadataFields[j], 
                   journalMetadata.get(journalMetadataFields[j]));
        }

        emit.emitMetadata(cu, am);
      }

      cachedUrlList.clear();
      articleMetadataList.clear();
    }

    /**
     * Stores a CachedUrl and ArticleMetadata for the most recently extracted
     * article so that it can be emitted later when we have access to journal
     * metadata values (see emitAllMetadata())
     */
    private static void storeCurrentArticleRec()
    {
      ArticleMetadata am = new ArticleMetadata();
      putMetadataIn(am);
      CachedUrl toStore = currCachedUrl.getArchivalUnit().makeCachedUrl(getPdfUrl(am.get(MetadataField.DC_FIELD_IDENTIFIER)));

      articleMetadataList.add(am);
      cachedUrlList.add(toStore);
    }

    /**
     * Uses the currCachedUrl and the current article_rec/.../fname to
     * generate the Url of the current article
     * @return the current article's pathname
     */
    private static String getPdfUrl(String fileName)
    {
      Pattern pattern = Pattern.compile("(http://clockss-ingest.lockss.org/sourcefiles/[^/]+/[\\d]+/[^/]+/)([^/]+)(/[^/]+.xml)",Pattern.CASE_INSENSITIVE);
      Matcher matcher = pattern.matcher(currCachedUrl.getUrl());
      return matcher.replaceFirst("$1$2/"+fileName);
    }

    /**
     * Moves an article's metadata from the xpathValArr to an actual
     * ArticleMetadata object
     * @param am - the object to store the article's metadata in
     */
    private static void putMetadataIn(ArticleMetadata am)
    {
      for (int i = 0; i < xpathValArr.length; ++i) {
        am.put(articleMetadataFields[i], xpathValArr[i]);
      }
    }
  }

  /**
   * Uses a CachedUrl assumed to be in the directory of the metadata file to find the
   * pathname for the metadata file (and return it)
   * @param cu - address of a sibling of the metadata file
   * @return the metadata file's pathname
   */
  private static String getMetadataFile(CachedUrl cu)
  {
    Pattern pattern = Pattern.compile("(http://clockss-ingest.lockss.org/sourcefiles/[^/]+/[\\d]+/[^/]+/)([^/]+)(/[^/]+.xml)",Pattern.CASE_INSENSITIVE);
    Matcher matcher = pattern.matcher(cu.getUrl());
    return matcher.replaceFirst("$1$2/$2.xml");
  }
}