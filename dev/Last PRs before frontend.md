
PR6-7 DONE 
First we need to simplify the endpoints to only have the one that accepts xpath and make it also provide the results.

PR8: DONE 
1. Then we need to ensure that api is not splitting sentences inside but only groups them by some element where default is self::p.
	1. Change query Parameter xpath to be a space separated string of element names. If not provided it should default to "p".
	2. These elements content is should be only text content. E.g. Any elements contained inside these elements (e.g. <b> in <p>) should NOT be included, but <b>'s content should. Use the string() xpath function to achieve this.

PR9: Enhance Extraction algorithm
Summary:
* Create working copy of original document with semsid, store in SessionData. DONE
	* Traverse the document with current logic (using elements parameter's space sep strings of element to choose.)
	* For each element, add an attribute to the working copy xml cms:semid, starting from one, counting upwards. 
* Extract elements and store as ExtractedText objects. If duplicates are found, save their semsid to already created ExtractedText 
* Calculate similarityScore between ExtractedText objects and store in SimilarityGroup objects.

1. Create a working copy of original document and store in memory.

2. Create cms:semid="textElementCounter" for all sentence elements in the original document, use the xpath to find them.

3. Refactor the Sentence object. It should be called ExtractedElement instead, and holding all data of the extracted element, including its attributes, element name and its content as a string (not that content can be elements but these are just represented as strings).

4. Make the element extraction algorithm create these ExtractedElement objects for each element found. However, they should always be stored in a container class called ElementDuplicateContainer. Moreover, the extraction algorithm should decide whether to store an ExtractedElement object in an already present ElementDuplicateContainer object or create a new one, depending on whether the element's content is unique or not.

The algoritm does this by having a hash map of all the text content in all ExtractedElement objects so far traversed. 
1. If the text content is already present in the hash map
	1. put the ExtractedElement in the ElementDuplicateContainer object representing that content. 
2. If not, 
	2. populate the hash map with the that unique text content
	3. create a new ElementDuplicateContainer object
	4. add the ExtractedElement to it
	5. add the cms:semid to the ElementDuplicateContainer object.

 ElementDuplicateContainer Fields:
* content: content (string) of the first ExtractedElement object
* semids: array of semids for all occurrences.
* vector: similarity vector of the content of the first ExtractedElement object

1. Based on similarity score of the content field of ElementDuplicateContainer group them together in SimiliarityGroup objects. These will have the fields:

* elements: array of ElementDuplicateContainer objects.
* average similarity score
* smallest similarity score
* biggest similarity score
* some kind of score telling about how distributed they are (a high score would mean very similar sentences within that group)
* id

* Store the similarity score used when deciding to group them in a field. 
	* NOTE: How exactly does this work? 
	* ANSWER: It takes the first one that has not yet been grouped and compares that one with the all the consecutive sentences that has yet not been grouped. 

* When constructing the get result json,

1. Include groups sorted by their similarity score, highest first.

2. there should also be some global metadata in the get result json, i.e. the properties:

3. number of groups found, as a number.

4. show the number of sentences minus the number of groups (i.e. the number of sentences that could potentially be removed as unique ones from the original document), as a number.

5. FUTURE: Create a front end to view the results.

6. Return a complete response for the status endpoint (already there?)

7. Visualize the result nicely.

8. Download a CSV with the results.

9. Create a api for getting the modified document with sid:ids.

10. Create a api for getting the sentences and their metadata as a csv file.

ALSO: Ensure that there's no splitting of sentences that are part of the same element.
ALSO: Similarity threshold should be a parameter.
ALSO: log out the highest similarity score for some document (useful if no groups were found)
ALSO: We need to adjust the JSON object, but that is dependent on what we want in it, which is dependent on the implementation of adding more classes for groups and Duplications and Elements. Those implementation needs to be done with care. Consider manual work in IDEA for that.

and calling to services