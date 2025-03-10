package org.acme.semsim.service;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for parsing XML and extracting text content from specified elements.
 */
@ApplicationScoped
public class XmlProcessorService {

	private static final Logger LOG = Logger.getLogger(XmlProcessorService.class);

	/**
	 * Parses an XML document and extracts text content from specified elements.
	 *
	 * @param elementNames Space-separated string of element names to extract text from
	 * @return List of extracted text content
	 * @throws Exception if XML parsing fails
	 */
	public List<String> extractElementTextFromXml(Document document, String elementNames) throws Exception {
		LOG.debug("Processing XML document with element names: " + elementNames);

		List<String> extractedTexts = new ArrayList<>();

		// Create a new XPath instance
		XPath xpath = XPathFactory.newInstance().newXPath();

		// Extract elements from the document
		NodeList elements = getElementsOfDocument(elementNames, document, xpath);

		// Extract text from each matching node using string() function
		for (int i = 0; i < elements.getLength(); i++) {
			Node node = elements.item(i);
			// Use normalize-space() and string() functions to get normalized text content
			String text = (String) xpath.evaluate("normalize-space(string())", node, XPathConstants.STRING);

			// Replace any remaining newlines with spaces
			text = text.replaceAll("\\n", " ");

			if (!text.isEmpty()) {
				extractedTexts.add(text);
			}
		}
		LOG.debug("Extracted " + extractedTexts.size() + " text elements");
		return extractedTexts;
	}

/**
 * Creates a working copy of the XML document with added attributes `cms:semid`.
 *
 * @param xmlContent The XML content as a string
 * @param elementNames A space-separated string of element names to add attributes to
 * @return The modified Document object
 * @throws ParserConfigurationException if a DocumentBuilder cannot be created
 * @throws SAXException if any parse errors occur
 * @throws IOException if any IO errors occur
 * @throws XPathExpressionException if the XPath expression evaluation fails
 */
public static Document createWorkingCopy(String xmlContent, String elementNames) throws ParserConfigurationException, SAXException, IOException, XPathExpressionException {
    Document document = buildDocument(xmlContent);
    NodeList elements = getElementsOfDocument(elementNames, document);
    addSemids(document, elements);
    return document;
}

	/**
	 * Extracts a list of elements from an XML document.
	 *
	 * @param elementNames A space-separated string of element names to extract
	 * @param document The XML document
	 * @param xpath The XPath instance to use
	 * @return A NodeList of elements
	 * @throws XPathExpressionException if the XPath expression evaluation fails
	 */
	public static NodeList getElementsOfDocument(String elementNames, Document document, XPath xpath) throws XPathExpressionException {

		// Split the element names by space
		String[] elements = elementNames.split("\\s+");

		// Build the XPath expression
		StringBuilder xpathExpr = new StringBuilder("//*[");
		for (int i = 0; i < elements.length; i++) {
			String element = elements[i];
			if (i > 0) {
				xpathExpr.append(" or ");
			}
			xpathExpr.append("name()='").append(element).append("'");
		}
		xpathExpr.append("]");

		// Evaluate the XPath expression and return
		return (NodeList) xpath.evaluate(xpathExpr.toString(), document, XPathConstants.NODESET);
	}

	/**
	 * Extracts a list of elements from an XML document.
	 * Creates a new xpath instance.
	 *
	 * @param elementNames A space-separated string of element names to extract
	 * @param document The XML document
	 * @return A NodeList of elements
	 * @throws XPathExpressionException if the XPath expression evaluation fails
	 */
	public static NodeList getElementsOfDocument(String elementNames, Document document) throws XPathExpressionException {
		XPath xpath = XPathFactory.newInstance().newXPath();
		return getElementsOfDocument(elementNames, document, xpath);
	}

	/**
	 * Builds a Document object from an XML content string.
	 *
	 * @param xmlContent The XML content as a string
	 * @return The parsed Document object
	 * @throws ParserConfigurationException if a DocumentBuilder cannot be created
	 * @throws SAXException if any parse errors occur
	 * @throws IOException if any IO errors occur
	 */
	public static Document buildDocument(String xmlContent) throws ParserConfigurationException, SAXException, IOException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		return builder.parse(new java.io.ByteArrayInputStream(xmlContent.getBytes()));
	}

	/**
	 * Create a working copy of the XML document with added attributes cms:semid, where the first element found has semid 1, next one 2, etc.
	 *
	 * @param document The original XML document
	 * @param elements A space-separated string of element names to add attributes to
	 * @return The modified Document object
	 * @throws ParserConfigurationException if a DocumentBuilder cannot be created
	 * @throws IOException if any IO errors occur
	 * @throws SAXException if any parse errors occur
	 * @throws XPathExpressionException if the XPath expression evaluation fails
	 */
	public static Document addSemids(Document document, NodeList elements) throws ParserConfigurationException, IOException, SAXException, XPathExpressionException {

		// Use NodeList elements to add attribute for all matching elements in the document.
		for (int i = 0; i < elements.getLength(); i++) {
			Node node = elements.item(i);
			// Add attribute cms:semid to the node
			Attr semidAttr = document.createAttribute("cms:semid");
			// Set the value of the semid attribute to the current index + 1
			semidAttr.setValue(String.valueOf(i + 1));
			node.getAttributes().setNamedItem(semidAttr);
		}

		return document;
	}

}