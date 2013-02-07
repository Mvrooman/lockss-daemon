/*
 * $Id: TestPortlandPressHtmlFilterFactory.java,v 1.1 2013/01/24 22:40:18 alexandraohlson Exp $
 */
/*

 Copyright (c) 2000-2013 Board of Trustees of Leland Stanford Jr. University,
 all rights reserved.

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of his software and associated documentation files (the "Software"), to deal
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

package org.lockss.plugin.portlandpress;

import java.io.*;

import org.lockss.util.*;
import org.lockss.test.*;

public class TestPortlandPressHtmlFilterFactory extends LockssTestCase {
  static String ENC = Constants.DEFAULT_ENCODING;

  private PortlandPressHtmlFilterFactory fact;
  private MockArchivalUnit mau;

  public void setUp() throws Exception {
    super.setUp();
    fact = new PortlandPressHtmlFilterFactory();
    mau = new MockArchivalUnit();
  }

  private static final String BJNameHtml = 
      "<tr valign=\"bottom\">" +
          "<td class=\"BJnameLogo\">" +
          "<div style=\"position:absolute;top:0;z-index:9999;\" onMouseOver=\"this.style.cursor='pointer';\"  " +
          "onclick =\"javascript:window.location.href='/'\">&nbsp;</div>" +
          "<span class=\"Courtesy\" id=\"CourtesyName\"></span></td>" +
          "<td align=\"right\">";
  private static final String BJNameHtmlFiltered = 
      "<tr valign=\"bottom\">" +
          "<td align=\"right\">";

  private static final String RHAdvertHtml =
      "<div class=\"RHCells\">" +
          "<a class=\"RHLinks\" href=\"/bj/author_resources.htm\"><img src=\"/images/redarrows.gif\" border=\"0\">Author resources</a>" +
          "</div>" +
          "<div class=\"RHAdvert\">" +
          "<a href=\"http://blah/bj/mobile/\"><img src=\"/images/ads/ad_160x150.jpg\" border=\"0\"></a>" +
          "</div>" +
          "</div>";
  private static final String RHAdvertHtmlFiltered =
      "<div class=\"RHCells\">" +
          "<a class=\"RHLinks\" href=\"/bj/author_resources.htm\"><img src=\"/images/redarrows.gif\" border=\"0\">Author resources</a>" +
          "</div>" +
          "</div>";

  private static final String LeftPanelHtml = 
      "<tr valign=\"top\">" +
          "<td class=\"Panel\" id=\"LeftPanel\">" +                                                                
          "<div id=\"EdBoard\">" +                                                                         
          "<div class=EdBoardHeading>Editorial Board</div>" +                                   
          "<div id=\"EdBoardCallIn\"><div style=\"text-align:center;padding-top:10px;\"><img src=\"/images/blahs.gif\"></div></div>" +                                                                                                              
          " <div class=\"EdBoard\"><b><a href=\"/blah.htm\"><img src=\"/images/redarrows.gif\" border=\"0\"" +
          ">Full Editorial Board</a></b></div>" +
          "<div class=\"EdBoard\"><b><a href=\"/blahl.htm\"><img src=\"/images/redarrows.gif\" border=\"0\">" +
          " Editorial Advisory Panel</a></b></div>" +                                                                                      
          " </div> " +
          " <div class=\"LHAdvert\">" +
          " <img src=\"/images/spacer.gif\" height=\"1\" width=\"10\" border=\"0\"><a href=\"/bj/section.htm?S=9\">" +
          " <img src=\"/images/bj_chem_146x230.gif\" border=\"0\"></a><br>  " +                                                                    
          "</div> " +                                                                            
          " </td><td>" +
          "    <!-- The second column is the main part of the page -->" +
          "<div class=\"MainPage\">";
  private static final String LeftPanelHtmlFiltered = 
      "<tr valign=\"top\">" +
          "<td>" +
          "    <!-- The second column is the main part of the page -->" +
          "<div class=\"MainPage\">";

  private static final String tagPairHtml =
      "<table>" +
          "<tr valign=\"top\"><td><img src=\"/images/arrow.gif\"></td><td><a class=\"sidelinks\" href=\"/cs/imps/toc.htm\">Immediate Publications</a></td></tr>" +
          "<tr valign=\"top\"><td><img src=\"/images/arrow.gif\"></td><td><a class=\"sidelinks\" href=\"/cs/toc.htm\">Browse archive</a></td></tr>" +
          "<tr valign=\"top\"><td><img src=\"/images/arrow.gif\"></td><td><a class=\"sidelinks\" href=\"/cs/search/search.htm\">Search archive</a></td></tr>" +
          "<tr valign=\"top\"><td><img src=\"/images/arrow.gif\"></td><td><a class=\"sidelinks\" href=\"/cs/comments.htm\">Commentaries</a></td></tr>" +
          " <tr valign=\"top\"><td><img src=\"/images/arrow.gif\"></td><td><a class=\"sidelinks\" href=\"/cs/subjects/reviews.htm\">Reviews &amp; Hypotheses</a></td></tr>" +
          " </table>";
  private static final String tagPairHtmlFiltered =
      "<table>" +
          "<tr valign=\"top\"><td><img src=\"/images/arrow.gif\"></td><td><a class=\"sidelinks\" href=\"/cs/imps/toc.htm\"/a></td></tr>" +
          "<tr valign=\"top\"><td><img src=\"/images/arrow.gif\"></td><td><a class=\"sidelinks\" href=\"/cs/search/search.htm\">Search archive</a></td></tr>" +
          "<tr valign=\"top\"><td><img src=\"/images/arrow.gif\"></td><td><a class=\"sidelinks\" href=\"/cs/comments.htm\">Commentaries</a></td></tr>" +
          " <tr valign=\"top\"><td><img src=\"/images/arrow.gif\"></td><td><a class=\"sidelinks\" href=\"/cs/subjects/reviews.htm\">Reviews &amp; Hypotheses</a></td></tr>" +
          " </table>";


  private static final String midTempHtml =
      "<!--- MID TEMPLATE --->" +
          "<!-- Holder for Cited By Data -->" +
          "<hr><a name=\"CitedBy\"></a><div id=\"CitedByResults\"></div>" +
          "<br></td>" +
          "    <td width=\"170\" align=\"left\" valign=\"top\">" +
          "         <!--- END MID TEMPLATE --->" +
          "<!--- FOOTER TEMPLATE --->";
  private static final String midTempHtmlFiltered =
          "<!--- FOOTER TEMPLATE --->";


  private static final String  RHAdsHtml = 
      "<div class=\"RHAdsBox\">" +
          "<a href=\"/cs/csopt2pay.htm\"><img src=\"/images/ads/cs_an170n.gif\" border=\"0\"  alt=\"Opt2Pay\"></a>" +
          "</div>" +
          "<img src=\"/images/spacer.gif\" width=\"1\" height=\"3\" border=\"0\"><br>";
  private static final String  RHAdsHtmlFiltered = 
          "<img src=\"/images/spacer.gif\" width=\"1\" height=\"3\" border=\"0\"><br>";

  public void testFiltering() throws Exception {
    InputStream inA;
    InputStream inB;
    
    inA = fact.createFilteredInputStream(mau, new StringInputStream(BJNameHtml),
        ENC);

    assertEquals(BJNameHtmlFiltered,StringUtil.fromInputStream(inA));
    
    inA = fact.createFilteredInputStream(mau, new StringInputStream(RHAdvertHtml),
        ENC);

    assertEquals(RHAdvertHtmlFiltered,StringUtil.fromInputStream(inA));
    
    inA = fact.createFilteredInputStream(mau, new StringInputStream(LeftPanelHtml),
        ENC);

    assertEquals(LeftPanelHtmlFiltered,StringUtil.fromInputStream(inA));
    
    inA = fact.createFilteredInputStream(mau, new StringInputStream(tagPairHtml),
        ENC);

    assertEquals(tagPairHtmlFiltered,StringUtil.fromInputStream(inA))
    
    ;    inA = fact.createFilteredInputStream(mau, new StringInputStream(midTempHtml),
        ENC);

    assertEquals(midTempHtmlFiltered,StringUtil.fromInputStream(inA));
    
    inA = fact.createFilteredInputStream(mau, new StringInputStream(RHAdsHtml),
        ENC);

    assertEquals(RHAdsHtmlFiltered,StringUtil.fromInputStream(inA));
  }
}


