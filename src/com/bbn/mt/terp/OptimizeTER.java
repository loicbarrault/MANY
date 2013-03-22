package com.bbn.mt.terp;

import java.io.*;
import java.util.TreeMap;
import java.util.Map;
import java.util.Iterator;
import java.util.regex.*;
import java.util.Random;
import java.util.ArrayList;

public class OptimizeTER
{
	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.err.println("usage: OptimizeTER <param_file>");
			System.exit(-1);
		}
		TEROptPara.para().readPara(args[0]);

		System.out.println("Optimize TERp Parameters:\n" + "---------------------------------------------\n"
				+ TEROptPara.para().outputPara() + "\n\n");

		//TERpara.para().readPara(TEROptPara.para().get_string(TEROptPara.OPTIONS.TERP_PARAM));
		String[] args_terp = {"-p", "terp.params"};
		TERpara params = new TERpara(args_terp);
		params.para().readPara(TEROptPara.para().get_string(TEROptPara.OPTIONS.TERP_PARAM));
		OptimizeTER ot = new OptimizeTER();

		ot.set_hj_type(TEROptPara.para().get_string(TEROptPara.OPTIONS.HJ_TYPE));
		ot.set_opt_func(TEROptPara.para().get_string(TEROptPara.OPTIONS.OPT_FUNC));

		if (((ot.hj_type == HJ_TYPE.PREF) || (ot.opt_func == OPTIMIZE_FUNC.PREF))
				&& ((ot.hj_type != HJ_TYPE.PREF) || (ot.opt_func != OPTIMIZE_FUNC.PREF)))
		{
			System.err.println("Human judgment type and optimization function mismatch.");
			System.err
					.println("Preference judgment type can be used if and only if preference optimization function is used.");
			System.err
					.println("Score judgment type can be used if and only if a correlation optimization function is used.");
		}

		ot.load_target_judgments(TEROptPara.para().get_string(TEROptPara.OPTIONS.HJ_FILE));
		ot.load_segment_lengths(TEROptPara.para().get_string(TEROptPara.OPTIONS.SEGLEN_FILE));

		boolean loaded_counts = ot.load_counts(TEROptPara.para().get_string(TEROptPara.OPTIONS.INIT_COUNT_FILE));

		ot.perturb_init_wgts(0);

		if (TEROptPara.para().get_boolean(TEROptPara.OPTIONS.RUN_TERP))
		{
			ot.optimize(loaded_counts, false);
		}
		else if (loaded_counts)
		{
			ot.optimize(true, true);
		}
		else
		{
			System.out
					.println("ERROR: Optimization cannot be used without either using initial counts or running TERp.");
			System.exit(-1);
		}

		int pseed = TEROptPara.para().get_integer(TEROptPara.OPTIONS.PERTURB_SEED);
		ot.set_seed(pseed);

		int num_out_multi_perturb = TEROptPara.para().get_integer(TEROptPara.OPTIONS.OUTPUT_MULTI_PERTURBS);
		String out_multi_perturb_pfx = TEROptPara.para().get_string(TEROptPara.OPTIONS.OUTPUT_MULTI_PERTURB_PFX);
		if ((num_out_multi_perturb >= 0) && (out_multi_perturb_pfx != null))
		{
			for (int i = 0; i < num_out_multi_perturb; i++)
			{
				boolean no_change = (i == 0);
				double[] pwgt = ot.gen_perturb_wgts(no_change);
				output_weights(out_multi_perturb_pfx + i, "Perturbed Weights (Num=" + i + " Seed=" + pseed + "):", pwgt);
			}
		}

		ot.output(TEROptPara.para().get_string(TEROptPara.OPTIONS.OUTPUT_WEIGHTS_FILE));
	}

	public OptimizeTER()
	{
		System.out.println("Initializing TERp");
		terp.init(TEROptPara.para().get_boolean(TEROptPara.OPTIONS.RUN_TERP));
		this.MAX_ITERS = TEROptPara.para().get_integer(TEROptPara.OPTIONS.NUM_ITER);
		this.MAX_STEPS_PER_ITER = TEROptPara.para().get_integer(TEROptPara.OPTIONS.NUM_STEPS);
		int[] cf = TEROptPara.para().get_integerlist(TEROptPara.OPTIONS.CHANGE_FLAGS);
		if (cf != null)
		{
			change_wgts = new boolean[cf.length];
			for (int k = 0; k < cf.length; k++)
				change_wgts[k] = (cf[k] != 0);
		}
		double[] max_wgts = TEROptPara.para().get_doublelist(TEROptPara.OPTIONS.MAX_WGTS);
		if (max_wgts != null)
			this.max_wgt = max_wgts;

		double[] min_wgts = TEROptPara.para().get_doublelist(TEROptPara.OPTIONS.MIN_WGTS);
		if (min_wgts != null)
			this.min_wgt = min_wgts;

		double[] perturb_range = TEROptPara.para().get_doublelist(TEROptPara.OPTIONS.PERTURB_RANGE);
		if (perturb_range != null)
			this.perturb_range = perturb_range;
	}

	public double[] gen_perturb_wgts(boolean no_change)
	{
		double[] p_wgts = null;
		if (best_wgts == null)
		{
			p_wgts = get_copy_init_weights();
		}
		else
		{
			p_wgts = new double[best_wgts.length];
			for (int k = 0; k < best_wgts.length; k++)
				p_wgts[k] = best_wgts[k];
		}
		gen_perturb_wgts(no_change, p_wgts);
		return p_wgts;
	}

	private void gen_perturb_wgts(boolean no_change, double[] p_wgts)
	{
		if (no_change)
			return;
		for (int i = 0; i < p_wgts.length; i++)
		{
			if ((i < change_wgts.length) && (change_wgts[i] == false))
				continue;
			double pr = 1.0;
			if (i < perturb_range.length)
				pr = perturb_range[i];
			double rn = ((1.0 - (2.0 * _rand.nextDouble())) * pr);
			p_wgts[i] += rn;

			if ((i < min_wgt.length) && (p_wgts[i] < min_wgt[i]))
				p_wgts[i] = min_wgt[i];
			if ((i < max_wgt.length) && (p_wgts[i] > max_wgt[i]))
				p_wgts[i] = max_wgt[i];
		}
		return;
	}

	public void perturb_init_wgts(int seed)
	{
		double[] p_wgts = get_copy_init_weights();
		if (seed != 0)
			set_seed(seed);
		gen_perturb_wgts((seed == 0), p_wgts);
		this.init_wgts = p_wgts;
		return;
	}

	public void load_target_judgments(String fname)
	{
		// file is of the format
		// ID val
		// or
		// SETID\tDOCID\tSEGID\tSYS_B\tSYS_W
		System.out.println("Loading Target Judgments from " + fname);
		TreeMap<TERid, Double> s_res = new TreeMap<TERid, Double>();
		TreeMap<TERid, ArrayList<TERid>> p_res = new TreeMap<TERid, ArrayList<TERid>>();
		try
		{
			BufferedReader fh = new BufferedReader(new FileReader(fname));
			String line;
			Pattern pat = Pattern.compile("^([^ ]+)[ \\t]+([-0-9.]+)[ \\t]*$");
			while ((line = fh.readLine()) != null)
			{
				if (hj_type == HJ_TYPE.SCORE)
				{
					Matcher mat = pat.matcher(line);
					if (mat.matches())
					{
						String id = mat.group(1);
						String val = mat.group(2);
						id = id.trim();
						TERid tid = new TERid(id);
						double d = Double.parseDouble(val);
						s_res.put(tid, d);
					}
				}
				else
				{
					String[] sline = line.split("\t");
					if (sline.length == 5)
					{
						String setid = sline[0];
						String docid = sline[1];
						String segid = sline[2];
						String sysb = sline[3];
						String sysw = sline[4];
						TERid id_b = new TERid("[" + sysb + "][" + setid + "][" + docid + "][" + segid + "]", setid,
								sysb, docid, segid);
						TERid id_w = new TERid("[" + sysw + "][" + setid + "][" + docid + "][" + segid + "]", setid,
								sysw, docid, segid);
						ArrayList<TERid> wlst = p_res.get(id_b);
						if (wlst == null)
						{
							wlst = new ArrayList<TERid>();
							p_res.put(id_b, wlst);
						}
						wlst.add(id_w);
						s_res.put(id_b, 0.0);
					}
				}
			}
		}
		catch (IOException ioe)
		{
			System.err.println("IO Error loading target judgments from " + fname + ": " + ioe);
			ioe.printStackTrace();
			System.exit(-1);
		}
		judgments = s_res;
		pref_judgments = p_res;
		return;
	}

	public void load_segment_lengths(String fname)
	{
		// file is of the format
		// ID val
		System.out.println("Loading Segment Lengths");
		TreeMap<String, Double> res = new TreeMap<String, Double>();
		try
		{
			BufferedReader fh = new BufferedReader(new FileReader(fname));
			String line;
			Pattern pat = Pattern.compile("^([^ \\t]+)[ \\t]+([^ \\t]+)[ \\t]+([-0-9.]+)[ \\t]*$");
			while ((line = fh.readLine()) != null)
			{
				Matcher mat = pat.matcher(line);
				if (mat.matches())
				{
					String doc = mat.group(1).trim();
					String seg = mat.group(2).trim();
					String len = mat.group(3).trim();
					String id = "[" + doc + "][" + seg + "]";
					double d = Double.parseDouble(len);
					res.put(id, d);
				}
			}
		}
		catch (IOException ioe)
		{
			System.err.println("Loading segment lengths from " + fname + ": " + ioe);
			return;
		}
		seglen = res;
		return;
	}

	public static void output_weights(String fname, String header, double[] wgts)
	{
		try
		{
			BufferedWriter out = new BufferedWriter(new FileWriter(fname));
			out.write(header + "\n");
			out.write(TERutilities.join(" ", wgts) + "\n");
			out.close();
		}
		catch (IOException ioe)
		{
			System.err.println("Optimize TER Weight Output " + fname + ": " + ioe);
		}
	}

	public void output(String fname)
	{
		if (best_output != null)
			best_output.output();
		output_weights(fname, "Optimized Weights (initval=" + init_sc + " optval=" + best_sc + "):", best_wgts);
		return;
	}

	private double get_next_step(double[] cur_wgts, double[] new_wgts, TERoutput output, double cur_sc)
	{
		double step_size = 0.01;
		double[] best_wgts = new double[cur_wgts.length];
		int[] good_dir = new int[cur_wgts.length];
		double best_sc = cur_sc;
		double num_rand = 0;

		double[] step_sizes =
		{0.05, 0.01, 0.02};
		for (int k = 0; k < cur_wgts.length; k++)
			best_wgts[k] = cur_wgts[k];
		for (int s = 0; s < step_sizes.length; s++)
		{
			for (int i = 0; i < cur_wgts.length; i++)
			{
				if ((i < change_wgts.length) && (change_wgts[i] == false))
					continue;
				for (int k = 0; k < cur_wgts.length; k++)
				{
					new_wgts[k] = cur_wgts[k];
				}
				good_dir[i] = 0;
				new_wgts[i] = cur_wgts[i] + step_sizes[s];
				boolean ok = true;

				if ((i < min_wgt.length) && (new_wgts[i] < min_wgt[i]))
					new_wgts[i] = min_wgt[i];
				if ((i < max_wgt.length) && (new_wgts[i] > max_wgt[i]))
					new_wgts[i] = max_wgt[i];

				// System.out.println("  considering new weights: " +
				// TERalignment.join(" ", new_wgts));
				double nsc = rescore(new_wgts, output);
				// System.out.println("   rescore is " + nsc + " (init_sc=" +
				// cur_sc + " best_sc=" + best_sc + ")");
				if (ok && ((nsc - cur_sc) > precision))
					good_dir[i] = 1;
				if (ok && ((nsc - best_sc) > precision))
				{
					best_sc = nsc;
					for (int k = 0; k < cur_wgts.length; k++)
						best_wgts[k] = new_wgts[k];
				}
				else
				{
					ok = true;
					new_wgts[i] = cur_wgts[i] + (step_sizes[s] * -1.0);
					if ((i < min_wgt.length) && (new_wgts[i] < min_wgt[i]))
						new_wgts[i] = min_wgt[i];
					if ((i < max_wgt.length) && (new_wgts[i] > max_wgt[i]))
						new_wgts[i] = max_wgt[i];

					// System.out.println("  considering new weights: " +
					// TERalignment.join(" ", new_wgts));
					nsc = rescore(new_wgts, output);
					// System.out.println("   rescore is " + nsc + " (init_sc="
					// + cur_sc + " best_sc=" + best_sc + ")");
					if (ok && ((nsc - cur_sc) > precision))
						good_dir[i] = -1;
					if (ok && ((nsc - best_sc) > precision))
					{
						best_sc = nsc;
						for (int k = 0; k < cur_wgts.length; k++)
							best_wgts[k] = new_wgts[k];
					}
				}
			}
		}

		double[] step_sizes2 =
		{0.05, 0.01, 0.02, -0.05, -0.01, -0.02};
		for (int i = 0; i < cur_wgts.length; i++)
		{
			if ((i < change_wgts.length) && (change_wgts[i] == false))
				continue;
			for (int j = 0; j < cur_wgts.length; j++)
			{
				if ((j < change_wgts.length) && (change_wgts[j] == false))
					continue;
				for (int s1 = 0; s1 < step_sizes2.length; s1++)
				{
					for (int s2 = 0; s2 < step_sizes2.length; s2++)
					{
						for (int k = 0; k < cur_wgts.length; k++)
						{
							new_wgts[k] = cur_wgts[k];
						}

						new_wgts[i] = cur_wgts[i] + step_sizes2[s1];
						new_wgts[j] = cur_wgts[j] + step_sizes2[s2];

						boolean ok = true;
						if (i == j)
							ok = false;
						if ((i < min_wgt.length) && (new_wgts[i] < min_wgt[i]))
							new_wgts[i] = min_wgt[i];
						if ((i < max_wgt.length) && (new_wgts[i] > max_wgt[i]))
							new_wgts[i] = max_wgt[i];

						if ((j < min_wgt.length) && (new_wgts[j] < min_wgt[j]))
							new_wgts[j] = min_wgt[j];
						if ((j < max_wgt.length) && (new_wgts[j] > max_wgt[j]))
							new_wgts[j] = max_wgt[j];

						if (ok)
						{
							double nsc = rescore(new_wgts, output);
							if (ok && ((nsc - best_sc) > precision))
							{
								best_sc = nsc;
								for (int k = 0; k < cur_wgts.length; k++)
									best_wgts[k] = new_wgts[k];
							}
						}
					}
				}
			}
		}

		step_size = 0.01;
		for (int r = 0; r < num_rand; r++)
		{
			for (int k = 0; k < cur_wgts.length; k++)
				new_wgts[k] = cur_wgts[k];
			for (int i = 0; i < cur_wgts.length; i++)
			{
				double choice = Math.random();
				if (((i < change_wgts.length) && (change_wgts[i] == false)) || (choice < 0.5))
				{
					// do nothing.
					new_wgts[i] += 0.0;
				}
				else if (choice < 0.9)
				{
					new_wgts[i] += (good_dir[i] * Math.random() * step_size);
				}
				else
				{
					new_wgts[i] += (1.0 - (2.0 * Math.random()) * step_size);
				}
				if ((i < max_wgt.length) && (new_wgts[i] > max_wgt[i]))
					new_wgts[i] = max_wgt[i];
				if ((i < min_wgt.length) && (new_wgts[i] < min_wgt[i]))
					new_wgts[i] = min_wgt[i];
			}
			double nsc = rescore(new_wgts, output);
			if ((nsc - best_sc) > precision)
			{
				best_sc = nsc;
				for (int k = 0; k < cur_wgts.length; k++)
					best_wgts[k] = new_wgts[k];
			}
		}

		for (int k = 0; k < cur_wgts.length; k++)
			new_wgts[k] = best_wgts[k];
		return best_sc;
	}

	public double[] get_new_wgts(double[] current_wgts, TERoutput output)
	{
		int steps_taken = 0;
		double[] cur_wgts = new double[current_wgts.length];
		double[] nwgts = new double[cur_wgts.length];
		double[] change_vec = new double[cur_wgts.length];
		for (int k = 0; k < cur_wgts.length; k++)
		{
			cur_wgts[k] = current_wgts[k];
			nwgts[k] = current_wgts[k];
		}

		double init_sc = 0.0;
		if (output == null)
		{
			init_sc = quick_rescore(cur_wgts, output);
		}
		else
		{
			init_sc = score(output);
		}
		double cur_sc = init_sc;
		for (int step = 0; step < MAX_STEPS_PER_ITER; step++)
		{
			double new_sc = get_next_step(cur_wgts, nwgts, output, cur_sc);
			for (int k = 0; k < nwgts.length; k++)
			{
				change_vec[k] = nwgts[k] - cur_wgts[k];
			}
			if ((new_sc - cur_sc) > precision)
			{
				System.out.println("\nStep " + (step + 1) + ": Score " + new_sc + " (old score was: " + cur_sc + ")");
				System.out.println(" Change is: " + TERutilities.join(" ", change_vec));
				System.out.println(" Weights: " + TERutilities.join(" ", nwgts));
				cur_sc = new_sc;
				for (int j = 0; j < cur_wgts.length; j++)
				{
					cur_wgts[j] = nwgts[j];
				}
				steps_taken++;
			}
			else
			{
				step = MAX_STEPS_PER_ITER;
			}
		}

		if (steps_taken > 0)
		{
			for (int k = 0; k < nwgts.length; k++)
			{
				change_vec[k] = cur_wgts[k] - current_wgts[k];
			}
			System.out.println("\n" + steps_taken + " steps taken. Old Score was: " + init_sc + " New Score is: "
					+ cur_sc + "\nChange Vec is: " + TERutilities.join(" ", change_vec) + "\nNew weights are: "
					+ TERutilities.join(" ", cur_wgts) + "\n");
			return cur_wgts;
		}
		System.out.println("\nNo steps taken.  Sorry.  Must be a local optimia.\n");
		return null;
	}

	public boolean load_counts(String fname)
	{
		if ((fname == null) || (fname.equals("")))
			return false;
		System.out.println("Loading counts from " + fname);
		TreeMap<TERid, String[]> tm = new TreeMap<TERid, String[]>();
		int num_wgts = 0;
		try
		{
			BufferedReader fh = new BufferedReader(new FileReader(fname));
			String line;
			Pattern pat = Pattern.compile("^([^ ]+)\\s+([-0-9eE. \\t]+)\\s*$");
			while ((line = fh.readLine()) != null)
			{
				Matcher mat = pat.matcher(line);
				if (mat.matches())
				{
					String id = mat.group(1);
					String val = mat.group(2);
					id = id.trim();
					TERid tid = new TERid(id);
					val = val.trim();
					String[] vals = val.split("\\s+");
					double[] ovals = new double[vals.length];
					for (int j = 0; j < vals.length; j++)
					{
						ovals[j] = Double.parseDouble(vals[j]);
					}
					num_wgts = vals.length;
					// System.out.println("Adding vals for " + id);
					tm.put(tid, vals);
				}
			}
			fh.close();
		}
		catch (IOException ioe)
		{
			System.err.println("Optimize TER Load Counts " + ioe);
		}

		if (judgments == null)
			return false;
		Iterator<TERid> it = judgments.keySet().iterator();
		_judge_vals = new double[judgments.size()];
		_doc_id = new int[judgments.size()];
		_sys_id = new int[judgments.size()];
		_lens = new double[judgments.size()];
		_out_vals = new double[judgments.size()];
		rescore_mat = new double[_out_vals.length][num_wgts];
		rescore_out = null;

		int pos = 0;
		while (it.hasNext())
		{
			TERid id = it.next();
			double tval = judgments.get(id);
			String sysname = id.sys_id;
			// String setname = id.test_id;
			String docname = id.doc_id;
			String segname = id.seg_id;

			Double slen = (Double) seglen.get("[" + docname + "][" + segname + "]");
			if (slen == null)
			{
				System.err.println("No segment length found for [" + docname + "][" + segname + "]");
				System.exit(-1);
			}
			String docidstr = "[" + sysname + "][" + docname + "]";
			Integer docid = (Integer) docnamemap.get(docidstr);
			Integer sysid = (Integer) sysnamemap.get(sysname);
			if (docid == null)
			{
				docid = docnamemap.size();
				docnamemap.put(docidstr, docid);
			}
			if (sysid == null)
			{
				sysid = sysnamemap.size();
				sysnamemap.put(sysname, docid);
			}

			// look up value from output
			double oval = 0.0;
			String[] ovals = (String[]) tm.get(id);
			if (ovals == null)
			{
				System.err.println("Cannot find result for " + id);
				System.exit(-1);
			}
			_out_vals[pos] = oval;
			_judge_vals[pos] = tval;
			_lens[pos] = slen;
			_doc_id[pos] = docid;
			_sys_id[pos] = sysid;
			for (int j = 0; j < ovals.length; j++)
			{
				rescore_mat[pos][j] = Double.parseDouble(ovals[j]);
			}
			pos++;
		}
		return true;
	}

	public double quick_rescore(double[] wgts, TERoutput output)
	{
		if (judgments == null)
			return 0.0;
		if (opt_func == OPTIMIZE_FUNC.PREF)
		{
			int num_gt = 0;
			int num_lt = 0;
			int num_t = 0;

			for (TERid sysb : pref_judgments.keySet())
			{
				ArrayList<TERid> lst = pref_judgments.get(sysb);
				double[] c_b = null;
				if (counts != null)
				{
					c_b = counts.get(sysb);
				}
				if (c_b == null)
				{
					TERalignment ta_b = output.getResult(sysb);
					if (ta_b != null)
					{
						c_b = ta_b.weight_vector();
					}
				}

				if (c_b == null)
				{
					// System.err.println("Cannot find result for " + sysb);
					// System.exit(-1);
				}
				else
				{
					double sc_b = 0.0;
					for (int i = 0; i < c_b.length; i++)
					{
						sc_b += (c_b[i] * wgts[i]);
					}
					for (TERid sysw : lst)
					{
						double[] c_w = null;
						if (counts != null)
						{
							c_w = counts.get(sysw);
						}
						if (c_w == null)
						{
							TERalignment ta_w = output.getResult(sysw);
							if (ta_w != null)
							{
								c_w = ta_w.weight_vector();
							}
						}
						if (c_w == null)
						{
							// System.err.println("Cannot find result for " +
							// sysw);
							// System.exit(-1);
						}
						else
						{
							double sc_w = 0.0;
							for (int i = 0; i < c_w.length; i++)
							{
								sc_w += (c_w[i] * wgts[i]);
							}
							if (sc_b > sc_w)
							{
								num_gt++;
							}
							else if (sc_b < sc_w)
							{
								num_lt++;
							}
							num_t++;
						}
					}
				}
			}
			if (num_t == 0)
				return 0.0;
			return ((double) num_lt) / ((double) num_t);
		}
		else
		{
			if ((rescore_mat == null) || (rescore_out != output))
			{
				if (judgments == null)
					return 0.0;
				Iterator<TERid> it = judgments.keySet().iterator();
				_judge_vals = new double[judgments.size()];
				_doc_id = new int[judgments.size()];
				_sys_id = new int[judgments.size()];
				_lens = new double[judgments.size()];
				_out_vals = new double[judgments.size()];
				rescore_mat = new double[_out_vals.length][wgts.length];
				rescore_out = output;

				int pos = 0;
				while (it.hasNext())
				{
					TERid id = it.next();
					double tval = (Double) judgments.get(id);

					String sysname = id.sys_id;
					// String setname = id.test_id;
					String docname = id.doc_id;
					String segname = id.seg_id;

					Double slen = (Double) seglen.get("[" + docname + "][" + segname + "]");
					if (slen == null)
					{
						System.err.println("No segment length found for [" + docname + "][" + segname + "]");
						System.exit(-1);
					}
					Integer docid = (Integer) docnamemap.get("[" + sysname + "][" + docname + "]");
					Integer sysid = (Integer) sysnamemap.get(sysname);
					if (docid == null)
					{
						docid = docnamemap.size();
						docnamemap.put(docname, docid);
					}
					if (sysid == null)
					{
						sysid = sysnamemap.size();
						sysnamemap.put(sysname, docid);
					}

					// look up value from output
					double oval = 0.0;
					TERalignment ta = output.getResult(id);
					if (ta == null)
					{
						System.err.println("Cannot find result for " + id);
						System.exit(-1);
					}
					oval = ta.score();
					_out_vals[pos] = oval;
					_judge_vals[pos] = tval;
					_lens[pos] = slen;
					_doc_id[pos] = docid;
					_sys_id[pos] = sysid;
					double ta_counts[] = ta.norm_weight_vector();
					for (int j = 0; j < ta_counts.length; j++)
					{
						rescore_mat[pos][j] = ta_counts[j];
					}
					pos++;
				}
			}

			for (int j = 0; j < _judge_vals.length; j++)
			{
				_out_vals[j] = 0.0;
				for (int i = 0; i < wgts.length; i++)
				{
					_out_vals[j] += (rescore_mat[j][i] * wgts[i]);
				}
			}
			return calc_opt(_judge_vals, _out_vals);
		}
	}

	public double rescore(double[] wgts, TERoutput output)
	{
		return quick_rescore(wgts, output);
	}

	public double full_rescore(double[] wgts, TERoutput output)
	{
		TERcost tc = terp.getLastCostFunc();
		tc.set_weights(wgts);
		output.rescore_all_alignments(tc);
		return score(output);
	}

	public double score(TERoutput output)
	{
		if (judgments == null)
			return 0.0;
		if (opt_func == OPTIMIZE_FUNC.PREF)
		{
			int num_gt = 0;
			int num_lt = 0;
			int num_t = 0;

			for (TERid sysb : pref_judgments.keySet())
			{
				ArrayList<TERid> lst = pref_judgments.get(sysb);
				TERalignment ta_b = output.getResult(sysb);
				if (ta_b == null)
				{
					// System.err.println("Cannot find result for " + sysb);
					// System.exit(-1);
				}
				else
				{
					double sc_b = ta_b.score();
					for (TERid sysw : lst)
					{
						TERalignment ta_w = output.getResult(sysw);
						if (ta_w == null)
						{
							// System.err.println("Cannot find result for " +
							// sysw);
							// System.exit(-1);
						}
						else
						{
							double sc_w = ta_w.score();
							if (sc_b > sc_w)
							{
								num_gt++;
							}
							else if (sc_b < sc_w)
							{
								num_lt++;
							}
							num_t++;
						}
					}
				}
			}
			if (num_t == 0)
				return 0.0;
			return ((double) num_lt) / ((double) num_t);
		}
		else
		{
			Iterator<TERid> it = judgments.keySet().iterator();
			double[] judge_vals = new double[judgments.size()];
			int[] _doc_id = new int[judgments.size()];
			int[] _sys_id = new int[judgments.size()];
			double[] _lens = new double[judgments.size()];
			double[] out_vals = new double[judgments.size()];

			int pos = 0;
			while (it.hasNext())
			{
				TERid id = it.next();
				double tval = (Double) judgments.get(id);

				String sysname = id.sys_id;
				// String setname = id.test_id;
				String docname = id.doc_id;
				String segname = id.seg_id;

				Double slen = (Double) seglen.get("[" + docname + "][" + segname + "]");
				if (slen == null)
				{
					System.err.println("No segment length found for [" + docname + "][" + segname + "]");
					System.exit(-1);
				}
				Integer docid = (Integer) docnamemap.get("[" + sysname + "][" + docname + "]");
				Integer sysid = (Integer) sysnamemap.get(sysname);
				if (docid == null)
				{
					docid = docnamemap.size();
					docnamemap.put(docname, docid);
				}
				if (sysid == null)
				{
					sysid = sysnamemap.size();
					sysnamemap.put(sysname, docid);
				}

				// look up value from output
				double oval = 0.0;
				TERalignment ta = output.getTERidResult(id);
				if (ta == null)
				{
					System.err.println("Cannot find result for " + id);
					System.exit(-1);
				}
				oval = ta.score();

				judge_vals[pos] = tval;
				out_vals[pos] = oval;
				_lens[pos] = slen;
				_doc_id[pos] = docid;
				_sys_id[pos] = sysid;

				pos++;
			}
			return calc_opt(judge_vals, out_vals);
		}
	}

	private double calc_opt(double[] tvals, double[] cur)
	{
		if (opt_func == OPTIMIZE_FUNC.SEG_PEAR)
		{
			return pearson(tvals, cur);
		}
		else if (opt_func == OPTIMIZE_FUNC.DOC_PEAR)
		{
			double[][] doc_vals = reduce_gran(tvals, cur, docnamemap.size(), _doc_id);
			return pearson(doc_vals[0], doc_vals[1]);
		}
		else if (opt_func == OPTIMIZE_FUNC.SYS_PEAR)
		{
			double[][] sys_vals = reduce_gran(tvals, cur, sysnamemap.size(), _sys_id);
			return pearson(sys_vals[0], sys_vals[1]);
		}
		return 0.0;
	}

	private double[][] reduce_gran(double[] tvals, double[] cur, int newsize, int[] granmap)
	{
		double[][] tr = new double[3][];

		double[] dtvals = new double[newsize];
		double[] dcur = new double[newsize];
		double[] len = new double[newsize];
		for (int i = 0; i < tvals.length; i++)
		{
			dtvals[granmap[i]] += (_lens[i] * tvals[i]);
			dcur[granmap[i]] += (_lens[i] * cur[i]);
			len[granmap[i]] += _lens[i];
		}
		int skip = 0;
		for (int i = 0; i < dtvals.length; i++)
		{
			if (len[i] == 0.0)
				skip++;
			else
			{
				dtvals[i] /= len[i];
				dcur[i] /= len[i];
			}
		}
		if (skip == 0)
		{
			tr[0] = dtvals;
			tr[1] = dcur;
			tr[2] = len;
			return tr;
		}
		double[] rdtvals = new double[dtvals.length - skip];
		double[] rdcur = new double[dtvals.length - skip];
		double[] rlen = new double[dtvals.length - skip];
		int pos = 0;
		for (int i = 0; i < dtvals.length; i++)
		{
			if (len[i] != 0.0)
			{
				rdtvals[pos] = dtvals[i];
				rdcur[pos] = dcur[i];
				rlen[pos] = len[i];
				pos++;
			}
		}
		tr[0] = rdtvals;
		tr[1] = rdcur;
		tr[2] = rlen;
		return tr;
	}

	private double pearson(double[] tvals, double[] cur)
	{
		// Compute Means
		if (tvals.length != cur.length)
		{
			System.err.println("Error.  Differing array lengths in pearson: " + tvals.length + " vs " + cur.length);
		}
		double tar_mu = 0.0;
		double cur_mu = 0.0;
		for (int i = 0; i < tvals.length; i++)
		{
			// printf("SCORES %4d: %0.4f %0.4f\n", i, target->ve[i], cur[i]);
			// System.out.println("SC " + tvals[i] + " " + cur[i]);
			tar_mu += tvals[i];
			cur_mu += cur[i];
		}
		tar_mu /= tvals.length;
		cur_mu /= tvals.length;

		double tar_var = 0.0;
		double cur_var = 0.0;
		for (int i = 0; i < tvals.length; i++)
		{
			tar_var += ((tvals[i] - tar_mu) * (tvals[i] - tar_mu));
			cur_var += ((cur[i] - cur_mu) * (cur[i] - cur_mu));
		}
		cur_var /= tvals.length;
		tar_var /= tvals.length;

		double tar_stddev = Math.sqrt(tar_var);
		double cur_stddev = Math.sqrt(cur_var);

		double r = 0.0;
		for (int i = 0; i < tvals.length; i++)
		{
			r += ((tvals[i] - tar_mu) * (cur[i] - cur_mu));
		}
		r /= ((tvals.length - 1) * tar_stddev * cur_stddev);

		return Math.abs(r);
	}

	public double[] get_copy_init_weights()
	{
		if (init_wgts == null)
		{
			String iwfname = TEROptPara.para().get_string(TEROptPara.OPTIONS.INIT_WEIGHTS_FILE);
			if ((iwfname != null) && (!(iwfname.equals(""))))
			{
				terp.getLastCostFunc().load_weights(iwfname);
			}
			init_wgts = terp.getLastCostFunc().current_weights();
		}

		double[] d = new double[init_wgts.length];
		for (int i = 0; i < init_wgts.length; i++)
			d[i] = init_wgts[i];
		return d;
	}

	public void optimize(boolean skip_first, boolean local_only)
	{
		double[] cur_wgts = get_copy_init_weights();
		boolean first = true;
		boolean done = false;
		TERoutput output = null;
		int cur_iter = 0;

		while (!done)
		{
			double cur_sc = 0.0;
			if (first && skip_first)
			{
				first = false;
				cur_sc = quick_rescore(cur_wgts, output);
			}
			else
			{
				System.out.println("Rerunning TERp");
				terp.set_weights(cur_wgts);
				output = terp.rerun();
				cur_sc = score(output);
			}

			System.out.println("Iteration " + cur_iter + " (of " + MAX_ITERS + ") Score is " + cur_sc);

			if (best_wgts == null)
			{
				init_sc = cur_sc;
			}
			if ((cur_sc > best_sc) || (best_wgts == null))
			{
				best_sc = cur_sc;
				best_output = output;
				best_wgts = new double[cur_wgts.length];
				for (int i = 0; i < cur_wgts.length; i++)
				{
					best_wgts[i] = cur_wgts[i];
				}
				System.out.println("Better than current best.  Storing weights.");
				System.out.println("Weights are: " + TERutilities.join(" ", best_wgts));
			}

			if (cur_iter == MAX_ITERS)
			{
				done = true;
			}
			else
			{
				cur_iter++;
				System.out.println("\nSearching for new weights without realigning");
				double[] new_weights = get_new_wgts(cur_wgts, output);

				if (new_weights == null)
				{
					done = true;
				}
				else if (local_only)
				{
					done = true;
					System.out.println("Not rerunning TERp.  Storing best local weights.");
					cur_sc = quick_rescore(new_weights, output);
					best_sc = cur_sc;
					best_output = output;
					best_wgts = new double[cur_wgts.length];
					for (int i = 0; i < cur_wgts.length; i++)
					{
						best_wgts[i] = new_weights[i];
					}
					System.out.println("Weights are: " + TERutilities.join(" ", best_wgts));
				}
				else
				{
					cur_wgts = new_weights;
				}
			}
		}
	}

	public void set_hj_type(String type)
	{
		if (type == null)
			return;
		type = type.trim();
		if (type.equals("") || type.equals("score"))
			hj_type = HJ_TYPE.SCORE;
		else if (type.equals("pref"))
			hj_type = HJ_TYPE.PREF;
		else
		{
			System.err.println("Error:  Invalid human judgment type: " + type);
			System.err.println("  Valid values are \"score\" \"pref\"");
			System.exit(-1);
		}

	}

	public void set_opt_func(String func)
	{
		if (func == null)
			return;
		func = func.trim();
		if (func.equals("") || func.equals("segment pearson"))
			opt_func = OPTIMIZE_FUNC.SEG_PEAR;
		else if (func.equals("document pearson"))
			opt_func = OPTIMIZE_FUNC.DOC_PEAR;
		else if (func.equals("system pearson"))
			opt_func = OPTIMIZE_FUNC.SYS_PEAR;
		else if (func.equals("preference"))
		{
			opt_func = OPTIMIZE_FUNC.PREF;
		}
		else
		{
			System.err.println("Error:  Invalid optimization function: " + func);
			System.err
					.println("  Valid values are \"segment pearson\" \"document pearson\" \"system pearson\" \"preference\"");
			System.exit(-1);
		}
	}

	public void set_seed(int seed)
	{
		_rand = new Random(seed);
	}

	private Random _rand = new Random(0);

	private TERplus terp = new TERplus(false);
	private Map<TERid, Double> judgments = null;
	private Map<TERid, ArrayList<TERid>> pref_judgments = null;
	private Map<TERid, double[]> counts = null;
	private Map<String, Double> seglen = null;

	public static enum HJ_TYPE
	{
		SCORE, PREF
	}
	private HJ_TYPE hj_type = HJ_TYPE.SCORE;

	public static enum OPTIMIZE_FUNC
	{
		SEG_PEAR, DOC_PEAR, SYS_PEAR, PREF
	}
	private OPTIMIZE_FUNC opt_func = OPTIMIZE_FUNC.SEG_PEAR;

	private int MAX_ITERS = 20;
	private int MAX_STEPS_PER_ITER = 5;

	private double[][] rescore_mat = null;
	private TERoutput rescore_out = null;
	private double[] _judge_vals = null;
	private double[] _out_vals = null;
	private int[] _doc_id = null;
	private int[] _sys_id = null;
	private double[] _lens = null;
	private Map<String, Integer> docnamemap = new TreeMap<String, Integer>();
	private Map<String, Integer> sysnamemap = new TreeMap<String, Integer>();

	private boolean[] change_wgts =
	{true, true, true, true, true, true, true, true, true, true, true};

	private double[] min_wgt =
	{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, -100.0, -100.0, -100.0, -100.0};
	private double[] max_wgt =
	{100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0, 100.0};
	private double[] perturb_range =
	{0.2, 0.2, 0.2, 0.2, 0.2, 0.2, 0.2, 0.2, 0.2, 0.2, 0.2};

	private static final double precision = 0.000001;

	private double best_sc = 0.0;
	private TERoutput best_output = null;
	private double[] best_wgts = null;
	private double init_sc = 0.0;
	private double[] init_wgts = null;
}
