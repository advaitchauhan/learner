package classifier;
/**********************************************************************************************
 * AUTHOR: Advait Chauhan
 * 
 * DEPENDENCIES: 
 * Score.java, RedBlackBST.java, Queue.java,  StringStemmer.java, Stemmer.java
 * 
 * DESCRIPTION: This program attempts to classify text by Native Bayesian model. 
 * Is built to implement multiple feature selection methods and for input, either user-generated
 * spreadsheet input (column 1 - category, column 2 - text) or Reuters RCV1 XML datasets. 
 **********************************************************************************************/

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.PrintStream;
import java.io.FileOutputStream;

import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;


public class NaiveBayesLearner {	

	//Regular expression patterns for parsing through RCV1
	private final Pattern topicPattern = Pattern.compile("<TOPICS><D>(.+?)</D></TOPICS>");
	private final Pattern topicYAY = Pattern.compile("<REUTERS TOPICS=\"YES\"");
	private final Pattern bodyPattern = Pattern.compile("<BODY>(.+?)</BODY>");
	private final Pattern docPattern = Pattern.compile("<REUTERS(.+?)</REUTERS>");
	
	private int numDocs; 	//total # of documents inputted
	private int numCats; 	//total # of categories found
	private int numVocab;   //total # of initial vocab words found
	private int numVocabReduced;  //total # of vocab words after feature selection
	
	//stores all vocabulary found in inputted documents
	//and the overall wordcount
	/** Vocabs Red-Black Tree with key=term and value=freq **/
	private RedBlackBST<String, Integer> vocab;
	/** selected Vocabs after MI/Chi-Sq Red-Black Tree with key=term and value=freq **/
	private RedBlackBST<String, Integer> selectVocab;
	/** This tree has key=class_label and value= queue of selected top feature terms in the class **/
	private RedBlackBST<String, Queue<String>> catFeatures;
	
	
	//Stores all categories as keys, with each category having a group of documents as value
	private RedBlackBST<String, HashSet<HashMap<String, Integer>>> masterTree;

	//Stores # of documents in which a given token is found within a given category
	private int [][] tokenAppearanceDoc;
	
	//Stores total # of documents in which a token is found
	private int [] tokenAppearance;
	
	//Stores number of documents in each category
	private int [] catCount;
	private double[] prior;    //prior probabilities (catCount[i]/numCats)
	
	/*stores posterior probability computed values
	 * probabilities[t][c] = posterior probability of token t in class c 
                           = likelihood probability of finding token t given we are in class c */
	private double [][] probabilities;
	
