/*
 * $Id: TestTaylorAndFrancisHtmlFilterFactory.java,v 1.8 2012/11/29 01:19:33 thib_gc Exp $
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

package org.lockss.plugin.taylorandfrancis;

import java.io.*;

import junit.framework.Test;

import org.apache.commons.lang.RandomStringUtils;
import org.lockss.util.*;
import org.lockss.plugin.FilterFactory;
import org.lockss.test.*;

public class TestTaylorAndFrancisHtmlFilterFactory extends LockssTestCase {
  
  public FilterFactory fact;
  
  private static MockArchivalUnit mau;
  
  //Example instances mostly from pages of strings of HTMl that should be filtered
  //and the expected post filter HTML strings. These are shared crawl & hash filters
  
  private static final String newsArticlesHtml =
  "<div id=\"newsArticles\" class=\"module widget\"><h2>Journal news</h2><ul><li>" +
  "<a href=\"http://www.tandfonline.com/doi/abs/10.1080/00018732.2011.598293\"><b>2010 Impact Factor: 21.214 / 2nd in Condensed Matter Physics</b></a>" +
  "</li></ul></div>";
  private static final String newsArticlesHtmlFiltered =
  "";

  private static final String relatedHtml =
  "<div id=\"relatedArticles\" class=\"module widget\"><h2>Related Articles</h2><div class=\"jqueryTab tabs clear\">" +
  "<ul class=\"tabsNav clear\"><li class=\"active\" id=\"readTab\"><a class='jslink' href=\"#read\">Most read</a>" +
  "</li></ul></div></div><div class=\"tabsPanel articleSummaries hide\" id=\"citedPanel\"></div>";
  private static final String relatedHtmlFiltered =
  "<div class=\"tabsPanel articleSummaries hide\" id=\"citedPanel\"></div>";

  private static final String moduleHtml =
  "<div class=\"ad module\"><!-- Literatum Advertisement --><!-- width:200 -->" +
  "<!-- placeholder id=null, description=Journal right column 1 --></div>";
  private static final String moduleHtmlFiltered =
  "";

  /**
   * Variant to test with Crawl Filter
   */
  public static class TestCrawl extends TestTaylorAndFrancisHtmlFilterFactory {

    public void setUp() throws Exception {
      super.setUp();
      fact = new TaylorAndFrancisHtmlCrawlFilterFactory();
    }
    
  }

  /**
   * Variant to test with Hash Filter
   */
  public static class TestHash extends TestTaylorAndFrancisHtmlFilterFactory {
    
    //Example instances mostly from pages of strings of HTMl that should be filtered
    //and the expected post filter HTML strings.
    private static final String sfxLinkHtmlHash = 
        "<a class=\"sfxLink\"></a>";
    private static final String sfxLinkHtmlHashFiltered = 
        "";
    
    private static final String brandingHtmlHash = 
        "<div id=\"hd\">\n<div id=\"branding\">XYZ</div>\n</div>";
    private static final String brandingHtmlHashFiltered = 
        "";
    
    private static final String rssHtmlHash = 
        "<link type=\"application/rss+xml\" rel=\"hello\" href=\"world\"/>";
    private static final String rssHtmlHashFiltered = 
        "";
    
    private static final String creditsHtmlHash = 
          "<div id=\"ft\">\n<div class=\"credits\">\n<p>(c) 2012</p>\n</div>\n</div>";
    private static final String creditsHtmlHashFiltered = 
          "";
    
    private static final String rssLinkHtmlHash = 
        "<a href=\"/example&feed=rss&foo=bar\">Example</a>";
    private static final String rssLinkHtmlHashFiltered = 
        "";
    
    private static final String accessHtmlHash = 
        "<div class=\"foo accessIconWrapper bar\">Yes</div>";
    private static final String accessHtmlHashFiltered = 
        "";
    
    private static final String linkoutHtmlHash = 
        "<a href=\"/servlet/linkout?yes=no&foo=bar\">Example</a>";
    private static final String linkoutHtmlHashFiltered = 
        "";

    private static final String articleUsageHtmlHash =
      "<div id=\"footer\">"
      + "<div class=\"block-1\">"
      + "<div class=\"articleUsage\">"
      + "<strong>Article Views:</strong> 179"
      + "<div class=\"auPopUp hidden\">"
      + "<div class=\"pointyEdge\">"
      + "</div>Article usage statistics combine cumulative total PDF"
      +	"downloads and full-text HTML views from publication"
      + "date (but no earlier than 25 Jun 2011, launch date of"
      +	"this website) to 08 Oct 2012. Article views are only"
      + "counted from this site. Although these data are updated"
      + "every 24 hours, there may be a 48-hour delay before the"
      + "most recent numbers are available."
      +	"</div>"
      + "</div>"
      + "<div class=\"block-2 sb-div\"></div>" + "</div>\"";

    private static final String articleUsageHtmlHashFiltered =
      "<div id=\"footer\">"
      + "<div class=\"block-1\">"
      + "<div class=\"block-2 sb-div\"></div>" + "</div>\"";
 
    
    private static final String javascriptHtmlHash = 
        "<noscript> onversion/1070139620/?label=_s1RCLTo-QEQ5JGk_gM&amp;amp;guid=ON&amp;amp; </noscript>\n" +
        "<script type=\"text/javascript\" src=\"http://nejm.resultspage.com/autosuggest/searchbox_suggest_v1.js\" language=\"javascript\">Hello</script>";
    // Trailing space is due to the WhiteSpaceFilter
    private static final String javascriptHtmlHashFiltered = 
        "<noscript> onversion/1070139620/?label=_s1RCLTo-QEQ5JGk_gM&amp;amp;guid=ON&amp;amp; </noscript> ";

         
    
    public void setUp() throws Exception {
      super.setUp();
      fact = new TaylorAndFrancisHtmlHashFilterFactory();
    }
    
    public void testSfxLinkHtmlHashFiltering() throws Exception {
      InputStream actIn =
          fact.createFilteredInputStream(mau,
                                         new StringInputStream(sfxLinkHtmlHash),
                                         Constants.DEFAULT_ENCODING);
      assertEquals(sfxLinkHtmlHashFiltered, StringUtil.fromInputStream(actIn));
    }

    public void testBrandingHtmlHashFiltering() throws Exception {
      InputStream actIn =
          fact.createFilteredInputStream(mau,
                                         new StringInputStream(brandingHtmlHash),
                                         Constants.DEFAULT_ENCODING);
      assertEquals(brandingHtmlHashFiltered, StringUtil.fromInputStream(actIn));
    }

    public void testRssHtmlHashFiltering() throws Exception {
      InputStream actIn = 
          fact.createFilteredInputStream(mau,
                                         new StringInputStream(rssHtmlHash),
                                         Constants.DEFAULT_ENCODING);
      assertEquals(rssHtmlHashFiltered, StringUtil.fromInputStream(actIn));
    }

    public void testRssLinkHtmlHashFiltering() throws Exception {
      InputStream actIn =
          fact.createFilteredInputStream(mau,
                                         new StringInputStream(rssLinkHtmlHash),
                                         Constants.DEFAULT_ENCODING);
      assertEquals(rssLinkHtmlHashFiltered, StringUtil.fromInputStream(actIn));
    }

    public void testCreditsHtmlHashFiltering() throws Exception {
      InputStream actIn =
          fact.createFilteredInputStream(mau,
                                         new StringInputStream(creditsHtmlHash),
                                         Constants.DEFAULT_ENCODING);
      assertEquals(creditsHtmlHashFiltered, StringUtil.fromInputStream(actIn));
    }

    public void testarticleUsageHtmlHashFiltering() throws Exception {
      InputStream actIn =
          fact.createFilteredInputStream(mau, 
                                         new StringInputStream(articleUsageHtmlHash),
                                         Constants.DEFAULT_ENCODING);
      assertEquals(articleUsageHtmlHashFiltered, StringUtil.fromInputStream(actIn));
    }

    public void testJavascriptHtmlHashFiltering() throws Exception {
      InputStream actIn =
          fact.createFilteredInputStream(mau, 
                                         new StringInputStream(javascriptHtmlHash),
                                         Constants.DEFAULT_ENCODING);
      assertEquals(javascriptHtmlHashFiltered, StringUtil.fromInputStream(actIn));
    }

    public void testAccessHtmlHashFiltering() throws Exception {
      InputStream actIn =
          fact.createFilteredInputStream(mau, 
                                         new StringInputStream(accessHtmlHash),
                                         Constants.DEFAULT_ENCODING);
      assertEquals(accessHtmlHashFiltered, StringUtil.fromInputStream(actIn));
    }

    public void testLinkoutHtmlHashFiltering() throws Exception {
      InputStream actIn =
          fact.createFilteredInputStream(mau, 
                                         new StringInputStream(linkoutHtmlHash),
                                         Constants.DEFAULT_ENCODING);
      assertEquals(linkoutHtmlHashFiltered, StringUtil.fromInputStream(actIn));
    }

    public void testLinkToHomePage() throws Exception {
      assertEquals("",
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("<a href=\"/\">" + rand() + "</a>"),
                                                                             Constants.DEFAULT_ENCODING)));
      assertEquals("",
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("<a href=\"http://www.tandfonline.com\">" + rand() + "</a>"),
                                                                             Constants.DEFAULT_ENCODING)));
    }
    
    public void testLinkToStyleSheets() throws Exception {
      assertEquals("",
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("<link rel=\"stylesheet\" type=\"text/css\" media=\"screen, projection\" href=\"/cssJawr/1107879557/style.css\" />"),
                                                                             Constants.DEFAULT_ENCODING)));
      assertEquals("",
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("<link href=\"/templates/jsp/css/grids-min.css\" rel=\"stylesheet\" type=\"text/css\" />"),
                                                                             Constants.DEFAULT_ENCODING)));
    }
    
    public void testH4() throws Exception {
      assertEquals("",
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("<h4 class=\"" + rand() + "\">" + rand() + "</h4>"),
                                                                             Constants.DEFAULT_ENCODING)));
      assertEquals("",
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("<h4>" + rand() + "</h4>"),
                                                                             Constants.DEFAULT_ENCODING)));
    }
    
    public void testCitationsWithStrongTag() throws Exception {
      assertEquals("",
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("<strong>Citations:" + rand() + "\n\n\n</strong>"),
                                                                             Constants.DEFAULT_ENCODING)));
      assertEquals("",
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("<strong><a href=\"/doi/citedby/10.1080/13607863.2010.551339\">Citations:" + rand() + "\n\n\n</strong>"),
                                                                             Constants.DEFAULT_ENCODING)));
    }
    
    public void testDivIdContent() throws Exception {
      assertEquals("<div id=\"content\">ABCDEFGHIJKLM</div>",
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("<div id=\"content\">ABCDEFGHIJKLM</div>"),
                                                                             Constants.DEFAULT_ENCODING)));
      assertEquals("<div id=\"content\">ABCDEFGHIJKLM</div>",
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("<div id=\"journal_content\">ABCDEFGHIJKLM</div>"),
                                                                             Constants.DEFAULT_ENCODING)));
      assertEquals("<div id=\"content\">ABCDEFGHIJKLM</div>",
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("<div id=\"tandf_content\">ABCDEFGHIJKLM</div>"),
                                                                             Constants.DEFAULT_ENCODING)));
      assertEquals("<a href=\"#tandf_content\">ABCDEFGHIJKLM</a>",
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("<a href=\"#content\">ABCDEFGHIJKLM</a>"),
                                                                             Constants.DEFAULT_ENCODING)));
      assertEquals("<a href=\"#tandf_content\">ABCDEFGHIJKLM</a>",
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("<a href=\"#tandf_content\">ABCDEFGHIJKLM</a>"),
                                                                             Constants.DEFAULT_ENCODING)));
    }
    
    public void testOverlay() throws Exception {
      assertEquals("",
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("<div id=\"overlay\">" + rand() + "</div>"),
                                                                             Constants.DEFAULT_ENCODING)));
      assertEquals("",
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("<div class=\"overlay clear overlayHelp\">" + rand() + "</div>"),
                                                                             Constants.DEFAULT_ENCODING)));
      assertEquals(" ", // Note the single space (WhiteSpaceFilter)
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("\n\n\n   <div id=\"overlay\">" + rand() + "</div>   \n\n\n   <div class=\"overlay clear overlayHelp\">" + rand() + "</div>   \n\n\n"),
                                                                             Constants.DEFAULT_ENCODING)));
    }
    
    public void testDivAlertDiv() throws Exception {
      assertEquals("",
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("<div id=\"alertDiv\">" + rand() + "</div>"),
                                                                             Constants.DEFAULT_ENCODING)));
      assertEquals("",
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("<div class=\"alertDiv\">" + rand() + "</div>"),
                                                                             Constants.DEFAULT_ENCODING)));
      assertEquals("",
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("<div class=\"script_only alertDiv\">" + rand() + "</div>"),
                                                                             Constants.DEFAULT_ENCODING)));
    }
    
    public void testIdFpi() throws Exception {
      assertEquals("",
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("<a href=\"\" id=\"fpi\">" + rand() + "</a>"),
                                                                             Constants.DEFAULT_ENCODING)));
      assertEquals("",
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("<div id=\"fpi\">" + rand() + "</div>"),
                                                                             Constants.DEFAULT_ENCODING)));
    }
    
    public void testCitationsTab() throws Exception {
      assertEquals("",
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("<li id=\"citationsTab\">" + rand() + "</li>"),
                                                                             Constants.DEFAULT_ENCODING)));
    }
    
    public void testCitationsPanel() throws Exception {
      assertEquals("",
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("<div id=\"citationsPanel\">" + rand() + "</div>"),
                                                                             Constants.DEFAULT_ENCODING)));
    }
    
    public void testHtmlComments() throws Exception {
      assertEquals("",
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("<!-- " + rand() + "-->"),
                                                                             Constants.DEFAULT_ENCODING)));
    }
    
    public void testStyle() throws Exception {
      assertEquals("",
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("<style>" + rand() + "</style>"),
                                                                             Constants.DEFAULT_ENCODING)));
    }
    
    public void testHd() throws Exception {
      assertEquals("",
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("<div id=\"hd\">\n" + rand() + "\n</div>"),
                                                                             Constants.DEFAULT_ENCODING)));
    }
    
    public void testFt() throws Exception {
      assertEquals("",
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("<div id=\"ft\">\n" + rand() + "\n</div>"),
                                                                             Constants.DEFAULT_ENCODING)));
    }
    
    public void testRelatedLink() throws Exception {
      assertEquals("",
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("<a class=\"relatedLink\" href=\"/action/doSearch?target=related&amp;doi=10.0000/123456789\">" + rand() + "</a>"),
                                                                             Constants.DEFAULT_ENCODING)));
    }

    public void testDoiMeta() throws Exception {
      assertEquals("",
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("<div class=\"doiMeta " + rand() + "\">\n" + rand() + "\n</div>"),
                                                                             Constants.DEFAULT_ENCODING)));
    }
  
    public void testSingleHighlightColor() throws Exception {
      assertEquals("",
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("<input type=\"hidden\" id=\"singleHighlightColor\" value=\"true\" />"),
                                                                             Constants.DEFAULT_ENCODING)));
    }
  
    public void testDivSpacing() throws Exception {
      assertEquals("</div> <div class=\"foo\">",
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("</div><div class=\"foo\">"),
                                                                             Constants.DEFAULT_ENCODING)));
      assertEquals("</div> <div class=\"foo\">",
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("</div> <div class=\"foo\">"),
                                                                             Constants.DEFAULT_ENCODING)));
      assertEquals("</div> <div class=\"foo\">",
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("</div>\n<div class=\"foo\">"),
                                                                             Constants.DEFAULT_ENCODING)));
      assertEquals("</div> <div class=\"foo\">",
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("</div>   \n\n\n   <div class=\"foo\">"),
                                                                             Constants.DEFAULT_ENCODING)));
    }
    
    public void testVersionOfRecordFirstPublished() throws Exception {
      assertEquals("<strong>Version of record first published:</strong>",
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("<strong>Available online:</strong>"),
                                                                             Constants.DEFAULT_ENCODING)));
      assertEquals("<strong>Version of record first published:</strong>",
                   StringUtil.fromInputStream(fact.createFilteredInputStream(null,
                                                                             new StringInputStream("<strong>Version of record first published:</strong>"),
                                                                             Constants.DEFAULT_ENCODING)));
    }
  
  }

  public static Test suite() {
    return variantSuites(new Class[] {
        TestCrawl.class,
        TestHash.class
      });
  }
  
  public void testRelatedHtmlFiltering() throws Exception {
    InputStream actIn =
      fact.createFilteredInputStream(mau,
                                     new StringInputStream(relatedHtml),
                                     Constants.DEFAULT_ENCODING);
    assertEquals(StringUtil.fromInputStream(actIn),
                 relatedHtmlFiltered);
  }
  
  public void testNewsArticlesHtmlFiltering() throws Exception {
    InputStream actIn =
        fact.createFilteredInputStream(mau,
                                       new StringInputStream(newsArticlesHtml),
                                       Constants.DEFAULT_ENCODING);
    assertEquals(newsArticlesHtmlFiltered, StringUtil.fromInputStream(actIn));
  }
  
  public void testModuleHtmlFiltering() throws Exception {
    InputStream actIn =
        fact.createFilteredInputStream(mau, 
                                       new StringInputStream(moduleHtml),
                                       Constants.DEFAULT_ENCODING);
    assertEquals(moduleHtmlFiltered, StringUtil.fromInputStream(actIn));
  }
  
  public static String rand() {
    return RandomStringUtils.randomAlphabetic(30);
  }
  
}
