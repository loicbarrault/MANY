package com.bbn.mt.terp;
/*

 Copyright 2006 by BBN Technologies and University of Maryland (UMD)

 BBN and UMD grant a nonexclusive, source code, royalty-free right to
 use this Software known as Translation Error Rate COMpute (the
 "Software") solely for research purposes. Provided, you must agree
 to abide by the license and terms stated herein. Title to the
 Software and its documentation and all applicable copyrights, trade
 secrets, patents and other intellectual rights in it are and remain
 with BBN and UMD and shall not be used, revealed, disclosed in
 marketing or advertisement or any other activity not explicitly
 permitted in writing.

 BBN and UMD make no representation about suitability of this
 Software for any purposes.  It is provided "AS IS" without express
 or implied warranties including (but not limited to) all implied
 warranties of merchantability or fitness for a particular purpose.
 In no event shall BBN or UMD be liable for any special, indirect or
 consequential damages whatsoever resulting from loss of use, data or
 profits, whether in an action of contract, negligence or other
 tortuous action, arising out of or in connection with the use or
 performance of this Software.

 Without limitation of the foregoing, user agrees to commit no act
 which, directly or indirectly, would violate any U.S. law,
 regulation, or treaty, or any other international treaty or
 agreement to which the United States adheres or with which the
 United States complies, relating to the export or re-export of any
 commodities, software, or technical data.  This Software is licensed
 to you on the condition that upon completion you will cease to use
 the Software and, on request of BBN and UMD, will destroy copies of
 the Software in your possession.                                                

 TERcalc.java v1
 Matthew Snover (snover@cs.umd.edu)                           

 */
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
public class TERcalc
{
	/* We may want to add some function to change the beam width */
	public int BEAM_WIDTH = 20;
	protected boolean use_porter = false;
	protected boolean use_wordnet = false;
	protected boolean use_phrases = false;
	protected static final double INF = 999999.0;
	protected int MAX_SHIFT_SIZE = 10;
	protected int MAX_SHIFT_DIST = 50;
	/* Variables for some internal counting. */
	protected int NUM_SEGMENTS_SCORED = 0;
	protected int NUM_SHIFTS_CONSIDERED = 0;
	protected int NUM_BEAM_SEARCH_CALLS = 0;
	/* These are resized by the MIN_EDIT_DIST code if they aren't big enough */
	protected double[][] S = new double[350][350];
	protected char[][] P = new char[350][350];
	protected PhraseTree.OffsetScore[][] PL = new PhraseTree.OffsetScore[350][350];
	protected static enum SHIFT_CON{ NOSHIFT, EXACT, RELAX_NP, RELAX, ALLOW_MISMATCH }
	protected SHIFT_CON shift_con = SHIFT_CON.EXACT;
	protected int max_mismatch_num = 1;
	protected double max_mismatch_percent = 0.5;
	protected HashSet<Comparable<String>> shift_stop_words = new HashSet<Comparable<String>>();
	protected TERpara params = null;
	protected Porter porter = null;
	protected WordNet wordnet = null;
	protected TERcalc(){}
	
	public TERcalc(TERcost costfunc, TERpara params)
	{
		this.costfunc = costfunc;
		this.params = params;
		porter = new Porter(params);
		wordnet = new WordNet();
	}
	protected TERcost costfunc;
	/* Turn on if you want a lot of debugging info. */
	static final private boolean DEBUG = false;
	public double ref_len = -1.;
	protected long calcTime = 0;
	public final void loadPT(String[] hyps, String[] refs, Comparable key)
	{
		PhraseTable pt = costfunc.getPhraseTable();
		if (pt == null)
			return;
		String[][] tokhyp = new String[hyps.length][];
		String[][] tokref = new String[refs.length][];
		for (int i = 0; i < hyps.length; i++)
		{
			Comparable[] hyparr = costfunc.process_input_hyp(NormalizeText.process(hyps[i]));
			tokhyp[i] = new String[hyparr.length];
			for (int j = 0; j < hyparr.length; j++)
			{
				tokhyp[i][j] = hyparr[j].toString();
			}
		}
		for (int i = 0; i < refs.length; i++)
		{
			Comparable[] refarr = costfunc.process_input_ref(NormalizeText.process(refs[i]));
			tokref[i] = new String[refarr.length];
			for (int j = 0; j < refarr.length; j++)
			{
				tokref[i][j] = refarr[j].toString();
			}
		}
		loadPT(tokhyp, tokref, key);
	}
	public void loadPT(String[][] tokhyps, String[][] tokrefs, Comparable key)
	{
		PhraseTable pt = costfunc.getPhraseTable();
		if (pt == null)
			return;
		pt.add_phrases(tokrefs, tokhyps, key);
	}
	public boolean init(Comparable key)
	{
		PhraseTable pt = costfunc.getPhraseTable();
		if (pt == null)
			return true;
		return pt.setKey(key);
	}
	public TERcost getCostFunc()
	{
		return costfunc;
	}
	public double getCalcTime()
	{
		double ETsec = this.calcTime / 1.0E09;
		return ETsec;
	}
	public void output_phrasetable(String fname)
	{
		PhraseTable pt = costfunc.getPhraseTable();
		if (pt == null)
			return;
		pt.output_phrase_table(fname);
	}
	public void setUseStemming(boolean b)
	{
		use_porter = b;
	}
	public void setUseSynonyms(boolean b)
	{
		use_wordnet = b;
	}
	public void setUsePhrases(boolean b)
	{
		use_phrases = b;
	}
	public boolean getUsePhrases()
	{
		return use_phrases;
	}
	public void setShiftSize(int i)
	{
		MAX_SHIFT_SIZE = i;
	}
	public void setBeamWidth(int i)
	{
		BEAM_WIDTH = i;
	}
	public void setShiftDist(int i)
	{
		MAX_SHIFT_DIST = i;
	}
	public void setRefLen(String[][] reflens)
	{
		if ((reflens == null) || (reflens.length == 0))
		{
			setRefLen(-1.0);
		} else
		{
			double alen = 0.0;
			for (String[] ref : reflens)
			{
				alen += ref.length;
			}
			setRefLen(alen / reflens.length);
		}
	}
	public void setRefLen(String[] reflens)
	{
		if (reflens == null || reflens.length == 0)
		{
			setRefLen(-1.0);
		} else
		{
			String[][] ls = new String[reflens.length][];
			for (int i = 0; i < reflens.length; i++)
				ls[i] = NormalizeText.process(reflens[i]);
			setRefLen(ls);
		}
	}
	public void setRefLen(double d)
	{
		ref_len = (d >= 0) ? d : -1;
	}
	public TERalignment[] TERall(String[] hyp, String[][] refs)
	{
		// Run TER on hyp and each ref (both should already be tokenized),
		// and return an array of TERalignment[]
		TERalignment[] tr = new TERalignment[refs.length];
		Comparable[] pphyp = costfunc.process_input_hyp(hyp);
		for (int i = 0; i < refs.length; i++)
		{
			Comparable[] ppref = costfunc.process_input_ref(refs[i]);
			TERalignment res = null;
			if ((ppref.length == 0) || (pphyp.length == 0))
				res = TERnullstr(pphyp, ppref);
			else
				res = TERpp(pphyp, ppref);
			if (ref_len >= 0)
				res.numWords = ref_len;
			tr[i] = res;
		}
		return tr;
	}
	/*public TERalignmentCN TERcn(List<String[]> hyps, String[] ref)
	{
		//System.out.println("START TERcn");
		// Run TER on hyp and each ref (both should already be tokenized),
		// and return an array of TERalignment[]
		TERalignmentCN align = null;
		boolean first = true;
		ArrayList<ArrayList<Comparable<String>>> cn = costfunc.process_input_cn(ref);

		for (int i = 0; i < hyps.size(); i++)
		{
			Comparable[] pphyp = costfunc.process_input_hyp(hyps.get(i));
			if ((cn.size() == 0) || (pphyp.length == 0))
				continue;
			else
			{
				if (first)
				{
					align = TERppcn(pphyp, cn, ref);
				} else
				{
					align = TERppcn(pphyp, align.cn, ref);
				}
				align.buildCN();
				first = false;
			}
			if (ref_len >= 0)
				align.numWords = ref_len;
		}
		//System.out.println("END TERcn");
		return align;
	}*/
	public TERalignment TER(String hyp, String ref)
	{
		/* Tokenize the strings and pass them off to TER */
		TERalignment to_return;
		Comparable[] hyparr = costfunc.process_input_hyp(NormalizeText.process(hyp));
		Comparable[] refarr = costfunc.process_input_ref(NormalizeText.process(ref));
		if (refarr.length == 0 || hyparr.length == 0)
		{
			to_return = TERnullstr(hyparr, refarr);
			if (ref_len >= 0)
				to_return.numWords = ref_len;
		} else
		{
			to_return = TERpp(hyparr, refarr);
			if (ref_len >= 0)
				to_return.numWords = ref_len;
		}
		to_return.orig_hyp = hyp;
		to_return.orig_ref = ref;
		return to_return;
	}
	public TERalignment TERnullstr(Comparable[] hyparr, Comparable[] refarr)
	{
		TERalignment to_return = new TERalignment(costfunc, params);
		if (hyparr.length == 0 && refarr.length == 0)
		{
			to_return.alignment = new char[0];
			to_return.alignment_r = new int[0];
			to_return.alignment_h = new int[0];
			to_return.numWords = 0;
			to_return.numEdits = 0;
		} else if (hyparr.length == 0)
		{
			to_return.alignment = new char[refarr.length];
			to_return.alignment_r = new int[to_return.alignment.length];
			to_return.alignment_h = new int[to_return.alignment.length];
			for (int i = 0; i < refarr.length; ++i)
			{
				to_return.alignment[i] = 'D';
				to_return.alignment_h[i] = 0;
				to_return.alignment_r[i] = 1;
				to_return.numEdits += costfunc.delete_cost(refarr[i]);
			}
			to_return.numWords = refarr.length;
		} else
		{
			to_return.alignment = new char[hyparr.length];
			to_return.alignment_r = new int[to_return.alignment.length];
			to_return.alignment_h = new int[to_return.alignment.length];
			for (int i = 0; i < hyparr.length; ++i)
			{
				to_return.alignment[i] = 'I';
				to_return.alignment_h[i] = 1;
				to_return.alignment_r[i] = 0;
				to_return.numEdits += costfunc.insert_cost(hyparr[i]);
			}
			to_return.numWords = 0;
		}
		to_return.hyp = hyparr;
		to_return.ref = refarr;
		to_return.aftershift = hyparr;
		return to_return;
	}
	public TERalignment TER(String[] hyp, String[] ref)
	{
		return TERpp(costfunc.process_input_hyp(hyp), costfunc.process_input_ref(ref));
	}
	// Run TER on preprocessed segment pair
	public TERalignment TERpp(Comparable<String>[] hyp, Comparable<String>[] ref)
	{
		/* Calculates the TER score for the hyp/ref pair */
		long startTime = System.nanoTime();
		TERalignment cur_align = MinEditDist(hyp, ref);
		Comparable<String>[] cur = hyp;
		cur_align.hyp = hyp;
		cur_align.ref = ref;
		cur_align.aftershift = hyp;
		double edits = 0;
		int numshifts = 0;
		ArrayList<TERshift> allshifts = new ArrayList<TERshift>(hyp.length + ref.length);
		if (DEBUG)
			System.out.println("Initial Alignment:\n" + cur_align + "\n");
		while (true)
		{
			Object[] returns = CalcBestShift(cur, hyp, ref, cur_align);
			// Object[] returns = CalcBestShift(cur, hyp, ref, rloc, cur_align);
			if (returns == null)
			{
				break;
			}
			TERshift bestShift = (TERshift) returns[0];
			edits += bestShift.cost;
			cur_align = (TERalignment) returns[1];
			bestShift.alignment = cur_align.alignment;
			bestShift.aftershift = cur_align.aftershift;
			allshifts.add(bestShift);
			cur = cur_align.aftershift;
		}
		TERalignment to_return = cur_align;
		to_return.allshifts = allshifts.toArray(new TERshift[0]);
		to_return.numEdits += edits;
		NUM_SEGMENTS_SCORED++;
		long endTime = System.nanoTime();
		this.calcTime += endTime - startTime;
		return to_return;
	}
	