	//stores total number of tokens per category
	private int[] categoryTotalTokens;
	
/******************************************************************************************** 
 * Constructor which takes in the document/category data feed and performs and stores
 * computations that can then be used to classify new documents via Naive Bayesian method.  
 * ******************************************************************************************/
	//dataType - 'R' for reuters data, 'T' for excel ticket data
	//feature selection - 'C' for chisquared, 'M' for mutual information
	public NaiveBayesLearner (String fileName, char dataType, char selectionMethod, int features) throws IOException
	{
		masterTree = new RedBlackBST<String, HashSet<HashMap<String, Integer>>>();
		vocab = new  RedBlackBST<String, Integer>();
		selectVocab = new RedBlackBST<String, Integer>();
		catFeatures = new RedBlackBST<String, Queue<String>>();
		numDocs = 0;

		//Parse the topics and body from each document and "learn them"
		if (dataType == 'R')
			parseReuters(fileName);
		else if (dataType == 'T')
			parseTickets(fileName);
		else
			throw new IllegalArgumentException("Invalid DataType Selection");
		
		//fill in our variables after getting data
		numCats = masterTree.size();
		numVocab = vocab.size();
		//for (String word: vocab.keys()) System.out.println(word);
		
		//Declare and fill up our category count and prior probability arrays:
		//by counting number of documents per category
		catCount = new int [numCats];
		prior = new double[numCats];
		countCategories();
		
		//Compute data and fill up arrays for # of documents that each token appears in
		tokenAppearanceDoc = new int [numVocab][numCats];
		tokenAppearance = new int[numVocab];
		computeTokenData();
		
		//select k features per category using mutual information formula and the 
		//token appearance data. Then update the new reduced vocabulary tree
		//input 'C' instead of 'M' of chi-squared feature selection is desired
		FeatureSelect(features, selectionMethod); 
		numVocabReduced = selectVocab.size(); 
		//System.out.println("Reduced Size " + numVocabReduced);
		
		//compute total # of tokens per category (only counting tokens from the selected vocabulary)
		categoryTotalTokens = new int [numCats];
		computeTokensPerCategory();
		
		//compute likelihood probabilities (t given c).
		probabilities = new double [numVocabReduced][numCats];
		computeProbabilities();
	}
	
/******************************************************************************************** 
 * Public Methods including classification of a new document and the return of
 * posterior probability of a given class/token.
 * ******************************************************************************************/
	//classify a set of data in Reuters format - outputs results via printout
	public void classifyFile(String testFile) throws IOException {
		
		System.out.println();
		System.out.println("------Classification Results------");
		
		//Break Reuters input feed into documents
		List<String> docs = separateDocuments(testFile);
		
		//For each document, extract the body and attempt to classify the document
		int numDocs = 1;
		int correctCount = 0;
		for (String doc: docs)
		{
			String cat = " ";
			String body = " ";
			
			//check if topic is yes
			Matcher topicMatcher = topicYAY.matcher(doc);
			if (topicMatcher.find())
			{
				//get what is between topic tags
				Matcher catMatcher = topicPattern.matcher(doc);
				if (catMatcher.find())
					cat = catMatcher.group(1);
				else
					continue;
					//throw new IllegalArgumentException("Topic UnFound");
				
				//get what is between the body tags
				Matcher bodyMatcher = bodyPattern.matcher(doc);
				if (bodyMatcher.find())
					body = bodyMatcher.group(1);
				else
					continue;
					//throw new IllegalArgumentException("Body UnFound");
	
				//classify the text and print out the actual category and the guessed category
				//also do a check to see if we have a correct classification!
				String guess = classify(body);
				Pattern guessP = Pattern.compile(guess);
				Matcher test = guessP.matcher(cat);
				String match = "No";
				if (test.find())
				{
					match = "Yes";
					correctCount++;
				}
				
				System.out.println("Document " + numDocs + ": " + " Guess-" + guess + "  Actual-" + cat + "   Match: " + match);
				numDocs++;
			}
			
		}
		System.out.println();
		System.out.println("-------Classifier Statistics------");
		double accuracy = (double) correctCount/numDocs;
		System.out.println("Number of Correct Guesses:" + correctCount + " out of " + numDocs + " documents.");
		System.out.println("Accuracy: " + accuracy);
	}
	
	//returns the category which a new document d is most likely to belong to
	public String classify (String d) throws IOException {
		//likelihood of category
		double [] score = new double[numCats];
		
		//tokenize, stem, remove stopwords, and remove rogue (non selected vocabulary) tokens from the document
		String d1 = StringStemmer.reduce(StringStemmer.swRemove(d));
		String [] dWords = d1.split("\\s"); 
		Queue<String> dVocabWords = new Queue<String>();
		for (String s: dWords) {
			if (selectVocab.contains(s))
			{
				dVocabWords.enqueue(s);
			}
		}
		
		//compute the probability that document falls in each category
		for (int c = 0; c < numCats; c++)
		{
			//score[c] += Math.log(prior[c]);
			for (String word: dVocabWords) 
			{
				int t = selectVocab.rank(word);
				score[c] += probabilities[t][c];
			}
			//System.out.println(masterTree.select(c) + ": " + score[c]);
		}
		
		//return category with maximum score
		return masterTree.select(maxIndex(score));
	}
	
