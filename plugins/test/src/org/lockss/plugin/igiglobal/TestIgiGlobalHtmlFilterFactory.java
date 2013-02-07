/*

Copyright (c) 2000-2003 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.plugin.igiglobal;

import java.io.*;
import org.lockss.util.*;
import org.lockss.test.*;

public class TestIgiGlobalHtmlFilterFactory extends LockssTestCase {
  private IgiGlobalHtmlFilterFactory fact;
  private MockArchivalUnit mau;

  public void setUp() throws Exception {
    super.setUp();
    fact = new IgiGlobalHtmlFilterFactory();
  }
  
  //Example instances mostly from pages of strings of HTMl that should be filtered
  //and the expected post filter HTML strings.
  private static final String sidebarHtml =
		"<td valign=\"top\" class=\"InnerRight\">\n" +
  			"<div class=\"SidebarRight\">\n" +
  				"<div style=\"margin-top: 10px;\" class=\"rightouter\"><div class=\"rightheader\">News</div>" +
  				"<img style=\"width: 100px; src=\"http://www.igi-global.com/Images/serulogo.gif\">" + 
  				"delivers excellent content efficiently  <br>- Cheryl LaGuardia,<br><br>Research Librarian, Harvard University<br><br>" +
  				"</div>\n" +
  			"</div>\n" +
  		"</td>";
  private static final String sidebarHtmlFiltered =
		"<td valign=\"top\" class=\"InnerRight\">\n" +
  			"\n" +
  		"</td>";
  
  private static final String institutionHeaderHtml =
		  "<span class=\"InstitutionName\" id=\"ctl00_lblInstitution\">LOCKSS</span>" +
		  "Hello World" +
		  "<span id=\"ctl00_cphCenterContent_ctl00_lblHeader\">LOCKSS's IGI Global Research Collection</span>";
  private static final String institutionHeaderHtmlFiltered = "Hello World";

  private static final String inputKeysHtml =
		  "<div>\n" +
			  "<input id=\"__EVENTVALIDATION\" type=\"hidden\" value=\"/wEWAwKM69DODwKpwbbBAgLz9t3hB0AqeZdR3lGFnROBTmBAbtw5sqoS\" name=\"__EVENTVALIDATION\">\n" +
		  "</div>\n" +
		  "<div>\n" +
			  "<input id=\"__EVENTTARGET\" type=\"hidden\" value=\"\" name=\"__EVENTTARGET\">\n" +
			  "<input id=\"__EVENTARGUMENT\" type=\"hidden\" value=\"\" name=\"__EVENTARGUMENT\">\n" +
			  "<input id=\"__VIEWSTATE\" type=\"hidden\" value=\"/wEPDwUENTM4MQ9kFgJmD2QWAgIDEBYCHgZhY3Rpb24FQy9nYXRld2F5L2NvbnRlbnRvd25lZC9hcnRpY2xlLmFzcHg/dGl0bGVpZD01NTY1NiZhY2Nlc3N0eXBlPWluZm9zY2lkFgoCAw9kFgYCAQ8WAh4HVmlzaWJsZWgWAgIBDw8WAh4EVGV4dGVkZAICD2QWBAIBDw8WAh4LTmF2aWdhdGVVcmwFci9tZW1iZXJzaGlwL2xvZ2luLmFzcHg/cmV0dXJudXJsPSUyZmdhdGV3YXklMmZjb250ZW50b3duZWQlMmZhcnRpY2xlLmFzcHglM2Z0aXRsZWlkJTNkNTU2NTYlMjZhY2Nlc3N0eXBlJTNkaW5mb3NjaWRkAgMPDxYCHwMFHi9tZW1iZXJzaGlwL2NyZWF0ZWFjY291bnQuYXNweGRkAgMPFgIfAWhkAgUPDxYCHwIFBkxPQ0tTU2RkAgkPZBYCZg9kFgQCAQ8PFgIfAgUQRS1EYXRhYm...8WAgU+Y3RsMDAkY3BoQ2VudGVyQ29udGVudCRjdGwwMCR1Y0NpdGVDb250ZW50JGxua1N1Ym1pdFRvUmVmV29ya3MFPWN0bDAwJGNwaENlbnRlckNvbnRlbnQkY3RsMDAkdWNDaXRlQ29udGVudCRsbmtTdWJtaXRUb0Vhc3lCaWIFJGN0bDAwJGNwaENlbnRlckNvbnRlbnQkY3RsMDAkbHN0SXN4bg88KwAKAgcUKwACZGQIAgJkBShjdGwwMCRjcGhTaWRlYmFyUmlnaHQkdG1fR2VuZXJpY0NvbnRlbnQxDxQrAAFkZAU2Y3RsMDAkdWNSZXNlYXJjaENvbGxlY3Rpb25zTmF2aWdhdGlvbiRsc3RQcm9kdWN0c093bmVkDzwrAAoBCGZkBTRjdGwwMCR1Y1Jlc2VhcmNoQ29sbGVjdGlvbnNOYXZpZ2F0aW9uJGxzdEJ1bmRsZVR5cGVzDzwrAAoBCGZkF8NmCQCZmjS6K/62J+kVNIwpJpA=\" name=\"__VIEWSTATE\">\n" +
		  "</div>";
  private static final String inputKeysHtmlFiltered =
		  "<div>\n" +
			  "\n" +
		  "</div>\n" +
		  "<div>\n" +
			  "<input id=\"__EVENTTARGET\" type=\"hidden\" value=\"\" name=\"__EVENTTARGET\">\n" +
			  "<input id=\"__EVENTARGUMENT\" type=\"hidden\" value=\"\" name=\"__EVENTARGUMENT\">\n" +
			  "\n" +
		  "</div>";

  private static final String citationHtml =
		  "<span id=\"citeContent\" class=\"useremail\" onclick=\"toggleOptions('citation', 'CiteContent');\"></span>\n" +
		  "<div id=\"citation\" class=\"useroptions\" style=\"width: 400px; padding: 15px; font-size: 10px; display: block;\">\n" +
			  "<h3> MLA </h3>\n" +
			  "<div style=\"margin-bottom: 15px;\">\n" +
			  "<h3> APA </h3>\n" +
			  "<div style=\"margin-bottom: 15px;\">\n" +
			  "<h3> Chicago </h3>\n" +
			  "<div style=\"margin-bottom: 15px;\">\n" +
			  "<h3> Export Reference </h3>\n" +
			  "<span class=\"GrayButton\" style=\"padding: 3px; display: inline-table; margin-right: 7px;\">\n" +
			  		"<input id=\"ctl00_cphCenterContent_ctl00_ucCiteContent_lnkSubmitToRefWorks\" type=\"image\" style=\"border-width:0px;\" onclick=\"this.form.target=\"_blank\";\" src=\"../../Images/ReWorks.jpg\" name=\"ctl00$cphCenterContent$ctl00$ucCiteContent$lnkSubmitToRefWorks\">\n" +
			  "</span>\n" +
			  "<span class=\"GrayButton\" style=\"padding: 3px; display: inline-table;\">\n" +
			  		"<input id=\"ctl00_cphCenterContent_ctl00_ucCiteContent_lnkSubmitToEasyBib\" type=\"image\" style=\"border-width:0px;\" onclick=\"this.form.target=\"_blank\";\" src=\"../../Images/EasyBib.jpg\" name=\"ctl00$cphCenterContent$ctl00$ucCiteContent$lnkSubmitToEasyBib\">\n" +
			  "</span>\n" +
		  "</div>";
  private static final String citationHtmlFiltered =
		  "<span id=\"citeContent\" class=\"useremail\" onclick=\"toggleOptions('citation', 'CiteContent');\"></span>\n";

  private static final String trialImageHtml =
		  "<br>" +
		  "<div class=\"FloatRight\">\n" +
		  		"<img style=\"background-color: #fff;\" alt=\"Trial Access\" src=\"/Images/trialaccess.png\">\n" +
		  "</div>";
  private static final String trialImageHtmlFiltered = "<br>";

  private static final String goodHtml =
	  "Hello\n" +
	  "<div class=\"relatedcats\">\n" +
		"READ MORE: <a href=\"/news/iraq\">Iraq</a>, <a href=\"/news/dick-cheney\">Dick Cheney</a>, <a href=\"/news/scooter-libby\">Scooter Libby</a>, <a href=\"/news/patrick-fitzgerald\">Patrick Fitzgerald</a>, <a href=\"/news/investigations\">Investigations</a>, <a href=\"/news/george-w-bush\">George W. Bush</a>                  </div>\n" +
	  "Goodbye\n";

  private static final String javascriptHtml =
		  "<div>\n" +
			  "<input id=\"__EVENTVALIDATIO\" type=\"hidden\" value=\"/wEWAwKM69DODwKpwbbBAgLz9t3hB0AqeZdR3lGFnROBTmBAbtw5sqoS\" name=\"__EVENTVALIDATION\">\n" +
		  "</div>\n" +
		  "<script type=\"text/javascript\"></script>\n" +
		  "<div>\n" +
			  "<input id=\"__EVENTTARGET\" type=\"hidden\" value=\"\" name=\"__EVENTTARGET\">\n" +
			  "<input id=\"__EVENTARGUMENT\" type=\"hidden\" value=\"\" name=\"__EVENTARGUMENT\">\n" +
			  "<input id=\"_VIEWSTATE\" type=\"hidden\" value=\"/wEPDwUENTM4MQ9kFgJmD2QWAgIDEBYCHgZhY3Rpb24FQy9nYXRld2F5L2NvbnRlbnRvd25lZC9hcnRpY2xlLmFzcHg/dGl0bGVpZD01NTY1NiZhY2Nlc3N0eXBlPWluZm9zY2lkFgoCAw9kFgYCAQ8WAh4HVmlzaWJsZWgWAgIBDw8WAh4EVGV4dGVkZAICD2QWBAIBDw8WAh4LTmF2aWdhdGVVcmwFci9tZW1iZXJzaGlwL2xvZ2luLmFzcHg/cmV0dXJudXJsPSUyZmdhdGV3YXklMmZjb250ZW50b3duZWQlMmZhcnRpY2xlLmFzcHglM2Z0aXRsZWlkJTNkNTU2NTYlMjZhY2Nlc3N0eXBlJTNkaW5mb3NjaWRkAgMPDxYCHwMFHi9tZW1iZXJzaGlwL2NyZWF0ZWFjY291bnQuYXNweGRkAgMPFgIfAWhkAgUPDxYCHwIFBkxPQ0tTU2RkAgkPZBYCZg9kFgQCAQ8PFgIfAgUQRS1EYXRhYm...8WAgU+Y3RsMDAkY3BoQ2VudGVyQ29udGVudCRjdGwwMCR1Y0NpdGVDb250ZW50JGxua1N1Ym1pdFRvUmVmV29ya3MFPWN0bDAwJGNwaENlbnRlckNvbnRlbnQkY3RsMDAkdWNDaXRlQ29udGVudCRsbmtTdWJtaXRUb0Vhc3lCaWIFJGN0bDAwJGNwaENlbnRlckNvbnRlbnQkY3RsMDAkbHN0SXN4bg88KwAKAgcUKwACZGQIAgJkBShjdGwwMCRjcGhTaWRlYmFyUmlnaHQkdG1fR2VuZXJpY0NvbnRlbnQxDxQrAAFkZAU2Y3RsMDAkdWNSZXNlYXJjaENvbGxlY3Rpb25zTmF2aWdhdGlvbiRsc3RQcm9kdWN0c093bmVkDzwrAAoBCGZkBTRjdGwwMCR1Y1Jlc2VhcmNoQ29sbGVjdGlvbnNOYXZpZ2F0aW9uJGxzdEJ1bmRsZVR5cGVzDzwrAAoBCGZkF8NmCQCZmjS6K/62J+kVNIwpJpA=\" name=\"__VIEWSTATE\">\n" +
		  "</div>";
  private static final String javascriptHtmlFiltered =
		  "<div>\n" +
			  "<input id=\"__EVENTVALIDATIO\" type=\"hidden\" value=\"/wEWAwKM69DODwKpwbbBAgLz9t3hB0AqeZdR3lGFnROBTmBAbtw5sqoS\" name=\"__EVENTVALIDATION\">\n" +
		  "</div>\n" +
		  "\n" +
		  "<div>\n" +
			  "<input id=\"__EVENTTARGET\" type=\"hidden\" value=\"\" name=\"__EVENTTARGET\">\n" +
			  "<input id=\"__EVENTARGUMENT\" type=\"hidden\" value=\"\" name=\"__EVENTARGUMENT\">\n" +
			  "<input id=\"_VIEWSTATE\" type=\"hidden\" value=\"/wEPDwUENTM4MQ9kFgJmD2QWAgIDEBYCHgZhY3Rpb24FQy9nYXRld2F5L2NvbnRlbnRvd25lZC9hcnRpY2xlLmFzcHg/dGl0bGVpZD01NTY1NiZhY2Nlc3N0eXBlPWluZm9zY2lkFgoCAw9kFgYCAQ8WAh4HVmlzaWJsZWgWAgIBDw8WAh4EVGV4dGVkZAICD2QWBAIBDw8WAh4LTmF2aWdhdGVVcmwFci9tZW1iZXJzaGlwL2xvZ2luLmFzcHg/cmV0dXJudXJsPSUyZmdhdGV3YXklMmZjb250ZW50b3duZWQlMmZhcnRpY2xlLmFzcHglM2Z0aXRsZWlkJTNkNTU2NTYlMjZhY2Nlc3N0eXBlJTNkaW5mb3NjaWRkAgMPDxYCHwMFHi9tZW1iZXJzaGlwL2NyZWF0ZWFjY291bnQuYXNweGRkAgMPFgIfAWhkAgUPDxYCHwIFBkxPQ0tTU2RkAgkPZBYCZg9kFgQCAQ8PFgIfAgUQRS1EYXRhYm...8WAgU+Y3RsMDAkY3BoQ2VudGVyQ29udGVudCRjdGwwMCR1Y0NpdGVDb250ZW50JGxua1N1Ym1pdFRvUmVmV29ya3MFPWN0bDAwJGNwaENlbnRlckNvbnRlbnQkY3RsMDAkdWNDaXRlQ29udGVudCRsbmtTdWJtaXRUb0Vhc3lCaWIFJGN0bDAwJGNwaENlbnRlckNvbnRlbnQkY3RsMDAkbHN0SXN4bg88KwAKAgcUKwACZGQIAgJkBShjdGwwMCRjcGhTaWRlYmFyUmlnaHQkdG1fR2VuZXJpY0NvbnRlbnQxDxQrAAFkZAU2Y3RsMDAkdWNSZXNlYXJjaENvbGxlY3Rpb25zTmF2aWdhdGlvbiRsc3RQcm9kdWN0c093bmVkDzwrAAoBCGZkBTRjdGwwMCR1Y1Jlc2VhcmNoQ29sbGVjdGlvbnNOYXZpZ2F0aW9uJGxzdEJ1bmRsZVR5cGVzDzwrAAoBCGZkF8NmCQCZmjS6K/62J+kVNIwpJpA=\" name=\"__VIEWSTATE\">\n" +
		  "</div>";
  public static final String dynamicCssHtml = 
    "<link rel=\"stylesheet\" href=\"css/grid2.css?1349472143\" type=\"text/css\" media=\"screen\" />";
  public static final String dynamicCssHtmlFiltered = "";
	

  public void testSibebarFiltering() throws Exception {
    InputStream actIn = fact.createFilteredInputStream(mau,
        new StringInputStream(sidebarHtml),
        Constants.DEFAULT_ENCODING);

    assertEquals(sidebarHtmlFiltered, StringUtil.fromInputStream(actIn));
  }

  public void testInputKeysFiltering() throws Exception {
    InputStream actIn = fact.createFilteredInputStream(mau,
        new StringInputStream(inputKeysHtml),
        Constants.DEFAULT_ENCODING);

    assertEquals(inputKeysHtmlFiltered, StringUtil.fromInputStream(actIn));
  }

  public void testCitationHeaderFiltering() throws Exception {
    InputStream actIn = fact.createFilteredInputStream(mau, 
        new StringInputStream(citationHtml),
        Constants.DEFAULT_ENCODING);

    assertEquals(citationHtmlFiltered, StringUtil.fromInputStream(actIn));
  }

  public void testTrialImageFiltering() throws Exception {
    InputStream actIn = fact.createFilteredInputStream(mau, 
        new StringInputStream(trialImageHtml),
        Constants.DEFAULT_ENCODING);

    assertEquals(trialImageHtmlFiltered, StringUtil.fromInputStream(actIn));
  }

  public void testGoodHtmlFiltering() throws Exception {
    InputStream actIn = fact.createFilteredInputStream(mau,
        new StringInputStream(goodHtml),
        Constants.DEFAULT_ENCODING);
    assertEquals(goodHtml, StringUtil.fromInputStream(actIn));
  }

  public void testJavascriptHtmlFiltering() throws Exception {
    InputStream actIn = fact.createFilteredInputStream(mau,
        new StringInputStream(javascriptHtml),
        Constants.DEFAULT_ENCODING);

    assertEquals(javascriptHtmlFiltered, StringUtil.fromInputStream(actIn));
  }
  public void testInstitutionHeaderHtmlFiltering() throws Exception {
    InputStream actIn = fact.createFilteredInputStream(mau,
        new StringInputStream(institutionHeaderHtml),
        Constants.DEFAULT_ENCODING);

    assertEquals(institutionHeaderHtmlFiltered, StringUtil.fromInputStream(actIn));
  }
  public void testDynamicCssHtmlFiltering() throws Exception {
    InputStream actIn = fact.createFilteredInputStream(mau,
        					       new StringInputStream(dynamicCssHtml),
        					       Constants.DEFAULT_ENCODING);

    assertEquals(dynamicCssHtmlFiltered, StringUtil.fromInputStream(actIn));
  }

}
