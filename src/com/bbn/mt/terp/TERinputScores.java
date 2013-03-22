/*
 * Copyright 2009 Loic BARRAULT.  
 * All Rights Reserved.  Use is subject to license terms.
 * 
 * See the file "LICENSE.txt" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL 
 * WARRANTIES.
 *
 */
package com.bbn.mt.terp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

public class TERinputScores extends TERinput
{
	protected int in_hyp_scores_format = 0;
	protected int in_ref_scores_format = 0;
	protected int max_num_refs_scores = 0;
	
	private List<String> untok_scores_data = new ArrayList<String>();
	private List<float[]> tok_scores_data = new ArrayList<float[]>();

	private int numdata_scores = 0;

	private TreeMap<TERid, TreeSet<TERid>> allSegScoresIds = new TreeMap<TERid, TreeSet<TERid>>();
	private HashMap<TERid, TreeSet<TERid>> refSegScoresIds = new HashMap<TERid, TreeSet<TERid>>();
	
	private HashMap<TERid, List<Integer>> ref_segs_scores = new HashMap<TERid, List<Integer>>();
	private HashMap<TERid, List<Integer>> hyp_segs_scores = new HashMap<TERid, List<Integer>>();
	
	private static final List<float[]> faketokscoresvec = new Vector<float[]>();
	
	private int add_sen_scores(String senScores)
	{
		// sen = sen.intern();
		float[] scores = parseScores(senScores);  
		untok_scores_data.add(senScores);
		tok_scores_data.add(scores);
		numdata_scores++;   
		if ((untok_scores_data.size() != tok_scores_data.size()) || (numdata_scores != tok_scores_data.size()))
		{
			System.err.println("TERinputScores add_sen_scores : data have not the same size.  Aborting.");
			System.exit(-1);
		}
		return (numdata_scores - 1);
	}
	private float[] parseScores(String senScores)
	{
		if (senScores.matches("^\\s*$"))
		{
			return new float[0];
		}
		String[] sc = senScores.split("\\s+");
		float[] tr = new float[sc.length];
		float d = 0;
		for (int i = 0; i < sc.length; i++)
		{
			try
			{
				d = Float.parseFloat(sc[i]);
			} catch (NumberFormatException nfe)
			{
				System.err.println("TERinput parseScores : impossible to correctly parse word scores ... aborting");
				nfe.printStackTrace();
			}
			tr[i] = d;
		}
		return tr;
	}
	
	private float[] get_tok_scores(int i)
	{
		return tok_scores_data.get(i);
	}
	
	private List<float[]> get_tok_scores(List<Integer> ls)
	{
		if (ls == null)
			return null;
		ArrayList<float[]> als = new ArrayList<float[]>(ls.size());
		for (int i = 0; i < ls.size(); i++)
		{
			als.add(i, get_tok_scores(ls.get(i)));
		}
		return als;
	}
	
	
	
	public List<float[]> getAllHypsScoresTok(TERid plainid)
	{
		List<float[]> toret = new Vector<float[]>();
		for (String sysid : get_sysids())
		{
			for (TERid tid : getHypIds(sysid, plainid))
			{
				if ((params.para().get_boolean(TERpara.OPTIONS.IGNORE_MISSING_HYP)) && (!hasHypSeg(tid)))
				{
					System.err.println("WARNING TERinput.getAllHypsScoresTok : no hypothesis for system " + sysid
							+ " ... skipping ...");
					continue;
				}
				List<float[]> orighyps = getAllHypScoresTok(tid);

				for (float[] h : orighyps)
					toret.add(h);
			}
		}

		if (toret.size() == 0)
			return faketokscoresvec;
		return toret;
	}
	
	public List<float[]> getAllHypScoresTok(TERid tid)
	{
		List<float[]> toret = get_tok_scores(hyp_segs_scores.get(tid));
		if ((toret == null) || (toret.size() == 0))
			return faketokscoresvec;
		return toret;
	}
	