	//returns the posterior probability given a category and token
	public double getPostProb(String token, String cat) throws IOException
	{
		//We must stem the token input feed so it matches the tokens that are stored inside our data model,
		//and then chop off the last whitespace character appended by the stemming method
		String stoken = StringStemmer.reduce(token);
		String stoken1 = stoken.substring(0, stoken.length()-1);
		
		if (!masterTree.contains(cat)) 
			throw new IllegalArgumentException("This Category is nonexistent!");
		
		int c = masterTree.rank(cat);
		
		//if token already contained within learned vocabulary, spit out
		//pre-computed posterior probability
		if(selectVocab.contains(stoken1)) 
		{
			int t = selectVocab.rank(stoken1);
			return probabilities[t][c];
		}
		
		//posterior probability formula for rogue tokens
		else
		{
			return  (double)(1)/(numVocab + categoryTotalTokens[c]);
		}
	}
	
/*******************************************************************************************
 * Private Methods associated with the initial storage of the category and document data
 * into token-holding data structures 
 * @throws IOException 
 * ******************************************************************************************/
	private void parseTickets(String fileName) throws IOException
	{
		FileInputStream file = new FileInputStream(new File(fileName));
		
		//Get the workbook instance for XLS file 
		XSSFWorkbook tickets = new XSSFWorkbook(file);
			 
		//Get first sheet from the workbook
		XSSFSheet sheet = tickets.getSheetAt(0);
		int rowNum = sheet.getLastRowNum();
		
		for (int i=1; i<rowNum+1; i++)
		{
			 XSSFRow row = sheet.getRow(i);
			 
			 String cat = row.getCell(1).getStringCellValue();
			 String sum = " ";
			 String notes = " ";
			 String res = " ";
			 
			 if (row.getCell(2) != null)
				 sum = row.getCell(2).getStringCellValue();
			 if (row.getCell(3) != null)
				 notes = row.getCell(3).getStringCellValue();
			 if (row.getCell(4) != null)
			 	 res = row.getCell(4).getStringCellValue();
			 
			 String fullDoc = sum + " " + notes + " " + res;
			 System.out.println(cat);
			 System.out.println(fullDoc);
			 learnDoc(cat, fullDoc);
			 numDocs++;
		}
	}
	
	//parse Reuters data, taking in document categories and text bodies and having them learned
	private void parseReuters(String fileName) throws IOException
	{
		//Break input feed into documents using REUTERS divider
		List<String> docs = separateDocuments(fileName);
		
		//Parse the topics and body from each document and "learn them"
		for (String doc: docs)
		{
			String cat = " ";
			String body = " ";
			String [] cats;
			
			//check if topic is yes
			Matcher topicMatcher = topicYAY.matcher(doc);
			if (topicMatcher.find())
			{
				//get what is between topic tags
				Matcher catMatcher = topicPattern.matcher(doc);
				if (catMatcher.find())
				{	
					cat = catMatcher.group(1);
					//split topic string to get all topics
					cats = cat.split("</D><D>");
				}
				else
					continue;
				
				//get what is between the body tags
				Matcher bodyMatcher = bodyPattern.matcher(doc);
				if (bodyMatcher.find())
					body = bodyMatcher.group(1);
				else
					continue;
				
				//learn it
				for (String CAT: cats)
				{
					learnDoc(CAT, body);
				}
				numDocs++;
			}
		}
	}
	
	//breaks Reuters style XML-formated input data into documents
	private List<String> separateDocuments(String fileName) {
		List<String> docs = new ArrayList<String>();
		String line = " ";
		try
		{
			//first convert the feed to string
			BufferedReader br = new BufferedReader(new FileReader(fileName));
			StringBuilder sb = new StringBuilder();
			while ((line = br.readLine()) != null)
			{
				sb.append(line);
			}
			br.close();
			String text = sb.toString(); 
			
			//separate into documents
			Matcher docMatcher = docPattern.matcher(text);
			while(docMatcher.find())
			{
				docs.add(docMatcher.group());
			}
		} 
		catch (IOException ioe)
		{
			ioe.printStackTrace();
		}
		
		return docs;
	}
	
