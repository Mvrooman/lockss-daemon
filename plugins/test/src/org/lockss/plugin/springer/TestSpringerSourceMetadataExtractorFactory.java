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

package org.lockss.plugin.springer;

import java.io.*;
import java.util.*;

import org.lockss.test.*;
import org.lockss.util.*;
import org.lockss.config.*;
import org.lockss.repository.*;
import org.lockss.extractor.*;
import org.lockss.plugin.*;
import org.lockss.plugin.simulated.*;

/**
 * One of the articles used to get the xml source for this plugin is:
 * ~/2010/ftp_PUB_10-05-17_06-11-02.zip/JOU=11864/VOL=2008.9/ISU=2-3/ART=2008_64/11864_2008_Article.xml.Meta 
 */
public class TestSpringerSourceMetadataExtractorFactory extends LockssTestCase {
  static Logger log = Logger.getLogger("TestSpringerMetadataExtractorFactory");

  private SimulatedArchivalUnit sau;	// Simulated AU to generate content
  private ArchivalUnit hau;		//BloomsburyQatar AU
  private MockLockssDaemon theDaemon;

  private static String PLUGIN_NAME =
    "org.lockss.plugin.springer.ClockssSpringerSourcePlugin";

  private static String BASE_URL = "http://www.example.com";
  private static String SIM_ROOT = BASE_URL + "cgi/reprint/";

  public void setUp() throws Exception {
    super.setUp();
    String tempDirPath = setUpDiskSpace();
    theDaemon = getMockLockssDaemon();
    theDaemon.getAlertManager();
    theDaemon.getPluginManager().setLoadablePluginsReady(true);
    theDaemon.setDaemonInited(true);
    theDaemon.getPluginManager().startService();
    theDaemon.getCrawlManager();

    sau = PluginTestUtil.createAndStartSimAu(MySimulatedPlugin.class,
					     simAuConfig(tempDirPath));
    hau = PluginTestUtil.createAndStartAu(PLUGIN_NAME, aipAuConfig());
  }

  public void tearDown() throws Exception {
    sau.deleteContentTree();
    theDaemon.stopDaemon();
    super.tearDown();
  }

  Configuration simAuConfig(String rootPath) {
    Configuration conf = ConfigManager.newConfiguration();
    conf.put("root", rootPath);
    conf.put("base_url", SIM_ROOT);
    conf.put("year", "2012");
    conf.put("depth", "2");
    conf.put("branch", "3");
    conf.put("numFiles", "7");
    conf.put("fileTypes", "" + (SimulatedContentGenerator.FILE_TYPE_PDF +
				SimulatedContentGenerator.FILE_TYPE_HTML));
    conf.put("binFileSize", "7");
    return conf;
  }

  Configuration aipAuConfig() {
    Configuration conf = ConfigManager.newConfiguration();
    conf.put("base_url", BASE_URL);
    conf.put("year", "2012");
    return conf;
  }
  
  String goodTitle = "Title";
  ArrayList<String> goodAuthors = new ArrayList<String>();
  String goodIssn = "5555-5555";
  String goodVolume = "Volume";
  String goodDate = "2008-12";
  String goodIssue = "Issue";
  String goodDoi = "10.1066/DOI";
  String goodSource = "USA";
  ArrayList<String> goodKeywords = new ArrayList<String>();
  String goodDescription = "Summary";
  String goodRights = "Rights";
  
  String goodPublisher = "Publisher";
  String goodEissn = "6666-6666";
  String goodJournal = "Journal";
  String goodLanguage = "Language";
  String goodStart = "Start";
  String goodEnd = "End";
  
