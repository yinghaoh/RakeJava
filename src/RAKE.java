import java.util.*;
import java.util.regex.Pattern;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import static com.aliasi.spell.JaroWinklerDistance.JARO_WINKLER_DISTANCE;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;

/**
 * Java Port of RAKE (Rapid Automatic Keyword Extraction algorithm)
 * implementation in python at (https://github.com/aneesha/RAKE).
 *
 * Author: AskDrCatcher
 * License: MIT
 */
public class RAKE {
	private String s;
	private BufferedReader in;
	private LinkedHashMap<String, Double> map;
	private static double shortTerm_percent_threshold = 0.5;
    private boolean isNumber( String str) {
        return str.matches("[0-9.]");
    }

	public ArrayList<String> ReadfileArray(File readfile) throws IOException {
		ArrayList<String> list = new ArrayList<String>();
		try{
                    FileReader rd = new FileReader(readfile);		
                    in = new BufferedReader(rd);
                    while((s = in.readLine())!= null){
                        if(s.trim().length()>0)
                            list.add(s.trim());
                    }		  
                    in.close();  
                    return list;
                }catch (FileNotFoundException e){e.printStackTrace();return null;} 
	}
    
    private List<String> loadStopWords(String filePath) throws FileNotFoundException, IOException {

        if (filePath == null || filePath.trim().length() == 0) {
            filePath = "input/FoxStoplist.txt";
        }

        List<String> stopWords = new ArrayList<String>();
        stopWords = ReadfileArray(new File(filePath));
        return stopWords;
    }

    private List<String> separateWords( String text,  int minimumWordReturnSize) {

         List<String> separateWords = new ArrayList<String>();
         String[] words = text.split("[^a-zA-Z0-9_\\+\\-/]");

        if (words != null && words.length > 0) {

            for ( String word : words) {

                String wordLowerCase = word.trim().toLowerCase();

                if (wordLowerCase.length() > 0 && wordLowerCase.length() > minimumWordReturnSize &&
                        !isNumber(wordLowerCase)) {

                    separateWords.add(wordLowerCase);
                }
            }
        }

        return separateWords;
    }


    private List<String> splitSentences(String text) {

        String[] sentences = text.split("[.!?,;:\\t\\\\-\\\\\"\\\\(\\\\)\\\\\\'\\u2019\\u2013]");

        if (sentences != null) {
            return new ArrayList<String>(Arrays.asList(sentences));
        } else {
            return new ArrayList<String>();
        }
    }

    private Pattern buildStopWordRegex( String stopWordFilePath) throws IOException {

         List<String> stopWords = loadStopWords(stopWordFilePath);
         StringBuilder stopWordPatternBuilder = new StringBuilder();
        int count = 0;
        for( String stopWord: stopWords) {
            if (count++ != 0) {
                stopWordPatternBuilder.append("|");
            }
            stopWordPatternBuilder.append("\\b").append(stopWord).append("\\b");
        }

        return Pattern.compile(stopWordPatternBuilder.toString(), Pattern.CASE_INSENSITIVE);
    }

    private List<String> generateCandidateKeywords(List<String> sentenceList, Pattern stopWordPattern,int ngram, int termLenLow, int termLenHigh) {
         List<String> phraseList = new ArrayList<String>();

        for ( String sentence : sentenceList) {

             String sentenceWithoutStopWord = stopWordPattern.matcher(sentence).replaceAll("|");
             String[] phrases = sentenceWithoutStopWord.split("\\|");

            if (null != phrases && phrases.length > 0) {
                for( String phrase : phrases) {
                	if(phrase.length() > termLenHigh)continue;
                	String[] terms = phrase.trim().split(" ");
                	if(terms.length > ngram)continue;
                	double count = 0;
                	for(String t:terms){
                		if(t.length()<termLenLow) count++;
                	}
                	if(count/terms.length > this.shortTerm_percent_threshold)continue;
                    if (phrase.trim().toLowerCase().length() > 0) {
                        phraseList.add(phrase.trim().toLowerCase());
                    }
                }
            }
        }

        return phraseList;
    }

