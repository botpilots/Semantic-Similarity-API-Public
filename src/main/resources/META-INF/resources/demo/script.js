document.addEventListener('DOMContentLoaded', () => {
    // --- New Element Selectors ---
    const elementNamesInput = document.getElementById('element-names');
    const similarityThresholdInput = document.getElementById('similarity-threshold');
    const thresholdValueDisplay = document.getElementById('threshold-value');
    const xmlInputViewer = document.getElementById('xml-input-viewer');
    const loadSmallBtn = document.getElementById('load-small');
    const loadMediumBtn = document.getElementById('load-medium');
    const loadLargeBtn = document.getElementById('load-large');
    const postRequestBtn = document.getElementById('post-request');
    const getResultsBtn = document.getElementById('get-results');
    const groupVisualizationContainer = document.getElementById('group-visualization');
    const postInfoBox = document.getElementById('post-info-box');
    const getInfoBox = document.getElementById('get-info-box');
    // --- Search Panel Selectors (NEW) ---
    const searchQueryInput = document.getElementById('search-query');
    const searchLimitInput = document.getElementById('search-limit');
    const searchResultsContainer = document.getElementById('search-results-visualization');
    const searchInfoBox = document.getElementById('search-info-box');
    const searchRequestBtn = document.getElementById('search-request');
    // -------------------------------------

    // --- Update threshold value display when slider changes ---
    similarityThresholdInput.addEventListener('input', () => {
        thresholdValueDisplay.textContent = similarityThresholdInput.value;
    });

    // --- Sample Data URLs ---
    const sampleSmallUrl = '/samples/small.xml';
    const sampleMediumUrl = '/samples/medium.xml';
    const sampleLargeUrl = '/samples/large.xml';
    // ----------------------

    // --- State Variable ---
    let currentLoadedXml = null; // To store the currently loaded XML content
    // ----------------------

    // --- Helper Function for Info Boxes ---
    function updateInfoBox(boxElement, message, type = 'info') {
        if (!boxElement) return;

        // Clear previous state classes
        boxElement.classList.remove('error', 'success'); // Keep default styles

        const displayMessage = message === null || message === undefined ? '' : String(message).trim();

        boxElement.textContent = displayMessage || 'Status...'; // Default placeholder

        // Apply specific class based on type
        if (type === 'error') {
            boxElement.classList.add('error');
        } else if (type === 'success') {
            boxElement.classList.add('success');
        }
        // No specific class needed for 'info' or default state, rely on base .info-box styles
    }

    // --- API Interaction Functions ---

    async function fetchSampleXml(url) {
        try {
            const response = await fetch(url);
            if (!response.ok) {
                throw new Error(`HTTP error! Status: ${response.status} fetching ${url}`);
            }
            return await response.text();
        } catch (error) {
            // Log error to browser console
            console.error(`Fetch Sample Error (${url}):`, error);
            alert(`Failed to fetch sample XML from ${url}: ${error.message}`);
            return null; // Indicate failure
        }
    }

    // --- New Function to Load and Display Sample XML ---
    async function handleLoadSample(sampleUrl) {
        updateInfoBox(postInfoBox, '');
        updateInfoBox(getInfoBox, '');
        console.log(`Attempting to load sample from: ${sampleUrl}`);
        const sampleXml = await fetchSampleXml(sampleUrl);
        if (sampleXml !== null) {
            currentLoadedXml = sampleXml; // Store the loaded XML
            displayXmlInViewer(sampleXml); // Display it
            console.log(`Successfully loaded and displayed XML from ${sampleUrl}`);
        } else {
            currentLoadedXml = null; // Clear stored XML on failure
            xmlInputViewer.textContent = `Failed to load XML from ${sampleUrl}. See browser console for details.`;
            console.error(`Failed to load XML from ${sampleUrl}`);
        }
    }

    // --- Function to Display XML in the Viewer ---
    function displayXmlInViewer(xmlContent) {
        // Escape HTML characters to prevent rendering XML tags as HTML
        const escapedXml = xmlContent
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
        xmlInputViewer.innerHTML = `<pre><code>${escapedXml}</code></pre>`;
    }

    // --- Modified POST Request Handler ---
    async function handlePostRequest() {
        updateInfoBox(postInfoBox, 'Preparing POST request...'); // Initial state

        if (!currentLoadedXml) {
            console.warn("POST request attempted without loaded XML. Please load a sample.");
            updateInfoBox(postInfoBox, "Please load an XML sample first.", 'error');
            return;
        }

        const elements = elementNamesInput.value.trim();
        if (!elements) {
            console.warn("POST request attempted without element names.");
            updateInfoBox(postInfoBox, "Please enter element names.", 'error');
            return;
        }

        // Get the threshold value and validate it
        const thresholdValue = similarityThresholdInput.value.trim();
        const threshold = parseFloat(thresholdValue);
        
        // Validate threshold value is a number between 0 and 1
        if (isNaN(threshold) || threshold < 0 || threshold > 1) {
            console.warn("POST request attempted with invalid threshold value.");
            updateInfoBox(postInfoBox, "Please enter a valid threshold value between 0.0 and 1.0.", 'error');
            return;
        }

        const encodedElements = encodeURIComponent(elements);
        // Add the threshold parameter to the URL
        const apiUrl = `/api/similarity?elements=${encodedElements}&threshold=${threshold}`; 

        console.log(`POST: Sending loaded XML to ${apiUrl} with elements: ${elements} and threshold: ${threshold}`);
        updateInfoBox(postInfoBox, "Sending POST request..."); // Update status

        try {
            const response = await fetch(apiUrl, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/xml'
                },
                body: currentLoadedXml // Send the stored XML
            });

            const responseText = await response.text(); // Get raw response text
            let message = '';
            let type = 'info'; // Default type

            console.log("POST Response:", {
                status: response.status,
                statusText: response.statusText,
                body: responseText
            });

            if (response.ok) {
                type = 'success';
                try {
                    const apiResponse = JSON.parse(responseText);
                    // Use message property if available, otherwise generic success
                    message = apiResponse.message || `POST OK (${response.status}): Processing initiated.`;
                } catch (parseError) {
                    // If not JSON, use the raw text if available, or generic success
                    message = responseText.trim() || `POST OK (${response.status}): Success (non-JSON response).`;
                }
            } else {
                type = 'error';
                try {
                    const apiResponse = JSON.parse(responseText);
                    // Use error property if available, otherwise use message or generic error
                    message = apiResponse.error || apiResponse.message || `POST Error (${response.status}): ${response.statusText}`;
                } catch (parseError) {
                    // If not JSON, use the raw text if available, or generic error
                    message = responseText.trim() || `POST Error (${response.status}): ${response.statusText}`;
                }
            }
            updateInfoBox(postInfoBox, message, type);

        } catch (error) {
            console.error("POST Error:", error);
            updateInfoBox(postInfoBox, `Network Error: ${error.message}`, 'error');
        }
    }

    // --- Modified GET Request Handler ---
    async function handleGetRequest() {
        updateInfoBox(getInfoBox, 'Preparing GET request...'); // Initial state
        const apiUrl = `/api/similarity/results`; // Use relative path
        console.log(`GET: Sending request to ${apiUrl}`);
        updateInfoBox(getInfoBox, "Sending GET request..."); // Update status

        try {
            const response = await fetch(apiUrl, {
                method: 'GET',
            });

            const responseText = await response.text(); // Get raw response text
            let message = '';
            let type = 'info'; // Default type

             console.log("GET Response:", {
                status: response.status,
                statusText: response.statusText,
                body: responseText
             });

            if (response.ok) {
                 type = 'success'; // Assume success initially
                 clearGroupVisualization(); // Clear previous viz
                try {
                    const apiResponse = JSON.parse(responseText);
                    console.log("Parsed GET API Response:", apiResponse);

                    // Check for similarityGroups first
                    if (apiResponse && Array.isArray(apiResponse.similarityGroups)) {
                        if (apiResponse.similarityGroups.length > 0) {
                            displayGroupVisualization(apiResponse.similarityGroups);
                             // Use message property if available, otherwise generate message
                             message = apiResponse.message || `GET OK (${response.status}): ${apiResponse.similarityGroups.length} groups visualized.`;
                         } else {
                            // No groups found, use message property or default
                            message = apiResponse.message || `GET OK (${response.status}): No similarity groups returned.`;
                         }
                    } else {
                         // No similarityGroups key, use message property or generic success
                         // Check if there's an error property even on success (e.g., session expired but status 200)
                         if (apiResponse.error) {
                             type = 'error'; // Override type if error property exists
                             message = apiResponse.error;
                         } else {
                              message = apiResponse.message || `GET OK (${response.status}): Response received, but no 'similarityGroups' array found.`;
                         }
                     }
                } catch (parseError) {
                     console.error("Failed to parse GET response JSON:", parseError);
                    // If not JSON, use the raw text if available, or generic success
                     message = responseText.trim() || `GET OK (${response.status}): Success (non-JSON response).`;
                     // Still treat as success if response.ok is true
                }
            } else {
                type = 'error';
                clearGroupVisualization();
                try {
                    const apiResponse = JSON.parse(responseText);
                    // Use error property if available, otherwise use message or generic error
                    message = apiResponse.error || apiResponse.message || `GET Error (${response.status}): ${response.statusText}`;
                } catch (parseError) {
                    // If not JSON, use the raw text if available, or generic error
                    message = responseText.trim() || `GET Error (${response.status}): ${response.statusText}`;
                }
            }
            updateInfoBox(getInfoBox, message, type);

        } catch (error) {
            console.error("GET Error:", error);
             clearGroupVisualization();
            updateInfoBox(getInfoBox, `Network Error: ${error.message}`, 'error');
        }
    }

    // --- NEW Search Request Handler ---
    async function handleSearchRequest() {
        updateInfoBox(searchInfoBox, 'Preparing search request...');
        clearSearchResults(); // Clear previous results

        const query = searchQueryInput.value.trim();
        const limit = parseInt(searchLimitInput.value, 10);

        if (!query) {
            console.warn("Search request attempted without a query.");
            updateInfoBox(searchInfoBox, "Please enter a search query.", 'error');
            return;
        }
        if (isNaN(limit) || limit < 1) {
            console.warn("Search request attempted with invalid limit.");
            updateInfoBox(searchInfoBox, "Please enter a valid positive number for the limit.", 'error');
            return;
        }

        // Encode the query parameter
        const encodedQuery = encodeURIComponent(query);
        const apiUrl = `/api/similarity/search?search=${encodedQuery}&limit=${limit}`; // Use relative path

        console.log(`SEARCH: Sending request to ${apiUrl}`);
        updateInfoBox(searchInfoBox, "Sending search request...");

        try {
            const response = await fetch(apiUrl, {
                method: 'GET',
            });

            const responseText = await response.text(); // Get raw response text
            let message = '';
            let type = 'info'; // Default type

            console.log("Search Response:", {
                status: response.status,
                statusText: response.statusText,
                body: responseText
            });

            if (response.ok) {
                type = 'success'; // Assume success initially
                try {
                    const apiResponse = JSON.parse(responseText);
                    console.log("Parsed Search API Response:", apiResponse);

                    // Check for searchResults (adjust if backend DTO uses 'data')
                    if (apiResponse && Array.isArray(apiResponse.searchResults)) {
                        if (apiResponse.searchResults.length > 0) {
                            displaySearchResults(apiResponse.searchResults);
                            message = apiResponse.message || `Search OK (${response.status}): ${apiResponse.searchResults.length} results found.`;
                        } else {
                            message = apiResponse.message || `Search OK (${response.status}): No matching sentences found.`;
                            clearSearchResults(false); // Clear but keep placeholder
                        }
                    } else if (apiResponse && Array.isArray(apiResponse.data)) { // Check 'data' field as fallback
                         if (apiResponse.data.length > 0) {
                            displaySearchResults(apiResponse.data); // Assume data contains SearchResult objects
                            message = apiResponse.message || `Search OK (${response.status}): ${apiResponse.data.length} results found.`;
                        } else {
                            message = apiResponse.message || `Search OK (${response.status}): No matching sentences found.`;
                            clearSearchResults(false);
                        }
                    } else {
                         // No suitable results array found
                         if (apiResponse.error) {
                             type = 'error';
                             message = apiResponse.error;
                         } else {
                             message = apiResponse.message || `Search OK (${response.status}): Response received, but no 'searchResults' or 'data' array found.`;
                         }
                         clearSearchResults(false);
                     }
                } catch (parseError) {
                    console.error("Failed to parse Search response JSON:", parseError);
                    message = responseText.trim() || `Search OK (${response.status}): Success (non-JSON response).`;
                    type = 'success'; // Still success if response.ok
                    clearSearchResults(false);
                }
            } else {
                type = 'error';
                clearSearchResults(); // Clear on error
                try {
                    const apiResponse = JSON.parse(responseText);
                    message = apiResponse.error || apiResponse.message || `Search Error (${response.status}): ${response.statusText}`;
                } catch (parseError) {
                    message = responseText.trim() || `Search Error (${response.status}): ${response.statusText}`;
                }
            }
            updateInfoBox(searchInfoBox, message, type);

        } catch (error) {
            console.error("Search Network Error:", error);
            clearSearchResults();
            updateInfoBox(searchInfoBox, `Network Error: ${error.message}`, 'error');
        }
    }

    // --- Utility Functions (REMOVED logToConsole) ---
    // logToConsole function removed.
    // Info boxes and console logging are used instead.

    // --- Function to display group visualization (Mostly Unchanged) ---
    function displayGroupVisualization(similarityGroups) {
        groupVisualizationContainer.innerHTML = ''; // Clear previous visualization

        if (!similarityGroups || similarityGroups.length === 0) {
            groupVisualizationContainer.textContent = "No similarity groups found in response.";
             console.log("No similarity groups to visualize.");
            return;
        }

        // Create a single shared popover that will be used for all columns
        const popover = document.createElement('div');
        popover.classList.add('popover');
        popover.style.display = 'none'; // Hide initially
        groupVisualizationContainer.appendChild(popover);
        
        // Add a single event listener to the container to hide popover when mouse leaves
        groupVisualizationContainer.addEventListener('mouseleave', () => {
            popover.style.display = 'none';
        });

        // Determine max sentences for scaling height
        let maxSentences = 0;
        similarityGroups.forEach(group => {
            if (Array.isArray(group) && group.length > maxSentences) {
                maxSentences = group.length;
            }
        });

        // Get container height for pixel calculations
        const containerHeight = groupVisualizationContainer.clientHeight; // Get rendered height
        const maxHeightPixels = Math.max(50, containerHeight * 0.70); // Ensure minimum height, use 70% of container
        const minHeightPixels = 5; // Minimum visual height

        console.log(`Visualizing Groups - Container Height: ${containerHeight}px, Max Sentences: ${maxSentences}, Max Bar Height: ${maxHeightPixels}px`);

        similarityGroups.forEach((group, index) => {
            console.log(`Processing Group ${index + 1}:`, JSON.parse(JSON.stringify(group)));

            const column = document.createElement('div');
            column.classList.add('group-column');
            
            // Store the group data as a data attribute
            column.dataset.groupIndex = index;
            column.dataset.groupSize = Array.isArray(group) ? group.length : 0;

            const sentenceCount = Array.isArray(group) ? group.length : 0;

            // Calculate height in pixels, ensuring valid numbers
            let columnHeightPixels = minHeightPixels;
            if (maxSentences > 0 && sentenceCount > 0) {
                columnHeightPixels = (sentenceCount / maxSentences) * maxHeightPixels;
            }
            columnHeightPixels = Math.max(minHeightPixels, Math.min(columnHeightPixels, maxHeightPixels));

            column.style.height = `${columnHeightPixels}px`;

            console.log(`Group ${index + 1}: Count=${sentenceCount}, Calculated Height=${columnHeightPixels}px`);

            // Simplified hover handler - just update popover content and show it
            column.addEventListener('mouseenter', () => {
                if (!Array.isArray(group) || group.length === 0) return;
                
                // Create the content for the popover
                const title = document.createElement('strong');
                title.textContent = `Group ${index + 1} | ${group.length} item${group.length !== 1 ? 's' : ''}`;
                
                const list = document.createElement('ul');
                
                // Add up to 5 items from the group to avoid overly large popovers
                const maxItemsToShow = Math.min(5, group.length);
                for (let i = 0; i < maxItemsToShow; i++) {
                    const item = document.createElement('li');
                    // Don't truncate text here - let CSS handle it
                    const text = typeof group[i] === 'string' 
                        ? group[i] 
                        : JSON.stringify(group[i]);
                    
                    item.textContent = text;
                    list.appendChild(item);
                }
                
                // If there are more items than shown, add an indicator
                if (group.length > maxItemsToShow) {
                    const moreItem = document.createElement('li');
                    moreItem.textContent = `... and ${group.length - maxItemsToShow} more`;
                    moreItem.classList.add('more-indicator'); // Add a specific class
                    list.appendChild(moreItem);
                }
                
                // Clear previous content and add new content
                popover.innerHTML = '';
                popover.appendChild(title);
                popover.appendChild(list);
                popover.style.display = 'block';
            });

            groupVisualizationContainer.appendChild(column);
        });
    }

    // --- Function to Clear Visualization (Unchanged) ---
    function clearGroupVisualization() {
        groupVisualizationContainer.innerHTML = '';
        groupVisualizationContainer.textContent = "-"; // Placeholder
        console.log("Group visualization cleared.");
    }

    // --- Function to display search results (NEW) ---
    function displaySearchResults(results) {
        searchResultsContainer.innerHTML = ''; // Clear previous results

        if (!results || results.length === 0) {
            searchResultsContainer.textContent = "No results to display.";
            console.log("No search results to visualize.");
            return;
        }

        console.log(`Displaying ${results.length} search results.`);

        results.forEach((result, index) => {
            if (!result || !result.sentence) {
                console.warn(`Skipping invalid search result at index ${index}:`, result);
                return;
            }

            const itemDiv = document.createElement('div');
            itemDiv.classList.add('search-result-item');

            const scoreSpan = document.createElement('span');
            scoreSpan.classList.add('search-result-score');
            // Format score to 3 decimal places
            scoreSpan.textContent = `Score: ${result.similarityScore !== undefined ? result.similarityScore.toFixed(3) : 'N/A'}`;

            const textSpan = document.createElement('span');
            textSpan.classList.add('search-result-text');
            textSpan.textContent = result.sentence.text || '[No text]'; // Access the text within the sentence object

            itemDiv.appendChild(scoreSpan); // Add score first so text flows around it
            itemDiv.appendChild(textSpan);

            searchResultsContainer.appendChild(itemDiv);
        });
    }

    // --- Function to Clear Search Results (NEW) ---
    function clearSearchResults(showPlaceholder = true) {
        searchResultsContainer.innerHTML = ''; // Clear content
        if (showPlaceholder) {
             searchResultsContainer.textContent = "-"; // Placeholder
        }
        console.log("Search results cleared.");
    }

    // --- UPDATED Event Listeners ---
    loadSmallBtn.addEventListener('click', () => handleLoadSample(sampleSmallUrl));
    loadMediumBtn.addEventListener('click', () => handleLoadSample(sampleMediumUrl));
    loadLargeBtn.addEventListener('click', () => handleLoadSample(sampleLargeUrl));
    postRequestBtn.addEventListener('click', handlePostRequest);
    getResultsBtn.addEventListener('click', handleGetRequest);
    searchRequestBtn.addEventListener('click', handleSearchRequest); // Add listener for search

    // --- Initial State --- 
    clearGroupVisualization(); // Clear visualization on load
    clearSearchResults(); // Clear search results on load
    xmlInputViewer.textContent = "Click 'Small', 'Medium', or 'Large' to load sample XML."; // Initial placeholder

    // Set initial info box states
    updateInfoBox(postInfoBox); // Show default placeholder
    updateInfoBox(getInfoBox); // Show default placeholder
    updateInfoBox(searchInfoBox); // Show default placeholder for search

}); 