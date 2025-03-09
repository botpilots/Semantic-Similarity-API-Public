package org.acme.semsim.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

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

		// Default element is "p"
		List<String> paragraphs = xmlProcessorService.extractElementTextFromXml(xml, defaultElement);

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

		List<String> paragraphs = xmlProcessorService.extractElementTextFromXml(xml, defaultElement);

		assertNotNull(paragraphs, "Extracted text should not be null");
		assertEquals(2, paragraphs.size(), "Should extract 2 paragraphs");

		// Check that paragraphs contain the expected text with nested content
		assertEquals("This is a paragraph with bold text and italic text.", paragraphs.get(0));
		assertEquals("This is another paragraph with nested content.", paragraphs.get(1));
	}

	@Test
	public void testExtractTextFromInvalidXml() {
		// Test with invalid XML
		String invalidXml = "<invalid>xml";

		// The service should handle invalid XML gracefully
		assertThrows(Exception.class, () -> {
			xmlProcessorService.extractElementTextFromXml(invalidXml, defaultElement);
		}, "Should throw exception for invalid XML");
	}

	@Test
	public void testExtractTextFromEmptyXml() {
		// Test with empty XML
		String emptyXml = "";

		// The service should handle empty XML gracefully
		assertThrows(Exception.class, () -> {
			xmlProcessorService.extractElementTextFromXml(emptyXml, defaultElement);
		}, "Should throw exception for empty XML");
	}

	@Test
	public void testExtractTextWithSpecialCharacters() throws Exception {
		// Test XML with special characters
		String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
				"<document>\n" +
				"\t<p>This has special chars: &amp; &lt; &gt;</p>\n" +
				"\t<p>This has numbers: 1, 2, 3</p>\n" +
				"</document>";

		List<String> paragraphs = xmlProcessorService.extractElementTextFromXml(xml, defaultElement);

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

		// Test with different element names

		// 1. Extract from all paragraphs (default)
		List<String> paragraphs = xmlProcessorService.extractElementTextFromXml(xml, defaultElement);
		assertEquals(2, paragraphs.size(), "Should extract 2 paragraphs");
		assertEquals("This is a paragraph.", paragraphs.get(0));
		assertEquals("This is a nested paragraph.", paragraphs.get(1));

		// 2. Extract from div elements
		List<String> divs = xmlProcessorService.extractElementTextFromXml(xml, "div");
		assertEquals(1, divs.size(), "Should extract 1 div");
		assertEquals("This is a div.", divs.get(0));

		// 3. Extract from title elements
		List<String> titles = xmlProcessorService.extractElementTextFromXml(xml, "title");
		assertEquals(1, titles.size(), "Should extract 1 title");
		assertEquals("Test Document", titles.get(0));

		// 4. Extract from heading elements
		List<String> headings = xmlProcessorService.extractElementTextFromXml(xml, "h1");
		assertEquals(1, headings.size(), "Should extract 1 heading");
		assertEquals("This is a heading.", headings.get(0));

		// 5. Extract from multiple element types
		List<String> mixedElements = xmlProcessorService.extractElementTextFromXml(xml, "p div");
		assertEquals(3, mixedElements.size(), "Should extract 3 elements (2 paragraphs and 1 div)");
		assertTrue(mixedElements.contains("This is a paragraph."));
		assertTrue(mixedElements.contains("This is a div."));
		assertTrue(mixedElements.contains("This is a nested paragraph."));

		// 6. Extract from all specified elements
		List<String> allElements = xmlProcessorService.extractElementTextFromXml(xml, "p div h1 title");
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

		List<String> paragraphs = xmlProcessorService.extractElementTextFromXml(xml, defaultElement);

		assertNotNull(paragraphs, "Extracted text should not be null");
		assertEquals(2, paragraphs.size(), "Should extract 2 paragraphs");

		// Check that whitespace is normalized
		assertEquals("This has multiple lines and tabs.", paragraphs.get(0));
		assertEquals("This has extra spaces.", paragraphs.get(1));
	}
}