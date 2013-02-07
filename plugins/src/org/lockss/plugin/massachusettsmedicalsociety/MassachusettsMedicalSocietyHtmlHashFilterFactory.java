
/*

Copyright (c) 2000-2011 Board of Trustees of Leland Stanford Jr. University,
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

Except as contained in this notice, tMassachusettsMedicalSocietyHtmlFilterFactoryhe name of Stanford University shall not
be used in advertising or otherwise to promote the sale, use or other dealings
in this Software without prior written authorization from Stanford University.

*/

package org.lockss.plugin.massachusettsmedicalsociety;

import java.io.*;

import org.htmlparser.*;
import org.htmlparser.filters.*;
import org.htmlparser.tags.*;
import org.htmlparser.util.NodeList;
import org.htmlparser.util.ParserException;
import org.htmlparser.visitors.NodeVisitor;
import org.lockss.daemon.PluginException;
import org.lockss.filter.WhiteSpaceFilter;
import org.lockss.filter.html.*;
import org.lockss.plugin.*;
import org.lockss.util.Logger;
import org.lockss.util.ReaderInputStream;

public class MassachusettsMedicalSocietyHtmlHashFilterFactory implements FilterFactory {

	Logger log = Logger.getLogger("MassachusettsMedicalSocietyHtmlHashFilterFactory");
	
  public static class FilteringException extends PluginException {
    public FilteringException() { super(); }
    public FilteringException(String msg, Throwable cause) { super(msg, cause); }
    public FilteringException(String msg) { super(msg); }
    public FilteringException(Throwable cause) { super(cause); }
  }
  
  @Override
  public InputStream createFilteredInputStream(ArchivalUnit au,
                                               InputStream in,
                                               String encoding)
      throws PluginException {
	  
    // First filter with HtmlParser
    NodeFilter[] filters = new NodeFilter[] {
        /*
         * Crawl filter
         */
        // Contain cross-links to other articles in other journals/volumes
        HtmlNodeFilters.tagWithAttribute("div", "id", "related"),
        HtmlNodeFilters.tagWithAttribute("div", "id", "trendsBox"),
        // Contains ads
        HtmlNodeFilters.tagWithAttribute("div", "id", "topAdBar"),
        HtmlNodeFilters.tagWithAttribute("div", "class", "topLeftAniv"),
        HtmlNodeFilters.tagWithAttribute("div", "class", "ad"),
        HtmlNodeFilters.tagWithAttribute("div", "id", "rightRailAd"),
        HtmlNodeFilters.tagWithAttribute("div", "class", "rightAd"),
        HtmlNodeFilters.tagWithAttribute("div", "id", "rightAd"),
        HtmlNodeFilters.tagWithAttribute("div", "class", "toolsAd"),
        HtmlNodeFilters.tagWithAttribute("div", "class", "bottomAd"),
        HtmlNodeFilters.tagWithAttribute("div", "class", "bannerAdTower"),
        //Certain ads do not have a specified div and must be removed based on regex
        HtmlNodeFilters.tagWithAttributeRegex("a", "href", "/action/clickThrough"),
        //Contains comments by users with possible links to articles in other journals/volumes
        HtmlNodeFilters.tagWithAttribute("dd", "id", "comments"),
        //Letter possible from a future au
        HtmlNodeFilters.tagWithAttribute("dd", "id", "letters"),
        //Contains links to articles currently citing in other volumes
        HtmlNodeFilters.tagWithAttribute("dd", "id", "citedby"),
        //Contains a link to the correction or the article which is possibly part of another au
        HtmlNodeFilters.tagWithAttribute("div", "class", "articleCorrection"),
        //Group of images/videos that link to other articles
        HtmlNodeFilters.tagWithAttribute("div", "id", "galleryContent"),
        //constantly changing discussion thread with links to current article +?sort=newest...
        HtmlNodeFilters.tagWithAttribute("div", "class", "discussion"),
        /*
         * Hash filter
         */
        // Contains ad-specific cookies
        new TagNameFilter("script"),
        // Contains a modified id that changes
        HtmlNodeFilters.commentWithRegex("modified"),
        // Contains the number of articles currently citing
        HtmlNodeFilters.tagWithAttribute("dt", "id", "citedbyTab"),
        HtmlNodeFilters.tagWithAttribute("div", "class", "articleActivity"),
        // Contains institution name
        HtmlNodeFilters.tagWithAttribute("div", "id", "institutionBox"),
        // Contains copyright year
        HtmlNodeFilters.tagWithAttribute("div", "id", "copyright"),
        // Contains recent issues
        HtmlNodeFilters.tagWithAttribute("a", "class", "issueArchive-recentIssue"),
        //More in ...
        HtmlNodeFilters.tagWithAttribute("div", "id", "moreIn"),
        //ID of the media player tag changes
        HtmlNodeFilters.tagWithAttributeRegex("div", "id", "layerPlayer"),
        //For recent issues you can submit a letter then this feature disappears
        HtmlNodeFilters.tagWithAttribute("li", "class", "submitLetter"),
        //For recent issues you can submit a comment then this feature disappears
        HtmlNodeFilters.tagWithAttribute("p", "class", "openUntilInfo"),
        //Poll if collected while poll is open will change
        HtmlNodeFilters.tagWithAttribute("div", "class", "poll")
        
    };
    
    HtmlTransform xform = new HtmlTransform() {
	    @Override
	    public NodeList transform(NodeList nodeList) throws IOException {
	      try {
	        nodeList.visitAllNodesWith(new NodeVisitor() {
	          @Override
	          public void visitTag(Tag tag) {
	            try {
	              if ("div".equalsIgnoreCase(tag.getTagName()) && tag.getAttribute("id") != null && tag.getAttribute("id").trim().startsWith("layerPlayer")) {
	                tag.removeAttribute("id");
	              }
	              else {
	                super.visitTag(tag);
	              }
	            }
	            catch (Exception exc) {
	              log.debug2("Internal error (visitor)", exc); // Ignore this tag and move on
	            }
	          }
	        });
	      }
	      catch (ParserException pe) {
	        log.debug2("Internal error (parser)", pe); // Bail
	      }
	      return nodeList;
	    }
	  };
	return new HtmlFilterInputStream(in,
              						 encoding,
              						 new HtmlCompoundTransform(HtmlNodeFilterTransform.exclude(new OrFilter(filters)),xform));
  }
  
}
