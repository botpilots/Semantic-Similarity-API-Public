package org.acme.semsim.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class XmlProcessorServiceTest {

	@Inject
	XmlProcessorService xmlProcessorService;

	@Test
	public void testExtractSentencesFromXml() throws Exception {
		// Test XML with multiple paragraphs and sentences
		String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
				"<document>\n" +
				"\t<title>Test Document</title>\n" +
				"\t<content>\n" +
				"\t\t<paragraph>This is sentence one. This is sentence two.</paragraph>\n" +
				"\t\t<paragraph>This is sentence three. This is sentence four.</paragraph>\n" +
				"\t</content>\n" +
				"</document>";

		List<String> sentences = xmlProcessorService.extractSentencesFromXml(xml);

		assertNotNull(sentences, "Sentences should not be null");
		assertEquals(4, sentences.size(), "Should extract 4 sentences");

		// Check that sentences contain the expected text
		// The exact sentence text might vary based on the implementation
		assertTrue(sentences.stream().anyMatch(s -> s.contains("sentence one")),
				"Should contain text with 'sentence one'");
		assertTrue(sentences.stream().anyMatch(s -> s.contains("sentence two")),
				"Should contain text with 'sentence two'");
		assertTrue(sentences.stream().anyMatch(s -> s.contains("sentence three")),
				"Should contain text with 'sentence three'");
		assertTrue(sentences.stream().anyMatch(s -> s.contains("sentence four")),
				"Should contain text with 'sentence four'");
	}

	@Test
	public void testExtractSentencesFromNestedXml() throws Exception {
		// Test XML with nested elements
		String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
				"<document>\n" +
				"\t<section>\n" +
				"\t\t<subsection>\n" +
				"\t\t\t<paragraph>Nested sentence one. Nested sentence two.</paragraph>\n" +
				"\t\t</subsection>\n" +
				"\t</section>\n" +
				"</document>";

		List<String> sentences = xmlProcessorService.extractSentencesFromXml(xml);

		assertNotNull(sentences, "Sentences should not be null");
		assertEquals(2, sentences.size(), "Should extract 2 sentences");

		// Check that sentences contain the expected text
		assertTrue(sentences.stream().anyMatch(s -> s.contains("Nested sentence one")),
				"Should contain text with 'Nested sentence one'");
		assertTrue(sentences.stream().anyMatch(s -> s.contains("Nested sentence two")),
				"Should contain text with 'Nested sentence two'");
	}

	@Test
	public void testExtractSentencesFromInvalidXml() {
		// Test with invalid XML
		String invalidXml = "<invalid>xml";

		// The service should handle invalid XML gracefully
		assertThrows(Exception.class, () -> {
			try {
				xmlProcessorService.extractSentencesFromXml(invalidXml);
			} catch (Exception e) {
				throw e;
			}
		}, "Should throw exception for invalid XML");
	}

	@Test
	public void testExtractSentencesFromEmptyXml() {
		// Test with empty XML
		String emptyXml = "";

		// The service should handle empty XML gracefully
		assertThrows(Exception.class, () -> {
			try {
				xmlProcessorService.extractSentencesFromXml(emptyXml);
			} catch (Exception e) {
				throw e;
			}
		}, "Should throw exception for empty XML");
	}

	@Test
	public void testExtractSentencesWithSpecialCharacters() throws Exception {
		// Test XML with special characters
		String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
				"<document>\n" +
				"\t<paragraph>This has special chars: &amp; &lt; &gt;. This has numbers: 1, 2, 3.</paragraph>\n" +
				"</document>";

		List<String> sentences = xmlProcessorService.extractSentencesFromXml(xml);

		assertNotNull(sentences, "Sentences should not be null");
		assertEquals(2, sentences.size(), "Should extract 2 sentences");

		// Check that sentences contain the expected text with entities decoded
		assertTrue(sentences.stream().anyMatch(s -> s.contains("special chars")),
				"Should contain text with 'special chars'");
		assertTrue(sentences.stream().anyMatch(s -> s.contains("numbers")),
				"Should contain text with 'numbers'");
	}

	@Test
	public void testExtractSentencesWithXPath() throws Exception {
		// Test XML with multiple paragraphs
		String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
				"<document>\n" +
				"\t<title>Test Document</title>\n" +
				"\t<content>\n" +
				"\t\t<paragraph>This is paragraph one. It has two sentences.</paragraph>\n" +
				"\t\t<paragraph>This is paragraph two. It also has two sentences.</paragraph>\n" +
				"\t\t<section>\n" +
				"\t\t\t<paragraph>This is paragraph three. It's in a section.</paragraph>\n" +
				"\t\t</section>\n" +
				"\t</content>\n" +
				"</document>";

		// Test with different XPath expressions

		// 1. Extract from all paragraphs
		List<String> allParagraphSentences = xmlProcessorService.extractSentencesFromXml(xml, "//paragraph");
		assertEquals(6, allParagraphSentences.size(), "Should extract 6 sentences from all paragraphs");

		// 2. Extract from first paragraph only
		List<String> firstParagraphSentences = xmlProcessorService.extractSentencesFromXml(xml, "//paragraph[1]");
		// The XPath expression matches both the first paragraph in content and the
		// first paragraph in section
		assertEquals(4, firstParagraphSentences.size(), "Should extract 4 sentences from matching paragraphs");
		assertTrue(firstParagraphSentences.stream().anyMatch(s -> s.contains("paragraph one")),
				"Should contain text from first paragraph");

		// 3. Extract from paragraphs in content (not in section)
		List<String> contentParagraphSentences = xmlProcessorService.extractSentencesFromXml(xml,
				"/document/content/paragraph");
		assertEquals(4, contentParagraphSentences.size(), "Should extract 4 sentences from content paragraphs");

		// 4. Extract from title element
		List<String> titleSentences = xmlProcessorService.extractSentencesFromXml(xml, "//title");
		assertEquals(1, titleSentences.size(), "Should extract 1 sentence from title");
		assertEquals("Test Document", titleSentences.get(0), "Should extract the title text");

		// 5. Extract with complex XPath (paragraphs with position < 3)
		List<String> firstTwoParagraphsSentences = xmlProcessorService.extractSentencesFromXml(xml,
				"//paragraph[position()<3]");
		// This matches all paragraphs that are the first or second child of their
		// parent
		assertEquals(6, firstTwoParagraphsSentences.size(),
				"Should extract 6 sentences from paragraphs with position < 3");

		// 6. Test with default XPath (should extract all text)
		List<String> allSentences = xmlProcessorService.extractSentencesFromXml(xml);
		// The default XPath is self::* which only selects the document element itself
		// In this test case, it extracts the same sentences as //paragraph
		assertEquals(6, allSentences.size(), "Should extract 6 sentences from the document element");
	}
}