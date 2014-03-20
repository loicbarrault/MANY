/*
 * Copyright 2009 Loic BARRAULT.  
 * Portions Copyright BBN and UMD (see LICENSE_TERP.txt).  
 * Portions Copyright 1999-2008 CMU (see LICENSE_SPHINX4.txt).
 * All Rights Reserved.  Use is subject to license terms.
 * 
 * See the file "LICENSE.txt" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL 
 * WARRANTIES.
 *
 */
package edu.lium.decoder;

import edu.cmu.sphinx.linguist.WordSequence;

public class Token implements Comparable<Token>
{
	float score, norm_score;
	Token pred;
	Node node;
	WordSequence history;
	WordSequence lmhistory;
	
	float lm_score;
	int nb_words;
	int nb_nulls;
	int[] word_by_sys;
	
	public Token(){}
	
	/*public Token(float score, Token pred, Node node, WordSequence history, WordSequence lmhistory, float lm_score, int nb_words, int nb_nulls, int[] word_by_sys)
	{
		this.score = score;
		this.pred = pred;
		this.node = node;
		this.history = history;
		this.lmhistory = lmhistory;
		
		this.lm_score = lm_score;
		this.nb_words = nb_words;
		this.nb_nulls = nb_nulls;
		this.word_by_sys = word_by_sys;
	}*/
	
	public Token(Token pred, Node node, WordSequence history, WordSequence lmhistory, float lm_score, int nb_words, int nb_nulls, int[] word_by_sys)
	{
		this.pred = pred;
		this.node = node;
		this.history = history;
		this.lmhistory = lmhistory;
		
		this.lm_score = lm_score;
		this.nb_words = nb_words;
		this.nb_nulls = nb_nulls;
		this.word_by_sys = word_by_sys;
		
	}
	
	
	
	public Node getNode() { return node;}
	public float getScore() { return score;}
	public void setScore(float score) { this.score = score;}
	//public float getNormalizedScore() { return norm_score;}
	//public void setNormalizedScore(float nscore) { this.norm_score = norm_score;}
	public WordSequence getHistory() { return history;}
	
	public int compareTo(Token tok)
	{
		if(score > tok.score)
			return 1;
		else if(score < tok.score)
			return -1;
		return 0;
	};


	public float computeScore(float[] lambdas)
	{
		if(lambdas == null)
			return -Float.MAX_VALUE;
		
		score = 0.0f;
		if(lambdas.length != (3+word_by_sys.length))
		{
			System.err.println("Number of lambdas is not the same as number of feature functions ... exiting!");
			System.exit(0);
		}
		
		score += lambdas[0]*lm_score;
		score += lambdas[1]*(-nb_words);
		score += lambdas[2]*(-nb_nulls);
		
		for(int i=0; i<word_by_sys.length; i++)
		{
			score += lambdas[i+3]*(-word_by_sys[i]);
		}
		setScore(score);
		
		return score;
	}

	public String toString(){
	    StringBuilder sb = new StringBuilder();
	    sb.append(" [TOK node id: ").append(node.id).append(" score:").append(score).append("]"); //.append(" norm_score:").append(norm_score);
	    return sb.toString();
	}


}
