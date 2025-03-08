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
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
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

	// Default XPath expression to select all elements
	private static final String DEFAULT_XPATH = "self::*";

	/**
	 * Parses an XML document and extracts all text contents.
	 * 
	 * @param xmlContent The XML document as a string
	 * @return List of extracted sentences
	 * @throws Exception if XML parsing fails
	 */
	public List<String> extractSentencesFromXml(String xmlContent) throws Exception {
		return extractSentencesFromXml(xmlContent, DEFAULT_XPATH);
	}

	/**
	 * Parses an XML document and extracts text contents based on the provided XPath
	 * expression.
	 * 
	 * @param xmlContent      The XML document as a string
	 * @param xpathExpression The XPath expression to select elements for text
	 *                        extraction
	 * @return List of extracted sentences
	 * @throws Exception if XML parsing or XPath evaluation fails
	 */
	public List<String> extractSentencesFromXml(String xmlContent, String xpathExpression) throws Exception {
		LOG.debug("Processing XML document with XPath: " + xpathExpression);
		String extractedText = extractTextFromXml(xmlContent, xpathExpression);
		LOG.debug("Extracted text length: " + extractedText.length());

		return splitIntoSentences(extractedText);
	}

	/**
	 * Extracts all text content from XML nodes.
	 */
	private String extractTextFromXml(String xmlContent)
			throws ParserConfigurationException, IOException, SAXException, XPathExpressionException {
		return extractTextFromXml(xmlContent, DEFAULT_XPATH);
	}

	/**
	 * Extracts text content from XML nodes that match the provided XPath
	 * expression.
	 */
	private String extractTextFromXml(String xmlContent, String xpathExpression)
			throws ParserConfigurationException, IOException, SAXException, XPathExpressionException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

		DocumentBuilder builder = factory.newDocumentBuilder();
		Document document = builder.parse(new InputSource(new StringReader(xmlContent)));

		StringBuilder textContent = new StringBuilder();

		// If using default XPath, use the original method for backward compatibility
		if (DEFAULT_XPATH.equals(xpathExpression)) {
			extractTextFromNode(document.getDocumentElement(), textContent);
		} else {
			// Use XPath to select nodes
			XPath xpath = XPathFactory.newInstance().newXPath();
			NodeList matchingNodes = (NodeList) xpath.evaluate(xpathExpression, document, XPathConstants.NODESET);

			LOG.debug("XPath expression matched " + matchingNodes.getLength() + " nodes");

			// Extract text from each matching node
			for (int i = 0; i < matchingNodes.getLength(); i++) {
				Node node = matchingNodes.item(i);
				extractTextFromNode(node, textContent);
			}
		}

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