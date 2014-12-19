package classifier;
/**********************************************************************************************
 * AUTHOR: Advait Chauhan
 * 
 * DEPENDENCIES: Stemmer.java
 * 
 * DESCRIPTION: This string-processing program removes stop-words and other 
 * undesired characters from documents,and then removes prefixes/suffixes. 
 **********************************************************************************************/

import java.io.IOException;
import java.util.regex.*;

public class StringStemmer {
	public static String swRemove(String org) throws IOException 
	{
		org = org.toLowerCase();
		
		//remove stopwords
		Pattern stopWords = Pattern.compile("\\b(?:a|corporation|corporations|corp|export|able|about|above|according|accordingly|across|actually|after|afterwards|again|against|all|allow|allows|almost|alone|along|already|also|although|always|am|among|amongst|an|and|another|any|anybody|anyhow|anyone|anything|anyway|anyways|anywhere|apart|appear|appreciate|appropriate|are|around|as|aside|ask|asking|associated|at|available|away|awfully|b|be|became|because|become|becomes|becoming|been|before|beforehand|behind|being|believe|below|beside|besides|best|better|between|beyond|both|brief|but|by|c|came|can|cannot|cant|cause|causes|certain|certainly|changes|clearly|co|com|come|comes|concerning|consequently|consider|considering|contain|containing|contains|corresponding|could|course|currently|d|definitely|described|despite|did|different|do|does|doing|done|down|downwards|during|e|each|edu|eg|eight|either|else|elsewhere|enough|entirely|especially|et|etc|even|ever|every|everybody|everyone|everything|everywhere|ex|exactly|example|except|f|far|few|fifth|first|five|followed|following|follows|for|former|formerly|forth|four|from|further|furthermore|g|get|gets|getting|given|gives|go|goes|going|gone|got|gotten|greetings|h|had|happens|hardly|has|have|having|he|hello|help|hence|her|here|hereafter|hereby|herein|hereupon|hers|herself|hi|him|himself|his|hither|hopefully|how|howbeit|however|i|ie|if|ignored|immediate|in|inasmuch|inc|indeed|indicate|indicated|indicates|inner|insofar|instead|into|inward|is|it|its|itself|j|just|k|keep|keeps|kept|know|knows|known|l|last|lately|later|latter|latterly|least|less|lest|let|like|liked|likely|little|ll|look|looking|looks|ltd|m|mainly|many|may|maybe|me|mean|meanwhile|merely|might|more|moreover|most|mostly|much|must|my|myself|n|name|namely|nd|near|nearly|necessary|need|needs|neither|never|nevertheless|new|next|nine|no|nobody|non|none|noone|nor|normally|not|nothing|novel|now|nowhere|o|obviously|of|off|often|oh|ok|okay|old|on|once|one|ones|only|onto|or|other|others|otherwise|ought|our|ours|ourselves|out|outside|over|overall|own|p|particular|particularly|per|perhaps|placed|please|plus|possible|presumably|probably|provides|q|que|quite|qv|r|rather|rd|re|really|reasonably|regarding|regardless|regards|relatively|respectively|right|s|said|same|saw|say|saying|says|second|secondly|see|seeing|seem|seemed|seeming|seems|seen|self|selves|sensible|sent|serious|seriously|seven|several|shall|she|should|since|six|so|some|somebody|somehow|someone|something|sometime|sometimes|somewhat|somewhere|soon|sorry|specified|specify|specifying|still|sub|such|sup|sure|t|take|taken|tell|tends|th|than|thank|thanks|thanx|that|thats|the|their|theirs|them|themselves|then|thence|there|thereafter|thereby|therefore|therein|theres|thereupon|these|they|think|third|this|thorough|thoroughly|those|though|three|through|throughout|thru|thus|to|together|too|took|toward|towards|tried|tries|truly|try|trying|twice|two|u|un|under|unfortunately|unless|unlikely|until|unto|up|upon|us|use|used|useful|uses|using|usually|uucp|v|value|various|ve|very|via|viz|vs|w|want|wants|was|way|we|welcome|well|went|were|what|whatever|when|whence|whenever|where|whereafter|whereas|whereby|wherein|whereupon|wherever|whether|which|while|whither|who|whoever|whole|whom|whose|why|will|willing|wish|with|within|without|wonder|would|would|x|y|yes|yet|you|your|yours|yourself|yourselves|z|zero)\\b\\s*", Pattern.CASE_INSENSITIVE);
		Matcher matcher = stopWords.matcher(org);
		String clean = matcher.replaceAll("");
		
		//remove words that are overused in Reuters data
		Pattern reuterWords = Pattern.compile("\\b(?:reuter|mln|dlr|dlrs)\\b\\s*");
		Matcher rmatcher = reuterWords.matcher(clean);
		String rclean = rmatcher.replaceAll("");
		
		//remove words that contain numbers or underscores in them
		Pattern noiseWords = Pattern.compile("\\b\\S*([0-9]+|_+|@+)\\S*\\b");
		Matcher nmatcher = noiseWords.matcher(rclean);
		String nclean = nmatcher.replaceAll("");
		
		//remove invalid words
		//Pattern invalids = Pattern.compile("([0-9]+.*)|&lt;");
		//The invalid pattern below (longer one) should be used, not the shorter one above!!!
		Pattern invalids = Pattern.compile("\\b[0-9]+\\b|(pe1)|&lt;|(&#[0-9]+;)");
		Matcher imatcher = invalids.matcher(nclean);
		String iclean = imatcher.replaceAll("");
		
		//remove punctuation
		Pattern punctuation = Pattern.compile("\\.|\\?|\\!|\\,|\\(|\\)|\\'|\"|/|<|>|:|\\||\\*|â|¬|\\{|\\}|\\[|\\]|\\~|\\#");
		Matcher pmatcher = punctuation.matcher(iclean);
		String pclean = pmatcher.replaceAll("");
		
		//remove dashes and &s and ;s
		Pattern dashes = Pattern.compile("\\-|&|;");
		Matcher dmatcher = dashes.matcher(pclean);
		String dclean = dmatcher.replaceAll(" ");
		
		//remove extra spaces
		Pattern spaces = Pattern.compile("\\s+");
		Matcher smatcher = spaces.matcher(dclean);
		String finalClean = smatcher.replaceAll(" ");
		
		return finalClean;
	}
	
	public static String reduce (String org) throws IOException 
	{
		StringBuilder ret = new StringBuilder(); 
		String [] orgWords = org.split("\\s"); 
		
		for (String word: orgWords)
		{
			Stemmer s = new Stemmer();
			for (int i = 0; i < word.length(); i++)
			{
				s.add(word.charAt(i));
			}
			s.stem();
			ret.append(s.toString() + " ");
		}
		
		return ret.toString();
	}
	
	public static void main (String [] args) throws IOException 
	{
		String test = "The** (purification) of, the' sugar \"distilling\" process|| is vital!"
				+ " to the growth this_delete and &lt;prosperitype1 of /the United-States of -America. If >we had "
				+ " &prevented poor & {growing]]     PROCESSES from&the early&days this  epidemic &#543; 66643 may not have happened.";
		System.out.println();
		System.out.println(test);
		System.out.println();
		String test1 = swRemove(test);
		System.out.println(test1);
		System.out.println();
		String stemmed = StringStemmer.reduce(StringStemmer.swRemove(test));
		System.out.println(stemmed);
		
	}	
}