  String goodContent = 
		  "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+
  "<!DOCTYPE Publisher PUBLIC \"-//Springer-Verlag//DTD A++ V2.4//EN\" \"http://devel.springer.de/A++/V2.4/DTD/A++V2.4.dtd\">"+
  "<Publisher>"+
     "<PublisherInfo>"+
        "<PublisherName>Publisher</PublisherName>"+
        "<PublisherLocation>PublisherLocation</PublisherLocation>"+
     "</PublisherInfo>"+
     "<Journal OutputMedium=\"All\">"+
        "<JournalInfo JournalProductType=\"ArchiveJournal\" NumberingStyle=\"Unnumbered\">"+
           "<JournalID>00000</JournalID>"+
           "<JournalPrintISSN>5555-5555</JournalPrintISSN>"+
           "<JournalElectronicISSN>6666-6666</JournalElectronicISSN>"+
           "<JournalTitle>Journal</JournalTitle>"+
           "<JournalAbbreviatedTitle>Jour</JournalAbbreviatedTitle>"+
           "<JournalSubjectGroup>"+
              "<JournalSubject Type=\"Primary\">Subject1</JournalSubject>"+
              "<JournalSubject Type=\"Secondary\">Subject2</JournalSubject>"+
              "<JournalSubject Type=\"Secondary\">Subject3</JournalSubject>"+
              "<JournalSubject Type=\"Secondary\">Subject4</JournalSubject>"+
           "</JournalSubjectGroup>"+
        "</JournalInfo>"+
        "<Volume OutputMedium=\"All\">"+
           "<VolumeInfo TocLevels=\"0\" VolumeType=\"Regular\">"+
              "<VolumeIDStart>Volume</VolumeIDStart>"+
              "<VolumeIDEnd>Volume</VolumeIDEnd>"+
              "<VolumeIssueCount>Issues</VolumeIssueCount>"+
           "</VolumeInfo>"+
           "<Issue IssueType=\"Regular\" OutputMedium=\"All\">"+
              "<IssueInfo IssueType=\"Regular\" TocLevels=\"0\">"+
                 "<IssueIDStart>Issue</IssueIDStart>"+
                 "<IssueIDEnd>Issue</IssueIDEnd>"+
                 "<IssueArticleCount>Articles</IssueArticleCount>"+
                 "<IssueHistory>"+
                    "<OnlineDate>"+
                       "<Year>2008</Year>"+
                       "<Month>11</Month>"+
                       "<Day>8</Day>"+
                    "</OnlineDate>"+
                    "<PrintDate>"+
                       "<Year>2008</Year>"+
                       "<Month>11</Month>"+
                       "<Day>7</Day>"+
                    "</PrintDate>"+
                    "<CoverDate>"+
                       "<Year>2008</Year>"+
                       "<Month>12</Month>"+
                    "</CoverDate>"+
                    "<PricelistYear>2008</PricelistYear>"+
                 "</IssueHistory>"+
                 "<IssueCopyright>"+
                    "<CopyrightHolderName>Copyright</CopyrightHolderName>"+
                    "<CopyrightYear>Rights</CopyrightYear>"+
                 "</IssueCopyright>"+
              "</IssueInfo>"+
              "<Article ID=\"s12080-008-0021-5\" OutputMedium=\"All\">"+
                 "<ArticleInfo ArticleType=\"OriginalPaper\" ContainsESM=\"No\" Language=\"Language\" NumberingStyle=\"Unnumbered\" TocLevels=\"0\">"+
                          "<ArticleID>ID</ArticleID>"+
                          "<ArticleDOI>10.1066/DOI</ArticleDOI>"+
                          "<ArticleSequenceNumber>3</ArticleSequenceNumber>"+
                          "<ArticleTitle Language=\"Language\">Title</ArticleTitle>"+
                          "<ArticleCategory>Original paper</ArticleCategory>"+
                          "<ArticleFirstPage>Start</ArticleFirstPage>"+
                          "<ArticleLastPage>End</ArticleLastPage>"+
                          "<ArticleHistory>"+
                       "<RegistrationDate>"+
                          "<Year>2008</Year>"+
                          "<Month>7</Month>"+
                          "<Day>10</Day>"+
                       "</RegistrationDate>"+
                       "<Received>"+
                          "<Year>2008</Year>"+
                          "<Month>4</Month>"+
                          "<Day>3</Day>"+
                       "</Received>"+
                       "<Accepted>"+
                          "<Year>2008</Year>"+
                          "<Month>7</Month>"+
                          "<Day>3</Day>"+
                       "</Accepted>"+
                       "<OnlineDate>"+
                          "<Year>2008</Year>"+
                          "<Month>7</Month>"+
                          "<Day>30</Day>"+
                       "</OnlineDate>"+
                    "</ArticleHistory>"+
                          "<ArticleCopyright>"+
                       "<CopyrightHolderName>Copyright</CopyrightHolderName>"+
                       "<CopyrightYear>Rights</CopyrightYear>"+
                    "</ArticleCopyright>"+
                          "<ArticleGrants Type=\"Regular\">"+
                              "<MetadataGrant Grant=\"OpenAccess\"/>"+
                              "<AbstractGrant Grant=\"OpenAccess\"/>"+
                              "<BodyPDFGrant Grant=\"Restricted\"/>"+
                              "<BodyHTMLGrant Grant=\"Restricted\"/>"+
                              "<BibliographyGrant Grant=\"Restricted\"/>"+
                              "<ESMGrant Grant=\"Restricted\"/>"+
                          "</ArticleGrants>"+
                      "</ArticleInfo>"+
                 "<ArticleHeader>"+
                    "<AuthorGroup>"+
                       "<Author AffiliationIDS=\"Aff1 Aff2\" CorrespondingAffiliationID=\"Aff1\">"+
                          "<AuthorName DisplayOrder=\"Western\">"+
                             "<GivenName>A.</GivenName>"+
                             "<FamilyName>Author</FamilyName>"+
                          "</AuthorName>"+
                       "</Author>"+
                       "<Author AffiliationIDS=\"Aff1\">"+
                          "<AuthorName DisplayOrder=\"Western\">"+
                             "<GivenName>B.</GivenName>"+
                             "<FamilyName>Author</FamilyName>"+
                          "</AuthorName>"+
                       "</Author>"+
                       "<Author AffiliationIDS=\"Aff2\">"+
                          "<AuthorName DisplayOrder=\"Western\">"+
                             "<GivenName>C.</GivenName>"+
                             "<FamilyName>Author</FamilyName>"+
                          "</AuthorName>"+
                       "</Author>"+
                       "<Affiliation ID=\"Aff1\">"+
                          "<OrgDivision>Fake Division</OrgDivision>"+
                          "<OrgName>Fake Organization</OrgName>"+
                          "<OrgAddress>"+
                             "<City>FreeTown</City>"+
                             "<State>IA</State>"+
                             "<Postcode>00000</Postcode>"+
                             "<Country>USA</Country>"+
                          "</OrgAddress>"+
                       "</Affiliation>"+
                    "</AuthorGroup>"+
                    "<Abstract ID=\"Abs1\" Language=\"Language\">"+
                       "<Heading>Abstract</Heading>"+
                       "<Para>Summary</Para>"+
                    "</Abstract>"+
                    "<KeywordGroup Language=\"Language\">"+
                       "<Heading>Keywords</Heading>"+
                       "<Keyword>Keyword1</Keyword>"+
                       "<Keyword>Keyword2</Keyword>"+
                       "<Keyword>Keyword3</Keyword>"+
                    "</KeywordGroup>"+
                 "</ArticleHeader>"+
                 "<NoBody/>"+
              "</Article>"+
           "</Issue>"+
        "</Volume>"+
     "</Journal>"+
  "</Publisher>";


