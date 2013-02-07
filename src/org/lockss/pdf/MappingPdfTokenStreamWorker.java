/*
 * $Id: MappingPdfTokenStreamWorker.java,v 1.2 2012/07/19 04:01:53 thib_gc Exp $
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

package org.lockss.pdf;

import java.util.*;

public abstract class MappingPdfTokenStreamWorker extends PdfTokenStreamWorker {

  public interface Action {
    
    void actionCallback(int index,
                        PdfToken operator,
                        List<PdfToken> operands)
        throws PdfException;
    
  }
  
  private Action defaultAction;

  private Map<String, Action> mapping;
  
  public MappingPdfTokenStreamWorker() {
    this.mapping = new HashMap<String, Action>();
    this.defaultAction = null;
  }
  
  public void addAction(String operator, Action action) {
    mapping.put(operator, action);
  }
  
  @Override
  public void operatorCallback() throws PdfException {
    Action action = mapping.get(getOperator().getOperator());
    if (action == null) {
      action = defaultAction;
    }
    if (action != null) {
      action.actionCallback(getIndex(), getOperator(), getOperands());
    }
  }
  
  public void setDefaultAction(Action defaultAction) {
    this.defaultAction = defaultAction;
  }
 
}