	//Breaks down and stores document/category data into the learning system's data structures
	private void learnDoc(String cat, String text) throws IOException {
		//new hashmap representing the document
		HashMap<String, Integer> hashDoc = new HashMap<String, Integer>();
		
		//remove stop words, then remove suffixes/prefixes
		String reducedText = StringStemmer.reduce(StringStemmer.swRemove(text));
		
		//Put tokens into the hash-map which stores tokens and determines how many 
		//of each token are there. Also update overall vocabulary.
		String [] reducedTokens = reducedText.split("\\s");
		for (String t: reducedTokens)
		{
			wordCount(t, hashDoc);
			vocabCount(t, vocab);
		}
		
		//Put the hashDoc in a set with all other hashDocs of the same category
		if (masterTree.contains(cat))
			masterTree.get(cat).add(hashDoc);
		else
		{
			HashSet<HashMap<String, Integer>> category = new HashSet<HashMap<String, Integer>>();
			category.add(hashDoc);
			masterTree.put(cat, category);
		}	
	}
	
	//helper method for keeping count of vocabulary overall
	private void vocabCount(String token, RedBlackBST<String, Integer> vocab)
	{
		if (vocab.contains(token))
		{
			int count = vocab.get(token);
			vocab.put(token, ++count);
		}
		else
		{
			vocab.put(token, 1);
		}
	}
	
	//helper method for keeping count of tokens within a document
	private void wordCount(String token, HashMap<String, Integer> doc) 
	{
		if (!doc.containsKey(token))
				doc.put(token, 1);
		else 
		{
			int currentCount = doc.get(token);
			doc.put(token, ++currentCount);
		}
	}
	
	//fill in counts of documents per category and document prior probability
	private void countCategories() {
		int i = 0;
		for (String c: masterTree.keys())
		{
			catCount[i] = masterTree.get(c).size();
			i++;
		}
		
		//Rather redudant, but we divide each # of documents in each category by # of total documents
		//to compute the category prior probability array
		for (int i1 = 0; i1 < numCats; i1++) 
		{
			prior[i1] = (double) catCount[i1]/numDocs;
		}
	}
	
	//returns the index corresponding to maximum value in array of doubles
	private int maxIndex (double[] arr)
	{
		int maxInd = 0;
		double max = Double.MIN_VALUE;
		for (int i = 0; i < arr.length; i++)
		{
			if (arr[i] > max) {
				maxInd = i;
				max = arr[i];
			}
		}
		return maxInd;
	}
	
/*******************************************************************************************
 * Private Methods associated with computing and storing the document-token count, ie
 * for every token, figuring out how many documents in each category contain that token. 
 * This is necessary data for our feature selection algorithms. 
 * ******************************************************************************************/
	//computes # of documents within which each token appears, both
	//within each category and overall (over all categories)
	private void computeTokenData() {
		int t = 0;
		for (String token: vocab.keys())
		{
			int c = 0;
			int totalDocs = 0;
			for (String cat: masterTree.keys())
			{
				HashSet<HashMap<String, Integer>> curCat = masterTree.get(cat);
				int docsWithToken = 0;
				for (HashMap<String, Integer> curDoc: curCat)
				{
					if(curDoc.containsKey(token))
						docsWithToken++;
				}
				tokenAppearanceDoc[t][c] = docsWithToken;
				totalDocs += docsWithToken;
				c++;
			}
			tokenAppearance[t] = totalDocs;
			t++;
		}
	}
	
/*******************************************************************************************
 * Private Methods associated with feature selection for reducing the subset 
 * of relevant vocabulary
 * ******************************************************************************************/	
	//select top k features per category via either mutual information or chi^2
	private void FeatureSelect(int k, char ch) {
		Queue<Integer> selectIndex = new Queue<Integer>();
		for (int c = 0; c < numCats; c++) {
			MaxPQ <Score> featureRank = new MaxPQ<Score>();
			for (int t = 0; t < numVocab; t++)
			{
				//System.out.println("Coordinates: " + t + ", " + c);
				//System.out.println("Category: " + masterTree.select(c));
				//System.out.println("Word: " + vocab.select(t));
				double score;
				
				//collect token appearance data (think of the 2 by 2 grid)
				int n11 = tokenAppearanceDoc[t][c];
				int n10 = tokenAppearance[t] - tokenAppearanceDoc[t][c];
				int n01 = catCount[c] - n11;
				int n00 = numDocs - n11 - n10 - n01;
				//System.out.println(n11 + " " + n10 + " " + n01 + " " + n00);
				
				//compute score (MI or chi^2)
				//each of the four values are increased by 1 as a correction to avoid division by 0 errors
				if (ch == 'M')
					score = mutualScore(++n11, ++n10, ++n01, ++n00); 
				else if (ch == 'C')
					score = chiScore(++n11, ++n10, ++n01, ++n00);
				else
					throw new IllegalArgumentException("Invalid Feature Selection Input!");
				
				//save score with token
				Score cur = new Score(t, score);
				featureRank.insert(cur);
			}
			String category = masterTree.select(c);
			Queue<String> q = new Queue<String>();
			catFeatures.put(category, q);
			for (int i = 0; i < k; i++)
			{
				Score del = featureRank.delMax();
				int ind = del.getIndex();
				selectIndex.enqueue(ind);
				catFeatures.get(category).enqueue(vocab.select(ind));
			}
		}
		
		//we may choose a better solution for this later
		//puts selected vocabulary into a new red-black tree
		for (int i: selectIndex)
		{
			String word = vocab.select(i);
			int count = vocab.get(word);
			selectVocab.put(word, count);
		}
	}
	