  public void testExtractFromGoodContent() throws Exception {
	  goodAuthors.add("Author, A.");
	  goodAuthors.add("Author, B.");
	  goodAuthors.add("Author, C.");
	  goodKeywords.add("Keyword1");
	  goodKeywords.add("Keyword2");
	  goodKeywords.add("Keyword3");
	  
    String url = "http://www.example.com/vol1/issue2/art3/";
    MockCachedUrl cu = new MockCachedUrl(url, hau);
    cu.setContent(goodContent);
    cu.setContentSize(goodContent.length());
    cu.setProperty(CachedUrl.PROPERTY_CONTENT_TYPE, "application/xml");
    FileMetadataExtractor me =
      new SpringerSourceMetadataExtractorFactory.SpringerSourceMetadataExtractor();
    assertNotNull(me);
    log.debug3("Extractor: " + me.toString());
    FileMetadataListExtractor mle =
      new FileMetadataListExtractor(me);
    List<ArticleMetadata> mdlist = mle.extract(MetadataTarget.Any, cu);
    assertNotEmpty(mdlist);
    ArticleMetadata md = mdlist.get(0);
    assertNotNull(md);
   
    assertEquals(goodTitle, md.get(MetadataField.FIELD_ARTICLE_TITLE));
    assertEquals(goodAuthors, md.getList(MetadataField.FIELD_AUTHOR));
    assertEquals(goodIssn, md.get(MetadataField.FIELD_ISSN));
    assertEquals(goodVolume, md.get(MetadataField.FIELD_VOLUME));
    assertEquals(goodDate, md.get(MetadataField.FIELD_DATE));
    assertEquals(goodIssue, md.get(MetadataField.FIELD_ISSUE));
    assertEquals(goodDoi, md.get(MetadataField.FIELD_DOI));
    assertEquals(goodKeywords, md.getList(MetadataField.FIELD_KEYWORDS));
    assertEquals(goodDescription, md.get(MetadataField.DC_FIELD_DESCRIPTION));
    assertEquals(goodRights, md.get(MetadataField.DC_FIELD_RIGHTS));
    
    assertEquals(goodPublisher, md.get(MetadataField.FIELD_PUBLISHER));
    assertEquals(goodEissn, md.get(MetadataField.FIELD_EISSN));
    assertEquals(goodJournal, md.get(MetadataField.FIELD_JOURNAL_TITLE));
    assertEquals(goodLanguage, md.get(MetadataField.DC_FIELD_LANGUAGE));
    assertEquals(goodStart, md.get(MetadataField.FIELD_START_PAGE));
    assertEquals(goodEnd, md.get(MetadataField.FIELD_END_PAGE));
  }
  
