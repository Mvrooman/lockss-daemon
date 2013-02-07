
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

package org.lockss.plugin.maffey;

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

public class MaffeyHtmlHashFilterFactory implements FilterFactory {

  Logger log = Logger.getLogger("MaffeyHtmlFilterFactoryy");
	
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
    //Related articles
    HtmlNodeFilters.tagWithAttribute("div", "class", "alsoRead"),
    /*
    * Hash filter
    */
    // Contains ad-specific cookies
    new TagNameFilter("script"),
    //Ad
    HtmlNodeFilters.tagWithAttribute("div", "id", "ad_holder"),
    //Dicussion and comments
    HtmlNodeFilters.tagWithAttribute("div", "id", "commentsBoxes"),
    //Constantly changing reference to css file: css/grid.css?1337026463
    HtmlNodeFilters.tagWithAttributeRegex("link", "href", "css/grid.css\\?[0-9]+"),
    //Article views <p class="article_views_p">
    HtmlNodeFilters.tagWithAttribute("p", "class", "article_views_p"),
    //News items change over time <div id="news_holder">
    HtmlNodeFilters.tagWithAttribute("div", "id", "news_holder"),
    //Chat with support availability status changes
    HtmlNodeFilters.tagWithAttribute("div", "class", "searchleft"),
    //Rotating user testimonials	
    HtmlNodeFilters.tagWithAttribute("div", "class", "categoriescolumn4"),
    //# article views
    HtmlNodeFilters.tagWithAttribute("div", "class", "yellowbgright1"),
    HtmlNodeFilters.tagWithAttribute("div", "class", "article_meta_stats"),
    //# total libertas academica article views
    HtmlNodeFilters.tagWithAttribute("p", "class", "laarticleviews"),
    //dynamic css/js urls
    new TagNameFilter("link")
    };
    
    return new HtmlFilterInputStream(in,
              		             encoding,
              			     new HtmlCompoundTransform(HtmlNodeFilterTransform.exclude(new OrFilter(filters))));
  }
  
}
