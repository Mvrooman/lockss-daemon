/*
 * $Id: MetaPressHtmlHashFilterFactory.java,v 1.5 2012/12/12 23:15:55 alexandraohlson Exp $
 */

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

Except as contained in this notice, the name of Stanford University shall not
be used in advertising or otherwise to promote the sale, use or other dealings
in this Software without prior written authorization from Stanford University.

*/

package org.lockss.plugin.metapress;

import java.io.*;

import org.apache.commons.io.IOUtils;
import org.htmlparser.*;
import org.htmlparser.filters.*;
import org.htmlparser.nodes.TextNode;
import org.htmlparser.tags.*;
import org.htmlparser.util.SimpleNodeIterator;
import org.lockss.daemon.PluginException;
import org.lockss.filter.html.*;
import org.lockss.plugin.*;


public class MetaPressHtmlHashFilterFactory implements FilterFactory {

  @Override
  public InputStream createFilteredInputStream(ArchivalUnit au,
                                               InputStream in,
                                               String encoding)
      throws PluginException {
    NodeFilter[] filters = new NodeFilter[] {
        /*
         * From the crawl filter
         */
        // Reverse citations
        HtmlNodeFilters.tagWithAttribute("div", "id", "References"),
        /*
         * Proper to the crawl filter
         */
        // Variable scripting
        new TagNameFilter("script"),
        // ASP state
        HtmlNodeFilters.tagWithAttribute("input", "id", "__EVENTVALIDATION"),
        HtmlNodeFilters.tagWithAttribute("input", "id", "__VIEWSTATE"),
        // Institution name and similar data
        HtmlNodeFilters.tagWithAttribute("div", "id", "ctl00_SidebarRecognitionPanel"),
        /*
         * SFX-related: find a <tr> tag, that has a single <td> child
         * whose 'class' attribute is set to 'labelValue', whose sole
         * child tag is a <div> tag, whose sole child tag is a link
         * that begins with '/home/linkout.mpx?'.
         */
        new NodeFilter() {
          public boolean accept(Node node) {
            if (!(node instanceof TableRow)) { return false;  }
            TableRow tr = (TableRow)node;
            if (tr.getColumnCount() != 1) { return false; }
            TableColumn td = tr.getColumns()[0];
            if (!"labelValue".equalsIgnoreCase(td.getAttribute("class"))) { return false; }
            Div div = null;
            for (SimpleNodeIterator iter = td.elements() ; iter.hasMoreNodes() ; ) {
              Node n = iter.nextNode();
              if (n instanceof TextNode) { continue; }
              if (n instanceof Div) {
                div = (Div)n;
                continue;
              }
              return false;
            }
            for (SimpleNodeIterator iter = div.elements() ; iter.hasMoreNodes() ; ) {
              Node n = iter.nextNode();
              if (n instanceof TextNode) { continue; }
              if (n instanceof LinkTag) {
                String href = ((LinkTag)n).extractLink();
                return href != null && href.startsWith("/home/linkout.mpx?");
              }
              return false;
            }
            return false;
          }
        },
        // Copyright year but also session information
        HtmlNodeFilters.tagWithAttribute("div", "class", "pageFooter"),
        // Dynamic URLs
        HtmlNodeFilters.tagWithAttributeRegex("link", "href", "^/dynamic-file\\.axd"),
        /*
         * This isn't satisfactory. We should be "rewriting" links so
         * that they are sanitized by the URL normalizer. This is a
         * bit much.
         */
        HtmlNodeFilters.tagWithAttributeRegex("a", "href", "[?&amp;]p="),
        // Institution-specific greeting
        HtmlNodeFilters.tagWithAttribute("div", "class", "MetaPress_Products_Reader_Web_UI_Controls_RecognizedAsControlBody"),
        // Institution-specific greeting
        HtmlNodeFilters.tagWithAttribute("div", "class", "PersonalizationPanel"),
        // Remote address and user agent
        HtmlNodeFilters.tagWithAttribute("div", "class", "MetaPress_Products_Reader_Web_UI_Controls_FooterControlUserDetails"),
    };
    return new HtmlFilterInputStream(in,
                                     encoding,
                                     HtmlNodeFilterTransform.exclude(new OrFilter(filters)));
  }
  
}

