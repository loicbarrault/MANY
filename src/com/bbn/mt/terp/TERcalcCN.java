package com.bbn.mt.terp;
/*
 * Copyright 2009 Loic BARRAULT.  
 * All Rights Reserved.  Use is subject to license terms.
 * 
 * See the file "LICENSE.txt" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL 
 * WARRANTIES.
 *
 */
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class TERcalcCN extends TERcalc
{
	public TERcalcCN(TERcost costfunc, TERpara params)
	{
		super(costfunc, params);
	}
	// Turn on if you want a lot of debugging info.
	static final private boolean DEBUG = false;

	private TERshift _bestShift = null;
	private TERalignmentCN _bestAlign = null;

	public TERalignmentCN TERcn(List<String[]> hyps, List<float[]> hyps_scores, String[] ref, float[] ref_scores)
	{
		// System.out.println("START TERcn");
		// Run TER on hyp and each ref (both should already be tokenized),
		// and return an array of TERalignment[]
		TERalignmentCN align = null;
		boolean first = true;
		ArrayList<ArrayList<Comparable<String>>> cn = costfunc.process_input_cn(ref);
		ArrayList<ArrayList<Float>> cn_scores = costfunc.process_input_cn(ref_scores);

		// compute MinEditDistance from each hyp to the ref and order the
		// hypotheses with the TER score
		if (DEBUG)
		{
			System.err.println("TERcalcCN::TERcn : Reference is : " + TERutilities.join(" ", ref));
		}

		ArrayList<TERutilities.Index_Score> idxScores = new ArrayList<TERutilities.Index_Score>();

		int[] hyps_idx = params.para().get_integerlist(TERpara.OPTIONS.HYP_IDX);

		for (int i = 0; i < hyps_idx.length; i++)
		{
			if (DEBUG)
				System.err.println("Eval of hyp #" + i + " : " + TERutilities.join(" ", hyps.get(i)) + " -> ");
			Comparable<String>[] pphyp = costfunc.process_input_hyp(hyps.get(i));
			Comparable<String>[] ppref = costfunc.process_input_ref(ref);
			TERalignment cur_align = MinEditDist(pphyp, ppref);

			idxScores.add(new TERutilities.Index_Score(i, cur_align.score()));

			if (DEBUG)
				System.err.println(" -> Score : " + idxScores.get(i).score);
		}
		Collections.sort(idxScores);

		for (int i = 0; i < idxScores.size(); i++)
		{
			int hyp_idx = hyps_idx[idxScores.get(i).index];
			if (DEBUG)
				System.err.println("Incremental add of hyp #" + hyp_idx + " : "
						+ TERutilities.join(" ", hyps.get(idxScores.get(i).index)));
			Comparable<String>[] pphyp = costfunc.process_input_hyp(hyps.get(idxScores.get(i).index));
			if (cn.size() == 0)
			{
				if (DEBUG)
					System.err.println("Size of CN is 0");
				continue;
			}
			else if (pphyp.length == 0)
			{
				if (DEBUG)
					System.err.println("Length of hypothesis is 0");
				continue;
			}
			else
			{
				if (first)
				{
					if (params.para().get_boolean(TERpara.OPTIONS.TRY_ALL_SHIFTS))
					{
						align = TERppcn_allshifts(pphyp, cn, ref);
					}
					else
					{
						align = TERppcn(pphyp, hyps_scores.get(idxScores.get(i).index), cn, cn_scores, ref, ref_scores);
					}
				}
				else
				{
					if (params.para().get_boolean(TERpara.OPTIONS.TRY_ALL_SHIFTS))
					{
						align = TERppcn_allshifts(pphyp, align.cn, ref);
					}
					else
					{
						align = TERppcn(pphyp, hyps_scores.get(idxScores.get(i).index), align, ref, ref_scores);
					}
				}
				align.ref_idx = params.para().get_integer(TERpara.OPTIONS.REF_IDX);
				align.buildCN(hyp_idx);

				first = false;
			}
			if (ref_len >= 0)
				align.numWords = ref_len;

			if (DEBUG)
			{
				System.out.println("----------------- Le CN : ");
				System.out.println(align.toCNString());
			}
		}
		if (DEBUG)
		{
			System.err.println("******** TERcn : The final BEST alignment : ");
			if (align != null)
				System.err.println(align.toCNString());
			else
				System.err.println("!!!! CN is empty !!!!");
		}
		return align;
	}

	private Hashtable<String, Boolean> encounteredHyp = new Hashtable<String, Boolean>();

	// Run TER on preprocessed segment pair
	public TERalignmentCN TERppcn_allshifts(Comparable<String>[] hyp, ArrayList<ArrayList<Comparable<String>>> cn,
			Comparable<String>[] ref)
	{
		// System.out.println("START TERppcn");
		/* Calculates the TER score for the hyp/ref pair */
		/*
		 * boolean first = true; System.out.print("HYP = "); for
		 * (Comparable<String> s : hyp) { if (!first) System.out.print(" ");
		 * System.out.print((String) s); first = false; } System.out.println();
		 * first = false; System.out.print("REF = "); for (Comparable<String> s
		 * : ref) { if (!first) System.out.print(" "); System.out.print((String)
		 * s); first = false; } System.out.println(); if (cn != null) {
		 * System.out.println("Le CN : ");
		 * System.out.println(TERalignmentCN.toCNString(cn)); }
		 */

		long startTime = System.nanoTime();
		TERalignmentCN cur_align = MinEditDistcn(hyp, cn); // MinEditDistcn(hyp,
															// cn, ref);
		cur_align.hyp = hyp;
		cur_align.aftershift = hyp;
		cur_align.ref = ref;
		cur_align.cn = cn;

		_bestAlign = cur_align;
		encounteredHyp.clear();
		encounteredHyp.put(TERutilities.join("", hyp), true);

		ArrayList<TERalignmentCN> aligns = new ArrayList<TERalignmentCN>();
		aligns.add(cur_align);

		// Comparable[] cur = hyp;
		ArrayList<Comparable<String>[]> cur = new ArrayList<Comparable<String>[]>();
		cur.add(hyp);

		// double edits = 0;
		// int numshifts = 0;
		// ArrayList allshifts = new ArrayList(hyp.length + cn.size());
		if (DEBUG)
			System.out.println("Initial Alignment:\n" + cur_align + "\n");
		int nbIt = 0;

		while (cur.isEmpty() == false) // while we have a shifts to perform ...
		{
			if (DEBUG)
				System.out.println("******** Looking for good shift, iteration #" + nbIt);
			nbIt++;
			CalcAllShiftcn(cur, hyp, cn, aligns, ref);

			if (_bestShift != null)
			{
				System.out.println("******** Best alignment so far : " + _bestShift + "\n************");
				_bestShift.alignment = _bestAlign.alignment;
				_bestShift.aftershift = _bestAlign.aftershift;
			}
		}

		// bestAlign is the best alignment !!
		if (DEBUG)
		{
			System.out.println("******** Found the BEST alignment : ");
			boolean first = true;
			for (Comparable<String> s : _bestAlign.aftershift)
			{
				if (!first)
					System.out.print(" ");
				System.out.print((String) s);
				first = false;
			}
			System.out.println("\n************");
		}
		TERalignmentCN to_return = cur_align;
		// to_return.allshifts = (TERshift[]) allshifts.toArray(new
		// TERshift[0]);
		to_return.numEdits = _bestAlign.numEdits;
		to_return.addHyp(hyp);
		to_return.ref = ref;
		to_return.aftershift = _bestAlign.aftershift;
		NUM_SEGMENTS_SCORED++;
		long endTime = System.nanoTime();
		this.calcTime += endTime - startTime;
		// System.err.println("END TERppcn");
		return to_return;
	}

	public TERalignmentCN TERppcn(Comparable<String>[] hyp, float[] hyp_scores, TERalignmentCN align,
			Comparable<String>[] ref, float[] ref_scores)
	{
		TERalignmentCN al = TERppcn(hyp, hyp_scores, align.cn, align.cn_scores, ref, ref_scores);
		al.full_cn = align.full_cn;
		al.full_cn_scores = align.full_cn_scores;
		al.full_cn_sys = align.full_cn_sys;
		return al;
	}

	public TERalignmentCN TERppcn(Comparable<String>[] hyp, float[] hyp_scores,
			ArrayList<ArrayList<Comparable<String>>> cn, ArrayList<ArrayList<Float>> cn_scores,
			Comparable<String>[] ref, float[] ref_scores)
	{
		// System.err.println("TERcalcCN::TERppcn : START");
		// Calculates the TER score for the hyp/ref pair
		/*
		 * if (DEBUG) { boolean first = true; System.err.print("HYP = "); for
		 * (Comparable<String> s : hyp) { if (!first) System.err.print(" ");
		 * System.err.print((String) s); first = false; } System.err.println();
		 * first = false; System.err.print("REF = "); for (Comparable<String> s
		 * : ref) { if (!first) System.err.print(" "); System.err.print((String)
		 * s); first = false; } System.err.println(); if (cn != null) {
		 * System.err.println("Le CN : ");
		 * System.err.println(TERutilities.toCNString(cn, cn_scores)); } }
		 */
		long startTime = System.nanoTime();
		TERalignmentCN cur_align = MinEditDistcn(hyp, cn);// MinEditDistcn(hyp,
															// cn, ref);

		float[] cur_scores = hyp_scores;
		Comparable<String>[] cur = hyp;
		cur_align.hyp = hyp;
		cur_align.aftershift = hyp;
		cur_align.aftershift_scores = hyp_scores;
		cur_align.ref = ref;
		cur_align.cn = cn;
		cur_align.cn_scores = cn_scores;

		double edits = 0;
		ArrayList<TERshift> allshifts = new ArrayList<TERshift>(hyp.length + cn.size());
		if (DEBUG)
			System.err.println("Initial Alignment:\n" + cur_align + "\n");
		int nbIt = 0;
		while (true) // while we have a good shift
		{
			if (DEBUG)
				System.err.println("******** Looking for good shift, iteration #" + nbIt);
			nbIt++;
			Object[] returns = CalcBestShiftcn(cur, hyp, hyp_scores, cn, cur_align, ref);
			if (returns == null)
			{
				break;
			}
			TERshift bestShift = (TERshift) returns[0];
			edits += bestShift.cost;
			cur_align = (TERalignmentCN) returns[1];
			if (DEBUG)
				System.err.println("******** Found new alignment : " + cur_align + "\n************");
			bestShift.alignment = cur_align.alignment;
			bestShift.aftershift = cur_align.aftershift;
			allshifts.add(bestShift);
			cur = cur_align.aftershift;
		}

		if (DEBUG)
		{
			System.err.println("******** TERppcn : Found the BEST alignment : ");
			boolean first = true;
			for (Comparable<String> s : cur)
			{
				if (!first)
					System.err.print(" ");
				System.err.print((String) s);
				first = false;
			}
			System.err.println("\n************");
		}

		TERalignmentCN to_return = cur_align;
		to_return.allshifts = allshifts.toArray(new TERshift[0]);
		to_return.numEdits += edits;
		to_return.addHyp(hyp);
		to_return.ref = ref;
		to_return.aftershift = cur;
		to_return.aftershift_scores = cur_scores;
		NUM_SEGMENTS_SCORED++;
		long endTime = System.nanoTime();
		this.calcTime += endTime - startTime;
		// System.err.println("TERcalcCN::TERppcn : END");
		return to_return;
	}

	private void _gather_exposs_shifts_cn(Comparable<String>[] hyp, ArrayList<ArrayList<Comparable<String>>> cn,
			boolean[] herr, boolean[] rerr, int[] ralign, int[] halign, Set[] allshifts, TreeMap[] paramap,
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
							}
							else if ((r_start + roff >= 0) && (r_start + roff < ralign.length)
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
			}
			else
			{
				ok = false;
			}
		}
	}
	private TERshift[][] gather_poss_shifts_cn(Comparable<String>[] hyp, ArrayList<ArrayList<Comparable<String>>> cn,
			boolean[] herr, boolean[] rerr, int[] ralign, int[] halign)
	{
		if ((shift_con == SHIFT_CON.NOSHIFT) || (MAX_SHIFT_SIZE <= 0) || (MAX_SHIFT_DIST <= 0))
		{
			TERshift[][] to_return = new TERshift[0][];
			return to_return;
		}
		Set[] allshifts = new TreeSet[MAX_SHIFT_SIZE + 1];
		for (int i = 0; i < allshifts.length; i++)
			allshifts[i] = new TreeSet();
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
						if (DEBUG)
							System.err.println("We found paraphrases : " + ph.get(0));
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
			ArrayList<TERshift[]> al = new ArrayList<TERshift[]>(allshifts[i]);
			to_return[i] = al.toArray(new TERshift[0]);
		}
		return to_return;
	}
	private Object[] CalcBestShiftcn(Comparable<String>[] cur, Comparable<String>[] hyp, float[] hyp_scores,
			ArrayList<ArrayList<Comparable<String>>> cn, TERalignmentCN align, Comparable<String>[] ref)
	{
		/*
		 * return null if no good shift is found or return Object[ TERshift
		 * bestShift, TERalignment cur_align ]
		 */
		Object[] to_return = new Object[2];
		boolean anygain = false;
		/* Arrays that records which hyp and ref words are currently wrong */
		boolean[] herr = new boolean[hyp.length];
		boolean[] rerr = new boolean[cn.size()];
		/* Array that records the alignment between ref and hyp */
		int[] ralign = new int[cn.size()];
		int[] halign = new int[hyp.length];
		FindAlignErr(align, herr, rerr, ralign, halign);
		TERshift[][] poss_shifts;
		// if ((Boolean) TERpara.para().get(TERpara.OPTIONS.RELAX_SHIFTS)) {
		// poss_shifts = GatherAllPossRelShifts(cur, ref, rloc, med_align, herr,
		// rerr, ralign);
		// } else {
		poss_shifts = gather_poss_shifts_cn(cur, cn, herr, rerr, ralign, halign);
		// poss_shifts = GatherAllPossShifts(cur, ref, rloc, med_align, herr,
		// rerr, ralign);
		// }
		if (DEBUG)
			System.err.println("Current ERR is " + align.numEdits);
		double curerr = align.numEdits;
		if (DEBUG)
		{
			System.err.println("Possible Shifts:");
			for (int i = poss_shifts.length - 1; i >= 0; i--)
			{
				for (int j = 0; j < poss_shifts[i].length; j++)
				{
					System.err.println(" [" + (i + 1) + "] " + poss_shifts[i][j]);
				}
			}
			System.err.println("");
		}
		double cur_best_shift_cost = 0.0;
		TERalignmentCN cur_best_align = align;
		TERshift cur_best_shift = new TERshift();
		for (int i = poss_shifts.length - 1; i >= 0; i--)
		{
			if (DEBUG && poss_shifts[i].length > 0)
				System.err.println("Considering shift of length " + (i + 1) + " (" + poss_shifts[i].length + ")");
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
				float[] shiftarr_scores = PerformShift(hyp_scores, curshift);

				if (DEBUG)
				{
					System.err.println("Considering shift : " + curshift + " which gives : "
							+ TERutilities.join(" ", shiftarr));
					System.err.println("Corresponding scores are : " + TERutilities.join(" ", shiftarr_scores));
				}

				TERalignmentCN curalign = MinEditDistcn(shiftarr, cn); // MinEditDistcn(shiftarr,
																		// cn,
																		// ref);
				curalign.hyp = hyp;
				curalign.ref = ref;
				curalign.cn = cn;
				curalign.cn_scores = align.cn_scores;
				curalign.aftershift = shiftarr;
				curalign.aftershift_scores = shiftarr_scores;

				if (DEBUG)
				{
					System.err.println("BEST : numEdits=" + cur_best_align.numEdits + " cost=" + cur_best_shift_cost
							+ "  CUR : numEdits=" + curalign.numEdits + " cost=" + curshift.cost);
				}
				double gain = (cur_best_align.numEdits + cur_best_shift_cost) - (curalign.numEdits + curshift.cost);
				if (DEBUG)
				{
					System.err.println("DEBUG CalcBestShiftcn : Gain for " + curshift + " is " + gain + ". (result: ["
							+ TERutilities.join(" ", shiftarr) + "], scores ["
							+ TERutilities.join(" ", shiftarr_scores) + "])");
					// System.out.println("" + curalign + "\n");
				}
				if ((gain > 0) || ((cur_best_shift_cost == 0) && (gain == 0)))
				{
					anygain = true;
					cur_best_shift = curshift;
					cur_best_shift_cost = curshift.cost;
					cur_best_align = curalign;
					if (DEBUG)
					{
						System.err.println("DEBUG CalcBestShiftcn : Choosing shift: " + cur_best_shift + " gives:\n"
								+ cur_best_align + "\n");
					}
				}
			}
		}
		if (anygain)
		{
			to_return[0] = cur_best_shift;
			to_return[1] = cur_best_align;
			return to_return;
		}
		else
		{
			if (DEBUG)
				System.err.println("No good shift found.\n");
			return null;
		}
	}

	private boolean CalcAllShiftcn(ArrayList<Comparable<String>[]> hyps, Comparable<String>[] hyp,
			ArrayList<ArrayList<Comparable<String>>> cn, ArrayList<TERalignmentCN> alignments, Comparable<String>[] ref)
	{
		// return null if no good shift is found or return Object[ TERshift
		// bestShift, TERalignment cur_align ]
		if (hyps.isEmpty())
			return false;

		if (hyps.size() != alignments.size())
		{
			System.err.println("hyps and aligns don't have same size ...");
			hyps.clear();
			return false;
		}

		ArrayList<Comparable<String>[]> current_hyps = new ArrayList<Comparable<String>[]>(hyps);
		hyps.clear();
		ArrayList<TERalignmentCN> aligns = new ArrayList<TERalignmentCN>(alignments);
		alignments.clear();

		// for (Comparable[] cur : current_hyps)
		for (int nums = 0; nums < current_hyps.size(); nums++)
		{
			Comparable<String>[] cur = current_hyps.get(nums);
			TERalignmentCN align = aligns.get(nums);

			// do not consider this hyp if it has already be seen
			if (encounteredHyp.containsKey(TERutilities.join("", cur)))
			{
				// System.err.println("Already seen hyp : "+join("",
				// cur)+" ... skipping ...");
				continue;
			}
			else
			{
				encounteredHyp.put(TERutilities.join("", cur), true);
				// System.err.println("Looking for shifts with hyp : "+join("",
				// cur));
			}
			// Arrays that records which hyp and ref words are currently wrong
			boolean[] herr = new boolean[hyp.length];
			boolean[] rerr = new boolean[cn.size()];
			// Array that records the alignment between ref and hyp
			int[] ralign = new int[cn.size()];
			int[] halign = new int[hyp.length];
			FindAlignErr(align, herr, rerr, ralign, halign);
			TERshift[][] poss_shifts;

			// otherwise, get the possible shifts and try them
			poss_shifts = gather_poss_shifts_cn(cur, cn, herr, rerr, ralign, halign);
			double curerr = align.numEdits;
			System.out.println("Current ERR is " + curerr);
			if (DEBUG)
			{
				System.out.println("Possible Shifts:");
				for (int i = poss_shifts.length - 1; i >= 0; i--)
				{
					for (int j = 0; j < poss_shifts[i].length; j++)
					{
						System.out.println(" [" + (i + 1) + "] " + poss_shifts[i][j]);
					}
				}
				System.out.println("");
			}
			double best_shift_cost = _bestShift == null ? 0 : _bestShift.cost;
			// TERalignmentCN cur_best_align = align;
			// TERshift cur_best_shift = new TERshift();
			for (int i = poss_shifts.length - 1; i >= 0; i--)
			{
				if (DEBUG && poss_shifts[i].length > 0)
					System.out.println("Considering shift of length " + (i + 1) + " (" + poss_shifts[i].length + ")");
				// Consider shifts of length i+1
				double curfix = curerr - (best_shift_cost + _bestAlign.numEdits);
				double maxfix = (2 * (1 + i));
				if ((curfix > maxfix) || ((best_shift_cost != 0) && (curfix == maxfix)))
				{
					break;
				}
				for (int s = 0; s < poss_shifts[i].length; s++)
				{
					curfix = curerr - (best_shift_cost + _bestAlign.numEdits);
					if ((curfix > maxfix) || ((best_shift_cost != 0) && (curfix == maxfix)))
					{
						break;
					}
					TERshift curshift = poss_shifts[i][s];
					Comparable<String>[] shiftarr = PerformShift(cur, curshift);
					TERalignmentCN curalign = MinEditDistcn(shiftarr, cn); // MinEditDistcn(shiftarr,
																			// cn,
																			// ref);
					curalign.hyp = hyp;
					curalign.ref = ref;
					curalign.cn = cn;
					curalign.aftershift = shiftarr;

					hyps.add(shiftarr);
					// System.err.println("Ading new hyp : "+join(" ",shiftarr));
					alignments.add(curalign);

					if (DEBUG)
					{
						System.err.println("BEST : numEdits=" + _bestAlign.numEdits + " cost=" + best_shift_cost
								+ "  CUR : numEdits=" + curalign.numEdits + " cost=" + curshift.cost);
					}

					double gain = (_bestAlign.numEdits + best_shift_cost) - (curalign.numEdits + curshift.cost);
					if (DEBUG)
					{
						System.out.println("DEBUG CalcBestShiftcn : Gain for " + curshift + " is " + gain
								+ ". (result: [" + TERutilities.join(" ", shiftarr) + "]");
						// System.out.println("" + curalign + "\n");
					}
					// if ((gain > 0) || ((cur_best_shift_cost == 0) && (gain ==
					// 0)))
					if (gain >= 0)
					{
						_bestShift = curshift;
						_bestShift.cost = curshift.cost;
						_bestAlign = curalign;
						// if (DEBUG)
						// System.out.println("DEBUG CalcBestShiftcn : Choosing shift: "
						// + cur_best_shift + " gives:\n"
						// + cur_best_align + "\n");
					}
				}
			}
		}
		return true;
	}

	public TERalignmentCN MinEditDistcn(Comparable<String>[] hyp, ArrayList<ArrayList<Comparable<String>>> cn)
	{
		if (hyp == null || cn == null)
		{
			if (DEBUG)
				System.err.println("hyp or cn is null in MinEditDist ... returning null");
			return null;
		}
		// System.err.println("BEGIN MinEditDistcn - with CN");
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
		// int hwsize = hyp.length - 1;
		// int rwsize = cn.size() - 1;
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
			last_peak = cur_last_peak;
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
					if (pos != -1)
					{
						// if(DEBUG)
						// System.err.println("MinEditDistance : CN["+i+"] contains word "+hyp[j]);
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
					}
					else
					{
						if (use_wordnet && wordnet.areSynonyms(hyp[j], cn.get(i)))
						{
							if (DEBUG)
								System.err.println("MinEditDistance : CN[" + i + "] contains SYNONYM of " + hyp[j]);
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
						else if (use_porter && porter.equivStems(hyp[j], cn.get(i)))
						{
							if (DEBUG)
								System.err.println("MinEditDistance : CN[" + i + "] contains STEM of " + hyp[j]);
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
						else
						{
							// if(DEBUG)
							// System.err.println("MinEditDistance : CN["+i+"] and "+hyp[j]+" are SUBSTITUTION");
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
						}
					}// else if ref[i] == hyp[j]
				}// if ((i < cn.size()) && (j < hyp.length))
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
						if (DEBUG)
						{
							System.err.println("MinEditDistance : CN[" + i + "]  and " + hyp[j] + " -- PHRASE TABLE");
							System.err.println("MinEditDistance : At i=" + i + " j=" + j + " found phrase: " + ph
									+ "\n");
						}
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
			}
			else if ((P[i][j] == 'S') || (P[i][j] == 'Y') || (P[i][j] == 'T'))
			{
				i--;
				j--;
			}
			else if (P[i][j] == 'D')
			{
				i--;
			}
			else if (P[i][j] == 'I')
			{
				j--;
			}
			else if (P[i][j] == 'P')
			{
				PhraseTree.OffsetScore os = PL[i][j];
				i -= os.ref_len;
				j -= os.hyp_len;
			}
			else
			{
				System.err.println("Invalid path: P[" + i + "][" + j + "] = " + P[i][j]);
				System.err.println("Ref Len=" + cn.size() + " Hyp Len=" + hyp.length + " TraceLength=" + tracelength);
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
			}
			else if ((P[i][j] == 'S') || (P[i][j] == 'T') || (P[i][j] == 'Y'))
			{
				i--;
				j--;
				r_path[tracelength] = 1;
				h_path[tracelength] = 1;
			}
			else if (P[i][j] == 'D')
			{
				i--;
				r_path[tracelength] = 1;
				h_path[tracelength] = 0;
			}
			else if (P[i][j] == 'I')
			{
				j--;
				r_path[tracelength] = 0;
				h_path[tracelength] = 1;
			}
			else if (P[i][j] == 'P')
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
		// to_return.ref = ref;
		// System.err.println("END MinEditDistcn - with CN");
		return to_return;
	}
	private int contains(ArrayList<Comparable<String>> ref, Comparable<String> hyp)
	{
		if (params.para().get_boolean(TERpara.OPTIONS.CASEON))
		{
			for (int i = 0; i < ref.size(); i++)
				if (ref.get(i).equals(hyp))
					return i;
		}
		else
		{
			for (int i = 0; i < ref.size(); i++)
				if (((String) ref.get(i)).equalsIgnoreCase((String) hyp))
					return i;
		}
		return -1;
	}

	public TERalignmentCN compute_ter(ArrayList<ArrayList<Comparable<String>>> cn, String[] ref)
	{
		// compute MinEditDistance between ref and cn
		if (DEBUG)
			System.err.println("Reference is : " + TERutilities.join(" ", ref));
		Comparable<String>[] ppref = costfunc.process_input_ref(ref);
		return MinEditDistcn(ppref, cn);
	}
}