  String badContent =
    "<HTML><HEAD><TITLE>" + goodTitle + "</TITLE></HEAD><BODY>\n" + 
    "<meta name=\"foo\"" +  " content=\"bar\">\n" +
    "  <div id=\"issn\">" +
    "<!-- FILE: /data/templates/www.example.com/bogus/issn.inc -->MUMBLE: " +
    goodDescription + " </div>\n";

  public void testExtractFromBadContent() throws Exception {
    String url = "http://www.example.com/vol1/issue2/art3/";
    MockCachedUrl cu = new MockCachedUrl(url, hau);
    cu.setContent(badContent);
    cu.setContentSize(badContent.length());
    FileMetadataExtractor me =
      new SpringerSourceMetadataExtractorFactory.SpringerSourceMetadataExtractor();
    assertNotNull(me);
    log.debug3("Extractor: " + me.toString());
    FileMetadataListExtractor mle =
      new FileMetadataListExtractor(me);
    List<ArticleMetadata> mdlist = mle.extract(MetadataTarget.Any, cu);
    assertNotEmpty(mdlist);
    ArticleMetadata md = mdlist.get(0);
    assertNotNull(md);
    assertNull(md.get(MetadataField.FIELD_DOI));
    assertNull(md.get(MetadataField.FIELD_VOLUME));
    assertNull(md.get(MetadataField.FIELD_ISSUE));
    assertNull(md.get(MetadataField.FIELD_START_PAGE));
    assertNull(md.get(MetadataField.FIELD_ISSN));
    assertNull(md.get(MetadataField.FIELD_AUTHOR));
    assertNull(md.get(MetadataField.FIELD_ARTICLE_TITLE));
    assertNull(md.get(MetadataField.FIELD_JOURNAL_TITLE));
    assertNull(md.get(MetadataField.FIELD_DATE));
  }
  
  /**
   * Inner class that where a number of Archival Units can be created
   *
   */
  public static class MySimulatedPlugin extends SimulatedPlugin {
    public ArchivalUnit createAu0(Configuration auConfig)
	throws ArchivalUnit.ConfigurationException {
      ArchivalUnit au = new SimulatedArchivalUnit(this);
      au.setConfiguration(auConfig);
      return au;
    }

    public SimulatedContentGenerator getContentGenerator(Configuration cf, String fileRoot) {
      return new MySimulatedContentGenerator(fileRoot);
    }
  }
  
  /**
   * Inner class to create a html source code simulated content
   */
  public static class MySimulatedContentGenerator extends	SimulatedContentGenerator {
    protected MySimulatedContentGenerator(String fileRoot) {
      super(fileRoot);
    }

    public String getHtmlFileContent(String filename, int fileNum, int depth, int branchNum, boolean isAbnormal) {
			
      String file_content = "<HTML><HEAD><TITLE>" + filename + "</TITLE></HEAD><BODY>\n";
			
      file_content += "  <meta name=\"lockss.filenum\" content=\""+ fileNum + "\">\n";
      file_content += "  <meta name=\"lockss.depth\" content=\"" + depth + "\">\n";
      file_content += "  <meta name=\"lockss.branchnum\" content=\"" + branchNum + "\">\n";			

      file_content += getHtmlContent(fileNum, depth, branchNum,	isAbnormal);
      file_content += "\n</BODY></HTML>";
      logger.debug2("MySimulatedContentGenerator.getHtmlFileContent: "
		    + file_content);

      return file_content;
    }
  }
}