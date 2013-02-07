/*
 * $Id: PdfBoxPage.java,v 1.4 2012/11/20 01:39:19 thib_gc Exp $
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

package org.lockss.pdf.pdfbox;

import java.io.*;
import java.util.*;

import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.graphics.xobject.*;
import org.lockss.pdf.*;
import org.lockss.util.Logger;

/**
 * <p>
 * A {@link PdfPage} implementation based on PDFBox 1.6.0.
 * </p>
 * <p>
 * This class acts as an adapter for the {@link PDPage} class.
 * </p>
 * @author Thib Guicherd-Callin
 * @since 1.56
 * @see PdfBoxDocumentFactory
 */
public class PdfBoxPage implements PdfPage {

  /**
   * <p>
   * Logger for use by this class.
   * </p>
   * @since 1.56
   */
  private static final Logger logger = Logger.getLogger(PdfBoxPage.class);
  
  /**
   * <p>
   * Determines if the given {@link PDXObject} instance is a byte
   * stream (as opposed to a token stream).
   * </p>
   * @param xObject A {@link PDXObject} instance.
   * @return <code>true</code> if and only if the argument is a byte
   *         stream.
   * @since 1.56.3
   * @see #isTokenStream(PDXObject)
   */
  private static boolean isByteStream(PDXObject xObject) {
    /*
     * IMPLEMENTATION NOTE
     * 
     * PDXObject is an abstract class whose subtypes all represent
     * image XObjects (PDJpeg, PDCcitt or PDPixelMap) i.e. a byte
     * stream, except one, PDXObjectForm, which represents a form
     * XObject i.e. a token stream.
     */
    return !isTokenStream(xObject);
  }

  /**
   * <p>
   * Determines if the given {@link PDXObject} instance is a token
   * stream (as opposed to a byte stream).
   * </p>
   * @param xObject A {@link PDXObject} instance.
   * @return <code>true</code> if and only if the argument is a token
   *         stream.
   * @since 1.56.3
   */
  private static boolean isTokenStream(PDXObject xObject) {
    /*
     * IMPLEMENTATION NOTE
     * 
     * PDXObject is an abstract class whose subtypes all represent
     * image XObjects (PDJpeg, PDCcitt or PDPixelMap) i.e. a byte
     * stream, except one, PDXObjectForm, which represents a form
     * XObject i.e. a token stream.
     */
    return xObject instanceof PDXObjectForm;
  }
  
  /**
   * <p>
   * The parent {@link PdfBoxDocument} instance.
   * </p>
   * @since 1.56
   */
  protected final PdfBoxDocument pdfBoxDocument;

  /**
   * <p>
   * The {@link PDPage) instance this instance represents.
   * </p>
   * @since 1.56
   */
  protected final PDPage pdPage;

  /**
   * <p>
   * This constructor is accessible to classes in this package and
   * subclasses.
   * </p>
   * @param pdfBoxDocument The parent {@link PdfBoxDocument} instance.
   * @param pdPage The {@link PDPage} instance underpinning this PDF
   *          page.
   * @since 1.56
   */
  protected PdfBoxPage(PdfBoxDocument pdfBoxDocument,
                       PDPage pdPage) {
    this.pdfBoxDocument = pdfBoxDocument;
    this.pdPage = pdPage;
  }

  @Override
  public List<InputStream> getAllByteStreams() throws PdfException {
    final List<InputStream> ret = new ArrayList<InputStream>();
    
    PdfTokenStreamWorker worker = new PdfTokenStreamWorker() {
      @Override public void operatorCallback() throws PdfException {
        // 'ID' and 'BI'
        if (   PdfOpcodes.BEGIN_IMAGE_DATA.equals(getOpcode())
            || PdfOpcodes.BEGIN_IMAGE_OBJECT.equals(getOpcode())) {
          /*
           * IMPLEMENTATION NOTE
           * 
           * getImageData() (PDFBox 1.6.0: PDFOperator line 105) does
           * not copy the byte array, nor does ByteArrayInputStream
           * (http://docs.oracle.com/javase/1.5.0/docs/api/java/io/ByteArrayInputStream.html#ByteArrayInputStream%28byte[]%29).
           */
          ret.add(new ByteArrayInputStream(PdfBoxTokens.asPDFOperator(getOperator()).getImageData()));
        }
        // 'Do'
        else if (PdfOpcodes.INVOKE_XOBJECT.equals(getOpcode())) {
          PdfToken operand = getTokens().get(getIndex() - 1);
          if (operand.isName()) {
            PDXObject xObject = getPDXObjectByName(operand.getName());
            if (isByteStream(xObject)) {
              try {
                ret.add(xObject.getCOSStream().getUnfilteredStream());
              }
              catch (IOException ioe) {
                logger.debug2("getAllByteStreams: Error retrieving a byte stream", ioe);
              }
            }
          }
          logger.debug2("getAllByteStreams: invalid input");
        }
      }
    };
    
    worker.process(getPageTokenStream());
    return ret;
  }

