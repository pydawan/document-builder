package com.craigburke.document.builder

import com.craigburke.document.builder.render.ParagraphRenderer
import com.craigburke.document.builder.render.TableRenderer
import com.craigburke.document.core.EmbeddedFont
import com.craigburke.document.core.builder.RenderState
import groovy.transform.InheritConstructors
import groovy.xml.MarkupBuilder

import com.craigburke.document.core.builder.DocumentBuilder
import com.craigburke.document.core.Document
import com.craigburke.document.core.Paragraph
import com.craigburke.document.core.Table
import com.craigburke.document.core.Image

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.common.PDMetadata

/**
 * Builder for PDF documents
 * @author Craig Burke
 */
@InheritConstructors
class PdfDocumentBuilder extends DocumentBuilder {

	void initializeDocument(Document document, OutputStream out) {
        PdfDocument pdfDocument = new PdfDocument(document)
        document.item = pdfDocument

        pdfDocument.x = document.margin.left
        pdfDocument.y = document.margin.top

        document.item = pdfDocument
    }

    @Override
    void addFont(EmbeddedFont embeddedFont) {
        super.addFont(embeddedFont)
        PdfFont.addFont(document.item.pdDocument, embeddedFont)
    }

	def addParagraphToDocument = { Paragraph paragraph, Document document ->
		document.item.x = paragraph.margin.left + document.margin.left
        document.item.scrollDownPage(paragraph.margin.top)
	}

	def onParagraphComplete = { Paragraph paragraph ->
        if (renderState == RenderState.PAGE) {
            int pageWidth = document.item.currentPage.mediaBox.width - document.margin.left - document.margin.right
            int maxLineWidth = pageWidth - paragraph.margin.left - paragraph.margin.right
            int renderStartX = document.margin.left + paragraph.margin.left

            ParagraphRenderer paragraphRenderer = new ParagraphRenderer(paragraph, document, renderStartX, maxLineWidth)
            paragraphRenderer.render()

            document.item.scrollDownPage(paragraph.margin.bottom)
        }
    }

	def addTableToDocument = { Table table, Document document ->
        document.item.x = table.margin.left + document.margin.left
        document.item.scrollDownPage(table.margin.top)
	}

    def onTableComplete = { Table table ->
        if (renderState == RenderState.PAGE) {
            TableRenderer tableRenderer = new TableRenderer(table, document)
            tableRenderer.render()
            document.item.scrollDownPage(table.margin.bottom)
        }
    }

	void writeDocument(Document document, OutputStream out) {
        addHeaderFooter()
		addMetadata()

		document.item.contentStream?.close()
		document.item.pdDocument.save(out)
		document.item.pdDocument.close()
	}

    private void addHeaderFooter() {
        int pageCount = document.pageCount ?: document.item.pages.size()

        (1..pageCount).each { int pageNumber ->
            document.item.pageNumber = pageNumber

            if (document.header) {
                renderState = RenderState.HEADER
                def header = document.header()
                document.item.y = header.margin.top
                int xStart = header.margin.left
                renderHeaderFooter(header, xStart)
            }
            if (document.footer) {
                renderState = RenderState.FOOTER
                def footer = document.footer()
                document.item.y = document.item.pageBottomY + footer.margin.top
                int xStart = footer.margin.left
                renderHeaderFooter(footer, xStart)
            }
        }
        
        renderState = RenderState.PAGE
    }

    private void renderHeaderFooter(headerFooter, int xStart) {
        if (headerFooter instanceof Paragraph) {
            ParagraphRenderer renderer = new ParagraphRenderer(headerFooter, document, xStart, document.width)
            renderer.render(renderState)
        }
        else if (headerFooter instanceof Table) {
            TableRenderer renderer = new TableRenderer(headerFooter, document)
            renderer.render(renderState)
        }
        
    }

	private void addMetadata() {
		ByteArrayOutputStream xmpOut = new ByteArrayOutputStream()
		def xml = new MarkupBuilder(xmpOut.newWriter())

		xml.document(marginTop:"${document.margin.top}", marginBottom:"${document.margin.bottom}",
                marginLeft:"${document.margin.left}", marginRight:"${document.margin.right}") {

			delegate = xml
			resolveStrategy = Closure.DELEGATE_FIRST

			document.children.each { child ->
				if (child.getClass() == Paragraph) {
                    addParagraphToMetadata(delegate, child)
				}
				else {
                    addTableToMetadata(delegate, child)
                }
			}
		}

		def catalog = document.item.pdDocument.documentCatalog
        InputStream inputStream = new ByteArrayInputStream(xmpOut.toByteArray())

		PDMetadata metadata = new PDMetadata(document.item.pdDocument as PDDocument, inputStream, false)
		catalog.metadata = metadata
	}

    private void addParagraphToMetadata(builder, Paragraph paragraphNode) {
        builder.paragraph(marginTop:"${paragraphNode.margin.top}",
                marginBottom:"${paragraphNode.margin.bottom}",
                marginLeft:"${paragraphNode.margin.left}",
                marginRight:"${paragraphNode.margin.right}") {
                    paragraphNode.children?.findAll { it.getClass() == Image }.each {
                        builder.image()
                    }
                }
    }

    private void addTableToMetadata(builder, Table tableNode) {

        builder.table(columns:tableNode.columns, width:tableNode.width, borderSize:tableNode.border.size) {

            delegate = builder
            resolveStrategy = Closure.DELEGATE_FIRST

            tableNode.children.each {
                def cells = it.children
                row {
                    cells.each {
                        cell(width:"${it.width ?: 0}")
                    }
                }
            }
        }
    }

}