	public List<float[]> getRefScoresForHypTok(TERid htid)
	{
		TERid pid = htid.getPlainIdent();
		TreeSet<TERid> hs = refSegScoresIds.get(pid);
		if ((hs == null) || (hs.size() == 0))
		{
			return faketokscoresvec;
		}
		Vector<float[]> toret = new Vector<float[]>();
		for (TERid tid : hs)
		{
			toret.addAll(get_tok_scores(ref_segs_scores.get(tid)));
		}
		if ((toret == null) || (toret.size() == 0))
			return faketokscoresvec;
		return toret;
	}
	
	
	private void add_segment_scores(TERid id, String scores, STYPE type)
	{
		if (this.ignore_setid)
			id.test_id = "";
		int seg_i = add_sen_scores(scores);
		TreeSet<TERid> idhs = null;
		TERid pid = id.getPlainIdent();
		idhs = allSegScoresIds.get(pid);
		if (idhs == null)
		{
			idhs = new TreeSet<TERid>();
			allSegIds.put(pid, idhs);
		}
		idhs.add(id);
		if (type == STYPE.HYP)
		{
			sysids.add(id.sys_id);
		} 
		else if (type == STYPE.REF)
		{
			TreeSet<TERid> ridhs = null;
			ridhs = refSegScoresIds.get(pid);
			if (ridhs == null)
			{
				ridhs = new TreeSet<TERid>();
				refSegScoresIds.put(pid, ridhs);
			}
			ridhs.add(id);
		} 
		else
		{
			System.err.println("TERinput_loic add_segment_scores : Can't add scores for that type : "+type);
		}
		HashMap<TERid, List<Integer>> hm;
		switch (type)
		{
			case HYP :
				hm = this.hyp_segs_scores;
				break;
			case REF :
				hm = this.ref_segs_scores;
				break;
			default :
				return;
		}
		List<Integer> lst = null;
		lst = hm.get(id);
		if (lst == null)
		{
			lst = new Vector<Integer>();
			hm.put(id, lst);
		}
		lst.add(seg_i);
		return;
	}
	
	private TERinputScores(){}
	
	/*public TERinputScores(String hyp_fn, String ref_fn, TERpara params)
	{
		this.params = params;
		//this.ignore_setid = ignore_setid;
		this.ignore_setid = params.para().get_boolean(TERpara.OPTIONS.IGNORE_SETID);
		load_hyp(hyp_fn);
		load_ref(ref_fn);
	}
	public TERinputScores(String hyp_fn, String ref_fn, String reflen_fn, TERpara params)
	{
		this.params = params;
		//this.ignore_setid = ignore_setid;
		this.ignore_setid = params.para().get_boolean(TERpara.OPTIONS.IGNORE_SETID);
		load_hyp(hyp_fn);
		load_ref(ref_fn);
		load_len(reflen_fn);
	}
	public TERinputScores(String[] hyp_fn, String ref_fn, String reflen_fn, TERpara params)
	{
		this.params = params;
		//this.ignore_setid = ignore_setid;
		this.ignore_setid = params.para().get_boolean(TERpara.OPTIONS.IGNORE_SETID);
		load_ref(ref_fn);
		load_len(reflen_fn);
		for (String hfn : hyp_fn)
			load_hyp(hfn);
	}*/

	public TERinputScores(String[] hyp_fn, String[] hyp_scores_fn, String ref_fn, String ref_scores_fn,
			String reflen_fn, TERpara params)
	{
		this.params = params;
		//this.ignore_setid = ignore_setid;
		this.ignore_setid = params.para().get_boolean(TERpara.OPTIONS.IGNORE_SETID);
		
		//System.err.println(" ---- Loading reference");
		load_ref(ref_fn);
		//System.err.println(" ---- Loading reference scores ...");
		load_ref_scores(ref_scores_fn);
		load_len(reflen_fn);
		
		
		for (int i = 0; i < hyp_fn.length; i++)
		{
			//System.err.println(" ---- Loading hypotheses");
			load_hyp(hyp_fn[i]);
			//System.err.println(" ---- Loading hypotheses scores ...");
			load_hyp_scores(hyp_scores_fn[i]);
		}
	}
	
	public void load_ref_scores(String ref_scores_fn)
	{
		LinkedHashMap<TERid, List<String>> segs = new LinkedHashMap<TERid, List<String>>();
		int form = load_file(ref_scores_fn, segs);
		in_ref_scores_format = form;
		for (Map.Entry<TERid, List<String>> me : segs.entrySet())
		{
			TERid id = me.getKey();
			List<String> lst = me.getValue();
			if (lst.size() > max_num_refs_scores)
			{
				max_num_refs_scores = lst.size();
			}
			for (String s : lst)
			{
				add_segment_scores(id, s, STYPE.REF);
			}
		}
	}
	
	public void load_hyp_scores(String hyp_scores_fn)
	{
		LinkedHashMap<TERid, List<String>> segs = new LinkedHashMap<TERid, List<String>>();
		int form = load_file(hyp_scores_fn, segs);
		in_hyp_scores_format = form;
		for (Map.Entry<TERid, List<String>> me : segs.entrySet())
		{
			TERid id = me.getKey();
			List<String> lst = me.getValue();
			for (String s : lst)
			{
				add_segment_scores(id, s, STYPE.HYP);
			}
		}
	}
}