    private Map<String,Double> calculateWordScores(List<String> phraseList) {

         Map<String, Integer> wordFrequency = new HashMap<String, Integer>();
         Map<String, Integer> wordDegree = new HashMap<String, Integer>();
         Map<String, Double> wordScore = new HashMap<String, Double>();

        for ( String phrase : phraseList) {

             List<String> wordList = separateWords(phrase, 0);
             int wordListLength = wordList.size();
             int wordListDegree = wordListLength - 1;

            for ( String word : wordList) {

               if (!wordFrequency.containsKey(word)) {
                   wordFrequency.put(word, 0);
               }

               if (!wordDegree.containsKey(word)) {
                   wordDegree.put(word, 0);
               }

               wordFrequency.put(word, wordFrequency.get(word) + 1);
               wordDegree.put(word, wordDegree.get(word) + wordListDegree);
            }
        }

         Iterator<String> wordIterator = wordFrequency.keySet().iterator();

        while (wordIterator.hasNext()) {
             String word = wordIterator.next();

            wordDegree.put(word, wordDegree.get(word) + wordFrequency.get(word));

            if (!wordScore.containsKey(word)) {
                wordScore.put(word, 0.0);
            }

            wordScore.put(word, wordDegree.get(word) / (wordFrequency.get(word) * 1.0));
        }

        return wordScore;
    }

    public Map<String, Double> generateCandidateKeywordScores(List<String> phraseList,
                                                               Map<String, Double> wordScore) {

        Map<String, Double> keyWordCandidates = new HashMap<String, Double>();

        for (String phrase : phraseList) {

             List<String> wordList = separateWords(phrase, 0);
            double candidateScore = 0;

            for ( String word : wordList) {
                candidateScore += wordScore.get(word);
            }

            keyWordCandidates.put(phrase, candidateScore);
        }

        return keyWordCandidates;
    }

    
    public Map<String, Double> keyPhraseExtractionMap(String text,String stopPath,int ngram,int termLenLow, int termLenHigh) throws IOException {
    	List<String> sentenceList = splitSentences(text);
        Pattern stopWordPattern = buildStopWordRegex(stopPath);
        List<String> phraseList = generateCandidateKeywords(sentenceList, stopWordPattern,ngram,termLenLow, termLenHigh);
        Map<String, Double> wordScore = calculateWordScores(phraseList);
        Map<String, Double> keywordCandidates = generateCandidateKeywordScores(phraseList, wordScore);
        //keywordCandidates = sortKeyWordCandidates(keywordCandidates);
        return keywordCandidates;
    } 

  
    public double strSimilarity(String phrase, String query){

        String[] queryTerms = query.trim().split(" ");
        String[] phraseTerms = phrase.trim().split(" ");
        if(phrase.compareTo(query)==0){
            return queryTerms.length * MyParameter.getScale();
        }
        double similarity = 0;
        for(int i = 0; i<queryTerms.length;i++){
            String qt = queryTerms[i];
            for(int j = 0; j<phraseTerms.length;j++){
                String pt = phraseTerms[j];
                
                double sim = JARO_WINKLER_DISTANCE.proximity(qt, pt);
                similarity = similarity + (sim==1.0?MyParameter.getScale():sim)* Math.pow(MyParameter.getPowerbase(), Math.abs(i+i-j));
            }
                
        }
        return similarity;
    }
     
	public List<Map.Entry<String, Double>> SolrProcessing(int N,String query){
		
		LinkedHashMap<String, Double> m = new LinkedHashMap<String, Double>();
		//Use solr to get data and call keyPhraseExtractionMap in a loop
		
		//m = keyPhraseExtraction(text, stopPath,5,3,50);   //Append
		//m = sortKeyWordCandidates(m);
		
		this.map = m;
		///////////////////////////////////////////////////////////////////////
		TreeMap<String, Double> similarityMap = new TreeMap<String, Double>();
        for(String t:m.keySet()){
            similarityMap.put(t, new Double(strSimilarity(t,query)));
        }
        List<Map.Entry<String, Double>> res =  findGreatest(similarityMap, N);     
        
        return adjustMapbyWeight(res);
	}
	
    
    
