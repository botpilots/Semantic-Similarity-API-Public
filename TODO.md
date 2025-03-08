1. Remove duplictes in same group. 
2. Add more metadata per group and to the full result. 
   1. Reference (id) to where each sentence was found in the original document.
   2. Similarity score per group. Results should be sorted by this score.
   3. Do not include groups with only one word in one sentence. 
   4. Number of groups for whole result.
3. Return a complete response for the status endpoint (already there?)
4. Create a front end to view the results.
   1. Visualize the sentences nicely.
   2. Download a CSV with the results.