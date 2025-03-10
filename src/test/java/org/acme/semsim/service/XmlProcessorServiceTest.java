package org.acme.semsim.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.acme.semsim.resource.SimilarityResource;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.util.List;

import static org.acme.semsim.service.XmlProcessorService.buildDocument;
import static org.acme.semsim.service.XmlProcessorService.createWorkingCopy;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class XmlProcessorServiceTest {

	String defaultElement = "p";

	@Inject
	XmlProcessorService xmlProcessorService;

	@Test
	public void testExtractTextFromParagraphs() throws Exception {
		// Test XML with multiple paragraphs
		String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
				"<document>\n" +
				"\t<title>Test Document</title>\n" +
				"\t<content>\n" +
				"\t\t<p>This is the first paragraph.</p>\n" +
				"\t\t<p>This is the second paragraph.</p>\n" +
				"\t\t<p>This is the third paragraph.</p>\n" +
				"\t</content>\n" +
				"</document>";

		// Build document
		Document document = buildDocument(xml);

		// Default element is "p"
		List<String> paragraphs = xmlProcessorService.extractElementTextFromXml(document, defaultElement);

		assertNotNull(paragraphs, "Extracted text should not be null");
		assertEquals(3, paragraphs.size(), "Should extract 3 paragraphs");

		// Check that paragraphs contain the expected text
		assertEquals("This is the first paragraph.", paragraphs.get(0));
		assertEquals("This is the second paragraph.", paragraphs.get(1));
		assertEquals("This is the third paragraph.", paragraphs.get(2));
	}

	@Test
	public void testExtractTextFromNestedElements() throws Exception {
		// Test XML with nested elements
		String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
				"<document>\n" +
				"\t<section>\n" +
				"\t\t<p>This is a paragraph with <b>bold text</b> and <i>italic text</i>.</p>\n" +
				"\t\t<p>This is another paragraph with <span>nested content</span>.</p>\n" +
				"\t</section>\n" +
				"</document>";

		Document document = buildDocument(xml);

		List<String> paragraphs = xmlProcessorService.extractElementTextFromXml(document, defaultElement);

		assertNotNull(paragraphs, "Extracted text should not be null");
		assertEquals(2, paragraphs.size(), "Should extract 2 paragraphs");

		// Check that paragraphs contain the expected text with nested content
		assertEquals("This is a paragraph with bold text and italic text.", paragraphs.get(0));
		assertEquals("This is another paragraph with nested content.", paragraphs.get(1));
	}

	@Test
	public void testBuildDocumentFromInvalidXml() {
		// Test with invalid XML
		String invalidXml = "<invalid>xml";

		// buildDocument should handle invalid XML gracefully
		assertThrows(Exception.class, () -> {
			buildDocument(invalidXml);
		}, "Should throw exception for invalid XML");
	}

	@Test
	public void testBuildDocumentFromEmptyXml() {
		// Test with empty XML
		String emptyXml = "";

		// buildDocument should handle empty XML gracefully
		assertThrows(SAXException.class, () -> {
			buildDocument(emptyXml);
		}, "Should throw SAXException for premature end of file.");
	}

	@Test
	public void testExtractTextWithSpecialCharacters() throws Exception {
		// Test XML with special characters
		String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
				"<document>\n" +
				"\t<p>This has special chars: &amp; &lt; &gt;</p>\n" +
				"\t<p>This has numbers: 1, 2, 3</p>\n" +
				"</document>";

		Document document = buildDocument(xml);

		List<String> paragraphs = xmlProcessorService.extractElementTextFromXml(document, defaultElement);

		assertNotNull(paragraphs, "Extracted text should not be null");
		assertEquals(2, paragraphs.size(), "Should extract 2 paragraphs");

		// Check that paragraphs contain the expected text with entities decoded
		assertEquals("This has special chars: & < >", paragraphs.get(0));
		assertEquals("This has numbers: 1, 2, 3", paragraphs.get(1));
	}

	@Test
	public void testExtractTextWithMultipleElementTypes() throws Exception {
		// Test XML with multiple element types
		String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
				"<document>\n" +
				"\t<title>Test Document</title>\n" +
				"\t<content>\n" +
				"\t\t<p>This is a paragraph.</p>\n" +
				"\t\t<div>This is a div.</div>\n" +
				"\t\t<section>\n" +
				"\t\t\t<p>This is a nested paragraph.</p>\n" +
				"\t\t\t<h1>This is a heading.</h1>\n" +
				"\t\t</section>\n" +
				"\t</content>\n" +
				"</document>";

		Document document = buildDocument(xml);

		// Test with different element names

		// 1. Extract from all paragraphs (default)
		List<String> paragraphs = xmlProcessorService.extractElementTextFromXml(document, defaultElement);
		assertEquals(2, paragraphs.size(), "Should extract 2 paragraphs");
		assertEquals("This is a paragraph.", paragraphs.get(0));
		assertEquals("This is a nested paragraph.", paragraphs.get(1));

		// 2. Extract from div elements
		List<String> divs = xmlProcessorService.extractElementTextFromXml(document, "div");
		assertEquals(1, divs.size(), "Should extract 1 div");
		assertEquals("This is a div.", divs.get(0));

		// 3. Extract from title elements
		List<String> titles = xmlProcessorService.extractElementTextFromXml(document, "title");
		assertEquals(1, titles.size(), "Should extract 1 title");
		assertEquals("Test Document", titles.get(0));

		// 4. Extract from heading elements
		List<String> headings = xmlProcessorService.extractElementTextFromXml(document, "h1");
		assertEquals(1, headings.size(), "Should extract 1 heading");
		assertEquals("This is a heading.", headings.get(0));

		// 5. Extract from multiple element types
		List<String> mixedElements = xmlProcessorService.extractElementTextFromXml(document, "p div");
		assertEquals(3, mixedElements.size(), "Should extract 3 elements (2 paragraphs and 1 div)");
		assertTrue(mixedElements.contains("This is a paragraph."));
		assertTrue(mixedElements.contains("This is a div."));
		assertTrue(mixedElements.contains("This is a nested paragraph."));

		// 6. Extract from all specified elements
		List<String> allElements = xmlProcessorService.extractElementTextFromXml(document, "p div h1 title");
		assertEquals(5, allElements.size(), "Should extract 5 elements");
		assertTrue(allElements.contains("Test Document"));
		assertTrue(allElements.contains("This is a paragraph."));
		assertTrue(allElements.contains("This is a div."));
		assertTrue(allElements.contains("This is a nested paragraph."));
		assertTrue(allElements.contains("This is a heading."));
	}

	@Test
	public void testWhitespaceNormalization() throws Exception {
		// Test XML with whitespace issues
		String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
				"<document>\n" +
				"\t<p>This has \n multiple \t lines \n and \t tabs.</p>\n" +
				"\t<p>  This has   extra   spaces.  </p>\n" +
				"</document>";

		Document document = buildDocument(xml);

		List<String> paragraphs = xmlProcessorService.extractElementTextFromXml(document, defaultElement);

		assertNotNull(paragraphs, "Extracted text should not be null");
		assertEquals(2, paragraphs.size(), "Should extract 2 paragraphs");

		// Check that whitespace is normalized
		assertEquals("This has multiple lines and tabs.", paragraphs.get(0));
		assertEquals("This has extra spaces.", paragraphs.get(1));
	}

	@Test
	public void testCreateWorkingCopy() throws Exception {
		// Test XML with multiple paragraphs
		String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
				"<document>\n" +
				"\t<title>Test Document</title>\n" +
				"\t<content>\n" +
				"\t\t<p>This is the first paragraph.</p>\n" +
				"\t\t<p>This is the second paragraph.</p>\n" +
				"\t\t<p>This is the third paragraph.</p>\n" +
				"\t</content>\n" +
				"</document>";

		// Build document
		Document document = buildDocument(xml);

		// Create a working copy
		Document workingCopy = createWorkingCopy(xml, "p title");

		// Verify that the working copy is not the same as the original
		assertNotSame(document, workingCopy,
				"Working copy should not be the same as the original document");

		// Verify that the working copy has the same content
		assertNotSame(document.getDocumentElement().getTextContent(), workingCopy.getDocumentElement().getTextContent(),
				"Working copy should not have the same content as the original document");

		// Look up all p and title elements and count they are four
		assertEquals(4, workingCopy.getElementsByTagName("p").getLength() + workingCopy.getElementsByTagName("title").getLength(),
				"Working copy should have 4 elements (3 paragraphs and 1 title)");
	}
}