  @Override
  public List<PdfTokenStream> getAllTokenStreams() throws PdfException {
    final List<PdfTokenStream> ret = new ArrayList<PdfTokenStream>();
    ret.add(getPageTokenStream()); // First, add the page stream itself

    PdfTokenStreamWorker worker = new PdfTokenStreamWorker() {
      @Override public void operatorCallback() throws PdfException {
        if (PdfOpcodes.INVOKE_XOBJECT.equals(getOperator().getOperator())) {
          PdfToken operand = getTokens().get(getIndex() - 1);
          if (operand.isName()) {
            PDXObject xObject = getPDXObjectByName(operand.getName());
            if (isTokenStream(xObject)) {
              ret.add(new PdfBoxXObjectTokenStream(PdfBoxPage.this, (PDXObjectForm)xObject));
            }
          }
          logger.debug2("getAllTokenStreams: invalid input");
        }
      }
    };
    
    worker.process(getPageTokenStream());
    return ret;
  }

  @Override
  public List<PdfToken> getAnnotations() {
    /*
     * IMPLEMENTATION NOTE
     * 
     * Annotations are just dictionaries, but because there are many
     * types, the PDFBox API defines a vast hierarchy of objects to
     * represent them. At this time, this is way too much detail for
     * this API, because only one type of annotation has a foreseeable
     * use case (the Link type). So for now, we are only representing
     * annotations as the dictionaries they are by circumventing the
     * PDAnnotation factory call in getAnnotations() (PDFBox 1.6.0:
     * PDPage line 780).
     */
    COSDictionary pageDictionary = pdPage.getCOSDictionary();
    COSArray annots = (COSArray)pageDictionary.getDictionaryObject(COSName.ANNOTS);
    if (annots == null) {
      return new ArrayList<PdfToken>();
    }
    return PdfBoxTokens.getArray(annots);
  }
  
  @Override
  public PdfDocument getDocument() {
    return pdfBoxDocument;
  }
  
  @Override
  public PdfTokenStream getPageTokenStream() throws PdfException {
    try {
      return new PdfBoxPageTokenStream(this, pdPage.getContents());
    }
    catch (IOException ioe) {
      throw new PdfException("Failed to get the page content stream", ioe);
    }
  }

  @Override
  public PdfTokenFactory getTokenFactory() throws PdfException {
    return getDocument().getTokenFactory();
  }

  @Override
  public void setAnnotations(List<PdfToken> annotations) {
    pdPage.getCOSDictionary().setItem(COSName.ANNOTS, PdfBoxTokens.asCOSArray(annotations));
  }
  
  /**
   * <p>
   * Returns an XObject from this page, as a {@link PDXObject}
   * instance.
   * </p>
   * @param name The name of the desired XObject.
   * @return The requested XObject, or <code>null</code> if not found.
   * @throws PdfException If PDF processing fails.
   * @since 1.56
   */
  protected PDXObject getPDXObjectByName(String name) throws PdfException {
    /*
     * IMPLEMENTATION NOTE
     * 
     * This map contains objects of type PDXObject (PDFBox 1.6.0:
     * see PDResources lines 157 and 160), which are null, or of
     * type either PDXObjectForm (see PDXObject line 162) or PDJpeg
     * (line 140) or PDCcitt (line 144) or PDPixelMap (line 153).
     * The latter three have a common supertype, PDXObjectImage.
     */
    return (PDXObject)(pdPage.getResources().getXObjects().get(name));
  }
  
}