	//computes relevance of a token by mutual information formula
	private double mutualScore(int n11, int n10, int n01, int n00) {
		
		//combined counts of a token occurring, not occurring, or
		//a category occurring or not occurring. 
		double n1s = n11 + n10;
		double ns1 = n11 + n01;
		double n0s = n01 + n00;
		double ns0 = n10 + n00;
		
		//score following MI formula
		double score = ((double)n11/numDocs)*(Math.log(n11*numDocs/(n1s*ns1))/Math.log(2));
		score += ((double)n01/numDocs)*(Math.log(n01*numDocs/(n0s*ns1))/Math.log(2));
		score += ((double)n10/numDocs)*(Math.log(n10*numDocs/(n1s*ns0))/Math.log(2));
		score += ((double)n00/numDocs)*(Math.log(n00*numDocs/(n0s*ns0))/Math.log(2));
		//System.out.println(score);
		
		/*double score = (Math.log(n11*numDocs/(n1s*ns1))/Math.log(2));
		*/
		return score;
	}
	
	//computes relevance of a token by Chi-Square feature scoring method
	private double chiScore (int n11, int n10, int n01, int n00) {
		//combined probabilities - chances of a token occuring, not occuring, or
		//a category occuring or not occuring. 
		int n1s = n11 + n10;
		int ns1 = n11 + n01;
		int n0s = n01 + n00;
		int ns0 = n10 + n00;
		
		//calculate our expected values
		double e11 = (n1s*ns1)/numDocs;
		double e01 = (n0s*ns1)/numDocs;
		double e10 = (n1s*ns0)/numDocs;
		double e00 = (n0s*n00)/numDocs;
		
		//compute chi-squared score, summing over all 4 permutations
		//CURRENTLY AM USING AN IMPROMPTU +1 CORRECTION TO AVOID NEAR-INFINITY SCORES
		double score = chi(++e11, n11) + chi(++e10, n10) + chi(++e01, n01) + chi(++e00, n00);
		return score;
	}
	
	//basic formula of chi^2 between expected and observed
	private double chi(double e, int o)
	{
		return (e-o)*(e-o)/e;
	}
	
/*******************************************************************************************
 * Private Methods associated with computing posterior probabilities which are needed 
 * to in order to classify new documents (Bayes Theorem).
 * ******************************************************************************************/