    public List<Map.Entry<String, Double>> adjustMapbyWeight(List<Map.Entry<String, Double>> list){
        int startIdx = 0;
        List<Map.Entry<String, Double>> finalList = new ArrayList<Map.Entry<String, Double>>();
        while(startIdx<list.size()){
            List<Map.Entry<String, Double>> subList = new ArrayList<Map.Entry<String, Double>>();
            subList.add(list.get(startIdx));
            for(int i = startIdx+1; i < list.size(); i++){
                if(Math.abs(list.get(i).getValue()-list.get(i-1).getValue())<=MyParameter.getEpsilon()){
                    subList.add(list.get(i));
                    if(i==list.size()-1){
                        startIdx = i; 
                        break;
                    }
                }                    
                else{
                    startIdx = i;
                    break;
                }
            }
            if(subList.size()>1){
                Collections.sort(subList, 
                        new Comparator<Map.Entry<String, Double>>(){
                                public int compare(Map.Entry<String, Double> left, Map.Entry<String, Double> right){
                                        Double leftValue = map.get(left.getKey());
                                        Double rightValue = map.get(right.getKey());
                                        return rightValue.compareTo(leftValue);
                                }
                }); 
            }
            finalList.addAll(subList);
            if(startIdx==list.size()-1)break;
        }
        return finalList;
    }
	
	
    private LinkedHashMap<String, Double> sortKeyWordCandidates
            (Map<String,Double> keywordCandidates) {

         LinkedHashMap<String, Double> sortedKeyWordCandidates = new LinkedHashMap<String, Double>();
        int totaKeyWordCandidates = keywordCandidates.size();
         List<Map.Entry<String, Double>> keyWordCandidatesAsList =
                new LinkedList<Map.Entry<String, Double>>(keywordCandidates.entrySet());

        Collections.sort(keyWordCandidatesAsList, new Comparator() {
            public int compare(Object o1, Object o2) {
                return ((Map.Entry<String, Double>)o2).getValue()
                        .compareTo(((Map.Entry<String, Double>)o1).getValue());
            }
        });

        totaKeyWordCandidates = totaKeyWordCandidates / 3;
        for( Map.Entry<String, Double> entry : keyWordCandidatesAsList) {
            sortedKeyWordCandidates.put(entry.getKey(), entry.getValue());
            if (--totaKeyWordCandidates == 0) {
                break;
            }
        }

        return sortedKeyWordCandidates;
    }   
    private static <K, V extends Comparable<? super V>> List<Map.Entry<K, V>> 
    findGreatest(Map<K, V> map, int n)
{
    Comparator<? super Map.Entry<K, V>> comparator = 
        new Comparator<Map.Entry<K, V>>()
    {
        public int compare(Map.Entry<K, V> e0, Map.Entry<K, V> e1)
        {
            V v0 = e0.getValue();
            V v1 = e1.getValue();
            return v0.compareTo(v1);
        }
    };
    PriorityQueue<Map.Entry<K, V>> highest = 
        new PriorityQueue<Map.Entry<K,V>>(n, comparator);
    for (Map.Entry<K, V> entry : map.entrySet())
    {
        highest.offer(entry);
        while (highest.size() > n)
        {
            highest.poll();
        }
    }

    List<Map.Entry<K, V>> result = new ArrayList<Map.Entry<K,V>>();
    while (highest.size() > 0)
    {
        result.add(highest.poll());
    }
    Collections.sort(result, 
            new Comparator<Map.Entry<K, V>>(){
                    public int compare(Map.Entry<K, V> left, Map.Entry<K, V> right){
                            V leftValue = left.getValue();
                            V rightValue = right.getValue();
                            return rightValue.compareTo(leftValue);
                    }
    });   
  
    return result;
}
    public String[] keyPhraseExtraction(String text,String stopPath,int ngram,int termLenLow, int termLenHigh) throws IOException {
    	List<String> sentenceList = splitSentences(text);
        Pattern stopWordPattern = buildStopWordRegex(stopPath);
        List<String> phraseList = generateCandidateKeywords(sentenceList, stopWordPattern,ngram,termLenLow, termLenHigh);
        Map<String, Double> wordScore = calculateWordScores(phraseList);
        Map<String, Double> keywordCandidates = generateCandidateKeywordScores(phraseList, wordScore);
        //keywordCandidates = sortKeyWordCandidates(keywordCandidates);
        Object[] obj = keywordCandidates.keySet().toArray();
        String[] s = new String[obj.length];
        s = Arrays.copyOf(obj, obj.length, String[].class);
        return s;
    } 
    public static void main(String[] args) throws IOException {

        String text =
               "Compatibility of systems of linear constraints over the set of natural numbers. Criteria of compatibility of a system of linear Diophantine equations, strict inequations, and nonstrict inequations are considered. Upper bounds for components of a minimal set of solutions and algorithms of construction of minimal generating sets of solutions for all types of systems are given. These criteria and the corresponding algorithms for constructing a minimal supporting set of solutions can be used in solving all the considered types of systems and systems of mixed types.";

        RAKE rakeInstance = new RAKE();

        String stopPath = "input/SmartStoplist.txt";
       String[] keywordCandidates = new String[24];
       keywordCandidates =  rakeInstance.keyPhraseExtraction(text, stopPath,5,3,50);
       		for(int i =0; i<keywordCandidates.length; i++)
       System.out.println("keyWordCandidates = "+ keywordCandidates[i]);

   }    
}