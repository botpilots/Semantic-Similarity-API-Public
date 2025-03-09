package org.acme.semsim.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for parsing XML and extracting text content from specified elements.
 */
@ApplicationScoped
public class XmlProcessorService {

	private static final Logger LOG = Logger.getLogger(XmlProcessorService.class);

	// Default element to extract text from if none specified
	private static final String DEFAULT_ELEMENT = "p";

	/**
	 * Parses an XML document and extracts text content from all paragraph elements.
	 * 
	 * @param xmlContent The XML document as a string
	 * @return List of extracted text content
	 * @throws Exception if XML parsing fails
	 */
	public List<String> extractElementTextFromXml(String xmlContent) throws Exception {
		return extractElementTextFromXml(xmlContent, DEFAULT_ELEMENT);
	}

	/**
	 * Parses an XML document and extracts text content from specified elements.
	 * 
	 * @param xmlContent   The XML document as a string
	 * @param elementNames Space-separated string of element names to extract text
	 *                     from
	 * @return List of extracted text content
	 * @throws Exception if XML parsing fails
	 */
	public List<String> extractElementTextFromXml(String xmlContent, String elementNames) throws Exception {
		LOG.debug("Processing XML document with element names: " + elementNames);

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

		DocumentBuilder builder = factory.newDocumentBuilder();
		Document document = builder.parse(new InputSource(new StringReader(xmlContent)));

		List<String> extractedTexts = new ArrayList<>();
		XPath xpath = XPathFactory.newInstance().newXPath();

		// Split the element names by space
		String[] elements = elementNames.split("\\s+");

		for (String element : elements) {
			// Create XPath expression to select all elements with the given name
			String xpathExpression = "//" + element;
			NodeList matchingNodes = (NodeList) xpath.evaluate(xpathExpression, document, XPathConstants.NODESET);

			LOG.debug("Found " + matchingNodes.getLength() + " " + element + " elements");

			// Extract text from each matching node using string() function
			for (int i = 0; i < matchingNodes.getLength(); i++) {
				Node node = matchingNodes.item(i);
				// Use normalize-space() and string() functions to get normalized text content
				String text = (String) xpath.evaluate("normalize-space(string())", node, XPathConstants.STRING);

				// Replace any remaining newlines with spaces
				text = text.replaceAll("\\n", " ");

				if (!text.isEmpty()) {
					extractedTexts.add(text);
				}
			}
		}

		LOG.debug("Extracted " + extractedTexts.size() + " text elements");
		return extractedTexts;
	}
}