	private void _gather_exposs_shifts(Comparable<String>[] hyp, Comparable<String>[] ref, boolean[] herr, boolean[] rerr,
			int[] ralign, int[] halign, Set<TERshift>[] allshifts, TreeMap[] paramap, int h_start, int r_start, int h_len,
			int r_len, int num_mismatch)
	{
		boolean ok = true;
		if (h_len >= MAX_SHIFT_SIZE)
			return;
		for (int len = 0; ok && (len < (MAX_SHIFT_SIZE - h_len)); len++)
		{
			int hind = h_start + h_len + len;
			int rind = r_start + r_len + len;
			if (hind >= hyp.length)
				return;
			if (rind >= ref.length)
				return;
			if ((paramap != null) && (paramap[rind] != null))
			{
				ArrayList pal = (ArrayList) paramap[rind].get(hind);
				if (pal != null)
				{
					for (int i = 0; i < pal.size(); i++)
					{
						PhraseTree.OffsetScore sc = (PhraseTree.OffsetScore) pal.get(i);
						_gather_exposs_shifts(hyp, ref, herr, rerr, ralign, halign, allshifts, paramap, h_start,
								r_start, h_len + len + sc.hyp_len, r_len + len + sc.ref_len, num_mismatch);
					}
				}
			}
			Comparable<String> hp = hyp[hind];
			Comparable<String> rp = ref[rind];
			boolean is_mismatch = true;
			if ((rp.equals(hp))
					|| (((shift_con == SHIFT_CON.RELAX) || (shift_con == SHIFT_CON.RELAX_NP) || (shift_con == SHIFT_CON.ALLOW_MISMATCH)) && ((use_porter && porter
							.equivStems(hp, rp)) || (use_wordnet && wordnet.areSynonyms(hp, rp)))))
			{
				is_mismatch = false;
			}
			if ((is_mismatch == false)
					|| ((shift_con == SHIFT_CON.ALLOW_MISMATCH) && ((num_mismatch + 1) <= max_mismatch_num) && ((num_mismatch + 1) <= (MAX_SHIFT_SIZE * max_mismatch_percent))))
			{
				if (is_mismatch)
				{
					num_mismatch++;
					_gather_exposs_shifts(hyp, ref, herr, rerr, ralign, halign, allshifts, paramap, h_start, r_start,
							h_len + len + 1, r_len + len, num_mismatch);
					_gather_exposs_shifts(hyp, ref, herr, rerr, ralign, halign, allshifts, paramap, h_start, r_start,
							h_len + len, r_len + len + 1, num_mismatch);
				}
				// Check number of mismatches (only matters for ALLOW_MISMATCH
				boolean too_many_mismatch = false;
				if ((shift_con != SHIFT_CON.ALLOW_MISMATCH) && (num_mismatch > 0))
					too_many_mismatch = true;
				if ((num_mismatch > 0)
						&& ((num_mismatch > max_mismatch_num) || ((num_mismatch / (1 + h_len + len)) > max_mismatch_percent)))
					too_many_mismatch = true;
				if (too_many_mismatch)
					continue;
				// Check if there is an error anywhere
				boolean no_err = true;
				for (int i = h_start; no_err && (i <= hind); i++)
				{
					if (herr[i])
						no_err = false;
				}
				if (no_err)
					continue;
				no_err = true;
				for (int i = r_start; no_err && (i <= rind); i++)
				{
					if (rerr[i])
						no_err = false;
				}
				if (no_err)
					continue;
				// Check the stop word list
				boolean all_hyp_stop = true;
				boolean all_ref_stop = true;
				if (shift_stop_words != null)
				{
					for (int i = h_start; all_hyp_stop && (i <= hind); i++)
						if (!shift_stop_words.contains(hyp[i]))
							all_hyp_stop = false;
					if (all_hyp_stop)
						continue;
					for (int i = r_start; all_ref_stop && (i <= rind); i++)
						if (!shift_stop_words.contains(ref[i]))
							all_ref_stop = false;
					if (all_ref_stop)
						continue;
				}
				if ((!too_many_mismatch) && (!all_hyp_stop) && (!all_ref_stop) && (!no_err))
				{
					int moveto = ralign[r_start];
					if ((moveto != h_start) && ((moveto < h_start) || (moveto > hind))
							&& ((moveto - h_start) <= MAX_SHIFT_DIST) && ((h_start - moveto) <= MAX_SHIFT_DIST))
					{
						for (int roff = -1; roff <= (hind - h_start); roff++)
						{
							TERshift topush = null;
							if ((roff == -1) && (r_start == 0))
							{
								topush = new TERshift(h_start, hind, -1, -1);
							} else if ((r_start + roff >= 0) && (r_start + roff < ralign.length)
									&& (h_start != ralign[r_start + roff])
									&& ((roff == 0) || (ralign[r_start + roff] != ralign[r_start])))
							{
								topush = new TERshift(h_start, hind, r_start + roff, ralign[r_start + roff]);
							}
							if (topush != null)
							{
								Comparable<String>[] sh = new Comparable[(hind - h_start) + 1];
								for (int hl = 0; hl < sh.length; hl++)
									sh[hl] = hyp[h_start + hl];
								Comparable<String>[] sr = new Comparable[(rind - r_start) + 1];
								for (int rl = 0; rl < sr.length; rl++)
									sr[rl] = ref[r_start + rl];
								topush.shifted = Arrays.asList(sh);
								topush.shiftedto = Arrays.asList(sr);
								topush.cost = costfunc.shift_cost(topush);
								int maxlen = (sr.length > sh.length) ? sr.length : sh.length;
								if (maxlen > allshifts.length)
									maxlen = allshifts.length;
								allshifts[maxlen - 1].add(topush);
							}
						}
					}
				}
			} else
			{
				ok = false;
			}
		}
	}
	private void _gather_exposs_shifts_cn(Comparable<String>[] hyp, ArrayList<ArrayList<Comparable<String>>> cn,
			boolean[] herr, boolean[] rerr, int[] ralign, int[] halign, Set<TERshift>[] allshifts, TreeMap[] paramap,
			int h_start, int r_start, int h_len, int r_len, int num_mismatch)
	{
		boolean ok = true;
		if (h_len >= MAX_SHIFT_SIZE)
			return;
		for (int len = 0; ok && (len < (MAX_SHIFT_SIZE - h_len)); len++)
		{
			int hind = h_start + h_len + len;
			int rind = r_start + r_len + len;
			if (hind >= hyp.length)
				return;
			if (rind >= cn.size())
				return;
			if ((paramap != null) && (paramap[rind] != null))
			{
				ArrayList pal = (ArrayList) paramap[rind].get(hind);
				if (pal != null)
				{
					for (int i = 0; i < pal.size(); i++)
					{
						PhraseTree.OffsetScore sc = (PhraseTree.OffsetScore) pal.get(i);
						_gather_exposs_shifts_cn(hyp, cn, herr, rerr, ralign, halign, allshifts, paramap, h_start,
								r_start, h_len + len + sc.hyp_len, r_len + len + sc.ref_len, num_mismatch);
					}
				}
			}

			boolean is_mismatch = true;
			Comparable<String> hp = hyp[hind];
			// Comparable rp = ref[rind];
			for (Comparable<String> rp : cn.get(rind))
			{

				if ((rp.equals(hp))
						|| (((shift_con == SHIFT_CON.RELAX) || (shift_con == SHIFT_CON.RELAX_NP) || (shift_con == SHIFT_CON.ALLOW_MISMATCH)) && ((use_porter && porter
								.equivStems(hp, rp)) || (use_wordnet && wordnet.areSynonyms(hp, rp)))))
				{
					is_mismatch = false;
				}
			}

			if ((is_mismatch == false)
					|| ((shift_con == SHIFT_CON.ALLOW_MISMATCH) && ((num_mismatch + 1) <= max_mismatch_num) && ((num_mismatch + 1) <= (MAX_SHIFT_SIZE * max_mismatch_percent))))
			{
				if (is_mismatch)
				{
					num_mismatch++;
					_gather_exposs_shifts_cn(hyp, cn, herr, rerr, ralign, halign, allshifts, paramap, h_start, r_start,
							h_len + len + 1, r_len + len, num_mismatch);
					_gather_exposs_shifts_cn(hyp, cn, herr, rerr, ralign, halign, allshifts, paramap, h_start, r_start,
							h_len + len, r_len + len + 1, num_mismatch);
				}
				// Check number of mismatches (only matters for ALLOW_MISMATCH
				boolean too_many_mismatch = false;
				if ((shift_con != SHIFT_CON.ALLOW_MISMATCH) && (num_mismatch > 0))
					too_many_mismatch = true;
				if ((num_mismatch > 0)
						&& ((num_mismatch > max_mismatch_num) || ((num_mismatch / (1 + h_len + len)) > max_mismatch_percent)))
					too_many_mismatch = true;
				if (too_many_mismatch)
					continue;
				// Check if there is an error anywhere
				boolean no_err = true;
				for (int i = h_start; no_err && (i <= hind); i++)
				{
					if (herr[i])
						no_err = false;
				}
				if (no_err)
					continue;
				no_err = true; // useless
				for (int i = r_start; no_err && (i <= rind); i++)
				{
					if (rerr[i])
						no_err = false;
				}
				if (no_err)
					continue;
				// Check the stop word list
				boolean all_hyp_stop = true;
				boolean all_ref_stop = true;
				if (shift_stop_words != null)
				{
					for (int i = h_start; all_hyp_stop && (i <= hind); i++)
						if (!shift_stop_words.contains(hyp[i]))
							all_hyp_stop = false;
					if (all_hyp_stop)
						continue;
					for (int i = r_start; all_ref_stop && (i <= rind); i++)
					{
						for (Comparable<String> c : cn.get(i))
							if (!shift_stop_words.contains(c))
								all_ref_stop = false;
					}
					if (all_ref_stop)
						continue;
				}
				if ((!too_many_mismatch) && (!all_hyp_stop) && (!all_ref_stop) && (!no_err))
				{
					int moveto = ralign[r_start];
					if ((moveto != h_start) && ((moveto < h_start) || (moveto > hind))
							&& ((moveto - h_start) <= MAX_SHIFT_DIST) && ((h_start - moveto) <= MAX_SHIFT_DIST))
					{
						for (int roff = -1; roff <= (hind - h_start); roff++)
						{
							TERshift topush = null;
							if ((roff == -1) && (r_start == 0))
							{
								topush = new TERshift(h_start, hind, -1, -1);
							} else if ((r_start + roff >= 0) && (r_start + roff < ralign.length)
									&& (h_start != ralign[r_start + roff])
									&& ((roff == 0) || (ralign[r_start + roff] != ralign[r_start])))
							{
								topush = new TERshift(h_start, hind, r_start + roff, ralign[r_start + roff]);
							}
							if (topush != null)
							{
								Comparable<String>[] sh = new Comparable[(hind - h_start) + 1];
								for (int hl = 0; hl < sh.length; hl++)
									sh[hl] = hyp[h_start + hl];
								Comparable<String>[] sr = new Comparable[(rind - r_start) + 1];
								for (int rl = 0; rl < sr.length; rl++)
									sr[rl] = cn.get(r_start + rl).get(0);
								ArrayList<ArrayList<Comparable<String>>> rcn = new ArrayList<ArrayList<Comparable<String>>>();
								for (int rl = r_start; rl < rind + 1; rl++)
								{
									rcn.add(cn.get(rl));
								}
								topush.shifted = Arrays.asList(sh);
								topush.shiftedto = Arrays.asList(sh);
								topush.shiftedto_cn = rcn;
								topush.cost = costfunc.shift_cost(topush);
								int maxlen = (rcn.size() > sh.length) ? rcn.size() : sh.length;
								if (maxlen > allshifts.length)
									maxlen = allshifts.length;
								allshifts[maxlen - 1].add(topush);
							}
						}
					}
				}
			} else
			{
				ok = false;
			}
		}
	}
	private TERshift[][] gather_poss_shifts(Comparable<String>[] hyp, Comparable<String>[] ref, boolean[] herr, boolean[] rerr,
			int[] ralign, int[] halign)
	{
		if ((shift_con == SHIFT_CON.NOSHIFT) || (MAX_SHIFT_SIZE <= 0) || (MAX_SHIFT_DIST <= 0))
		{
			TERshift[][] to_return = new TERshift[0][];
			return to_return;
		}
		Set<TERshift>[] allshifts = new TreeSet[MAX_SHIFT_SIZE + 1];
		for (int i = 0; i < allshifts.length; i++)
			allshifts[i] = new TreeSet<TERshift>();
		TreeMap[] paramap = null;
		if (use_phrases && (costfunc.getPhraseTable() != null) && (shift_con != SHIFT_CON.EXACT)
				&& (shift_con != SHIFT_CON.RELAX_NP))
		{
			paramap = new TreeMap[ref.length];
			PhraseTable pt = costfunc.getPhraseTable();
			for (int ri = 0; ri < ref.length; ri++)
			{
				paramap[ri] = new TreeMap();
			}
			for (int hi = 0; hi < hyp.length; hi++)
			{
				// We don't need to look at paraphrases that are
				// too far away to shift
				int starth = hi - MAX_SHIFT_DIST;
				if (starth < 0)
					starth = 0;
				int endh = hi + MAX_SHIFT_DIST;
				if (endh >= hyp.length)
					endh = hyp.length - 1;
				int startr = halign[starth];
				int endr = halign[endh];
				if (startr < 0)
					startr = 0;
				if (endr >= ref.length)
					endr = ref.length - 1;
				for (int ri = startr; ri < (endr + 1); ri++)
				{
					ArrayList ph = pt.retrieve_all(ref, ri, hyp, hi);
					if ((ph != null) && (ph.size() > 0))
					{
						paramap[ri].put(hi, ph);
					}
				}
			}
		}
		for (int ri = 0; ri < ref.length; ri++)
		{
			for (int hi = 0; hi < hyp.length; hi++)
			{
				_gather_exposs_shifts(hyp, ref, herr, rerr, ralign, halign, allshifts, paramap, hi, ri, 0, 0, 0);
			}
		}
		TERshift[][] to_return = new TERshift[MAX_SHIFT_SIZE + 1][];
		for (int i = 0; i < to_return.length; i++)
		{
			ArrayList<TERshift> al = new ArrayList<TERshift>(allshifts[i]);
			to_return[i] = al.toArray(new TERshift[0]);
		}
		return to_return;
	}
	private TERshift[][] gather_poss_shifts_cn(Comparable<String>[] hyp, ArrayList<ArrayList<Comparable<String>>> cn,
			boolean[] herr, boolean[] rerr, int[] ralign, int[] halign)
	{
		if ((shift_con == SHIFT_CON.NOSHIFT) || (MAX_SHIFT_SIZE <= 0) || (MAX_SHIFT_DIST <= 0))
		{
			TERshift[][] to_return = new TERshift[0][];
			return to_return;
		}
		Set<TERshift>[] allshifts = new TreeSet[MAX_SHIFT_SIZE + 1];
		for (int i = 0; i < allshifts.length; i++)
			allshifts[i] = new TreeSet<TERshift>();
		TreeMap[] paramap = null;
		if (use_phrases && (costfunc.getPhraseTable() != null) && (shift_con != SHIFT_CON.EXACT)
				&& (shift_con != SHIFT_CON.RELAX_NP))
		{
			paramap = new TreeMap[cn.size()];
			PhraseTable pt = costfunc.getPhraseTable();
			for (int ri = 0; ri < cn.size(); ri++)
			{
				paramap[ri] = new TreeMap();
			}
			for (int hi = 0; hi < hyp.length; hi++)
			{
				// We don't need to look at paraphrases that are too far away to
				// shift
				int starth = hi - MAX_SHIFT_DIST;
				if (starth < 0)
					starth = 0;
				int endh = hi + MAX_SHIFT_DIST;
				if (endh >= hyp.length)
					endh = hyp.length - 1;
				int startr = halign[starth];
				int endr = halign[endh];
				if (startr < 0)
					startr = 0;
				if (endr >= cn.size())
					endr = cn.size() - 1;
				for (int ri = startr; ri < (endr + 1); ri++)
				{
					ArrayList ph = pt.retrieve_all(cn, ri, hyp, hi);
					if ((ph != null) && (ph.size() > 0))
					{	
						paramap[ri].put(hi, ph);
					}
				}
			}
		}
		for (int ri = 0; ri < cn.size(); ri++)
		{
			for (int hi = 0; hi < hyp.length; hi++)
			{
				_gather_exposs_shifts_cn(hyp, cn, herr, rerr, ralign, halign, allshifts, paramap, hi, ri, 0, 0, 0);
			}
		}
		TERshift[][] to_return = new TERshift[MAX_SHIFT_SIZE + 1][];
		for (int i = 0; i < to_return.length; i++)
		{
			ArrayList<TERshift> al = new ArrayList<TERshift>(allshifts[i]);
			to_return[i] = al.toArray(new TERshift[0]);
		}
		return to_return;
	}
	private Map BuildWordMatches(Comparable<String>[] hyp, Comparable<String>[] ref)
	{
		Set<Comparable<String>> hwhash = new HashSet<Comparable<String>>();
		for (int i = 0; i < hyp.length; i++)
		{
			hwhash.add(hyp[i]);
		}
		boolean[] cor = new boolean[ref.length];
		for (int i = 0; i < ref.length; i++)
		{
			if (hwhash.contains(ref[i]))
			{
				cor[i] = true;
			} else
			{
				cor[i] = false;
			}
		}
		List<Comparable<String>> reflist = Arrays.asList(ref);
		HashMap<List<Comparable<String>>, Set<Integer>> to_return = new HashMap<List<Comparable<String>>, Set<Integer>>();
		for (int start = 0; start < ref.length; start++)
		{
			if (cor[start])
			{
				for (int end = start; ((end < ref.length) && (end - start <= MAX_SHIFT_SIZE) && (cor[end])); end++)
				{
					List<Comparable<String>> topush = reflist.subList(start, end + 1);
					if (to_return.containsKey(topush))
					{
						Set<Integer> vals = to_return.get(topush);
						vals.add(new Integer(start));
					} else
					{
						Set<Integer> vals = new TreeSet<Integer>();
						vals.add(new Integer(start));
						to_return.put(topush, vals);
					}
				}
			}
		}
		return to_return;
	}
	protected void FindAlignErr(TERalignment align, boolean[] herr, boolean[] rerr, int[] ralign, int[] halign)
	{
		int hpos = -1;
		int rpos = -1;
		for (int i = 0; i < align.alignment.length; i++)
		{
			char sym = align.alignment[i];
			if (sym == ' ')
			{
				hpos++;
				rpos++;
				herr[hpos] = false;
				rerr[rpos] = false;
				ralign[rpos] = hpos;
				halign[hpos] = rpos;
			} else if ((sym == 'S') || (sym == 'T') || (sym == 'Y'))
			{
				hpos++;
				rpos++;
				herr[hpos] = true;
				rerr[rpos] = true;
				ralign[rpos] = hpos;
				halign[hpos] = rpos;
			} else if (sym == 'P')
			{
				int srpos = rpos + 1;
				int shpos = hpos + 1;
				for (int j = 0; j < align.alignment_h[i]; j++)
				{
					hpos++;
					herr[hpos] = true;
					halign[hpos] = srpos;
				}
				for (int j = 0; j < align.alignment_r[i]; j++)
				{
					rpos++;
					rerr[rpos] = true;
					ralign[rpos] = shpos;
				}
			} else if (sym == 'I')
			{
				hpos++;
				herr[hpos] = true;
				halign[hpos] = rpos;
			} else if (sym == 'D')
			{
				rpos++;
				rerr[rpos] = true;
				ralign[rpos] = hpos;
			} else
			{
				System.err.print("Error!  Invalid mini align sequence " + sym + " at pos " + i + "\n");
				System.exit(-1);
			}
		}
	}
	private Object[] CalcBestShift(Comparable<String>[] cur, Comparable<String>[] hyp, Comparable<String>[] ref, TERalignment med_align)
	{
		/*
		 * return null if no good shift is found or return Object[ TERshift
		 * bestShift, TERalignment cur_align ]
		 */
		Object[] to_return = new Object[2];
		boolean anygain = false;
		// Arrays that records which hyp and ref words are currently wrong
		boolean[] herr = new boolean[hyp.length];
		boolean[] rerr = new boolean[ref.length];
		// Array that records the alignment between ref and hyp
		int[] ralign = new int[ref.length];
		int[] halign = new int[hyp.length];
		FindAlignErr(med_align, herr, rerr, ralign, halign);
		TERshift[][] poss_shifts;
		// if ((Boolean) TERpara.para().get(TERpara.OPTIONS.RELAX_SHIFTS)) {
		// poss_shifts = GatherAllPossRelShifts(cur, ref, rloc, med_align, herr,
		// rerr, ralign);
		// } else {
		poss_shifts = gather_poss_shifts(cur, ref, herr, rerr, ralign, halign);
		// poss_shifts = GatherAllPossShifts(cur, ref, rloc, med_align, herr,
		// rerr, ralign);
		// }
		double curerr = med_align.numEdits;
		if (DEBUG)
		{
			System.out.println("Possible Shifts:");
			for (int i = poss_shifts.length - 1; i >= 0; i--)
			{
				for (int j = 0; j < poss_shifts[i].length; j++)
				{
					System.out.println(" [" + i + "] " + poss_shifts[i][j]);
				}
			}
			System.out.println("");
		}
		double cur_best_shift_cost = 0.0;
		TERalignment cur_best_align = med_align;
		TERshift cur_best_shift = new TERshift();
		for (int i = poss_shifts.length - 1; i >= 0; i--)
		{
			if (DEBUG)
				System.out.println("Considering shift of length " + i + " (" + poss_shifts[i].length + ")");
			/* Consider shifts of length i+1 */
			double curfix = curerr - (cur_best_shift_cost + cur_best_align.numEdits);
			double maxfix = (2 * (1 + i));
			if ((curfix > maxfix) || ((cur_best_shift_cost != 0) && (curfix == maxfix)))
			{
				break;
			}
			for (int s = 0; s < poss_shifts[i].length; s++)
			{
				curfix = curerr - (cur_best_shift_cost + cur_best_align.numEdits);
				if ((curfix > maxfix) || ((cur_best_shift_cost != 0) && (curfix == maxfix)))
				{
					break;
				}
				TERshift curshift = poss_shifts[i][s];
				Comparable<String>[] shiftarr = PerformShift(cur, curshift);
				TERalignment curalign = MinEditDist(shiftarr, ref);
				curalign.hyp = hyp;
				curalign.ref = ref;
				curalign.aftershift = shiftarr;
				double gain = (cur_best_align.numEdits + cur_best_shift_cost) - (curalign.numEdits + curshift.cost);
				if (DEBUG)
				{
					System.out.println("Gain for " + curshift + " is " + gain + ". (result: ["
							+ TERutilities.join(" ", shiftarr) + "]");
					System.out.println("" + curalign + "\n");
				}
				if ((gain > 0) || ((cur_best_shift_cost == 0) && (gain == 0)))
				{
					anygain = true;
					cur_best_shift = curshift;
					cur_best_shift_cost = curshift.cost;
					cur_best_align = curalign;
					if (DEBUG)
						System.out.println("Tmp Choosing shift: " + cur_best_shift + " gives:\n" + cur_best_align
								+ "\n");
				}
			}
		}
		if (anygain)
		{
			to_return[0] = cur_best_shift;
			to_return[1] = cur_best_align;
			return to_return;
		} else
		{
			if (DEBUG)
				System.out.println("No good shift found.\n");
			return null;
		}
	}
	