	//compute total # of tokens per category
	private void computeTokensPerCategory()
	{
		int i = 0;
		for (String cat: masterTree.keys())
		{
			int count = 0;
			for (HashMap<String, Integer> doc : masterTree.get(cat))
			{
				for (String token: doc.keySet())
				{
					if (selectVocab.contains(token))
					{
						count += doc.get(token);
					}
				}
			} 
			//System.out.println(count);
			categoryTotalTokens[i] = count;
			i++;
		}
	}
	
	//likelihood function/posterior probability computation
	private void computeProbabilities() {
		int c = 0;
		for (String cat: masterTree.keys())
		{
			int t = 0;
			for (String word: selectVocab.keys())
			{
				int wordOccur = 0; //how many times does token t appear in category c?
				for (HashMap<String, Integer> doc : masterTree.get(cat))
				{
					int wordOccurInDoc;
					if (doc.containsKey(word))
						wordOccurInDoc = doc.get(word);
					else
						wordOccurInDoc = 0;
					wordOccur += wordOccurInDoc;
				}
				probabilities[t][c] = (double)(wordOccur + 1)/(categoryTotalTokens[c] + numVocabReduced);
				//System.out.println(probabilities[t][c]);
				t++;
			}
			c++;
		}
	}
	
/*******************************************************************************************
 * Unit testing
 * ******************************************************************************************/
	public void printData() 
	{
		System.out.println();
		System.out.println("------Input Data Summary------");
		System.out.println("Number of Documents: " + numDocs);
		System.out.println("Number of Categories: " + numCats);
		System.out.println("Original Vocabulary Size: " + numVocab);
		System.out.println("Reduced Vocabulary Size: " + numVocabReduced);
		System.out.println();
	}
	
	public void printVocab()
	{
		System.out.println();
		System.out.println("------Selected Vocabulary------");
		
		int count = 1;
		for (String word: selectVocab.keys())
		{
			System.out.print(word + " ");
			if (count == 15)
			{
				System.out.println();
				count = 1;
			}
			count++;
		}
		System.out.println();
	}
	
	public void printPriors()
	{
		System.out.println();
		System.out.println("------Prior Probability of Each Category-----");
		for (int i = 0; i < numCats; i++)
		{
			System.out.println(masterTree.select(i) + ": " + prior[i]);
		}
		System.out.println();
	}
	
	public void printCategoryFeatures()
	{
		for (String cat: catFeatures.keys())
		{
			System.out.println("Category: " + cat + " - ");
			for (String t: catFeatures.get(cat))
			{
				System.out.print(t+ ", ");
			}
			System.out.println();
		}
	}
	
	public static void main (String [] args) throws IOException 
	{
		/*
		File file = new File ("out.txt");
		FileOutputStream fos = new FileOutputStream(file);
		PrintStream ps = new PrintStream(fos);
		System.setOut(ps);
		
		//testing token consolidation, vocabulary extraction, and all counts
		String trainingFile = ("reut2-bigtrain.txt");
		System.out.println("------Top Features in Each Category------");
		NaiveBayesLearner trainer = new NaiveBayesLearner(trainingFile, 'R', 'M', 50);
	
		trainer.printData();
		trainer.printVocab();
		trainer.printPriors();
		trainer.printCategoryFeatures();
		
		//attempt classification, using an 80-20 training/testing split
		String testingFile = "reut2-bigtest.txt";
		trainer.classifyFile(testingFile);
		*/
		
		File file = new File ("out.txt");
		FileOutputStream fos = new FileOutputStream(file);
		PrintStream ps = new PrintStream(fos);
		System.setOut(ps);
		
		String trainingFile = ("TicketDataFirst100.xlsx");
		
		System.out.println("---M, 50---");
		NaiveBayesLearner trainer8 = new NaiveBayesLearner(trainingFile,'T', 'M', 50);
		trainer8.printData();
		trainer8.printCategoryFeatures();
	}
}
