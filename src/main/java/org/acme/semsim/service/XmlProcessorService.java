package org.acme.semsim.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for parsing XML and extracting sentences.
 */
@ApplicationScoped
public class XmlProcessorService {

	private static final Logger LOG = Logger.getLogger(XmlProcessorService.class);

	// Simple sentence splitter pattern: Looks for period, question mark, or
	// exclamation mark,
	// followed by a space and an uppercase letter
	private static final Pattern SENTENCE_PATTERN = Pattern.compile("([.!?])\\s+([A-Z])");

	/**
	 * Parses an XML document and extracts all text contents.
	 * 
	 * @param xmlContent The XML document as a string
	 * @return List of extracted sentences
	 * @throws Exception if XML parsing fails
	 */
	public List<String> extractSentencesFromXml(String xmlContent) throws Exception {
		LOG.debug("Processing XML document");
		String extractedText = extractTextFromXml(xmlContent);
		LOG.debug("Extracted text length: " + extractedText.length());

		return splitIntoSentences(extractedText);
	}

	/**
	 * Extracts all text content from XML nodes.
	 */
	private String extractTextFromXml(String xmlContent)
			throws ParserConfigurationException, IOException, SAXException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

		DocumentBuilder builder = factory.newDocumentBuilder();
		Document document = builder.parse(new InputSource(new StringReader(xmlContent)));

		StringBuilder textContent = new StringBuilder();
		extractTextFromNode(document.getDocumentElement(), textContent);

		return textContent.toString().trim();
	}

	/**
	 * Recursively extracts text from XML nodes.
	 */
	private void extractTextFromNode(Node node, StringBuilder textContent) {
		if (node.getNodeType() == Node.TEXT_NODE) {
			String text = node.getNodeValue().trim();
			if (!text.isEmpty()) {
				textContent.append(text).append(" ");
			}
		}

		NodeList children = node.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			extractTextFromNode(children.item(i), textContent);
		}
	}

	/**
	 * Splits text into sentences.
	 */
	private List<String> splitIntoSentences(String text) {
		List<String> sentences = new ArrayList<>();

		// Replace periods followed by space and capital letter with period and special
		// marker
		Matcher matcher = SENTENCE_PATTERN.matcher(text);
		String markedText = matcher.replaceAll("$1\n$2");

		// Split by the special marker
		String[] parts = markedText.split("\n");

		// Process each part (removing empty/blank sentences)
		for (String part : parts) {
			part = part.trim();
			if (!part.isEmpty()) {
				sentences.add(part);
			}
		}

		LOG.debug("Extracted " + sentences.size() + " sentences");
		return sentences;
	}
}