	public Comparable<String>[] PerformShift(Comparable<String>[] words, TERshift s)
	{
		return PerformShift(words, s.start, s.end, s.newloc);
	}
	public float[] PerformShift(float[] scores, TERshift s)
	{
		return PerformShift(scores, s.start, s.end, s.newloc);
	}
	public static Object[] PerformShiftNS(Object[] words, TERshift s)
	{
		int c = 0;
		Object[] nwords = words.clone();
		int start = s.start;
		int end = s.end;
		int newloc = s.newloc;
		if (newloc == -1)
		{
			for (int i = start; i <= end; i++)
				nwords[c++] = words[i];
			for (int i = 0; i <= start - 1; i++)
				nwords[c++] = words[i];
			for (int i = end + 1; i < words.length; i++)
				nwords[c++] = words[i];
		} else if (newloc < start)
		{
			for (int i = 0; i <= newloc; i++)
				nwords[c++] = words[i];
			for (int i = start; i <= end; i++)
				nwords[c++] = words[i];
			for (int i = newloc + 1; i <= start - 1; i++)
				nwords[c++] = words[i];
			for (int i = end + 1; i < words.length; i++)
				nwords[c++] = words[i];
		} else if (newloc > end)
		{
			for (int i = 0; i <= start - 1; i++)
				nwords[c++] = words[i];
			for (int i = end + 1; i <= newloc; i++)
				nwords[c++] = words[i];
			for (int i = start; i <= end; i++)
				nwords[c++] = words[i];
			for (int i = newloc + 1; i < words.length; i++)
				nwords[c++] = words[i];
		} else
		{
			// we are moving inside of ourselves
			for (int i = 0; i <= start - 1; i++)
				nwords[c++] = words[i];
			for (int i = end + 1; (i < words.length) && (i <= (end + (newloc - start))); i++)
				nwords[c++] = words[i];
			for (int i = start; i <= end; i++)
				nwords[c++] = words[i];
			for (int i = (end + (newloc - start) + 1); i < words.length; i++)
				nwords[c++] = words[i];
		}
		return nwords;
	}
	private Comparable<String>[] PerformShift(Comparable<String>[] words, int start, int end, int newloc)
	{
		int c = 0;
		Comparable<String>[] nwords = words.clone();
		/*if (DEBUG)
		{
			System.out.println("INFO PerformShift : word length: " + words.length);
		}*/
		if (newloc == -1)
		{
			for (int i = start; i <= end; i++)
				nwords[c++] = words[i];
			for (int i = 0; i <= start - 1; i++)
				nwords[c++] = words[i];
			for (int i = end + 1; i < words.length; i++)
				nwords[c++] = words[i];
		} else if (newloc < start)
		{
			for (int i = 0; i <= newloc; i++)
				nwords[c++] = words[i];
			for (int i = start; i <= end; i++)
				nwords[c++] = words[i];
			for (int i = newloc + 1; i <= start - 1; i++)
				nwords[c++] = words[i];
			for (int i = end + 1; i < words.length; i++)
				nwords[c++] = words[i];
		} else if (newloc > end)
		{
			for (int i = 0; i <= start - 1; i++)
				nwords[c++] = words[i];
			for (int i = end + 1; i <= newloc; i++)
				nwords[c++] = words[i];
			for (int i = start; i <= end; i++)
				nwords[c++] = words[i];
			for (int i = newloc + 1; i < words.length; i++)
				nwords[c++] = words[i];
		} else
		{
			// we are moving inside of ourselves
			for (int i = 0; i <= start - 1; i++)
				nwords[c++] = words[i];
			for (int i = end + 1; (i < words.length) && (i <= (end + (newloc - start))); i++)
				nwords[c++] = words[i];
			for (int i = start; i <= end; i++)
				nwords[c++] = words[i];
			for (int i = (end + (newloc - start) + 1); i < words.length; i++)
				nwords[c++] = words[i];
		}
		NUM_SHIFTS_CONSIDERED++;
		return nwords;
	}
	private float[] PerformShift(float[] words, int start, int end, int newloc)
	{
		int c = 0;
		float[] nwords = words.clone();
		/*if (DEBUG)
		{
			System.out.println("INFO PerformShift : word length: " + words.length);
		}*/
		if (newloc == -1)
		{
			for (int i = start; i <= end; i++)
				nwords[c++] = words[i];
			for (int i = 0; i <= start - 1; i++)
				nwords[c++] = words[i];
			for (int i = end + 1; i < words.length; i++)
				nwords[c++] = words[i];
		} else if (newloc < start)
		{
			for (int i = 0; i <= newloc; i++)
				nwords[c++] = words[i];
			for (int i = start; i <= end; i++)
				nwords[c++] = words[i];
			for (int i = newloc + 1; i <= start - 1; i++)
				nwords[c++] = words[i];
			for (int i = end + 1; i < words.length; i++)
				nwords[c++] = words[i];
		} else if (newloc > end)
		{
			for (int i = 0; i <= start - 1; i++)
				nwords[c++] = words[i];
			for (int i = end + 1; i <= newloc; i++)
				nwords[c++] = words[i];
			for (int i = start; i <= end; i++)
				nwords[c++] = words[i];
			for (int i = newloc + 1; i < words.length; i++)
				nwords[c++] = words[i];
		} else
		{
			// we are moving inside of ourselves
			for (int i = 0; i <= start - 1; i++)
				nwords[c++] = words[i];
			for (int i = end + 1; (i < words.length) && (i <= (end + (newloc - start))); i++)
				nwords[c++] = words[i];
			for (int i = start; i <= end; i++)
				nwords[c++] = words[i];
			for (int i = (end + (newloc - start) + 1); i < words.length; i++)
				nwords[c++] = words[i];
		}
		NUM_SHIFTS_CONSIDERED++;
		return nwords;
	}
	protected TERalignment MinEditDist(Comparable<String>[] hyp, Comparable<String>[] ref)
	{
		//System.err.println("BEGIN MinEditDist - no CN");
		double current_best = INF;
		double last_best = INF;
		int first_good = 0;
		int current_first_good = 0;
		int last_good = -1;
		int cur_last_good = 0;
		int last_peak = 0;
		int cur_last_peak = 0;
		int i, j;
		double cost, icost, dcost;
		double score;
		int hwsize = hyp.length - 1;
		int rwsize = ref.length - 1;
		NUM_BEAM_SEARCH_CALLS++;
		PhraseTable pt = null;
		if (use_phrases)
		{
			pt = costfunc.getPhraseTable();
		}
		if ((ref.length + 1 > S.length) || (hyp.length + 1 > S.length))
		{
			int max = (hyp.length > ref.length) ? hyp.length : ref.length;
			max += 26; // we only need a +1 here, but let's pad for future use
			S = new double[max][max];
			P = new char[max][max];
			PL = new PhraseTree.OffsetScore[max][max];
		}
		for (i = 0; i <= ref.length; i++)
		{
			for (j = 0; j <= hyp.length; j++)
			{
				S[i][j] = -1.0;
				P[i][j] = '0';
				PL[i][j] = null;
			}
		}
		S[0][0] = 0.0;
		for (j = 0; j <= hyp.length; j++)
		{
			last_best = current_best;
			current_best = INF;
			first_good = current_first_good;
			current_first_good = -1;
			last_good = cur_last_good;
			cur_last_good = -1;
			last_peak = cur_last_peak;
			cur_last_peak = 0;
			for (i = first_good; i <= ref.length; i++)
			{
				if ((j != hyp.length) && (i > last_good))
					break;
				if (S[i][j] < 0)
					continue;
				score = S[i][j];
				if ((j < hyp.length) && (score > last_best + BEAM_WIDTH))
					continue;
				if (current_first_good == -1)
					current_first_good = i;
				if ((i < ref.length) && (j < hyp.length))
				{
					if (ref[i].equals(hyp[j]))
					{
						cost = costfunc.match_cost(hyp[j], ref[i]) + score;
						if ((S[i + 1][j + 1] == -1) || (cost < S[i + 1][j + 1]))
						{
							S[i + 1][j + 1] = cost;
							P[i + 1][j + 1] = ' ';
						}
						if (cost < current_best)
							current_best = cost;
						if (current_best == cost)
							cur_last_peak = i + 1;
					} else
					{
						boolean are_stems = false;
						boolean are_syns = false;
						if (use_porter)
							are_stems = porter.equivStems(hyp[j], ref[i]);
						if (use_wordnet)
							are_syns = wordnet.areSynonyms(hyp[j], ref[i]);
						cost = costfunc.substitute_cost(hyp[j], ref[i]) + score;
						if ((S[i + 1][j + 1] < 0) || (cost < S[i + 1][j + 1]))
						{
							S[i + 1][j + 1] = cost;
							P[i + 1][j + 1] = 'S';
							if (cost < current_best)
								current_best = cost;
							if (current_best == cost)
								cur_last_peak = i + 1;
						}
						if (are_stems)
						{
							cost = costfunc.stem_cost(hyp[j], ref[i]) + score;
							if ((S[i + 1][j + 1] < 0) || (cost < S[i + 1][j + 1]))
							{
								S[i + 1][j + 1] = cost;
								P[i + 1][j + 1] = 'T';
								if (cost < current_best)
									current_best = cost;
								if (current_best == cost)
									cur_last_peak = i + 1;
							}
						}
						if (are_syns)
						{
							cost = costfunc.syn_cost(hyp[j], ref[i]) + score;
							if ((S[i + 1][j + 1] < 0) || (cost < S[i + 1][j + 1]))
							{
								S[i + 1][j + 1] = cost;
								P[i + 1][j + 1] = 'Y';
								if (cost < current_best)
									current_best = cost;
								if (current_best == cost)
									cur_last_peak = i + 1;
							}
						}
					}
				}
				cur_last_good = i + 1;
				if (pt != null)
				{
					ArrayList phrases = pt.retrieve_all(ref, i, hyp, j);
					for (int pi = 0; pi < phrases.size(); pi++)
					{
						PhraseTree.OffsetScore ph = (PhraseTree.OffsetScore) phrases.get(pi);
						cost = score + ph.cost;
						if ((S[i + ph.ref_len][j + ph.hyp_len] < 0) || (cost < S[i + ph.ref_len][j + ph.hyp_len]))
						{
							S[i + ph.ref_len][j + ph.hyp_len] = cost;
							P[i + ph.ref_len][j + ph.hyp_len] = 'P';
							PL[i + ph.ref_len][j + ph.hyp_len] = ph;
						}
						// System.out.println(" At i=" + i + " j=" + j +
						// " found phrase: "+ ph +"\n");
					}
				}
				if (j < hyp.length)
				{
					icost = score + costfunc.insert_cost(hyp[j]);
					if ((S[i][j + 1] < 0) || (S[i][j + 1] > icost))
					{
						S[i][j + 1] = icost;
						P[i][j + 1] = 'I';
						if ((cur_last_peak < i) && (current_best == icost))
							cur_last_peak = i;
					}
				}
				if (i < ref.length)
				{
					dcost = score + costfunc.delete_cost(ref[i]);
					if ((S[i + 1][j] < 0.0) || (S[i + 1][j] > dcost))
					{
						S[i + 1][j] = dcost;
						P[i + 1][j] = 'D';
						if (i >= last_good)
							last_good = i + 1;
					}
				}
			}
		}
		int tracelength = 0;
		i = ref.length;
		j = hyp.length;
		while ((i > 0) || (j > 0))
		{
			tracelength++;
			if (P[i][j] == ' ')
			{
				i--;
				j--;
			} else if ((P[i][j] == 'S') || (P[i][j] == 'Y') || (P[i][j] == 'T'))
			{
				i--;
				j--;
			} else if (P[i][j] == 'D')
			{
				i--;
			} else if (P[i][j] == 'I')
			{
				j--;
			} else if (P[i][j] == 'P')
			{
				PhraseTree.OffsetScore os = PL[i][j];
				i -= os.ref_len;
				j -= os.hyp_len;
			} else
			{
				System.out.println("Invalid path: P[" + i + "][" + j + "] = " + P[i][j]);
				System.out.println("Ref Len=" + ref.length + " Hyp Len=" + hyp.length + " TraceLength=" + tracelength);
				System.exit(-1);
			}
		}
		char[] path = new char[tracelength];
		int[] r_path = new int[tracelength];
		int[] h_path = new int[tracelength];
		double[] cost_path = new double[tracelength];
		i = ref.length;
		j = hyp.length;
		while ((i > 0) || (j > 0))
		{
			path[--tracelength] = P[i][j];
			cost_path[tracelength] = S[i][j];
			if (P[i][j] == ' ')
			{
				i--;
				j--;
				r_path[tracelength] = 1;
				h_path[tracelength] = 1;
			} else if ((P[i][j] == 'S') || (P[i][j] == 'T') || (P[i][j] == 'Y'))
			{
				i--;
				j--;
				r_path[tracelength] = 1;
				h_path[tracelength] = 1;
			} else if (P[i][j] == 'D')
			{
				i--;
				r_path[tracelength] = 1;
				h_path[tracelength] = 0;
			} else if (P[i][j] == 'I')
			{
				j--;
				r_path[tracelength] = 0;
				h_path[tracelength] = 1;
			} else if (P[i][j] == 'P')
			{
				PhraseTree.OffsetScore os = PL[i][j];
				i -= os.ref_len;
				j -= os.hyp_len;
				r_path[tracelength] = os.ref_len;
				h_path[tracelength] = os.hyp_len;
			}
			cost_path[tracelength] -= S[i][j];
		}
		TERalignment to_return = new TERalignment(costfunc, params);
		to_return.numWords = ref.length;
		to_return.alignment = path;
		to_return.alignment_h = h_path;
		to_return.alignment_r = r_path;
		to_return.alignment_cost = cost_path;
		to_return.numEdits = S[ref.length][hyp.length];
		//System.err.println("END MinEditDist - no CN");
		return to_return;
	}
	/*private TERalignmentCN MinEditDistcn(Comparable<String>[] hyp, ArrayList<ArrayList<Comparable<String>>> cn, Comparable<String>[] ref)
	{
		//System.err.println("BEGIN MinEditDistcn - with CN");
		double current_best = INF;
		double last_best = INF;
		int first_good = 0;
		int current_first_good = 0;
		int last_good = -1;
		int cur_last_good = 0;
		//int last_peak = 0;
		int cur_last_peak = 0;
		int i, j;
		double cost, icost, dcost;
		double score;
		//int hwsize = hyp.length - 1;
		//int rwsize = cn.size() - 1;
		NUM_BEAM_SEARCH_CALLS++;
		PhraseTable pt = null;
		if (use_phrases)
		{
			pt = costfunc.getPhraseTable();
		}
		if ((cn.size() + 1 > S.length) || (hyp.length + 1 > S.length))
		{
			int max = (hyp.length > cn.size()) ? hyp.length : cn.size();
			max += 26; // we only need a +1 here, but let's pad for future use
			S = new double[max][max];
			P = new char[max][max];
			PL = new PhraseTree.OffsetScore[max][max];
		}
		for (i = 0; i <= cn.size(); i++)
		{
			for (j = 0; j <= hyp.length; j++)
			{
				S[i][j] = -1.0;
				P[i][j] = '0';
				PL[i][j] = null;
			}
		}
		S[0][0] = 0.0;
		for (j = 0; j <= hyp.length; j++)
		{
			last_best = current_best;
			current_best = INF;
			first_good = current_first_good;
			current_first_good = -1;
			last_good = cur_last_good;
			cur_last_good = -1;
			//last_peak = cur_last_peak;
			cur_last_peak = 0;
			for (i = first_good; i <= cn.size(); i++)
			{
				if ((j != hyp.length) && (i > last_good))
					break;
				if (S[i][j] < 0)
					continue;
				score = S[i][j];
				if ((j < hyp.length) && (score > last_best + BEAM_WIDTH))
					continue;
				if (current_first_good == -1)
					current_first_good = i;
				if ((i < cn.size()) && (j < hyp.length))
				{
					// if (ref[i].equals(hyp[j]))
					int pos = contains(cn.get(i), hyp[j]);
					//int pos = containsIgnoreCase(cn.get(i), hyp[j]);
					if (pos != -1)
					{
						cost = costfunc.match_cost(hyp[j], cn.get(i).get(pos)) + score;
						if ((S[i + 1][j + 1] == -1) || (cost < S[i + 1][j + 1]))
						{
							S[i + 1][j + 1] = cost;
							P[i + 1][j + 1] = ' ';
						}
						if (cost < current_best)
							current_best = cost;
						if (current_best == cost)
							cur_last_peak = i + 1;
					} else
					{
						boolean are_stems = false;
						boolean are_syns = false;
						if (use_porter)
							are_stems = porter.equivStems(hyp[j], cn.get(i));
						if (use_wordnet)
							are_syns = wordnet.areSynonyms(hyp[j], cn.get(i));
						cost = costfunc.substitute_cost(hyp[j], cn.get(i)) + score;
						if ((S[i + 1][j + 1] < 0) || (cost < S[i + 1][j + 1]))
						{
							S[i + 1][j + 1] = cost;
							P[i + 1][j + 1] = 'S';
							if (cost < current_best)
								current_best = cost;
							if (current_best == cost)
								cur_last_peak = i + 1;
						}
						if (are_stems)
						{
							cost = costfunc.stem_cost(hyp[j], cn.get(i)) + score;
							if ((S[i + 1][j + 1] < 0) || (cost < S[i + 1][j + 1]))
							{
								S[i + 1][j + 1] = cost;
								P[i + 1][j + 1] = 'T';
								if (cost < current_best)
									current_best = cost;
								if (current_best == cost)
									cur_last_peak = i + 1;
							}
						}
						if (are_syns)
						{
							cost = costfunc.syn_cost(hyp[j], cn.get(i)) + score;
							if ((S[i + 1][j + 1] < 0) || (cost < S[i + 1][j + 1]))
							{
								S[i + 1][j + 1] = cost;
								P[i + 1][j + 1] = 'Y';
								if (cost < current_best)
									current_best = cost;
								if (current_best == cost)
									cur_last_peak = i + 1;
							}
						}
					}// else if ref[i] == hyp[j]
				}// if ((i < align.cn.length) && (j < hyp.length))
				cur_last_good = i + 1;
				if (pt != null)
				{
					ArrayList phrases = pt.retrieve_all(cn, i, hyp, j);
					for (int pi = 0; pi < phrases.size(); pi++)
					{
						PhraseTree.OffsetScore ph = (PhraseTree.OffsetScore) phrases.get(pi);
						cost = score + ph.cost;
						if ((S[i + ph.ref_len][j + ph.hyp_len] < 0) || (cost < S[i + ph.ref_len][j + ph.hyp_len]))
						{
							S[i + ph.ref_len][j + ph.hyp_len] = cost;
							P[i + ph.ref_len][j + ph.hyp_len] = 'P';
							PL[i + ph.ref_len][j + ph.hyp_len] = ph;
						}
						// System.out.println(" At i=" + i + " j=" + j +
						// " found phrase: "+ ph +"\n");
					}
				}
				if (j < hyp.length)
				{
					icost = score + costfunc.insert_cost(hyp[j]);
					if ((S[i][j + 1] < 0) || (S[i][j + 1] > icost))
					{
						S[i][j + 1] = icost;
						P[i][j + 1] = 'I';
						if ((cur_last_peak < i) && (current_best == icost))
							cur_last_peak = i;
					}
				}
				if (i < cn.size())
				{
					dcost = score + costfunc.delete_cost(cn.get(i));
					if ((S[i + 1][j] < 0.0) || (S[i + 1][j] > dcost))
					{
						S[i + 1][j] = dcost;
						P[i + 1][j] = 'D';
						if (i >= last_good)
							last_good = i + 1;
					}
				}
			}
		}
		int tracelength = 0;
		i = cn.size();
		j = hyp.length;
		while ((i > 0) || (j > 0))
		{
			tracelength++;
			if (P[i][j] == ' ')
			{
				i--;
				j--;
			} else if ((P[i][j] == 'S') || (P[i][j] == 'Y') || (P[i][j] == 'T'))
			{
				i--;
				j--;
			} else if (P[i][j] == 'D')
			{
				i--;
			} else if (P[i][j] == 'I')
			{
				j--;
			} else if (P[i][j] == 'P')
			{
				PhraseTree.OffsetScore os = PL[i][j];
				i -= os.ref_len;
				j -= os.hyp_len;
			} else
			{
				System.out.println("Invalid path: P[" + i + "][" + j + "] = " + P[i][j]);
				System.out.println("Ref Len=" + cn.size() + " Hyp Len=" + hyp.length + " TraceLength=" + tracelength);
				System.exit(-1);
			}
		}
		char[] path = new char[tracelength];
		int[] r_path = new int[tracelength];
		int[] h_path = new int[tracelength];
		double[] cost_path = new double[tracelength];
		i = cn.size();
		j = hyp.length;
		while ((i > 0) || (j > 0))
		{
			path[--tracelength] = P[i][j];
			cost_path[tracelength] = S[i][j];
			if (P[i][j] == ' ')
			{
				i--;
				j--;
				r_path[tracelength] = 1;
				h_path[tracelength] = 1;
			} else if ((P[i][j] == 'S') || (P[i][j] == 'T') || (P[i][j] == 'Y'))
			{
				i--;
				j--;
				r_path[tracelength] = 1;
				h_path[tracelength] = 1;
			} else if (P[i][j] == 'D')
			{
				i--;
				r_path[tracelength] = 1;
				h_path[tracelength] = 0;
			} else if (P[i][j] == 'I')
			{
				j--;
				r_path[tracelength] = 0;
				h_path[tracelength] = 1;
			} else if (P[i][j] == 'P')
			{
				PhraseTree.OffsetScore os = PL[i][j];
				i -= os.ref_len;
				j -= os.hyp_len;
				r_path[tracelength] = os.ref_len;
				h_path[tracelength] = os.hyp_len;
			}
			cost_path[tracelength] -= S[i][j];
		}
		TERalignmentCN to_return = new TERalignmentCN(costfunc, params);
		to_return.numWords = cn.size();
		to_return.alignment = path;
		to_return.alignment_h = h_path;
		to_return.alignment_r = r_path;
		to_return.alignment_cost = cost_path;
		to_return.numEdits = S[cn.size()][hyp.length];
		to_return.cn = cn;
		to_return.ref = ref;
		//System.err.println("END MinEditDistcn - with CN");
		return to_return;
	}*/
	private int contains(ArrayList<Comparable<String>> ref, Comparable<String> hyp)
	{
		if(params.para().get_boolean(TERpara.OPTIONS.CASEON))
		{
			for (int i = 0; i < ref.size(); i++)
				if (ref.get(i).equals(hyp))
					return i;
		}
		else
		{
			for (int i = 0; i < ref.size(); i++)
				if (((String)ref.get(i)).equalsIgnoreCase((String)hyp))
					return i;
		}
		return -1;
	}
	// Accessor functions to some internal counters 
	public int numBeamCalls()
	{
		return NUM_BEAM_SEARCH_CALLS;
	}
	public int numSegsScored()
	{
		return NUM_SEGMENTS_SCORED;
	}
	public int numShiftsTried()
	{
		return NUM_SHIFTS_CONSIDERED;
	}
	public String get_info()
	{
		String s = ("TER Calculation\n" + " Stemming Used: " + use_porter + "\n" + " Synonyms Used: " + use_wordnet
				+ "\n" + " Beam Width: " + BEAM_WIDTH + "\n" + " Max Shift Size: " + MAX_SHIFT_SIZE + "\n"
				+ " Max Shift Dist: " + MAX_SHIFT_DIST + "\n" + " Shifting Constraint: " + shift_con + "\n"
				+ " Number of Shifting Stop Words: " + shift_stop_words.size() + "\n" + " Size of MinEditDist Matrix: "
				+ S.length + "x" + S.length + "\n" + " Number of Segments Scored: " + NUM_SEGMENTS_SCORED + "\n"
				+ " Number of Shifts Considered: " + NUM_SHIFTS_CONSIDERED + "\n" + " Number of Calls to Beam Search: "
				+ NUM_BEAM_SEARCH_CALLS + "\n" + " Total Calc Time: " + String.format("%.3f sec\n", getCalcTime()) + costfunc
				.get_info());
		return s;
	}
	public void loadShiftStopWordList(String fname)
	{
		if ((fname == null) || fname.equals(""))
		{
			shift_stop_words = new HashSet<Comparable<String>>();
			return;
		}
		try
		{
			BufferedReader fh = new BufferedReader(new FileReader(fname));
			shift_stop_words = new HashSet<Comparable<String>>();
			String line;
			while ((line = fh.readLine()) != null)
			{
				line = line.trim();
				if (line.length() == 0)
					continue;
				String[] wds = NormalizeText.process(line);
				Comparable<String>[] nswds = costfunc.process_input_hyp(wds);
				for (int i = 0; i < nswds.length; i++)
				{
					shift_stop_words.add(nswds[i]);
				}
			}
			fh.close();
		} catch (IOException ioe)
		{
			System.out.println("Loading shift stop word list from " + fname + ": " + ioe);
			System.exit(-1);
		}
	}
	public void setShiftCon(String sc)
	{
		if (sc.equals("exact"))
		{
			shift_con = SHIFT_CON.EXACT;
		} else if (sc.equals("relax_nophrase"))
		{
			shift_con = SHIFT_CON.RELAX_NP;
		} else if (sc.equals("relax"))
		{
			shift_con = SHIFT_CON.RELAX;
		} else if (sc.equals("allow_mismatch"))
		{
			shift_con = SHIFT_CON.ALLOW_MISMATCH;
		} else if (sc.equals("noshift"))
		{
			shift_con = SHIFT_CON.NOSHIFT;
		} else
		{
			System.err.println("Invalid shift constraint: " + sc + "\n"
					+ "  valid constraints are: noshift, exact, relax_nophrase, relax and allow_mismatch");
			System.exit(-1);
		}
	}
	
	public TERalignmentCN getFakeTERAlignmentCN()
	{
		TERalignmentCN to_return = new TERalignmentCN(costfunc, params);
		to_return.numWords = 0;
		to_return.alignment = null;
		to_return.alignment_h = null;
		to_return.alignment_r = null;
		to_return.alignment_cost = null;
		to_return.numEdits = 0.0;
		to_return.cn = null;
		to_return.ref = null;
		return to_return;
	}  
	

}
